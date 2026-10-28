"""Browse endpoints for the dictionary tables.

``/dictionary/entries/`` itself is served by the search app, because a ``?q=``
query on it is answered by the ranked search index.
"""

from shared.views import PublicReadOnlyViewSet

from .models import (
    Collocation,
    DictionaryEntry,
    ExampleSentence,
    SynonymAntonym,
    Translation,
    WordForm,
    WordMeaning,
)
from .serializers import (
    CollocationSerializer,
    DictionaryEntrySerializer,
    ExampleSentenceSerializer,
    SynonymAntonymSerializer,
    TranslationSerializer,
    WordFormSerializer,
    WordMeaningSerializer,
)


class DictionaryEntryBrowseViewSet(PublicReadOnlyViewSet):
    """Paged entry list/detail, optionally filtered by ``language`` and ``level``."""

    queryset = DictionaryEntry.objects.all()
    serializer_class = DictionaryEntrySerializer

    def get_queryset(self):
        queryset = super().get_queryset()
        language = self.request.query_params.get("language")
        level = self.request.query_params.get("level")
        if language:
            queryset = queryset.filter(language=language)
        if level:
            queryset = queryset.filter(cefr_level=level)
        return queryset


class WordMeaningViewSet(PublicReadOnlyViewSet):
    queryset = WordMeaning.objects.all()
    serializer_class = WordMeaningSerializer


class TranslationViewSet(PublicReadOnlyViewSet):
    queryset = Translation.objects.all()
    serializer_class = TranslationSerializer


class ExampleSentenceViewSet(PublicReadOnlyViewSet):
    queryset = ExampleSentence.objects.all()
    serializer_class = ExampleSentenceSerializer


class SynonymAntonymViewSet(PublicReadOnlyViewSet):
    queryset = SynonymAntonym.objects.all()
    serializer_class = SynonymAntonymSerializer


class WordFormViewSet(PublicReadOnlyViewSet):
    queryset = WordForm.objects.all()
    serializer_class = WordFormSerializer


class CollocationViewSet(PublicReadOnlyViewSet):
    queryset = Collocation.objects.all()
    serializer_class = CollocationSerializer
