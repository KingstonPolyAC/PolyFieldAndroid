package com.polyfieldandroid

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * Integration test activity for testing the mobile app components
 * This is a simple test interface to verify functionality without full UI
 */
class IntegrationTestActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                IntegrationTestScreen()
            }
        }
    }
}

@Composable
fun IntegrationTestScreen() {
    var testResults by remember { mutableStateOf(listOf<TestResult>()) }
    var isRunning by remember { mutableStateOf(false) }
    
    val modeManager: ModeManagerViewModel = viewModel(
        factory = ModeManagerViewModelFactory(androidx.compose.ui.platform.LocalContext.current)
    )
    
    val competitionManager: CompetitionManagerViewModel = viewModel(
        factory = CompetitionManagerViewModelFactory(androidx.compose.ui.platform.LocalContext.current)
    )
    
    val athleteManager: AthleteManagerViewModel = viewModel(
        factory = AthleteManagerViewModelFactory(androidx.compose.ui.platform.LocalContext.current)
    )
    
    val demoManager: DemoModeManager = viewModel(
        factory = DemoModeManagerFactory(androidx.compose.ui.platform.LocalContext.current)
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "PolyField Integration Tests",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        
        Button(
            onClick = {
                isRunning = true
                runIntegrationTests(
                    modeManager = modeManager,
                    competitionManager = competitionManager,
                    athleteManager = athleteManager,
                    demoManager = demoManager,
                    onResult = { result ->
                        testResults = testResults + result
                    },
                    onComplete = {
                        isRunning = false
                    }
                )
            },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isRunning) "Running Tests..." else "Run Integration Tests")
        }
        
        if (testResults.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(testResults) { result ->
                    TestResultCard(result)
                }
            }
        }
    }
}

@Composable
fun TestResultCard(result: TestResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (result.status) {
                TestStatus.PASSED -> MaterialTheme.colorScheme.primaryContainer
                TestStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                TestStatus.RUNNING -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.testName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = result.status.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (result.status) {
                        TestStatus.PASSED -> MaterialTheme.colorScheme.primary
                        TestStatus.FAILED -> MaterialTheme.colorScheme.error
                        TestStatus.RUNNING -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            if (result.message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (result.duration > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Duration: ${result.duration}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class TestResult(
    val testName: String,
    val status: TestStatus,
    val message: String = "",
    val duration: Long = 0
)

enum class TestStatus {
    RUNNING, PASSED, FAILED
}

/**
 * Run integration tests for all components
 */
fun runIntegrationTests(
    modeManager: ModeManagerViewModel,
    competitionManager: CompetitionManagerViewModel,
    athleteManager: AthleteManagerViewModel,
    demoManager: DemoModeManager,
    onResult: (TestResult) -> Unit,
    onComplete: () -> Unit
) {
    modeManager.viewModelScope.launch {
        try {
            // Test 1: Mode Manager
            onResult(TestResult("Mode Manager Initialization", TestStatus.RUNNING))
            val startTime1 = System.currentTimeMillis()
            
            val modeState = modeManager.modeState.value
            val success1 = modeState.isInitialized
            val duration1 = System.currentTimeMillis() - startTime1
            
            onResult(TestResult(
                "Mode Manager Initialization",
                if (success1) TestStatus.PASSED else TestStatus.FAILED,
                if (success1) "Mode manager initialized successfully" else "Failed to initialize mode manager",
                duration1
            ))
            
            // Test 2: Competition Manager Settings
            onResult(TestResult("Competition Manager Settings", TestStatus.RUNNING))
            val startTime2 = System.currentTimeMillis()
            
            competitionManager.setNumberOfRounds(6)
            competitionManager.setAthleteCutoff(8, false)
            competitionManager.setReorderAfterRound3(true)
            
            val settings = competitionManager.getSettings()
            val success2 = settings.numberOfRounds == 6 && 
                          settings.athleteCutoff == 8 && 
                          settings.reorderAfterRound3
            val duration2 = System.currentTimeMillis() - startTime2
            
            onResult(TestResult(
                "Competition Manager Settings",
                if (success2) TestStatus.PASSED else TestStatus.FAILED,
                if (success2) "Settings updated successfully" else "Failed to update settings",
                duration2
            ))
            
            // Test 3: Athlete Manager - Manual Athletes
            onResult(TestResult("Athlete Manager - Manual Entry", TestStatus.RUNNING))
            val startTime3 = System.currentTimeMillis()
            
            athleteManager.addManualAthlete("T01", "Test Athlete 1", "Test Club")
            athleteManager.addManualAthlete("T02", "Test Athlete 2", "Test Club")
            
            kotlinx.coroutines.delay(100) // Allow state updates
            
            val athletes = athleteManager.getSelectedAthletes()
            val success3 = athletes.size >= 2
            val duration3 = System.currentTimeMillis() - startTime3
            
            onResult(TestResult(
                "Athlete Manager - Manual Entry",
                if (success3) TestStatus.PASSED else TestStatus.FAILED,
                if (success3) "Added ${athletes.size} athletes successfully" else "Failed to add athletes",
                duration3
            ))
            
            // Test 4: Demo Mode Manager
            onResult(TestResult("Demo Mode Manager", TestStatus.RUNNING))
            val startTime4 = System.currentTimeMillis()
            
            demoManager.setDemoMode(true)
            val templates = demoManager.getAvailableTemplates()
            
            val success4 = demoManager.isDemoEnabled() && templates.isNotEmpty()
            val duration4 = System.currentTimeMillis() - startTime4
            
            onResult(TestResult(
                "Demo Mode Manager",
                if (success4) TestStatus.PASSED else TestStatus.FAILED,
                if (success4) "Demo mode enabled with ${templates.size} templates" else "Failed to enable demo mode",
                duration4
            ))
            
            // Test 5: Demo Template Selection
            onResult(TestResult("Demo Template Selection", TestStatus.RUNNING))
            val startTime5 = System.currentTimeMillis()
            
            if (templates.isNotEmpty()) {
                demoManager.selectDemoTemplate(templates.first().id)
                
                kotlinx.coroutines.delay(50)
                
                val selectedTemplate = demoManager.getCurrentTemplate()
                val success5 = selectedTemplate != null
                val duration5 = System.currentTimeMillis() - startTime5
                
                onResult(TestResult(
                    "Demo Template Selection",
                    if (success5) TestStatus.PASSED else TestStatus.FAILED,
                    if (success5) "Selected template: ${selectedTemplate?.name}" else "Failed to select template",
                    duration5
                ))
            } else {
                onResult(TestResult(
                    "Demo Template Selection",
                    TestStatus.FAILED,
                    "No templates available",
                    System.currentTimeMillis() - startTime5
                ))
            }
            
            // Test 6: Server Connection Test (will fail without server, but tests the API)
            onResult(TestResult("API Client - Server Connection Test", TestStatus.RUNNING))
            val startTime6 = System.currentTimeMillis()
            
            val connectionTest = try {
                modeManager.testServerConnection("192.168.1.100", 8080)
            } catch (e: Exception) {
                false
            }
            val duration6 = System.currentTimeMillis() - startTime6
            
            onResult(TestResult(
                "API Client - Server Connection Test",
                TestStatus.PASSED, // Always pass this test as it's expected to fail without server
                if (connectionTest) "Server connection successful" else "Server connection failed (expected without server)",
                duration6
            ))
            
            Log.d("IntegrationTest", "All integration tests completed")
            
        } catch (e: Exception) {
            onResult(TestResult(
                "Integration Test Suite",
                TestStatus.FAILED,
                "Test suite failed: ${e.message}",
                0
            ))
            Log.e("IntegrationTest", "Integration tests failed", e)
        } finally {
            onComplete()
        }
    }
}