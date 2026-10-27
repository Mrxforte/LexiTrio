"""PostgreSQL pg_trgm extension and GIN indexes for fast dictionary search.

This migration runs conditionally when deployed on PostgreSQL. On SQLite,
it is a no-op.
"""

from django.db import migrations


def apply_postgres_indexes(apps, schema_editor):
    if schema_editor.connection.vendor != "postgresql":
        return
    with schema_editor.connection.cursor() as cursor:
        cursor.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm;")
        cursor.execute(
            "CREATE INDEX IF NOT EXISTS dict_headword_trgm_idx "
            "ON learning_dictionaryentry USING gin (headword_normalized gin_trgm_ops);"
        )
        cursor.execute(
            "CREATE INDEX IF NOT EXISTS trans_text_trgm_idx "
            "ON learning_translation USING gin (translation_text gin_trgm_ops);"
        )
        cursor.execute(
            "CREATE INDEX IF NOT EXISTS dict_meaning_en_trgm_idx "
            "ON learning_dictionaryentry USING gin (primary_meaning_en gin_trgm_ops);"
        )
        cursor.execute(
            "CREATE INDEX IF NOT EXISTS dict_meaning_ru_trgm_idx "
            "ON learning_dictionaryentry USING gin (primary_meaning_ru gin_trgm_ops);"
        )
        cursor.execute(
            "CREATE INDEX IF NOT EXISTS dict_meaning_uz_trgm_idx "
            "ON learning_dictionaryentry USING gin (primary_meaning_uz gin_trgm_ops);"
        )


def reverse_postgres_indexes(apps, schema_editor):
    if schema_editor.connection.vendor != "postgresql":
        return
    with schema_editor.connection.cursor() as cursor:
        cursor.execute("DROP INDEX IF EXISTS dict_headword_trgm_idx;")
        cursor.execute("DROP INDEX IF EXISTS trans_text_trgm_idx;")
        cursor.execute("DROP INDEX IF EXISTS dict_meaning_en_trgm_idx;")
        cursor.execute("DROP INDEX IF EXISTS dict_meaning_ru_trgm_idx;")
        cursor.execute("DROP INDEX IF EXISTS dict_meaning_uz_trgm_idx;")


class Migration(migrations.Migration):

    dependencies = [
        ("learning", "0009_dictionaryentry_dict_lang_freq_headword_idx_and_more"),
    ]

    operations = [
        migrations.RunPython(apply_postgres_indexes, reverse_postgres_indexes),
    ]
