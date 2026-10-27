from django.contrib import admin
from django.urls import include, path

from .views import HealthCheckView

urlpatterns = [
    path("health/", HealthCheckView.as_view(), name="root-health"),
    path("api/v1/health/", HealthCheckView.as_view(), name="health"),
    path("admin/", admin.site.urls),
    path("api/v1/auth/", include("accounts.urls")),
    path("api/v1/", include("learning.urls")),
    path("api/v1/", include("translation.urls")),
    path("api/v1/", include("syncing.urls")),
]
