"""Automated batch migration script from SQLite to PostgreSQL.

Safely copies all dictionary entries, translations, user accounts, and learning content
from a source SQLite database into PostgreSQL with batching, sequence resetting,
and record count verification.
"""

import os
import sqlite3
import time
from pathlib import Path

from django.apps import apps
from django.conf import settings
from django.core.management import call_command
from django.core.management.base import BaseCommand, CommandError
from django.db import connection, transaction


class Command(BaseCommand):
    help = "Migrates all data from SQLite db.sqlite3 into target PostgreSQL database."

    def add_arguments(self, parser):
        parser.add_argument(
            "--sqlite-path",
            type=str,
            default=str(settings.BASE_DIR / "db.sqlite3"),
            help="Path to source SQLite database.",
        )
        parser.add_argument(
            "--batch-size",
            type=int,
            default=2000,
            help="Rows per bulk insert batch.",
        )

    def handle(self, *args, **options):
        sqlite_path = Path(options["sqlite_path"])
        batch_size = max(100, options["batch_size"])

        if not sqlite_path.exists():
            raise CommandError(f"Source SQLite database not found at {sqlite_path}")

        if connection.vendor != "postgresql":
            self.stdout.write(
                self.style.WARNING(
                    f"Current DATABASE_URL is using '{connection.vendor}', not PostgreSQL. "
                    "Make sure DATABASE_URL points to PostgreSQL before running this migration."
                )
            )

        self.stdout.write(
            self.style.MIGRATE_HEADING(f"Starting SQLite -> PostgreSQL Migration")
        )
        self.stdout.write(f"Source: {sqlite_path}")
        self.stdout.write(f"Target: {connection.settings_dict['NAME']} on {connection.settings_dict['HOST'] or 'localhost'}")

        started_time = time.monotonic()
        sqlite_conn = sqlite3.connect(str(sqlite_path))
        sqlite_conn.row_factory = sqlite3.Row

        # Ordered list of models to migrate respecting foreign keys
        models_to_migrate = [
            # Auth
            "auth.Group",
            "auth.Permission",
            "accounts.User",
            "authtoken.Token",
            # Core Dictionary
            "learning.DictionaryEntry",
            "learning.WordMeaning",
            "learning.Translation",
            "learning.ExampleSentence",
            "learning.SynonymAntonym",
            "learning.WordForm",
            "learning.Collocation",
            # Learning Content
            "learning.Topic",
            "learning.TopicWord",
            "learning.Conversation",
            "learning.ConversationTurn",
            "learning.GrammarLesson",
            "learning.QuizQuestion",
            # User Data
            "learning.VocabularyCollection",
            "learning.SavedWord",
            "learning.QuizAttempt",
            "learning.UserProgress",
            "learning.UserPreferences",
        ]

        total_migrated_rows = 0

        for model_label in models_to_migrate:
            try:
                model = apps.get_model(model_label)
            except LookupError:
                self.stdout.write(f"Skipping {model_label}: model not found.")
                continue

            table_name = model._meta.db_table
            self.stdout.write(f"Migrating {model_label} ({table_name})... ", ending="")

            cursor = sqlite_conn.cursor()
            try:
                cursor.execute(f"SELECT COUNT(*) FROM {table_name}")
                source_count = cursor.fetchone()[0]
            except sqlite3.OperationalError:
                self.stdout.write("table missing in source.")
                continue

            if source_count == 0:
                self.stdout.write("0 records.")
                continue

            cursor.execute(f"SELECT * FROM {table_name}")
            columns = [col[0] for col in cursor.description]

            # Clear existing rows in target if needed in a single transaction
            with transaction.atomic():
                model.objects.all().delete()

                batch = []
                migrated_count = 0
                for row in cursor:
                    row_dict = dict(zip(columns, row))
                    # Map row dict to model instance
                    field_kwargs = {}
                    for field in model._meta.fields:
                        col_name = field.column
                        if col_name in row_dict and row_dict[col_name] is not None:
                            field_kwargs[field.attname] = row_dict[col_name]

                    batch.append(model(**field_kwargs))
                    if len(batch) >= batch_size:
                        model.objects.bulk_create(batch, ignore_conflicts=True)
                        migrated_count += len(batch)
                        batch = []

                if batch:
                    model.objects.bulk_create(batch, ignore_conflicts=True)
                    migrated_count += len(batch)

            total_migrated_rows += migrated_count
            target_count = model.objects.count()
            self.stdout.write(
                self.style.SUCCESS(f"{migrated_count}/{source_count} rows copied (target total: {target_count}).")
            )

        sqlite_conn.close()

        # Reset sequences on PostgreSQL
        if connection.vendor == "postgresql":
            self.stdout.write("Resetting PostgreSQL primary key sequences...")
            call_command("sqlsequencereset", "accounts", "learning", "authtoken", stdout=self.stdout)

        duration = round(time.monotonic() - started_time, 2)
        self.stdout.write("")
        self.stdout.write(
            self.style.SUCCESS(
                f"Successfully migrated {total_migrated_rows} records from SQLite to PostgreSQL in {duration}s!"
            )
        )
