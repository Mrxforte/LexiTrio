"""Content inventory: how much of the 50,000-unit curriculum target exists and is published."""

from __future__ import annotations

from django.db.models import Sum

from apps.curriculum.content.choices import PublicationStatus, ValidationStatus
from apps.curriculum.content.models import (
    ContentBatch,
    CurriculumExercise,
    CurriculumTopic,
    TestBankQuestion,
)

TARGETS = {
    "en_exercises": 10000,
    "ru_exercises": 10000,
    "en_tests": 10000,
    "ru_tests": 10000,
    "en_topics": 5000,
    "ru_topics": 5000,
    "total_units": 50000,
}

_PUBLISHED = PublicationStatus.PUBLISHED


def _percent(part: int, whole: int) -> float:
    return round((part / whole) * 100, 2)


def content_inventory() -> dict:
    """Counts generated and published units per language and kind, with completion rates."""
    generated = {
        "en_exercises": CurriculumExercise.objects.filter(language="en").count(),
        "ru_exercises": CurriculumExercise.objects.filter(language="ru").count(),
        "en_tests": TestBankQuestion.objects.filter(language="en").count(),
        "ru_tests": TestBankQuestion.objects.filter(language="ru").count(),
        "en_topics": CurriculumTopic.objects.filter(language="en").count(),
        "ru_topics": CurriculumTopic.objects.filter(language="ru").count(),
    }
    published = {
        "en_exercises": CurriculumExercise.objects.filter(language="en", publication_status=_PUBLISHED).count(),
        "ru_exercises": CurriculumExercise.objects.filter(language="ru", publication_status=_PUBLISHED).count(),
        "en_tests": TestBankQuestion.objects.filter(language="en", publication_status=_PUBLISHED).count(),
        "ru_tests": TestBankQuestion.objects.filter(language="ru", publication_status=_PUBLISHED).count(),
        "en_topics": CurriculumTopic.objects.filter(
            language="en", lesson_content__publication_status=_PUBLISHED
        ).count(),
        "ru_topics": CurriculumTopic.objects.filter(
            language="ru", lesson_content__publication_status=_PUBLISHED
        ).count(),
    }
    return {
        "targets": dict(TARGETS),
        "generated": generated,
        "published": published,
        "total_generated": sum(generated.values()),
        "total_published": sum(published.values()),
        "completion": {
            **{key: _percent(published[key], TARGETS[key]) for key in published},
            "overall": _percent(sum(published.values()), TARGETS["total_units"]),
        },
        "validation_failures": (
            CurriculumExercise.objects.filter(validation_status=ValidationStatus.INVALID).count()
            + TestBankQuestion.objects.filter(validation_status=ValidationStatus.INVALID).count()
        ),
        "duplicates": ContentBatch.objects.aggregate(total=Sum("duplicate_count"))["total"] or 0,
    }
