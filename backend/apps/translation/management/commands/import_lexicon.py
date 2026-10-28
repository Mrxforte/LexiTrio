from django.core.management.base import BaseCommand, CommandError
from django.db import transaction

from apps.translation.services.lexicon import (
    LexiconImportError,
    file_records,
    import_records,
)


class _DryRunComplete(Exception):
    """Raised to roll back a preview run."""


class Command(BaseCommand):
    help = (
        "Tayyor lug'at faylini import qiladi: JSONL, JSON, CSV, TSV yoki oddiy so'z ro'yxati. "
        "Ustun nomlari erkin: headword/word/lemma, definition/meaning/gloss, "
        "definition_ru/ru, definition_uz/uz, pos, cefr, frequency."
    )

    def add_arguments(self, parser):
        parser.add_argument("path")
        parser.add_argument("--limit", type=int, default=0, help="0 = hammasi.")
        parser.add_argument("--max-words", type=int, default=4)
        parser.add_argument("--no-multiword", action="store_true")
        parser.add_argument("--chunk-size", type=int, default=2000)
        parser.add_argument(
            "--dry-run",
            action="store_true",
            help="Faylni tekshiradi va hisobot beradi, lekin bazaga yozmaydi.",
        )

    def handle(self, *args, **options):
        allow_multiword = not options["no_multiword"]
        max_words = max(1, options["max_words"])
        dry_run = options["dry_run"]

        def progress(stats):
            self.stdout.write(
                f"  ko'rildi: {stats.seen}, qo'shildi: {stats.created}, "
                f"o'tkazildi: {stats.skipped} "
                f"(noto'g'ri: {stats.invalid}, takror: {stats.duplicate}), "
                f"tarjima: {stats.translations}",
                ending="\r",
            )

        try:
            stats = self._run(options, allow_multiword, max_words, progress, dry_run)
        except LexiconImportError as exc:
            raise CommandError(str(exc)) from exc

        self.stdout.write("")
        prefix = "Sinov (yozilmadi). " if dry_run else ""
        self.stdout.write(
            self.style.SUCCESS(
                f"{prefix}Tayyor: {stats.created} yangi yozuv, {stats.meanings} ma'no, "
                f"{stats.translations} tarjima. "
                f"O'tkazildi: {stats.skipped} "
                f"(noto'g'ri: {stats.invalid}, takroriy: {stats.duplicate})."
            )
        )

    def _run(self, options, allow_multiword, max_words, progress, dry_run):
        records = file_records(options["path"], limit=max(0, options["limit"]))
        import_kwargs = dict(
            chunk_size=max(100, options["chunk_size"]),
            allow_multiword=allow_multiword,
            max_words=max_words,
            progress=progress,
        )
        if not dry_run:
            return import_records(records, **import_kwargs)
        try:
            with transaction.atomic():
                stats = import_records(records, **import_kwargs)
                raise _DryRunComplete(stats)
        except _DryRunComplete as done:
            return done.args[0]
