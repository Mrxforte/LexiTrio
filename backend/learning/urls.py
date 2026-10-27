from django.urls import path
from rest_framework.routers import DefaultRouter

from .views import (
    CatalogSnapshotView,
    CollocationViewSet,
    ContentInventoryDashboardView,
    ConversationTurnViewSet,
    ConversationViewSet,
    CurriculumCourseViewSet,
    CurriculumExerciseViewSet,
    CurriculumLevelViewSet,
    CurriculumTopicViewSet,
    CurriculumUnitViewSet,
    DictionaryEntryViewSet,
    DictionarySearchView,
    DictionarySyncView,
    ExampleSentenceViewSet,
    GrammarLessonViewSet,
    LessonContentViewSet,
    MistakeRecordViewSet,
    QuizQuestionViewSet,
    SynonymAntonymViewSet,
    TestBankQuestionViewSet,
    TopicViewSet,
    TopicWordViewSet,
    TranslationViewSet,
    UserTopicProgressViewSet,
    WordFormViewSet,
    WordMeaningViewSet,
)

router = DefaultRouter()
router.register("dictionary/entries", DictionaryEntryViewSet)
router.register("dictionary/meanings", WordMeaningViewSet)
router.register("dictionary/translations", TranslationViewSet)
router.register("dictionary/examples", ExampleSentenceViewSet)
router.register("dictionary/synonyms", SynonymAntonymViewSet)
router.register("dictionary/forms", WordFormViewSet)
router.register("dictionary/collocations", CollocationViewSet)
router.register("topics", TopicViewSet)
router.register("topic-words", TopicWordViewSet)
router.register("conversations", ConversationViewSet)
router.register("conversation-turns", ConversationTurnViewSet)
router.register("grammar-lessons", GrammarLessonViewSet)
router.register("quiz-questions", QuizQuestionViewSet)

# Curriculum routes
router.register("curriculum/courses", CurriculumCourseViewSet)
router.register("curriculum/levels", CurriculumLevelViewSet)
router.register("curriculum/units", CurriculumUnitViewSet)
router.register("curriculum/topics", CurriculumTopicViewSet)
router.register("curriculum/lessons", LessonContentViewSet)
router.register("curriculum/exercises", CurriculumExerciseViewSet)
router.register("curriculum/tests", TestBankQuestionViewSet)
router.register("curriculum/progress", UserTopicProgressViewSet, basename="user-topic-progress")
router.register("curriculum/mistakes", MistakeRecordViewSet, basename="user-mistakes")

urlpatterns = router.urls
urlpatterns.insert(0, path("catalog/", CatalogSnapshotView.as_view(), name="catalog"))
urlpatterns.insert(
    1, path("dictionary/sync/", DictionarySyncView.as_view(), name="dictionary-sync")
)
urlpatterns.insert(
    2, path("dictionary/search/", DictionarySearchView.as_view(), name="dictionary-search")
)
urlpatterns.insert(
    3, path("curriculum/inventory/", ContentInventoryDashboardView.as_view(), name="content-inventory")
)
