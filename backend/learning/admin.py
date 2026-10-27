from django.contrib import admin

from .models import (
    Collocation,
    ContentBatch,
    ContentValidationReport,
    Conversation,
    ConversationTurn,
    CurriculumCourse,
    CurriculumExercise,
    CurriculumLevel,
    CurriculumTopic,
    CurriculumUnit,
    DictionaryEntry,
    ExampleSentence,
    GrammarLesson,
    GrammarTable,
    LessonContent,
    MistakeRecord,
    QuizQuestion,
    SavedWord,
    SynonymAntonym,
    TestBankQuestion,
    Topic,
    TopicWord,
    Translation,
    UserPreferences,
    UserProgress,
    UserTopicProgress,
    VocabularyCollection,
    WordForm,
    WordMeaning,
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


@admin.register(LessonContent)
class LessonContentAdmin(admin.ModelAdmin):
    list_display = ("topic", "publication_status")
    list_filter = ("publication_status",)
    search_fields = ("topic__title_en", "simple_explanation_en", "main_rule_en")
    inlines = [GrammarTableInline]
    actions = ["publish_content", "reject_content"]

    @admin.action(description="Mark selected lessons as Published")
    def publish_content(self, request, queryset):
        queryset.update(publication_status="published")

    @admin.action(description="Mark selected lessons as Rejected")
    def reject_content(self, request, queryset):
        queryset.update(publication_status="rejected")


@admin.register(CurriculumExercise)
class CurriculumExerciseAdmin(admin.ModelAdmin):
    list_display = ("id", "topic", "language", "cefr_level", "exercise_type", "validation_status", "publication_status")
    list_filter = ("language", "cefr_level", "exercise_type", "validation_status", "publication_status")
    search_fields = ("prompt_en", "prompt_ru", "correct_answer")
    actions = ["publish_exercises", "reject_exercises"]

    @admin.action(description="Publish selected exercises")
    def publish_exercises(self, request, queryset):
        queryset.update(publication_status="published")

    @admin.action(description="Reject selected exercises")
    def reject_exercises(self, request, queryset):
        queryset.update(publication_status="rejected")


@admin.register(TestBankQuestion)
class TestBankQuestionAdmin(admin.ModelAdmin):
    list_display = ("id", "topic", "language", "cefr_level", "question_type", "publication_status")
    list_filter = ("language", "cefr_level", "publication_status")
    search_fields = ("prompt_en", "prompt_ru", "correct_answer")


@admin.register(ContentBatch)
class ContentBatchAdmin(admin.ModelAdmin):
    list_display = ("batch_code", "target_language", "content_type", "generated_count", "approved_count", "status")
    list_filter = ("target_language", "content_type", "status")


admin.site.register([
    DictionaryEntry,
    WordMeaning,
    Translation,
    ExampleSentence,
    SynonymAntonym,
    WordForm,
    Collocation,
    Topic,
    TopicWord,
    Conversation,
    ConversationTurn,
    GrammarLesson,
    QuizQuestion,
    VocabularyCollection,
    SavedWord,
    UserProgress,
    UserPreferences,
    GrammarTable,
    ContentValidationReport,
    UserTopicProgress,
    MistakeRecord,
])
