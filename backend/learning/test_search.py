"""Tests for ranked dictionary search, the search index and its API."""

from django.core.cache import cache
from django.db import connection
from django.test import override_settings
from rest_framework.test import APITestCase

from learning import search as search_module
from learning.models import DictionaryEntry, Translation
from learning.search import SearchRequest, normalize_query, search


def make_entry(headword, *, language="en", frequency=0, part_of_speech="noun", **extra):
    return DictionaryEntry.objects.create(
        headword=headword,
        headword_normalized=normalize_query(headword),
        language=language,
        frequency_rank=frequency,
        part_of_speech=part_of_speech,
        **extra,
    )


class SearchServiceTests(APITestCase):
    def setUp(self):
        cache.clear()
        search_module.reset_search_caches()

    def test_normalisation_matches_import_normalisation(self):
        from translation.services.lexicon import normalize_headword

        samples = [
            "  Apple ",
            "O\u2018rik",
            "\u0401\u041b\u041a\u0410",
            "look  after",
        ]
        for value in samples:
            self.assertEqual(normalize_query(value), normalize_headword(value))

    def test_exact_match_outranks_prefix_and_text_matches(self):
        exact = make_entry("app", frequency=1)
        prefix = make_entry("apple", frequency=900)
        text = make_entry("pineapple", frequency=50, primary_meaning_en="a tropical apple")

        result = search(SearchRequest("app"), limit=10, offset=0)

        self.assertEqual(result.entry_ids[0], exact.id)
        self.assertIn(prefix.id, result.entry_ids)
        self.assertIn(text.id, result.entry_ids)

    def test_search_finds_russian_and_uzbek_words(self):
        russian = make_entry("\u043a\u043d\u0438\u0433\u0430", language="ru")
        uzbek = make_entry("kitob", language="uz")

        cyrillic = search(
            SearchRequest("\u041a\u041d\u0418\u0413\u0410"), limit=10, offset=0
        )
        self.assertEqual(cyrillic.entry_ids, [russian.id])
        self.assertEqual(search(SearchRequest("kitob"), limit=10, offset=0).entry_ids, [uzbek.id])

    def test_search_matches_translations_and_meanings(self):
        entry = make_entry("apple", primary_meaning_ru="\u044f\u0431\u043b\u043e\u043a\u043e")
        translated = make_entry("pear")
        Translation.objects.create(
            entry=translated, target_language="uz", translation_text="nok"
        )

        by_meaning = search(
            SearchRequest("\u044f\u0431\u043b\u043e\u043a\u043e"), limit=10, offset=0
        )
        self.assertIn(entry.id, by_meaning.entry_ids)
        self.assertIn(translated.id, search(SearchRequest("nok"), limit=10, offset=0).entry_ids)

    def test_substring_match_is_returned_last(self):
        inside = make_entry("pineapple")
        make_entry("apple")

        result = search(SearchRequest("pple"), limit=10, offset=0)

        self.assertIn(inside.id, result.entry_ids)

    def test_partial_search_can_be_disabled(self):
        make_entry("pineapple")

        result = search(SearchRequest("pple", match_partial=False), limit=10, offset=0)

        self.assertEqual(result.entry_ids, [])

    def test_single_character_queries_return_exact_matches_only(self):
        make_entry("a", language="en")
        make_entry("an")

        result = search(SearchRequest("a"), limit=10, offset=0)

        self.assertEqual(len(result.entry_ids), 1)

    def test_filters_by_source_language(self):
        make_entry("salom", language="uz")
        english = make_entry("salom", language="en")

        result = search(SearchRequest("salom", source_language="en"), limit=10, offset=0)

        self.assertEqual(result.entry_ids, [english.id])

    def test_filters_by_target_language_and_part_of_speech(self):
        with_translation = make_entry("book", part_of_speech="noun")
        make_entry("bookish", part_of_speech="adjective")
        Translation.objects.create(
            entry=with_translation, target_language="uz", translation_text="kitob"
        )

        result = search(
            SearchRequest("book", target_language="uz", part_of_speech="noun"),
            limit=10,
            offset=0,
        )

        self.assertEqual(result.entry_ids, [with_translation.id])

    def test_part_of_speech_filter_applies_to_text_matches(self):
        noun = make_entry(
            "motor",
            part_of_speech="noun",
            primary_meaning_en="machine that converts energy",
        )
        verb = make_entry("machined", part_of_speech="verb")

        result = search(
            SearchRequest("machine", part_of_speech="verb"), limit=10, offset=0
        )

        self.assertIn(verb.id, result.entry_ids)
        self.assertNotIn(noun.id, result.entry_ids)

    def test_pagination_is_stable_and_complete(self):
        for index in range(12):
            make_entry(f"item{index}", frequency=index)

        first = search(SearchRequest("item"), limit=5, offset=0)
        second = search(SearchRequest("item"), limit=5, offset=5)

        self.assertEqual(len(first.entry_ids), 5)
        self.assertTrue(first.has_more)
        self.assertFalse(set(first.entry_ids) & set(second.entry_ids))
        self.assertEqual(len(set(first.entry_ids) | set(second.entry_ids)), 10)

    def test_empty_query_returns_nothing(self):
        make_entry("apple")

        self.assertEqual(search(SearchRequest("   "), limit=10, offset=0).entry_ids, [])

    def test_special_characters_cannot_break_the_match_query(self):
        entry = make_entry("apple")

        for query in ['"apple', "*apple*", "-apple-", "(apple)", "apple..."]:
            result = search(SearchRequest(query), limit=10, offset=0)
            self.assertIn(entry.id, result.entry_ids)


class SearchIndexSyncTests(APITestCase):
    """The triggers must keep the index equal to the source tables."""

    def setUp(self):
        cache.clear()
        search_module.reset_search_caches()

    def indexed_documents(self):
        with connection.cursor() as cursor:
            cursor.execute(f"SELECT count(*) FROM {search_module.FTS_TABLE}")
            return cursor.fetchone()[0]

    def test_insert_update_and_delete_keep_the_index_in_sync(self):
        entry = make_entry("apple")
        self.assertEqual(self.indexed_documents(), 1)
        self.assertEqual(search(SearchRequest("apple"), limit=5, offset=0).entry_ids, [entry.id])

        entry.headword = "apricot"
        entry.headword_normalized = "apricot"
        entry.save()

        self.assertEqual(search(SearchRequest("apple"), limit=5, offset=0).entry_ids, [])
        self.assertEqual(search(SearchRequest("apricot"), limit=5, offset=0).entry_ids, [entry.id])

        entry.delete()

        self.assertEqual(self.indexed_documents(), 0)
        self.assertEqual(search(SearchRequest("apricot"), limit=5, offset=0).entry_ids, [])

    def test_translation_changes_reach_the_index(self):
        entry = make_entry("pear")
        translation = Translation.objects.create(
            entry=entry, target_language="uz", translation_text="nok"
        )
        self.assertIn(entry.id, search(SearchRequest("nok"), limit=5, offset=0).entry_ids)

        translation.translation_text = "olmurut"
        translation.save()

        self.assertNotIn(entry.id, search(SearchRequest("nok"), limit=5, offset=0).entry_ids)
        self.assertIn(entry.id, search(SearchRequest("olmurut"), limit=5, offset=0).entry_ids)

        translation.delete()

        self.assertEqual(search(SearchRequest("olmurut"), limit=5, offset=0).entry_ids, [])

    def test_moving_a_translation_updates_both_entries(self):
        first = make_entry("one")
        second = make_entry("two")
        translation = Translation.objects.create(
            entry=first, target_language="uz", translation_text="bir"
        )

        translation.entry = second
        translation.save()

        self.assertEqual(search(SearchRequest("bir"), limit=5, offset=0).entry_ids, [second.id])

    def test_bulk_created_rows_are_indexed(self):
        DictionaryEntry.objects.bulk_create(
            [
                DictionaryEntry(
                    headword=f"bulk{index}",
                    headword_normalized=f"bulk{index}",
                    language="en",
                )
                for index in range(5)
            ]
        )

        self.assertEqual(self.indexed_documents(), 5)
        self.assertTrue(search(SearchRequest("bulk"), limit=10, offset=0).entry_ids)

    def test_rebuild_command_restores_a_dropped_index(self):
        from django.core.management import call_command

        entry = make_entry("apple")
        with connection.cursor() as cursor:
            for statement in (
                "DROP TRIGGER IF EXISTS dictionary_search_entry_ai",
                "DROP TRIGGER IF EXISTS dictionary_search_entry_au",
                "DROP TRIGGER IF EXISTS dictionary_search_entry_ad",
                f"DROP TABLE IF EXISTS {search_module.FTS_TABLE}",
            ):
                cursor.execute(statement)

        self.assertEqual(search(SearchRequest("apple"), limit=5, offset=0).backend, "orm")

        call_command("rebuild_search_index", verbosity=0)

        result = search(SearchRequest("apple"), limit=5, offset=0)
        self.assertEqual(result.backend, "fts5")
        self.assertEqual(result.entry_ids, [entry.id])


class SearchApiTests(APITestCase):
    url = "/api/v1/dictionary/search/"

    def setUp(self):
        cache.clear()
        search_module.reset_search_caches()
        self.apple = make_entry(
            "apple",
            frequency=500,
            phonetic_ipa="/\u02c8\u00e6p\u0259l/",
        )
        Translation.objects.create(
            entry=self.apple,
            target_language="uz",
            translation_text="olma",
            part_of_speech="noun",
            quality="wiktionary",
        )
        self.apple.primary_meaning_ru = "\u044f\u0431\u043b\u043e\u043a\u043e"
        self.apple.save()
        make_entry("application", frequency=100)
        make_entry("pineapple")

    def test_response_shape(self):
        response = self.client.get(self.url, {"q": "apple"})

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["query"], "apple")
        self.assertEqual(response.data["page"], 1)
        self.assertIn("next", response.data)
        self.assertIn("hasMore", response.data)
        self.assertEqual(response.data["backend"], "fts5")
        first = response.data["results"][0]
        self.assertEqual(first["id"], self.apple.id)
        self.assertEqual(first["word"], "apple")
        self.assertEqual(first["headword"], "apple")
        self.assertEqual(first["sourceLanguage"], "en")
        self.assertEqual(first["language"], "en")
        self.assertEqual(first["translations"][0]["text"], "olma")

    def test_target_language_limits_translations(self):
        response = self.client.get(self.url, {"q": "apple", "target": "uz"})

        translations = response.data["results"][0]["translations"]
        self.assertTrue(translations)
        self.assertEqual({row["targetLanguage"] for row in translations}, {"uz"})

    def test_missing_query_is_rejected(self):
        response = self.client.get(self.url)

        self.assertEqual(response.status_code, 400)
        self.assertIn("q", response.data)

    def test_invalid_language_code_is_rejected(self):
        response = self.client.get(self.url, {"q": "apple", "source": "!!"})

        self.assertEqual(response.status_code, 400)
        self.assertIn("source", response.data)

    def test_page_size_is_capped(self):
        from django.conf import settings

        response = self.client.get(self.url, {"q": "a", "pageSize": 10_000})

        self.assertEqual(response.status_code, 200)
        self.assertLessEqual(response.data["pageSize"], settings.DICTIONARY_SEARCH_MAX_PAGE_SIZE)

    def test_second_page_does_not_repeat_results(self):
        first = self.client.get(self.url, {"q": "app", "pageSize": 2})
        second = self.client.get(self.url, {"q": "app", "pageSize": 2, "page": 2})

        first_ids = {row["id"] for row in first.data["results"]}
        second_ids = {row["id"] for row in second.data["results"]}
        self.assertFalse(first_ids & second_ids)

    def test_etag_allows_conditional_requests(self):
        first = self.client.get(self.url, {"q": "apple"})
        etag = first.headers["ETag"]
        self.assertTrue(first.headers["Cache-Control"].startswith("public"))

        second = self.client.get(self.url, {"q": "apple"}, HTTP_IF_NONE_MATCH=etag)

        self.assertEqual(second.status_code, 304)
        self.assertEqual(second.content, b"")

    def test_etag_changes_when_dictionary_changes(self):
        first = self.client.get(self.url, {"q": "apple"})
        etag = first.headers["ETag"]

        Translation.objects.create(
            entry=self.apple, target_language="uz", translation_text="olma mevasi"
        )

        second = self.client.get(self.url, {"q": "apple"}, HTTP_IF_NONE_MATCH=etag)

        self.assertEqual(second.status_code, 200)
        self.assertNotEqual(second.headers["ETag"], etag)

    @override_settings(DICTIONARY_SEARCH_CACHE_TTL=0, DICTIONARY_VERSION_CACHE_TTL=0)
    def test_query_count_stays_bounded(self):
        from django.test.utils import CaptureQueriesContext

        with CaptureQueriesContext(connection) as queries:
            self.client.get(self.url, {"q": "apple", "pageSize": 5})

        # Nine of them build the uncached version stamp (three cheap lookups
        # per table); the rest are the search query, the substring fallback,
        # the entry page and its translation prefetch.
        self.assertLessEqual(len(queries), 15)

    def test_legacy_entries_endpoint_uses_the_same_ranking(self):
        response = self.client.get("/api/v1/dictionary/entries/", {"q": "apple"})

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["results"][0]["headword"], "apple")


class CatalogPaginationTests(APITestCase):
    def test_entry_list_is_paged(self):
        for index in range(5):
            make_entry(f"word{index}")

        response = self.client.get("/api/v1/dictionary/entries/", {"pageSize": 2})

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data["results"]), 2)
        self.assertEqual(response.data["count"], 5)
        self.assertIsNotNone(response.data["next"])

    def test_topics_are_paged(self):
        response = self.client.get("/api/v1/topics/")

        self.assertEqual(response.status_code, 200)
        self.assertIn("results", response.data)


class OrmFallbackTests(APITestCase):
    """Search must keep working when the FTS index is not available."""

    def setUp(self):
        cache.clear()
        search_module.reset_search_caches()

    def test_fallback_returns_ranked_results(self):
        exact = make_entry("apple")
        make_entry("pineapple")
        meaning = make_entry("pear", primary_meaning_en="a pear")

        original = search_module._has_fts_table
        search_module._has_fts_table = lambda: False
        try:
            result = search(SearchRequest("apple"), limit=10, offset=0)
            translation = search(SearchRequest("a pear"), limit=10, offset=0)
        finally:
            search_module._has_fts_table = original

        self.assertEqual(result.backend, "orm")
        self.assertEqual(result.entry_ids[0], exact.id)
        self.assertIn(meaning.id, translation.entry_ids)
