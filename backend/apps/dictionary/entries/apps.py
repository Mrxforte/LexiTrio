from django.apps import AppConfig


class DictionaryEntriesConfig(AppConfig):
    """Dictionary words and everything attached to them (meanings, translations, examples, ...).

    The label stays ``dictionary`` and the tables keep their original
    ``learning_*`` names (see ``Meta.db_table`` on the models), so no data moved
    when the old ``learning`` app was split.
    """

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.dictionary.entries"
    label = "dictionary"
    verbose_name = "Dictionary"

    def ready(self):
        from . import signals  # noqa: F401 - cache invalidation and deletion tombstones
