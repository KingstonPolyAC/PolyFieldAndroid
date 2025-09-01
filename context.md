# PolyField Android v4.2 - Development Context

## CRITICAL RULES - NO EXCEPTIONS

### 1. **ABSOLUTE NO SIMULATION IN LIVE MODE**
- **RULE**: Never use fallback, simulation, or default values in live mode under any circumstances
- **VIOLATIONS**: Using `0.0`, `targetRadius`, `15.0`, or any hardcoded values as fallbacks when real EDM data is unavailable
- **CORRECT APPROACH**: Show error dialogs and refuse to proceed without real EDM measurements
- **EXAMPLES OF VIOLATIONS**:
  ```kotlin
  // WRONG - Uses fallback simulation
  val stationX = if (jsonResult.has("stationX")) jsonResult.getDouble("stationX") else 0.0
  
  // CORRECT - Refuses to proceed without real data
  if (!jsonResult.has("stationX")) {
      showErrorDialog("Centre Set Failed", "Cannot proceed without real EDM data.")
      return@launch
  }
  val stationX = jsonResult.getDouble("stationX")
  ```

### 2. **EDM DEVICE INTEGRATION REQUIREMENTS**
- **Target Device**: Mato MTS602R+ EDM via USB-to-serial (CH340 adapter, VID: 1A86, PID: 7523)
- **Default Mode**: Single read mode (not double read)
- **Communication Flow**: USB-serial → EDM device → Translation → Go Mobile trigonometry → Display
- **Real Device Testing**: User has tablet connected for testing, cannot use EDM while connected to laptop

### 3. **PRECISION STANDARDS**
- **UKA/WA Tolerance**: ±5mm for Shot/Hammer/Discus, ±10mm for Javelin Arc
- **Radius Display**: 4 decimal places (e.g., 1.0675m not 1.068m)
- **Target Radii**: Shot/Hammer: 1.0675m, Discus: 1.25m, Javelin Arc: 8.0m

## TECHNICAL ARCHITECTURE

### Go Mobile Integration
- **Purpose**: Performs trigonometric calculations for EDM measurements
- **Key Functions**: `setCentreWithGoMobile()`, `verifyEdgeWithGoMobile()`, `measureThrowWithGoMobile()`
- **Formula**: `horizontalDistance = slopeDistance × sin(verticalAngle)`
- **Integration**: EDMModule.kt handles Go Mobile communication and serial connections

### File Structure
```
app/src/main/java/com/polyfieldandroid/
├── MainActivityCompose.kt          # Main UI and measurement logic
├── EDMModule.kt                    # EDM device communication + Go Mobile
├── SerialCommunicationModule.kt   # USB-to-serial communication
├── PolyFieldCalibrationScreens.kt # Calibration UI screens
└── [device translators]/          # Device-specific protocol handlers
```

### Key Components

#### EDMModule.kt
- **Primary EDM communication**: Handles Mato MTS602R+ device integration
- **Go Mobile integration**: Uses trigonometric calculations instead of raw distance
- **Serial communication**: `getReliableEDMReading()` with double read support (100ms delay, 3mm tolerance)
- **CRITICAL**: All simulation fallbacks removed from live mode functions

#### MainActivityCompose.kt
- **Default settings**: `isDoubleReadMode: Boolean = false` (single read mode default)
- **Measurement functions**: Updated to use Go Mobile calculations
- **ERROR HANDLING**: Proper error dialogs instead of simulation fallbacks
- **FIXED**: All `return@withContext` changed to `return@launch` for viewModelScope blocks

#### SerialCommunicationModule.kt
- **USB-to-serial support**: CH340, FTDI, and other adapters
- **Device detection**: Automatic USB device enumeration
- **Communication**: Full request/response handling with timeouts
- **Mato MTS602R+ specific**: Enhanced response detection patterns

## CURRENT STATE & FIXES APPLIED

### Recently Fixed Issues
1. **Single vs Double Read Mode**: Fixed double reading logic with 100ms delay and 3mm tolerance checking
2. **Edge Verification Reset**: Fixed by letting Go Mobile handle entire measurement process internally
3. **Radius Display Format**: Changed from `"%.3f"` to `"%.4f"` to show 1.0675m correctly
4. **CRITICAL Simulation Violation**: Removed ALL fallback values (0.0, targetRadius, 15.0) in live mode
5. **Compilation Errors**: Fixed `return@withContext` to `return@launch` in viewModelScope blocks

### Current Status
- ✅ Compilation errors resolved
- ✅ All simulation violations in live mode eliminated
- ✅ Proper error handling implemented
- ✅ Default single read mode set
- ✅ Radius display format corrected
- 🔄 Ready for real device testing with Mato MTS602R+

## MEASUREMENT WORKFLOW

### Calibration Process
1. **Circle Selection**: Shot(1.0675m), Discus(1.25m), Hammer(1.0675m), Javelin Arc(8.0m)
2. **Set Centre**: Position EDM at centre (0,0), establish reference point
3. **Edge Verification**: Measure to circle edge, verify within UKA/WA tolerance
4. **Sector Line** (optional): Sector angle measurement for throw sectors
   - Shot/Discus/Hammer: 34.92° sector (17.46° each side from centre line)
   - Javelin: 28.96° sector (14.48° each side from centre line)

### Measurement Flow
```
User Action → EDM Reading → Serial Communication → Device Translation → Go Mobile Trigonometry → UI Display
```

### Error Handling (Live Mode)
- **No EDM Response**: Show error dialog, refuse to proceed
- **Invalid Data**: Show error dialog, refuse to proceed  
- **Missing Coordinates**: Show error dialog, refuse to proceed
- **Never**: Use simulation, fallback, or default values

## DEVELOPMENT GUIDELINES

### Code Standards
- **Android**: Jetpack Compose with Material 3 design
- **Language**: Kotlin with coroutines for async operations
- **Architecture**: MVVM pattern with StateFlow
- **Error Handling**: Comprehensive error dialogs, never fail silently

### Testing Requirements
- **Real Device**: Mato MTS602R+ EDM with CH340 USB-to-serial adapter
- **Verification**: Run lint and typecheck commands after significant changes
- **Commands**: Check README or search codebase for testing approach

### File Conventions
- **Precision**: Always use 4 decimal places for radius display
- **Logging**: Use `android.util.Log.d("PolyField", message)` for debugging
- **Comments**: Avoid unless specifically requested
- **Naming**: Follow existing codebase patterns

## SESSION CONTINUATION PROTOCOL

When starting new development sessions:
1. **Read this context.md file** to understand all rules and current state
2. **Verify no simulation violations** remain in live mode code
3. **Check compilation** with `./gradlew compileDebugKotlin`
4. **Test with real device** when making EDM-related changes
5. **Follow TodoWrite tool** for task management and progress tracking

## HARDWARE SPECIFICATIONS

### EDM Device: Mato MTS602R+
- **Connection**: USB-to-serial via CH340 adapter
- **VID/PID**: 0x1A86/0x7523
- **Communication**: Serial protocol with custom response patterns
- **Precision**: Millimeter-level accuracy for athletic field measurements

### USB-to-Serial Support
- **Primary**: CH340 (for Mato EDM)
- **Secondary**: FTDI, Prolific PL2303, Silicon Labs CP2102
- **Configuration**: 9600 baud, 8 data bits, 1 stop bit, no parity
- **Permissions**: USB device permissions required

## ATHLETIC FIELD STANDARDS

### UKA/WA Specifications
- **Shot Put Circle**: 1.0675m radius, ±5mm tolerance
- **Discus Circle**: 1.25m radius, ±5mm tolerance  
- **Hammer Circle**: 1.0675m radius, ±5mm tolerance
- **Javelin Arc**: 8.0m radius, ±10mm tolerance
- **Sector Lines**: 
  - Shot/Discus/Hammer: 34.92° total sector (17.46° each side from centre)
  - Javelin: 28.96° total sector (14.48° each side from centre)

### Coordinate System
- **Centre**: (0,0) reference point established by EDM
- **Origin**: EDM station position becomes coordinate origin
- **Measurements**: All distances calculated from centre point
- **Heat Map**: Real coordinate plotting for throw visualization

---

**Last Updated**: 2025-08-23  
**Version**: v4.2  
**Status**: Ready for real device testing  
**Critical**: NO SIMULATION IN LIVE MODE - this rule is non-negotiable