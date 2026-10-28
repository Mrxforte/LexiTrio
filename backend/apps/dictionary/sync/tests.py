from datetime import timedelta

from django.conf import settings
from django.test import override_settings
from django.utils import timezone
from rest_framework.test import APITestCase

from apps.dictionary.entries.models import DictionaryEntry, SynonymAntonym, Translation, WordMeaning


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

    def test_lite_pages_carry_only_entries_and_meanings(self):
        response = self.client.get("/api/v1/dictionary/sync/?limit=1&lite=1")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data["entries"]), 1)
        self.assertEqual(len(response.data["meanings"]), 1)
        for key in ("translations", "examples", "synonyms", "forms", "collocations"):
            self.assertNotIn(key, response.data)
        self.assertIn("nextAfterId", response.data)
        self.assertIn("serverTime", response.data)

    def test_lite_pages_allow_larger_limits(self):
        response = self.client.get("/api/v1/dictionary/sync/?limit=99999&lite=1")

        self.assertEqual(len(response.data["entries"]), 5)

    def test_deleted_entries_are_reported_to_incremental_clients(self):
        since = timezone.now() - timedelta(minutes=1)
        doomed = self.entries[4]
        doomed_id = doomed.id
        doomed.delete()

        response = self.client.get("/api/v1/dictionary/sync/", {"since": since.isoformat()})

        self.assertEqual(response.data["deletedIds"], [doomed_id])
        self.assertFalse(response.data["deletedIdsTruncated"])

    def test_full_passes_do_not_carry_deletion_markers(self):
        self.entries[4].delete()

        response = self.client.get("/api/v1/dictionary/sync/")

        self.assertNotIn("deletedIds", response.data)

    def test_cursor_older_than_tombstone_retention_gets_a_full_pass(self):
        ancient = timezone.now() - timedelta(days=settings.DICTIONARY_TOMBSTONE_RETENTION_DAYS + 5)
        DictionaryEntry.objects.update(updated_at=ancient)

        response = self.client.get("/api/v1/dictionary/sync/", {"since": ancient.isoformat()})

        self.assertTrue(response.data["fullResync"])
        self.assertEqual(len(response.data["entries"]), 5)

    def test_deletion_markers_are_capped(self):
        since = timezone.now() - timedelta(minutes=1)
        for entry in self.entries[2:]:
            entry.delete()

        with override_settings(DICTIONARY_TOMBSTONE_PAGE_MAX=2):
            response = self.client.get("/api/v1/dictionary/sync/", {"since": since.isoformat()})

        self.assertEqual(len(response.data["deletedIds"]), 2)
        self.assertTrue(response.data["deletedIdsTruncated"])

    def test_invalid_since_is_rejected(self):
        response = self.client.get("/api/v1/dictionary/sync/?since=not-a-date")

        self.assertEqual(response.status_code, 400)
        self.assertIn("since", response.data)


class DictionaryDetailsApiTests(APITestCase):
    url = "/api/v1/dictionary/details/"

    def setUp(self):
        self.entry = DictionaryEntry.objects.create(
            headword="apple", headword_normalized="apple", language="en"
        )
        self.other = DictionaryEntry.objects.create(
            headword="pear", headword_normalized="pear", language="en"
        )
        WordMeaning.objects.create(entry=self.entry, sense_number=1, definition_en="fruit")
        Translation.objects.create(entry=self.entry, target_language="uz", translation_text="olma")
        Translation.objects.create(entry=self.other, target_language="uz", translation_text="nok")

    def test_returns_child_rows_for_several_ids_in_one_request(self):
        response = self.client.get(self.url, {"ids": f"{self.entry.id},{self.other.id},9999"})

        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data["entries"]), 2)
        self.assertEqual({row["translationText"] for row in response.data["translations"]}, {"olma", "nok"})
        self.assertEqual(len(response.data["meanings"]), 1)
        self.assertEqual(response.data["missingIds"], [9999])

    def test_requires_ids(self):
        self.assertEqual(self.client.get(self.url).status_code, 400)

    def test_rejects_malformed_and_non_positive_ids(self):
        self.assertEqual(self.client.get(self.url, {"ids": "1,abc"}).status_code, 400)
        self.assertEqual(self.client.get(self.url, {"ids": "0"}).status_code, 400)

    def test_batch_size_is_bounded(self):
        ids = ",".join(str(number) for number in range(1, 52))

        response = self.client.get(self.url, {"ids": ids})

        self.assertEqual(response.status_code, 400)
        self.assertIn("ids", response.data)

    def test_uses_a_constant_number_of_queries(self):
        with self.assertNumQueries(7):
            self.client.get(self.url, {"ids": f"{self.entry.id},{self.other.id}"})
