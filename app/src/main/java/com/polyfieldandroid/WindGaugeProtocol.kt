package com.polyfieldandroid

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.Socket

/**
 * Wind Gauge TCP Protocol Implementation
 *
 * Supports common wind gauge protocols:
 * - Gill WindMaster (ASCII protocol)
 * - Lynx Type 3002 (Simple ASCII)
 * - Generic NMEA-style wind gauges
 *
 * Protocol format (typical):
 * Command: "READ\r\n" or "R\r\n"
 * Response: "+2.3\r\n" or "WS:+2.3,WD:045\r\n"
 *
 * Wind speed in m/s, positive/negative indicates direction
 */
class WindGaugeProtocol(
    private val protocolType: WindGaugeType = WindGaugeType.GENERIC
) : DeviceProtocol {

    companion object {
        private const val TAG = "WindGaugeProtocol"
    }

    override val name: String = "WindGauge-${protocolType.name}"

    /**
     * Supported wind gauge types
     */
    enum class WindGaugeType {
        GENERIC,        // Simple "READ" -> "+2.3" format
        GILL_WINDMASTER, // Gill WindMaster ASCII protocol
        LYNX_3002,      // Lynx Type 3002
        NMEA_STYLE      // NMEA-like comma-separated format
    }

    /**
     * Initialize connection with wind gauge
     * Some gauges require initialization commands
     */
    override suspend fun initialize(socket: Socket): ProtocolResult = withContext(Dispatchers.IO) {
        try {
            when (protocolType) {
                WindGaugeType.GILL_WINDMASTER -> {
                    // Gill WindMaster may need initialization
                    // Send "Q\r\n" to enable continuous output or "P\r\n" for polling
                    val writer = socket.getOutputStream().writer()
                    writer.write("P\r\n") // Poll mode
                    writer.flush()
                }
                else -> {
                    // Most simple gauges don't need initialization
                }
            }

            Log.d(TAG, "Wind gauge initialized: $protocolType")
            ProtocolResult(success = true)

        } catch (e: Exception) {
            Log.e(TAG, "Wind gauge initialization failed: ${e.message}")
            ProtocolResult(success = false, error = e.message)
        }
    }

    /**
     * Encode command for wind gauge
     */
    override fun encodeCommand(command: DeviceCommand): String {
        return when (command.type) {
            "READ_WIND" -> encodeReadCommand()
            "SET_AVERAGING" -> encodeAveragingCommand(command.parameters)
            "RESET" -> encodeResetCommand()
            else -> {
                Log.w(TAG, "Unknown command type: ${command.type}")
                "READ\r\n"
            }
        }
    }

    /**
     * Read response from wind gauge
     */
    override fun readResponse(reader: BufferedReader): String {
        return when (protocolType) {
            WindGaugeType.GILL_WINDMASTER -> {
                // Gill may send multi-line response
                val line = reader.readLine()
                line ?: ""
            }
            WindGaugeType.NMEA_STYLE -> {
                // NMEA sentences end with checksum
                val line = reader.readLine()
                line ?: ""
            }
            else -> {
                // Simple single-line response
                reader.readLine() ?: ""
            }
        }
    }

    /**
     * Decode wind gauge response
     */
    override fun decodeResponse(rawResponse: String, command: DeviceCommand): DeviceResponse {
        if (rawResponse.isEmpty()) {
            return DeviceResponse(
                success = false,
                error = "Empty response from wind gauge"
            )
        }

        return try {
            when (protocolType) {
                WindGaugeType.GENERIC -> decodeGenericResponse(rawResponse)
                WindGaugeType.GILL_WINDMASTER -> decodeGillResponse(rawResponse)
                WindGaugeType.LYNX_3002 -> decodeLynxResponse(rawResponse)
                WindGaugeType.NMEA_STYLE -> decodeNMEAResponse(rawResponse)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode wind response: ${e.message}")
            DeviceResponse(
                success = false,
                error = "Decode error: ${e.message}"
            )
        }
    }

    /**
     * Cleanup before disconnect
     */
    override suspend fun cleanup(socket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                when (protocolType) {
                    WindGaugeType.GILL_WINDMASTER -> {
                        // Send stop command if needed
                        val writer = socket.getOutputStream().writer()
                        writer.write("Q\r\n")
                        writer.flush()
                    }
                    else -> {
                        // Most gauges don't need cleanup
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cleanup error: ${e.message}")
            }
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Encode read wind command
     */
    private fun encodeReadCommand(): String {
        return when (protocolType) {
            WindGaugeType.GENERIC -> "READ\r\n"
            WindGaugeType.GILL_WINDMASTER -> "Q\r\n"
            WindGaugeType.LYNX_3002 -> "R\r\n"
            WindGaugeType.NMEA_STYLE -> "\$WIMWV\r\n"
        }
    }

    /**
     * Encode averaging period command
     */
    private fun encodeAveragingCommand(params: Map<String, Any>): String {
        val seconds = params["seconds"] as? Int ?: 5
        return when (protocolType) {
            WindGaugeType.GILL_WINDMASTER -> "AVG=$seconds\r\n"
            else -> "AVG $seconds\r\n"
        }
    }

    /**
     * Encode reset command
     */
    private fun encodeResetCommand(): String {
        return "RESET\r\n"
    }

    /**
     * Decode generic wind gauge response
     * Format: "+2.3" or "-1.5"
     */
    private fun decodeGenericResponse(response: String): DeviceResponse {
        val trimmed = response.trim()
        val windSpeed = trimmed.toDoubleOrNull()

        if (windSpeed == null) {
            return DeviceResponse(
                success = false,
                error = "Invalid wind speed format: $response"
            )
        }

        return DeviceResponse(
            success = true,
            data = mapOf(
                "windSpeed" to windSpeed,
                "unit" to "m/s",
                "rawResponse" to response
            )
        )
    }

    /**
     * Decode Gill WindMaster response
     * Format: "Q,123,+002.30,M,00,\r\n"
     * Fields: Node, Direction, Speed, Units, Status, Checksum
     */
    private fun decodeGillResponse(response: String): DeviceResponse {
        val parts = response.trim().split(",")

        if (parts.size < 3) {
            return DeviceResponse(
                success = false,
                error = "Invalid Gill WindMaster response format"
            )
        }

        val windDirection = parts[1].toIntOrNull() ?: 0
        val windSpeed = parts[2].toDoubleOrNull() ?: 0.0

        return DeviceResponse(
            success = true,
            data = mapOf(
                "windSpeed" to windSpeed,
                "windDirection" to windDirection,
                "unit" to "m/s",
                "rawResponse" to response
            )
        )
    }

    /**
     * Decode Lynx Type 3002 response
     * Format: "WS:+2.3\r\n" or "WS:+2.3,WD:045\r\n"
     */
    private fun decodeLynxResponse(response: String): DeviceResponse {
        val trimmed = response.trim()
        val data = mutableMapOf<String, Any>()

        // Parse key:value pairs
        val pairs = trimmed.split(",")
        for (pair in pairs) {
            val kv = pair.split(":")
            if (kv.size == 2) {
                when (kv[0].trim()) {
                    "WS" -> data["windSpeed"] = kv[1].trim().toDoubleOrNull() ?: 0.0
                    "WD" -> data["windDirection"] = kv[1].trim().toIntOrNull() ?: 0
                }
            }
        }

        if (!data.containsKey("windSpeed")) {
            return DeviceResponse(
                success = false,
                error = "No wind speed in response: $response"
            )
        }

        data["unit"] = "m/s"
        data["rawResponse"] = response

        return DeviceResponse(
            success = true,
            data = data
        )
    }

    /**
     * Decode NMEA-style response
     * Format: "$WIMWV,123.4,R,2.3,M,A*checksum\r\n"
     * Fields: Sentence ID, Wind Angle, Reference, Wind Speed, Units, Status, Checksum
     */
    private fun decodeNMEAResponse(response: String): DeviceResponse {
        val trimmed = response.trim()

        // Remove $ and checksum
        val sentence = trimmed.removePrefix("$").split("*")[0]
        val parts = sentence.split(",")

        if (parts.size < 6) {
            return DeviceResponse(
                success = false,
                error = "Invalid NMEA format"
            )
        }

        val windAngle = parts[1].toDoubleOrNull() ?: 0.0
        val windSpeed = parts[3].toDoubleOrNull() ?: 0.0
        val status = parts[5] // A = valid, V = invalid

        if (status != "A") {
            return DeviceResponse(
                success = false,
                error = "Invalid wind data (status=$status)"
            )
        }

        return DeviceResponse(
            success = true,
            data = mapOf(
                "windSpeed" to windSpeed,
                "windDirection" to windAngle.toInt(),
                "unit" to "m/s",
                "rawResponse" to response
            )
        )
    }
}

/**
 * Wind reading result
 */
data class WindReading(
    val success: Boolean,
    val windSpeed: Double? = null,
    val windDirection: Int? = null,
    val unit: String = "m/s",
    val error: String? = null
) {
    companion object {
        fun fromDeviceResponse(response: DeviceResponse): WindReading {
            return if (response.success) {
                WindReading(
                    success = true,
                    windSpeed = response.data["windSpeed"] as? Double,
                    windDirection = response.data["windDirection"] as? Int,
                    unit = response.data["unit"] as? String ?: "m/s"
                )
            } else {
                WindReading(
                    success = false,
                    error = response.error
                )
            }
        }
    }
}
