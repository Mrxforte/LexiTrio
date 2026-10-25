from django.urls import path
from rest_framework.routers import DefaultRouter

from .views import (
    CatalogSnapshotView,
    CollocationViewSet,
    ConversationTurnViewSet,
    ConversationViewSet,
    DictionaryEntryViewSet,
    DictionarySyncView,
    ExampleSentenceViewSet,
    GrammarLessonViewSet,
    QuizQuestionViewSet,
    SynonymAntonymViewSet,
    TopicViewSet,
    TopicWordViewSet,
    TranslationViewSet,
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

urlpatterns = router.urls
urlpatterns.insert(0, path("catalog/", CatalogSnapshotView.as_view(), name="catalog"))
urlpatterns.insert(
    1, path("dictionary/sync/", DictionarySyncView.as_view(), name="dictionary-sync")
)
