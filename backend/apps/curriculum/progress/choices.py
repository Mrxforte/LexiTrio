"""Stored values for learner progress (persisted and consumed by the Android client: never change them)."""

from django.db import models


class TopicProgressStatus(models.TextChoices):
    """Learner progress on a curriculum topic (stored as free text, not a field choice)."""

    NOT_STARTED = "NOT_STARTED", "Not started"
    IN_PROGRESS = "IN_PROGRESS", "In progress"
    COMPLETED = "COMPLETED", "Completed"
    MASTERED = "MASTERED", "Mastered"
