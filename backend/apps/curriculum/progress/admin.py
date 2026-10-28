from django.contrib import admin

from .models import MistakeRecord, UserTopicProgress

admin.site.register([UserTopicProgress, MistakeRecord])
