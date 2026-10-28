"""Serializer base shared by every app that talks to the Android client."""

from rest_framework import serializers


class CamelCaseFieldsSerializer(serializers.ModelSerializer):
    """ModelSerializer whose output keys are camelCase (``frequency_rank`` -> ``frequencyRank``)."""

    def to_representation(self, instance):
        result = super().to_representation(instance)
        return {_camel_case(key): value for key, value in result.items()}


def _camel_case(value):
    first, *rest = value.split("_")
    return first + "".join(part.capitalize() for part in rest)
