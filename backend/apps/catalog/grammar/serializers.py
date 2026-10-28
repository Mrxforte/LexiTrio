from shared.serializers import CamelCaseFieldsSerializer

from .models import GrammarLesson


class GrammarLessonSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = GrammarLesson
        fields = "__all__"
