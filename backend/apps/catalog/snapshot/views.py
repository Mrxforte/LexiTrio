from django.utils import timezone
from rest_framework import permissions
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.catalog.conversations.models import Conversation, ConversationTurn
from apps.catalog.conversations.serializers import ConversationSerializer, ConversationTurnSerializer
from apps.catalog.grammar.models import GrammarLesson
from apps.catalog.grammar.serializers import GrammarLessonSerializer
from apps.catalog.quiz.models import QuizQuestion
from apps.catalog.quiz.serializers import QuizQuestionSerializer
from apps.catalog.topics.models import Topic, TopicWord
from apps.catalog.topics.serializers import TopicSerializer, TopicWordSerializer
from apps.dictionary.sync.services import dictionary_meta


class CatalogSnapshotView(APIView):
    """Learning content snapshot plus dictionary metadata.

    Dictionary rows are served by the dictionary sync endpoint in pages, so the
    Android client never downloads the whole 100k+ corpus in one response.
    """

    permission_classes = [permissions.AllowAny]

    def get(self, request):
        return Response(
            {
                "serverTime": timezone.now().isoformat(),
                "dictionary": dictionary_meta(),
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
