import json
import tempfile
from pathlib import Path
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import override_settings
from rest_framework.test import APIClient, APITestCase

from learning.models import DictionaryEntry, Translation

from .models import TranslationJob
from .services.lexicon import file_records, import_records
from .services.openai_client import ChatResult
from .services.translator import (
    TranslationInputError,
    split_text,
    translate_headwords,
    translate_items,
)

User = get_user_model()


class FakeClient:
    """Stands in for OpenAIClient without touching the network."""

    model = "fake-model"

    def __init__(self, builder=None):
        self.builder = builder or _echo_builder
        self.calls: list[str] = []

    def complete(self, *, system_prompt, user_prompt, json_mode=True) -> ChatResult:
        self.calls.append(user_prompt)
        return ChatResult(content=self.builder(user_prompt), model=self.model)


def _echo_builder(user_prompt: str) -> str:
    payload = json.loads(user_prompt.split("Items:", 1)[1])
    translations = []
    for item in payload:
        source = item.get("text") or item.get("headword") or ""
        translations.append(
            {"index": item["index"], "ru": f"ru:{source}", "uz": f"uz:{source}"}
        )
    return json.dumps({"translations": translations})


class TranslatorTests(APITestCase):
    def test_split_text_respects_max_segment_length(self):
        text = " ".join(["word"] * 400) + "\n\nIkkinchi paragraf."

        segments = split_text(text, max_chars=500, max_segments=50)

        self.assertGreater(len(segments), 1)
        self.assertTrue(all(len(segment) <= 500 for segment in segments))

    @override_settings(TRANSLATION_MAX_SEGMENTS=2)
    def test_split_text_rejects_too_many_segments(self):
        text = "\n\n".join("Paragraf raqami " + str(index) for index in range(10))

        with self.assertRaises(TranslationInputError):
            split_text(text, max_chars=50, max_segments=2)

    def test_translate_items_returns_both_languages(self):
        client = FakeClient()

        records, model, warnings = translate_items(["hello", "world"], client=client)

        self.assertEqual(model, "fake-model")
        self.assertEqual([record["source"] for record in records], ["hello", "world"])
        self.assertEqual(records[0]["ru"], "ru:hello")
        self.assertEqual(records[1]["uz"], "uz:world")
        self.assertEqual(warnings, [])

    def test_translate_items_accepts_fenced_json(self):
        def fenced(_prompt):
            return '```json\n{"translations":[{"index":0,"ru":"привет","uz":"salom"}]}\n```'

        records, _, _ = translate_items(["hello"], client=FakeClient(fenced))

        self.assertEqual(records[0]["ru"], "привет")
        self.assertEqual(records[0]["uz"], "salom")

    def test_translate_headwords_includes_definition_as_context(self):
        client = FakeClient()

        records, _, _ = translate_headwords(
            [{"headword": "acquire", "definition": "to obtain"}], client=client
        )

        self.assertIn("to obtain", client.calls[0])
        self.assertEqual(records[0]["headword"], "acquire")
        self.assertEqual(records[0]["ru"], "ru:acquire")


class TextTranslationApiTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(
            username="learner", email="learner@example.com", password="secret123"
        )
        self.client.force_authenticate(user=self.user)

    def test_text_endpoint_translates_into_russian_and_uzbek(self):
        with patch("translation.views.OpenAIClient", return_value=FakeClient()):
            response = self.client.post(
                "/api/v1/translation/text/",
                {"text": "hello world", "targets": ["ru", "uz"]},
                format="json",
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["translations"]["ru"], "ru:hello world")
        self.assertEqual(response.data["translations"]["uz"], "uz:hello world")
        self.assertEqual(response.data["segmentCount"], 1)

    @override_settings(OPENAI_API_KEY="")
    def test_text_endpoint_reports_missing_api_key(self):
        response = self.client.post(
            "/api/v1/translation/text/", {"text": "hello"}, format="json"
        )

        self.assertEqual(response.status_code, 503)
        self.assertIn("OPENAI_API_KEY", str(response.data["detail"]))

    def test_text_endpoint_rejects_empty_text(self):
        response = self.client.post(
            "/api/v1/translation/text/", {"text": "   "}, format="json"
        )

        self.assertEqual(response.status_code, 400)

    def test_text_endpoint_requires_authentication(self):
        response = APIClient().post(
            "/api/v1/translation/text/", {"text": "hello"}, format="json"
        )

        self.assertEqual(response.status_code, 401)

    def test_document_endpoint_extracts_and_translates_text_file(self):
        upload = SimpleUploadedFile(
            "dictionary.txt", b"acquire - to obtain\nresilience - to recover", "text/plain"
        )

        with patch("translation.views.OpenAIClient", return_value=FakeClient()):
            response = self.client.post(
                "/api/v1/translation/document/", {"file": upload}, format="multipart"
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["file"]["name"], "dictionary.txt")
        self.assertEqual(len(response.data["segments"]), 2)
        self.assertIn("acquire", response.data["segments"][0]["source"])

    def test_document_endpoint_rejects_unsupported_extension(self):
        upload = SimpleUploadedFile("archive.zip", b"PK\x03\x04", "application/zip")

        response = self.client.post(
            "/api/v1/translation/document/", {"file": upload}, format="multipart"
        )

        self.assertEqual(response.status_code, 400)
        self.assertIn("file", response.data)

    @override_settings(TRANSLATION_MAX_UPLOAD_BYTES=5)
    def test_document_endpoint_rejects_oversized_file(self):
        upload = SimpleUploadedFile("big.txt", b"x" * 50, "text/plain")

        response = self.client.post(
            "/api/v1/translation/document/", {"file": upload}, format="multipart"
        )

        self.assertEqual(response.status_code, 400)


class BackfillApiTests(APITestCase):
    def setUp(self):
        self.staff = User.objects.create_user(
            username="staff", email="staff@example.com", password="secret123", is_staff=True
        )
        self.entry = DictionaryEntry.objects.create(
            headword="acquire",
            headword_normalized="acquire",
            language="en",
            primary_meaning_en="to obtain something",
        )
        DictionaryEntry.objects.create(
            headword="salom",
            headword_normalized="salom",
            language="uz",
            primary_meaning_en="",
        )

    def test_backfill_requires_staff(self):
        user = User.objects.create_user(
            username="plain", email="plain@example.com", password="secret123"
        )
        self.client.force_authenticate(user=user)

        response = self.client.post("/api/v1/translation/backfill/", {}, format="json")

        self.assertEqual(response.status_code, 403)

    def test_backfill_updates_entries_and_creates_translations(self):
        self.client.force_authenticate(user=self.staff)

        with patch("translation.views.OpenAIClient", return_value=FakeClient()):
            response = self.client.post(
                "/api/v1/translation/backfill/", {"limit": 10}, format="json"
            )

        self.assertEqual(response.status_code, 200)
        self.entry.refresh_from_db()
        self.assertEqual(self.entry.primary_meaning_ru, "ru:acquire")
        self.assertEqual(self.entry.primary_meaning_uz, "uz:acquire")
        self.assertEqual(
            set(self.entry.translations.values_list("target_language", flat=True)),
            {"ru", "uz"},
        )
        self.assertEqual(response.data["processed"], 1)
        self.assertEqual(response.data["translated"], 1)
        self.assertEqual(response.data["remaining"], 0)

    def test_backfill_dry_run_does_not_write(self):
        self.client.force_authenticate(user=self.staff)

        with patch("translation.views.OpenAIClient", return_value=FakeClient()):
            response = self.client.post(
                "/api/v1/translation/backfill/", {"limit": 10, "dryRun": True}, format="json"
            )

        self.assertEqual(response.status_code, 200)
        self.entry.refresh_from_db()
        self.assertEqual(self.entry.primary_meaning_ru, "")
        self.assertEqual(Translation.objects.count(), 0)
        self.assertEqual(response.data["translated"], 1)

    def test_status_reports_dictionary_coverage(self):
        self.client.force_authenticate(user=self.staff)

        response = self.client.get("/api/v1/translation/status/")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["dictionary"]["totalEntries"], 1)
        self.assertEqual(response.data["dictionary"]["missingRussian"], 1)
        self.assertIn("uz", response.data["targetLanguages"])


class TranslationJobApiTests(APITestCase):
    def setUp(self):
        self.staff = User.objects.create_user(
            username="staff", email="staff@example.com", password="secret123", is_staff=True
        )

    def test_create_and_cancel_job(self):
        self.client.force_authenticate(user=self.staff)
        DictionaryEntry.objects.create(
            headword="resilience",
            headword_normalized="resilience",
            language="en",
            primary_meaning_en="ability to recover",
        )

        created = self.client.post(
            "/api/v1/translation/jobs/", {"languages": ["uz"], "limit": 100}, format="json"
        )

        self.assertEqual(created.status_code, 201)
        self.assertEqual(created.data["status"], TranslationJob.Status.PENDING)
        self.assertEqual(created.data["totalCandidates"], 1)

        job_id = created.data["id"]
        detail = self.client.get(f"/api/v1/translation/jobs/{job_id}/")
        self.assertEqual(detail.status_code, 200)

        cancelled = self.client.post(f"/api/v1/translation/jobs/{job_id}/cancel/")
        self.assertEqual(cancelled.status_code, 200)
        self.assertEqual(cancelled.data["status"], TranslationJob.Status.CANCELLED)

    def test_job_endpoints_require_staff(self):
        user = User.objects.create_user(
            username="plain", email="plain@example.com", password="secret123"
        )
        self.client.force_authenticate(user=user)

        response = self.client.get("/api/v1/translation/jobs/")

        self.assertEqual(response.status_code, 403)


class LexiconImportTests(APITestCase):
    def test_file_records_reads_csv_with_aliases(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "words.csv"
            path.write_text(
                "word,definition,ru,uz,pos\n"
                "hello,greeting,привет,salom,interjection\n",
                encoding="utf-8",
            )

            records = list(file_records(path))

        self.assertEqual(len(records), 1)
        self.assertEqual(records[0]["headword"], "hello")
        self.assertEqual(records[0]["primary_meaning_ru"], "привет")
        self.assertEqual(records[0]["primary_meaning_uz"], "salom")

    def test_import_records_creates_entries_meanings_and_translations(self):
        records = [
            {
                "headword": "hello",
                "part_of_speech": "interjection",
                "cefr_level": "A1",
                "primary_meaning_en": "a greeting",
                "primary_meaning_ru": "привет",
                "senses": [{"definition_en": "a greeting"}],
                "translations": {"ru": ["привет"], "uz": ["salom"]},
            },
            {
                "headword": "hello",
                "primary_meaning_en": "duplicate",
            },
            {
                "headword": "!!!",
                "primary_meaning_en": "invalid",
            },
        ]

        stats = import_records(records)

        self.assertEqual(stats.created, 1)
        self.assertEqual(stats.skipped, 2)
        entry = DictionaryEntry.objects.get(headword_normalized="hello")
        self.assertEqual(entry.primary_meaning_ru, "привет")
        self.assertEqual(entry.meanings.count(), 1)
        self.assertEqual(entry.translations.count(), 2)
