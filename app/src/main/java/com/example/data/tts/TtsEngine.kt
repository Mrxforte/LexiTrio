package com.example.data.tts

import kotlinx.coroutines.flow.StateFlow

interface TtsEngine {
    val engineType: TtsEngineType
    val isAvailable: Boolean
    val isPlaying: StateFlow<Boolean>
    val availableVoices: StateFlow<List<TtsVoiceInfo>>

    suspend fun initialize()

    fun speak(
        text: String,
        languageCode: String,
        rate: Float = 1.0f,
        pitch: Float = 1.0f,
        voiceId: String? = null,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    )

    fun stop()

    fun shutdown()
}
