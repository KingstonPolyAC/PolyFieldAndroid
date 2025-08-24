package com.polyfieldandroid

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Locale

// Helper function to format timestamp
fun formatTimestamp(timestamp: String?): String {
    if (timestamp == null) return ""
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val date = inputFormat.parse(timestamp)
        outputFormat.format(date ?: return "")
    } catch (e: Exception) {
        timestamp // Return original if parsing fails
    }
}

// CALIBRATION_SELECT_CIRCLE Screen - Exact implementation
@Composable
fun CalibrationSelectCircleScreenExact(
    selectedCircle: String,
    isDoubleReadMode: Boolean,
    onCircleSelected: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Title section
        Column {
            Text(
                text = "Circle Selection",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                text = "Select the circle type for calibration",
                fontSize = 16.sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
        
        // Circle tiles in grid layout - UKA/WA specifications
        val circles = listOf(
            Triple("SHOT", "1.0675m", "±5mm"),
            Triple("DISCUS", "1.25m", "±5mm"),
            Triple("HAMMER", "1.0675m", "±5mm"),
            Triple("JAVELIN_ARC", "8.0m", "±10mm")
        )
        
        // Grid layout - 2x2
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircleTileCompact(
                    title = "SHOT",
                    radius = "1.0675m",
                    tolerance = "±5mm UKA/WA",
                    isSelected = selectedCircle == "SHOT",
                    onClick = { onCircleSelected("SHOT") },
                    modifier = Modifier.weight(1f)
                )
                CircleTileCompact(
                    title = "DISCUS",
                    radius = "1.25m",
                    tolerance = "±5mm UKA/WA",
                    isSelected = selectedCircle == "DISCUS",
                    onClick = { onCircleSelected("DISCUS") },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircleTileCompact(
                    title = "HAMMER",
                    radius = "1.0675m",
                    tolerance = "±5mm UKA/WA",
                    isSelected = selectedCircle == "HAMMER",
                    onClick = { onCircleSelected("HAMMER") },
                    modifier = Modifier.weight(1f)
                )
                CircleTileCompact(
                    title = "JAVELIN ARC",
                    radius = "8.0m",
                    tolerance = "±10mm UKA/WA",
                    isSelected = selectedCircle == "JAVELIN_ARC",
                    onClick = { onCircleSelected("JAVELIN_ARC") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Bottom spacer
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun CircleCardExact(
    title: String,
    radius: String,
    tolerance: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    screenWidth: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onClick() }
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, Color(0xFF1976D2), RoundedCornerShape(15.dp))
                } else Modifier
            ),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1976D2).copy(alpha = 0.1f) 
                            else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = maxOf(20f, screenWidth * 0.024f).sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color(0xFF1976D2) else Color(0xFF333333),
                textAlign = TextAlign.Center
            )
            
            Text(
                text = radius,
                fontSize = maxOf(16f, screenWidth * 0.02f).sp,
                color = if (isSelected) Color(0xFF1976D2) else Color(0xFF666666),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            Text(
                text = tolerance,
                fontSize = maxOf(14f, screenWidth * 0.018f).sp,
                color = if (isSelected) Color(0xFF1976D2).copy(alpha = 0.7f) else Color(0xFF999999),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CircleTileCompact(
    title: String,
    radius: String,
    tolerance: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(100.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1976D2) 
                            else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else Color(0xFF333333),
                textAlign = TextAlign.Center
            )
            
            Text(
                text = radius,
                fontSize = 14.sp,
                color = if (isSelected) Color.White else Color(0xFF666666),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
            
            Text(
                text = tolerance,
                fontSize = 12.sp,
                color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color(0xFF999999),
                textAlign = TextAlign.Center
            )
        }
    }
}

// CALIBRATION_SET_CENTRE Screen - With real status display and historical calibration selection
@Composable
fun CalibrationSetCentreScreenExact(
    calibration: CalibrationState,
    isLoading: Boolean,
    availableCalibrations: List<CalibrationRecord>,
    onSetCentre: () -> Unit,
    onResetCentre: () -> Unit,
    onLoadHistoricalCalibration: (CalibrationRecord) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    if (isLandscape && availableCalibrations.isNotEmpty() && !calibration.centreSet) {
        // Landscape layout with previous calibrations
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left column - Set Centre functionality
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Title and circle info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Set Centre",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                    
                    Text(
                        text = "${calibration.circleType.replace("_", " ")} Circle",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "Radius: ${String.format("%.4f", calibration.targetRadius)}m",
                        fontSize = 18.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center
                    )
                }
                
                // Set Centre button
                Column {
                    Button(
                        onClick = onSetCentre,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (calibration.centreSet) Color(0xFF4CAF50) else Color(0xFF1976D2)
                        )
                    ) {
                        Text(
                            text = when {
                                isLoading -> "Setting Centre..."
                                calibration.centreSet && calibration.selectedHistoricalCalibration != null -> 
                                    "✓ Loaded from ${calibration.selectedHistoricalCalibration.getDisplayName()}"
                                calibration.centreSet -> "✓ Centre Set"
                                else -> "Set Centre"
                            },
                            fontSize = if (calibration.centreSet) 14.sp else 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    // Reset button
                    if (calibration.centreSet) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onResetCentre,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Reset Centre",
                                color = Color(0xFFF44336)
                            )
                        }
                    }
                }
            }
            
            // Right column - Previous calibrations
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F8FF)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Load Previous Calibration",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1976D2),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        availableCalibrations.forEach { calibrationRecord ->
                            OutlinedButton(
                                onClick = { onLoadHistoricalCalibration(calibrationRecord) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF1976D2)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, Color(0xFF1976D2)
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = calibrationRecord.getDisplayName(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (calibrationRecord.isComplete()) {
                                        Text(
                                            text = "✓ Complete (Centre + Edge + Sector Line)",
                                            fontSize = 11.sp,
                                            color = Color(0xFF4CAF50)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Select a previous calibration to reuse centre, edge verification, and sector line measurements from today.",
                            fontSize = 12.sp,
                            color = Color(0xFF666666),
                            style = androidx.compose.ui.text.TextStyle(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        )
                    }
                }
            }
        }
    } else {
        // Portrait layout or no previous calibrations
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "EDM Calibration - Set Centre",
                fontSize = maxOf(24f, screenWidth * 0.028f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 30.dp)
            )
            
            // Circle type display
            Text(
                text = "${calibration.circleType.replace("_", " ")} Circle",
                fontSize = maxOf(20f, screenWidth * 0.024f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            
            // Radius display
            Text(
                text = "Radius: ${String.format("%.4f", calibration.targetRadius)}m",
                fontSize = maxOf(18f, screenWidth * 0.022f).sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            
            // Historical calibration selection (only show if calibrations available and centre not set)
            if (availableCalibrations.isNotEmpty() && !calibration.centreSet) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F8FF)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Load Previous Calibration (Today)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1976D2),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        availableCalibrations.forEach { calibrationRecord ->
                            OutlinedButton(
                                onClick = { onLoadHistoricalCalibration(calibrationRecord) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF1976D2)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, Color(0xFF1976D2)
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = calibrationRecord.getDisplayName(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (calibrationRecord.isComplete()) {
                                        Text(
                                            text = "✓ Complete (Centre + Edge + Sector Line)",
                                            fontSize = 12.sp,
                                            color = Color(0xFF4CAF50)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Text(
                            text = "Select a previous calibration to reuse centre, edge verification, and sector line measurements.",
                            fontSize = 12.sp,
                            color = Color(0xFF666666),
                            style = androidx.compose.ui.text.TextStyle(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Set Centre button
            Button(
                onClick = onSetCentre,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (calibration.centreSet) Color(0xFF4CAF50) else Color(0xFF1976D2)
                )
            ) {
                Text(
                    text = when {
                        isLoading -> "Setting Centre..."
                        calibration.centreSet && calibration.selectedHistoricalCalibration != null -> 
                            "✓ Loaded from ${calibration.selectedHistoricalCalibration.getDisplayName()}"
                        calibration.centreSet -> "✓ Centre Set - ${formatTimestamp(calibration.centreTimestamp)}"
                        else -> "Set Centre"
                    },
                    fontSize = if (calibration.centreSet) 14.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
            
            // Reset button (only show if centre is set)
            if (calibration.centreSet) {
                Spacer(modifier = Modifier.height(20.dp))
                
                OutlinedButton(
                    onClick = onResetCentre,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Reset Centre",
                        color = Color(0xFFF44336)
                    )
                }
            }
        }
    }
}

// CALIBRATION_VERIFY_EDGE Screen - With tolerance display (double read) or direct measurement (single read)
@Composable
fun CalibrationVerifyEdgeScreenExact(
    calibration: CalibrationState,
    isLoading: Boolean,
    isDoubleReadMode: Boolean,
    onVerifyEdge: () -> Unit,
    onResetEdge: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Title section
        Column {
            Text(
                text = "EDM Calibration - Verify Edge",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Show circle info before measurement
            if (calibration.edgeResult == null) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "${calibration.circleType.replace("_", " ")} Circle",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = "Radius: ${String.format("%.4f", calibration.targetRadius)}m",
                    fontSize = 16.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
                
                Text(
                    text = "UKA/WA Tolerance: ${if (calibration.circleType == "JAVELIN_ARC") "±10mm" else "±5mm"}",
                    fontSize = 16.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )
                
                if (!isDoubleReadMode) {
                    Text(
                        text = "Single read mode - No 3mm comparison check",
                        fontSize = 14.sp,
                        color = Color(0xFF1976D2),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
        }
        
        // Main content area
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Show results if edge verification has been completed
            calibration.edgeResult?.let { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.toleranceCheck) Color(0xFFE8F5E8) else Color(0xFFFFEBEE)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Pass/Fail indicator
                        Text(
                            text = if (result.toleranceCheck) "PASS - In Tolerance" else "FAIL - OUT OF TOLERANCE",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (result.toleranceCheck) Color(0xFF4CAF50) else Color(0xFFF44336),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 15.dp)
                        )
                        
                        // Results details - simplified format
                        Text(
                            text = "Measured radius: ${String.format("%.3f", result.averageRadius)}m\n" +
                                   "Deviation: ${if (result.deviation >= 0) "+" else ""}${String.format("%.1f", result.deviation * 1000)}mm",
                            fontSize = 14.sp,
                            color = Color(0xFF333333),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
        
        // Action buttons based on state
        Column {
            if (calibration.edgeResult == null) {
                // Initial verify button
                Button(
                    onClick = onVerifyEdge,
                    enabled = !isLoading && calibration.centreSet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2)
                    )
                ) {
                    Text(
                        text = when {
                            isLoading -> "Verifying Edge..."
                            !calibration.centreSet -> "Set Centre First"
                            else -> "Verify Edge"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                // Instructions
                Text(
                    text = "Position the Prism at the circle edge and take a measurement.",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 15.dp)
                )
            } else {
                // Results are shown, display appropriate action button
                calibration.edgeResult?.let { result ->
                    if (result.toleranceCheck) {
                        // UKA/WA tolerance passed - show optional remeasure button
                        Button(
                            onClick = onResetEdge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Text(
                                text = "Remeasure",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    } else {
                        // UKA/WA tolerance failed - show Remeasure button
                        Button(
                            onClick = onResetEdge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF44336)
                            )
                        ) {
                            Text(
                                text = "Remeasure",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

