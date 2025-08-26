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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

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
    val isDoubleReadMode: Boolean = false,
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

// Heat Map Data Classes
data class ThrowCoordinate(
    val x: Double,
    val y: Double,
    val distance: Double,
    val circleType: String,
    val timestamp: String,
    val id: String,
    val athleteId: String? = null,
    val round: String? = null
)

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
                        text = "${String.format("%.3f", calibration.sectorLineDistance)} m",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2)
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
        val filteredCoords = throwCoordinates.filter { it.circleType == calibration.circleType }
        
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

class AppViewModel(private val context: android.content.Context) : androidx.lifecycle.ViewModel() {
    private val _uiState = MutableStateFlow(AppState())
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()
    
    // EDM Module for device communication
    private val edmModule = EDMModule(context)
    
    // SharedPreferences for persistent storage
    private val sharedPrefs = context.getSharedPreferences("PolyFieldCalibrations", android.content.Context.MODE_PRIVATE)
    
    init {
        // Load calibration history on startup
        loadCalibrationHistoryFromDisk()
        
        // CRITICAL: Initialize Go Mobile demo mode to prevent simulation in live mode
        try {
            val initialDemoMode = _uiState.value.isDemoMode
            mobile.Mobile.setDemoMode(initialDemoMode)
            android.util.Log.d("PolyField", "Go Mobile initialized with demo mode: $initialDemoMode")
        } catch (e: Exception) {
            android.util.Log.e("PolyField", "Failed to initialize Go Mobile demo mode", e)
        }
        
        // DEBUG: Setup serial communication logging (REMOVE WHEN DEBUG COMPLETE)
        edmModule.setDebugLogger { direction, data, dataHex, success, error ->
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
        
        // CRITICAL: Tell Go Mobile the current demo mode to prevent simulation in live mode
        try {
            mobile.Mobile.setDemoMode(newMode)
            android.util.Log.d("PolyField", "Go Mobile demo mode set to: $newMode")
        } catch (e: Exception) {
            android.util.Log.e("PolyField", "Failed to set Go Mobile demo mode", e)
        }
        
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
        edmModule.setSelectedEDMDevice(settings.selectedEDMDevice)
        android.util.Log.d("PolyField", "Updated EDM device to: ${settings.selectedEDMDevice.displayName}")
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
        return edmModule.listUsbDevices()
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
                    val result = edmModule.connectUsbDevice("edm", edmDevice.serialPath)
                    if (result["success"] == true) {
                        android.util.Log.d("PolyField", "Auto-connect successful: ${result["message"]}")
                        
                        // Register device with Go Mobile for EDM operations
                        try {
                            val goMobileResult = mobile.Mobile.registerUSBDevice("edm", edmDevice.deviceName)
                            android.util.Log.d("PolyField", "Go Mobile device registration: $goMobileResult")
                        } catch (e: Exception) {
                            android.util.Log.w("PolyField", "Go Mobile registration failed but proceeding: ${e.message}")
                        }
                        
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
        } else if (!connected) {
            // Disconnect device
            edmModule.disconnectDevice(deviceType)
            // Update state to disconnected
            updateDeviceConnectionState(deviceType, false)
        }
        // Note: For connection=true, state is updated in connectToRealDevice on success
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
                
                val result = edmModule.connectUsbDevice(deviceType, edmDevice.serialPath)
                android.util.Log.d("PolyField", "Device connection result: $result")
                
                // Update connection status based on result
                val success = result["success"] as? Boolean == true
                if (success) {
                    android.util.Log.d("PolyField", "Device connection successful")
                    
                    // Register device with Go Mobile for EDM operations
                    if (deviceType == "edm") {
                        try {
                            val deviceName = result["edmDevice"] as? String ?: edmDevice.deviceName
                            val goMobileResult = mobile.Mobile.registerUSBDevice(deviceType, deviceName)
                            android.util.Log.d("PolyField", "Go Mobile device registration: $goMobileResult")
                        } catch (e: Exception) {
                            android.util.Log.w("PolyField", "Go Mobile registration failed but proceeding: ${e.message}")
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
                    
                    // Use Go Mobile's setCentre function which handles proper trigonometry
                    val isDoubleReadMode = _uiState.value.settings.isDoubleReadMode
                    android.util.Log.e("PolyField", "🔵 Calling setCentreWithGoMobile with doubleRead: $isDoubleReadMode")
                    
                    val result = edmModule.setCentreWithGoMobile("edm", _uiState.value.calibration.targetRadius, _uiState.value.calibration.circleType, isDoubleReadMode)
                    
                    android.util.Log.e("PolyField", "🔵 Go Mobile setCentre result: $result")
                    
                    if (result["success"] as Boolean) {
                        val goMobileResult = result["result"] as String
                        val jsonResult = JSONObject(goMobileResult)
                        
                        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        
                        // CRITICAL: In live mode, ONLY use real coordinates from Go Mobile - NO fallbacks or simulation
                        if (!jsonResult.has("stationX") || !jsonResult.has("stationY")) {
                            android.util.Log.e("PolyField", "CRITICAL: Go Mobile setCentre missing station coordinates - REFUSING to use simulation in live mode")
                            showErrorDialog(
                                "Centre Set Failed",
                                "Go Mobile did not return station coordinates. Cannot proceed without real EDM data."
                            )
                            _uiState.value = _uiState.value.copy(isLoading = false)
                            return@launch
                        }
                        
                        // Extract REAL station coordinates from Go Mobile result (no fallbacks allowed)
                        val stationX = jsonResult.getDouble("stationX")
                        val stationY = jsonResult.getDouble("stationY")
                        
                        android.util.Log.d("PolyField", "Centre set successfully with REAL Go Mobile calculations")
                        android.util.Log.d("PolyField", "REAL station coordinates from EDM: X=$stationX, Y=$stationY")
                        
                        _uiState.value = _uiState.value.copy(
                            calibration = _uiState.value.calibration.copy(
                                centreSet = true,
                                centreTimestamp = timestamp,
                                stationCoordinates = Pair(stationX, stationY)
                            ),
                            isLoading = false
                        )
                    } else {
                        val error = result["error"] as? String ?: "Failed to set centre"
                        android.util.Log.e("PolyField", "Failed to set centre with Go Mobile: $error")
                        showErrorDialog(
                            "Centre Set Failed",
                            error
                        )
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
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
                    
                    // Use Go Mobile's verifyEdge function which calculates horizontal distance from centre
                    val isDoubleReadMode = _uiState.value.settings.isDoubleReadMode
                    val result = edmModule.verifyEdgeWithGoMobile("edm", targetRadius, isDoubleReadMode)
                    
                    android.util.Log.d("PolyField", "Go Mobile verifyEdge result: $result")
                    
                    if (result["success"] as Boolean) {
                        val goMobileResult = result["result"] as String
                        val jsonResult = JSONObject(goMobileResult)
                        
                        // CRITICAL: In live mode, ONLY use real measurements from Go Mobile - NO fallbacks or simulation
                        val measuredRadius = if (jsonResult.has("measuredRadius")) {
                            jsonResult.getDouble("measuredRadius")
                        } else if (jsonResult.has("averageRadius")) {
                            jsonResult.getDouble("averageRadius")  
                        } else {
                            android.util.Log.e("PolyField", "CRITICAL: Go Mobile verifyEdge missing radius measurement - REFUSING to use simulation in live mode")
                            showErrorDialog(
                                "Edge Verification Failed",
                                "Go Mobile did not return measured radius. Cannot proceed without real EDM measurement."
                            )
                            _uiState.value = _uiState.value.copy(isLoading = false)
                            return@launch
                        }
                        val deviation = kotlin.math.abs(measuredRadius - targetRadius)
                        
                        // Different tolerances per UKA/WA rules
                        val tolerance = if (_uiState.value.calibration.circleType == "JAVELIN_ARC") 0.010 else 0.005 // 10mm for javelin, 5mm for others
                        val toleranceCheck = deviation <= tolerance
                        
                        android.util.Log.d("PolyField", "Edge verification: measured=${measuredRadius}m, target=${targetRadius}m, deviation=${deviation*1000}mm, pass=${toleranceCheck}")
                        
                        val edgeResult = EdgeResult(
                            toleranceCheck = toleranceCheck,
                            measurements = listOf(measuredRadius),
                            averageRadius = measuredRadius,
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
                        val error = result["error"] as? String ?: "Failed to verify edge"
                        android.util.Log.e("PolyField", "Edge verification failed with Go Mobile: $error")
                        showErrorDialog(
                            "Edge Verification Failed",
                            error
                        )
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
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
                    
                    // Use Go Mobile's measureThrow function for sector line (it's the same calculation)
                    val isDoubleReadMode = _uiState.value.settings.isDoubleReadMode
                    val result = edmModule.measureThrowWithGoMobile("edm", isDoubleReadMode)
                    
                    if (result["success"] as Boolean) {
                        val goMobileResult = result["result"] as String
                        val jsonResult = JSONObject(goMobileResult)
                        
                        // CRITICAL: In live mode, ONLY use real distance from Go Mobile - NO fallbacks or simulation
                        // Extract the total distance from center first
                        val totalDistance = if (jsonResult.has("distance")) {
                            jsonResult.getDouble("distance")
                        } else if (jsonResult.has("distanceFromCentre")) {
                            jsonResult.getDouble("distanceFromCentre") 
                        } else {
                            android.util.Log.e("PolyField", "CRITICAL: Go Mobile measureThrow missing distance measurement - REFUSING to use simulation in live mode")
                            showErrorDialog(
                                "Sector Line Measurement Failed",
                                "Go Mobile did not return distance measurement. Cannot proceed without real EDM data."
                            )
                            _uiState.value = _uiState.value.copy(isLoading = false)
                            return@launch
                        }
                        
                        android.util.Log.d("PolyField", "Sector line measurement successful with Go Mobile: ${totalDistance}m from center")
                        
                        // Calculate distance beyond circle edge (distance from center minus circle radius)
                        val targetRadius = _uiState.value.calibration.targetRadius
                        val distanceBeyondCircle = totalDistance - targetRadius
                        
                        android.util.Log.d("PolyField", "Sector line: ${totalDistance}m from center, ${distanceBeyondCircle}m beyond circle edge (radius: ${targetRadius}m)")
                        
                        // Calculate sector line coordinates based on UKA/WA angles
                        // Shot/Discus/Hammer: 34.92° total (17.46° each side), Javelin: 28.96° total (14.48° each side)
                        val sectorAngleDegrees = if (_uiState.value.calibration.circleType == "JAVELIN_ARC") 14.48 else 17.46
                        val sectorLineCoordinates = Pair(
                            totalDistance * kotlin.math.sin(Math.toRadians(sectorAngleDegrees)),
                            totalDistance * kotlin.math.cos(Math.toRadians(sectorAngleDegrees))
                        )
                        
                        _uiState.value = _uiState.value.copy(
                            calibration = _uiState.value.calibration.copy(
                                sectorLineSet = true,
                                sectorLineDistance = distanceBeyondCircle,  // Store distance beyond circle edge
                                sectorLineCoordinates = sectorLineCoordinates
                            ),
                            isLoading = false
                        )
                        
                        // Save complete calibration to history
                        saveCurrentCalibrationToHistory()
                    } else {
                        val error = result["error"] as? String ?: "Failed to measure sector line"
                        android.util.Log.e("PolyField", "Sector line measurement failed with Go Mobile: $error")
                        showErrorDialog(
                            "Sector Line Measurement Failed",
                            error
                        )
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
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
            val isDoubleReadMode = _uiState.value.settings.isDoubleReadMode
            android.util.Log.d("PolyField", "🔵 measureDistance DEMO - Double read mode setting: $isDoubleReadMode")
            val distance = generateDemoThrow()
            _uiState.value = _uiState.value.copy(
                measurement = String.format("%.2f m", distance),
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
                    val isDoubleReadMode = _uiState.value.settings.isDoubleReadMode
                    android.util.Log.d("PolyField", "🔵 measureDistance LIVE - Double read mode setting: $isDoubleReadMode")
                    val result = edmModule.measureThrowWithGoMobile("edm", isDoubleReadMode)
                    
                    if (result["success"] as Boolean) {
                        val goMobileResult = result["result"] as String
                        val jsonResult = JSONObject(goMobileResult)
                        
                        // CRITICAL: In live mode, ONLY use real distance from Go Mobile - NO fallbacks or simulation
                        val distance = if (jsonResult.has("distanceBeyondCircle")) {
                            jsonResult.getDouble("distanceBeyondCircle")
                        } else if (jsonResult.has("distance")) {
                            // Fallback: calculate distance beyond circle from total distance
                            val totalDistance = jsonResult.getDouble("distance")
                            totalDistance - _uiState.value.calibration.targetRadius
                        } else if (jsonResult.has("throwDistance")) {
                            // Fallback: calculate distance beyond circle from throw distance
                            val totalDistance = jsonResult.getDouble("throwDistance")
                            totalDistance - _uiState.value.calibration.targetRadius
                        } else {
                            android.util.Log.e("PolyField", "CRITICAL: Go Mobile measureThrow missing distance field - REFUSING to use simulation in live mode")
                            android.util.Log.e("PolyField", "Go Mobile result: $goMobileResult")
                            showErrorDialog(
                                "Measurement Error",
                                "Go Mobile did not return distance measurement. Cannot proceed without real EDM data."
                            )
                            _uiState.value = _uiState.value.copy(isLoading = false)
                            return@launch
                        }
                        
                        android.util.Log.d("PolyField", "Go Mobile measurement successful: ${distance}m beyond circle edge")
                        
                        _uiState.value = _uiState.value.copy(
                            measurement = String.format("%.2f m", distance),
                            isLoading = false
                        )
                        
                        // Extract coordinates from Go Mobile result for heat map
                        // CRITICAL: Only add coordinates if Go Mobile provides them - NO simulation fallbacks
                        if (jsonResult.has("x") && jsonResult.has("y")) {
                            val throwX = jsonResult.getDouble("x")
                            val throwY = jsonResult.getDouble("y")
                            val throwCoord = ThrowCoordinate(
                                x = throwX,
                                y = throwY, 
                                distance = distance,
                                circleType = _uiState.value.calibration.circleType,
                                timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                                id = java.util.UUID.randomUUID().toString()
                            )
                            
                            _uiState.value = _uiState.value.copy(
                                throwCoordinates = _uiState.value.throwCoordinates + throwCoord
                            )
                            
                            android.util.Log.d("PolyField", "Real throw coordinate added: x=${throwX}, y=${throwY}, distance=${distance}m")
                        }
                        
                    } else {
                        val error = result["error"] as? String ?: "Failed to measure distance"
                        android.util.Log.e("PolyField", "Go Mobile measurement failed: $error")
                        showErrorDialog(
                            "Measurement Error", 
                            error
                        )
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
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
                windMeasurement = String.format("%s%.1f m/s", if (windSpeed > 0) "+" else "", windSpeed),
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
                    val reading = edmModule.measureWind()
                    
                    if (reading.success && reading.windSpeed != null) {
                        val windSpeed = reading.windSpeed
                        android.util.Log.d("PolyField", "Wind reading successful: ${windSpeed}m/s")
                        
                        _uiState.value = _uiState.value.copy(
                            windMeasurement = String.format("%s%.1f m/s", if (windSpeed > 0) "+" else "", windSpeed),
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
            
            sharedPrefs.edit()
                .putString("calibration_history", jsonArray.toString())
                .apply()
                
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
        
        // CRITICAL: Sync Go Mobile internal state when loading historical calibration
        if (calibrationRecord.stationCoordinates != null) {
            val stationX = calibrationRecord.stationCoordinates!!.first
            val stationY = calibrationRecord.stationCoordinates!!.second
            val result = mobile.Mobile.setCalibrationState(
                "edm",
                stationX,
                stationY,
                calibrationRecord.targetRadius,
                calibrationRecord.circleType
            )
            android.util.Log.d("PolyField", "🔵 Synced Go Mobile state for historical calibration: $result")
            
            // Also sync edge verification result if available
            if (calibrationRecord.edgeResult != null) {
                val edgeResult = mobile.Mobile.setEdgeVerificationResult(
                    "edm",
                    calibrationRecord.edgeResult!!.averageRadius,
                    calibrationRecord.edgeResult!!.deviation,
                    5.0, // Standard tolerance for throws circles
                    calibrationRecord.edgeResult!!.toleranceCheck
                )
                android.util.Log.d("PolyField", "🔵 Synced edge verification result: $edgeResult")
            }
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
            circleType = _uiState.value.calibration.circleType,
            timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
            id = java.util.UUID.randomUUID().toString()
        )
    }
}

// Settings Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDemoMode: Boolean,
    isDoubleReadMode: Boolean,
    selectedEDMDevice: EDMDeviceSpec,
    serverIpAddress: String,
    serverPort: Int,
    onDemoModeToggle: () -> Unit,
    onDoubleReadModeToggle: (Boolean) -> Unit,
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
                    
                    Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                    
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
                    
                    // Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                    
                    // HIDDEN: Single/Double Read Toggle - disabled for now
                    /*
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "EDM Read Mode",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF333333)
                            )
                            Text(
                                text = if (isDoubleReadMode) "Double read with tolerance" else "Single read only",
                                fontSize = 14.sp,
                                color = Color(0xFF666666)
                            )
                        }
                        Switch(
                            checked = isDoubleReadMode,
                            onCheckedChange = onDoubleReadModeToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF1976D2),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFF999999)
                            )
                        )
                    }
                    
                    Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                    */
                    
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
                        text = "🔧 DEBUG: Serial Communication Monitor",
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
        
        viewModel = AppViewModel(this)
        initializeUSB()
        
        setContent {
            PolyFieldTheme {
                PolyFieldApp(viewModel = viewModel)
            }
        }
        
        checkConnectedUSBDevices()
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
        registerReceiver(usbReceiver, filter)
        
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
                    "(VID: ${String.format("%04X", device.vendorId)}, " +
                    "PID: ${String.format("%04X", device.productId)})")
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

// Main App Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolyFieldApp(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    
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
            onToggleDemoMode = { viewModel.toggleDemoMode() }
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
                        viewModel.updateScreen("DEVICE_SETUP")
                    },
                    onSettingsClick = {
                        viewModel.updateScreen("SETTINGS")
                    }
                )
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
                    isDoubleReadMode = uiState.settings.isDoubleReadMode,
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
                    isDoubleReadMode = uiState.settings.isDoubleReadMode,
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
                    isDoubleReadMode = uiState.settings.isDoubleReadMode,
                    selectedEDMDevice = uiState.settings.selectedEDMDevice,
                    serverIpAddress = uiState.settings.serverIpAddress,
                    serverPort = uiState.settings.serverPort,
                    onDemoModeToggle = { viewModel.toggleDemoMode() },
                    onDoubleReadModeToggle = { enabled ->
                        viewModel.updateSettings(uiState.settings.copy(isDoubleReadMode = enabled))
                    },
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
            onBackClick = { navigateBack(viewModel, uiState) },
            onNextClick = { navigateForward(viewModel, uiState) },
            onHeatMapClick = { viewModel.toggleHeatMap() },
            onNewEventClick = {
                viewModel.resetSession()
                viewModel.resetCalibration()
                viewModel.updateScreen("SELECT_EVENT_TYPE")
            }
        )
        }
    }
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
        "SELECT_EVENT_TYPE", "DEVICE_SETUP", "CALIBRATION_SELECT_CIRCLE", 
        "CALIBRATION_SET_CENTRE", "CALIBRATION_VERIFY_EDGE", "CALIBRATION_SECTOR_LINE" -> true
        "HEAT_MAP" -> false // Remove next button from heat map
        else -> false
    }
}

private fun showBottomNavigation(screen: String): Boolean {
    return screen != "SELECT_EVENT_TYPE" && screen != "SETTINGS" // Hide on initial screen and settings
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
                    text = "🔧 Serial Communication Monitor",
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
        "DEVICE_SETUP" -> "SELECT_EVENT_TYPE"
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

private fun navigateForward(viewModel: AppViewModel, uiState: AppState) {
    val nextScreen = when (uiState.currentScreen) {
        "SELECT_EVENT_TYPE" -> "DEVICE_SETUP"
        "DEVICE_SETUP" -> if (uiState.eventType == "Throws") "CALIBRATION_SELECT_CIRCLE" else "MEASUREMENT"
        "CALIBRATION_SELECT_CIRCLE" -> {
            if (uiState.calibration.circleType.isNotEmpty()) "CALIBRATION_SET_CENTRE" 
            else uiState.currentScreen // Don't advance if no circle selected
        }
        "CALIBRATION_SET_CENTRE" -> "CALIBRATION_VERIFY_EDGE"
        "CALIBRATION_VERIFY_EDGE" -> "CALIBRATION_SECTOR_LINE"
        "CALIBRATION_SECTOR_LINE" -> "MEASUREMENT"
        else -> uiState.currentScreen
    }
    viewModel.updateScreen(nextScreen)
}