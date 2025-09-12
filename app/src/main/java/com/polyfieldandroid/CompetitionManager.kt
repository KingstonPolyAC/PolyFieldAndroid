package com.polyfieldandroid

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Competition Setup and Management
 * Handles competition configuration, rules, and state management
 */
data class CompetitionSettings(
    val numberOfRounds: Int = 3,
    val athleteCutoff: Int = 8, // Number advancing, or -1 for ALL
    val cutoffEnabled: Boolean = true,
    val reorderAfterRound3: Boolean = true,
    val eventType: String = "SHOT", // SHOT, DISCUS, HAMMER, JAVELIN_ARC
    val allowAllAthletes: Boolean = false
) {
    fun getCutoffDisplay(): String = if (allowAllAthletes) "ALL" else athleteCutoff.toString()
}

data class CompetitionState(
    val isActive: Boolean = false,
    val currentRound: Int = 1,
    val currentAthleteIndex: Int = 0,
    val settings: CompetitionSettings = CompetitionSettings(),
    val selectedEvent: PolyFieldApiClient.Event? = null,
    val totalAthletes: Int = 0,
    val completedAttempts: Map<String, Int> = emptyMap(), // athleteBib -> completed attempts
    val isCalibrated: Boolean = false,
    val roundComplete: Boolean = false,
    val competitionComplete: Boolean = false
)

/**
 * ViewModel for managing competition setup and state
 */
class CompetitionManagerViewModel(private val context: Context) : ViewModel() {
    
    companion object {
        private const val TAG = "CompetitionManager"
        private const val PREFS_NAME = "polyfield_competition_prefs"
        private const val PREF_SETTINGS = "competition_settings"
    }
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // State management
    private val _competitionState = MutableStateFlow(CompetitionState())
    val competitionState: StateFlow<CompetitionState> = _competitionState.asStateFlow()
    
    // Available round options (1-6)
    val roundOptions = (1..6).toList()
    
    // Available cutoff options (3, 4, 5, 6, 8, 9, 10, 12, ALL)
    val cutoffOptions = listOf(3, 4, 5, 6, 8, 9, 10, 12)
    
    init {
        loadSavedSettings()
    }
    
    /**
     * Load previously saved competition settings
     */
    private fun loadSavedSettings() {
        try {
            val savedSettingsJson = preferences.getString(PREF_SETTINGS, null)
            val settings = if (savedSettingsJson != null) {
                gson.fromJson(savedSettingsJson, CompetitionSettings::class.java)
            } else {
                CompetitionSettings()
            }
            
            _competitionState.value = _competitionState.value.copy(
                settings = settings
            )
            
            Log.d(TAG, "Loaded competition settings: $settings")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading settings: ${e.message}")
            _competitionState.value = _competitionState.value.copy(
                settings = CompetitionSettings()
            )
        }
    }
    
    /**
     * Update competition settings
     */
    fun updateSettings(settings: CompetitionSettings) {
        viewModelScope.launch {
            _competitionState.value = _competitionState.value.copy(
                settings = settings
            )
            
            saveSettings(settings)
            Log.d(TAG, "Updated competition settings: $settings")
        }
    }
    
    /**
     * Set number of rounds (1-6)
     */
    fun setNumberOfRounds(rounds: Int) {
        val currentSettings = _competitionState.value.settings
        val newSettings = currentSettings.copy(numberOfRounds = rounds.coerceIn(1, 6))
        updateSettings(newSettings)
    }
    
    /**
     * Set athlete cutoff number
     */
    fun setAthleteCutoff(cutoff: Int, allowAll: Boolean = false) {
        val currentSettings = _competitionState.value.settings
        val newSettings = currentSettings.copy(
            athleteCutoff = if (allowAll) -1 else cutoff,
            allowAllAthletes = allowAll,
            cutoffEnabled = !allowAll
        )
        updateSettings(newSettings)
    }
    
    /**
     * Toggle reorder after round 3 setting
     */
    fun setReorderAfterRound3(enabled: Boolean) {
        val currentSettings = _competitionState.value.settings
        val newSettings = currentSettings.copy(reorderAfterRound3 = enabled)
        updateSettings(newSettings)
    }
    
    /**
     * Set event type (circle type for calibration)
     */
    fun setEventType(eventType: String) {
        val currentSettings = _competitionState.value.settings
        val newSettings = currentSettings.copy(eventType = eventType)
        updateSettings(newSettings)
    }
    
    fun selectEvent(event: PolyFieldApiClient.Event) {
        _competitionState.value = _competitionState.value.copy(
            selectedEvent = event
        )
        Log.d(TAG, "Selected event: ${event.name}")
    }
    
    /**
     * Start competition with current settings
     */
    fun startCompetition(selectedEvent: PolyFieldApiClient.Event? = null, totalAthletes: Int) {
        viewModelScope.launch {
            val settings = _competitionState.value.settings
            
            // Validate settings
            val effectiveCutoff = if (settings.allowAllAthletes || settings.athleteCutoff > totalAthletes) {
                totalAthletes
            } else {
                settings.athleteCutoff
            }
            
            _competitionState.value = _competitionState.value.copy(
                isActive = true,
                currentRound = 1,
                currentAthleteIndex = 0,
                selectedEvent = selectedEvent,
                totalAthletes = effectiveCutoff,
                completedAttempts = emptyMap(),
                roundComplete = false,
                competitionComplete = false,
                settings = settings.copy(
                    athleteCutoff = effectiveCutoff,
                    allowAllAthletes = effectiveCutoff == totalAthletes
                )
            )
            
            Log.d(TAG, "Started competition: rounds=${settings.numberOfRounds}, athletes=$effectiveCutoff")
        }
    }
    
    /**
     * Mark calibration as complete
     */
    fun setCalibrationComplete(isComplete: Boolean) {
        _competitionState.value = _competitionState.value.copy(
            isCalibrated = isComplete
        )
    }
    
    /**
     * Record completion of an attempt for an athlete
     */
    fun recordAttempt(athleteBib: String, attemptNumber: Int) {
        viewModelScope.launch {
            val currentAttempts = _competitionState.value.completedAttempts.toMutableMap()
            currentAttempts[athleteBib] = attemptNumber
            
            _competitionState.value = _competitionState.value.copy(
                completedAttempts = currentAttempts
            )
            
            checkRoundCompletion()
        }
    }
    
    /**
     * Move to next athlete in rotation
     */
    fun nextAthlete() {
        val state = _competitionState.value
        if (!state.isActive) return
        
        val nextIndex = (state.currentAthleteIndex + 1) % state.totalAthletes
        
        _competitionState.value = state.copy(
            currentAthleteIndex = nextIndex
        )
    }
    
    /**
     * Move to previous athlete in rotation
     */
    fun previousAthlete() {
        val state = _competitionState.value
        if (!state.isActive) return
        
        val previousIndex = if (state.currentAthleteIndex > 0) {
            state.currentAthleteIndex - 1
        } else {
            state.totalAthletes - 1
        }
        
        _competitionState.value = state.copy(
            currentAthleteIndex = previousIndex
        )
    }
    
    /**
     * Advance to next round
     */
    fun nextRound() {
        viewModelScope.launch {
            val state = _competitionState.value
            if (!state.isActive || !state.roundComplete) return@launch
            
            val nextRound = state.currentRound + 1
            
            if (nextRound > state.settings.numberOfRounds) {
                // Competition complete
                _competitionState.value = state.copy(
                    competitionComplete = true,
                    roundComplete = false
                )
                Log.d(TAG, "Competition completed")
            } else {
                // Move to next round
                _competitionState.value = state.copy(
                    currentRound = nextRound,
                    currentAthleteIndex = 0,
                    roundComplete = false,
                    completedAttempts = emptyMap() // Reset attempts for new round
                )
                
                Log.d(TAG, "Advanced to round $nextRound")
                
                // Check if we need to reorder after round 3
                if (nextRound == 4 && state.settings.reorderAfterRound3) {
                    // Implementation would trigger athlete reordering
                    Log.d(TAG, "Reordering athletes after round 3")
                }
            }
        }
    }
    
    /**
     * Check if current round is complete
     */
    private fun checkRoundCompletion() {
        val state = _competitionState.value
        val completedCount = state.completedAttempts.size
        
        val isRoundComplete = completedCount >= state.totalAthletes
        
        if (isRoundComplete && !state.roundComplete) {
            _competitionState.value = state.copy(
                roundComplete = true
            )
            Log.d(TAG, "Round ${state.currentRound} completed")
        }
    }
    
    /**
     * Reset/end competition
     */
    fun endCompetition() {
        _competitionState.value = CompetitionState(
            settings = _competitionState.value.settings // Preserve settings
        )
        Log.d(TAG, "Competition ended")
    }
    
    /**
     * Get remaining athletes count for cutoff
     */
    fun getRemainingAthletesCount(): Int {
        val state = _competitionState.value
        return if (state.settings.allowAllAthletes) {
            state.totalAthletes
        } else {
            minOf(state.settings.athleteCutoff, state.totalAthletes)
        }
    }
    
    /**
     * Check if athlete should advance to next round
     */
    fun shouldAthleteAdvance(athleteRanking: Int): Boolean {
        val state = _competitionState.value
        return if (state.settings.allowAllAthletes) {
            true
        } else {
            athleteRanking <= state.settings.athleteCutoff
        }
    }
    
    /**
     * Get current competition progress
     */
    fun getCompetitionProgress(): String {
        val state = _competitionState.value
        return if (state.isActive) {
            "Round ${state.currentRound}/${state.settings.numberOfRounds}"
        } else {
            "Not Started"
        }
    }
    
    /**
     * Save settings to preferences
     */
    private fun saveSettings(settings: CompetitionSettings) {
        try {
            val settingsJson = gson.toJson(settings)
            preferences.edit()
                .putString(PREF_SETTINGS, settingsJson)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving settings: ${e.message}")
        }
    }
    
    // Convenience getters
    fun isCompetitionActive(): Boolean = _competitionState.value.isActive
    fun isCompetitionCalibrated(): Boolean = _competitionState.value.isCalibrated
    fun getCurrentRound(): Int = _competitionState.value.currentRound
    fun getCurrentAthleteIndex(): Int = _competitionState.value.currentAthleteIndex
    fun getSettings(): CompetitionSettings = _competitionState.value.settings
}

/**
 * Factory for creating CompetitionManagerViewModel with Context
 */
class CompetitionManagerViewModelFactory(private val context: Context) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CompetitionManagerViewModel::class.java)) {
            return CompetitionManagerViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}