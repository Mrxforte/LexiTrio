from shared.serializers import CamelCaseFieldsSerializer

from .models import QuizQuestion


class QuizQuestionSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = QuizQuestion
        fields = "__all__"
