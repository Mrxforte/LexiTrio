"""Views for the public learning catalog."""

import hashlib
import time
from datetime import timezone as dt_timezone
from urllib.parse import urlencode

from django.conf import settings
from django.core.cache import cache
from django.db import models
from django.db.models import Case, IntegerField, Prefetch, Q, Sum, Value, When
from django.utils import timezone
from django.utils.dateparse import parse_datetime
from rest_framework import filters, permissions, status, viewsets
from rest_framework.exceptions import ValidationError
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle
from rest_framework.views import APIView

from .cache import dictionary_version, safe_cache_get_or_set
from .models import (
    Collocation,
    ContentBatch,
    ContentValidationReport,
    Conversation,
    ConversationTurn,
    CurriculumCourse,
    CurriculumExercise,
    CurriculumLevel,
    CurriculumTopic,
    CurriculumUnit,
    DictionaryEntry,
    ExampleSentence,
    GrammarLesson,
    GrammarTable,
    LessonContent,
    MistakeRecord,
    QuizQuestion,
    SynonymAntonym,
    TestBankQuestion,
    Topic,
    TopicWord,
    Translation,
    UserTopicProgress,
    WordForm,
    WordMeaning,
)
from .search import SearchRequest, normalize_query, search
from .serializers import (
    CollocationSerializer,
    ContentBatchSerializer,
    ConversationSerializer,
    ConversationTurnSerializer,
    CurriculumCourseSerializer,
    CurriculumExerciseSerializer,
    CurriculumLevelSerializer,
    CurriculumTopicSerializer,
    CurriculumUnitSerializer,
    DictionaryEntrySerializer,
    DictionarySearchResultSerializer,
    ExampleSentenceSerializer,
    GrammarLessonSerializer,
    GrammarTableSerializer,
    LessonContentSerializer,
    MistakeRecordSerializer,
    QuizQuestionSerializer,
    SynonymAntonymSerializer,
    TestBankQuestionSerializer,
    TopicSerializer,
    TopicWordSerializer,
    TranslationSerializer,
    UserTopicProgressSerializer,
    WordFormSerializer,
    WordMeaningSerializer,
)


class PublicReadOnlyViewSet(viewsets.ReadOnlyModelViewSet):
    permission_classes = [permissions.AllowAny]
    filter_backends = [filters.OrderingFilter]
    ordering_fields = ["id"]
    ordering = ["id"]


class DictionaryEntryViewSet(PublicReadOnlyViewSet):
    queryset = DictionaryEntry.objects.all()
    serializer_class = DictionaryEntrySerializer

    def get_queryset(self):
        queryset = super().get_queryset()
        language = self.request.query_params.get("language")
        level = self.request.query_params.get("level")
        if language:
            queryset = queryset.filter(language=language)
        if level:
            queryset = queryset.filter(cefr_level=level)
        return queryset

    def list(self, request, *args, **kwargs):
        # A free-text query is answered by the ranked search index; the plain
        # list stays a paged queryset browse.
        query = (request.query_params.get("q") or "").strip()
        if not query:
            return super().list(request, *args, **kwargs)
        return _search_response(
            request,
            SearchRequest(
                query=query,
                source_language=request.query_params.get("language") or None,
                target_language=request.query_params.get("target") or None,
                part_of_speech=request.query_params.get("pos") or None,
                cefr_level=request.query_params.get("level") or None,
            ),
            serializer_class=DictionaryEntrySerializer,
        )


class WordMeaningViewSet(PublicReadOnlyViewSet):
    queryset = WordMeaning.objects.all()
    serializer_class = WordMeaningSerializer


class TranslationViewSet(PublicReadOnlyViewSet):
    queryset = Translation.objects.all()
    serializer_class = TranslationSerializer


class ExampleSentenceViewSet(PublicReadOnlyViewSet):
    queryset = ExampleSentence.objects.all()
    serializer_class = ExampleSentenceSerializer


class SynonymAntonymViewSet(PublicReadOnlyViewSet):
    queryset = SynonymAntonym.objects.all()
    serializer_class = SynonymAntonymSerializer


class WordFormViewSet(PublicReadOnlyViewSet):
    queryset = WordForm.objects.all()
    serializer_class = WordFormSerializer


class CollocationViewSet(PublicReadOnlyViewSet):
    queryset = Collocation.objects.all()
    serializer_class = CollocationSerializer


class TopicViewSet(PublicReadOnlyViewSet):
    queryset = Topic.objects.all()
    serializer_class = TopicSerializer


class TopicWordViewSet(PublicReadOnlyViewSet):
    queryset = TopicWord.objects.all()
    serializer_class = TopicWordSerializer


class ConversationViewSet(PublicReadOnlyViewSet):
    queryset = Conversation.objects.all()
    serializer_class = ConversationSerializer


class ConversationTurnViewSet(PublicReadOnlyViewSet):
    queryset = ConversationTurn.objects.all()
    serializer_class = ConversationTurnSerializer


class GrammarLessonViewSet(PublicReadOnlyViewSet):
    queryset = GrammarLesson.objects.all()
    serializer_class = GrammarLessonSerializer


class QuizQuestionViewSet(PublicReadOnlyViewSet):
    queryset = QuizQuestion.objects.all()
    serializer_class = QuizQuestionSerializer


MAX_DICTIONARY_PAGE_SIZE = 500
DEFAULT_DICTIONARY_PAGE_SIZE = 200


class DictionarySearchView(APIView):
    """Ranked, paged dictionary search.

    Query parameters:
        ``q``        free text (required). Matched against headwords, primary
                     meanings, alternative spellings and translations.
        ``source``   entry language (``en``/``ru``/``uz``/``uz-cyrl``).
        ``target``   keep only entries that have a translation in this language.
        ``pos``      part of speech (``noun``, ``verb``, ...).
        ``level``    CEFR level (``A1`` .. ``C2``).
        ``partial``  ``false`` disables the substring fallback.
        ``page``     1-based page number.
        ``pageSize`` rows per page, capped at ``DICTIONARY_SEARCH_MAX_PAGE_SIZE``.

    Responses carry an ``ETag`` derived from the current dictionary version, so
    a client that sends ``If-None-Match`` gets a ``304`` instead of a payload.
    """

    permission_classes = [permissions.AllowAny]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "dictionary_search"

    def get(self, request):
        query = (request.query_params.get("q") or "").strip()
        if not query:
            raise ValidationError({"q": "Qidiruv so'zi kiritilishi shart."})
        return _search_response(
            request,
            SearchRequest(
                query=query,
                source_language=_language_param(request, "source"),
                target_language=_language_param(request, "target"),
                part_of_speech=request.query_params.get("pos") or None,
                cefr_level=request.query_params.get("level") or None,
                match_partial=_bool_param(request, "partial", default=True),
            ),
            serializer_class=DictionarySearchResultSerializer,
        )


def _search_response(request, search_request: SearchRequest, *, serializer_class):
    page, page_size = _page_params(request)
    key = _search_cache_key(search_request, page, page_size, serializer_class.__name__)

    def _compute_body():
        started = time.monotonic()
        result = search(search_request, limit=page_size, offset=(page - 1) * page_size)
        body = {
            "query": search_request.query.strip(),
            "sourceLanguage": search_request.source_language,
            "targetLanguage": search_request.target_language,
            "partOfSpeech": search_request.part_of_speech,
            "page": page,
            "pageSize": page_size,
            "hasMore": result.has_more,
            "next": _page_url(request, page + 1) if result.has_more else None,
            "previous": _page_url(request, page - 1) if page > 1 else None,
            "backend": result.backend,
            "tookMs": round((time.monotonic() - started) * 1000, 2),
            "results": serializer_class(
                _load_entries(result.entry_ids, search_request.target_language), many=True
            ).data,
        }
        etag = _etag_for(key)
        return (etag, body)

    if settings.DICTIONARY_SEARCH_CACHE_TTL:
        etag, body = safe_cache_get_or_set(
            key, _compute_body, settings.DICTIONARY_SEARCH_CACHE_TTL
        )
    else:
        etag, body = _compute_body()

    headers = {
        "ETag": etag,
        "Cache-Control": f"public, max-age={max(0, settings.DICTIONARY_VERSION_CACHE_TTL)}",
    }
    if _etag_matches(request, etag):
        return Response(status=status.HTTP_304_NOT_MODIFIED, headers=headers)
    return Response(body, headers=headers)


def _load_entries(entry_ids, target_language):
    """Fetch the page rows without N+1 queries, preserving search order."""
    if not entry_ids:
        return []
    translations = Translation.objects.all()
    if target_language:
        translations = translations.filter(target_language=target_language)
    entries = DictionaryEntry.objects.filter(id__in=entry_ids).prefetch_related(
        Prefetch(
            "translations",
            queryset=translations.order_by("target_language", "id"),
            to_attr="selected_translations",
        )
    )
    position = Case(
        *[When(pk=entry_id, then=Value(index)) for index, entry_id in enumerate(entry_ids)],
        output_field=IntegerField(),
    )
    return list(entries.annotate(search_position=position).order_by("search_position"))


def _search_cache_key(search_request, page, page_size, serializer_name):
    stamp = dictionary_version()
    parts = [
        stamp,
        serializer_name,
        search_request.normalized,
        search_request.source_language or "",
        search_request.target_language or "",
        search_request.part_of_speech or "",
        search_request.cefr_level or "",
        "partial" if search_request.match_partial else "indexed",
        str(page),
        str(page_size),
    ]
    return "dictionary:search:" + hashlib.sha1("|".join(parts).encode("utf-8")).hexdigest()


def _etag_for(cache_key: str) -> str:
    return '"' + hashlib.sha256(cache_key.encode("utf-8")).hexdigest()[:32] + '"'


def _etag_matches(request, etag: str) -> bool:
    header = request.headers.get("If-None-Match")
    if not header:
        return False
    candidates = [part.strip() for part in header.split(",")]
    return "*" in candidates or etag in candidates


def _page_params(request):
    page = max(1, _query_int(request, "page", 1))
    page_size = max(
        1,
        _query_int(
            request,
            "pageSize",
            settings.DICTIONARY_SEARCH_DEFAULT_PAGE_SIZE,
            settings.DICTIONARY_SEARCH_MAX_PAGE_SIZE,
        ),
    )
    return page, page_size


def _page_url(request, page: int) -> str:
    params = request.query_params.copy()
    params["page"] = page
    return request.build_absolute_uri(request.path + "?" + urlencode(params, doseq=True))


def _language_param(request, name):
    raw = (request.query_params.get(name) or "").strip().lower()
    if not raw:
        return None
    normalized = normalize_query(raw)
    if not all(part.isalnum() or part == "-" for part in normalized.split("-")):
        raise ValidationError({name: "Til kodi noto'g'ri."})
    return normalized


def _bool_param(request, name, *, default: bool) -> bool:
    raw = request.query_params.get(name)
    if raw is None or raw == "":
        return default
    return raw.strip().lower() not in {"0", "false", "no", "off"}


class CatalogSnapshotView(APIView):
    """Learning content snapshot plus dictionary metadata.

    Dictionary rows are served by :class:`DictionarySyncView` in pages, so the
    Android client never downloads the whole 100k+ corpus in one response.
    """

    permission_classes = [permissions.AllowAny]

    def get(self, request):
        return Response(
            {
                "serverTime": timezone.now().isoformat(),
                "dictionary": _dictionary_meta(),
                "topics": TopicSerializer(Topic.objects.all(), many=True).data,
                "topicWords": TopicWordSerializer(TopicWord.objects.all(), many=True).data,
                "conversations": ConversationSerializer(
                    Conversation.objects.all(), many=True
                ).data,
                "conversationTurns": ConversationTurnSerializer(
                    ConversationTurn.objects.all(), many=True
                ).data,
                "grammarLessons": GrammarLessonSerializer(
                    GrammarLesson.objects.all(), many=True
                ).data,
                "quizQuestions": QuizQuestionSerializer(
                    QuizQuestion.objects.all(), many=True
                ).data,
            }
        )


class DictionarySyncView(APIView):
    """Paged, resumable dictionary export used by the Android offline cache.

    Query parameters:
        ``afterId`` - cursor; only rows with a greater id are returned.
        ``limit``   - page size, capped at 500.
        ``since``   - ISO 8601 timestamp; returns rows changed after it
                      (entry itself or any of its meanings).
    """

    permission_classes = [permissions.AllowAny]
    # Bulk download; the shared anonymous rate would need minutes per corpus.
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "dictionary_sync"

    def get(self, request):
        limit = max(
            1,
            _query_int(
                request, "limit", DEFAULT_DICTIONARY_PAGE_SIZE, MAX_DICTIONARY_PAGE_SIZE
            ),
        )
        after_id = _query_int(request, "afterId", 0)
        since = _parse_since(request.query_params.get("since"))

        queryset = DictionaryEntry.objects.all()
        if since is not None:
            changed_entries = DictionaryEntry.objects.filter(
                updated_at__gt=since
            ).values("id")
            changed_meanings = WordMeaning.objects.filter(
                updated_at__gt=since
            ).values("entry_id")
            queryset = queryset.filter(
                Q(id__in=changed_entries) | Q(id__in=changed_meanings)
            )
        if after_id:
            queryset = queryset.filter(id__gt=after_id)

        page = list(queryset.order_by("id")[: limit + 1])
        has_more = len(page) > limit
        entries = page[:limit]
        entry_ids = [entry.id for entry in entries]

        meanings = (
            WordMeaning.objects.filter(entry_id__in=entry_ids).order_by("id")
            if entry_ids
            else WordMeaning.objects.none()
        )
        meaning_ids = [meaning.id for meaning in meanings]

        payload = {
            "entries": DictionaryEntrySerializer(entries, many=True).data,
            "meanings": WordMeaningSerializer(meanings, many=True).data,
            "translations": TranslationSerializer(
                Translation.objects.filter(entry_id__in=entry_ids).order_by("id")
                if entry_ids
                else Translation.objects.none(),
                many=True,
            ).data,
            "examples": ExampleSentenceSerializer(
                ExampleSentence.objects.filter(entry_id__in=entry_ids).order_by("id")
                if entry_ids
                else ExampleSentence.objects.none(),
                many=True,
            ).data,
            "synonyms": SynonymAntonymSerializer(
                SynonymAntonym.objects.filter(meaning_id__in=meaning_ids).order_by("id")
                if meaning_ids
                else SynonymAntonym.objects.none(),
                many=True,
            ).data,
            "forms": WordFormSerializer(
                WordForm.objects.filter(entry_id__in=entry_ids).order_by("id")
                if entry_ids
                else WordForm.objects.none(),
                many=True,
            ).data,
            "collocations": CollocationSerializer(
                Collocation.objects.filter(entry_id__in=entry_ids).order_by("id")
                if entry_ids
                else Collocation.objects.none(),
                many=True,
            ).data,
            "nextAfterId": entries[-1].id if entries else after_id,
            "hasMore": has_more,
            "serverTime": timezone.now().isoformat(),
        }
        if after_id == 0:
            payload["dictionary"] = _dictionary_meta()
        return Response(payload)


def _dictionary_meta():
    entries = DictionaryEntry.objects.all()
    return {
        "entries": entries.count(),
        "maxEntryId": entries.order_by("-id").values_list("id", flat=True).first() or 0,
        "missingRussian": entries.filter(primary_meaning_ru="").count(),
        "missingUzbek": entries.filter(primary_meaning_uz="").count(),
    }


def _query_int(request, name, default, maximum=None):
    raw = request.query_params.get(name)
    if raw in (None, ""):
        return default
    try:
        value = int(raw)
    except (TypeError, ValueError) as exc:
        raise ValidationError({name: "Butun son bo'lishi kerak."}) from exc
    if value < 0:
        raise ValidationError({name: "Manfiy bo'lishi mumkin emas."})
    return min(value, maximum) if maximum is not None else value


def _parse_since(raw):
    if not raw:
        return None
    parsed = parse_datetime(raw)
    if parsed is None:
        raise ValidationError({"since": "ISO 8601 sana formati kutilgan."})
    if timezone.is_naive(parsed):
        parsed = parsed.replace(tzinfo=dt_timezone.utc)
    return parsed


class CurriculumCourseViewSet(PublicReadOnlyViewSet):
    queryset = CurriculumCourse.objects.all()
    serializer_class = CurriculumCourseSerializer


class CurriculumLevelViewSet(PublicReadOnlyViewSet):
    queryset = CurriculumLevel.objects.all()
    serializer_class = CurriculumLevelSerializer


class CurriculumUnitViewSet(PublicReadOnlyViewSet):
    queryset = CurriculumUnit.objects.all()
    serializer_class = CurriculumUnitSerializer


class CurriculumTopicViewSet(PublicReadOnlyViewSet):
    queryset = CurriculumTopic.objects.all()
    serializer_class = CurriculumTopicSerializer

    def get_queryset(self):
        qs = super().get_queryset()
        language = self.request.query_params.get("language")
        level = self.request.query_params.get("level")
        category = self.request.query_params.get("category")
        if language:
            qs = qs.filter(language=language)
        if level:
            qs = qs.filter(cefr_level=level)
        if category:
            qs = qs.filter(category=category)
        return qs


class LessonContentViewSet(PublicReadOnlyViewSet):
    queryset = LessonContent.objects.all()
    serializer_class = LessonContentSerializer


class CurriculumExerciseViewSet(PublicReadOnlyViewSet):
    queryset = CurriculumExercise.objects.all()
    serializer_class = CurriculumExerciseSerializer

    def get_queryset(self):
        qs = super().get_queryset()
        language = self.request.query_params.get("language")
        level = self.request.query_params.get("level")
        topic_id = self.request.query_params.get("topicId")
        exercise_type = self.request.query_params.get("type")
        if language:
            qs = qs.filter(language=language)
        if level:
            qs = qs.filter(cefr_level=level)
        if topic_id:
            qs = qs.filter(topic_id=topic_id)
        if exercise_type:
            qs = qs.filter(exercise_type=exercise_type)
        return qs


class TestBankQuestionViewSet(PublicReadOnlyViewSet):
    queryset = TestBankQuestion.objects.all()
    serializer_class = TestBankQuestionSerializer

    def get_queryset(self):
        qs = super().get_queryset()
        language = self.request.query_params.get("language")
        level = self.request.query_params.get("level")
        topic_id = self.request.query_params.get("topicId")
        if language:
            qs = qs.filter(language=language)
        if level:
            qs = qs.filter(cefr_level=level)
        if topic_id:
            qs = qs.filter(topic_id=topic_id)
        return qs


class UserTopicProgressViewSet(viewsets.ModelViewSet):
    permission_classes = [permissions.IsAuthenticated]
    serializer_class = UserTopicProgressSerializer

    def get_queryset(self):
        return UserTopicProgress.objects.filter(user=self.request.user)

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


class MistakeRecordViewSet(viewsets.ModelViewSet):
    permission_classes = [permissions.IsAuthenticated]
    serializer_class = MistakeRecordSerializer

    def get_queryset(self):
        return MistakeRecord.objects.filter(user=self.request.user)

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


class ContentInventoryDashboardView(APIView):
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        # Requirements:
        # Target: 10,000 EN exercises, 10,000 RU exercises, 10,000 EN tests, 10,000 RU tests, 5,000 EN topics, 5,000 RU topics
        # Total: 50,000 learning units
        targets = {
            "en_exercises": 10000,
            "ru_exercises": 10000,
            "en_tests": 10000,
            "ru_tests": 10000,
            "en_topics": 5000,
            "ru_topics": 5000,
            "total_units": 50000,
        }

        en_ex = CurriculumExercise.objects.filter(language="en")
        ru_ex = CurriculumExercise.objects.filter(language="ru")
        en_tests = TestBankQuestion.objects.filter(language="en")
        ru_tests = TestBankQuestion.objects.filter(language="ru")
        en_topics = CurriculumTopic.objects.filter(language="en")
        ru_topics = CurriculumTopic.objects.filter(language="ru")

        gen_en_ex = en_ex.count()
        gen_ru_ex = ru_ex.count()
        gen_en_tests = en_tests.count()
        gen_ru_tests = ru_tests.count()
        gen_en_topics = en_topics.count()
        gen_ru_topics = ru_topics.count()

        pub_en_ex = en_ex.filter(publication_status="published").count()
        pub_ru_ex = ru_ex.filter(publication_status="published").count()
        pub_en_tests = en_tests.filter(publication_status="published").count()
        pub_ru_tests = ru_tests.filter(publication_status="published").count()
        pub_en_topics = CurriculumTopic.objects.filter(language="en", lesson_content__publication_status="published").count()
        pub_ru_topics = CurriculumTopic.objects.filter(language="ru", lesson_content__publication_status="published").count()

        total_generated = gen_en_ex + gen_ru_ex + gen_en_tests + gen_ru_tests + gen_en_topics + gen_ru_topics
        total_published = pub_en_ex + pub_ru_ex + pub_en_tests + pub_ru_tests + pub_en_topics + pub_ru_topics

        return Response({
            "target": targets,
            "generated": {
                "enExercises": gen_en_ex,
                "ruExercises": gen_ru_ex,
                "enTests": gen_en_tests,
                "ruTests": gen_ru_tests,
                "enTopics": gen_en_topics,
                "ruTopics": gen_ru_topics,
                "total": total_generated,
            },
            "published": {
                "enExercises": pub_en_ex,
                "ruExercises": pub_ru_ex,
                "enTests": pub_en_tests,
                "ruTests": pub_ru_tests,
                "enTopics": pub_en_topics,
                "ruTopics": pub_ru_topics,
                "total": total_published,
            },
            "completionPercentage": {
                "enExercises": round((pub_en_ex / targets["en_exercises"]) * 100, 2),
                "ruExercises": round((pub_ru_ex / targets["ru_exercises"]) * 100, 2),
                "enTests": round((pub_en_tests / targets["en_tests"]) * 100, 2),
                "ruTests": round((pub_ru_tests / targets["ru_tests"]) * 100, 2),
                "enTopics": round((pub_en_topics / targets["en_topics"]) * 100, 2),
                "ruTopics": round((pub_ru_topics / targets["ru_topics"]) * 100, 2),
                "overall": round((total_published / targets["total_units"]) * 100, 2),
            },
            "validationFailures": CurriculumExercise.objects.filter(validation_status="invalid").count() + TestBankQuestion.objects.filter(validation_status="invalid").count(),
            "duplicates": ContentBatch.objects.aggregate(total=Sum("duplicate_count"))["total"] or 0
        })

