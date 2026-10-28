"""Tests for the bulk lexicon importer and the integrity report."""

import json
import tempfile
from pathlib import Path

from django.core.management import call_command
from django.test import TestCase

from apps.dictionary.entries.models import DictionaryEntry, Translation


class LexiconImportCommandTests(TestCase):
    def write_jsonl(self, rows) -> str:
        handle = tempfile.NamedTemporaryFile(
            "w", suffix=".jsonl", delete=False, encoding="utf-8"
        )
        with handle as stream:
            for row in rows:
                stream.write(json.dumps(row) + "\n")
        return handle.name

    def test_dry_run_reports_without_writing(self):
        path = self.write_jsonl([{"headword": "apple"}, {"headword": "pear"}])

        call_command("import_lexicon", path, "--dry-run", verbosity=0)

        self.assertEqual(DictionaryEntry.objects.count(), 0)

    def test_import_counts_invalid_and_duplicate_rows(self):
        path = self.write_jsonl(
            [
                {"headword": "apple"},
                {"headword": "Apple"},
                {"headword": ""},
                {"headword": "pear"},
            ]
        )

        call_command("import_lexicon", path, verbosity=0)

        self.assertEqual(DictionaryEntry.objects.count(), 2)
        entries = DictionaryEntry.objects.order_by("headword_normalized")
        self.assertEqual(
            [entry.headword_normalized for entry in entries], ["apple", "pear"]
        )

    def test_jsonl_translations_are_stored(self):
        path = self.write_jsonl(
            [
                {
                    "headword": "wallet",
                    "translations": {"uz": ["hamyon"], "ru": ["бумажник"]},
                }
            ]
        )

        call_command("import_lexicon", path, verbosity=0)

        entry = DictionaryEntry.objects.get(headword_normalized="wallet")
        languages = set(
            Translation.objects.filter(entry=entry).values_list(
                "target_language", flat=True
            )
        )
        self.assertEqual(languages, {"uz", "ru"})


class DirectoryIntegrityCommandTests(TestCase):
    def test_reports_empty_dictionary_as_healthy(self):
        from io import StringIO

        output = StringIO()

        call_command("check_dictionary_integrity", stdout=output)

        report = output.getvalue()
        self.assertIn("Qidiruv indeksi holati", report)
        self.assertNotIn("indeks 0 hujjat", report)
