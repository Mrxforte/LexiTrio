from django.db import migrations

from shared.migration_utils import release_legacy_models

# Models now live in the feature apps; only Django's migration state changes.
MOVED_MODELS = [
    "SavedWord", "VocabularyCollection", "QuizAttempt", "UserProgress", "UserPreferences",
    "MistakeRecord", "UserTopicProgress",
    "TestBankQuestion", "GrammarTable", "LessonContent", "CurriculumExercise", "CurriculumTopic",
    "CurriculumUnit", "CurriculumLevel", "CurriculumCourse", "ContentValidationReport", "ContentBatch",
    "ConversationTurn", "Conversation", "GrammarLesson", "QuizQuestion", "TopicWord", "Topic",
    "Collocation", "ExampleSentence", "WordForm", "WordMeaning", "Translation", "SynonymAntonym",
    "DictionaryEntry", "DictionaryTombstone",
]


class Migration(migrations.Migration):

    dependencies = [
        ("learning", "0012_dictionarytombstone"),
        ("dictionary", "0001_initial"),
        ("catalog_topics", "0001_initial"),
        ("catalog_conversations", "0001_initial"),
        ("catalog_grammar", "0001_initial"),
        ("catalog_quiz", "0001_initial"),
        ("curriculum", "0001_initial"),
        ("curriculum_progress", "0001_initial"),
        ("userdata", "0001_initial"),
    ]

    operations = release_legacy_models(
        [migrations.RemoveField(model_name="topic", name="words")]
        + [migrations.DeleteModel(name=name) for name in MOVED_MODELS]
    )
