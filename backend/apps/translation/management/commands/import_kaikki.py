from django.core.management.base import BaseCommand, CommandError

from apps.translation.services.lexicon import (
    LexiconImportError,
    import_records,
    kaikki_records,
)


class Command(BaseCommand):
    help = (
        "kaikki.org (Wiktionary) JSONL dump'idan ingliz lug'atini import qiladi. "
        "Rus/uzbek tarjimalari mavjud bo'lsa, ular ham saqlanadi."
    )

    def add_arguments(self, parser):
        parser.add_argument("path", help="kaikki .jsonl yoki .jsonl.gz fayli.")
        parser.add_argument(
            "--limit", type=int, default=0, help="Nechta yozuv import qilinsin (0 = hammasi)."
        )
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
            records = kaikki_records(options["path"], limit=max(0, options["limit"]))
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
