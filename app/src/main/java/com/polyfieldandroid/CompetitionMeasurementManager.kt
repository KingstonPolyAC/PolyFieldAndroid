package com.polyfieldandroid

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Coordinates for throw visualization  
 */
data class ThrowCoordinate(
    val x: Double, // X coordinate relative to circle center
    val y: Double, // Y coordinate relative to circle center
    val distance: Double, // Calculated throw distance
    val round: Int,
    val attemptNumber: Int,
    val isValid: Boolean = true
)

/**
 * Enhanced measurement module for competition management
 * Integrates existing EDM functionality with athlete tracking and results submission
 */
data class MeasurementResult(
    val athleteBib: String,
    val round: Int,
    val attemptNumber: Int,
    val distance: Double?, // Distance in meters
    val windSpeed: Double? = null, // Wind speed in m/s
    val isValid: Boolean = true,
    val coordinates: ThrowCoordinate? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val rawEDMReading: Any? = null // Can store any EDM reading format
)

data class MeasurementState(
    val isActive: Boolean = false,
    val currentMeasurement: MeasurementResult? = null,
    val isConnectedToEDM: Boolean = false,
    val isConnectedToWind: Boolean = false,
    val isDemoMode: Boolean = false,
    val lastEDMReading: Any? = null,
    val lastWindReading: Double? = null, // m/s
    val measurementHistory: List<MeasurementResult> = emptyList(),
    val errorMessage: String? = null
)

// Alias for compatibility with CompetitionFlowScreens.kt
typealias CompetitionMeasurementState = MeasurementState

/**
 * Competition-focused measurement manager that extends existing EDM functionality
 */
class CompetitionMeasurementManager(
    private val context: Context,
    private val edmModule: EDMModule,
    private val athleteManager: AthleteManagerViewModel,
    private val competitionManager: CompetitionManagerViewModel,
    private val modeManager: ModeManagerViewModel
) : ViewModel() {
    
    // Clean EDM Interface for new measurements
    private val edmInterface = EDMInterface(context)
    
    companion object {
        private const val TAG = "CompetitionMeasurement"
    }
    
    private val edmCalculations = EDMCalculations()
    
    // State management
    private val _measurementState = MutableStateFlow(MeasurementState())
    val measurementState: StateFlow<MeasurementState> = _measurementState.asStateFlow()
    
    /**
     * Start measurement session
     */
    fun startMeasurement() {
        viewModelScope.launch {
            _measurementState.value = _measurementState.value.copy(
                isActive = true,
                errorMessage = null
            )
            
            Log.d(TAG, "Measurement session started")
        }
    }
    
    /**
     * Stop measurement session
     */
    fun stopMeasurement() {
        _measurementState.value = _measurementState.value.copy(
            isActive = false
        )
        
        Log.d(TAG, "Measurement session stopped")
    }
    
    /**
     * Take throw measurement for current athlete
     */
    suspend fun measureThrow(): MeasurementResult? {
        return try {
            val currentAthlete = athleteManager.getCurrentAthlete()
            if (currentAthlete == null) {
                _measurementState.value = _measurementState.value.copy(
                    errorMessage = "No current athlete selected"
                )
                return null
            }
            
            val competitionState = competitionManager.competitionState.value
            if (!competitionState.isActive) {
                _measurementState.value = _measurementState.value.copy(
                    errorMessage = "Competition not active"
                )
                return null
            }
            
            // Get current attempt number for athlete
            val currentRound = competitionState.currentRound
            val attemptNumber = currentAthlete.getAttemptCount(currentRound) + 1
            
            // Take measurement using existing EDM module
            val measurementResult = if (_measurementState.value.isDemoMode) {
                takeDemoMeasurement(currentAthlete.bib, currentRound, attemptNumber)
            } else {
                takeRealMeasurement(currentAthlete.bib, currentRound, attemptNumber)
            }
            
            // Record the measurement
            if (measurementResult != null) {
                recordMeasurement(measurementResult)
            }
            
            measurementResult
            
        } catch (e: Exception) {
            Log.e(TAG, "Error taking measurement: ${e.message}")
            _measurementState.value = _measurementState.value.copy(
                errorMessage = "Measurement failed: ${e.message}"
            )
            null
        }
    }
    
    /**
     * Take real measurement using EDM device
     */
    private suspend fun takeRealMeasurement(
        athleteBib: String, 
        round: Int, 
        attemptNumber: Int
    ): MeasurementResult? {
        return try {
            // Use clean EDM interface for measurement
            val throwResult = edmInterface.measure("edm")
            
            if (throwResult.isFailure) {
                val error = throwResult.exceptionOrNull()?.message ?: "Unknown measurement error"
                _measurementState.value = _measurementState.value.copy(
                    errorMessage = error
                )
                return null
            }
            
            // Extract distance from clean interface result
            val data = throwResult.getOrThrow()
            val distance = data["throwDistance"] as? Double
            val isValid = distance != null && distance > 0.0
            
            // Get wind reading if available
            val windReading = try {
                val windResult = edmModule.measureWind()
                // Extract wind speed from wind reading
                windResult.windSpeed
            } catch (e: Exception) {
                Log.w(TAG, "Wind measurement not available: ${e.message}")
                null
            }
            
            // Generate coordinates for heatmap using clean interface data
            val coordinates = if (distance != null) {
                val throwCoords = data["throwCoordinates"] as? Map<String, Double>
                if (throwCoords != null) {
                    ThrowCoordinate(
                        x = throwCoords["x"] ?: 0.0,
                        y = throwCoords["y"] ?: 0.0,
                        distance = distance,
                        round = round,
                        attemptNumber = attemptNumber,
                        isValid = isValid
                    )
                } else {
                    generateThrowCoordinates(distance, round, attemptNumber)
                }
            } else null
            
            val result = MeasurementResult(
                athleteBib = athleteBib,
                round = round,
                attemptNumber = attemptNumber,
                distance = distance,
                windSpeed = windReading,
                isValid = isValid,
                coordinates = coordinates,
                rawEDMReading = null // Store raw data if needed
            )
            
            _measurementState.value = _measurementState.value.copy(
                currentMeasurement = result,
                lastWindReading = windReading
            )
            
            Log.d(TAG, "Real measurement taken: ${result.distance?.let { "%.2f m".format(it) } ?: "FOUL"}")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error taking real measurement: ${e.message}")
            null
        }
    }
    
    /**
     * Take demo measurement with simulated values
     */
    private fun takeDemoMeasurement(
        athleteBib: String, 
        round: Int, 
        attemptNumber: Int
    ): MeasurementResult {
        // Generate realistic demo values
        val baseDistance = 12.0 + (kotlin.random.Random.nextDouble() * 8.0) // 12-20m range
        val isValidThrow = kotlin.random.Random.nextDouble() > 0.15 // 85% valid throws
        val distance = if (isValidThrow) baseDistance else null
        
        val windSpeed = -2.0 + (kotlin.random.Random.nextDouble() * 4.0) // -2 to +2 m/s
        
        val coordinates = if (distance != null) {
            generateDemoCoordinates(distance, round, attemptNumber)
        } else null
        
        val result = MeasurementResult(
            athleteBib = athleteBib,
            round = round,
            attemptNumber = attemptNumber,
            distance = distance,
            windSpeed = windSpeed,
            isValid = isValidThrow,
            coordinates = coordinates
        )
        
        _measurementState.value = _measurementState.value.copy(
            currentMeasurement = result,
            lastWindReading = windSpeed
        )
        
        Log.d(TAG, "Demo measurement taken: ${result.distance?.let { "%.2f m".format(it) } ?: "FOUL"}")
        return result
    }
    
    /**
     * Generate simplified throw coordinates for heatmap visualization
     */
    private fun generateThrowCoordinates(
        distance: Double,
        round: Int,
        attemptNumber: Int
    ): ThrowCoordinate {
        // Generate realistic coordinates based on distance
        // In a real implementation, this would come from the EDM calculation results
        val angle = kotlin.random.Random.nextDouble() * 2 * PI
        val x = distance * cos(angle)
        val y = distance * sin(angle)
        
        return ThrowCoordinate(
            x = x,
            y = y,
            distance = distance,
            round = round,
            attemptNumber = attemptNumber,
            isValid = true
        )
    }
    
    /**
     * Generate demo coordinates for testing
     */
    private fun generateDemoCoordinates(
        distance: Double, 
        round: Int, 
        attemptNumber: Int
    ): ThrowCoordinate {
        val angle = kotlin.random.Random.nextDouble() * 2 * PI
        val x = distance * cos(angle)
        val y = distance * sin(angle)
        
        return ThrowCoordinate(
            x = x,
            y = y,
            distance = distance,
            round = round,
            attemptNumber = attemptNumber,
            isValid = true
        )
    }
    
    /**
     * Record measurement result
     */
    private fun recordMeasurement(result: MeasurementResult) {
        viewModelScope.launch {
            try {
                // Add to measurement history
                val updatedHistory = _measurementState.value.measurementHistory.toMutableList()
                updatedHistory.add(result)
                
                _measurementState.value = _measurementState.value.copy(
                    measurementHistory = updatedHistory
                )
                
                // Record attempt in athlete manager
                athleteManager.recordAttempt(
                    athleteBib = result.athleteBib,
                    round = result.round,
                    attemptNumber = result.attemptNumber,
                    distance = result.distance,
                    isValid = result.isValid,
                    windSpeed = result.windSpeed,
                    coordinates = result.coordinates
                )
                
                // Record attempt in competition manager
                competitionManager.recordAttempt(result.athleteBib, result.attemptNumber)
                
                // Submit to server if in connected mode
                if (modeManager.isConnectedMode() && competitionManager.competitionState.value.selectedEvent != null) {
                    submitResultToServer(result)
                }
                
                Log.d(TAG, "Measurement recorded for athlete ${result.athleteBib}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error recording measurement: ${e.message}")
                _measurementState.value = _measurementState.value.copy(
                    errorMessage = "Failed to record measurement: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Submit result to server (Connected mode)
     */
    private suspend fun submitResultToServer(result: MeasurementResult) {
        try {
            val event = competitionManager.competitionState.value.selectedEvent ?: return
            val athlete = athleteManager.getAthleteByBib(result.athleteBib) ?: return
            
            // Get all attempts for this athlete to build series
            val allAttempts = athlete.getCurrentRoundAttempts(result.round)
            val series = allAttempts.map { attempt ->
                PolyFieldApiClient.Performance(
                    attempt = attempt.attemptNumber,
                    mark = attempt.distance?.let { "%.2f".format(it) } ?: "FOUL",
                    unit = "m",
                    wind = attempt.windSpeed?.let { "%.1f".format(it) },
                    valid = attempt.isValid
                )
            }
            
            val payload = PolyFieldApiClient.ResultPayload(
                eventId = event.id,
                athleteBib = result.athleteBib,
                series = series
            )
            
            val success = modeManager.submitResult(payload)
            if (success) {
                Log.d(TAG, "Result submitted to server for athlete ${result.athleteBib}")
            } else {
                Log.w(TAG, "Failed to submit result to server, cached for retry")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting result to server: ${e.message}")
        }
    }
    
    /**
     * Mark current measurement as foul
     */
    fun markAsFoul() {
        val currentAthlete = athleteManager.getCurrentAthlete() ?: return
        val competitionState = competitionManager.competitionState.value
        
        if (competitionState.isActive) {
            val currentRound = competitionState.currentRound
            val attemptNumber = currentAthlete.getAttemptCount(currentRound) + 1
            
            val foulResult = MeasurementResult(
                athleteBib = currentAthlete.bib,
                round = currentRound,
                attemptNumber = attemptNumber,
                distance = null,
                isValid = false
            )
            
            recordMeasurement(foulResult)
            Log.d(TAG, "Marked as foul for athlete ${currentAthlete.bib}")
        }
    }
    
    /**
     * Set demo mode
     */
    fun setDemoMode(enabled: Boolean) {
        _measurementState.value = _measurementState.value.copy(
            isDemoMode = enabled
        )
        
        Log.d(TAG, "Demo mode ${if (enabled) "enabled" else "disabled"}")
        // Note: Demo mode handling is managed at the measurement level
        // EDM module doesn't have a setDemoMode method - we handle simulation here
    }
    
    /**
     * Update device connection status
     */
    fun updateDeviceConnectionStatus(edmConnected: Boolean, windConnected: Boolean) {
        _measurementState.value = _measurementState.value.copy(
            isConnectedToEDM = edmConnected,
            isConnectedToWind = windConnected
        )
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _measurementState.value = _measurementState.value.copy(errorMessage = null)
    }
    
    // Missing functions that are called from UI components
    
    /**
     * Start competition with athlete
     */
    fun startCompetitionWithAthlete(athlete: PolyFieldApiClient.Athlete) {
        viewModelScope.launch {
            athleteManager.selectAthlete(athlete)
            startMeasurement()
            Log.d(TAG, "Started competition with athlete ${athlete.name}")
        }
    }
    
    /**
     * Start competition
     */
    fun startCompetition() {
        viewModelScope.launch {
            startMeasurement()
            Log.d(TAG, "Started competition")
        }
    }
    
    /**
     * End competition
     */
    fun endCompetition() {
        stopMeasurement()
        competitionManager.endCompetition()
        Log.d(TAG, "Competition ended")
    }
    
    /**
     * Take measurement for current athlete
     */
    suspend fun takeMeasurement(athleteBib: String) {
        measureThrow()
    }
    
    /**
     * Record measurement result
     */
    fun recordResult(athleteBib: String, round: Int, attemptNumber: Int, distance: Double?, isValid: Boolean) {
        val result = MeasurementResult(
            athleteBib = athleteBib,
            round = round,
            attemptNumber = attemptNumber,
            distance = distance,
            isValid = isValid
        )
        recordMeasurement(result)
    }
    
    /**
     * Advance to next athlete in rotation
     */
    fun advanceToNextAthlete() {
        athleteManager.nextAthlete()
        competitionManager.nextAthlete()
    }
    
    /**
     * Get best mark for specific athlete
     */
    fun getBestMarkForAthlete(athleteBib: String): Double? {
        val athlete = athleteManager.getAthleteByBib(athleteBib)
        return athlete?.getBestMark()
    }
    
    /**
     * Get formatted best mark for specific athlete
     */
    fun getBestMarkFormattedForAthlete(athleteBib: String): String? {
        val athlete = athleteManager.getAthleteByBib(athleteBib)
        return athlete?.getBestMark()?.let { "%.2f m".format(it) }
    }
    
    /**
     * Get ranking for specific athlete
     */
    fun getRankingForAthlete(athleteBib: String): Int {
        val rankings = athleteManager.getAthletesByRanking()
        val ranking = rankings.find { it.first.bib == athleteBib }?.second ?: 0
        return ranking
    }
    
    // Additional missing state properties for UI
    val currentRound: Int
        get() = competitionManager.getCurrentRound()
    
    val currentAttemptNumber: Int  
        get() = 1 // Simplified for now
    
    val isLoading: Boolean
        get() = false // Simplified for now
    
    // Convenience getters
    fun getCurrentMeasurement(): MeasurementResult? = _measurementState.value.currentMeasurement
    fun getLastDistance(): String? = _measurementState.value.currentMeasurement?.distance?.let { "%.2f m".format(it) }
    fun getLastWindSpeed(): String? = _measurementState.value.lastWindReading?.let { "%.1f m/s".format(it) }
    fun isEDMConnected(): Boolean = _measurementState.value.isConnectedToEDM
    fun isWindConnected(): Boolean = _measurementState.value.isConnectedToWind
    fun isDemoMode(): Boolean = _measurementState.value.isDemoMode
}

/**
 * Factory for creating CompetitionMeasurementManager
 */
class CompetitionMeasurementManagerFactory(
    private val context: Context,
    private val edmModule: EDMModule,
    private val athleteManager: AthleteManagerViewModel,
    private val competitionManager: CompetitionManagerViewModel,
    private val modeManager: ModeManagerViewModel
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CompetitionMeasurementManager::class.java)) {
            return CompetitionMeasurementManager(
                context, edmModule, athleteManager, competitionManager, modeManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}