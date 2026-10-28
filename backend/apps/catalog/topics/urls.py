from rest_framework.routers import SimpleRouter

from .views import TopicViewSet, TopicWordViewSet

router = SimpleRouter()
router.register("topics", TopicViewSet)
router.register("topic-words", TopicWordViewSet)

urlpatterns = router.urls
