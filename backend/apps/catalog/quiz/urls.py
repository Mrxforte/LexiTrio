from rest_framework.routers import SimpleRouter

from .views import QuizQuestionViewSet

router = SimpleRouter()
router.register("quiz-questions", QuizQuestionViewSet)

urlpatterns = router.urls
