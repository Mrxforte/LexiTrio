from pathlib import Path

from django.conf import settings
from rest_framework import serializers

from .models import TranslationJob
from .services.text_extraction import SUPPORTED_EXTENSIONS
from .services.translator import DEFAULT_TARGETS, SUPPORTED_TARGETS


class LanguageListField(serializers.Field):
    default_error_messages = {
        "invalid": f"Til ro'yxati noto'g'ri. Ruxsat etilgan: {', '.join(SUPPORTED_TARGETS)}.",
    }

    def to_internal_value(self, data):
        if isinstance(data, str):
            raw_values = data.split(",")
        elif isinstance(data, (list, tuple)):
            raw_values = data
        else:
            self.fail("invalid")

        languages: list[str] = []
        for value in raw_values:
            language = str(value).strip().lower()
            if not language:
                continue
            if language not in SUPPORTED_TARGETS:
                self.fail("invalid")
            if language not in languages:
                languages.append(language)
        return languages or list(DEFAULT_TARGETS)

    def to_representation(self, value):
        return list(value)


class TextTranslationRequestSerializer(serializers.Serializer):
    text = serializers.CharField(trim_whitespace=False)
    targets = LanguageListField(required=False, default=list(DEFAULT_TARGETS))

    def validate_text(self, value):
        if not value.strip():
            raise serializers.ValidationError("Matn bo'sh bo'lishi mumkin emas.")
        limit = int(getattr(settings, "TRANSLATION_MAX_TEXT_CHARS", 20000))
        if len(value) > limit:
            raise serializers.ValidationError(
                f"Matn juda uzun ({len(value)} belgi). Ruxsat etilgan limit: {limit} belgi."
            )
        return value


class DocumentTranslationRequestSerializer(serializers.Serializer):
    file = serializers.FileField()
    targets = LanguageListField(required=False, default=list(DEFAULT_TARGETS))

    def validate_file(self, value):
        suffix = Path(getattr(value, "name", "") or "").suffix.lower()
        if suffix not in SUPPORTED_EXTENSIONS:
            raise serializers.ValidationError(
                "Qo'llab-quvvatlanmaydigan fayl turi. Ruxsat etilgan: "
                + ", ".join(SUPPORTED_EXTENSIONS)
            )
        limit = int(getattr(settings, "TRANSLATION_MAX_UPLOAD_BYTES", 10 * 1024 * 1024))
        if int(getattr(value, "size", 0) or 0) > limit:
            raise serializers.ValidationError(
                f"Fayl hajmi juda katta. Ruxsat etilgan limit: {limit} bayt."
            )
        return value


class BackfillRequestSerializer(serializers.Serializer):
    limit = serializers.IntegerField(required=False, default=20, min_value=1, max_value=200)
    languages = LanguageListField(required=False, default=list(DEFAULT_TARGETS))
    onlyMissing = serializers.BooleanField(required=False, default=True)
    dryRun = serializers.BooleanField(required=False, default=False)
    entryIds = serializers.ListField(
        child=serializers.IntegerField(min_value=1),
        required=False,
        allow_empty=False,
        max_length=200,
    )


class TranslationJobCreateSerializer(serializers.Serializer):
    languages = LanguageListField(required=False, default=list(DEFAULT_TARGETS))
    limit = serializers.IntegerField(required=False, default=0, min_value=0, max_value=1_000_000)
    batchSize = serializers.IntegerField(required=False, default=6, min_value=1, max_value=20)
    onlyMissing = serializers.BooleanField(required=False, default=True)


def job_payload(job: TranslationJob) -> dict:
    return {
        "id": str(job.id),
        "status": job.status,
        "languages": job.language_list,
        "batchSize": job.batch_size,
        "limit": job.limit,
        "onlyMissing": job.only_missing,
        "cursorId": job.cursor_id,
        "totalCandidates": job.total_candidates,
        "processed": job.processed,
        "translated": job.translated,
        "failed": job.failed,
        "progress": job.progress,
        "lastError": job.last_error,
        "createdAt": job.created_at.isoformat(),
        "updatedAt": job.updated_at.isoformat(),
    }
