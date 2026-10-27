package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ConversationGrammarDao
import com.example.data.dao.CurriculumDao
import com.example.data.dao.DictionaryDao
import com.example.data.dao.LearningDao
import com.example.data.dao.QuizDao
import com.example.data.dao.VocabularyDao
import com.example.data.model.CollocationEntity
import com.example.data.model.ConversationEntity
import com.example.data.model.ConversationTurnEntity
import com.example.data.model.CurriculumCourseEntity
import com.example.data.model.CurriculumExerciseEntity
import com.example.data.model.CurriculumLevelEntity
import com.example.data.model.CurriculumTopicEntity
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.ExampleSentenceEntity
import com.example.data.model.GrammarLessonEntity
import com.example.data.model.GrammarTableEntity
import com.example.data.model.LessonContentEntity
import com.example.data.model.MistakeRecordEntity
import com.example.data.model.QuizAttemptEntity
import com.example.data.model.QuizQuestionEntity
import com.example.data.model.SavedWordEntity
import com.example.data.model.SearchHistoryEntity
import com.example.data.model.SynonymAntonymEntity
import com.example.data.model.SyncTombstoneEntity
import com.example.data.model.TestBankQuestionEntity
import com.example.data.model.TopicEntity
import com.example.data.model.TopicWordCrossRef
import com.example.data.model.TranslationEntity
import com.example.data.model.UserProgressEntity
import com.example.data.model.UserTopicProgressEntity
import com.example.data.model.VocabularyCollectionEntity
import com.example.data.model.WordFormEntity
import com.example.data.model.WordMeaningEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        DictionaryEntryEntity::class,
        WordMeaningEntity::class,
        TranslationEntity::class,
        ExampleSentenceEntity::class,
        SynonymAntonymEntity::class,
        WordFormEntity::class,
        CollocationEntity::class,
        SavedWordEntity::class,
        VocabularyCollectionEntity::class,
        TopicEntity::class,
        TopicWordCrossRef::class,
        ConversationEntity::class,
        ConversationTurnEntity::class,
        GrammarLessonEntity::class,
        QuizQuestionEntity::class,
        QuizAttemptEntity::class,
        SearchHistoryEntity::class,
        UserProgressEntity::class,
        SyncTombstoneEntity::class,
        CurriculumCourseEntity::class,
        CurriculumLevelEntity::class,
        CurriculumTopicEntity::class,
        LessonContentEntity::class,
        GrammarTableEntity::class,
        CurriculumExerciseEntity::class,
        TestBankQuestionEntity::class,
        UserTopicProgressEntity::class,
        MistakeRecordEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class LexiTrioDatabase : RoomDatabase() {

    abstract fun dictionaryDao(): DictionaryDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun learningDao(): LearningDao
    abstract fun conversationGrammarDao(): ConversationGrammarDao
    abstract fun quizDao(): QuizDao
    abstract fun curriculumDao(): CurriculumDao

    companion object {
        @Volatile
        private var INSTANCE: LexiTrioDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE saved_words ADD COLUMN clientId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "UPDATE saved_words SET clientId = printf('00000000-0000-4000-8000-%012x', id)"
                )
                db.execSQL(
                    "ALTER TABLE vocabulary_collections ADD COLUMN clientId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "UPDATE vocabulary_collections SET clientId = printf('00000000-0000-4000-8000-%012x', id)"
                )
                db.execSQL(
                    "ALTER TABLE quiz_attempts ADD COLUMN clientId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "UPDATE quiz_attempts SET clientId = printf('00000000-0000-4000-8000-%012x', id)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_tombstones (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        entityType TEXT NOT NULL,
                        clientId TEXT NOT NULL,
                        deletedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_sync_tombstones_entityType_clientId " +
                        "ON sync_tombstones (entityType, clientId)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE saved_words ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE saved_words SET updatedAt = MAX(savedAt, lastReviewTimestamp)"
                )
                db.execSQL(
                    "ALTER TABLE vocabulary_collections ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE vocabulary_collections SET updatedAt = createdAt"
                )
                db.execSQL(
                    "ALTER TABLE user_progress ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE user_progress SET updatedAt = lastStudyDateTimestamp"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `curriculum_courses` (
                        `code` TEXT NOT NULL,
                        `titleEn` TEXT NOT NULL,
                        `titleRu` TEXT NOT NULL,
                        `titleUz` TEXT NOT NULL,
                        `language` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        PRIMARY KEY(`code`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `curriculum_levels` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `courseCode` TEXT NOT NULL,
                        `code` TEXT NOT NULL,
                        `order` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_curriculum_levels_courseCode` ON `curriculum_levels` (`courseCode`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `curriculum_topics` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `topicCode` TEXT NOT NULL,
                        `language` TEXT NOT NULL,
                        `titleEn` TEXT NOT NULL,
                        `titleRu` TEXT NOT NULL,
                        `titleUz` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `cefrLevel` TEXT NOT NULL,
                        `estimatedMinutes` INTEGER NOT NULL,
                        `order` INTEGER NOT NULL,
                        `iconName` TEXT NOT NULL,
                        `summaryEn` TEXT NOT NULL,
                        `summaryRu` TEXT NOT NULL,
                        `summaryUz` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_curriculum_topics_language` ON `curriculum_topics` (`language`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_curriculum_topics_cefrLevel` ON `curriculum_topics` (`cefrLevel`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_curriculum_topics_topicCode` ON `curriculum_topics` (`topicCode`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `lesson_contents` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `topicId` INTEGER NOT NULL,
                        `simpleExplanationEn` TEXT NOT NULL,
                        `simpleExplanationRu` TEXT NOT NULL,
                        `simpleExplanationUz` TEXT NOT NULL,
                        `realLifeAnalogyEn` TEXT NOT NULL,
                        `realLifeAnalogyRu` TEXT NOT NULL,
                        `realLifeAnalogyUz` TEXT NOT NULL,
                        `mainRuleEn` TEXT NOT NULL,
                        `mainRuleRu` TEXT NOT NULL,
                        `mainRuleUz` TEXT NOT NULL,
                        `positiveExamplesJson` TEXT NOT NULL,
                        `negativeExamplesJson` TEXT NOT NULL,
                        `questionFormsJson` TEXT NOT NULL,
                        `exceptionsJson` TEXT NOT NULL,
                        `commonMistakesJson` TEXT NOT NULL,
                        `recapEn` TEXT NOT NULL,
                        `recapRu` TEXT NOT NULL,
                        `recapUz` TEXT NOT NULL,
                        `publicationStatus` TEXT NOT NULL,
                        FOREIGN KEY(`topicId`) REFERENCES `curriculum_topics`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_contents_topicId` ON `lesson_contents` (`topicId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `grammar_tables` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `lessonId` INTEGER NOT NULL,
                        `titleEn` TEXT NOT NULL,
                        `titleRu` TEXT NOT NULL,
                        `titleUz` TEXT NOT NULL,
                        `tableType` TEXT NOT NULL,
                        `headersJson` TEXT NOT NULL,
                        `rowsJson` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_grammar_tables_lessonId` ON `grammar_tables` (`lessonId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `curriculum_exercises` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `topicId` INTEGER NOT NULL,
                        `language` TEXT NOT NULL,
                        `cefrLevel` TEXT NOT NULL,
                        `skillCategory` TEXT NOT NULL,
                        `difficulty` TEXT NOT NULL,
                        `exerciseType` TEXT NOT NULL,
                        `promptEn` TEXT NOT NULL,
                        `promptRu` TEXT NOT NULL,
                        `promptUz` TEXT NOT NULL,
                        `instructions` TEXT NOT NULL,
                        `audioText` TEXT NOT NULL,
                        `optionsJson` TEXT NOT NULL,
                        `correctAnswer` TEXT NOT NULL,
                        `acceptedVariantsJson` TEXT NOT NULL,
                        `explanationEn` TEXT NOT NULL,
                        `explanationRu` TEXT NOT NULL,
                        `explanationUz` TEXT NOT NULL,
                        `hintEn` TEXT NOT NULL,
                        `hintRu` TEXT NOT NULL,
                        `hintUz` TEXT NOT NULL,
                        `relatedRule` TEXT NOT NULL,
                        `tags` TEXT NOT NULL,
                        `validationStatus` TEXT NOT NULL,
                        `publicationStatus` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_curriculum_exercises_topicId` ON `curriculum_exercises` (`topicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_curriculum_exercises_language` ON `curriculum_exercises` (`language`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_curriculum_exercises_cefrLevel` ON `curriculum_exercises` (`cefrLevel`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_curriculum_exercises_exerciseType` ON `curriculum_exercises` (`exerciseType`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `test_bank_questions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `topicId` INTEGER NOT NULL,
                        `language` TEXT NOT NULL,
                        `cefrLevel` TEXT NOT NULL,
                        `questionType` TEXT NOT NULL,
                        `promptEn` TEXT NOT NULL,
                        `promptRu` TEXT NOT NULL,
                        `promptUz` TEXT NOT NULL,
                        `optionsJson` TEXT NOT NULL,
                        `correctAnswer` TEXT NOT NULL,
                        `acceptedVariantsJson` TEXT NOT NULL,
                        `explanationEn` TEXT NOT NULL,
                        `explanationRu` TEXT NOT NULL,
                        `explanationUz` TEXT NOT NULL,
                        `difficulty` TEXT NOT NULL,
                        `publicationStatus` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_test_bank_questions_topicId` ON `test_bank_questions` (`topicId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_test_bank_questions_language` ON `test_bank_questions` (`language`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_test_bank_questions_cefrLevel` ON `test_bank_questions` (`cefrLevel`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_topic_progress` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `topicId` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `completedExercises` INTEGER NOT NULL,
                        `totalExercises` INTEGER NOT NULL,
                        `quizScore` INTEGER NOT NULL,
                        `timeSpentSec` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_topic_progress_topicId` ON `user_topic_progress` (`topicId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `mistake_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `exerciseId` INTEGER NOT NULL,
                        `questionType` TEXT NOT NULL,
                        `userAnswer` TEXT NOT NULL,
                        `correctAnswer` TEXT NOT NULL,
                        `timesFailed` INTEGER NOT NULL,
                        `isMastered` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `curriculum_topics` ADD COLUMN `prerequisitesJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `curriculum_topics` ADD COLUMN `learningObjectivesJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `curriculum_topics` ADD COLUMN `relatedTopicCodesJson` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `curriculum_topics` ADD COLUMN `masteryCriteriaScore` INTEGER NOT NULL DEFAULT 80")
                db.execSQL("ALTER TABLE `curriculum_topics` ADD COLUMN `reviewStatus` TEXT NOT NULL DEFAULT 'published'")
                db.execSQL("ALTER TABLE `curriculum_topics` ADD COLUMN `provenance` TEXT NOT NULL DEFAULT 'curriculum_baseline'")
                db.execSQL("ALTER TABLE `curriculum_topics` ADD COLUMN `validationStatus` TEXT NOT NULL DEFAULT 'passed'")
                db.execSQL("ALTER TABLE `lesson_contents` ADD COLUMN `detailedExamplesJson` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        fun getInstance(context: Context): LexiTrioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LexiTrioDatabase::class.java,
                    "lexitrio_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialCorpus(database)
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        if (database.dictionaryDao().getEntryCount() == 0 || database.curriculumDao().getTopicCount("en") == 0) {
                            populateInitialCorpus(database)
                        }
                    }
                }
            }

            private suspend fun populateInitialCorpus(database: LexiTrioDatabase) {
                val dictDao = database.dictionaryDao()
                val bundle = PreloadDictionaryData.getInitialData()
                dictDao.insertEntries(bundle.entries)
                dictDao.insertMeanings(bundle.meanings)
                dictDao.insertTranslations(bundle.translations)
                dictDao.insertExamples(bundle.examples)
                dictDao.insertSynonyms(bundle.synonyms)
                dictDao.insertForms(bundle.forms)
                dictDao.insertCollocations(bundle.collocations)

                // Populate learning entities
                val vocabDao = database.vocabularyDao()
                PreloadLearningData.getInitialCollections().forEach {
                    vocabDao.insertCollection(it)
                }
                vocabDao.insertSavedWords(PreloadLearningData.getInitialSavedWords())

                val learnDao = database.learningDao()
                learnDao.insertTopics(PreloadLearningData.getInitialTopics())
                learnDao.insertTopicWordCrossRefs(PreloadLearningData.getInitialTopicCrossRefs())

                val convDao = database.conversationGrammarDao()
                PreloadLearningData.getInitialConversations().forEach { (conv, turns) ->
                    convDao.insertConversations(listOf(conv))
                    convDao.insertConversationTurns(turns)
                }
                convDao.insertGrammarLessons(PreloadLearningData.getInitialGrammarLessons())

                val quizDao = database.quizDao()
                quizDao.insertQuestions(PreloadLearningData.getInitialQuizQuestions())
                quizDao.insertOrUpdateProgress(UserProgressEntity())

                // Populate Enterprise Curriculum entities
                val currDao = database.curriculumDao()
                if (currDao.getTopicCount("en") == 0) {
                    currDao.insertCourses(PreloadCurriculumData.getInitialCourses())
                    currDao.insertLevels(PreloadCurriculumData.getInitialLevels())
                    currDao.insertTopics(PreloadCurriculumData.getInitialTopics())
                    PreloadCurriculumData.getInitialLessons().forEach { (lesson, tables) ->
                        val lessonId = currDao.insertLessonContent(lesson)
                        val tablesWithLessonId = tables.map { it.copy(lessonId = lessonId) }
                        currDao.insertGrammarTables(tablesWithLessonId)
                    }
                    currDao.insertExercises(PreloadCurriculumData.getInitialExercises())
                    currDao.insertTestQuestions(PreloadCurriculumData.getInitialTestQuestions())
                }
            }
        }
    }
}
