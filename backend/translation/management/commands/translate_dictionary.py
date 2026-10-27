import time

from django.conf import settings
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
        parser.add_argument(
            "--page-size",
            type=int,
            default=0,
            help="Bir sahifada ishlanadigan yozuvlar soni (0 = parallelikka moslash).",
        )
        parser.add_argument("--batch-size", type=int, default=None)
        parser.add_argument("--start-id", type=int, default=0)
        parser.add_argument(
            "--overwrite",
            action="store_true",
            help="Mavjud tarjimalarni ham qayta yozish.",
        )
        parser.add_argument("--dry-run", action="store_true")
        parser.add_argument(
            "--professional",
            action="store_true",
            help="Lug'at sifati: transkripsiya, so'z turkumi, artikl, ko'plik/rod ham yoziladi.",
        )
        parser.add_argument("--sleep", type=float, default=0.0)
        parser.add_argument(
            "--retry-wait",
            type=float,
            default=60.0,
            help="Barcha provayder band bo'lsa, shu soniya kutib sahifani qayta urinadi.",
        )
        parser.add_argument(
            "--max-retries",
            type=int,
            default=0,
            help="Ketma-ket xatolarga necha marta chidash (0 = cheksiz).",
        )

    def handle(self, *args, **options):
        try:
            languages = normalize_targets(options["languages"])
        except TranslationInputError as exc:
            raise CommandError(str(exc)) from exc

        only_missing = not options["overwrite"]
        remaining = max(0, options["limit"])
        cursor = max(0, options["start_id"])
        batch_size = options["batch_size"]
        # A page must be wide enough to keep every worker busy; otherwise the
        # page boundary serialises the parallel batches.
        effective_batch = batch_size or getattr(settings, "TRANSLATION_BATCH_SIZE", 6)
        concurrency = max(1, int(getattr(settings, "TRANSLATION_CONCURRENCY", 1)))
        page_size = options["page_size"] or max(50, int(effective_batch) * concurrency * 2)
        dry_run = options["dry_run"]
        pause = max(0.0, options["sleep"])
        retry_wait = max(0.0, options["retry_wait"])
        max_retries = max(0, options["max_retries"])
        retries = 0

        totals = {"processed": 0, "translated": 0, "failed": 0, "warnings": 0}
        if dry_run:
            self.stdout.write(self.style.WARNING("Dry run: baza o'zgartirilmaydi."))

        try:
            while True:
                page_limit = min(page_size, remaining) if remaining else page_size
                try:
                    summary = backfill_entries(
                        languages=languages,
                        limit=page_limit,
                        only_missing=only_missing,
                        dry_run=dry_run,
                        professional=options["professional"],
                        cursor_id=cursor,
                        batch_size=batch_size,
                    )
                except TranslationNotConfigured:
                    raise
                except TranslationProviderError as exc:
                    # Quota/timeout bursts are normal when several providers are
                    # in use, so keep the run alive instead of dropping the job.
                    retries += 1
                    if max_retries and retries > max_retries:
                        raise CommandError(f"Tarjima xatosi: {exc}") from exc
                    self.stderr.write(
                        f"  provayder band ({exc}); {retry_wait:.0f}s kutib qayta urinamiz."
                    )
                    if retry_wait:
                        time.sleep(retry_wait)
                    continue
                retries = 0
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
