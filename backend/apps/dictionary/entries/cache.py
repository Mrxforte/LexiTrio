"""Cache helpers for dictionary reads with stampede protection and outage resilience.

Every dictionary response carries a version stamp derived from the newest
``updated_at`` of the entry, meaning and translation tables plus their row
counts. Updating a translation (or deleting a row) changes the stamp, so a
cached search response can never outlive the data it was rendered from.
"""

from __future__ import annotations

import hashlib
import logging
import time

from django.conf import settings
from django.core.cache import cache

logger = logging.getLogger(__name__)

VERSION_CACHE_KEY = "dictionary:version"


def _table_stamp(model) -> str:
    """Cheap per-table facts: row count, newest id and newest write."""
    rows = model.objects.count()
    newest_id = model.objects.order_by("-id").values_list("id", flat=True).first() or 0
    latest = (
        model.objects.order_by("-updated_at").values_list("updated_at", flat=True).first()
    )
    return (
        f"{model._meta.db_table}:{rows}:{newest_id}:"
        f"{latest.isoformat() if latest else '-'}"
    )


def compute_dictionary_version() -> str:
    from .models import DictionaryEntry, Translation, WordMeaning

    parts = [_table_stamp(model) for model in (DictionaryEntry, WordMeaning, Translation)]
    return hashlib.sha1("|".join(parts).encode("utf-8")).hexdigest()[:16]


def dictionary_version() -> str:
    """Return a stamp that changes whenever dictionary content changes.

    Resilient to cache service failures; if Redis is unreachable, computes and returns
    the version directly without throwing an exception.
    """
    try:
        stamp = cache.get(VERSION_CACHE_KEY)
        if stamp:
            return stamp
    except Exception as exc:
        logger.warning(f"Cache get failed for {VERSION_CACHE_KEY}: {exc}")
        stamp = None

    stamp = compute_dictionary_version()

    try:
        cache.set(VERSION_CACHE_KEY, stamp, settings.DICTIONARY_VERSION_CACHE_TTL)
    except Exception as exc:
        logger.warning(f"Cache set failed for {VERSION_CACHE_KEY}: {exc}")

    return stamp


def invalidate_dictionary_cache() -> None:
    """Drop the cached version stamp so the next read recomputes it."""
    try:
        cache.delete(VERSION_CACHE_KEY)
    except Exception as exc:
        logger.warning(f"Cache delete failed for {VERSION_CACHE_KEY}: {exc}")


def safe_cache_get_or_set(key: str, default_fn, ttl: int):
    """Retrieve value from cache or compute it, with lock-based stampede protection.

    Prevents multiple concurrent workers from executing identical expensive computation
    (e.g., heavy search/serialization) simultaneously when a cache key expires.
    """
    if ttl <= 0:
        return default_fn()

    try:
        cached = cache.get(key)
        if cached is not None:
            return cached
    except Exception as exc:
        logger.warning(f"Cache read failed for key {key}: {exc}")
        return default_fn()

    # Acquire lock for stampede protection
    lock_key = f"lock:{key}"
    got_lock = False
    try:
        got_lock = cache.add(lock_key, "1", timeout=10)
    except Exception:
        got_lock = False

    if got_lock:
        try:
            val = default_fn()
            cache.set(key, val, ttl)
            return val
        except Exception as exc:
            logger.error(f"Error computing value for cache key {key}: {exc}")
            raise
        finally:
            try:
                cache.delete(lock_key)
            except Exception:
                pass
    else:
        # Another process is computing the value; wait briefly or compute if timed out
        for _ in range(10):
            time.sleep(0.05)
            try:
                cached = cache.get(key)
                if cached is not None:
                    return cached
            except Exception:
                break
        return default_fn()
