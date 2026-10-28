from django.db import models

from apps.dictionary.entries.models import DictionaryEntry


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
        db_table = "learning_topic"
        ordering = ["id"]


class TopicWord(models.Model):
    topic = models.ForeignKey(Topic, on_delete=models.CASCADE)
    word = models.ForeignKey(DictionaryEntry, on_delete=models.CASCADE)

    class Meta:
        db_table = "learning_topicword"
        constraints = [
            models.UniqueConstraint(fields=["topic", "word"], name="unique_topic_word")
        ]
