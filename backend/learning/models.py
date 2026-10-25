import uuid

from django.conf import settings
from django.db import models


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
        ordering = ["-frequency_rank", "headword"]
        indexes = [models.Index(fields=["headword_normalized", "language"])]

    def __str__(self):
        return f"{self.headword} ({self.language})"


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
        ordering = ["sense_number", "id"]


class Translation(models.Model):
    class Quality(models.TextChoices):
        MACHINE = "machine", "Machine"
        WIKTIONARY = "wiktionary", "Wiktionary"
        REVIEWED = "reviewed", "Reviewed"

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


class ExampleSentence(models.Model):
    entry = models.ForeignKey(DictionaryEntry, related_name="examples", on_delete=models.CASCADE)
    meaning_id = models.PositiveBigIntegerField(default=0)
    sentence_en = models.TextField()
    sentence_ru = models.TextField(blank=True)
    sentence_uz = models.TextField(blank=True)


class SynonymAntonym(models.Model):
    meaning_id = models.PositiveBigIntegerField(db_index=True)
    word = models.CharField(max_length=200, db_index=True)
    is_antonym = models.BooleanField(default=False)


class WordForm(models.Model):
    entry = models.ForeignKey(DictionaryEntry, related_name="forms", on_delete=models.CASCADE)
    form_type = models.CharField(max_length=80)
    form_value = models.CharField(max_length=200)


class Collocation(models.Model):
    entry = models.ForeignKey(DictionaryEntry, related_name="collocations", on_delete=models.CASCADE)
    expression = models.CharField(max_length=300)
    translation_ru = models.CharField(max_length=300, blank=True)
    translation_uz = models.CharField(max_length=300, blank=True)


class Topic(models.Model):
    title_en = models.CharField(max_length=200)
    title_ru = models.CharField(max_length=200)
    title_uz_latn = models.CharField(max_length=200)
    title_uz_cyrl = models.CharField(max_length=200)
    category = models.CharField(max_length=80, db_index=True)
    icon_name = models.CharField(max_length=60, default="book")
    description_en = models.TextField(blank=True)
    description_ru = models.TextField(blank=True)
    description_uz_latn = models.TextField(blank=True)
    cefr_level = models.CharField(max_length=8, default="A2")
    estimated_minutes = models.PositiveSmallIntegerField(default=10)
    total_words = models.PositiveSmallIntegerField(default=15)
    words = models.ManyToManyField(DictionaryEntry, through="TopicWord", related_name="topics")

    class Meta:
        ordering = ["id"]


class TopicWord(models.Model):
    topic = models.ForeignKey(Topic, on_delete=models.CASCADE)
    word = models.ForeignKey(DictionaryEntry, on_delete=models.CASCADE)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=["topic", "word"], name="unique_topic_word")
        ]


class Conversation(models.Model):
    title_en = models.CharField(max_length=200)
    title_ru = models.CharField(max_length=200)
    title_uz_latn = models.CharField(max_length=200)
    title_uz_cyrl = models.CharField(max_length=200)
    situation_en = models.TextField()
    situation_ru = models.TextField(blank=True)
    situation_uz_latn = models.TextField(blank=True)
    category = models.CharField(max_length=80, db_index=True)
    difficulty = models.CharField(max_length=8, default="A2")
    icon_name = models.CharField(max_length=60, default="chat")
    speaker_one_name = models.CharField(max_length=100, default="Speaker A")
    speaker_two_name = models.CharField(max_length=100, default="Speaker B")

    class Meta:
        ordering = ["id"]


class ConversationTurn(models.Model):
    conversation = models.ForeignKey(Conversation, related_name="turns", on_delete=models.CASCADE)
    turn_index = models.PositiveSmallIntegerField()
    speaker_role = models.CharField(max_length=80)
    is_speaker_one = models.BooleanField(default=True)
    text_en = models.TextField()
    text_ru = models.TextField(blank=True)
    text_uz = models.TextField(blank=True)
    cultural_note = models.TextField(blank=True)

    class Meta:
        ordering = ["turn_index"]
        constraints = [
            models.UniqueConstraint(
                fields=["conversation", "turn_index"], name="unique_conversation_turn"
            )
        ]


class GrammarLesson(models.Model):
    title_en = models.CharField(max_length=200)
    title_ru = models.CharField(max_length=200)
    title_uz_latn = models.CharField(max_length=200)
    title_uz_cyrl = models.CharField(max_length=200)
    title_uz_cyrl = models.CharField(max_length=200)
    category = models.CharField(max_length=100, db_index=True)
    level = models.CharField(max_length=8, default="B1")
    summary_en = models.TextField()
    summary_ru = models.TextField(blank=True)
    summary_uz_latn = models.TextField(blank=True)
    rule_details_json = models.TextField(blank=True)
    examples_json = models.TextField(blank=True)


class QuizQuestion(models.Model):
    type = models.CharField(max_length=40, default="WORD_TO_TRANSLATION", db_index=True)
    question_text = models.TextField()
    prompt_audio_text = models.TextField(blank=True)
    prompt_language = models.CharField(max_length=12, default="en")
    correct_answer = models.CharField(max_length=500)
    option_a = models.CharField(max_length=500)
    option_b = models.CharField(max_length=500)
    option_c = models.CharField(max_length=500)
    option_d = models.CharField(max_length=500)
    explanation = models.TextField(blank=True)
    category = models.CharField(max_length=80, default="General", db_index=True)
    difficulty = models.CharField(max_length=8, default="B1")


class VocabularyCollection(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, related_name="vocabulary_collections", on_delete=models.CASCADE)
    client_id = models.UUIDField(default=uuid.uuid4)
    title = models.CharField(max_length=150)
    description = models.TextField(blank=True)
    icon_name = models.CharField(max_length=60, default="folder")
    is_custom = models.BooleanField(default=True)
    is_deleted = models.BooleanField(default=False)
    updated_at_ms = models.PositiveBigIntegerField(default=0)
    created_at = models.BigIntegerField()

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=["user", "client_id"], name="unique_user_collection_client_id")
        ]


class SavedWord(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, related_name="saved_words", on_delete=models.CASCADE)
    client_id = models.UUIDField()
    entry_id = models.PositiveBigIntegerField(null=True, blank=True)
    headword = models.CharField(max_length=200)
    language = models.CharField(max_length=12, default="en")
    part_of_speech = models.CharField(max_length=40, default="noun")
    phonetic_ipa = models.CharField(max_length=200, blank=True)
    definition = models.TextField(blank=True)
    translation = models.TextField(blank=True)
    example_sentence = models.TextField(blank=True)
    collection_client_id = models.UUIDField(null=True, blank=True)
    saved_at = models.BigIntegerField()
    is_favorite = models.BooleanField(default=False)
    status = models.CharField(max_length=20, default="NEW")
    notes = models.TextField(blank=True)
    repetition_number = models.PositiveSmallIntegerField(default=0)
    interval_days = models.PositiveIntegerField(default=0)
    ease_factor = models.FloatField(default=2.5)
    next_review_timestamp = models.BigIntegerField()
    last_review_timestamp = models.BigIntegerField(default=0)
    lapses_count = models.PositiveIntegerField(default=0)
    is_deleted = models.BooleanField(default=False)
    updated_at_ms = models.PositiveBigIntegerField(default=0)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=["user", "client_id"], name="unique_user_saved_word_client_id")
        ]
        indexes = [models.Index(fields=["user", "is_deleted", "updated_at_ms"])]


class QuizAttempt(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, related_name="quiz_attempts", on_delete=models.CASCADE)
    client_id = models.UUIDField()
    timestamp = models.BigIntegerField()
    category = models.CharField(max_length=100)
    total_questions = models.PositiveSmallIntegerField()
    correct_count = models.PositiveSmallIntegerField()
    time_spent_sec = models.PositiveIntegerField()
    xp_earned = models.PositiveIntegerField()

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=["user", "client_id"], name="unique_user_attempt_client_id")
        ]


class UserProgress(models.Model):
    user = models.OneToOneField(settings.AUTH_USER_MODEL, related_name="learning_progress", on_delete=models.CASCADE)
    xp = models.PositiveIntegerField(default=0)
    streak_days = models.PositiveIntegerField(default=0)
    last_study_date_timestamp = models.BigIntegerField(default=0)
    daily_goal_words = models.PositiveSmallIntegerField(default=10)
    today_words_reviewed = models.PositiveIntegerField(default=0)
    words_mastered_count = models.PositiveIntegerField(default=0)
    total_quizzes_completed = models.PositiveIntegerField(default=0)
    total_minutes_listened = models.PositiveIntegerField(default=0)
    updated_at_ms = models.PositiveBigIntegerField(default=0)


class UserPreferences(models.Model):
    user = models.OneToOneField(
        settings.AUTH_USER_MODEL, related_name="learning_preferences", on_delete=models.CASCADE
    )
    app_language = models.CharField(max_length=12, default="uz")
    search_source_language = models.CharField(max_length=12, default="en")
    speech_rate = models.FloatField(default=1.0)
    speech_pitch = models.FloatField(default=1.0)
    selected_voice_id = models.CharField(max_length=200, blank=True)
    auto_play_audio = models.BooleanField(default=False)
    daily_goal_words = models.PositiveSmallIntegerField(default=10)
    dark_mode = models.BooleanField(null=True, blank=True)
    updated_at_ms = models.PositiveBigIntegerField(default=0)
