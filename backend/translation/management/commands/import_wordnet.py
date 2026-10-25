from django.core.management.base import BaseCommand, CommandError

from translation.services.lexicon import (
    LexiconImportError,
    import_records,
    wordnet_records,
)


class Command(BaseCommand):
    help = (
        "WordNet va Open Multilingual WordNet'dan ingliz lug'atini import qiladi "
        "(~100 000 so'z, inglizcha ta'rif va ruscha tarjimalar bilan)."
    )

    def add_arguments(self, parser):
        parser.add_argument(
            "--limit",
            type=int,
            default=100000,
            help="Import qilinadigan yozuvlar soni (0 = cheksiz).",
        )
        parser.add_argument("--max-senses", type=int, default=3)
        parser.add_argument("--max-words", type=int, default=4)
        parser.add_argument(
            "--no-multiword",
            action="store_true",
            help="Faqat bir so'zdan iborat headword'larni import qilish.",
        )
        parser.add_argument(
            "--translation-language",
            default="rus",
            help="OMW tili kodi (masalan rus). 'none' = tarjimasiz.",
        )
        parser.add_argument("--chunk-size", type=int, default=2000)

    def handle(self, *args, **options):
        allow_multiword = not options["no_multiword"]
        max_words = options["max_words"]
        translation_language = options["translation_language"].strip()
        if translation_language.lower() in {"none", "off", "-"}:
            translation_language = ""

        def progress(stats):
            self.stdout.write(
                f"  ko'rildi: {stats.seen}, qo'shildi: {stats.created}, "
                f"o'tkazildi: {stats.skipped}, ma'no: {stats.meanings}, "
                f"tarjima: {stats.translations}",
                ending="\r",
            )

        self.stdout.write("WordNet ma'lumotlari yuklanmoqda...")
        try:
            records = wordnet_records(
                limit=max(0, options["limit"]),
                max_senses=max(1, options["max_senses"]),
                allow_multiword=allow_multiword,
                max_words=max(1, max_words),
                translation_language=translation_language,
            )
            stats = import_records(
                records,
                chunk_size=max(100, options["chunk_size"]),
                allow_multiword=allow_multiword,
                max_words=max(1, max_words),
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
