package com.polyfieldandroid

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Data class for ranking display
 */
data class AthleteRanking(
    val position: Int,
    val athlete: CompetitionAthlete,
    val bestMark: Double?,
    val isAdvancing: Boolean = false,
    val recentChange: Int = 0 // Position change since last update
) {
    fun getDisplayMark(): String = bestMark?.let { "%.2f m" .format(it) } ?: "—"
    fun getPositionChange(): String = when {
        recentChange > 0 -> "↑$recentChange"
        recentChange < 0 -> "↓${abs(recentChange)}"
        else -> ""
    }
}

/**
 * Visualization state management
 */
data class VisualizationState(
    val currentRankings: List<AthleteRanking> = emptyList(),
    val selectedAthleteForHeatmap: String? = null,
    val showHeatmap: Boolean = true,
    val showRankings: Boolean = true,
    val heatmapScale: Float = 1.0f,
    val updateCount: Int = 0 // Track updates for animations
)

/**
 * Visualization and Results Manager
 * Handles live rankings, individual heatmaps, and results display
 */
class VisualizationManager(
    private val context: Context,
    private val athleteManager: AthleteManagerViewModel,
    private val competitionManager: CompetitionManagerViewModel
) : ViewModel() {
    
    companion object {
        private const val TAG = "VisualizationManager"
    }
    
    // State management
    private val _visualizationState = MutableStateFlow(VisualizationState())
    val visualizationState: StateFlow<VisualizationState> = _visualizationState.asStateFlow()
    
    // Track previous rankings for change detection
    private var previousRankings: List<AthleteRanking> = emptyList()
    
    init {
        // Start observing athlete and competition changes
        observeDataChanges()
    }
    
    /**
     * Observe changes in athlete and competition data
     */
    private fun observeDataChanges() {
        viewModelScope.launch {
            athleteManager.athleteState.collect { athleteState ->
                updateRankings()
            }
        }
        
        viewModelScope.launch {
            competitionManager.competitionState.collect { competitionState ->
                updateRankings()
            }
        }
    }
    
    /**
     * Update live rankings
     */
    private fun updateRankings() {
        viewModelScope.launch {
            try {
                val competitionState = competitionManager.competitionState.value
                val selectedAthletes = athleteManager.getSelectedAthletes()
                
                if (!competitionState.isActive || selectedAthletes.isEmpty()) {
                    _visualizationState.value = _visualizationState.value.copy(
                        currentRankings = emptyList()
                    )
                    return@launch
                }
                
                // Create new rankings
                val newRankings = selectedAthletes
                    .sortedByDescending { it.getBestMark() ?: 0.0 }
                    .mapIndexed { index, athlete ->
                        val position = index + 1
                        val previousPosition = previousRankings.find { it.athlete.bib == athlete.bib }?.position ?: position
                        val positionChange = previousPosition - position
                        
                        AthleteRanking(
                            position = position,
                            athlete = athlete,
                            bestMark = athlete.getBestMark(),
                            isAdvancing = competitionState.settings.allowAllAthletes || position <= competitionState.settings.athleteCutoff,
                            recentChange = positionChange
                        )
                    }
                
                previousRankings = newRankings
                
                _visualizationState.value = _visualizationState.value.copy(
                    currentRankings = newRankings,
                    updateCount = _visualizationState.value.updateCount + 1
                )
                
                Log.d(TAG, "Updated rankings: ${newRankings.size} athletes")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error updating rankings: ${e.message}")
            }
        }
    }
    
    /**
     * Select athlete for heatmap display
     */
    fun selectAthleteForHeatmap(athleteBib: String) {
        _visualizationState.value = _visualizationState.value.copy(
            selectedAthleteForHeatmap = athleteBib
        )
    }
    
    /**
     * Toggle heatmap visibility
     */
    fun toggleHeatmap() {
        _visualizationState.value = _visualizationState.value.copy(
            showHeatmap = !_visualizationState.value.showHeatmap
        )
    }
    
    /**
     * Toggle rankings visibility
     */
    fun toggleRankings() {
        _visualizationState.value = _visualizationState.value.copy(
            showRankings = !_visualizationState.value.showRankings
        )
    }
    
    /**
     * Set heatmap scale
     */
    fun setHeatmapScale(scale: Float) {
        _visualizationState.value = _visualizationState.value.copy(
            heatmapScale = scale.coerceIn(0.5f, 2.0f)
        )
    }
    
    /**
     * Get athlete heatmap data
     */
    fun getAthleteHeatmapData(athleteBib: String): List<ThrowCoordinate> {
        return athleteManager.getAthleteByBib(athleteBib)?.heatmapData ?: emptyList()
    }
    
    /**
     * Get current rankings
     */
    fun getCurrentRankings(): List<AthleteRanking> {
        return _visualizationState.value.currentRankings
    }
    
    /**
     * Get advancing athletes count
     */
    fun getAdvancingAthletesCount(): Int {
        return _visualizationState.value.currentRankings.count { it.isAdvancing }
    }
    
    // Convenience getters
    fun getSelectedAthleteForHeatmap(): String? = _visualizationState.value.selectedAthleteForHeatmap
    fun isHeatmapVisible(): Boolean = _visualizationState.value.showHeatmap
    fun isRankingsVisible(): Boolean = _visualizationState.value.showRankings
    fun getHeatmapScale(): Float = _visualizationState.value.heatmapScale
}

/**
 * Composable for displaying live rankings
 */
@Composable
fun LiveRankingsDisplay(
    rankings: List<AthleteRanking>,
    onAthleteSelect: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Live Rankings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (rankings.isEmpty()) {
                Text(
                    text = "No results yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    state = rememberLazyListState(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rankings) { ranking ->
                        RankingItem(
                            ranking = ranking,
                            onClick = { onAthleteSelect(ranking.athlete.bib) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual ranking item
 */
@Composable
private fun RankingItem(
    ranking: AthleteRanking,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ranking.isAdvancing) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position and change indicator
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${ranking.position}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.Center
                )
                
                if (ranking.recentChange != 0) {
                    Text(
                        text = ranking.getPositionChange(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ranking.recentChange > 0) Color.Green else Color.Red,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            
            // Athlete info
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            ) {
                Text(
                    text = ranking.athlete.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${ranking.athlete.bib} - ${ranking.athlete.club}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Best mark
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = ranking.getDisplayMark(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                
                if (ranking.isAdvancing) {
                    Text(
                        text = "Q",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Composable for displaying athlete heatmap
 */
@Composable
fun AthleteHeatmapDisplay(
    athleteBib: String,
    throwData: List<ThrowCoordinate>,
    circleType: String,
    scale: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Throw Heatmap - Athlete $athleteBib",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (throwData.isEmpty()) {
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No throws recorded",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier.size((300 * scale).dp)
                ) {
                    drawHeatmap(throwData, circleType, scale)
                }
            }
            
            // Legend
            if (throwData.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HeatmapLegend(throwData)
            }
        }
    }
}

/**
 * Draw heatmap on canvas
 */
private fun DrawScope.drawHeatmap(
    throwData: List<ThrowCoordinate>,
    circleType: String,
    scale: Float
) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val canvasRadius = minOf(size.width, size.height) / 2 * 0.8f
    
    // Draw throwing circle
    val circleRadius = when (circleType) {
        "SHOT", "HAMMER" -> 30f * scale
        "DISCUS" -> (30f * 1.17f) * scale // Discus circle is larger
        "JAVELIN_ARC" -> (30f * 7.5f) * scale // Javelin arc is much larger
        else -> 30f * scale
    }
    
    drawCircle(
        color = Color.Black,
        radius = circleRadius,
        center = Offset(centerX, centerY),
        style = Stroke(width = 2.dp.toPx())
    )
    
    // Draw sector lines for throws circles (not javelin)
    if (circleType != "JAVELIN_ARC") {
        val sectorAngle = 34.92 // degrees for throws sector
        drawSectorLines(centerX, centerY, canvasRadius, sectorAngle)
    }
    
    // Find max distance for scaling
    val maxDistance = throwData.maxByOrNull { it.distance }?.distance ?: 1.0
    val distanceScale = canvasRadius / maxDistance.toFloat()
    
    // Draw throw points
    throwData.forEachIndexed { index, throwCoord ->
        val x = centerX + (throwCoord.x * distanceScale * scale).toFloat()
        val y = centerY - (throwCoord.y * distanceScale * scale).toFloat() // Flip Y axis
        
        val color = when (throwCoord.round) {
            1 -> Color.Blue
            2 -> Color.Green
            3 -> Color.Red
            4 -> Color.Magenta
            5 -> Color.Cyan
            6 -> Color.Yellow
            else -> Color.Gray
        }
        
        drawCircle(
            color = color,
            radius = if (throwCoord.isValid) 8.dp.toPx() else 6.dp.toPx(),
            center = Offset(x, y)
        )
        
        // Draw attempt number (simplified - no text drawing for now)
        // TODO: Add text drawing for attempt numbers if needed
    }
}

/**
 * Draw sector lines for throws circles
 */
private fun DrawScope.drawSectorLines(
    centerX: Float,
    centerY: Float,
    radius: Float,
    sectorAngle: Double
) {
    val halfAngle = Math.toRadians(sectorAngle / 2)
    
    val leftX = centerX + (radius * cos(PI - halfAngle)).toFloat()
    val leftY = centerY + (radius * sin(PI - halfAngle)).toFloat()
    
    val rightX = centerX + (radius * cos(halfAngle)).toFloat()
    val rightY = centerY + (radius * sin(halfAngle)).toFloat()
    
    // Left sector line
    drawLine(
        color = Color.Black,
        start = Offset(centerX, centerY),
        end = Offset(leftX, leftY),
        strokeWidth = 1.dp.toPx()
    )
    
    // Right sector line
    drawLine(
        color = Color.Black,
        start = Offset(centerX, centerY),
        end = Offset(rightX, rightY),
        strokeWidth = 1.dp.toPx()
    )
}

/**
 * Heatmap legend showing rounds and colors
 */
@Composable
private fun HeatmapLegend(throwData: List<ThrowCoordinate>) {
    val roundsPresent = throwData.map { it.round }.distinct().sorted()
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        roundsPresent.forEach { round ->
            val color = when (round) {
                1 -> Color.Blue
                2 -> Color.Green
                3 -> Color.Red
                4 -> Color.Magenta
                5 -> Color.Cyan
                6 -> Color.Yellow
                else -> Color.Gray
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "R$round",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * Factory for creating VisualizationManager
 */
class VisualizationManagerFactory(
    private val context: Context,
    private val athleteManager: AthleteManagerViewModel,
    private val competitionManager: CompetitionManagerViewModel
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VisualizationManager::class.java)) {
            return VisualizationManager(context, athleteManager, competitionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}