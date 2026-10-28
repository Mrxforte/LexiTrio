from django.apps import AppConfig


class DictionarySearchConfig(AppConfig):
    """Ranked dictionary search: SQLite FTS5 / PostgreSQL trigram / ORM engines, HTTP endpoint and index tools.

    Owns ``/dictionary/search/`` and ``/dictionary/entries/`` (the latter answers
    ``?q=`` queries through the same index).
    """

    default_auto_field = "django.db.models.BigAutoField"
    name = "apps.dictionary.search"
    label = "dictionary_search"
    verbose_name = "Dictionary search"
