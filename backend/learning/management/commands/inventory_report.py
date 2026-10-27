from django.core.management.base import BaseCommand
from django.db.models import Sum
from learning.models import (
    ContentBatch,
    CurriculumExercise,
    CurriculumTopic,
    TestBankQuestion,
)


class Command(BaseCommand):
    help = "Displays the Content Inventory Status for the 50,000 learning unit target."

    def handle(self, *args, **options):
        targets = {
            "en_exercises": 10000,
            "ru_exercises": 10000,
            "en_tests": 10000,
            "ru_tests": 10000,
            "en_topics": 5000,
            "ru_topics": 5000,
            "total_units": 50000,
        }

        en_ex = CurriculumExercise.objects.filter(language="en").count()
        ru_ex = CurriculumExercise.objects.filter(language="ru").count()
        en_tests = TestBankQuestion.objects.filter(language="en").count()
        ru_tests = TestBankQuestion.objects.filter(language="ru").count()
        en_topics = CurriculumTopic.objects.filter(language="en").count()
        ru_topics = CurriculumTopic.objects.filter(language="ru").count()

        pub_en_ex = CurriculumExercise.objects.filter(language="en", publication_status="published").count()
        pub_ru_ex = CurriculumExercise.objects.filter(language="ru", publication_status="published").count()
        pub_en_tests = TestBankQuestion.objects.filter(language="en", publication_status="published").count()
        pub_ru_tests = TestBankQuestion.objects.filter(language="ru", publication_status="published").count()
        pub_en_topics = CurriculumTopic.objects.filter(language="en", lesson_content__publication_status="published").count()
        pub_ru_topics = CurriculumTopic.objects.filter(language="ru", lesson_content__publication_status="published").count()

        total_gen = en_ex + ru_ex + en_tests + ru_tests + en_topics + ru_topics
        total_pub = pub_en_ex + pub_ru_ex + pub_en_tests + pub_ru_tests + pub_en_topics + pub_ru_topics

        pct = round((total_pub / targets["total_units"]) * 100, 2)

        self.stdout.write("==================================================")
        self.stdout.write("  LEXITRIO CONTENT INVENTORY DASHBOARD REPORT     ")
        self.stdout.write("==================================================")
        self.stdout.write(f"Target Total Units Required: {targets['total_units']:,}")
        self.stdout.write(f"Total Content Units Generated: {total_gen:,}")
        self.stdout.write(f"Total Published / Approved: {total_pub:,} ({pct}% overall completion)")
        self.stdout.write("--------------------------------------------------")
        self.stdout.write(f"English Exercises: {pub_en_ex:,} / {targets['en_exercises']:,} ({round(pub_en_ex/targets['en_exercises']*100, 2)}%)")
        self.stdout.write(f"Russian Exercises: {pub_ru_ex:,} / {targets['ru_exercises']:,} ({round(pub_ru_ex/targets['ru_exercises']*100, 2)}%)")
        self.stdout.write(f"English Test Questions: {pub_en_tests:,} / {targets['en_tests']:,} ({round(pub_en_tests/targets['en_tests']*100, 2)}%)")
        self.stdout.write(f"Russian Test Questions: {pub_ru_tests:,} / {targets['ru_tests']:,} ({round(pub_ru_tests/targets['ru_tests']*100, 2)}%)")
        self.stdout.write(f"English Topics: {pub_en_topics:,} / {targets['en_topics']:,} ({round(pub_en_topics/targets['en_topics']*100, 2)}%)")
        self.stdout.write(f"Russian Topics: {pub_ru_topics:,} / {targets['ru_topics']:,} ({round(pub_ru_topics/targets['ru_topics']*100, 2)}%)")
        self.stdout.write("==================================================")
