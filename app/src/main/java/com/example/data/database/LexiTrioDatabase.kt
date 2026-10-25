package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ConversationGrammarDao
import com.example.data.dao.DictionaryDao
import com.example.data.dao.LearningDao
import com.example.data.dao.QuizDao
import com.example.data.dao.VocabularyDao
import com.example.data.model.CollocationEntity
import com.example.data.model.ConversationEntity
import com.example.data.model.ConversationTurnEntity
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.ExampleSentenceEntity
import com.example.data.model.GrammarLessonEntity
import com.example.data.model.QuizAttemptEntity
import com.example.data.model.QuizQuestionEntity
import com.example.data.model.SavedWordEntity
import com.example.data.model.SearchHistoryEntity
import com.example.data.model.SynonymAntonymEntity
import com.example.data.model.SyncTombstoneEntity
import com.example.data.model.TopicEntity
import com.example.data.model.TopicWordCrossRef
import com.example.data.model.TranslationEntity
import com.example.data.model.UserProgressEntity
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
        SyncTombstoneEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class LexiTrioDatabase : RoomDatabase() {

    abstract fun dictionaryDao(): DictionaryDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun learningDao(): LearningDao
    abstract fun conversationGrammarDao(): ConversationGrammarDao
    abstract fun quizDao(): QuizDao

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

        fun getInstance(context: Context): LexiTrioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LexiTrioDatabase::class.java,
                    "lexitrio_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
                        if (database.dictionaryDao().getEntryCount() == 0) {
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
            }
        }
    }
}
