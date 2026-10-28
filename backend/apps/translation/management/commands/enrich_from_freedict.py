from django.core.management.base import BaseCommand, CommandError

from apps.translation.services.freedict import enrich_from_freedict
from apps.translation.services.lexicon import LexiconImportError


class Command(BaseCommand):
    help = (
        "FreeDict rus lug'atini (eng-rus TEI) import qiladi: 62 000+ bosh so'z, "
        "rus tarjimalari, so'z turkumi, rod va transkripsiya bilan."
    )

    def add_arguments(self, parser):
        parser.add_argument("path", help="eng-rus.tei fayli.")
        parser.add_argument("--batch-size", type=int, default=800)
        parser.add_argument(
            "--overwrite", action="store_true", help="Mavjud rus tarjimalarini qayta yozish."
        )

    def handle(self, *args, **options):
        def progress(stats):
            self.stdout.write(
                f"  o'qildi: {stats.scanned}, mos: {stats.matched}, "
                f"yozuv: {stats.updated_entries}, tarjima: {stats.created_translations}",
                ending="\r",
            )

        try:
            stats = enrich_from_freedict(
                options["path"],
                batch_size=max(100, options["batch_size"]),
                only_missing=not options["overwrite"],
                progress=progress,
            )
        except LexiconImportError as exc:
            raise CommandError(str(exc)) from exc

        self.stdout.write("")
        self.stdout.write(
            self.style.SUCCESS(
                f"Tayyor: {stats.matched} so'z mos keldi, {stats.updated_entries} yozuv "
                f"yangilandi, {stats.created_translations} rus tarjimasi qo'shildi."
            )
        )
