from rest_framework.routers import SimpleRouter

from .views import ConversationTurnViewSet, ConversationViewSet

router = SimpleRouter()
router.register("conversations", ConversationViewSet)
router.register("conversation-turns", ConversationTurnViewSet)

urlpatterns = router.urls
