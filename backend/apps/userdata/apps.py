from django.apps import AppConfig


class UserDataConfig(AppConfig):
    """A learner's own data: saved words, collections, quiz attempts, progress and preferences."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.userdata"
    label = "userdata"
    verbose_name = "Learner data"
