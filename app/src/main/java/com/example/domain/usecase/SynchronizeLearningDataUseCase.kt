package com.example.domain.usecase

import com.example.domain.model.SyncSummary
import com.example.domain.repository.CloudRepository

class SynchronizeLearningDataUseCase(private val repository: CloudRepository) {
    suspend operator fun invoke(): SyncSummary = repository.synchronize()
}
