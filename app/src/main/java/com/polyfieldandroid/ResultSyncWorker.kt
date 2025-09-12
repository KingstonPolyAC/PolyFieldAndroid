package com.polyfieldandroid

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker for syncing cached results to server
 * Handles offline resilience and automatic retry mechanisms
 */
class ResultSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "ResultSyncWorker"
        const val WORK_NAME = "result_sync_work"
        const val WORK_TAG = "result_sync"
        
        // Input data keys
        const val KEY_SERVER_IP = "server_ip"
        const val KEY_SERVER_PORT = "server_port"
        const val KEY_RETRY_COUNT = "retry_count"
        
        /**
         * Schedule periodic sync work
         */
        fun schedulePeriodicSync(context: Context, serverIp: String, serverPort: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val inputData = Data.Builder()
                .putString(KEY_SERVER_IP, serverIp)
                .putInt(KEY_SERVER_PORT, serverPort)
                .build()
            
            val periodicSyncRequest = PeriodicWorkRequestBuilder<ResultSyncWorker>(
                repeatInterval = 15, // Every 15 minutes
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag(WORK_TAG)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, // Initial delay
                    TimeUnit.SECONDS
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicSyncRequest
            )
            
            Log.d(TAG, "Scheduled periodic sync for $serverIp:$serverPort")
        }
        
        /**
         * Schedule one-time immediate sync
         */
        fun scheduleImmediateSync(context: Context, serverIp: String, serverPort: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val inputData = Data.Builder()
                .putString(KEY_SERVER_IP, serverIp)
                .putInt(KEY_SERVER_PORT, serverPort)
                .putInt(KEY_RETRY_COUNT, 0)
                .build()
            
            val immediateSync = OneTimeWorkRequestBuilder<ResultSyncWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag(WORK_TAG)
                .build()
            
            WorkManager.getInstance(context).enqueue(immediateSync)
            
            Log.d(TAG, "Scheduled immediate sync for $serverIp:$serverPort")
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
            val serverIp = inputData.getString(KEY_SERVER_IP) ?: return@withContext Result.failure()
            val serverPort = inputData.getInt(KEY_SERVER_PORT, 8080)
            val retryCount = inputData.getInt(KEY_RETRY_COUNT, 0)
            
            Log.d(TAG, "Starting sync work - attempt ${retryCount + 1}")
            
            val apiClient = PolyFieldApiClient(applicationContext)
            val cacheManager = ResultsCacheManager(applicationContext)
            
            val cachedResults = cacheManager.getCachedResults()
            if (cachedResults.isEmpty()) {
                Log.d(TAG, "No cached results to sync")
                return@withContext Result.success()
            }
            
            Log.d(TAG, "Found ${cachedResults.size} cached results to sync")
            
            var successCount = 0
            var failureCount = 0
            val failedResults = mutableListOf<PolyFieldApiClient.ResultPayload>()
            
            for (result in cachedResults) {
                try {
                    apiClient.postResult(serverIp, serverPort, result)
                    cacheManager.removeFromCache(result)
                    successCount++
                    Log.d(TAG, "Successfully synced result for athlete ${result.athleteBib}")
                } catch (e: Exception) {
                    failureCount++
                    failedResults.add(result)
                    Log.w(TAG, "Failed to sync result for athlete ${result.athleteBib}: ${e.message}")
                }
            }
            
            Log.d(TAG, "Sync completed: $successCount successful, $failureCount failed")
            
            // If all results failed and we haven't retried too many times, retry with exponential backoff
            if (failureCount > 0 && successCount == 0 && retryCount < 3) {
                Log.d(TAG, "All results failed, scheduling retry ${retryCount + 1}")
                return@withContext Result.retry()
            }
            
            // Create summary for notification
            val outputData = Data.Builder()
                .putInt("success_count", successCount)
                .putInt("failure_count", failureCount)
                .putInt("total_processed", cachedResults.size)
                .build()
            
            return@withContext if (failureCount == 0) {
                Result.success(outputData)
            } else {
                // Partial success - some results synced, some failed
                Result.success(outputData)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Sync work failed with exception: ${e.message}", e)
            return@withContext Result.retry()
        }
    }
}

/**
 * Enhanced Results Cache Manager with better organization
 */
class ResultsCacheManager(private val context: Context) {
    companion object {
        private const val TAG = "ResultsCacheManager"
        private const val CACHE_FILE_NAME = "polyfield_results_cache.json"
        private const val METADATA_FILE_NAME = "polyfield_cache_metadata.json"
    }
    
    private val gson = com.google.gson.Gson()
    
    data class CacheMetadata(
        val lastSyncAttempt: Long = 0L,
        val totalCachedResults: Int = 0,
        val lastSuccessfulSync: Long = 0L,
        val consecutiveFailures: Int = 0
    )
    
    fun cacheResult(result: PolyFieldApiClient.ResultPayload) {
        try {
            val cachedResults = getCachedResults().toMutableList()
            
            // Remove any existing result for the same athlete/event to avoid duplicates
            cachedResults.removeAll { 
                it.eventId == result.eventId && it.athleteBib == result.athleteBib 
            }
            
            cachedResults.add(result)
            
            val cacheFile = java.io.File(context.cacheDir, CACHE_FILE_NAME)
            cacheFile.writeText(gson.toJson(cachedResults))
            
            // Update metadata
            updateMetadata { metadata ->
                metadata.copy(
                    totalCachedResults = cachedResults.size,
                    lastSyncAttempt = System.currentTimeMillis()
                )
            }
            
            Log.d(TAG, "Result cached successfully. Total cached: ${cachedResults.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching result: ${e.message}", e)
        }
    }
    
    fun getCachedResults(): List<PolyFieldApiClient.ResultPayload> {
        return try {
            val cacheFile = java.io.File(context.cacheDir, CACHE_FILE_NAME)
            if (cacheFile.exists()) {
                val jsonString = cacheFile.readText()
                val listType = object : com.google.gson.reflect.TypeToken<List<PolyFieldApiClient.ResultPayload>>() {}.type
                gson.fromJson<List<PolyFieldApiClient.ResultPayload>>(jsonString, listType) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cached results: ${e.message}", e)
            emptyList()
        }
    }
    
    fun removeFromCache(result: PolyFieldApiClient.ResultPayload) {
        try {
            val cachedResults = getCachedResults().toMutableList()
            val removed = cachedResults.removeAll { 
                it.eventId == result.eventId && it.athleteBib == result.athleteBib 
            }
            
            if (removed) {
                val cacheFile = java.io.File(context.cacheDir, CACHE_FILE_NAME)
                cacheFile.writeText(gson.toJson(cachedResults))
                
                updateMetadata { metadata ->
                    metadata.copy(
                        totalCachedResults = cachedResults.size,
                        lastSuccessfulSync = System.currentTimeMillis(),
                        consecutiveFailures = 0
                    )
                }
                
                Log.d(TAG, "Result removed from cache. Remaining: ${cachedResults.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing result from cache: ${e.message}", e)
        }
    }
    
    fun hasCachedResults(): Boolean {
        return getCachedResults().isNotEmpty()
    }
    
    fun getCachedResultsCount(): Int {
        return getCachedResults().size
    }
    
    fun clearCache() {
        try {
            val cacheFile = java.io.File(context.cacheDir, CACHE_FILE_NAME)
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            
            updateMetadata { metadata ->
                metadata.copy(
                    totalCachedResults = 0,
                    lastSuccessfulSync = System.currentTimeMillis()
                )
            }
            
            Log.d(TAG, "Cache cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache: ${e.message}", e)
        }
    }
    
    fun getMetadata(): CacheMetadata {
        return try {
            val metadataFile = java.io.File(context.cacheDir, METADATA_FILE_NAME)
            if (metadataFile.exists()) {
                val jsonString = metadataFile.readText()
                gson.fromJson(jsonString, CacheMetadata::class.java) ?: CacheMetadata()
            } else {
                CacheMetadata()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cache metadata: ${e.message}", e)
            CacheMetadata()
        }
    }
    
    private fun updateMetadata(update: (CacheMetadata) -> CacheMetadata) {
        try {
            val currentMetadata = getMetadata()
            val updatedMetadata = update(currentMetadata)
            
            val metadataFile = java.io.File(context.cacheDir, METADATA_FILE_NAME)
            metadataFile.writeText(gson.toJson(updatedMetadata))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating cache metadata: ${e.message}", e)
        }
    }
}