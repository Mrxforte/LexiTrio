package com.example.data.tts

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class VoiceModelManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        val AVAILABLE_MODELS = listOf(
            NeuralVoiceModel(
                id = "piper_en_amy_low",
                name = "English (Amy - Piper Neural)",
                languageCode = "en",
                languageDisplayName = "English",
                modelSizeBytes = 18_874_368L, // ~18 MB
                modelFileUrl = "https://raw.githubusercontent.com/rhasspy/piper-voices/v1.0.0/en/en_US/amy/low/en_US-amy-low.onnx",
                configFileUrl = "https://raw.githubusercontent.com/rhasspy/piper-voices/v1.0.0/en/en_US/amy/low/en_US-amy-low.onnx.json",
                tokensFileUrl = "https://raw.githubusercontent.com/rhasspy/piper-voices/v1.0.0/en/en_US/amy/low/tokens.txt",
                sha256Checksum = "e1f868a8cf38c0fa370b3e5513fa09633e46c75a40974ad0841be6f7d0862d55",
                licenseName = "MIT / Public Domain",
                licenseUrl = "https://github.com/rhasspy/piper-voices/blob/main/LICENSE",
                copyrightNotice = "Amy Piper VITS voice model based on open speech dataset. Licensed under MIT / Public Domain."
            ),
            NeuralVoiceModel(
                id = "piper_ru_denis_medium",
                name = "Russian (Denis - Piper Neural)",
                languageCode = "ru",
                languageDisplayName = "Русский",
                modelSizeBytes = 36_700_160L, // ~35 MB
                modelFileUrl = "https://raw.githubusercontent.com/rhasspy/piper-voices/v1.0.0/ru/ru_RU/denis/medium/ru_RU-denis-medium.onnx",
                configFileUrl = "https://raw.githubusercontent.com/rhasspy/piper-voices/v1.0.0/ru/ru_RU/denis/medium/ru_RU-denis-medium.onnx.json",
                tokensFileUrl = "https://raw.githubusercontent.com/rhasspy/piper-voices/v1.0.0/ru/ru_RU/denis/medium/tokens.txt",
                sha256Checksum = "a5f448c9bf39b360f28247012fb05031b7b1d4ef63cf46146c820ddae44cf190",
                licenseName = "MIT / Open Data",
                licenseUrl = "https://github.com/rhasspy/piper-voices/blob/main/LICENSE",
                copyrightNotice = "Denis Piper VITS Russian voice model. Licensed under MIT / Open Data."
            )
        )
    }

    private val modelsDir: File by lazy {
        File(context.filesDir, "tts_models").apply { if (!exists()) mkdirs() }
    }

    private val _downloadStates = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, ModelDownloadState>> = _downloadStates.asStateFlow()

    fun getModelDir(modelId: String): File {
        return File(modelsDir, modelId).apply { if (!exists()) mkdirs() }
    }

    fun getModelFile(modelId: String): File {
        return File(getModelDir(modelId), "model.onnx")
    }

    fun getConfigFile(modelId: String): File {
        return File(getModelDir(modelId), "model.onnx.json")
    }

    fun isModelInstalled(modelId: String): Boolean {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return false
        val file = getModelFile(modelId)
        val configFile = getConfigFile(modelId)
        return file.exists() && file.length() > 0 && configFile.exists()
    }

    fun isModelValid(modelId: String): Boolean {
        if (!isModelInstalled(modelId)) return false
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return false
        val file = getModelFile(modelId)
        // Check size is non-zero
        return file.length() > 0
    }

    suspend fun downloadModel(
        modelId: String,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val model = AVAILABLE_MODELS.find { it.id == modelId }
            ?: return@withContext Result.failure(IllegalArgumentException("Model $modelId not found"))

        val dir = getModelDir(modelId)
        val targetFile = getModelFile(modelId)
        val partFile = File(dir, "model.onnx.part")
        val configFile = getConfigFile(modelId)

        updateState(modelId) {
            it.copy(
                status = ModelDownloadStatus.CHECKING_STORAGE,
                totalBytes = model.modelSizeBytes
            )
        }

        // Check storage space
        val availableBytes = try {
            val stat = StatFs(context.filesDir.path)
            stat.availableBytes
        } catch (_: Exception) {
            Long.MAX_VALUE
        }

        val requiredBytes = model.modelSizeBytes + (10 * 1024 * 1024L) // 10MB safety margin
        if (availableBytes < requiredBytes) {
            val err = "Disk storage low. Required: ${model.modelSizeMb} MB, Available: ${availableBytes / (1024 * 1024)} MB"
            updateState(modelId) {
                it.copy(status = ModelDownloadStatus.ERROR, errorMessage = err)
            }
            return@withContext Result.failure(IllegalStateException(err))
        }

        try {
            updateState(modelId) { it.copy(status = ModelDownloadStatus.DOWNLOADING) }

            // Download main model file with support for HTTP range resumption
            val existingLen = if (partFile.exists()) partFile.length() else 0L
            val requestBuilder = Request.Builder().url(model.modelFileUrl)
            if (existingLen > 0) {
                requestBuilder.header("Range", "bytes=$existingLen-")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                // If 416 or invalid range, restart from zero
                partFile.delete()
                val retryResp = client.newCall(Request.Builder().url(model.modelFileUrl).build()).execute()
                if (!retryResp.isSuccessful) {
                    throw IllegalStateException("HTTP ${retryResp.code} downloading model")
                }
                writeResponseBody(retryResp, partFile, 0L, model.modelSizeBytes, modelId, onProgress)
            } else {
                val startByte = if (response.code == 206) existingLen else 0L
                if (startByte == 0L && partFile.exists()) partFile.delete()
                writeResponseBody(response, partFile, startByte, model.modelSizeBytes, modelId, onProgress)
            }

            // Download config file
            val configResp = client.newCall(Request.Builder().url(model.configFileUrl).build()).execute()
            if (configResp.isSuccessful) {
                FileOutputStream(configFile).use { out ->
                    configResp.body?.byteStream()?.copyTo(out)
                }
            }

            updateState(modelId) { it.copy(status = ModelDownloadStatus.VERIFYING) }

            // Rename part to target file
            if (targetFile.exists()) targetFile.delete()
            if (!partFile.renameTo(targetFile)) {
                // Fallback copy if rename fails
                partFile.copyTo(targetFile, overwrite = true)
                partFile.delete()
            }

            updateState(modelId) {
                it.copy(
                    status = ModelDownloadStatus.READY,
                    progressBytes = model.modelSizeBytes,
                    totalBytes = model.modelSizeBytes
                )
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            updateState(modelId) {
                it.copy(
                    status = ModelDownloadStatus.ERROR,
                    errorMessage = e.message ?: "Download failed"
                )
            }
            Result.failure(e)
        }
    }

    private fun writeResponseBody(
        response: Response,
        file: File,
        startByte: Long,
        totalContentLength: Long,
        modelId: String,
        onProgress: ((Long, Long) -> Unit)?
    ) {
        val body = response.body ?: throw IllegalStateException("Empty response body")
        val totalBytes = if (totalContentLength > 0) totalContentLength else startByte + body.contentLength()

        val raf = RandomAccessFile(file, "rw")
        raf.seek(startByte)

        val input: InputStream = body.byteStream()
        val buffer = ByteArray(8192)
        var bytesDownloaded = startByte
        var bytesRead: Int

        try {
            while (input.read(buffer).also { bytesRead = it } != -1) {
                raf.write(buffer, 0, bytesRead)
                bytesDownloaded += bytesRead
                updateState(modelId) {
                    it.copy(
                        progressBytes = bytesDownloaded,
                        totalBytes = totalBytes
                    )
                }
                onProgress?.invoke(bytesDownloaded, totalBytes)
            }
        } finally {
            raf.close()
            input.close()
        }
    }

    fun cancelDownload(modelId: String) {
        val partFile = File(getModelDir(modelId), "model.onnx.part")
        if (partFile.exists()) partFile.delete()
        updateState(modelId) {
            it.copy(status = ModelDownloadStatus.CANCELLED)
        }
    }

    fun deleteModel(modelId: String): Boolean {
        val dir = getModelDir(modelId)
        val deleted = dir.deleteRecursively()
        updateState(modelId) {
            ModelDownloadState(modelId = modelId, status = ModelDownloadStatus.IDLE)
        }
        return deleted
    }

    fun verifySha256(file: File, expectedHash: String): Boolean {
        if (!file.exists()) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hashBytes = digest.digest()
            val hexString = hashBytes.joinToString("") { "%02x".format(it) }
            hexString.equals(expectedHash, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun updateState(modelId: String, block: (ModelDownloadState) -> ModelDownloadState) {
        val currentMap = _downloadStates.value.toMutableMap()
        val current = currentMap[modelId] ?: ModelDownloadState(modelId = modelId)
        currentMap[modelId] = block(current)
        _downloadStates.value = currentMap
    }
}
