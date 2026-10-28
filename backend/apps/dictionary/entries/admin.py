from django.contrib import admin

from .models import (
    Collocation,
    DictionaryEntry,
    ExampleSentence,
    SynonymAntonym,
    Translation,
    WordForm,
    WordMeaning,
)

admin.site.register(
    [DictionaryEntry, WordMeaning, Translation, ExampleSentence, SynonymAntonym, WordForm, Collocation]
)
