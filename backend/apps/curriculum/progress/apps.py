from django.apps import AppConfig


class CurriculumProgressConfig(AppConfig):
    """A learner's progress on curriculum topics and the mistakes they keep making."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.curriculum.progress"
    label = "curriculum_progress"
    verbose_name = "Curriculum progress"
