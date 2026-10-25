from rest_framework.test import APITestCase

from .models import DictionaryEntry, Translation


class CatalogApiTests(APITestCase):
    def test_catalog_snapshot_returns_the_android_corpus_shape(self):
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
        self.assertEqual(response.data["dictionaryEntries"][0]["headwordNormalized"], "hello")
        self.assertEqual(response.data["translations"][0]["entryId"], entry.id)
        self.assertIn("conversationTurns", response.data)

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
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]["language"], "en")
