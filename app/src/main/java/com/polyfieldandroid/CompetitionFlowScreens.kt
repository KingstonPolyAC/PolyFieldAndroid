package com.polyfieldandroid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.util.Log

/**
 * Main competition flow screens that integrate all modules
 */

/**
 * Competition Athlete Check-In Screen - Connected mode only
 * Shows list of athletes with check-in functionality and navigation options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionAthleteScreen(
    selectedEvent: PolyFieldApiClient.Event,
    athleteManager: AthleteManagerViewModel,
    onAthleteSelected: (PolyFieldApiClient.Athlete) -> Unit,
    onStartCompetition: () -> Unit,
    modifier: Modifier = Modifier
) {
    val athleteState by athleteManager.athleteState.collectAsState()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    val isLandscape = screenWidth > screenHeight
    
    var showOnlyCheckedIn by remember { mutableStateOf(false) }
    
    // Get athletes from selected event, or use empty list if none
    val allAthletes = selectedEvent.athletes ?: emptyList()
    
    // Filter athletes based on check-in status if needed
    val displayedAthletes = if (showOnlyCheckedIn) {
        allAthletes.filter { athlete ->
            athleteState.checkedInAthletes.contains(athlete.bib)
        }
    } else {
        allAthletes
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(maxOf(20f, screenWidth * 0.025f).dp)
    ) {
        // Header with event name and check-in count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${translateEventName(selectedEvent.name)} - Checked In ${athleteState.checkedInAthletes.size}/${allAthletes.size}",
                fontSize = maxOf(18f, screenWidth * 0.022f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )
            
            // Filter toggle button
            FilterChip(
                onClick = { showOnlyCheckedIn = !showOnlyCheckedIn },
                label = { 
                    Text(
                        if (showOnlyCheckedIn) "Show All" else "Checked In Only",
                        fontSize = 18.sp
                    ) 
                },
                selected = showOnlyCheckedIn,
                leadingIcon = {
                    Icon(
                        painter = painterResource(
                            if (showOnlyCheckedIn) R.drawable.arrow_back_48px else R.drawable.settings_48px
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Add Athlete button (compact design)
        var showAddAthleteDialog by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = { showAddAthleteDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.settings_48px),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Add Athlete",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
        
        // Add Athlete Dialog
        if (showAddAthleteDialog) {
            AddAthleteDialog(
                onDismiss = { showAddAthleteDialog = false },
                onAthleteAdded = { athleteInfo ->
                    // Add the athlete to the list
                    athleteManager.addManualAthlete(
                        bib = athleteInfo.bib,
                        name = athleteInfo.name,
                        club = athleteInfo.club
                    )
                    showAddAthleteDialog = false
                }
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Athletes list - compact layout: 1 column portrait, 2 columns landscape
        val columns = if (isLandscape) 2 else 1
        
        // Calculate competition order for checked-in athletes only
        val checkedInAthletes = allAthletes.filter { athlete ->
            athleteState.checkedInAthletes.contains(athlete.bib)
        }.sortedBy { it.order }
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 100.dp), // Bottom padding for navigation
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(displayedAthletes) { athlete ->
                // Get athlete performance data if available
                val athleteResults = athleteState.athletes.find { it.bib == athlete.bib }
                val currentPosition = if (athleteResults?.getBestMark() != null) {
                    // Calculate position based on best marks within the displayed athletes context
                    val rankedAthletes = displayedAthletes.mapNotNull { dispAthlete ->
                        val results = athleteState.athletes.find { it.bib == dispAthlete.bib }
                        results?.getBestMark()?.let { dispAthlete to results }
                    }.sortedByDescending { (_, results) -> results.getBestMark() ?: 0.0 }
                    
                    val position = rankedAthletes.indexOfFirst { (dispAthlete, _) -> dispAthlete.bib == athlete.bib }
                    if (position >= 0) position + 1 else null
                } else null
                
                // Calculate competition order for checked-in athletes only
                val competitionOrder = if (athleteState.checkedInAthletes.contains(athlete.bib)) {
                    checkedInAthletes.indexOfFirst { it.bib == athlete.bib } + 1
                } else null
                
                val bestMark = athleteResults?.getBestMark()?.let { 
                    String.format("%.2fm", it) 
                }
                
                AthleteGridItem(
                    athlete = athlete,
                    isCheckedIn = athleteState.checkedInAthletes.contains(athlete.bib),
                    onCheckInToggle = { 
                        athleteManager.toggleAthleteCheckIn(athlete.bib)
                    },
                    onAthleteClick = {
                        onAthleteSelected(athlete)
                    },
                    screenWidth = screenWidth,
                    isCompactMode = isLandscape,
                    currentPosition = currentPosition,
                    bestMark = bestMark,
                    competitionOrder = competitionOrder
                )
            }
        }
        
        if (displayedAthletes.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.person_48px),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color(0xFF999999)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (showOnlyCheckedIn) "No athletes checked in yet" else "No athletes found",
                        fontSize = 18.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun AthleteListItem(
    athlete: PolyFieldApiClient.Athlete,
    isCheckedIn: Boolean,
    onCheckInToggle: () -> Unit,
    onAthleteClick: () -> Unit,
    screenWidth: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAthleteClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isCheckedIn) Color(0xFFE8F5E8) else Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Athlete info
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bib number
                Card(
                    modifier = Modifier.size(40.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCheckedIn) Color(0xFF4CAF50) else Color(0xFF1976D2)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = athlete.bib,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                
                // Name and club
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = athlete.name,
                        fontSize = maxOf(16f, screenWidth * 0.020f).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF333333)
                    )
                    if (athlete.club.isNotEmpty()) {
                        Text(
                            text = athlete.club,
                            fontSize = maxOf(14f, screenWidth * 0.018f).sp,
                            color = Color(0xFF666666)
                        )
                    }
                }
            }
            
            // Check in button
            Button(
                onClick = onCheckInToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCheckedIn) Color(0xFF4CAF50) else Color(0xFF1976D2)
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    painter = painterResource(
                        if (isCheckedIn) R.drawable.settings_48px else R.drawable.person_48px
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isCheckedIn) "Checked In" else "Check In",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Compact athlete grid item for landscape mode and efficient space usage
 * Shows bib number prominently and allows more athletes per screen
 */
@Composable
fun AthleteGridItem(
    athlete: PolyFieldApiClient.Athlete,
    isCheckedIn: Boolean,
    onCheckInToggle: () -> Unit,
    onAthleteClick: () -> Unit,
    screenWidth: Int,
    isCompactMode: Boolean = false,
    currentPosition: Int? = null, // Current ranking position when measurements exist
    bestMark: String? = null, // Best mark when measurements exist
    competitionOrder: Int? = null // Competition order for checked-in athletes only
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAthleteClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isCheckedIn) Color(0xFFE8F5E8) else Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position badge (when available)
            if (currentPosition != null) {
                Card(
                    modifier = Modifier.size(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (currentPosition) {
                            1 -> Color(0xFFFFD700) // Gold
                            2 -> Color(0xFFC0C0C0) // Silver  
                            3 -> Color(0xFFCD7F32) // Bronze
                            else -> Color(0xFF9E9E9E) // Gray
                        }
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$currentPosition",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            // Bib number
            Card(
                modifier = Modifier.size(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCheckedIn) Color(0xFF4CAF50) else Color(0xFF1976D2)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = athlete.bib,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Athlete info column
            Column(modifier = Modifier.weight(1f)) {
                // Name and order
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = athlete.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Show competition order only for checked-in athletes
                    if (competitionOrder != null) {
                        Text(
                            text = "#$competitionOrder",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF666666)
                        )
                    }
                }
                
                // Club name
                if (athlete.club.isNotEmpty()) {
                    Text(
                        text = athlete.club,
                        fontSize = 12.sp,
                        color = Color(0xFF666666),
                        maxLines = 1
                    )
                }
                
                // Best mark (when available)
                if (bestMark != null && bestMark.isNotEmpty()) {
                    Text(
                        text = bestMark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1976D2)
                    )
                }
            }
            
            // Compact check-in button
            Button(
                onClick = onCheckInToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCheckedIn) Color(0xFF4CAF50) else Color(0xFF1976D2)
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    painter = painterResource(
                        if (isCheckedIn) R.drawable.settings_48px else R.drawable.person_48px
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isCheckedIn) "✓" else "Check",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Competition Results and Standings Screen
 * Shows all athletes with their current standings and performance
 */
@Composable
fun CompetitionResultsScreen(
    selectedEvent: PolyFieldApiClient.Event,
    athleteManager: AthleteManagerViewModel,
    onBackToCompetition: () -> Unit,
    onViewAthleteHeatmap: (String) -> Unit = {},
    onViewOverallHeatmap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val athleteState by athleteManager.athleteState.collectAsState()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    val isLandscape = screenWidth > screenHeight
    
    // Get only checked-in athletes with performance data
    val allAthletes = selectedEvent.athletes ?: emptyList()
    val checkedInAthletes = allAthletes.filter { athlete ->
        athleteState.checkedInAthletes.contains(athlete.bib)
    }
    val athletesWithData = checkedInAthletes.map { athlete ->
        val athleteResults = athleteState.athletes.find { it.bib == athlete.bib }
        athlete to athleteResults
    }
    
    // Sort athletes: those with results by performance, those without at the end
    val sortedAthletes = athletesWithData.sortedWith { (_, results1), (_, results2) ->
        val mark1 = results1?.getBestMark()
        val mark2 = results2?.getBestMark()
        when {
            mark1 != null && mark2 != null -> mark2.compareTo(mark1) // Descending by performance
            mark1 != null && mark2 == null -> -1 // Results first
            mark1 == null && mark2 != null -> 1 // No results last
            else -> 0 // Both null, maintain order
        }
    }
    
    val athletesWithResults = sortedAthletes.filter { (_, results) -> results?.getBestMark() != null }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with heatmap button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${translateEventName(selectedEvent.name)} - Results & Standings",
                fontSize = maxOf(20f, screenWidth * 0.025f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )

            // Overall heatmap button (only show if there are results)
            if (athletesWithResults.isNotEmpty()) {
                Button(
                    onClick = onViewOverallHeatmap,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_dialog_map),
                        contentDescription = "Overall Heatmap",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Overall Heatmap", fontSize = 14.sp)
                }
            }
        }
        
        // Summary stats
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${checkedInAthletes.size}",
                        fontSize = 32.sp, // Increased from 24sp to 32sp for better visibility
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2)
                    )
                    Text(
                        text = "Total Athletes",
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${athletesWithResults.size}",
                        fontSize = 32.sp, // Increased from 24sp to 32sp for better visibility
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "With Results",
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val bestResult = athletesWithResults.firstOrNull()?.second?.getBestMark()
                    Text(
                        text = bestResult?.let { String.format("%.2fm", it) } ?: "—",
                        fontSize = 32.sp, // Increased from 24sp to 32sp for better visibility
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800)
                    )
                    Text(
                        text = "Best Mark",
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                }
            }
        }
        
        // Results list
        if (athletesWithResults.isEmpty()) {
            // Show "No Results Recorded" message
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Results Recorded",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Athletes will appear here once measurements are recorded",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Show all athletes with N/A positions
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(allAthletes.size) { index ->
                    val athlete = allAthletes[index]
                    
                    ResultsListItem(
                        position = null, // N/A position
                        athlete = athlete,
                        results = null, // No results
                        screenWidth = screenWidth,
                        isCompact = isLandscape
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(sortedAthletes.size) { index ->
                    val (athlete, results) = sortedAthletes[index]
                    val position = if (results?.getBestMark() != null) {
                        // Calculate position among athletes with results
                        athletesWithResults.indexOfFirst { (a, _) -> a.bib == athlete.bib } + 1
                    } else {
                        null // N/A for athletes without results
                    }

                    ResultsListItem(
                        position = position,
                        athlete = athlete,
                        results = results,
                        screenWidth = screenWidth,
                        isCompact = isLandscape,
                        onViewHeatmap = { onViewAthleteHeatmap(athlete.bib) }
                    )
                }
            }
        }
    }
}

@Composable
fun ResultsListItem(
    position: Int?,
    athlete: PolyFieldApiClient.Athlete,
    results: CompetitionAthlete?,
    screenWidth: Int,
    isCompact: Boolean = false,
    onViewHeatmap: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (position) {
                1 -> Color(0xFFFFF8E1) // Gold background
                2 -> Color(0xFFF5F5F5) // Silver background
                3 -> Color(0xFFFFF3E0) // Bronze background
                else -> Color.White
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position badge
            Card(
                modifier = Modifier.size(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (position) {
                        1 -> Color(0xFFFFD700) // Gold
                        2 -> Color(0xFFC0C0C0) // Silver
                        3 -> Color(0xFFCD7F32) // Bronze
                        null -> Color(0xFFE0E0E0) // Light gray for N/A
                        else -> Color(0xFF9E9E9E) // Gray
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = position?.toString() ?: "N/A",
                        fontSize = if (position == null) 10.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (position == null) Color(0xFF666666) else Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Bib number
            Card(
                modifier = Modifier.size(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1976D2)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = athlete.bib,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Athlete details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = athlete.name,
                    fontSize = if (isCompact) 14.sp else 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF333333)
                )
                if (athlete.club.isNotEmpty()) {
                    Text(
                        text = athlete.club,
                        fontSize = if (isCompact) 11.sp else 12.sp,
                        color = Color(0xFF666666)
                    )
                }
            }
            
            // Performance data
            Column(
                horizontalAlignment = Alignment.End
            ) {
                val bestMark = results?.getBestMark()
                // Count ALL attempts including X and P
                val totalAttempts = results?.attempts?.size ?: 0
                if (bestMark != null) {
                    Text(
                        text = String.format("%.2fm", bestMark),
                        fontSize = if (isCompact) 24.sp else 28.sp, // Increased from 16/18sp to 24/28sp for field visibility
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2)
                    )
                    Text(
                        text = "$totalAttempts attempt${if (totalAttempts != 1) "s" else ""}",
                        fontSize = if (isCompact) 10.sp else 11.sp,
                        color = Color(0xFF666666)
                    )
                } else {
                    if (totalAttempts > 0) {
                        Text(
                            text = "$totalAttempts attempt${if (totalAttempts != 1) "s" else ""} (no valid marks)",
                            fontSize = if (isCompact) 12.sp else 14.sp,
                            color = Color(0xFF999999),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    } else {
                        Text(
                            text = "No results",
                            fontSize = if (isCompact) 12.sp else 14.sp,
                            color = Color(0xFF999999),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            // Heatmap button (only show if athlete has results with coordinates)
            if (results != null && results.heatmapData.isNotEmpty()) {
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = onViewHeatmap,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_dialog_map),
                        contentDescription = "View Heatmap",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Add Athlete Dialog for manual athlete entry
 */
data class AthleteInfo(
    val bib: String,
    val name: String,
    val club: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAthleteDialog(
    onDismiss: () -> Unit,
    onAthleteAdded: (AthleteInfo) -> Unit
) {
    var bib by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var club by remember { mutableStateOf("") }
    
    val isValid = bib.isNotBlank() && name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Add Athlete",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Bib Number
                OutlinedTextField(
                    value = bib,
                    onValueChange = { bib = it },
                    label = { Text("Bib Number") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // Club
                OutlinedTextField(
                    value = club,
                    onValueChange = { club = it },
                    label = { Text("Club (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) {
                        onAthleteAdded(
                            AthleteInfo(
                                bib = bib.trim(),
                                name = name.trim(),
                                club = club.trim()
                            )
                        )
                    }
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                Text("Add Athlete", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF666666))
            }
        }
    )
}

/**
 * Competition Measurement Screen - Main competition interface
 * Handles athlete rotation, rounds, measurements, and rankings
 */
@Composable
fun CompetitionMeasurementScreen(
    selectedEvent: PolyFieldApiClient.Event,
    athleteManager: AthleteManagerViewModel,
    measurementManager: CompetitionMeasurementManager,
    modeManager: ModeManagerViewModel,
    onBackToAthletes: () -> Unit,
    onEndCompetition: () -> Unit,
    onNextAthlete: () -> Unit = {},
    onRegisterNextAthleteCallback: ((callback: () -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val athleteState by athleteManager.athleteState.collectAsState()
    val measurementState by measurementManager.measurementState.collectAsState()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val scope = rememberCoroutineScope()
    
    // Get checked-in athletes for competition rotation
    val allAthletes = selectedEvent.athletes ?: emptyList()
    val checkedInAthletes = allAthletes.filter { athlete ->
        athleteState.checkedInAthletes.contains(athlete.bib)
    }
    
    // Competition state - get current round from competition manager reactively
    val competitionState by measurementManager.competitionState.collectAsState()
    val currentRound = competitionState.currentRound

    // Local state for managing current athlete within checked-in athletes
    var currentAthleteIndex by remember { mutableStateOf(0) }
    var selectedRound by remember { mutableStateOf(currentRound) }

    // Update selectedRound when currentRound changes
    LaunchedEffect(currentRound) {
        selectedRound = currentRound
    }

    // Get current athlete - default to first checked-in athlete
    val currentAthlete = if (checkedInAthletes.isNotEmpty() && currentAthleteIndex < checkedInAthletes.size) {
        checkedInAthletes[currentAthleteIndex].also { athlete ->
            android.util.Log.d("CompetitionFlow", "Current athlete: ${athlete.name} (${athlete.bib}) at index $currentAthleteIndex/${checkedInAthletes.size}, Round: $selectedRound")
        }
    } else {
        android.util.Log.d("CompetitionFlow", "No current athlete - index: $currentAthleteIndex, checkedIn: ${checkedInAthletes.size}")
        null
    }

    // Reset athlete index when checked-in athletes change
    LaunchedEffect(checkedInAthletes.size) {
        if (checkedInAthletes.isNotEmpty() && currentAthleteIndex >= checkedInAthletes.size) {
            currentAthleteIndex = 0
        }
    }

    // Sync local UI state with backend athlete state changes
    LaunchedEffect(athleteState.currentAthlete?.bib) {
        val currentBackendAthlete = athleteState.currentAthlete
        if (currentBackendAthlete != null && checkedInAthletes.isNotEmpty()) {
            val backendIndex = checkedInAthletes.indexOfFirst { it.bib == currentBackendAthlete.bib }
            if (backendIndex >= 0 && backendIndex != currentAthleteIndex) {
                currentAthleteIndex = backendIndex
                android.util.Log.d("CompetitionFlow", "🔄 UI synchronized with backend: index $currentAthleteIndex -> athlete ${currentBackendAthlete.name}")
            }
        }
    }

    // Define the callback with popup logic
    val nextAthleteCallback: () -> Unit = {
        Log.d("CompetitionFlow", "🔵 Next Athlete callback executed with popup logic")

        // Check if all athletes have measurements for this round BEFORE advancing
        val allCompletedThisRound = checkedInAthletes.all { athlete ->
            measurementState.measurementHistory.any { measurement ->
                measurement.athleteBib == athlete.bib &&
                measurement.round == competitionState.currentRound &&
                (measurement.distance != null && measurement.distance > 0 ||
                 measurement.isPass ||
                 !measurement.isValid) // Covers distance, pass (P), and foul (X)
            }
        }

        Log.d("CompetitionFlow", "🔵 Round completion check: allCompleted=$allCompletedThisRound, round=${competitionState.currentRound}")

        if (allCompletedThisRound && competitionState.currentRound in 1..5) {
            Log.d("CompetitionFlow", "🔵 Triggering round transition popup for round ${competitionState.currentRound}")
            when (competitionState.currentRound) {
                3 -> {
                    // Round 3-4 transition: Show athlete cut popup
                    measurementManager.showAthleteCutPopup(true)
                }
                in 4..5 -> {
                    // Round 4-5 and 5-6 transitions: Show reorder confirmation popup only
                    measurementManager.showReorderConfirmationPopup(true)
                }
                else -> {
                    // Rounds 1-2 and 2-3: Show standard popup
                    measurementManager.showRoundTransitionPopup(true)
                }
            }
        } else {
            Log.d("CompetitionFlow", "🔵 Advancing to next athlete without popup")
            // Advance to next athlete locally first
            if (currentAthleteIndex < checkedInAthletes.size - 1) {
                currentAthleteIndex++
                Log.d("CompetitionFlow", "🔵 Advanced to next athlete index: $currentAthleteIndex")
            } else {
                currentAthleteIndex = 0 // Wrap around to first athlete
                Log.d("CompetitionFlow", "🔵 Wrapped around to first athlete")
            }
        }
    }

    // Register the callback with the parent navigation system
    LaunchedEffect(onRegisterNextAthleteCallback) {
        onRegisterNextAthleteCallback?.invoke(nextAthleteCallback)
        Log.d("CompetitionFlow", "🔵 Registered Next Athlete callback with navigation system")
    }

    // Start competition with correct athlete count - only once when we have checked-in athletes
    LaunchedEffect(checkedInAthletes.isNotEmpty()) {
        if (checkedInAthletes.isNotEmpty()) {
            measurementManager.setDemoMode(true)
            val actualCheckedInCount = checkedInAthletes.size
            android.util.Log.d("CompetitionFlow", "Starting competition with $actualCheckedInCount checked-in athletes")
            measurementManager.startCompetitionWithCount(actualCheckedInCount)
        }
    }
    
    val isLoading = measurementManager.isLoading
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(maxOf(8f, screenWidth * 0.012f).dp)
    ) {

        if (currentAthlete != null) {
            // Use the enhanced measurement screen for individual athlete
            EnhancedAthleteMeasurementScreen(
                currentAthlete = currentAthlete,
                allAthletes = checkedInAthletes,
                athleteManager = athleteManager,
                measurementManager = measurementManager,
                currentRound = currentRound,
                totalRounds = 6,
                onBackToAthletes = onBackToAthletes,
                onEndCompetition = onEndCompetition,
                onNextAthlete = nextAthleteCallback,
                onPreviousAthlete = {
                    android.util.Log.d("CompetitionFlow", "Previous athlete requested: $currentAthleteIndex -> ${currentAthleteIndex - 1}")
                    if (currentAthleteIndex > 0) {
                        currentAthleteIndex--
                        android.util.Log.d("CompetitionFlow", "Went back to athlete ${checkedInAthletes[currentAthleteIndex].name}")
                    } else {
                        android.util.Log.d("CompetitionFlow", "Cannot go back - at first athlete")
                    }
                },
                modifier = Modifier.weight(1f) // Take remaining space
            )
        } else {
            // No current athlete - show selection prompt
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No Athletes Selected",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF666666)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Please check in athletes to begin the competition",
                    fontSize = 16.sp,
                    color = Color(0xFF999999),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onBackToAthletes,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Text(
                        text = "Back to Athletes",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Handle round completion dialogs
        HandleRoundCompletionDialogs(
            measurementState = measurementState,
            measurementManager = measurementManager,
            onEndCompetition = onEndCompetition
        )
    }
}

@Composable
fun CompetitionHeader(
    eventName: String,
    currentRound: Int,
    totalRounds: Int,
    screenWidth: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1976D2)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = eventName,
                fontSize = maxOf(20f, screenWidth * 0.024f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Round $currentRound of $totalRounds",
                fontSize = maxOf(16f, screenWidth * 0.020f).sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE3F2FD),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CurrentAthleteCard(
    athlete: PolyFieldApiClient.Athlete,
    attemptNumber: Int,
    bestMark: Double?,
    currentRanking: Int,
    screenWidth: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Current Athlete",
                fontSize = maxOf(14f, screenWidth * 0.018f).sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF666666)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Athlete info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bib and name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Bib number
                    Card(
                        modifier = Modifier.size(50.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1976D2)),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = athlete.bib,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    
                    // Name and club
                    Column {
                        Text(
                            text = athlete.name,
                            fontSize = maxOf(18f, screenWidth * 0.022f).sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333)
                        )
                        if (athlete.club.isNotEmpty()) {
                            Text(
                                text = athlete.club,
                                fontSize = maxOf(14f, screenWidth * 0.018f).sp,
                                color = Color(0xFF666666)
                            )
                        }
                    }
                }
                
                // Stats
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Rank: #$currentRanking",
                        fontSize = maxOf(16f, screenWidth * 0.020f).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2)
                    )
                    Text(
                        text = "Attempt: $attemptNumber/3",
                        fontSize = maxOf(14f, screenWidth * 0.018f).sp,
                        color = Color(0xFF666666)
                    )
                    bestMark?.let { mark ->
                        Text(
                            text = "Best: ${String.format("%.2fm", mark)}",
                            fontSize = maxOf(14f, screenWidth * 0.018f).sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MeasurementInterface(
    measurement: String,
    isLoading: Boolean,
    onMeasure: () -> Unit,
    onRecordResult: (Double) -> Unit,
    onNextAthlete: () -> Unit,
    screenWidth: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Measurement display
            Text(
                text = "Distance:",
                fontSize = maxOf(16f, screenWidth * 0.020f).sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF666666)
            )
            
            Text(
                text = measurement.ifEmpty { "--" },
                fontSize = maxOf(36f, screenWidth * 0.06f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Measure button
                Button(
                    onClick = onMeasure,
                    enabled = !isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Text(
                        text = if (isLoading) "Measuring..." else "MEASURE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                // Record/Next button (show when measurement available)
                if (measurement.isNotEmpty() && !isLoading) {
                    Button(
                        onClick = {
                            measurement.replace("m", "").toDoubleOrNull()?.let { distance ->
                                onRecordResult(distance)
                                onNextAthlete()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Text(
                            text = "RECORD",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Current athlete card showing athlete details and performance
 */
@Composable
fun CurrentAthleteCard(
    athlete: PolyFieldApiClient.Athlete,
    attemptNumber: Int,
    bestMark: String?,
    currentRanking: Int,
    screenWidth: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Current Athlete",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF666666)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${athlete.name} (#${athlete.bib})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    Text(
                        text = athlete.club,
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Ranking: #$currentRanking",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1976D2)
                    )
                    Text(
                        text = "Best: ${bestMark ?: "--"}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Attempt #$attemptNumber",
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1976D2)
            )
        }
    }
}

@Composable
fun CompetitionRotationDisplay(
    athletes: List<PolyFieldApiClient.Athlete>,
    measurementManager: CompetitionMeasurementManager,
    currentAthleteIndex: Int,
    currentRound: Int,
    screenWidth: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Competition Order & Rankings",
                fontSize = maxOf(16f, screenWidth * 0.020f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            LazyColumn(
                modifier = Modifier.heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(athletes.take(8)) { athlete -> // Show max 8 for space
                    val index = athletes.indexOf(athlete)
                    val isCurrent = index == currentAthleteIndex
                    val bestMark = measurementManager.getBestMarkForAthlete(athlete.bib)
                    val ranking = measurementManager.getRankingForAthlete(athlete.bib)
                    
                    RotationAthleteItem(
                        athlete = athlete,
                        ranking = ranking,
                        bestMark = bestMark,
                        isCurrent = isCurrent,
                        screenWidth = screenWidth
                    )
                }
            }
        }
    }
}

@Composable
fun RotationAthleteItem(
    athlete: PolyFieldApiClient.Athlete,
    ranking: Int,
    bestMark: Double?,
    isCurrent: Boolean,
    screenWidth: Int
) {
    val backgroundColor = if (isCurrent) Color(0xFFE3F2FD) else Color(0xFFFAFAFA)
    val borderColor = if (isCurrent) Color(0xFF1976D2) else Color.Transparent
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (isCurrent) it.border(2.dp, borderColor, RoundedCornerShape(6.dp)) else it },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank and athlete info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Ranking
                Text(
                    text = "#$ranking",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2),
                    modifier = Modifier.width(35.dp)
                )
                
                // Bib
                Text(
                    text = athlete.bib,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                    modifier = Modifier.width(40.dp)
                )
                
                // Name
                Text(
                    text = athlete.name,
                    fontSize = maxOf(14f, screenWidth * 0.018f).sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = Color(0xFF333333),
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Best mark
            Text(
                text = bestMark?.let { String.format("%.2fm", it) } ?: "--",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (bestMark != null) Color(0xFF4CAF50) else Color(0xFF999999)
            )
        }
    }
}

@Composable
fun HandleRoundCompletionDialogs(
    measurementState: CompetitionMeasurementState,
    measurementManager: CompetitionMeasurementManager,
    onEndCompetition: () -> Unit
) {
    val competitionState by measurementManager.competitionState.collectAsState()

    // Show standard round transition popup (rounds 1-2, 2-3)
    if (competitionState.showRoundTransitionPopup) {
        AlertDialog(
            onDismissRequest = {
                measurementManager.showRoundTransitionPopup(false)
            },
            title = {
                Text(
                    text = "Round ${competitionState.currentRound} Complete",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "All athletes have completed round ${competitionState.currentRound}. Ready to advance to round ${competitionState.currentRound + 1}?",
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        measurementManager.showRoundTransitionPopup(false)
                        measurementManager.advanceToNextRound()
                        android.util.Log.d("BottomNavDebug", "🔵 onContinueToNextRound completed successfully")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2)
                    )
                ) {
                    Text(
                        text = "Continue to Round ${competitionState.currentRound + 1}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        measurementManager.showRoundTransitionPopup(false)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE0E0E0),
                        contentColor = Color(0xFF333333)
                    )
                ) {
                    Text(
                        text = "Stay in Round ${competitionState.currentRound}",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }

    // Show athlete cut popup (round 3-4)
    if (competitionState.athleteCutState.showCutPopup) {
        AthleteCutDialog(
            currentRound = competitionState.currentRound,
            measurementManager = measurementManager
        )
    }

    // Show athlete progression confirmation popup (round 3-4)
    if (competitionState.athleteCutState.showProgressionPopup) {
        AthleteProgressionDialog(
            measurementManager = measurementManager
        )
    }

    // Show reorder confirmation popup (round 4-5, 5-6)
    if (competitionState.athleteCutState.showReorderPopup) {
        ReorderConfirmationDialog(
            currentRound = competitionState.currentRound,
            measurementManager = measurementManager
        )
    }
}

@Composable
fun AthleteCutDialog(
    currentRound: Int,
    measurementManager: CompetitionMeasurementManager
) {
    var selectedAthleteCount by remember { mutableIntStateOf(8) }
    var reorderEnabled by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            measurementManager.showAthleteCutPopup(false)
        },
        title = {
            Text(
                text = "Round $currentRound Complete - Athlete Selection",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "How many athletes should advance to the final rounds?",
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Athlete count selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(4, 6, 8, 10).forEach { count ->
                        FilterChip(
                            onClick = { selectedAthleteCount = count },
                            label = { Text("$count") },
                            selected = selectedAthleteCount == count
                        )
                    }
                    FilterChip(
                        onClick = { selectedAthleteCount = 999 }, // Use 999 to represent "All"
                        label = { Text("All") },
                        selected = selectedAthleteCount == 999
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reorder option
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = reorderEnabled,
                        onCheckedChange = { reorderEnabled = it }
                    )
                    Text(
                        text = "Reorder athletes worst to best",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    measurementManager.setSelectedAthleteCount(selectedAthleteCount)
                    measurementManager.setReorderEnabled(reorderEnabled)
                    measurementManager.calculateAndSetAthleteCut() // Calculate using selected count
                    measurementManager.showAthleteCutPopup(false)
                    measurementManager.showProgressionConfirmationPopup(true)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1976D2)
                )
            ) {
                Text(
                    text = "Continue",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            Button(
                onClick = {
                    measurementManager.showAthleteCutPopup(false)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE0E0E0),
                    contentColor = Color(0xFF333333)
                )
            ) {
                Text(
                    text = "Cancel",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
fun AthleteProgressionDialog(
    measurementManager: CompetitionMeasurementManager
) {
    val competitionState by measurementManager.competitionState.collectAsState()
    val athleteCutState = competitionState.athleteCutState
    AlertDialog(
        onDismissRequest = {
            measurementManager.showProgressionConfirmationPopup(false)
        },
        title = {
            Text(
                text = "Athlete Progression Confirmation",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "The following athletes will advance to the next round:",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Advancing athletes list (placeholder - will be populated with actual data)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E8)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "✅ ADVANCING:",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        // Display actual advancing athletes
                        if (athleteCutState.advancingAthletes.isNotEmpty()) {
                            athleteCutState.advancingAthletes.forEach { ranking ->
                                Text(
                                    text = "${ranking.position}. ${ranking.athlete.name} (${String.format("%.2fm", ranking.bestMark)})",
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "${athleteCutState.selectedAthleteCount} athletes will advance",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Eliminated athletes list (placeholder - will be populated with actual data)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEBEE)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "❌ ELIMINATED:",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD32F2F)
                        )
                        // Display actual eliminated athletes
                        if (athleteCutState.eliminatedAthletes.isNotEmpty()) {
                            athleteCutState.eliminatedAthletes.forEach { ranking ->
                                Text(
                                    text = "${ranking.position}. ${ranking.athlete.name} (${String.format("%.2fm", ranking.bestMark)})",
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "Remaining athletes will be eliminated",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    measurementManager.applyAthleteCutAndAdvance()
                    measurementManager.showProgressionConfirmationPopup(false)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1976D2)
                )
            ) {
                Text(
                    text = "Confirm & Advance",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            Button(
                onClick = {
                    measurementManager.showProgressionConfirmationPopup(false)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE0E0E0),
                    contentColor = Color(0xFF333333)
                )
            ) {
                Text(
                    text = "Cancel",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
fun ReorderConfirmationDialog(
    currentRound: Int,
    measurementManager: CompetitionMeasurementManager
) {
    val competitionState by measurementManager.competitionState.collectAsState()

    // Get the default reorder setting from Round 3 if available
    val defaultReorderEnabled = competitionState.finalRoundSettings?.reorderEnabled ?: false
    var reorderEnabled by remember { mutableStateOf(defaultReorderEnabled) }

    AlertDialog(
        onDismissRequest = { },
        title = {
            Column {
                Text(
                    text = "Round $currentRound Complete",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                Text(
                    text = "Ready to advance to Round ${currentRound + 1}",
                    fontSize = 18.sp,
                    color = Color(0xFF666666)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Round Transition Options:",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Reorder option only
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { reorderEnabled = !reorderEnabled }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = reorderEnabled,
                        onCheckedChange = { reorderEnabled = it }
                    )

                    Column(
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "Reorder Athletes (Worst to Best)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF333333)
                        )
                        Text(
                            text = "Change throwing order for Round ${currentRound + 1}",
                            fontSize = 14.sp,
                            color = Color(0xFF666666)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Apply reorder setting and advance to next round
                    if (reorderEnabled) {
                        measurementManager.applyAthleteReorder()
                    }
                    measurementManager.showReorderConfirmationPopup(false)
                    measurementManager.advanceToNextRound()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1976D2)
                )
            ) {
                Text(
                    text = "Continue to Round ${currentRound + 1}",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            Button(
                onClick = {
                    measurementManager.showReorderConfirmationPopup(false)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE0E0E0),
                    contentColor = Color(0xFF333333)
                )
            ) {
                Text(
                    text = "Cancel",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

/**
 * Mode Selection Screen - Choose between Stand-Alone and Connected modes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelectionScreen(
    modeManager: ModeManagerViewModel,
    onModeSelected: (AppMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modeState by modeManager.modeState.collectAsState()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(maxOf(20f, screenWidth * 0.025f).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title matching SelectEventTypeScreenExact
            Text(
                text = "Select Mode",
                fontSize = maxOf(24f, screenWidth * 0.028f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = maxOf(20f, screenHeight * 0.025f).dp)
            )
            
            // Horizontal card container matching original - 2 cards 50:50
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 20.dp, horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeCardMaterialDesign(
                    title = "Stand-Alone Mode",
                    description = "Direct EDM connection",
                    iconDrawable = R.drawable.sync_disabled_48px,
                    onClick = { 
                        modeManager.setStandaloneMode()
                        onModeSelected(AppMode.STANDALONE)
                    },
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    modifier = Modifier.weight(1f)
                )
                
                ModeCardMaterialDesign(
                    title = "Connected Mode", 
                    description = "Server-based competition",
                    iconDrawable = R.drawable.sync_desktop_48px,
                    onClick = { 
                        modeManager.setConnectedMode()
                        onModeSelected(AppMode.CONNECTED)
                    },
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Error message (if any)
            modeState.errorMessage?.let { error ->
                Text(
                    text = error,
                    fontSize = maxOf(14f, screenWidth * 0.018f).sp,
                    color = Color(0xFFE53E3E),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

/**
 * Mode selection card with Material Design icons
 */
@Composable
fun ModeCardMaterialDesign(
    title: String,
    description: String,
    iconDrawable: Int,
    onClick: () -> Unit,
    screenWidth: Int,
    screenHeight: Int,
    isStandalone: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(maxOf(220f, screenHeight * 0.32f).dp)
            .clickable { onClick() }
            .border(
                width = 3.dp,
                color = Color(0xFFE0E0E0),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isStandalone) Color(0xFF1976D2) else Color.White
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
            Icon(
                painter = painterResource(iconDrawable),
                contentDescription = title,
                modifier = Modifier
                    .size(maxOf(64f, screenWidth * 0.08f).dp),
                tint = if (isStandalone) Color.White else Color(0xFF1976D2)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = title,
                fontSize = maxOf(18f, screenWidth * 0.022f).sp,
                fontWeight = FontWeight.Bold,
                color = if (isStandalone) Color.White else Color(0xFF333333),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = description,
                fontSize = maxOf(14f, screenWidth * 0.018f).sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Event Selection Screen - 50/50 split with Server Events and Stand-Alone mode
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSelectionScreen(
    modeManager: ModeManagerViewModel,
    onEventSelected: (PolyFieldApiClient.Event) -> Unit,
    onStandAloneSelected: () -> Unit,
    onBackToMode: () -> Unit,
    onEditServer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val availableEvents by modeManager.availableEvents.collectAsState()
    val modeState by modeManager.modeState.collectAsState()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(maxOf(20f, screenWidth * 0.025f).dp)
    ) {
        // 66/33 Split Layout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Left Side - Server Events (66%)
            ServerEventsCard(
                availableEvents = availableEvents,
                modeState = modeState,
                onEventSelected = onEventSelected,
                onRefreshEvents = { modeManager.refreshEvents() },
                onEditServer = onEditServer,
                screenWidth = screenWidth,
                modifier = Modifier.weight(2f)
            )
            
            // Right Side - Stand-Alone Mode (33%)
            StandAloneModeCard(
                onStandAloneSelected = onStandAloneSelected,
                screenWidth = screenWidth,
                modifier = Modifier.weight(1f)
            )
        }
        
    }
}

/**
 * Server Events Card - Left side of 50/50 split
 */
@Composable
fun ServerEventsCard(
    availableEvents: List<PolyFieldApiClient.Event>,
    modeState: ModeState,
    onEventSelected: (PolyFieldApiClient.Event) -> Unit,
    onRefreshEvents: () -> Unit,
    onEditServer: () -> Unit,
    screenWidth: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header with connection status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Server Events",
                    fontSize = maxOf(18f, screenWidth * 0.022f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Connection status icon
                    when {
                        modeState.serverConfig.isConnected -> {
                            Icon(
                                painter = painterResource(R.drawable.cloud_done_48px),
                                contentDescription = "Connected",
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFF2E7D32)
                            )
                        }
                        modeState.errorMessage != null -> {
                            Icon(
                                painter = painterResource(R.drawable.cloud_off_48px),
                                contentDescription = "Disconnected",
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFFD32F2F)
                            )
                        }
                        else -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                    
                    // Refresh button
                    IconButton(
                        onClick = onRefreshEvents,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.sync_desktop_48px),
                            contentDescription = "Refresh",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF1976D2)
                        )
                    }
                    
                    // Edit button
                    IconButton(
                        onClick = onEditServer,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.settings_48px),
                            contentDescription = "Settings",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF1976D2)
                        )
                    }
                }
            }
            
            // Connection info
            if (!modeState.serverConfig.isConnected || modeState.errorMessage != null) {
                Text(
                    text = "${modeState.serverConfig.ipAddress}:${modeState.serverConfig.port}",
                    fontSize = 12.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                modeState.errorMessage?.let { error ->
                    Text(
                        text = error,
                        fontSize = 11.sp,
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Events list or loading/error state
            when {
                availableEvents.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableEvents) { event ->
                            ServerEventButton(
                                event = event,
                                onClick = { onEventSelected(event) },
                                screenWidth = screenWidth
                            )
                        }
                    }
                }
                
                modeState.serverConfig.isConnected -> {
                    // Connected but no events
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.data_alert_48px),
                            contentDescription = "No Events",
                            modifier = Modifier.size(32.dp),
                            tint = Color(0xFF666666)
                        )
                        Text(
                            text = "No events available",
                            fontSize = 14.sp,
                            color = Color(0xFF666666),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                
                else -> {
                    // Loading or error state
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (modeState.errorMessage != null) {
                            Icon(
                                painter = painterResource(R.drawable.cloud_off_48px),
                                contentDescription = "Connection Error",
                                modifier = Modifier.size(32.dp),
                                tint = Color(0xFFD32F2F)
                            )
                            Text(
                                text = "Connection Failed",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFD32F2F),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF1976D2)
                            )
                            Text(
                                text = "Connecting...",
                                fontSize = 14.sp,
                                color = Color(0xFF666666),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Stand-Alone Mode Card - Right side of 50/50 split
 */
@Composable
fun StandAloneModeCard(
    onStandAloneSelected: () -> Unit,
    screenWidth: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onStandAloneSelected() },
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.sync_disabled_48px),
                contentDescription = "Stand-Alone Mode",
                modifier = Modifier.size(maxOf(48f, screenWidth * 0.08f).dp),
                tint = Color(0xFF1976D2)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Stand-Alone Mode",
                fontSize = maxOf(18f, screenWidth * 0.022f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Direct EDM connection without server",
                fontSize = maxOf(14f, screenWidth * 0.018f).sp,
                color = Color(0xFF666666),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Button(
                onClick = onStandAloneSelected,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                Text(
                    text = "Start Stand-Alone",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Single row event button for server events list
 */
@Composable
fun ServerEventButton(
    event: PolyFieldApiClient.Event,
    onClick: () -> Unit,
    screenWidth: Int
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFE3F2FD),
            contentColor = Color(0xFF1976D2)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = translateEventName(event.name),
                fontSize = maxOf(24f, screenWidth * 0.028f).sp, // Increased from 14f to 24f for better visibility
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1976D2),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )

            if (event.athletes?.isNotEmpty() == true) {
                Text(
                    text = "[${event.athletes?.size}]",
                    fontSize = 22.sp, // Increased from 16sp to 22sp for better visibility
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF666666)
                )
            }
        }
    }
}

/**
 * Map event codes to circle types for automatic calibration
 * HT1, WT1 -> Hammer, SP1 -> Shot, DT1 -> Discus, JT1 -> Javelin
 */
fun getCircleTypeFromEvent(event: PolyFieldApiClient.Event): String {
    val eventName = event.name.uppercase()
    val eventType = event.type.uppercase()
    
    return when {
        eventName.startsWith("HT") || eventName.contains("HAMMER") || eventType.contains("HAMMER") -> "HAMMER_CIRCLE"
        eventName.startsWith("WT") || eventName.contains("WEIGHT") || eventType.contains("WEIGHT") -> "HAMMER_CIRCLE"
        eventName.startsWith("SP") || eventName.contains("SHOT") || eventType.contains("SHOT") -> "SHOT_CIRCLE"
        eventName.startsWith("DT") || eventName.contains("DISCUS") || eventType.contains("DISCUS") -> "DISCUS_CIRCLE"
        eventName.startsWith("JT") || eventName.contains("JAVELIN") || eventType.contains("JAVELIN") -> "JAVELIN_ARC"
        else -> "SHOT_CIRCLE" // Default fallback
    }
}

/**
 * Translate event codes to full event names
 * E.g. "DT1 - Discus Throw" -> "Discus Throw" or "DT1" -> "Discus Throw"
 */
private fun translateEventName(eventName: String): String {
    // Event code translations
    val codeTranslations = mapOf(
        "DT" to "Discus Throw",
        "HT" to "Hammer Throw", 
        "JT" to "Javelin Throw",
        "SP" to "Shot Put",
        "WT" to "Weight Throw",
        "LJ" to "Long Jump",
        "TJ" to "Triple Jump",
        "HJ" to "High Jump",
        "PV" to "Pole Vault"
    )
    
    // Check if the name contains a code pattern (e.g., "DT1", "HT2")
    val codePattern = Regex("([A-Z]{2})\\d+")
    val match = codePattern.find(eventName)
    
    return if (match != null) {
        val code = match.groupValues[1] // Extract just the letters (e.g., "DT" from "DT1")
        val translation = codeTranslations[code]
        
        if (translation != null) {
            // If there's additional descriptive text after the code, use the translation
            val afterCode = eventName.replace(Regex("[A-Z]{2}\\d+\\s*-?\\s*"), "").trim()
            if (afterCode.isNotEmpty() && afterCode.lowercase() != translation.lowercase()) {
                // Use the descriptive text if it's different from our translation
                afterCode
            } else {
                // Use our translation
                translation
            }
        } else {
            // Unknown code, just remove the code pattern and keep the rest
            eventName.replace(Regex("[A-Z]{2}\\d+\\s*-?\\s*"), "").trim().ifEmpty { eventName }
        }
    } else {
        // No code pattern found, return as-is
        eventName
    }
}

@Composable
fun EventCard(
    event: PolyFieldApiClient.Event,
    onClick: () -> Unit,
    screenWidth: Int,
    modifier: Modifier = Modifier
) {
    // Translate event codes to full names
    val cleanEventName = translateEventName(event.name)
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleanEventName,
                    fontSize = maxOf(16f, screenWidth * 0.02f).sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )
                
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = event.type,
                        fontSize = maxOf(12f, screenWidth * 0.016f).sp,
                        color = Color(0xFF666666)
                    )
                    
                    if (event.athletes?.isNotEmpty() == true) {
                        Text(
                            text = "• ${event.athletes?.size ?: 0} athletes",
                            fontSize = maxOf(12f, screenWidth * 0.016f).sp,
                            color = Color(0xFF888888)
                        )
                    }
                }
            }
            
            // Small arrow indicator instead of logo
            Icon(
                painter = painterResource(R.drawable.arrow_forward_48px),
                contentDescription = "Select",
                modifier = Modifier.size(20.dp),
                tint = Color(0xFF1976D2)
            )
        }
    }
}

/**
 * Competition Flow Screen - Main measurement interface
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionFlowScreen(
    event: PolyFieldApiClient.Event,
    modeManager: ModeManagerViewModel,
    appViewModel: AppViewModel,
    onBackToSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by appViewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp
    
    var currentAthleteIndex by remember { mutableStateOf(0) }
    var currentAttempt by remember { mutableStateOf(1) }
    val maxAttempts = 3
    
    val activeAthletes = event.athletes?.sortedBy { it.order } ?: emptyList()
    val currentAthlete = if (activeAthletes.isNotEmpty() && currentAthleteIndex < activeAthletes.size) {
        activeAthletes[currentAthleteIndex]
    } else null
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(maxOf(20f, screenWidth * 0.025f).dp)
    ) {
        // Event header
        Text(
            text = event.name,
            fontSize = maxOf(24f, screenWidth * 0.028f).sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = maxOf(20f, screenHeight * 0.025f).dp)
        )
        
        if (currentAthlete != null) {
            // Current athlete info
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Current Athlete",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        text = "${currentAthlete.name} (#${currentAthlete.bib})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    Text(
                        text = "Attempt $currentAttempt of $maxAttempts",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                }
            }
            
            // Measurement interface
            if (event.type.lowercase().contains("throw") || 
                event.type.lowercase().contains("shot") || 
                event.type.lowercase().contains("discus") ||
                event.type.lowercase().contains("hammer")) {
                
                ThrowsMeasurementInterface(
                    measurement = uiState.measurement,
                    isLoading = uiState.isLoading,
                    onMeasureDistance = { appViewModel.measureDistance() },
                    screenWidth = screenWidth
                )
            } else {
                HorizontalJumpsMeasurementInterface(
                    windMeasurement = uiState.windMeasurement,
                    isLoading = uiState.isLoading,
                    onMeasureWind = { appViewModel.measureWind() },
                    screenWidth = screenWidth
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Navigation controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBackToSetup,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back_48px),
                        contentDescription = "Back",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Setup")
                }
                
                Button(
                    onClick = {
                        // Advance to next attempt or athlete
                        if (currentAttempt < maxAttempts) {
                            currentAttempt++
                        } else if (currentAthleteIndex < activeAthletes.size - 1) {
                            currentAthleteIndex++
                            currentAttempt = 1
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Next")
                    Icon(
                        painter = painterResource(R.drawable.arrow_forward_48px),
                        contentDescription = "Next",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            // No athletes
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No athletes available for this event",
                    fontSize = 16.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
/**
 * Alias functions for MainActivityCompose compatibility
 */
@Composable
fun CompetitionSetupScreenConnected(
    selectedEvent: PolyFieldApiClient.Event,
    modeManager: ModeManagerViewModel,
    onSetupComplete: () -> Unit,
    onBackToEvents: () -> Unit
) {
    // Determine event type from the selected event for calibration flow
    val eventType = determineEventTypeFromEvent(selectedEvent)
    
    // Navigate to appropriate setup flow based on event type
    LaunchedEffect(selectedEvent) {
        // For throws events, we need calibration flow
        if (eventType == "Throws") {
            onSetupComplete() // This will trigger navigation to device setup
        } else {
            // For field events like jumps, go directly to active (no calibration needed)
            onSetupComplete()
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Setting up ${translateEventName(selectedEvent.name)}...")
        }
    }
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

@Composable
fun CompetitionActiveScreenConnected(
    selectedEvent: PolyFieldApiClient.Event,
    modeManager: ModeManagerViewModel,
    appViewModel: AppViewModel,
    onBackToSetup: () -> Unit
) {
    CompetitionFlowScreen(
        event = selectedEvent,
        modeManager = modeManager,
        appViewModel = appViewModel,
        onBackToSetup = onBackToSetup
    )
}

/**
 * Competition measurement interface for recording athlete attempts
 */
@Composable
fun MeasurementInterface(
    measurement: MeasurementResult?,
    isLoading: Boolean,
    onMeasure: () -> Unit,
    onRecordResult: (Double?) -> Unit,
    onNextAthlete: () -> Unit,
    screenWidth: Int
) {
    Column {
        // Current measurement display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Distance Measurement",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = measurement?.distance?.let { String.format("%.2f m", it) } ?: "--",
                    fontSize = 64.sp, // Increased from 36sp to 64sp for field visibility
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Measure button
                    Button(
                        onClick = onMeasure,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                    ) {
                        Text(
                            text = if (isLoading) "Measuring..." else "Measure",
                            color = Color.White
                        )
                    }
                    
                    // Record result button
                    if (measurement?.distance != null) {
                        Button(
                            onClick = { onRecordResult(measurement.distance) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("Record", color = Color.White)
                        }
                    }
                }
                
                // Additional controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Mark as foul button
                    OutlinedButton(
                        onClick = { onRecordResult(null) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Mark as Foul")
                    }
                    
                    // Next athlete button
                    Button(
                        onClick = onNextAthlete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF666666))
                    ) {
                        Text("Next Athlete", color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Enhanced Athlete Measurement Screen with comprehensive features:
 * - Responsive landscape/portrait layouts
 * - Measure/Foul/Pass buttons
 * - Athlete throw history with round breakdown
 * - Clickable round editing
 * - Smart athlete navigation
 * - Current position display
 */
@Composable
fun EnhancedAthleteMeasurementScreen(
    currentAthlete: PolyFieldApiClient.Athlete,
    allAthletes: List<PolyFieldApiClient.Athlete>,
    athleteManager: AthleteManagerViewModel,
    measurementManager: CompetitionMeasurementManager,
    currentRound: Int,
    totalRounds: Int = 6,
    onBackToAthletes: () -> Unit,
    onEndCompetition: () -> Unit,
    onNextAthlete: () -> Unit,
    onPreviousAthlete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val athleteState by athleteManager.athleteState.collectAsState()
    val measurementState by measurementManager.measurementState.collectAsState()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val scope = rememberCoroutineScope()
    
    // Get athlete data - avoid AthleteManager dependencies to prevent cycling
    val competitionAthlete = athleteState.athletes.find { it.bib == currentAthlete.bib }
    val currentPosition = 1 // Simplified for now to avoid cycling issues
    val bestMark = competitionAthlete?.getBestMark()
    val hasMeasurementThisRound = competitionAthlete?.getCurrentRoundMeasurements(currentRound)?.isNotEmpty() ?: false
    val attemptNumber = 1 // Always 1 since only one attempt per round

    // Get athlete-specific measurement for current round - return null to show "--" if no measurement exists
    val athleteSpecificMeasurement = if (hasMeasurementThisRound) {
        // Get the actual measurement from the athlete data for the current round
        val roundMeasurements = competitionAthlete?.getCurrentRoundMeasurements(currentRound)
        val attempt = roundMeasurements?.firstOrNull()
        // Convert AthleteAttempt to MeasurementResult
        attempt?.let {
            MeasurementResult(
                athleteBib = currentAthlete.bib,
                round = currentRound,
                attemptNumber = 1,
                distance = it.distance,
                isValid = it.isValid,
                isPass = it.isPass
            )
        }
    } else {
        null
    }

    // Remove next athlete info call to prevent cycling
    val nextAthlete: PolyFieldApiClient.Athlete? = null
    
    // Competition setup is now handled by parent CompetitionMeasurementScreen

    // Athlete selection is now handled by the parent CompetitionMeasurementScreen
    
    if (isLandscape) {
        // Landscape layout - top 2/3 split 50/50, bottom 1/3 full-width history
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Top 2/3 - Split 50/50 between Athlete Info and Measurement Interface
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f), // Takes 2/3 of available height
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left 50% - Athlete info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    AthleteInfoCard(
                        athlete = currentAthlete,
                        position = currentPosition,
                        bestMark = bestMark,
                        currentRound = currentRound,
                        attemptNumber = attemptNumber,
                        screenWidth = screenWidth
                    )

                }

                // Right 50% - Measurement interface
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    EnhancedMeasurementInterface(
                        measurement = athleteSpecificMeasurement,
                        isLoading = measurementManager.isLoading,
                        onMeasure = {
                            android.util.Log.d("MeasurementCallbacks", "MEASURE CALLBACK: athlete=${currentAthlete.bib}, round=$currentRound, attempt=$attemptNumber")
                            scope.launch {
                                android.util.Log.d("MeasurementCallbacks", "Starting measureThrowForAthlete coroutine...")
                                val result = measurementManager.measureThrowForAthlete(currentAthlete)
                                android.util.Log.d("MeasurementCallbacks", "measureThrowForAthlete result: $result")
                            }
                        },
                        onFoul = {
                            android.util.Log.d("MeasurementCallbacks", "FOUL CALLBACK: athlete=${currentAthlete.bib}, round=$currentRound, attempt=$attemptNumber")
                            measurementManager.recordFoul(currentAthlete.bib, currentRound, attemptNumber)
                            android.util.Log.d("MeasurementCallbacks", "recordFoul completed")
                        },
                        onPass = {
                            android.util.Log.d("MeasurementCallbacks", "PASS CALLBACK: athlete=${currentAthlete.bib}, round=$currentRound, attempt=$attemptNumber")
                            measurementManager.recordPass(currentAthlete.bib, currentRound, attemptNumber)
                            android.util.Log.d("MeasurementCallbacks", "recordPass completed")
                        },
                        screenWidth = screenWidth
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom 1/3 - Full-width throw history (two-column layout)
            AthleteHistoryCard(
                competitionAthlete = competitionAthlete,
                currentRound = currentRound,
                onEditMeasurement = { round, measurement ->
                    measurementManager.editMeasurement(currentAthlete.bib, round, measurement)
                },
                onRoundSelected = { round ->
                    android.util.Log.d("CompetitionFlow", "Selected round $round for direct measurement")
                    measurementManager.setCurrentRound(round)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // Takes 1/3 of available height
                twoColumnLayout = true
            )
        }
    } else {
        // Portrait layout - stacked
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp) // Constrain athlete info height
            ) {
                AthleteInfoCard(
                    athlete = currentAthlete,
                    position = currentPosition,
                    bestMark = bestMark,
                    currentRound = currentRound,
                    attemptNumber = attemptNumber,
                    screenWidth = screenWidth
                )
            }

            
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp) // Constrain measurement interface height to show all buttons
            ) {
                EnhancedMeasurementInterface(
                    measurement = athleteSpecificMeasurement,
                    isLoading = measurementManager.isLoading,
                    onMeasure = {
                        android.util.Log.d("MeasurementCallbacks", "MEASURE CALLBACK (portrait): athlete=${currentAthlete.bib}, round=$currentRound, attempt=$attemptNumber")
                        scope.launch {
                            android.util.Log.d("MeasurementCallbacks", "Starting measureThrowForAthlete coroutine (portrait)...")
                            val result = measurementManager.measureThrowForAthlete(currentAthlete)
                            android.util.Log.d("MeasurementCallbacks", "measureThrowForAthlete result (portrait): $result")
                        }
                    },
                    onFoul = {
                        android.util.Log.d("MeasurementCallbacks", "FOUL CALLBACK (portrait): athlete=${currentAthlete.bib}, round=$currentRound, attempt=$attemptNumber")
                        measurementManager.recordFoul(currentAthlete.bib, currentRound, attemptNumber)
                        android.util.Log.d("MeasurementCallbacks", "recordFoul completed (portrait)")
                    },
                    onPass = {
                        android.util.Log.d("MeasurementCallbacks", "PASS CALLBACK (portrait): athlete=${currentAthlete.bib}, round=$currentRound, attempt=$attemptNumber")
                        measurementManager.recordPass(currentAthlete.bib, currentRound, attemptNumber)
                        android.util.Log.d("MeasurementCallbacks", "recordPass completed (portrait)")
                    },
                    screenWidth = screenWidth
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp) // Constrain athlete history height
            ) {
                AthleteHistoryCard(
                    competitionAthlete = competitionAthlete,
                    currentRound = currentRound,
                    onEditMeasurement = { round, measurement ->
                        measurementManager.editMeasurement(currentAthlete.bib, round, measurement)
                    },
                    onRoundSelected = { round ->
                        android.util.Log.d("CompetitionFlow", "Selected round $round for direct measurement (portrait)")
                        measurementManager.setCurrentRound(round)
                    }
                )
            }
        }
    }
}

@Composable
fun AthleteInfoCard(
    athlete: PolyFieldApiClient.Athlete,
    position: Int,
    bestMark: Double?,
    currentRound: Int,
    attemptNumber: Int,
    screenWidth: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header with position and round
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Round $currentRound",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
                
                // Current position
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            position <= 3 -> Color(0xFFFFD700) // Gold for top 3
                            position <= 8 -> Color(0xFF4CAF50) // Green for advancement
                            else -> Color(0xFFE0E0E0)
                        }
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "#$position",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (position <= 3) Color.Black else Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Athlete info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = athlete.bib,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF666666)
                    )
                    Text(
                        text = athlete.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    Text(
                        text = athlete.club,
                        fontSize = 16.sp,
                        color = Color(0xFF666666)
                    )
                }
                
                // Best mark
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Best Mark",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        text = bestMark?.let { String.format("%.2fm", it) } ?: "--",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (bestMark != null) Color(0xFF4CAF50) else Color(0xFF999999)
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancedMeasurementInterface(
    measurement: MeasurementResult?,
    isLoading: Boolean,
    onMeasure: () -> Unit,
    onFoul: () -> Unit,
    onPass: () -> Unit,
    screenWidth: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Current measurement display
            Text(
                text = "Distance",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF666666)
            )
            
            Text(
                text = measurement?.let {
                    if (it.isValid && it.distance != null) {
                        String.format("%.2f m", it.distance)
                    } else if (!it.isValid) {
                        "X"
                    } else {
                        "P"
                    }
                } ?: "--",
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = measurement?.let {
                    when {
                        !it.isValid -> Color(0xFFF44336) // Red for foul
                        it.distance == null -> Color(0xFFFF9800) // Orange for pass
                        else -> Color(0xFF1976D2) // Blue for valid measurement
                    }
                } ?: Color(0xFF999999),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            // Action buttons - Measure, Foul, Pass
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        android.util.Log.d("MeasurementUI", "MEASURE BUTTON CLICKED!")
                        onMeasure()
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isLoading) "Measuring..." else "MEASURE",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Button(
                    onClick = {
                        android.util.Log.d("MeasurementUI", "X BUTTON CLICKED!")
                        onFoul()
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "X",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Button(
                    onClick = {
                        android.util.Log.d("MeasurementUI", "P BUTTON CLICKED!")
                        onPass()
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "P",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            
            // Next athlete functionality is now handled by the bottom navigation Next button
        }
    }
}

@Composable
fun AthleteHistoryCard(
    competitionAthlete: CompetitionAthlete?,
    currentRound: Int,
    onEditMeasurement: (round: Int, measurement: Int) -> Unit,
    onRoundSelected: (round: Int) -> Unit = {},
    modifier: Modifier = Modifier,
    twoColumnLayout: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (competitionAthlete != null) {
                if (twoColumnLayout) {
                    // Two-column layout for landscape
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left column: rounds 1-3
                        Column(modifier = Modifier.weight(1f)) {
                            (1..3).forEach { round ->
                                RoundRow(
                                    round = round,
                                    competitionAthlete = competitionAthlete,
                                    currentRound = currentRound,
                                    onRoundSelected = onRoundSelected,
                                    onEditMeasurement = onEditMeasurement,
                                    showDivider = round < 3
                                )
                            }
                        }

                        // Right column: rounds 4-6
                        Column(modifier = Modifier.weight(1f)) {
                            (4..6).forEach { round ->
                                RoundRow(
                                    round = round,
                                    competitionAthlete = competitionAthlete,
                                    currentRound = currentRound,
                                    onRoundSelected = onRoundSelected,
                                    onEditMeasurement = onEditMeasurement,
                                    showDivider = round < 6
                                )
                            }
                        }
                    }
                } else {
                    // Single column layout for portrait
                    (1..6).forEach { round ->
                        RoundRow(
                            round = round,
                            competitionAthlete = competitionAthlete,
                            currentRound = currentRound,
                            onRoundSelected = onRoundSelected,
                            onEditMeasurement = onEditMeasurement,
                            showDivider = round < 6
                        )
                    }
                }
            } else {
                Text(
                    text = "No attempts recorded",
                    fontSize = 16.sp,
                    color = Color(0xFF999999),
                    style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                )
            }
        }
    }
}

@Composable
private fun RoundRow(
    round: Int,
    competitionAthlete: CompetitionAthlete,
    currentRound: Int,
    onRoundSelected: (Int) -> Unit,
    onEditMeasurement: (Int, Int) -> Unit,
    showDivider: Boolean
) {
    val roundMeasurements = competitionAthlete.getCurrentRoundMeasurements(round)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable {
                android.util.Log.d("AthleteHistory", "Round $round selected")
                onRoundSelected(round)
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Round label
        Text(
            text = "R$round:",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (round == currentRound) Color(0xFF1976D2) else Color(0xFF666666),
            modifier = Modifier.width(50.dp)
        )

        // Attempts for this round
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (roundMeasurements.isEmpty()) {
                Text(
                    text = if (round <= currentRound) "—" else "•",
                    fontSize = 24.sp,
                    color = Color(0xFF999999)
                )
            } else {
                roundMeasurements.forEachIndexed { index, measurement ->
                    Card(
                        modifier = Modifier.clickable {
                            if (round <= currentRound) {
                                onEditMeasurement(round, index + 1)
                            }
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                !measurement.isValid -> Color(0xFFF44336) // Red for foul
                                measurement.distance == null -> Color(0xFFFF9800) // Orange for pass
                                else -> Color(0xFF4CAF50) // Green for valid
                            }
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = measurement.getDisplayMark(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDivider) {
        Divider(
            color = Color(0xFFE0E0E0),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

/**
 * Inter-round notification showing current standings and round progression
 */
@Composable
fun InterRoundNotification(
    currentRound: Int,
    nextRound: Int,
    athleteStandings: List<Pair<CompetitionAthlete, Int>>, // athlete to ranking
    competitionSettings: CompetitionSettings,
    onContinueToNextRound: () -> Unit,
    onCutoffSelected: (Int) -> Unit = {}, // For round 3 cutoff selection
    onReorderingSelected: (Boolean) -> Unit = {}, // For reordering selection
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedReordering by remember { mutableStateOf(competitionSettings.reorderAfterRound3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = when (currentRound) {
                    3 -> "Round 3 Complete - Finals Setup"
                    4 -> "Round 4 Complete - Reorder for Round 5?"
                    5 -> "Round 5 Complete - Reorder for Round 6?"
                    else -> "Round $currentRound Complete"
                },
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
        },
        text = {
            Column {
                Text(
                    text = "Current Standings:",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // Show all athletes for round 3, limited for other rounds
                val displayAthletes = if (currentRound == 3) athleteStandings else athleteStandings.take(8)
                LazyColumn(
                    modifier = Modifier.heightIn(max = if (currentRound == 3) 400.dp else 300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(displayAthletes) { (athlete, ranking) ->
                        StandingRow(
                            athlete = athlete,
                            ranking = ranking,
                            isAdvancing = if (currentRound == 3) {
                                if (competitionSettings.allowAllAthletes) true else ranking <= competitionSettings.athleteCutoff
                            } else {
                                ranking <= 8 // Default for other rounds
                            }
                        )
                    }
                }
                
                // Round-specific options
                when (currentRound) {
                    3 -> {
                        // Round 3: Cutoff selection AND reordering option
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Select number of athletes advancing to finals:",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Cutoff options: 3, 4, 5, 6, 8, 9, 10, 12, ALL
                        val cutoffOptions = listOf(3, 4, 5, 6, 8, 9, 10, 12) + listOf(-1) // -1 represents ALL
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            items(cutoffOptions) { cutoff ->
                                FilterChip(
                                    onClick = { onCutoffSelected(cutoff) },
                                    label = {
                                        Text(
                                            text = if (cutoff == -1) "ALL" else cutoff.toString(),
                                            fontSize = 14.sp
                                        )
                                    },
                                    selected = competitionSettings.athleteCutoff == cutoff,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF1976D2),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                        
                        // Reordering option for Round 3
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Reorder athletes for finals? (WA/UKA Rules)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilterChip(
                                onClick = { 
                                    selectedReordering = true
                                    onReorderingSelected(true) 
                                },
                                label = { Text("Yes - Reorder", fontSize = 14.sp) },
                                selected = selectedReordering,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF4CAF50),
                                    selectedLabelColor = Color.White
                                )
                            )
                            
                            FilterChip(
                                onClick = { 
                                    selectedReordering = false
                                    onReorderingSelected(false) 
                                },
                                label = { Text("No - Keep Order", fontSize = 14.sp) },
                                selected = !selectedReordering,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF666666),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                        
                        if (selectedReordering) {
                            Text(
                                text = "Athletes will be reordered by performance (best thrower last)",
                                fontSize = 12.sp,
                                color = Color(0xFF666666),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    
                    4, 5 -> {
                        // Rounds 4 & 5: Reordering option only
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Reorder athletes for Round $nextRound? (WA/UKA Rules)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Text(
                            text = "Best performing athletes throw last for psychological advantage",
                            fontSize = 14.sp,
                            color = Color(0xFF666666),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilterChip(
                                onClick = { 
                                    selectedReordering = true
                                    onReorderingSelected(true) 
                                },
                                label = { Text("Yes - Reorder", fontSize = 14.sp) },
                                selected = selectedReordering,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF4CAF50),
                                    selectedLabelColor = Color.White
                                )
                            )
                            
                            FilterChip(
                                onClick = { 
                                    selectedReordering = false
                                    onReorderingSelected(false) 
                                },
                                label = { Text("No - Keep Order", fontSize = 14.sp) },
                                selected = !selectedReordering,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF666666),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
                
                if (nextRound <= 6) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when (currentRound) {
                            3 -> "Ready to start Round $nextRound with selected settings?"
                            4, 5 -> "Ready to start Round $nextRound with selected order?"
                            else -> "Ready to start Round $nextRound?"
                        },
                        fontSize = 16.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onContinueToNextRound,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text(
                    text = if (nextRound <= 6) "Start Round $nextRound" else "End Competition",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(
                    text = "Review",
                    fontSize = 16.sp,
                    color = Color(0xFF1976D2)
                )
            }
        }
    )
}

@Composable
fun StandingRow(
    athlete: CompetitionAthlete,
    ranking: Int,
    isAdvancing: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isAdvancing) Color(0xFFE8F5E8) else Color(0xFFFFF3E0),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Position and athlete info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Ranking
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        ranking <= 3 -> Color(0xFFFFD700) // Gold for top 3
                        isAdvancing -> Color(0xFF4CAF50) // Green for advancing
                        else -> Color(0xFFE0E0E0) // Grey for non-advancing
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "$ranking",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (ranking <= 3) Color.Black else Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            
            // Athlete info
            Column {
                Text(
                    text = "${athlete.bib} - ${athlete.name}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                Text(
                    text = athlete.club,
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
            }
        }
        
        // Best mark
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = athlete.getBestMark()?.let { String.format("%.2fm", it) } ?: "--",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (athlete.getBestMark() != null) Color(0xFF4CAF50) else Color(0xFF999999)
            )
            Text(
                text = if (isAdvancing) "Advancing" else "Eliminated",
                fontSize = 12.sp,
                color = if (isAdvancing) Color(0xFF4CAF50) else Color(0xFFF44336),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Individual Athlete Heatmap Screen
 * Shows enhanced heatmap visualization for a single athlete
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndividualAthleteHeatmapScreen(
    athleteBib: String,
    selectedEvent: PolyFieldApiClient.Event,
    athleteManager: AthleteManagerViewModel,
    calibration: CalibrationState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val athleteState by athleteManager.athleteState.collectAsState()
    val athlete = athleteState.athletes.find { it.bib == athleteBib }
    val serverAthlete = selectedEvent.athletes?.find { it.bib == athleteBib }

    // Filter controls state
    var selectedRounds by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6)) }
    var showValidOnly by remember { mutableStateOf(false) }
    var showFoulsOnly by remember { mutableStateOf(false) }

    // Filter throw data
    val filteredThrows = remember(athlete, selectedRounds, showValidOnly, showFoulsOnly) {
        athlete?.heatmapData?.filter { throwCoord ->
            val roundMatch = throwCoord.round in selectedRounds
            val validityMatch = when {
                showValidOnly -> throwCoord.isValid
                showFoulsOnly -> !throwCoord.isValid
                else -> true
            }
            roundMatch && validityMatch
        } ?: emptyList()
    }

    // Handle system back button
    androidx.activity.compose.BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Heatmap: ${serverAthlete?.name ?: athleteBib}")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1976D2),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Statistics card
            if (athlete != null) {
                HeatmapStatisticsCard(
                    throwData = filteredThrows,
                    athlete = athlete,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Filter controls
            HeatmapFilterControls(
                selectedRounds = selectedRounds,
                onRoundsChanged = { selectedRounds = it },
                showValidOnly = showValidOnly,
                onShowValidOnlyChanged = { showValidOnly = it; if (it) showFoulsOnly = false },
                showFoulsOnly = showFoulsOnly,
                onShowFoulsOnlyChanged = { showFoulsOnly = it; if (it) showValidOnly = false },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Heatmap visualization
            if (filteredThrows.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No throws match the current filters",
                        fontSize = 16.sp,
                        color = Color(0xFF666666)
                    )
                }
            } else {
                EnhancedHeatmapVisualization(
                    throwData = filteredThrows,
                    calibration = calibration,
                    bestThrow = athlete?.getBestMark(),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Overall Competition Heatmap Screen
 * Shows combined heatmap for all athletes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverallCompetitionHeatmapScreen(
    selectedEvent: PolyFieldApiClient.Event,
    athleteManager: AthleteManagerViewModel,
    calibration: CalibrationState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val athleteState by athleteManager.athleteState.collectAsState()

    // Filter controls state
    var selectedRounds by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6)) }
    var showValidOnly by remember { mutableStateOf(false) }
    var showFoulsOnly by remember { mutableStateOf(false) }

    // Combine all throw data
    val allThrows = remember(athleteState.athletes, selectedRounds, showValidOnly, showFoulsOnly) {
        athleteState.athletes.flatMap { athlete ->
            athlete.heatmapData.filter { throwCoord ->
                val roundMatch = throwCoord.round in selectedRounds
                val validityMatch = when {
                    showValidOnly -> throwCoord.isValid
                    showFoulsOnly -> !throwCoord.isValid
                    else -> true
                }
                roundMatch && validityMatch
            }
        }
    }

    // Handle system back button
    androidx.activity.compose.BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Overall Competition Heatmap")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFF9800),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Overall statistics card
            OverallStatisticsCard(
                throwData = allThrows,
                totalAthletes = athleteState.athletes.count { it.heatmapData.isNotEmpty() },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Filter controls
            HeatmapFilterControls(
                selectedRounds = selectedRounds,
                onRoundsChanged = { selectedRounds = it },
                showValidOnly = showValidOnly,
                onShowValidOnlyChanged = { showValidOnly = it; if (it) showFoulsOnly = false },
                showFoulsOnly = showFoulsOnly,
                onShowFoulsOnlyChanged = { showFoulsOnly = it; if (it) showValidOnly = false },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Heatmap visualization
            if (allThrows.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No throws match the current filters",
                        fontSize = 16.sp,
                        color = Color(0xFF666666)
                    )
                }
            } else {
                EnhancedHeatmapVisualization(
                    throwData = allThrows,
                    calibration = calibration,
                    bestThrow = allThrows.filter { it.isValid }.maxByOrNull { it.distance }?.distance,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Heatmap filter controls component
 * Note: Fouls/Passes don't have coordinates, so only valid throws appear on heatmap
 */
@Composable
fun HeatmapFilterControls(
    selectedRounds: Set<Int>,
    onRoundsChanged: (Set<Int>) -> Unit,
    showValidOnly: Boolean,
    onShowValidOnlyChanged: (Boolean) -> Unit,
    showFoulsOnly: Boolean,
    onShowFoulsOnlyChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Filters",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Round selection
            Text(
                text = "Rounds",
                fontSize = 12.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..6).forEach { round ->
                    FilterChip(
                        selected = round in selectedRounds,
                        onClick = {
                            onRoundsChanged(
                                if (round in selectedRounds) selectedRounds - round
                                else selectedRounds + round
                            )
                        },
                        label = { Text("R$round", fontSize = 12.sp) }
                    )
                }
            }

            // Note: Removed "Fouls Only" filter since fouls/passes don't have coordinates to plot
        }
    }
}

/**
 * Statistics card for individual athlete
 */
@Composable
fun HeatmapStatisticsCard(
    throwData: List<ThrowCoordinate>,
    athlete: CompetitionAthlete,
    modifier: Modifier = Modifier
) {
    val validThrows = throwData.filter { it.isValid }
    val bestThrow = validThrows.maxByOrNull { it.distance }
    val avgDistance = if (validThrows.isNotEmpty()) validThrows.map { it.distance }.average() else 0.0

    // Calculate 2D spatial consistency (landing area clustering)
    val consistency = if (validThrows.size > 1) {
        // Calculate centroid (average x, y position)
        val centroidX = validThrows.map { it.x }.average()
        val centroidY = validThrows.map { it.y }.average()

        // Calculate average 2D displacement from centroid
        val avgDisplacement = validThrows.map { throwCoord ->
            val dx = throwCoord.x - centroidX
            val dy = throwCoord.y - centroidY
            kotlin.math.sqrt(dx * dx + dy * dy)
        }.average()

        // Convert to consistency score (1m² target = 1m radius)
        // 0m displacement = 100%, 1m = 50%, 2m+ = 0%
        val consistencyScore = 100.0 * kotlin.math.exp(-avgDisplacement / 1.0)
        consistencyScore.coerceIn(0.0, 100.0)
    } else 100.0

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Statistics",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HeatmapStatItem("Total", "${throwData.size}")
                HeatmapStatItem("Valid", "${validThrows.size}")
                HeatmapStatItem("Fouls", "${throwData.size - validThrows.size}")
                HeatmapStatItem("Best", if (bestThrow != null) String.format("%.2fm", bestThrow.distance) else "--")
                HeatmapStatItem("Avg", if (validThrows.isNotEmpty()) String.format("%.2fm", avgDistance) else "--")
                HeatmapStatItem("Consistency", String.format("%.0f%%", consistency))
            }
        }
    }
}

/**
 * Statistics card for overall competition
 */
@Composable
fun OverallStatisticsCard(
    throwData: List<ThrowCoordinate>,
    totalAthletes: Int,
    modifier: Modifier = Modifier
) {
    val validThrows = throwData.filter { it.isValid }
    val bestThrow = validThrows.maxByOrNull { it.distance }
    val avgDistance = if (validThrows.isNotEmpty()) validThrows.map { it.distance }.average() else 0.0

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Overall Statistics",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF9800),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HeatmapStatItem("Athletes", "$totalAthletes")
                HeatmapStatItem("Total Throws", "${throwData.size}")
                HeatmapStatItem("Valid", "${validThrows.size}")
                HeatmapStatItem("Fouls", "${throwData.size - validThrows.size}")
                HeatmapStatItem("Best", if (bestThrow != null) String.format("%.2fm", bestThrow.distance) else "--")
                HeatmapStatItem("Avg", if (validThrows.isNotEmpty()) String.format("%.2fm", avgDistance) else "--")
            }
        }
    }
}

/**
 * Small statistic item component for heatmap stats
 */
@Composable
fun HeatmapStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFF666666)
        )
    }
}

/**
 * Enhanced heatmap visualization with distance circles and labels
 */
@Composable
fun EnhancedHeatmapVisualization(
    throwData: List<ThrowCoordinate>,
    calibration: CalibrationState,
    bestThrow: Double?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val canvasRadius = minOf(size.width, size.height) / 2 * 0.85f

        // Calculate scale
        val maxDistance = throwData.maxByOrNull { it.distance }?.distance ?: 100.0
        val distanceScale = canvasRadius / maxDistance.toFloat()

        // Draw distance circles every 10m
        val maxCircle = (maxDistance / 10).toInt() * 10 + 10
        for (dist in 10..maxCircle step 10) {
            val radius = dist * distanceScale
            if (radius < canvasRadius) {
                drawCircle(
                    color = Color(0xFFE0E0E0),
                    radius = radius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        // Draw throwing circle
        val circleRadius = when (calibration.circleType) {
            "SHOT", "HAMMER" -> 1.0675f * distanceScale // 2.135m diameter
            "DISCUS" -> 1.25f * distanceScale // 2.5m diameter
            "JAVELIN_ARC" -> 4.0f * distanceScale // 8m arc
            else -> 1.0675f * distanceScale
        }

        drawCircle(
            color = Color.Black,
            radius = circleRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw sector lines for throws circles (not javelin)
        if (calibration.circleType != "JAVELIN_ARC" && calibration.sectorLineCoordinates != null) {
            // Use actual measured right-hand sector line from calibration
            val sectorAngle = 34.92 // UKA-compliant sector angle in degrees

            // Get measured right sector line coordinates
            val rightSectorX = calibration.sectorLineCoordinates.first
            val rightSectorY = calibration.sectorLineCoordinates.second

            // Calculate angle of right sector line from center
            val rightAngle = kotlin.math.atan2(rightSectorY, rightSectorX)

            // Calculate left sector line angle (34.92° counterclockwise from right)
            val leftAngle = rightAngle + Math.toRadians(sectorAngle)

            // Draw right sector line (measured)
            val rightX = centerX + (canvasRadius * kotlin.math.cos(rightAngle)).toFloat()
            val rightY = centerY - (canvasRadius * kotlin.math.sin(rightAngle)).toFloat() // Flip Y for screen coords

            drawLine(
                color = Color(0xFFFF6B6B), // Red for measured right sector line
                start = Offset(centerX, centerY),
                end = Offset(rightX, rightY),
                strokeWidth = 2.dp.toPx()
            )

            // Draw left sector line (calculated)
            val leftX = centerX + (canvasRadius * kotlin.math.cos(leftAngle)).toFloat()
            val leftY = centerY - (canvasRadius * kotlin.math.sin(leftAngle)).toFloat() // Flip Y for screen coords

            drawLine(
                color = Color.Black, // Black for calculated left sector line
                start = Offset(centerX, centerY),
                end = Offset(leftX, leftY),
                strokeWidth = 2.dp.toPx()
            )
        }

        // Draw throw points
        throwData.forEach { throwCoord ->
            val x = centerX + (throwCoord.x * distanceScale).toFloat()
            val y = centerY - (throwCoord.y * distanceScale).toFloat()

            val color = when {
                !throwCoord.isValid -> Color.Red // Fouls in red
                bestThrow != null && kotlin.math.abs(throwCoord.distance - bestThrow) < 0.01 -> Color(0xFFFFD700) // Best throw in gold
                else -> when (throwCoord.round) {
                    1 -> Color.Blue
                    2 -> Color.Green
                    3 -> Color(0xFFFF6B6B)
                    4 -> Color.Magenta
                    5 -> Color.Cyan
                    6 -> Color(0xFFFFD700)
                    else -> Color.Gray
                }
            }

            // Draw best throw with larger circle and border
            if (bestThrow != null && kotlin.math.abs(throwCoord.distance - bestThrow) < 0.01) {
                drawCircle(
                    color = Color.Black,
                    radius = 12.dp.toPx(),
                    center = Offset(x, y),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            drawCircle(
                color = color,
                radius = if (throwCoord.isValid) 8.dp.toPx() else 6.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}
