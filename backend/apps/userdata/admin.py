from django.contrib import admin

from .models import SavedWord, UserPreferences, UserProgress, VocabularyCollection

admin.site.register([VocabularyCollection, SavedWord, UserProgress, UserPreferences])
