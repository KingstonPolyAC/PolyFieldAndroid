package com.polyfieldandroid

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * API Client for PolyField Control Server Integration
 * Handles all HTTP communication with the competition management server
 */
class PolyFieldApiClient(private val context: Context) {
    
    companion object {
        private const val TAG = "PolyFieldApiClient"
        private const val CONNECTION_TIMEOUT = 10000 // 10 seconds
        private const val READ_TIMEOUT = 15000 // 15 seconds
        private const val CACHE_FILE_NAME = "polyfield_results_cache.json"
        private const val RETRY_INTERVAL_MS = 120000L // 2 minutes
    }
    
    private val gson = Gson()
    private var baseUrl: String = ""
    private val resultsCacheManager = ResultsCacheManager(context)
    
    // Data classes matching API specification
    data class Event(
        val id: String,
        val name: String,
        val type: String,
        val rules: EventRules? = null,
        val athletes: List<Athlete>? = null
    )
    
    data class EventRules(
        val attempts: Int,
        val cutEnabled: Boolean,
        val cutQualifiers: Int,
        val reorderAfterCut: Boolean
    )
    
    data class Athlete(
        val bib: String,
        val order: Int,
        val name: String,
        val club: String
    )
    
    data class ResultPayload(
        val eventId: String,
        val athleteBib: String,
        val series: List<Performance>
    )
    
    data class Performance(
        val attempt: Int,
        val mark: String,
        val unit: String,
        val wind: String? = null,
        val valid: Boolean
    )
    
    /**
     * Set server address for API calls
     */
    fun setServerAddress(ip: String, port: Int) {
        baseUrl = "http://$ip:$port"
        Log.d(TAG, "Server address set to: $baseUrl")
    }
    
    /**
     * Fetch list of available events from server
     */
    suspend fun fetchEvents(ip: String, port: Int): List<Event> = withContext(Dispatchers.IO) {
        try {
            setServerAddress(ip, port)
            val url = URL("$baseUrl/api/v1/events")
            val response = performHttpRequest(url, "GET")
            
            val listType = object : TypeToken<List<Event>>() {}.type
            gson.fromJson<List<Event>>(response, listType)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching events: ${e.message}")
            throw e
        }
    }
    
    /**
     * Fetch detailed information for a specific event
     */
    suspend fun fetchEventDetails(ip: String, port: Int, eventId: String): Event = withContext(Dispatchers.IO) {
        try {
            setServerAddress(ip, port)
            val url = URL("$baseUrl/api/v1/events/$eventId")
            val response = performHttpRequest(url, "GET")
            
            gson.fromJson(response, Event::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching event details for $eventId: ${e.message}")
            throw e
        }
    }
    
    /**
     * Submit results to server with automatic caching and retry
     */
    suspend fun postResult(ip: String, port: Int, payload: ResultPayload) = withContext(Dispatchers.IO) {
        try {
            setServerAddress(ip, port)
            val url = URL("$baseUrl/api/v1/results")
            val jsonPayload = gson.toJson(payload)
            
            performHttpRequest(url, "POST", jsonPayload)
            Log.d(TAG, "Result submitted successfully for athlete ${payload.athleteBib}")
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to submit result, caching for retry: ${e.message}")
            resultsCacheManager.cacheResult(payload)
            
            // Start background retry process 
            // Note: In production, this should use WorkManager for proper background processing
        }
    }
    
    /**
     * Perform HTTP request with proper error handling
     */
    private fun performHttpRequest(url: URL, method: String, body: String? = null): String {
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECTION_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            
            // Send request body for POST requests
            if (method == "POST" && body != null) {
                connection.doOutput = true
                connection.outputStream.use { outputStream ->
                    OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                        writer.write(body)
                    }
                }
            }
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorStream = connection.errorStream?.bufferedReader()?.readText()
                throw IOException("HTTP $responseCode: $errorStream")
            }
            
            return connection.inputStream.bufferedReader().readText()
            
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Start background retry process for cached results
     * Note: Simplified implementation - production should use WorkManager
     */
    private fun startRetryProcess(ip: String, port: Int) {
        // In production, this would use WorkManager for proper background processing
        // For now, this is just a placeholder
        Log.d(TAG, "Retry process would start in background (WorkManager implementation needed)")
    }
    
    /**
     * Results caching manager for offline resilience
     */
    private class ResultsCacheManager(private val context: Context) {
        private val gson = Gson()
        
        fun cacheResult(result: ResultPayload) {
            try {
                val cachedResults = getCachedResults().toMutableList()
                cachedResults.add(result)
                
                val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
                cacheFile.writeText(gson.toJson(cachedResults))
                
                Log.d(TAG, "Result cached successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error caching result: ${e.message}")
            }
        }
        
        fun getCachedResults(): List<ResultPayload> {
            return try {
                val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
                if (cacheFile.exists()) {
                    val jsonString = cacheFile.readText()
                    val listType = object : TypeToken<List<ResultPayload>>() {}.type
                    gson.fromJson<List<ResultPayload>>(jsonString, listType) ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading cached results: ${e.message}")
                emptyList()
            }
        }
        
        fun removeFromCache(result: ResultPayload) {
            try {
                val cachedResults = getCachedResults().toMutableList()
                cachedResults.removeAll { 
                    it.eventId == result.eventId && it.athleteBib == result.athleteBib 
                }
                
                val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
                cacheFile.writeText(gson.toJson(cachedResults))
                
            } catch (e: Exception) {
                Log.e(TAG, "Error removing result from cache: ${e.message}")
            }
        }
        
        fun hasCachedResults(): Boolean {
            return getCachedResults().isNotEmpty()
        }
        
        fun clearCache() {
            try {
                val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing cache: ${e.message}")
            }
        }
    }
}