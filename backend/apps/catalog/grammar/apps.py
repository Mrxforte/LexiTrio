from django.apps import AppConfig


class CatalogGrammarConfig(AppConfig):
    """Short grammar lessons shown in the Grammar section."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.catalog.grammar"
    label = "catalog_grammar"
    verbose_name = "Grammar lessons"
