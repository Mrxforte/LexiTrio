from django.contrib import admin

from .models import Topic, TopicWord

admin.site.register([Topic, TopicWord])
