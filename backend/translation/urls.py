from django.urls import path

from .views import (
    DictionaryBackfillView,
    DocumentTranslateView,
    TextTranslateView,
    TranslationJobCancelView,
    TranslationJobDetailView,
    TranslationJobListCreateView,
    TranslationStatusView,
)

urlpatterns = [
    path("translation/status/", TranslationStatusView.as_view(), name="translation-status"),
    path("translation/text/", TextTranslateView.as_view(), name="translation-text"),
    path("translation/document/", DocumentTranslateView.as_view(), name="translation-document"),
    path("translation/backfill/", DictionaryBackfillView.as_view(), name="translation-backfill"),
    path(
        "translation/jobs/",
        TranslationJobListCreateView.as_view(),
        name="translation-job-list",
    ),
    path(
        "translation/jobs/<uuid:job_id>/",
        TranslationJobDetailView.as_view(),
        name="translation-job-detail",
    ),
    path(
        "translation/jobs/<uuid:job_id>/cancel/",
        TranslationJobCancelView.as_view(),
        name="translation-job-cancel",
    ),
]
