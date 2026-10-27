from rest_framework import serializers

from .models import (
    Collocation,
    ContentBatch,
    Conversation,
    ConversationTurn,
    CurriculumCourse,
    CurriculumExercise,
    CurriculumLevel,
    CurriculumTopic,
    CurriculumUnit,
    DictionaryEntry,
    ExampleSentence,
    GrammarLesson,
    GrammarTable,
    LessonContent,
    MistakeRecord,
    QuizQuestion,
    SynonymAntonym,
    TestBankQuestion,
    Topic,
    TopicWord,
    Translation,
    UserTopicProgress,
    WordForm,
    WordMeaning,
)


class CamelCaseFieldsSerializer(serializers.ModelSerializer):
    def to_representation(self, instance):
        result = super().to_representation(instance)
        return {_camel_case(key): value for key, value in result.items()}


def _camel_case(value):
    first, *rest = value.split("_")
    return first + "".join(part.capitalize() for part in rest)


class DictionaryEntrySerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = DictionaryEntry
        fields = "__all__"


class WordMeaningSerializer(CamelCaseFieldsSerializer):
    entryId = serializers.PrimaryKeyRelatedField(
        source="entry", queryset=DictionaryEntry.objects.all()
    )

    class Meta:
        model = WordMeaning
        fields = ("id", "entryId", "sense_number", "definition_en", "definition_ru",
                  "definition_uz", "register", "grammar_notes", "updated_at")


class TranslationSerializer(CamelCaseFieldsSerializer):
    entryId = serializers.PrimaryKeyRelatedField(
        source="entry", queryset=DictionaryEntry.objects.all()
    )

    class Meta:
        model = Translation
        fields = ("id", "entryId", "meaning_id", "target_language", "translation_text",
                  "transliteration", "context_note", "part_of_speech", "gender",
                  "number", "article", "quality")


class ExampleSentenceSerializer(CamelCaseFieldsSerializer):
    entryId = serializers.PrimaryKeyRelatedField(
        source="entry", queryset=DictionaryEntry.objects.all()
    )

    class Meta:
        model = ExampleSentence
        fields = ("id", "entryId", "meaning_id", "sentence_en", "sentence_ru", "sentence_uz")


class SynonymAntonymSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = SynonymAntonym
        fields = "__all__"


class WordFormSerializer(CamelCaseFieldsSerializer):
    entryId = serializers.PrimaryKeyRelatedField(
        source="entry", queryset=DictionaryEntry.objects.all()
    )

    class Meta:
        model = WordForm
        fields = ("id", "entryId", "form_type", "form_value")


class CollocationSerializer(CamelCaseFieldsSerializer):
    entryId = serializers.PrimaryKeyRelatedField(
        source="entry", queryset=DictionaryEntry.objects.all()
    )

    class Meta:
        model = Collocation
        fields = ("id", "entryId", "expression", "translation_ru", "translation_uz")


class TopicSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = Topic
        fields = "__all__"


class TopicWordSerializer(CamelCaseFieldsSerializer):
    topicId = serializers.PrimaryKeyRelatedField(source="topic", queryset=Topic.objects.all())
    wordId = serializers.PrimaryKeyRelatedField(
        source="word", queryset=DictionaryEntry.objects.all()
    )

    class Meta:
        model = TopicWord
        fields = ("id", "topicId", "wordId")


class ConversationSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = Conversation
        fields = "__all__"


class ConversationTurnSerializer(CamelCaseFieldsSerializer):
    conversationId = serializers.PrimaryKeyRelatedField(
        source="conversation", queryset=Conversation.objects.all()
    )

    class Meta:
        model = ConversationTurn
        fields = ("id", "conversationId", "turn_index", "speaker_role", "is_speaker_one",
                  "text_en", "text_ru", "text_uz", "cultural_note")


class GrammarLessonSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = GrammarLesson
        fields = "__all__"


class QuizQuestionSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = QuizQuestion
        fields = "__all__"


class SearchTranslationSerializer(CamelCaseFieldsSerializer):
    text = serializers.CharField(source="translation_text", read_only=True)

    class Meta:
        model = Translation
        fields = (
            "id",
            "text",
            "target_language",
            "part_of_speech",
            "gender",
            "number",
            "article",
            "context_note",
            "transliteration",
            "quality",
        )


class DictionarySearchResultSerializer(CamelCaseFieldsSerializer):
    """One search hit: the headword plus the translations for the asked pair."""

    word = serializers.CharField(source="headword", read_only=True)
    sourceLanguage = serializers.CharField(source="language", read_only=True)
    translations = serializers.SerializerMethodField()

    class Meta:
        model = DictionaryEntry
        fields = (
            "id",
            "headword",
            "headword_normalized",
            "word",
            "language",
            "sourceLanguage",
            "part_of_speech",
            "phonetic_ipa",
            "cefr_level",
            "frequency_rank",
            "article",
            "plural_form",
            "primary_meaning_en",
            "primary_meaning_ru",
            "primary_meaning_uz",
            "translations",
        )

    def get_translations(self, entry):
        from django.conf import settings

        selected = getattr(entry, "selected_translations", None)
        if selected is None:
            selected = list(entry.translations.all())
        limit = settings.DICTIONARY_SEARCH_TRANSLATIONS_PER_RESULT
        return SearchTranslationSerializer(selected[:limit], many=True).data


class CurriculumCourseSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = CurriculumCourse
        fields = "__all__"


class CurriculumLevelSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = CurriculumLevel
        fields = "__all__"


class CurriculumUnitSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = CurriculumUnit
        fields = "__all__"


class CurriculumTopicSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = CurriculumTopic
        fields = "__all__"


class GrammarTableSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = GrammarTable
        fields = "__all__"


class LessonContentSerializer(CamelCaseFieldsSerializer):
    grammarTables = GrammarTableSerializer(source="grammar_tables", many=True, read_only=True)

    class Meta:
        model = LessonContent
        fields = (
            "id",
            "topic",
            "simple_explanation_en",
            "simple_explanation_ru",
            "simple_explanation_uz",
            "real_life_analogy_en",
            "real_life_analogy_ru",
            "real_life_analogy_uz",
            "main_rule_en",
            "main_rule_ru",
            "main_rule_uz",
            "positive_examples_json",
            "negative_examples_json",
            "question_forms_json",
            "exceptions_json",
            "common_mistakes_json",
            "recap_en",
            "recap_ru",
            "recap_uz",
            "review_recommendation_en",
            "review_recommendation_ru",
            "publication_status",
            "grammarTables",
        )


class CurriculumExerciseSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = CurriculumExercise
        fields = "__all__"


class TestBankQuestionSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = TestBankQuestion
        fields = "__all__"


class ContentBatchSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = ContentBatch
        fields = "__all__"


class UserTopicProgressSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = UserTopicProgress
        fields = "__all__"


class MistakeRecordSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = MistakeRecord
        fields = "__all__"

