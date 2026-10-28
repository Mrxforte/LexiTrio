from rest_framework.routers import SimpleRouter

from .views import (
    CollocationViewSet,
    ExampleSentenceViewSet,
    SynonymAntonymViewSet,
    TranslationViewSet,
    WordFormViewSet,
    WordMeaningViewSet,
)

router = SimpleRouter()
router.register("dictionary/meanings", WordMeaningViewSet)
router.register("dictionary/translations", TranslationViewSet)
router.register("dictionary/examples", ExampleSentenceViewSet)
router.register("dictionary/synonyms", SynonymAntonymViewSet)
router.register("dictionary/forms", WordFormViewSet)
router.register("dictionary/collocations", CollocationViewSet)

urlpatterns = router.urls
