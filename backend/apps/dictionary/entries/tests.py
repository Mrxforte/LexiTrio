from django.core.management import call_command
from django.test import TestCase
from rest_framework.test import APITestCase

from apps.dictionary.entries.management.commands.build_reverse_entries import (
    SOURCE,
    clean_terms,
    normalize_term,
)
from apps.dictionary.entries.models import DictionaryEntry, Translation


class CleanTermsTests(TestCase):
    def test_strips_part_of_speech_markers_and_splits(self):
        self.assertEqual(clean_terms("kitob, jild (ot)", "uz"), ["kitob", "jild"])
        self.assertEqual(clean_terms("книга, книжка (сущ.)", "ru"), ["книга", "книжка"])

    def test_rejects_terms_in_the_wrong_script(self):
        self.assertEqual(clean_terms("book, книга", "ru"), ["книга"])
        self.assertEqual(clean_terms("kitob, книга", "uz"), ["kitob"])

    def test_uzbek_apostrophes_are_unified(self):
        self.assertEqual(normalize_term("O'zbek", "uz"), "o‘zbek")
        self.assertEqual(normalize_term("Oʻzbek", "uz"), "o‘zbek")


class EntryBrowseApiTests(APITestCase):
    def test_entry_list_is_paged(self):
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

    def test_a_free_text_query_is_answered_by_the_search_index_and_filters_by_language(self):
        DictionaryEntry.objects.create(headword="book", headword_normalized="book", language="en")
        DictionaryEntry.objects.create(headword="книга", headword_normalized="книга", language="ru")

        response = self.client.get("/api/v1/dictionary/entries/?q=book&language=en")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["page"], 1)
        self.assertEqual(len(response.data["results"]), 1)
        self.assertEqual(response.data["results"][0]["language"], "en")


class BuildReverseEntriesTests(TestCase):
    def setUp(self):
        book = DictionaryEntry.objects.create(
            headword="book", headword_normalized="book", language="en", frequency_rank=900
        )
        Translation.objects.create(entry=book, target_language="ru", translation_text="книга, том (сущ.)")
        Translation.objects.create(entry=book, target_language="uz", translation_text="kitob (ot)")

    def test_creates_ru_and_uz_headwords_pointing_to_english(self):
        call_command("build_reverse_entries", verbosity=0)

        russian = DictionaryEntry.objects.get(language="ru", headword_normalized="книга")
        uzbek = DictionaryEntry.objects.get(language="uz", headword_normalized="kitob")
        self.assertEqual(russian.primary_meaning_en, "book")
        self.assertEqual(russian.primary_meaning_uz, "kitob")
        self.assertEqual(uzbek.primary_meaning_ru, "книга, том")
        self.assertEqual(russian.frequency_rank, 900)
        self.assertEqual(russian.source, SOURCE)

    def test_is_idempotent_and_purgeable(self):
        call_command("build_reverse_entries", verbosity=0)
        total = DictionaryEntry.objects.count()
        call_command("build_reverse_entries", verbosity=0)
        self.assertEqual(DictionaryEntry.objects.count(), total)

        call_command("build_reverse_entries", "--purge", verbosity=0)
        self.assertEqual(DictionaryEntry.objects.count(), 1)
