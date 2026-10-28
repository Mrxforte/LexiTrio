from rest_framework.routers import SimpleRouter

from .views import GrammarLessonViewSet

router = SimpleRouter()
router.register("grammar-lessons", GrammarLessonViewSet)

urlpatterns = router.urls
