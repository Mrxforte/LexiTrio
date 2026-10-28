from django.db import models


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

    class Meta:
        db_table = "learning_quizquestion"
