package com.polyfieldandroid

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Application Mode Management
 * Handles switching between Stand-Alone and Connected modes
 */
enum class AppMode {
    STANDALONE,  // Offline mode with direct EDM connection
    CONNECTED    // Online mode with server integration
}

data class ServerConfig(
    val ipAddress: String = "192.168.1.100",
    val port: Int = 8080,
    val isConnected: Boolean = false,
    val lastConnectionAttempt: Long = 0L
)

data class ModeState(
    val currentMode: AppMode = AppMode.STANDALONE,
    val serverConfig: ServerConfig = ServerConfig(),
    val isInitialized: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel for managing application modes and server connectivity
 * Integrates with existing AppSettings for server configuration
 */
class ModeManagerViewModel(
    private val context: Context,
    private val appViewModel: AppViewModel? = null
) : ViewModel() {
    
    companion object {
        private const val TAG = "ModeManager"
        private const val PREFS_NAME = "polyfield_mode_prefs"
        private const val PREF_MODE = "app_mode"
        private const val CONNECTION_TIMEOUT_MS = 5000L
    }
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val apiClient = PolyFieldApiClient(context)
    private val cacheManager = ResultsCacheManager(context)
    
    // State management
    private val _modeState = MutableStateFlow(ModeState())
    val modeState: StateFlow<ModeState> = _modeState.asStateFlow()
    
    private val _availableEvents = MutableStateFlow<List<PolyFieldApiClient.Event>>(emptyList())
    val availableEvents: StateFlow<List<PolyFieldApiClient.Event>> = _availableEvents.asStateFlow()
    
    init {
        loadSavedSettings()
    }
    
    /**
     * Load previously saved mode and server settings
     * Uses AppViewModel settings if available, fallback to local preferences
     */
    private fun loadSavedSettings() {
        val savedMode = preferences.getString(PREF_MODE, AppMode.STANDALONE.name)
        
        val mode = try {
            AppMode.valueOf(savedMode ?: AppMode.STANDALONE.name)
        } catch (e: Exception) {
            AppMode.STANDALONE
        }
        
        // Get server settings from AppViewModel if available, otherwise use defaults
        val appSettings = appViewModel?.uiState?.value?.settings
        val ipAddress = appSettings?.serverIpAddress ?: "192.168.0.90"
        val port = appSettings?.serverPort ?: 8080
        
        _modeState.value = _modeState.value.copy(
            currentMode = mode,
            serverConfig = ServerConfig(
                ipAddress = ipAddress,
                port = port
            ),
            isInitialized = true
        )
        
        Log.d(TAG, "Loaded settings: mode=$mode, server=$ipAddress:$port")
    }
    
    /**
     * Switch to Stand-Alone mode
     */
    fun setStandaloneMode() {
        viewModelScope.launch {
            // Cancel any ongoing sync work when switching to standalone
            ResultSyncWorker.cancelSync(context)
            
            _modeState.value = _modeState.value.copy(
                currentMode = AppMode.STANDALONE,
                errorMessage = null
            )
            
            saveMode(AppMode.STANDALONE)
            Log.d(TAG, "Switched to Stand-Alone mode")
        }
    }
    
    /**
     * Create demo event data for training purposes
     */
    private fun createDemoEvent(): PolyFieldApiClient.Event {
        val demoAthletes = listOf(
            PolyFieldApiClient.Athlete(bib = "1", order = 1, name = "John Smith", club = "Athletics Club"),
            PolyFieldApiClient.Athlete(bib = "2", order = 2, name = "Sarah Johnson", club = "Track Stars"),
            PolyFieldApiClient.Athlete(bib = "3", order = 3, name = "Mike Davis", club = "Speed Demons"),
            PolyFieldApiClient.Athlete(bib = "4", order = 4, name = "Emma Wilson", club = "Lightning Bolts"),
            PolyFieldApiClient.Athlete(bib = "5", order = 5, name = "Chris Brown", club = "Thunder Throws"),
            PolyFieldApiClient.Athlete(bib = "6", order = 6, name = "Lisa Garcia", club = "Power Athletes"),
            PolyFieldApiClient.Athlete(bib = "7", order = 7, name = "David Miller", club = "Elite Track"),
            PolyFieldApiClient.Athlete(bib = "8", order = 8, name = "Ashley Taylor", club = "Fast Feet"),
            PolyFieldApiClient.Athlete(bib = "9", order = 9, name = "Ryan Anderson", club = "Velocity Vaults"),
            PolyFieldApiClient.Athlete(bib = "10", order = 10, name = "Jessica Martinez", club = "Dynamic Throws")
        )
        
        return PolyFieldApiClient.Event(
            id = "DEMO_EVENT_1",
            name = "Demo Discus Open",
            type = "Throws",
            rules = PolyFieldApiClient.EventRules(
                attempts = 3,
                cutEnabled = true,
                cutQualifiers = 8,
                reorderAfterCut = true
            ),
            athletes = demoAthletes
        )
    }

    /**
     * Switch to Connected mode and test server connection
     * Uses current AppViewModel settings if no parameters provided
     */
    fun setConnectedMode(ipAddress: String? = null, port: Int? = null) {
        viewModelScope.launch {
            try {
                // Check if we're in demo mode
                val isDemoMode = appViewModel?.uiState?.value?.isDemoMode ?: false
                
                if (isDemoMode) {
                    // Use demo data instead of connecting to server
                    Log.d(TAG, "Demo mode enabled - using demo event data")
                    
                    val demoEvent = createDemoEvent()
                    val demoEvents = listOf(demoEvent)
                    
                    // Set connected state with demo data
                    _modeState.value = _modeState.value.copy(
                        currentMode = AppMode.CONNECTED,
                        serverConfig = _modeState.value.serverConfig.copy(
                            ipAddress = "DEMO_SERVER",
                            port = 0,
                            isConnected = true
                        ),
                        errorMessage = null
                    )
                    
                    _availableEvents.value = demoEvents
                    
                    Log.d(TAG, "Demo mode connected with demo event: ${demoEvent.name}")
                    Log.d(TAG, "Demo event has ${demoEvent.athletes?.size ?: 0} athletes")
                    
                } else {
                    // Real server connection mode
                    val appSettings = appViewModel?.uiState?.value?.settings
                    val finalIpAddress = ipAddress ?: appSettings?.serverIpAddress ?: "192.168.0.90"
                    val finalPort = port ?: appSettings?.serverPort ?: 8080
                    
                    // Update state to show connection attempt
                    _modeState.value = _modeState.value.copy(
                        serverConfig = _modeState.value.serverConfig.copy(
                            ipAddress = finalIpAddress,
                            port = finalPort,
                            lastConnectionAttempt = System.currentTimeMillis()
                        )
                    )
                    
                    // Test connection by fetching events
                    val basicEvents = apiClient.fetchEvents(finalIpAddress, finalPort)
                    
                    // Fetch detailed information for each event to get athlete data
                    val detailedEvents = basicEvents.map { event ->
                        try {
                            apiClient.fetchEventDetails(finalIpAddress, finalPort, event.id)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to fetch details for event ${event.id}: ${e.message}")
                            event // Use basic event info if details fetch fails
                        }
                    }
                    
                    // Connection successful
                    _modeState.value = _modeState.value.copy(
                        currentMode = AppMode.CONNECTED,
                        serverConfig = _modeState.value.serverConfig.copy(
                            isConnected = true
                        ),
                        errorMessage = null
                    )
                    
                    _availableEvents.value = detailedEvents
                    
                    // Start background sync for cached results
                    ResultSyncWorker.schedulePeriodicSync(context, finalIpAddress, finalPort)
                    
                    Log.d(TAG, "Successfully connected to server at $finalIpAddress:$finalPort")
                    Log.d(TAG, "Found ${detailedEvents.size} events on server")
                    
                    // Check for cached results and sync immediately if found
                    if (cacheManager.hasCachedResults()) {
                        Log.d(TAG, "Found ${cacheManager.getCachedResultsCount()} cached results, scheduling immediate sync")
                        ResultSyncWorker.scheduleImmediateSync(context, finalIpAddress, finalPort)
                    }
                }
                
                saveMode(AppMode.CONNECTED)
                
            } catch (e: Exception) {
                // Connection failed
                _modeState.value = _modeState.value.copy(
                    currentMode = AppMode.STANDALONE, // Fall back to standalone
                    serverConfig = _modeState.value.serverConfig.copy(
                        isConnected = false
                    ),
                    errorMessage = "Failed to connect to server: ${e.message}"
                )
                
                Log.e(TAG, "Failed to connect to server: ${e.message}")
            }
        }
    }
    
    /**
     * Refresh events from server (Connected mode only)
     */
    fun refreshEvents() {
        if (_modeState.value.currentMode != AppMode.CONNECTED) return
        
        viewModelScope.launch {
            try {
                val serverConfig = _modeState.value.serverConfig
                val basicEvents = apiClient.fetchEvents(serverConfig.ipAddress, serverConfig.port)
                
                // Fetch detailed information for each event to get athlete data
                val detailedEvents = basicEvents.map { event ->
                    try {
                        apiClient.fetchEventDetails(serverConfig.ipAddress, serverConfig.port, event.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to fetch details for event ${event.id}: ${e.message}")
                        event // Use basic event info if details fetch fails
                    }
                }
                
                _availableEvents.value = detailedEvents
                
                Log.d(TAG, "Refreshed events: found ${detailedEvents.size} events with details")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh events: ${e.message}")
                _modeState.value = _modeState.value.copy(
                    errorMessage = "Failed to refresh events: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Get event details for a specific event
     */
    suspend fun getEventDetails(eventId: String): PolyFieldApiClient.Event? {
        return if (_modeState.value.currentMode == AppMode.CONNECTED) {
            try {
                val serverConfig = _modeState.value.serverConfig
                apiClient.fetchEventDetails(serverConfig.ipAddress, serverConfig.port, eventId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get event details: ${e.message}")
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Submit results (Connected mode only)
     */
    suspend fun submitResult(result: PolyFieldApiClient.ResultPayload): Boolean {
        return if (_modeState.value.currentMode == AppMode.CONNECTED) {
            try {
                val serverConfig = _modeState.value.serverConfig
                apiClient.postResult(serverConfig.ipAddress, serverConfig.port, result)
                Log.d(TAG, "Result submitted successfully for athlete ${result.athleteBib}")
                true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to submit result for athlete ${result.athleteBib}: ${e.message}")
                // Cache the result for later sync
                cacheManager.cacheResult(result)
                
                // Schedule immediate retry if we have connectivity
                val currentServerConfig = _modeState.value.serverConfig
                if (currentServerConfig.isConnected) {
                    ResultSyncWorker.scheduleImmediateSync(context, currentServerConfig.ipAddress, currentServerConfig.port)
                }
                
                Log.d(TAG, "Result cached for later sync")
                false // Return false to indicate it was cached, not immediately successful
            }
        } else {
            Log.w(TAG, "Cannot submit result in Stand-Alone mode")
            false
        }
    }
    
    /**
     * Clear any error messages
     */
    fun clearError() {
        _modeState.value = _modeState.value.copy(errorMessage = null)
    }
    
    /**
     * Test server connection without switching modes
     */
    suspend fun testServerConnection(ipAddress: String, port: Int): Boolean {
        return try {
            apiClient.fetchEvents(ipAddress, port)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Server connection test failed: ${e.message}")
            false
        }
    }
    
    // Convenience getters
    fun isStandaloneMode(): Boolean = _modeState.value.currentMode == AppMode.STANDALONE
    fun isConnectedMode(): Boolean = _modeState.value.currentMode == AppMode.CONNECTED
    fun getServerConfig(): ServerConfig = _modeState.value.serverConfig
    
    /**
     * Get cached results information
     */
    fun getCachedResultsCount(): Int = cacheManager.getCachedResultsCount()
    fun hasCachedResults(): Boolean = cacheManager.hasCachedResults()
    fun getCacheMetadata(): ResultsCacheManager.CacheMetadata = cacheManager.getMetadata()
    
    /**
     * Manually trigger sync of cached results
     */
    fun triggerManualSync() {
        if (_modeState.value.currentMode == AppMode.CONNECTED) {
            val serverConfig = _modeState.value.serverConfig
            ResultSyncWorker.scheduleImmediateSync(context, serverConfig.ipAddress, serverConfig.port)
            Log.d(TAG, "Manual sync triggered")
        }
    }
    
    /**
     * Clear all cached results (use with caution)
     */
    fun clearCache() {
        cacheManager.clearCache()
        Log.d(TAG, "Cache cleared manually")
    }
    
    /**
     * Save current mode to preferences
     */
    private fun saveMode(mode: AppMode) {
        preferences.edit()
            .putString(PREF_MODE, mode.name)
            .apply()
    }
    
}

/**
 * Factory for creating ModeManagerViewModel with Context and AppViewModel
 */
class ModeManagerViewModelFactory(
    private val context: Context,
    private val appViewModel: AppViewModel? = null
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModeManagerViewModel::class.java)) {
            return ModeManagerViewModel(context, appViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}