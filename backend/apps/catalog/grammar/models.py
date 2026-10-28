from django.db import models


class GrammarLesson(models.Model):
    title_en = models.CharField(max_length=200)
    title_ru = models.CharField(max_length=200)
    title_uz_latn = models.CharField(max_length=200)
    title_uz_cyrl = models.CharField(max_length=200)
    category = models.CharField(max_length=100, db_index=True)
    level = models.CharField(max_length=8, default="B1")
    summary_en = models.TextField()
    summary_ru = models.TextField(blank=True)
    summary_uz_latn = models.TextField(blank=True)
    rule_details_json = models.TextField(blank=True)
    examples_json = models.TextField(blank=True)

    class Meta:
        db_table = "learning_grammarlesson"
