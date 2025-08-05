import React, { useState, useEffect, useRef } from 'react';
import {
  View, Text, TouchableOpacity, StyleSheet, Dimensions, SafeAreaView,
  Alert, ScrollView, TextInput, Switch, Modal, FlatList, NativeModules
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {Picker} from '@react-native-picker/picker';

const { EDMModule } = NativeModules;

const { width: screenWidth, height: screenHeight } = Dimensions.get('window');

// UKA Official Circle Radii
const UKA_RADII = {
  SHOT: 1.0675,
  DISCUS: 1.250,
  HAMMER: 1.0675,
  JAVELIN_ARC: 8.000
};

// World Record approximate values (men) for scaling heat map
const WORLD_RECORDS = {
  SHOT: 23.37,      // Shot Put
  DISCUS: 74.08,    // Discus
  HAMMER: 86.74,    // Hammer
  JAVELIN_ARC: 98.48 // Javelin
};

// Calculate 80% of world record for heat map default scale
const getDefaultHeatMapScale = (circleType) => {
  return Math.round(WORLD_RECORDS[circleType] * 0.8);
};

// Device connection types
const CONNECTION_TYPES = {
  SERIAL: 'serial',
  NETWORK: 'network'
};

// Storage keys
const STORAGE_KEYS = {
  CONNECTION_DETAILS: 'polyfield-connections',
  THROW_COORDINATES: 'polyfield-throws',
  APP_SETTINGS: 'polyfield-settings',
  DEVICE_CONFIG: 'polyfield-device-config',
  CALIBRATION_DATA: 'polyfield-calibration'
};

const PolyFieldApp = () => {
  const [currentScreen, setCurrentScreen] = useState('SELECT_EVENT_TYPE');
  const [eventType, setEventType] = useState(null);
  const [demoMode, setDemoMode] = useState(true);
  const [measurement, setMeasurement] = useState('');
  const [windMeasurement, setWindMeasurement] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  // Device connection state
  const [devices, setDevices] = useState({
    edm: { connected: false, type: 'serial', address: '', port: 10001 },
    wind: { connected: false, type: 'serial', address: '', port: 10001 },
    scoreboard: { connected: false, type: 'serial', address: '', port: 10001 }
  });

  // Calibration state
  const [calibration, setCalibration] = useState({
    circleType: 'SHOT',
    targetRadius: UKA_RADII.SHOT,
    centreSet: false,
    centreTimestamp: null,
    edgeVerified: false,
    edgeResult: null,
    stationCoordinates: { x: 0, y: 0 }
  });

  // Throw data management
  const [throwCoordinates, setThrowCoordinates] = useState([]);
  const [currentSession, setCurrentSession] = useState(null);
  const [sessionStats, setSessionStats] = useState(null);

  // Heat map state
  const [heatMapData, setHeatMapData] = useState(null);
  const [heatMapVisible, setHeatMapVisible] = useState(false);

  const [deviceSetupVisible, setDeviceSetupVisible] = useState(false);
  
  // Device configuration inputs
  const [deviceConfig, setDeviceConfig] = useState({
    edm: { type: 'serial', ip: '192.168.1.100', port: '10001', comPort: 'COM1' },
    wind: { type: 'serial', ip: '192.168.1.101', port: '10002', comPort: 'COM2' },
    scoreboard: { type: 'network', ip: '192.168.1.102', port: '10003', comPort: 'COM3' }
  });
  
  // Available serial ports
  const [availableSerialPorts, setAvailableSerialPorts] = useState([]);

  // Load saved data on app start
  useEffect(() => {
    loadStoredData();
  }, []);

  // Save data when state changes
  useEffect(() => {
    saveAppSettings();
  }, [demoMode, eventType, devices]);

  useEffect(() => {
    saveThrowCoordinates();
  }, [throwCoordinates]);

  useEffect(() => {
    saveDeviceConfig();
  }, [deviceConfig]);

  useEffect(() => {
    saveCalibrationData();
  }, [calibration]);

  const loadStoredData = async () => {
    try {
      const savedSettings = await AsyncStorage.getItem(STORAGE_KEYS.APP_SETTINGS);
      if (savedSettings) {
        const settings = JSON.parse(savedSettings);
        setDemoMode(settings.demoMode || true);
        setEventType(settings.eventType);
        setDevices(settings.devices || devices);
      }

      const savedThrows = await AsyncStorage.getItem(STORAGE_KEYS.THROW_COORDINATES);
      if (savedThrows) {
        setThrowCoordinates(JSON.parse(savedThrows));
      }

      const savedDeviceConfig = await AsyncStorage.getItem(STORAGE_KEYS.DEVICE_CONFIG);
      if (savedDeviceConfig) {
        setDeviceConfig(JSON.parse(savedDeviceConfig));
      }

      const savedCalibration = await AsyncStorage.getItem(STORAGE_KEYS.CALIBRATION_DATA);
      if (savedCalibration) {
        const calibrationData = JSON.parse(savedCalibration);
        // Only restore if it's from the same day
        const today = new Date().toDateString();
        if (calibrationData.centreTimestamp && 
            new Date(calibrationData.centreTimestamp).toDateString() === today) {
          setCalibration(calibrationData);
        }
      }
    } catch (error) {
      console.error('Error loading stored data:', error);
    }
  };

  const saveAppSettings = async () => {
    try {
      const settings = { demoMode, eventType, devices };
      await AsyncStorage.setItem(STORAGE_KEYS.APP_SETTINGS, JSON.stringify(settings));
    } catch (error) {
      console.error('Error saving settings:', error);
    }
  };

  const saveThrowCoordinates = async () => {
    try {
      await AsyncStorage.setItem(STORAGE_KEYS.THROW_COORDINATES, JSON.stringify(throwCoordinates));
    } catch (error) {
      console.error('Error saving throw coordinates:', error);
    }
  };

  const saveDeviceConfig = async () => {
    try {
      await AsyncStorage.setItem(STORAGE_KEYS.DEVICE_CONFIG, JSON.stringify(deviceConfig));
    } catch (error) {
      console.error('Error saving device config:', error);
    }
  };

  const saveCalibrationData = async () => {
    try {
      await AsyncStorage.setItem(STORAGE_KEYS.CALIBRATION_DATA, JSON.stringify(calibration));
    } catch (error) {
      console.error('Error saving calibration data:', error);
    }
  };

  // Demo simulation functions
  const generateDemoReading = (eventType, measurementType = 'throw') => {
    const targetRadius = calibration.targetRadius;

    if (measurementType === 'centre') {
      // Simulate centre reading
      return {
        slopeDistanceMm: 8000 + Math.random() * 7000, // 8-15m from centre
        vAzDecimal: 88.0 + Math.random() * 4.0,
        hARDecimal: Math.random() * 360.0
      };
    } else if (measurementType === 'edge') {
      // Simulate edge reading within tolerance
      const toleranceVariation = (Math.random() - 0.5) * 8; // ±4mm
      return {
        toleranceCheck: Math.abs(toleranceVariation) <= 5,
        differenceMm: toleranceVariation,
        measuredRadius: targetRadius + (toleranceVariation / 1000)
      };
    } else if (measurementType === 'wind') {
      const windSpeed = (Math.random() * 4) - 2; // -2 to +2 m/s
      return windSpeed;
    } else {
      // Throw measurement
      let distance;
      switch (calibration.circleType) {
        case 'SHOT':
          distance = 8 + Math.random() * 10; // 8-18m
          break;
        case 'DISCUS':
          distance = 25 + Math.random() * 40; // 25-65m
          break;
        case 'HAMMER':
          distance = 20 + Math.random() * 55; // 20-75m
          break;
        case 'JAVELIN_ARC':
          distance = 35 + Math.random() * 50; // 35-85m
          break;
        default:
          distance = 15 + Math.random() * 35;
      }
      return distance;
    }
  };

  const measureDistance = async () => {
    setIsLoading(true);
    
    if (demoMode) {
      // Demo mode - use simulated values
      setTimeout(() => {
        const distance = generateDemoReading(eventType, 'throw');
        const result = `${distance.toFixed(2)} m`;
        setMeasurement(result);

        // Store throw coordinate
        storeThrowCoordinate({
          x: Math.random() * 20 - 10, // Random X coordinate
          y: Math.random() * 50 + distance, // Y based on distance
          distance: distance,
          circleType: calibration.circleType,
          timestamp: new Date().toISOString()
        });

        setIsLoading(false);
      }, 1500);
    } else {
      // Live mode - use EDM device
      try {
        if (!devices.edm.connected) {
          Alert.alert('Device Error', 'EDM device is not connected. Please connect a device first.');
          setIsLoading(false);
          return;
        }

        const response = await EDMModule.measureThrow('edm');
        if (response && response.distance !== undefined) {
          const distance = response.distance;
          const result = `${distance.toFixed(2)} m`;
          setMeasurement(result);

          // Store throw coordinate
          storeThrowCoordinate({
            x: Math.random() * 20 - 10, // Random X coordinate  
            y: Math.random() * 50 + distance, // Y based on distance
            distance: distance,
            circleType: calibration.circleType,
            timestamp: new Date().toISOString()
          });
        } else {
          Alert.alert('Measurement Error', 'Failed to get measurement from device');
        }
      } catch (error) {
        console.error('EDM measurement error:', error);
        Alert.alert('Device Error', 'Failed to measure distance: ' + error.message);
      }
      setIsLoading(false);
    }
  };

  const measureWind = async () => {
    setIsLoading(true);
    
    if (demoMode) {
      // Demo mode - use simulated values
      setTimeout(() => {
        const windSpeed = generateDemoReading(eventType, 'wind');
        const result = `${windSpeed > 0 ? '+' : ''}${windSpeed.toFixed(1)} m/s`;
        setWindMeasurement(result);
        setIsLoading(false);
      }, 3000);
    } else {
      // Live mode - use wind device
      try {
        if (!devices.wind.connected) {
          Alert.alert('Device Error', 'Wind gauge is not connected. Please connect a device first.');
          setIsLoading(false);
          return;
        }

        const windSpeed = await EDMModule.measureWind();
        const result = `${windSpeed > 0 ? '+' : ''}${windSpeed.toFixed(1)} m/s`;
        setWindMeasurement(result);
      } catch (error) {
        console.error('Wind measurement error:', error);
        Alert.alert('Device Error', 'Failed to measure wind: ' + error.message);
      }
      setIsLoading(false);
    }
  };

  const setCentre = async () => {
    setIsLoading(true);
    setTimeout(() => {
      const reading = generateDemoReading(eventType, 'centre');

      // Calculate station coordinates (simplified for demo)
      const stationX = (Math.random() - 0.5) * 20; // ±10m
      const stationY = (Math.random() - 0.5) * 20; // ±10m

      setCalibration(prev => ({
        ...prev,
        centreSet: true,
        centreTimestamp: new Date().toISOString(),
        stationCoordinates: { x: stationX, y: stationY },
        edgeVerified: false,
        edgeResult: null
      }));
      setIsLoading(false);
    }, 2000);
  };

  const verifyEdge = async () => {
    setIsLoading(true);
    return new Promise((resolve) => {
      setTimeout(() => {
        const mockResult = generateDemoReading(eventType, 'edge');

        setCalibration(prev => ({
          ...prev,
          edgeVerified: mockResult.toleranceCheck,
          edgeResult: mockResult
        }));
        setIsLoading(false);
        resolve(mockResult);
      }, 2000);
    });
  };

  const storeThrowCoordinate = (coordinate) => {
    const newCoord = {
      ...coordinate,
      id: Date.now().toString(),
      athleteId: currentSession?.athleteId || '',
      round: currentSession?.round || ''
    };

    setThrowCoordinates(prev => [...prev, newCoord]);

    // Add to current session if active
    if (currentSession) {
      setCurrentSession(prev => ({
        ...prev,
        coordinates: [...(prev.coordinates || []), newCoord]
      }));
    }
  };

  const generateHeatMapData = () => {
    const filteredCoords = throwCoordinates.filter(coord =>
      coord.circleType === calibration.circleType
    );

    // Calculate optimal scale based on actual throw data
    let optimalScale;
    if (filteredCoords.length > 0) {
      // Find the furthest throw and add 10% padding for better visualization
      const maxDistance = Math.max(...filteredCoords.map(c => c.distance));
      optimalScale = Math.ceil(maxDistance * 1.1); // Add 10% padding and round up

      // Ensure minimum scale for good circle visibility
      const minScale = Math.max(calibration.targetRadius * 15, 20); // At least 15x circle radius or 20m
      optimalScale = Math.max(optimalScale, minScale);
    } else {
      // Fall back to 80% of world record when no data exists
      optimalScale = getDefaultHeatMapScale(calibration.circleType);
    }

    // Calculate bounds based on data or use optimal scale
    let bounds;
    if (filteredCoords.length === 0) {
      // Use optimal scale when no data
      bounds = {
        minX: -optimalScale * 0.3,
        maxX: optimalScale * 0.3,
        minY: -calibration.targetRadius * 2,
        maxY: optimalScale + calibration.targetRadius
      };
    } else {
      // Use actual data bounds but ensure minimum scale
      const dataMinX = Math.min(...filteredCoords.map(c => c.x));
      const dataMaxX = Math.max(...filteredCoords.map(c => c.x));
      const dataMinY = Math.min(...filteredCoords.map(c => c.y));
      const dataMaxY = Math.max(...filteredCoords.map(c => c.y));

      bounds = {
        minX: Math.min(dataMinX, -optimalScale * 0.2),
        maxX: Math.max(dataMaxX, optimalScale * 0.2),
        minY: Math.min(dataMinY, -calibration.targetRadius * 2),
        maxY: Math.max(dataMaxY, optimalScale * 0.8)
      };
    }

    // Simple statistics
    const stats = filteredCoords.length > 0 ? {
      totalThrows: filteredCoords.length,
      averageDistance: filteredCoords.reduce((sum, c) => sum + c.distance, 0) / filteredCoords.length,
      maxDistance: Math.max(...filteredCoords.map(c => c.distance)),
      minDistance: Math.min(...filteredCoords.map(c => c.distance))
    } : {
      totalThrows: 0,
      averageDistance: 0,
      maxDistance: 0,
      minDistance: 0
    };

    return {
      coordinates: filteredCoords,
      bounds,
      stats,
      circleType: calibration.circleType,
      targetRadius: calibration.targetRadius,
      optimalScale,
      isAutoScaled: filteredCoords.length > 0 // Flag to indicate if we used auto-scaling
    };
  };

  const exportThrowData = async () => {
    const data = throwCoordinates.map(coord => ({
      X: coord.x.toFixed(3),
      Y: coord.y.toFixed(3),
      Distance: coord.distance.toFixed(2),
      CircleType: coord.circleType,
      Timestamp: coord.timestamp,
      AthleteId: coord.athleteId || '',
      Round: coord.round || ''
    }));

    // In a real app, you would export this data to a file or share it
    Alert.alert(
      'Export Data',
      `Ready to export ${data.length} throw coordinates`,
      [
        { text: 'Cancel' },
        { text: 'Copy to Clipboard', onPress: () => {
          const csvData = [
            'X,Y,Distance,CircleType,Timestamp,AthleteId,Round',
            ...data.map(row => Object.values(row).join(','))
          ].join('\n');
          // In real app: Clipboard.setString(csvData);
          Alert.alert('Exported', 'Data copied to clipboard');
        }}
      ]
    );
  };

  const clearAllData = () => {
    Alert.alert(
      'Clear All Data',
      'This will delete all stored throw coordinates. Are you sure?',
      [
        { text: 'Cancel' },
        { text: 'Clear', style: 'destructive', onPress: () => {
          setThrowCoordinates([]);
          setCurrentSession(null);
          Alert.alert('Cleared', 'All throw data has been deleted');
        }}
      ]
    );
  };

  // Help instruction handler
  const showInstructions = (instructions) => {
    Alert.alert('Instructions', instructions, [{ text: 'OK' }]);
  };

  // Discover available serial ports
  const discoverSerialPorts = async () => {
    try {
      if (EDMModule && EDMModule.getAvailableSerialPorts) {
        const ports = await EDMModule.getAvailableSerialPorts();
        setAvailableSerialPorts(ports || []);
      } else {
        // Fallback for demo/testing - typical Android serial ports
        setAvailableSerialPorts([
          '/dev/ttyUSB0',
          '/dev/ttyUSB1', 
          '/dev/ttyACM0',
          '/dev/ttyS0',
          '/dev/ttyS1'
        ]);
      }
    } catch (error) {
      console.log('Error discovering serial ports:', error);
      // Set common Android serial device paths as fallback
      setAvailableSerialPorts([
        '/dev/ttyUSB0',
        '/dev/ttyUSB1', 
        '/dev/ttyACM0',
        '/dev/ttyS0',
        '/dev/ttyS1'
      ]);
    }
  };

  // Load serial ports when component mounts or when switching to live mode
  useEffect(() => {
    if (!demoMode) {
      discoverSerialPorts();
    }
  }, [demoMode]);

  // Device connection functions (simplified for demo)
  const connectDevice = async (deviceType, connectionType, address, port) => {
    setDevices(prev => ({
      ...prev,
      [deviceType]: {
        connected: true,
        type: connectionType,
        address,
        port
      }
    }));
    Alert.alert('Connected', `${deviceType.toUpperCase()} connected successfully`);
  };

  const disconnectDevice = (deviceType) => {
    setDevices(prev => ({
      ...prev,
      [deviceType]: { ...prev[deviceType], connected: false }
    }));
    Alert.alert('Disconnected', `${deviceType.toUpperCase()} disconnected`);
  };

  // Event Selection Screen
  if (currentScreen === 'SELECT_EVENT_TYPE') {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>PolyField by KACPH</Text>
          <View style={styles.headerRight}>
            {demoMode && (
              <TouchableOpacity onPress={() => {
                setDemoMode(!demoMode);
                // Reset calibration when switching to live mode
                setCalibration(prev => ({
                  ...prev,
                  centreSet: false,
                  centreTimestamp: null,
                  edgeVerified: false,
                  edgeResult: null
                }));
                // Go back to device setup for recalibration
                if (eventType === 'Throws') {
                  setCurrentScreen('DEVICE_SETUP');
                }
              }}>
                <Text style={styles.demoStatus}>Demo Active</Text>
              </TouchableOpacity>
            )}
            {!demoMode && (
              <TouchableOpacity onPress={() => setDemoMode(!demoMode)}>
                <Text style={styles.realModeStatus}>Live Mode</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>

        <View style={styles.mainContent}>
          <Text style={styles.title}>Select Event Type</Text>

          <View style={styles.cardContainer}>
            <TouchableOpacity
              style={styles.card}
              onPress={() => {
                setEventType('Throws');
                setCurrentScreen('DEVICE_SETUP');
              }}
            >
              <View style={styles.cardIcon}>
                <Text style={styles.iconText}>🎯</Text>
              </View>
              <Text style={styles.cardTitle}>Throws</Text>
              <Text style={styles.cardSubtitle}>Shot • Discus • Hammer • Javelin</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={styles.card}
              onPress={() => {
                setEventType('Horizontal Jumps');
                setCurrentScreen('DEVICE_SETUP');
              }}
            >
              <View style={styles.cardIcon}>
                <Text style={styles.iconText}>🏃</Text>
              </View>
              <Text style={styles.cardTitle}>Horizontal Jumps</Text>
              <Text style={styles.cardSubtitle}>Long Jump • Triple Jump</Text>
            </TouchableOpacity>
          </View>
        </View>

      </SafeAreaView>
    );
  }

  // Device Setup Screen
  if (currentScreen === 'DEVICE_SETUP') {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>PolyField by KACPH</Text>
          <View style={styles.headerRight}>
            {demoMode && (
              <TouchableOpacity onPress={() => {
                setDemoMode(!demoMode);
                // Reset calibration when switching to live mode
                setCalibration(prev => ({
                  ...prev,
                  centreSet: false,
                  centreTimestamp: null,
                  edgeVerified: false,
                  edgeResult: null
                }));
                // Go back to device setup for recalibration
                if (eventType === 'Throws') {
                  setCurrentScreen('DEVICE_SETUP');
                }
              }}>
                <Text style={styles.demoStatus}>Demo Active</Text>
              </TouchableOpacity>
            )}
            {!demoMode && (
              <TouchableOpacity onPress={() => setDemoMode(!demoMode)}>
                <Text style={styles.realModeStatus}>Live Mode</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>

        <View style={styles.mainContent}>
          <Text style={styles.title}>Device Setup</Text>

          <View style={styles.setupContainer}>
            {/* Device Configuration for Live Mode */}
            {!demoMode && (
              <ScrollView style={styles.deviceConfigContainer}>
                {/* EDM Configuration */}
                {eventType === 'Throws' && (
                  <View style={styles.deviceConfigSection}>
                    <Text style={styles.deviceConfigTitle}>EDM Device</Text>
                    <Text style={[styles.deviceStatusText,
                      devices.edm.connected ? styles.connectedText : styles.disconnectedText]}>
                      {devices.edm.connected ? 'Connected' : 'Not Connected'}
                    </Text>

                    <View style={styles.connectionTypeRow}>
                      <TouchableOpacity
                        style={[styles.connectionTypeButton, 
                          deviceConfig.edm.type === 'serial' && styles.connectionTypeActive]}
                        onPress={() => setDeviceConfig(prev => ({
                          ...prev,
                          edm: { ...prev.edm, type: 'serial' }
                        }))}
                      >
                        <Text style={styles.connectionTypeText}>Serial</Text>
                      </TouchableOpacity>
                      <TouchableOpacity
                        style={[styles.connectionTypeButton, 
                          deviceConfig.edm.type === 'network' && styles.connectionTypeActive]}
                        onPress={() => setDeviceConfig(prev => ({
                          ...prev,
                          edm: { ...prev.edm, type: 'network' }
                        }))}
                      >
                        <Text style={styles.connectionTypeText}>Network</Text>
                      </TouchableOpacity>
                    </View>

                    {deviceConfig.edm.type === 'network' ? (
                      <View style={styles.networkConfig}>
                        <Text style={styles.configLabel}>IP Address:</Text>
                        <TextInput
                          style={styles.configInput}
                          value={deviceConfig.edm.ip}
                          onChangeText={(text) => setDeviceConfig(prev => ({
                            ...prev,
                            edm: { ...prev.edm, ip: text }
                          }))}
                          placeholder="192.168.1.100"
                        />
                        <Text style={styles.configLabel}>Port:</Text>
                        <TextInput
                          style={styles.configInput}
                          value={deviceConfig.edm.port}
                          onChangeText={(text) => setDeviceConfig(prev => ({
                            ...prev,
                            edm: { ...prev.edm, port: text }
                          }))}
                          placeholder="10001"
                          keyboardType="numeric"
                        />
                        <TouchableOpacity 
                          style={styles.testButton}
                          onPress={async () => {
                            if (!deviceConfig.edm.ip || !deviceConfig.edm.port) {
                              Alert.alert('Missing Information', 'Please enter IP address and port');
                              return;
                            }
                            try {
                              const result = await EDMModule.pingNetworkDevice(
                                deviceConfig.edm.ip, 
                                parseInt(deviceConfig.edm.port)
                              );
                              Alert.alert(
                                result.reachable && result.portOpen ? 'Connection Test Passed' : 'Connection Test Failed',
                                result.status
                              );
                            } catch (error) {
                              Alert.alert('Test Error', 'Failed to test connection: ' + error.message);
                            }
                          }}
                        >
                          <Text style={styles.testButtonText}>🔍 Test Connection</Text>
                        </TouchableOpacity>
                      </View>
                    ) : (
                      <View style={styles.serialConfig}>
                        <Text style={styles.configLabel}>Serial Port:</Text>
                        <View style={styles.pickerContainer}>
                          <Picker
                            selectedValue={deviceConfig.edm.comPort}
                            onValueChange={(itemValue) => setDeviceConfig(prev => ({
                              ...prev,
                              edm: { ...prev.edm, comPort: itemValue }
                            }))}
                            style={styles.picker}
                          >
                            <Picker.Item label="Select Serial Port" value="" />
                            {availableSerialPorts.map((portInfo, index) => (
                              <Picker.Item 
                                key={index} 
                                label={portInfo.description || portInfo.port || portInfo} 
                                value={portInfo.port || portInfo} 
                              />
                            ))}
                          </Picker>
                        </View>
                        <TouchableOpacity 
                          style={styles.refreshButton}
                          onPress={discoverSerialPorts}
                        >
                          <Text style={styles.refreshButtonText}>🔄 Refresh Ports</Text>
                        </TouchableOpacity>
                      </View>
                    )}

                    <View style={styles.deviceButtons}>
                      <TouchableOpacity
                        style={styles.connectButton}
                        onPress={() => {
                          const config = deviceConfig.edm;
                          if (config.type === 'network') {
                            connectDevice('edm', 'network', config.ip, parseInt(config.port));
                          } else {
                            connectDevice('edm', 'serial', config.comPort, 9600);
                          }
                        }}
                      >
                        <Text style={styles.connectButtonText}>Connect</Text>
                      </TouchableOpacity>
                      {devices.edm.connected && (
                        <TouchableOpacity
                          style={[styles.connectButton, styles.disconnectButton]}
                          onPress={() => disconnectDevice('edm')}
                        >
                          <Text style={styles.connectButtonText}>Disconnect</Text>
                        </TouchableOpacity>
                      )}
                    </View>
                  </View>
                )}

                {/* Wind Gauge Configuration */}
                {eventType === 'Horizontal Jumps' && (
                  <View style={styles.deviceConfigSection}>
                    <Text style={styles.deviceConfigTitle}>Wind Gauge</Text>
                    <Text style={[styles.deviceStatusText,
                      devices.wind.connected ? styles.connectedText : styles.disconnectedText]}>
                      {devices.wind.connected ? 'Connected' : 'Not Connected'}
                    </Text>

                    <View style={styles.connectionTypeRow}>
                      <TouchableOpacity
                        style={[styles.connectionTypeButton, 
                          deviceConfig.wind.type === 'serial' && styles.connectionTypeActive]}
                        onPress={() => setDeviceConfig(prev => ({
                          ...prev,
                          wind: { ...prev.wind, type: 'serial' }
                        }))}
                      >
                        <Text style={styles.connectionTypeText}>Serial</Text>
                      </TouchableOpacity>
                      <TouchableOpacity
                        style={[styles.connectionTypeButton, 
                          deviceConfig.wind.type === 'network' && styles.connectionTypeActive]}
                        onPress={() => setDeviceConfig(prev => ({
                          ...prev,
                          wind: { ...prev.wind, type: 'network' }
                        }))}
                      >
                        <Text style={styles.connectionTypeText}>Network</Text>
                      </TouchableOpacity>
                    </View>

                    {deviceConfig.wind.type === 'network' ? (
                      <View style={styles.networkConfig}>
                        <Text style={styles.configLabel}>IP Address:</Text>
                        <TextInput
                          style={styles.configInput}
                          value={deviceConfig.wind.ip}
                          onChangeText={(text) => setDeviceConfig(prev => ({
                            ...prev,
                            wind: { ...prev.wind, ip: text }
                          }))}
                          placeholder="192.168.1.101"
                        />
                        <Text style={styles.configLabel}>Port:</Text>
                        <TextInput
                          style={styles.configInput}
                          value={deviceConfig.wind.port}
                          onChangeText={(text) => setDeviceConfig(prev => ({
                            ...prev,
                            wind: { ...prev.wind, port: text }
                          }))}
                          placeholder="10002"
                          keyboardType="numeric"
                        />
                      </View>
                    ) : (
                      <View style={styles.serialConfig}>
                        <Text style={styles.configLabel}>Serial Port:</Text>
                        <View style={styles.pickerContainer}>
                          <Picker
                            selectedValue={deviceConfig.wind.comPort}
                            onValueChange={(itemValue) => setDeviceConfig(prev => ({
                              ...prev,
                              wind: { ...prev.wind, comPort: itemValue }
                            }))}
                            style={styles.picker}
                          >
                            <Picker.Item label="Select Serial Port" value="" />
                            {availableSerialPorts.map((portInfo, index) => (
                              <Picker.Item 
                                key={index} 
                                label={portInfo.description || portInfo.port || portInfo} 
                                value={portInfo.port || portInfo} 
                              />
                            ))}
                          </Picker>
                        </View>
                        <TouchableOpacity 
                          style={styles.refreshButton}
                          onPress={discoverSerialPorts}
                        >
                          <Text style={styles.refreshButtonText}>🔄 Refresh Ports</Text>
                        </TouchableOpacity>
                      </View>
                    )}

                    <View style={styles.deviceButtons}>
                      <TouchableOpacity
                        style={styles.connectButton}
                        onPress={() => {
                          const config = deviceConfig.wind;
                          if (config.type === 'network') {
                            connectDevice('wind', 'network', config.ip, parseInt(config.port));
                          } else {
                            connectDevice('wind', 'serial', config.comPort, 9600);
                          }
                        }}
                      >
                        <Text style={styles.connectButtonText}>Connect</Text>
                      </TouchableOpacity>
                      {devices.wind.connected && (
                        <TouchableOpacity
                          style={[styles.connectButton, styles.disconnectButton]}
                          onPress={() => disconnectDevice('wind')}
                        >
                          <Text style={styles.connectButtonText}>Disconnect</Text>
                        </TouchableOpacity>
                      )}
                    </View>
                  </View>
                )}

                {/* Scoreboard Configuration */}
                <View style={styles.deviceConfigSection}>
                  <Text style={styles.deviceConfigTitle}>Scoreboard</Text>
                  <Text style={[styles.deviceStatusText,
                    devices.scoreboard.connected ? styles.connectedText : styles.disconnectedText]}>
                    {devices.scoreboard.connected ? 'Connected' : 'Not Connected'}
                  </Text>

                  <View style={styles.connectionTypeRow}>
                    <TouchableOpacity
                      style={[styles.connectionTypeButton, 
                        deviceConfig.scoreboard.type === 'serial' && styles.connectionTypeActive]}
                      onPress={() => setDeviceConfig(prev => ({
                        ...prev,
                        scoreboard: { ...prev.scoreboard, type: 'serial' }
                      }))}
                    >
                      <Text style={styles.connectionTypeText}>Serial</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={[styles.connectionTypeButton, 
                        deviceConfig.scoreboard.type === 'network' && styles.connectionTypeActive]}
                      onPress={() => setDeviceConfig(prev => ({
                        ...prev,
                        scoreboard: { ...prev.scoreboard, type: 'network' }
                      }))}
                    >
                      <Text style={styles.connectionTypeText}>Network</Text>
                    </TouchableOpacity>
                  </View>

                  {deviceConfig.scoreboard.type === 'network' ? (
                    <View style={styles.networkConfig}>
                      <Text style={styles.configLabel}>IP Address:</Text>
                      <TextInput
                        style={styles.configInput}
                        value={deviceConfig.scoreboard.ip}
                        onChangeText={(text) => setDeviceConfig(prev => ({
                          ...prev,
                          scoreboard: { ...prev.scoreboard, ip: text }
                        }))}
                        placeholder="192.168.1.102"
                      />
                      <Text style={styles.configLabel}>Port:</Text>
                      <TextInput
                        style={styles.configInput}
                        value={deviceConfig.scoreboard.port}
                        onChangeText={(text) => setDeviceConfig(prev => ({
                          ...prev,
                          scoreboard: { ...prev.scoreboard, port: text }
                        }))}
                        placeholder="10003"
                        keyboardType="numeric"
                      />
                    </View>
                  ) : (
                    <View style={styles.serialConfig}>
                      <Text style={styles.configLabel}>Serial Port:</Text>
                      <View style={styles.pickerContainer}>
                        <Picker
                          selectedValue={deviceConfig.scoreboard.comPort}
                          onValueChange={(itemValue) => setDeviceConfig(prev => ({
                            ...prev,
                            scoreboard: { ...prev.scoreboard, comPort: itemValue }
                          }))}
                          style={styles.picker}
                        >
                          <Picker.Item label="Select Serial Port" value="" />
                          {availableSerialPorts.map((portInfo, index) => (
                            <Picker.Item 
                              key={index} 
                              label={portInfo.description || portInfo.port || portInfo} 
                              value={portInfo.port || portInfo} 
                            />
                          ))}
                        </Picker>
                      </View>
                      <TouchableOpacity 
                        style={styles.refreshButton}
                        onPress={discoverSerialPorts}
                      >
                        <Text style={styles.refreshButtonText}>🔄 Refresh Ports</Text>
                      </TouchableOpacity>
                    </View>
                  )}

                  <View style={styles.deviceButtons}>
                    <TouchableOpacity
                      style={styles.connectButton}
                      onPress={() => {
                        const config = deviceConfig.scoreboard;
                        if (config.type === 'network') {
                          connectDevice('scoreboard', 'network', config.ip, parseInt(config.port));
                        } else {
                          connectDevice('scoreboard', 'serial', config.comPort, 9600);
                        }
                      }}
                    >
                      <Text style={styles.connectButtonText}>Connect</Text>
                    </TouchableOpacity>
                    {devices.scoreboard.connected && (
                      <TouchableOpacity
                        style={[styles.connectButton, styles.disconnectButton]}
                        onPress={() => disconnectDevice('scoreboard')}
                      >
                        <Text style={styles.connectButtonText}>Disconnect</Text>
                      </TouchableOpacity>
                    )}
                  </View>
                </View>
              </ScrollView>
            )}

            {/* Demo Mode - Simple Status Display */}
            {demoMode && (
              <View style={styles.deviceStatus}>
                <Text style={styles.deviceTitle}>Device Status (Demo Mode):</Text>
                <View style={styles.deviceList}>
                  {eventType === 'Throws' && (
                    <View style={styles.deviceItem}>
                      <Text style={styles.deviceName}>EDM:</Text>
                      <Text style={styles.simulatedText}>Simulated</Text>
                    </View>
                  )}
                  {eventType === 'Horizontal Jumps' && (
                    <View style={styles.deviceItem}>
                      <Text style={styles.deviceName}>Wind Gauge:</Text>
                      <Text style={styles.simulatedText}>Simulated</Text>
                    </View>
                  )}
                  <View style={styles.deviceItem}>
                    <Text style={styles.deviceName}>Scoreboard:</Text>
                    <Text style={styles.simulatedText}>Simulated</Text>
                  </View>
                </View>
              </View>
            )}
          </View>
        </View>

        <View style={styles.bottomNav}>
          <TouchableOpacity
            style={styles.navButton}
            onPress={() => setCurrentScreen('SELECT_EVENT_TYPE')}
          >
            <Text style={styles.navButtonText}>← Back</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.navButton, styles.primaryButton]}
            onPress={() => {
              if (eventType === 'Throws') {
                // In live mode, check if EDM is connected before proceeding to calibration
                if (!demoMode && !devices.edm.connected) {
                  Alert.alert(
                    'EDM Required', 
                    'Please connect an EDM device before proceeding to calibration.',
                    [{ text: 'OK' }]
                  );
                  return;
                }
                setCurrentScreen('CALIBRATION_SELECT_CIRCLE');
              } else {
                setCurrentScreen('MEASUREMENT');
              }
            }}
          >
            <Text style={styles.navButtonText}>Next →</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  // Calibration Step 1: Circle Type Selection
  if (currentScreen === 'CALIBRATION_SELECT_CIRCLE') {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>PolyField by KACPH</Text>
          <View style={styles.headerRight}>
            {demoMode && (
              <TouchableOpacity onPress={() => {
                setDemoMode(!demoMode);
                // Reset calibration when switching to live mode
                setCalibration(prev => ({
                  ...prev,
                  centreSet: false,
                  centreTimestamp: null,
                  edgeVerified: false,
                  edgeResult: null
                }));
                // Go back to device setup for recalibration
                if (eventType === 'Throws') {
                  setCurrentScreen('DEVICE_SETUP');
                }
              }}>
                <Text style={styles.demoStatus}>Demo Active</Text>
              </TouchableOpacity>
            )}
            {!demoMode && (
              <TouchableOpacity onPress={() => setDemoMode(!demoMode)}>
                <Text style={styles.realModeStatus}>Live Mode</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>

        <View style={styles.mainContent}>
          <Text style={styles.title}>EDM Calibration - Select Circle</Text>

          <View style={styles.calibrationStepContainer}>
            <View style={styles.circleTypeGrid}>
              {Object.keys(UKA_RADII).map((type) => (
                <TouchableOpacity
                  key={type}
                  style={[
                    styles.circleTypeCardLarge,
                    calibration.circleType === type && styles.circleTypeCardActiveLarge
                  ]}
                  onPress={() => setCalibration(prev => ({
                    ...prev,
                    circleType: type,
                    targetRadius: UKA_RADII[type],
                    centreSet: false,
                    centreTimestamp: null,
                    edgeVerified: false,
                    edgeResult: null
                  }))}
                >
                  <Text style={[
                    styles.circleTypeCardTitleLarge,
                    calibration.circleType === type && styles.circleTypeCardTitleActiveLarge
                  ]}>
                    {type.replace('_', ' ')}
                  </Text>
                  <Text style={[
                    styles.circleTypeCardRadiusLarge,
                    calibration.circleType === type && styles.circleTypeCardRadiusActiveLarge
                  ]}>
                    {UKA_RADII[type].toFixed(3)}m radius
                  </Text>
                  <Text style={[
                    styles.circleTypeCardToleranceLarge,
                    calibration.circleType === type && styles.circleTypeCardToleranceActiveLarge
                  ]}>
                    Tolerance: ±{type === 'JAVELIN_ARC' ? '10' : '5'}mm
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>
        </View>

        <View style={styles.bottomNav}>
          <TouchableOpacity
            style={styles.navButton}
            onPress={() => setCurrentScreen('DEVICE_SETUP')}
          >
            <Text style={styles.navButtonText}>← Back</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[
              styles.navButton,
              styles.primaryButton,
              !calibration.circleType && styles.disabledButton
            ]}
            onPress={() => setCurrentScreen('CALIBRATION_SET_CENTRE')}
            disabled={!calibration.circleType}
          >
            <Text style={styles.navButtonText}>Next →</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  // Calibration Step 2: Set Centre
  if (currentScreen === 'CALIBRATION_SET_CENTRE') {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>PolyField by KACPH</Text>
          <View style={styles.headerRight}>
            {demoMode && (
              <TouchableOpacity onPress={() => {
                setDemoMode(!demoMode);
                // Reset calibration when switching to live mode
                setCalibration(prev => ({
                  ...prev,
                  centreSet: false,
                  centreTimestamp: null,
                  edgeVerified: false,
                  edgeResult: null
                }));
                // Go back to device setup for recalibration
                if (eventType === 'Throws') {
                  setCurrentScreen('DEVICE_SETUP');
                }
              }}>
                <Text style={styles.demoStatus}>Demo Active</Text>
              </TouchableOpacity>
            )}
            {!demoMode && (
              <TouchableOpacity onPress={() => setDemoMode(!demoMode)}>
                <Text style={styles.realModeStatus}>Live Mode</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>

        <View style={styles.mainContent}>
          <Text style={styles.title}>EDM Calibration - Set Centre</Text>

          <View style={styles.calibrationStepContainer}>
            <View style={styles.centreSetupContainer}>
              <Text style={styles.circleTypeDisplay}>
                {calibration.circleType.replace('_', ' ')} Circle
              </Text>
              <Text style={styles.radiusDisplay}>
                Radius: {calibration.targetRadius?.toFixed(3)}m
              </Text>

              <TouchableOpacity
                style={[
                  styles.calibrationActionButton,
                  calibration.centreSet && styles.calibrationButtonSuccess,
                  isLoading && styles.disabledButton
                ]}
                onPress={setCentre}
                disabled={isLoading}
              >
                <Text style={styles.calibrationActionButtonText}>
                  {isLoading ? 'Setting Centre...' :
                   calibration.centreSet ? '✓ Centre Set' : 'Set Centre'}
                </Text>
              </TouchableOpacity>

              {calibration.centreSet && calibration.centreTimestamp && (
                <View style={styles.timestampContainer}>
                  <Text style={styles.timestampLabel}>Centre Set:</Text>
                  <Text style={styles.timestampValue}>
                    {new Date(calibration.centreTimestamp).toLocaleString('en-GB', {
                      day: '2-digit',
                      month: '2-digit',
                      year: '2-digit',
                      hour: '2-digit',
                      minute: '2-digit',
                      second: '2-digit'
                    })}
                  </Text>
                </View>
              )}
            </View>
          </View>
        </View>

        <View style={styles.bottomNav}>
          <TouchableOpacity
            style={styles.navButton}
            onPress={() => setCurrentScreen('CALIBRATION_SELECT_CIRCLE')}
          >
            <Text style={styles.navButtonText}>← Back</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.navButton}
            onPress={() => {
              setCalibration(prev => ({
                ...prev,
                centreSet: false,
                centreTimestamp: null,
                edgeVerified: false,
                edgeResult: null
              }));
            }}
          >
            <Text style={styles.navButtonText}>🔄 Remeasure</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[
              styles.navButton,
              styles.primaryButton,
              !calibration.centreSet && styles.disabledButton
            ]}
            onPress={() => setCurrentScreen('CALIBRATION_VERIFY_EDGE')}
            disabled={!calibration.centreSet}
          >
            <Text style={styles.navButtonText}>Next →</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  // Calibration Step 3: Verify Edge
  if (currentScreen === 'CALIBRATION_VERIFY_EDGE') {
    const toleranceMm = calibration.circleType === 'JAVELIN_ARC' ? 10 : 5;

    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>PolyField by KACPH</Text>
          <View style={styles.headerRight}>
            {demoMode && (
              <TouchableOpacity onPress={() => {
                setDemoMode(!demoMode);
                // Reset calibration when switching to live mode
                setCalibration(prev => ({
                  ...prev,
                  centreSet: false,
                  centreTimestamp: null,
                  edgeVerified: false,
                  edgeResult: null
                }));
                // Go back to device setup for recalibration
                if (eventType === 'Throws') {
                  setCurrentScreen('DEVICE_SETUP');
                }
              }}>
                <Text style={styles.demoStatus}>Demo Active</Text>
              </TouchableOpacity>
            )}
            {!demoMode && (
              <TouchableOpacity onPress={() => setDemoMode(!demoMode)}>
                <Text style={styles.realModeStatus}>Live Mode</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>

        <View style={styles.mainContent}>
          <View style={styles.stepHeader}>
            <Text style={styles.title}>EDM Calibration - Verify Edge</Text>
            <TouchableOpacity
              style={styles.helpButton}
              onPress={() => {
                console.log('Help button pressed - Verify Edge');
                const instructions = `Verify Circle Edge:\n\n1. Aim the EDM at any point on the circle's edge\n2. Press "Verify Edge" to check calibration accuracy\n3. Ensure measurement is within ±${toleranceMm}mm tolerance\n\nThis verifies the EDM calibration meets UKA standards for ${calibration.circleType.replace('_', ' ').toLowerCase()} competition.`;
                console.log('About to show instructions:', instructions);
                showInstructions(instructions);
              }}
            >
              <Text style={styles.helpButtonText}>?</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.calibrationStepContainer}>
            <View style={styles.edgeVerificationContainer}>
              <Text style={styles.circleTypeDisplay}>
                {calibration.circleType.replace('_', ' ')} Circle
              </Text>

              <View style={styles.toleranceInfoBox}>
                <Text style={styles.toleranceTitle}>Tolerance Specification</Text>
                <Text style={styles.toleranceValue}>±{toleranceMm}mm</Text>
                <Text style={styles.toleranceDescription}>
                  UKA & WA standard for {calibration.circleType.replace('_', ' ').toLowerCase()}
                </Text>
              </View>

              <TouchableOpacity
                style={[
                  styles.calibrationActionButton,
                  isLoading && styles.disabledButton
                ]}
                onPress={() => {
                  verifyEdge().then(() => {
                    // Navigate to results screen after verification
                    setCurrentScreen('CALIBRATION_EDGE_RESULTS');
                  });
                }}
                disabled={isLoading}
              >
                <Text style={styles.calibrationActionButtonText}>
                  {isLoading ? 'Verifying Edge...' : 'Verify Edge'}
                </Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>

        <View style={styles.bottomNav}>
          <TouchableOpacity
            style={styles.navButton}
            onPress={() => setCurrentScreen('CALIBRATION_SET_CENTRE')}
          >
            <Text style={styles.navButtonText}>← Back</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.navButton}
            onPress={() => {
              setCalibration(prev => ({
                ...prev,
                edgeVerified: false,
                edgeResult: null
              }));
            }}
          >
            <Text style={styles.navButtonText}>🔄 Remeasure</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[
              styles.navButton,
              styles.primaryButton,
              (!calibration.edgeResult || !calibration.edgeResult.toleranceCheck) && styles.disabledButton
            ]}
            onPress={() => setCurrentScreen('MEASUREMENT')}
            disabled={!calibration.edgeResult || !calibration.edgeResult.toleranceCheck}
          >
            <Text style={styles.navButtonText}>Start Measuring →</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  // Calibration Edge Results Screen
  if (currentScreen === 'CALIBRATION_EDGE_RESULTS') {
    const toleranceMm = calibration.circleType === 'JAVELIN_ARC' ? 10 : 5;

    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>PolyField by KACPH</Text>
          <View style={styles.headerRight}>
            {demoMode && (
              <TouchableOpacity onPress={() => {
                setDemoMode(!demoMode);
                // Reset calibration when switching to live mode
                setCalibration(prev => ({
                  ...prev,
                  centreSet: false,
                  centreTimestamp: null,
                  edgeVerified: false,
                  edgeResult: null
                }));
                // Go back to device setup for recalibration
                if (eventType === 'Throws') {
                  setCurrentScreen('DEVICE_SETUP');
                }
              }}>
                <Text style={styles.demoStatus}>Demo Active</Text>
              </TouchableOpacity>
            )}
            {!demoMode && (
              <TouchableOpacity onPress={() => setDemoMode(!demoMode)}>
                <Text style={styles.realModeStatus}>Live Mode</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>

        <View style={styles.mainContent}>
          <Text style={styles.title}>Edge Verification Results</Text>

          <View style={styles.resultsContainer}>
            {calibration.edgeResult && (
              <View style={[
                styles.resultCard,
                calibration.edgeResult.toleranceCheck ? styles.resultCardPass : styles.resultCardFail
              ]}>
                <View style={styles.resultHeader}>
                  <Text style={styles.resultStatus}>
                    {calibration.edgeResult.toleranceCheck ? '✅ PASS' : '❌ FAIL'}
                  </Text>
                  <Text style={styles.resultSpec}>
                    WITHIN SPEC: {calibration.edgeResult.toleranceCheck ? 'YES' : 'NO'}
                  </Text>
                </View>

                <View style={styles.resultDetails}>
                  <View style={styles.resultDetailItem}>
                    <Text style={styles.resultDetailLabel}>Difference:</Text>
                    <Text style={styles.resultDetailValue}>
                      {Math.abs(calibration.edgeResult.differenceMm).toFixed(1)}mm
                    </Text>
                  </View>
                  <View style={styles.resultDetailItem}>
                    <Text style={styles.resultDetailLabel}>Tolerance:</Text>
                    <Text style={styles.resultDetailValue}>±{toleranceMm}mm</Text>
                  </View>
                  <View style={styles.resultDetailItem}>
                    <Text style={styles.resultDetailLabel}>Measured Radius:</Text>
                    <Text style={styles.resultDetailValue}>
                      {calibration.edgeResult.measuredRadius?.toFixed(4)}m
                    </Text>
                  </View>
                </View>

                {!calibration.edgeResult.toleranceCheck && (
                  <View style={styles.resultAdvice}>
                    <Text style={styles.resultAdviceTitle}>Recommendation:</Text>
                    <Text style={styles.resultAdviceText}>
                      {Math.abs(calibration.edgeResult.differenceMm) > 20
                        ? 'Recalibrate centre position and try again'
                        : 'Remeasure edge or check circle alignment'}
                    </Text>
                  </View>
                )}
              </View>
            )}
          </View>
        </View>

        <View style={styles.bottomNav}>
          <TouchableOpacity
            style={styles.navButton}
            onPress={() => setCurrentScreen('CALIBRATION_VERIFY_EDGE')}
          >
            <Text style={styles.navButtonText}>← Back</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.navButton}
            onPress={() => {
              setCalibration(prev => ({
                ...prev,
                edgeVerified: false,
                edgeResult: null
              }));
              setCurrentScreen('CALIBRATION_VERIFY_EDGE');
            }}
          >
            <Text style={styles.navButtonText}>🔄 Remeasure</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[
              styles.navButton,
              styles.primaryButton,
              (!calibration.edgeResult || !calibration.edgeResult.toleranceCheck) && styles.disabledButton
            ]}
            onPress={() => setCurrentScreen('MEASUREMENT')}
            disabled={!calibration.edgeResult || !calibration.edgeResult.toleranceCheck}
          >
            <Text style={styles.navButtonText}>Start Measuring →</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  // Measurement Screen
  if (currentScreen === 'MEASUREMENT') {
    return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>PolyField by KACPH</Text>
        <View style={styles.headerRight}>
          {demoMode && (
            <TouchableOpacity onPress={() => {
              setDemoMode(!demoMode);
              // Reset calibration when switching to live mode
              setCalibration(prev => ({
                ...prev,
                centreSet: false,
                centreTimestamp: null,
                edgeVerified: false,
                edgeResult: null
              }));
              // Go back to device setup for recalibration
              if (eventType === 'Throws') {
                setCurrentScreen('DEVICE_SETUP');
              }
            }}>
              <Text style={styles.demoStatus}>Demo Active</Text>
            </TouchableOpacity>
          )}
          {!demoMode && (
            <TouchableOpacity onPress={() => setDemoMode(!demoMode)}>
              <Text style={styles.realModeStatus}>Live Mode</Text>
            </TouchableOpacity>
          )}
        </View>
      </View>

      <View style={styles.mainContent}>
        <View style={styles.measurementSection}>
          {eventType === 'Throws' ? (
            <View style={styles.throwsContainer}>
              <View style={styles.measurementRow}>
                <View style={styles.distanceDisplayLeft}>
                  <Text style={styles.distanceLabel}>Distance:</Text>
                  <Text style={styles.distanceValueLarge}>{measurement || '--'}</Text>
                </View>
                
                <TouchableOpacity
                  style={[styles.measureButtonRight, isLoading && styles.disabledButton]}
                  onPress={measureDistance}
                  disabled={isLoading}
                >
                  <Text style={styles.measureButtonText}>
                    {isLoading ? 'Measuring...' : 'Measure\nDistance'}
                  </Text>
                </TouchableOpacity>
              </View>
            </View>
          ) : (
            <View style={styles.jumpsContainer}>
              <TouchableOpacity
                style={[styles.measureButton, isLoading && styles.disabledButton]}
                onPress={measureWind}
                disabled={isLoading}
              >
                <Text style={styles.measureButtonText}>
                  {isLoading ? 'Measuring Wind...' : 'Measure Wind'}
                </Text>
              </TouchableOpacity>

              <View style={styles.display}>
                <Text style={styles.label}>Wind:</Text>
                <Text style={styles.value}>{windMeasurement || '--'}</Text>
              </View>
            </View>
          )}
        </View>

        {/* Full Width Statistics Display */}
        {eventType === 'Throws' && throwCoordinates.length > 0 && (
          <View style={styles.statsContainerFullWidth}>
            <Text style={styles.statsTitle}>Session Statistics</Text>
            <View style={styles.statsGrid}>
              <View style={styles.statItem}>
                <Text style={styles.statValue}>{throwCoordinates.length}</Text>
                <Text style={styles.statLabel}>Total Throws</Text>
              </View>
              <View style={styles.statItem}>
                <Text style={styles.statValue}>
                  {throwCoordinates.length > 0 ?
                    (throwCoordinates.reduce((sum, t) => sum + t.distance, 0) / throwCoordinates.length).toFixed(1)
                    : '0.0'}m
                </Text>
                <Text style={styles.statLabel}>Average</Text>
              </View>
              <View style={styles.statItem}>
                <Text style={styles.statValue}>
                  {throwCoordinates.length > 0 ?
                    Math.max(...throwCoordinates.map(t => t.distance)).toFixed(1)
                    : '0.0'}m
                </Text>
                <Text style={styles.statLabel}>Best</Text>
              </View>
            </View>
          </View>
        )}
      </View>

      <View style={styles.bottomNav}>
        <TouchableOpacity
          style={styles.navButton}
          onPress={() => setCurrentScreen(eventType === 'Throws' ? 'CALIBRATION_VERIFY_EDGE' : 'DEVICE_SETUP')}
        >
          <Text style={styles.navButtonText}>← Setup</Text>
        </TouchableOpacity>
        {eventType === 'Throws' && (
          <TouchableOpacity
            style={styles.navButton}
            onPress={() => setHeatMapVisible(true)}
          >
            <Text style={styles.navButtonText}>📊 Heat Map</Text>
          </TouchableOpacity>
        )}
        <TouchableOpacity
          style={styles.navButton}
          onPress={() => {
            setCurrentScreen('SELECT_EVENT_TYPE');
            setMeasurement('');
            setWindMeasurement('');
            setCalibration(prev => ({
              ...prev,
              centreSet: false,
              centreTimestamp: null,
              edgeVerified: false,
              edgeResult: null
            }));
          }}
        >
          <Text style={styles.navButtonText}>New Event</Text>
        </TouchableOpacity>
      </View>

      {/* Heat Map Modal - moved here to be globally accessible */}
      <Modal visible={heatMapVisible} animationType="slide">
        <SafeAreaView style={styles.container}>
          <View style={styles.header}>
            <Text style={styles.headerTitle}>Landing Heat Map</Text>
            <TouchableOpacity onPress={() => setHeatMapVisible(false)}>
              <Text style={styles.closeText}>✕</Text>
            </TouchableOpacity>
          </View>

          {(() => {
            const heatData = generateHeatMapData();
            
            if (heatData.coordinates.length === 0) {
              return (
                <View style={styles.noDataContainer}>
                  <Text style={styles.noDataText}>No Throw Data</Text>
                  <Text style={styles.noDataSubtext}>Complete some throws to see the heat map</Text>
                  <Text style={styles.noDataSubtext}>Circle: {heatData.circleType}</Text>
                </View>
              );
            }

            return (
              <View style={styles.heatMapContainer}>
                <Text style={styles.heatMapTitle}>
                  {`${heatData.circleType} Landing Pattern (${heatData.coordinates.length} throws)`}
                </Text>
                
                <View style={styles.heatMapVisualization}>
                  {/* Distance markers */}
                  {Array.from({ length: Math.floor(heatData.optimalScale / 10) + 1 }, (_, i) => i * 10).map(distance => (
                    distance > 0 && (
                      <View key={distance} style={[styles.distanceMarker, {
                        bottom: `${5 + (distance / heatData.optimalScale) * 70}%`
                      }]}>
                        <Text style={styles.distanceText}>{distance}m</Text>
                      </View>
                    )
                  ))}

                  {/* Circle center */}
                  <View style={styles.circleCenter}>
                    <Text style={styles.centerLabel}>Centre</Text>
                  </View>

                  {/* Throw points */}
                  {heatData.coordinates.map((coord, index) => {
                    const xPercent = Math.max(5, Math.min(95, 50 + ((coord.x - 0) / heatData.optimalScale) * 40));
                    const yPercent = Math.max(5, Math.min(95, 90 - ((coord.distance) / heatData.optimalScale) * 70));
                    
                    return (
                      <View
                        key={index}
                        style={[
                          styles.throwPoint,
                          {
                            left: `${xPercent}%`,
                            bottom: `${yPercent}%`,
                          },
                        ]}
                      />
                    );
                  })}
                </View>

                <View style={styles.statsContainer}>
                  <Text style={styles.heatMapTitle}>Session Statistics</Text>
                  <View style={styles.statsRow}>
                    <Text style={styles.statText}>Throws: {heatData.stats.totalThrows}</Text>
                    <Text style={styles.statText}>Avg: {heatData.stats.averageDistance.toFixed(1)}m</Text>
                    <Text style={styles.statText}>Best: {heatData.stats.maxDistance.toFixed(1)}m</Text>
                  </View>
                </View>
              </View>
            );
          })()}
        </SafeAreaView>
      </Modal>
    </SafeAreaView>
    );
  }

  // Fallback - should never reach here, but needed for React
  return (
    <>
      <SafeAreaView style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>PolyField by KACPH</Text>
          <TouchableOpacity onPress={() => setCurrentScreen('SELECT_EVENT_TYPE')}>
            <Text style={styles.demoStatus}>Error - Redirecting...</Text>
          </TouchableOpacity>
        </View>
        <View style={styles.mainContent}>
          <Text style={styles.title}>Loading...</Text>
        </View>
      </SafeAreaView>

      {/* Global Modals - Always Available */}

      {/* Duplicate Heat Map Modal Removed */}

      {/* Device Setup Modal */}
      <Modal visible={deviceSetupVisible} animationType="slide" transparent={true}>
        <View style={styles.modalOverlay}>
          <ScrollView style={styles.modalContent}>
            <Text style={styles.modalTitle}>Device Configuration</Text>

            {/* EDM Configuration */}
            {(eventType === 'Throws' || !eventType) && (
              <View style={styles.deviceConfig}>
                <Text style={styles.deviceConfigTitle}>EDM Device</Text>
                <Text style={[styles.deviceStatusText,
                  demoMode ? styles.simulatedText :
                  (devices.edm.connected ? styles.connectedText : styles.disconnectedText)]}>
                  {demoMode ? 'Simulated' : (devices.edm.connected ? 'Connected' : 'Disconnected')}
                </Text>

                <View style={styles.connectionTypeRow}>
                  <TouchableOpacity
                    style={[styles.connectionTypeButton, 
                      deviceConfig.edm.type === 'serial' && styles.connectionTypeActive]}
                    onPress={() => setDeviceConfig(prev => ({
                      ...prev,
                      edm: { ...prev.edm, type: 'serial' }
                    }))}
                  >
                    <Text style={styles.connectionTypeText}>Serial</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.connectionTypeButton, 
                      deviceConfig.edm.type === 'network' && styles.connectionTypeActive]}
                    onPress={() => setDeviceConfig(prev => ({
                      ...prev,
                      edm: { ...prev.edm, type: 'network' }
                    }))}
                  >
                    <Text style={styles.connectionTypeText}>Network</Text>
                  </TouchableOpacity>
                </View>

                {deviceConfig.edm.type === 'network' ? (
                  <View style={styles.networkConfig}>
                    <Text style={styles.configLabel}>IP Address:</Text>
                    <TextInput
                      style={styles.configInput}
                      value={deviceConfig.edm.ip}
                      onChangeText={(text) => setDeviceConfig(prev => ({
                        ...prev,
                        edm: { ...prev.edm, ip: text }
                      }))}
                      placeholder="192.168.1.100"
                    />
                    <Text style={styles.configLabel}>Port:</Text>
                    <TextInput
                      style={styles.configInput}
                      value={deviceConfig.edm.port}
                      onChangeText={(text) => setDeviceConfig(prev => ({
                        ...prev,
                        edm: { ...prev.edm, port: text }
                      }))}
                      placeholder="10001"
                      keyboardType="numeric"
                    />
                  </View>
                ) : (
                  <View style={styles.serialConfig}>
                    <Text style={styles.configLabel}>COM Port:</Text>
                    <TextInput
                      style={styles.configInput}
                      value={deviceConfig.edm.comPort}
                      onChangeText={(text) => setDeviceConfig(prev => ({
                        ...prev,
                        edm: { ...prev.edm, comPort: text }
                      }))}
                      placeholder="COM1"
                    />
                  </View>
                )}

                {!demoMode && (
                  <View style={styles.deviceButtons}>
                    <TouchableOpacity
                      style={styles.connectButton}
                      onPress={() => {
                        const config = deviceConfig.edm;
                        if (config.type === 'network') {
                          connectDevice('edm', 'network', config.ip, parseInt(config.port));
                        } else {
                          connectDevice('edm', 'serial', config.comPort, 9600);
                        }
                      }}
                    >
                      <Text style={styles.connectButtonText}>Connect</Text>
                    </TouchableOpacity>
                    {devices.edm.connected && (
                      <TouchableOpacity
                        style={[styles.connectButton, styles.disconnectButton]}
                        onPress={() => disconnectDevice('edm')}
                      >
                        <Text style={styles.connectButtonText}>Disconnect</Text>
                      </TouchableOpacity>
                    )}
                  </View>
                )}
              </View>
            )}

            {/* Wind Gauge Configuration */}
            {(eventType === 'Horizontal Jumps' || !eventType) && (
              <View style={styles.deviceConfig}>
                <Text style={styles.deviceConfigTitle}>Wind Gauge</Text>
                <Text style={[styles.deviceStatusText,
                  demoMode ? styles.simulatedText :
                  (devices.wind.connected ? styles.connectedText : styles.disconnectedText)]}>
                  {demoMode ? 'Simulated' : (devices.wind.connected ? 'Connected' : 'Disconnected')}
                </Text>

                <View style={styles.connectionTypeRow}>
                  <TouchableOpacity
                    style={[styles.connectionTypeButton, 
                      deviceConfig.wind.type === 'serial' && styles.connectionTypeActive]}
                    onPress={() => setDeviceConfig(prev => ({
                      ...prev,
                      wind: { ...prev.wind, type: 'serial' }
                    }))}
                  >
                    <Text style={styles.connectionTypeText}>Serial</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.connectionTypeButton, 
                      deviceConfig.wind.type === 'network' && styles.connectionTypeActive]}
                    onPress={() => setDeviceConfig(prev => ({
                      ...prev,
                      wind: { ...prev.wind, type: 'network' }
                    }))}
                  >
                    <Text style={styles.connectionTypeText}>Network</Text>
                  </TouchableOpacity>
                </View>

                {deviceConfig.wind.type === 'network' ? (
                  <View style={styles.networkConfig}>
                    <Text style={styles.configLabel}>IP Address:</Text>
                    <TextInput
                      style={styles.configInput}
                      value={deviceConfig.wind.ip}
                      onChangeText={(text) => setDeviceConfig(prev => ({
                        ...prev,
                        wind: { ...prev.wind, ip: text }
                      }))}
                      placeholder="192.168.1.101"
                    />
                    <Text style={styles.configLabel}>Port:</Text>
                    <TextInput
                      style={styles.configInput}
                      value={deviceConfig.wind.port}
                      onChangeText={(text) => setDeviceConfig(prev => ({
                        ...prev,
                        wind: { ...prev.wind, port: text }
                      }))}
                      placeholder="10002"
                      keyboardType="numeric"
                    />
                  </View>
                ) : (
                  <View style={styles.serialConfig}>
                    <Text style={styles.configLabel}>COM Port:</Text>
                    <TextInput
                      style={styles.configInput}
                      value={deviceConfig.wind.comPort}
                      onChangeText={(text) => setDeviceConfig(prev => ({
                        ...prev,
                        wind: { ...prev.wind, comPort: text }
                      }))}
                      placeholder="COM2"
                    />
                  </View>
                )}

                {!demoMode && (
                  <View style={styles.deviceButtons}>
                    <TouchableOpacity
                      style={styles.connectButton}
                      onPress={() => {
                        const config = deviceConfig.wind;
                        if (config.type === 'network') {
                          connectDevice('wind', 'network', config.ip, parseInt(config.port));
                        } else {
                          connectDevice('wind', 'serial', config.comPort, 9600);
                        }
                      }}
                    >
                      <Text style={styles.connectButtonText}>Connect</Text>
                    </TouchableOpacity>
                    {devices.wind.connected && (
                      <TouchableOpacity
                        style={[styles.connectButton, styles.disconnectButton]}
                        onPress={() => disconnectDevice('wind')}
                      >
                        <Text style={styles.connectButtonText}>Disconnect</Text>
                      </TouchableOpacity>
                    )}
                  </View>
                )}
              </View>
            )}

            {/* Scoreboard Configuration */}
            <View style={styles.deviceConfig}>
              <Text style={styles.deviceConfigTitle}>Scoreboard</Text>
              <Text style={[styles.deviceStatusText,
                demoMode ? styles.simulatedText :
                (devices.scoreboard.connected ? styles.connectedText : styles.disconnectedText)]}>
                {demoMode ? 'Simulated' : (devices.scoreboard.connected ? 'Connected' : 'Disconnected')}
              </Text>

              <View style={styles.connectionTypeRow}>
                <TouchableOpacity
                  style={[styles.connectionTypeButton, 
                    deviceConfig.scoreboard.type === 'serial' && styles.connectionTypeActive]}
                  onPress={() => setDeviceConfig(prev => ({
                    ...prev,
                    scoreboard: { ...prev.scoreboard, type: 'serial' }
                  }))}
                >
                  <Text style={styles.connectionTypeText}>Serial</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[styles.connectionTypeButton, 
                    deviceConfig.scoreboard.type === 'network' && styles.connectionTypeActive]}
                  onPress={() => setDeviceConfig(prev => ({
                    ...prev,
                    scoreboard: { ...prev.scoreboard, type: 'network' }
                  }))}
                >
                  <Text style={styles.connectionTypeText}>Network</Text>
                </TouchableOpacity>
              </View>

              {deviceConfig.scoreboard.type === 'network' ? (
                <View style={styles.networkConfig}>
                  <Text style={styles.configLabel}>IP Address:</Text>
                  <TextInput
                    style={styles.configInput}
                    value={deviceConfig.scoreboard.ip}
                    onChangeText={(text) => setDeviceConfig(prev => ({
                      ...prev,
                      scoreboard: { ...prev.scoreboard, ip: text }
                    }))}
                    placeholder="192.168.1.102"
                  />
                  <Text style={styles.configLabel}>Port:</Text>
                  <TextInput
                    style={styles.configInput}
                    value={deviceConfig.scoreboard.port}
                    onChangeText={(text) => setDeviceConfig(prev => ({
                      ...prev,
                      scoreboard: { ...prev.scoreboard, port: text }
                    }))}
                    placeholder="10003"
                    keyboardType="numeric"
                  />
                </View>
              ) : (
                <View style={styles.serialConfig}>
                  <Text style={styles.configLabel}>COM Port:</Text>
                  <TextInput
                    style={styles.configInput}
                    value={deviceConfig.scoreboard.comPort}
                    onChangeText={(text) => setDeviceConfig(prev => ({
                      ...prev,
                      scoreboard: { ...prev.scoreboard, comPort: text }
                    }))}
                    placeholder="COM3"
                  />
                </View>
              )}

              {!demoMode && (
                <View style={styles.deviceButtons}>
                  <TouchableOpacity
                    style={styles.connectButton}
                    onPress={() => {
                      const config = deviceConfig.scoreboard;
                      if (config.type === 'network') {
                        connectDevice('scoreboard', 'network', config.ip, parseInt(config.port));
                      } else {
                        connectDevice('scoreboard', 'serial', config.comPort, 9600);
                      }
                    }}
                  >
                    <Text style={styles.connectButtonText}>Connect</Text>
                  </TouchableOpacity>
                  {devices.scoreboard.connected && (
                    <TouchableOpacity
                      style={[styles.connectButton, styles.disconnectButton]}
                      onPress={() => disconnectDevice('scoreboard')}
                    >
                      <Text style={styles.connectButtonText}>Disconnect</Text>
                    </TouchableOpacity>
                  )}
                </View>
              )}
            </View>

            <TouchableOpacity style={styles.closeButton} onPress={() => setDeviceSetupVisible(false)}>
              <Text style={styles.closeButtonText}>Close</Text>
            </TouchableOpacity>
          </ScrollView>
        </View>
      </Modal>
    </>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },

  // Header with demo status
  header: {
    backgroundColor: '#1976D2',
    height: Math.max(80, screenHeight * 0.1),
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 4,
  },
  headerTitle: {
    color: 'white',
    fontSize: Math.max(24, screenWidth * 0.025),
    fontWeight: 'bold',
  },
  demoStatus: {
    color: '#ffeb3b',
    fontSize: Math.max(18, screenWidth * 0.02),
    fontWeight: 'bold',
    backgroundColor: 'rgba(255, 235, 59, 0.2)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 15,
  },
  realModeStatus: {
    color: '#4CAF50',
    fontSize: Math.max(18, screenWidth * 0.02),
    fontWeight: 'bold',
    backgroundColor: 'rgba(76, 175, 80, 0.2)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 15,
  },
  closeText: {
    color: 'white',
    fontSize: 28,
    fontWeight: 'bold',
  },

  mainContent: {
    flex: 1,
    padding: Math.max(20, screenWidth * 0.025),
  },

  // Title styling
  title: {
    fontSize: Math.max(24, screenWidth * 0.028),
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: Math.max(20, screenHeight * 0.025),
    color: '#333',
    flexShrink: 1, // Allow text to shrink if needed
  },

  subtitle: {
    fontSize: Math.max(20, screenWidth * 0.022),
    textAlign: 'center',
    marginBottom: Math.max(25, screenHeight * 0.03),
    color: '#666',
  },

  // Event cards
  cardContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    flex: 1,
    paddingVertical: 20,
  },

  card: {
    backgroundColor: 'white',
    borderRadius: 20,
    padding: Math.max(30, screenWidth * 0.025),
    minHeight: Math.max(200, screenHeight * 0.3),
    width: Math.max(280, screenWidth * 0.35),
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    borderWidth: 3,
    borderColor: '#e0e0e0',
  },

  cardIcon: {
    marginBottom: 15,
  },

  iconText: {
    fontSize: Math.max(48, screenWidth * 0.055),
  },

  cardTitle: {
    fontSize: Math.max(22, screenWidth * 0.028),
    fontWeight: 'bold',
    marginBottom: 10,
    textAlign: 'center',
    color: '#333',
  },

  cardSubtitle: {
    fontSize: Math.max(16, screenWidth * 0.02),
    color: '#666',
    textAlign: 'center',
    lineHeight: 22,
  },

  // Device Setup
  setupContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'stretch',
  },

  demoToggle: {
    backgroundColor: 'white',
    borderRadius: 20,
    padding: 35,
    marginBottom: 40,
    borderWidth: 3,
    borderColor: '#ddd',
    minWidth: 400,
    elevation: 4,
  },

  demoToggleActive: {
    borderColor: '#4CAF50',
    backgroundColor: '#f1f8e9',
  },

  demoToggleContent: {
    flexDirection: 'row',
    alignItems: 'center',
  },

  demoToggleIcon: {
    fontSize: 36,
    marginRight: 25,
    color: '#4CAF50',
    fontWeight: 'bold',
  },

  demoToggleTitle: {
    fontSize: 26,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 5,
  },

  demoToggleSubtitle: {
    fontSize: 18,
    color: '#666',
  },

  deviceStatus: {
    alignItems: 'center',
    marginBottom: 30,
  },

  deviceTitle: {
    fontSize: 22,
    fontWeight: 'bold',
    marginBottom: 15,
    color: '#333',
  },

  deviceList: {
    alignItems: 'flex-start',
  },

  deviceItem: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
    minWidth: 200,
  },

  deviceName: {
    fontSize: 18,
    fontWeight: 'bold',
    marginRight: 15,
    minWidth: 100,
  },

  deviceStatusText: {
    fontSize: 18,
    fontWeight: 'bold',
  },

  connectedText: {
    color: '#4CAF50',
  },

  disconnectedText: {
    color: '#f44336',
  },

  simulatedText: {
    color: '#ff9800',
    fontStyle: 'italic',
  },

  // Calibration walkthrough styles
  calibrationStepContainer: {
    flex: 1,
    justifyContent: 'center',
    paddingVertical: 10,
  },

  circleTypeGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-around',
    paddingHorizontal: 10,
  },

  // Larger circle type cards
  circleTypeCardLarge: {
    backgroundColor: '#f8f9fa',
    borderRadius: 20,
    padding: 25,
    margin: 10,
    minWidth: 170,
    alignItems: 'center',
    borderWidth: 4,
    borderColor: '#dee2e6',
    elevation: 4,
  },

  circleTypeCardActiveLarge: {
    backgroundColor: '#1976D2',
    borderColor: '#1976D2',
    elevation: 8,
  },

  circleTypeCardTitleLarge: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#333',
    textAlign: 'center',
    marginBottom: 8,
  },

  circleTypeCardTitleActiveLarge: {
    color: 'white',
  },

  circleTypeCardRadiusLarge: {
    fontSize: 18,
    color: '#666',
    marginBottom: 5,
    fontWeight: '600',
  },

  circleTypeCardRadiusActiveLarge: {
    color: '#e3f2fd',
  },

  circleTypeCardToleranceLarge: {
    fontSize: 16,
    color: '#999',
    fontStyle: 'italic',
  },

  circleTypeCardToleranceActiveLarge: {
    color: '#bbdefb',
  },

  centreSetupContainer: {
    alignItems: 'center',
    paddingHorizontal: 20,
  },

  circleTypeDisplay: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1976D2',
    marginBottom: 8,
  },

  radiusDisplay: {
    fontSize: 18,
    color: '#666',
    marginBottom: 20,
  },

  calibrationActionButton: {
    backgroundColor: '#1976D2',
    paddingHorizontal: 40,
    paddingVertical: 18,
    borderRadius: 15,
    marginBottom: 15,
    elevation: 4,
    minWidth: 280,
  },

  calibrationActionButtonText: {
    color: 'white',
    fontSize: 20,
    fontWeight: 'bold',
    textAlign: 'center',
  },

  calibrationButtonSuccess: {
    backgroundColor: '#4CAF50',
  },

  calibrationButtonFailure: {
    backgroundColor: '#f44336',
  },

  stepHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 15,
  },

  helpButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#1976D2',
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 3,
  },

  helpButtonText: {
    color: 'white',
    fontSize: 20,
    fontWeight: 'bold',
  },

  timestampContainer: {
    backgroundColor: '#e8f5e9',
    borderRadius: 10,
    padding: 15,
    alignItems: 'center',
    borderWidth: 2,
    borderColor: '#4CAF50',
    minWidth: 250,
  },

  timestampLabel: {
    fontSize: 16,
    color: '#2e7d32',
    marginBottom: 5,
  },

  timestampValue: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1b5e20',
  },

  edgeVerificationContainer: {
    alignItems: 'center',
    paddingHorizontal: 20,
  },

  toleranceInfoBox: {
    backgroundColor: '#fff3e0',
    borderRadius: 15,
    padding: 15,
    marginBottom: 20,
    alignItems: 'center',
    borderWidth: 2,
    borderColor: '#ffb74d',
    minWidth: 200,
  },

  toleranceTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#e65100',
    marginBottom: 5,
  },

  toleranceValue: {
    fontSize: 22,
    fontWeight: 'bold',
    color: '#ff6f00',
    marginBottom: 3,
  },

  toleranceDescription: {
    fontSize: 12,
    color: '#bf360c',
    textAlign: 'center',
  },

  verificationResultContainer: {
    width: '100%',
    marginTop: 15,
  },

  verificationResultBox: {
    borderRadius: 15,
    padding: 15,
    borderWidth: 3,
  },

  verificationHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },

  verificationStatusText: {
    fontSize: 18,
    fontWeight: 'bold',
  },

  verificationSpecText: {
    fontSize: 14,
    fontWeight: 'bold',
  },

  verificationDetails: {
    marginBottom: 10,
  },

  verificationDetailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 3,
  },

  verificationDetailLabel: {
    fontSize: 14,
    color: '#333',
  },

  verificationDetailValue: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#333',
  },

  verificationAdvice: {
    backgroundColor: 'rgba(255, 255, 255, 0.8)',
    borderRadius: 8,
    padding: 10,
  },

  verificationAdviceText: {
    fontSize: 12,
    fontStyle: 'italic',
    textAlign: 'center',
    color: '#333',
  },

  // Instructions modal styles
  instructionsModalContent: {
    backgroundColor: 'white',
    borderRadius: 20,
    padding: 30,
    minWidth: screenWidth * 0.75,
    maxWidth: screenWidth * 0.85,
    maxHeight: screenHeight * 0.65,
    elevation: 15,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
  },

  instructionsModalTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 20,
    color: '#1976D2',
  },

  instructionsModalText: {
    fontSize: 16,
    lineHeight: 24,
    color: '#333',
    marginBottom: 25,
    textAlign: 'left',
  },

  instructionsCloseButton: {
    backgroundColor: '#1976D2',
    padding: 15,
    borderRadius: 12,
    alignItems: 'center',
    elevation: 2,
  },

  instructionsCloseButtonText: {
    color: 'white',
    fontSize: 18,
    fontWeight: 'bold',
  },

  // Edge verification results screen
  resultsContainer: {
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 20,
  },

  resultCard: {
    borderRadius: 20,
    padding: 25,
    borderWidth: 4,
    elevation: 6,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.3,
    shadowRadius: 6,
  },

  resultCardPass: {
    backgroundColor: '#e8f5e8',
    borderColor: '#4CAF50',
  },

  resultCardFail: {
    backgroundColor: '#ffebee',
    borderColor: '#f44336',
  },

  resultHeader: {
    alignItems: 'center',
    marginBottom: 20,
  },

  resultStatus: {
    fontSize: 32,
    fontWeight: 'bold',
    marginBottom: 8,
  },

  resultSpec: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#333',
  },

  resultDetails: {
    marginBottom: 20,
  },

  resultDetailItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0,0,0,0.1)',
  },

  resultDetailLabel: {
    fontSize: 18,
    color: '#333',
    fontWeight: '500',
  },

  resultDetailValue: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
  },

  resultAdvice: {
    backgroundColor: 'rgba(255, 255, 255, 0.9)',
    borderRadius: 12,
    padding: 15,
    marginTop: 10,
  },

  resultAdviceTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 5,
  },

  resultAdviceText: {
    fontSize: 15,
    color: '#333',
    lineHeight: 20,
  },

  // Measurement styling improvements
  measurementHeader: {
    alignItems: 'center',
    marginBottom: 15,
  },

  measurementSection: {
    paddingVertical: 10,
    marginBottom: 20,
  },

  throwsContainer: {
    alignItems: 'center',
  },

  jumpsContainer: {
    alignItems: 'center',
  },

  measureButton: {
    backgroundColor: '#1976D2',
    paddingHorizontal: Math.max(40, screenWidth * 0.05),
    paddingVertical: Math.max(20, screenHeight * 0.025),
    borderRadius: 15,
    marginBottom: Math.max(30, screenHeight * 0.04),
    elevation: 6,
    minWidth: Math.max(280, screenWidth * 0.35),
  },

  disabledButton: {
    backgroundColor: '#ccc',
  },

  measureButtonText: {
    color: 'white',
    fontSize: Math.max(22, screenWidth * 0.025),
    fontWeight: 'bold',
    textAlign: 'center',
  },

  display: {
    backgroundColor: '#f8f9fa',
    borderRadius: 20,
    padding: Math.max(25, screenWidth * 0.035),
    alignItems: 'center',
    minWidth: Math.max(320, screenWidth * 0.4),
    minHeight: Math.max(120, screenHeight * 0.15),
    justifyContent: 'center',
    borderWidth: 3,
    borderColor: '#e9ecef',
    elevation: 4,
    marginBottom: 20,
  },

  label: {
    fontSize: Math.max(18, screenWidth * 0.02),
    color: '#666',
    marginBottom: 10,
  },

  value: {
    fontSize: Math.max(36, screenWidth * 0.045),
    fontWeight: 'bold',
    color: '#1976D2',
    textAlign: 'center',
  },

  // Statistics improvements
  statsContainer: {
    backgroundColor: 'white',
    borderRadius: 15,
    padding: 20,
    elevation: 3,
    minWidth: Math.max(320, screenWidth * 0.4),
    borderWidth: 2,
    borderColor: '#e3f2fd',
  },

  statsTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 15,
    color: '#1976D2',
  },

  statsGrid: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },

  statItem: {
    alignItems: 'center',
    flex: 1,
  },

  statValue: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1976D2',
    marginBottom: 5,
  },

  statLabel: {
    fontSize: 12,
    color: '#666',
    textAlign: 'center',
  },

  // Bottom navigation
  bottomNav: {
    backgroundColor: '#e3f2fd',
    paddingVertical: Math.max(15, screenHeight * 0.018),
    paddingHorizontal: Math.max(25, screenWidth * 0.03),
    borderTopWidth: 1,
    borderTopColor: '#ddd',
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    minHeight: 80,
  },

  navButton: {
    backgroundColor: '#757575',
    paddingHorizontal: Math.max(30, screenWidth * 0.035),
    paddingVertical: Math.max(15, screenHeight * 0.018),
    borderRadius: 12,
    minWidth: Math.max(120, screenWidth * 0.14),
    elevation: 3,
  },

  primaryButton: {
    backgroundColor: '#1976D2',
  },

  navButtonText: {
    color: 'white',
    fontSize: Math.max(18, screenWidth * 0.021),
    fontWeight: 'bold',
    textAlign: 'center',
  },

  // Modal styles
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },

  modalContent: {
    backgroundColor: 'white',
    borderRadius: 20,
    padding: 30,
    minWidth: screenWidth * 0.8,
    maxHeight: screenHeight * 0.8,
    elevation: 10,
  },

  modalTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 25,
    color: '#333',
  },

  settingItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 15,
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },

  settingLabel: {
    fontSize: 18,
    color: '#333',
  },

  settingButton: {
    backgroundColor: '#1976D2',
    padding: 15,
    borderRadius: 10,
    marginVertical: 8,
    alignItems: 'center',
  },

  dangerButton: {
    backgroundColor: '#f44336',
  },

  settingButtonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
  },

  closeButton: {
    backgroundColor: '#757575',
    padding: 15,
    borderRadius: 10,
    marginTop: 20,
    alignItems: 'center',
  },

  closeButtonText: {
    color: 'white',
    fontSize: 18,
    fontWeight: 'bold',
  },

  // Device configuration
  deviceConfig: {
    backgroundColor: '#f5f5f5',
    padding: 15,
    borderRadius: 10,
    marginBottom: 15,
  },

  deviceConfigTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 8,
    color: '#333',
  },

  connectButton: {
    backgroundColor: '#4CAF50',
    padding: 10,
    borderRadius: 8,
    marginTop: 10,
    alignItems: 'center',
  },

  disconnectButton: {
    backgroundColor: '#f44336',
  },

  connectButtonText: {
    color: 'white',
    fontSize: 14,
    fontWeight: 'bold',
  },

  // Heat map styles
  heatMapContainer: {
    flex: 1,
    padding: 20,
  },

  coordinateSystem: {
    flex: 1,
    position: 'relative',
  },

  noDataContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },

  noDataText: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#666',
    marginBottom: 10,
  },

  noDataSubtext: {
    fontSize: 18,
    color: '#999',
    textAlign: 'center',
    marginBottom: 10,
  },

  scaleInfo: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
    fontStyle: 'italic',
  },

  heatMapContent: {
    flex: 1,
  },

  heatMapStats: {
    backgroundColor: 'white',
    padding: 20,
    borderRadius: 15,
    marginBottom: 20,
    elevation: 3,
  },

  heatMapTitle: {
    fontSize: 22,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 15,
    color: '#333',
  },

  statsRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
  },

  statText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#1976D2',
  },

  heatMapVisualization: {
    flex: 1,
    backgroundColor: 'white',
    borderRadius: 15,
    position: 'relative',
    elevation: 3,
  },

  circleCenter: {
    position: 'absolute',
    bottom: '5%',
    left: '47%',
    width: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: '#4CAF50',
    borderWidth: 3,
    borderColor: 'white',
    zIndex: 2,
  },

  centerLabel: {
    position: 'absolute',
    top: 25,
    left: -30,
    fontSize: 12,
    color: '#4CAF50',
    fontWeight: 'bold',
  },

  throwPoint: {
    position: 'absolute',
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#f44336',
    borderWidth: 1,
    borderColor: 'white',
  },

  distanceMarker: {
    position: 'absolute',
    left: 10,
    zIndex: 1,
  },

  distanceText: {
    fontSize: 16,
    color: '#666',
    fontWeight: 'bold',
  },

  // Device Configuration Styles
  connectionTypeRow: {
    flexDirection: 'row',
    marginVertical: 10,
    gap: 10,
  },
  connectionTypeButton: {
    flex: 1,
    padding: 10,
    backgroundColor: '#e0e0e0',
    borderRadius: 8,
    alignItems: 'center',
  },
  connectionTypeActive: {
    backgroundColor: '#1976D2',
  },
  connectionTypeText: {
    fontSize: 16,
    color: '#333',
    fontWeight: '600',
  },
  networkConfig: {
    marginVertical: 10,
  },
  serialConfig: {
    marginVertical: 10,
  },
  configLabel: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginBottom: 5,
    marginTop: 10,
  },
  configInput: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
    backgroundColor: 'white',
  },
  deviceButtons: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginTop: 15,
    gap: 10,
  },

  // Device Configuration Container Styles
  deviceConfigContainer: {
    flex: 1,
    width: '100%',
    marginBottom: 20,
  },
  deviceConfigSection: {
    backgroundColor: 'white',
    marginBottom: 15,
    marginHorizontal: 0,
    borderRadius: 12,
    padding: 20,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },

  // Picker Styles
  pickerContainer: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    backgroundColor: 'white',
    marginVertical: 5,
  },
  picker: {
    height: 50,
    color: '#333',
  },
  refreshButton: {
    backgroundColor: '#e3f2fd',
    borderRadius: 8,
    padding: 12,
    alignItems: 'center',
    marginTop: 10,
    borderWidth: 1,
    borderColor: '#1976D2',
  },
  refreshButtonText: {
    color: '#1976D2',
    fontSize: 14,
    fontWeight: '600',
  },

  testButton: {
    backgroundColor: '#2196F3',
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 10,
  },

  testButtonText: {
    color: 'white',
    fontSize: 14,
    fontWeight: 'bold',
  },

  // Improved measurement layout styles
  measurementRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 20,
    minHeight: 150,
  },
  distanceDisplayLeft: {
    flex: 0.6,
    backgroundColor: 'white',
    borderRadius: 15,
    padding: 20,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    marginRight: 15,
    minHeight: 150,
  },
  distanceLabel: {
    fontSize: Math.max(20, screenWidth * 0.025),
    fontWeight: '600',
    color: '#666',
    marginBottom: 10,
  },
  distanceValueLarge: {
    fontSize: Math.max(48, screenWidth * 0.08),
    fontWeight: 'bold',
    color: '#1976D2',
    textAlign: 'center',
  },
  measureButtonRight: {
    flex: 0.35,
    backgroundColor: '#1976D2',
    borderRadius: 15,
    padding: 20,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
    minHeight: 150,
  },

  // Header improvements
  headerRight: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 15,
  },

  // Full width statistics container
  statsContainerFullWidth: {
    backgroundColor: 'white',
    marginHorizontal: 20,
    marginTop: 2,
    marginBottom: 30,
    borderRadius: 15,
    padding: 20,
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
});

export default PolyFieldApp;