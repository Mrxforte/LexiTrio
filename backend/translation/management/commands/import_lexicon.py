from django.core.management.base import BaseCommand, CommandError

from translation.services.lexicon import (
    LexiconImportError,
    file_records,
    import_records,
)


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

    def handle(self, *args, **options):
        allow_multiword = not options["no_multiword"]
        max_words = max(1, options["max_words"])

        def progress(stats):
            self.stdout.write(
                f"  ko'rildi: {stats.seen}, qo'shildi: {stats.created}, "
                f"o'tkazildi: {stats.skipped}, tarjima: {stats.translations}",
                ending="\r",
            )

        try:
            records = file_records(options["path"], limit=max(0, options["limit"]))
            stats = import_records(
                records,
                chunk_size=max(100, options["chunk_size"]),
                allow_multiword=allow_multiword,
                max_words=max_words,
                progress=progress,
            )
        except LexiconImportError as exc:
            raise CommandError(str(exc)) from exc

        self.stdout.write("")
        self.stdout.write(
            self.style.SUCCESS(
                f"Tayyor: {stats.created} yangi yozuv, {stats.meanings} ma'no, "
                f"{stats.translations} tarjima ({stats.skipped} o'tkazildi)."
            )
        )
