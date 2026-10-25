package com.example.data.importer

import android.content.Context
import android.net.Uri
import com.example.data.dao.DictionaryDao
import com.example.data.model.DictionaryEntryEntity
import com.example.data.model.Language
import com.example.data.model.TranslationEntity
import com.example.data.model.WordMeaningEntity
import com.example.domain.model.ImportProgress
import com.example.domain.repository.DictionaryImportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

class DictionaryImportManager(
    private val context: Context,
    private val dictionaryDao: DictionaryDao
) : DictionaryImportRepository {

    private val _progress = MutableStateFlow(ImportProgress())
    override val progress: Flow<ImportProgress> = _progress.asStateFlow()

    override suspend fun importFromUri(uri: Uri): Result<Int> =
        importFromUri(uri, null, uri.lastPathSegment)

    suspend fun importFromUri(uri: Uri, mimeType: String?, fileName: String?): Result<Int> = withContext(Dispatchers.IO) {
        _progress.value = ImportProgress(isRunning = true, stageMessage = "Fayl tahlil qilinmoqda...")
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) 
                ?: return@withContext Result.failure(Exception("Faylni ochib bo‘lmadi"))

            val name = (fileName ?: uri.lastPathSegment ?: "").lowercase()
            val totalInserted: Int

            when {
                name.endsWith(".zip") -> {
                    totalInserted = parseZip(inputStream)
                }
                name.endsWith(".jsonl") -> {
                    totalInserted = parseJsonl(inputStream)
                }
                name.endsWith(".json") -> {
                    totalInserted = parseJson(inputStream)
                }
                name.endsWith(".csv") || name.endsWith(".tsv") -> {
                    totalInserted = parseCsv(inputStream)
                }
                else -> {
                    // Try JSON/JSONL first as fallback
                    totalInserted = parseJsonl(inputStream)
                }
            }

            _progress.value = ImportProgress(
                processedCount = totalInserted,
                insertedCount = totalInserted,
                isRunning = false,
                stageMessage = "Muvaffaqiyatli yakunlandi! $totalInserted ta yangi lug‘at birligi kiritildi."
            )
            Result.success(totalInserted)
        } catch (e: Exception) {
            _progress.value = ImportProgress(
                isRunning = false,
                stageMessage = "Xatolik yuz berdi",
                error = e.localizedMessage ?: "Import xatosi"
            )
            Result.failure(e)
        }
    }

    private suspend fun parseZip(inputStream: InputStream): Int {
        var total = 0
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryName = entry.name.lowercase()
                    if (entryName.endsWith(".jsonl")) {
                        total += parseJsonlStream(zis)
                    } else if (entryName.endsWith(".csv")) {
                        total += parseCsvStream(zis)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return total
    }

    private suspend fun parseJsonl(inputStream: InputStream): Int {
        return inputStream.use { parseJsonlStream(it) }
    }

    private suspend fun parseJsonlStream(stream: InputStream): Int {
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        val entryBatch = mutableListOf<DictionaryEntryEntity>()
        val meaningBatch = mutableListOf<WordMeaningEntity>()
        val transBatch = mutableListOf<TranslationEntity>()

        var line = reader.readLine()
        var lineCount = 0
        var insertedTotal = 0

        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && trimmed.startsWith("{")) {
                lineCount++
                try {
                    val obj = JSONObject(trimmed)
                    val headword = obj.optString("word", obj.optString("headword", "")).trim()
                    if (headword.isNotBlank()) {
                        val lang = obj.optString("language", "en").lowercase()
                        val ipa = obj.optString("ipa", obj.optString("phonetic", ""))
                        val pos = obj.optString("pos", obj.optString("partOfSpeech", "noun"))
                        val cefr = obj.optString("cefr", "B1")
                        val defEn = obj.optString("def_en", obj.optString("definition", ""))
                        val defRu = obj.optString("def_ru", "")
                        val defUz = obj.optString("def_uz", "")
                        val transRu = obj.optString("trans_ru", "")
                        val transUz = obj.optString("trans_uz", "")

                        val norm = Language.normalizeText(headword)
                        val entry = DictionaryEntryEntity(
                            headword = headword,
                            headwordNormalized = norm,
                            language = lang,
                            phoneticIpa = ipa,
                            partOfSpeech = pos,
                            cefrLevel = cefr,
                            frequencyRank = obj.optInt("rank", 500),
                            lemma = headword,
                            alternativeSpellings = if (lang == "uz") Language.transliterateUzbek(headword, true) else "",
                            primaryMeaningEn = defEn,
                            primaryMeaningRu = defRu,
                            primaryMeaningUz = defUz
                        )
                        entryBatch.add(entry)
                    }
                } catch (_: Exception) {
                    // Skip corrupt single lines safely
                }

                if (entryBatch.size >= 250) {
                    insertedTotal += flushBatch(entryBatch)
                    _progress.value = _progress.value.copy(
                        processedCount = lineCount,
                        insertedCount = insertedTotal,
                        stageMessage = "$insertedTotal ta so‘z saqlandi..."
                    )
                }
            }
            line = reader.readLine()
        }

        if (entryBatch.isNotEmpty()) {
            insertedTotal += flushBatch(entryBatch)
        }

        return insertedTotal
    }

    private suspend fun parseJson(inputStream: InputStream): Int {
        val content = inputStream.bufferedReader(Charsets.UTF_8).readText()
        if (content.trim().startsWith("[")) {
            val jsonArray = org.json.JSONArray(content)
            val entryBatch = mutableListOf<DictionaryEntryEntity>()
            var insertedTotal = 0

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val headword = obj.optString("word", obj.optString("headword", "")).trim()
                if (headword.isNotBlank()) {
                    val lang = obj.optString("language", "en").lowercase()
                    entryBatch.add(
                        DictionaryEntryEntity(
                            headword = headword,
                            headwordNormalized = Language.normalizeText(headword),
                            language = lang,
                            phoneticIpa = obj.optString("ipa", ""),
                            partOfSpeech = obj.optString("pos", "noun"),
                            cefrLevel = obj.optString("cefr", "B1"),
                            frequencyRank = obj.optInt("rank", 500),
                            lemma = headword,
                            primaryMeaningEn = obj.optString("def_en", obj.optString("definition", "")),
                            primaryMeaningRu = obj.optString("def_ru", ""),
                            primaryMeaningUz = obj.optString("def_uz", "")
                        )
                    )
                }

                if (entryBatch.size >= 250) {
                    insertedTotal += flushBatch(entryBatch)
                    _progress.value = _progress.value.copy(
                        processedCount = i + 1,
                        insertedCount = insertedTotal,
                        stageMessage = "$insertedTotal ta so‘z kiritildi..."
                    )
                }
            }

            if (entryBatch.isNotEmpty()) {
                insertedTotal += flushBatch(entryBatch)
            }
            return insertedTotal
        } else {
            // Might be JSONL
            return parseJsonl(content.byteInputStream())
        }
    }

    private suspend fun parseCsv(inputStream: InputStream): Int {
        return inputStream.use { parseCsvStream(it) }
    }

    private suspend fun parseCsvStream(stream: InputStream): Int {
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        val entryBatch = mutableListOf<DictionaryEntryEntity>()
        var insertedTotal = 0
        var lineCount = 0

        var line = reader.readLine()
        // Check if first line is header
        if (line != null && (line.contains("word", ignoreCase = true) || line.contains("headword", ignoreCase = true))) {
            line = reader.readLine()
        }

        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.isNotBlank()) {
                lineCount++
                val parts = if (trimmed.contains("\t")) trimmed.split("\t") else trimmed.split(",")
                if (parts.isNotEmpty()) {
                    val headword = parts[0].trim().removeSurrounding("\"")
                    if (headword.isNotBlank()) {
                        val lang = parts.getOrNull(1)?.trim()?.removeSurrounding("\"")?.lowercase() ?: "en"
                        val pos = parts.getOrNull(2)?.trim()?.removeSurrounding("\"") ?: "noun"
                        val ipa = parts.getOrNull(3)?.trim()?.removeSurrounding("\"") ?: ""
                        val primaryRu = parts.getOrNull(4)?.trim()?.removeSurrounding("\"") ?: ""
                        val primaryUz = parts.getOrNull(5)?.trim()?.removeSurrounding("\"") ?: ""

                        entryBatch.add(
                            DictionaryEntryEntity(
                                headword = headword,
                                headwordNormalized = Language.normalizeText(headword),
                                language = lang,
                                phoneticIpa = ipa,
                                partOfSpeech = pos,
                                primaryMeaningRu = primaryRu,
                                primaryMeaningUz = primaryUz,
                                primaryMeaningEn = headword
                            )
                        )
                    }
                }

                if (entryBatch.size >= 250) {
                    insertedTotal += flushBatch(entryBatch)
                    _progress.value = _progress.value.copy(
                        processedCount = lineCount,
                        insertedCount = insertedTotal,
                        stageMessage = "$insertedTotal CSV qatori yuklandi..."
                    )
                }
            }
            line = reader.readLine()
        }

        if (entryBatch.isNotEmpty()) {
            insertedTotal += flushBatch(entryBatch)
        }

        return insertedTotal
    }

    private suspend fun flushBatch(batch: MutableList<DictionaryEntryEntity>): Int {
        val count = batch.size
        dictionaryDao.insertEntries(batch)
        batch.clear()
        return count
    }

    override fun getSampleJsonlTemplate(): String {
        return """
            {"headword":"perseverance","language":"en","pos":"noun","ipa":"/ˌpɜːsɪˈvɪərəns/","cefr":"B2","def_en":"persistence in doing something despite difficulty","def_ru":"настойчивость, упорство","def_uz":"qat’iyatlilik, sabot, tirishqoqlik"}
            {"headword":"созидать","language":"ru","pos":"verb","ipa":"/səzʲɪˈdatʲ/","cefr":"B2","def_en":"to create, construct","def_ru":"творить, создавать нечто ценное","def_uz":"bunyod etmoq, yaratmoq"}
            {"headword":"ma’rifat","language":"uz","pos":"noun","ipa":"/mæːrɪˈfæt/","cefr":"B2","def_en":"enlightenment, education","def_ru":"просвещение, духовность","def_uz":"ilm va ma’naviy kamolot nuri"}
        """.trimIndent()
    }
}
