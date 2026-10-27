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
        indexes = [
            models.Index(fields=["headword_normalized", "language"]),
            models.Index(fields=["language", "-frequency_rank", "headword"], name="dict_lang_freq_headword_idx"),
        ]

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
    updated_at = models.DateTimeField(auto_now=True, db_index=True)

    class Meta:
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


class CurriculumCourse(models.Model):
    code = models.CharField(max_length=40, unique=True)
    title_en = models.CharField(max_length=200)
    title_ru = models.CharField(max_length=200, blank=True)
    title_uz = models.CharField(max_length=200, blank=True)
    language = models.CharField(max_length=12, db_index=True)  # "en" or "ru"
    description = models.TextField(blank=True)

    def __str__(self):
        return f"{self.title_en} ({self.language})"


class CurriculumLevel(models.Model):
    course = models.ForeignKey(CurriculumCourse, related_name="levels", on_delete=models.CASCADE)
    code = models.CharField(max_length=20, db_index=True)  # A1, A2, B1, B2, C1, C2
    order = models.PositiveSmallIntegerField(default=1)
    title = models.CharField(max_length=200)
    description = models.TextField(blank=True)

    class Meta:
        ordering = ["order"]

    def __str__(self):
        return f"{self.course.code} - {self.code}"


class CurriculumUnit(models.Model):
    level = models.ForeignKey(CurriculumLevel, related_name="units", on_delete=models.CASCADE)
    order = models.PositiveSmallIntegerField(default=1)
    title_en = models.CharField(max_length=200)
    title_ru = models.CharField(max_length=200, blank=True)
    title_uz = models.CharField(max_length=200, blank=True)
    summary = models.TextField(blank=True)

    class Meta:
        ordering = ["order"]


class CurriculumTopic(models.Model):
    unit = models.ForeignKey(CurriculumUnit, related_name="topics", null=True, blank=True, on_delete=models.SET_NULL)
    topic_code = models.CharField(max_length=100, unique=True)
    language = models.CharField(max_length=12, db_index=True)  # "en" or "ru"
    title_en = models.CharField(max_length=200)
    title_ru = models.CharField(max_length=200, blank=True)
    title_uz = models.CharField(max_length=200, blank=True)
    category = models.CharField(max_length=80, db_index=True)  # Grammar, Vocabulary, etc.
    cefr_level = models.CharField(max_length=12, default="A1", db_index=True)
    estimated_minutes = models.PositiveSmallIntegerField(default=15)
    order = models.PositiveIntegerField(default=1)
    icon_name = models.CharField(max_length=60, default="book")
    summary_en = models.TextField(blank=True)
    summary_ru = models.TextField(blank=True)
    summary_uz = models.TextField(blank=True)

    class Meta:
        ordering = ["order", "id"]


class LessonContent(models.Model):
    class Status(models.TextChoices):
        DRAFT = "draft", "Draft"
        REVIEW = "review", "Review"
        APPROVED = "approved", "Approved"
        PUBLISHED = "published", "Published"
        REJECTED = "rejected", "Rejected"

    topic = models.OneToOneField(CurriculumTopic, related_name="lesson_content", on_delete=models.CASCADE)
    simple_explanation_en = models.TextField()
    simple_explanation_ru = models.TextField(blank=True)
    simple_explanation_uz = models.TextField(blank=True)
    real_life_analogy_en = models.TextField(blank=True)
    real_life_analogy_ru = models.TextField(blank=True)
    real_life_analogy_uz = models.TextField(blank=True)
    main_rule_en = models.TextField()
    main_rule_ru = models.TextField(blank=True)
    main_rule_uz = models.TextField(blank=True)
    positive_examples_json = models.TextField(default="[]")
    negative_examples_json = models.TextField(default="[]")
    question_forms_json = models.TextField(default="[]")
    exceptions_json = models.TextField(default="[]")
    common_mistakes_json = models.TextField(default="[]")
    recap_en = models.TextField(blank=True)
    recap_ru = models.TextField(blank=True)
    recap_uz = models.TextField(blank=True)
    review_recommendation_en = models.TextField(blank=True)
    review_recommendation_ru = models.TextField(blank=True)
    publication_status = models.CharField(max_length=16, choices=Status.choices, default=Status.PUBLISHED, db_index=True)


class GrammarTable(models.Model):
    class TableType(models.TextChoices):
        RULE = "RULE", "Rule"
        WORD_FORM = "WORD_FORM", "Word Form"
        VERB_CONJUGATION = "VERB_CONJUGATION", "Verb Conjugation"
        TENSE_COMPARISON = "TENSE_COMPARISON", "Tense Comparison"
        SENTENCE_STRUCTURE = "SENTENCE_STRUCTURE", "Sentence Structure"

    lesson = models.ForeignKey(LessonContent, related_name="grammar_tables", on_delete=models.CASCADE)
    title_en = models.CharField(max_length=200)
    title_ru = models.CharField(max_length=200, blank=True)
    title_uz = models.CharField(max_length=200, blank=True)
    table_type = models.CharField(max_length=40, choices=TableType.choices, default=TableType.RULE)
    headers_json = models.TextField(default="[]")  # e.g. ["Subject", "Verb (Be)", "Example"]
    rows_json = models.TextField(default="[]")     # e.g. [["I", "am", "I am a student."], ...]


class CurriculumExercise(models.Model):
    class Type(models.TextChoices):
        MULTIPLE_CHOICE = "MULTIPLE_CHOICE", "Multiple Choice"
        FILL_IN_BLANK = "FILL_IN_BLANK", "Fill in Blank"
        TYPE_MISSING_WORD = "TYPE_MISSING_WORD", "Type Missing Word"
        SENTENCE_ORDERING = "SENTENCE_ORDERING", "Sentence Ordering"
        WORD_MATCHING = "WORD_MATCHING", "Word Matching"
        ERROR_IDENTIFICATION = "ERROR_IDENTIFICATION", "Error Identification"
        ERROR_CORRECTION = "ERROR_CORRECTION", "Error Correction"
        TRANSLATION = "TRANSLATION", "Translation"
        AFFIRMATIVE_TO_NEGATIVE = "AFFIRMATIVE_TO_NEGATIVE", "Affirmative to Negative"
        SENTENCE_TO_QUESTION = "SENTENCE_TO_QUESTION", "Sentence to Question"
        CONJUGATION = "CONJUGATION", "Conjugation"
        SHORT_ANSWER = "SHORT_ANSWER", "Short Answer"
        LISTENING = "LISTENING", "Listening"
        CONTEXTUAL_VOCAB = "CONTEXTUAL_VOCAB", "Contextual Vocab"
        MIXED_REVIEW = "MIXED_REVIEW", "Mixed Review"

    class Status(models.TextChoices):
        DRAFT = "draft", "Draft"
        REVIEW = "review", "Review"
        APPROVED = "approved", "Approved"
        PUBLISHED = "published", "Published"
        REJECTED = "rejected", "Rejected"

    topic = models.ForeignKey(CurriculumTopic, related_name="exercises", null=True, blank=True, on_delete=models.CASCADE)
    language = models.CharField(max_length=12, db_index=True)  # "en" or "ru"
    cefr_level = models.CharField(max_length=12, default="A1", db_index=True)
    skill_category = models.CharField(max_length=80, default="Grammar", db_index=True)
    difficulty = models.CharField(max_length=12, default="Medium")
    exercise_type = models.CharField(max_length=40, choices=Type.choices, default=Type.MULTIPLE_CHOICE, db_index=True)
    prompt_en = models.TextField()
    prompt_ru = models.TextField(blank=True)
    prompt_uz = models.TextField(blank=True)
    instructions = models.TextField(blank=True)
    audio_text = models.TextField(blank=True)
    options_json = models.TextField(default="[]")
    correct_answer = models.CharField(max_length=500)
    accepted_variants_json = models.TextField(default="[]")
    explanation_en = models.TextField(blank=True)
    explanation_ru = models.TextField(blank=True)
    explanation_uz = models.TextField(blank=True)
    hint_en = models.TextField(blank=True)
    hint_ru = models.TextField(blank=True)
    hint_uz = models.TextField(blank=True)
    related_rule = models.CharField(max_length=200, blank=True)
    tags = models.CharField(max_length=200, blank=True)
    validation_status = models.CharField(max_length=20, default="valid", db_index=True)
    publication_status = models.CharField(max_length=16, choices=Status.choices, default=Status.PUBLISHED, db_index=True)


class TestBankQuestion(models.Model):
    class Status(models.TextChoices):
        DRAFT = "draft", "Draft"
        REVIEW = "review", "Review"
        APPROVED = "approved", "Approved"
        PUBLISHED = "published", "Published"
        REJECTED = "rejected", "Rejected"

    topic = models.ForeignKey(CurriculumTopic, related_name="test_questions", null=True, blank=True, on_delete=models.CASCADE)
    language = models.CharField(max_length=12, db_index=True)  # "en" or "ru"
    cefr_level = models.CharField(max_length=12, default="A1", db_index=True)
    question_type = models.CharField(max_length=40, default="MULTIPLE_CHOICE", db_index=True)
    prompt_en = models.TextField()
    prompt_ru = models.TextField(blank=True)
    prompt_uz = models.TextField(blank=True)
    options_json = models.TextField(default="[]")
    correct_answer = models.CharField(max_length=500)
    accepted_variants_json = models.TextField(default="[]")
    explanation_en = models.TextField(blank=True)
    explanation_ru = models.TextField(blank=True)
    explanation_uz = models.TextField(blank=True)
    difficulty = models.CharField(max_length=12, default="Medium")
    validation_status = models.CharField(max_length=20, default="valid", db_index=True)
    publication_status = models.CharField(max_length=16, choices=Status.choices, default=Status.PUBLISHED, db_index=True)


class ContentBatch(models.Model):
    class Status(models.TextChoices):
        INIT = "init", "Initialized"
        IN_PROGRESS = "in_progress", "In Progress"
        COMPLETED = "completed", "Completed"
        FAILED = "failed", "Failed"

    batch_code = models.CharField(max_length=100, unique=True)
    target_language = models.CharField(max_length=12)
    content_type = models.CharField(max_length=40)  # EXERCISE, TEST_QUESTION, TOPIC, LESSON
    requested_count = models.IntegerField(default=0)
    generated_count = models.IntegerField(default=0)
    validated_count = models.IntegerField(default=0)
    approved_count = models.IntegerField(default=0)
    rejected_count = models.IntegerField(default=0)
    duplicate_count = models.IntegerField(default=0)
    status = models.CharField(max_length=20, choices=Status.choices, default=Status.INIT)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)


class ContentValidationReport(models.Model):
    batch = models.ForeignKey(ContentBatch, related_name="validation_reports", null=True, blank=True, on_delete=models.CASCADE)
    checked_at = models.DateTimeField(auto_now_add=True)
    total_items = models.IntegerField(default=0)
    valid_items = models.IntegerField(default=0)
    invalid_items = models.IntegerField(default=0)
    duplicate_items = models.IntegerField(default=0)
    error_details_json = models.TextField(default="[]")


class UserTopicProgress(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, related_name="topic_progresses", on_delete=models.CASCADE)
    topic = models.ForeignKey(CurriculumTopic, on_delete=models.CASCADE)
    status = models.CharField(max_length=20, default="NOT_STARTED")  # NOT_STARTED, IN_PROGRESS, COMPLETED, MASTERED
    completed_exercises = models.IntegerField(default=0)
    total_exercises = models.IntegerField(default=0)
    quiz_score = models.IntegerField(default=0)
    time_spent_sec = models.IntegerField(default=0)
    updated_at_ms = models.BigIntegerField(default=0)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=["user", "topic"], name="unique_user_topic_progress")
        ]


class MistakeRecord(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, related_name="mistake_records", on_delete=models.CASCADE)
    exercise_id = models.BigIntegerField(db_index=True)
    question_type = models.CharField(max_length=40, default="EXERCISE")
    user_answer = models.CharField(max_length=500)
    correct_answer = models.CharField(max_length=500)
    times_failed = models.IntegerField(default=1)
    is_mastered = models.BooleanField(default=False)
    updated_at_ms = models.BigIntegerField(default=0)

