package com.example.data.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.repository.CloudRepositoryProvider
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            val repository = CloudRepositoryProvider.create(applicationContext)
            if (repository.session.first() == null) {
                Result.success()
            } else {
                repository.synchronize()
                Result.success()
            }
        } catch (error: IOException) {
            Result.retry()
        } catch (error: HttpException) {
            if (error.code() in 500..599) Result.retry() else Result.failure()
        } catch (error: IllegalStateException) {
            Result.failure()
        }
    }
}

object SyncScheduler {
    private const val UNIQUE_WORK_NAME = "lexitrio-periodic-cloud-sync"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
