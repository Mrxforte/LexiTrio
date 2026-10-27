package com.example.data.database

import com.example.data.model.CurriculumCourseEntity
import com.example.data.model.CurriculumExerciseEntity
import com.example.data.model.CurriculumLevelEntity
import com.example.data.model.CurriculumTopicEntity
import com.example.data.model.GrammarTableEntity
import com.example.data.model.LessonContentEntity
import com.example.data.model.TestBankQuestionEntity

object PreloadCurriculumData {

    fun getInitialCourses(): List<CurriculumCourseEntity> {
        return GrammarCurriculumRegistry.getCourses()
    }

    fun getInitialLevels(): List<CurriculumLevelEntity> {
        return GrammarCurriculumRegistry.getLevels()
    }

    fun getInitialTopics(): List<CurriculumTopicEntity> {
        return GrammarCurriculumRegistry.getAllTopicBundles().map { it.topic }
    }

    fun getInitialLessons(): List<Pair<LessonContentEntity, List<GrammarTableEntity>>> {
        return GrammarCurriculumRegistry.getAllTopicBundles().map { Pair(it.lesson, it.tables) }
    }

    fun getInitialExercises(): List<CurriculumExerciseEntity> {
        return GrammarCurriculumRegistry.getAllTopicBundles().flatMap { it.exercises }
    }

    fun getInitialTestQuestions(): List<TestBankQuestionEntity> {
        return GrammarCurriculumRegistry.getAllTopicBundles().flatMap { it.testQuestions }
    }
}
