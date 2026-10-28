from shared.views import PublicReadOnlyViewSet

from .models import GrammarLesson
from .serializers import GrammarLessonSerializer


class GrammarLessonViewSet(PublicReadOnlyViewSet):
    queryset = GrammarLesson.objects.all()
    serializer_class = GrammarLessonSerializer
