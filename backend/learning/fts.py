"""SQLite FTS5 index definition for dictionary search.

The DDL lives here rather than inside a single migration so the rebuild
command and the migration always produce exactly the same index and triggers.

Index contents
--------------
One document per ``DictionaryEntry`` row (the rowid is the entry id) with:

``headword``      normalised headword
``meanings``      the three primary meaning columns plus alternative spellings
``translations``  every translation text for the entry
``language``      entry language, stored but not tokenised so it can filter

Triggers keep the index current for both entry and translation writes. Search
falls back to the ORM when this table is missing, so the feature degrades
instead of breaking.
"""

from __future__ import annotations

FTS_TABLE = "dictionary_search"

TABLE_SQL = f"""
CREATE VIRTUAL TABLE IF NOT EXISTS {FTS_TABLE} USING fts5(
    headword,
    meanings,
    translations,
    language UNINDEXED,
    tokenize = 'unicode61 remove_diacritics 2'
)
"""

_ROW_SELECT = f"""
SELECT e.id,
       e.headword_normalized,
       coalesce(e.primary_meaning_en, '') || ' ' ||
       coalesce(e.primary_meaning_ru, '') || ' ' ||
       coalesce(e.primary_meaning_uz, '') || ' ' ||
       coalesce(e.alternative_spellings, ''),
       (SELECT coalesce(group_concat(t.translation_text, ' '), '')
          FROM learning_translation AS t
         WHERE t.entry_id = e.id),
       e.language
FROM learning_dictionaryentry AS e
"""

POPULATE_SQL = (
    f"INSERT INTO {FTS_TABLE}(rowid, headword, meanings, translations, language)"
    f"{_ROW_SELECT}"
)

_REFRESH_ONE_SQL = (
    f"INSERT INTO {FTS_TABLE}(rowid, headword, meanings, translations, language)"
    f"{_ROW_SELECT} WHERE e.id = {{entry_id}}"
)


def _refresh_block(*entry_ids: str) -> str:
    statements = []
    for entry_id in entry_ids:
        statements.append(f"DELETE FROM {FTS_TABLE} WHERE rowid = {entry_id};")
        statements.append(_REFRESH_ONE_SQL.format(entry_id=entry_id) + ";")
    return "\n    ".join(statements)


def _trigger(name: str, event: str, table: str, *entry_ids: str) -> str:
    return f"""
CREATE TRIGGER IF NOT EXISTS {name}
AFTER {event} ON {table}
FOR EACH ROW
BEGIN
    {_refresh_block(*entry_ids)}
END
"""


TRIGGERS = (
    _trigger("dictionary_search_entry_ai", "INSERT", "learning_dictionaryentry", "new.id"),
    _trigger("dictionary_search_entry_au", "UPDATE", "learning_dictionaryentry", "new.id"),
    _trigger("dictionary_search_entry_ad", "DELETE", "learning_dictionaryentry", "old.id"),
    _trigger("dictionary_search_translation_ai", "INSERT", "learning_translation", "new.entry_id"),
    # A translation may also move to another entry, which invalidates both rows.
    _trigger(
        "dictionary_search_translation_au",
        "UPDATE",
        "learning_translation",
        "old.entry_id",
        "new.entry_id",
    ),
    _trigger("dictionary_search_translation_ad", "DELETE", "learning_translation", "old.entry_id"),
)

TRIGGER_NAMES = (
    "dictionary_search_entry_ai",
    "dictionary_search_entry_au",
    "dictionary_search_entry_ad",
    "dictionary_search_translation_ai",
    "dictionary_search_translation_au",
    "dictionary_search_translation_ad",
)


def create_statements() -> list[str]:
    return [TABLE_SQL, *TRIGGERS]


def drop_statements() -> list[str]:
    return [
        *(f"DROP TRIGGER IF EXISTS {name}" for name in TRIGGER_NAMES),
        f"DROP TABLE IF EXISTS {FTS_TABLE}",
    ]


def rebuild_statements() -> list[str]:
    return [f"DELETE FROM {FTS_TABLE}", POPULATE_SQL]
