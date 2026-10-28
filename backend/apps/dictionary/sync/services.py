"""Dictionary synchronisation: what changed, what was deleted, and word details.

Pure application logic. No HTTP or serializer imports; the API layer turns the
returned data into JSON.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta

from django.conf import settings
from django.db.models import Q, QuerySet
from django.utils import timezone

from apps.dictionary.entries.cache import dictionary_version, safe_cache_get_or_set
from apps.dictionary.entries.models import (
    Collocation,
    DictionaryEntry,
    DictionaryTombstone,
    ExampleSentence,
    SynonymAntonym,
    Translation,
    WordForm,
    WordMeaning,
)

DEFAULT_PAGE_SIZE = 200
MAX_PAGE_SIZE = 500
MAX_LITE_PAGE_SIZE = 2000
MAX_DETAIL_IDS = 50


@dataclass(frozen=True)
class SyncPage:
    entries: list[DictionaryEntry]
    meanings: list[WordMeaning]
    next_after_id: int
    has_more: bool
    #: Deletions since ``since``; only set on the first page of an incremental pass.
    deleted_ids: list[int] | None = None
    deleted_ids_truncated: bool = False
    #: The cursor was too old to answer incrementally and a full pass was served.
    full_resync: bool = False

    @property
    def entry_ids(self) -> list[int]:
        return [entry.id for entry in self.entries]

    @property
    def meaning_ids(self) -> list[int]:
        return [meaning.id for meaning in self.meanings]


@dataclass(frozen=True)
class WordDetails:
    entries: list[DictionaryEntry]
    meanings: list[WordMeaning]
    missing_ids: list[int] = field(default_factory=list)


def max_page_size(lite: bool) -> int:
    return MAX_LITE_PAGE_SIZE if lite else MAX_PAGE_SIZE


def fetch_sync_page(
    *, after_id: int, limit: int, since: datetime | None, now: datetime | None = None
) -> SyncPage:
    """One page of entries (with their meanings) in id order.

    ``since`` selects entries whose own row or meanings changed after it. A
    cursor older than the tombstone retention can no longer be answered
    incrementally, so it is ignored and flagged as ``full_resync``.
    """
    now = now or timezone.now()
    full_resync = False
    if since is not None and since < now - timedelta(days=settings.DICTIONARY_TOMBSTONE_RETENTION_DAYS):
        since = None
        full_resync = True

    queryset = DictionaryEntry.objects.all()
    if since is not None:
        changed_entries = DictionaryEntry.objects.filter(updated_at__gt=since).values("id")
        changed_meanings = WordMeaning.objects.filter(updated_at__gt=since).values("entry_id")
        queryset = queryset.filter(Q(id__in=changed_entries) | Q(id__in=changed_meanings))
    if after_id:
        queryset = queryset.filter(id__gt=after_id)

    page = list(queryset.order_by("id")[: limit + 1])
    entries = page[:limit]
    meanings = _meanings_for([entry.id for entry in entries])

    deleted_ids = None
    truncated = False
    if after_id == 0 and since is not None:
        deleted_ids, truncated = _deleted_entry_ids(since)

    return SyncPage(
        entries=entries,
        meanings=meanings,
        next_after_id=entries[-1].id if entries else after_id,
        has_more=len(page) > limit,
        deleted_ids=deleted_ids,
        deleted_ids_truncated=truncated,
        full_resync=full_resync,
    )


def fetch_word_details(ids: list[int]) -> WordDetails:
    """Entries and meanings for ``ids`` (callers bound the list to ``MAX_DETAIL_IDS``)."""
    entries = list(DictionaryEntry.objects.filter(id__in=ids).order_by("id"))
    found = {entry.id for entry in entries}
    return WordDetails(
        entries=entries,
        meanings=_meanings_for(list(found)),
        missing_ids=[entry_id for entry_id in ids if entry_id not in found],
    )


def fetch_child_rows(entry_ids: list[int], meaning_ids: list[int]) -> dict[str, QuerySet]:
    """Lazily loaded content of ``entry_ids`` (translations, examples, synonyms, forms, collocations)."""

    def for_entries(model):
        queryset = model.objects.filter(entry_id__in=entry_ids) if entry_ids else model.objects.none()
        return queryset.order_by("id")

    synonyms = (
        SynonymAntonym.objects.filter(meaning_id__in=meaning_ids)
        if meaning_ids
        else SynonymAntonym.objects.none()
    )
    return {
        "translations": for_entries(Translation),
        "examples": for_entries(ExampleSentence),
        "synonyms": synonyms.order_by("id"),
        "forms": for_entries(WordForm),
        "collocations": for_entries(Collocation),
    }


def dictionary_meta() -> dict:
    """Corpus totals; counting 400k rows on every first page is wasteful, so the
    result is cached against the dictionary version stamp."""
    return safe_cache_get_or_set(
        f"dictionary:meta:{dictionary_version()}",
        _compute_dictionary_meta,
        settings.DICTIONARY_META_CACHE_TTL,
    )


def _compute_dictionary_meta() -> dict:
    entries = DictionaryEntry.objects.all()
    return {
        "entries": entries.count(),
        "maxEntryId": entries.order_by("-id").values_list("id", flat=True).first() or 0,
        "missingRussian": entries.filter(primary_meaning_ru="").count(),
        "missingUzbek": entries.filter(primary_meaning_uz="").count(),
    }


def _meanings_for(entry_ids: list[int]) -> list[WordMeaning]:
    if not entry_ids:
        return []
    return list(WordMeaning.objects.filter(entry_id__in=entry_ids).order_by("id"))


def _deleted_entry_ids(since: datetime) -> tuple[list[int], bool]:
    cap = settings.DICTIONARY_TOMBSTONE_PAGE_MAX
    ids = list(
        DictionaryTombstone.objects.filter(deleted_at__gt=since)
        .order_by("id")
        .values_list("entry_id", flat=True)[: cap + 1]
    )
    return ids[:cap], len(ids) > cap
