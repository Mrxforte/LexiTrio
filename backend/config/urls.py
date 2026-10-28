from django.contrib import admin
from django.urls import include, path

from .api import ApiRootView
from .views import HealthCheckView

# Every app owns its URLs; all of them are mounted under /api/v1/.
API_V1_URLCONFS = [
    "apps.catalog.snapshot.urls",
    "apps.dictionary.entries.urls",
    "apps.dictionary.search.urls",
    "apps.dictionary.sync.urls",
    "apps.catalog.topics.urls",
    "apps.catalog.conversations.urls",
    "apps.catalog.grammar.urls",
    "apps.catalog.quiz.urls",
    "apps.curriculum.content.urls",
    "apps.curriculum.progress.urls",
    "apps.translation.urls",
    "apps.syncing.urls",
]

urlpatterns = [
    path("health/", HealthCheckView.as_view(), name="root-health"),
    path("api/v1/health/", HealthCheckView.as_view(), name="health"),
    path("admin/", admin.site.urls),
    path("api/v1/", ApiRootView.as_view(), name="api-root"),
    path("api/v1/auth/", include("apps.accounts.urls")),
    *[path("api/v1/", include(urlconf)) for urlconf in API_V1_URLCONFS],
]
