from django.contrib import admin

from .models import (
    Collocation,
    Conversation,
    ConversationTurn,
    DictionaryEntry,
    ExampleSentence,
    GrammarLesson,
    QuizQuestion,
    SavedWord,
    SynonymAntonym,
    Topic,
    TopicWord,
    Translation,
    UserProgress,
    UserPreferences,
    VocabularyCollection,
    WordForm,
    WordMeaning,
)

admin.site.register(
    [
        DictionaryEntry,
        WordMeaning,
        Translation,
        ExampleSentence,
        SynonymAntonym,
        WordForm,
        Collocation,
        Topic,
        TopicWord,
        Conversation,
        ConversationTurn,
        GrammarLesson,
        QuizQuestion,
        VocabularyCollection,
        SavedWord,
        UserProgress,
        UserPreferences,
    ]
)
