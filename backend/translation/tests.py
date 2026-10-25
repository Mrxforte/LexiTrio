import json
import tempfile
from pathlib import Path
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import SimpleTestCase, override_settings
from rest_framework.test import APIClient, APITestCase

from learning.models import DictionaryEntry, Translation

from .models import TranslationJob
from .services.lexicon import file_records, import_records
from .services.openai_client import ChatResult
from .services.professional import (
    POS_ADJECTIVE,
    POS_ARTICLE,
    POS_NOUN,
    POS_NUMERAL,
    POS_VERB,
    context_note,
    english_article,
    format_translation,
    normalize_pos,
    primary_meaning_from_translations,
)
from .services.wiktionary import parse_payload
from .services.openai_client import ChatResult, TranslationProviderError
from .services.providers import RotatingClient, configured_providers
from .services.translator import (
    TranslationInputError,
    split_text,
    translate_headwords,
    translate_items,
    translate_professional,
)
from .services.backfill import backfill_entries

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

    def test_translate_professional_returns_structured_grammar(self):
        def builder(_prompt):
            return json.dumps(
                {
                    "translations": [
                        {
                            "index": 0,
                            "pos": "noun",
                            "ipa": "/ˈwɜːd/",
                            "article": "a",
                            "plural": "words",
                            "ru": {"text": "слово", "pos": "noun", "gender": "n"},
                            "uz": {"text": "so'z", "pos": "noun"},
                        }
                    ]
                }
            )

        records, _, warnings = translate_professional(
            [{"headword": "word", "definition": "a unit of language"}],
            client=FakeClient(builder),
        )

        self.assertEqual(warnings, [])
        self.assertEqual(records[0]["pos"], "noun")
        self.assertEqual(records[0]["ipa"], "/ˈwɜːd/")
        self.assertEqual(records[0]["plural"], "words")
        self.assertEqual(records[0]["ru"]["text"], "слово")
        self.assertEqual(records[0]["ru"]["gender"], "n")

    def test_professional_backfill_writes_grammar_fields(self):
        entry = DictionaryEntry.objects.create(
            headword="word",
            headword_normalized="word",
            language="en",
            primary_meaning_en="a unit of language",
        )

        def builder(_prompt):
            return json.dumps(
                {
                    "translations": [
                        {
                            "index": 0,
                            "pos": "noun",
                            "ipa": "/ˈwɜːd/",
                            "article": "a",
                            "plural": "words",
                            "ru": {"text": "слово", "pos": "noun", "gender": "n"},
                            "uz": {"text": "so'z", "pos": "noun"},
                        }
                    ]
                }
            )

        summary = backfill_entries(
            limit=1, professional=True, client=FakeClient(builder), entry_ids=[entry.id]
        )

        entry.refresh_from_db()
        self.assertEqual(summary["translated"], 1)
        self.assertEqual(entry.phonetic_ipa, "/ˈwɜːd/")
        self.assertEqual(entry.part_of_speech, "noun")
        self.assertEqual(entry.article, "a")
        self.assertEqual(entry.plural_form, "words")
        self.assertEqual(entry.primary_meaning_ru, "слово (сущ., с.р.)")
        self.assertEqual(entry.primary_meaning_uz, "so'z (ot)")
        translation = entry.translations.get(target_language="ru")
        self.assertEqual(translation.gender, "n")
        self.assertEqual(translation.context_note, "сущ., с.р.")


class ProfessionalHelpersTests(SimpleTestCase):
    def test_pos_normalization_covers_uzbek_dictionary_labels(self):
        self.assertEqual(normalize_pos("num"), POS_NUMERAL)
        self.assertEqual(normalize_pos("adj"), POS_ADJECTIVE)
        self.assertEqual(normalize_pos("Article"), POS_ARTICLE)
        self.assertEqual(normalize_pos("unknown"), "")

    def test_article_is_derived_from_the_first_sound(self):
        self.assertEqual(english_article("/ˈaʊə/", POS_NOUN), "an")
        self.assertEqual(english_article("/ˌjuːnɪˈvɜːsəti/", POS_NOUN), "a")
        self.assertEqual(english_article("/ˈwɜːd/", POS_VERB), "")

    def test_context_note_uses_dictionary_abbreviations(self):
        note = context_note("ru", pos=POS_NOUN, gender="f")
        self.assertEqual(note, "сущ., ж.р.")
        self.assertEqual(context_note("uz", pos=POS_NUMERAL), "son")

    def test_primary_meaning_appends_the_note(self):
        value = primary_meaning_from_translations(["случайность", "удача"], "сущ., ж.р.")

        self.assertEqual(value, "случайность, удача (сущ., ж.р.)")

    def test_wiki_markup_is_stripped_from_translations(self):
        self.assertEqual(
            format_translation("серебро, [[серебреник]]и, {{l|ru|аргент}}", ""),
            "серебро, серебреники",
        )


class WiktionaryPayloadTests(SimpleTestCase):
    def test_parse_payload_collects_professional_fields(self):
        payload = {
            "word": "whimsical",
            "pos": "adj",
            "sounds": [{"ipa": "/ˈwɪmzɪkəl/"}],
            "forms": [
                {"form": "more whimsical", "tags": ["comparative"]},
                {"form": "most whimsical", "tags": ["superlative"]},
            ],
            "senses": [{"gloss": "determined by whim"}],
            "translations": [
                {"lang": "Russian", "word": "причудливый", "roman": "pričudlivyj"},
                {"lang": "Uzbek", "word": "injiq"},
            ],
        }

        entry = parse_payload(payload)

        self.assertIsNotNone(entry)
        self.assertEqual(entry.pos, POS_ADJECTIVE)
        self.assertEqual(entry.ipa, "/ˈwɪmzɪkəl/")
        self.assertEqual(entry.comparative, "more whimsical")
        self.assertEqual(entry.translations["ru"][0]["word"], "причудливый")
        self.assertEqual(entry.translations["uz"][0]["word"], "injiq")

    def test_parse_payload_reads_gender_and_plural_tags(self):
        payload = {
            "word": "dictionary",
            "pos": "noun",
            "forms": [{"form": "dictionaries", "tags": ["plural"]}],
            "senses": [{"gloss": "a reference book"}],
            "translations": [
                {"lang": "Russian", "word": "словарь", "tags": ["masculine"]},
            ],
        }

        entry = parse_payload(payload)

        self.assertEqual(entry.plural, "dictionaries")
        self.assertEqual(entry.translations["ru"][0]["gender"], "m")

    def test_parse_payload_ignores_entries_without_content(self):
        self.assertIsNone(parse_payload({"word": "empty", "pos": "noun", "senses": []}))


class FreedictParseTests(SimpleTestCase):
    def _parse(self, xml: str):
        import xml.etree.ElementTree as ET

        from .services.freedict import parse_entry

        return parse_entry(ET.fromstring(xml))

    def test_parse_entry_reads_translation_pos_gender_and_ipa(self):
        entry = self._parse(
            """
            <entry xmlns="http://www.tei-c.org/ns/1.0">
              <form><orth>dictionary</orth><pron>/ˈdɪkʃənəri/</pron></form>
              <gramGrp><pos>n</pos></gramGrp>
              <sense>
                <cit type="trans" xml:lang="ru"><quote>слова́рь</quote>
                  <gramGrp><gen>masc</gen></gramGrp>
                </cit>
                <sense><def>a reference book</def></sense>
              </sense>
            </entry>
            """
        )

        self.assertIsNotNone(entry)
        self.assertEqual(entry.headword, "dictionary")
        self.assertEqual(entry.pos, POS_NOUN)
        self.assertEqual(entry.ipa, "/ˈdɪkʃənəri/")
        self.assertEqual(entry.translations, ["словарь"])
        self.assertEqual(entry.definitions, ["a reference book"])
        self.assertEqual(entry.gender, "m")

    def test_parse_entry_skips_entries_without_russian_translation(self):
        entry = self._parse(
            """
            <entry xmlns="http://www.tei-c.org/ns/1.0">
              <form><orth>untranslated</orth></form>
              <sense><sense><def>something</def></sense></sense>
            </entry>
            """
        )

        self.assertIsNone(entry)


class ProviderRotationTests(SimpleTestCase):
    def test_configured_providers_detects_gemini_and_deepseek(self):
        with override_settings(
            OPENAI_API_KEY="g-key",
            OPENAI_BASE_URL="https://generativelanguage.googleapis.com/v1beta/openai",
            OPENAI_MODEL="gemini-3.5-flash-lite",
            DEEPSEEK_API_KEY="d-key",
            DEEPSEEK_BASE_URL="https://api.deepseek.com/v1",
            DEEPSEEK_MODEL="deepseek-chat",
        ):
            providers = configured_providers()

        self.assertEqual([provider.name for provider in providers], ["gemini", "deepseek"])
        self.assertEqual(providers[1].model, "deepseek-chat")

    def test_deepseek_is_skipped_without_a_key(self):
        with override_settings(OPENAI_API_KEY="g-key", DEEPSEEK_API_KEY=""):
            providers = configured_providers()

        self.assertEqual([provider.name for provider in providers], ["gemini"])

    def test_rotation_falls_back_when_a_provider_is_down(self):
        class Failing:
            model = "down"

            def complete(self, **_kwargs):
                raise TranslationProviderError("429")

        class Working:
            model = "ok"

            def complete(self, **_kwargs):
                return ChatResult(content="{}", model="ok")

        client = RotatingClient([Failing(), Working()])
        result = client.complete(system_prompt="s", user_prompt="u")

        self.assertEqual(result.model, "ok")

    def test_rotation_raises_when_every_provider_fails(self):
        class Failing:
            model = "down"

            def complete(self, **_kwargs):
                raise TranslationProviderError("429")

        client = RotatingClient([Failing(), Failing()])

        with self.assertRaises(TranslationProviderError):
            client.complete(system_prompt="s", user_prompt="u")


class BatchResilienceTests(APITestCase):
    def _single_item_builder(self, bad_marker: str):
        def builder(user_prompt: str) -> str:
            items = json.loads(user_prompt.split("Items:", 1)[1])
            if len(items) > 1:
                return bad_marker
            source = items[0].get("text") or items[0].get("headword") or ""
            return json.dumps(
                {
                    "translations": [
                        {"index": 0, "ru": f"ru:{source}", "uz": f"uz:{source}"}
                    ]
                }
            )

        return builder

    def test_malformed_json_splits_the_batch_and_still_translates(self):
        client = FakeClient(self._single_item_builder("not json at all"))

        records, _, warnings = translate_items(
            ["alpha", "beta", "gamma", "delta"], client=client, batch_size=4
        )

        self.assertEqual(
            [record["ru"] for record in records],
            ["ru:alpha", "ru:beta", "ru:gamma", "ru:delta"],
        )
        self.assertTrue(warnings)

    @override_settings(TRANSLATION_CONCURRENCY=4)
    def test_concurrent_requests_keep_the_input_order(self):
        client = FakeClient()

        records, _, warnings = translate_items(
            ["one", "two", "three", "four", "five"], client=client, batch_size=2
        )

        self.assertEqual([record["source"] for record in records], ["one", "two", "three", "four", "five"])
        self.assertEqual([record["index"] for record in records], [0, 1, 2, 3, 4])
        self.assertEqual(warnings, [])


class TextTranslationApiTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(
            username="learner", email="learner@example.com", password="secret123"
        )
        self.client.force_authenticate(user=self.user)

    def test_text_endpoint_translates_into_russian_and_uzbek(self):
        with patch("translation.views.build_translation_client", return_value=FakeClient()):
            response = self.client.post(
                "/api/v1/translation/text/",
                {"text": "hello world", "targets": ["ru", "uz"]},
                format="json",
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["translations"]["ru"], "ru:hello world")
        self.assertEqual(response.data["translations"]["uz"], "uz:hello world")
        self.assertEqual(response.data["segmentCount"], 1)

    @override_settings(OPENAI_API_KEY="", DEEPSEEK_API_KEY="")
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

        with patch("translation.views.build_translation_client", return_value=FakeClient()):
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

        with patch("translation.views.build_translation_client", return_value=FakeClient()):
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

        with patch("translation.views.build_translation_client", return_value=FakeClient()):
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
