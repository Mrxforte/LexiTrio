import time

from django.core.management.base import BaseCommand, CommandError

from translation.services.backfill import backfill_entries
from translation.services.openai_client import (
    TranslationNotConfigured,
    TranslationProviderError,
)
from translation.services.translator import TranslationInputError, normalize_targets


class Command(BaseCommand):
    help = (
        "Bazadagi lug'at yozuvlarining rus/uzbek tarjimalarini OpenAI orqali to'ldiradi. "
        "Jarayonni to'xtatib, keyin `--start-id` bilan davom ettirish mumkin."
    )

    def add_arguments(self, parser):
        parser.add_argument("--languages", default="ru,uz")
        parser.add_argument("--limit", type=int, default=0, help="0 = hammasi.")
        parser.add_argument("--page-size", type=int, default=50)
        parser.add_argument("--batch-size", type=int, default=None)
        parser.add_argument("--start-id", type=int, default=0)
        parser.add_argument(
            "--overwrite",
            action="store_true",
            help="Mavjud tarjimalarni ham qayta yozish.",
        )
        parser.add_argument("--dry-run", action="store_true")
        parser.add_argument("--sleep", type=float, default=0.0)

    def handle(self, *args, **options):
        try:
            languages = normalize_targets(options["languages"])
        except TranslationInputError as exc:
            raise CommandError(str(exc)) from exc

        only_missing = not options["overwrite"]
        remaining = max(0, options["limit"])
        cursor = max(0, options["start_id"])
        page_size = max(1, options["page_size"])
        batch_size = options["batch_size"]
        dry_run = options["dry_run"]
        pause = max(0.0, options["sleep"])

        totals = {"processed": 0, "translated": 0, "failed": 0, "warnings": 0}
        if dry_run:
            self.stdout.write(self.style.WARNING("Dry run: baza o'zgartirilmaydi."))

        try:
            while True:
                page_limit = min(page_size, remaining) if remaining else page_size
                summary = backfill_entries(
                    languages=languages,
                    limit=page_limit,
                    only_missing=only_missing,
                    dry_run=dry_run,
                    cursor_id=cursor,
                    batch_size=batch_size,
                )
                if not summary["processed"]:
                    break

                cursor = summary["nextCursor"]
                totals["processed"] += summary["processed"]
                totals["translated"] += summary["translated"]
                totals["failed"] += summary["failed"]
                totals["warnings"] += len(summary["warnings"])

                for warning in summary["warnings"]:
                    self.stderr.write(f"  ogohlantirish: {warning}")
                self.stdout.write(
                    f"  {totals['processed']} ko'rildi, {totals['translated']} tarjima, "
                    f"{totals['failed']} xato (keyingi id: {cursor})",
                    ending="\r",
                )

                if remaining:
                    remaining -= summary["processed"]
                    if remaining <= 0:
                        break
                if pause:
                    time.sleep(pause)
        except TranslationNotConfigured as exc:
            raise CommandError(str(exc)) from exc
        except TranslationProviderError as exc:
            raise CommandError(f"Tarjima xatosi: {exc}") from exc

        self.stdout.write("")
        if not totals["processed"]:
            self.stdout.write("Tarjima qilish uchun yozuv topilmadi.")
            return
        self.stdout.write(
            self.style.SUCCESS(
                f"Tayyor: {totals['processed']} yozuv ko'rildi, "
                f"{totals['translated']} yangilandi, {totals['failed']} xato, "
                f"{totals['warnings']} ogohlantirish."
            )
        )
