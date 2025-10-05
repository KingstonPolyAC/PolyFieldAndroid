# Daktronics Scoreboard Protocol Implementation Guide

## Overview

This guide documents the communication protocol for Daktronics 7-segment LED scoreboards (TI-2009 Glow Cube and compatible models) as used with FieldLynx timing systems.

**Protocol Type:** Binary, 7-segment display encoding  
**Transport:** TCP/IP or RS-232 serial  
**Default Serial Settings:** 19200 baud, 8 data bits, No parity, 1 stop bit (8-N-1)  
**Default TCP Port:** 1950

---

## Connection Requirements

### TCP/IP Connection
- **IP Address:** Configurable (commonly 127.0.0.1 for localhost or 172.0.0.1)
- **Port:** 1950
- **Protocol:** TCP

### RS-232 Serial Connection
- **Baud Rate:** 19200
- **Data Bits:** 8
- **Parity:** None
- **Stop Bits:** 1
- **Flow Control:** None

### RS-232 to Ethernet Converter
If using a serial scoreboard with TCP/IP, use an RS-232 to Ethernet converter configured with the above serial settings.

---

## Protocol Overview

### Communication Flow

1. **Handshake Phase**
   - Client sends handshake byte: `0x55`
   - Scoreboard responds with ACK: `0x06`

2. **Data Transmission Phase**
   - Send Message 1: Performance mark (time/distance)
   - Send Message 2: Athlete ID and attempt number

3. **Repeat** for each result

### Message Types

The protocol sends two messages per athlete result:

| Message | Purpose | Content |
|---------|---------|---------|
| **Result[1]** | Athlete Information | 3-digit ID + blank spaces + 1-digit attempt |
| **Result[2]** | Performance Mark | 2 blank spaces + 4-6 digit mark + decimal point |

---

## Message Structure

### Standard Message Format (15 bytes)

```
Byte    Name            Description
----    ----            -----------
[0]     Sync Byte       0xAA (or omitted in some messages)
[1]     Address         0x16 (fixed)
[2]     Count           0x09 (9 data bytes)
[3]     Tag/Command     Increments with each message (starts at 0x08)
[4]     Checksum 1      Checksum of bytes [0-3]
[5]     Control Byte    0x22 (fixed)
[6]     Digit 1         7-segment encoded
[7]     Digit 2         7-segment encoded
[8]     Digit 3         7-segment encoded
[9]     Digit 4         7-segment encoded
[10]    Digit 5         7-segment encoded
[11]    Digit 6         7-segment encoded
[12]    Digit 7         7-segment encoded
[13]    Punctuation     0x40 = decimal point, 0x00 = none
[14]    Checksum 2      Checksum of bytes [5-13]
```

### Message Format Without Sync (14 bytes)

Some messages omit the initial sync byte (0xAA). In this case, all byte positions shift down by 1.

---

## 7-Segment Encoding Table

Characters are encoded using 7-segment display patterns:

| Hex Value | Decimal | Character | Binary Pattern |
|-----------|---------|-----------|----------------|
| `0x00`    | 0       | ` ` (space) | 00000000 |
| `0xBF`    | 191     | `0`       | 10111111 |
| `0x86`    | 134     | `1`       | 10000110 |
| `0xDB`    | 219     | `2`       | 11011011 |
| `0xCF`    | 207     | `3`       | 11001111 |
| `0xE6`    | 230     | `4`       | 11100110 |
| `0xED`    | 237     | `5`       | 11101101 |
| `0xFD`    | 253     | `6`       | 11111101 |
| `0x87`    | 135     | `7`       | 10000111 |
| `0xFF`    | 255     | `8`       | 11111111 |
| `0xE7`    | 231     | `9`       | 11100111 |

### 7-Segment Bit Mapping

```
     A
    ---
  F| G |B
    ---
  E|   |C
    ---  .DP
     D
```

Bit pattern: `DP G F E D C B A` (MSB to LSB)

---

## Checksum Calculation

### Checksum Algorithm

Both checksums use a **subtraction checksum**:

1. Initialize register to 0
2. For each byte in range:
   - Subtract byte value from register
   - Store result back in register (8-bit wrap-around)
3. Final register value is the checksum

### Checksum 1 (Byte 4)
Covers bytes [0-3]:
```python
checksum1 = 0
for byte in [sync, address, count, tag]:
    checksum1 = (checksum1 - byte) & 0xFF
```

### Checksum 2 (Byte 14)
Covers bytes [5-13]:
```python
checksum2 = 0
for byte in [control, digit1, digit2, ..., digit7, punctuation]:
    checksum2 = (checksum2 - byte) & 0xFF
```

---

## Implementation Examples

### Example 1: Display Time "21.34" for Athlete #79, Round 1

#### Message 1: Performance Mark (21.34)

Display format: `  21.34 ` (2 leading spaces, decimal point after digit 4)

```
Byte    Value   Description
[0]     0xAA    Sync byte
[1]     0x16    Address
[2]     0x09    Count
[3]     0x08    Tag (starting value)
[4]     0x??    Checksum 1 (calculated)
[5]     0x22    Control byte
[6]     0x00    Space
[7]     0x00    Space
[8]     0xDB    '2'
[9]     0x86    '1'
[10]    0xCF    '3'
[11]    0xE6    '4'
[12]    0x00    Space
[13]    0x40    Decimal point (after position 9)
[14]    0x??    Checksum 2 (calculated)
```

#### Message 2: Athlete ID & Attempt

Display format: ` 79    1` (athlete 79, attempt 1)

```
Byte    Value   Description
[0]     0xAA    Sync byte
[1]     0x16    Address
[2]     0x09    Count
[3]     0x18    Tag (incremented by 0x10)
[4]     0x??    Checksum 1 (calculated)
[5]     0x22    Control byte
[6]     0x00    Space
[7]     0x87    '7'
[8]     0xE7    '9'
[9]     0x00    Space
[10]    0x00    Space
[11]    0x00    Space
[12]    0x86    '1' (attempt)
[13]    0x00    No punctuation
[14]    0x??    Checksum 2 (calculated)
```

### Python Implementation

```python
# 7-segment encoding table
SEGMENT_MAP = {
    ' ': 0x00, '0': 0xBF, '1': 0x86, '2': 0xDB, '3': 0xCF, '4': 0xE6,
    '5': 0xED, '6': 0xFD, '7': 0x87, '8': 0xFF, '9': 0xE7
}

def calculate_checksum(data):
    """Calculate subtraction checksum"""
    checksum = 0
    for byte in data:
        checksum = (checksum - byte) & 0xFF
    return checksum

def encode_7segment(text, length=7):
    """Encode text to 7-segment bytes"""
    text = text.ljust(length)[:length]  # Pad or truncate to length
    return [SEGMENT_MAP.get(c, 0x00) for c in text]

def create_performance_message(time_str, tag=0x08):
    """Create performance mark message (e.g., "21.34")"""
    # Format: "  21.34 " (7 digits)
    display = f"{time_str:>7}"
    
    # Build message
    msg = [
        0xAA,  # Sync
        0x16,  # Address
        0x09,  # Count
        tag,   # Tag
        0x00,  # Checksum 1 (placeholder)
        0x22,  # Control
    ]
    
    # Add encoded digits
    msg.extend(encode_7segment(display.replace('.', '')))
    
    # Add punctuation (0x40 if decimal point needed)
    msg.append(0x40 if '.' in time_str else 0x00)
    
    # Calculate checksums
    msg[4] = calculate_checksum(msg[0:4])
    checksum2 = calculate_checksum(msg[5:14])
    msg.append(checksum2)
    
    return bytes(msg)

def create_athlete_message(athlete_id, attempt, tag=0x18):
    """Create athlete ID and attempt message"""
    # Format: " 79    1" (7 digits: ID padded left, attempt at end)
    id_str = str(athlete_id).rjust(3)
    display = f"{id_str}   {attempt}"
    
    # Build message (may omit sync byte)
    msg = [
        0x16,  # Address
        0x09,  # Count
        tag,   # Tag
        0x00,  # Checksum 1 (placeholder)
        0x22,  # Control
    ]
    
    # Add encoded digits
    msg.extend(encode_7segment(display))
    
    # No punctuation for athlete message
    msg.append(0x00)
    
    # Calculate checksums
    msg[3] = calculate_checksum(msg[0:3])
    checksum2 = calculate_checksum(msg[4:13])
    msg.append(checksum2)
    
    return bytes(msg)

def send_result(sock, performance, athlete_id, attempt=1):
    """Send complete result to scoreboard"""
    # Send handshake
    sock.sendall(b'\x55')
    response = sock.recv(1)
    if response != b'\x06':
        raise Exception("Handshake failed")
    
    # Send performance message
    perf_msg = create_performance_message(performance, tag=0x08)
    sock.sendall(perf_msg)
    
    # Send athlete message
    athlete_msg = create_athlete_message(athlete_id, attempt, tag=0x18)
    sock.sendall(athlete_msg)

# Usage example
import socket

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.connect(('127.0.0.1', 1950))

try:
    send_result(sock, "21.34", 79, 1)
    send_result(sock, "50.00", 99, 1)
finally:
    sock.close()
```

### Kotlin Implementation

```kotlin
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * Daktronics Scoreboard Protocol Implementation
 */
class DaktronicsScoreboard(
    private val host: String = "127.0.0.1",
    private val port: Int = 1950
) {
    
    companion object {
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
        
        // Protocol constants
        private const val SYNC_BYTE: Byte = 0xAA.toByte()
        private const val HANDSHAKE_BYTE: Byte = 0x55
        private const val ACK_BYTE: Byte = 0x06
        private const val ADDRESS_BYTE: Byte = 0x16
        private const val COUNT_BYTE: Byte = 0x09
        private const val CONTROL_BYTE: Byte = 0x22
        private const val DECIMAL_POINT: Byte = 0x40
        private const val NO_PUNCTUATION: Byte = 0x00
    }
    
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    
    /**
     * Connect to the scoreboard
     */
    fun connect() {
        socket = Socket(host, port)
        outputStream = socket?.getOutputStream()
        inputStream = socket?.getInputStream()
    }
    
    /**
     * Disconnect from the scoreboard
     */
    fun disconnect() {
        outputStream?.close()
        inputStream?.close()
        socket?.close()
        socket = null
        outputStream = null
        inputStream = null
    }
    
    /**
     * Calculate subtraction checksum
     */
    private fun calculateChecksum(data: ByteArray): Byte {
        var checksum = 0
        data.forEach { byte ->
            checksum = (checksum - byte.toInt()) and 0xFF
        }
        return checksum.toByte()
    }
    
    /**
     * Encode text to 7-segment bytes
     */
    private fun encode7Segment(text: String, length: Int = 7): ByteArray {
        val padded = text.padEnd(length).take(length)
        return padded.map { char ->
            SEGMENT_MAP[char] ?: 0x00.toByte()
        }.toByteArray()
    }
    
    /**
     * Create performance mark message
     */
    private fun createPerformanceMessage(timeStr: String, tag: Byte = 0x08): ByteArray {
        // Format: "  21.34 " (7 digits, right-aligned)
        val display = timeStr.padStart(7)
        val hasDecimal = '.' in timeStr
        
        // Build message
        val message = mutableListOf<Byte>()
        message.add(SYNC_BYTE)
        message.add(ADDRESS_BYTE)
        message.add(COUNT_BYTE)
        message.add(tag)
        message.add(0x00) // Checksum 1 placeholder
        message.add(CONTROL_BYTE)
        
        // Add encoded digits (remove decimal for encoding)
        val digits = encode7Segment(display.replace(".", ""))
        message.addAll(digits.toList())
        
        // Add punctuation
        message.add(if (hasDecimal) DECIMAL_POINT else NO_PUNCTUATION)
        
        // Calculate checksums
        val bytes = message.toByteArray()
        bytes[4] = calculateChecksum(bytes.sliceArray(0..3))
        val checksum2 = calculateChecksum(bytes.sliceArray(5..13))
        
        return bytes + checksum2
    }
    
    /**
     * Create athlete ID and attempt message
     */
    private fun createAthleteMessage(athleteId: Int, attempt: Int = 1, tag: Byte = 0x18): ByteArray {
        // Format: " 79    1" (3-digit ID, 3 spaces, 1-digit attempt)
        val idStr = athleteId.toString().padStart(3)
        val display = "${idStr}   $attempt"
        
        // Build message (without sync byte)
        val message = mutableListOf<Byte>()
        message.add(ADDRESS_BYTE)
        message.add(COUNT_BYTE)
        message.add(tag)
        message.add(0x00) // Checksum 1 placeholder
        message.add(CONTROL_BYTE)
        
        // Add encoded digits
        val digits = encode7Segment(display)
        message.addAll(digits.toList())
        
        // No punctuation
        message.add(NO_PUNCTUATION)
        
        // Calculate checksums
        val bytes = message.toByteArray()
        bytes[3] = calculateChecksum(bytes.sliceArray(0..2))
        val checksum2 = calculateChecksum(bytes.sliceArray(4..12))
        
        return bytes + checksum2
    }
    
    /**
     * Perform handshake with scoreboard
     */
    private fun handshake(): Boolean {
        outputStream?.write(HANDSHAKE_BYTE.toInt())
        outputStream?.flush()
        
        val response = inputStream?.read()?.toByte()
        return response == ACK_BYTE
    }
    
    /**
     * Send a complete result to the scoreboard
     */
    fun sendResult(performance: String, athleteId: Int, attempt: Int = 1) {
        if (socket == null || !socket!!.isConnected) {
            throw IllegalStateException("Not connected to scoreboard")
        }
        
        // Perform handshake
        if (!handshake()) {
            throw Exception("Handshake failed - scoreboard did not respond with ACK")
        }
        
        // Send performance message
        val perfMessage = createPerformanceMessage(performance, tag = 0x08)
        outputStream?.write(perfMessage)
        outputStream?.flush()
        
        // Send athlete message
        val athleteMessage = createAthleteMessage(athleteId, attempt, tag = 0x18)
        outputStream?.write(athleteMessage)
        outputStream?.flush()
    }
    
    /**
     * Send multiple results
     */
    fun sendResults(results: List<Result>) {
        var tag = 0x08.toByte()
        
        results.forEach { result ->
            if (!handshake()) {
                throw Exception("Handshake failed for result: $result")
            }
            
            val perfMessage = createPerformanceMessage(result.performance, tag)
            outputStream?.write(perfMessage)
            outputStream?.flush()
            
            val athleteMessage = createAthleteMessage(result.athleteId, result.attempt, (tag.toInt() + 0x10).toByte())
            outputStream?.write(athleteMessage)
            outputStream?.flush()
            
            // Increment tag by 0x10 for next message
            tag = ((tag.toInt() + 0x10) and 0xFF).toByte()
            if (tag == 0.toByte()) tag = 0x08
        }
    }
}

/**
 * Data class representing a scoreboard result
 */
data class Result(
    val performance: String,
    val athleteId: Int,
    val attempt: Int = 1
)

/**
 * Extension function to convert byte to unsigned int
 */
fun Byte.toUnsignedInt(): Int = this.toInt() and 0xFF

/**
 * Extension function to print byte array as hex
 */
fun ByteArray.toHexString(): String = 
    this.joinToString(" ") { "%02X".format(it) }

// ========== Usage Examples ==========

fun main() {
    // Example 1: Single result
    val scoreboard = DaktronicsScoreboard("127.0.0.1", 1950)
    
    try {
        scoreboard.connect()
        println("Connected to scoreboard")
        
        // Send single result
        scoreboard.sendResult("21.34", athleteId = 79, attempt = 1)
        println("Sent result: 21.34 for athlete 79")
        
    } catch (e: Exception) {
        println("Error: ${e.message}")
        e.printStackTrace()
    } finally {
        scoreboard.disconnect()
        println("Disconnected from scoreboard")
    }
    
    // Example 2: Multiple results
    val scoreboard2 = DaktronicsScoreboard()
    
    try {
        scoreboard2.connect()
        
        val results = listOf(
            Result("21.34", 79, 1),
            Result("50.00", 99, 1),
            Result("12.34", 5, 1)
        )
        
        scoreboard2.sendResults(results)
        println("Sent ${results.size} results")
        
    } catch (e: Exception) {
        println("Error: ${e.message}")
    } finally {
        scoreboard2.disconnect()
    }
}

// ========== Advanced Usage with Coroutines ==========

import kotlinx.coroutines.*
import java.io.IOException

class AsyncDaktronicsScoreboard(
    private val host: String = "127.0.0.1",
    private val port: Int = 1950
) {
    private val scoreboard = DaktronicsScoreboard(host, port)
    
    suspend fun sendResultAsync(performance: String, athleteId: Int, attempt: Int = 1) = 
        withContext(Dispatchers.IO) {
            scoreboard.sendResult(performance, athleteId, attempt)
        }
    
    suspend fun connectAsync() = withContext(Dispatchers.IO) {
        scoreboard.connect()
    }
    
    fun disconnect() {
        scoreboard.disconnect()
    }
}

// Coroutine usage example
suspend fun sendResultsAsync() = coroutineScope {
    val scoreboard = AsyncDaktronicsScoreboard()
    
    try {
        scoreboard.connectAsync()
        
        // Send results concurrently (with delay to avoid overwhelming the scoreboard)
        val jobs = listOf(
            async {
                delay(100)
                scoreboard.sendResultAsync("21.34", 79)
            },
            async {
                delay(200)
                scoreboard.sendResultAsync("50.00", 99)
            }
        )
        
        jobs.awaitAll()
        println("All results sent successfully")
        
    } catch (e: IOException) {
        println("Network error: ${e.message}")
    } finally {
        scoreboard.disconnect()
    }
}
```

---

## Tag/Command Byte Management

The tag byte (position 3) increments between messages:

- **Initial value:** `0x08` (with bit 3 set for mark messages)
- **Increment:** Add `0x10` for each subsequent message
- **Wrap-around:** When exceeding `0xFF`, wrap to `0x08`

Example sequence:
```
Message 1: 0x08
Message 2: 0x18
Message 3: 0x28
...
Message 15: 0xF8
Message 16: 0x08 (wraps around)
```

The lower nibble (bits 0-3) can contain command flags:
- Bit 3 (0x08): Often set for mark/result messages

---

## Display Formats

### Time Display
- **Format:** `  MM.SS ` or `  SS.HH ` (HH = hundredths)
- **Decimal:** Position byte 13 = `0x40`
- **Example:** 21.34 → `  21.34 `

### Distance Display  
- **Format:** ` MM.MM  ` or `MMM.MM `
- **Decimal:** Position byte 13 = `0x40`
- **Example:** 5.00m → `  5.00  `

### Athlete ID
- **Format:** `III    A` (3-digit ID, 3 spaces, 1-digit attempt)
- **Example:** Athlete 79, Attempt 1 → ` 79    1`

---

## Testing Procedure

### 1. Test Connection
```python
import socket

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.connect(('127.0.0.1', 1950))
print("Connected successfully")
sock.close()
```

### 2. Test Handshake
```python
sock.connect(('127.0.0.1', 1950))
sock.sendall(b'\x55')
response = sock.recv(1)
assert response == b'\x06', f"Expected ACK (0x06), got {response.hex()}"
print("Handshake successful")
```

### 3. Test Known Message
Send a verified working message:
```python
# Performance: 50.00, Athlete: 99, Attempt: 1
msg1 = bytes([0xAA, 0x16, 0x09, 0x58, 0x8A, 0x22, 0x00, 0x00, 
              0xED, 0xBF, 0xBF, 0xBF, 0x00, 0x40, 0x74])
msg2 = bytes([0x16, 0x09, 0x68, 0x7A, 0x22, 0xE7, 0x00, 0x00, 
              0x00, 0x00, 0x86, 0xE7, 0x00, 0x8A])

sock.sendall(b'\x55')
sock.recv(1)
sock.sendall(msg1)
sock.sendall(msg2)
```

### 4. Verify Display
After sending messages, verify the scoreboard displays:
- Performance time correctly
- Athlete ID correctly
- Decimal point in correct position

---

## Troubleshooting

### Connection Issues

| Problem | Solution |
|---------|----------|
| Cannot connect to IP | Verify IP address and port; check firewall settings |
| Connection refused | Ensure listener/converter is running on specified port |
| Connection timeout | Check network connectivity and TCP settings |

### Display Issues

| Problem | Cause | Solution |
|---------|-------|----------|
| No display | Handshake failed | Verify 0x55 → 0x06 handshake exchange |
| Wrong numbers | Incorrect 7-segment encoding | Verify encoding table usage |
| No decimal point | Punctuation byte wrong | Set byte 13 to 0x40 for decimal |
| Garbled display | Checksum error | Recalculate checksums correctly |
| Display flickers | Messages sent too fast | Add small delay between messages |

### Checksum Verification

To verify checksums are correct:
```python
def verify_message(msg):
    # Check checksum 1
    calc_cs1 = calculate_checksum(msg[0:4])
    assert msg[4] == calc_cs1, f"Checksum 1 mismatch: {msg[4]} != {calc_cs1}"
    
    # Check checksum 2
    calc_cs2 = calculate_checksum(msg[5:14])
    assert msg[14] == calc_cs2, f"Checksum 2 mismatch: {msg[14]} != {calc_cs2}"
    
    print("Checksums valid")
```

---

## Captured Message Examples

### Example: Performance 99.99, Athlete 79

**Message 1 (Performance):**
```
AA 16 09 98 4A 22 00 00 E7 E7 E7 E7 00 40 02
│  │  │  │  │  │  └─────────────────┘ │  └─ Checksum 2
│  │  │  │  │  │         │             └──── Decimal point
│  │  │  │  │  │         └────────────────── "  9999  " (99.99)
│  │  │  │  │  └──────────────────────────── Control (0x22)
│  │  │  │  └─────────────────────────────── Checksum 1
│  │  │  └────────────────────────────────── Tag
│  │  └───────────────────────────────────── Count (9)
│  └──────────────────────────────────────── Address (0x16)
└─────────────────────────────────────────── Sync (0xAA)
```

**Message 2 (Athlete ID):**
```
AA 16 09 D8 0A 22 E7 00 00 00 00 86 87 00 EA
│  │  │  │  │  │  └─────────────────┘ │  └─ Checksum 2
│  │  │  │  │  │         │             └──── No punctuation
│  │  │  │  │  │         └────────────────── "9    17" (ID: 79, Attempt: 1)
│  │  │  │  │  └──────────────────────────── Control (0x22)
│  │  │  │  └─────────────────────────────── Checksum 1
│  │  │  └────────────────────────────────── Tag (incremented)
│  │  └───────────────────────────────────── Count (9)
│  └──────────────────────────────────────── Address (0x16)
└─────────────────────────────────────────── Sync (0xAA)
```

---

## Reference Documents

- **Original Script:** FieldLynx Scoreboard Script for Daktronics TI-2009
- **Protocol Type:** Multidrop with 7-segment encoding
- **Copyright:** Lynx System Developers, Inc. (1995-2018)

---

## Version History

- **v1.0** - Initial implementation guide based on reverse-engineered protocol
- Documentation created from packet captures and script analysis

---

## Additional Notes

1. **Timing:** Some implementations add a small delay (10-50ms) between messages
2. **Multiple Athletes:** Send handshake once, then send message pairs for each athlete
3. **Error Handling:** Always verify handshake ACK before sending data
4. **Testing:** Use a TCP listener script to capture and verify messages during development

---

*This guide provides complete information for implementing Daktronics scoreboard communication. Test thoroughly with your specific hardware configuration.*