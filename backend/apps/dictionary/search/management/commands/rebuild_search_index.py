"""Rebuild or verify the SQLite FTS5 dictionary search index."""

from django.core.management.base import BaseCommand, CommandError
from django.db import connection, transaction

from apps.dictionary.entries.models import DictionaryEntry
from apps.dictionary.search import fts
from apps.dictionary.search.engine import FTS_TABLE, fts_table_has_rows, reset_search_caches


class Command(BaseCommand):
    help = (
        "Django lug'ati uchun SQLite FTS5 qidiruv indeksini qayta quradi. "
        "Indeks buzilganda yoki ommaviy importdan keyin ishlatiladi."
    )

    def add_arguments(self, parser):
        parser.add_argument(
            "--check",
            action="store_true",
            help="Faqat indeks holatini tekshiradi, qayta qurmaydi.",
        )

    def handle(self, *args, **options):
        if connection.vendor != "sqlite":
            raise CommandError(
                f"FTS5 indeksi faqat SQLite uchun; joriy baza: {connection.vendor}."
            )

        if options["check"]:
            self._check()
            return

        entries = DictionaryEntry.objects.count()
        self.stdout.write(f"Indeks qayta qurilmoqda ({entries} yozuv)...")

        try:
            with transaction.atomic():
                with connection.cursor() as cursor:
                    for statement in fts.create_statements():
                        cursor.execute(statement)
                    for statement in fts.rebuild_statements():
                        cursor.execute(statement)
        except Exception as error:  # noqa: BLE001 - surfaced as a command error
            raise CommandError(f"Indeksni qurish muvaffaqiyatsiz: {error}") from error

        reset_search_caches()
        self._check()

    def _check(self) -> None:
        with connection.cursor() as cursor:
            cursor.execute(f"SELECT count(*) FROM {FTS_TABLE}")
            indexed = cursor.fetchone()[0]
        entries = DictionaryEntry.objects.count()
        status = "OK" if indexed == entries else "MOS EMAS"
        self.stdout.write(
            self.style.SUCCESS(
                f"Indeks: {indexed} hujjat / {entries} yozuv -> {status}"
            )
        )
        if not fts_table_has_rows():
            raise CommandError("Indeks bo'sh; `rebuild_search_index` ni ishga tushiring.")
