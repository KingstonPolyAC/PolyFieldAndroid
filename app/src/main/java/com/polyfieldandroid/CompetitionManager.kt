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
    val numberOfRounds: Int = 6,
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
    val competitionComplete: Boolean = false,
    val useNextAthleteMode: Boolean = true, // Track if using Next Athlete buttons vs manual round selection
    val showRoundTransitionPopup: Boolean = false, // Show end-of-round popup
    val finalRoundSettings: FinalRoundSettings? = null, // Settings from Round 3 dialog
    val progressingAthletes: List<String> = emptyList(), // Bib numbers of athletes progressing to rounds 4-6
    val athleteCutState: AthleteCutState = AthleteCutState() // State for athlete cut and reordering
)

data class FinalRoundSettings(
    val athleteCount: Int, // 6, 8, 10, or -1 for ALL
    val reorderEnabled: Boolean // Whether to reorder worst-to-best for rounds 4-6
)

/**
 * Performance data for a specific round
 */
data class RoundData(
    val round: Int,
    val bestDistance: Double,
    val attempts: List<String> // e.g., ["12.34", "FOUL", "12.56"]
)

/**
 * State for athlete cut and reordering functionality
 */
data class AthleteCutState(
    val showCutPopup: Boolean = false,           // First popup: select advancing count
    val showProgressionPopup: Boolean = false,   // Second popup: show advancing/eliminated
    val showReorderPopup: Boolean = false,       // For rounds 4-5, 5-6 reorder only
    val selectedAthleteCount: Int = 8,           // Number advancing (4, 6, 8, 10, or -1 for ALL)
    val reorderEnabled: Boolean = false,         // Whether to reorder worst-to-best
    val athleteRankings: List<AthleteRanking> = emptyList(), // Current rankings
    val advancingAthletes: List<AthleteRanking> = emptyList(), // Athletes moving forward
    val eliminatedAthletes: List<AthleteRanking> = emptyList() // Athletes being cut
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
    
    /**
     * Update athlete cutoff (typically called at end of round 3)
     */
    fun updateAthleteCutoff(cutoff: Int) {
        viewModelScope.launch {
            val currentState = _competitionState.value
            val updatedSettings = currentState.settings.copy(
                athleteCutoff = cutoff,
                allowAllAthletes = cutoff == -1
            )
            
            _competitionState.value = currentState.copy(
                settings = updatedSettings
            )
            
            saveSettings(updatedSettings)
            Log.d(TAG, "Updated athlete cutoff to: ${if (cutoff == -1) "ALL" else cutoff}")
        }
    }
    
    /**
     * Update reordering setting for current round
     */
    fun updateReorderingSetting(enableReordering: Boolean) {
        viewModelScope.launch {
            val currentState = _competitionState.value
            val updatedSettings = currentState.settings.copy(
                reorderAfterRound3 = enableReordering
            )
            
            _competitionState.value = currentState.copy(
                settings = updatedSettings
            )
            
            saveSettings(updatedSettings)
            Log.d(TAG, "Updated reordering setting to: $enableReordering")
        }
    }
    
    /**
     * Advance to the next round
     */
    fun advanceToNextRound() {
        viewModelScope.launch {
            val currentState = _competitionState.value
            val nextRound = currentState.currentRound + 1
            
            if (nextRound <= currentState.settings.numberOfRounds) {
                _competitionState.value = currentState.copy(
                    currentRound = nextRound,
                    currentAthleteIndex = 0, // Reset to first athlete
                    roundComplete = false
                )
                
                Log.d(TAG, "Advanced to round $nextRound")
            } else {
                // Competition complete
                _competitionState.value = currentState.copy(
                    competitionComplete = true
                )
                
                Log.d(TAG, "Competition completed")
            }
        }
    }
    
    // Convenience getters
    fun isCompetitionActive(): Boolean = _competitionState.value.isActive
    fun isCompetitionCalibrated(): Boolean = _competitionState.value.isCalibrated
    fun getCurrentRound(): Int = _competitionState.value.currentRound
    fun getCurrentAthleteIndex(): Int = _competitionState.value.currentAthleteIndex
    fun getSettings(): CompetitionSettings = _competitionState.value.settings

    /**
     * Set the current athlete index for state persistence
     */
    fun setCurrentAthleteIndex(index: Int) {
        _competitionState.value = _competitionState.value.copy(
            currentAthleteIndex = index
        )
        Log.d(TAG, "Updated current athlete index to: $index")
    }

    /**
     * Set the current round directly (for round navigation)
     */
    fun setCurrentRound(round: Int) {
        val maxRounds = _competitionState.value.settings.numberOfRounds
        if (round in 1..maxRounds) {
            _competitionState.value = _competitionState.value.copy(
                currentRound = round,
                useNextAthleteMode = false // Disable popup system when manually selecting rounds
            )
            Log.d(TAG, "Direct navigation to round $round - disabled popup mode")
        } else {
            Log.w(TAG, "Invalid round $round. Must be between 1 and $maxRounds")
        }
    }

    /**
     * Set navigation mode (Next Athlete vs manual round selection)
     */
    fun setNextAthleteMode(enabled: Boolean) {
        _competitionState.value = _competitionState.value.copy(
            useNextAthleteMode = enabled
        )
        Log.d(TAG, "Next Athlete mode: ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Show round transition popup
     */
    fun showRoundTransitionPopup(show: Boolean) {
        _competitionState.value = _competitionState.value.copy(
            showRoundTransitionPopup = show
        )
    }

    /**
     * Set final round settings from Round 3 dialog
     */
    fun setFinalRoundSettings(athleteCount: Int, reorderEnabled: Boolean, progressingAthletes: List<String>) {
        val settings = FinalRoundSettings(athleteCount, reorderEnabled)
        _competitionState.value = _competitionState.value.copy(
            finalRoundSettings = settings,
            progressingAthletes = progressingAthletes
        )
        Log.d(TAG, "Final round settings: $athleteCount athletes, reorder: $reorderEnabled")
    }

    /**
     * Check if should show round transition popup
     */
    fun shouldShowRoundTransitionPopup(completedRound: Int): Boolean {
        val state = _competitionState.value
        return state.useNextAthleteMode && completedRound in 1..5 // Rounds 1-5 can trigger popups
    }

    /**
     * Athlete Cut and Reordering Management
     */

    /**
     * Show athlete cut popup (Round 3-4 transition)
     */
    fun showAthleteCutPopup(show: Boolean) {
        _competitionState.value = _competitionState.value.copy(
            athleteCutState = _competitionState.value.athleteCutState.copy(
                showCutPopup = show
            )
        )
        Log.d(TAG, "Athlete cut popup: ${if (show) "shown" else "hidden"}")
    }

    /**
     * Show athlete progression confirmation popup
     */
    fun showProgressionConfirmationPopup(show: Boolean) {
        _competitionState.value = _competitionState.value.copy(
            athleteCutState = _competitionState.value.athleteCutState.copy(
                showProgressionPopup = show
            )
        )
        Log.d(TAG, "Progression confirmation popup: ${if (show) "shown" else "hidden"}")
    }

    /**
     * Show reorder confirmation popup (Round 4-5, 5-6 transitions)
     */
    fun showReorderConfirmationPopup(show: Boolean) {
        _competitionState.value = _competitionState.value.copy(
            athleteCutState = _competitionState.value.athleteCutState.copy(
                showReorderPopup = show
            )
        )
        Log.d(TAG, "Reorder confirmation popup: ${if (show) "shown" else "hidden"}")
    }

    /**
     * Update athlete cut selection (4, 6, 8, 10, or -1 for ALL)
     */
    fun setSelectedAthleteCount(count: Int) {
        _competitionState.value = _competitionState.value.copy(
            athleteCutState = _competitionState.value.athleteCutState.copy(
                selectedAthleteCount = count
            )
        )
        Log.d(TAG, "Selected athlete count: ${if (count == -1) "ALL" else count}")
    }

    /**
     * Update reordering preference
     */
    fun setReorderEnabled(enabled: Boolean) {
        _competitionState.value = _competitionState.value.copy(
            athleteCutState = _competitionState.value.athleteCutState.copy(
                reorderEnabled = enabled
            )
        )
        Log.d(TAG, "Reorder enabled: $enabled")
    }

    /**
     * Calculate athlete rankings based on measurement history (placeholder)
     */
    fun calculateAthleteRankings(): List<AthleteRanking> {
        // TODO: Implement proper athlete rankings calculation
        return emptyList()
    }

    /*
    // Original implementation - commented out for now due to data type issues
    fun calculateAthleteRankings(measurementHistory: List<CompetitionMeasurement>, checkedInAthletes: List<PolyFieldApiClient.Athlete>): List<AthleteRanking> {
        // Group measurements by athlete
        val athletePerformances = measurementHistory
            .filter { it.distance != null && it.distance > 0 } // Only valid distances
            .groupBy { it.athleteBib }

        // Calculate rankings
        val rankings = checkedInAthletes.mapNotNull { athlete ->
            val performances = athletePerformances[athlete.bib] ?: return@mapNotNull null
            val bestMark = performances.maxOfOrNull { it.distance ?: 0.0 } ?: 0.0

            // Calculate round data
            val roundData = (1.._competitionState.value.currentRound).map { round ->
                val roundPerformances = performances.filter { it.round == round }
                val roundBest = roundPerformances.maxOfOrNull { it.distance ?: 0.0 } ?: 0.0
                val attempts = roundPerformances.map { measurement ->
                    when {
                        measurement.distance != null && measurement.distance > 0 -> String.format("%.2f", measurement.distance)
                        measurement.isPass -> "PASS"
                        !measurement.isValid -> "FOUL"
                        else -> "---"
                    }
                }
                RoundData(round, roundBest, attempts)
            }

            AthleteRanking(
                athleteBib = athlete.bib,
                athleteName = athlete.name,
                bestMark = bestMark,
                position = 0, // Will be set after sorting
                roundData = roundData
            )
        }.sortedByDescending { it.bestMark } // Sort by best performance (descending)
         .mapIndexed { index, ranking -> ranking.copy(position = index + 1) } // Assign positions

        Log.d(TAG, "Calculated ${rankings.size} athlete rankings")
        return rankings
    }
    */

    /**
     * Calculate athlete cut using stored selectedAthleteCount - fixes the bug
     */
    fun calculateAndSetAthleteCut(measurementManager: CompetitionMeasurementManager? = null) {
        val rankings = if (measurementManager != null) {
            // Use real athlete data from athlete manager
            measurementManager.getRealAthleteRankings()
        } else {
            // Fallback to dummy data for testing
            generateAthleteRankingsFromAthletes()
        }

        val cutState = _competitionState.value.athleteCutState
        val selectedCount = cutState.selectedAthleteCount

        Log.d(TAG, "Calculating athlete cut with selected count: $selectedCount using ${if (measurementManager != null) "real" else "dummy"} athlete data")

        // Convert 999 ("All") to actual count
        val actualCutCount = if (selectedCount == 999) -1 else selectedCount

        val (advancing, eliminated) = calculateAthleteCut(rankings, actualCutCount)

        setAthleteRankings(rankings)
        setAthleteCutResults(advancing, eliminated)

        Log.d(TAG, "Set athlete cut results: ${advancing.size} advancing, ${eliminated.size} eliminated")
    }

    /**
     * Generate simple athlete rankings for demonstration - placeholder implementation
     */
    fun generateAthleteRankingsFromAthletes(): List<AthleteRanking> {
        // For now, create dummy rankings to test the UI flow
        // This will be replaced with actual athlete data integration
        val dummyAthletes = listOf(
            createDummyAthlete("John Smith", 1, 15.75),
            createDummyAthlete("Sarah Johnson", 2, 14.23),
            createDummyAthlete("Mike Brown", 3, 13.89),
            createDummyAthlete("Lisa Davis", 4, 13.45),
            createDummyAthlete("Tom Wilson", 5, 12.98),
            createDummyAthlete("Emma Taylor", 6, 12.67),
            createDummyAthlete("David Lee", 7, 12.34),
            createDummyAthlete("Amy Clark", 8, 11.92),
            createDummyAthlete("Chris Martinez", 9, 11.55),
            createDummyAthlete("Rachel White", 10, 11.12)
        )

        Log.d(TAG, "Generated ${dummyAthletes.size} dummy athlete rankings for testing")
        return dummyAthletes
    }

    private fun createDummyAthlete(name: String, bib: Int, bestMark: Double): AthleteRanking {
        val dummyAthlete = CompetitionAthlete(
            bib = bib.toString(),
            order = bib,
            name = name,
            club = "Test Club"
        )

        return AthleteRanking(
            position = bib,
            athlete = dummyAthlete,
            bestMark = bestMark,
            isAdvancing = true
        )
    }

    /**
     * Set athlete rankings in state
     */
    fun setAthleteRankings(rankings: List<AthleteRanking>) {
        _competitionState.value = _competitionState.value.copy(
            athleteCutState = _competitionState.value.athleteCutState.copy(
                athleteRankings = rankings
            )
        )
    }

    /**
     * Calculate advancing and eliminated athletes for Round 3-4 cut
     */
    fun calculateAthleteCut(rankings: List<AthleteRanking>, cutCount: Int): Pair<List<AthleteRanking>, List<AthleteRanking>> {
        val effectiveCutCount = if (cutCount == -1) rankings.size else minOf(cutCount, rankings.size)

        val advancing = rankings.take(effectiveCutCount).map { it.copy(isAdvancing = true) }
        val eliminated = rankings.drop(effectiveCutCount).map { it.copy(isAdvancing = false) }

        Log.d(TAG, "Athlete cut: ${advancing.size} advancing, ${eliminated.size} eliminated")
        return Pair(advancing, eliminated)
    }

    /**
     * Set advancing and eliminated athletes
     */
    fun setAthleteCutResults(advancing: List<AthleteRanking>, eliminated: List<AthleteRanking>) {
        _competitionState.value = _competitionState.value.copy(
            athleteCutState = _competitionState.value.athleteCutState.copy(
                advancingAthletes = advancing,
                eliminatedAthletes = eliminated
            )
        )
    }

    /**
     * Apply athlete cut and advance to next round
     */
    fun applyAthleteCutAndAdvance() {
        val cutState = _competitionState.value.athleteCutState
        val advancingBibs = cutState.advancingAthletes.map { it.athlete.bib }

        // Update competition state with cut results
        _competitionState.value = _competitionState.value.copy(
            progressingAthletes = advancingBibs,
            athleteCutState = cutState.copy(
                showCutPopup = false,
                showProgressionPopup = false
            )
        )

        // Advance to next round
        advanceToNextRound()
        Log.d(TAG, "Applied athlete cut and advanced to round ${_competitionState.value.currentRound}")
    }

    /**
     * Apply athlete cut (placeholder)
     */
    fun applyAthleteCut() {
        // TODO: Implement athlete cut logic
        Log.d(TAG, "Applied athlete cut")
    }

    /**
     * Apply athlete reorder (placeholder)
     */
    fun applyAthleteReorder() {
        // TODO: Implement athlete reorder logic
        Log.d(TAG, "Applied athlete reorder")
    }

    /**
     * Reset athlete cut state (placeholder)
     */
    fun resetAthleteCutState() {
        _competitionState.value = _competitionState.value.copy(
            athleteCutState = AthleteCutState()
        )
        Log.d(TAG, "Reset athlete cut state")
    }
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