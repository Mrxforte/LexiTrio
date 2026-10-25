"""Professional lexicographic helpers shared by the import and translation paths.

The goal is to present each word the way a printed learner's dictionary does:
part of speech, article, grammatical gender, number, transcription, and short
sense notes next to every translation.
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence

# Canonical English part-of-speech labels used across the catalog.
POS_NOUN = "noun"
POS_VERB = "verb"
POS_ADJECTIVE = "adjective"
POS_ADVERB = "adverb"
POS_NUMERAL = "numeral"
POS_ARTICLE = "article"
POS_PRONOUN = "pronoun"
POS_PREPOSITION = "preposition"
POS_CONJUNCTION = "conjunction"
POS_INTERJECTION = "interjection"
POS_DETERMINER = "determiner"
POS_PARTICLE = "particle"
POS_PHRASE = "phrase"
POS_ABBREVIATION = "abbreviation"

CANONICAL_POS = (
    POS_NOUN,
    POS_VERB,
    POS_ADJECTIVE,
    POS_ADVERB,
    POS_NUMERAL,
    POS_ARTICLE,
    POS_PRONOUN,
    POS_PREPOSITION,
    POS_CONJUNCTION,
    POS_INTERJECTION,
    POS_DETERMINER,
    POS_PARTICLE,
    POS_PHRASE,
    POS_ABBREVIATION,
)

_POS_ALIASES = {
    "n": POS_NOUN,
    "noun": POS_NOUN,
    "proper noun": POS_NOUN,
    "name": POS_NOUN,
    "v": POS_VERB,
    "verb": POS_VERB,
    "adj": POS_ADJECTIVE,
    "adjective": POS_ADJECTIVE,
    "adv": POS_ADVERB,
    "adverb": POS_ADVERB,
    "num": POS_NUMERAL,
    "numeral": POS_NUMERAL,
    "number": POS_NUMERAL,
    "article": POS_ARTICLE,
    "art": POS_ARTICLE,
    "pron": POS_PRONOUN,
    "pronoun": POS_PRONOUN,
    "prep": POS_PREPOSITION,
    "preposition": POS_PREPOSITION,
    "postp": POS_PREPOSITION,
    "pn": POS_NOUN,
    "proper noun": POS_NOUN,
    "conj": POS_CONJUNCTION,
    "conjunction": POS_CONJUNCTION,
    "intj": POS_INTERJECTION,
    "interjection": POS_INTERJECTION,
    "det": POS_DETERMINER,
    "determiner": POS_DETERMINER,
    "particle": POS_PARTICLE,
    "phrase": POS_PHRASE,
    "prep_phrase": POS_PHRASE,
    "abbrev": POS_ABBREVIATION,
    "abbreviation": POS_ABBREVIATION,
}

# Compact labels printed next to a translation, as in a paper dictionary.
_POS_ABBREVIATIONS = {
    "ru": {
        POS_NOUN: "сущ.",
        POS_VERB: "гл.",
        POS_ADJECTIVE: "прил.",
        POS_ADVERB: "нареч.",
        POS_NUMERAL: "числ.",
        POS_ARTICLE: "арт.",
        POS_PRONOUN: "мест.",
        POS_PREPOSITION: "предл.",
        POS_CONJUNCTION: "союз",
        POS_INTERJECTION: "межд.",
        POS_DETERMINER: "дет.",
        POS_PARTICLE: "част.",
        POS_PHRASE: "фраза",
        POS_ABBREVIATION: "сокр.",
    },
    "uz": {
        POS_NOUN: "ot",
        POS_VERB: "fe'l",
        POS_ADJECTIVE: "sifat",
        POS_ADVERB: "ravish",
        POS_NUMERAL: "son",
        POS_ARTICLE: "artikl",
        POS_PRONOUN: "olmosh",
        POS_PREPOSITION: "predlog",
        POS_CONJUNCTION: "bog'lovchi",
        POS_INTERJECTION: "undov",
        POS_DETERMINER: "aniqlovchi",
        POS_PARTICLE: "yuklama",
        POS_PHRASE: "ibora",
        POS_ABBREVIATION: "qisqa",
    },
}

_GENDER_ABBREVIATIONS = {
    "ru": {"m": "м.р.", "f": "ж.р.", "n": "с.р.", "c": "общ.р."},
    "uz": {},
}

_GENDER_ALIASES = {
    "masculine": "m",
    "masc": "m",
    "m": "m",
    "feminine": "f",
    "fem": "f",
    "f": "f",
    "neuter": "n",
    "neut": "n",
    "n": "n",
    "common": "c",
    "c": "c",
}

_IPA_VOWEL_STARTS = (
    "a",
    "e",
    "i",
    "o",
    "u",
    "æ",
    "ɑ",
    "ɒ",
    "ɔ",
    "ə",
    "ɜ",
    "ɛ",
    "ɪ",
    "ʊ",
    "ʌ",
    "ɐ",
    "ɘ",
)


def normalize_pos(value) -> str:
    text = str(value or "").strip().lower().replace("_", " ")
    if not text:
        return ""
    if text in _POS_ALIASES:
        return _POS_ALIASES[text]
    for alias, canonical in _POS_ALIASES.items():
        if text.startswith(alias):
            return canonical
    return text if text in CANONICAL_POS else ""


def pos_abbreviation(language: str, pos: str) -> str:
    return _POS_ABBREVIATIONS.get(language, {}).get(pos, "")


def normalize_gender(value) -> str:
    text = str(value or "").strip().lower()
    return _GENDER_ALIASES.get(text, "")


def gender_abbreviation(language: str, gender: str) -> str:
    return _GENDER_ABBREVIATIONS.get(language, {}).get(gender, "")


def normalize_number(value) -> str:
    text = str(value or "").strip().lower()
    if text in {"sg", "singular", "s"}:
        return "singular"
    if text in {"pl", "plural", "p"}:
        return "plural"
    if text in {"uncountable", "uncount", "mass"}:
        return "uncountable"
    return ""


def english_article(ipa: str, pos: str) -> str:
    """Return "a" or "an" for countable English nouns.

    Derived from the first phoneme so ``hour`` gets "an" while ``university``
    gets "a"; non-nouns return an empty string.
    """
    if pos != POS_NOUN:
        return ""
    transcription = (ipa or "").strip()
    if not transcription:
        return "a/an"
    symbol = transcription.strip("/[] ").lstrip("ˈˌ")
    if not symbol:
        return "a/an"
    return "an" if symbol[0].lower() in _IPA_VOWEL_STARTS else "a"


def context_note(
    language: str,
    *,
    pos: str = "",
    gender: str = "",
    number: str = "",
    article: str = "",
) -> str:
    """Build the compact "(сущ., ж.р.)"-style note shown next to a translation."""
    parts: list[str] = []
    abbreviation = pos_abbreviation(language, pos)
    if abbreviation:
        parts.append(abbreviation)
    gender_label = gender_abbreviation(language, gender)
    if gender_label:
        parts.append(gender_label)
    if number == "plural":
        parts.append("мн." if language == "ru" else "ko'plik")
    elif number == "uncountable":
        parts.append("неисч." if language == "ru" else "sanalmaydi")
    if article:
        parts.append(article)
    return ", ".join(part for part in parts if part)


def format_translation(value: str, note: str, *, max_length: int = 300) -> str:
    """Keep the machine-readable text clean; the note travels separately."""
    text = " ".join(str(value or "").split())
    if not text:
        return ""
    return text[:max_length]


def join_forms(values: Iterable[str], limit: int = 4) -> str:
    seen: list[str] = []
    for value in values:
        text = str(value or "").strip()
        if text and text not in seen:
            seen.append(text)
        if len(seen) >= limit:
            break
    return ", ".join(seen)


def primary_meaning_from_translations(
    translations: Sequence[str], note: str = "", limit: int = 4
) -> str:
    """Compose the string stored in ``primary_meaning_<lang>``."""
    text = join_forms(translations, limit=limit)
    if not text:
        return ""
    return f"{text} ({note})" if note else text
