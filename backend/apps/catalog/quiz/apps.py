from django.apps import AppConfig


class CatalogQuizConfig(AppConfig):
    """Multiple-choice quiz question bank."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.catalog.quiz"
    label = "catalog_quiz"
    verbose_name = "Quiz questions"
