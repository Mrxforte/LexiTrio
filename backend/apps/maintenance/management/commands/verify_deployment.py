"""Read-only deployment gate against accidentally publishing the demo catalog."""
import json

from django.core.management.base import BaseCommand, CommandError
from django.db import connection
from django.db.migrations.executor import MigrationExecutor
from apps.dictionary.entries.models import DictionaryEntry, Translation


class Command(BaseCommand):
    help = "Check schema, database integrity and minimum dictionary content before publishing."

    def add_arguments(self, parser):
        parser.add_argument("--minimum-entries", type=int, default=130000)

    def handle(self, *args, **options):
        executor = MigrationExecutor(connection)
        if executor.migration_plan(executor.loader.graph.leaf_nodes()):
            raise CommandError("Apply pending database migrations before publishing.")
        if connection.vendor == "sqlite":
            with connection.cursor() as cursor:
                cursor.execute("PRAGMA quick_check")
                if cursor.fetchone() != ("ok",):
                    raise CommandError("SQLite integrity check failed.")
        entries = DictionaryEntry.objects.count()
        translations = Translation.objects.count()
        minimum = max(0, options["minimum_entries"])
        if entries < minimum:
            raise CommandError(f"Catalog has {entries} entries; at least {minimum} are required. Upload the full database.")
        if not translations:
            raise CommandError("Dictionary translations are missing.")
        self.stdout.write(json.dumps({"entries": entries, "translations": translations, "database": connection.vendor}))
