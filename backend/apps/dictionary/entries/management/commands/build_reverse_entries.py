"""Create Russian and Uzbek headword entries from the existing translations.

The imported catalog keeps one entry per English word; Russian and Uzbek words
only exist as translation rows of those entries. Searching in Russian or Uzbek
therefore returns English headwords and the ru/uz "dictionaries" look empty.
This command turns every distinct Russian/Uzbek translation term into its own
entry (``language`` ru/uz) that points back to the English words.
"""

from __future__ import annotations

import re
from collections import Counter, defaultdict

from django.core.management.base import BaseCommand, CommandError
from django.db import transaction

from apps.dictionary.entries.cache import invalidate_dictionary_cache
from apps.dictionary.entries.models import DictionaryEntry, Translation
from shared.text import normalize_text

SOURCE = "LexiTrio reverse index"
LANGUAGES = ("ru", "uz")
MAX_TERM_LENGTH = 60
MAX_TERM_WORDS = 4
MAX_LINKED_TERMS = 6
MAX_OTHER_TERMS = 4
CHUNK = 2000

_PARENTHETICAL_RE = re.compile(r"\s*[\(\[][^\)\]]*[\)\]]")
_SPLIT_RE = re.compile(r"[,;/]")
_CYRILLIC_RE = re.compile(r"[\u0400-\u04ff]")
_LATIN_RE = re.compile(r"[A-Za-z]")
_APOSTROPHES = str.maketrans({"’": "‘", "`": "‘", "'": "‘", "ʻ": "‘", "ʼ": "‘"})
_CEFR_ORDER = {"A1": 0, "A2": 1, "B1": 2, "B2": 3, "C1": 4, "C2": 5}


def normalize_term(term: str, language: str) -> str:
    """Same normalisation the Android app applies to search queries."""
    text = normalize_text(term)
    if language == "uz":
        text = text.translate(_APOSTROPHES)
    return text


def clean_terms(raw: str, language: str) -> list[str]:
    """Split a translation cell such as ``"kitob, jild (ot)"`` into clean terms."""
    text = _PARENTHETICAL_RE.sub("", raw or "")
    terms: list[str] = []
    for part in _SPLIT_RE.split(text):
        term = " ".join(part.split()).strip(" .-")
        if len(term) < 2 or len(term) > MAX_TERM_LENGTH:
            continue
        if len(term.split()) > MAX_TERM_WORDS:
            continue
        has_cyrillic = bool(_CYRILLIC_RE.search(term))
        has_latin = bool(_LATIN_RE.search(term))
        if language == "ru" and (not has_cyrillic or has_latin):
            continue
        if language == "uz" and (not has_latin or has_cyrillic):
            continue
        terms.append(term)
    return terms


class Command(BaseCommand):
    help = (
        "Rus va o'zbek tarjimalaridan mustaqil lug'at yozuvlari (ru/uz) yaratadi, "
        "shunda ular qidiruvda bosh so'z sifatida topiladi."
    )

    def add_arguments(self, parser):
        parser.add_argument(
            "--purge",
            action="store_true",
            help="Avval yaratilgan teskari yozuvlarni o'chiradi va chiqadi.",
        )
        parser.add_argument(
            "--limit",
            type=int,
            default=0,
            help="Sinov uchun: faqat shuncha yangi yozuv yaratadi (0 - cheklovsiz).",
        )

    def handle(self, *args, **options):
        if options["purge"]:
            deleted, _ = DictionaryEntry.objects.filter(source=SOURCE).delete()
            invalidate_dictionary_cache()
            self.stdout.write(self.style.SUCCESS(f"{deleted} ta yozuv o'chirildi."))
            return

        english = {
            row[0]: row[1:]
            for row in DictionaryEntry.objects.filter(language="en").values_list(
                "id", "headword", "part_of_speech", "cefr_level", "frequency_rank"
            )
        }
        if not english:
            raise CommandError("Inglizcha yozuvlar topilmadi.")

        self.stdout.write("Tarjimalar o'qilmoqda...")
        terms_by_entry: dict[tuple[int, str], list[str]] = defaultdict(list)
        translations = Translation.objects.filter(
            target_language__in=LANGUAGES, entry__language="en"
        ).values_list("entry_id", "target_language", "translation_text")
        for entry_id, language, text in translations.iterator(chunk_size=20000):
            terms = clean_terms(text, language)
            if terms:
                terms_by_entry[(entry_id, language)].extend(terms)

        groups: dict[tuple[str, str], dict] = {}
        for (entry_id, language), terms in terms_by_entry.items():
            for term in terms:
                key = (language, normalize_term(term, language))
                group = groups.setdefault(key, {"forms": Counter(), "sources": []})
                group["forms"][term] += 1
                if entry_id not in group["sources"]:
                    group["sources"].append(entry_id)

        existing = set(
            DictionaryEntry.objects.exclude(language="en").values_list(
                "language", "headword_normalized"
            )
        )

        created = 0
        batch: list[DictionaryEntry] = []
        limit = options["limit"]

        for key in sorted(groups):
            if key in existing:
                continue
            language, normalized = key
            group = groups[key]
            sources = sorted(group["sources"], key=lambda eid: -english[eid][3])
            batch.append(
                self._build_entry(language, normalized, group, sources, english, terms_by_entry)
            )
            created += 1
            if len(batch) >= CHUNK:
                self._flush(batch)
                self.stdout.write(f"  {created} ta yozuv...")
            if limit and created >= limit:
                break
        self._flush(batch)

        invalidate_dictionary_cache()
        self.stdout.write(self.style.SUCCESS(f"Yaratildi: {created} ta ru/uz yozuv."))

    def _build_entry(self, language, normalized, group, sources, english, terms_by_entry):
        headword = group["forms"].most_common(1)[0][0]
        if language == "uz":
            headword = headword.translate(_APOSTROPHES)
        top = sources[:MAX_LINKED_TERMS]
        english_words = list(dict.fromkeys(english[eid][0] for eid in top))
        pos_counts = Counter(english[eid][1] for eid in sources)
        cefr = min((english[eid][2] for eid in sources), key=lambda c: _CEFR_ORDER.get(c, 2))

        meanings = {"ru": "", "uz": ""}
        for other in LANGUAGES:
            if other == language:
                continue
            collected: list[str] = []
            for eid in top:
                collected.extend(terms_by_entry.get((eid, other), ()))
            meanings[other] = ", ".join(list(dict.fromkeys(collected))[:MAX_OTHER_TERMS])

        alternative = ""
        if language == "uz" and "‘" in normalized:
            alternative = normalized.replace("‘", "'")

        return DictionaryEntry(
            headword=headword[:200],
            headword_normalized=normalized[:200],
            language=language,
            part_of_speech=pos_counts.most_common(1)[0][0][:40],
            cefr_level=cefr,
            frequency_rank=max(english[eid][3] for eid in sources),
            lemma=normalized[:200],
            alternative_spellings=alternative,
            source=SOURCE,
            primary_meaning_en=", ".join(english_words),
            primary_meaning_ru=meanings["ru"],
            primary_meaning_uz=meanings["uz"],
            verified=True,
        )

    @transaction.atomic
    def _flush(self, batch):
        if not batch:
            return
        # The primary meaning columns already carry everything a reverse entry
        # shows, so no WordMeaning rows are created (keeps sync pages small).
        DictionaryEntry.objects.bulk_create(batch)
        batch.clear()
