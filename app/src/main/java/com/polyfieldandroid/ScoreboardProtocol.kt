package com.polyfieldandroid

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.Socket

/**
 * Scoreboard TCP Protocol Implementation
 *
 * Supports common athletics scoreboard protocols:
 * - Generic ASCII text display
 * - Seiko/OmegaTiming scoreboard protocol
 * - FinishLynx display protocol
 * - Custom PolyField protocol
 *
 * Commands:
 * - DISPLAY: Show result/text on scoreboard
 * - CLEAR: Clear display
 * - SET_BRIGHTNESS: Adjust brightness
 * - SHOW_ATHLETE: Display athlete info
 * - SHOW_RESULT: Display competition result
 */
class ScoreboardProtocol(
    private val protocolType: ScoreboardType = ScoreboardType.GENERIC
) : DeviceProtocol {

    companion object {
        private const val TAG = "ScoreboardProtocol"
    }

    override val name: String = "Scoreboard-${protocolType.name}"

    /**
     * Supported scoreboard types
     */
    enum class ScoreboardType {
        GENERIC,        // Simple text-based protocol
        SEIKO_OMEGA,    // Seiko/Omega timing system protocol
        FINISH_LYNX,    // FinishLynx scoreboard protocol
        POLYFIELD       // Custom PolyField scoreboard protocol
    }

    /**
     * Display layouts
     */
    enum class DisplayLayout {
        SINGLE_LINE,    // One line display (distance only)
        DUAL_LINE,      // Two lines (athlete + distance)
        QUAD_LINE,      // Four lines (multiple athletes)
        FULL_RESULTS    // Complete results table
    }

    /**
     * Initialize connection with scoreboard
     */
    override suspend fun initialize(socket: Socket): ProtocolResult = withContext(Dispatchers.IO) {
        try {
            when (protocolType) {
                ScoreboardType.SEIKO_OMEGA -> {
                    // Seiko protocol initialization
                    val writer = socket.getOutputStream().writer()
                    writer.write("\u0002INIT\u0003\r\n") // STX + INIT + ETX + CR LF
                    writer.flush()
                }
                ScoreboardType.FINISH_LYNX -> {
                    // FinishLynx initialization
                    val writer = socket.getOutputStream().writer()
                    writer.write("CONNECT\r\n")
                    writer.flush()
                }
                ScoreboardType.POLYFIELD -> {
                    // PolyField protocol handshake
                    val writer = socket.getOutputStream().writer()
                    writer.write("POLYFIELD_V1\r\n")
                    writer.flush()
                }
                else -> {
                    // Generic scoreboards usually don't need initialization
                }
            }

            Log.d(TAG, "Scoreboard initialized: $protocolType")
            ProtocolResult(success = true)

        } catch (e: Exception) {
            Log.e(TAG, "Scoreboard initialization failed: ${e.message}")
            ProtocolResult(success = false, error = e.message)
        }
    }

    /**
     * Encode command for scoreboard
     */
    override fun encodeCommand(command: DeviceCommand): String {
        return when (command.type) {
            "DISPLAY_RESULT" -> encodeDisplayResult(command.parameters)
            "DISPLAY_ATHLETE" -> encodeDisplayAthlete(command.parameters)
            "DISPLAY_TEXT" -> encodeDisplayText(command.parameters)
            "CLEAR" -> encodeClearCommand()
            "SET_BRIGHTNESS" -> encodeBrightnessCommand(command.parameters)
            "SHOW_LEADERBOARD" -> encodeLeaderboard(command.parameters)
            else -> {
                Log.w(TAG, "Unknown command type: ${command.type}")
                "CLEAR\r\n"
            }
        }
    }

    /**
     * Read response from scoreboard (if any)
     */
    override fun readResponse(reader: BufferedReader): String {
        return try {
            when (protocolType) {
                ScoreboardType.SEIKO_OMEGA -> {
                    // Seiko sends ACK/NAK
                    reader.readLine() ?: ""
                }
                ScoreboardType.FINISH_LYNX -> {
                    // FinishLynx sends OK/ERROR
                    reader.readLine() ?: ""
                }
                ScoreboardType.POLYFIELD -> {
                    // PolyField sends JSON response
                    reader.readLine() ?: ""
                }
                else -> {
                    // Most scoreboards don't send responses
                    ""
                }
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Decode scoreboard response
     */
    override fun decodeResponse(rawResponse: String, command: DeviceCommand): DeviceResponse {
        if (rawResponse.isEmpty()) {
            // Most scoreboards don't send responses - assume success
            return DeviceResponse(success = true, data = mapOf("status" to "sent"))
        }

        return try {
            when (protocolType) {
                ScoreboardType.SEIKO_OMEGA -> decodeSeikoResponse(rawResponse)
                ScoreboardType.FINISH_LYNX -> decodeLynxResponse(rawResponse)
                ScoreboardType.POLYFIELD -> decodePolyFieldResponse(rawResponse)
                else -> decodeGenericResponse(rawResponse)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode scoreboard response: ${e.message}")
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
                    ScoreboardType.SEIKO_OMEGA -> {
                        val writer = socket.getOutputStream().writer()
                        writer.write("\u0002CLEAR\u0003\r\n")
                        writer.flush()
                    }
                    ScoreboardType.FINISH_LYNX -> {
                        val writer = socket.getOutputStream().writer()
                        writer.write("DISCONNECT\r\n")
                        writer.flush()
                    }
                    else -> {
                        // Clear generic scoreboard
                        val writer = socket.getOutputStream().writer()
                        writer.write("CLEAR\r\n")
                        writer.flush()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cleanup error: ${e.message}")
            }
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Encode display result command
     * Shows measurement result on scoreboard
     */
    private fun encodeDisplayResult(params: Map<String, Any>): String {
        val distance = params["distance"] as? String ?: "0.00"
        val unit = params["unit"] as? String ?: "m"
        val athleteName = params["athleteName"] as? String
        val bib = params["bib"] as? String
        val attempt = params["attempt"] as? Int

        return when (protocolType) {
            ScoreboardType.GENERIC -> {
                if (athleteName != null) {
                    "DISPLAY:$athleteName - $distance$unit\r\n"
                } else {
                    "DISPLAY:$distance$unit\r\n"
                }
            }
            ScoreboardType.SEIKO_OMEGA -> {
                // Seiko format: STX + DATA + ETX
                "\u0002RESULT:$distance$unit\u0003\r\n"
            }
            ScoreboardType.FINISH_LYNX -> {
                "SHOW:RESULT:$distance:$unit\r\n"
            }
            ScoreboardType.POLYFIELD -> {
                // JSON format for custom protocol
                buildPolyFieldCommand(
                    "DISPLAY_RESULT",
                    mapOf(
                        "distance" to distance,
                        "unit" to unit,
                        "athlete" to (athleteName ?: ""),
                        "bib" to (bib ?: ""),
                        "attempt" to (attempt ?: 0)
                    )
                )
            }
        }
    }

    /**
     * Encode display athlete command
     * Shows athlete info on scoreboard
     */
    private fun encodeDisplayAthlete(params: Map<String, Any>): String {
        val name = params["name"] as? String ?: ""
        val bib = params["bib"] as? String ?: ""
        val club = params["club"] as? String ?: ""

        return when (protocolType) {
            ScoreboardType.GENERIC -> "DISPLAY:BIB $bib - $name ($club)\r\n"
            ScoreboardType.SEIKO_OMEGA -> "\u0002ATHLETE:$bib:$name:$club\u0003\r\n"
            ScoreboardType.FINISH_LYNX -> "SHOW:ATHLETE:$bib:$name:$club\r\n"
            ScoreboardType.POLYFIELD -> buildPolyFieldCommand(
                "DISPLAY_ATHLETE",
                mapOf("name" to name, "bib" to bib, "club" to club)
            )
        }
    }

    /**
     * Encode display text command
     * Shows arbitrary text on scoreboard
     */
    private fun encodeDisplayText(params: Map<String, Any>): String {
        val text = params["text"] as? String ?: ""
        val line = params["line"] as? Int ?: 1

        return when (protocolType) {
            ScoreboardType.GENERIC -> "DISPLAY:$text\r\n"
            ScoreboardType.SEIKO_OMEGA -> "\u0002TEXT:$line:$text\u0003\r\n"
            ScoreboardType.FINISH_LYNX -> "SHOW:TEXT:$text\r\n"
            ScoreboardType.POLYFIELD -> buildPolyFieldCommand(
                "DISPLAY_TEXT",
                mapOf("text" to text, "line" to line)
            )
        }
    }

    /**
     * Encode clear command
     */
    private fun encodeClearCommand(): String {
        return when (protocolType) {
            ScoreboardType.GENERIC -> "CLEAR\r\n"
            ScoreboardType.SEIKO_OMEGA -> "\u0002CLEAR\u0003\r\n"
            ScoreboardType.FINISH_LYNX -> "CLEAR\r\n"
            ScoreboardType.POLYFIELD -> buildPolyFieldCommand("CLEAR", emptyMap())
        }
    }

    /**
     * Encode brightness command
     */
    private fun encodeBrightnessCommand(params: Map<String, Any>): String {
        val level = params["level"] as? Int ?: 50 // 0-100

        return when (protocolType) {
            ScoreboardType.GENERIC -> "BRIGHTNESS:$level\r\n"
            ScoreboardType.SEIKO_OMEGA -> "\u0002BRIGHT:$level\u0003\r\n"
            ScoreboardType.FINISH_LYNX -> "SET:BRIGHTNESS:$level\r\n"
            ScoreboardType.POLYFIELD -> buildPolyFieldCommand(
                "SET_BRIGHTNESS",
                mapOf("level" to level)
            )
        }
    }

    /**
     * Encode leaderboard command
     * Shows top results from competition
     */
    private fun encodeLeaderboard(params: Map<String, Any>): String {
        @Suppress("UNCHECKED_CAST")
        val results = params["results"] as? List<Map<String, Any>> ?: emptyList()

        return when (protocolType) {
            ScoreboardType.POLYFIELD -> {
                buildPolyFieldCommand("SHOW_LEADERBOARD", mapOf("results" to results))
            }
            else -> {
                // Build simple text leaderboard
                val lines = results.take(4).mapIndexed { index, result ->
                    val pos = index + 1
                    val name = result["name"] as? String ?: ""
                    val mark = result["mark"] as? String ?: ""
                    "$pos. $name - $mark"
                }
                "DISPLAY:${lines.joinToString(" | ")}\r\n"
            }
        }
    }

    /**
     * Build PolyField JSON command
     */
    private fun buildPolyFieldCommand(command: String, params: Map<String, Any>): String {
        // Simple JSON-like format
        val paramsJson = params.entries.joinToString(",") { (k, v) ->
            "\"$k\":\"$v\""
        }
        return "{\"cmd\":\"$command\",\"params\":{$paramsJson}}\r\n"
    }

    /**
     * Decode Seiko/Omega response
     */
    private fun decodeSeikoResponse(response: String): DeviceResponse {
        return if (response.contains("\u0006")) { // ACK
            DeviceResponse(success = true, data = mapOf("status" to "acknowledged"))
        } else if (response.contains("\u0015")) { // NAK
            DeviceResponse(success = false, error = "Scoreboard rejected command")
        } else {
            DeviceResponse(success = true, data = mapOf("response" to response))
        }
    }

    /**
     * Decode FinishLynx response
     */
    private fun decodeLynxResponse(response: String): DeviceResponse {
        return if (response.startsWith("OK")) {
            DeviceResponse(success = true, data = mapOf("status" to "ok"))
        } else if (response.startsWith("ERROR")) {
            DeviceResponse(success = false, error = response.removePrefix("ERROR:").trim())
        } else {
            DeviceResponse(success = true, data = mapOf("response" to response))
        }
    }

    /**
     * Decode PolyField JSON response
     */
    private fun decodePolyFieldResponse(response: String): DeviceResponse {
        return try {
            // Simple JSON parsing (in production, use Gson)
            if (response.contains("\"success\":true")) {
                DeviceResponse(success = true, data = mapOf("response" to response))
            } else {
                DeviceResponse(success = false, error = "Command failed")
            }
        } catch (e: Exception) {
            DeviceResponse(success = false, error = "Invalid JSON response")
        }
    }

    /**
     * Decode generic response
     */
    private fun decodeGenericResponse(response: String): DeviceResponse {
        return DeviceResponse(
            success = true,
            data = mapOf("response" to response)
        )
    }
}

/**
 * Scoreboard display request
 */
data class ScoreboardDisplayRequest(
    val type: String, // "RESULT", "ATHLETE", "TEXT", "LEADERBOARD"
    val content: Map<String, Any>
)

/**
 * Helper function to create scoreboard result display
 */
fun createResultDisplay(
    distance: String,
    unit: String = "m",
    athleteName: String? = null,
    bib: String? = null,
    attempt: Int? = null
): DeviceCommand {
    return DeviceCommand(
        type = "DISPLAY_RESULT",
        parameters = buildMap {
            put("distance", distance)
            put("unit", unit)
            athleteName?.let { put("athleteName", it) }
            bib?.let { put("bib", it) }
            attempt?.let { put("attempt", it) }
        },
        expectResponse = false
    )
}

/**
 * Helper function to create scoreboard athlete display
 */
fun createAthleteDisplay(
    name: String,
    bib: String,
    club: String = ""
): DeviceCommand {
    return DeviceCommand(
        type = "DISPLAY_ATHLETE",
        parameters = mapOf(
            "name" to name,
            "bib" to bib,
            "club" to club
        ),
        expectResponse = false
    )
}

/**
 * Helper function to create scoreboard text display
 */
fun createTextDisplay(text: String, line: Int = 1): DeviceCommand {
    return DeviceCommand(
        type = "DISPLAY_TEXT",
        parameters = mapOf(
            "text" to text,
            "line" to line
        ),
        expectResponse = false
    )
}

/**
 * Helper function to clear scoreboard
 */
fun createClearCommand(): DeviceCommand {
    return DeviceCommand(
        type = "CLEAR",
        parameters = emptyMap(),
        expectResponse = false
    )
}
