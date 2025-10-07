package com.polyfieldandroid

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.Socket

/**
 * Daktronics Scoreboard Protocol Implementation
 *
 * Implements the binary protocol for Daktronics 7-segment LED scoreboards
 * (TI-2009 Glow Cube and compatible models)
 *
 * Protocol Specifications:
 * - Transport: TCP/IP (default port 1950) or RS-232 (19200 baud, 8-N-1)
 * - Message Format: 14-15 byte binary messages with 7-segment encoding
 * - Handshake: 0x55 → 0x06 ACK
 * - Two messages per result: Performance mark + Athlete ID/attempt
 *
 * Based on: FieldLynx Scoreboard Script for Daktronics TI-2009
 */
class DaktronicsScoreboardProtocol : DeviceProtocol {

    companion object {
        private const val TAG = "DaktronicsProtocol"

        // Protocol constants
        private const val HANDSHAKE_BYTE: Byte = 0x55.toByte()
        private const val HANDSHAKE_ACK: Byte = 0x06.toByte()
        private const val SYNC_BYTE: Byte = 0xAA.toByte()
        private const val ADDRESS_BYTE: Byte = 0x16.toByte()
        private const val COUNT_BYTE: Byte = 0x09.toByte()
        private const val CONTROL_BYTE: Byte = 0x22.toByte()
        private const val DECIMAL_POINT: Byte = 0x40.toByte()
        private const val NO_PUNCTUATION: Byte = 0x00.toByte()

        // Tag values (increment by 0x10 between messages)
        private const val TAG_PERFORMANCE: Byte = 0x08.toByte()
        private const val TAG_ATHLETE: Byte = 0x18.toByte()

        // 7-segment encoding table
        private val SEGMENT_MAP = mapOf(
            ' ' to 0x00.toByte(),
            '0' to 0xBF.toByte(),
            '1' to 0x86.toByte(),
            '2' to 0xDB.toByte(),
            '3' to 0xCF.toByte(),
            '4' to 0xE6.toByte(),
            '5' to 0xED.toByte(),
            '6' to 0xFD.toByte(),
            '7' to 0x87.toByte(),
            '8' to 0xFF.toByte(),
            '9' to 0xE7.toByte()
        )

        // Delay between messages (milliseconds)
        private const val MESSAGE_DELAY_MS = 20L
    }

    override val name: String = "Daktronics-TI2009"

    // Track message tag counter
    private var currentTag: Byte = TAG_PERFORMANCE

    /**
     * Initialize connection with handshake
     */
    override suspend fun initialize(socket: Socket): ProtocolResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing Daktronics scoreboard connection")

            // Send handshake byte
            socket.getOutputStream().write(HANDSHAKE_BYTE.toInt())
            socket.getOutputStream().flush()

            Log.d(TAG, "Sent handshake: 0x55")

            // Wait for ACK response
            socket.soTimeout = 2000 // 2 second timeout for handshake
            val ack = socket.getInputStream().read()

            if (ack == HANDSHAKE_ACK.toInt()) {
                Log.d(TAG, "Received handshake ACK: 0x06")
                ProtocolResult(success = true)
            } else {
                Log.e(TAG, "Handshake failed - received: 0x${ack.toString(16)}")
                ProtocolResult(success = false, error = "Handshake failed - expected 0x06, got 0x${ack.toString(16)}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Handshake failed: ${e.message}", e)
            ProtocolResult(success = false, error = "Handshake error: ${e.message}")
        }
    }

    /**
     * Encode command for scoreboard
     */
    override fun encodeCommand(command: DeviceCommand): String {
        // Daktronics uses binary protocol, but we'll handle encoding in sendCommand
        // This method returns empty string as we don't use text-based commands
        return ""
    }

    /**
     * Read response from scoreboard
     * Daktronics scoreboard doesn't send responses after data messages
     */
    override fun readResponse(reader: BufferedReader): String {
        return ""
    }

    /**
     * Decode scoreboard response
     */
    override fun decodeResponse(rawResponse: String, command: DeviceCommand): DeviceResponse {
        // Daktronics doesn't send responses for display commands
        return DeviceResponse(success = true, data = mapOf("status" to "displayed"))
    }

    /**
     * Cleanup before disconnect
     */
    override suspend fun cleanup(socket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                // Optionally clear display before disconnect
                Log.d(TAG, "Cleaning up Daktronics connection")
            } catch (e: Exception) {
                Log.e(TAG, "Cleanup error: ${e.message}")
            }
        }
    }

    /**
     * Send binary message directly to scoreboard
     * This bypasses the text-based command system for binary protocols
     */
    suspend fun sendBinaryMessage(socket: Socket, command: DeviceCommand): DeviceResponse {
        return withContext(Dispatchers.IO) {
            try {
                when (command.type) {
                    "DISPLAY_RESULT" -> displayResult(socket, command.parameters)
                    "CLEAR" -> clearDisplay(socket)
                    else -> DeviceResponse(success = false, error = "Unknown command: ${command.type}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Binary message failed: ${e.message}", e)
                DeviceResponse(success = false, error = "Message error: ${e.message}")
            }
        }
    }

    /**
     * Display athlete result on scoreboard
     */
    private suspend fun displayResult(socket: Socket, params: Map<String, Any>): DeviceResponse {
        val distance = params["distance"] as? String ?: return DeviceResponse(
            success = false,
            error = "Missing distance parameter"
        )
        val bib = params["bib"] as? String ?: "0"
        val attempt = (params["attempt"] as? Int) ?: 1

        try {
            // Convert bib to integer (handle potential parsing errors)
            val athleteId = bib.toIntOrNull() ?: 0

            // Message 1: Performance mark
            val perfMessage = createPerformanceMessage(distance, TAG_PERFORMANCE)
            socket.getOutputStream().write(perfMessage)
            socket.getOutputStream().flush()

            Log.d(TAG, "Sent performance message: $distance")
            logBinaryMessage("PERF", perfMessage)

            // Small delay between messages
            delay(MESSAGE_DELAY_MS)

            // Message 2: Athlete ID and attempt
            val athleteMessage = createAthleteMessage(athleteId, attempt, TAG_ATHLETE)
            socket.getOutputStream().write(athleteMessage)
            socket.getOutputStream().flush()

            Log.d(TAG, "Sent athlete message: Bib $bib, Attempt $attempt")
            logBinaryMessage("ATHLETE", athleteMessage)

            return DeviceResponse(
                success = true,
                data = mapOf(
                    "displayed" to distance,
                    "bib" to bib,
                    "attempt" to attempt
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Display result failed: ${e.message}", e)
            return DeviceResponse(success = false, error = "Display error: ${e.message}")
        }
    }

    /**
     * Clear scoreboard display
     */
    private suspend fun clearDisplay(socket: Socket): DeviceResponse {
        return displayResult(socket, mapOf("distance" to "     ", "bib" to "0", "attempt" to 0))
    }

    /**
     * Create performance mark message (15 bytes)
     * Format: "  21.34 " (7 digits, 2 leading spaces, decimal point)
     */
    private fun createPerformanceMessage(distance: String, tag: Byte): ByteArray {
        val message = ByteArray(15)

        // Remove existing decimal point for processing
        val cleanDistance = distance.replace(".", "")

        // Determine decimal position (typically after 4th digit for "XX.XX")
        val hasDecimal = distance.contains('.')
        val decimalPos = if (hasDecimal) distance.indexOf('.') else -1

        // Format display: right-align in 7 characters
        var displayText = cleanDistance.padStart(7, ' ')
        if (displayText.length > 7) {
            displayText = displayText.takeLast(7) // Truncate if too long
        }

        // Build message
        message[0] = SYNC_BYTE
        message[1] = ADDRESS_BYTE
        message[2] = COUNT_BYTE
        message[3] = tag
        message[4] = 0 // Checksum 1 (calculated below)
        message[5] = CONTROL_BYTE

        // Encode 7 display digits
        for (i in 0 until 7) {
            val char = displayText.getOrNull(i) ?: ' '
            message[6 + i] = SEGMENT_MAP[char] ?: SEGMENT_MAP[' ']!!
        }

        // Punctuation (decimal point)
        message[13] = if (hasDecimal) DECIMAL_POINT else NO_PUNCTUATION

        // Calculate checksums
        message[4] = calculateChecksum(message, 0, 4)
        message[14] = calculateChecksum(message, 5, 14)

        return message
    }

    /**
     * Create athlete ID and attempt message (15 bytes)
     * Format: " 79    1" (3-digit ID, spaces, 1-digit attempt)
     */
    private fun createAthleteMessage(athleteId: Int, attempt: Int, tag: Byte): ByteArray {
        val message = ByteArray(15)

        // Format: " ID    A" where ID is 3 digits, A is attempt
        val idStr = athleteId.toString().padStart(3, ' ').take(3)
        val attemptStr = (attempt % 10).toString() // Single digit
        val displayText = "$idStr   $attemptStr"

        // Build message
        message[0] = SYNC_BYTE
        message[1] = ADDRESS_BYTE
        message[2] = COUNT_BYTE
        message[3] = tag
        message[4] = 0 // Checksum 1 (calculated below)
        message[5] = CONTROL_BYTE

        // Encode 7 display digits
        for (i in 0 until 7) {
            val char = displayText.getOrNull(i) ?: ' '
            message[6 + i] = SEGMENT_MAP[char] ?: SEGMENT_MAP[' ']!!
        }

        // No punctuation for athlete message
        message[13] = NO_PUNCTUATION

        // Calculate checksums
        message[4] = calculateChecksum(message, 0, 4)
        message[14] = calculateChecksum(message, 5, 14)

        return message
    }

    /**
     * Calculate subtraction checksum
     * Algorithm: Initialize to 0, subtract each byte, wrap at 8 bits
     */
    private fun calculateChecksum(data: ByteArray, start: Int, end: Int): Byte {
        var checksum = 0
        for (i in start until end) {
            checksum = (checksum - data[i].toInt()) and 0xFF
        }
        return checksum.toByte()
    }

    /**
     * Log binary message for debugging
     */
    private fun logBinaryMessage(label: String, message: ByteArray) {
        val hex = message.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        Log.d(TAG, "$label Message: $hex")
    }
}

/**
 * Extension function to send Daktronics binary commands via NetworkDeviceModule
 */
suspend fun NetworkDeviceModule.sendDaktronicsCommand(
    deviceId: String,
    distance: String,
    bib: String,
    attempt: Int
): DeviceResponse {
    val command = DeviceCommand(
        type = "DISPLAY_RESULT",
        parameters = mapOf(
            "distance" to distance,
            "bib" to bib,
            "attempt" to attempt
        ),
        expectResponse = false
    )

    return sendCommand(deviceId, command)
}
