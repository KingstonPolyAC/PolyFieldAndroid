package com.polyfieldandroid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// UKA radius helper - EXACT values from original
fun getDemoUKARadius(circleType: String): Double {
    return when (circleType) {
        "SHOT" -> 1.065      // 2.13m diameter
        "DISCUS" -> 1.25     // 2.5m diameter  
        "HAMMER" -> 1.065    // 2.13m diameter
        "JAVELIN_ARC" -> 8.0 // 16m diameter with 10mm tolerance
        else -> 1.0
    }
}

// Header Component - Matching original design exactly
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolyFieldHeaderExact(
    currentScreen: String,
    isDemoMode: Boolean,
    canGoBack: Boolean,
    onBackClick: () -> Unit,
    onToggleDemoMode: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxOf(80.dp, (screenHeight * 0.1f).dp)),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1976D2)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Header title
            Text(
                text = "PolyField by KACPH",
                color = Color.White,
                fontSize = maxOf(24f, screenWidth * 0.025f).sp,
                fontWeight = FontWeight.Bold
            )
            
            // Demo/Live mode status only
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                // Mode toggle button
                if (isDemoMode) {
                    Card(
                        modifier = Modifier.clickable { onToggleDemoMode() },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0x33FFEB3B)
                        ),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Text(
                            text = "Demo Active",
                            color = Color(0xFFFFEB3B),
                            fontSize = maxOf(18f, screenWidth * 0.02f).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier.clickable { onToggleDemoMode() },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0x334CAF50)
                        ),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Text(
                            text = "Live Mode",
                            color = Color(0xFF4CAF50),
                            fontSize = maxOf(18f, screenWidth * 0.02f).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

// SELECT_EVENT_TYPE Screen - Exact match
@Composable
fun SelectEventTypeScreenExact(
    onEventSelected: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(maxOf(20f, screenWidth * 0.025f).dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "Select Event Type",
            fontSize = maxOf(24f, screenWidth * 0.028f).sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = maxOf(20f, screenHeight * 0.025f).dp)
        )
        
        // Horizontal card container matching original - ONLY 2 cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EventTypeCardExact(
                title = "Throws",
                description = "Shot, Discus, Hammer, Javelin",
                onClick = { onEventSelected("Throws") },
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )
            
            EventTypeCardExact(
                title = "Horizontal Jumps", 
                description = "Long Jump, Triple Jump",
                onClick = { onEventSelected("Horizontal Jumps") },
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )
        }
    }
}

@Composable
fun EventTypeCardExact(
    title: String,
    description: String,
    onClick: () -> Unit,
    screenWidth: Int,
    screenHeight: Int
) {
    Card(
        modifier = Modifier
            .width(maxOf(280f, screenWidth * 0.35f).dp)
            .height(maxOf(220f, screenHeight * 0.32f).dp)
            .clickable { onClick() }
            .border(
                width = 3.dp,
                color = Color(0xFFE0E0E0),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(maxOf(30f, screenWidth * 0.025f).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Card icon
            Text(
                text = when (title) {
                    "Throws" -> "🎯"
                    "Horizontal Jumps" -> "🏃‍♂️"
                    else -> "🏃‍♂️"
                },
                fontSize = 48.sp,
                modifier = Modifier.padding(bottom = 15.dp)
            )
            
            Text(
                text = title,
                fontSize = maxOf(18f, screenWidth * 0.022f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = description,
                fontSize = maxOf(14f, screenWidth * 0.018f).sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center
            )
        }
    }
}

// DEVICE_SETUP Screen - Complete implementation
@Composable
fun DeviceSetupScreenExact(
    eventType: String?,
    devices: DeviceConfig,
    isDemoMode: Boolean,
    onConnectDevice: (String) -> Unit,
    onToggleDeviceSetupModal: (String) -> Unit,
    onContinue: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "Device Setup - ${eventType ?: ""}",
            fontSize = maxOf(24f, screenWidth * 0.028f).sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 30.dp)
        )
        
        // Device list based on event type
        when (eventType) {
            "Throws" -> {
                // EDM Device for Throws
                DeviceStatusCard(
                    deviceType = "EDM Device",
                    connected = devices.edm.connected,
                    isDemoMode = isDemoMode,
                    connectionType = devices.edm.connectionType,
                    serialPort = devices.edm.serialPort,
                    ipAddress = devices.edm.ipAddress,
                    port = devices.edm.port,
                    isEnabled = true,
                    onConnect = { onConnectDevice("edm") },
                    onConfigure = { onToggleDeviceSetupModal("edm") }
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Scoreboard for Throws (greyed out)
                DeviceStatusCard(
                    deviceType = "Scoreboard",
                    connected = devices.scoreboard.connected,
                    isDemoMode = isDemoMode,
                    connectionType = devices.scoreboard.connectionType,
                    serialPort = devices.scoreboard.serialPort,
                    ipAddress = devices.scoreboard.ipAddress,
                    port = devices.scoreboard.port,
                    isEnabled = false,
                    onConnect = { /* Disabled */ },
                    onConfigure = { /* Disabled */ }
                )
            }
            
            "Horizontal Jumps" -> {
                // EDM Device for Jumps (greyed out)
                DeviceStatusCard(
                    deviceType = "EDM Device",
                    connected = devices.edm.connected,
                    isDemoMode = isDemoMode,
                    connectionType = devices.edm.connectionType,
                    serialPort = devices.edm.serialPort,
                    ipAddress = devices.edm.ipAddress,
                    port = devices.edm.port,
                    isEnabled = false,
                    onConnect = { /* Disabled */ },
                    onConfigure = { /* Disabled */ }
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Wind Gauge for Jumps
                DeviceStatusCard(
                    deviceType = "Wind Gauge",
                    connected = devices.wind.connected,
                    isDemoMode = isDemoMode,
                    connectionType = devices.wind.connectionType,
                    serialPort = devices.wind.serialPort,
                    ipAddress = devices.wind.ipAddress,
                    port = devices.wind.port,
                    isEnabled = true,
                    onConnect = { onConnectDevice("wind") },
                    onConfigure = { onToggleDeviceSetupModal("wind") }
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Scoreboard for Jumps (greyed out)
                DeviceStatusCard(
                    deviceType = "Scoreboard",
                    connected = devices.scoreboard.connected,
                    isDemoMode = isDemoMode,
                    connectionType = devices.scoreboard.connectionType,
                    serialPort = devices.scoreboard.serialPort,
                    ipAddress = devices.scoreboard.ipAddress,
                    port = devices.scoreboard.port,
                    isEnabled = false,
                    onConnect = { /* Disabled */ },
                    onConfigure = { /* Disabled */ }
                )
            }
        }
        
        // Bottom spacing for navigation bar
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun DeviceStatusCard(
    deviceType: String,
    connected: Boolean,
    isDemoMode: Boolean,
    connectionType: String,
    serialPort: String,
    ipAddress: String,
    port: Int,
    isEnabled: Boolean = true,
    onConnect: () -> Unit,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) Color.White else Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isEnabled) 4.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = deviceType,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) Color(0xFF333333) else Color(0xFF999999)
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Status
            Text(
                text = when {
                    !isEnabled -> "NOT AVAILABLE"
                    isDemoMode -> "SIMULATED"
                    connected -> "CONNECTED"
                    else -> "DISCONNECTED"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    !isEnabled -> Color(0xFF999999)
                    isDemoMode -> Color(0xFFFF9800)
                    connected -> Color(0xFF4CAF50)
                    else -> Color(0xFFF44336)
                }
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Connection details - hide serial/network info in demo mode or when disabled
            if (!isDemoMode && isEnabled) {
                Text(
                    text = if (connectionType == "serial") {
                        "Serial: $serialPort"
                    } else {
                        "Network: $ipAddress:$port"
                    },
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
            }
            
            Spacer(modifier = Modifier.height(15.dp))
            
            // Action buttons - only show in live mode and when enabled
            if (!isDemoMode && isEnabled) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (connected) Color(0xFFF44336) else Color(0xFF4CAF50)
                        )
                    ) {
                        Text(
                            text = if (connected) "Disconnect" else "Connect",
                            color = Color.White
                        )
                    }
                    
                    OutlinedButton(
                        onClick = onConfigure,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Configure",
                            color = Color(0xFF1976D2)
                        )
                    }
                }
            }
        }
    }
}