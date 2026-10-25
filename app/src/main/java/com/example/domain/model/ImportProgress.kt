package com.example.domain.model

data class ImportProgress(
    val processedCount: Int = 0,
    val insertedCount: Int = 0,
    val skippedCount: Int = 0,
    val isRunning: Boolean = false,
    val stageMessage: String = "",
    val error: String? = null
)
