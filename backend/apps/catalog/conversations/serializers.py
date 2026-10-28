from rest_framework import serializers

from shared.serializers import CamelCaseFieldsSerializer

from .models import Conversation, ConversationTurn


class ConversationSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = Conversation
        fields = "__all__"


class ConversationTurnSerializer(CamelCaseFieldsSerializer):
    conversationId = serializers.PrimaryKeyRelatedField(
        source="conversation", queryset=Conversation.objects.all()
    )

    class Meta:
        model = ConversationTurn
        fields = ("id", "conversationId", "turn_index", "speaker_role", "is_speaker_one",
                  "text_en", "text_ru", "text_uz", "cultural_note")
