import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  StatusBar,
  Dimensions,
  TextInput,
  Alert,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { Picker } from '@react-native-picker/picker';

// Import our utilities
import { UKA_RADII, TOLERANCES, STORAGE_KEYS } from './src/config/constants';
import SerialManager from './src/utils/SerialManager';

const { width: screenWidth, height: screenHeight } = Dimensions.get('window');

// Large button component optimized for elderly/outdoor use
const LargeButton = ({ title, onPress, disabled = false, variant = 'primary', icon, style }) => {
  const buttonStyles = [
    styles.largeButton,
    variant === 'secondary' && styles.secondaryButton,
    variant === 'danger' && styles.dangerButton,
    variant === 'success' && styles.successButton,
    disabled && styles.disabledButton,
    style
  ];

  return (
    <TouchableOpacity 
      style={buttonStyles} 
      onPress={onPress} 
      disabled={disabled}
      activeOpacity={0.7}
    >
      <View style={styles.buttonContent}>
        {icon && <Icon name={icon} size={36} color={disabled ? '#999' : '#fff'} style={styles.buttonIcon} />}
        <Text style={[styles.largeButtonText, disabled && styles.disabledButtonText]}>
          {title}
        </Text>
      </View>
    </TouchableOpacity>
  );
};

// Large card component for event selection
const EventCard = ({ title, subtitle, icon, onPress, style }) => (
  <TouchableOpacity style={[styles.eventCard, style]} onPress={onPress} activeOpacity={0.7}>
    <Icon name={icon} size={80} color="#1976D2" style={styles.eventCardIcon} />
    <Text style={styles.eventCardTitle}>{title}</Text>
    <Text style={styles.eventCardSubtitle}>{subtitle}</Text>
  </TouchableOpacity>
);

// Large input field component
const LargeInput = ({ label, value, onChangeText, placeholder, style }) => (
  <View style={[styles.inputContainer, style]}>
    {label && <Text style={styles.inputLabel}>{label}</Text>}
    <TextInput
      style={styles.largeInput}
      value={value}
      onChangeText={onChangeText}
      placeholder={placeholder}
      placeholderTextColor="#999"
    />
  </View>
);

// Large picker component
const LargePicker = ({ label, selectedValue, onValueChange, items, style }) => (
  <View style={[styles.pickerContainer, style]}>
    {label && <Text style={styles.inputLabel}>{label}</Text>}
    <View style={styles.pickerWrapper}>
      <Picker
        selectedValue={selectedValue}
        onValueChange={onValueChange}
        style={styles.largePicker}
        itemStyle={styles.pickerItem}
      >
        {items.map((item, index) => (
          <Picker.Item key={index} label={item.label} value={item.value} />
        ))}
      </Picker>
    </View>
  </View>
);

// Status bar component
const StatusDisplay = ({ message, type = 'info' }) => (
  <View style={[styles.statusBar, type === 'error' && styles.statusBarError]}>
    <Text style={styles.statusText}>{message}</Text>
  </View>
);

// Main App Component
const PolyFieldApp = () => {
  // State management
  const [currentScreen, setCurrentScreen] = useState('SELECT_EVENT_TYPE');
  const [eventType, setEventType] = useState(null);
  const [demoMode, setDemoMode] = useState(true); // Start in demo mode
  const [statusMessage, setStatusMessage] = useState('Ready - Demo Mode Active');
  const [statusType, setStatusType] = useState('info');
  
  // Device connection states
  const [devices, setDevices] = useState({
    edm: { connected: false, type: 'serial', port: '/dev/ttyUSB0', ip: '192.168.1.100', tcpPort: '10001' },
    wind: { connected: false, type: 'serial', port: '/dev/ttyUSB1', ip: '192.168.1.102', tcpPort: '10001' },
    scoreboard: { connected: false, type: 'serial', port: '/dev/ttyUSB2', ip: '192.168.1.101', tcpPort: '10001' }
  });

  // Calibration state
  const [calibration, setCalibration] = useState({
    circleType: 'SHOT',
    targetRadius: UKA_RADII.SHOT,
    centreSet: false,
    edgeVerified: false,
    edgeVerificationResult: null
  });

  // Measurement state
  const [currentMeasurement, setCurrentMeasurement] = useState('');
  const [isMeasuring, setIsMeasuring] = useState(false);

  // Load saved data on app start
  useEffect(() => {
    loadAppData();
  }, []);

  const loadAppData = async () => {
    try {
      const savedSettings = await AsyncStorage.getItem(STORAGE_KEYS.APP_SETTINGS);
      if (savedSettings) {
        const settings = JSON.parse(savedSettings);
        setDemoMode(settings.demoMode !== false); // Default to true
        setEventType(settings.eventType);
      }

      const savedConnections = await AsyncStorage.getItem(STORAGE_KEYS.CONNECTION_DETAILS);
      if (savedConnections) {
        setDevices(JSON.parse(savedConnections));
      }

      const savedCalibration = await AsyncStorage.getItem(STORAGE_KEYS.CALIBRATION_DATA);
      if (savedCalibration) {
        setCalibration(JSON.parse(savedCalibration));
      }
    } catch (error) {
      console.error('Error loading app data:', error);
    }
  };

  const saveAppData = async () => {
    try {
      await AsyncStorage.setItem(STORAGE_KEYS.APP_SETTINGS, JSON.stringify({
        demoMode,
        eventType
      }));
      await AsyncStorage.setItem(STORAGE_KEYS.CONNECTION_DETAILS, JSON.stringify(devices));
      await AsyncStorage.setItem(STORAGE_KEYS.CALIBRATION_DATA, JSON.stringify(calibration));
    } catch (error) {
      console.error('Error saving app data:', error);
    }
  };

  // Save data whenever state changes
  useEffect(() => {
    saveAppData();
  }, [demoMode, eventType, devices, calibration]);

  const updateStatus = (message, type = 'info') => {
    setStatusMessage(message);
    setStatusType(type);
    setTimeout(() => {
      setStatusMessage(demoMode ? 'Ready - Demo Mode Active' : 'Ready');
      setStatusType('info');
    }, 5000);
  };

  // Device connection functions
  const connectDevice = async (deviceType) => {
    const device = devices[deviceType];
    updateStatus(`Connecting ${deviceType}...`, 'info');
    
    try {
      if (demoMode) {
        // Demo connection - always succeeds
        setDevices(prev => ({
          ...prev,
          [deviceType]: { ...prev[deviceType], connected: true }
        }));
        updateStatus(`Demo: Connected ${deviceType}`, 'info');
      } else {
        if (device.type === 'serial') {
          await SerialManager.connectSerial(deviceType, device.port);
        } else {
          await SerialManager.connectNetwork(deviceType, device.ip, device.tcpPort);
        }
        setDevices(prev => ({
          ...prev,
          [deviceType]: { ...prev[deviceType], connected: true }
        }));
        updateStatus(`Connected ${deviceType}`, 'info');
      }
    } catch (error) {
      updateStatus(`Error connecting ${deviceType}: ${error.message}`, 'error');
    }
  };

  const disconnectDevice = (deviceType) => {
    if (!demoMode) {
      SerialManager.disconnect(deviceType);
    }
    setDevices(prev => ({
      ...prev,
      [deviceType]: { ...prev[deviceType], connected: false }
    }));
    updateStatus(`Disconnected ${deviceType}`, 'info');
  };

  // EDM calibration functions
  const setCentre = async () => {
    updateStatus('Setting centre... Aim at circle centre and wait', 'info');
    setIsMeasuring(true);
    
    try {
      if (demoMode) {
        // Simulate measurement delay
        await new Promise(resolve => setTimeout(resolve, 2000));
      } else {
        await SerialManager.getReliableEDMReading('edm');
      }
      
      setCalibration(prev => ({ ...prev, centreSet: true }));
      updateStatus('Circle centre has been set successfully', 'info');
    } catch (error) {
      updateStatus(`Error setting centre: ${error.message}`, 'error');
    }
    setIsMeasuring(false);
  };

  const verifyEdge = async () => {
    if (!calibration.centreSet) {
      updateStatus('Must set centre first', 'error');
      return;
    }

    updateStatus('Verifying edge... Aim at circle edge and wait', 'info');
    setIsMeasuring(true);
    
    try {
      if (demoMode) {
        await new Promise(resolve => setTimeout(resolve, 2000));
        // Generate realistic mock result
        const mockResult = {
          isInTolerance: Math.random() > 0.2, // 80% success rate
          differenceMm: (Math.random() - 0.5) * 8, // ±4mm
          toleranceAppliedMm: calibration.circleType === 'JAVELIN_ARC' ? 10.0 : 5.0
        };
        
        setCalibration(prev => ({
          ...prev,
          edgeVerified: mockResult.isInTolerance,
          edgeVerificationResult: mockResult
        }));
        
        const status = mockResult.isInTolerance 
          ? `Edge verification PASSED (${Math.abs(mockResult.differenceMm).toFixed(1)}mm difference)`
          : `Edge verification FAILED (${Math.abs(mockResult.differenceMm).toFixed(1)}mm difference)`;
        
        updateStatus(status, mockResult.isInTolerance ? 'info' : 'error');
      } else {
        // Real EDM measurement logic would go here
        updateStatus('Real EDM edge verification not implemented yet', 'error');
      }
    } catch (error) {
      updateStatus(`Error verifying edge: ${error.message}`, 'error');
    }
    setIsMeasuring(false);
  };

  const measureDistance = async () => {
    if (!calibration.centreSet) {
      updateStatus('EDM must be calibrated first', 'error');
      return;
    }

    setIsMeasuring(true);
    updateStatus('Measuring distance... Please wait', 'info');

    try {
      if (demoMode) {
        await new Promise(resolve => setTimeout(resolve, 1500));
        
        // Generate realistic distance based on event type
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

        const measurement = `${distance.toFixed(2)} m`;
        setCurrentMeasurement(measurement);
        updateStatus(`Measurement complete: ${measurement}`, 'info');
      } else {
        // Real EDM measurement logic would go here
        const reading = await SerialManager.getReliableEDMReading('edm');
        // Calculate actual distance from EDM reading
        const distance = Math.random() * 50; // Placeholder
        const measurement = `${distance.toFixed(2)} m`;
        setCurrentMeasurement(measurement);
        updateStatus(`Measurement: ${measurement}`, 'info');
      }
    } catch (error) {
      updateStatus(`Measurement error: ${error.message}`, 'error');
    }
    setIsMeasuring(false);
  };

  const measureWind = async () => {
    setIsMeasuring(true);
    updateStatus('Measuring wind... 5 second average', 'info');

    try {
      if (demoMode) {
        await new Promise(resolve => setTimeout(resolve, 5000));
        const windSpeed = (Math.random() * 4) - 2; // -2 to +2 m/s
        const measurement = `${windSpeed > 0 ? '+' : ''}${windSpeed.toFixed(1)} m/s`;
        setCurrentMeasurement(measurement);
        updateStatus(`Wind measurement: ${measurement}`, 'info');
      } else {
        const windSpeed = await SerialManager.readWindGauge('wind');
        const measurement = `${windSpeed > 0 ? '+' : ''}${windSpeed.toFixed(1)} m/s`;
        setCurrentMeasurement(measurement);
        updateStatus(`Wind: ${measurement}`, 'info');
      }
    } catch (error) {
      updateStatus(`Wind measurement error: ${error.message}`, 'error');
    }
    setIsMeasuring(false);
  };

  // Screen rendering functions
  const renderSelectEventType = () => (
    <View style={styles.screenContainer}>
      <Text style={styles.screenTitle}>SELECT EVENT TYPE</Text>
      <View style={styles.eventCardsContainer}>
        <EventCard
          title="Throws"
          subtitle="Shot, Discus, Hammer, Javelin"
          icon="track-changes"
          onPress={() => {
            setEventType('Throws');
            setCurrentScreen('DEVICE_SETUP');
          }}
          style={styles.eventCardLeft}
        />
        <EventCard
          title="Horizontal Jumps"
          subtitle="Long Jump, Triple Jump"
          icon="directions-run"
          onPress={() => {
            setEventType('Horizontal Jumps');
            setCurrentScreen('DEVICE_SETUP');
          }}
          style={styles.eventCardRight}
        />
      </View>
    </View>
  );

  const renderDeviceSetup = () => (
    <View style={styles.screenContainer}>
      <Text style={styles.screenTitle}>DEVICE SETUP</Text>
      <Text style={styles.screenSubtitle}>Configure Demo Mode</Text>
      
      <View style={styles.demoModeContainer}>
        <TouchableOpacity
          style={[styles.demoModeButton, demoMode && styles.demoModeActive]}
          onPress={() => setDemoMode(!demoMode)}
        >
          <Icon name={demoMode ? "check-box" : "check-box-outline-blank"} size={48} color={demoMode ? "#4CAF50" : "#999"} />
          <Text style={styles.demoModeText}>Demo Mode</Text>
          <Text style={styles.demoModeSubtext}>
            Use simulated data without hardware
          </Text>
        </TouchableOpacity>
      </View>

      <View style={styles.navigationContainer}>
        <LargeButton
          title="Back"
          variant="secondary"
          icon="arrow-back"
          onPress={() => setCurrentScreen('SELECT_EVENT_TYPE')}
          style={styles.navButton}
        />
        <LargeButton
          title="Next"
          icon="arrow-forward"
          onPress={() => setCurrentScreen(eventType === 'Throws' ? 'CALIBRATION' : 'MEASUREMENT')}
          style={styles.navButton}
        />
      </View>
    </View>
  );

  const renderCalibration = () => (
    <View style={styles.screenContainer}>
      <Text style={styles.screenTitle}>EDM CALIBRATION</Text>
      <Text style={styles.screenSubtitle}>{calibration.circleType} Circle</Text>
      
      <View style={styles.calibrationContainer}>
        <LargePicker
          label="Circle Type"
          selectedValue={calibration.circleType}
          onValueChange={(value) => setCalibration(prev => ({
            ...prev,
            circleType: value,
            targetRadius: UKA_RADII[value],
            centreSet: false,
            edgeVerified: false,
            edgeVerificationResult: null
          }))}
          items={Object.keys(UKA_RADII).map(key => ({ label: key, value: key }))}
          style={styles.circleTypePicker}
        />
        
        <View style={styles.radiusDisplay}>
          <Text style={styles.radiusLabel}>Target Radius:</Text>
          <Text style={styles.radiusValue}>
            {calibration.targetRadius?.toFixed(4)} m
          </Text>
        </View>

        <LargeButton
          title={calibration.centreSet ? "Centre Set ✓" : "Set Centre"}
          icon={calibration.centreSet ? "check-circle" : "my-location"}
          variant={calibration.centreSet ? "success" : "primary"}
          onPress={setCentre}
          disabled={isMeasuring}
          style={styles.calibrationButton}
        />

        <LargeButton
          title={calibration.edgeVerified ? "Edge Verified ✓" : "Verify Edge"}
          icon={calibration.edgeVerified ? "check-circle" : "gps-fixed"}
          variant={calibration.edgeVerified ? "success" : "primary"}
          onPress={verifyEdge}
          disabled={isMeasuring || !calibration.centreSet}
          style={styles.calibrationButton}
        />

        {calibration.edgeVerificationResult && (
          <View style={[
            styles.verificationResult,
            calibration.edgeVerificationResult.isInTolerance ? styles.verificationPass : styles.verificationFail
          ]}>
            <Text style={styles.verificationText}>
              {calibration.edgeVerificationResult.isInTolerance ? '✓ PASS' : '✗ FAIL'}
            </Text>
            <Text style={styles.verificationDetail}>
              {Math.abs(calibration.edgeVerificationResult.differenceMm).toFixed(1)}mm difference
            </Text>
          </View>
        )}
      </View>

      <View style={styles.navigationContainer}>
        <LargeButton
          title="Back"
          variant="secondary"
          icon="arrow-back"
          onPress={() => setCurrentScreen('DEVICE_SETUP')}
          style={styles.navButton}
        />
        <LargeButton
          title="Start Measuring"
          icon="arrow-forward"
          onPress={() => setCurrentScreen('MEASUREMENT')}
          disabled={!calibration.centreSet}
          style={styles.navButton}
        />
      </View>
    </View>
  );

  const renderMeasurement = () => (
    <View style={styles.screenContainer}>
      <Text style={styles.screenTitle}>MEASUREMENT MODE</Text>
      <Text style={styles.screenSubtitle}>
        {eventType} {demoMode && '(DEMO)'}
      </Text>
      
      <View style={styles.measurementContainer}>
        <LargeButton
          title={isMeasuring ? "Measuring..." : `Measure ${eventType === 'Throws' ? 'Distance' : 'Wind'}`}
          icon={eventType === 'Throws' ? 'straighten' : 'air'}
          onPress={eventType === 'Throws' ? measureDistance : measureWind}
          disabled={isMeasuring}
          style={styles.measureButton}
        />
        
        <View style={styles.measurementDisplay}>
          <Text style={styles.measurementLabel}>
            {eventType === 'Throws' ? 'Distance:' : 'Wind:'}
          </Text>
          <Text style={styles.measurementValue}>{currentMeasurement || '--'}</Text>
        </View>
      </View>

      <View style={styles.navigationContainer}>
        <LargeButton
          title="Setup"
          variant="secondary"
          icon="settings"
          onPress={() => setCurrentScreen('DEVICE_SETUP')}
          style={styles.navButton}
        />
        <LargeButton
          title="New Event"
          icon="refresh"
          onPress={() => {
            setCurrentMeasurement('');
            setCurrentScreen('SELECT_EVENT_TYPE');
          }}
          style={styles.navButton}
        />
      </View>
    </View>
  );

  const renderCurrentScreen = () => {
    switch (currentScreen) {
      case 'SELECT_EVENT_TYPE':
        return renderSelectEventType();
      case 'DEVICE_SETUP':
        return renderDeviceSetup();
      case 'CALIBRATION':
        return renderCalibration();
      case 'MEASUREMENT':
        return renderMeasurement();
      default:
        return renderSelectEventType();
    }
  };

  return (
    <View style={styles.container}>
      <StatusBar backgroundColor="#1976D2" barStyle="light-content" />
      
      <View style={styles.header}>
        <Text style={styles.headerTitle}>PolyField by KACPH</Text>
      </View>
      
      <View style={styles.content}>
        {renderCurrentScreen()}
      </View>
      
      <StatusDisplay message={statusMessage} type={statusType} />
    </View>
  );
};

// Styles optimized for landscape tablet with large touch targets
const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  header: {
    backgroundColor: '#1976D2',
    height: 80,
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerTitle: {
    color: 'white',
    fontSize: 28,
    fontWeight: 'bold',
  },
  content: {
    flex: 1,
    backgroundColor: 'white',
    margin: 20,
    borderRadius: 12,
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 4,
  },
  screenContainer: {
    flex: 1,
    padding: 30,
  },
  screenTitle: {
    fontSize: 36,
    fontWeight: 'bold',
    textAlign: 'center',
    color: '#333',
    marginBottom: 10,
  },
  screenSubtitle: {
    fontSize: 24,
    textAlign: 'center',
    color: '#666',
    marginBottom: 40,
  },
  
  // Event cards
  eventCardsContainer: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 40,
  },
  eventCard: {
    backgroundColor: 'white',
    borderWidth: 3,
    borderColor: '#ddd',
    borderRadius: 16,
    padding: 40,
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 6,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.3,
    shadowRadius: 6,
    minHeight: 280,
    minWidth: 300,
  },
  eventCardLeft: {
    marginRight: 20,
  },
  eventCardRight: {
    marginLeft: 20,
  },
  eventCardIcon: {
    marginBottom: 20,
  },
  eventCardTitle: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 10,
    textAlign: 'center',
  },
  eventCardSubtitle: {
    fontSize: 18,
    color: '#666',
    textAlign: 'center',
    lineHeight: 24,
  },
  
  // Large buttons
  largeButton: {
    backgroundColor: '#1976D2',
    borderRadius: 12,
    paddingVertical: 20,
    paddingHorizontal: 30,
    minHeight: 80,
    minWidth: 160,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 4,
  },
  secondaryButton: {
    backgroundColor: '#757575',
  },
  dangerButton: {
    backgroundColor: '#D32F2F',
  },
  successButton: {
    backgroundColor: '#388E3C',
  },
  disabledButton: {
    backgroundColor: '#ccc',
  },
  buttonContent: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  buttonIcon: {
    marginRight: 12,
  },
  largeButtonText: {
    color: 'white',
    fontSize: 22,
    fontWeight: 'bold',
    textAlign: 'center',
  },
  disabledButtonText: {
    color: '#999',
  },
  
  // Demo mode
  demoModeContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  demoModeButton: {
    backgroundColor: 'white',
    borderWidth: 3,
    borderColor: '#ddd',
    borderRadius: 16,
    padding: 40,
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 300,
    minHeight: 200,
  },
  demoModeActive: {
    borderColor: '#4CAF50',
    backgroundColor: '#f1f8e9',
  },
  demoModeText: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#333',
    marginTop: 15,
    marginBottom: 10,
  },
  demoModeSubtext: {
    fontSize: 18,
    color: '#666',
    textAlign: 'center',
  },
  
  // Input components
  inputContainer: {
    marginBottom: 20,
  },
  inputLabel: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 10,
  },
  largeInput: {
    borderWidth: 2,
    borderColor: '#ddd',
    borderRadius: 8,
    paddingHorizontal: 20,
    paddingVertical: 15,
    fontSize: 20,
    backgroundColor: 'white',
    minHeight: 60,
  },
  
  // Picker components
  pickerContainer: {
    marginBottom: 20,
  },
  pickerWrapper: {
    borderWidth: 2,
    borderColor: '#ddd',
    borderRadius: 8,
    backgroundColor: 'white',
    minHeight: 60,
  },
  largePicker: {
    fontSize: 20,
  },
  pickerItem: {
    fontSize: 20,
    height: 60,
  },
  
  // Calibration
  calibrationContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 40,
  },
  circleTypePicker: {
    marginBottom: 40,
    minWidth: 300,
  },
  radiusDisplay: {
    backgroundColor: '#f5f5f5',
    borderRadius: 8,
    padding: 20,
    alignItems: 'center',
    minWidth: 250,
    marginBottom: 40,
  },
  radiusLabel: {
    fontSize: 18,
    color: '#666',
    marginBottom: 5,
  },
  radiusValue: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1976D2',
  },
  calibrationButton: {
    minWidth: 300,
    marginBottom: 20,
  },
  verificationResult: {
    marginTop: 30,
    padding: 20,
    borderRadius: 8,
    alignItems: 'center',
    minWidth: 250,
  },
  verificationPass: {
    backgroundColor: '#e8f5e8',
    borderWidth: 2,
    borderColor: '#4CAF50',
  },
  verificationFail: {
    backgroundColor: '#ffebee',
    borderWidth: 2,
    borderColor: '#f44336',
  },
  verificationText: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 5,
  },
  verificationDetail: {
    fontSize: 18,
    color: '#666',
  },
  
  // Measurement
  measurementContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 40,
  },
  measureButton: {
    minWidth: 300,
    marginBottom: 60,
  },
  measurementDisplay: {
    backgroundColor: '#f5f5f5',
    borderRadius: 16,
    padding: 40,
    alignItems: 'center',
    minWidth: 400,
    minHeight: 200,
  },
  measurementLabel: {
    fontSize: 24,
    color: '#666',
    marginBottom: 20,
  },
  measurementValue: {
    fontSize: 60,
    fontWeight: 'bold',
    color: '#1976D2',
    textAlign: 'center',
  },
  
  // Navigation
  navigationContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 40,
    paddingTop: 20,
  },
  navButton: {
    minWidth: 140,
  },
  
  // Status bar
  statusBar: {
    backgroundColor: '#e3f2fd',
    paddingVertical: 15,
    paddingHorizontal: 20,
    borderTopWidth: 1,
    borderTopColor: '#ddd',
  },
  statusBarError: {
    backgroundColor: '#ffebee',
  },
  statusText: {
    fontSize: 18,
    color: '#333',
    textAlign: 'center',
    fontWeight: '500',
  },
});

export default PolyFieldApp;
