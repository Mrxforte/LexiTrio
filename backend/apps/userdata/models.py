import uuid

from django.conf import settings
from django.db import models


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
        db_table = "learning_vocabularycollection"
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
        db_table = "learning_savedword"
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
        db_table = "learning_quizattempt"
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

    class Meta:
        db_table = "learning_userprogress"


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

    class Meta:
        db_table = "learning_userpreferences"
