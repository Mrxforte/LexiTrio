from django.apps import AppConfig


class CatalogConversationsConfig(AppConfig):
    """Dialogues for speaking practice."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.catalog.conversations"
    label = "catalog_conversations"
    verbose_name = "Conversations"
