"""Guards for the refactored architecture: stable stored values, layering and shared rules."""

import ast
from io import StringIO
from pathlib import Path
from unittest.mock import patch

from django.apps import apps as django_apps
from django.core.management import call_command
from django.db import OperationalError
from django.test import SimpleTestCase, TestCase
from rest_framework.test import APIClient

from apps.curriculum.content.choices import (
    ContentBatchStatus,
    ExerciseType,
    GrammarTableType,
    PublicationStatus,
    ValidationStatus,
)
from apps.curriculum.content.models import (
    ContentBatch,
    CurriculumExercise,
    CurriculumTopic,
    LessonContent,
    TestBankQuestion,
)
from apps.curriculum.content.services.inventory import content_inventory
from apps.curriculum.progress.choices import TopicProgressStatus
from apps.dictionary.entries.choices import TranslationQuality
from apps.dictionary.entries.models import Translation
from apps.dictionary.search.engine import normalize_query
from apps.translation.models import TranslationJob
from apps.translation.services.lexicon import normalize_headword

BACKEND = Path(__file__).resolve().parent.parent


class StoredValuesTests(SimpleTestCase):
    """The values below are persisted and consumed by the Android client: never change them."""

    def test_publication_statuses(self):
        self.assertEqual(
            list(PublicationStatus.values), ["draft", "review", "approved", "published", "rejected"]
        )

    def test_other_status_values(self):
        self.assertEqual(list(ValidationStatus.values), ["valid", "invalid"])
        self.assertEqual(list(TranslationQuality.values), ["machine", "wiktionary", "reviewed"])
        self.assertEqual(
            list(ContentBatchStatus.values), ["init", "in_progress", "completed", "failed"]
        )
        self.assertEqual(
            list(TopicProgressStatus.values), ["NOT_STARTED", "IN_PROGRESS", "COMPLETED", "MASTERED"]
        )
        self.assertEqual(len(ExerciseType.values), 15)
        self.assertEqual(len(GrammarTableType.values), 5)

    def test_translation_job_keeps_its_own_vocabulary(self):
        # The job runner says "running"; content batches say "in_progress". Different domains.
        self.assertEqual(
            list(TranslationJob.Status.values),
            ["pending", "running", "completed", "failed", "cancelled"],
        )

    def test_models_expose_the_shared_enums_under_their_old_names(self):
        self.assertIs(LessonContent.Status, PublicationStatus)
        self.assertIs(CurriculumExercise.Status, PublicationStatus)
        self.assertIs(TestBankQuestion.Status, PublicationStatus)
        self.assertIs(CurriculumExercise.Type, ExerciseType)
        self.assertIs(ContentBatch.Status, ContentBatchStatus)
        self.assertIs(Translation.Quality, TranslationQuality)

    def test_field_choices_come_from_the_shared_enums(self):
        field = CurriculumExercise._meta.get_field("publication_status")
        self.assertEqual(list(field.choices), list(PublicationStatus.choices))
        self.assertEqual(field.default, PublicationStatus.PUBLISHED)


class LayeringTests(SimpleTestCase):
    """Inner layers must not depend on HTTP or on the view layer."""

    FORBIDDEN = ("rest_framework", "django.http")

    def _imports(self, path):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        names = []
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                names.extend(alias.name for alias in node.names)
            elif isinstance(node, ast.ImportFrom):
                module = node.module or ""
                names.append("." * node.level + module)
        return names

    def test_services_and_domain_modules_do_not_import_the_api_layer(self):
        modules = [
            BACKEND / "apps" / "curriculum" / "content" / "services" / "inventory.py",
            BACKEND / "apps" / "curriculum" / "content" / "choices.py",
            BACKEND / "apps" / "curriculum" / "progress" / "choices.py",
            BACKEND / "apps" / "dictionary" / "entries" / "choices.py",
            BACKEND / "apps" / "dictionary" / "search" / "services.py",
            BACKEND / "apps" / "dictionary" / "sync" / "services.py",
            BACKEND / "apps" / "syncing" / "services.py",
            BACKEND / "apps" / "translation" / "choices.py",
            BACKEND / "shared" / "text.py",
        ]
        for module in modules:
            self.assertTrue(module.exists(), module)
            for name in self._imports(module):
                bare = name.lstrip(".")
                self.assertFalse(
                    bare.startswith(self.FORBIDDEN)
                    or bare in {"views", "serializers"}
                    or bare.endswith((".views", ".serializers")),
                    f"{module.name} imports {name}",
                )


class FeatureAppLayoutTests(SimpleTestCase):
    """Every feature is its own Django app with the standard files, and no stray ``learning`` code remains."""

    def feature_apps(self):
        return [
            config
            for config in django_apps.get_app_configs()
            if config.name.startswith("apps.") and config.label != "learning"
        ]

    def test_every_feature_app_has_the_standard_files(self):
        configs = self.feature_apps()
        self.assertGreaterEqual(len(configs), 14)
        for config in configs:
            path = Path(config.path)
            self.assertTrue((path / "apps.py").exists(), config.name)
            has_surface = any((path / name).exists() for name in ("models.py", "views.py", "management"))
            self.assertTrue(has_surface, f"{config.name} has nothing to own")

    def test_app_labels_are_unique_and_stable(self):
        labels = sorted(config.label for config in self.feature_apps())
        self.assertEqual(
            labels,
            sorted(
                [
                    "accounts", "catalog_conversations", "catalog_grammar", "catalog_quiz",
                    "catalog_snapshot", "catalog_topics", "curriculum", "curriculum_progress",
                    "dictionary", "dictionary_search", "dictionary_sync", "maintenance",
                    "syncing", "translation", "userdata",
                ]
            ),
        )

    def test_the_legacy_learning_app_lives_under_apps_and_only_keeps_migrations(self):
        config = django_apps.get_app_config("learning")
        self.assertEqual(config.name, "apps.learning")
        python_files = {p.name for p in Path(config.path).glob("*.py")}
        self.assertEqual(python_files, {"__init__.py", "apps.py"})
        self.assertEqual(list(config.get_models()), [])

    def test_moved_models_keep_their_original_table_names(self):
        for model in django_apps.get_models():
            if model._meta.app_label in {
                "dictionary", "catalog_topics", "catalog_conversations", "catalog_grammar",
                "catalog_quiz", "curriculum", "curriculum_progress", "userdata",
            }:
                self.assertTrue(
                    model._meta.db_table.startswith("learning_"), f"{model.__name__}: {model._meta.db_table}"
                )

    def test_dictionary_apps_depend_in_one_direction_only(self):
        entries = BACKEND / "apps" / "dictionary" / "entries"
        for module in entries.rglob("*.py"):
            if "migrations" in module.parts or module.name == "tests.py":
                continue
            source = module.read_text(encoding="utf-8")
            self.assertNotIn("apps.dictionary.search", source, module.name)
            self.assertNotIn("apps.dictionary.sync", source, module.name)


class NormalisationTests(SimpleTestCase):
    def test_imports_and_queries_normalise_identically(self):
        for sample in ["  Hello   World ", "O’zbek", "ﬁsh", "re–enter", "КНИГА", "", None]:
            self.assertEqual(normalize_headword(sample), normalize_query(sample))
        self.assertEqual(normalize_query("ﬁsh"), "fish")
        self.assertEqual(normalize_query("O’zbek"), "o'zbek")


class ErrorHandlingTests(TestCase):
    def test_database_errors_are_hidden_from_clients(self):
        with patch(
            "apps.dictionary.sync.services.fetch_sync_page",
            side_effect=OperationalError("no such table: learning_secret /var/db/app.sqlite3"),
        ):
            with self.assertLogs("config.api", level="ERROR"):
                response = APIClient().get("/api/v1/dictionary/sync/")

        self.assertEqual(response.status_code, 503)
        self.assertEqual(list(response.json()), ["detail"])
        self.assertNotIn("learning_secret", response.content.decode())
        self.assertNotIn("sqlite", response.content.decode())

    def test_validation_errors_keep_drf_format(self):
        response = APIClient().get("/api/v1/dictionary/search/")

        self.assertEqual(response.status_code, 400)
        self.assertEqual(list(response.json()), ["q"])

    def test_every_response_carries_a_request_id(self):
        client = APIClient()

        generated = client.get("/api/v1/health/")
        honoured = client.get("/api/v1/health/", headers={"X-Request-ID": "client-supplied-123"})
        rejected = client.get("/api/v1/health/", headers={"X-Request-ID": "bad id with spaces!"})

        self.assertEqual(len(generated["X-Request-ID"]), 32)
        self.assertEqual(honoured["X-Request-ID"], "client-supplied-123")
        self.assertNotEqual(rejected["X-Request-ID"], "bad id with spaces!")


class ContentInventoryTests(TestCase):
    def setUp(self):
        topic = CurriculumTopic.objects.create(
            topic_code="t1", language="en", title_en="Topic", category="Grammar"
        )
        LessonContent.objects.create(
            topic=topic, simple_explanation_en="x", main_rule_en="y",
            publication_status=PublicationStatus.PUBLISHED,
        )
        for index, status in enumerate([PublicationStatus.PUBLISHED, PublicationStatus.DRAFT]):
            CurriculumExercise.objects.create(
                topic=topic, language="en", prompt_en=f"p{index}", correct_answer="a",
                publication_status=status,
                validation_status=ValidationStatus.INVALID if index else ValidationStatus.VALID,
            )
        ContentBatch.objects.create(batch_code="b1", target_language="en", content_type="EXERCISE", duplicate_count=3)

    def test_counts_and_completion(self):
        inventory = content_inventory()

        self.assertEqual(inventory["generated"]["en_exercises"], 2)
        self.assertEqual(inventory["published"]["en_exercises"], 1)
        self.assertEqual(inventory["published"]["en_topics"], 1)
        self.assertEqual(inventory["total_published"], 2)
        self.assertEqual(inventory["completion"]["en_exercises"], 0.01)
        self.assertEqual(inventory["validation_failures"], 1)
        self.assertEqual(inventory["duplicates"], 3)

    def test_api_and_report_command_use_the_same_numbers(self):
        api = APIClient().get("/api/v1/curriculum/inventory/").json()
        out = StringIO()
        call_command("inventory_report", stdout=out)

        self.assertEqual(api["published"]["enExercises"], 1)
        self.assertEqual(api["generated"]["total"], 3)
        self.assertEqual(api["validationFailures"], 1)
        self.assertEqual(api["duplicates"], 3)
        self.assertIn("English Exercises: 1 / 10,000 (0.01%)", out.getvalue())
        self.assertIn("Total Content Units Generated: 3", out.getvalue())
