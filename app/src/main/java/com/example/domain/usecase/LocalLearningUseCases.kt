package com.example.domain.usecase

import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.data.model.QuizType
import com.example.data.model.ReviewQuality
import com.example.data.model.SavedWordEntity
import com.example.domain.repository.DictionaryRepository
import com.example.domain.repository.LearningRepository
import com.example.domain.repository.VocabularyRepository

class SearchDictionaryUseCase(private val repository: DictionaryRepository) {
    suspend operator fun invoke(query: String, language: Language?, partOfSpeech: String?) =
        repository.search(query, language, partOfSpeech)
}

class SaveWordUseCase(private val repository: VocabularyRepository) {
    suspend operator fun invoke(
        entry: DictionaryEntryEntity,
        collectionId: Long = 1,
        notes: String = "",
        isFavorite: Boolean = false
    ) = repository.saveOrUpdateWord(entry, collectionId, notes, isFavorite)
}

class ProcessWordReviewUseCase(private val repository: VocabularyRepository) {
    suspend operator fun invoke(word: SavedWordEntity, quality: ReviewQuality) =
        repository.processReview(word, quality)
}

class GetQuizQuestionsUseCase(private val repository: LearningRepository) {
    suspend operator fun invoke(count: Int, type: QuizType?, category: String?) =
        repository.getRandomQuiz(count, type, category)
}

class RecordQuizAttemptUseCase(private val repository: LearningRepository) {
    suspend operator fun invoke(
        category: String,
        total: Int,
        correct: Int,
        timeSec: Int,
        xp: Int
    ) = repository.saveQuizAttempt(category, total, correct, timeSec, xp)
}

class UpdateLearningProgressUseCase(private val repository: LearningRepository) {
    suspend operator fun invoke(xp: Int, isQuiz: Boolean, wordsReviewed: Int) =
        repository.addXpAndProgress(xp, isQuiz, wordsReviewed)
}
