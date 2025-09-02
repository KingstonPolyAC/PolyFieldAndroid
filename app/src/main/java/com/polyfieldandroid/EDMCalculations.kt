package com.polyfieldandroid

import kotlin.math.*
import org.json.JSONObject
import java.util.*

/**
 * Native Kotlin EDM Calculations Module
 * Replaces Go Mobile implementation with enhanced decimal seconds precision
 * Contains only actively used functions - cleaned up for production
 */
class EDMCalculations {
    
    companion object {
        private const val TAG = "EDMCalculations"
        
        // UKA Official Circle Radii (as per methodology guide)
        const val UKA_RADIUS_SHOT = 1.0675       // Shot put circle radius (meters)
        const val UKA_RADIUS_DISCUS = 1.250      // Discus circle radius (meters)
        const val UKA_RADIUS_HAMMER = 1.0675     // Hammer circle radius (meters)
        const val UKA_RADIUS_JAVELIN_ARC = 8.000 // Javelin arc radius (meters)
        
        // Circle types
        const val CIRCLE_SHOT = "SHOT"
        const val CIRCLE_DISCUS = "DISCUS"
        const val CIRCLE_HAMMER = "HAMMER"
        const val CIRCLE_JAVELIN = "JAVELIN_ARC"
        
        // Tolerance constants (millimeters)
        const val TOLERANCE_THROWS_CIRCLE_MM = 5.0  // Standard tolerance for throws circles
        const val TOLERANCE_JAVELIN_MM = 10.0       // Tolerance for javelin arc
        
        // Measurement precision constants
        const val SD_TOLERANCE_MM = 3.0 // Slope distance tolerance for double readings
    }
    
    /**
     * Data class for parsed EDM reading
     */
    data class ParsedEDMReading(
        val slopeDistanceMm: Double,
        val vazDecimal: Double,
        val harDecimal: Double
    )
    
    /**
     * Data class for averaged EDM reading (from multiple measurements)
     */
    data class AveragedEDMReading(
        val slopeDistanceMm: Double,
        val vazDecimal: Double,
        val harDecimal: Double
    )
    
    /**
     * Data class for 2D coordinate point
     */
    data class EDMPoint(
        val x: Double,
        val y: Double
    )
    
    /**
     * Data class for edge verification result
     */
    data class EdgeVerificationResult(
        val measuredRadius: Double,
        val differenceMm: Double,
        val isInTolerance: Boolean,
        val toleranceAppliedMm: Double
    )
    
    /**
     * Data class for calibration data
     */
    data class EDMCalibrationData(
        val deviceId: String,
        val timestamp: Date = Date(),
        val selectedCircleType: String,
        val targetRadius: Double,
        val stationCoordinates: EDMPoint,
        val isCentreSet: Boolean,
        val edgeVerificationResult: EdgeVerificationResult? = null
    )
    
    /**
     * Enhanced angle parsing with decimal seconds precision
     * Supports formats: DDDMMSS, DDMMSS, DDDMMSS.S, DDMMSS.S, etc.
     * 
     * CRITICAL FIX: This addresses the 60mm measurement error (7.94m vs 8.00m)
     * by preserving sub-second precision instead of truncating decimal seconds
     */
    fun parseDDDMMSSAngle(angleStr: String): Double {
        if (angleStr.length < 6) {
            throw IllegalArgumentException("Invalid angle string length: got ${angleStr.length} for '$angleStr'")
        }
        
        // Handle both integer and decimal formats
        val baseStr: String
        var decimalPart = 0.0
        
        // Check for decimal point in the string
        if (angleStr.contains(".")) {
            val parts = angleStr.split(".")
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid decimal format in angle '$angleStr'")
            }
            baseStr = parts[0]
            // Parse decimal part (e.g., "7" from "45.7" seconds)
            val decimalStr = parts[1]
            try {
                decimalPart = "0.$decimalStr".toDouble()
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Invalid decimal seconds in '$angleStr'")
            }
        } else {
            baseStr = angleStr
        }
        
        // Ensure we have at least 6 digits for DDMMSS format
        val normalizedStr = if (baseStr.length == 6) {
            "0$baseStr" // Convert DDMMSS to DDDMMSS
        } else {
            baseStr
        }
        
        if (normalizedStr.length != 7) {
            throw IllegalArgumentException("Invalid base angle format: expected 7 digits, got ${normalizedStr.length} for '$normalizedStr'")
        }
        
        // Parse degrees, minutes, seconds
        val ddd = try {
            normalizedStr.substring(0, 3).toInt()
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid degrees in '$angleStr': ${e.message}")
        }
        
        val mm = try {
            normalizedStr.substring(3, 5).toInt()
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid minutes in '$angleStr': ${e.message}")
        }
        
        val ss = try {
            normalizedStr.substring(5, 7).toInt()
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid seconds in '$angleStr': ${e.message}")
        }
        
        // Validate ranges
        if (mm >= 60) {
            throw IllegalArgumentException("Minutes must be < 60, got $mm in '$angleStr'")
        }
        if (ss >= 60) {
            throw IllegalArgumentException("Seconds must be < 60, got $ss in '$angleStr'")
        }
        
        // Calculate final angle with decimal precision
        // Convert seconds with decimal part to decimal degrees
        val totalSeconds = ss.toDouble() + decimalPart
        
        return ddd.toDouble() + (mm.toDouble() / 60.0) + (totalSeconds / 3600.0)
    }
    
    /**
     * Parse EDM response string into structured data
     */
    fun parseEDMResponseString(raw: String): ParsedEDMReading {
        val parts = raw.trim().split("\\s+".toRegex())
        if (parts.size < 4) {
            throw IllegalArgumentException("Malformed response, got ${parts.size} parts")
        }
        
        val sd = try {
            parts[0].toDouble()
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid slope distance: ${e.message}")
        }
        
        val vaz = parseDDDMMSSAngle(parts[1])
        val har = parseDDDMMSSAngle(parts[2])
        
        return ParsedEDMReading(
            slopeDistanceMm = sd,
            vazDecimal = vaz,
            harDecimal = har
        )
    }
    
    /**
     * Get circle radius for given circle type
     */
    fun getCircleRadius(circleType: String): Double {
        return when (circleType) {
            CIRCLE_SHOT -> UKA_RADIUS_SHOT
            CIRCLE_DISCUS -> UKA_RADIUS_DISCUS
            CIRCLE_HAMMER -> UKA_RADIUS_HAMMER
            CIRCLE_JAVELIN -> UKA_RADIUS_JAVELIN_ARC
            else -> UKA_RADIUS_SHOT
        }
    }
    
    /**
     * Get tolerance for circle type
     */
    fun getToleranceForCircle(circleType: String): Double {
        return if (circleType == CIRCLE_JAVELIN) {
            TOLERANCE_JAVELIN_MM
        } else {
            TOLERANCE_THROWS_CIRCLE_MM
        }
    }
    
    /**
     * Validate calibration measurement against UKA/WA standards
     */
    fun validateCalibration(circleType: String, measuredRadius: Double): Boolean {
        val targetRadius = getCircleRadius(circleType)
        val toleranceMm = getToleranceForCircle(circleType) / 1000.0 // Convert to meters
        val difference = abs(measuredRadius - targetRadius)
        return difference <= toleranceMm
    }
    
    /**
     * Calculate station coordinates from centre reading
     * Using corrected formula from EDM documentation:
     * hd = sd * cos(90° - va) where va is measured from vertically upwards
     */
    fun calculateStationCoordinates(reading: AveragedEDMReading): EDMPoint {
        val sdMeters = reading.slopeDistanceMm / 1000.0
        val vazRad = Math.toRadians(reading.vazDecimal)
        val harRad = Math.toRadians(reading.harDecimal)
        
        // CORRECTED: va is measured from vertically upwards, so hd = sd * cos(90° - va)
        // This simplifies to: hd = sd * sin(va) only if va is from horizontal
        // But since va is from vertical: hd = sd * cos(90° - va) = sd * sin(va)
        // Actually, let's be explicit about the 90° conversion
        val horizontalDistance = sdMeters * cos(Math.toRadians(90.0) - vazRad)
        
        val stationX = -horizontalDistance * cos(harRad)
        val stationY = -horizontalDistance * sin(harRad)
        
        return EDMPoint(stationX, stationY)
    }
    
    /**
     * Verify edge measurement and calculate radius
     * Using corrected formula from EDM documentation:
     * hd = sd * cos(90° - va) where va is measured from vertically upwards
     */
    fun verifyEdge(
        reading: AveragedEDMReading,
        stationCoordinates: EDMPoint,
        circleType: String,
        targetRadius: Double
    ): EdgeVerificationResult {
        val sdMeters = reading.slopeDistanceMm / 1000.0
        val vazRad = Math.toRadians(reading.vazDecimal)
        val harRad = Math.toRadians(reading.harDecimal)
        
        // CORRECTED: Using proper formula hd = sd * cos(90° - va)
        val horizontalDistance = sdMeters * cos(Math.toRadians(90.0) - vazRad)
        val edgeX = horizontalDistance * cos(harRad)
        val edgeY = horizontalDistance * sin(harRad)
        
        val absoluteEdgeX = stationCoordinates.x + edgeX
        val absoluteEdgeY = stationCoordinates.y + edgeY
        
        val measuredRadius = sqrt(absoluteEdgeX.pow(2) + absoluteEdgeY.pow(2))
        val diffMm = (measuredRadius - targetRadius) * 1000.0
        
        val toleranceMm = getToleranceForCircle(circleType)
        val isInTolerance = abs(diffMm) <= toleranceMm
        
        return EdgeVerificationResult(
            measuredRadius = measuredRadius,
            differenceMm = diffMm,
            isInTolerance = isInTolerance,
            toleranceAppliedMm = toleranceMm
        )
    }
    
    /**
     * Calculate throw distance from measurement
     * Using corrected formula from EDM documentation:
     * hd = sd * cos(90° - va) where va is measured from vertically upwards
     */
    fun calculateThrowDistance(
        reading: AveragedEDMReading,
        stationCoordinates: EDMPoint,
        circleRadius: Double
    ): Double {
        val sdMeters = reading.slopeDistanceMm / 1000.0
        val vazRad = Math.toRadians(reading.vazDecimal)
        val harRad = Math.toRadians(reading.harDecimal)
        
        // CORRECTED: Using proper formula hd = sd * cos(90° - va)
        val horizontalDistance = sdMeters * cos(Math.toRadians(90.0) - vazRad)
        val throwX = horizontalDistance * cos(harRad)
        val throwY = horizontalDistance * sin(harRad)
        
        val absoluteThrowX = stationCoordinates.x + throwX
        val absoluteThrowY = stationCoordinates.y + throwY
        
        val distanceFromCentre = sqrt(absoluteThrowX.pow(2) + absoluteThrowY.pow(2))
        return distanceFromCentre - circleRadius
    }
    
    /**
     * Check if double readings are within tolerance
     */
    fun areReadingsConsistent(reading1: ParsedEDMReading, reading2: ParsedEDMReading): Boolean {
        return abs(reading1.slopeDistanceMm - reading2.slopeDistanceMm) <= SD_TOLERANCE_MM
    }
    
    /**
     * Average two EDM readings
     */
    fun averageReadings(reading1: ParsedEDMReading, reading2: ParsedEDMReading): AveragedEDMReading {
        return AveragedEDMReading(
            slopeDistanceMm = (reading1.slopeDistanceMm + reading2.slopeDistanceMm) / 2.0,
            vazDecimal = (reading1.vazDecimal + reading2.vazDecimal) / 2.0,
            harDecimal = (reading1.harDecimal + reading2.harDecimal) / 2.0
        )
    }
    
    /**
     * Convert single reading to averaged reading (for single-read mode)
     */
    fun singleToAveraged(reading: ParsedEDMReading): AveragedEDMReading {
        return AveragedEDMReading(
            slopeDistanceMm = reading.slopeDistanceMm,
            vazDecimal = reading.vazDecimal,
            harDecimal = reading.harDecimal
        )
    }
    
    /**
     * Calculate distance between two points
     */
    fun calculateDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        return sqrt((x2 - x1).pow(2) + (y2 - y1).pow(2))
    }
}