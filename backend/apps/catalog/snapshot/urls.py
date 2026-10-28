from django.urls import path

from .views import CatalogSnapshotView

urlpatterns = [
    path("catalog/", CatalogSnapshotView.as_view(), name="catalog"),
]
