# PolyField API Enhancement: Circle & Landing Coordinates

## Overview

This document describes the enhancement to the PolyField API to transmit complete calibration geometry along with throw landing coordinates, enabling server-side reconstruction of the complete field setup and measurement context.

## Problem Statement

Currently, the API transmits:
- ✅ Landing coordinates (x, y) relative to circle center
- ✅ Distance measurements
- ❌ **Missing**: Circle geometry and EDM position
- ❌ **Missing**: Sector line positions

Without calibration metadata, the server cannot:
- Reconstruct the actual field layout
- Visualize EDM position relative to circle
- Render accurate sector lines
- Validate measurements against UKA/WA standards
- Perform independent quality control checks

## Proposed Solution

### New Data Structures

#### 1. CalibrationMetadata
```kotlin
data class CalibrationMetadata(
    val circleType: String,              // "SHOT", "DISCUS", "HAMMER", "JAVELIN_ARC"
    val circleRadius: Double,            // Circle radius in meters (e.g., 1.0675 for shot)
    val edmPosition: Coordinate,         // EDM station position (x, y) relative to circle center
    val sectorLines: SectorLines? = null,// Sector line positions (null for javelin)
    val timestamp: String,               // ISO 8601 timestamp of calibration
    val calibrationId: String? = null    // Unique ID for calibration session
)
```

#### 2. Coordinate
```kotlin
data class Coordinate(
    val x: Double,  // East-West axis in meters (+ = East, - = West)
    val y: Double   // North-South axis in meters (+ = North, - = South)
)
```

#### 3. SectorLines
```kotlin
data class SectorLines(
    val rightLine: Coordinate,           // Right sector line endpoint (measured)
    val leftLine: Coordinate,            // Left sector line endpoint (calculated)
    val sectorAngle: Double = 34.92      // Sector angle in degrees (UKA/WA standard)
)
```

### Updated ResultPayload

```kotlin
data class ResultPayload(
    val eventId: String,
    val athleteBib: String,
    val series: List<Performance>,
    val heatmapCoordinates: List<HeatmapCoordinate>? = null,
    val calibrationMetadata: CalibrationMetadata? = null  // NEW
)
```

## Coordinate System

All coordinates use a **2D Cartesian system** with origin at circle center:

```
           +Y (North)
              ↑
              |
              |
  -X (West) ←-●-→ +X (East)    ● = Circle Center (0, 0)
              |
              |
              ↓
           -Y (South)
```

### Key Points:
- **Origin (0, 0)**: Circle center
- **Units**: Meters
- **EDM Position**: Negative coordinates typically (e.g., x=-5.1, y=6.5)
- **Throws**: Positive Y direction (into the sector)
- **Sector Lines**: Define legal throwing area

## Example JSON Payload

```json
{
  "eventId": "shot-put-men",
  "athleteBib": "123",
  "series": [
    {
      "attempt": 1,
      "mark": "15.23",
      "unit": "m",
      "valid": true,
      "coordinates": {
        "x": 2.34,
        "y": 15.12,
        "distance": 15.23,
        "round": 1,
        "attempt": 1,
        "valid": true
      }
    }
  ],
  "heatmapCoordinates": [
    {
      "x": 2.34,
      "y": 15.12,
      "distance": 15.23,
      "round": 1,
      "attempt": 1,
      "valid": true
    },
    {
      "x": -0.89,
      "y": 14.98,
      "distance": 14.98,
      "round": 1,
      "attempt": 2,
      "valid": true
    }
  ],
  "calibrationMetadata": {
    "circleType": "SHOT",
    "circleRadius": 1.0675,
    "edmPosition": {
      "x": -5.100402687628842,
      "y": 6.494792357643899
    },
    "sectorLines": {
      "rightLine": {
        "x": 6.45,
        "y": 18.23
      },
      "leftLine": {
        "x": -5.12,
        "y": 19.87
      },
      "sectorAngle": 34.92
    },
    "timestamp": "2025-10-05T14:23:45Z",
    "calibrationId": "cal-20251005-142345"
  }
}
```

## Server-Side Capabilities

With this enhanced data, servers can:

### 1. **Reconstruct Complete Field Layout**
- Draw circle at origin with correct radius
- Plot EDM position
- Render sector lines
- Visualize throw landing points

### 2. **Validate Measurements**
- Verify throws are within sector (foul detection)
- Check distances match coordinate calculations
- Validate calibration quality (EDM position geometry)

### 3. **Quality Control**
- Compare EDM position across events
- Detect calibration drift
- Identify measurement anomalies
- Generate calibration reports

### 4. **Advanced Analytics**
- Athlete consistency analysis (lateral deviation)
- Sector usage patterns
- Optimal release angles
- Wind impact correlation

### 5. **Visualization**
- Generate competition heatmaps
- Show multiple athletes on same field
- Animate throw progression
- Compare sessions over time

## Implementation Notes

### Data Collection
1. Calibration metadata collected during setup phase:
   - Set circle center → captures EDM position
   - Verify edge → validates circle radius
   - Check sector line → records sector geometry

2. Metadata attached to first result submission
3. Shared across all athletes in the same event
4. Updated if recalibration occurs

### Backwards Compatibility
- `calibrationMetadata` field is optional (nullable)
- Existing clients continue to work without modification
- Servers can detect presence of enhanced data

### Data Size
- Metadata adds ~200-300 bytes per result payload
- Typically sent once per event (shared across athletes)
- Negligible impact on network traffic

## Migration Path

### Phase 1: Client Update (Current)
- ✅ Add new data structures to API client
- ✅ Update result payload to include metadata
- ✅ Populate metadata from calibration state

### Phase 2: Server Update
- Update server API to accept new fields
- Store calibration metadata with results
- Implement validation endpoints

### Phase 3: Enhanced Features
- Server-side heatmap generation
- Automated foul validation
- Calibration quality reports
- Multi-event analytics

## Benefits

### For Competition Officials
- ✅ Complete audit trail of field setup
- ✅ Validation of measurement accuracy
- ✅ Detection of equipment issues
- ✅ Compliance verification (UKA/WA standards)

### For Athletes & Coaches
- ✅ Detailed throw analysis
- ✅ Consistency metrics
- ✅ Technique optimization data
- ✅ Session comparison

### For Developers
- ✅ Complete data model for visualization
- ✅ Independent calculation verification
- ✅ Standardized coordinate system
- ✅ Future-proof extensibility

## Technical Specifications

### Precision
- Coordinates: 6 decimal places (~1mm accuracy)
- Distances: 2 decimal places (centimeter accuracy)
- Angles: 2 decimal places (0.01° accuracy)

### Standards Compliance
- **UKA/WA Sector Angle**: 34.92°
- **Circle Radii**: Official UKA standards
  - Shot Put: 1.0675m (2.135m diameter)
  - Discus: 1.250m (2.5m diameter)
  - Hammer: 1.0675m (2.135m diameter)
  - Javelin Arc: 8.000m radius

### Coordinate Precision
- EDM measurements: mm precision
- Calculated coordinates: Sub-millimeter precision
- Reported coordinates: 6 decimal places (~micrometers)

## Example Use Cases

### 1. Server-Side Heatmap
```python
# Server can reconstruct exact field layout
circle = Circle(center=(0, 0), radius=metadata.circleRadius)
edm = Point(metadata.edmPosition.x, metadata.edmPosition.y)
sector = Sector(
    right=metadata.sectorLines.rightLine,
    left=metadata.sectorLines.leftLine
)

# Plot all throws
for coord in heatmapCoordinates:
    plot_throw(coord.x, coord.y, color=coord.valid ? 'green' : 'red')
```

### 2. Foul Validation
```python
# Server can independently validate fouls
def is_in_sector(throw, sector_lines):
    angle_to_throw = atan2(throw.y, throw.x)
    angle_to_right = atan2(sector_lines.rightLine.y, sector_lines.rightLine.x)
    angle_to_left = atan2(sector_lines.leftLine.y, sector_lines.leftLine.x)
    return angle_to_right <= angle_to_throw <= angle_to_left

# Check all throws
for throw in throws:
    if not is_in_sector(throw, calibrationMetadata.sectorLines):
        mark_as_foul(throw)
```

### 3. Calibration Quality
```python
# Verify EDM position is reasonable
edm_distance = sqrt(edm.x^2 + edm.y^2)
if edm_distance < 3.0 or edm_distance > 15.0:
    flag_calibration_warning("EDM too close/far from circle")

# Check sector line geometry
sector_width = distance(sector.leftLine, sector.rightLine)
expected_width = 2 * tan(sector_angle/2) * sector_distance
if abs(sector_width - expected_width) > 0.1:
    flag_calibration_warning("Sector geometry inconsistent")
```

## Conclusion

This API enhancement provides complete spatial context for throw measurements, enabling:
- ✅ Server-side reconstruction of field layout
- ✅ Independent measurement validation
- ✅ Advanced analytics and visualization
- ✅ Quality control and compliance verification

The enhancement is backwards compatible and adds minimal overhead while significantly increasing data utility.
