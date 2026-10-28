from shared.views import PublicReadOnlyViewSet

from .models import QuizQuestion
from .serializers import QuizQuestionSerializer


class QuizQuestionViewSet(PublicReadOnlyViewSet):
    queryset = QuizQuestion.objects.all()
    serializer_class = QuizQuestionSerializer
