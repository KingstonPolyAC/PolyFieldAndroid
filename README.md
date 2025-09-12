# PolyField Android v4.5.1

A Jetpack Compose Android application for athletic field measurement and validation, built to UK Athletics and WA standards as of 2025.

## Latest Release - v4.5.1

### Download Links
- In Releases

### Key Features
- **Dual EDM Reading Protocol**: 100ms delay between readings, 10s timeout per read (max 20s total)
- **Real Device Integration**: No simulation fallbacks in live mode
- **UKA & WA Standards**: Shot, Discus, Hammer circles + 8m Javelin Arc with **correct UKA radius (1.0675m)**
- **Device Support**: EDM, Wind Gauge, Scoreboard via USB/Serial/Network
- **🎯 Sector Line Calibration**: Precise check mark positioning for accurate heat map plotting
- **📊 Advanced Heat Map**: Real-time visualization with calibrated sector lines and throw coordinates
- **⚙️ EDM Device Translator**: Multi-manufacturer support with Mato MTS-602R+ protocol implementation
- **💾 Calibration History**: Smart storage of last 2 calibrations with same-day reuse functionality

## Architecture

### Frontend (Android/Kotlin)
- **Jetpack Compose** with Material 3 design system
- **StateFlow** for reactive state management  
- **Coroutines** for async operations
- **USB/Serial permissions** handling

### Backend (Go Mobile)
- **Native Go module** (`mobile.aar`) for device communication
- **Cross-platform** EDM protocol implementation
- **Optimized timeouts** and error handling
- **Dual reading verification** with 3mm tolerance

### Device Translation Layer
- **EDM Device Registry**: Supports multiple manufacturers
- **Protocol Translators**: Mato MTS-602R+ implementation
- **Universal Go Mobile Interface**: Standardized measurement format

## Project Structure

```
app/src/main/java/com/polyfieldandroid/
├── MainActivityCompose.kt          # Main app with navigation & sector line calibration
├── EDMModule.kt                    # Device communication wrapper
├── EDMDeviceTranslator.kt          # Abstract device interface
├── MatoMTS602RTranslator.kt        # Mato MTS-602R+ protocol implementation
├── EDMCommunicationBridge.kt       # Device communication bridge
├── PolyFieldScreensExact.kt        # Core UI screens
├── PolyFieldCalibrationScreens.kt  # Calibration workflow
├── PolyFieldMeasurementScreen.kt   # Measurement interface
└── MainActivity.java               # Legacy activity

go-mobile/mobile/
├── mobile.go                       # Native device communication
├── go.mod                          # Go module dependencies
└── -> builds to: app/src/main/libs/mobile.aar
```

## Development Setup

### Prerequisites
- **Android Studio** Arctic Fox or later
- **Go 1.21+** with gomobile
- **Android SDK** with NDK
- **USB/Serial device access** for testing

### Build Steps

1. **Clone repository**
   ```bash
   git clone https://github.com/KingstonPolyAC/PolyFieldAndroid.git
   cd PolyFieldAndroid
   ```

2. **Build Go Mobile module**
   ```bash
   cd go-mobile
   gomobile bind -target=android -o ../app/src/main/libs/mobile.aar ./mobile
   ```

3. **Build Android app**
   ```bash
   ./gradlew assembleDebug      # APK
   ./gradlew bundleDebug        # AAB
   ```

### Testing
- **Demo Mode**: Simulated readings for UI testing
- **Live Mode**: Real device communication required
- **Device Types**: EDM, Wind Gauge, Scoreboard

## Technical Details

### EDM Communication Flow
1. **First Reading**: Send command, wait up to 10s for response
2. **100ms Delay**: Minimal delay between readings  
3. **Second Reading**: Send command, wait up to 10s for response
4. **Tolerance Check**: Verify readings within 3mm difference
5. **Average Result**: Return mean of both readings if valid

### Sector Line Calibration
1. **Check Mark Measurement**: Place prism on right-hand sector line
2. **Coordinate Calculation**: Uses UKA/WA standard angle (17.46°)
3. **Heat Map Integration**: Accurate sector line plotting from calibrated position
4. **Visual Indicators**: Green lines and check mark on heat map

### Calibration History System
1. **Automatic Storage**: Complete calibrations saved automatically after sector line measurement
2. **Daily Filter**: Only shows calibrations from current date
3. **Smart Selection**: Available at "Set Centre" screen with dropdown selection
4. **Complete Reuse**: Loads centre, edge verification, and sector line data
5. **Storage Limit**: Maintains last 2 calibrations to prevent storage bloat

### Event Types
- **Throws**: Shot, Discus, Hammer, Javelin Arc
- **Horizontal Jumps**: Long Jump, Triple Jump (with wind gauge)

### Device Configuration
- **USB**: Android USB Host API integration
- **Serial**: Configurable baud rates, multiple ports
- **Network**: TCP/UDP socket communication
- **Hands Off**: Auto detection of EDM device in serial mode and auto configuration to connect

### EDM Device Support
- **Mato MTS-602R+**: Full protocol implementation
  - Response format: "0008390 1001021 3080834 83"
  - Slope distance, vertical angle, horizontal angle parsing
  - Status code interpretation
- **Extensible**: Framework for adding other manufacturers

## Navigation Flow

### Throws (8 screens)
1. Event Selection → 2. Circle Selection → 3. Device Setup → 4. Centre Calibration → 5. Edge Verification → 6. Edge Results → **7. Sector Line Check Mark** → 8. Measurement → 9. Heat Map

### Horizontal Jumps (3 screens)  
1. Event Selection → 2. Device Setup → 3. Measurement

## Heat Map Features

### Visualization
- **Real Throw Data**: Uses actual measurement coordinates
- **Calibrated Sector Lines**: Green lines based on check mark measurement
- **Distance Arcs**: Concentric circles for distance reference
- **Statistics**: Total throws, average distance, best throw
- **Check Mark Indicator**: Shows calibrated sector line position

### Standards Compliance
- **UKA/WA Sector Angles**: 34.92° total sector (17.46° each side)
- **Accurate Radius**: 1.0675m for shot put and hammer
- **Precise Plotting**: Based on measured sector line coordinates

## Settings & Configuration

### Server Settings
- **Default Server**: 192.168.0.90:8080
- **Configurable**: IP address and port settings

### EDM Settings
- **Device Selection**: Choose EDM manufacturer
- **Reading Mode**: Single or Double read with tolerance checking
- **Default**: Mato MTS-602R+ with double read mode

## Error Handling
- **Timeout Errors**: "Could not find prism. Check your aim and remeasure. If EDM displays 'STOP' then press F1 to reset"
- **Connection Errors**: Device-specific error messages
- **Tolerance Errors**: Reading inconsistency notifications
- **Sector Line Errors**: Measurement validation and retry options

## Permissions Required
- `android.permission.USB_PERMISSION`
- `android.hardware.usb.host` feature

## Changelog

### v4.2 - **Latest** (Calibration History)

#### 🎯 New Features
- **📚 Calibration History System**: Smart storage and reuse of calibrations
  - Automatic saving of complete calibrations (centre + edge + sector line)
  - Same-day calibration selection at "Set Centre" screen
  - Stores last 2 calibrations with intelligent filtering
  - Complete state restoration (coordinates, measurements, verification data)
- **🔄 Historical Calibration Loading**: One-click reuse of previous calibrations
- **📅 Date-based Filtering**: Only shows calibrations from current day

#### 🔧 Technical Improvements
- **CalibrationRecord Data Model**: Comprehensive calibration storage structure
- **Enhanced State Management**: Historical calibration integration
- **UI Improvements**: Dropdown selection with completion status indicators

### v4.1 - (Sector Line Calibration)

#### 🎯 New Features
- **Sector Line Check Mark Screen**: Precise calibration for heat map accuracy
- **Enhanced Heat Map**: Calibrated sector lines with visual check mark indicator
- **EDM Device Translator Architecture**: Multi-manufacturer support framework
- **Mato MTS-602R+ Support**: Complete protocol implementation
- **Server Configuration**: IP address and port settings
- **Real Data Integration**: Heat map uses actual throw coordinates

#### 🐛 Bug Fixes
- **Corrected UKA Radius**: Fixed to 1.0675m (was 1.065m)
- **Heat Map Navigation**: Fixed back button and navigation flow
- **Live Mode Compliance**: Removed all simulation fallbacks

#### 🔧 Technical Improvements
- **Navigation Flow**: Complete 8-screen calibration workflow
- **State Management**: Enhanced calibration state with sector line data
- **Device Communication**: Improved USB device detection and connection
- **Error Handling**: Better error messages and recovery options

## License
Copyright Kingston Athletic club and Polytechnic Harriers 2025

## Important Note
**EDM connection and calculations are untested in this version**

## Support
web@kingstonandpoly.org