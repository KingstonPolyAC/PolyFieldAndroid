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
    val screenHeight = configuration.screenHeightDp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(maxOf(12f, screenWidth * 0.015f).dp)
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
                .padding(bottom = maxOf(20f, screenHeight * 0.025f).dp)
        )

        if (eventType == "Throws") {
            // Throws measurement interface - NO WIND for throws
            ThrowsMeasurementInterface(
                measurement = measurement,
                isLoading = isLoading,
                onMeasureDistance = onMeasureDistance,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )

            // Session statistics
            if (throwCoordinates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(maxOf(20f, screenHeight * 0.025f).dp))

                SessionStatistics(
                    throwCoordinates = throwCoordinates,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight
                )
            }

        } else {
            // Horizontal Jumps measurement interface
            HorizontalJumpsMeasurementInterface(
                windMeasurement = windMeasurement,
                isLoading = isLoading,
                onMeasureWind = onMeasureWind,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )
        }
    }
}

@Composable
fun ThrowsMeasurementInterface(
    measurement: String,
    isLoading: Boolean,
    onMeasureDistance: () -> Unit,
    screenWidth: Int,
    screenHeight: Int
) {
    // Main measurement row - large distance display + measure button
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxOf(120f, screenHeight * 0.18f).dp),
        horizontalArrangement = Arrangement.spacedBy(maxOf(12f, screenWidth * 0.015f).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Distance display
        Card(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(maxOf(12f, screenWidth * 0.015f).dp),
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
                    fontSize = maxOf(18f, screenWidth * 0.022f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF666666)
                )

                Spacer(modifier = Modifier.height(maxOf(6f, screenHeight * 0.01f).dp))

                Text(
                    text = measurement.ifEmpty { "--" },
                    fontSize = maxOf(56f, screenWidth * 0.09f).sp,
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
            shape = RoundedCornerShape(maxOf(12f, screenWidth * 0.015f).dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1976D2)
            )
        ) {
            Text(
                text = if (isLoading) "Measuring..." else "MEASURE",
                fontSize = maxOf(20f, screenWidth * 0.028f).sp,
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
    screenWidth: Int,
    screenHeight: Int
) {
    // Wind measurement for horizontal jumps
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(maxOf(12f, screenWidth * 0.015f).dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(maxOf(20f, screenWidth * 0.025f).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Wind Measurement",
                fontSize = maxOf(24f, screenWidth * 0.028f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )

            Spacer(modifier = Modifier.height(maxOf(16f, screenHeight * 0.02f).dp))

            Text(
                text = "Current Wind Speed:",
                fontSize = maxOf(16f, screenWidth * 0.022f).sp,
                color = Color(0xFF666666)
            )

            Text(
                text = windMeasurement.ifEmpty { "--" },
                fontSize = maxOf(40f, screenWidth * 0.06f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2),
                modifier = Modifier.padding(vertical = maxOf(12f, screenHeight * 0.015f).dp)
            )

            Button(
                onClick = onMeasureWind,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxOf(50f, screenHeight * 0.08f).dp),
                shape = RoundedCornerShape(maxOf(12f, screenWidth * 0.015f).dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text(
                    text = if (isLoading) "Reading Wind..." else "Measure Wind",
                    fontSize = maxOf(20f, screenWidth * 0.028f).sp,
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
    screenWidth: Int,
    screenHeight: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxOf(70f, screenHeight * 0.1f).dp),
        shape = RoundedCornerShape(maxOf(12f, screenWidth * 0.015f).dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        // Statistics grid - no title, just key stats
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(maxOf(12f, screenWidth * 0.015f).dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                label = "Total Throws",
                value = throwCoordinates.size.toString(),
                screenWidth = screenWidth
            )

            StatItem(
                label = "Longest",
                value = if (throwCoordinates.isNotEmpty()) {
                    String.format(java.util.Locale.UK, "%.2fm", throwCoordinates.maxOf { it.distance })
                } else "0.00m",
                screenWidth = screenWidth
            )

            StatItem(
                label = "Average",
                value = if (throwCoordinates.isNotEmpty()) {
                    String.format(java.util.Locale.UK, "%.2fm", throwCoordinates.map { it.distance }.average())
                } else "0.00m",
                screenWidth = screenWidth
            )
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    screenWidth: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = maxOf(22f, screenWidth * 0.032f).sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1976D2)
        )

        Text(
            text = label,
            fontSize = maxOf(11f, screenWidth * 0.014f).sp,
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
                    onClick = {
                        android.util.Log.d("BottomNavDebug", "🔵 Bottom Nav Next Athlete button clicked for screen: $currentScreen")
                        if (currentScreen == "MEASUREMENT") {
                            try {
                                onNewEventClick()
                                android.util.Log.d("BottomNavDebug", "🔵 onNewEventClick completed successfully")
                            } catch (e: Exception) {
                                android.util.Log.e("BottomNavDebug", "🔴 Error in onNewEventClick: ${e.message}", e)
                            }
                        } else {
                            try {
                                onNextClick()
                                android.util.Log.d("BottomNavDebug", "🔵 onNextClick completed successfully")
                            } catch (e: Exception) {
                                android.util.Log.e("BottomNavDebug", "🔴 Error in onNextClick: ${e.message}", e)
                            }
                        }
                    },
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
                            "COMPETITION_MEASUREMENT_CONNECTED" -> "Next Athlete"
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