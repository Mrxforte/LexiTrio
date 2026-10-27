"""Ranked dictionary search.

The catalog holds ~130k entries and ~85k translations.
Search hot path uses SQLite FTS5 index on SQLite or pg_trgm GIN index on PostgreSQL,
falling back to bounded ORM query when no specialized index is present.
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass

from django.conf import settings
from django.db import DatabaseError, connection
from django.db.models import Case, Exists, IntegerField, OuterRef, Q, Value, When

from .models import DictionaryEntry, Translation

FTS_TABLE = "dictionary_search"

# Results are grouped by how closely they match; lower is better.
RANK_EXACT = 0
RANK_PREFIX = 1
RANK_TEXT = 2
RANK_PARTIAL = 3

# A single character matches tens of thousands of rows; exact matching is the
# only useful response at that length.
MIN_INDEXED_QUERY_LENGTH = 2

BACKEND_FTS = "fts5"
BACKEND_POSTGRES = "postgres"
BACKEND_ORM = "orm"

# Upper bound for the page probe ("one row more than the page") and arm limits.
_ARM_LIMIT_MAX = 1000


@dataclass(frozen=True)
class SearchRequest:
    query: str
    source_language: str | None = None
    target_language: str | None = None
    part_of_speech: str | None = None
    cefr_level: str | None = None
    match_partial: bool = True

    @property
    def normalized(self) -> str:
        return normalize_query(self.query)


@dataclass(frozen=True)
class SearchResult:
    entry_ids: list[int]
    has_more: bool
    backend: str


def normalize_query(value) -> str:
    """Normalise user input the same way imports normalise headwords."""
    text = unicodedata.normalize("NFKC", str(value or ""))
    text = text.strip().lower().replace("\u2019", "'").replace("\u2013", "-")
    return " ".join(text.split())


def build_fts_query(normalized: str) -> str:
    """Turn user input into a safe FTS5 MATCH expression.

    Only word characters survive, so FTS5 operators cannot be injected. The
    final token is a prefix query so typing ``app`` already finds ``apple``.
    """
    tokens = [token for token in re.split(r"[^\w]+", normalized, flags=re.UNICODE) if token]
    if not tokens:
        return ""
    quoted = [f'"{token}"' for token in tokens[:-1]]
    quoted.append(f'"{tokens[-1]}"*')
    return " ".join(quoted)


def prefix_upper_bound(prefix: str) -> str:
    """Smallest string greater than every string that starts with ``prefix``."""
    return prefix + "\U0010ffff"


def _has_fts_table() -> bool:
    if connection.vendor != "sqlite":
        return False
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = %s", [FTS_TABLE]
            )
            return cursor.fetchone() is not None
    except DatabaseError:
        return False


_HAS_ROWS_CACHE: dict[str, bool] = {}


def fts_table_has_rows() -> bool:
    """Whether the index currently holds documents.

    Positive answers are cached per process. Negative answers are re-checked on
    every call (a cheap lookup) so a freshly imported catalog starts using the
    index as soon as it is rebuilt.
    """
    key = connection.alias
    if _HAS_ROWS_CACHE.get(key):
        return True
    try:
        with connection.cursor() as cursor:
            cursor.execute(f"SELECT 1 FROM {FTS_TABLE} LIMIT 1")
            has_rows = cursor.fetchone() is not None
    except DatabaseError:
        has_rows = False
    if has_rows:
        _HAS_ROWS_CACHE[key] = True
    return has_rows


def reset_search_caches() -> None:
    """Forget process-local index state (used by the rebuild command and tests)."""
    _HAS_ROWS_CACHE.clear()


def search(request: SearchRequest, *, limit: int, offset: int) -> SearchResult:
    """Return one page of matching entry ids ordered from best to worst match."""
    normalized = request.normalized
    if not normalized:
        return SearchResult(entry_ids=[], has_more=False, backend=BACKEND_FTS)

    if connection.vendor == "postgresql":
        try:
            return _postgres_search(request, normalized, limit=limit, offset=offset)
        except DatabaseError:
            pass
    elif _has_fts_table():
        try:
            return _sqlite_search(request, normalized, limit=limit, offset=offset)
        except DatabaseError:
            pass

    return _orm_search(request, normalized, limit=limit, offset=offset)


def _postgres_search(
    request: SearchRequest, normalized: str, *, limit: int, offset: int
) -> SearchResult:
    wanted = offset + limit + 1
    arm_limit = min(wanted * 4, _ARM_LIMIT_MAX)
    indexed = len(normalized) >= MIN_INDEXED_QUERY_LENGTH

    arms: list[str] = []
    params: list = []

    arms.append(
        f"""
        SELECT * FROM (
            SELECT e.id AS entry_id, {RANK_EXACT} AS arm_rank
            FROM learning_dictionaryentry AS e
            WHERE e.headword_normalized = %s {_arm_filters(request)}
            LIMIT %s
        ) AS exact_arm"""
    )
    params.append(normalized)
    params.extend(_arm_filter_params(request))
    params.append(arm_limit)

    if indexed:
        arms.append(
            f"""
        UNION ALL
        SELECT * FROM (
            SELECT e.id AS entry_id, {RANK_PREFIX} AS arm_rank
            FROM learning_dictionaryentry AS e
            WHERE e.headword_normalized LIKE %s {_arm_filters(request)}
            ORDER BY e.frequency_rank DESC, e.headword ASC, e.id ASC
            LIMIT %s
        ) AS prefix_arm"""
        )
        params.append(_escape_like(normalized) + "%")
        params.extend(_arm_filter_params(request))
        params.append(arm_limit)

        target_filter = ""
        if request.target_language:
            target_filter = "AND t.target_language = %s"
        arms.append(
            f"""
        UNION ALL
        SELECT * FROM (
            SELECT e.id AS entry_id, {RANK_TEXT} AS arm_rank
            FROM learning_dictionaryentry AS e
            LEFT JOIN learning_translation AS t ON t.entry_id = e.id
            WHERE (t.translation_text ILIKE %s {target_filter} OR e.primary_meaning_en ILIKE %s OR e.primary_meaning_ru ILIKE %s OR e.primary_meaning_uz ILIKE %s)
            {_arm_filters(request)}
            LIMIT %s
        ) AS text_arm"""
        )
        like_pattern = "%" + _escape_like(normalized) + "%"
        params.append(like_pattern)
        if request.target_language:
            params.append(request.target_language)
        params.extend([like_pattern, like_pattern, like_pattern])
        params.extend(_arm_filter_params(request))
        params.append(arm_limit)

    sql = f"""
        SELECT m.entry_id, MIN(m.arm_rank) AS best_rank
        FROM ({" ".join(arms)}) AS m
        JOIN learning_dictionaryentry AS e ON e.id = m.entry_id
        WHERE 1 = 1 {_outer_filters(request)}
        GROUP BY m.entry_id, e.frequency_rank, e.headword, e.id
        ORDER BY best_rank ASC,
                 e.frequency_rank DESC,
                 e.headword ASC,
                 e.id ASC
        LIMIT %s OFFSET %s
    """
    params.extend(_outer_filter_params(request))
    params.extend([wanted, offset])

    with connection.cursor() as cursor:
        cursor.execute(sql, params)
        rows = [row[0] for row in cursor.fetchall()]

    return SearchResult(
        entry_ids=rows[:limit],
        has_more=len(rows) > limit,
        backend=BACKEND_POSTGRES,
    )


def _sqlite_search(
    request: SearchRequest, normalized: str, *, limit: int, offset: int
) -> SearchResult:
    over_fetch = 4 if (request.target_language or request.cefr_level) else 1
    wanted = offset + limit + 1
    arm_limit = min(wanted * over_fetch, _ARM_LIMIT_MAX)
    indexed = len(normalized) >= MIN_INDEXED_QUERY_LENGTH
    fts_query = build_fts_query(normalized) if indexed else ""
    use_fts = bool(fts_query) and fts_table_has_rows()

    arms: list[str] = []
    params: list = []

    arms.append(
        f"""
        SELECT * FROM (
            SELECT e.id AS entry_id, {RANK_EXACT} AS arm_rank, 0.0 AS arm_score
            FROM learning_dictionaryentry AS e
            WHERE e.headword_normalized = %s {_arm_filters(request)}
            LIMIT %s
        )"""
    )
    params.append(normalized)
    params.extend(_arm_filter_params(request))
    params.append(arm_limit)

    if indexed:
        arms.append(
            f"""
        UNION ALL
        SELECT * FROM (
            SELECT e.id, {RANK_PREFIX}, 0.0
            FROM learning_dictionaryentry AS e
            WHERE e.headword_normalized >= %s AND e.headword_normalized < %s
                  {_arm_filters(request)}
            ORDER BY e.frequency_rank DESC, e.headword ASC, e.id ASC
            LIMIT %s
        )"""
        )
        params.extend([normalized, prefix_upper_bound(normalized)])
        params.extend(_arm_filter_params(request))
        params.append(arm_limit)

    if use_fts:
        target_filter = ""
        if request.target_language:
            target_filter = (
                "AND EXISTS (SELECT 1 FROM learning_translation AS tt "
                "WHERE tt.entry_id = f.rowid AND tt.target_language = %s)"
            )
        source_filter = "AND f.language = %s" if request.source_language else ""
        arms.append(
            f"""
        UNION ALL
        SELECT * FROM (
            SELECT f.rowid, {RANK_TEXT}, bm25({FTS_TABLE})
            FROM {FTS_TABLE} AS f
            WHERE {FTS_TABLE} MATCH %s {source_filter} {target_filter}
            ORDER BY bm25({FTS_TABLE})
            LIMIT %s
        )"""
        )
        params.append(fts_query)
        params.extend(_source_params(request))
        if request.target_language:
            params.append(request.target_language)
        params.append(arm_limit)

    sql = f"""
        SELECT m.entry_id, MIN(m.arm_rank) AS best_rank
        FROM ({" ".join(arms)}) AS m
        JOIN learning_dictionaryentry AS e ON e.id = m.entry_id
        WHERE 1 = 1 {_outer_filters(request)}
        GROUP BY m.entry_id
        ORDER BY best_rank ASC,
                 e.frequency_rank DESC,
                 e.headword ASC,
                 e.id ASC
        LIMIT %s OFFSET %s
    """
    with connection.cursor() as cursor:
        cursor.execute(sql, params + _outer_filter_params(request) + [wanted, offset])
        rows = [row[0] for row in cursor.fetchall()]

    if offset == 0 and len(rows) < limit and request.match_partial and indexed:
        rows.extend(_partial_matches(request, normalized, rows, needed=limit - len(rows) + 1))

    return SearchResult(
        entry_ids=rows[:limit],
        has_more=len(rows) > limit,
        backend=BACKEND_FTS,
    )


def _partial_matches(
    request: SearchRequest,
    normalized: str,
    existing: list[int],
    *,
    needed: int,
) -> list[int]:
    if needed <= 0:
        return []

    budget = min(
        max(needed * 2, 50),
        _ARM_LIMIT_MAX,
        max(1, settings.DICTIONARY_SEARCH_PARTIAL_ROWS),
    )
    params: list = ["%" + _escape_like(normalized) + "%"]
    params.extend(_arm_filter_params(request))
    params.extend(_outer_filter_params(request))
    params.append(budget)

    sql = f"""
        SELECT e.id
        FROM learning_dictionaryentry AS e
        WHERE e.headword_normalized LIKE %s ESCAPE '\\' {_arm_filters(request)}
              {_outer_filters(request)}
        LIMIT %s
    """
    try:
        with connection.cursor() as cursor:
            cursor.execute(sql, params)
            candidates = [row[0] for row in cursor.fetchall()]
    except DatabaseError:
        return []

    seen = set(existing)
    extra: list[int] = []
    for entry_id in candidates:
        if entry_id in seen:
            continue
        seen.add(entry_id)
        extra.append(entry_id)
        if len(extra) >= needed:
            break
    return extra


def _arm_filters(request: SearchRequest) -> str:
    filters = ""
    if request.source_language:
        filters += " AND e.language = %s" if connection.vendor == "postgresql" else " AND +e.language = %s"
    if request.part_of_speech:
        filters += " AND e.part_of_speech = %s"
    return filters


def _arm_filter_params(request: SearchRequest) -> list:
    params: list = []
    if request.source_language:
        params.append(request.source_language)
    if request.part_of_speech:
        params.append(request.part_of_speech)
    return params


def _source_params(request: SearchRequest) -> list:
    return [request.source_language] if request.source_language else []


def _outer_filters(request: SearchRequest) -> str:
    filters = ""
    if request.target_language:
        filters += (
            " AND EXISTS (SELECT 1 FROM learning_translation AS t "
            "WHERE t.entry_id = e.id AND t.target_language = %s)"
        )
    if request.cefr_level:
        filters += " AND e.cefr_level = %s"
    if request.part_of_speech:
        filters += " AND e.part_of_speech = %s"
    return filters


def _outer_filter_params(request: SearchRequest) -> list:
    params: list = []
    if request.target_language:
        params.append(request.target_language)
    if request.cefr_level:
        params.append(request.cefr_level)
    if request.part_of_speech:
        params.append(request.part_of_speech)
    return params


def _escape_like(value: str) -> str:
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


def _orm_search(
    request: SearchRequest, normalized: str, *, limit: int, offset: int
) -> SearchResult:
    matches = (
        Q(headword_normalized=normalized)
        | Q(headword_normalized__startswith=normalized)
        | Q(headword_normalized__contains=normalized)
        | Q(primary_meaning_en__icontains=normalized)
        | Q(primary_meaning_ru__icontains=normalized)
        | Q(primary_meaning_uz__icontains=normalized)
        | Q(alternative_spellings__icontains=normalized)
        | Q(
            Exists(
                Translation.objects.filter(
                    entry=OuterRef("pk"), translation_text__icontains=normalized
                )
            )
        )
    )

    queryset = DictionaryEntry.objects.filter(matches)
    if request.source_language:
        queryset = queryset.filter(language=request.source_language)
    if request.target_language:
        queryset = queryset.filter(
            Exists(
                Translation.objects.filter(
                    entry=OuterRef("pk"), target_language=request.target_language
                )
            )
        )
    if request.part_of_speech:
        queryset = queryset.filter(part_of_speech=request.part_of_speech)
    if request.cefr_level:
        queryset = queryset.filter(cefr_level=request.cefr_level)

    queryset = queryset.annotate(
        match_rank=Case(
            When(headword_normalized=normalized, then=Value(RANK_EXACT)),
            When(headword_normalized__startswith=normalized, then=Value(RANK_PREFIX)),
            default=Value(RANK_TEXT),
            output_field=IntegerField(),
        )
    ).order_by("match_rank", "-frequency_rank", "headword", "id")

    page = list(queryset.values_list("id", flat=True)[offset : offset + limit + 1])
    return SearchResult(
        entry_ids=page[:limit],
        has_more=len(page) > limit,
        backend=BACKEND_ORM,
    )
