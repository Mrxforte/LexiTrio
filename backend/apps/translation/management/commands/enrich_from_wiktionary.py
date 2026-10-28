from django.core.management.base import BaseCommand, CommandError

from apps.translation.services.lexicon import LexiconImportError
from apps.translation.services.wiktionary import enrich_from_wiktionary


class Command(BaseCommand):
    help = (
        "Wiktionary (kaikki.org) dump'idan professional lug'at ma'lumotlarini oladi: "
        "transkripsiya (IPA), so'z turkumi, artikl, ko'plik/qiyosiy daraja, "
        "rus va o'zbek tarjimalari (rod va son belgilari bilan)."
    )

    def add_arguments(self, parser):
        parser.add_argument("path", help="kaikki .jsonl yoki .jsonl.gz fayli.")
        parser.add_argument("--batch-size", type=int, default=1500)
        parser.add_argument(
            "--max-lines",
            type=int,
            default=0,
            help="Faqat birinchi N qatorni o'qish (sinov uchun).",
        )
        parser.add_argument(
            "--overwrite",
            action="store_true",
            help="Mavjud tarjimalarni ham qayta yozish.",
        )

    def handle(self, *args, **options):
        def progress(stats):
            self.stdout.write(
                f"  ko'rildi: {stats.scanned}, mos: {stats.matched}, "
                f"yozuv: {stats.updated_entries}, ru: {stats.russian}, uz: {stats.uzbek}, "
                f"tarjima: {stats.created_translations}",
                ending="\r",
            )

        try:
            stats = enrich_from_wiktionary(
                options["path"],
                batch_size=max(100, options["batch_size"]),
                only_missing=not options["overwrite"],
                max_lines=max(0, options["max_lines"]),
                progress=progress,
            )
        except LexiconImportError as exc:
            raise CommandError(str(exc)) from exc

        self.stdout.write("")
        self.stdout.write(
            self.style.SUCCESS(
                f"Tayyor: {stats.matched} so'z mos keldi, {stats.updated_entries} yozuv "
                f"yangilandi, {stats.created_translations} tarjima qo'shildi "
                f"(ru: {stats.russian}, uz: {stats.uzbek})."
            )
        )
