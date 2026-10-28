from rest_framework import permissions, viewsets

from .models import MistakeRecord, UserTopicProgress
from .serializers import MistakeRecordSerializer, UserTopicProgressSerializer


class _OwnRecordsViewSet(viewsets.ModelViewSet):
    """Authenticated learners read and write only their own rows."""

    permission_classes = [permissions.IsAuthenticated]
    model = None

    def get_queryset(self):
        return self.model.objects.filter(user=self.request.user)

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)


class UserTopicProgressViewSet(_OwnRecordsViewSet):
    model = UserTopicProgress
    serializer_class = UserTopicProgressSerializer


class MistakeRecordViewSet(_OwnRecordsViewSet):
    model = MistakeRecord
    serializer_class = MistakeRecordSerializer
