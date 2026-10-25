"""Import FreeDict's English-Russian dictionary into the catalog.

FreeDict's ``eng-rus`` is generated from Wiktionary via DBnary/WikDict and ships
as TEI XML with pronunciation, part of speech, grammatical gender and sense
definitions, so it fits the professional schema directly.
"""

from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from collections.abc import Iterator
from dataclasses import dataclass, field
from pathlib import Path

from django.utils import timezone

from learning.models import DictionaryEntry, Translation

from .lexicon import LexiconImportError, is_valid_headword, normalize_headword
from .professional import (
    context_note,
    english_article,
    normalize_gender,
    normalize_pos,
    primary_meaning_from_translations,
)
from .wiktionary import clean_word

TEI_NS = "{http://www.tei-c.org/ns/1.0}"
XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"
_COMBINING = re.compile(r"[\u0300-\u036f]")


@dataclass
class FreedictEntry:
    headword: str
    pos: str = ""
    ipa: str = ""
    gender: str = ""
    translations: list[str] = field(default_factory=list)
    definitions: list[str] = field(default_factory=list)
    plural: str = ""


@dataclass
class FreedictStats:
    scanned: int = 0
    matched: int = 0
    updated_entries: int = 0
    created_translations: int = 0

    def as_dict(self) -> dict:
        return {
            "scanned": self.scanned,
            "matched": self.matched,
            "updatedEntries": self.updated_entries,
            "createdTranslations": self.created_translations,
        }


def _text(element) -> str:
    return " ".join((element.text or "").split()) if element is not None else ""


def parse_entry(element) -> FreedictEntry | None:
    orth = element.find(f"{TEI_NS}form/{TEI_NS}orth")
    headword = clean_word(_text(orth))
    if not headword or not is_valid_headword(headword):
        return None

    entry = FreedictEntry(headword=headword)

    for pron in element.findall(f"{TEI_NS}form/{TEI_NS}pron"):
        value = _text(pron)
        if value and not entry.ipa:
            entry.ipa = value

    pos = element.find(f"{TEI_NS}gramGrp/{TEI_NS}pos")
    entry.pos = normalize_pos(_text(pos))

    for sense in element.iter(f"{TEI_NS}sense"):
        if not entry.ipa:
            for pron in sense.iter(f"{TEI_NS}pron"):
                value = _text(pron)
                if value:
                    entry.ipa = value
                    break
        if not entry.gender:
            gen = sense.find(f".//{TEI_NS}gen")
            entry.gender = normalize_gender(_text(gen))
        for definition in sense.iter(f"{TEI_NS}def"):
            value = _text(definition)
            if value and value not in entry.definitions:
                entry.definitions.append(value)
        for cit in sense.iter(f"{TEI_NS}cit"):
            if cit.get("type") != "trans" or cit.get(XML_LANG) != "ru":
                continue
            for quote in cit.iter(f"{TEI_NS}quote"):
                value = _COMBINING.sub("", _text(quote))
                if value and value not in entry.translations:
                    entry.translations.append(value)

    if not entry.translations:
        return None
    return entry


def iter_entries(path: str | Path) -> Iterator[FreedictEntry]:
    source = Path(path)
    if not source.exists():
        raise LexiconImportError(f"Fayl topilmadi: {source}")
    context = ET.iterparse(source, events=("end",))
    for _, element in context:
        if element.tag != f"{TEI_NS}entry":
            continue
        parsed = parse_entry(element)
        element.clear()
        if parsed is not None:
            yield parsed


def enrich_from_freedict(
    path: str | Path,
    *,
    batch_size: int = 800,
    only_missing: bool = True,
    progress=None,
) -> FreedictStats:
    stats = FreedictStats()
    pending: dict[str, list[FreedictEntry]] = {}

    def flush() -> None:
        if not pending:
            return
        _apply_batch(pending, stats, only_missing=only_missing)
        pending.clear()
        if progress:
            progress(stats)

    for entry in iter_entries(path):
        stats.scanned += 1
        pending.setdefault(normalize_headword(entry.headword), []).append(entry)
        if len(pending) >= batch_size:
            flush()
    flush()
    if progress:
        progress(stats)
    return stats


def _apply_batch(
    pending: dict[str, list[FreedictEntry]],
    stats: FreedictStats,
    *,
    only_missing: bool,
) -> None:
    headwords = list(pending.keys())
    entries: dict[str, DictionaryEntry] = {}
    for start in range(0, len(headwords), 900):
        chunk = headwords[start : start + 900]
        for entry in DictionaryEntry.objects.filter(
            language="en", headword_normalized__in=chunk
        ):
            entries[entry.headword_normalized] = entry
    if not entries:
        return

    known = set()
    entry_ids = [entry.id for entry in entries.values()]
    for start in range(0, len(entry_ids), 900):
        known.update(
            Translation.objects.filter(
                entry_id__in=entry_ids[start : start + 900], target_language="ru"
            ).values_list("entry_id", "translation_text")
        )

    updates: list[DictionaryEntry] = []
    created: list[Translation] = []

    for headword, records in pending.items():
        entry = entries.get(headword)
        if entry is None:
            continue
        stats.matched += 1
        chosen = max(records, key=lambda item: (len(item.translations), len(item.definitions)))

        changed: list[str] = []
        if chosen.ipa and not entry.phonetic_ipa.strip():
            entry.phonetic_ipa = chosen.ipa[:200]
            changed.append("phonetic_ipa")
        if chosen.pos and (not entry.part_of_speech or entry.part_of_speech == "noun"):
            entry.part_of_speech = chosen.pos[:40]
            changed.append("part_of_speech")
        article = english_article(entry.phonetic_ipa, entry.part_of_speech)
        if article and not entry.article:
            entry.article = article[:8]
            changed.append("article")
        elif not article and entry.article:
            entry.article = ""
            changed.append("article")

        if only_missing and entry.primary_meaning_ru.strip():
            if changed:
                updates.append(entry)
            continue

        note = context_note("ru", pos=chosen.pos, gender=chosen.gender)
        formatted = primary_meaning_from_translations(chosen.translations, note)
        if formatted:
            entry.primary_meaning_ru = formatted
            changed.append("primary_meaning_ru")

        for value in chosen.translations[:6]:
            if (entry.id, value[:300]) in known:
                continue
            known.add((entry.id, value[:300]))
            created.append(
                Translation(
                    entry_id=entry.id,
                    target_language="ru",
                    translation_text=value[:300],
                    context_note=note,
                    part_of_speech=chosen.pos[:40],
                    gender=chosen.gender[:8],
                    quality=Translation.Quality.WIKTIONARY,
                )
            )

        if changed:
            updates.append(entry)

    if updates:
        now = timezone.now()
        for entry in updates:
            entry.updated_at = now
        DictionaryEntry.objects.bulk_update(
            updates,
            ["phonetic_ipa", "part_of_speech", "article", "primary_meaning_ru", "updated_at"],
            batch_size=500,
        )
        stats.updated_entries += len(updates)
    if created:
        Translation.objects.bulk_create(created, batch_size=1000)
        stats.created_translations += len(created)
