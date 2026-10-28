from django.apps import AppConfig


class CatalogSnapshotConfig(AppConfig):
    """The one-request learning-content snapshot the Android app downloads, plus its seed importer."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.catalog.snapshot"
    label = "catalog_snapshot"
    verbose_name = "Catalog snapshot"
