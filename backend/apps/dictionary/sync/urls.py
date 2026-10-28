from django.urls import path

from .views import DictionaryDetailsView, DictionarySyncView

urlpatterns = [
    path("dictionary/sync/", DictionarySyncView.as_view(), name="dictionary-sync"),
    path("dictionary/details/", DictionaryDetailsView.as_view(), name="dictionary-details"),
]
