from rest_framework.test import APITestCase

from apps.dictionary.entries.models import DictionaryEntry, Translation


class CatalogSnapshotApiTests(APITestCase):
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
