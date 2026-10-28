from django.conf import settings
from rest_framework import serializers

from apps.dictionary.entries.models import DictionaryEntry, Translation
from shared.serializers import CamelCaseFieldsSerializer


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
        selected = getattr(entry, "selected_translations", None)
        if selected is None:
            selected = list(entry.translations.all())
        limit = settings.DICTIONARY_SEARCH_TRANSLATIONS_PER_RESULT
        return SearchTranslationSerializer(selected[:limit], many=True).data
