"""Translate dictionary text into Russian and Uzbek through the OpenAI API."""

from __future__ import annotations

import json
import re
from collections.abc import Sequence

from django.conf import settings

from .openai_client import ChatResult, OpenAIClient, TranslationProviderError
from .text_extraction import normalize_text

SUPPORTED_TARGETS = ("ru", "uz")
DEFAULT_TARGETS = ("ru", "uz")

_LANGUAGE_LABELS = {
    "ru": "Russian (Cyrillic script)",
    "uz": "Uzbek (Latin script)",
}
_FENCE_RE = re.compile(r"```(?:json)?\s*(.*?)```", re.DOTALL | re.IGNORECASE)
_SENTENCE_SPLIT_RE = re.compile(r"(?<=[.!?;:])\s+")
_PARAGRAPH_SPLIT_RE = re.compile(r"\n\s*\n")
_ENTRY_LINE_RE = re.compile(r"(\s[-–—]\s|\s\|\s|\t)")

_SYSTEM_PROMPT = (
    "You are a professional lexicographer working on an English-Uzbek-Russian learner's "
    "dictionary. Translate every item faithfully and concisely. Preserve punctuation, line "
    "breaks, and dictionary notation. Never invent extra commentary. Uzbek must be written in "
    "the modern Latin script, Russian in Cyrillic. Always answer with JSON only."
)

_HEADWORD_SYSTEM_PROMPT = (
    "You are a bilingual lexicographer building an English-Uzbek-Russian learner's dictionary. "
    "For every English headword you return the most common dictionary equivalents in the target "
    "languages, separated by commas. Never add explanations or full sentences. Always answer "
    "with JSON only."
)

_PROFESSIONAL_SYSTEM_PROMPT = (
    "You are a senior lexicographer producing camera-ready dictionary entries for an "
    "English-Russian-Uzbek learner's dictionary. For every headword return exactly what a "
    "printed dictionary shows: the IPA transcription, the part of speech, the English article "
    "(a/an for countable nouns, empty otherwise), the plural or comparative/superlative forms, "
    "and the best target-language equivalents with their own part of speech and, for Russian, "
    "the grammatical gender. Use standard dictionary abbreviations, never add commentary, and "
    "answer with JSON only."
)

_ITEMS_MARKER = "Items:"


class TranslationInputError(Exception):
    """Caller supplied text that cannot be translated."""


def normalize_targets(values: str | Sequence[str] | None) -> list[str]:
    if values is None:
        return list(DEFAULT_TARGETS)
    if isinstance(values, str):
        raw_values: Sequence[str] = values.split(",")
    else:
        raw_values = values

    targets: list[str] = []
    for value in raw_values:
        language = str(value).strip().lower()
        if not language:
            continue
        if language not in SUPPORTED_TARGETS:
            raise TranslationInputError(
                f"Qo'llab-quvvatlanmaydigan til: '{language}'. Ruxsat etilgan: "
                + ", ".join(SUPPORTED_TARGETS)
            )
        if language not in targets:
            targets.append(language)
    return targets or list(DEFAULT_TARGETS)


def split_text(text: str, *, max_chars: int | None = None, max_segments: int | None = None) -> list[str]:
    max_chars = max_chars or int(getattr(settings, "TRANSLATION_MAX_SEGMENT_CHARS", 2000))
    max_segments = max_segments or int(getattr(settings, "TRANSLATION_MAX_SEGMENTS", 40))

    normalized = normalize_text(text)
    if not normalized:
        return []

    segments: list[str] = []
    for block in _PARAGRAPH_SPLIT_RE.split(normalized):
        block = block.strip()
        if not block:
            continue
        segments.extend(_split_block(block, max_chars))

    if len(segments) > max_segments:
        raise TranslationInputError(
            f"Matn juda katta: {len(segments)} segment (limit {max_segments}). "
            "Matnni bo'laklarga ajratib yuboring."
        )
    return segments


def translate_text(
    text: str,
    targets: str | Sequence[str] | None = None,
    client: OpenAIClient | None = None,
    *,
    batch_size: int | None = None,
) -> dict:
    languages = normalize_targets(targets)
    segments = split_text(text)
    if not segments:
        raise TranslationInputError("Tarjima qilish uchun matn bo'sh.")

    records, model, warnings = translate_items(
        segments, languages, client=client, batch_size=batch_size
    )
    translations = {
        language: "\n\n".join(
            record[language] for record in records if record.get(language)
        )
        for language in languages
    }
    return {
        "targets": languages,
        "sourceCharacters": len(normalize_text(text)),
        "segmentCount": len(records),
        "segments": records,
        "translations": translations,
        "model": model,
        "warnings": warnings,
    }


def translate_items(
    items: Sequence[str],
    targets: str | Sequence[str] | None = None,
    client: OpenAIClient | None = None,
    *,
    batch_size: int | None = None,
) -> tuple[list[dict], str, list[str]]:
    """Translate free text items, returning `(records, model, warnings)`.

    Each record holds `index`, `source` and one key per target language.
    """
    languages = normalize_targets(targets)
    clean_items = [normalize_text(str(item)) for item in items]
    if not clean_items:
        return [], "", []

    payloads = [{"index": index, "text": text} for index, text in enumerate(clean_items)]
    records, model, warnings = _translate_batches(
        payloads,
        languages,
        client=client,
        batch_size=batch_size,
        system_prompt=_SYSTEM_PROMPT,
        build_prompt=_build_items_prompt,
    )
    for record in records:
        record["source"] = clean_items[record["index"]]
    return records, model, warnings


def translate_headwords(
    entries: Sequence[dict],
    targets: str | Sequence[str] | None = None,
    client: OpenAIClient | None = None,
    *,
    batch_size: int | None = None,
) -> tuple[list[dict], str, list[str]]:
    """Translate dictionary headwords into short lists of equivalents.

    Each input entry is `{"headword": str, "definition": str}`. Records hold
    `index`, `headword` and one comma separated equivalents string per language.
    """
    languages = normalize_targets(targets)
    payloads = []
    for index, entry in enumerate(entries):
        headword = str(entry.get("headword", "")).strip()
        if not headword:
            continue
        payloads.append(
            {
                "index": index,
                "headword": headword,
                "definition": normalize_text(str(entry.get("definition", ""))),
            }
        )
    if not payloads:
        return [], "", []

    records, model, warnings = _translate_batches(
        payloads,
        languages,
        client=client,
        batch_size=batch_size,
        system_prompt=_HEADWORD_SYSTEM_PROMPT,
        build_prompt=_build_headword_prompt,
    )
    for record, payload in zip(records, payloads):
        record["headword"] = payload["headword"]
    return records, model, warnings


def translate_professional(
    entries: Sequence[dict],
    targets: str | Sequence[str] | None = None,
    client: OpenAIClient | None = None,
    *,
    batch_size: int | None = None,
) -> tuple[list[dict], str, list[str]]:
    """Produce dictionary-ready entries: transcription, POS, article, forms, senses.

    Each input entry is ``{"headword": str, "definition": str}``. Each record
    holds ``index``, ``headword``, ``pos``, ``ipa``, ``article``, ``plural``,
    plus one object per target language with ``text``, ``pos``, ``gender``.
    """
    languages = normalize_targets(targets)
    payloads = []
    for index, entry in enumerate(entries):
        headword = str(entry.get("headword", "")).strip()
        if not headword:
            continue
        payloads.append(
            {
                "index": index,
                "headword": headword,
                "definition": normalize_text(str(entry.get("definition", ""))),
            }
        )
    if not payloads:
        return [], "", []

    records, model, warnings = _translate_batches(
        payloads,
        languages,
        client=client,
        batch_size=batch_size,
        system_prompt=_PROFESSIONAL_SYSTEM_PROMPT,
        build_prompt=_build_professional_prompt,
        parse_payload=_parse_professional_payload,
    )
    for record, payload in zip(records, payloads):
        record["headword"] = payload["headword"]
    return records, model, warnings


def _translate_batches(
    payloads: Sequence[dict],
    languages: Sequence[str],
    *,
    client: OpenAIClient | None,
    batch_size: int | None,
    system_prompt: str,
    build_prompt,
    parse_payload=None,
) -> tuple[list[dict], str, list[str]]:
    client = client or OpenAIClient()
    configured_batch = batch_size or getattr(settings, "TRANSLATION_BATCH_SIZE", 6)
    step = max(1, int(configured_batch))
    parse_payload = parse_payload or _parse_translation_payload

    records: list[dict] = []
    warnings: list[str] = []
    model = client.model

    for start in range(0, len(payloads), step):
        batch = list(payloads[start : start + step])
        result: ChatResult = client.complete(
            system_prompt=system_prompt,
            user_prompt=build_prompt(batch, languages),
        )
        model = result.model or model
        batch_records = parse_payload(result.content, len(batch), languages)
        for offset, parsed in enumerate(batch_records):
            index = start + offset
            record = {"index": index}
            record.update(parsed)
            for language in languages:
                if not _has_value(record.get(language)):
                    warnings.append(
                        f"{index}-element uchun '{language}' tarjimasi bo'sh qaytdi."
                    )
            records.append(record)

    return records, model, warnings


def _has_value(value) -> bool:
    if isinstance(value, dict):
        return bool(str(value.get("text", "")).strip())
    return bool(str(value or "").strip())


def _build_items_prompt(batch: Sequence[dict], languages: Sequence[str]) -> str:
    language_list = ", ".join(_LANGUAGE_LABELS[language] for language in languages)
    shape = ",".join(
        json.dumps(language) + ':"..."' for language in languages
    )
    return (
        f"Translate every item into: {language_list}.\n"
        f'Reply exactly as {{"translations":[{{"index":0,{shape}}}]}}.\n'
        "Rules: keep the same index values, return one object per input item in the same "
        "order, never merge or split items, and return text already in the target language "
        "unchanged. If a term has no direct equivalent, use the closest natural equivalent.\n"
        f"{_ITEMS_MARKER}\n"
        f"{json.dumps(batch, ensure_ascii=False)}"
    )


def _build_headword_prompt(batch: Sequence[dict], languages: Sequence[str]) -> str:
    language_list = ", ".join(_LANGUAGE_LABELS[language] for language in languages)
    shape = ",".join(
        json.dumps(language) + ':"..."' for language in languages
    )
    return (
        f"For each English headword below, give its dictionary equivalents in: {language_list}.\n"
        f'Reply exactly as {{"translations":[{{"index":0,{shape}}}]}}.\n'
        "Rules: keep the same index values and return one object per item; use the English "
        "definition only to choose the correct sense; return at most four equivalents separated "
        "by commas; never add explanations or repeat the English word. Uzbek must use the Latin "
        "script, Russian the Cyrillic script.\n"
        f"{_ITEMS_MARKER}\n"
        f"{json.dumps(batch, ensure_ascii=False)}"
    )


def _build_professional_prompt(batch: Sequence[dict], languages: Sequence[str]) -> str:
    language_list = ", ".join(_LANGUAGE_LABELS[language] for language in languages)
    shape = ",".join(
        json.dumps(language) + ':{text:"...",pos:"...",gender:"..."}' for language in languages
    )
    return (
        "Build one dictionary entry per item.\n"
        f"Target languages: {language_list}.\n"
        f'Reply exactly as {{"translations":[{{"index":0,"pos":"noun","ipa":"/.../",'
        f'"article":"a","plural":"...",{shape}}}]}}.\n'
        "Rules:\n"
        "- index must match the input item; return one object per item, same order.\n"
        "- pos: noun, verb, adjective, adverb, numeral, article, pronoun, preposition, "
        "conjunction, interjection, determiner, phrase.\n"
        "- ipa: the British IPA transcription in slashes; reuse the known one when it is given.\n"
        "- article: 'a' or 'an' for countable nouns, '' otherwise.\n"
        "- plural: plural form for nouns, comparative for adjectives, else ''.\n"
        "- ru.text: up to four Russian equivalents separated by commas, Cyrillic script.\n"
        "- ru.gender: m, f, n or c for the Russian noun, else ''.\n"
        "- uz.text: up to four Uzbek equivalents in the modern Latin script.\n"
        "- Never repeat the English headword and never add explanations.\n"
        f"{_ITEMS_MARKER}\n"
        f"{json.dumps(batch, ensure_ascii=False)}"
    )


def _parse_professional_payload(
    content: str, expected_count: int, languages: Sequence[str]
) -> list[dict]:
    data = _loads_json(content)
    items = data.get("translations") if isinstance(data, dict) else data
    if isinstance(items, dict):
        items = [items]
    if not isinstance(items, list):
        raise TranslationProviderError("Tarjima javobi kutilgan JSON formatida emas.")

    parsed: dict[int, dict] = {}
    for position, item in enumerate(items):
        if not isinstance(item, dict):
            continue
        try:
            index = int(item.get("index", position))
        except (TypeError, ValueError):
            index = position
        if not 0 <= index < expected_count:
            continue
        record: dict = {
            "pos": _clean_value(item.get("pos")),
            "ipa": _clean_value(item.get("ipa")),
            "article": _clean_value(item.get("article")),
            "plural": _clean_value(item.get("plural")),
        }
        for language in languages:
            value = item.get(language)
            if isinstance(value, dict):
                record[language] = {
                    "text": _clean_value(value.get("text")),
                    "pos": _clean_value(value.get("pos")) or record["pos"],
                    "gender": _clean_value(value.get("gender")),
                }
            else:
                record[language] = {"text": _clean_value(value), "pos": record["pos"], "gender": ""}
        parsed[index] = record

    empty = {"pos": "", "ipa": "", "article": "", "plural": ""}
    empty.update({language: {"text": "", "pos": "", "gender": ""} for language in languages})
    return [parsed.get(position, dict(empty)) for position in range(expected_count)]


def _parse_translation_payload(
    content: str, expected_count: int, languages: Sequence[str]
) -> list[dict[str, str]]:
    data = _loads_json(content)
    items = data.get("translations") if isinstance(data, dict) else data
    if isinstance(items, dict):
        items = [items]
    if not isinstance(items, list):
        raise TranslationProviderError("Tarjima javobi kutilgan JSON formatida emas.")

    by_index: dict[int, dict[str, str]] = {}
    for position, item in enumerate(items):
        if not isinstance(item, dict):
            continue
        raw_index = item.get("index", position)
        try:
            index = int(raw_index)
        except (TypeError, ValueError):
            index = position
        if 0 <= index < expected_count:
            by_index[index] = {
                language: _clean_value(item.get(language)) for language in languages
            }

    empty = {language: "" for language in languages}
    return [by_index.get(position, dict(empty)) for position in range(expected_count)]


def _clean_value(value) -> str:
    if value is None:
        return ""
    if isinstance(value, (list, tuple)):
        return ", ".join(str(part).strip() for part in value if str(part).strip())
    return str(value).strip()


def _loads_json(raw: str):
    text = (raw or "").strip()
    fence = _FENCE_RE.search(text)
    if fence:
        text = fence.group(1).strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        start, end = text.find("{"), text.rfind("}")
        if start != -1 and end > start:
            try:
                return json.loads(text[start : end + 1])
            except json.JSONDecodeError:
                pass
        raise TranslationProviderError("Tarjima xizmati noto'g'ri JSON qaytardi.")


def _split_block(block: str, max_chars: int) -> list[str]:
    lines = [line.strip() for line in block.splitlines() if line.strip()]
    if len(lines) > 1 and _looks_like_entries(lines):
        segments: list[str] = []
        for line in lines:
            if len(line) <= max_chars:
                segments.append(line)
            else:
                segments.extend(_split_long_line(line, max_chars))
        return segments

    if len(block) <= max_chars:
        return [block]
    return _pack(_split_long_line(block, max_chars), max_chars)


def _looks_like_entries(lines: Sequence[str]) -> bool:
    matches = sum(1 for line in lines if _ENTRY_LINE_RE.search(line))
    return matches * 2 >= len(lines)


def _split_long_line(line: str, max_chars: int) -> list[str]:
    pieces: list[str] = []
    for sentence in _SENTENCE_SPLIT_RE.split(line):
        sentence = sentence.strip()
        if not sentence:
            continue
        if len(sentence) <= max_chars:
            pieces.append(sentence)
        else:
            pieces.extend(_hard_wrap(sentence, max_chars))
    return pieces


def _hard_wrap(value: str, max_chars: int) -> list[str]:
    words = value.split()
    chunks: list[str] = []
    buffer = ""
    for word in words:
        while len(word) > max_chars:
            if buffer:
                chunks.append(buffer)
                buffer = ""
            chunks.append(word[:max_chars])
            word = word[max_chars:]
        if not buffer:
            buffer = word
        elif len(buffer) + 1 + len(word) <= max_chars:
            buffer = f"{buffer} {word}"
        else:
            chunks.append(buffer)
            buffer = word
    if buffer:
        chunks.append(buffer)
    return chunks


def _pack(pieces: Sequence[str], max_chars: int) -> list[str]:
    chunks: list[str] = []
    buffer = ""
    for piece in pieces:
        if not buffer:
            buffer = piece
        elif len(buffer) + 1 + len(piece) <= max_chars:
            buffer = f"{buffer} {piece}"
        else:
            chunks.append(buffer)
            buffer = piece
    if buffer:
        chunks.append(buffer)
    return chunks
