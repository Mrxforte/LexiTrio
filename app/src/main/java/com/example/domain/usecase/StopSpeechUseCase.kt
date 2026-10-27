package com.example.domain.usecase

import com.example.data.tts.TtsRepository

class StopSpeechUseCase(
    private val repository: TtsRepository
) {
    operator fun invoke() {
        repository.stop()
    }
}
