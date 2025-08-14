package mobile

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"math/rand"
	"net"
	"strconv"
	"strings"
	"sync"
	"time"

	"go.bug.st/serial"
)

// --- Constants ---
var edmReadCommand = []byte{0x11, 0x0d, 0x0a}

const (
	sdToleranceMm           = 3.0
	delayBetweenReadsInPair = 100 * time.Millisecond // Reduced from 250ms to 100ms
	edmReadTimeout          = 10 * time.Second       // Overall timeout remains 10s
	edmSingleReadTimeout    = 10 * time.Second       // Individual read timeout - 10s per read
	windBufferSize          = 120                    // Approx 2 minutes of data at 1 reading/sec
)

// UKA Official Circle Radii (as per methodology guide)
const (
	UkaRadiusShot       = 1.0675 // Shot put circle radius (meters)
	UkaRadiusDiscus     = 1.250  // Discus circle radius (meters)
	UkaRadiusHammer     = 1.0675 // Hammer circle radius (meters)
	UkaRadiusJavelinArc = 8.000  // Javelin arc radius (meters)
)

// Circle types
const (
	CircleShot     = "SHOT"
	CircleDiscus   = "DISCUS"
	CircleHammer   = "HAMMER"
	CircleJavelin  = "JAVELIN_ARC"
)

// Tolerance constants
const (
	ToleranceThrowsCircleMm = 5.0  // Standard tolerance for throws circles
	ToleranceJavelinMm      = 10.0 // Tolerance for javelin arc
)

// Demo mode delays
const (
	CENTRE_DELAY = 2000 * time.Millisecond
	EDGE_DELAY   = 2000 * time.Millisecond
	THROW_DELAY  = 1500 * time.Millisecond
)

// --- Data Structures ---
type Device struct {
	Conn           io.ReadWriteCloser
	ConnectionType string
	Address        string
	cancelListener context.CancelFunc
}

type WindReading struct {
	Value     float64
	Timestamp time.Time
}

type EDMPoint struct {
	X float64 `json:"x"`
	Y float64 `json:"y"`
}

type AveragedEDMReading struct {
	SlopeDistanceMm float64 `json:"slopeDistanceMm"`
	VAzDecimal      float64 `json:"vAzDecimal"`
	HARDecimal      float64 `json:"harDecimal"`
}

type EdgeVerificationResult struct {
	MeasuredRadius     float64 `json:"measuredRadius"`
	DifferenceMm       float64 `json:"differenceMm"`
	IsInTolerance      bool    `json:"isInTolerance"`
	ToleranceAppliedMm float64 `json:"toleranceAppliedMm"`
}

type EDMCalibrationData struct {
	DeviceID               string                  `json:"deviceId"`
	Timestamp              time.Time               `json:"timestamp"`
	SelectedCircleType     string                  `json:"selectedCircleType"`
	TargetRadius           float64                 `json:"targetRadius"`
	StationCoordinates     EDMPoint                `json:"stationCoordinates"`
	IsCentreSet            bool                    `json:"isCentreSet"`
	EdgeVerificationResult *EdgeVerificationResult `json:"edgeVerificationResult,omitempty"`
}

type ParsedEDMReading struct {
	SlopeDistanceMm float64
	VAzDecimal      float64
	HARDecimal      float64
}

type ThrowCoordinate struct {
	X                float64   `json:"x"`                // X coordinate (metres from centre)
	Y                float64   `json:"y"`                // Y coordinate (metres from centre)
	Distance         float64   `json:"distance"`         // Calculated throw distance
	CircleType       string    `json:"circleType"`       // SHOT, DISCUS, HAMMER, JAVELIN_ARC
	Timestamp        time.Time `json:"timestamp"`        // When the throw was measured
	AthleteID        string    `json:"athleteId"`        // Optional athlete identifier
	CompetitionRound string    `json:"competitionRound"` // Optional round/session identifier
	EDMReading       string    `json:"edmReading"`       // Raw EDM reading for reference
}

type SessionStatistics struct {
	TotalThrows     int     `json:"totalThrows"`
	AverageX        float64 `json:"averageX"`
	AverageY        float64 `json:"averageY"`
	MaxDistance     float64 `json:"maxDistance"`
	MinDistance     float64 `json:"minDistance"`
	AverageDistance float64 `json:"averageDistance"`
	SpreadRadius    float64 `json:"spreadRadius"` // Standard deviation of landing positions
}

type DemoSimulation struct {
	stationX      float64
	stationY      float64
	centreReading *AveragedEDMReading
	initialized   bool
}

// Global app state
var (
	appMux           sync.Mutex
	devices          = make(map[string]*Device)
	windBuffer       = make([]WindReading, 0, windBufferSize)
	demoMode         = true
	calibrationStore = make(map[string]*EDMCalibrationData)
	demoSim          = make(map[string]*DemoSimulation)
	throwCoordinates = make([]ThrowCoordinate, 0)
)

// --- Utility Functions ---
func parseDDDMMSSAngle(angleStr string) (float64, error) {
	if len(angleStr) < 6 || len(angleStr) > 7 {
		return 0, fmt.Errorf("invalid angle string length: got %d for '%s'", len(angleStr), angleStr)
	}
	if len(angleStr) == 6 {
		angleStr = "0" + angleStr
	}
	ddd, err := strconv.Atoi(angleStr[0:3])
	if err != nil {
		return 0, err
	}
	mm, err := strconv.Atoi(angleStr[3:5])
	if err != nil {
		return 0, err
	}
	ss, err := strconv.Atoi(angleStr[5:7])
	if err != nil {
		return 0, err
	}
	if mm >= 60 || ss >= 60 {
		return 0, fmt.Errorf("invalid angle values (MM or SS >= 60) in '%s'", angleStr)
	}
	return float64(ddd) + (float64(mm) / 60.0) + (float64(ss) / 3600.0), nil
}

func parseEDMResponseString(raw string) (*ParsedEDMReading, error) {
	parts := strings.Fields(strings.TrimSpace(raw))
	if len(parts) < 4 {
		return nil, fmt.Errorf("malformed response, got %d parts", len(parts))
	}
	sd, err := strconv.ParseFloat(parts[0], 64)
	if err != nil {
		return nil, err
	}
	vaz, err := parseDDDMMSSAngle(parts[1])
	if err != nil {
		return nil, err
	}
	har, err := parseDDDMMSSAngle(parts[2])
	if err != nil {
		return nil, err
	}
	return &ParsedEDMReading{SlopeDistanceMm: sd, VAzDecimal: vaz, HARDecimal: har}, nil
}

func parseWindResponse(raw string) (float64, bool) {
	parts := strings.Split(strings.TrimSpace(raw), ",")
	if len(parts) > 1 && (strings.HasPrefix(parts[1], "+") || strings.HasPrefix(parts[1], "-")) {
		val, err := strconv.ParseFloat(parts[1], 64)
		if err == nil {
			return val, true
		}
	}
	return 0, false
}

// --- Demo Simulation Functions ---
func initDemoSimulation(devType string, targetRadius float64) {
	distance := 8.0 + rand.Float64()*7.0  // 8-15 meters
	angle := rand.Float64() * 2 * math.Pi // Random angle

	stationX := distance * math.Cos(angle)
	stationY := distance * math.Sin(angle)

	demoSim[devType] = &DemoSimulation{
		stationX:    stationX,
		stationY:    stationY,
		initialized: true,
	}
}

func generateDemoCentreReading(devType string, targetRadius float64) *AveragedEDMReading {
	sim, exists := demoSim[devType]
	if !exists {
		initDemoSimulation(devType, targetRadius)
		sim = demoSim[devType]
	}

	distanceToCenter := math.Sqrt(sim.stationX*sim.stationX + sim.stationY*sim.stationY)
	
	harDegrees := math.Atan2(-sim.stationY, -sim.stationX) * 180.0 / math.Pi
	if harDegrees < 0 {
		harDegrees += 360.0
	}

	vazDegrees := 88.0 + rand.Float64()*4.0

	vazRad := vazDegrees * math.Pi / 180.0
	slopeDistance := distanceToCenter / math.Sin(vazRad)

	slopeDistance += (rand.Float64() - 0.5) * 0.01
	harDegrees += (rand.Float64() - 0.5) * 0.1
	vazDegrees += (rand.Float64() - 0.5) * 0.1

	reading := &AveragedEDMReading{
		SlopeDistanceMm: slopeDistance * 1000.0,
		VAzDecimal:      vazDegrees,
		HARDecimal:      harDegrees,
	}

	sim.centreReading = reading
	return reading
}

func generateDemoEdgeReading(devType string, targetRadius float64) *AveragedEDMReading {
	sim, exists := demoSim[devType]
	if !exists || sim.centreReading == nil {
		generateDemoCentreReading(devType, targetRadius)
		sim = demoSim[devType]
	}

	maxVariationMm := 4.0
	toleranceVariation := (rand.Float64() - 0.5) * (maxVariationMm / 1000.0)
	effectiveRadius := targetRadius + toleranceVariation

	edgeAngle := rand.Float64() * 2 * math.Pi
	edgeX := effectiveRadius * math.Cos(edgeAngle)
	edgeY := effectiveRadius * math.Sin(edgeAngle)

	deltaX := edgeX - sim.stationX
	deltaY := edgeY - sim.stationY
	distanceToEdge := math.Sqrt(deltaX*deltaX + deltaY*deltaY)

	harDegrees := math.Atan2(deltaY, deltaX) * 180.0 / math.Pi
	if harDegrees < 0 {
		harDegrees += 360.0
	}

	vazDegrees := sim.centreReading.VAzDecimal + (rand.Float64()-0.5)*1.0

	vazRad := vazDegrees * math.Pi / 180.0
	slopeDistance := distanceToEdge / math.Sin(vazRad)

	slopeDistance += (rand.Float64() - 0.5) * 0.005
	harDegrees += (rand.Float64() - 0.5) * 0.05
	vazDegrees += (rand.Float64() - 0.5) * 0.05

	return &AveragedEDMReading{
		SlopeDistanceMm: slopeDistance * 1000.0,
		VAzDecimal:      vazDegrees,
		HARDecimal:      harDegrees,
	}
}

func generateDemoThrowReading(devType string, targetRadius float64, circleType string) *AveragedEDMReading {
	sim, exists := demoSim[devType]
	if !exists || sim.centreReading == nil {
		generateDemoCentreReading(devType, targetRadius)
		sim = demoSim[devType]
	}

	var minThrow, maxThrow float64
	switch circleType {
	case CircleShot:
		minThrow, maxThrow = 8.0, 18.0
	case CircleDiscus:
		minThrow, maxThrow = 25.0, 65.0
	case CircleHammer:
		minThrow, maxThrow = 20.0, 75.0
	case CircleJavelin:
		minThrow, maxThrow = 35.0, 85.0
	default:
		minThrow, maxThrow = 15.0, 50.0
	}

	throwDistance := minThrow + rand.Float64()*(maxThrow-minThrow)
	totalDistanceFromCentre := throwDistance + targetRadius

	throwAngle := (rand.Float64() - 0.5) * math.Pi / 3

	throwX := totalDistanceFromCentre * math.Cos(throwAngle)
	throwY := totalDistanceFromCentre * math.Sin(throwAngle)

	deltaX := throwX - sim.stationX
	deltaY := throwY - sim.stationY
	distanceToThrow := math.Sqrt(deltaX*deltaX + deltaY*deltaY)

	harDegrees := math.Atan2(deltaY, deltaX) * 180.0 / math.Pi
	if harDegrees < 0 {
		harDegrees += 360.0
	}

	vazDegrees := sim.centreReading.VAzDecimal + (rand.Float64()-0.5)*3.0

	vazRad := vazDegrees * math.Pi / 180.0
	slopeDistance := distanceToThrow / math.Sin(vazRad)

	slopeDistance += (rand.Float64() - 0.5) * 0.02
	harDegrees += (rand.Float64() - 0.5) * 0.1
	vazDegrees += (rand.Float64() - 0.5) * 0.1

	return &AveragedEDMReading{
		SlopeDistanceMm: slopeDistance * 1000.0,
		VAzDecimal:      vazDegrees,
		HARDecimal:      harDegrees,
	}
}

// --- Basic Functions (exported for React Native) ---
func HelloWorld() string {
	return "PolyField EDM Integration - Hello from Go Mobile!"
}

func GetCircleRadius(circleType string) float64 {
	switch circleType {
	case CircleShot:
		return UkaRadiusShot
	case CircleDiscus:
		return UkaRadiusDiscus
	case CircleHammer:
		return UkaRadiusHammer
	case CircleJavelin:
		return UkaRadiusJavelinArc
	default:
		return UkaRadiusShot
	}
}

func GetToleranceForCircle(circleType string) float64 {
	if circleType == CircleJavelin {
		return ToleranceJavelinMm
	}
	return ToleranceThrowsCircleMm
}

func CalculateDistance(x1, y1, x2, y2 float64) float64 {
	return math.Sqrt(math.Pow(x2-x1, 2) + math.Pow(y2-y1, 2))
}

func ValidateThrowCoordinates(x, y, circleRadius float64) bool {
	distance := math.Sqrt(x*x + y*y)
	return distance >= circleRadius
}

func ValidateCalibration(circleType string, measuredRadius float64) bool {
	targetRadius := GetCircleRadius(circleType)
	tolerance := 0.005 // 5mm
	if circleType == CircleJavelin {
		tolerance = 0.010 // 10mm for javelin
	}

	difference := math.Abs(measuredRadius - targetRadius)
	return difference <= tolerance
}

// --- Demo Mode Functions ---
func SetDemoMode(enabled bool) {
	appMux.Lock()
	defer appMux.Unlock()
	demoMode = enabled
	if enabled {
		demoSim = make(map[string]*DemoSimulation)
	}
}

func GetDemoMode() bool {
	appMux.Lock()
	defer appMux.Unlock()
	return demoMode
}

// --- Device Connection Functions ---
func ListSerialPorts() string {
	ports, err := serial.GetPortsList()
	if err != nil {
		return fmt.Sprintf("{\"error\": \"%s\"}", err.Error())
	}
	
	jsonData, _ := json.Marshal(map[string]interface{}{
		"ports": ports,
	})
	return string(jsonData)
}

func ConnectSerialDevice(devType, portName string) string {
	appMux.Lock()
	defer appMux.Unlock()
	
	if d, ok := devices[devType]; ok && d.Conn != nil {
		if d.cancelListener != nil {
			d.cancelListener()
		}
		d.Conn.Close()
	}
	
	mode := &serial.Mode{BaudRate: 9600}
	port, err := serial.Open(portName, mode)
	if err != nil {
		return fmt.Sprintf("{\"error\": \"%s\"}", err.Error())
	}
	
	ctx, cancel := context.WithCancel(context.Background())
	devices[devType] = &Device{
		Conn:           port,
		ConnectionType: "serial",
		Address:        portName,
		cancelListener: cancel,
	}
	
	if devType == "wind" {
		go startWindListener(devType, ctx)
	}
	
	result := map[string]interface{}{
		"success": true,
		"message": fmt.Sprintf("Connected to %s on %s", devType, portName),
	}
	jsonData, _ := json.Marshal(result)
	return string(jsonData)
}

func ConnectNetworkDevice(devType, ipAddress string, port int) string {
	appMux.Lock()
	defer appMux.Unlock()
	
	if d, ok := devices[devType]; ok && d.Conn != nil {
		if d.cancelListener != nil {
			d.cancelListener()
		}
		d.Conn.Close()
	}
	
	address := net.JoinHostPort(ipAddress, strconv.Itoa(port))
	conn, err := net.DialTimeout("tcp", address, 5*time.Second)
	if err != nil {
		return fmt.Sprintf("{\"error\": \"%s\"}", err.Error())
	}
	
	ctx, cancel := context.WithCancel(context.Background())
	devices[devType] = &Device{
		Conn:           conn,
		ConnectionType: "network",
		Address:        address,
		cancelListener: cancel,
	}
	
	if devType == "wind" {
		go startWindListener(devType, ctx)
	}
	
	result := map[string]interface{}{
		"success": true,
		"message": fmt.Sprintf("Connected to %s at %s", devType, address),
	}
	jsonData, _ := json.Marshal(result)
	return string(jsonData)
}

func DisconnectDevice(devType string) string {
	appMux.Lock()
	defer appMux.Unlock()
	
	if dev, ok := devices[devType]; ok && dev.Conn != nil {
		if dev.cancelListener != nil {
			dev.cancelListener()
		}
		dev.Conn.Close()
		delete(devices, devType)
		
		result := map[string]interface{}{
			"success": true,
			"message": fmt.Sprintf("Disconnected %s", devType),
		}
		jsonData, _ := json.Marshal(result)
		return string(jsonData)
	}
	
	return fmt.Sprintf("{\"error\": \"%s not connected\"}", devType)
}

// RegisterUSBDevice registers a USB device as connected without opening a port
// This is used when Android USB Host API handles the actual communication
func RegisterUSBDevice(devType, deviceName string) string {
	appMux.Lock()
	defer appMux.Unlock()
	
	// Close any existing connection
	if d, ok := devices[devType]; ok && d.Conn != nil {
		if d.cancelListener != nil {
			d.cancelListener()
		}
		d.Conn.Close()
	}
	
	// Create a dummy connection that represents the USB device
	// The Android side handles actual communication
	_, cancel := context.WithCancel(context.Background())
	devices[devType] = &Device{
		Conn:           &usbDeviceStub{deviceName: deviceName},
		ConnectionType: "usb_android",
		Address:        deviceName,
		cancelListener: cancel,
	}
	
	result := map[string]interface{}{
		"success": true,
		"message": fmt.Sprintf("USB device %s registered for %s (communication via Android USB Host API)", deviceName, devType),
	}
	jsonData, _ := json.Marshal(result)
	return string(jsonData)
}

// usbDeviceStub is a stub connection for USB devices managed by Android
type usbDeviceStub struct {
	deviceName string
}

func (u *usbDeviceStub) Read(p []byte) (n int, err error) {
	return 0, fmt.Errorf("USB communication handled by Android - use EDM native methods")
}

func (u *usbDeviceStub) Write(p []byte) (n int, err error) {
	return 0, fmt.Errorf("USB communication handled by Android - use EDM native methods")
}

func (u *usbDeviceStub) Close() error {
	return nil // Android handles the actual USB connection
}

// --- EDM Reading Functions ---
func triggerSingleEDMRead(dev *Device) (*ParsedEDMReading, error) {
	if _, err := dev.Conn.Write(edmReadCommand); err != nil {
		return nil, err
	}
	
	// Create a channel to receive the result
	type readResult struct {
		response string
		err      error
	}
	resultChan := make(chan readResult, 1)
	
	// Start reading in a goroutine
	go func() {
		r := bufio.NewReader(dev.Conn)
		resp, err := r.ReadString('\n')
		resultChan <- readResult{response: resp, err: err}
	}()
	
	// Wait for either the read to complete or timeout
	select {
	case result := <-resultChan:
		if result.err != nil {
			return nil, result.err
		}
		return parseEDMResponseString(result.response)
	case <-time.After(edmSingleReadTimeout):
		return nil, fmt.Errorf("Could not find prism. Check your aim and remeasure. If EDM displays \"STOP\" then press F1 to reset")
	}
}

func GetReliableEDMReading(devType string) string {
	appMux.Lock()
	isDemoMode := demoMode
	device, ok := devices[devType]
	appMux.Unlock()
	
	if isDemoMode {
		reading := &AveragedEDMReading{
			SlopeDistanceMm: 10000 + rand.Float64()*15000,
			VAzDecimal:      92.0 + rand.Float64()*5.0,
			HARDecimal:      rand.Float64() * 360.0,
		}
		jsonData, _ := json.Marshal(reading)
		return string(jsonData)
	}
	
	if !ok || device.Conn == nil {
		return fmt.Sprintf("{\"error\": \"EDM device type '%s' not connected\"}", devType)
	}
	
	// For USB devices managed by Android, return a signal that Android should handle the communication
	if device.ConnectionType == "usb_android" {
		return fmt.Sprintf("{\"error\": \"USB_ANDROID_DELEGATE\", \"deviceType\": \"%s\"}", devType)
	}

	r1, e1 := triggerSingleEDMRead(device)
	if e1 != nil {
		return fmt.Sprintf("{\"error\": \"first read failed: %s\"}", e1.Error())
	}

	time.Sleep(delayBetweenReadsInPair)

	r2, e2 := triggerSingleEDMRead(device)
	if e2 != nil {
		return fmt.Sprintf("{\"error\": \"second read failed: %s\"}", e2.Error())
	}

	if math.Abs(r1.SlopeDistanceMm-r2.SlopeDistanceMm) <= sdToleranceMm {
		reading := &AveragedEDMReading{
			SlopeDistanceMm: (r1.SlopeDistanceMm + r2.SlopeDistanceMm) / 2.0,
			VAzDecimal:      (r1.VAzDecimal + r2.VAzDecimal) / 2.0,
			HARDecimal:      (r1.HARDecimal + r2.HARDecimal) / 2.0,
		}
		jsonData, _ := json.Marshal(reading)
		return string(jsonData)
	}
	
	return fmt.Sprintf("{\"error\": \"readings inconsistent. R1(SD): %.0fmm, R2(SD): %.0fmm\"}", r1.SlopeDistanceMm, r2.SlopeDistanceMm)
}

// --- Calibration Functions ---
func GetCalibration(devType string) string {
	appMux.Lock()
	defer appMux.Unlock()
	
	if cal, exists := calibrationStore[devType]; exists {
		jsonData, _ := json.Marshal(cal)
		return string(jsonData)
	}
	
	defaultCal := &EDMCalibrationData{
		DeviceID:           devType,
		SelectedCircleType: CircleShot,
		TargetRadius:       UkaRadiusShot,
	}
	jsonData, _ := json.Marshal(defaultCal)
	return string(jsonData)
}

func SaveCalibration(devType string, calibrationJSON string) string {
	appMux.Lock()
	defer appMux.Unlock()
	
	var data EDMCalibrationData
	if err := json.Unmarshal([]byte(calibrationJSON), &data); err != nil {
		return fmt.Sprintf("{\"error\": \"%s\"}", err.Error())
	}
	
	if existingCal, ok := calibrationStore[devType]; ok {
		data.Timestamp = existingCal.Timestamp
	}
	calibrationStore[devType] = &data

	if demoMode {
		delete(demoSim, devType)
	}

	return "{\"success\": true}"
}

func ResetCalibration(devType string) string {
	appMux.Lock()
	defer appMux.Unlock()
	
	delete(calibrationStore, devType)
	if demoMode {
		delete(demoSim, devType)
	}
	
	return "{\"success\": true}"
}

func SetCentre(devType string) string {
	var reading *AveragedEDMReading
	
	appMux.Lock()
	isDemoMode := demoMode
	cal, exists := calibrationStore[devType]
	if !exists {
		cal = &EDMCalibrationData{
			DeviceID:           devType,
			SelectedCircleType: CircleShot,
			TargetRadius:       UkaRadiusShot,
		}
	}
	targetRadius := cal.TargetRadius
	appMux.Unlock()

	if isDemoMode {
		time.Sleep(CENTRE_DELAY)
		reading = generateDemoCentreReading(devType, targetRadius)
	} else {
		readingStr := GetReliableEDMReading(devType)
		
		// Check if this is a USB delegation request
		if strings.Contains(readingStr, "USB_ANDROID_DELEGATE") {
			return readingStr  // Return the delegation signal to Android
		}
		
		var readingData AveragedEDMReading
		if err := json.Unmarshal([]byte(readingStr), &readingData); err != nil {
			return fmt.Sprintf("{\"error\": \"could not get centre reading: %s\"}", err.Error())
		}
		reading = &readingData
	}

	sdMeters := reading.SlopeDistanceMm / 1000.0
	vazRad := reading.VAzDecimal * math.Pi / 180.0
	harRad := reading.HARDecimal * math.Pi / 180.0

	horizontalDistance := sdMeters * math.Sin(vazRad)
	stationX := -horizontalDistance * math.Cos(harRad)
	stationY := -horizontalDistance * math.Sin(harRad)

	appMux.Lock()
	cal.StationCoordinates = EDMPoint{X: stationX, Y: stationY}
	cal.IsCentreSet = true
	cal.EdgeVerificationResult = nil
	cal.Timestamp = time.Now().UTC()
	calibrationStore[devType] = cal
	appMux.Unlock()

	result := map[string]interface{}{
		"success":         true,
		"slopeDistanceMm": reading.SlopeDistanceMm,
		"vAzDecimal":      reading.VAzDecimal,
		"hARDecimal":      reading.HARDecimal,
		"message":         "Centre point set successfully",
	}
	
	jsonData, _ := json.Marshal(result)
	return string(jsonData)
}

func VerifyEdge(devType string, targetRadius float64) string {
	appMux.Lock()
	cal, exists := calibrationStore[devType]
	isDemoMode := demoMode
	if !exists || !cal.IsCentreSet {
		appMux.Unlock()
		return "{\"error\": \"must set circle centre first\"}"
	}

	actualTargetRadius := cal.TargetRadius
	circleType := cal.SelectedCircleType
	appMux.Unlock()

	var reading *AveragedEDMReading

	if isDemoMode {
		time.Sleep(EDGE_DELAY)
		reading = generateDemoEdgeReading(devType, actualTargetRadius)
	} else {
		readingStr := GetReliableEDMReading(devType)
		
		// Check if this is a USB delegation request
		if strings.Contains(readingStr, "USB_ANDROID_DELEGATE") {
			return readingStr  // Return the delegation signal to Android
		}
		
		var readingData AveragedEDMReading
		if err := json.Unmarshal([]byte(readingStr), &readingData); err != nil {
			return fmt.Sprintf("{\"error\": \"could not get edge reading: %s\"}", err.Error())
		}
		reading = &readingData
	}

	sdMeters := reading.SlopeDistanceMm / 1000.0
	vazRad := reading.VAzDecimal * math.Pi / 180.0
	harRad := reading.HARDecimal * math.Pi / 180.0

	horizontalDistance := sdMeters * math.Sin(vazRad)
	edgeX := horizontalDistance * math.Cos(harRad)
	edgeY := horizontalDistance * math.Sin(harRad)

	absoluteEdgeX := cal.StationCoordinates.X + edgeX
	absoluteEdgeY := cal.StationCoordinates.Y + edgeY

	measuredRadius := math.Sqrt(math.Pow(absoluteEdgeX, 2) + math.Pow(absoluteEdgeY, 2))
	diffMm := (measuredRadius - actualTargetRadius) * 1000.0

	toleranceMm := ToleranceThrowsCircleMm
	if circleType == CircleJavelin {
		toleranceMm = ToleranceJavelinMm
	}

	isInTolerance := math.Abs(diffMm) <= toleranceMm

	cal.EdgeVerificationResult = &EdgeVerificationResult{
		MeasuredRadius:     measuredRadius,
		DifferenceMm:       diffMm,
		IsInTolerance:      isInTolerance,
		ToleranceAppliedMm: toleranceMm,
	}

	appMux.Lock()
	calibrationStore[devType] = cal
	appMux.Unlock()

	result := map[string]interface{}{
		"success":        true,
		"toleranceCheck": isInTolerance,
		"differenceMm":   diffMm,
		"measuredRadius": measuredRadius,
		"message":        "Edge verification completed",
	}

	jsonData, _ := json.Marshal(result)
	return string(jsonData)
}

func MeasureThrow(devType string) string {
	appMux.Lock()
	cal, exists := calibrationStore[devType]
	isDemoMode := demoMode
	if !exists || !cal.IsCentreSet {
		appMux.Unlock()
		return "{\"error\": \"EDM is not calibrated - centre not set\"}"
	}

	if !isDemoMode && (cal.EdgeVerificationResult == nil || !cal.EdgeVerificationResult.IsInTolerance) {
		appMux.Unlock()
		return "{\"error\": \"EDM must be calibrated with valid edge verification before measurement\"}"
	}

	targetRadius := cal.TargetRadius
	circleType := cal.SelectedCircleType
	appMux.Unlock()

	var reading *AveragedEDMReading

	if isDemoMode {
		time.Sleep(THROW_DELAY)
		reading = generateDemoThrowReading(devType, targetRadius, circleType)
	} else {
		readingStr := GetReliableEDMReading(devType)
		
		// Check if this is a USB delegation request
		if strings.Contains(readingStr, "USB_ANDROID_DELEGATE") {
			return readingStr  // Return the delegation signal to Android
		}
		
		var readingData AveragedEDMReading
		if err := json.Unmarshal([]byte(readingStr), &readingData); err != nil {
			return fmt.Sprintf("{\"error\": \"could not get throw reading: %s\"}", err.Error())
		}
		reading = &readingData
	}

	sdMeters := reading.SlopeDistanceMm / 1000.0
	vazRad := reading.VAzDecimal * math.Pi / 180.0
	harRad := reading.HARDecimal * math.Pi / 180.0

	horizontalDistance := sdMeters * math.Sin(vazRad)
	throwX := horizontalDistance * math.Cos(harRad)
	throwY := horizontalDistance * math.Sin(harRad)

	absoluteThrowX := cal.StationCoordinates.X + throwX
	absoluteThrowY := cal.StationCoordinates.Y + throwY

	distanceFromCentre := math.Sqrt(math.Pow(absoluteThrowX, 2) + math.Pow(absoluteThrowY, 2))
	finalThrowDistance := distanceFromCentre - targetRadius

	// Store the coordinate
	coord := ThrowCoordinate{
		X:          absoluteThrowX,
		Y:          absoluteThrowY,
		Distance:   finalThrowDistance,
		CircleType: circleType,
		Timestamp:  time.Now().UTC(),
		EDMReading: fmt.Sprintf("%.0f %.6f %.6f", reading.SlopeDistanceMm, reading.VAzDecimal, reading.HARDecimal),
	}
	
	appMux.Lock()
	throwCoordinates = append(throwCoordinates, coord)
	appMux.Unlock()

	result := map[string]interface{}{
		"distance":  finalThrowDistance,
		"x":         absoluteThrowX,
		"y":         absoluteThrowY,
		"timestamp": coord.Timestamp.Format(time.RFC3339),
	}

	jsonData, _ := json.Marshal(result)
	return string(jsonData)
}

// --- Wind Measurement Functions ---
func startWindListener(devType string, ctx context.Context) {
	appMux.Lock()
	device, ok := devices[devType]
	appMux.Unlock()
	if !ok {
		return
	}

	scanner := bufio.NewScanner(device.Conn)
	for scanner.Scan() {
		select {
		case <-ctx.Done():
			return
		default:
			text := scanner.Text()
			if val, ok := parseWindResponse(text); ok {
				appMux.Lock()
				windBuffer = append(windBuffer, WindReading{Value: val, Timestamp: time.Now()})
				if len(windBuffer) > windBufferSize {
					windBuffer = windBuffer[1:]
				}
				appMux.Unlock()
			}
		}
	}
}

func MeasureWind() float64 {
	appMux.Lock()
	defer appMux.Unlock()

	if demoMode {
		return (rand.Float64() * 4.0) - 2.0
	}

	_, ok := devices["wind"]
	if !ok {
		return 0.0 // Error condition
	}

	now := time.Now()
	fiveSecondsAgo := now.Add(-5 * time.Second)
	var readingsInWindow []float64
	for _, reading := range windBuffer {
		if reading.Timestamp.After(fiveSecondsAgo) {
			readingsInWindow = append(readingsInWindow, reading.Value)
		}
	}

	if len(readingsInWindow) == 0 {
		return 0.0 // No recent readings
	}

	var sum float64
	for _, v := range readingsInWindow {
		sum += v
	}
	return sum / float64(len(readingsInWindow))
}

// --- Coordinate Storage and Statistics Functions ---
func GetThrowCoordinates() string {
	appMux.Lock()
	defer appMux.Unlock()
	
	result := map[string]interface{}{
		"coordinates": throwCoordinates,
	}
	
	jsonData, _ := json.Marshal(result)
	return string(jsonData)
}

func GetThrowStatistics(circleType string) string {
	appMux.Lock()
	defer appMux.Unlock()

	var coordinates []ThrowCoordinate
	for _, coord := range throwCoordinates {
		if coord.CircleType == circleType {
			coordinates = append(coordinates, coord)
		}
	}

	if len(coordinates) == 0 {
		return fmt.Sprintf("{\"error\": \"no throws found for %s\"}", circleType)
	}

	stats := &SessionStatistics{
		TotalThrows: len(coordinates),
	}

	var sumX, sumY, sumDistance float64
	var maxDist, minDist float64 = coordinates[0].Distance, coordinates[0].Distance

	for _, coord := range coordinates {
		sumX += coord.X
		sumY += coord.Y
		sumDistance += coord.Distance

		if coord.Distance > maxDist {
			maxDist = coord.Distance
		}
		if coord.Distance < minDist {
			minDist = coord.Distance
		}
	}

	stats.AverageX = sumX / float64(len(coordinates))
	stats.AverageY = sumY / float64(len(coordinates))
	stats.AverageDistance = sumDistance / float64(len(coordinates))
	stats.MaxDistance = maxDist
	stats.MinDistance = minDist

	var sumSquaredDist float64
	for _, coord := range coordinates {
		dx := coord.X - stats.AverageX
		dy := coord.Y - stats.AverageY
		sumSquaredDist += dx*dx + dy*dy
	}
	stats.SpreadRadius = math.Sqrt(sumSquaredDist / float64(len(coordinates)))

	jsonData, _ := json.Marshal(stats)
	return string(jsonData)
}

func ClearThrowCoordinates() {
	appMux.Lock()
	defer appMux.Unlock()
	throwCoordinates = make([]ThrowCoordinate, 0)
}

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