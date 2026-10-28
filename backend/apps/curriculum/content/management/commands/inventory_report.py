from django.core.management.base import BaseCommand

from apps.curriculum.content.services.inventory import content_inventory


class Command(BaseCommand):
    help = "Displays the Content Inventory Status for the 50,000 learning unit target."

    def handle(self, *args, **options):
        inventory = content_inventory()
        targets = inventory["targets"]
        published = inventory["published"]
        completion = inventory["completion"]

        rows = (
            ("English Exercises", "en_exercises"),
            ("Russian Exercises", "ru_exercises"),
            ("English Test Questions", "en_tests"),
            ("Russian Test Questions", "ru_tests"),
            ("English Topics", "en_topics"),
            ("Russian Topics", "ru_topics"),
        )

        self.stdout.write("==================================================")
        self.stdout.write("  LEXITRIO CONTENT INVENTORY DASHBOARD REPORT     ")
        self.stdout.write("==================================================")
        self.stdout.write(f"Target Total Units Required: {targets['total_units']:,}")
        self.stdout.write(f"Total Content Units Generated: {inventory['total_generated']:,}")
        self.stdout.write(
            f"Total Published / Approved: {inventory['total_published']:,} "
            f"({completion['overall']}% overall completion)"
        )
        self.stdout.write("--------------------------------------------------")
        for label, key in rows:
            self.stdout.write(
                f"{label}: {published[key]:,} / {targets[key]:,} ({completion[key]}%)"
            )
        self.stdout.write("==================================================")
