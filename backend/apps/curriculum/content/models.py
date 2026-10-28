from django.db import models

from .choices import (
    ContentBatchStatus,
    ExerciseType,
    GrammarTableType,
    PublicationStatus,
    ValidationStatus,
)


class CurriculumCourse(models.Model):
    code = models.CharField(max_length=40, unique=True)
    title_en = models.CharField(max_length=200)
    title_ru = models.CharField(max_length=200, blank=True)
    title_uz = models.CharField(max_length=200, blank=True)
    language = models.CharField(max_length=12, db_index=True)  # "en" or "ru"
    description = models.TextField(blank=True)

    class Meta:
        db_table = "learning_curriculumcourse"

    def __str__(self):
        return f"{self.title_en} ({self.language})"


class CurriculumLevel(models.Model):
    course = models.ForeignKey(CurriculumCourse, related_name="levels", on_delete=models.CASCADE)
    code = models.CharField(max_length=20, db_index=True)  # A1, A2, B1, B2, C1, C2
    order = models.PositiveSmallIntegerField(default=1)
    title = models.CharField(max_length=200)
    description = models.TextField(blank=True)

    class Meta:
        db_table = "learning_curriculumlevel"
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
        db_table = "learning_curriculumunit"
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
        db_table = "learning_curriculumtopic"
        ordering = ["order", "id"]


class LessonContent(models.Model):
    Status = PublicationStatus

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

    class Meta:
        db_table = "learning_lessoncontent"


class GrammarTable(models.Model):
    TableType = GrammarTableType

    lesson = models.ForeignKey(LessonContent, related_name="grammar_tables", on_delete=models.CASCADE)
    title_en = models.CharField(max_length=200)
    title_ru = models.CharField(max_length=200, blank=True)
    title_uz = models.CharField(max_length=200, blank=True)
    table_type = models.CharField(max_length=40, choices=TableType.choices, default=TableType.RULE)
    headers_json = models.TextField(default="[]")  # e.g. ["Subject", "Verb (Be)", "Example"]
    rows_json = models.TextField(default="[]")     # e.g. [["I", "am", "I am a student."], ...]

    class Meta:
        db_table = "learning_grammartable"


class CurriculumExercise(models.Model):
    Type = ExerciseType
    Status = PublicationStatus

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
    validation_status = models.CharField(max_length=20, default=ValidationStatus.VALID, db_index=True)
    publication_status = models.CharField(max_length=16, choices=Status.choices, default=Status.PUBLISHED, db_index=True)

    class Meta:
        db_table = "learning_curriculumexercise"


class TestBankQuestion(models.Model):
    Status = PublicationStatus

    topic = models.ForeignKey(CurriculumTopic, related_name="test_questions", null=True, blank=True, on_delete=models.CASCADE)
    language = models.CharField(max_length=12, db_index=True)  # "en" or "ru"
    cefr_level = models.CharField(max_length=12, default="A1", db_index=True)
    question_type = models.CharField(max_length=40, default=ExerciseType.MULTIPLE_CHOICE, db_index=True)
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
    validation_status = models.CharField(max_length=20, default=ValidationStatus.VALID, db_index=True)
    publication_status = models.CharField(max_length=16, choices=Status.choices, default=Status.PUBLISHED, db_index=True)

    class Meta:
        db_table = "learning_testbankquestion"


class ContentBatch(models.Model):
    Status = ContentBatchStatus

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

    class Meta:
        db_table = "learning_contentbatch"


class ContentValidationReport(models.Model):
    batch = models.ForeignKey(ContentBatch, related_name="validation_reports", null=True, blank=True, on_delete=models.CASCADE)
    checked_at = models.DateTimeField(auto_now_add=True)
    total_items = models.IntegerField(default=0)
    valid_items = models.IntegerField(default=0)
    invalid_items = models.IntegerField(default=0)
    duplicate_items = models.IntegerField(default=0)
    error_details_json = models.TextField(default="[]")

    class Meta:
        db_table = "learning_contentvalidationreport"
