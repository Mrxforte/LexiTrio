import time

from django.core.management.base import BaseCommand, CommandError

from translation.models import TranslationJob
from translation.services.jobs import next_pending_job, run_job


class Command(BaseCommand):
    help = "Navbatdagi TranslationJob vazifalarini bajaradi (fon ishchisi)."

    def add_arguments(self, parser):
        parser.add_argument(
            "--watch",
            action="store_true",
            help="To'xtamasdan ishlash va yangi vazifalarni kutish.",
        )
        parser.add_argument("--sleep", type=float, default=10.0)
        parser.add_argument("--job-id", help="Faqat bitta vazifani bajarish.")

    def handle(self, *args, **options):
        job_id = options.get("job_id")
        watch = options["watch"]
        sleep_seconds = max(1.0, options["sleep"])

        while True:
            if job_id:
                job = TranslationJob.objects.filter(pk=job_id).first()
                if job is None:
                    raise CommandError(f"Vazifa topilmadi: {job_id}")
                if job.status in {
                    TranslationJob.Status.COMPLETED,
                    TranslationJob.Status.CANCELLED,
                }:
                    self.stdout.write(f"Vazifa allaqachon tugagan: {job.status}")
                    return
            else:
                job = next_pending_job()

            if job is None:
                if watch:
                    time.sleep(sleep_seconds)
                    continue
                self.stdout.write("Navbatda vazifa yo'q.")
                return

            self.stdout.write(f"Vazifa boshlandi: {job.id}")
            run_job(job, log=lambda message: self.stdout.write(f"  {message}"))
            message = f"Vazifa tugadi: {job.id} — {job.status}"
            if job.last_error:
                message = f"{message} ({job.last_error})"
            self.stdout.write(self.style.SUCCESS(message))

            if job_id:
                return
