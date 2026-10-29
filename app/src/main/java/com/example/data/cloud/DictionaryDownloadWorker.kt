package com.example.data.cloud

import android.content.Context
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.cache.DetailCacheManager
import com.example.data.database.LexiTrioDatabase
import com.example.data.repository.SearchCacheVersion
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads the dictionary in the background without an account: entries and
 * meanings only ("lite" pages), resumable from a stored cursor. While it runs,
 * search is answered by the server, so nobody waits for the download.
 */
internal class DictionaryDownloader(
    private val database: LexiTrioDatabase,
    private val api: CloudApi,
    private val state: SyncStateStore,
    // Android kills background jobs after ~10 minutes; stopping earlier on our
    // own terms keeps the app out of the system's job-timeout quota.
    private val budgetMs: Long = Long.MAX_VALUE
) {
    /**
     * Runs one pass. Returns true when the catalog is current, false when the
     * time budget ran out first; call again to continue from the stored cursor.
     */
    suspend fun run(): Boolean {
        val deadline = System.nanoTime() + budgetMs.coerceAtMost(Long.MAX_VALUE / 2_000_000) * 1_000_000
        val resuming = state.pendingPassStartedAt != null
        val since = if (resuming) state.pendingPassSince else state.lastSyncAt
        var afterId = if (resuming || since == null) state.dictionaryCursor else 0L
        var passStartedAt: String? = state.pendingPassStartedAt
        val dao = database.dictionaryDao()
        if (resuming || since == null) state.isPassRunning = true

        val dynamicPageSize = if (state.isDictionaryComplete) PAGE_SIZE else 5000

        while (true) {
            val page = api.syncDictionary(
                afterId = afterId,
                limit = dynamicPageSize,
                since = since,
                lite = true
            )
            if (page.hasMore) state.isPassRunning = true
            if (passStartedAt == null && page.serverTime != null) {
                passStartedAt = page.serverTime
                state.pendingPassSince = since
                state.pendingPassStartedAt = passStartedAt
            }
            if (page.entries.isNotEmpty() || page.deletedIds.isNotEmpty()) {
                // Inserts, updates and deletions commit together; the cursor moves
                // only after this transaction succeeded.
                database.withTransaction {
                    dao.insertEntries(page.entries)
                    dao.insertMeanings(page.meanings)
                    page.deletedIds.chunked(DELETE_CHUNK).forEach { ids ->
                        dao.deleteEntriesUnlessSaved(ids)
                        database.cacheDao().delete(ids)
                    }
                }
                state.saveDictionaryCursor(page.nextAfterId)
                SearchCacheVersion.bump()
            }
            if (!page.hasMore || page.entries.isEmpty()) break
            afterId = page.nextAfterId
            if (System.nanoTime() >= deadline) return false
        }
        state.saveLastSyncAt(passStartedAt)
        state.pendingPassStartedAt = null
        state.pendingPassSince = null
        state.isPassRunning = false
        return true
    }

    companion object {
        const val PAGE_SIZE = 2000
        const val DELETE_CHUNK = 400
        const val WORKER_BUDGET_MS = 8L * 60 * 1000
    }
}

class DictionaryDownloadWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        return try {
            val finished = DictionaryDownloader(
                LexiTrioDatabase.getInstance(context),
                CloudApiFactory.create(context),
                SyncStateStore(context),
                budgetMs = DictionaryDownloader.WORKER_BUDGET_MS
            ).run()
            // Budget spent: continue shortly from the stored cursor.
            if (finished) Result.success() else Result.retry()
        } catch (error: IOException) {
            Result.retry()
        } catch (error: HttpException) {
            if (error.code() in 500..599 || error.code() == 429) Result.retry() else Result.failure()
        } catch (error: IllegalStateException) {
            // Release build without an API URL; nothing to retry.
            Result.failure()
        }
    }
}

object DictionaryDownloadScheduler {
    private const val DOWNLOAD_WORK = "lexitrio-dictionary-download"
    private const val REFRESH_WORK = "lexitrio-dictionary-refresh"

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    /**
     * Called on every launch: finishes an interrupted download right away and
     * keeps a periodic job that picks up server-side changes.
     */
    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.enqueueUniqueWork(
            DOWNLOAD_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DictionaryDownloadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        )
        workManager.enqueueUniquePeriodicWork(
            REFRESH_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DictionaryDownloadWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        )
    }
}

/**
 * Daily housekeeping: downloads details for the few highest-scored words that
 * lack them (bounded requests per run) and keeps detail content within budget.
 */
class AdaptivePrefetchWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        return try {
            val manager = DetailCacheManager(
                LexiTrioDatabase.getInstance(context),
                CloudApiFactory.create(context)
            )
            manager.evictExpiredDetails()
            manager.prefetch()
            manager.evictIfNeeded()
            Result.success()
        } catch (error: IOException) {
            Result.retry()
        } catch (error: IllegalStateException) {
            Result.failure()
        }
    }
}

object AdaptivePrefetchScheduler {
    private const val WORK_NAME = "lexitrio-adaptive-prefetch"

    // Prefetch is an optimisation, so it waits for unmetered network and spare battery/storage.
    fun schedule(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AdaptivePrefetchWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
        )
    }
}