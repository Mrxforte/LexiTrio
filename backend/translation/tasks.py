"""Celery background tasks for long-running and asynchronous translation jobs."""

import logging
from celery import shared_task
from django.core.management import call_command

logger = logging.getLogger(__name__)


@shared_task(
    bind=True,
    max_retries=3,
    default_retry_delay=60,
    autoretry_for=(Exception,),
    retry_backoff=True,
)
def run_translation_batch_task(self, limit: int = 50, dry_run: bool = False):
    """Asynchronous background task to process pending dictionary translation backfills."""
    logger.info(f"Starting async translation batch task (limit={limit}, dry_run={dry_run})")
    try:
        call_command(
            "translate_dictionary",
            limit=limit,
            dry_run=dry_run,
        )
        logger.info("Translation batch task completed successfully.")
        return {"status": "success", "limit": limit}
    except Exception as exc:
        logger.error(f"Error executing translation batch task: {exc}")
        raise


@shared_task(bind=True, max_retries=2)
def rebuild_search_index_task(self):
    """Asynchronous background task to rebuild the dictionary search index."""
    logger.info("Starting async search index rebuild task...")
    try:
        call_command("rebuild_search_index")
        logger.info("Search index rebuild completed successfully.")
        return {"status": "success"}
    except Exception as exc:
        logger.error(f"Error rebuilding search index: {exc}")
        raise
