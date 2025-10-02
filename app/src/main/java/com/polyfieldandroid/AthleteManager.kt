package com.polyfieldandroid

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Enhanced Athlete data model for competition management
 */
data class CompetitionAthlete(
    val bib: String,
    val order: Int,
    val name: String,
    val club: String,
    val isSelected: Boolean = true, // Whether athlete is selected for competition
    val currentBestMark: Double? = null, // Best mark in meters
    val attempts: MutableList<AthleteAttempt> = mutableListOf(),
    val heatmapData: MutableList<ThrowCoordinate> = mutableListOf(), // Individual heatmap data
    val isAdvancing: Boolean = false // Whether athlete advances to next round
) {
    fun getDisplayName(): String = "$bib - $name ($club)"
    
    fun getCurrentRoundMeasurements(round: Int): List<AthleteAttempt> {
        return attempts.filter { it.round == round }
    }
    
    fun getValidMeasurements(): List<AthleteAttempt> {
        return attempts.filter { it.isValid && it.distance != null }
    }
    
    fun getBestMark(): Double? {
        return getValidMeasurements().maxByOrNull { it.distance ?: 0.0 }?.distance
    }
    
    fun getMeasurementCount(round: Int): Int {
        return getCurrentRoundMeasurements(round).size
    }
}

/**
 * Individual measurement data for a round
 */
data class AthleteAttempt(
    val id: String = java.util.UUID.randomUUID().toString(),
    val round: Int,
    val attemptNumber: Int = 1, // Always 1 for throws/horizontal jumps
    val distance: Double? = null, // Distance in meters
    val windSpeed: Double? = null, // Wind speed in m/s
    val isValid: Boolean = true,
    val isPass: Boolean = false, // Whether this is a pass (valid but no distance)
    val timestamp: Long = System.currentTimeMillis(),
    val coordinates: ThrowCoordinate? = null // Landing coordinates for heatmap
) {
    fun getDisplayMark(): String = when {
        !isValid -> "X"
        isPass -> "P"
        distance != null -> String.format("%.2f m", distance)
        else -> "—"
    }
}

// ThrowCoordinate moved to shared data classes

/**
 * Athlete management state
 */
data class AthleteManagementState(
    val athletes: List<CompetitionAthlete> = emptyList(),
    val selectedAthletes: List<CompetitionAthlete> = emptyList(),
    val checkedInAthletes: Set<String> = emptySet(), // Set of bib numbers
    val currentAthleteIndex: Int = 0,
    val currentAthlete: CompetitionAthlete? = null,
    val rotationOrder: List<CompetitionAthlete> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel for managing athletes in competition
 */
class AthleteManagerViewModel(private val context: Context) : ViewModel() {
    
    companion object {
        private const val TAG = "AthleteManager"
        private const val PREFS_NAME = "polyfield_athlete_prefs"
        private const val PREF_MANUAL_ATHLETES = "manual_athletes"
        private const val PREF_ATHLETE_RESULTS = "athlete_results"
    }
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // State management
    private val _athleteState = MutableStateFlow(AthleteManagementState())
    val athleteState: StateFlow<AthleteManagementState> = _athleteState.asStateFlow()
    
    init {
        loadSavedAthletes()
    }
    
    /**
     * Load athletes from server event data
     */
    fun loadAthletesFromEvent(event: PolyFieldApiClient.Event) {
        viewModelScope.launch {
            try {
                _athleteState.value = _athleteState.value.copy(isLoading = true)
                
                val athletes = event.athletes?.mapIndexed { index, serverAthlete ->
                    CompetitionAthlete(
                        bib = serverAthlete.bib,
                        order = serverAthlete.order,
                        name = serverAthlete.name,
                        club = serverAthlete.club,
                        isSelected = true // All athletes selected by default
                    )
                }?.sortedBy { it.order } ?: emptyList()
                
                _athleteState.value = _athleteState.value.copy(
                    athletes = athletes,
                    selectedAthletes = athletes.filter { it.isSelected },
                    rotationOrder = athletes.filter { it.isSelected },
                    isLoading = false,
                    errorMessage = null
                )
                
                Log.d(TAG, "Loaded ${athletes.size} athletes from server event")
                
            } catch (e: Exception) {
                _athleteState.value = _athleteState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load athletes: ${e.message}"
                )
                Log.e(TAG, "Error loading athletes from event: ${e.message}")
            }
        }
    }
    
    /**
     * Load manually entered athletes (stand-alone mode)
     */
    fun loadManualAthletes() {
        try {
            val savedAthletesJson = preferences.getString(PREF_MANUAL_ATHLETES, null)
            if (savedAthletesJson != null) {
                val listType = object : TypeToken<List<CompetitionAthlete>>() {}.type
                val athletes = gson.fromJson<List<CompetitionAthlete>>(savedAthletesJson, listType)
                
                _athleteState.value = _athleteState.value.copy(
                    athletes = athletes,
                    selectedAthletes = athletes.filter { it.isSelected },
                    rotationOrder = athletes.filter { it.isSelected }.sortedBy { it.order }
                )
                
                Log.d(TAG, "Loaded ${athletes.size} manual athletes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading manual athletes: ${e.message}")
        }
    }
    
    /**
     * Add manual athlete (stand-alone mode)
     */
    fun addManualAthlete(bib: String, name: String, club: String = "") {
        viewModelScope.launch {
            val currentAthletes = _athleteState.value.athletes.toMutableList()
            val nextOrder = (currentAthletes.maxByOrNull { it.order }?.order ?: 0) + 1
            
            val newAthlete = CompetitionAthlete(
                bib = bib,
                order = nextOrder,
                name = name,
                club = club,
                isSelected = true
            )
            
            currentAthletes.add(newAthlete)
            val selectedAthletes = currentAthletes.filter { it.isSelected }
            
            _athleteState.value = _athleteState.value.copy(
                athletes = currentAthletes,
                selectedAthletes = selectedAthletes,
                rotationOrder = selectedAthletes.sortedBy { it.order }
            )
            
            saveManualAthletes(currentAthletes)
            Log.d(TAG, "Added manual athlete: $bib - $name")
        }
    }
    
    /**
     * Remove athlete
     */
    fun removeAthlete(bib: String) {
        viewModelScope.launch {
            val currentAthletes = _athleteState.value.athletes.toMutableList()
            currentAthletes.removeAll { it.bib == bib }
            
            val selectedAthletes = currentAthletes.filter { it.isSelected }
            
            _athleteState.value = _athleteState.value.copy(
                athletes = currentAthletes,
                selectedAthletes = selectedAthletes,
                rotationOrder = selectedAthletes.sortedBy { it.order }
            )
            
            saveManualAthletes(currentAthletes)
            Log.d(TAG, "Removed athlete: $bib")
        }
    }
    
    /**
     * Toggle athlete selection for competition
     */
    fun toggleAthleteSelection(bib: String) {
        viewModelScope.launch {
            val currentAthletes = _athleteState.value.athletes.toMutableList()
            val athleteIndex = currentAthletes.indexOfFirst { it.bib == bib }
            
            if (athleteIndex != -1) {
                val athlete = currentAthletes[athleteIndex]
                currentAthletes[athleteIndex] = athlete.copy(isSelected = !athlete.isSelected)
                
                val selectedAthletes = currentAthletes.filter { it.isSelected }
                
                _athleteState.value = _athleteState.value.copy(
                    athletes = currentAthletes,
                    selectedAthletes = selectedAthletes,
                    rotationOrder = selectedAthletes.sortedBy { it.order }
                )
                
                Log.d(TAG, "Toggled selection for athlete $bib: ${athlete.isSelected}")
            }
        }
    }
    
    /**
     * Record measurement for athlete
     */
    fun recordMeasurement(
        athleteBib: String, 
        round: Int, 
        attemptNumber: Int, 
        distance: Double?, 
        isValid: Boolean,
        isPass: Boolean = false,
        windSpeed: Double? = null,
        coordinates: ThrowCoordinate? = null
    ) {
        viewModelScope.launch {
            val currentAthletes = _athleteState.value.athletes.toMutableList()
            val athleteIndex = currentAthletes.indexOfFirst { it.bib == athleteBib }
            
            if (athleteIndex != -1) {
                val athlete = currentAthletes[athleteIndex]
                
                val measurement = AthleteAttempt(
                    round = round,
                    attemptNumber = 1, // Always 1 for throws/horizontal jumps
                    distance = distance,
                    windSpeed = windSpeed,
                    isValid = isValid,
                    isPass = isPass,
                    coordinates = coordinates
                )
                
                // Replace existing round measurement or add new one
                val updatedMeasurements = athlete.attempts.toMutableList()
                val existingIndex = updatedMeasurements.indexOfFirst { it.round == round }
                
                if (existingIndex >= 0) {
                    // Replace existing measurement for this round
                    updatedMeasurements[existingIndex] = measurement
                } else {
                    // Add new measurement for this round
                    updatedMeasurements.add(measurement)
                }
                
                // Update heatmap data - remove old coordinates for this round/attempt first
                val updatedHeatmapData = athlete.heatmapData.toMutableList()
                updatedHeatmapData.removeAll { it.round == round && it.attemptNumber == attemptNumber }
                // Add coordinates for ALL attempts (including fouls and passes)
                if (coordinates != null) {
                    updatedHeatmapData.add(coordinates)
                }
                
                // Create updated athlete with new data
                val updatedAthlete = athlete.copy(
                    attempts = updatedMeasurements,
                    heatmapData = updatedHeatmapData,
                    currentBestMark = updatedMeasurements.filter { it.isValid && it.distance != null }
                        .maxByOrNull { it.distance ?: 0.0 }?.distance
                )
                currentAthletes[athleteIndex] = updatedAthlete
                
                _athleteState.value = _athleteState.value.copy(
                    athletes = currentAthletes,
                    selectedAthletes = currentAthletes.filter { it.isSelected },
                    rotationOrder = currentAthletes.filter { it.isSelected }.sortedBy { it.order }
                )
                
                saveAthleteResults()
                Log.d(TAG, "Recorded measurement for athlete $athleteBib: ${measurement.getDisplayMark()}")
            }
        }
    }
    
    /**
     * Navigate to next athlete in rotation
     */
    fun nextAthlete() {
        val currentState = _athleteState.value
        val checkedInAthletes = currentState.athletes.filter { it.bib in currentState.checkedInAthletes }

        if (checkedInAthletes.isNotEmpty()) {
            // COMPETITION MODE: Use checked-in athletes rotation
            Log.d(TAG, "🔴🔴🔴 NEXT BUTTON CLICKED - Competition mode - Total checked-in: ${checkedInAthletes.size}")

            // Find current athlete in checked-in list
            val currentAthlete = currentState.rotationOrder.getOrNull(currentState.currentAthleteIndex)
            val currentCheckedInIndex = checkedInAthletes.indexOfFirst { it.bib == currentAthlete?.bib }

            // Move to next checked-in athlete
            val nextCheckedInIndex = if (currentCheckedInIndex >= 0) {
                (currentCheckedInIndex + 1) % checkedInAthletes.size
            } else {
                0 // Default to first checked-in athlete if current not found
            }

            val nextAthlete = checkedInAthletes[nextCheckedInIndex]

            // Find this athlete's position in rotation order
            val nextIndex = currentState.rotationOrder.indexOfFirst { it.bib == nextAthlete.bib }

            if (nextIndex >= 0) {
                _athleteState.value = currentState.copy(
                    currentAthleteIndex = nextIndex
                )

                Log.d(TAG, "🔴🔴🔴 COMPETITION MODE ADVANCED from ${currentAthlete?.name ?: "unknown"} to ${nextAthlete.name} (index $nextIndex)")
            }
        } else {
            // DIRECT MODE: Use original rotation order logic for direct athlete selection
            Log.d(TAG, "🔴🔴🔴 NEXT BUTTON CLICKED - Direct mode - Current index: ${currentState.currentAthleteIndex}, Total athletes: ${currentState.rotationOrder.size}")

            val nextIndex = (currentState.currentAthleteIndex + 1) % currentState.rotationOrder.size

            _athleteState.value = currentState.copy(
                currentAthleteIndex = nextIndex
            )

            Log.d(TAG, "🔴🔴🔴 DIRECT MODE ADVANCED from index ${currentState.currentAthleteIndex} to index $nextIndex")
        }
    }
    
    /**
     * Navigate to previous athlete in rotation
     */
    fun previousAthlete() {
        val currentState = _athleteState.value
        val previousIndex = if (currentState.currentAthleteIndex > 0) {
            currentState.currentAthleteIndex - 1
        } else {
            currentState.rotationOrder.size - 1
        }
        
        _athleteState.value = currentState.copy(
            currentAthleteIndex = previousIndex
        )
    }
    
    /**
     * Set current athlete by bib number
     */
    fun setCurrentAthlete(bib: String) {
        val currentState = _athleteState.value
        val athleteIndex = currentState.rotationOrder.indexOfFirst { it.bib == bib }
        
        if (athleteIndex != -1) {
            _athleteState.value = currentState.copy(
                currentAthleteIndex = athleteIndex
            )
        }
    }
    
    /**
     * Reorder athletes by performance (for post-round-3 reordering)
     * WA/UKA Rules: Best performers throw last (reverse order)
     */
    fun reorderAthletesByPerformance(reverseOrder: Boolean = true) {
        viewModelScope.launch {
            val currentState = _athleteState.value
            val reorderedAthletes = if (reverseOrder) {
                // WA/UKA Rules: Best performers throw last (worst to best)
                currentState.selectedAthletes.sortedBy { 
                    it.getBestMark() ?: 0.0 
                }
            } else {
                // Traditional: Best performers throw first (best to worst)
                currentState.selectedAthletes.sortedByDescending { 
                    it.getBestMark() ?: 0.0 
                }
            }
            
            _athleteState.value = currentState.copy(
                rotationOrder = reorderedAthletes
            )
            
            Log.d(TAG, "Reordered ${reorderedAthletes.size} athletes by performance (reverse: $reverseOrder)")
        }
    }
    
    /**
     * Apply cutoff and reordering after Round 3
     */
    fun applyCutoffAndReordering(cutoff: Int, enableReordering: Boolean) {
        viewModelScope.launch {
            val currentState = _athleteState.value
            
            // Apply cutoff first
            val cutoffAthletes = if (cutoff == -1) {
                // ALL athletes advance
                currentState.selectedAthletes
            } else {
                // Top N athletes advance
                currentState.selectedAthletes
                    .sortedByDescending { it.getBestMark() ?: 0.0 }
                    .take(cutoff)
            }
            
            // Apply reordering if enabled
            val finalOrder = if (enableReordering) {
                // WA/UKA Rules: Best performers throw last
                cutoffAthletes.sortedBy { it.getBestMark() ?: 0.0 }
            } else {
                // Keep current order
                cutoffAthletes.sortedBy { it.order }
            }
            
            _athleteState.value = currentState.copy(
                selectedAthletes = cutoffAthletes,
                rotationOrder = finalOrder,
                currentAthleteIndex = 0 // Reset to first athlete
            )
            
            Log.d(TAG, "Applied cutoff ($cutoff) and reordering ($enableReordering). ${finalOrder.size} athletes advancing")
        }
    }
    
    /**
     * Get current athlete
     */
    fun getCurrentAthlete(): CompetitionAthlete? {
        val state = _athleteState.value
        return if (state.rotationOrder.isNotEmpty() && state.currentAthleteIndex < state.rotationOrder.size) {
            state.rotationOrder[state.currentAthleteIndex]
        } else null
    }
    
    /**
     * Get athlete by bib number
     */
    fun getAthleteByBib(bib: String): CompetitionAthlete? {
        return _athleteState.value.athletes.find { it.bib == bib }
    }
    
    /**
     * Get athletes ranked by performance
     */
    fun getAthletesByRanking(): List<Pair<CompetitionAthlete, Int>> {
        return _athleteState.value.selectedAthletes
            .sortedByDescending { it.getBestMark() ?: 0.0 }
            .mapIndexed { index, athlete -> athlete to (index + 1) }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _athleteState.value = _athleteState.value.copy(errorMessage = null)
    }
    
    /**
     * Load saved athletes from preferences
     */
    private fun loadSavedAthletes() {
        loadManualAthletes()
    }
    
    /**
     * Save manual athletes to preferences
     */
    private fun saveManualAthletes(athletes: List<CompetitionAthlete>) {
        try {
            val athletesJson = gson.toJson(athletes)
            preferences.edit()
                .putString(PREF_MANUAL_ATHLETES, athletesJson)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving manual athletes: ${e.message}")
        }
    }
    
    /**
     * Save athlete results and attempts
     */
    private fun saveAthleteResults() {
        try {
            val resultsData = _athleteState.value.athletes.associate { athlete ->
                athlete.bib to mapOf(
                    "attempts" to athlete.attempts,
                    "heatmapData" to athlete.heatmapData,
                    "bestMark" to athlete.currentBestMark
                )
            }
            
            val resultsJson = gson.toJson(resultsData)
            preferences.edit()
                .putString(PREF_ATHLETE_RESULTS, resultsJson)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving athlete results: ${e.message}")
        }
    }
    
    /**
     * Toggle athlete check-in status
     */
    fun toggleAthleteCheckIn(bib: String) {
        val currentCheckedIn = _athleteState.value.checkedInAthletes.toMutableSet()
        if (currentCheckedIn.contains(bib)) {
            currentCheckedIn.remove(bib)
        } else {
            currentCheckedIn.add(bib)
        }
        
        _athleteState.value = _athleteState.value.copy(
            checkedInAthletes = currentCheckedIn.toSet()
        )
        
        Log.d(TAG, "Athlete $bib check-in toggled. Total checked in: ${currentCheckedIn.size}")
    }
    
    /**
     * Select an athlete for measurement
     */
    fun selectAthlete(athlete: PolyFieldApiClient.Athlete) {
        // Convert server athlete to competition athlete if needed
        val existingAthlete = _athleteState.value.athletes.find { it.bib == athlete.bib }
        val competitionAthlete = existingAthlete ?: CompetitionAthlete(
            bib = athlete.bib,
            order = athlete.order,
            name = athlete.name,
            club = athlete.club,
            isSelected = true
        )

        // Add athlete to athletes list if not already present
        val updatedAthletes = if (existingAthlete == null) {
            _athleteState.value.athletes.toMutableList().apply { add(competitionAthlete) }
        } else {
            _athleteState.value.athletes
        }

        // Find the athlete's index in the rotation order
        val rotationIndex = _athleteState.value.rotationOrder.indexOfFirst { it.bib == athlete.bib }

        _athleteState.value = _athleteState.value.copy(
            athletes = updatedAthletes,
            currentAthlete = competitionAthlete,
            currentAthleteIndex = if (rotationIndex >= 0) rotationIndex else 0,
            selectedAthletes = updatedAthletes.filter { it.isSelected },
            rotationOrder = updatedAthletes.filter { it.isSelected }.sortedBy { it.order }
        )

        Log.d(TAG, "Selected athlete: ${athlete.name} (${athlete.bib}) at rotation index $rotationIndex. Total athletes: ${updatedAthletes.size}")
    }

    /**
     * Navigate to next checked-in athlete (for competition mode)
     * This mimics the working green button logic from CompetitionFlowScreens
     */
    fun nextCheckedInAthlete() {
        val currentState = _athleteState.value
        val checkedInAthletes = currentState.athletes.filter { it.bib in currentState.checkedInAthletes }

        if (checkedInAthletes.isEmpty()) {
            Log.w(TAG, "🔴🔴🔴 NEXT BUTTON CLICKED but no checked-in athletes!")
            return
        }

        // Find current athlete's position in checked-in list
        val currentCheckedInIndex = if (currentState.currentAthlete != null) {
            checkedInAthletes.indexOfFirst { it.bib == currentState.currentAthlete.bib }
        } else 0

        val oldIndex = if (currentCheckedInIndex >= 0) currentCheckedInIndex else 0
        val newIndex = (oldIndex + 1) % checkedInAthletes.size
        val nextAthlete = checkedInAthletes[newIndex]

        Log.d(TAG, "🔴🔴🔴 NEXT BUTTON CLICKED - Current index: $oldIndex, Total checked-in: ${checkedInAthletes.size}")
        Log.d(TAG, "🔴🔴🔴 ADVANCED from index $oldIndex (${checkedInAthletes.getOrNull(oldIndex)?.name ?: "none"}) to index $newIndex (${nextAthlete.name})")

        // Convert CompetitionAthlete to PolyFieldApiClient.Athlete and select
        val serverAthlete = PolyFieldApiClient.Athlete(
            bib = nextAthlete.bib,
            order = nextAthlete.order,
            name = nextAthlete.name,
            club = nextAthlete.club
        )
        selectAthlete(serverAthlete)
    }

    // Convenience getters
    fun getSelectedAthletes(): List<CompetitionAthlete> = _athleteState.value.selectedAthletes
    fun getTotalAthletes(): Int = _athleteState.value.athletes.size
    fun getSelectedAthleteCount(): Int = _athleteState.value.selectedAthletes.size
    fun getCurrentAthleteIndex(): Int = _athleteState.value.currentAthleteIndex
}

/**
 * Factory for creating AthleteManagerViewModel with Context
 */
class AthleteManagerViewModelFactory(private val context: Context) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AthleteManagerViewModel::class.java)) {
            return AthleteManagerViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}