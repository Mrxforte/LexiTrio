from django.conf import settings
from django.db import models

from apps.curriculum.content.models import CurriculumTopic

from .choices import TopicProgressStatus


class UserTopicProgress(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, related_name="topic_progresses", on_delete=models.CASCADE)
    topic = models.ForeignKey(CurriculumTopic, on_delete=models.CASCADE)
    status = models.CharField(max_length=20, default=TopicProgressStatus.NOT_STARTED)
    completed_exercises = models.IntegerField(default=0)
    total_exercises = models.IntegerField(default=0)
    quiz_score = models.IntegerField(default=0)
    time_spent_sec = models.IntegerField(default=0)
    updated_at_ms = models.BigIntegerField(default=0)

    class Meta:
        db_table = "learning_usertopicprogress"
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

    class Meta:
        db_table = "learning_mistakerecord"
