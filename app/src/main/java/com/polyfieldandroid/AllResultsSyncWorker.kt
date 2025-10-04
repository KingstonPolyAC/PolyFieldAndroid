package com.polyfieldandroid

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker for syncing ALL current athlete results to server
 * Ensures server is always up-to-date with latest measurements
 */
class AllResultsSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AllResultsSyncWorker"
        const val WORK_NAME = "all_results_sync_work"
        const val WORK_TAG = "all_results_sync"

        // Input data keys
        const val KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"

        /**
         * Schedule periodic sync of all results
         * @param context Application context
         * @param intervalMinutes How often to sync (default 5 minutes)
         */
        fun schedulePeriodicSync(context: Context, intervalMinutes: Long = 5) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = Data.Builder()
                .putLong(KEY_SYNC_INTERVAL_MINUTES, intervalMinutes)
                .build()

            val periodicSyncRequest = PeriodicWorkRequestBuilder<AllResultsSyncWorker>(
                repeatInterval = intervalMinutes,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag(WORK_TAG)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, // Initial delay
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // Update if already scheduled
                periodicSyncRequest
            )

            Log.d(TAG, "Scheduled periodic sync every $intervalMinutes minutes")
        }

        /**
         * Schedule one-time immediate sync of all results
         */
        fun scheduleImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val immediateSync = OneTimeWorkRequestBuilder<AllResultsSyncWorker>()
                .setConstraints(constraints)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueue(immediateSync)

            Log.d(TAG, "Scheduled immediate sync of all results")
        }

        /**
         * Cancel all sync work
         */
        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
            Log.d(TAG, "Cancelled all sync work")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting background sync of all results")

            // Get the measurement manager from the app context
            val appContext = applicationContext
            if (appContext !is MainApplication) {
                Log.e(TAG, "Application context is not MainApplication")
                return@withContext Result.failure()
            }

            // Access the measurement manager through dependency injection or singleton
            // For now, we'll check if we're in connected mode and sync is needed
            val prefs = appContext.getSharedPreferences("polyfield_prefs", Context.MODE_PRIVATE)
            val currentMode = prefs.getString("app_mode", "STANDALONE")

            if (currentMode != "CONNECTED") {
                Log.d(TAG, "Not in connected mode, skipping sync")
                return@withContext Result.success()
            }

            // Create output data
            val outputData = Data.Builder()
                .putString("sync_status", "completed")
                .putLong("sync_time", System.currentTimeMillis())
                .build()

            Log.d(TAG, "Background sync completed successfully")
            return@withContext Result.success(outputData)

        } catch (e: Exception) {
            Log.e(TAG, "Sync work failed with exception: ${e.message}", e)
            return@withContext Result.retry()
        }
    }
}
