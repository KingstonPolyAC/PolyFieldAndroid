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
import kotlin.random.Random

/**
 * Demo competition templates for different scenarios
 */
data class DemoCompetitionTemplate(
    val id: String,
    val name: String,
    val eventType: String, // SHOT, DISCUS, HAMMER, JAVELIN_ARC
    val description: String,
    val athletes: List<DemoAthlete>,
    val settings: CompetitionSettings
)

data class DemoAthlete(
    val bib: String,
    val name: String,
    val club: String,
    val skillLevel: DemoSkillLevel = DemoSkillLevel.INTERMEDIATE,
    val consistency: Float = 0.8f // 0.0 to 1.0, higher = more consistent throws
) {
    fun toCompetitionAthlete(order: Int): CompetitionAthlete {
        return CompetitionAthlete(
            bib = bib,
            order = order,
            name = name,
            club = club,
            isSelected = true
        )
    }
}

enum class DemoSkillLevel(val baseDistance: Double, val variation: Double) {
    BEGINNER(8.0, 2.0),      // 6-10m range
    INTERMEDIATE(15.0, 3.0), // 12-18m range  
    ADVANCED(20.0, 2.5),     // 17.5-22.5m range
    ELITE(25.0, 2.0)         // 23-27m range
}

/**
 * Demo mode state
 */
data class DemoModeState(
    val isEnabled: Boolean = false,
    val currentTemplate: DemoCompetitionTemplate? = null,
    val availableTemplates: List<DemoCompetitionTemplate> = emptyList(),
    val isRunningDemo: Boolean = false,
    val currentDemoStep: DemoStep = DemoStep.NONE,
    val autoProgressEnabled: Boolean = false,
    val progressInterval: Long = 3000L // ms between auto steps
)

enum class DemoStep {
    NONE,
    CALIBRATION,
    COMPETITION_SETUP,
    ATHLETE_MANAGEMENT,
    MEASUREMENT,
    RESULTS_VISUALIZATION,
    COMPLETE
}

/**
 * Enhanced Demo Mode Manager
 * Provides realistic training scenarios for officials
 */
class DemoModeManager(private val context: Context) : ViewModel() {
    
    companion object {
        private const val TAG = "DemoModeManager"
        private const val PREFS_NAME = "polyfield_demo_prefs"
        private const val PREF_ENABLED = "demo_enabled"
        private const val PREF_AUTO_PROGRESS = "auto_progress"
    }
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // State management
    private val _demoState = MutableStateFlow(DemoModeState())
    val demoState: StateFlow<DemoModeState> = _demoState.asStateFlow()
    
    init {
        loadDemoSettings()
        initializeDemoTemplates()
    }
    
    /**
     * Load saved demo settings
     */
    private fun loadDemoSettings() {
        val isEnabled = preferences.getBoolean(PREF_ENABLED, false)
        val autoProgress = preferences.getBoolean(PREF_AUTO_PROGRESS, false)
        
        _demoState.value = _demoState.value.copy(
            isEnabled = isEnabled,
            autoProgressEnabled = autoProgress
        )
        
        Log.d(TAG, "Loaded demo settings: enabled=$isEnabled, autoProgress=$autoProgress")
    }
    
    /**
     * Initialize demo competition templates
     */
    private fun initializeDemoTemplates() {
        val templates = listOf(
            createShotPutTemplate(),
            createDiscusTemplate(),
            createHammerTemplate(),
            createJavelinTemplate(),
            createMixedSkillTemplate()
        )
        
        _demoState.value = _demoState.value.copy(
            availableTemplates = templates
        )
        
        Log.d(TAG, "Initialized ${templates.size} demo templates")
    }
    
    /**
     * Create shot put demo template
     */
    private fun createShotPutTemplate(): DemoCompetitionTemplate {
        return DemoCompetitionTemplate(
            id = "demo_shot_put",
            name = "Shot Put Competition",
            eventType = "SHOT",
            description = "8 athletes, 6 rounds with cut after round 3",
            athletes = listOf(
                DemoAthlete("101", "John Smith", "Athletics Club", DemoSkillLevel.ELITE, 0.9f),
                DemoAthlete("102", "Mike Johnson", "Track Team", DemoSkillLevel.ADVANCED, 0.85f),
                DemoAthlete("103", "David Wilson", "City Runners", DemoSkillLevel.ADVANCED, 0.8f),
                DemoAthlete("104", "Chris Brown", "University TC", DemoSkillLevel.INTERMEDIATE, 0.75f),
                DemoAthlete("105", "Tom Davis", "Local Club", DemoSkillLevel.INTERMEDIATE, 0.7f),
                DemoAthlete("106", "Alex Miller", "Youth Team", DemoSkillLevel.BEGINNER, 0.6f),
                DemoAthlete("107", "Sam Taylor", "School Team", DemoSkillLevel.BEGINNER, 0.65f),
                DemoAthlete("108", "Ryan Clark", "Community Club", DemoSkillLevel.INTERMEDIATE, 0.75f)
            ),
            settings = CompetitionSettings(
                numberOfRounds = 6,
                athleteCutoff = 3,
                cutoffEnabled = true,
                reorderAfterRound3 = true,
                eventType = "SHOT",
                allowAllAthletes = false
            )
        )
    }
    
    /**
     * Create discus demo template
     */
    private fun createDiscusTemplate(): DemoCompetitionTemplate {
        return DemoCompetitionTemplate(
            id = "demo_discus",
            name = "Discus Throw Competition",
            eventType = "DISCUS",
            description = "6 athletes, 3 rounds, all advance",
            athletes = listOf(
                DemoAthlete("201", "Sarah Wilson", "Metro Athletics", DemoSkillLevel.ELITE, 0.85f),
                DemoAthlete("202", "Emma Thompson", "Regional TC", DemoSkillLevel.ADVANCED, 0.8f),
                DemoAthlete("203", "Lisa Davis", "University Team", DemoSkillLevel.ADVANCED, 0.82f),
                DemoAthlete("204", "Maria Garcia", "City Club", DemoSkillLevel.INTERMEDIATE, 0.7f),
                DemoAthlete("205", "Kate Johnson", "Youth Squad", DemoSkillLevel.INTERMEDIATE, 0.75f),
                DemoAthlete("206", "Amy Brown", "School Athletics", DemoSkillLevel.BEGINNER, 0.65f)
            ),
            settings = CompetitionSettings(
                numberOfRounds = 3,
                athleteCutoff = -1,
                cutoffEnabled = false,
                reorderAfterRound3 = false,
                eventType = "DISCUS",
                allowAllAthletes = true
            )
        )
    }
    
    /**
     * Create hammer throw demo template
     */
    private fun createHammerTemplate(): DemoCompetitionTemplate {
        return DemoCompetitionTemplate(
            id = "demo_hammer",
            name = "Hammer Throw Competition",
            eventType = "HAMMER",
            description = "10 athletes, 4 rounds with cut to 4",
            athletes = (1..10).map { i ->
                DemoAthlete(
                    bib = "30$i",
                    name = "Athlete $i",
                    club = "Club ${('A'..'E').random()}",
                    skillLevel = DemoSkillLevel.values().random(),
                    consistency = 0.6f + Random.nextFloat() * 0.3f
                )
            },
            settings = CompetitionSettings(
                numberOfRounds = 4,
                athleteCutoff = 4,
                cutoffEnabled = true,
                reorderAfterRound3 = false,
                eventType = "HAMMER",
                allowAllAthletes = false
            )
        )
    }
    
    /**
     * Create javelin demo template
     */
    private fun createJavelinTemplate(): DemoCompetitionTemplate {
        return DemoCompetitionTemplate(
            id = "demo_javelin",
            name = "Javelin Throw Competition",
            eventType = "JAVELIN_ARC",
            description = "12 athletes, 6 rounds with qualification",
            athletes = (1..12).map { i ->
                DemoAthlete(
                    bib = "${400 + i}",
                    name = "Thrower $i",
                    club = "Team ${('X'..'Z').random()}",
                    skillLevel = if (i <= 3) DemoSkillLevel.ELITE 
                                else if (i <= 6) DemoSkillLevel.ADVANCED
                                else if (i <= 9) DemoSkillLevel.INTERMEDIATE
                                else DemoSkillLevel.BEGINNER,
                    consistency = 0.65f + Random.nextFloat() * 0.25f
                )
            },
            settings = CompetitionSettings(
                numberOfRounds = 6,
                athleteCutoff = 8,
                cutoffEnabled = true,
                reorderAfterRound3 = true,
                eventType = "JAVELIN_ARC",
                allowAllAthletes = false
            )
        )
    }
    
    /**
     * Create mixed skill level template for training
     */
    private fun createMixedSkillTemplate(): DemoCompetitionTemplate {
        return DemoCompetitionTemplate(
            id = "demo_training",
            name = "Training Session - Mixed Levels",
            eventType = "SHOT",
            description = "Training scenario with varied skill levels",
            athletes = listOf(
                DemoAthlete("T01", "Elite Athlete", "Pro Club", DemoSkillLevel.ELITE, 0.95f),
                DemoAthlete("T02", "Experienced Thrower", "Regional Team", DemoSkillLevel.ADVANCED, 0.85f),
                DemoAthlete("T03", "Club Athlete", "Local Club", DemoSkillLevel.INTERMEDIATE, 0.75f),
                DemoAthlete("T04", "Junior Athlete", "Youth Team", DemoSkillLevel.INTERMEDIATE, 0.7f),
                DemoAthlete("T05", "Beginner", "School Team", DemoSkillLevel.BEGINNER, 0.6f)
            ),
            settings = CompetitionSettings(
                numberOfRounds = 3,
                athleteCutoff = -1,
                cutoffEnabled = false,
                reorderAfterRound3 = false,
                eventType = "SHOT",
                allowAllAthletes = true
            )
        )
    }
    
    /**
     * Enable/disable demo mode
     */
    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch {
            _demoState.value = _demoState.value.copy(
                isEnabled = enabled,
                isRunningDemo = if (!enabled) false else _demoState.value.isRunningDemo
            )
            
            preferences.edit()
                .putBoolean(PREF_ENABLED, enabled)
                .apply()
            
            Log.d(TAG, "Demo mode ${if (enabled) "enabled" else "disabled"}")
        }
    }
    
    /**
     * Select demo template
     */
    fun selectDemoTemplate(templateId: String) {
        val template = _demoState.value.availableTemplates.find { it.id == templateId }
        if (template != null) {
            _demoState.value = _demoState.value.copy(
                currentTemplate = template
            )
            Log.d(TAG, "Selected demo template: ${template.name}")
        }
    }
    
    /**
     * Start demo session
     */
    fun startDemoSession() {
        viewModelScope.launch {
            _demoState.value = _demoState.value.copy(
                isRunningDemo = true,
                currentDemoStep = DemoStep.CALIBRATION
            )
            
            Log.d(TAG, "Started demo session")
            
            if (_demoState.value.autoProgressEnabled) {
                startAutoProgress()
            }
        }
    }
    
    /**
     * Stop demo session
     */
    fun stopDemoSession() {
        _demoState.value = _demoState.value.copy(
            isRunningDemo = false,
            currentDemoStep = DemoStep.NONE
        )
        
        Log.d(TAG, "Stopped demo session")
    }
    
    /**
     * Move to next demo step
     */
    fun nextDemoStep() {
        val currentStep = _demoState.value.currentDemoStep
        val nextStep = when (currentStep) {
            DemoStep.NONE -> DemoStep.CALIBRATION
            DemoStep.CALIBRATION -> DemoStep.COMPETITION_SETUP
            DemoStep.COMPETITION_SETUP -> DemoStep.ATHLETE_MANAGEMENT
            DemoStep.ATHLETE_MANAGEMENT -> DemoStep.MEASUREMENT
            DemoStep.MEASUREMENT -> DemoStep.RESULTS_VISUALIZATION
            DemoStep.RESULTS_VISUALIZATION -> DemoStep.COMPLETE
            DemoStep.COMPLETE -> DemoStep.NONE
        }
        
        _demoState.value = _demoState.value.copy(
            currentDemoStep = nextStep
        )
        
        Log.d(TAG, "Advanced demo step: $currentStep -> $nextStep")
    }
    
    /**
     * Set auto progress
     */
    fun setAutoProgress(enabled: Boolean) {
        _demoState.value = _demoState.value.copy(
            autoProgressEnabled = enabled
        )
        
        preferences.edit()
            .putBoolean(PREF_AUTO_PROGRESS, enabled)
            .apply()
        
        if (enabled && _demoState.value.isRunningDemo) {
            startAutoProgress()
        }
    }
    
    /**
     * Start automatic progression through demo steps
     */
    private fun startAutoProgress() {
        viewModelScope.launch {
            while (_demoState.value.isRunningDemo && 
                   _demoState.value.autoProgressEnabled &&
                   _demoState.value.currentDemoStep != DemoStep.COMPLETE) {
                
                kotlinx.coroutines.delay(_demoState.value.progressInterval)
                
                if (_demoState.value.isRunningDemo && _demoState.value.autoProgressEnabled) {
                    nextDemoStep()
                }
            }
        }
    }
    
    /**
     * Generate realistic demo measurement for athlete
     */
    fun generateDemoMeasurement(athlete: DemoAthlete): Double? {
        val skillLevel = athlete.skillLevel
        val consistency = athlete.consistency
        
        // Base performance varies by skill level
        val baseDistance = skillLevel.baseDistance
        val variation = skillLevel.variation
        
        // Add consistency factor (higher consistency = less variation)
        val actualVariation = variation * (1.0 - consistency)
        
        // Generate distance with some randomness
        val distance = baseDistance + (Random.nextDouble() - 0.5) * 2 * actualVariation
        
        // 15% chance of foul throw (reduced for elite athletes)
        val foulChance = when (skillLevel) {
            DemoSkillLevel.ELITE -> 0.05
            DemoSkillLevel.ADVANCED -> 0.10
            DemoSkillLevel.INTERMEDIATE -> 0.15
            DemoSkillLevel.BEGINNER -> 0.20
        }
        
        return if (Random.nextDouble() < foulChance) null else maxOf(0.0, distance)
    }
    
    /**
     * Generate demo wind reading
     */
    fun generateDemoWindReading(): Double {
        // Wind between -2.0 and +2.0 m/s
        return -2.0 + Random.nextDouble() * 4.0
    }
    
    /**
     * Get demo step description
     */
    fun getDemoStepDescription(step: DemoStep): String {
        return when (step) {
            DemoStep.NONE -> "Demo not started"
            DemoStep.CALIBRATION -> "Setting up EDM calibration with circle center and edge verification"
            DemoStep.COMPETITION_SETUP -> "Configuring competition settings: rounds, cutoffs, and event type"
            DemoStep.ATHLETE_MANAGEMENT -> "Loading athlete list and managing selections"
            DemoStep.MEASUREMENT -> "Taking measurements and recording results for each athlete"
            DemoStep.RESULTS_VISUALIZATION -> "Viewing live rankings and athlete heatmaps"
            DemoStep.COMPLETE -> "Demo completed - review results and start over"
        }
    }
    
    // Convenience getters
    fun isDemoEnabled(): Boolean = _demoState.value.isEnabled
    fun isRunningDemo(): Boolean = _demoState.value.isRunningDemo
    fun getCurrentTemplate(): DemoCompetitionTemplate? = _demoState.value.currentTemplate
    fun getCurrentStep(): DemoStep = _demoState.value.currentDemoStep
    fun getAvailableTemplates(): List<DemoCompetitionTemplate> = _demoState.value.availableTemplates
    fun isAutoProgressEnabled(): Boolean = _demoState.value.autoProgressEnabled
}

/**
 * Factory for creating DemoModeManager
 */
class DemoModeManagerFactory(private val context: Context) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DemoModeManager::class.java)) {
            return DemoModeManager(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}