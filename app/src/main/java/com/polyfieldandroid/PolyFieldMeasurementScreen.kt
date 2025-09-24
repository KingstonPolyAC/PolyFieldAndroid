package com.polyfieldandroid

import androidx.compose.foundation.background
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

// MEASUREMENT Screen - Complete implementation with statistics
@Composable
fun MeasurementScreenExact(
    eventType: String?,
    calibration: CalibrationState,
    measurement: String,
    windMeasurement: String,
    throwCoordinates: List<ThrowCoordinate>,
    isLoading: Boolean,
    onMeasureDistance: () -> Unit,
    onMeasureWind: () -> Unit,
    onResetSession: () -> Unit,
    onShowHeatMap: () -> Unit,
    onNewEvent: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = "Measurement - ${if (eventType == "Throws") calibration.circleType.replace("_", " ") else eventType}",
            fontSize = maxOf(24f, screenWidth * 0.028f).sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 30.dp)
        )
        
        if (eventType == "Throws") {
            // Throws measurement interface - NO WIND for throws
            ThrowsMeasurementInterface(
                measurement = measurement,
                isLoading = isLoading,
                onMeasureDistance = onMeasureDistance,
                screenWidth = screenWidth
            )
            
            // Session statistics
            if (throwCoordinates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(30.dp))
                
                SessionStatistics(
                    throwCoordinates = throwCoordinates,
                    screenWidth = screenWidth
                )
            }
            
        } else {
            // Horizontal Jumps measurement interface  
            HorizontalJumpsMeasurementInterface(
                windMeasurement = windMeasurement,
                isLoading = isLoading,
                onMeasureWind = onMeasureWind,
                screenWidth = screenWidth
            )
        }
    }
}

@Composable
fun ThrowsMeasurementInterface(
    measurement: String,
    isLoading: Boolean,
    onMeasureDistance: () -> Unit,
    screenWidth: Int
) {
    // Main measurement row - large distance display + measure button
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Distance display
        Card(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Distance:",
                    fontSize = maxOf(20f, screenWidth * 0.025f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF666666)
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = measurement.ifEmpty { "--" },
                    fontSize = maxOf(72f, screenWidth * 0.12f).sp, // Increased from 48sp to 72sp for field visibility
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // Measure button
        Button(
            onClick = onMeasureDistance,
            enabled = !isLoading,
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1976D2)
            )
        ) {
            Text(
                text = if (isLoading) "Measuring..." else "MEASURE",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
    // No wind measurement for throws - throws don't need wind readings
}

@Composable
fun HorizontalJumpsMeasurementInterface(
    windMeasurement: String,
    isLoading: Boolean,
    onMeasureWind: () -> Unit,
    screenWidth: Int
) {
    // Wind measurement for horizontal jumps
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Wind Measurement",
                fontSize = maxOf(24f, screenWidth * 0.028f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = "Current Wind Speed:",
                fontSize = 18.sp,
                color = Color(0xFF666666)
            )
            
            Text(
                text = windMeasurement.ifEmpty { "--" },
                fontSize = 48.sp, // Increased from 32sp to 48sp for field visibility
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2),
                modifier = Modifier.padding(vertical = 15.dp)
            )
            
            Button(
                onClick = onMeasureWind,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text(
                    text = if (isLoading) "Reading Wind..." else "Measure Wind",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun SessionStatistics(
    throwCoordinates: List<ThrowCoordinate>,
    screenWidth: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp), // Fixed height to ensure visibility
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        // Statistics grid - no title, just key stats
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                label = "Total Throws",
                value = throwCoordinates.size.toString()
            )
            
            StatItem(
                label = "Longest",
                value = if (throwCoordinates.isNotEmpty()) {
                    String.format(java.util.Locale.UK, "%.1fm", throwCoordinates.maxOf { it.distance })
                } else "0.0m"
            )
            
            StatItem(
                label = "Average",
                value = if (throwCoordinates.isNotEmpty()) {
                    String.format(java.util.Locale.UK, "%.1fm", throwCoordinates.map { it.distance }.average())
                } else "0.0m"
            )
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 28.sp, // Increased from 20sp to 28sp for better field visibility
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1976D2)
        )
        
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF666666),
            textAlign = TextAlign.Center
        )
    }
}

// Bottom Navigation matching original
@Composable
fun BottomNavigationExact(
    currentScreen: String,
    eventType: String?,
    canGoBack: Boolean,
    canGoForward: Boolean,
    showHeatMapButton: Boolean = false,
    showResultsButton: Boolean = false,
    onBackClick: () -> Unit,
    onNextClick: () -> Unit,
    onHeatMapClick: () -> Unit = {},
    onResultsClick: () -> Unit = {},
    onNewEventClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = androidx.compose.ui.graphics.RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back/Setup button
            Button(
                onClick = onBackClick,
                enabled = canGoBack,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE0E0E0),
                    contentColor = Color(0xFF333333)
                )
            ) {
                Text(
                    text = if (currentScreen == "MEASUREMENT") "← Setup" else "← Back",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Heat map button (for throws measurement screen)
            if (showHeatMapButton) {
                Button(
                    onClick = onHeatMapClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2)
                    )
                ) {
                    Text(
                        text = "Heat Map",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            // Results/Standings button (for competition screens)
            if (showResultsButton) {
                Button(
                    onClick = onResultsClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9C27B0) // Purple for standings
                    )
                ) {
                    Text(
                        text = "Results",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            // Next/New Event button - hide on heat map screen
            if (currentScreen != "HEAT_MAP") {
                Button(
                    onClick = if (currentScreen == "MEASUREMENT") onNewEventClick else onNextClick,
                    enabled = if (currentScreen == "MEASUREMENT") true else canGoForward,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentScreen == "MEASUREMENT") Color(0xFFE0E0E0) else Color(0xFF1976D2),
                        contentColor = if (currentScreen == "MEASUREMENT") Color(0xFF333333) else Color.White
                    )
                ) {
                    Text(
                        text = when (currentScreen) {
                            "MEASUREMENT" -> "New Event"
                            "COMPETITION_ACTIVE_CONNECTED" -> "Start"
                            else -> "Next →"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}