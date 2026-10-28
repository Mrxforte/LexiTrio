from django.apps import AppConfig


class LearningConfig(AppConfig):
    """Legacy app kept only for its migration history.

    Its models now live in ``apps.dictionary``, ``apps.catalog``, ``apps.curriculum``
    and ``apps.userdata``; the tables still carry the ``learning_`` prefix. The
    migrations stay because a fresh database creates those tables here, and the
    new apps' migrations then adopt them without touching the schema.
    """

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.learning"
    label = "learning"
    verbose_name = "Legacy schema history"
