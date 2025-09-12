# PolyField Control Server API Documentation

## Overview

The PolyField Control Server is a Wails-based desktop application built with Go (backend) and React (frontend) that provides control and measurement functionality for athletic field equipment including Electronic Distance Measurement (EDM) devices, wind gauges, and scoreboards.

## Architecture

- **Framework**: Wails v2 (Go + React)
- **Backend**: Go 1.19+
- **Frontend**: React + Vite
- **Communication**: Wails IPC binding between Go backend and React frontend

## API Endpoints

All API endpoints are exposed as Go methods bound to the Wails frontend through the IPC system. These methods are automatically available in the frontend TypeScript environment.

## Event and Competition Management

The PolyField Control Server operates in two modes:
1. **Standalone Mode**: Direct hardware control and measurement
2. **Event Mode**: Connected to external competition management server for event data and result submission

### Server Connection

#### `SetServerAddress(ip: string, port: number)`
Sets the server address for event mode operations.

**Parameters**:
- `ip` (string): IP address of the competition management server
- `port` (number): Port number

**Returns**: 
- `Promise<void>`

**Example**:
```javascript
await SetServerAddress("192.168.1.100", 8080);
```

### Event Management

#### `FetchEvents(ip: string, port: number)`
Retrieves list of available events from the competition management server.

**Parameters**:
- `ip` (string): Server IP address
- `port` (number): Server port

**Returns**: 
- `Promise<Event[]>` - Array of available events

**HTTP Endpoint Called**: `GET http://{server}/api/v1/events`

**Data Structure - Event**:
```typescript
{
  id: string;           // Unique event identifier
  name: string;         // Event name (e.g., "Men's Shot Put")
  type: string;         // Event type (e.g., "throws")
  rules?: EventRules;   // Competition rules (optional)
  athletes?: Athlete[]; // List of competitors (optional)
}
```

**Data Structure - EventRules**:
```typescript
{
  attempts: number;           // Number of attempts per athlete
  cutEnabled: boolean;        // Whether qualifying cut is enabled
  cutQualifiers: number;      // Number of qualifiers if cut enabled
  reorderAfterCut: boolean;   // Whether to reorder after cut
}
```

**Data Structure - Athlete**:
```typescript
{
  bib: string;    // Athlete's bib number
  order: number;  // Competition order
  name: string;   // Athlete name
  club: string;   // Club/team affiliation
}
```

**Example**:
```javascript
const events = await FetchEvents("192.168.1.100", 8080);
// Returns: [
//   {
//     id: "event_1",
//     name: "Men's Shot Put",
//     type: "throws",
//     rules: { attempts: 3, cutEnabled: true, cutQualifiers: 8, reorderAfterCut: true }
//   }
// ]
```

#### `FetchEventDetails(ip: string, port: number, eventId: string)`
Retrieves detailed information for a specific event including athlete list.

**Parameters**:
- `ip` (string): Server IP address
- `port` (number): Server port
- `eventId` (string): Event identifier

**Returns**: 
- `Promise<Event>` - Complete event details with athlete list

**HTTP Endpoint Called**: `GET http://{server}/api/v1/events/{eventId}`

**Example**:
```javascript
const eventDetails = await FetchEventDetails("192.168.1.100", 8080, "event_1");
// Returns: {
//   id: "event_1",
//   name: "Men's Shot Put",
//   type: "throws",
//   rules: { attempts: 3, cutEnabled: true, cutQualifiers: 8, reorderAfterCut: true },
//   athletes: [
//     { bib: "101", order: 1, name: "John Smith", club: "Athletics Club" },
//     { bib: "102", order: 2, name: "Mike Johnson", club: "Track Team" }
//   ]
// }
```

### Results Management

#### `PostResult(ip: string, port: number, payload: ResultPayload)`
Submits measurement results to the competition management server. Automatically caches results if server is unreachable and retries every 2 minutes.

**Parameters**:
- `ip` (string): Server IP address
- `port` (number): Server port
- `payload` (ResultPayload): Result data to submit

**Returns**: 
- `Promise<void>` - Resolves on successful submission or caching

**HTTP Endpoint Called**: `POST http://{server}/api/v1/results`

**Data Structure - ResultPayload**:
```typescript
{
  eventId: string;          // Event identifier
  athleteBib: string;       // Athlete's bib number
  series: Performance[];    // Array of performance attempts
}
```

**Data Structure - Performance**:
```typescript
{
  attempt: number;     // Attempt number (1, 2, 3, etc.)
  mark: string;        // Performance result (e.g., "15.25")
  unit: string;        // Unit of measurement (e.g., "m")
  wind?: string;       // Wind reading (optional, e.g., "+1.2")
  valid: boolean;      // Whether the attempt was valid
}
```

**Example**:
```javascript
const resultPayload = {
  eventId: "event_1",
  athleteBib: "101",
  series: [
    { attempt: 1, mark: "15.25", unit: "m", wind: "+1.2", valid: true },
    { attempt: 2, mark: "15.87", unit: "m", wind: "+0.8", valid: true },
    { attempt: 3, mark: "FOUL", unit: "m", wind: "+1.5", valid: false }
  ]
};

await PostResult("192.168.1.100", 8080, resultPayload);
```

**Caching Behavior**:
- Results are automatically cached locally if server is unreachable
- Cache file location: `{UserCacheDir}/polyfield/polyfield_results_cache.json`
- Automatic retry every 2 minutes until successful submission
- Cache persists between application sessions

### Device Management

#### `ListSerialPorts()`
Lists all available serial ports on the system.

**Parameters**: None

**Returns**: 
- `Promise<string[]>` - Array of available serial port names

**Example**:
```javascript
const ports = await ListSerialPorts();
// Returns: ["/dev/ttyUSB0", "/dev/ttyUSB1", "COM1", "COM2"]
```

#### `ConnectSerialDevice(deviceType: string, portName: string)`
Connects to a serial device.

**Parameters**:
- `deviceType` (string): Type of device ("edm", "wind", "scoreboard")
- `portName` (string): Serial port name (e.g., "COM1", "/dev/ttyUSB0")

**Returns**: 
- `Promise<string>` - Success message

**Supported Device Types**:
- `"edm"` - Electronic Distance Measurement device
- `"wind"` - Wind gauge device
- `"scoreboard"` - Display scoreboard

**Example**:
```javascript
const result = await ConnectSerialDevice("edm", "COM1");
// Returns: "Connected to edm on COM1"
```

#### `ConnectNetworkDevice(deviceType: string, ipAddress: string, port: number)`
Connects to a network-based device.

**Parameters**:
- `deviceType` (string): Type of device ("edm", "wind", "scoreboard")
- `ipAddress` (string): IP address of the device
- `port` (number): Port number

**Returns**: 
- `Promise<string>` - Success message

**Example**:
```javascript
const result = await ConnectNetworkDevice("edm", "192.168.1.100", 8080);
// Returns: "Connected to edm at 192.168.1.100:8080"
```

#### `DisconnectDevice(deviceType: string)`
Disconnects a device.

**Parameters**:
- `deviceType` (string): Type of device to disconnect

**Returns**: 
- `Promise<string>` - Success message

**Example**:
```javascript
const result = await DisconnectDevice("edm");
// Returns: "Disconnected edm"
```

### EDM (Electronic Distance Measurement) Operations

#### `GetReliableEDMReading(deviceType: string)`
Performs two consecutive EDM readings and returns the average if they're within tolerance.

**Parameters**:
- `deviceType` (string): EDM device identifier

**Returns**: 
- `Promise<AveragedEDMReading>` - Averaged measurement data

**Data Structure - AveragedEDMReading**:
```typescript
{
  slopeDistanceMm: number;    // Slope distance in millimeters
  vAzDecimal: number;         // Vertical angle in decimal degrees
  harDecimal: number;         // Horizontal angle in decimal degrees
}
```

**Example**:
```javascript
const reading = await GetReliableEDMReading("edm");
// Returns: { slopeDistanceMm: 15240.5, vAzDecimal: 92.15, harDecimal: 45.30 }
```

### Calibration Management

#### `GetCalibration(deviceType: string)`
Retrieves calibration data for a device.

**Parameters**:
- `deviceType` (string): Device identifier

**Returns**: 
- `Promise<EDMCalibrationData>` - Current calibration data

**Data Structure - EDMCalibrationData**:
```typescript
{
  deviceId: string;                              // Device identifier
  timestamp: Date;                               // Calibration timestamp
  selectedCircleType: string;                    // Circle type ("SHOT", "DISCUS", "HAMMER", "JAVELIN_ARC")
  targetRadius: number;                          // Target circle radius in meters
  stationCoordinates: EDMPoint;                  // Station position coordinates
  isCentreSet: boolean;                         // Whether center has been calibrated
  edgeVerificationResult?: EdgeVerificationResult; // Edge verification results (optional)
}
```

**Data Structure - EDMPoint**:
```typescript
{
  x: number;  // X coordinate in meters
  y: number;  // Y coordinate in meters
}
```

**Data Structure - EdgeVerificationResult**:
```typescript
{
  measuredRadius: number;      // Measured radius in meters
  differenceMm: number;        // Difference from target in millimeters
  isInTolerance: boolean;      // Whether measurement is within tolerance
  toleranceAppliedMm: number;  // Applied tolerance value in millimeters
}
```

#### `SaveCalibration(deviceType: string, data: EDMCalibrationData)`
Saves calibration data for a device.

**Parameters**:
- `deviceType` (string): Device identifier
- `data` (EDMCalibrationData): Calibration data to save

**Returns**: 
- `Promise<void>`

#### `ResetCalibration(deviceType: string)`
Resets calibration data for a device.

**Parameters**:
- `deviceType` (string): Device identifier

**Returns**: 
- `Promise<void>`

#### `SetCircleCentre(deviceType: string)`
Sets the circle center using current EDM reading position.

**Parameters**:
- `deviceType` (string): EDM device identifier

**Returns**: 
- `Promise<EDMCalibrationData>` - Updated calibration data

**Example**:
```javascript
const calibration = await SetCircleCentre("edm");
// Returns updated calibration data with center coordinates set
```

#### `VerifyCircleEdge(deviceType: string)`
Verifies the circle edge measurement against the target radius.

**Parameters**:
- `deviceType` (string): EDM device identifier

**Returns**: 
- `Promise<EDMCalibrationData>` - Updated calibration data with verification results

**Prerequisites**: Circle center must be set first using `SetCircleCentre()`

### Measurement Operations

#### `MeasureThrow(deviceType: string)`
Measures throw distance from the current position to the circle edge.

**Parameters**:
- `deviceType` (string): EDM device identifier

**Returns**: 
- `Promise<string>` - Formatted throw distance (e.g., "45.67 m")

**Prerequisites**: Device must be calibrated with center and edge verification

**Example**:
```javascript
const throwDistance = await MeasureThrow("edm");
// Returns: "45.67 m"
```

### Wind Measurement

#### `StartWindListener(deviceType: string, context: Context)`
Starts continuous wind data collection from the wind gauge.

**Parameters**:
- `deviceType` (string): Wind device identifier
- `context` (Context): Cancellation context

**Returns**: 
- `Promise<void>`

**Note**: This method is typically called automatically when connecting a wind device.

#### `MeasureWind(deviceType: string)`
Gets the average wind reading from the last 5 seconds of data.

**Parameters**:
- `deviceType` (string): Wind device identifier

**Returns**: 
- `Promise<string>` - Formatted wind speed (e.g., "+1.2 m/s", "-0.8 m/s")

**Example**:
```javascript
const windSpeed = await MeasureWind("wind");
// Returns: "+1.2 m/s"
```

### Scoreboard Operations

#### `SendToScoreboard(value: string)`
Sends a value to the connected scoreboard for display.

**Parameters**:
- `value` (string): Value to display on scoreboard

**Returns**: 
- `Promise<void>`

**Example**:
```javascript
await SendToScoreboard("45.67");
// Displays "45.67" on the scoreboard
```

### System Operations

#### `SetDemoMode(enabled: boolean)`
Enables or disables demo mode for testing without physical devices.

**Parameters**:
- `enabled` (boolean): Whether to enable demo mode

**Returns**: 
- `Promise<void>`

**Demo Mode Behavior**:
- EDM readings return random values within realistic ranges
- Wind readings return random values between -2.0 and +2.0 m/s
- Scoreboard commands are logged instead of sent to hardware

## Constants and Configuration

### Circle Types and Radii (UKA Standards)
- **SHOT**: 1.0675 meters
- **DISCUS**: 1.250 meters  
- **HAMMER**: 1.0675 meters
- **JAVELIN_ARC**: 8.000 meters

### Tolerances
- **Throws Circle Tolerance**: ±5.0 mm
- **Javelin Arc Tolerance**: ±10.0 mm
- **EDM Reading Tolerance**: ±3.0 mm between consecutive readings

### Timeouts and Intervals
- **EDM Read Timeout**: 10 seconds
- **Delay Between EDM Reads**: 250 milliseconds
- **Wind Buffer Duration**: ~2 minutes (120 readings at 1/second)
- **Wind Measurement Window**: 5 seconds

## Error Handling

All API methods return Promise objects that can be handled with standard JavaScript error handling:

```javascript
try {
  const result = await ConnectSerialDevice("edm", "COM1");
  console.log(result);
} catch (error) {
  console.error("Connection failed:", error.message);
}
```

Common error scenarios:
- Device not connected when attempting operations
- Serial port access issues
- Network connectivity problems
- EDM readings outside tolerance
- Missing calibration data

## Communication Protocols

### EDM Device Protocol
- **Command**: `0x11, 0x0D, 0x0A` (hexadecimal)
- **Response Format**: Space-separated values: `[SlopeDistance] [VAz] [HAR] [Additional]`
- **Angle Format**: DDDMMSS (degrees, minutes, seconds)

### Wind Gauge Protocol
- **Response Format**: Comma-separated values with wind speed in second field
- **Speed Format**: Decimal number with + or - prefix
- **Units**: meters per second

### Scoreboard Protocol
- **Command Format**: Text value followed by `\r\n`
- **Display**: Direct value display on scoreboard

## Usage Examples

### Complete Event Mode Workflow
```javascript
// 1. Connect to competition server
await SetServerAddress("192.168.1.100", 8080);

// 2. Fetch available events
const events = await FetchEvents("192.168.1.100", 8080);

// 3. Get event details with athlete list
const eventDetails = await FetchEventDetails("192.168.1.100", 8080, "event_1");

// 4. Set up EDM for measurements
await ConnectSerialDevice("edm", "COM1");
await SetCircleCentre("edm");
await VerifyCircleEdge("edm");

// 5. Connect wind gauge for wind readings
await ConnectSerialDevice("wind", "COM2");

// 6. Measure throws for each athlete
for (const athlete of eventDetails.athletes) {
  const performances = [];
  
  for (let attempt = 1; attempt <= eventDetails.rules.attempts; attempt++) {
    // Take measurement
    const throwDistance = await MeasureThrow("edm");
    const windSpeed = await MeasureWind("wind");
    
    performances.push({
      attempt,
      mark: throwDistance.replace(" m", ""),
      unit: "m",
      wind: windSpeed.replace(" m/s", ""),
      valid: true
    });
  }
  
  // Submit results (automatically cached if server unreachable)
  await PostResult("192.168.1.100", 8080, {
    eventId: eventDetails.id,
    athleteBib: athlete.bib,
    series: performances
  });
}
```

### Complete EDM Workflow (Standalone Mode)
```javascript
// 1. Connect to EDM device
await ConnectSerialDevice("edm", "COM1");

// 2. Set calibration parameters
const calibration = await GetCalibration("edm");
calibration.selectedCircleType = "SHOT";
await SaveCalibration("edm", calibration);

// 3. Set circle center
await SetCircleCentre("edm");

// 4. Verify circle edge
await VerifyCircleEdge("edm");

// 5. Measure throw
const throwDistance = await MeasureThrow("edm");
console.log("Throw distance:", throwDistance);
```

### Wind Measurement Workflow
```javascript
// 1. Connect wind gauge
await ConnectSerialDevice("wind", "COM2");

// 2. Wait for data collection (automatic)
await new Promise(resolve => setTimeout(resolve, 6000));

// 3. Measure wind
const windSpeed = await MeasureWind("wind");
console.log("Wind speed:", windSpeed);
```

### Scoreboard Integration
```javascript
// Connect scoreboard
await ConnectNetworkDevice("scoreboard", "192.168.1.50", 8080);

// Display throw result
await SendToScoreboard("45.67");

// Display wind reading  
await SendToScoreboard("+1.2 m/s");
```