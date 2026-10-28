from rest_framework import serializers

from shared.serializers import CamelCaseFieldsSerializer

from .models import (
    Collocation,
    DictionaryEntry,
    ExampleSentence,
    SynonymAntonym,
    Translation,
    WordForm,
    WordMeaning,
)


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
