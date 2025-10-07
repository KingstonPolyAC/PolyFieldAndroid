package com.polyfieldandroid

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.*

/**
 * Clean EDM Interface
 * Single communication conduit to EDM devices with standardized response format
 * Provides 4 core measurement functions: setCentre, measure, verifyEdge, sectorCheck
 */
class EDMInterface(private val context: Context) {
    
    companion object {
        private const val TAG = "EDMInterface"
        
        // Circle specifications (UKA Official)
        const val RADIUS_SHOT = 1.0675      // meters
        const val RADIUS_DISCUS = 1.250     // meters  
        const val RADIUS_HAMMER = 1.0675    // meters
        const val RADIUS_JAVELIN_ARC = 8.000 // meters
        
        // Tolerance specifications (World Athletics/UKA)
        const val TOLERANCE_THROWS_MM = 5.0   // Standard throws circles
        const val TOLERANCE_JAVELIN_MM = 10.0 // Javelin arc
    }
    
    // Communication layer - using existing modules
    private val edmModule = EDMModule(context)
    
    // Current calibration state
    private var centreCoordinates: Pair<Double, Double>? = null // EDM position relative to circle center (0,0)
    private var currentCircleType: String? = null
    private var currentCircleRadius: Double? = null
    
    /**
     * Data class for standardized EDM reading format
     * This is the single format used throughout the system
     */
    data class EDMReading(
        val slopeDistanceM: Double,      // Slope distance in meters
        val verticalAngleDeg: Double,    // Vertical angle in decimal degrees (from vertical upwards)
        val horizontalAngleDeg: Double,  // Horizontal angle in decimal degrees
        val timestamp: String = java.time.Instant.now().toString()
    )
    
    /**
     * SINGLE EDM COMMUNICATION FUNCTION
     * All EDM device communication goes through this function
     * Returns standardized JSON format for any supported EDM device
     */
    suspend fun getEDMReading(deviceType: String): Result<EDMReading> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Getting EDM reading from device: $deviceType")
            
            // Use existing EDM module to get reading
            val edmResult = edmModule.getReliableEDMReading(deviceType, true) // singleMode = true
            if (!edmResult.success) {
                return@withContext Result.failure(
                    Exception("EDM communication failed: ${edmResult.error}")
                )
            }
            
            // Parse the EDM data 
            val goMobileData = edmResult.goMobileData
            if (goMobileData.isNullOrEmpty()) {
                return@withContext Result.failure(
                    Exception("No EDM measurement data available")
                )
            }
            
            Log.d(TAG, "Raw EDM data: $goMobileData")
            
            // Parse Go Mobile format to get the raw values
            val jsonData = JSONObject(goMobileData)
            val slopeDistanceMm = jsonData.getDouble("slopeDistanceMm")
            val verticalAngleDeg = jsonData.getDouble("vAzDecimal") 
            val horizontalAngleDeg = jsonData.getDouble("harDecimal")
            
            // Convert to standardized format
            val reading = EDMReading(
                slopeDistanceM = slopeDistanceMm / 1000.0, // Convert mm to meters
                verticalAngleDeg = verticalAngleDeg,
                horizontalAngleDeg = horizontalAngleDeg
            )
            
            Log.d(TAG, "Standardized EDM reading: slope=${reading.slopeDistanceM}m, vertical=${reading.verticalAngleDeg}°, horizontal=${reading.horizontalAngleDeg}°")
            
            Result.success(reading)
            
        } catch (e: Exception) {
            Log.e(TAG, "EDM reading failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 1. SET CENTRE
     * Identifies the position of the centre of the circle relative to the EDM
     * Circle center becomes (0,0), EDM position is calculated relative to this
     */
    suspend fun setCentre(deviceType: String, circleType: String): Result<Map<String, Any>> {
        return try {
            Log.d(TAG, "Setting centre for circle type: $circleType")
            
            // Set circle configuration
            currentCircleType = circleType
            currentCircleRadius = getCircleRadius(circleType)
            
            // Get EDM reading
            val readingResult = getEDMReading(deviceType)
            if (readingResult.isFailure) {
                return Result.failure(readingResult.exceptionOrNull()!!)
            }
            
            val reading = readingResult.getOrThrow()
            
            // Calculate EDM position relative to circle center (0,0)
            val coordinates = calculateCoordinatesFromReading(reading)
            centreCoordinates = coordinates
            
            Log.d(TAG, "Centre set: EDM at (${coordinates.first}, ${coordinates.second}) relative to circle center (0,0)")
            
            Result.success(mapOf(
                "success" to true,
                "circleType" to circleType,
                "circleRadius" to currentCircleRadius!!,
                "edmPosition" to mapOf(
                    "x" to coordinates.first,
                    "y" to coordinates.second
                ),
                "message" to "Centre set successfully"
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "Set centre failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 2. MEASURE  
     * Calculates the distance from the centre of the circle to the measured response
     * Subtracts the radius of the selected event circle
     * Returns throw distance beyond circle edge
     */
    suspend fun measure(deviceType: String): Result<Map<String, Any>> {
        return try {
            if (centreCoordinates == null || currentCircleRadius == null) {
                return Result.failure(Exception("Centre must be set before measuring"))
            }
            
            Log.d(TAG, "Measuring throw distance")
            
            // Get EDM reading
            val readingResult = getEDMReading(deviceType)
            if (readingResult.isFailure) {
                return Result.failure(readingResult.exceptionOrNull()!!)
            }
            
            val reading = readingResult.getOrThrow()
            
            // Calculate throw coordinates relative to circle center
            val throwCoords = calculateThrowCoordinates(reading)
            
            // Distance from circle center to throw point
            val distanceFromCenter = sqrt(throwCoords.first.pow(2) + throwCoords.second.pow(2))
            
            // Throw distance = distance from center - circle radius
            val throwDistance = distanceFromCenter - currentCircleRadius!!
            
            Log.d(TAG, "Throw measured: ${String.format("%.2f", throwDistance)}m beyond circle edge")
            
            Result.success(mapOf(
                "success" to true,
                "throwDistance" to throwDistance,
                "distanceFromCenter" to distanceFromCenter,
                "throwCoordinates" to mapOf(
                    "x" to throwCoords.first,
                    "y" to throwCoords.second
                ),
                "circleRadius" to currentCircleRadius!!,
                "measurement" to "${String.format("%.2f", throwDistance)} m"
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "Measure failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 3. VERIFY EDGE
     * Measures distance and compares against known circle radius
     * Checks against circle tolerance for pass/fail (World Athletics/UKA rules)
     */
    suspend fun verifyEdge(deviceType: String): Result<Map<String, Any>> {
        return try {
            if (centreCoordinates == null || currentCircleRadius == null || currentCircleType == null) {
                return Result.failure(Exception("Centre must be set before verifying edge"))
            }
            
            Log.d(TAG, "Verifying edge measurement")
            
            // Get EDM reading
            val readingResult = getEDMReading(deviceType)
            if (readingResult.isFailure) {
                return Result.failure(readingResult.exceptionOrNull()!!)
            }
            
            val reading = readingResult.getOrThrow()
            
            // Calculate edge coordinates relative to circle center
            val edgeCoords = calculateThrowCoordinates(reading)
            
            // Measured radius from center
            val measuredRadius = sqrt(edgeCoords.first.pow(2) + edgeCoords.second.pow(2))
            
            // Difference from target radius
            val differenceMm = (measuredRadius - currentCircleRadius!!) * 1000.0
            
            // Check tolerance
            val toleranceMm = if (currentCircleType == "JAVELIN_ARC") TOLERANCE_JAVELIN_MM else TOLERANCE_THROWS_MM
            val isInTolerance = abs(differenceMm) <= toleranceMm
            
            Log.d(TAG, "Edge verification: measured=${String.format("%.3f", measuredRadius)}m, target=${String.format("%.3f", currentCircleRadius!!)}m, diff=${String.format("%.1f", differenceMm)}mm, tolerance=${toleranceMm}mm, result=${if (isInTolerance) "PASS" else "FAIL"}")
            
            Result.success(mapOf(
                "success" to true,
                "measuredRadius" to measuredRadius,
                "targetRadius" to currentCircleRadius!!,
                "differenceMm" to differenceMm,
                "toleranceMm" to toleranceMm,
                "isInTolerance" to isInTolerance,
                "result" to if (isInTolerance) "PASS" else "FAIL",
                "message" to "Edge verification ${if (isInTolerance) "PASSED" else "FAILED"} - ${String.format("%.1f", abs(differenceMm))}mm ${if (differenceMm > 0) "over" else "under"}"
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "Verify edge failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 4. SECTOR CHECK
     * Measures point for sector line verification and heatmap plotting
     * Used to plot right-hand sector line and verify against known distance
     */
    suspend fun sectorCheck(deviceType: String): Result<Map<String, Any>> {
        return try {
            if (centreCoordinates == null) {
                return Result.failure(Exception("Centre must be set before sector check"))
            }
            
            Log.d(TAG, "Performing sector check")
            
            // Get EDM reading
            val readingResult = getEDMReading(deviceType)
            if (readingResult.isFailure) {
                return Result.failure(readingResult.exceptionOrNull()!!)
            }
            
            val reading = readingResult.getOrThrow()
            
            // Calculate sector point coordinates relative to circle center
            val sectorCoords = calculateThrowCoordinates(reading)

            // Distance from circle center
            val distanceFromCenter = sqrt(sectorCoords.first.pow(2) + sectorCoords.second.pow(2))

            // Distance beyond circle edge (same as throw distance calculation)
            val distanceBeyondEdge = distanceFromCenter - currentCircleRadius!!

            // Calculate angle from positive X-axis for sector line
            val angleFromXAxis = atan2(sectorCoords.second, sectorCoords.first)
            val angleDegrees = Math.toDegrees(angleFromXAxis)

            Log.d(TAG, "Sector point: (${String.format("%.3f", sectorCoords.first)}, ${String.format("%.3f", sectorCoords.second)}), distance from center=${String.format("%.2f", distanceFromCenter)}m, beyond edge=${String.format("%.2f", distanceBeyondEdge)}m, angle=${String.format("%.1f", angleDegrees)}°")

            Result.success(mapOf(
                "success" to true,
                "sectorCoordinates" to mapOf(
                    "x" to sectorCoords.first,
                    "y" to sectorCoords.second
                ),
                "distanceFromCenter" to distanceFromCenter,
                "distanceBeyondEdge" to distanceBeyondEdge,
                "angleFromXAxis" to angleDegrees,
                "measurement" to "${String.format("%.2f", distanceBeyondEdge)} m",
                "message" to "Sector point measured successfully"
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "Sector check failed", e)
            Result.failure(e)
        }
    }
    
    // ========== PRIVATE HELPER FUNCTIONS ==========
    
    /**
     * Get circle radius for circle type
     */
    private fun getCircleRadius(circleType: String): Double {
        return when (circleType.uppercase()) {
            "SHOT" -> RADIUS_SHOT
            "DISCUS" -> RADIUS_DISCUS
            "HAMMER" -> RADIUS_HAMMER
            "JAVELIN_ARC" -> RADIUS_JAVELIN_ARC
            else -> RADIUS_SHOT
        }
    }
    
    /**
     * Calculate coordinates from EDM reading using PDF formula
     * hd = sd * cos(90° - va) where va is measured from vertically upwards
     */
    private fun calculateCoordinatesFromReading(reading: EDMReading): Pair<Double, Double> {
        val sdMeters = reading.slopeDistanceM
        val vazRad = Math.toRadians(reading.verticalAngleDeg)
        val harRad = Math.toRadians(reading.horizontalAngleDeg)
        
        // Apply PDF formula: hd = sd * cos(90° - va)
        val horizontalDistance = sdMeters * cos(Math.toRadians(90.0) - vazRad)
        
        // Calculate coordinates (negative to position EDM relative to circle center)
        val x = -horizontalDistance * cos(harRad)
        val y = -horizontalDistance * sin(harRad)
        
        return Pair(x, y)
    }
    
    /**
     * Calculate throw coordinates relative to circle center (0,0)
     */
    private fun calculateThrowCoordinates(reading: EDMReading): Pair<Double, Double> {
        val sdMeters = reading.slopeDistanceM
        val vazRad = Math.toRadians(reading.verticalAngleDeg)
        val harRad = Math.toRadians(reading.horizontalAngleDeg)
        
        // Apply PDF formula: hd = sd * cos(90° - va)
        val horizontalDistance = sdMeters * cos(Math.toRadians(90.0) - vazRad)
        
        // Calculate throw point relative to EDM
        val throwX = horizontalDistance * cos(harRad)
        val throwY = horizontalDistance * sin(harRad)
        
        // Convert to coordinates relative to circle center (0,0)
        val absoluteX = centreCoordinates!!.first + throwX
        val absoluteY = centreCoordinates!!.second + throwY
        
        return Pair(absoluteX, absoluteY)
    }
}