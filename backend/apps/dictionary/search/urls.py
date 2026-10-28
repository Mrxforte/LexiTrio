from django.urls import path
from rest_framework.routers import SimpleRouter

from .views import DictionaryEntryViewSet, DictionarySearchView

router = SimpleRouter()
router.register("dictionary/entries", DictionaryEntryViewSet)

urlpatterns = [
    path("dictionary/search/", DictionarySearchView.as_view(), name="dictionary-search"),
    *router.urls,
]
