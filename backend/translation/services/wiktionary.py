"""Enrich catalog entries with professional data from a Wiktionary dump.

The kaikki.org dump carries what a printed dictionary shows: part of speech,
IPA transcription, plural/comparative forms, and translations tagged with
grammatical gender and number.
"""

from __future__ import annotations

import gzip
import json
from collections.abc import Iterator, Sequence
from dataclasses import dataclass, field
from pathlib import Path

from learning.models import DictionaryEntry, Translation

from .lexicon import LexiconImportError, is_valid_headword, normalize_headword
from .professional import (
    POS_NOUN,
    context_note,
    english_article,
    normalize_gender,
    normalize_number,
    normalize_pos,
    primary_meaning_from_translations,
)

LANGUAGE_NAMES = {"Russian": "ru", "Uzbek": "uz"}
GENDER_TAGS = {"masculine", "feminine", "neuter", "common"}
NUMBER_TAGS = {"plural", "singular", "uncountable"}
_COMBINING_MARKS = dict.fromkeys(range(0x0300, 0x0370))


def clean_word(value: str) -> str:
    """Drop combining stress marks so ``слова́рь`` matches a plain ``словарь``."""
    text = str(value or "").translate(_COMBINING_MARKS)
    return " ".join(text.split())


@dataclass
class WiktionaryEntry:
    headword: str
    pos: str = ""
    ipa: str = ""
    plural: str = ""
    comparative: str = ""
    superlative: str = ""
    glosses: list[str] = field(default_factory=list)
    translations: dict[str, list[dict]] = field(default_factory=lambda: {"ru": [], "uz": []})

    def has_translations(self) -> bool:
        return any(self.translations.get(language) for language in ("ru", "uz"))


def parse_payload(payload: dict) -> WiktionaryEntry | None:
    headword = str(payload.get("word") or "").strip()
    if not headword or not is_valid_headword(headword):
        return None

    entry = WiktionaryEntry(headword=headword)
    entry.pos = normalize_pos(payload.get("pos"))

    for sound in payload.get("sounds") or []:
        ipa = str(sound.get("ipa") or "").strip()
        if ipa and not entry.ipa:
            entry.ipa = ipa
            break

    for form in payload.get("forms") or []:
        value = str(form.get("form") or "").strip()
        if not value or value == headword or len(value) > 40:
            continue
        tags = {str(tag).lower() for tag in form.get("tags") or []}
        if "plural" in tags and not entry.plural:
            entry.plural = value
        elif "comparative" in tags and not entry.comparative:
            entry.comparative = value
        elif "superlative" in tags and not entry.superlative:
            entry.superlative = value

    for sense in payload.get("senses") or []:
        candidates = sense.get("glosses") or []
        if not candidates and sense.get("gloss"):
            candidates = [sense["gloss"]]
        for gloss in candidates:
            text = str(gloss or "").strip()
            if text and text not in entry.glosses:
                entry.glosses.append(text)

    for item in payload.get("translations") or []:
        language = LANGUAGE_NAMES.get(str(item.get("lang") or ""))
        if not language:
            continue
        word = clean_word(item.get("word"))
        if not word:
            continue
        tags = {str(tag).lower() for tag in item.get("tags") or []}
        entry.translations[language].append(
            {
                "word": word,
                "roman": clean_word(item.get("roman")),
                "gender": next(
                    (normalize_gender(tag) for tag in tags if tag in GENDER_TAGS), ""
                ),
                "number": next(
                    (normalize_number(tag) for tag in tags if tag in NUMBER_TAGS), ""
                ),
                "pos": normalize_pos(item.get("pos")) or entry.pos,
                "cyrillic": "cyrillic" in tags,
            }
        )

    for language, items in entry.translations.items():
        if not items:
            continue
        latin = [item for item in items if not item["cyrillic"]]
        entry.translations[language] = (latin or items)[:8]

    if not entry.glosses and not entry.has_translations():
        return None
    return entry


def iter_wiktionary(path: str | Path) -> Iterator[dict]:
    source = Path(path)
    if not source.exists():
        raise LexiconImportError(f"Fayl topilmadi: {source}")
    opener = gzip.open if source.suffix.lower() == ".gz" else open
    with opener(source, "rt", encoding="utf-8", errors="replace") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                yield json.loads(line)
            except json.JSONDecodeError:
                continue


def best_entry(entries: Sequence[WiktionaryEntry]) -> WiktionaryEntry:
    """Pick the record a dictionary would print as the headword entry."""
    ranked = sorted(
        entries,
        key=lambda item: (
            len(item.translations.get("ru", [])) + len(item.translations.get("uz", [])),
            len(item.glosses),
            1 if item.ipa else 0,
        ),
        reverse=True,
    )
    return ranked[0]


@dataclass
class EnrichStats:
    scanned: int = 0
    matched: int = 0
    updated_entries: int = 0
    created_translations: int = 0
    replaced_translations: int = 0
    russian: int = 0
    uzbek: int = 0

    def as_dict(self) -> dict:
        return {
            "scanned": self.scanned,
            "matched": self.matched,
            "updatedEntries": self.updated_entries,
            "createdTranslations": self.created_translations,
            "replacedTranslations": self.replaced_translations,
            "russian": self.russian,
            "uzbek": self.uzbek,
        }


def enrich_from_wiktionary(
    path: str | Path,
    *,
    batch_size: int = 1500,
    only_missing: bool = True,
    max_lines: int = 0,
    progress=None,
) -> EnrichStats:
    """Walk the dump once and enrich every matching catalog entry."""
    stats = EnrichStats()
    pending: dict[str, list[WiktionaryEntry]] = {}

    def flush() -> None:
        if not pending:
            return
        _apply_batch(pending, stats, only_missing=only_missing)
        pending.clear()
        if progress:
            progress(stats)

    for payload in iter_wiktionary(path):
        stats.scanned += 1
        if max_lines and stats.scanned > max_lines:
            break
        parsed = parse_payload(payload)
        if parsed is None:
            continue
        pending.setdefault(normalize_headword(parsed.headword), []).append(parsed)
        if len(pending) >= batch_size:
            flush()
    flush()
    if progress:
        progress(stats)
    return stats


def _apply_batch(
    pending: dict[str, list[WiktionaryEntry]], stats: EnrichStats, *, only_missing: bool
) -> None:
    headwords = list(pending.keys())
    entries: dict[str, DictionaryEntry] = {}
    for chunk in _chunks(headwords, 900):
        for entry in DictionaryEntry.objects.filter(
            language="en", headword_normalized__in=chunk
        ):
            entries[entry.headword_normalized] = entry
    if not entries:
        return

    entry_updates: list[DictionaryEntry] = []
    entry_ids = [entry.id for entry in entries.values()]
    existing_keys = set()
    for chunk in _chunks(entry_ids, 900):
        existing_keys.update(
            Translation.objects.filter(entry_id__in=chunk).values_list(
                "entry_id", "target_language", "translation_text"
            )
        )
    new_translations: list[Translation] = []

    for headword, records in pending.items():
        entry = entries.get(headword)
        if entry is None:
            continue
        stats.matched += 1
        chosen = best_entry(records)

        changed: list[str] = []
        if chosen.ipa and not (entry.phonetic_ipa or "").strip():
            entry.phonetic_ipa = chosen.ipa[:200]
            changed.append("phonetic_ipa")
        if chosen.pos and (not entry.part_of_speech or entry.part_of_speech == "noun"):
            entry.part_of_speech = chosen.pos[:40]
            changed.append("part_of_speech")
        if chosen.plural and not entry.plural_form:
            entry.plural_form = chosen.plural[:200]
            changed.append("plural_form")
        if chosen.comparative and not entry.comparative_form:
            entry.comparative_form = chosen.comparative[:200]
            changed.append("comparative_form")
        if chosen.superlative and not entry.superlative_form:
            entry.superlative_form = chosen.superlative[:200]
            changed.append("superlative_form")
        article = english_article(entry.phonetic_ipa, entry.part_of_speech)
        if article and not entry.article:
            entry.article = article[:8]
            changed.append("article")
        elif not article and entry.article:
            entry.article = ""
            changed.append("article")

        for language in ("ru", "uz"):
            items = chosen.translations.get(language) or []
            if not items:
                continue
            primary_field = f"primary_meaning_{language}"
            if only_missing and getattr(entry, primary_field).strip():
                continue

            note = context_note(
                language,
                pos=items[0].get("pos") or chosen.pos,
                gender=items[0].get("gender") or "",
                number=items[0].get("number") or "",
            )
            formatted = primary_meaning_from_translations(
                [item["word"] for item in items], note
            )
            if formatted:
                setattr(entry, primary_field, formatted)
                changed.append(primary_field)
                if language == "ru":
                    stats.russian += 1
                else:
                    stats.uzbek += 1

            for item in items[:6]:
                key = (entry.id, language, item["word"][:300])
                if key in existing_keys:
                    continue
                existing_keys.add(key)
                new_translations.append(
                    Translation(
                        entry_id=entry.id,
                        target_language=language,
                        translation_text=item["word"][:300],
                        transliteration=item.get("roman", "")[:200],
                        context_note=context_note(
                            language,
                            pos=item.get("pos") or chosen.pos,
                            gender=item.get("gender") or "",
                            number=item.get("number") or "",
                        ),
                        part_of_speech=(item.get("pos") or chosen.pos)[:40],
                        gender=item.get("gender", "")[:8],
                        number=item.get("number", "")[:16],
                        quality=Translation.Quality.WIKTIONARY,
                    )
                )

        if changed:
            entry_updates.append(entry)

    if entry_updates:
        DictionaryEntry.objects.bulk_update(
            entry_updates,
            [
                "phonetic_ipa",
                "part_of_speech",
                "plural_form",
                "comparative_form",
                "superlative_form",
                "article",
                "primary_meaning_ru",
                "primary_meaning_uz",
            ],
            batch_size=500,
        )
        stats.updated_entries += len(entry_updates)
    if new_translations:
        Translation.objects.bulk_create(new_translations, batch_size=1000)
        stats.created_translations += len(new_translations)

def _chunks(values, size):
    for start in range(0, len(values), size):
        yield values[start : start + size]
