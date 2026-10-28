from django.apps import AppConfig


class CatalogTopicsConfig(AppConfig):
    """Vocabulary topics and the words that belong to them."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.catalog.topics"
    label = "catalog_topics"
    verbose_name = "Vocabulary topics"
