import json
from django.core.management import call_command
from django.test import TestCase
from rest_framework.test import APIClient
from apps.curriculum.content.models import (
    ContentBatch,
    CurriculumCourse,
    CurriculumExercise,
    CurriculumLevel,
    CurriculumTopic,
    GrammarTable,
    LessonContent,
    TestBankQuestion,
)


class CurriculumTestCase(TestCase):
    def setUp(self):
        self.client = APIClient()

    def test_import_curriculum_command(self):
        call_command("import_curriculum", batch_code="TEST_BATCH")

        # Verify courses
        en_course = CurriculumCourse.objects.get(code="EN_CORE")
        self.assertEqual(en_course.language, "en")

        ru_course = CurriculumCourse.objects.get(code="RU_CORE")
        self.assertEqual(ru_course.language, "ru")

        # Verify levels
        self.assertGreaterEqual(CurriculumLevel.objects.count(), 10)

        # Verify topics and exercises
        self.assertGreaterEqual(CurriculumTopic.objects.count(), 5)
        self.assertGreaterEqual(CurriculumExercise.objects.count(), 10)
        self.assertGreaterEqual(TestBankQuestion.objects.count(), 5)

        # Verify batch status
        batch = ContentBatch.objects.get(batch_code="TEST_BATCH")
        self.assertEqual(batch.status, "completed")

    def test_content_inventory_dashboard_api(self):
        call_command("import_curriculum", batch_code="TEST_DASHBOARD")
        response = self.client.get("/api/v1/curriculum/inventory/")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["target"]["total_units"], 50000)
        self.assertIn("generated", data)
        self.assertIn("published", data)
        self.assertIn("completionPercentage", data)

    def test_curriculum_api_endpoints(self):
        call_command("import_curriculum", batch_code="TEST_API")

        # Courses list
        res_courses = self.client.get("/api/v1/curriculum/courses/")
        self.assertEqual(res_courses.status_code, 200)
        self.assertGreaterEqual(len(res_courses.json()), 2)

        # Topics list with level filtering
        res_topics = self.client.get("/api/v1/curriculum/topics/?language=en&level=A1")
        self.assertEqual(res_topics.status_code, 200)
        self.assertGreaterEqual(len(res_topics.json()), 1)

        # Lessons list
        res_lessons = self.client.get("/api/v1/curriculum/lessons/")
        self.assertEqual(res_lessons.status_code, 200)

        # Exercises list
        res_ex = self.client.get("/api/v1/curriculum/exercises/?language=en")
        self.assertEqual(res_ex.status_code, 200)
        self.assertGreaterEqual(len(res_ex.json()), 1)
