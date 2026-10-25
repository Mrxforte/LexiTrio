from django.db.models import Q
from rest_framework import filters, permissions, viewsets
from rest_framework.response import Response
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


class CatalogSnapshotView(APIView):
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        return Response(
            {
                "dictionaryEntries": DictionaryEntrySerializer(
                    DictionaryEntry.objects.all(), many=True
                ).data,
                "wordMeanings": WordMeaningSerializer(WordMeaning.objects.all(), many=True).data,
                "translations": TranslationSerializer(Translation.objects.all(), many=True).data,
                "examples": ExampleSentenceSerializer(
                    ExampleSentence.objects.all(), many=True
                ).data,
                "synonyms": SynonymAntonymSerializer(
                    SynonymAntonym.objects.all(), many=True
                ).data,
                "wordForms": WordFormSerializer(WordForm.objects.all(), many=True).data,
                "collocations": CollocationSerializer(
                    Collocation.objects.all(), many=True
                ).data,
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
