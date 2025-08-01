package mobile

import (
	"encoding/json"
	"math"
	"time"
)

// UKA Official Circle Radii
const (
	ShotRadius     = 1.0675
	DiscusRadius   = 1.250
	HammerRadius   = 1.0675
	JavelinRadius  = 8.000
)

// Circle types
const (
	CircleShot     = "SHOT"
	CircleDiscus   = "DISCUS"
	CircleHammer   = "HAMMER"
	CircleJavelin  = "JAVELIN_ARC"
)

// Basic functions
func HelloWorld() string {
	return "Hello from PolyField Go Mobile!"
}

func GetShotRadius() float64 {
	return ShotRadius
}

func GetDiscusRadius() float64 {
	return DiscusRadius
}

func GetHammerRadius() float64 {
	return HammerRadius
}

func GetJavelinRadius() float64 {
	return JavelinRadius
}

// GetCircleRadius returns the radius for a given circle type
func GetCircleRadius(circleType string) float64 {
	switch circleType {
	case CircleShot:
		return ShotRadius
	case CircleDiscus:
		return DiscusRadius
	case CircleHammer:
		return HammerRadius
	case CircleJavelin:
		return JavelinRadius
	default:
		return ShotRadius
	}
}

// CalculateDistance calculates the distance between two points
func CalculateDistance(x1, y1, x2, y2 float64) float64 {
	return math.Sqrt(math.Pow(x2-x1, 2) + math.Pow(y2-y1, 2))
}

// ValidateThrowCoordinates checks if throw coordinates are valid (outside circle)
func ValidateThrowCoordinates(x, y, circleRadius float64) bool {
	distance := math.Sqrt(x*x + y*y)
	return distance >= circleRadius
}

// CalculateCircleAccuracy returns accuracy information for calibration
func CalculateCircleAccuracy(measuredRadius, targetRadius float64) string {
	difference := math.Abs(measuredRadius - targetRadius)
	tolerance := 0.005 // 5mm in meters
	withinTolerance := difference <= tolerance

	result := map[string]interface{}{
		"withinTolerance": withinTolerance,
		"differenceInMm":  difference * 1000,
		"measuredRadius":  measuredRadius,
		"targetRadius":    targetRadius,
	}

	jsonData, _ := json.Marshal(result)
	return string(jsonData)
}

// SetCentre simulates setting the centre point for calibration
func SetCentre(circleType string) string {
	result := map[string]interface{}{
		"success":         true,
		"slopeDistanceMm": 8000 + (math.Sin(float64(time.Now().Unix()))*3000 + 3000),
		"vAzDecimal":      88.0 + (math.Cos(float64(time.Now().Unix()))*2.0 + 2.0),
		"hARDecimal":      math.Mod(float64(time.Now().Unix()), 360),
		"message":         "Centre point set successfully",
	}

	jsonData, _ := json.Marshal(result)
	return string(jsonData)
}

// VerifyEdge simulates edge verification for calibration
func VerifyEdge(circleType string, targetRadius float64) string {
	toleranceMm := 5.0
	if circleType == CircleJavelin {
		toleranceMm = 10.0
	}

	variation := (math.Sin(float64(time.Now().UnixNano())/1e9) * toleranceMm * 1.5)
	withinTolerance := math.Abs(variation) <= toleranceMm

	result := map[string]interface{}{
		"success":        true,
		"toleranceCheck": withinTolerance,
		"differenceMm":   variation,
		"measuredRadius": targetRadius + (variation / 1000),
		"message":        "Edge verification completed",
	}

	jsonData, _ := json.Marshal(result)
	return string(jsonData)
}

// MeasureThrow simulates measuring a throw distance
func MeasureThrow(circleType string) string {
	var distance float64

	baseTime := float64(time.Now().UnixNano()) / 1e9
	randomFactor := math.Sin(baseTime) * 0.5 + 0.5

	switch circleType {
	case CircleShot:
		distance = 8 + (randomFactor * 15)
	case CircleDiscus:
		distance = 25 + (randomFactor * 50)
	case CircleHammer:
		distance = 20 + (randomFactor * 65)
	case CircleJavelin:
		distance = 35 + (randomFactor * 60)
	default:
		distance = 15 + (randomFactor * 35)
	}

	x := (math.Cos(baseTime) * 10)
	y := distance + (math.Sin(baseTime*1.5) * 2)

	result := map[string]interface{}{
		"distance":  distance,
		"x":         x,
		"y":         y,
		"timestamp": time.Now().Format(time.RFC3339),
	}

	jsonData, _ := json.Marshal(result)
	return string(jsonData)
}

// MeasureWind simulates wind measurement
func MeasureWind() float64 {
	baseTime := float64(time.Now().UnixNano()) / 1e9
	windSpeed := math.Sin(baseTime/10) * 3.0
	return windSpeed
}

// ValidateCalibration checks if calibration is within acceptable limits
func ValidateCalibration(circleType string, measuredRadius float64) bool {
	targetRadius := GetCircleRadius(circleType)
	tolerance := 0.005
	if circleType == CircleJavelin {
		tolerance = 0.010
	}

	difference := math.Abs(measuredRadius - targetRadius)
	return difference <= tolerance
}

// GetToleranceForCircle returns the tolerance in mm for a given circle type
func GetToleranceForCircle(circleType string) float64 {
	if circleType == CircleJavelin {
		return 10.0
	}
	return 5.0
}
