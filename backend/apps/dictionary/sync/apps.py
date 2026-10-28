from django.apps import AppConfig


class DictionarySyncConfig(AppConfig):
    """Dictionary delta sync and batched word details for the Android offline cache."""

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.dictionary.sync"
    label = "dictionary_sync"
    verbose_name = "Dictionary sync"
