from io import StringIO
from pathlib import Path

from django.core.management import call_command
from django.core.management.base import CommandError
from django.test import TestCase


class DeploymentGateTests(TestCase):
    def test_empty_database_cannot_be_published_as_full_catalog(self):
        with self.assertRaisesMessage(CommandError, "Upload the full database"):
            call_command("verify_deployment", stdout=StringIO())

    def test_translations_are_required_even_with_explicit_small_catalog(self):
        with self.assertRaisesMessage(CommandError, "translations are missing"):
            call_command("verify_deployment", minimum_entries=0, stdout=StringIO())

    def test_seed_can_only_pass_with_an_explicit_small_catalog_override(self):
        call_command("import_catalog", str(Path(__file__).resolve().parents[1] / "catalog_seed.json"), stdout=StringIO())
        output = StringIO()
        call_command("verify_deployment", minimum_entries=13, stdout=output)
        self.assertIn('"entries": 13', output.getvalue())
