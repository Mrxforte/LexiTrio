"""Stored values of the dictionary app (persisted and consumed by the Android client: never change them)."""

from django.db import models


class TranslationQuality(models.TextChoices):
    MACHINE = "machine", "Machine"
    WIKTIONARY = "wiktionary", "Wiktionary"
    REVIEWED = "reviewed", "Reviewed"
