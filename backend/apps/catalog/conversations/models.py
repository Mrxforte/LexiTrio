from django.db import models


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
        db_table = "learning_conversation"
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
        db_table = "learning_conversationturn"
        ordering = ["turn_index"]
        constraints = [
            models.UniqueConstraint(
                fields=["conversation", "turn_index"], name="unique_conversation_turn"
            )
        ]
