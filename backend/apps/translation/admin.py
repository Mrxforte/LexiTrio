from django.contrib import admin

from .models import TranslationJob


@admin.register(TranslationJob)
class TranslationJobAdmin(admin.ModelAdmin):
    list_display = (
        "id",
        "status",
        "languages",
        "processed",
        "total_candidates",
        "translated",
        "failed",
        "created_at",
    )
    list_filter = ("status", "languages", "only_missing")
    search_fields = ("id", "last_error")
    readonly_fields = (
        "id",
        "cursor_id",
        "total_candidates",
        "processed",
        "translated",
        "failed",
        "last_error",
        "created_at",
        "updated_at",
    )
