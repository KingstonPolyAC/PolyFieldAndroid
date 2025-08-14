# PolyField Android v3.0

A Jetpack Compose Android application for athletic field measurement and validation, built for the UK Athletics standards.

## Latest Release - v3.0

### Download Links
- **APK (Direct Install)**: `releases/v3.0/app-debug.apk`
- **AAB (Play Store)**: `releases/v3.0/app-debug.aab`

### Key Features
- **Dual EDM Reading Protocol**: 100ms delay between readings, 10s timeout per read (max 20s total)
- **Real Device Integration**: No simulation fallbacks in live mode
- **UKA Standards**: Shot, Discus, Hammer circles + 8m Javelin Arc
- **Material 3 Design**: Jetpack Compose UI with #1976D2 primary blue
- **Device Support**: EDM, Wind Gauge, Scoreboard via USB/Serial/Network

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

## Project Structure

```
app/src/main/java/com/polyfieldandroid/
├── MainActivityCompose.kt          # Main app with navigation
├── EDMModule.kt                    # Device communication wrapper
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

### Event Types
- **Throws**: Shot, Discus, Hammer, Javelin Arc (8m radius)
- **Horizontal Jumps**: Long Jump, Triple Jump (with wind gauge)

### Device Configuration
- **USB**: Android USB Host API integration
- **Serial**: Configurable baud rates, multiple ports
- **Network**: TCP/UDP socket communication

## Navigation Flow

### Throws (7 screens)
1. Event Selection → 2. Circle Selection → 3. Device Setup → 4. Centre Calibration → 5. Edge Verification → 6. Measurement → 7. Results

### Horizontal Jumps (3 screens)  
1. Event Selection → 2. Device Setup → 3. Measurement

## Error Handling
- **Timeout Errors**: "Could not find prism. Check your aim and remeasure. If EDM displays 'STOP' then press F1 to reset"
- **Connection Errors**: Device-specific error messages
- **Tolerance Errors**: Reading inconsistency notifications

## Permissions Required
- `android.permission.USB_PERMISSION`
- `android.hardware.usb.host` feature

## License
Copyright Kingston Polytechnic Athletic Club

## Support
For development questions or device integration issues, refer to the backup reference implementation in `backupUI/App.tsx`.