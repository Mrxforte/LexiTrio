from shared.views import PublicReadOnlyViewSet

from .models import Conversation, ConversationTurn
from .serializers import ConversationSerializer, ConversationTurnSerializer


class ConversationViewSet(PublicReadOnlyViewSet):
    queryset = Conversation.objects.all()
    serializer_class = ConversationSerializer


class ConversationTurnViewSet(PublicReadOnlyViewSet):
    queryset = ConversationTurn.objects.all()
    serializer_class = ConversationTurnSerializer
