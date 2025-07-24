package net.bunnystream.android.demo.worker

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.coroutineScope
import net.bunnystream.api.playback.DefaultPlaybackPositionManager
import net.bunnystream.api.playback.ResumeConfig
import java.util.concurrent.TimeUnit

class PositionCleanupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "position_cleanup_work"
        const val DEFAULT_RETENTION_DAYS = 7

        fun schedulePeriodicCleanup(context: Context, retentionDays: Int = DEFAULT_RETENTION_DAYS) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build()

            val cleanupRequest = PeriodicWorkRequestBuilder<PositionCleanupWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setInputData(workDataOf("retention_days" to retentionDays))
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    cleanupRequest
                )
        }

        fun cancelCleanup(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result = coroutineScope {
        try {
            val retentionDays = inputData.getInt("retention_days", DEFAULT_RETENTION_DAYS)

            // Create a temporary config for cleanup
            val config = ResumeConfig(retentionDays = retentionDays)

            // Use our simplified position manager for cleanup
            val positionManager = DefaultPlaybackPositionManager(applicationContext, config)

            // Perform cleanup
            positionManager.cleanupExpiredPositions()

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}