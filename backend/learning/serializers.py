from rest_framework import serializers

from .models import (
    Collocation,
    Conversation,
    ConversationTurn,
    DictionaryEntry,
    ExampleSentence,
    GrammarLesson,
    QuizQuestion,
    SynonymAntonym,
    Topic,
    TopicWord,
    Translation,
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
                  "transliteration", "context_note")


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
