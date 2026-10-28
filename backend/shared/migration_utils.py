"""Helpers for migrations that move models between apps without touching the database.

When the single ``learning`` app was split, its tables kept their names
(``Meta.db_table = "learning_..."``). The new apps' initial migrations therefore
only *adopt* those tables: they change Django's migration state, run no DDL, and
re-label the existing content types so permissions and admin history follow the
models to their new app.
"""

from django.db import migrations

LEGACY_APP_LABEL = "learning"
LEGACY_LAST_MIGRATION = ("learning", "0012_dictionarytombstone")
CONTENT_TYPES_MIGRATION = ("contenttypes", "0002_remove_content_type_name")


def adopt_legacy_tables(state_operations, *, label, models):
    """Operations for an app's initial migration: state only, plus content-type re-labelling."""

    def relabel_content_types(apps, schema_editor):
        ContentType = apps.get_model("contenttypes", "ContentType")
        for model in models:
            already_moved = ContentType.objects.filter(app_label=label, model=model).exists()
            if not already_moved:
                ContentType.objects.filter(app_label=LEGACY_APP_LABEL, model=model).update(app_label=label)

    return [
        migrations.SeparateDatabaseAndState(state_operations=state_operations),
        migrations.RunPython(relabel_content_types, migrations.RunPython.noop),
    ]


def release_legacy_models(state_operations):
    """Operations for the legacy app's last migration: forget the moved models, state only."""
    return [migrations.SeparateDatabaseAndState(state_operations=state_operations)]
