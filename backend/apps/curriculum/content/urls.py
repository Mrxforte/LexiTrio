from django.urls import path
from rest_framework.routers import SimpleRouter

from .views import (
    ContentInventoryDashboardView,
    CurriculumCourseViewSet,
    CurriculumExerciseViewSet,
    CurriculumLevelViewSet,
    CurriculumTopicViewSet,
    CurriculumUnitViewSet,
    LessonContentViewSet,
    TestBankQuestionViewSet,
)

router = SimpleRouter()
router.register("curriculum/courses", CurriculumCourseViewSet)
router.register("curriculum/levels", CurriculumLevelViewSet)
router.register("curriculum/units", CurriculumUnitViewSet)
router.register("curriculum/topics", CurriculumTopicViewSet)
router.register("curriculum/lessons", LessonContentViewSet)
router.register("curriculum/exercises", CurriculumExerciseViewSet)
router.register("curriculum/tests", TestBankQuestionViewSet)

urlpatterns = [
    path("curriculum/inventory/", ContentInventoryDashboardView.as_view(), name="content-inventory"),
    *router.urls,
]
