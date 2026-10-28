"""Dictionary search use case: run the ranked search and load the matching rows."""

from __future__ import annotations

import hashlib
import time
from dataclasses import dataclass

from django.db.models import Case, IntegerField, Prefetch, Value, When

from apps.dictionary.entries.cache import dictionary_version
from apps.dictionary.entries.models import DictionaryEntry, Translation

from .engine import SearchRequest, search


@dataclass(frozen=True)
class SearchPage:
    entries: list[DictionaryEntry]
    has_more: bool
    backend: str
    took_ms: float


def run_search(search_request: SearchRequest, *, page: int, page_size: int) -> SearchPage:
    """One page of ranked results with their translations, in ranking order."""
    started = time.monotonic()
    result = search(search_request, limit=page_size, offset=(page - 1) * page_size)
    entries = load_entries(result.entry_ids, search_request.target_language)
    return SearchPage(
        entries=entries,
        has_more=result.has_more,
        backend=result.backend,
        took_ms=round((time.monotonic() - started) * 1000, 2),
    )


def load_entries(entry_ids, target_language):
    """Fetch the page rows without N+1 queries, preserving search order."""
    if not entry_ids:
        return []
    translations = Translation.objects.all()
    if target_language:
        translations = translations.filter(target_language=target_language)
    entries = DictionaryEntry.objects.filter(id__in=entry_ids).prefetch_related(
        Prefetch(
            "translations",
            queryset=translations.order_by("target_language", "id"),
            to_attr="selected_translations",
        )
    )
    position = Case(
        *[When(pk=entry_id, then=Value(index)) for index, entry_id in enumerate(entry_ids)],
        output_field=IntegerField(),
    )
    return list(entries.annotate(search_position=position).order_by("search_position"))


def search_cache_key(search_request: SearchRequest, page: int, page_size: int, variant: str) -> str:
    """Cache key that changes with the query, paging, response shape and dictionary content."""
    parts = [
        dictionary_version(),
        variant,
        search_request.normalized,
        search_request.source_language or "",
        search_request.target_language or "",
        search_request.part_of_speech or "",
        search_request.cefr_level or "",
        "partial" if search_request.match_partial else "indexed",
        str(page),
        str(page_size),
    ]
    return "dictionary:search:" + hashlib.sha1("|".join(parts).encode("utf-8")).hexdigest()
