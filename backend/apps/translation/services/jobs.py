"""Run queued `TranslationJob` rows in the foreground."""

from __future__ import annotations

from ..models import TranslationJob
from .backfill import backfill_entries, count_candidates
from .openai_client import TranslationNotConfigured, TranslationProviderError
from .translator import normalize_targets

PAGE_SIZE = 50


def next_pending_job():
    return (
        TranslationJob.objects.filter(status=TranslationJob.Status.PENDING)
        .order_by("created_at")
        .first()
    )


def create_job(*, user, languages, batch_size, limit, only_missing):
    """Queue a job; ``total_candidates`` is known up front for "only missing" runs."""
    return TranslationJob.objects.create(
        languages=",".join(languages),
        batch_size=batch_size,
        limit=limit,
        only_missing=only_missing,
        total_candidates=count_candidates(languages, only_missing=True) if only_missing else 0,
        created_by=user,
    )


def cancel_job(job):
    """Cancel a job that has not finished; finished jobs are returned unchanged."""
    if job.status in {TranslationJob.Status.PENDING, TranslationJob.Status.RUNNING}:
        job.status = TranslationJob.Status.CANCELLED
        job.save(update_fields=["status", "updated_at"])
    return job


def run_job(job, *, client=None, log=None):
    """Execute a job until it finishes, fails or is cancelled."""
    job.status = TranslationJob.Status.RUNNING
    job.last_error = ""
    job.save(update_fields=["status", "last_error", "updated_at"])

    languages = normalize_targets(job.language_list)

    try:
        if not job.total_candidates:
            job.total_candidates = count_candidates(
                languages, only_missing=job.only_missing
            )
            job.save(update_fields=["total_candidates", "updated_at"])

        while True:
            if _is_cancelled(job):
                job.status = TranslationJob.Status.CANCELLED
                job.save(update_fields=["status", "updated_at"])
                return job

            page_limit = PAGE_SIZE
            if job.limit:
                page_limit = min(page_limit, job.limit - job.processed)
                if page_limit <= 0:
                    break

            summary = backfill_entries(
                languages=languages,
                limit=page_limit,
                only_missing=job.only_missing,
                cursor_id=job.cursor_id,
                client=client,
                batch_size=job.batch_size,
            )
            if not summary["processed"]:
                break

            job.cursor_id = summary["nextCursor"]
            job.processed += summary["processed"]
            job.translated += summary["translated"]
            job.failed += summary["failed"]
            job.save(
                update_fields=[
                    "cursor_id",
                    "processed",
                    "translated",
                    "failed",
                    "updated_at",
                ]
            )
            if log:
                log(
                    f"job {job.id}: {job.processed}/{job.total_candidates} "
                    f"(tarjima: {job.translated}, xato: {job.failed})"
                )

        job.status = TranslationJob.Status.COMPLETED
        job.save(update_fields=["status", "updated_at"])
    except TranslationNotConfigured as exc:
        job.status = TranslationJob.Status.FAILED
        job.last_error = str(exc)
        job.save(update_fields=["status", "last_error", "updated_at"])
    except TranslationProviderError as exc:
        job.status = TranslationJob.Status.FAILED
        job.last_error = str(exc)
        job.save(update_fields=["status", "last_error", "updated_at"])
    except Exception as exc:  # noqa: BLE001 - keep job state consistent
        job.status = TranslationJob.Status.FAILED
        job.last_error = f"{type(exc).__name__}: {exc}"
        job.save(update_fields=["status", "last_error", "updated_at"])

    return job


def _is_cancelled(job) -> bool:
    status = (
        TranslationJob.objects.filter(pk=job.pk)
        .values_list("status", flat=True)
        .first()
    )
    return status == TranslationJob.Status.CANCELLED
