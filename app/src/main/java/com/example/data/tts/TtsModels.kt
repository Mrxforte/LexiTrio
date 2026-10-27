package com.example.data.tts

import java.util.Locale

enum class TtsEngineType {
    SYSTEM,
    SHERPA_ONNX_NEURAL,
    AUTO
}

data class TtsVoiceInfo(
    val id: String,
    val name: String,
    val locale: Locale,
    val languageCode: String,
    val engineType: TtsEngineType,
    val requiresNetwork: Boolean = false,
    val quality: String = "Normal",
    val latency: String = "Normal",
    val isDownloaded: Boolean = true
)

data class NeuralVoiceModel(
    val id: String,
    val name: String,
    val languageCode: String, // "en", "ru"
    val languageDisplayName: String,
    val modelSizeBytes: Long,
    val modelFileUrl: String,
    val configFileUrl: String,
    val tokensFileUrl: String,
    val sha256Checksum: String,
    val licenseName: String,
    val licenseUrl: String,
    val copyrightNotice: String
) {
    val modelSizeMb: Float
        get() = modelSizeBytes / (1024f * 1024f)
}

enum class ModelDownloadStatus {
    IDLE,
    CHECKING_STORAGE,
    DOWNLOADING,
    VERIFYING,
    READY,
    ERROR,
    CANCELLED
}

data class ModelDownloadState(
    val modelId: String,
    val status: ModelDownloadStatus = ModelDownloadStatus.IDLE,
    val progressBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null
) {
    val progressPercentage: Int
        get() = if (totalBytes > 0) ((progressBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

    val progressMb: Float
        get() = progressBytes / (1024f * 1024f)

    val totalMb: Float
        get() = totalBytes / (1024f * 1024f)
}

data class TtsPlaybackState(
    val isPlaying: Boolean = false,
    val currentText: String = "",
    val currentLanguage: String = "",
    val activeEngineType: TtsEngineType = TtsEngineType.SYSTEM,
    val currentUtteranceId: String? = null,
    val isSlowRate: Boolean = false
)
