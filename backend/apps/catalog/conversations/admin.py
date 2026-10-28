from django.contrib import admin

from .models import Conversation, ConversationTurn

admin.site.register([Conversation, ConversationTurn])
