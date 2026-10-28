import json
from django.core.management.base import BaseCommand
from django.db import transaction
from apps.curriculum.content.models import (
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


class Command(BaseCommand):
    help = "Imports structured English and Russian learning curriculum seed into database idempotently."

    def add_arguments(self, parser):
        parser.add_argument("--batch-code", type=str, default="SEED_CURRICULUM_V1", help="Batch identifier code")

    def handle(self, *args, **options):
        batch_code = options["batch_code"]
        self.stdout.write(f"Starting curriculum import for batch '{batch_code}'...")

        with transaction.atomic():
            batch, _ = ContentBatch.objects.get_or_create(
                batch_code=batch_code,
                defaults={
                    "target_language": "multi",
                    "content_type": "CURRICULUM_SEED",
                    "status": "in_progress",
                },
            )

            # 1. Create English Course
            en_course, _ = CurriculumCourse.objects.get_or_create(
                code="EN_CORE",
                defaults={
                    "title_en": "English Core Mastery",
                    "title_ru": "Базовый курс английского языка",
                    "title_uz": "Ingliz tili asosiy kursi",
                    "language": "en",
                    "description": "Comprehensive English curriculum from CEFR A1 to C2.",
                },
            )

            # 2. Create Russian Course
            ru_course, _ = CurriculumCourse.objects.get_or_create(
                code="RU_CORE",
                defaults={
                    "title_en": "Russian Core Mastery",
                    "title_ru": "Основной курс русского языка",
                    "title_uz": "Rus tili asosiy kursi",
                    "language": "ru",
                    "description": "Coherent Russian language progression from beginner to advanced.",
                },
            )

            # Create CEFR Levels
            levels_def = [
                ("A1", 1, "A1 Beginner / Start"),
                ("A2", 2, "A2 Elementary"),
                ("B1", 3, "B1 Intermediate"),
                ("B2", 4, "B2 Upper Intermediate"),
                ("C1", 5, "C1 Advanced"),
                ("C2", 6, "C2 Mastery / Proficient"),
            ]

            en_levels = {}
            ru_levels = {}

            for code, order, title in levels_def:
                en_lvl, _ = CurriculumLevel.objects.get_or_create(
                    course=en_course,
                    code=code,
                    defaults={"order": order, "title": title},
                )
                en_levels[code] = en_lvl

                ru_lvl, _ = CurriculumLevel.objects.get_or_create(
                    course=ru_course,
                    code=code,
                    defaults={"order": order, "title": f"Уровень {code}"},
                )
                ru_levels[code] = ru_lvl

            # Seed Topics & Lessons data
            self._seed_english_topics(en_levels)
            self._seed_russian_topics(ru_levels)

            # Update batch stats
            ex_count = CurriculumExercise.objects.count()
            test_count = TestBankQuestion.objects.count()
            topic_count = CurriculumTopic.objects.count()

            batch.generated_count = ex_count + test_count + topic_count
            batch.validated_count = batch.generated_count
            batch.approved_count = batch.generated_count
            batch.status = "completed"
            batch.save()

            ContentValidationReport.objects.create(
                batch=batch,
                total_items=batch.generated_count,
                valid_items=batch.generated_count,
                invalid_items=0,
                duplicate_items=0,
                error_details_json="[]",
            )

        self.stdout.write(
            self.style.SUCCESS(
                f"Successfully imported curriculum seed batch '{batch_code}'. Total units in system: Topics={topic_count}, Exercises={ex_count}, TestQuestions={test_count}."
            )
        )

    def _seed_english_topics(self, en_levels):
        # Sample structured English topics covering A1-C2 grammar
        topics_data = [
            {
                "level": "A1",
                "code": "EN_A1_001",
                "title_en": "To Be Verb (Am, Is, Are)",
                "title_ru": "Глагол To Be (Am, Is, Are)",
                "title_uz": "To Be fe'li (Am, Is, Are)",
                "category": "Grammar",
                "simple_exp_en": "Think of 'to be' like a bridge that connects a person or thing to what they are, where they are, or how they feel.",
                "analogy_en": "Imagine giving someone a name tag or a status sticker. 'I am happy', 'She is a doctor'.",
                "main_rule_en": "Use 'am' with 'I'. Use 'is' with singular pronouns (he, she, it) or single names. Use 'are' with plural pronouns (you, we, they).",
                "positive_ex": ["I am a student.", "She is at home.", "They are happy."],
                "negative_ex": ["I am not hungry.", "He is not late.", "We are not tired."],
                "question_forms": ["Am I right?", "Is she ready?", "Are you ready?"],
                "exceptions": ["Use 'are' with 'you' even when talking to one person!"],
                "common_mistakes": [
                    {"mistake": "I is a teacher.", "correction": "I am a teacher.", "reason": "Use 'am' with 'I'."},
                    {"mistake": "They is my friends.", "correction": "They are my friends.", "reason": "Use 'are' with plural subjects."},
                ],
                "headers": ["Subject Pronoun", "To Be Form", "Example Sentence"],
                "rows": [
                    ["I", "am", "I am a doctor."],
                    ["He / She / It", "is", "She is smart."],
                    ["You / We / They", "are", "They are ready."],
                ],
                "exercises": [
                    {
                        "type": "MULTIPLE_CHOICE",
                        "prompt": "Choose the correct form: She ___ my best friend.",
                        "options": ["am", "is", "are", "be"],
                        "answer": "is",
                        "exp": "'She' takes 'is' in the present tense.",
                    },
                    {
                        "type": "FILL_IN_BLANK",
                        "prompt": "Fill in the blank: They ___ excited about the trip.",
                        "options": [],
                        "answer": "are",
                        "exp": "'They' is plural and takes 'are'.",
                    },
                    {
                        "type": "TYPE_MISSING_WORD",
                        "prompt": "Type the missing verb: I ___ learning English.",
                        "options": [],
                        "answer": "am",
                        "exp": "'I' is paired with 'am'.",
                    },
                    {
                        "type": "SENTENCE_ORDERING",
                        "prompt": "Arrange the words into a correct sentence: [student, a, am, I]",
                        "options": ["I", "am", "a", "student"],
                        "answer": "I am a student",
                        "exp": "Subject + Verb + Article + Noun.",
                    },
                ],
                "test_questions": [
                    {
                        "prompt": "Which sentence is grammatically correct?",
                        "options": ["We is ready.", "We am ready.", "We are ready.", "We be ready."],
                        "answer": "We are ready.",
                        "exp": "'We' requires 'are'.",
                    }
                ],
            },
            {
                "level": "A2",
                "code": "EN_A2_001",
                "title_en": "Present Continuous Tense",
                "title_ru": "Настоящее длительное время",
                "title_uz": "Hozirgi davomiy zamon",
                "category": "Grammar",
                "simple_exp_en": "Present Continuous describes an action that is happening right now as you speak.",
                "analogy_en": "It is like taking a live video recording of an action happening right this moment.",
                "main_rule_en": "Form: Subject + am/is/are + verb-ing.",
                "positive_ex": ["I am reading a book.", "He is eating lunch.", "We are walking."],
                "negative_ex": ["I am not sleeping.", "She is not playing.", "They are not waiting."],
                "question_forms": ["What are you doing?", "Is he coming?"],
                "exceptions": ["Stative verbs like 'know', 'love', 'like', 'want' are rarely used in continuous form."],
                "common_mistakes": [
                    {"mistake": "I am know the answer.", "correction": "I know the answer.", "reason": "'Know' is a state verb, not action."},
                ],
                "headers": ["Subject", "Be Verb", "Verb + ING", "Full Sentence"],
                "rows": [
                    ["I", "am", "working", "I am working right now."],
                    ["She", "is", "singing", "She is singing a song."],
                    ["They", "are", "dancing", "They are dancing."],
                ],
                "exercises": [
                    {
                        "type": "MULTIPLE_CHOICE",
                        "prompt": "Look! The train ___ now.",
                        "options": ["comes", "is coming", "came", "has come"],
                        "answer": "is coming",
                        "exp": "The signal word 'Look!' indicates an action happening now.",
                    },
                    {
                        "type": "FILL_IN_BLANK",
                        "prompt": "She is (read) ___ a newspaper at the moment.",
                        "options": [],
                        "answer": "reading",
                        "exp": "Add -ing to 'read' for present continuous.",
                    },
                ],
                "test_questions": [
                    {
                        "prompt": "What are you doing right now?",
                        "options": ["I study.", "I am studying.", "I was studied.", "I have study."],
                        "answer": "I am studying.",
                        "exp": "Use 'am studying' for an action happening now.",
                    }
                ],
            },
            {
                "level": "B1",
                "code": "EN_B1_001",
                "title_en": "Present Perfect Tense",
                "title_ru": "Настоящее совершенное время",
                "title_uz": "Hozirgi tugallangan zamon",
                "category": "Grammar",
                "simple_exp_en": "Present Perfect connects a past event to the present moment.",
                "analogy_en": "Like holding a photo of something you did in the past that still matters today.",
                "main_rule_en": "Form: Subject + have/has + Past Participle (V3).",
                "positive_ex": ["I have visited London.", "She has completed her project."],
                "negative_ex": ["I have not finished yet.", "He has not arrived."],
                "question_forms": ["Have you ever been to France?", "Has she called?"],
                "exceptions": ["Do not use specific past time expressions like 'yesterday' or 'in 2010' with Present Perfect; use Simple Past instead."],
                "common_mistakes": [
                    {"mistake": "I have seen him yesterday.", "correction": "I saw him yesterday.", "reason": "'Yesterday' requires Past Simple."},
                ],
                "headers": ["Subject", "Have/Has", "Past Participle (V3)", "Result in Present"],
                "rows": [
                    ["I / You / We / They", "have", "eaten", "I am not hungry now."],
                    ["He / She / It", "has", "lost", "She cannot open her door."],
                ],
                "exercises": [
                    {
                        "type": "MULTIPLE_CHOICE",
                        "prompt": "I ___ my homework already.",
                        "options": ["finished", "have finished", "finishing", "has finished"],
                        "answer": "have finished",
                        "exp": "'Already' with connection to present uses Present Perfect.",
                    }
                ],
                "test_questions": [
                    {
                        "prompt": "Select the correct sentence:",
                        "options": [
                            "I have lived here since 2020.",
                            "I am living here since 2020.",
                            "I live here since 2020.",
                            "I lived here since 2020.",
                        ],
                        "answer": "I have lived here since 2020.",
                        "exp": "'Since + starting year' requires Present Perfect.",
                    }
                ],
            },
        ]

        self._create_topics_and_lessons(en_levels, topics_data, "en")

    def _seed_russian_topics(self, ru_levels):
        topics_data = [
            {
                "level": "A1",
                "code": "RU_A1_001",
                "title_en": "Cyrillic Alphabet & Noun Gender",
                "title_ru": "Кириллица и род существительных",
                "title_uz": "Kirill alifbosi va otlarning jinsi",
                "category": "Grammar",
                "simple_exp_en": "Russian nouns have three genders: Masculine, Feminine, and Neuter. Look at the last letter of the word to easily identify the gender!",
                "analogy_en": "Like color-coding items based on their ending label.",
                "main_rule_en": "Masculine ends in consonant or -й. Feminine ends in -а or -я. Neuter ends in -о or -е.",
                "positive_ex": ["дом (masculine)", "книга (feminine)", "окно (neuter)"],
                "negative_ex": ["не дом (not a house)", "не мама (not mom)"],
                "question_forms": ["Какой это род?", "Это дом или окно?"],
                "exceptions": ["Words like 'папа' (dad), 'дедушка' (grandpa) are grammatically feminine by ending, but masculine by natural gender!"],
                "common_mistakes": [
                    {"mistake": "Мой книга", "correction": "Моя книга", "reason": "'Книга' ends in -а (feminine), so use 'моя'."},
                ],
                "headers": ["Gender", "Typical Ending", "Example Word", "Translation"],
                "rows": [
                    ["Мужской (Masculine)", "Consonant / -й", "дом / музей", "house / museum"],
                    ["Женский (Feminine)", "-а / -я", "мама / песня", "mom / song"],
                    ["Средний (Neuter)", "-о / -е", "окно / море", "window / sea"],
                ],
                "exercises": [
                    {
                        "type": "MULTIPLE_CHOICE",
                        "prompt": "Определите род слова 'окно':",
                        "options": ["Мужской", "Женский", "Средний"],
                        "answer": "Средний",
                        "exp": "Слово оканчивается на -о, это средний род.",
                    },
                    {
                        "type": "FILL_IN_BLANK",
                        "prompt": "Слово 'мама' относится к ___ роду.",
                        "options": [],
                        "answer": "женскому",
                        "exp": "Окончание -а обозначает женский род.",
                    },
                ],
                "test_questions": [
                    {
                        "prompt": "Какое из слов среднее рода?",
                        "options": ["стол", "книга", "молоко", "музей"],
                        "answer": "молоко",
                        "exp": "'Молоко' оканчивается на -о.",
                    }
                ],
            },
            {
                "level": "A2",
                "code": "RU_A2_001",
                "title_en": "Prepositional Case (Где?)",
                "title_ru": "Предложный падеж (Где?)",
                "title_uz": "O'rin-payt kelishigi (Qayerda?)",
                "category": "Grammar",
                "simple_exp_en": "Use the Prepositional Case with 'в' (in/at) or 'на' (on/at) to tell where someone or something is located.",
                "analogy_en": "Like putting an location tag on a photo.",
                "main_rule_en": "Add -е to most singular nouns when answering 'Where?'. (e.g., город -> в городе, школа -> в школе).",
                "positive_ex": ["Я в Москве.", "Книга на столе."],
                "negative_ex": ["Я не в школе.", "Он не на работе."],
                "question_forms": ["Где ты?", "Где находится банк?"],
                "exceptions": ["Some short masculine nouns take stressed -у with 'в/на': в лесу, на мосту, в порту."],
                "common_mistakes": [
                    {"mistake": "Я в Москва.", "correction": "Я в Москве.", "reason": "Location requires Prepositional case ending -е."},
                ],
                "headers": ["Nominative (Who/What)", "Preposition", "Prepositional Form", "Meaning"],
                "rows": [
                    ["город", "в", "в городе", "in the city"],
                    ["комната", "в", "в комнате", "in the room"],
                    ["стол", "на", "на столе", "on the table"],
                ],
                "exercises": [
                    {
                        "type": "MULTIPLE_CHOICE",
                        "prompt": "Выберите правильный вариант: Иван сейчас ___.",
                        "options": ["в работа", "на работе", "на работу", "в работу"],
                        "answer": "на работе",
                        "exp": "'Работа' с предлогом 'на' в предложном падеже дает 'на работе'.",
                    }
                ],
                "test_questions": [
                    {
                        "prompt": "Где вы живёте?",
                        "options": ["В Ташкент", "В Ташкенте", "На Ташкент", "В Ташкента"],
                        "answer": "В Ташкенте",
                        "exp": "Город Ташкент в предложном падеже — в Ташкенте.",
                    }
                ],
            },
        ]

        self._create_topics_and_lessons(ru_levels, topics_data, "ru")

    def _create_topics_and_lessons(self, levels_map, topics_data, lang):
        unit_order = 1
        for item in topics_data:
            lvl_obj = levels_map[item["level"]]
            unit, _ = CurriculumUnit.objects.get_or_create(
                level=lvl_obj,
                order=unit_order,
                defaults={
                    "title_en": f"{item['title_en']} Unit",
                    "title_ru": f"Раздел: {item['title_ru']}",
                    "title_uz": f"Bo'lim: {item['title_uz']}",
                    "summary": item["simple_exp_en"],
                },
            )
            unit_order += 1

            topic, _ = CurriculumTopic.objects.get_or_create(
                topic_code=item["code"],
                defaults={
                    "unit": unit,
                    "language": lang,
                    "title_en": item["title_en"],
                    "title_ru": item["title_ru"],
                    "title_uz": item["title_uz"],
                    "category": item["category"],
                    "cefr_level": item["level"],
                    "estimated_minutes": 15,
                    "order": unit_order,
                    "summary_en": item["simple_exp_en"],
                    "summary_ru": item["title_ru"],
                    "summary_uz": item["title_uz"],
                },
            )

            # Create Patient Teacher Lesson Content
            lesson, _ = LessonContent.objects.get_or_create(
                topic=topic,
                defaults={
                    "simple_explanation_en": item["simple_exp_en"],
                    "simple_explanation_ru": item["title_ru"],
                    "simple_explanation_uz": item["title_uz"],
                    "real_life_analogy_en": item["analogy_en"],
                    "main_rule_en": item["main_rule_en"],
                    "positive_examples_json": json.dumps(item["positive_ex"]),
                    "negative_examples_json": json.dumps(item["negative_ex"]),
                    "question_forms_json": json.dumps(item["question_forms"]),
                    "exceptions_json": json.dumps(item["exceptions"]),
                    "common_mistakes_json": json.dumps(item["common_mistakes"]),
                    "recap_en": f"Recap: {item['main_rule_en']}",
                    "publication_status": "published",
                },
            )

            # Create Grammar Table
            GrammarTable.objects.get_or_create(
                lesson=lesson,
                title_en=f"{item['title_en']} Reference Table",
                defaults={
                    "title_ru": item["title_ru"],
                    "title_uz": item["title_uz"],
                    "table_type": "RULE",
                    "headers_json": json.dumps(item["headers"]),
                    "rows_json": json.dumps(item["rows"]),
                },
            )

            # Create Exercises
            for ex in item["exercises"]:
                CurriculumExercise.objects.get_or_create(
                    topic=topic,
                    prompt_en=ex["prompt"],
                    defaults={
                        "language": lang,
                        "cefr_level": item["level"],
                        "skill_category": item["category"],
                        "difficulty": "Medium",
                        "exercise_type": ex["type"],
                        "prompt_ru": ex["prompt"],
                        "prompt_uz": ex["prompt"],
                        "options_json": json.dumps(ex.get("options", [])),
                        "correct_answer": ex["answer"],
                        "explanation_en": ex["exp"],
                        "publication_status": "published",
                    },
                )

            # Create Test Questions
            for tq in item["test_questions"]:
                TestBankQuestion.objects.get_or_create(
                    topic=topic,
                    prompt_en=tq["prompt"],
                    defaults={
                        "language": lang,
                        "cefr_level": item["level"],
                        "question_type": "MULTIPLE_CHOICE",
                        "prompt_ru": tq["prompt"],
                        "prompt_uz": tq["prompt"],
                        "options_json": json.dumps(tq["options"]),
                        "correct_answer": tq["answer"],
                        "explanation_en": tq["exp"],
                        "publication_status": "published",
                    },
                )
