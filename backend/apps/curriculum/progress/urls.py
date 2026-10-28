from rest_framework.routers import SimpleRouter

from .views import MistakeRecordViewSet, UserTopicProgressViewSet

router = SimpleRouter()
router.register("curriculum/progress", UserTopicProgressViewSet, basename="user-topic-progress")
router.register("curriculum/mistakes", MistakeRecordViewSet, basename="user-mistakes")

urlpatterns = router.urls
