package com.polyfieldandroid

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.*

/**
 * Native Kotlin EDM Calibration Manager
 * Replaces Go Mobile calibration functions with enhanced decimal precision
 * Manages calibration state and calculations
 */
class EDMCalibrationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "EDMCalibrationManager"
        private const val PREFS_NAME = "edm_calibration_v2"
        private const val KEY_CALIBRATION_DATA = "calibration_data_"
    }
    
    private val calculations = EDMCalculations()
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val calibrationStore = mutableMapOf<String, EDMCalculations.EDMCalibrationData>()
    
    /**
     * Calibration state for UI
     */
    data class CalibrationState(
        val circleType: String,
        val targetRadius: Double,
        val centreSet: Boolean,
        val stationCoordinates: EDMCalculations.EDMPoint?,
        val centreTimestamp: String?,
        val edgeResult: EdgeResult?,
        val selectedHistoricalCalibration: CalibrationRecord? = null
    )
    
    /**
     * Edge verification result for UI
     */
    data class EdgeResult(
        val toleranceCheck: Boolean,
        val averageRadius: Double,
        val deviation: Double
    )
    
    /**
     * Historical calibration record
     */
    data class CalibrationRecord(
        val timestamp: Date,
        val circleType: String,
        val isComplete: Boolean
    ) {
        fun getDisplayName(): String {
            val timeFormat = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
            return "${circleType.replace("_", " ")} - ${timeFormat.format(timestamp)}"
        }
        
    }
    
    /**
     * Set circle type and initialize calibration
     */
    suspend fun setCircleType(deviceType: String, circleType: String): CalibrationState = withContext(Dispatchers.IO) {
        val targetRadius = calculations.getCircleRadius(circleType)
        
        val calibrationData = EDMCalculations.EDMCalibrationData(
            deviceId = deviceType,
            selectedCircleType = circleType,
            targetRadius = targetRadius,
            stationCoordinates = EDMCalculations.EDMPoint(0.0, 0.0),
            isCentreSet = false
        )
        
        calibrationStore[deviceType] = calibrationData
        saveCalibration(deviceType, calibrationData)
        
        return@withContext CalibrationState(
            circleType = circleType,
            targetRadius = targetRadius,
            centreSet = false,
            stationCoordinates = null,
            centreTimestamp = null,
            edgeResult = null
        )
    }
    
    /**
     * Set centre point from EDM reading
     */
    suspend fun setCentre(
        deviceType: String,
        edmReading: String,
        singleMode: Boolean
    ): Result<CalibrationState> = withContext(Dispatchers.IO) {
        try {
            val calibrationData = calibrationStore[deviceType]
                ?: return@withContext Result.failure(Exception("No calibration data found for device type"))
            
            // Parse the EDM reading JSON
            val readingJson = JSONObject(edmReading)
            val reading = EDMCalculations.AveragedEDMReading(
                slopeDistanceMm = readingJson.getDouble("slopeDistanceMm"),
                vazDecimal = readingJson.getDouble("vAzDecimal"),
                harDecimal = readingJson.getDouble("hARDecimal")
            )
            
            // Calculate station coordinates using native Kotlin
            val stationCoordinates = calculations.calculateStationCoordinates(reading)
            
            // Update calibration data
            val updatedCalibration = calibrationData.copy(
                stationCoordinates = stationCoordinates,
                isCentreSet = true,
                timestamp = Date(),
                edgeVerificationResult = null // Reset edge verification
            )
            
            calibrationStore[deviceType] = updatedCalibration
            saveCalibration(deviceType, updatedCalibration)
            
            val state = CalibrationState(
                circleType = updatedCalibration.selectedCircleType,
                targetRadius = updatedCalibration.targetRadius,
                centreSet = true,
                stationCoordinates = stationCoordinates,
                centreTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(updatedCalibration.timestamp),
                edgeResult = null
            )
            
            return@withContext Result.success(state)
            
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Failed to set centre: ${e.message}"))
        }
    }
    
    /**
     * Verify edge measurement
     */
    suspend fun verifyEdge(
        deviceType: String,
        edmReading: String,
        singleMode: Boolean
    ): Result<CalibrationState> = withContext(Dispatchers.IO) {
        try {
            val calibrationData = calibrationStore[deviceType]
                ?: return@withContext Result.failure(Exception("No calibration data found"))
            
            if (!calibrationData.isCentreSet) {
                return@withContext Result.failure(Exception("Centre must be set first"))
            }
            
            // Parse the EDM reading JSON
            val readingJson = JSONObject(edmReading)
            val reading = EDMCalculations.AveragedEDMReading(
                slopeDistanceMm = readingJson.getDouble("slopeDistanceMm"),
                vazDecimal = readingJson.getDouble("vAzDecimal"),
                harDecimal = readingJson.getDouble("hARDecimal")
            )
            
            // Perform edge verification using native Kotlin calculations
            val edgeResult = calculations.verifyEdge(
                reading = reading,
                stationCoordinates = calibrationData.stationCoordinates,
                circleType = calibrationData.selectedCircleType,
                targetRadius = calibrationData.targetRadius
            )
            
            // Update calibration with edge result
            val updatedCalibration = calibrationData.copy(
                edgeVerificationResult = edgeResult
            )
            
            calibrationStore[deviceType] = updatedCalibration
            saveCalibration(deviceType, updatedCalibration)
            
            val state = CalibrationState(
                circleType = updatedCalibration.selectedCircleType,
                targetRadius = updatedCalibration.targetRadius,
                centreSet = true,
                stationCoordinates = updatedCalibration.stationCoordinates,
                centreTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(updatedCalibration.timestamp),
                edgeResult = EdgeResult(
                    toleranceCheck = edgeResult.isInTolerance,
                    averageRadius = edgeResult.measuredRadius,
                    deviation = edgeResult.differenceMm / 1000.0 // Convert to meters
                )
            )
            
            return@withContext Result.success(state)
            
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Failed to verify edge: ${e.message}"))
        }
    }
    
    /**
     * Measure throw distance
     */
    suspend fun measureThrow(
        deviceType: String,
        edmReading: String,
        singleMode: Boolean
    ): Result<Double> = withContext(Dispatchers.IO) {
        try {
            val calibrationData = calibrationStore[deviceType]
                ?: return@withContext Result.failure(Exception("No calibration data found"))
            
            if (!calibrationData.isCentreSet) {
                return@withContext Result.failure(Exception("EDM is not calibrated - centre not set"))
            }
            
            val edgeResult = calibrationData.edgeVerificationResult
            if (edgeResult == null || !edgeResult.isInTolerance) {
                return@withContext Result.failure(Exception("EDM must be calibrated with valid edge verification"))
            }
            
            // Parse the EDM reading JSON
            val readingJson = JSONObject(edmReading)
            val reading = EDMCalculations.AveragedEDMReading(
                slopeDistanceMm = readingJson.getDouble("slopeDistanceMm"),
                vazDecimal = readingJson.getDouble("vAzDecimal"),
                harDecimal = readingJson.getDouble("hARDecimal")
            )
            
            // Calculate throw distance using native Kotlin
            val throwDistance = calculations.calculateThrowDistance(
                reading = reading,
                stationCoordinates = calibrationData.stationCoordinates,
                circleRadius = calibrationData.targetRadius
            )
            
            return@withContext Result.success(throwDistance)
            
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Failed to measure throw: ${e.message}"))
        }
    }
    
    /**
     * Get current calibration state
     */
    suspend fun getCalibrationState(deviceType: String): CalibrationState = withContext(Dispatchers.IO) {
        val calibrationData = calibrationStore[deviceType] ?: loadCalibration(deviceType)
        
        val edgeResult = calibrationData?.edgeVerificationResult?.let { edge ->
            EdgeResult(
                toleranceCheck = edge.isInTolerance,
                averageRadius = edge.measuredRadius,
                deviation = edge.differenceMm / 1000.0
            )
        }
        
        return@withContext CalibrationState(
            circleType = calibrationData?.selectedCircleType ?: EDMCalculations.CIRCLE_SHOT,
            targetRadius = calibrationData?.targetRadius ?: EDMCalculations.UKA_RADIUS_SHOT,
            centreSet = calibrationData?.isCentreSet ?: false,
            stationCoordinates = calibrationData?.stationCoordinates,
            centreTimestamp = calibrationData?.timestamp?.let { timestamp ->
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(timestamp)
            },
            edgeResult = edgeResult
        )
    }
    
    /**
     * Reset calibration
     */
    suspend fun resetCalibration(deviceType: String): CalibrationState = withContext(Dispatchers.IO) {
        calibrationStore.remove(deviceType)
        prefs.edit { remove("$KEY_CALIBRATION_DATA$deviceType") }
        
        return@withContext CalibrationState(
            circleType = EDMCalculations.CIRCLE_SHOT,
            targetRadius = EDMCalculations.UKA_RADIUS_SHOT,
            centreSet = false,
            stationCoordinates = null,
            centreTimestamp = null,
            edgeResult = null
        )
    }
    
    /**
     * Save calibration to persistent storage
     */
    private fun saveCalibration(deviceType: String, calibration: EDMCalculations.EDMCalibrationData) {
        try {
            val json = JSONObject().apply {
                put("deviceId", calibration.deviceId)
                put("timestamp", calibration.timestamp.time)
                put("selectedCircleType", calibration.selectedCircleType)
                put("targetRadius", calibration.targetRadius)
                put("stationX", calibration.stationCoordinates.x)
                put("stationY", calibration.stationCoordinates.y)
                put("isCentreSet", calibration.isCentreSet)
                calibration.edgeVerificationResult?.let { edge ->
                    put("edgeResult", JSONObject().apply {
                        put("measuredRadius", edge.measuredRadius)
                        put("differenceMm", edge.differenceMm)
                        put("isInTolerance", edge.isInTolerance)
                        put("toleranceAppliedMm", edge.toleranceAppliedMm)
                    })
                }
            }
            prefs.edit { putString("$KEY_CALIBRATION_DATA$deviceType", json.toString()) }
        } catch (e: Exception) {
            // Log error but don't crash
        }
    }
    
    /**
     * Load calibration from persistent storage
     */
    private fun loadCalibration(deviceType: String): EDMCalculations.EDMCalibrationData? {
        return try {
            val jsonStr = prefs.getString("$KEY_CALIBRATION_DATA$deviceType", null) ?: return null
            val json = JSONObject(jsonStr)
            
            val edgeResult = if (json.has("edgeResult")) {
                val edgeJson = json.getJSONObject("edgeResult")
                EDMCalculations.EdgeVerificationResult(
                    measuredRadius = edgeJson.getDouble("measuredRadius"),
                    differenceMm = edgeJson.getDouble("differenceMm"),
                    isInTolerance = edgeJson.getBoolean("isInTolerance"),
                    toleranceAppliedMm = edgeJson.getDouble("toleranceAppliedMm")
                )
            } else null
            
            val calibration = EDMCalculations.EDMCalibrationData(
                deviceId = json.getString("deviceId"),
                timestamp = Date(json.getLong("timestamp")),
                selectedCircleType = json.getString("selectedCircleType"),
                targetRadius = json.getDouble("targetRadius"),
                stationCoordinates = EDMCalculations.EDMPoint(
                    x = json.getDouble("stationX"),
                    y = json.getDouble("stationY")
                ),
                isCentreSet = json.getBoolean("isCentreSet"),
                edgeVerificationResult = edgeResult
            )
            
            calibrationStore[deviceType] = calibration
            calibration
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get available historical calibrations (placeholder for future implementation)
     */
    suspend fun getAvailableCalibrations(): List<CalibrationRecord> = withContext(Dispatchers.IO) {
        // For now return empty list - can be extended later
        return@withContext emptyList()
    }
}