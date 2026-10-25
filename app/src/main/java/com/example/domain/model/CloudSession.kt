package com.example.domain.model

data class CloudSession(val email: String, val token: String)

data class SyncSummary(
    val savedWords: Int,
    val quizAttempts: Int,
    val catalogEntries: Int
)
