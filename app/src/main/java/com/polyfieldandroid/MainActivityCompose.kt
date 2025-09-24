package com.polyfieldandroid

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.unit.IntSize
import kotlin.math.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider

data class ViewModelContainer(
    val appViewModel: AppViewModel,
    val modeManager: ModeManagerViewModel,
    val competitionManager: CompetitionManagerViewModel,
    val athleteManager: AthleteManagerViewModel,
    val measurementManager: CompetitionMeasurementManager
)

// Device State
data class DeviceState(
    val connected: Boolean = false,
    val connectionType: String = "serial", // "serial" or "network"
    val serialPort: String = "/dev/ttyUSB0",
    val ipAddress: String = "192.168.1.100",
    val port: Int = 8080,
    val deviceName: String = "" // Real device name like "Mato MTS-602R+"
)

data class DeviceConfig(
    val edm: DeviceState = DeviceState(),
    val wind: DeviceState = DeviceState(),
    val scoreboard: DeviceState = DeviceState()
)

// Complete Calibration Record
data class CalibrationRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val circleType: String,
    val targetRadius: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val dateString: String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
    val stationCoordinates: Pair<Double, Double>? = null,
    val edgeResult: EdgeResult? = null,
    val sectorLineDistance: Double? = null,
    val sectorLineCoordinates: Pair<Double, Double>? = null
) {
    fun isFromToday(): Boolean {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val calibrationDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
        return today == calibrationDate
    }
    
    fun getDisplayName(): String {
        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return "${circleType.replace("_", " ")} - ${timeFormat.format(java.util.Date(timestamp))}"
    }
    
    fun isComplete(): Boolean {
        return stationCoordinates != null && edgeResult != null && sectorLineDistance != null
    }
}

// Current Calibration State
data class CalibrationState(
    val circleType: String = "SHOT",
    val targetRadius: Double = 1.0675,
    val centreSet: Boolean = false,
    val centreTimestamp: String? = null,
    val stationCoordinates: Pair<Double, Double>? = null,
    val edgeVerified: Boolean = false,
    val edgeResult: EdgeResult? = null,
    val sectorLineSet: Boolean = false,
    val sectorLineDistance: Double? = null,
    val sectorLineCoordinates: Pair<Double, Double>? = null,
    val selectedHistoricalCalibration: CalibrationRecord? = null
)

data class EdgeResult(
    val toleranceCheck: Boolean = false,
    val measurements: List<Double> = emptyList(),
    val averageRadius: Double = 0.0,
    val deviation: Double = 0.0
)

// Settings data class
data class AppSettings(
    val selectedEDMDevice: EDMDeviceSpec = EDMDeviceRegistry.getDefaultDevice(),
    val serverIpAddress: String = "192.168.0.90",
    val serverPort: Int = 8080
)

// DEBUG: Serial Communication Log Entry (REMOVE WHEN DEBUG COMPLETE)
data class SerialCommLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val direction: String, // "OUT" or "IN"
    val data: String,
    val dataHex: String = "",
    val success: Boolean = true,
    val error: String? = null
) {
    fun getFormattedTime(): String {
        return java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }
}

// Heat Map Data Classes - Using shared ThrowCoordinate from CompetitionMeasurementManager

data class HeatMapData(
    val coordinates: List<ThrowCoordinate>,
    val bounds: HeatMapBounds,
    val stats: ThrowStatistics,
    val circleType: String,
    val targetRadius: Double,
    val optimalScale: Double,
    val isAutoScaled: Boolean = true
)

data class HeatMapBounds(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double
)

data class ThrowStatistics(
    val totalThrows: Int,
    val averageDistance: Double,
    val maxDistance: Double,
    val minDistance: Double
)

// Sector Line Calibration Screen
@Composable
fun CalibrationSectorLineScreen(
    calibration: CalibrationState,
    isLoading: Boolean,
    onMeasureSectorLine: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Instructions Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F8FF))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Sector Line Check Mark",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Place the prism on the right-hand sector line and click measure.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
        
        // Current Measurement Display
        if (calibration.sectorLineSet && calibration.sectorLineDistance != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "✓ Sector Line Measured",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "${String.format(java.util.Locale.UK, "%.2f", kotlin.math.floor(calibration.sectorLineDistance * 100) / 100)} m",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "beyond circle edge",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF666666),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onMeasureSectorLine,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1976D2)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (calibration.sectorLineSet) "Re-measure" else "Measure",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
        }
    }
}

// Heat Map Screen
@Composable
fun HeatMapScreen(
    calibration: CalibrationState,
    throwCoordinates: List<ThrowCoordinate>,
    onBackClick: () -> Unit
) {
    // Use actual throw coordinates and calibration data
    val heatMapData = remember(throwCoordinates, calibration) {
        val filteredCoords = throwCoordinates // Use all coordinates
        
        if (filteredCoords.isEmpty()) {
            // Default scale based on event type
            val defaultScale = when (calibration.circleType) {
                "SHOT" -> 25.0
                "DISCUS" -> 80.0
                "HAMMER" -> 90.0
                "JAVELIN_ARC" -> 100.0
                else -> 50.0
            }
            HeatMapData(
                coordinates = emptyList(),
                bounds = HeatMapBounds(minX = -15.0, maxX = 15.0, minY = 0.0, maxY = defaultScale),
                stats = ThrowStatistics(totalThrows = 0, averageDistance = 0.0, maxDistance = 0.0, minDistance = 0.0),
                circleType = calibration.circleType,
                targetRadius = calibration.targetRadius,
                optimalScale = defaultScale
            )
        } else {
            val distances = filteredCoords.map { it.distance }
            val xCoords = filteredCoords.map { it.x }
            val yCoords = filteredCoords.map { it.y }
            val maxDistance = distances.maxOrNull() ?: 0.0
            val minDistance = distances.minOrNull() ?: 0.0
            
            // Reactive scaling: ensure all throws are visible with appropriate margins
            val maxX = kotlin.math.max(kotlin.math.abs(xCoords.maxOrNull() ?: 0.0), kotlin.math.abs(xCoords.minOrNull() ?: 0.0))
            val maxY = yCoords.maxOrNull() ?: 0.0
            
            // Scale to fit all throws with 20% margin
            val optimalScale = kotlin.math.max(maxY * 1.2, maxDistance * 1.2)
            val lateralScale = maxX * 1.5 // Extra margin for lateral spread
            
            HeatMapData(
                coordinates = filteredCoords,
                bounds = HeatMapBounds(
                    minX = -lateralScale,
                    maxX = lateralScale,
                    minY = kotlin.math.min(0.0, minDistance - 5.0),
                    maxY = kotlin.math.max(optimalScale, maxY + 5.0)
                ),
                stats = ThrowStatistics(
                    totalThrows = filteredCoords.size,
                    averageDistance = distances.average(),
                    maxDistance = maxDistance,
                    minDistance = minDistance
                ),
                circleType = calibration.circleType,
                targetRadius = calibration.targetRadius,
                optimalScale = optimalScale
            )
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Heat Map Visualization - Full screen usage with labels overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
            ) {
                HeatMapVisualization(
                    heatMapData = heatMapData,
                    calibration = calibration,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Add distance labels as overlay Text composables
                HeatMapDistanceLabels(
                    heatMapData = heatMapData,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Statistics
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HeatMapStatItem(
                        value = "${heatMapData.stats.totalThrows}",
                        label = "Total Throws"
                    )
                    HeatMapStatItem(
                        value = "${"%.2f".format(heatMapData.stats.averageDistance)}m",
                        label = "Average"
                    )
                    HeatMapStatItem(
                        value = "${"%.2f".format(heatMapData.stats.maxDistance)}m",
                        label = "Best"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
    }
}

@Composable
fun HeatMapStatItem(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1976D2)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF666666),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun HeatMapVisualization(
    heatMapData: HeatMapData,
    calibration: CalibrationState,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerX = canvasWidth / 2f
        val centerY = canvasHeight * 0.9f // Center towards bottom for throw area
        
        // Calculate scale for visualization - use more of the screen
        val maxDisplayDistance = heatMapData.optimalScale.toFloat()
        val pixelsPerMeter = (canvasHeight * 0.85f) / maxDisplayDistance
        
        // Colors
        val arcColor = androidx.compose.ui.graphics.Color(0xFFE0E0E0)
        val sectorLineColor = androidx.compose.ui.graphics.Color(0xFFBBBBBB)
        val centerColor = androidx.compose.ui.graphics.Color(0xFF1976D2)
        val throwPointColor = androidx.compose.ui.graphics.Color(0xFFf44336)
        val textColor = androidx.compose.ui.graphics.Color(0xFF666666)
        
        // Draw distance arc lines with event-specific intervals
        val arcInterval = when (heatMapData.circleType) {
            "SHOT" -> 2 // Every 2m for shot put
            else -> 10 // Every 10m for discus, hammer, javelin
        }
        
        // Determine range based on throws or default range
        val distances = if (heatMapData.coordinates.isNotEmpty()) {
            val throwDistances = heatMapData.coordinates.map { it.distance.toInt() }
            val minDist = throwDistances.minOrNull() ?: arcInterval
            val maxDist = throwDistances.maxOrNull() ?: maxDisplayDistance.toInt()
            (minDist / arcInterval * arcInterval)..(maxDist / arcInterval * arcInterval + arcInterval) step arcInterval
        } else {
            arcInterval..maxDisplayDistance.toInt() step arcInterval
        }
        
        for (distance in distances) {
            val radius = distance * pixelsPerMeter
            if (radius < canvasHeight * 0.8f) { // Only draw if within canvas bounds
                // Draw the distance arc
                drawCircle(
                    color = arcColor,
                    radius = radius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 2.dp.toPx())
                )
                
                // Distance labels are now handled by overlay composables
            }
        }
        
        // Draw UKA/WA sector lines for throwing events
        // Use calibrated sector line position if available, otherwise use standard angle
        if (calibration.sectorLineSet && calibration.sectorLineCoordinates != null) {
            // Use the calibrated sector line position to draw accurate sector lines
            val (sectorX, sectorY) = calibration.sectorLineCoordinates!!
            
            // Calculate extension factor to go beyond furthest throw
            val distance = kotlin.math.sqrt(sectorX * sectorX + sectorY * sectorY)
            val extendedDistance = kotlin.math.max(distance * 1.5, maxDisplayDistance * 1.1)
            val extensionRatio = extendedDistance / distance
            
            // Convert calibrated coordinates to screen coordinates (extended)
            val calibratedScreenX = centerX + sectorX.toFloat() * pixelsPerMeter * extensionRatio.toFloat()
            val calibratedScreenY = centerY - sectorY.toFloat() * pixelsPerMeter * extensionRatio.toFloat()
            
            // Draw calibrated right sector line (where the measurement was taken) - extended
            drawLine(
                color = androidx.compose.ui.graphics.Color(0xFF4CAF50), // Green for calibrated line
                start = Offset(centerX, centerY),
                end = Offset(calibratedScreenX, calibratedScreenY),
                strokeWidth = 4.dp.toPx()
            )
            
            // Draw left sector line as mirror of right line (same angle, opposite side) - extended
            val leftScreenX = centerX - sectorX.toFloat() * pixelsPerMeter * extensionRatio.toFloat()
            val leftScreenY = centerY - sectorY.toFloat() * pixelsPerMeter * extensionRatio.toFloat()
            drawLine(
                color = androidx.compose.ui.graphics.Color(0xFF4CAF50), // Green for calibrated line
                start = Offset(centerX, centerY),
                end = Offset(leftScreenX, leftScreenY),
                strokeWidth = 4.dp.toPx()
            )
            
            // Add a small check mark at the original calibrated position (not extended)
            val originalScreenX = centerX + sectorX.toFloat() * pixelsPerMeter
            val originalScreenY = centerY - sectorY.toFloat() * pixelsPerMeter
            val checkSize = 12.dp.toPx()
            drawCircle(
                color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                radius = checkSize,
                center = Offset(originalScreenX, originalScreenY),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
            drawCircle(
                color = androidx.compose.ui.graphics.Color.White,
                radius = checkSize * 0.6f,
                center = Offset(originalScreenX, originalScreenY),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
        } else {
            // Use UKA/WA sector angles: Shot/Discus/Hammer = 34.92° total, Javelin = 28.96° total
            // TODO: This should be parameterized based on event type
            val sectorAngleDegrees = 34.92f / 2f // Half angle for each side (17.46°) - default for Shot/Discus/Hammer
            val sectorAngleRadians = Math.toRadians(sectorAngleDegrees.toDouble()).toFloat()
            
            // Left sector line - extended beyond furthest throw
            val extendedDistance = maxDisplayDistance * 1.1f // Extend 10% beyond
            val leftLineEndX = centerX - sin(sectorAngleRadians) * extendedDistance * pixelsPerMeter
            val leftLineEndY = centerY - cos(sectorAngleRadians) * extendedDistance * pixelsPerMeter
            drawLine(
                color = sectorLineColor,
                start = Offset(centerX, centerY),
                end = Offset(leftLineEndX, leftLineEndY),
                strokeWidth = 3.dp.toPx()
            )
            
            // Right sector line - extended beyond furthest throw  
            val rightLineEndX = centerX + sin(sectorAngleRadians) * extendedDistance * pixelsPerMeter
            val rightLineEndY = centerY - cos(sectorAngleRadians) * extendedDistance * pixelsPerMeter
            drawLine(
                color = sectorLineColor,
                start = Offset(centerX, centerY),
                end = Offset(rightLineEndX, rightLineEndY),
                strokeWidth = 3.dp.toPx()
            )
        }
        
        // Center line (0° reference) - extended
        val extendedCenterDistance = maxDisplayDistance * 1.1f
        drawLine(
            color = sectorLineColor,
            start = Offset(centerX, centerY),
            end = Offset(centerX, centerY - extendedCenterDistance * pixelsPerMeter),
            strokeWidth = 3.dp.toPx()
        )
        
        // Draw throw points using actual coordinates
        heatMapData.coordinates.forEach { coord ->
            // Map actual X,Y coordinates to screen position
            val throwX = centerX + coord.x.toFloat() * pixelsPerMeter
            val throwY = centerY - coord.y.toFloat() * pixelsPerMeter  // Use actual Y coordinate
            
            // Draw all throws
            drawCircle(
                color = throwPointColor,
                radius = 6.dp.toPx(),
                center = Offset(throwX, throwY),
                style = androidx.compose.ui.graphics.drawscope.Fill
            )
            // White border for visibility
            drawCircle(
                color = androidx.compose.ui.graphics.Color.White,
                radius = 6.dp.toPx(),
                center = Offset(throwX, throwY),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        
        // Draw circle center
        drawCircle(
            color = centerColor,
            radius = 12.dp.toPx(),
            center = Offset(centerX, centerY),
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
        
        // Draw target circle (throwing circle outline) - actual size
        val targetRadiusPixels = heatMapData.targetRadius.toFloat() * pixelsPerMeter
        drawCircle(
            color = centerColor,
            radius = targetRadiusPixels,
            center = Offset(centerX, centerY),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
fun HeatMapDistanceLabels(
    heatMapData: HeatMapData,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()
        val centerX = canvasWidth / 2f
        val centerY = canvasHeight * 0.9f
        
        // Calculate scale matching the heat map
        val maxDisplayDistance = heatMapData.optimalScale.toFloat()
        val pixelsPerMeter = (canvasHeight * 0.85f) / maxDisplayDistance
        
        // Distance label positioning - 45 degrees (northeast)
        val labelAngleRadians = Math.toRadians(45.0) // 45 degrees in radians
        val labelOffsetX = kotlin.math.sin(labelAngleRadians).toFloat()
        val labelOffsetY = -kotlin.math.cos(labelAngleRadians).toFloat() // Negative for upward direction
        
        // Event-specific arc intervals
        val arcInterval = when (heatMapData.circleType) {
            "SHOT" -> 2
            else -> 10
        }
        
        // Calculate distance range based on throws
        val distances = if (heatMapData.coordinates.isNotEmpty()) {
            val throwDistances = heatMapData.coordinates.map { it.distance.toInt() }
            val minDist = throwDistances.minOrNull() ?: arcInterval
            val maxDist = throwDistances.maxOrNull() ?: maxDisplayDistance.toInt()
            (minDist / arcInterval * arcInterval)..(maxDist / arcInterval * arcInterval + arcInterval) step arcInterval
        } else {
            arcInterval..maxDisplayDistance.toInt() step arcInterval
        }
        
        // Create distance labels at 45-degree positions
        distances.forEach { distance ->
            val radius = distance * pixelsPerMeter
            if (radius < canvasHeight * 0.8f) {
                // Calculate label position at 45-degree angle
                val labelX = centerX + labelOffsetX * radius
                val labelY = centerY + labelOffsetY * radius
                
                // Position the text composable
                Text(
                    text = distance.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF666666),
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (labelX - 15.dp.toPx()).toInt(), // Center horizontally (-15dp for text width estimate)
                                (labelY - 10.dp.toPx()).toInt()  // Center vertically (-10dp for text height estimate)
                            )
                        }
                )
            }
        }
    }
}

// Detected USB Device info
data class DetectedDevice(
    val vendorId: Int,
    val productId: Int,
    val deviceName: String,
    val serialPath: String
)

// Complete App State matching original
data class AppState(
    val currentScreen: String = "SELECT_EVENT_TYPE",
    val eventType: String? = null,
    val isDemoMode: Boolean = false, // Live mode by default like original
    val measurement: String = "",
    val windMeasurement: String = "",
    val isLoading: Boolean = false,
    val devices: DeviceConfig = DeviceConfig(),
    val calibration: CalibrationState = CalibrationState(),
    val calibrationHistory: List<CalibrationRecord> = emptyList(),
    val throwCoordinates: List<ThrowCoordinate> = emptyList(),
    val deviceSetupVisible: Boolean = false,
    val selectedDeviceForConfig: String? = null,
    val heatMapVisible: Boolean = false,
    val connectedDevice: UsbDevice? = null,
    val detectedDevices: List<DetectedDevice> = emptyList(),
    val errorMessage: String? = null,
    val errorTitle: String? = null,
    val settings: AppSettings = AppSettings(),
    // DEBUG: Serial Communication Log (REMOVE WHEN DEBUG COMPLETE)
    val serialCommLog: List<SerialCommLogEntry> = emptyList(),
    val debugCommStreamVisible: Boolean = false
)

class AppViewModel(context: android.content.Context, private val fastInit: Boolean = false) : androidx.lifecycle.ViewModel() {
    private val _uiState = MutableStateFlow(AppState())
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()
    
    // Clean EDM Interface for device communication - lazy init to avoid blocking startup
    private var edmInterface: EDMInterface? = null
    
    // Legacy EDM Module for backward compatibility during transition  
    private var edmModule: EDMModule? = null
    
    // Context reference for deferred initialization
    private val appContext = context.applicationContext
    
    // SharedPreferences for persistent storage - use Application context to avoid memory leaks
    private val sharedPrefs = context.applicationContext.getSharedPreferences("PolyFieldCalibrations", android.content.Context.MODE_PRIVATE)
    private val settingsPrefs = context.applicationContext.getSharedPreferences("PolyFieldSettings", android.content.Context.MODE_PRIVATE)
    
    init {
        if (fastInit) {
            // Fast initialization - only essential components
            android.util.Log.d("PolyField", "AppViewModel fast initialization - heavy components deferred")
            
            // Load only critical settings immediately (blocking)
            loadCriticalSettingsFromDisk()
            
            // Defer heavy operations to background
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                // Load remaining settings and calibration history
                loadRemainingSettingsFromDisk()
                loadCalibrationHistoryFromDisk()
                
                // Heavy modules initialized only when accessed
            }
        } else {
            // Traditional full initialization for backward compatibility
            viewModelScope.launch {
                // Initialize heavy modules first
                edmInterface = EDMInterface(appContext)
                edmModule = EDMModule(appContext)
                
                // Load settings 
                loadSettingsFromDisk()
                
                // Then load calibration history
                loadCalibrationHistoryFromDisk()
                
                // Setup debug logger after startup
                setupDebugLogger()
            }
            
            android.util.Log.d("PolyField", "AppViewModel initialized - Settings will load asynchronously")
        }
    }
    
    // Null-safe getters for modules with lazy initialization
    private fun getEDMInterface(): EDMInterface {
        return edmInterface ?: run {
            android.util.Log.d("PolyField", "Lazy initializing EDMInterface...")
            EDMInterface(appContext).also { 
                edmInterface = it
                // Setup debug logger after EDM initialization
                if (fastInit) {
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                        setupDebugLogger()
                    }
                }
            }
        }
    }
    
    private fun getEDMModule(): EDMModule {
        return edmModule ?: run {
            android.util.Log.d("PolyField", "Lazy initializing EDMModule...")
            EDMModule(appContext).also { 
                edmModule = it
                // Setup debug logger after EDM initialization
                if (fastInit) {
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                        setupDebugLogger()
                    }
                }
            }
        }
    }
    
    private fun setupDebugLogger() {
        // DEBUG: Setup serial communication logging (REMOVE WHEN DEBUG COMPLETE)
        getEDMModule().setDebugLogger { direction, data, dataHex, success, error ->
            val entry = SerialCommLogEntry(
                direction = direction,
                data = data,
                dataHex = dataHex,
                success = success,
                error = error
            )
            addSerialCommLog(entry)
        }
    }
    
    /**
     * Load calibration history from persistent storage
     */
    private fun loadCalibrationHistoryFromDisk() {
        try {
            val jsonString = sharedPrefs.getString("calibration_history", "[]") ?: "[]"
            val jsonArray = org.json.JSONArray(jsonString)
            val calibrations = mutableListOf<CalibrationRecord>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                
                // Parse station coordinates
                val stationCoordinates = if (jsonObj.has("stationCoordinates")) {
                    val coordsObj = jsonObj.getJSONObject("stationCoordinates")
                    Pair(coordsObj.getDouble("x"), coordsObj.getDouble("y"))
                } else null
                
                // Parse edge result
                val edgeResult = if (jsonObj.has("edgeResult")) {
                    val edgeObj = jsonObj.getJSONObject("edgeResult")
                    val measurementsArray = edgeObj.getJSONArray("measurements")
                    val measurements = mutableListOf<Double>()
                    for (j in 0 until measurementsArray.length()) {
                        measurements.add(measurementsArray.getDouble(j))
                    }
                    
                    EdgeResult(
                        averageRadius = edgeObj.getDouble("averageRadius"),
                        toleranceCheck = edgeObj.getBoolean("toleranceCheck"),
                        measurements = measurements,
                        deviation = edgeObj.getDouble("deviation")
                    )
                } else null
                
                // Parse sector line coordinates
                val sectorLineCoordinates = if (jsonObj.has("sectorLineCoordinates")) {
                    val coordsObj = jsonObj.getJSONObject("sectorLineCoordinates")
                    Pair(coordsObj.getDouble("x"), coordsObj.getDouble("y"))
                } else null
                
                val calibration = CalibrationRecord(
                    id = jsonObj.getString("id"),
                    circleType = jsonObj.getString("circleType"),
                    targetRadius = jsonObj.getDouble("targetRadius"),
                    timestamp = jsonObj.getLong("timestamp"),
                    dateString = jsonObj.getString("dateString"),
                    stationCoordinates = stationCoordinates,
                    edgeResult = edgeResult,
                    sectorLineDistance = if (jsonObj.has("sectorLineDistance")) jsonObj.getDouble("sectorLineDistance") else null,
                    sectorLineCoordinates = sectorLineCoordinates
                )
                
                calibrations.add(calibration)
            }
            
            // Filter to today's calibrations and limit to last 2
            val todaysCalibrations = calibrations
                .filter { it.isFromToday() }
                .sortedByDescending { it.timestamp }
                .take(2)
            
            _uiState.value = _uiState.value.copy(calibrationHistory = todaysCalibrations)
            
            android.util.Log.d("PolyField", "Loaded ${todaysCalibrations.size} today's calibrations from persistent storage")
            
        } catch (e: Exception) {
            android.util.Log.e("PolyField", "Failed to load calibration history", e)
            _uiState.value = _uiState.value.copy(calibrationHistory = emptyList())
        }
    }
    
    fun updateScreen(screen: String) {
        _uiState.value = _uiState.value.copy(currentScreen = screen)
        
        // Automatically refresh devices when navigating to device setup in live mode
        if (screen == "DEVICE_SETUP" && !_uiState.value.isDemoMode) {
            android.util.Log.d("PolyField", "Auto-refreshing devices on DEVICE_SETUP navigation")
            refreshUsbDevices()
        }
    }
    
    fun updateEventType(eventType: String) {
        _uiState.value = _uiState.value.copy(eventType = eventType)
    }
    
    fun updateCircleType(circleType: String) {
        val targetRadius = getDemoUKARadius(circleType)
        _uiState.value = _uiState.value.copy(
            calibration = _uiState.value.calibration.copy(
                circleType = circleType,
                targetRadius = targetRadius
            )
        )
    }
    
    fun toggleDemoMode() {
        val currentMode = _uiState.value.isDemoMode
        val newMode = !currentMode
        _uiState.value = _uiState.value.copy(isDemoMode = newMode)
        
        // Demo mode is now handled natively in Kotlin
        android.util.Log.d("PolyField", "Demo mode set to: $newMode")
        
        if (currentMode) { // Switching to live mode
            resetCalibration()
            if (_uiState.value.eventType == "Throws") {
                updateScreen("DEVICE_SETUP")
            }
        }
    }
    
    fun updateSettings(settings: AppSettings) {
        _uiState.value = _uiState.value.copy(settings = settings)
        
        // Update EDM module with selected device
        getEDMModule().setSelectedEDMDevice(settings.selectedEDMDevice)
        android.util.Log.d("PolyField", "Updated EDM device to: ${settings.selectedEDMDevice.displayName}")
        
        // Save settings to persistent storage
        saveSettingsToDisk()
    }
    
    // DEBUG: Serial Communication Logging Functions (REMOVE WHEN DEBUG COMPLETE)
    fun addSerialCommLog(entry: SerialCommLogEntry) {
        val currentLog = _uiState.value.serialCommLog
        val updatedLog = (currentLog + entry).takeLast(100) // Keep last 100 entries
        _uiState.value = _uiState.value.copy(serialCommLog = updatedLog)
    }
    
    fun toggleDebugCommStream() {
        updateScreen("DEBUG_SERIAL_COMM")
    }
    
    fun clearSerialCommLog() {
        _uiState.value = _uiState.value.copy(serialCommLog = emptyList())
    }
    
    fun updateDetectedDevices(devices: List<DetectedDevice>) {
        _uiState.value = _uiState.value.copy(detectedDevices = devices)
    }
    
    fun getUsbDevices(): Map<String, Any> {
        return getEDMModule().listUsbDevices()
    }
    
    fun refreshUsbDevices() {
        android.util.Log.d("PolyField", "Manual USB refresh requested")
        val usbDevicesResult = getUsbDevices()
        android.util.Log.d("PolyField", "USB refresh result: $usbDevicesResult")
        
        @Suppress("UNCHECKED_CAST")
        val usbDevices = (usbDevicesResult["ports"] as? List<Map<String, Any>>) ?: emptyList()
        
        val detectedDevices = if (usbDevices.isEmpty()) {
            // In live mode, show no devices if none detected
            // In demo mode, add a test device for UI verification
            if (_uiState.value.isDemoMode) {
                android.util.Log.d("PolyField", "Demo mode: No real USB devices found - adding test device for UI verification")
                listOf(
                    DetectedDevice(
                        vendorId = 1027,  // FTDI VID
                        productId = 24577, // FTDI PID
                        deviceName = "Test FTDI Device - FT232R USB UART (VID:0403 PID:6001)",
                        serialPath = "/dev/ttyUSB0"
                    )
                )
            } else {
                android.util.Log.d("PolyField", "Live mode: No USB devices found - showing empty list")
                emptyList()
            }
        } else {
            usbDevices.mapIndexed { index, deviceInfo ->
                DetectedDevice(
                    vendorId = deviceInfo["vendorId"] as? Int ?: 0,
                    productId = deviceInfo["productId"] as? Int ?: 0,
                    deviceName = deviceInfo["description"] as? String ?: "USB Device ${index + 1}",
                    serialPath = deviceInfo["port"] as? String ?: "/dev/ttyUSB$index"
                )
            }
        }
        
        android.util.Log.d("PolyField", "Detected ${detectedDevices.size} USB devices after refresh")
        updateDetectedDevices(detectedDevices)
        
        // Auto-connect if only one device detected after refresh
        if (!_uiState.value.isDemoMode && detectedDevices.size == 1) {
            android.util.Log.d("PolyField", "Single device detected - auto-connecting")
            autoConnectToEDMDevices(detectedDevices)
        }
    }
    
    /**
     * Auto-connect to detected EDM devices in live mode
     */
    private fun autoConnectToEDMDevices(detectedDevices: List<DetectedDevice>) {
        viewModelScope.launch {
            // Check if EDM is already connected
            if (_uiState.value.devices.edm.connected) {
                android.util.Log.d("PolyField", "EDM already connected, skipping auto-connect")
                return@launch
            }
            
            // Find EDM devices (CH340 or FTDI)
            val edmDevices = detectedDevices.filter { device ->
                // CH340 (Mato EDM): VID:1A86 (6790) PID:7523 (29987)
                // FTDI devices: VID:0403 (1027) 
                (device.vendorId == 6790 && device.productId == 29987) || // CH340
                (device.vendorId == 1027) // FTDI
            }
            
            if (edmDevices.isNotEmpty()) {
                val edmDevice = edmDevices.first()
                android.util.Log.d("PolyField", "Auto-connecting to EDM device: ${edmDevice.deviceName}")
                
                try {
                    val result = getEDMModule().connectUsbDevice("edm", edmDevice.serialPath)
                    if (result["success"] == true) {
                        android.util.Log.d("PolyField", "Auto-connect successful: ${result["message"]}")
                        
                        // Device is now registered directly with native Kotlin EDMModule
                        android.util.Log.d("PolyField", "Device registered with native EDMModule")
                        
                        // Update device with connection and real device name
                        val deviceState = DeviceState(
                            connected = true,
                            connectionType = "serial",
                            serialPort = edmDevice.serialPath,
                            deviceName = edmDevice.deviceName
                        )
                        updateDeviceConfig("edm", deviceState)
                        
                        // Log successful auto-connection
                        android.util.Log.d("PolyField", "Successfully auto-connected to ${edmDevice.deviceName}")
                    } else {
                        android.util.Log.w("PolyField", "Auto-connect failed: ${result["error"]}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PolyField", "Auto-connect error", e)
                }
            }
        }
    }
    
    fun updateDevice(device: UsbDevice?, isDemoMode: Boolean) {
        _uiState.value = _uiState.value.copy(
            connectedDevice = device,
            isDemoMode = isDemoMode
        )
    }
    
    fun updateDeviceConnection(deviceType: String, connected: Boolean) {
        if (connected && !_uiState.value.isDemoMode) {
            // In live mode, attempt actual device connection
            // Only set connected=true if connection succeeds
            connectToRealDevice(deviceType)
        } else if (connected && _uiState.value.isDemoMode) {
            // In demo mode, immediately set connected state without real device connection
            updateDeviceConnectionState(deviceType, true)
            android.util.Log.d("PolyField", "Demo mode: Set $deviceType to connected")
        } else if (!connected) {
            // Disconnect device
            getEDMModule().disconnectDevice(deviceType)
            // Update state to disconnected
            updateDeviceConnectionState(deviceType, false)
        }
    }
    
    private fun connectToRealDevice(deviceType: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                // Find the detected device for this device type
                val detectedDevices = _uiState.value.detectedDevices
                val edmDevice = detectedDevices.find { device ->
                    // Find EDM device (CH340 or FTDI)
                    (device.vendorId == 6790 && device.productId == 29987) || // CH340
                    (device.vendorId == 1027) // FTDI
                }
                
                if (edmDevice == null) {
                    android.util.Log.e("PolyField", "No compatible EDM device found for connection")
                    updateDeviceConnectionState(deviceType, false)
                    return@launch
                }
                
                android.util.Log.d("PolyField", "Connecting to detected EDM device: ${edmDevice.deviceName} at ${edmDevice.serialPath}")
                
                val result = getEDMModule().connectUsbDevice(deviceType, edmDevice.serialPath)
                android.util.Log.d("PolyField", "Device connection result: $result")
                
                // Update connection status based on result
                val success = result["success"] as? Boolean == true
                if (success) {
                    android.util.Log.d("PolyField", "Device connection successful")
                    
                    // Register device with Go Mobile for EDM operations
                    if (deviceType == "edm") {
                        try {
                            val deviceName = result["edmDevice"] as? String ?: edmDevice.deviceName
                            // Device registration handled natively by EDMModule
                            android.util.Log.d("PolyField", "Device registered with native EDMModule: $deviceName")
                        } catch (e: Exception) {
                            android.util.Log.w("PolyField", "Device registration issue (continuing): ${e.message}")
                        }
                    }
                    
                    // Update device state with successful connection
                    val deviceState = DeviceState(
                        connected = true,
                        connectionType = result["connectionType"] as? String ?: "serial",
                        serialPort = edmDevice.serialPath,
                        deviceName = result["edmDevice"] as? String ?: edmDevice.deviceName
                    )
                    updateDeviceConfig(deviceType, deviceState)
                    android.util.Log.d("PolyField", "Device state updated to connected")
                } else {
                    val error = result["error"] as? String ?: "Unknown error"
                    android.util.Log.e("PolyField", "Device connection failed: $error")
                    // Reset connection state on failure
                    updateDeviceConnectionState(deviceType, false)
                }
            } catch (e: Exception) {
                android.util.Log.e("PolyField", "Device connection error", e)
                updateDeviceConnectionState(deviceType, false)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    private fun updateDeviceConnectionState(deviceType: String, connected: Boolean) {
        val devices = _uiState.value.devices
        val updatedDevices = when (deviceType) {
            "edm" -> devices.copy(edm = devices.edm.copy(connected = connected))
            "wind" -> devices.copy(wind = devices.wind.copy(connected = connected))
            "scoreboard" -> devices.copy(scoreboard = devices.scoreboard.copy(connected = connected))
            else -> devices
        }
        _uiState.value = _uiState.value.copy(devices = updatedDevices)
    }
    
    fun updateDeviceConfig(deviceType: String, deviceState: DeviceState) {
        val devices = _uiState.value.devices
        val updatedDevices = when (deviceType) {
            "edm" -> devices.copy(edm = deviceState)
            "wind" -> devices.copy(wind = deviceState)
            "scoreboard" -> devices.copy(scoreboard = deviceState)
            else -> devices
        }
        _uiState.value = _uiState.value.copy(devices = updatedDevices)
    }
    
    fun setCentre() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        if (_uiState.value.isDemoMode) {
            // Demo mode - simulate centre setting
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val stationX = (kotlin.random.Random.nextDouble() - 0.5) * 20 // ±10m
            val stationY = (kotlin.random.Random.nextDouble() - 0.5) * 20 // ±10m
            
            _uiState.value = _uiState.value.copy(
                calibration = _uiState.value.calibration.copy(
                    centreSet = true,
                    centreTimestamp = timestamp,
                    stationCoordinates = Pair(stationX, stationY)
                ),
                isLoading = false
            )
        } else {
            // Live mode - ABSOLUTELY NO SIMULATION ALLOWED
            
            // DEBUG: Check UI connection state
            android.util.Log.d("PolyField", "=== SET CENTRE DEBUG ===")
            android.util.Log.d("PolyField", "UI State EDM connected: ${_uiState.value.devices.edm.connected}")
            android.util.Log.d("PolyField", "UI State EDM device: ${_uiState.value.devices.edm}")
            
            // First check: Is device connection state correct?
            if (!_uiState.value.devices.edm.connected) {
                showErrorDialog(
                    "Device Error",
                    "EDM device is not connected. Cannot set centre in live mode."
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
                return
            }
            
            // Device is connected in UI and responding - proceed with measurement
            
            // Second check: Is device actually physically present?
            val usbDevices = getUsbDevices()
            @Suppress("UNCHECKED_CAST")
            val deviceList = (usbDevices["ports"] as? List<Map<String, Any>>) ?: emptyList()
            val hasPhysicalEDMDevice = deviceList.any { device ->
                val isEDMDevice = device["edmDevice"] as? Boolean ?: false
                isEDMDevice
            }
            
            if (!hasPhysicalEDMDevice) {
                // Device shows connected but no physical device found - this is the bug!
                showErrorDialog(
                    "Critical Error",
                    "EDM shows connected but no physical device found. Disconnecting to prevent simulation."
                )
                updateDeviceConnection("edm", false) // Force disconnect
                _uiState.value = _uiState.value.copy(isLoading = false)
                return
            }
            
            viewModelScope.launch {
                try {
                    android.util.Log.e("PolyField", "🔵 STARTING Set Centre with Go Mobile...")
                    android.util.Log.e("PolyField", "🔵 EDM Device: ${_uiState.value.devices.edm.deviceName}, Port: ${_uiState.value.devices.edm.serialPort}")
                    
                    // Use clean EDM interface for centre setting
                    android.util.Log.d("PolyField", "🔵 Calling setCentreClean")
                    
                    setCentreClean()
                    return@launch
                } catch (e: Exception) {
                    android.util.Log.e("PolyField", "Centre setting error", e)
                    showErrorDialog(
                        "Centre Set Failed", 
                        e.message ?: "Unknown error occurred while setting centre"
                    )
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }
    
    fun verifyEdge() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        if (_uiState.value.isDemoMode) {
            // Demo mode - simulate edge verification
            val targetRadius = _uiState.value.calibration.targetRadius
            val measurements = List(5) { 
                targetRadius + (kotlin.random.Random.nextDouble() - 0.5) * 0.01 // ±5mm variation
            }
            val averageRadius = measurements.average()
            val deviation = kotlin.math.abs(averageRadius - targetRadius)
            // Different tolerances per UKA/WA rules
            val tolerance = if (_uiState.value.calibration.circleType == "JAVELIN_ARC") 0.010 else 0.005 // 10mm for javelin, 5mm for others
            val toleranceCheck = deviation <= tolerance
            
            val edgeResult = EdgeResult(
                toleranceCheck = toleranceCheck,
                measurements = measurements,
                averageRadius = averageRadius,
                deviation = deviation
            )
            
            _uiState.value = _uiState.value.copy(
                calibration = _uiState.value.calibration.copy(
                    edgeVerified = true,  // Edge verification completed successfully (measurement obtained)
                    edgeResult = edgeResult  // Contains toleranceCheck for pass/fail status
                ),
                isLoading = false
            )
        } else {
            // Live mode - ABSOLUTELY NO SIMULATION ALLOWED
            
            // First check: Is device connection state correct?
            if (!_uiState.value.devices.edm.connected) {
                showErrorDialog(
                    "Device Error",
                    "EDM device is not connected. Cannot verify edge in live mode."
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
                return
            }
            
            // Second check: Is device actually physically present?
            val usbDevices = getUsbDevices()
            @Suppress("UNCHECKED_CAST")
            val deviceList = (usbDevices["ports"] as? List<Map<String, Any>>) ?: emptyList()
            val hasPhysicalEDMDevice = deviceList.any { device ->
                val isEDMDevice = device["edmDevice"] as? Boolean ?: false
                isEDMDevice
            }
            
            if (!hasPhysicalEDMDevice) {
                showErrorDialog(
                    "Critical Error",
                    "EDM shows connected but no physical device found. Disconnecting to prevent simulation."
                )
                updateDeviceConnection("edm", false) // Force disconnect
                _uiState.value = _uiState.value.copy(isLoading = false)
                return
            }
            
            viewModelScope.launch {
                try {
                    val targetRadius = _uiState.value.calibration.targetRadius
                    android.util.Log.d("PolyField", "Verifying edge with Go Mobile trigonometric calculations...")
                    android.util.Log.d("PolyField", "Target radius: ${targetRadius}m")
                    
                    // Use clean EDM interface for edge verification
                    verifyEdgeClean()
                    return@launch
                } catch (e: Exception) {
                    android.util.Log.e("PolyField", "Edge verification error", e)
                    showErrorDialog(
                        "Edge Verification Failed",
                        e.message ?: "Unknown error occurred while verifying edge"
                    )
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }
    
    fun measureSectorLine() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        if (_uiState.value.isDemoMode) {
            // Demo mode - simulate sector line measurement
            // Sector angles: Shot/Discus/Hammer = 17.46° each side, Javelin = 14.48° each side
            val sectorAngleDegrees = if (_uiState.value.calibration.circleType == "JAVELIN_ARC") 14.48 else 17.46
            val distanceFromCentre = 15.0 + kotlin.random.Random.nextDouble() * 10.0 // 15-25m from centre
            
            val sectorLineCoordinates = Pair(
                distanceFromCentre * kotlin.math.sin(Math.toRadians(sectorAngleDegrees)),
                distanceFromCentre * kotlin.math.cos(Math.toRadians(sectorAngleDegrees))
            )
            
            _uiState.value = _uiState.value.copy(
                calibration = _uiState.value.calibration.copy(
                    sectorLineSet = true,
                    sectorLineDistance = distanceFromCentre,
                    sectorLineCoordinates = sectorLineCoordinates
                ),
                isLoading = false
            )
            
            // Save complete calibration to history
            saveCurrentCalibrationToHistory()
        } else {
            // Live mode - use real EDM device for sector line measurement
            if (!_uiState.value.devices.edm.connected) {
                showErrorDialog(
                    "Device Error",
                    "EDM device is not connected. Please connect a device first."
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
                return
            }
            
            viewModelScope.launch {
                try {
                    android.util.Log.d("PolyField", "Measuring sector line with Go Mobile calculations...")
                    
                    // Use clean EDM interface for sector line measurement
                    sectorCheckClean()
                    return@launch
                } catch (e: Exception) {
                    android.util.Log.e("PolyField", "Sector line measurement error", e)
                    showErrorDialog(
                        "Sector Line Measurement Failed",
                        e.message ?: "Unknown error occurred while measuring sector line"
                    )
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }
    
    fun measureDistance() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        if (_uiState.value.isDemoMode) {
            // Demo mode - use simulated values
            android.util.Log.d("PolyField", "🔵 measureDistance DEMO - Using single read mode")
            val distance = generateDemoThrow()
            _uiState.value = _uiState.value.copy(
                measurement = String.format(java.util.Locale.UK, "%.2f m", distance),
                isLoading = false
            )
            
            // Store throw coordinate - generate realistic within sector lines
            val throwCoord = generateDemoThrowCoordinate(distance)
            
            
            _uiState.value = _uiState.value.copy(
                throwCoordinates = _uiState.value.throwCoordinates + throwCoord
            )
        } else {
            // Live mode - use real EDM device
            if (!_uiState.value.devices.edm.connected) {
                showErrorDialog(
                    "Device Error",
                    "EDM device is not connected. Please connect a device first."
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
                return
            }
            
            viewModelScope.launch {
                try {
                    android.util.Log.d("PolyField", "Measuring distance: USB-serial \u2192 EDM \u2192 Translation \u2192 Go Mobile \u2192 Display")
                    
                    // Use Go Mobile's measureThrow function which handles the complete flow:
                    // 1. Gets EDM data from our serial communication
                    // 2. Applies proper trigonometric calculations (horizontal distance from centre minus radius)
                    // 3. Returns the calculated throw distance and coordinates
                    android.util.Log.d("PolyField", "🔵 measureDistance LIVE - Using clean EDM interface")
                    measureDistanceClean()
                    return@launch
                } catch (e: Exception) {
                    android.util.Log.e("PolyField", "Measurement error", e)
                    showErrorDialog(
                        "Device Error",
                        "Failed to measure distance: ${e.message}"
                    )
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }
    
    fun measureWind() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        if (_uiState.value.isDemoMode) {
            // Demo mode - use simulated values
            val windSpeed = (kotlin.random.Random.nextDouble() - 0.5) * 4.0 // ±2 m/s
            _uiState.value = _uiState.value.copy(
                windMeasurement = String.format(java.util.Locale.UK, "%s%.1f m/s", if (windSpeed > 0) "+" else "", windSpeed),
                isLoading = false
            )
        } else {
            // Live mode - use real wind gauge
            if (!_uiState.value.devices.wind.connected) {
                showErrorDialog(
                    "Device Error",
                    "Wind gauge is not connected. Please connect a device first."
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
                return
            }
            
            viewModelScope.launch {
                try {
                    android.util.Log.d("PolyField", "Getting real wind reading...")
                    val reading = getEDMModule().measureWind()
                    
                    if (reading.success && reading.windSpeed != null) {
                        val windSpeed = reading.windSpeed
                        android.util.Log.d("PolyField", "Wind reading successful: ${windSpeed}m/s")
                        
                        _uiState.value = _uiState.value.copy(
                            windMeasurement = String.format(java.util.Locale.UK, "%s%.1f m/s", if (windSpeed > 0) "+" else "", windSpeed),
                            isLoading = false
                        )
                    } else {
                        android.util.Log.e("PolyField", "Wind reading failed: ${reading.error}")
                        showErrorDialog(
                            "Measurement Error", 
                            "Failed to get wind measurement from device"
                        )
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PolyField", "Wind measurement error", e)
                    showErrorDialog(
                        "Device Error",
                        "Failed to measure wind: ${e.message}"
                    )
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }
    
    fun resetCalibration() {
        _uiState.value = _uiState.value.copy(
            calibration = CalibrationState()
        )
    }
    
    fun resetEdgeVerification() {
        val currentCalibration = _uiState.value.calibration
        _uiState.value = _uiState.value.copy(
            calibration = currentCalibration.copy(
                edgeVerified = false,
                edgeResult = null
            )
        )
        android.util.Log.d("PolyField", "Edge verification reset - centre preserved")
    }
    
    // ========== CLEAN EDM INTERFACE FUNCTIONS ==========
    // These use the new simplified EDMInterface for clean communication
    
    /**
     * Set centre using clean EDM interface
     */
    fun setCentreClean() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        viewModelScope.launch {
            try {
                val circleType = _uiState.value.calibration.circleType
                android.util.Log.d("PolyField", "Setting centre with clean EDM interface for circle: $circleType")
                
                val result = getEDMInterface().setCentre("edm", circleType)
                if (result.isSuccess) {
                    val data = result.getOrThrow()
                    android.util.Log.d("PolyField", "Clean set centre successful: $data")
                    
                    val edmPosition = data["edmPosition"] as Map<String, Double>
                    val stationX = edmPosition["x"]!!
                    val stationY = edmPosition["y"]!!
                    
                    _uiState.value = _uiState.value.copy(
                        calibration = _uiState.value.calibration.copy(
                            centreSet = true,
                            stationCoordinates = Pair(stationX, stationY)
                        ),
                        isLoading = false
                    )
                } else {
                    android.util.Log.e("PolyField", "Clean set centre failed", result.exceptionOrNull())
                    showErrorDialog("Calibration Error", result.exceptionOrNull()?.message ?: "Failed to set centre")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                android.util.Log.e("PolyField", "Set centre error", e)
                showErrorDialog("Device Error", "Failed to set centre: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    /**
     * Verify edge using clean EDM interface
     */
    fun verifyEdgeClean() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        viewModelScope.launch {
            try {
                android.util.Log.d("PolyField", "Verifying edge with clean EDM interface")
                
                val result = getEDMInterface().verifyEdge("edm")
                if (result.isSuccess) {
                    val data = result.getOrThrow()
                    android.util.Log.d("PolyField", "Clean verify edge successful: $data")
                    
                    val toleranceCheck = data["isInTolerance"] as Boolean
                    val measuredRadius = data["measuredRadius"] as Double
                    val differenceMm = data["differenceMm"] as Double
                    val message = data["message"] as String
                    
                    val edgeResult = EdgeResult(
                        toleranceCheck = toleranceCheck,
                        averageRadius = measuredRadius,
                        deviation = differenceMm / 1000.0 // Convert mm to meters
                    )
                    
                    _uiState.value = _uiState.value.copy(
                        calibration = _uiState.value.calibration.copy(
                            edgeVerified = true,
                            edgeResult = edgeResult
                        ),
                        isLoading = false
                    )
                } else {
                    android.util.Log.e("PolyField", "Clean verify edge failed", result.exceptionOrNull())
                    showErrorDialog("Verification Error", result.exceptionOrNull()?.message ?: "Failed to verify edge")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                android.util.Log.e("PolyField", "Verify edge error", e)
                showErrorDialog("Device Error", "Failed to verify edge: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    /**
     * Measure throw distance using clean EDM interface
     */
    fun measureDistanceClean() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        viewModelScope.launch {
            try {
                android.util.Log.d("PolyField", "Measuring distance with clean EDM interface")
                
                val result = getEDMInterface().measure("edm")
                if (result.isSuccess) {
                    val data = result.getOrThrow()
                    android.util.Log.d("PolyField", "Clean measure successful: $data")
                    
                    val throwDistance = data["throwDistance"] as Double
                    val throwCoordinates = data["throwCoordinates"] as Map<String, Double>
                    val measurementText = data["measurement"] as String
                    
                    // Create ThrowCoordinate for the measurement
                    val throwCoord = ThrowCoordinate(
                        x = throwCoordinates["x"]!!,
                        y = throwCoordinates["y"]!!,
                        distance = throwDistance,
                        round = 1,
                        attemptNumber = 1,
                        isValid = true
                    )
                    
                    _uiState.value = _uiState.value.copy(
                        measurement = measurementText,
                        throwCoordinates = listOf(throwCoord),
                        isLoading = false
                    )
                } else {
                    android.util.Log.e("PolyField", "Clean measure failed", result.exceptionOrNull())
                    showErrorDialog("Measurement Error", result.exceptionOrNull()?.message ?: "Failed to measure distance")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                android.util.Log.e("PolyField", "Measure distance error", e)
                showErrorDialog("Device Error", "Failed to measure distance: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    /**
     * Perform sector check using clean EDM interface
     */
    fun sectorCheckClean() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        viewModelScope.launch {
            try {
                android.util.Log.d("PolyField", "Performing sector check with clean EDM interface")
                
                val result = getEDMInterface().sectorCheck("edm")
                if (result.isSuccess) {
                    val data = result.getOrThrow()
                    android.util.Log.d("PolyField", "Clean sector check successful: $data")
                    
                    val sectorCoordinates = data["sectorCoordinates"] as Map<String, Double>
                    val distanceFromCenter = data["distanceFromCenter"] as Double
                    val measurement = data["measurement"] as String
                    val message = data["message"] as String
                    
                    _uiState.value = _uiState.value.copy(
                        calibration = _uiState.value.calibration.copy(
                            sectorLineSet = true,
                            sectorLineDistance = distanceFromCenter,
                            sectorLineCoordinates = Pair(sectorCoordinates["x"]!!, sectorCoordinates["y"]!!)
                        ),
                        isLoading = false
                    )
                } else {
                    android.util.Log.e("PolyField", "Clean sector check failed", result.exceptionOrNull())
                    showErrorDialog("Sector Check Error", result.exceptionOrNull()?.message ?: "Failed to perform sector check")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                android.util.Log.e("PolyField", "Sector check error", e)
                showErrorDialog("Device Error", "Failed to perform sector check: ${e.message}")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    // Calibration History Management
    fun saveCurrentCalibrationToHistory() {
        val currentCalibration = _uiState.value.calibration
        
        // Only save if we have meaningful calibration data
        if (currentCalibration.centreSet && currentCalibration.edgeVerified && currentCalibration.sectorLineSet) {
            val calibrationRecord = CalibrationRecord(
                circleType = currentCalibration.circleType,
                targetRadius = currentCalibration.targetRadius,
                stationCoordinates = currentCalibration.stationCoordinates,
                edgeResult = currentCalibration.edgeResult,
                sectorLineDistance = currentCalibration.sectorLineDistance,
                sectorLineCoordinates = currentCalibration.sectorLineCoordinates
            )
            
            // Add to history and keep only last 2 records
            val updatedHistory = (_uiState.value.calibrationHistory + calibrationRecord)
                .sortedByDescending { it.timestamp }
                .take(2)
            
            _uiState.value = _uiState.value.copy(
                calibrationHistory = updatedHistory
            )
            
            // Save to persistent storage
            saveCalibrationHistoryToDisk()
            
            android.util.Log.d("PolyField", "Saved calibration to history: ${calibrationRecord.getDisplayName()}")
        }
    }
    
    /**
     * Save calibration history to persistent storage
     */
    private fun saveCalibrationHistoryToDisk() {
        try {
            val calibrations = _uiState.value.calibrationHistory
            val jsonArray = org.json.JSONArray()
            
            calibrations.forEach { calibration ->
                val jsonObj = org.json.JSONObject().apply {
                    put("id", calibration.id)
                    put("circleType", calibration.circleType)
                    put("targetRadius", calibration.targetRadius)
                    put("timestamp", calibration.timestamp)
                    put("dateString", calibration.dateString)
                    
                    // Station coordinates
                    calibration.stationCoordinates?.let { coords ->
                        val coordsObj = org.json.JSONObject().apply {
                            put("x", coords.first)
                            put("y", coords.second)
                        }
                        put("stationCoordinates", coordsObj)
                    }
                    
                    // Edge result
                    calibration.edgeResult?.let { edge ->
                        val edgeObj = org.json.JSONObject().apply {
                            put("averageRadius", edge.averageRadius)
                            put("toleranceCheck", edge.toleranceCheck)
                            put("measurements", org.json.JSONArray(edge.measurements))
                            put("deviation", edge.deviation)
                        }
                        put("edgeResult", edgeObj)
                    }
                    
                    // Sector line data
                    calibration.sectorLineDistance?.let { distance ->
                        put("sectorLineDistance", distance)
                    }
                    
                    calibration.sectorLineCoordinates?.let { coords ->
                        val coordsObj = org.json.JSONObject().apply {
                            put("x", coords.first)
                            put("y", coords.second)
                        }
                        put("sectorLineCoordinates", coordsObj)
                    }
                }
                jsonArray.put(jsonObj)
            }
            
            sharedPrefs.edit { 
                putString("calibration_history", jsonArray.toString()) 
            }
                
            android.util.Log.d("PolyField", "Saved ${calibrations.size} calibrations to persistent storage")
            
        } catch (e: Exception) {
            android.util.Log.e("PolyField", "Failed to save calibration history", e)
        }
    }
    
    fun getTodaysCalibrations(): List<CalibrationRecord> {
        return _uiState.value.calibrationHistory
            .filter { it.isFromToday() && it.circleType == _uiState.value.calibration.circleType }
            .sortedByDescending { it.timestamp }
    }
    
    fun loadHistoricalCalibration(calibrationRecord: CalibrationRecord) {
        _uiState.value = _uiState.value.copy(
            calibration = _uiState.value.calibration.copy(
                circleType = calibrationRecord.circleType,
                targetRadius = calibrationRecord.targetRadius,
                centreSet = calibrationRecord.stationCoordinates != null,
                centreTimestamp = calibrationRecord.dateString,
                stationCoordinates = calibrationRecord.stationCoordinates,
                edgeVerified = calibrationRecord.edgeResult != null,
                edgeResult = calibrationRecord.edgeResult,
                sectorLineSet = calibrationRecord.sectorLineDistance != null,
                sectorLineDistance = calibrationRecord.sectorLineDistance,
                sectorLineCoordinates = calibrationRecord.sectorLineCoordinates,
                selectedHistoricalCalibration = calibrationRecord
            )
        )
        
        // Sync native calibration manager state when loading historical calibration
        if (calibrationRecord.stationCoordinates != null) {
            val stationX = calibrationRecord.stationCoordinates!!.first
            val stationY = calibrationRecord.stationCoordinates!!.second
            // Native calibration manager handles the state internally through the UI state
            android.util.Log.d("PolyField", "🔵 Historical calibration loaded - station coordinates: ($stationX, $stationY)")
            android.util.Log.d("PolyField", "🔵 Native calibration manager handles state internally")
        }
        
        android.util.Log.d("PolyField", "Loaded historical calibration: ${calibrationRecord.getDisplayName()}")
    }
    
    fun resetSession() {
        _uiState.value = _uiState.value.copy(
            throwCoordinates = emptyList(),
            measurement = "",
            windMeasurement = ""
        )
    }
    
    /**
     * Load settings from persistent storage
     */
    private fun loadSettingsFromDisk() {
        try {
            val serverIpAddress = settingsPrefs.getString("serverIpAddress", "192.168.0.90") ?: "192.168.0.90"
            val serverPort = settingsPrefs.getInt("serverPort", 8080)
            val isDemoMode = settingsPrefs.getBoolean("isDemoMode", false)
            
            val loadedSettings = _uiState.value.settings.copy(
                serverIpAddress = serverIpAddress,
                serverPort = serverPort
            )
            
            _uiState.value = _uiState.value.copy(
                settings = loadedSettings,
                isDemoMode = isDemoMode
            )
            
            android.util.Log.d("PolyField", "Loaded settings from disk - Server: $serverIpAddress:$serverPort")
            
        } catch (e: Exception) {
            android.util.Log.e("PolyField", "Error loading settings from disk: ${e.message}")
        }
    }
    
    /**
     * Load only critical settings immediately for fast startup
     */
    private fun loadCriticalSettingsFromDisk() {
        try {
            // Load only essential settings needed for immediate UI
            val isDemoMode = settingsPrefs.getBoolean("isDemoMode", false)
            
            _uiState.value = _uiState.value.copy(
                isDemoMode = isDemoMode
            )
            
            android.util.Log.d("PolyField", "Loaded critical settings - Demo mode: $isDemoMode")
            
        } catch (e: Exception) {
            android.util.Log.e("PolyField", "Error loading critical settings: ${e.message}")
        }
    }
    
    /**
     * Load remaining settings in background for fast startup
     */
    private fun loadRemainingSettingsFromDisk() {
        try {
            val serverIpAddress = settingsPrefs.getString("serverIpAddress", "192.168.0.90") ?: "192.168.0.90"
            val serverPort = settingsPrefs.getInt("serverPort", 8080)
            
            val loadedSettings = _uiState.value.settings.copy(
                serverIpAddress = serverIpAddress,
                serverPort = serverPort
            )
            
            _uiState.value = _uiState.value.copy(
                settings = loadedSettings
            )
            
            android.util.Log.d("PolyField", "Loaded remaining settings - Server: $serverIpAddress:$serverPort")
            
        } catch (e: Exception) {
            android.util.Log.e("PolyField", "Error loading remaining settings: ${e.message}")
        }
    }
    
    /**
     * Save settings to persistent storage
     */
    private fun saveSettingsToDisk() {
        try {
            with(settingsPrefs.edit()) {
                val settings = _uiState.value.settings
                val uiState = _uiState.value
                putString("serverIpAddress", settings.serverIpAddress)
                putInt("serverPort", settings.serverPort)
                putBoolean("isDemoMode", uiState.isDemoMode)
                apply()
            }
            
            android.util.Log.d("PolyField", "Saved settings to disk - Server: ${_uiState.value.settings.serverIpAddress}:${_uiState.value.settings.serverPort}")
            
        } catch (e: Exception) {
            android.util.Log.e("PolyField", "Error saving settings to disk: ${e.message}")
        }
    }
    
    fun toggleDeviceSetupModal(deviceType: String? = null) {
        _uiState.value = _uiState.value.copy(
            deviceSetupVisible = !_uiState.value.deviceSetupVisible,
            selectedDeviceForConfig = if (!_uiState.value.deviceSetupVisible) deviceType else null
        )
    }
    
    fun showErrorDialog(title: String, message: String) {
        _uiState.value = _uiState.value.copy(
            errorTitle = title,
            errorMessage = message
        )
    }
    
    fun dismissErrorDialog() {
        _uiState.value = _uiState.value.copy(
            errorTitle = null,
            errorMessage = null
        )
    }
    
    fun toggleHeatMap() {
        // Navigate to heat map screen
        _uiState.value = _uiState.value.copy(
            currentScreen = "HEAT_MAP"
        )
    }
    
    private fun generateDemoThrow(): Double {
        val baseDistance = when (_uiState.value.calibration.circleType) {
            "SHOT" -> 15.0 + kotlin.random.Random.nextDouble() * 8.0 // 15-23m
            "DISCUS" -> 45.0 + kotlin.random.Random.nextDouble() * 25.0 // 45-70m  
            "HAMMER" -> 55.0 + kotlin.random.Random.nextDouble() * 25.0 // 55-80m
            "JAVELIN_ARC" -> 60.0 + kotlin.random.Random.nextDouble() * 30.0 // 60-90m
            else -> 20.0 + kotlin.random.Random.nextDouble() * 10.0
        }
        return baseDistance
    }
    
    private fun generateDemoThrowCoordinate(distance: Double): ThrowCoordinate {
        // Generate throw within UKA/WA sector lines
        // Shot/Discus/Hammer: 34.92° total (17.46° each side), Javelin: 28.96° total (14.48° each side)
        val halfSectorAngleDegrees = if (_uiState.value.calibration.circleType == "JAVELIN_ARC") 14.48 else 17.46
        val maxSectorAngle = Math.toRadians(halfSectorAngleDegrees) // Half sector angle in radians
        
        // Random angle within the sector (can be negative for left side)
        val throwAngle = kotlin.random.Random.nextDouble() * 2 * maxSectorAngle - maxSectorAngle
        
        // Add some variation to the distance (±10% for realism)
        val distanceVariation = 0.9 + kotlin.random.Random.nextDouble() * 0.2 // 0.9 to 1.1
        val actualDistance = distance * distanceVariation
        
        // Calculate X (lateral) and Y (forward) coordinates
        val x = kotlin.math.sin(throwAngle) * actualDistance
        val y = kotlin.math.cos(throwAngle) * actualDistance
        
        return ThrowCoordinate(
            x = x,
            y = y,
            distance = actualDistance,
            round = 1, // Default round 
            attemptNumber = 1, // Default attempt
            isValid = true
        )
    }
}

// Settings Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDemoMode: Boolean,
    selectedEDMDevice: EDMDeviceSpec,
    serverIpAddress: String,
    serverPort: Int,
    onDemoModeToggle: () -> Unit,
    onEDMDeviceChange: (EDMDeviceSpec) -> Unit,
    onServerSettingsChange: (String, Int) -> Unit,
    onBackClick: () -> Unit,
    onDebugCommToggle: () -> Unit = {} // DEBUG: Add debug comm toggle (REMOVE WHEN DEBUG COMPLETE)
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(maxOf(16f, screenWidth * 0.02f).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "Settings",
                fontSize = maxOf(24f, screenWidth * 0.028f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            
            // Settings options
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Demo/Live Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Demo Mode",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF333333)
                            )
                            Text(
                                text = if (isDemoMode) "Currently: Demo Active" else "Currently: Live Mode",
                                fontSize = 14.sp,
                                color = Color(0xFF666666)
                            )
                        }
                        Switch(
                            checked = isDemoMode,
                            onCheckedChange = { onDemoModeToggle() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFFFEB3B),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFF4CAF50)
                            )
                        )
                    }
                    
                    HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                    
                    // EDM Device Selection
                    Column {
                        Text(
                            text = "EDM Device Type",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF333333),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        
                        var edmDeviceExpanded by remember { mutableStateOf(false) }
                        val availableDevices = EDMDeviceRegistry.getAvailableDevices()
                        
                        ExposedDropdownMenuBox(
                            expanded = edmDeviceExpanded,
                            onExpandedChange = { edmDeviceExpanded = !edmDeviceExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedEDMDevice.displayName,
                                onValueChange = { },
                                readOnly = true,
                                label = { Text("Selected EDM Device") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = edmDeviceExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF1976D2),
                                    unfocusedBorderColor = Color(0xFFCCCCCC)
                                )
                            )
                            
                            ExposedDropdownMenu(
                                expanded = edmDeviceExpanded,
                                onDismissRequest = { edmDeviceExpanded = false }
                            ) {
                                availableDevices.forEach { device ->
                                    DropdownMenuItem(
                                        text = { 
                                            Column {
                                                Text(
                                                    text = device.displayName,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "${device.manufacturer.name} • ${device.baudRate} baud",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF666666)
                                                )
                                            }
                                        },
                                        onClick = {
                                            onEDMDeviceChange(device)
                                            edmDeviceExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Text(
                            text = "Current: ${selectedEDMDevice.manufacturer.name} ${selectedEDMDevice.model}",
                            fontSize = 12.sp,
                            color = Color(0xFF666666),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    
                    
                    // Server Settings
                    Column {
                        Text(
                            text = "Server Settings",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF333333),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        var tempIpAddress by remember { mutableStateOf(serverIpAddress) }
                        var tempPort by remember { mutableStateOf(serverPort.toString()) }
                        
                        // IP Address field
                        OutlinedTextField(
                            value = tempIpAddress,
                            onValueChange = { tempIpAddress = it },
                            label = { Text("Server IP Address") },
                            placeholder = { Text("192.168.0.90") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1976D2),
                                unfocusedBorderColor = Color(0xFFCCCCCC)
                            ),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Port field
                        OutlinedTextField(
                            value = tempPort,
                            onValueChange = { newValue ->
                                // Only allow numeric input
                                if (newValue.all { it.isDigit() } && newValue.length <= 5) {
                                    tempPort = newValue
                                }
                            },
                            label = { Text("Server Port") },
                            placeholder = { Text("8080") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1976D2),
                                unfocusedBorderColor = Color(0xFFCCCCCC)
                            ),
                            singleLine = true
                        )
                        
                        // Apply button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    val portNumber = tempPort.toIntOrNull() ?: 8080
                                    onServerSettingsChange(tempIpAddress, portNumber)
                                }
                            ) {
                                Text(
                                    text = "Apply",
                                    color = Color(0xFF1976D2)
                                )
                            }
                        }
                        
                        Text(
                            text = "Current: $serverIpAddress:$serverPort",
                            fontSize = 12.sp,
                            color = Color(0xFF666666),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            
            // DEBUG: Serial Communication Debug Button (REMOVE WHEN DEBUG COMPLETE)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "DEBUG: Serial Communication Monitor",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF856404),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "Monitor real-time serial communication with EDM device",
                        fontSize = 12.sp,
                        color = Color(0xFF856404),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Button(
                        onClick = onDebugCommToggle,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))
                    ) {
                        Text(
                            text = "Open Communication Monitor",
                            color = Color(0xFF212529)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Back button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = RectangleShape,
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBackClick,
                    modifier = Modifier.width(200.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE0E0E0),
                        contentColor = Color(0xFF333333)
                    )
                ) {
                    Text(
                        text = "← Back",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

class MainActivityCompose : ComponentActivity() {
    
    // USB Management
    private lateinit var usbManager: UsbManager
    private var permissionIntent: PendingIntent? = null
    private var usbReceiver: BroadcastReceiver? = null
    
    private lateinit var viewModel: AppViewModel
    
    companion object {
        private const val ACTION_USB_PERMISSION = "com.polyfieldandroid.USB_PERMISSION"
    }
    
    // USB status logging (console only like original)
    private fun updateStatus(message: String) {
        android.util.Log.d("PolyField", "USB Status: $message")
    }
    
    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        kotlin.io.println(
            if (allGranted) "Runtime permissions granted" 
            else "Some permissions denied - functionality may be limited"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Show splash screen IMMEDIATELY with zero initialization
        setContent {
            PolyFieldTheme {
                ZeroDelayApp()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        usbReceiver?.let { unregisterReceiver(it) }
    }
    
    // USB Management Implementation
    private fun initializeUSB() {
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        permissionIntent = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                android.util.Log.d("PolyField", "USB BroadcastReceiver: Received intent action: ${intent.action}")
                when (intent.action) {
                    ACTION_USB_PERMISSION -> {
                        synchronized(this) {
                            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                                device?.let { onUSBDevicePermissionGranted(it) }
                            } else {
                                android.util.Log.d("PolyField", "USB permission denied for device")
                            }
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        device?.let { onUSBDeviceAttached(it) }
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        device?.let { onUSBDeviceDetached(it) }
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            usbReceiver, 
            filter, 
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        
        requestRuntimePermissions()
    }
    
    private fun requestRuntimePermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    private fun checkConnectedUSBDevices() {
        val deviceList = usbManager.deviceList
        android.util.Log.d("PolyField", "Checking USB devices - Total devices found: ${deviceList.size}")
        
        if (deviceList.isEmpty()) {
            android.util.Log.d("PolyField", "No USB devices detected")
            viewModel.updateDetectedDevices(emptyList())
            return
        }
        
        // Log all detected USB devices for debugging
        deviceList.values.forEach { device ->
            android.util.Log.d("PolyField", "USB Device found: ${device.productName ?: "Unknown"} " +
                    "(VID: ${String.format(java.util.Locale.UK, "%04X", device.vendorId)}, " +
                    "PID: ${String.format(java.util.Locale.UK, "%04X", device.productId)})")
        }
        
        // Use the EDMModule to list USB devices (matches v16 approach)
        val usbDevicesResult = viewModel.getUsbDevices()
        
        @Suppress("UNCHECKED_CAST")
        val usbDevices = (usbDevicesResult["ports"] as? List<Map<String, Any>>) ?: emptyList()
        
        // Find all serial devices and populate the detected devices list
        val serialDevices = deviceList.values.filter { isUSBSerialDevice(it) }
        android.util.Log.d("PolyField", "Compatible USB serial devices: ${serialDevices.size}")
        
        val detectedDevices = usbDevices.mapIndexed { index, deviceInfo ->
            DetectedDevice(
                vendorId = deviceInfo["vendorId"] as? Int ?: 0,
                productId = deviceInfo["productId"] as? Int ?: 0,
                deviceName = deviceInfo["description"] as? String ?: "USB Device ${index + 1}",
                serialPath = "/dev/ttyUSB$index"
            )
        }
        
        // Update the state with detected devices
        viewModel.updateDetectedDevices(detectedDevices)
        
        if (serialDevices.isNotEmpty()) {
            val deviceNames = serialDevices.map { it.productName ?: "Unknown" }
            android.util.Log.d("PolyField", "Found ${serialDevices.size} USB serial device(s): $deviceNames")
            
            // Request permission for the first device (or handle multiple devices)
            val firstDevice = serialDevices.first()
            android.util.Log.d("PolyField", "Checking permissions for: ${firstDevice.productName}")
            
            if (!usbManager.hasPermission(firstDevice)) {
                android.util.Log.d("PolyField", "Requesting USB permission for device")
                usbManager.requestPermission(firstDevice, permissionIntent)
            } else {
                android.util.Log.d("PolyField", "Device already has permission, connecting")
                onUSBDevicePermissionGranted(firstDevice)
            }
        } else {
            android.util.Log.d("PolyField", "No compatible USB serial devices found")
        }
    }
    
    private fun isUSBSerialDevice(device: UsbDevice): Boolean {
        val vendorId = device.vendorId
        val productId = device.productId
        
        // FTDI devices (0x0403 = 1027 decimal)
        if (vendorId == 1027) {
            return productId == 24577 || productId == 24596 || productId == 24582
        }
        
        // Prolific PL2303 (0x067b = 1659 decimal)
        if (vendorId == 1659 && productId == 8963) {
            return true
        }
        
        // Silicon Labs CP2102 (0x10c4 = 4292 decimal)
        if (vendorId == 4292 && productId == 60000) {
            return true
        }
        
        return false
    }
    
    private fun onUSBDeviceAttached(device: UsbDevice) {
        if (isUSBSerialDevice(device)) {
            val deviceName = device.productName ?: "Unknown"
            android.util.Log.d("PolyField", "USB serial device attached: $deviceName")
            
            if (!usbManager.hasPermission(device)) {
                usbManager.requestPermission(device, permissionIntent)
            } else {
                onUSBDevicePermissionGranted(device)
            }
        }
    }
    
    private fun onUSBDeviceDetached(device: UsbDevice) {
        val currentDevice = viewModel.uiState.value.connectedDevice
        if (device == currentDevice) {
            viewModel.updateDevice(null, true)
            android.util.Log.d("PolyField", "USB device disconnected - switching to demo mode")
        }
    }
    
    private fun onUSBDevicePermissionGranted(device: UsbDevice) {
        val deviceName = device.productName ?: "Serial Device"
        viewModel.updateDevice(device, false)
        
        // Auto-connect to EDM device if only one device found
        val deviceList = usbManager.deviceList
        val serialDevices = deviceList.values.filter { isUSBSerialDevice(it) }
        
        if (serialDevices.size == 1) {
            android.util.Log.d("PolyField", "Single USB serial device detected: $deviceName - Auto-connecting to EDM")
            
            // Update device config with auto-detected device info
            val devicePath = "/dev/ttyUSB0" // Default path for USB serial devices
            val autoDetectedConfig = DeviceState(
                connected = false, // Will be set to true after successful connection
                connectionType = "serial",
                serialPort = devicePath,
                ipAddress = "192.168.1.100", // Default fallback
                port = 8080 // Default fallback
            )
            
            // Update device configuration
            viewModel.updateDeviceConfig("edm", autoDetectedConfig)
            
            // Auto-connect to EDM device
            viewModel.updateDeviceConnection("edm", true)
            
            android.util.Log.d("PolyField", "Auto-connecting EDM device at $devicePath")
        } else if (serialDevices.size > 1) {
            android.util.Log.d("PolyField", "Multiple USB serial devices detected (${serialDevices.size}) - Manual selection required")
        }
        
        android.util.Log.d("PolyField", "USB device connected: $deviceName")
    }
}

// Theme Definition - Matching original exactly
@Composable
fun PolyFieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1976D2),           // Exact blue from original
            background = Color(0xFFF5F5F5),        // Exact background gray
            surface = Color.White,                  // White cards
            secondary = Color(0xFFFFEB3B),         // Demo yellow
            tertiary = Color(0xFF4CAF50),          // Success green
            onPrimary = Color.White,
            onBackground = Color(0xFF333333),      // Text color
            onSurface = Color(0xFF333333)
        ),
        content = content
    )
}

@Composable
fun ZeroDelayApp() {
    val context = LocalContext.current as MainActivityCompose
    var showSplash by remember { mutableStateOf(true) }
    
    // Progressive initialization state
    var appViewModel by remember { mutableStateOf<AppViewModel?>(null) }
    var modeManager by remember { mutableStateOf<ModeManagerViewModel?>(null) }
    var competitionManager by remember { mutableStateOf<CompetitionManagerViewModel?>(null) }
    var athleteManager by remember { mutableStateOf<AthleteManagerViewModel?>(null) }
    var measurementManager by remember { mutableStateOf<CompetitionMeasurementManager?>(null) }
    
    // Show splash for minimum 200ms, then start progressive loading
    LaunchedEffect(Unit) {
        // Minimum splash duration for smooth UX
        kotlinx.coroutines.delay(200)
        showSplash = false
        
        // Start progressive background initialization
        launch(kotlinx.coroutines.Dispatchers.Default) {
            // Phase 1: Critical ViewModels first (lightweight initialization)
            appViewModel = AppViewModel(context, fastInit = true)
            
            // Phase 2: Secondary ViewModels
            launch {
                competitionManager = ViewModelProvider(context, CompetitionManagerViewModelFactory(context))
                    .get(CompetitionManagerViewModel::class.java)
            }
            launch {
                athleteManager = ViewModelProvider(context, AthleteManagerViewModelFactory(context))
                    .get(AthleteManagerViewModel::class.java)
            }
            
            // Phase 3: Mode manager depends on app view model
            appViewModel?.let { appVM ->
                modeManager = ViewModelProvider(context, ModeManagerViewModelFactory(context, appVM))
                    .get(ModeManagerViewModel::class.java)
            }
            
            // Phase 4: Heavy operations last (deferred until needed)
            // EDM Module and measurement manager created lazily when first accessed
        }
    }
    
    if (showSplash) {
        SplashScreen()
    } else {
        // Show main app with progressive loading states
        appViewModel?.let { appVM ->
            ProgressivePolyFieldApp(
                appViewModel = appVM,
                modeManager = modeManager,
                competitionManager = competitionManager,
                athleteManager = athleteManager,
                measurementManager = measurementManager,
                context = context
            )
        } ?: run {
            // Fallback loading state if AppViewModel isn't ready yet
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun ProgressivePolyFieldApp(
    appViewModel: AppViewModel,
    modeManager: ModeManagerViewModel?,
    competitionManager: CompetitionManagerViewModel?,
    athleteManager: AthleteManagerViewModel?,
    measurementManager: CompetitionMeasurementManager?,
    context: MainActivityCompose
) {
    // Lazy EDM Module creation - only when needed
    var edmModule by remember { mutableStateOf<EDMModule?>(null) }
    var isCreatingMeasurementManager by remember { mutableStateOf(false) }
    
    if (modeManager != null && competitionManager != null && athleteManager != null) {
        // Create measurement manager lazily when first accessed
        val finalMeasurementManager = measurementManager ?: run {
            LaunchedEffect(Unit) {
                isCreatingMeasurementManager = true
                // Create in background
                kotlinx.coroutines.delay(100) // Small delay to show loading
                isCreatingMeasurementManager = false
            }
            
            remember {
                val edm = edmModule ?: EDMModule(context).also { edmModule = it }
                ViewModelProvider(
                    context,
                    CompetitionMeasurementManagerFactory(context, edm, athleteManager, competitionManager, modeManager)
                ).get(CompetitionMeasurementManager::class.java).apply {
                    // Sync initial demo mode state
                    setDemoMode(appViewModel.uiState.value.isDemoMode)
                }
            }
        }
        
        // Show loading overlay if still creating heavy components
        Box {
            PolyFieldApp(
                viewModel = appViewModel,
                modeManager = modeManager,
                competitionManager = competitionManager,
                athleteManager = athleteManager,
                measurementManager = finalMeasurementManager
            )
            
            // Show loading overlay for heavy operations
            if (isCreatingMeasurementManager) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Initializing device communication...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    } else {
        // Show loading state with partial UI
        ProgressiveLoadingScreen(
            appViewModel = appViewModel,
            modeManager = modeManager,
            competitionManager = competitionManager,
            athleteManager = athleteManager
        )
    }
}

@Composable
fun ProgressiveLoadingScreen(
    appViewModel: AppViewModel,
    modeManager: ModeManagerViewModel?,
    competitionManager: CompetitionManagerViewModel?,
    athleteManager: AthleteManagerViewModel?
) {
    val uiState by appViewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Show header immediately
        PolyFieldHeaderExact(
            currentScreen = uiState.currentScreen,
            isDemoMode = uiState.isDemoMode,
            canGoBack = canGoBack(uiState.currentScreen),
            onBackClick = { navigateBack(appViewModel, uiState) },
            onToggleDemoMode = { appViewModel.toggleDemoMode() }
        )
        
        // Show loading states for components that aren't ready
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                
                val loadingMessages = mutableListOf<String>()
                if (modeManager == null) loadingMessages.add("Mode management")
                if (competitionManager == null) loadingMessages.add("Competition setup")
                if (athleteManager == null) loadingMessages.add("Athlete management")
                
                Text(
                    text = if (loadingMessages.isEmpty()) {
                        "Finalizing setup..."
                    } else {
                        "Loading ${loadingMessages.joinToString(", ")}..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// Main App Composable
@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Logo - using vector drawable
            Image(
                painter = painterResource(R.drawable.app_logo_vector),
                contentDescription = "PolyField Logo",
                modifier = Modifier.size(120.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // App Name
            Text(
                text = "PolyField",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtitle
            Text(
                text = "Track & Field Competition Manager",
                fontSize = 16.sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Attribution
            Text(
                text = "Built by Kingston Athletic Club\n& Polytechnic Harriers",
                fontSize = 14.sp,
                color = Color(0xFF888888),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Loading indicator
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color(0xFF1976D2),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
fun PolyFieldApp(
    viewModel: AppViewModel,
    modeManager: ModeManagerViewModel,
    competitionManager: CompetitionManagerViewModel,
    athleteManager: AthleteManagerViewModel,
    measurementManager: CompetitionMeasurementManager
) {
    val uiState by viewModel.uiState.collectAsState()
    val modeState by modeManager.modeState.collectAsState()
    val configuration = LocalConfiguration.current
    
    // Force recomposition on orientation changes to prevent UI artifacts
    key(configuration.orientation) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        // Header
        PolyFieldHeaderExact(
            currentScreen = uiState.currentScreen,
            isDemoMode = uiState.isDemoMode,
            canGoBack = canGoBack(uiState.currentScreen),
            onBackClick = { navigateBack(viewModel, uiState) },
            onToggleDemoMode = { 
                viewModel.toggleDemoMode()
                // Sync demo mode with measurement manager
                measurementManager?.setDemoMode(viewModel.uiState.value.isDemoMode)
                // Refresh connected mode to load/unload demo events based on demo mode
                if (uiState.currentScreen == "EVENT_SELECTION_CONNECTED") {
                    modeManager.setConnectedMode()
                }
            }
        )
        
        // Main Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (uiState.currentScreen) {
                "SELECT_EVENT_TYPE" -> SelectEventTypeScreenExact(
                    onEventSelected = { eventType ->
                        viewModel.updateEventType(eventType)
                        // Set connected mode to load demo events if demo mode is enabled
                        modeManager.setConnectedMode()
                        viewModel.updateScreen("EVENT_SELECTION_CONNECTED")
                    },
                    onSettingsClick = {
                        viewModel.updateScreen("SETTINGS")
                    }
                )
                "MODE_SELECTION" -> {
                    ModeSelectionScreen(
                        modeManager = modeManager,
                        onModeSelected = { mode ->
                            when (mode) {
                                AppMode.STANDALONE -> {
                                    // Continue with existing flow
                                    viewModel.updateScreen("DEVICE_SETUP")
                                }
                                AppMode.CONNECTED -> {
                                    // Switch to connected mode flow
                                    modeManager.setConnectedMode()
                                    viewModel.updateScreen("EVENT_SELECTION_CONNECTED")
                                }
                            }
                        }
                    )
                }
                "EVENT_SELECTION_CONNECTED" -> {
                    EventSelectionScreen(
                        modeManager = modeManager,
                        onEventSelected = { event ->
                            // First, set event type and circle type
                            val eventType = determineEventTypeFromEvent(event)
                            val circleType = getCircleTypeFromEvent(event)
                            viewModel.updateEventType(eventType)
                            viewModel.updateCircleType(circleType)
                            
                            // Then select the event in competition manager
                            competitionManager.selectEvent(event)
                            
                            // Load athletes from the selected event
                            athleteManager.loadAthletesFromEvent(event)
                            
                            // Navigate to device setup for calibration (throws) or direct to athlete checking (jumps)
                            if (eventType == "Throws") {
                                viewModel.updateScreen("DEVICE_SETUP_CONNECTED")
                            } else {
                                viewModel.updateScreen("COMPETITION_ACTIVE_CONNECTED")
                            }
                        },
                        onStandAloneSelected = {
                            // Switch to standalone mode and navigate to device setup
                            modeManager.setStandaloneMode()
                            viewModel.updateScreen("DEVICE_SETUP")
                        },
                        onBackToMode = {
                            viewModel.updateScreen("SELECT_EVENT_TYPE")
                        },
                        onEditServer = {
                            viewModel.updateScreen("SETTINGS")
                        }
                    )
                }
                "DEVICE_SETUP_CONNECTED" -> DeviceSetupScreenExact(
                    eventType = uiState.eventType,
                    devices = uiState.devices,
                    isDemoMode = uiState.isDemoMode,
                    onConnectDevice = { deviceType ->
                        viewModel.updateDeviceConnection(deviceType, !uiState.devices.let {
                            when (deviceType) {
                                "edm" -> it.edm.connected
                                "wind" -> it.wind.connected
                                "scoreboard" -> it.scoreboard.connected
                                else -> false
                            }
                        })
                    },
                    onToggleDeviceSetupModal = { deviceType -> viewModel.toggleDeviceSetupModal(deviceType) },
                    onContinue = {
                        // Same validation logic as standalone mode
                        if (!uiState.isDemoMode && uiState.eventType == "Throws" && !uiState.devices.edm.connected) {
                            viewModel.showErrorDialog(
                                "Device Required",
                                "EDM device must be connected to proceed in live mode. Please connect your EDM device or switch to demo mode."
                            )
                        } else {
                            // For connected mode, continue to calibration for throws
                            val nextScreen = if (uiState.eventType == "Throws") "CALIBRATION_SELECT_CIRCLE_CONNECTED" else "COMPETITION_ACTIVE_CONNECTED"
                            viewModel.updateScreen(nextScreen)
                        }
                    }
                )
                "CALIBRATION_SELECT_CIRCLE_CONNECTED" -> {
                    // Pre-select circle type based on event
                    val selectedEvent = competitionManager.competitionState.collectAsState().value.selectedEvent
                    val preSelectedCircle = selectedEvent?.let { getCircleTypeForEvent(it) } ?: ""
                    
                    // Auto-select the circle if we can determine it from the event
                    if (preSelectedCircle.isNotEmpty() && uiState.calibration.circleType.isEmpty()) {
                        LaunchedEffect(selectedEvent) {
                            viewModel.updateCircleType(preSelectedCircle)
                            viewModel.updateScreen("CALIBRATION_SET_CENTRE_CONNECTED")
                        }
                    }
                    
                    CalibrationSelectCircleScreenExact(
                        selectedCircle = uiState.calibration.circleType,
                        onCircleSelected = { circleType ->
                            viewModel.updateCircleType(circleType)
                            viewModel.updateScreen("CALIBRATION_SET_CENTRE_CONNECTED")
                        }
                    )
                }
                "CALIBRATION_SET_CENTRE_CONNECTED" -> CalibrationSetCentreScreenExact(
                    calibration = uiState.calibration,
                    isLoading = uiState.isLoading,
                    availableCalibrations = viewModel.getTodaysCalibrations(),
                    onSetCentre = { 
                        viewModel.setCentre()
                    },
                    onResetCentre = { 
                        viewModel.resetCalibration()
                        viewModel.updateCircleType(uiState.calibration.circleType)
                    },
                    onLoadHistoricalCalibration = { calibrationRecord ->
                        viewModel.loadHistoricalCalibration(calibrationRecord)
                    }
                )
                "CALIBRATION_VERIFY_EDGE_CONNECTED" -> CalibrationVerifyEdgeScreenExact(
                    calibration = uiState.calibration,
                    isLoading = uiState.isLoading,
                    onVerifyEdge = { 
                        viewModel.verifyEdge()
                    },
                    onResetEdge = { 
                        viewModel.verifyEdge()  // Remeasure button triggers new reading
                    }
                )
                "CALIBRATION_SECTOR_LINE_CONNECTED" -> CalibrationSectorLineScreen(
                    calibration = uiState.calibration,
                    isLoading = uiState.isLoading,
                    onMeasureSectorLine = { 
                        viewModel.measureSectorLine()
                    },
                    onContinue = {
                        viewModel.saveCurrentCalibrationToHistory()
                        viewModel.updateScreen("COMPETITION_ACTIVE_CONNECTED")
                    }
                )
                "COMPETITION_SETUP_CONNECTED" -> {
                    // This screen is now unused - keeping for backward compatibility
                    competitionManager.competitionState.collectAsState().value.selectedEvent?.let { selectedEvent ->
                        CompetitionSetupScreenConnected(
                            selectedEvent = selectedEvent,
                            modeManager = modeManager,
                            onSetupComplete = { 
                                viewModel.updateScreen("COMPETITION_ACTIVE_CONNECTED")
                            },
                            onBackToEvents = {
                                viewModel.updateScreen("EVENT_SELECTION_CONNECTED")
                            }
                        )
                    } ?: run {
                        // Fallback if no event selected
                        viewModel.updateScreen("EVENT_SELECTION_CONNECTED")
                    }
                }
                "COMPETITION_ACTIVE_CONNECTED" -> {
                    competitionManager.competitionState.collectAsState().value.selectedEvent?.let { selectedEvent ->
                        CompetitionAthleteScreen(
                            selectedEvent = selectedEvent,
                            athleteManager = athleteManager,
                            onAthleteSelected = { athlete ->
                                // Navigate directly to this athlete for measurement
                                athleteManager.selectAthlete(athlete)
                                measurementManager.startCompetitionWithAthlete(athlete)
                                viewModel.updateScreen("COMPETITION_MEASUREMENT_CONNECTED")
                            },
                            onStartCompetition = {
                                // Start competition with all checked-in athletes
                                measurementManager.startCompetition()
                                viewModel.updateScreen("COMPETITION_MEASUREMENT_CONNECTED")
                            }
                        )
                    } ?: run {
                        // Fallback if no event selected
                        viewModel.updateScreen("EVENT_SELECTION_CONNECTED")
                    }
                }
                "COMPETITION_MEASUREMENT_CONNECTED" -> {
                    competitionManager.competitionState.collectAsState().value.selectedEvent?.let { selectedEvent ->
                        CompetitionMeasurementScreen(
                            selectedEvent = selectedEvent,
                            athleteManager = athleteManager,
                            measurementManager = measurementManager,
                            modeManager = modeManager,
                            onBackToAthletes = {
                                viewModel.updateScreen("COMPETITION_ACTIVE_CONNECTED")
                            },
                            onEndCompetition = {
                                // Reset competition and go back to event selection
                                measurementManager.endCompetition()
                                viewModel.updateScreen("EVENT_SELECTION_CONNECTED")
                            }
                        )
                    } ?: run {
                        // Fallback if no event selected
                        viewModel.updateScreen("EVENT_SELECTION_CONNECTED")
                    }
                }
                "COMPETITION_RESULTS_CONNECTED" -> {
                    competitionManager.competitionState.collectAsState().value.selectedEvent?.let { selectedEvent ->
                        CompetitionResultsScreen(
                            selectedEvent = selectedEvent,
                            athleteManager = athleteManager,
                            onBackToCompetition = {
                                viewModel.updateScreen("COMPETITION_ACTIVE_CONNECTED")
                            }
                        )
                    } ?: run {
                        // Fallback if no event selected
                        viewModel.updateScreen("EVENT_SELECTION_CONNECTED")
                    }
                }
                "DEVICE_SETUP" -> DeviceSetupScreenExact(
                    eventType = uiState.eventType,
                    devices = uiState.devices,
                    isDemoMode = uiState.isDemoMode,
                    onConnectDevice = { deviceType ->
                        viewModel.updateDeviceConnection(deviceType, !uiState.devices.let {
                            when (deviceType) {
                                "edm" -> it.edm.connected
                                "wind" -> it.wind.connected
                                "scoreboard" -> it.scoreboard.connected
                                else -> false
                            }
                        })
                    },
                    onToggleDeviceSetupModal = { deviceType -> viewModel.toggleDeviceSetupModal(deviceType) },
                    onContinue = {
                        // Validate device connection for live mode
                        if (!uiState.isDemoMode && uiState.eventType == "Throws" && !uiState.devices.edm.connected) {
                            viewModel.showErrorDialog(
                                "Device Required",
                                "EDM device must be connected to proceed in live mode. Please connect your EDM device or switch to demo mode."
                            )
                        } else {
                            val nextScreen = if (uiState.eventType == "Throws") "CALIBRATION_SELECT_CIRCLE" else "MEASUREMENT"
                            viewModel.updateScreen(nextScreen)
                        }
                    }
                )
                "CALIBRATION_SELECT_CIRCLE" -> CalibrationSelectCircleScreenExact(
                    selectedCircle = uiState.calibration.circleType,
                    onCircleSelected = { circleType ->
                        viewModel.updateCircleType(circleType)
                    }
                )
                "CALIBRATION_SET_CENTRE" -> CalibrationSetCentreScreenExact(
                    calibration = uiState.calibration,
                    isLoading = uiState.isLoading,
                    availableCalibrations = viewModel.getTodaysCalibrations(),
                    onSetCentre = { viewModel.setCentre() },
                    onResetCentre = { 
                        viewModel.resetCalibration()
                        viewModel.updateCircleType(uiState.calibration.circleType)
                    },
                    onLoadHistoricalCalibration = { calibrationRecord ->
                        viewModel.loadHistoricalCalibration(calibrationRecord)
                    }
                )
                "CALIBRATION_VERIFY_EDGE" -> CalibrationVerifyEdgeScreenExact(
                    calibration = uiState.calibration,
                    isLoading = uiState.isLoading,
                    onVerifyEdge = { viewModel.verifyEdge() },
                    onResetEdge = { 
                        viewModel.verifyEdge()  // Changed: Remeasure button now triggers new reading instead of just resetting
                    }
                )
                "CALIBRATION_SECTOR_LINE" -> CalibrationSectorLineScreen(
                    calibration = uiState.calibration,
                    isLoading = uiState.isLoading,
                    onMeasureSectorLine = { viewModel.measureSectorLine() },
                    onContinue = {
                        viewModel.updateScreen("MEASUREMENT")
                    }
                )
                "MEASUREMENT" -> MeasurementScreenExact(
                    eventType = uiState.eventType,
                    calibration = uiState.calibration,
                    measurement = uiState.measurement,
                    windMeasurement = uiState.windMeasurement,
                    throwCoordinates = uiState.throwCoordinates,
                    isLoading = uiState.isLoading,
                    onMeasureDistance = { viewModel.measureDistance() },
                    onMeasureWind = { viewModel.measureWind() },
                    onResetSession = { viewModel.resetSession() },
                    onShowHeatMap = { viewModel.toggleHeatMap() },
                    onNewEvent = {
                        viewModel.resetSession()
                        viewModel.resetCalibration()
                        viewModel.updateScreen("SELECT_EVENT_TYPE")
                    }
                )
                "SETTINGS" -> SettingsScreen(
                    isDemoMode = uiState.isDemoMode,
                    selectedEDMDevice = uiState.settings.selectedEDMDevice,
                    serverIpAddress = uiState.settings.serverIpAddress,
                    serverPort = uiState.settings.serverPort,
                    onDemoModeToggle = { viewModel.toggleDemoMode() },
                    onEDMDeviceChange = { edmDevice ->
                        viewModel.updateSettings(uiState.settings.copy(selectedEDMDevice = edmDevice))
                    },
                    onServerSettingsChange = { ipAddress, port ->
                        viewModel.updateSettings(uiState.settings.copy(serverIpAddress = ipAddress, serverPort = port))
                    },
                    onBackClick = {
                        viewModel.updateScreen("SELECT_EVENT_TYPE")
                    },
                    onDebugCommToggle = { // DEBUG: Add debug comm toggle (REMOVE WHEN DEBUG COMPLETE)
                        viewModel.toggleDebugCommStream()
                    }
                )
                
                // DEBUG: Serial Communication Monitor Screen (REMOVE WHEN DEBUG COMPLETE)
                "DEBUG_SERIAL_COMM" -> DebugSerialCommScreen(
                    serialCommLog = uiState.serialCommLog,
                    onBackClick = {
                        viewModel.updateScreen("SETTINGS")
                    },
                    onClearLog = {
                        viewModel.clearSerialCommLog()
                    }
                )
                
                "HEAT_MAP" -> HeatMapScreen(
                    calibration = uiState.calibration,
                    throwCoordinates = uiState.throwCoordinates,
                    onBackClick = {
                        viewModel.updateScreen("MEASUREMENT")
                    }
                )
            }
        }
        
        // Device Configuration Modal
        if (uiState.deviceSetupVisible) {
            DeviceConfigurationModal(
                onDismiss = { viewModel.toggleDeviceSetupModal() },
                devices = uiState.devices,
                detectedDevices = uiState.detectedDevices,
                initialSelectedDevice = uiState.selectedDeviceForConfig,
                onUpdateDevice = { deviceType, deviceConfig ->
                    viewModel.updateDeviceConfig(deviceType, deviceConfig)
                    viewModel.toggleDeviceSetupModal()
                },
                onRefreshUsb = { viewModel.refreshUsbDevices() }
            )
        }
        
        // Error Dialog
        if (uiState.errorMessage != null && uiState.errorTitle != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissErrorDialog() },
                title = {
                    Text(
                        text = uiState.errorTitle ?: "Error",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = uiState.errorMessage ?: "An error occurred",
                        fontSize = 16.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissErrorDialog() }) {
                        Text("OK")
                    }
                }
            )
        }
        
        // Bottom Navigation - Hide on initial screen
        if (showBottomNavigation(uiState.currentScreen)) {
            BottomNavigationExact(
            currentScreen = uiState.currentScreen,
            eventType = uiState.eventType,
            canGoBack = canGoBack(uiState.currentScreen),
            canGoForward = canGoForward(uiState.currentScreen),
            showHeatMapButton = uiState.currentScreen == "MEASUREMENT" && uiState.eventType == "Throws",
            showResultsButton = uiState.currentScreen.contains("COMPETITION") && 
                              (uiState.currentScreen == "COMPETITION_ACTIVE_CONNECTED" || 
                               uiState.currentScreen == "COMPETITION_MEASUREMENT_CONNECTED"),
            onBackClick = { navigateBack(viewModel, uiState) },
            onNextClick = { navigateForward(viewModel, uiState, athleteManager) },
            onHeatMapClick = { viewModel.toggleHeatMap() },
            onResultsClick = { 
                viewModel.updateScreen("COMPETITION_RESULTS_CONNECTED") 
            },
            onNewEventClick = {
                viewModel.resetSession()
                viewModel.resetCalibration()
                viewModel.updateScreen("SELECT_EVENT_TYPE")
            }
        )
        }
    }
    } // End key block for orientation change recomposition
}

// Helper functions for navigation and UI state
private fun getScreenTitle(screen: String, eventType: String, circleType: String): String {
    return when (screen) {
        "SELECT_EVENT_TYPE" -> "PolyField"
        "DEVICE_SETUP" -> "Device Setup - $eventType"
        "CALIBRATION_SELECT_CIRCLE" -> "Circle Selection"
        "CALIBRATION_SET_CENTRE" -> "Set Circle Centre - ${circleType.replace("_", " ")}"
        "CALIBRATION_VERIFY_EDGE" -> "Verify Circle Edge - ${circleType.replace("_", " ")}"
        "CALIBRATION_SECTOR_LINE" -> "Sector Line Check Mark - ${circleType.replace("_", " ")}"
        "MEASUREMENT" -> "Measurement - ${circleType.replace("_", " ")}"
        "HEAT_MAP" -> "Heat Map - ${circleType.replace("_", " ")}"
        else -> "PolyField"
    }
}

private fun canGoBack(screen: String): Boolean {
    return screen != "SELECT_EVENT_TYPE" && screen != "SETTINGS"
}

private fun canGoForward(screen: String): Boolean {
    return when (screen) {
        "SELECT_EVENT_TYPE", "DEVICE_SETUP", "DEVICE_SETUP_CONNECTED", 
        "CALIBRATION_SELECT_CIRCLE", "CALIBRATION_SELECT_CIRCLE_CONNECTED",
        "CALIBRATION_SET_CENTRE", "CALIBRATION_SET_CENTRE_CONNECTED",
        "CALIBRATION_VERIFY_EDGE", "CALIBRATION_VERIFY_EDGE_CONNECTED",
        "CALIBRATION_SECTOR_LINE", "CALIBRATION_SECTOR_LINE_CONNECTED",
        "COMPETITION_ACTIVE_CONNECTED" -> true
        "HEAT_MAP" -> false // Remove next button from heat map
        else -> false
    }
}

private fun showBottomNavigation(screen: String): Boolean {
    return screen != "SELECT_EVENT_TYPE" && screen != "SETTINGS" // Hide on initial screen and settings
}

/**
 * Determine event type from selected event for calibration purposes
 */
private fun determineEventTypeFromEvent(event: PolyFieldApiClient.Event): String {
    val eventName = event.name.lowercase()
    val eventType = event.type.lowercase()
    
    return when {
        // Throwing events
        eventName.contains("discus") || eventName.contains("hammer") || 
        eventName.contains("javelin") || eventName.contains("shot") || 
        eventName.contains("weight") || eventType.contains("throw") -> "Throws"
        
        // Jumping events  
        eventName.contains("jump") || eventName.contains("vault") -> "Jumps"
        
        // Default to throws if uncertain
        else -> "Throws"
    }
}

/**
 * Get the appropriate circle type based on the selected event
 */
private fun getCircleTypeForEvent(event: PolyFieldApiClient.Event): String {
    val eventName = event.name.lowercase()
    val eventType = event.type.lowercase()
    
    return when {
        eventName.contains("discus") || eventType.contains("discus") -> "DISCUS_CIRCLE"
        eventName.contains("hammer") || eventType.contains("hammer") -> "HAMMER_CIRCLE"
        eventName.contains("shot") || eventType.contains("shot") -> "SHOT_CIRCLE"
        eventName.contains("weight") || eventType.contains("weight") -> "WEIGHT_CIRCLE"
        eventName.contains("javelin") || eventType.contains("javelin") -> "JAVELIN_RUNWAY"
        else -> "DISCUS_CIRCLE" // Default fallback
    }
}

// DEBUG: Serial Communication Monitor Screen (REMOVE WHEN DEBUG COMPLETE)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSerialCommScreen(
    serialCommLog: List<SerialCommLogEntry>,
    onBackClick: () -> Unit,
    onClearLog: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when new entries are added
    LaunchedEffect(serialCommLog.size) {
        if (serialCommLog.isNotEmpty()) {
            listState.animateScrollToItem(serialCommLog.size - 1)
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1976D2))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Serial Communication Monitor",
                    fontSize = maxOf(20f, screenWidth * 0.025f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Row {
                    Button(
                        onClick = onClearLog,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Clear", color = Color(0xFF212529))
                    }
                    
                    Button(
                        onClick = onBackClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text("← Back", color = Color(0xFF1976D2))
                    }
                }
            }
        }
        
        // Communication log
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (serialCommLog.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Text(
                            text = "No communication logged yet.\nTry using 'Set Centre' to trigger EDM communication.",
                            modifier = Modifier.padding(16.dp),
                            color = Color(0xFF666666),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            items(serialCommLog) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (entry.direction == "OUT") Color(0xFFE3F2FD) else Color(0xFFF3E5F5)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${entry.direction} ${entry.getFormattedTime()}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (entry.direction == "OUT") Color(0xFF1565C0) else Color(0xFF7B1FA2)
                            )
                            
                            if (!entry.success && entry.error != null) {
                                Text(
                                    text = "ERROR",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD32F2F),
                                    modifier = Modifier
                                        .background(Color(0xFFFFEBEE), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "DATA: '${entry.data}'",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF212529)
                        )
                        
                        if (entry.dataHex.isNotEmpty()) {
                            Text(
                                text = "HEX:  ${entry.dataHex}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF666666)
                            )
                        }
                        
                        if (!entry.success && entry.error != null) {
                            Text(
                                text = "ERROR: ${entry.error}",
                                fontSize = 10.sp,
                                color = Color(0xFFD32F2F),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun navigateBack(viewModel: AppViewModel, uiState: AppState) {
    val previousScreen = when (uiState.currentScreen) {
        "MODE_SELECTION" -> "SELECT_EVENT_TYPE"
        "DEVICE_SETUP" -> "SELECT_EVENT_TYPE"
        "EVENT_SELECTION_CONNECTED" -> "SELECT_EVENT_TYPE"
        "DEVICE_SETUP_CONNECTED" -> "EVENT_SELECTION_CONNECTED"
        "CALIBRATION_SELECT_CIRCLE_CONNECTED" -> "DEVICE_SETUP_CONNECTED"
        "CALIBRATION_SET_CENTRE_CONNECTED" -> "CALIBRATION_SELECT_CIRCLE_CONNECTED"
        "CALIBRATION_VERIFY_EDGE_CONNECTED" -> "CALIBRATION_SET_CENTRE_CONNECTED"
        "CALIBRATION_SECTOR_LINE_CONNECTED" -> "CALIBRATION_VERIFY_EDGE_CONNECTED"
        "COMPETITION_SETUP_CONNECTED" -> "EVENT_SELECTION_CONNECTED"
        "COMPETITION_ACTIVE_CONNECTED" -> {
            // Check if we came from calibration or direct from event selection
            if (uiState.eventType == "Throws") "CALIBRATION_SECTOR_LINE_CONNECTED" else "EVENT_SELECTION_CONNECTED"
        }
        "COMPETITION_MEASUREMENT_CONNECTED" -> "COMPETITION_ACTIVE_CONNECTED"
        "COMPETITION_RESULTS_CONNECTED" -> "COMPETITION_ACTIVE_CONNECTED"
        "CALIBRATION_SELECT_CIRCLE" -> "DEVICE_SETUP"
        "CALIBRATION_SET_CENTRE" -> "CALIBRATION_SELECT_CIRCLE"
        "CALIBRATION_VERIFY_EDGE" -> "CALIBRATION_SET_CENTRE"
        "CALIBRATION_SECTOR_LINE" -> "CALIBRATION_VERIFY_EDGE"
        "MEASUREMENT" -> {
            if (uiState.eventType == "Throws") "CALIBRATION_SECTOR_LINE" else "DEVICE_SETUP"
        }
        "HEAT_MAP" -> "MEASUREMENT"
        else -> uiState.currentScreen
    }
    viewModel.updateScreen(previousScreen)
}

private fun navigateForward(viewModel: AppViewModel, uiState: AppState, athleteManager: AthleteManagerViewModel? = null) {
    val nextScreen = when (uiState.currentScreen) {
        "SELECT_EVENT_TYPE" -> "DEVICE_SETUP"
        "DEVICE_SETUP" -> if (uiState.eventType == "Throws") "CALIBRATION_SELECT_CIRCLE" else "MEASUREMENT"
        "DEVICE_SETUP_CONNECTED" -> {
            // Same validation logic as the onContinue callback
            if (!uiState.isDemoMode && uiState.eventType == "Throws" && !uiState.devices.edm.connected) {
                viewModel.showErrorDialog(
                    "Device Required",
                    "EDM device must be connected to proceed in live mode. Please connect your EDM device or switch to demo mode."
                )
                uiState.currentScreen // Don't advance if device not connected in live mode
            } else {
                if (uiState.eventType == "Throws") "CALIBRATION_SELECT_CIRCLE_CONNECTED" else "COMPETITION_ACTIVE_CONNECTED"
            }
        }
        "CALIBRATION_SELECT_CIRCLE" -> {
            if (uiState.calibration.circleType.isNotEmpty()) "CALIBRATION_SET_CENTRE"
            else uiState.currentScreen // Don't advance if no circle selected
        }
        "CALIBRATION_SELECT_CIRCLE_CONNECTED" -> {
            if (uiState.calibration.circleType.isNotEmpty()) "CALIBRATION_SET_CENTRE_CONNECTED"
            else uiState.currentScreen // Don't advance if no circle selected
        }
        "CALIBRATION_SET_CENTRE" -> "CALIBRATION_VERIFY_EDGE"
        "CALIBRATION_SET_CENTRE_CONNECTED" -> "CALIBRATION_VERIFY_EDGE_CONNECTED"
        "CALIBRATION_VERIFY_EDGE" -> "CALIBRATION_SECTOR_LINE"
        "CALIBRATION_VERIFY_EDGE_CONNECTED" -> "CALIBRATION_SECTOR_LINE_CONNECTED"
        "CALIBRATION_SECTOR_LINE" -> "MEASUREMENT"
        "CALIBRATION_SECTOR_LINE_CONNECTED" -> "COMPETITION_ACTIVE_CONNECTED"
        "COMPETITION_ACTIVE_CONNECTED" -> "COMPETITION_MEASUREMENT_CONNECTED"
        "COMPETITION_MEASUREMENT_CONNECTED" -> {
            // Move to next athlete instead of changing screen
            android.util.Log.d("Navigation", "Next button clicked on measurement screen - calling nextAthlete()")
            athleteManager?.nextAthlete()
            android.util.Log.d("Navigation", "nextAthlete() called successfully")
            uiState.currentScreen // Stay on measurement screen
        }
        else -> uiState.currentScreen
    }
    viewModel.updateScreen(nextScreen)
}