from shared.views import PublicReadOnlyViewSet

from .models import Topic, TopicWord
from .serializers import TopicSerializer, TopicWordSerializer


class TopicViewSet(PublicReadOnlyViewSet):
    queryset = Topic.objects.all()
    serializer_class = TopicSerializer


class TopicWordViewSet(PublicReadOnlyViewSet):
    queryset = TopicWord.objects.all()
    serializer_class = TopicWordSerializer
