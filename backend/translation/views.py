from django.conf import settings
from django.shortcuts import get_object_or_404
from rest_framework import permissions, status
from rest_framework.exceptions import APIException, PermissionDenied, ValidationError
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle, UserRateThrottle
from rest_framework.views import APIView

from learning.models import DictionaryEntry

from .models import TranslationJob
from .serializers import (
    BackfillRequestSerializer,
    DocumentTranslationRequestSerializer,
    TextTranslationRequestSerializer,
    TranslationJobCreateSerializer,
    job_payload,
)
from .services.backfill import backfill_entries
from .services.openai_client import (
    TranslationNotConfigured,
    TranslationProviderError,
)
from .services.providers import build_translation_client, configured_providers
from .services.text_extraction import (
    SUPPORTED_EXTENSIONS,
    DocumentError,
    extract_document,
)
from .services.translator import (
    DEFAULT_TARGETS,
    SUPPORTED_TARGETS,
    TranslationInputError,
    translate_text,
)


class TranslationServiceUnavailable(APIException):
    status_code = status.HTTP_503_SERVICE_UNAVAILABLE
    default_detail = "Tarjima xizmati vaqtincha mavjud emas."
    default_code = "translation_unavailable"


class TranslationAPIView(APIView):
    permission_classes = [permissions.IsAuthenticated]
    throttle_classes = [ScopedRateThrottle, UserRateThrottle]
    throttle_scope = "translation"


class TranslationStatusView(TranslationAPIView):
    def get(self, request):
        entries = DictionaryEntry.objects.filter(language="en")
        return Response(
            {
                "provider": "+".join(
                    provider.name for provider in configured_providers()
                )
                or "none",
                "configured": bool(settings.OPENAI_API_KEY or settings.DEEPSEEK_API_KEY),
                "model": "+".join(
                    provider.model for provider in configured_providers()
                )
                or settings.OPENAI_MODEL,
                "providers": [
                    {"name": provider.name, "model": provider.model}
                    for provider in configured_providers()
                ],
                "targetLanguages": list(SUPPORTED_TARGETS),
                "defaultTargets": list(DEFAULT_TARGETS),
                "supportedExtensions": list(SUPPORTED_EXTENSIONS),
                "limits": {
                    "maxUploadBytes": settings.TRANSLATION_MAX_UPLOAD_BYTES,
                    "maxTextCharacters": settings.TRANSLATION_MAX_TEXT_CHARS,
                    "maxSegmentCharacters": settings.TRANSLATION_MAX_SEGMENT_CHARS,
                    "maxSegments": settings.TRANSLATION_MAX_SEGMENTS,
                    "batchSize": settings.TRANSLATION_BATCH_SIZE,
                },
                "dictionary": {
                    "totalEntries": entries.count(),
                    "missingRussian": entries.filter(primary_meaning_ru="").count(),
                    "missingUzbek": entries.filter(primary_meaning_uz="").count(),
                },
            }
        )


class TextTranslateView(TranslationAPIView):
    def post(self, request):
        serializer = TextTranslationRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        result = _translate_text(
            serializer.validated_data["text"], serializer.validated_data["targets"]
        )
        return Response(result)


class DocumentTranslateView(TranslationAPIView):
    def post(self, request):
        serializer = DocumentTranslationRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        try:
            document = extract_document(serializer.validated_data["file"])
        except DocumentError as exc:
            raise ValidationError({"file": str(exc)}) from exc

        result = _translate_text(document.text, serializer.validated_data["targets"])
        result["file"] = {
            "name": document.file_name,
            "extension": document.extension,
            "sizeBytes": document.size_bytes,
            "pageCount": document.page_count,
        }
        return Response(result)


class DictionaryBackfillView(TranslationAPIView):
    def post(self, request):
        _require_staff(request)
        serializer = BackfillRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        result = _run_backfill(
            {
                "languages": data["languages"],
                "limit": data["limit"],
                "only_missing": data["onlyMissing"],
                "dry_run": data["dryRun"],
                "entry_ids": data.get("entryIds"),
            }
        )
        result["remaining"] = _remaining(
            data["languages"], only_missing=data["onlyMissing"]
        )
        return Response(result)


class TranslationJobListCreateView(TranslationAPIView):
    permission_classes = [permissions.IsAdminUser]
    throttle_scope = "translation_admin"

    def get(self, request):
        jobs = TranslationJob.objects.all()[:50]
        return Response([job_payload(job) for job in jobs])

    def post(self, request):
        serializer = TranslationJobCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        job = TranslationJob.objects.create(
            languages=",".join(data["languages"]),
            batch_size=data["batchSize"],
            limit=data["limit"],
            only_missing=data["onlyMissing"],
            total_candidates=_remaining(data["languages"], only_missing=data["onlyMissing"])
            if data["onlyMissing"]
            else 0,
            created_by=request.user,
        )
        return Response(job_payload(job), status=status.HTTP_201_CREATED)


class TranslationJobDetailView(TranslationAPIView):
    permission_classes = [permissions.IsAdminUser]
    throttle_scope = "translation_admin"

    def get(self, request, job_id):
        job = get_object_or_404(TranslationJob, pk=job_id)
        return Response(job_payload(job))


class TranslationJobCancelView(TranslationAPIView):
    permission_classes = [permissions.IsAdminUser]
    throttle_scope = "translation_admin"

    def post(self, request, job_id):
        job = get_object_or_404(TranslationJob, pk=job_id)
        if job.status in {TranslationJob.Status.PENDING, TranslationJob.Status.RUNNING}:
            job.status = TranslationJob.Status.CANCELLED
            job.save(update_fields=["status", "updated_at"])
        return Response(job_payload(job))


def _translate_text(text: str, targets) -> dict:
    try:
        return translate_text(text, targets=targets, client=build_translation_client())
    except TranslationInputError as exc:
        raise ValidationError({"text": str(exc)}) from exc
    except TranslationNotConfigured as exc:
        raise TranslationServiceUnavailable(str(exc)) from exc
    except TranslationProviderError as exc:
        raise TranslationServiceUnavailable(str(exc)) from exc


def _run_backfill(payload: dict) -> dict:
    try:
        return backfill_entries(**payload, client=build_translation_client())
    except TranslationInputError as exc:
        raise ValidationError({"languages": str(exc)}) from exc
    except TranslationNotConfigured as exc:
        raise TranslationServiceUnavailable(str(exc)) from exc
    except TranslationProviderError as exc:
        raise TranslationServiceUnavailable(str(exc)) from exc


def _remaining(languages, *, only_missing: bool) -> int:
    queryset = DictionaryEntry.objects.filter(language="en")
    if only_missing:
        from django.db.models import Q

        condition = Q()
        for language in languages:
            condition |= Q(**{f"primary_meaning_{language}": ""})
        queryset = queryset.filter(condition)
    return queryset.count()


def _require_staff(request) -> None:
    if getattr(settings, "TRANSLATION_BACKFILL_REQUIRES_STAFF", True) and not request.user.is_staff:
        raise PermissionDenied(
            "Lug'atni ommaviy tarjima qilish faqat xodimlar uchun. "
            "TRANSLATION_BACKFILL_REQUIRES_STAFF=false bilan o'chirish mumkin."
        )
