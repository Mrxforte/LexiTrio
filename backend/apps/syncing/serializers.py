from rest_framework import serializers

from apps.userdata.models import (
    QuizAttempt,
    SavedWord,
    UserPreferences,
    UserProgress,
    VocabularyCollection,
)


class CollectionSerializer(serializers.ModelSerializer):
    clientId = serializers.UUIDField(source="client_id")
    iconName = serializers.CharField(source="icon_name")
    createdAt = serializers.IntegerField(source="created_at")
    updatedAt = serializers.IntegerField(source="updated_at_ms", required=False, default=0)
    isCustom = serializers.BooleanField(source="is_custom")
    isDeleted = serializers.BooleanField(source="is_deleted", required=False, default=False)

    class Meta:
        model = VocabularyCollection
        fields = (
            "clientId",
            "title",
            "description",
            "iconName",
            "isCustom",
            "isDeleted",
            "createdAt",
            "updatedAt",
        )


class SavedWordSerializer(serializers.ModelSerializer):
    clientId = serializers.UUIDField(source="client_id")
    entryId = serializers.IntegerField(source="entry_id", required=False, allow_null=True)
    phoneticIpa = serializers.CharField(source="phonetic_ipa", required=False, allow_blank=True)
    partOfSpeech = serializers.CharField(source="part_of_speech", required=False)
    exampleSentence = serializers.CharField(source="example_sentence", required=False, allow_blank=True)
    collectionClientId = serializers.UUIDField(
        source="collection_client_id", required=False, allow_null=True
    )
    savedAt = serializers.IntegerField(source="saved_at")
    isFavorite = serializers.BooleanField(source="is_favorite")
    repetitionNumber = serializers.IntegerField(source="repetition_number")
    intervalDays = serializers.IntegerField(source="interval_days")
    easeFactor = serializers.FloatField(source="ease_factor")
    nextReviewTimestamp = serializers.IntegerField(source="next_review_timestamp")
    lastReviewTimestamp = serializers.IntegerField(source="last_review_timestamp")
    lapsesCount = serializers.IntegerField(source="lapses_count")
    isDeleted = serializers.BooleanField(source="is_deleted", required=False, default=False)
    updatedAt = serializers.IntegerField(source="updated_at_ms", required=False, default=0)

    class Meta:
        model = SavedWord
        fields = (
            "clientId",
            "entryId",
            "headword",
            "language",
            "partOfSpeech",
            "phoneticIpa",
            "definition",
            "translation",
            "exampleSentence",
            "collectionClientId",
            "savedAt",
            "isFavorite",
            "status",
            "notes",
            "repetitionNumber",
            "intervalDays",
            "easeFactor",
            "nextReviewTimestamp",
            "lastReviewTimestamp",
            "lapsesCount",
            "isDeleted",
            "updatedAt",
        )


class QuizAttemptSerializer(serializers.ModelSerializer):
    clientId = serializers.UUIDField(source="client_id")
    timestamp = serializers.IntegerField()
    totalQuestions = serializers.IntegerField(source="total_questions")
    correctCount = serializers.IntegerField(source="correct_count")
    timeSpentSec = serializers.IntegerField(source="time_spent_sec")
    xpEarned = serializers.IntegerField(source="xp_earned")

    class Meta:
        model = QuizAttempt
        fields = (
            "clientId",
            "timestamp",
            "category",
            "totalQuestions",
            "correctCount",
            "timeSpentSec",
            "xpEarned",
        )


class UserProgressSerializer(serializers.ModelSerializer):
    streakDays = serializers.IntegerField(source="streak_days", required=False)
    lastStudyDateTimestamp = serializers.IntegerField(
        source="last_study_date_timestamp", required=False
    )
    dailyGoalWords = serializers.IntegerField(source="daily_goal_words", required=False)
    todayWordsReviewed = serializers.IntegerField(source="today_words_reviewed", required=False)
    wordsMasteredCount = serializers.IntegerField(
        source="words_mastered_count", required=False
    )
    totalQuizzesCompleted = serializers.IntegerField(
        source="total_quizzes_completed", required=False
    )
    totalMinutesListened = serializers.IntegerField(
        source="total_minutes_listened", required=False
    )
    updatedAt = serializers.IntegerField(source="updated_at_ms", required=False, default=0)

    class Meta:
        model = UserProgress
        fields = (
            "xp",
            "streakDays",
            "lastStudyDateTimestamp",
            "dailyGoalWords",
            "todayWordsReviewed",
            "wordsMasteredCount",
            "totalQuizzesCompleted",
            "totalMinutesListened",
            "updatedAt",
        )


class UserPreferencesSerializer(serializers.ModelSerializer):
    appLanguage = serializers.CharField(source="app_language", required=False)
    searchSourceLanguage = serializers.CharField(source="search_source_language", required=False)
    speechRate = serializers.FloatField(source="speech_rate", required=False)
    speechPitch = serializers.FloatField(source="speech_pitch", required=False)
    selectedVoiceId = serializers.CharField(
        source="selected_voice_id", required=False, allow_blank=True
    )
    autoPlayAudio = serializers.BooleanField(source="auto_play_audio", required=False)
    dailyGoalWords = serializers.IntegerField(source="daily_goal_words", required=False)
    darkMode = serializers.BooleanField(source="dark_mode", required=False, allow_null=True)
    updatedAt = serializers.IntegerField(source="updated_at_ms", required=False, default=0)

    class Meta:
        model = UserPreferences
        fields = (
            "appLanguage",
            "searchSourceLanguage",
            "speechRate",
            "speechPitch",
            "selectedVoiceId",
            "autoPlayAudio",
            "dailyGoalWords",
            "darkMode",
            "updatedAt",
        )


class SyncDeletionSerializer(serializers.Serializer):
    clientId = serializers.UUIDField(source="client_id")
    deletedAt = serializers.IntegerField(source="deleted_at")


class SyncRequestSerializer(serializers.Serializer):
    collections = CollectionSerializer(many=True, required=False, default=list)
    deletedCollectionIds = SyncDeletionSerializer(many=True, required=False, default=list)
    savedWords = SavedWordSerializer(many=True, required=False, default=list)
    deletedSavedWordIds = SyncDeletionSerializer(many=True, required=False, default=list)
    quizAttempts = QuizAttemptSerializer(many=True, required=False, default=list)
    progress = UserProgressSerializer(required=False)
    preferences = UserPreferencesSerializer(required=False)
