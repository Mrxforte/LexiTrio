"""Public curriculum endpoints and the content inventory."""

from rest_framework import permissions
from rest_framework.response import Response
from rest_framework.views import APIView

from shared.views import PublicReadOnlyViewSet

from .models import (
    CurriculumCourse,
    CurriculumExercise,
    CurriculumLevel,
    CurriculumTopic,
    CurriculumUnit,
    LessonContent,
    TestBankQuestion,
)
from .serializers import (
    CurriculumCourseSerializer,
    CurriculumExerciseSerializer,
    CurriculumLevelSerializer,
    CurriculumTopicSerializer,
    CurriculumUnitSerializer,
    LessonContentSerializer,
    TestBankQuestionSerializer,
)
from .services.inventory import content_inventory


class CurriculumCourseViewSet(PublicReadOnlyViewSet):
    queryset = CurriculumCourse.objects.all()
    serializer_class = CurriculumCourseSerializer


class CurriculumLevelViewSet(PublicReadOnlyViewSet):
    queryset = CurriculumLevel.objects.all()
    serializer_class = CurriculumLevelSerializer


class CurriculumUnitViewSet(PublicReadOnlyViewSet):
    queryset = CurriculumUnit.objects.all()
    serializer_class = CurriculumUnitSerializer


class CurriculumTopicViewSet(PublicReadOnlyViewSet):
    queryset = CurriculumTopic.objects.all()
    serializer_class = CurriculumTopicSerializer

    def get_queryset(self):
        qs = super().get_queryset()
        language = self.request.query_params.get("language")
        level = self.request.query_params.get("level")
        category = self.request.query_params.get("category")
        if language:
            qs = qs.filter(language=language)
        if level:
            qs = qs.filter(cefr_level=level)
        if category:
            qs = qs.filter(category=category)
        return qs


class LessonContentViewSet(PublicReadOnlyViewSet):
    queryset = LessonContent.objects.all()
    serializer_class = LessonContentSerializer


class CurriculumExerciseViewSet(PublicReadOnlyViewSet):
    queryset = CurriculumExercise.objects.all()
    serializer_class = CurriculumExerciseSerializer

    def get_queryset(self):
        qs = super().get_queryset()
        language = self.request.query_params.get("language")
        level = self.request.query_params.get("level")
        topic_id = self.request.query_params.get("topicId")
        exercise_type = self.request.query_params.get("type")
        if language:
            qs = qs.filter(language=language)
        if level:
            qs = qs.filter(cefr_level=level)
        if topic_id:
            qs = qs.filter(topic_id=topic_id)
        if exercise_type:
            qs = qs.filter(exercise_type=exercise_type)
        return qs


class TestBankQuestionViewSet(PublicReadOnlyViewSet):
    queryset = TestBankQuestion.objects.all()
    serializer_class = TestBankQuestionSerializer

    def get_queryset(self):
        qs = super().get_queryset()
        language = self.request.query_params.get("language")
        level = self.request.query_params.get("level")
        topic_id = self.request.query_params.get("topicId")
        if language:
            qs = qs.filter(language=language)
        if level:
            qs = qs.filter(cefr_level=level)
        if topic_id:
            qs = qs.filter(topic_id=topic_id)
        return qs


class ContentInventoryDashboardView(APIView):
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        inventory = content_inventory()
        generated = inventory["generated"]
        published = inventory["published"]
        completion = inventory["completion"]
        return Response(
            {
                "target": inventory["targets"],
                "generated": {
                    "enExercises": generated["en_exercises"],
                    "ruExercises": generated["ru_exercises"],
                    "enTests": generated["en_tests"],
                    "ruTests": generated["ru_tests"],
                    "enTopics": generated["en_topics"],
                    "ruTopics": generated["ru_topics"],
                    "total": inventory["total_generated"],
                },
                "published": {
                    "enExercises": published["en_exercises"],
                    "ruExercises": published["ru_exercises"],
                    "enTests": published["en_tests"],
                    "ruTests": published["ru_tests"],
                    "enTopics": published["en_topics"],
                    "ruTopics": published["ru_topics"],
                    "total": inventory["total_published"],
                },
                "completionPercentage": {
                    "enExercises": completion["en_exercises"],
                    "ruExercises": completion["ru_exercises"],
                    "enTests": completion["en_tests"],
                    "ruTests": completion["ru_tests"],
                    "enTopics": completion["en_topics"],
                    "ruTopics": completion["ru_topics"],
                    "overall": completion["overall"],
                },
                "validationFailures": inventory["validation_failures"],
                "duplicates": inventory["duplicates"],
            }
        )
