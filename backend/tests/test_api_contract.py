"""Android-facing API contract.

``contracts/api_shapes.json`` freezes, for every public endpoint, the HTTP
status and the JSON *shape* (field names and value types, not values) that the
Android client depends on. It was recorded before the backend was refactored;
a failure here means a refactor changed what the client sees.
"""

import json
from pathlib import Path

from django.test import TestCase
from rest_framework.test import APIClient

from apps.catalog.conversations.models import Conversation, ConversationTurn
from apps.catalog.grammar.models import GrammarLesson
from apps.catalog.quiz.models import QuizQuestion
from apps.catalog.topics.models import Topic, TopicWord
from apps.dictionary.entries.models import (
    Collocation,
    DictionaryEntry,
    ExampleSentence,
    SynonymAntonym,
    Translation,
    WordForm,
    WordMeaning,
)

CONTRACT = json.loads((Path(__file__).parent / "contracts" / "api_shapes.json").read_text(encoding="utf-8"))


def shape(value):
    if isinstance(value, dict):
        return {key: shape(item) for key, item in value.items()}
    if isinstance(value, list):
        return [shape(value[0])] if value else []
    return type(value).__name__


class ApiContractTests(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.entry = DictionaryEntry.objects.create(
            headword="apple", headword_normalized="apple", language="en", primary_meaning_uz="olma"
        )
        meaning = WordMeaning.objects.create(entry=cls.entry, definition_en="fruit")
        Translation.objects.create(entry=cls.entry, target_language="uz", translation_text="olma")
        ExampleSentence.objects.create(entry=cls.entry, sentence_en="x", sentence_ru="y", sentence_uz="z")
        SynonymAntonym.objects.create(meaning_id=meaning.id, word="s")
        WordForm.objects.create(entry=cls.entry, form_type="plural", form_value="apples")
        Collocation.objects.create(entry=cls.entry, expression="e", translation_ru="r", translation_uz="u")
        topic = Topic.objects.create(
            title_en="T", title_ru="T", title_uz_latn="T", title_uz_cyrl="T", category="c"
        )
        TopicWord.objects.create(topic=topic, word=cls.entry)
        conversation = Conversation.objects.create(
            title_en="c", title_ru="c", title_uz_latn="c", title_uz_cyrl="c", situation_en="s", category="c"
        )
        ConversationTurn.objects.create(
            conversation=conversation, turn_index=0, speaker_role="a", text_en="t"
        )
        GrammarLesson.objects.create(
            title_en="g", title_ru="g", title_uz_latn="g", title_uz_cyrl="g", category="c", summary_en="s"
        )
        QuizQuestion.objects.create(
            question_text="q", correct_answer="a", option_a="a", option_b="b", option_c="c", option_d="d"
        )

    def test_every_endpoint_keeps_its_status_and_json_shape(self):
        client = APIClient()
        mismatches = []
        for template, (status_code, expected_shape) in CONTRACT.items():
            url = template.replace("{id}", str(self.entry.id))
            response = client.get(url)
            body = None
            if response.content and response["Content-Type"].startswith("application/json"):
                body = response.json()
            if [response.status_code, shape(body)] != [status_code, expected_shape]:
                mismatches.append(url)
        self.assertEqual(mismatches, [])

    def test_the_contract_covers_the_public_surface(self):
        self.assertGreaterEqual(len(CONTRACT), 30)
        for required in ("/api/v1/catalog/", "/api/v1/dictionary/search/?q=apple", "/api/v1/dictionary/sync/?limit=1&lite=1"):
            self.assertIn(required, CONTRACT)
