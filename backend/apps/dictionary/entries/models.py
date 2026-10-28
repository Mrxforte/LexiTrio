from django.db import models

from .choices import TranslationQuality


class DictionaryEntry(models.Model):
    headword = models.CharField(max_length=200, db_index=True)
    headword_normalized = models.CharField(max_length=200, db_index=True)
    language = models.CharField(max_length=12, db_index=True)
    phonetic_ipa = models.CharField(max_length=200, blank=True)
    part_of_speech = models.CharField(max_length=40, default="noun")
    cefr_level = models.CharField(max_length=8, default="B1")
    frequency_rank = models.IntegerField(default=0)
    lemma = models.CharField(max_length=200, blank=True)
    alternative_spellings = models.TextField(blank=True)
    transliteration = models.CharField(max_length=200, blank=True)
    etymology = models.TextField(blank=True)
    verified = models.BooleanField(default=True)
    source = models.CharField(max_length=200, default="LexiTrio Core Corpus")
    primary_meaning_en = models.TextField(blank=True)
    primary_meaning_ru = models.TextField(blank=True)
    primary_meaning_uz = models.TextField(blank=True)
    article = models.CharField(max_length=8, blank=True)
    plural_form = models.CharField(max_length=200, blank=True)
    comparative_form = models.CharField(max_length=200, blank=True)
    superlative_form = models.CharField(max_length=200, blank=True)
    updated_at = models.DateTimeField(auto_now=True, db_index=True)

    class Meta:
        db_table = "learning_dictionaryentry"
        ordering = ["-frequency_rank", "headword"]
        indexes = [
            models.Index(fields=["headword_normalized", "language"]),
            models.Index(fields=["language", "-frequency_rank", "headword"], name="dict_lang_freq_headword_idx"),
        ]

    def __str__(self):
        return f"{self.headword} ({self.language})"


class DictionaryTombstone(models.Model):
    """Records a deleted entry so incremental sync can tell clients to drop it."""

    entry_id = models.PositiveBigIntegerField(db_index=True)
    deleted_at = models.DateTimeField(auto_now_add=True, db_index=True)

    class Meta:
        db_table = "learning_dictionarytombstone"

    def __str__(self):
        return f"deleted entry {self.entry_id}"


class WordMeaning(models.Model):
    entry = models.ForeignKey(DictionaryEntry, related_name="meanings", on_delete=models.CASCADE)
    sense_number = models.PositiveSmallIntegerField(default=1)
    definition_en = models.TextField()
    definition_ru = models.TextField(blank=True)
    definition_uz = models.TextField(blank=True)
    register = models.CharField(max_length=40, default="general")
    grammar_notes = models.TextField(blank=True)
    updated_at = models.DateTimeField(auto_now=True, db_index=True)

    class Meta:
        db_table = "learning_wordmeaning"
        ordering = ["sense_number", "id"]


class Translation(models.Model):
    Quality = TranslationQuality

    entry = models.ForeignKey(DictionaryEntry, related_name="translations", on_delete=models.CASCADE)
    meaning_id = models.PositiveBigIntegerField(default=0)
    target_language = models.CharField(max_length=12, db_index=True)
    translation_text = models.CharField(max_length=300, db_index=True)
    transliteration = models.CharField(max_length=200, blank=True)
    context_note = models.TextField(blank=True)
    part_of_speech = models.CharField(max_length=40, blank=True)
    gender = models.CharField(max_length=8, blank=True)
    number = models.CharField(max_length=16, blank=True)
    article = models.CharField(max_length=8, blank=True)
    quality = models.CharField(
        max_length=16, choices=Quality.choices, default=Quality.MACHINE, db_index=True
    )
    updated_at = models.DateTimeField(auto_now=True, db_index=True)

    class Meta:
        db_table = "learning_translation"
        indexes = [
            models.Index(
                fields=["entry_id", "target_language"], name="translation_entry_target_idx"
            ),
            models.Index(
                fields=["target_language", "translation_text"], name="trans_target_text_idx"
            ),
        ]


class ExampleSentence(models.Model):
    entry = models.ForeignKey(DictionaryEntry, related_name="examples", on_delete=models.CASCADE)
    meaning_id = models.PositiveBigIntegerField(default=0)
    sentence_en = models.TextField()
    sentence_ru = models.TextField(blank=True)
    sentence_uz = models.TextField(blank=True)

    class Meta:
        db_table = "learning_examplesentence"


class SynonymAntonym(models.Model):
    meaning_id = models.PositiveBigIntegerField(db_index=True)
    word = models.CharField(max_length=200, db_index=True)
    is_antonym = models.BooleanField(default=False)

    class Meta:
        db_table = "learning_synonymantonym"


class WordForm(models.Model):
    entry = models.ForeignKey(DictionaryEntry, related_name="forms", on_delete=models.CASCADE)
    form_type = models.CharField(max_length=80)
    form_value = models.CharField(max_length=200)

    class Meta:
        db_table = "learning_wordform"


class Collocation(models.Model):
    entry = models.ForeignKey(DictionaryEntry, related_name="collocations", on_delete=models.CASCADE)
    expression = models.CharField(max_length=300)
    translation_ru = models.CharField(max_length=300, blank=True)
    translation_uz = models.CharField(max_length=300, blank=True)

    class Meta:
        db_table = "learning_collocation"
