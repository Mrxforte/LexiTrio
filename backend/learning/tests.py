from datetime import timedelta

from django.utils import timezone
from rest_framework.test import APITestCase

from .models import DictionaryEntry, SynonymAntonym, Translation, WordMeaning


class CatalogApiTests(APITestCase):
    def test_catalog_snapshot_returns_learning_content_and_dictionary_meta(self):
        entry = DictionaryEntry.objects.create(
            id=1,
            headword="hello",
            headword_normalized="hello",
            language="en",
            primary_meaning_uz="salom",
        )
        Translation.objects.create(
            entry=entry,
            target_language="uz",
            translation_text="salom",
        )

        response = self.client.get("/api/v1/catalog/")

        self.assertEqual(response.status_code, 200)
        self.assertNotIn("dictionaryEntries", response.data)
        self.assertEqual(response.data["dictionary"]["entries"], 1)
        self.assertEqual(response.data["dictionary"]["missingRussian"], 1)
        self.assertEqual(response.data["dictionary"]["missingUzbek"], 0)
        self.assertIn("conversationTurns", response.data)
        self.assertIn("serverTime", response.data)

    def test_dictionary_search_can_filter_by_language(self):
        DictionaryEntry.objects.create(
            headword="book",
            headword_normalized="book",
            language="en",
        )
        DictionaryEntry.objects.create(
            headword="книга",
            headword_normalized="книга",
            language="ru",
        )

        response = self.client.get("/api/v1/dictionary/entries/?q=book&language=en")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["page"], 1)
        self.assertEqual(len(response.data["results"]), 1)
        self.assertEqual(response.data["results"][0]["language"], "en")

    def test_dictionary_entry_list_is_paged(self):
        for index in range(3):
            DictionaryEntry.objects.create(
                headword=f"word{index}",
                headword_normalized=f"word{index}",
                language="en",
            )

        response = self.client.get("/api/v1/dictionary/entries/?pageSize=2")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data["results"]), 2)
        self.assertIsNotNone(response.data["next"])


class DictionarySyncApiTests(APITestCase):
    def setUp(self):
        self.entries = [
            DictionaryEntry.objects.create(
                headword=f"word{index}",
                headword_normalized=f"word{index}",
                language="en",
                primary_meaning_en=f"meaning {index}",
            )
            for index in range(5)
        ]
        self.meaning = WordMeaning.objects.create(
            entry=self.entries[0], sense_number=1, definition_en="meaning 0"
        )
        SynonymAntonym.objects.create(meaning_id=self.meaning.id, word="synonym")
        Translation.objects.create(
            entry=self.entries[0], target_language="ru", translation_text="слово"
        )

    def test_pages_are_resumable_by_after_id(self):
        first = self.client.get("/api/v1/dictionary/sync/?limit=2")

        self.assertEqual(first.status_code, 200)
        self.assertEqual(len(first.data["entries"]), 2)
        self.assertTrue(first.data["hasMore"])
        self.assertEqual(first.data["nextAfterId"], self.entries[1].id)
        self.assertEqual(first.data["dictionary"]["entries"], 5)

        second = self.client.get(
            f"/api/v1/dictionary/sync/?limit=2&afterId={first.data['nextAfterId']}"
        )
        self.assertEqual(len(second.data["entries"]), 2)
        self.assertEqual(second.data["entries"][0]["id"], self.entries[2].id)
        self.assertNotIn("dictionary", second.data)

    def test_last_page_reports_no_more_rows(self):
        response = self.client.get("/api/v1/dictionary/sync/?limit=50")

        self.assertEqual(len(response.data["entries"]), 5)
        self.assertFalse(response.data["hasMore"])
        self.assertEqual(response.data["nextAfterId"], self.entries[-1].id)

    def test_page_includes_child_rows(self):
        response = self.client.get("/api/v1/dictionary/sync/?limit=1")

        self.assertEqual(len(response.data["meanings"]), 1)
        self.assertEqual(response.data["meanings"][0]["entryId"], self.entries[0].id)
        self.assertEqual(response.data["translations"][0]["translationText"], "слово")
        self.assertEqual(response.data["synonyms"][0]["word"], "synonym")

    def test_since_returns_only_changed_entries(self):
        old = timezone.now() - timedelta(days=1)
        DictionaryEntry.objects.update(updated_at=old)
        WordMeaning.objects.update(updated_at=old)
        DictionaryEntry.objects.filter(pk=self.entries[3].pk).update(
            updated_at=timezone.now()
        )

        response = self.client.get(
            "/api/v1/dictionary/sync/", {"since": old.isoformat()}
        )

        self.assertEqual(
            [row["id"] for row in response.data["entries"]], [self.entries[3].id]
        )

    def test_since_includes_entries_with_changed_meanings(self):
        WordMeaning.objects.create(
            entry=self.entries[2], sense_number=1, definition_en="meaning 2"
        )
        old = timezone.now() - timedelta(days=1)
        DictionaryEntry.objects.update(updated_at=old)
        WordMeaning.objects.update(updated_at=old)
        WordMeaning.objects.filter(entry=self.entries[2]).update(
            updated_at=timezone.now()
        )

        response = self.client.get(
            "/api/v1/dictionary/sync/", {"since": old.isoformat()}
        )

        self.assertEqual(
            [row["id"] for row in response.data["entries"]], [self.entries[2].id]
        )

    def test_limit_is_capped(self):
        response = self.client.get("/api/v1/dictionary/sync/?limit=99999")

        self.assertEqual(response.status_code, 200)
        self.assertLessEqual(len(response.data["entries"]), 500)

    def test_invalid_since_is_rejected(self):
        response = self.client.get("/api/v1/dictionary/sync/?since=not-a-date")

        self.assertEqual(response.status_code, 400)
        self.assertIn("since", response.data)
