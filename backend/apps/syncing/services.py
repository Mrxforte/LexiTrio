"""Two-way synchronisation of a learner's data with the Android client.

Conflict policy (documented in ``docs/caching-and-sync.md``): last writer wins
per row, decided by the client-supplied ``updated_at_ms``; an older write never
overwrites a newer one, deletions are soft (``is_deleted``) and quiz attempts
are append-only. Every row is addressed by a client UUID, so repeating a
request is idempotent.
"""

from django.db import transaction

from apps.userdata.models import (
    QuizAttempt,
    SavedWord,
    UserPreferences,
    UserProgress,
    VocabularyCollection,
)


@transaction.atomic
def apply_sync(user, payload):
    _merge_rows(VocabularyCollection, user, payload.get("collections", []))
    _apply_deletions(VocabularyCollection, user, payload.get("deletedCollectionIds", []))

    _merge_rows(SavedWord, user, payload.get("savedWords", []))
    _apply_deletions(SavedWord, user, payload.get("deletedSavedWordIds", []))

    for values in payload.get("quizAttempts", []):
        client_id = values.pop("client_id")
        QuizAttempt.objects.get_or_create(user=user, client_id=client_id, defaults=values)

    if progress := payload.get("progress"):
        _merge_singleton(UserProgress, user, progress)

    if preferences := payload.get("preferences"):
        _merge_singleton(UserPreferences, user, preferences)


def _merge_rows(model, user, rows):
    """Upsert rows by client id unless the stored row is newer; soft-delete flagged ones."""
    for values in rows:
        client_id = values.pop("client_id")
        is_deleted = values.pop("is_deleted", False)
        existing = model.objects.filter(user=user, client_id=client_id).first()
        if existing and values.get("updated_at_ms", 0) < existing.updated_at_ms:
            continue
        if is_deleted:
            model.objects.filter(user=user, client_id=client_id).update(
                is_deleted=True,
                updated_at_ms=values.get("updated_at_ms", 0),
            )
            continue
        model.objects.update_or_create(user=user, client_id=client_id, defaults=values)


def _apply_deletions(model, user, deletions):
    """Soft-delete rows named in tombstones, unless they were edited after the deletion."""
    for deletion in deletions:
        model.objects.filter(
            user=user,
            client_id=deletion["client_id"],
            updated_at_ms__lte=deletion["deleted_at"],
        ).update(is_deleted=True, updated_at_ms=deletion["deleted_at"])


def _merge_singleton(model, user, values):
    current = model.objects.filter(user=user).first()
    if current is None or values.get("updated_at_ms", 0) >= current.updated_at_ms:
        model.objects.update_or_create(user=user, defaults=values)


def build_sync_snapshot(user):
    progress, _ = UserProgress.objects.get_or_create(user=user)
    preferences, _ = UserPreferences.objects.get_or_create(user=user)
    return {
        "collections": [
            {
                "clientId": str(item.client_id),
                "title": item.title,
                "description": item.description,
                "iconName": item.icon_name,
                "isCustom": item.is_custom,
                "isDeleted": item.is_deleted,
                "createdAt": item.created_at,
                "updatedAt": item.updated_at_ms,
            }
            for item in VocabularyCollection.objects.filter(user=user).order_by("created_at")
        ],
        "savedWords": [
            _saved_word_payload(item)
            for item in SavedWord.objects.filter(user=user).order_by("saved_at")
        ],
        "quizAttempts": [
            {
                "clientId": str(item.client_id),
                "timestamp": item.timestamp,
                "category": item.category,
                "totalQuestions": item.total_questions,
                "correctCount": item.correct_count,
                "timeSpentSec": item.time_spent_sec,
                "xpEarned": item.xp_earned,
            }
            for item in QuizAttempt.objects.filter(user=user).order_by("-timestamp")[:100]
        ],
        "progress": {
            "xp": progress.xp,
            "streakDays": progress.streak_days,
            "lastStudyDateTimestamp": progress.last_study_date_timestamp,
            "dailyGoalWords": progress.daily_goal_words,
            "todayWordsReviewed": progress.today_words_reviewed,
            "wordsMasteredCount": progress.words_mastered_count,
            "totalQuizzesCompleted": progress.total_quizzes_completed,
            "totalMinutesListened": progress.total_minutes_listened,
            "updatedAt": progress.updated_at_ms,
        },
        "preferences": {
            "appLanguage": preferences.app_language,
            "searchSourceLanguage": preferences.search_source_language,
            "speechRate": preferences.speech_rate,
            "speechPitch": preferences.speech_pitch,
            "selectedVoiceId": preferences.selected_voice_id,
            "autoPlayAudio": preferences.auto_play_audio,
            "dailyGoalWords": preferences.daily_goal_words,
            "darkMode": preferences.dark_mode,
            "updatedAt": preferences.updated_at_ms,
        },
    }


def _saved_word_payload(item):
    return {
        "clientId": str(item.client_id),
        "entryId": item.entry_id,
        "headword": item.headword,
        "language": item.language,
        "partOfSpeech": item.part_of_speech,
        "phoneticIpa": item.phonetic_ipa,
        "definition": item.definition,
        "translation": item.translation,
        "exampleSentence": item.example_sentence,
        "collectionClientId": (
            str(item.collection_client_id) if item.collection_client_id else None
        ),
        "savedAt": item.saved_at,
        "isFavorite": item.is_favorite,
        "status": item.status,
        "notes": item.notes,
        "repetitionNumber": item.repetition_number,
        "intervalDays": item.interval_days,
        "easeFactor": item.ease_factor,
        "nextReviewTimestamp": item.next_review_timestamp,
        "lastReviewTimestamp": item.last_review_timestamp,
        "lapsesCount": item.lapses_count,
        "isDeleted": item.is_deleted,
        "updatedAt": item.updated_at_ms,
    }
