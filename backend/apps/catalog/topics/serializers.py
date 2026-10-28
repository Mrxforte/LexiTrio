from rest_framework import serializers

from apps.dictionary.entries.models import DictionaryEntry
from shared.serializers import CamelCaseFieldsSerializer

from .models import Topic, TopicWord


class TopicSerializer(CamelCaseFieldsSerializer):
    class Meta:
        model = Topic
        fields = "__all__"


class TopicWordSerializer(CamelCaseFieldsSerializer):
    topicId = serializers.PrimaryKeyRelatedField(source="topic", queryset=Topic.objects.all())
    wordId = serializers.PrimaryKeyRelatedField(
        source="word", queryset=DictionaryEntry.objects.all()
    )

    class Meta:
        model = TopicWord
        fields = ("id", "topicId", "wordId")
