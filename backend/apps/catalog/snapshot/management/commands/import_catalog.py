import json
from pathlib import Path

from django.core.management.base import BaseCommand, CommandError
from django.db import transaction

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


TABLE_MODELS = {
    "dictionary_entries": DictionaryEntry,
    "word_meanings": WordMeaning,
    "translations": Translation,
    "example_sentences": ExampleSentence,
    "synonyms_antonyms": SynonymAntonym,
    "word_forms": WordForm,
    "collocations": Collocation,
    "topics": Topic,
    "topic_word_cross_ref": TopicWord,
    "conversations": Conversation,
    "conversation_turns": ConversationTurn,
    "grammar_lessons": GrammarLesson,
    "quiz_questions": QuizQuestion,
}


class Command(BaseCommand):
    help = "Import the app's offline catalog JSON into the Django learning database."

    def add_arguments(self, parser):
        parser.add_argument("path", type=Path)

    @transaction.atomic
    def handle(self, *args, **options):
        path = options["path"]
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise CommandError(f"Could not read catalog file: {error}") from error

        if not isinstance(data, dict):
            raise CommandError("Catalog must be a JSON object containing table arrays.")

        total = 0
        for table, model in TABLE_MODELS.items():
            rows = data.get(table, [])
            if not isinstance(rows, list):
                raise CommandError(f"'{table}' must be a JSON array.")
            for row in rows:
                values = {_to_snake_case(key): value for key, value in row.items()}
                if table == "topic_word_cross_ref":
                    model.objects.get_or_create(**values)
                else:
                    model.objects.update_or_create(id=values.pop("id"), defaults=values)
                total += 1

        self.stdout.write(self.style.SUCCESS(f"Imported {total} catalog records."))


def _to_snake_case(value):
    result = []
    for character in value:
        if character.isupper():
            result.append("_")
            result.append(character.lower())
        else:
            result.append(character)
    return "".join(result)
