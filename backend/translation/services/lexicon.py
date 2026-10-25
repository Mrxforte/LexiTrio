"""Import English headwords into the learning catalog from WordNet, kaikki or files."""

from __future__ import annotations

import csv
import gzip
import io
import json
import re
from collections.abc import Iterable, Iterator, Sequence
from dataclasses import dataclass
from pathlib import Path

from learning.models import DictionaryEntry, Translation, WordMeaning

MAX_SENSES_PER_ENTRY = 3
MAX_TRANSLATION_WORDS = 6

_POS_MAP = {"n": "noun", "v": "verb", "a": "adjective", "s": "adjective", "r": "adverb"}
_HEADWORD_RE = re.compile(r"^[a-zà-öø-ÿ][a-zà-öø-ÿ'\- ]{0,59}$", re.IGNORECASE)
_LANGUAGE_NAMES = {
    "Russian": "ru",
    "Uzbek": "uz",
}

_FIELD_ALIASES = {
    "headword": ("headword", "word", "lemma", "term", "english", "en", "so'z", "soz"),
    "part_of_speech": ("part_of_speech", "pos", "type", "turkum"),
    "primary_meaning_en": (
        "primary_meaning_en",
        "definition_en",
        "definition",
        "meaning",
        "meaning_en",
        "gloss",
        "tarif",
    ),
    "primary_meaning_ru": (
        "primary_meaning_ru",
        "definition_ru",
        "meaning_ru",
        "translation_ru",
        "ru",
        "russian",
    ),
    "primary_meaning_uz": (
        "primary_meaning_uz",
        "definition_uz",
        "meaning_uz",
        "translation_uz",
        "uz",
        "uzbek",
    ),
    "cefr_level": ("cefr_level", "cefr", "level", "daraja"),
    "frequency_rank": ("frequency_rank", "frequency", "freq", "rank"),
    "phonetic_ipa": ("phonetic_ipa", "ipa", "phonetic"),
    "source": ("source", "manba"),
}


class LexiconImportError(Exception):
    """Raised when a lexicon source cannot be read."""


@dataclass
class ImportStats:
    seen: int = 0
    created: int = 0
    skipped: int = 0
    meanings: int = 0
    translations: int = 0

    def as_dict(self) -> dict:
        return {
            "seen": self.seen,
            "created": self.created,
            "skipped": self.skipped,
            "meanings": self.meanings,
            "translations": self.translations,
        }


def normalize_headword(value) -> str:
    text = str(value or "").strip().lower().replace("’", "'").replace("–", "-")
    return " ".join(text.split())


def is_valid_headword(value, *, allow_multiword: bool = True, max_words: int = 4) -> bool:
    normalized = normalize_headword(value)
    if not normalized or not _HEADWORD_RE.match(normalized):
        return False
    words = normalized.split(" ")
    if not allow_multiword and len(words) > 1:
        return False
    return len(words) <= max_words


def estimate_cefr(zipf_score: float) -> str:
    if not zipf_score:
        return "B1"
    if zipf_score >= 6.0:
        return "A1"
    if zipf_score >= 5.4:
        return "A2"
    if zipf_score >= 4.8:
        return "B1"
    if zipf_score >= 3.9:
        return "B2"
    if zipf_score >= 2.9:
        return "C1"
    return "C2"


def import_records(
    records: Iterable[dict],
    *,
    chunk_size: int = 2000,
    allow_multiword: bool = True,
    max_words: int = 4,
    progress=None,
) -> ImportStats:
    """Persist lexicon records, skipping headwords that already exist."""
    stats = ImportStats()
    seen = set(
        DictionaryEntry.objects.filter(language="en").values_list(
            "headword_normalized", flat=True
        )
    )
    buffer: list[dict] = []

    def flush() -> None:
        if not buffer:
            return
        created = _persist_records(buffer, stats)
        stats.created += created
        buffer.clear()

    for record in records:
        stats.seen += 1
        headword = record.get("headword")
        if not is_valid_headword(
            headword, allow_multiword=allow_multiword, max_words=max_words
        ):
            stats.skipped += 1
            continue
        normalized = normalize_headword(headword)
        if normalized in seen:
            stats.skipped += 1
            continue
        seen.add(normalized)
        record["headword_normalized"] = normalized
        buffer.append(record)
        if len(buffer) >= chunk_size:
            flush()
            if progress:
                progress(stats)
    flush()
    if progress:
        progress(stats)
    return stats


def _persist_records(buffer: Sequence[dict], stats: ImportStats) -> int:
    entries = []
    for record in buffer:
        headword = str(record["headword"]).strip()
        entries.append(
            DictionaryEntry(
                headword=headword[:200],
                headword_normalized=record["headword_normalized"][:200],
                language=str(record.get("language") or "en")[:12],
                phonetic_ipa=str(record.get("phonetic_ipa") or "")[:200],
                part_of_speech=str(record.get("part_of_speech") or "noun")[:40],
                cefr_level=str(record.get("cefr_level") or "B1")[:8],
                frequency_rank=_as_int(record.get("frequency_rank")),
                lemma=record["headword_normalized"][:200],
                source=str(record.get("source") or "LexiTrio import")[:200],
                primary_meaning_en=str(record.get("primary_meaning_en") or ""),
                primary_meaning_ru=str(record.get("primary_meaning_ru") or ""),
                primary_meaning_uz=str(record.get("primary_meaning_uz") or ""),
                verified=True,
            )
        )

    DictionaryEntry.objects.bulk_create(entries)

    id_map: dict[str, int] = {}
    if any(entry.pk is None for entry in entries):
        id_map = _existing_id_map([record["headword_normalized"] for record in buffer])
    else:
        id_map = {
            record["headword_normalized"]: entry.pk
            for record, entry in zip(buffer, entries)
        }

    meanings: list[WordMeaning] = []
    translations: list[Translation] = []
    translation_length = Translation._meta.get_field("translation_text").max_length or 300

    for record in buffer:
        entry_id = id_map.get(record["headword_normalized"])
        if not entry_id:
            continue
        for sense_number, sense in enumerate(
            (record.get("senses") or [])[:MAX_SENSES_PER_ENTRY], start=1
        ):
            definition = str(sense.get("definition_en") or "").strip()
            if not definition:
                continue
            meanings.append(
                WordMeaning(
                    entry_id=entry_id,
                    sense_number=sense_number,
                    definition_en=definition,
                    definition_ru=str(sense.get("definition_ru") or ""),
                )
            )
        for language, values in (record.get("translations") or {}).items():
            for value in list(dict.fromkeys(values))[:MAX_TRANSLATION_WORDS]:
                text = str(value).strip()
                if not text:
                    continue
                translations.append(
                    Translation(
                        entry_id=entry_id,
                        target_language=str(language)[:12],
                        translation_text=text[:translation_length],
                    )
                )

    if meanings:
        WordMeaning.objects.bulk_create(meanings)
        stats.meanings += len(meanings)
    if translations:
        Translation.objects.bulk_create(translations)
        stats.translations += len(translations)
    return len(entries)


def _existing_id_map(headwords: Sequence[str]) -> dict[str, int]:
    mapping: dict[str, int] = {}
    for start in range(0, len(headwords), 400):
        chunk = headwords[start : start + 400]
        rows = DictionaryEntry.objects.filter(
            headword_normalized__in=chunk, language="en"
        ).values("id", "headword_normalized")
        for row in rows:
            mapping[row["headword_normalized"]] = row["id"]
    return mapping


def _as_int(value) -> int:
    try:
        return max(0, int(float(value)))
    except (TypeError, ValueError):
        return 0


def wordnet_records(
    *,
    limit: int = 100000,
    max_senses: int = MAX_SENSES_PER_ENTRY,
    allow_multiword: bool = True,
    max_words: int = 4,
    translation_language: str = "rus",
    target_language: str = "ru",
) -> Iterator[dict]:
    """Yield English headwords from WordNet with definitions and OMW translations."""
    wordnet = _load_wordnet()
    words = _collect_wordnet_lemmas(
        wordnet,
        allow_multiword=allow_multiword,
        max_words=max_words,
    )
    ranked = _rank_words(words)

    produced = 0
    for score, word in ranked:
        if limit and produced >= limit:
            break
        synsets = _ranked_synsets(wordnet, word, max_senses)
        if not synsets:
            continue

        senses = []
        translations: list[str] = []
        for synset in synsets:
            definition = (synset.definition() or "").strip()
            if not definition:
                continue
            senses.append({"definition_en": definition})
            if translation_language:
                translations.extend(_lemma_names(synset, translation_language))
        if not senses:
            continue

        unique_translations = list(dict.fromkeys(translations))
        yield {
            "headword": word,
            "part_of_speech": _POS_MAP.get(synsets[0].pos(), "noun"),
            "cefr_level": estimate_cefr(score),
            "frequency_rank": int(round(score * 100)),
            "primary_meaning_en": senses[0]["definition_en"],
            "primary_meaning_ru": ", ".join(unique_translations)
            if target_language == "ru"
            else "",
            "senses": senses,
            "translations": {target_language: unique_translations}
            if unique_translations
            else {},
            "source": "WordNet 3.0 + Open Multilingual WordNet"
            if unique_translations
            else "WordNet 3.0",
        }
        produced += 1


def _load_wordnet():
    try:
        from nltk.corpus import wordnet
    except ImportError as exc:  # pragma: no cover - dependency guard
        raise LexiconImportError(
            "WordNet importi uchun 'nltk' kerak: pip install -r requirements.txt"
        ) from exc
    try:
        wordnet.ensure_loaded()
    except LookupError as exc:
        raise LexiconImportError(
            "WordNet ma'lumotlari topilmadi. Avval: "
            "python -m nltk.downloader wordnet omw-1.4"
        ) from exc
    return wordnet


def _collect_wordnet_lemmas(
    wordnet,
    *,
    allow_multiword: bool,
    max_words: int,
) -> set[str]:
    words: set[str] = set()
    for synset in wordnet.all_synsets():
        for lemma in synset.lemmas():
            word = lemma.name().replace("_", " ").strip().lower()
            if is_valid_headword(
                word, allow_multiword=allow_multiword, max_words=max_words
            ):
                words.add(word)
    return words


def _rank_words(words: set[str]) -> list[tuple[float, str]]:
    try:
        from wordfreq import zipf_frequency
    except ImportError:
        return [(0.0, word) for word in sorted(words)]

    scored = [(float(zipf_frequency(word, "en")), word) for word in words]
    scored.sort(key=lambda item: (-item[0], item[1]))
    return scored


def _ranked_synsets(wordnet, word: str, max_senses: int) -> list:
    """Order a word's synsets by how often the word is used in each sense."""
    lookup = word.replace(" ", "_")
    scored = []
    for order, synset in enumerate(wordnet.synsets(lookup)):
        usage = 0
        for lemma in synset.lemmas():
            if lemma.name().replace("_", " ").strip().lower() == word:
                usage = max(usage, int(lemma.count()))
        scored.append((usage, -order, synset))
    scored.sort(key=lambda item: (-item[0], -item[1]))
    return [synset for _, _, synset in scored[: max(1, max_senses)]]


def _lemma_names(synset, language: str) -> list[str]:
    try:
        names = synset.lemma_names(language)
    except Exception:  # noqa: BLE001 - missing OMW data must not abort the import
        return []
    return [str(name).replace("_", " ").strip() for name in names if str(name).strip()]


def kaikki_records(path: str | Path, *, limit: int = 0) -> Iterator[dict]:
    """Stream records from a kaikki.org (Wiktionary) JSONL dump."""
    produced = 0
    with _open_text(path) as handle:
        for line in handle:
            if limit and produced >= limit:
                break
            line = line.strip()
            if not line:
                continue
            try:
                payload = json.loads(line)
            except json.JSONDecodeError:
                continue
            record = _kaikki_record(payload)
            if record is None:
                continue
            produced += 1
            yield record


def _kaikki_record(payload: dict) -> dict | None:
    headword = str(payload.get("word") or "").strip()
    if not is_valid_headword(headword):
        return None

    senses = []
    for sense in (payload.get("senses") or [])[:MAX_SENSES_PER_ENTRY]:
        candidates = sense.get("glosses") or []
        if not candidates and sense.get("gloss"):
            candidates = [sense["gloss"]]
        gloss = str(candidates[0] if candidates else "").strip()
        if gloss:
            senses.append({"definition_en": gloss})
    if not senses:
        return None

    translations: dict[str, list[str]] = {}
    for item in payload.get("translations") or []:
        language = _LANGUAGE_NAMES.get(str(item.get("lang") or ""))
        word = str(item.get("word") or "").strip()
        if language and word:
            translations.setdefault(language, []).append(word)

    ipa = ""
    for sound in payload.get("sounds") or []:
        if sound.get("ipa"):
            ipa = str(sound["ipa"])
            break

    return {
        "headword": headword,
        "part_of_speech": str(payload.get("pos") or "noun"),
        "cefr_level": "B1",
        "frequency_rank": 0,
        "phonetic_ipa": ipa,
        "primary_meaning_en": senses[0]["definition_en"],
        "primary_meaning_ru": ", ".join(dict.fromkeys(translations.get("ru", []))),
        "primary_meaning_uz": ", ".join(dict.fromkeys(translations.get("uz", []))),
        "senses": senses,
        "translations": translations,
        "source": "Wiktionary (kaikki.org)",
    }


def file_records(path: str | Path, *, limit: int = 0) -> Iterator[dict]:
    """Read headwords from JSONL, JSON, CSV/TSV or a plain word list."""
    source = Path(path)
    extension = "".join(source.suffixes).lower()
    if extension in {".jsonl", ".jsonl.gz", ".ndjson"}:
        iterator = _jsonl_records(source)
    elif extension in {".json", ".json.gz"}:
        iterator = _json_records(source)
    elif extension in {".csv", ".tsv"}:
        iterator = _delimited_records(source)
    else:
        iterator = _wordlist_records(source)

    produced = 0
    for record in iterator:
        if limit and produced >= limit:
            break
        if record:
            produced += 1
            yield record


def _jsonl_records(path: Path) -> Iterator[dict]:
    with _open_text(path) as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                payload = json.loads(line)
            except json.JSONDecodeError:
                continue
            record = _generic_record(payload)
            if record:
                yield record


def _json_records(path: Path) -> Iterator[dict]:
    with _open_text(path) as handle:
        payload = json.load(handle)
    if isinstance(payload, dict):
        for key in ("entries", "words", "dictionary_entries", "items"):
            if isinstance(payload.get(key), list):
                payload = payload[key]
                break
    if not isinstance(payload, list):
        raise LexiconImportError("JSON fayl ro'yxat yoki {'entries': [...]} bo'lishi kerak.")
    for item in payload:
        record = _generic_record(item)
        if record:
            yield record


def _delimited_records(path: Path) -> Iterator[dict]:
    delimiter = "\t" if path.suffix.lower() == ".tsv" else ","
    with _open_text(path) as handle:
        reader = csv.DictReader(handle, delimiter=delimiter)
        for row in reader:
            record = _generic_record(row)
            if record:
                yield record


def _wordlist_records(path: Path) -> Iterator[dict]:
    with _open_text(path) as handle:
        for line in handle:
            word = line.strip()
            if not word or word.startswith("#"):
                continue
            yield {
                "headword": word,
                "part_of_speech": "noun",
                "cefr_level": "B1",
                "primary_meaning_en": "",
                "senses": [],
                "translations": {},
                "source": f"Word list ({path.name})",
            }


def _generic_record(row) -> dict | None:
    if isinstance(row, str):
        row = {"headword": row}
    if not isinstance(row, dict):
        return None

    lowered = {str(key).strip().lower(): value for key, value in row.items() if key}
    record: dict = {}
    for field, aliases in _FIELD_ALIASES.items():
        for alias in aliases:
            value = lowered.get(alias)
            if value not in (None, ""):
                record[field] = str(value).strip()
                break

    headword = record.get("headword")
    if not headword or not is_valid_headword(headword):
        return None

    definition = record.get("primary_meaning_en", "")
    return {
        "headword": headword,
        "part_of_speech": record.get("part_of_speech", "noun"),
        "cefr_level": record.get("cefr_level", "B1"),
        "frequency_rank": _as_int(record.get("frequency_rank")),
        "phonetic_ipa": record.get("phonetic_ipa", ""),
        "primary_meaning_en": definition,
        "primary_meaning_ru": record.get("primary_meaning_ru", ""),
        "primary_meaning_uz": record.get("primary_meaning_uz", ""),
        "senses": [{"definition_en": definition}] if definition else [],
        "translations": {
            language: [value]
            for language, value in (
                ("ru", record.get("primary_meaning_ru")),
                ("uz", record.get("primary_meaning_uz")),
            )
            if value
        },
        "source": record.get("source", "LexiTrio file import"),
    }


def _open_text(path: str | Path) -> io.TextIOBase:
    source = Path(path)
    if not source.exists():
        raise LexiconImportError(f"Fayl topilmadi: {source}")
    if source.suffix.lower() == ".gz":
        return gzip.open(source, "rt", encoding="utf-8", errors="replace")
    return source.open("r", encoding="utf-8", errors="replace")
