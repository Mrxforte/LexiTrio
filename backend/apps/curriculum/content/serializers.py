from shared.serializers import CamelCaseFieldsSerializer

from .models import (
    ContentBatch,
    CurriculumCourse,
    CurriculumExercise,
    CurriculumLevel,
    CurriculumTopic,
    CurriculumUnit,
    GrammarTable,
    LessonContent,
    TestBankQuestion,
)


class CurriculumCourseSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = CurriculumCourse
        fields = "__all__"


class CurriculumLevelSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = CurriculumLevel
        fields = "__all__"


class CurriculumUnitSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = CurriculumUnit
        fields = "__all__"


class CurriculumTopicSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = CurriculumTopic
        fields = "__all__"


class GrammarTableSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = GrammarTable
        fields = "__all__"


class LessonContentSerializer(CamelCaseFieldsSerializer):
    grammarTables = GrammarTableSerializer(source="grammar_tables", many=True, read_only=True)

    class Meta:
        model = LessonContent
        fields = (
            "id",
            "topic",
            "simple_explanation_en",
            "simple_explanation_ru",
            "simple_explanation_uz",
            "real_life_analogy_en",
            "real_life_analogy_ru",
            "real_life_analogy_uz",
            "main_rule_en",
            "main_rule_ru",
            "main_rule_uz",
            "positive_examples_json",
            "negative_examples_json",
            "question_forms_json",
            "exceptions_json",
            "common_mistakes_json",
            "recap_en",
            "recap_ru",
            "recap_uz",
            "review_recommendation_en",
            "review_recommendation_ru",
            "publication_status",
            "grammarTables",
        )


class CurriculumExerciseSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = CurriculumExercise
        fields = "__all__"


class TestBankQuestionSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = TestBankQuestion
        fields = "__all__"


class ContentBatchSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = ContentBatch
        fields = "__all__"
