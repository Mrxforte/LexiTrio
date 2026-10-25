"""Views for the public learning catalog."""

from datetime import timezone as dt_timezone

from django.db.models import Q
from django.utils import timezone
from django.utils.dateparse import parse_datetime
from rest_framework import filters, permissions, viewsets
from rest_framework.exceptions import ValidationError
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle
from rest_framework.views import APIView

from .models import (
    Collocation,
    Conversation,
    ConversationTurn,
    DictionaryEntry,
    ExampleSentence,
    GrammarLesson,
    QuizQuestion,
    SynonymAntonym,
    Topic,
    TopicWord,
    Translation,
    WordForm,
    WordMeaning,
)
from .serializers import (
    CollocationSerializer,
    ConversationSerializer,
    ConversationTurnSerializer,
    DictionaryEntrySerializer,
    ExampleSentenceSerializer,
    GrammarLessonSerializer,
    QuizQuestionSerializer,
    SynonymAntonymSerializer,
    TopicSerializer,
    TopicWordSerializer,
    TranslationSerializer,
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
        query = self.request.query_params.get("q", "").strip()
        language = self.request.query_params.get("language")
        level = self.request.query_params.get("level")
        if query:
            queryset = queryset.filter(
                Q(headword__icontains=query)
                | Q(headword_normalized__icontains=query)
                | Q(primary_meaning_en__icontains=query)
                | Q(primary_meaning_ru__icontains=query)
                | Q(primary_meaning_uz__icontains=query)
                | Q(translations__translation_text__icontains=query)
            ).distinct()
        if language:
            queryset = queryset.filter(language=language)
        if level:
            queryset = queryset.filter(cefr_level=level)
        return queryset


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
