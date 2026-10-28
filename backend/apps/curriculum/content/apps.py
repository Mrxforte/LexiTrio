from django.apps import AppConfig


class CurriculumContentConfig(AppConfig):
    """The editorial curriculum: courses, levels, topics, lessons, exercises, test bank and content batches."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.curriculum.content"
    label = "curriculum"
    verbose_name = "Curriculum"
