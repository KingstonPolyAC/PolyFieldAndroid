package com.polyfieldandroid

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

/**
 * Network Device Communication Module
 * Handles TCP/IP communication with network-connected devices (wind gauges, scoreboards, etc.)
 */
class NetworkDeviceModule {

    companion object {
        private const val TAG = "NetworkDeviceModule"
        private const val DEFAULT_TIMEOUT_MS = 5000
        private const val DEFAULT_SOCKET_TIMEOUT_MS = 3000
    }

    // Active device connections
    private val connections = ConcurrentHashMap<String, NetworkDeviceConnection>()

    /**
     * Network device connection wrapper
     */
    data class NetworkDeviceConnection(
        val deviceId: String,
        val host: String,
        val port: Int,
        val protocol: DeviceProtocol,
        var socket: Socket? = null,
        var isConnected: Boolean = false,
        var lastError: String? = null
    )

    /**
     * Connect to a network device
     * @param deviceId Unique identifier for this device (e.g., "wind_gauge_1", "scoreboard_main")
     * @param host IP address or hostname
     * @param port TCP port number
     * @param protocol Device-specific protocol adapter
     * @return Success status and connection info
     */
    suspend fun connect(
        deviceId: String,
        host: String,
        port: Int,
        protocol: DeviceProtocol
    ): NetworkConnectionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Connecting to device $deviceId at $host:$port")

            // Close existing connection if present
            disconnect(deviceId)

            // Create socket with timeout
            val socket = withTimeout(DEFAULT_TIMEOUT_MS.toLong()) {
                Socket(host, port).apply {
                    soTimeout = DEFAULT_SOCKET_TIMEOUT_MS
                    keepAlive = true
                    tcpNoDelay = true // Disable Nagle's algorithm for low-latency
                }
            }

            // Initialize connection with protocol handshake
            val connection = NetworkDeviceConnection(
                deviceId = deviceId,
                host = host,
                port = port,
                protocol = protocol,
                socket = socket,
                isConnected = true
            )

            // Perform protocol-specific initialization
            val initResult = protocol.initialize(socket)
            if (!initResult.success) {
                socket.close()
                return@withContext NetworkConnectionResult(
                    success = false,
                    error = "Protocol initialization failed: ${initResult.error}"
                )
            }

            connections[deviceId] = connection

            Log.d(TAG, "Successfully connected to $deviceId at $host:$port")
            NetworkConnectionResult(
                success = true,
                deviceId = deviceId,
                connectionInfo = "Connected to $host:$port (${protocol.name})"
            )

        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Connection timeout for $deviceId at $host:$port")
            NetworkConnectionResult(
                success = false,
                error = "Connection timeout: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to $deviceId: ${e.message}", e)
            NetworkConnectionResult(
                success = false,
                error = "Connection failed: ${e.message}"
            )
        }
    }

    /**
     * Disconnect from a network device
     */
    suspend fun disconnect(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = connections.remove(deviceId)
            if (connection?.socket != null) {
                connection.protocol.cleanup(connection.socket!!)
                connection.socket?.close()
                connection.isConnected = false
                Log.d(TAG, "Disconnected from $deviceId")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from $deviceId: ${e.message}")
            false
        }
    }

    /**
     * Send command to device and receive response
     * @param deviceId Device identifier
     * @param command Command to send
     * @return Device response
     */
    suspend fun sendCommand(
        deviceId: String,
        command: DeviceCommand
    ): DeviceResponse = withContext(Dispatchers.IO) {
        val connection = connections[deviceId]

        if (connection == null || !connection.isConnected || connection.socket == null) {
            return@withContext DeviceResponse(
                success = false,
                error = "Device $deviceId not connected"
            )
        }

        try {
            val socket = connection.socket!!

            // Check if protocol supports binary messages (e.g., Daktronics)
            if (connection.protocol is DaktronicsScoreboardProtocol) {
                return@withContext (connection.protocol as DaktronicsScoreboardProtocol)
                    .sendBinaryMessage(socket, command)
            }

            // Standard text-based protocol
            // Encode command using protocol
            val encodedCommand = connection.protocol.encodeCommand(command)

            // Send command
            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
            writer.write(encodedCommand)
            writer.flush()

            Log.d(TAG, "Sent command to $deviceId: ${command.type}")

            // Read response if expected
            if (command.expectResponse) {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val rawResponse = connection.protocol.readResponse(reader)

                // Decode response using protocol
                val response = connection.protocol.decodeResponse(rawResponse, command)

                Log.d(TAG, "Received response from $deviceId: ${response.data}")
                response
            } else {
                DeviceResponse(success = true, data = mapOf("status" to "sent"))
            }

        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Command timeout for $deviceId")
            connection.lastError = "Timeout: ${e.message}"
            DeviceResponse(
                success = false,
                error = "Command timeout: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error sending command to $deviceId: ${e.message}", e)
            connection.lastError = e.message
            connection.isConnected = false
            DeviceResponse(
                success = false,
                error = "Command failed: ${e.message}"
            )
        }
    }

    /**
     * Check if device is connected
     */
    fun isConnected(deviceId: String): Boolean {
        return connections[deviceId]?.isConnected == true
    }

    /**
     * Get connection status
     */
    fun getConnectionStatus(deviceId: String): ConnectionStatus {
        val connection = connections[deviceId]
        return ConnectionStatus(
            deviceId = deviceId,
            isConnected = connection?.isConnected ?: false,
            host = connection?.host,
            port = connection?.port,
            protocol = connection?.protocol?.name,
            lastError = connection?.lastError
        )
    }

    /**
     * Disconnect all devices
     */
    suspend fun disconnectAll() {
        val deviceIds = connections.keys.toList()
        deviceIds.forEach { disconnect(it) }
    }

    /**
     * Get list of all connected devices
     */
    fun getConnectedDevices(): List<String> {
        return connections.filter { it.value.isConnected }.keys.toList()
    }
}

/**
 * Device protocol interface - implement for each device type
 */
interface DeviceProtocol {
    val name: String

    /**
     * Initialize connection (handshake, authentication, etc.)
     */
    suspend fun initialize(socket: Socket): ProtocolResult

    /**
     * Encode command for transmission
     */
    fun encodeCommand(command: DeviceCommand): String

    /**
     * Read raw response from device
     */
    fun readResponse(reader: BufferedReader): String

    /**
     * Decode device response
     */
    fun decodeResponse(rawResponse: String, command: DeviceCommand): DeviceResponse

    /**
     * Cleanup before disconnect
     */
    suspend fun cleanup(socket: Socket)
}

/**
 * Generic device command
 */
data class DeviceCommand(
    val type: String,
    val parameters: Map<String, Any> = emptyMap(),
    val expectResponse: Boolean = true
)

/**
 * Generic device response
 */
data class DeviceResponse(
    val success: Boolean,
    val data: Map<String, Any> = emptyMap(),
    val error: String? = null
)

/**
 * Network connection result
 */
data class NetworkConnectionResult(
    val success: Boolean,
    val deviceId: String? = null,
    val connectionInfo: String? = null,
    val error: String? = null
)

/**
 * Protocol operation result
 */
data class ProtocolResult(
    val success: Boolean,
    val error: String? = null
)

/**
 * Connection status info
 */
data class ConnectionStatus(
    val deviceId: String,
    val isConnected: Boolean,
    val host: String?,
    val port: Int?,
    val protocol: String?,
    val lastError: String?
)
