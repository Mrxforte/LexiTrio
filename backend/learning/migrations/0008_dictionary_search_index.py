"""Build the SQLite FTS5 dictionary search index.

The index is created and populated in one migration, then kept current by
triggers on ``learning_dictionaryentry`` and ``learning_translation``. It is
SQLite-only: other backends keep using the ORM search path, and a SQLite build
without the FTS5 module skips the index instead of failing the migration.
"""

import warnings

from django.db import DatabaseError, migrations, transaction

from learning import fts


def create_index(apps, schema_editor):
    connection = schema_editor.connection
    if connection.vendor != "sqlite":
        return

    try:
        with transaction.atomic(using=connection.alias):
            with connection.cursor() as cursor:
                for statement in fts.create_statements():
                    cursor.execute(statement)
                for statement in fts.rebuild_statements():
                    cursor.execute(statement)
    except DatabaseError as exc:
        warnings.warn(
            "SQLite FTS5 is unavailable; dictionary search will use the slower "
            f"ORM fallback. Reason: {exc}",
            RuntimeWarning,
        )


def drop_index(apps, schema_editor):
    connection = schema_editor.connection
    if connection.vendor != "sqlite":
        return
    with connection.cursor() as cursor:
        for statement in fts.drop_statements():
            cursor.execute(statement)


class Migration(migrations.Migration):

    dependencies = [
        ("learning", "0007_translation_updated_at"),
    ]

    operations = [
        migrations.RunPython(create_index, drop_index),
    ]
