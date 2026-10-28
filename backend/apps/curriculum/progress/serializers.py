from shared.serializers import CamelCaseFieldsSerializer

from .models import MistakeRecord, UserTopicProgress


class UserTopicProgressSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = UserTopicProgress
        fields = "__all__"


class MistakeRecordSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = MistakeRecord
        fields = "__all__"
