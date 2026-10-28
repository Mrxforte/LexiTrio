import uuid

from django.conf import settings
from django.db import models

from .choices import TranslationJobStatus


class TranslationJob(models.Model):
    """Background dictionary translation run that can be resumed after a restart."""

    Status = TranslationJobStatus

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    status = models.CharField(
        max_length=16, choices=Status.choices, default=Status.PENDING, db_index=True
    )
    languages = models.CharField(max_length=32, default="ru,uz")
    batch_size = models.PositiveSmallIntegerField(default=6)
    limit = models.PositiveIntegerField(default=0, help_text="0 means no limit.")
    only_missing = models.BooleanField(default=True)
    cursor_id = models.BigIntegerField(default=0)
    total_candidates = models.PositiveIntegerField(default=0)
    processed = models.PositiveIntegerField(default=0)
    translated = models.PositiveIntegerField(default=0)
    failed = models.PositiveIntegerField(default=0)
    last_error = models.TextField(blank=True)
    created_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        null=True,
        blank=True,
        on_delete=models.SET_NULL,
        related_name="translation_jobs",
    )
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-created_at"]

    def __str__(self):
        return f"{self.id} ({self.status})"

    @property
    def language_list(self):
        return [language.strip() for language in self.languages.split(",") if language.strip()]

    @property
    def progress(self):
        if not self.total_candidates:
            return 1.0 if self.status == self.Status.COMPLETED else 0.0
        return round(min(self.processed / self.total_candidates, 1.0), 4)
