from django.contrib import admin

from .choices import PublicationStatus
from .models import (
    ContentBatch,
    ContentValidationReport,
    CurriculumCourse,
    CurriculumExercise,
    CurriculumLevel,
    CurriculumTopic,
    CurriculumUnit,
    GrammarTable,
    LessonContent,
    TestBankQuestion,
)


@admin.register(CurriculumCourse)
class CurriculumCourseAdmin(admin.ModelAdmin):
    list_display = ("code", "title_en", "language")
    search_fields = ("code", "title_en", "title_ru")
    list_filter = ("language",)


@admin.register(CurriculumLevel)
class CurriculumLevelAdmin(admin.ModelAdmin):
    list_display = ("code", "course", "order", "title")
    list_filter = ("course", "code")


@admin.register(CurriculumUnit)
class CurriculumUnitAdmin(admin.ModelAdmin):
    list_display = ("title_en", "level", "order")
    list_filter = ("level",)


@admin.register(CurriculumTopic)
class CurriculumTopicAdmin(admin.ModelAdmin):
    list_display = ("topic_code", "title_en", "language", "category", "cefr_level", "order")
    list_filter = ("language", "cefr_level", "category")
    search_fields = ("topic_code", "title_en", "title_ru")


class GrammarTableInline(admin.TabularInline):
    model = GrammarTable
    extra = 1


class PublicationActionsMixin:
    """Admin actions for the editorial status shared by lessons, exercises and tests."""

    actions = ["publish_selected", "reject_selected"]

    @admin.action(description="Mark selected as Published")
    def publish_selected(self, request, queryset):
        queryset.update(publication_status=PublicationStatus.PUBLISHED)

    @admin.action(description="Mark selected as Rejected")
    def reject_selected(self, request, queryset):
        queryset.update(publication_status=PublicationStatus.REJECTED)


@admin.register(LessonContent)
class LessonContentAdmin(PublicationActionsMixin, admin.ModelAdmin):
    list_display = ("topic", "publication_status")
    list_filter = ("publication_status",)
    search_fields = ("topic__title_en", "simple_explanation_en", "main_rule_en")
    inlines = [GrammarTableInline]


@admin.register(CurriculumExercise)
class CurriculumExerciseAdmin(PublicationActionsMixin, admin.ModelAdmin):
    list_display = ("id", "topic", "language", "cefr_level", "exercise_type", "validation_status", "publication_status")
    list_filter = ("language", "cefr_level", "exercise_type", "validation_status", "publication_status")
    search_fields = ("prompt_en", "prompt_ru", "correct_answer")


@admin.register(TestBankQuestion)
class TestBankQuestionAdmin(admin.ModelAdmin):
    list_display = ("id", "topic", "language", "cefr_level", "question_type", "publication_status")
    list_filter = ("language", "cefr_level", "publication_status")
    search_fields = ("prompt_en", "prompt_ru", "correct_answer")


@admin.register(ContentBatch)
class ContentBatchAdmin(admin.ModelAdmin):
    list_display = ("batch_code", "target_language", "content_type", "generated_count", "approved_count", "status")
    list_filter = ("target_language", "content_type", "status")


admin.site.register([GrammarTable, ContentValidationReport])
