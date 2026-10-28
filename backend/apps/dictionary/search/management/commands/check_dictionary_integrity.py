"""Read-only integrity report for the dictionary tables.

Nothing is deleted or rewritten: duplicate-looking headwords may be separate
senses with different parts of speech, pronunciations or translations, so the
report only lists them for a human to review.
"""

from django.core.management.base import BaseCommand, CommandError
from django.db import connection
from django.db.models import Count, Q

from apps.dictionary.entries.models import DictionaryEntry
from apps.dictionary.search.engine import FTS_TABLE, fts_table_has_rows

SUPPORTED_LANGUAGES = ("en", "ru", "uz", "uz-cyrl")


class Command(BaseCommand):
    help = "Lug'at ma'lumotlari butunligini tekshiradi (hech narsani o'chirmaydi)."

    def add_arguments(self, parser):
        parser.add_argument("--limit", type=int, default=10, help="Har bir bo'limda nechta namuna.")
        parser.add_argument(
            "--strict",
            action="store_true",
            help="Muammo topilsa nol bo'lmagan kod bilan chiqadi.",
        )

    def handle(self, *args, **options):
        self.limit = max(1, options["limit"])
        self.issues = 0

        self._report("Takrorlanuvchi so'zlar (so'z + til + turkum)", self._duplicates())
        self._report("Tarjimasiz yozuvlar", self._without_translation())
        self._report("Bo'sh tarjima maydonlari", self._missing_meanings())
        self._report("Noto'g'ri til kodlari", self._invalid_languages())
        self._report("Yetim qatorlar", self._orphans())
        self._report("Qidiruv indeksi holati", self._index_status())

        self.stdout.write("")
        if self.issues:
            self.stdout.write(self.style.WARNING(f"{self.issues} ta bo'limda muammo topildi."))
            if options["strict"]:
                raise CommandError(f"{self.issues} ta muammo topildi.")
        else:
            self.stdout.write(self.style.SUCCESS("Muammo topilmadi."))

    def _report(self, title, findings):
        self.stdout.write("")
        self.stdout.write(self.style.MIGRATE_HEADING(title))
        if not findings:
            self.stdout.write("  muammo yo'q")
            return
        self.issues += 1
        for line in findings:
            self.stdout.write(f"  - {line}")

    def _duplicates(self):
        rows = (
            DictionaryEntry.objects.values("headword_normalized", "language", "part_of_speech")
            .annotate(dupes=Count("id"))
            .filter(dupes__gt=1)
            .order_by("-dupes")[: self.limit]
        )
        return [
            f"{row['headword_normalized']} ({row['language']}, {row['part_of_speech']}): "
            f"{row['dupes']} yozuv"
            for row in rows
        ]

    def _without_translation(self):
        queryset = DictionaryEntry.objects.filter(translations__isnull=True)
        total = queryset.count()
        if not total:
            return []
        samples = [
            f"{entry.headword} ({entry.language})" for entry in queryset[: self.limit]
        ]
        return [f"jami {total}"] + samples

    def _missing_meanings(self):
        rows = (
            DictionaryEntry.objects.values("language")
            .annotate(
                missing_ru=Count("id", filter=Q(primary_meaning_ru="")),
                missing_uz=Count("id", filter=Q(primary_meaning_uz="")),
                total=Count("id"),
            )
            .order_by("language")
        )
        return [
            f"{row['language']}: ru bo'sh {row['missing_ru']}/{row['total']}, "
            f"uz bo'sh {row['missing_uz']}/{row['total']}"
            for row in rows
            if row["missing_ru"] or row["missing_uz"]
        ]

    def _invalid_languages(self):
        return [
            f"{row['language']}: {row['total']} yozuv"
            for row in DictionaryEntry.objects.exclude(language__in=SUPPORTED_LANGUAGES)
            .values("language")
            .annotate(total=Count("id"))
            .order_by("-total")[: self.limit]
        ]

    def _orphans(self):
        findings = []
        with connection.cursor() as cursor:
            for label, sql in (
                (
                    "tarjima",
                    """
                    SELECT count(*) FROM learning_translation AS t
                    LEFT JOIN learning_dictionaryentry AS e ON e.id = t.entry_id
                    WHERE e.id IS NULL
                    """,
                ),
                (
                    "ma'no",
                    """
                    SELECT count(*) FROM learning_wordmeaning AS m
                    LEFT JOIN learning_dictionaryentry AS e ON e.id = m.entry_id
                    WHERE e.id IS NULL
                    """,
                ),
            ):
                cursor.execute(sql)
                orphans = cursor.fetchone()[0]
                if orphans:
                    findings.append(f"{orphans} {label} yozuvsiz qolgan")
        return findings

    def _index_status(self):
        if connection.vendor != "sqlite":
            return [f"SQLite emas ({connection.vendor}): ORM qidiruvi ishlatiladi"]
        if not fts_table_has_rows():
            return ["FTS5 indeksi bo'sh - `manage.py rebuild_search_index`"]
        with connection.cursor() as cursor:
            cursor.execute(f"SELECT count(*) FROM {FTS_TABLE}")
            indexed = cursor.fetchone()[0]
        entries = DictionaryEntry.objects.count()
        if indexed != entries:
            return [f"indeks {indexed} hujjat, bazada {entries} yozuv - qayta qurish kerak"]
        return []
