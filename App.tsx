import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Dimensions, SafeAreaView } from 'react-native';

const { width: screenWidth, height: screenHeight } = Dimensions.get('window');

// UKA Official Circle Radii
const UKA_RADII = {
  SHOT: 1.0675,
  DISCUS: 1.250,
  HAMMER: 1.0675,
  JAVELIN_ARC: 8.000
};

const PolyFieldApp = () => {
  const [currentScreen, setCurrentScreen] = useState('SELECT_EVENT_TYPE');
  const [eventType, setEventType] = useState<string | null>(null);
  const [demoMode, setDemoMode] = useState(true);
  const [measurement, setMeasurement] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  
  // Calibration state
  const [calibration, setCalibration] = useState({
    circleType: 'SHOT',
    centreSet: false,
    edgeVerified: false,
    edgeResult: null as any
  });

  const measureDemo = async () => {
    setIsLoading(true);
    setTimeout(() => {
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
      setMeasurement(`${distance.toFixed(2)} m`);
      setIsLoading(false);
    }, 1500);
  };

  const measureWind = async () => {
    setIsLoading(true);
    setTimeout(() => {
      const windSpeed = (Math.random() * 4) - 2; // -2 to +2 m/s
      setMeasurement(`${windSpeed > 0 ? '+' : ''}${windSpeed.toFixed(1)} m/s`);
      setIsLoading(false);
    }, 3000); // 3 seconds for wind measurement
  };

  const setCentre = async () => {
    setIsLoading(true);
    setTimeout(() => {
      setCalibration(prev => ({ ...prev, centreSet: true }));
      setIsLoading(false);
    }, 2000);
  };

  const verifyEdge = async () => {
    setIsLoading(true);
    setTimeout(() => {
      const mockResult = {
        isInTolerance: Math.random() > 0.2, // 80% success rate
        differenceMm: (Math.random() - 0.5) * 8, // ±4mm
      };
      setCalibration(prev => ({ 
        ...prev, 
        edgeVerified: mockResult.isInTolerance,
        edgeResult: mockResult
      }));
      setIsLoading(false);
    }, 2000);
  };

  // Event Selection Screen
  if (currentScreen === 'SELECT_EVENT_TYPE') {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>PolyField by KACPH</Text>
          {demoMode && <Text style={styles.demoStatus}>Demo Active</Text>}
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
                setCurrentScreen('MEASUREMENT');
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
          {demoMode && <Text style={styles.demoStatus}>Demo Active</Text>}
        </View>
        
        <View style={styles.mainContent}>
          <Text style={styles.title}>Device Setup</Text>
          
          <View style={styles.setupContainer}>
            <TouchableOpacity
              style={[styles.demoToggle, demoMode && styles.demoToggleActive]}
              onPress={() => setDemoMode(!demoMode)}
            >
              <View style={styles.demoToggleContent}>
                <Text style={styles.demoToggleIcon}>{demoMode ? '✓' : '○'}</Text>
                <View>
                  <Text style={styles.demoToggleTitle}>Demo Mode</Text>
                  <Text style={styles.demoToggleSubtitle}>
                    Use simulated data without hardware
                  </Text>
                </View>
              </View>
            </TouchableOpacity>
            
            <View style={styles.deviceStatus}>
              <Text style={styles.deviceTitle}>EDM Status:</Text>
              <Text style={[styles.deviceStatusText, demoMode ? styles.connectedText : styles.disconnectedText]}>
                {demoMode ? 'Demo Ready' : 'Not Connected'}
              </Text>
            </View>
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
            onPress={() => setCurrentScreen('CALIBRATION')}
          >
            <Text style={styles.navButtonText}>Next →</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  // Calibration Screen
  if (currentScreen === 'CALIBRATION') {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>PolyField by KACPH</Text>
          {demoMode && <Text style={styles.demoStatus}>Demo Active</Text>}
        </View>
        
        <View style={styles.mainContent}>
          <Text style={styles.title}>EDM Calibration</Text>
          
          <View style={styles.calibrationContainer}>
            {/* Circle Type Selection */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>1. Select Circle Type</Text>
              <View style={styles.circleTypeContainer}>
                {Object.keys(UKA_RADII).map((type) => (
                  <TouchableOpacity
                    key={type}
                    style={[
                      styles.circleTypeButton,
                      calibration.circleType === type && styles.circleTypeActive
                    ]}
                    onPress={() => setCalibration(prev => ({ 
                      ...prev, 
                      circleType: type,
                      centreSet: false,
                      edgeVerified: false,
                      edgeResult: null
                    }))}
                  >
                    <Text style={[
                      styles.circleTypeText,
                      calibration.circleType === type && styles.circleTypeTextActive
                    ]}>
                      {type.replace('_', ' ')}
                    </Text>
                    <Text style={styles.radiusText}>
                      {UKA_RADII[type as keyof typeof UKA_RADII].toFixed(3)}m
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>

            {/* Calibration Steps */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>2. Calibration Steps</Text>
              
              <TouchableOpacity 
                style={[
                  styles.calibrationButton, 
                  calibration.centreSet && styles.calibrationButtonSuccess,
                  isLoading && styles.disabledButton
                ]}
                onPress={setCentre}
                disabled={isLoading}
              >
                <Text style={styles.calibrationButtonText}>
                  {isLoading && !calibration.centreSet ? 'Setting Centre...' : 
                   calibration.centreSet ? '✓ Centre Set' : 'Set Centre'}
                </Text>
              </TouchableOpacity>
              
              <TouchableOpacity 
                style={[
                  styles.calibrationButton, 
                  calibration.edgeVerified && styles.calibrationButtonSuccess,
                  !calibration.centreSet && styles.disabledButton,
                  isLoading && styles.disabledButton
                ]}
                onPress={verifyEdge}
                disabled={!calibration.centreSet || isLoading}
              >
                <Text style={styles.calibrationButtonText}>
                  {isLoading && calibration.centreSet && !calibration.edgeVerified ? 'Verifying Edge...' :
                   calibration.edgeVerified ? '✓ Edge Verified' : 'Verify Edge'}
                </Text>
              </TouchableOpacity>

              {calibration.edgeResult && (
                <View style={[
                  styles.verificationResult,
                  calibration.edgeResult.isInTolerance ? styles.verificationPass : styles.verificationFail
                ]}>
                  <Text style={styles.verificationText}>
                    {calibration.edgeResult.isInTolerance ? '✓ PASS' : '✗ FAIL'}
                  </Text>
                  <Text style={styles.verificationDetail}>
                    {Math.abs(calibration.edgeResult.differenceMm).toFixed(1)}mm difference
                  </Text>
                </View>
              )}
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
              !calibration.centreSet && styles.disabledButton
            ]} 
            onPress={() => setCurrentScreen('MEASUREMENT')}
            disabled={!calibration.centreSet}
          >
            <Text style={styles.navButtonText}>Start Measuring →</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  // Measurement Screen
  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>PolyField by KACPH</Text>
        {demoMode && <Text style={styles.demoStatus}>Demo Active</Text>}
      </View>
      
      <View style={styles.mainContent}>
        <Text style={styles.title}>Measurement Mode</Text>
        <Text style={styles.subtitle}>{eventType} • {calibration.circleType.replace('_', ' ')}</Text>
        
        <View style={styles.measurementSection}>
          <TouchableOpacity 
            style={[styles.measureButton, isLoading && styles.disabledButton]} 
            onPress={eventType === 'Throws' ? measureDemo : measureWind}
            disabled={isLoading}
          >
            <Text style={styles.measureButtonText}>
              {isLoading ? 
                (eventType === 'Throws' ? 'Measuring...' : 'Measuring Wind...') : 
                `Measure ${eventType === 'Throws' ? 'Distance' : 'Wind'}`
              }
            </Text>
          </TouchableOpacity>
          
          <View style={styles.display}>
            <Text style={styles.label}>
              {eventType === 'Throws' ? 'Distance:' : 'Wind:'}
            </Text>
            <Text style={styles.value}>{measurement || '--'}</Text>
          </View>
        </View>
      </View>
      
      <View style={styles.bottomNav}>
        <TouchableOpacity 
          style={styles.navButton} 
          onPress={() => setCurrentScreen(eventType === 'Throws' ? 'CALIBRATION' : 'DEVICE_SETUP')}
        >
          <Text style={styles.navButtonText}>← Setup</Text>
        </TouchableOpacity>
        <TouchableOpacity 
          style={styles.navButton} 
          onPress={() => {
            setCurrentScreen('SELECT_EVENT_TYPE');
            setMeasurement('');
            setCalibration(prev => ({ ...prev, centreSet: false, edgeVerified: false, edgeResult: null }));
          }}
        >
          <Text style={styles.navButtonText}>New Event</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
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
    height: Math.max(60, screenHeight * 0.08),
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
    fontSize: Math.max(22, screenWidth * 0.022),
    fontWeight: 'bold',
  },
  demoStatus: {
    color: '#ffeb3b',
    fontSize: Math.max(16, screenWidth * 0.018),
    fontWeight: 'bold',
    backgroundColor: 'rgba(255, 235, 59, 0.2)',
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 12,
  },
  
  mainContent: {
    flex: 1,
    padding: Math.max(20, screenWidth * 0.025),
  },
  
  // Smaller title for more screen space
  title: {
    fontSize: Math.max(24, screenWidth * 0.028),
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: Math.max(15, screenHeight * 0.02),
    color: '#333',
  },
  
  subtitle: {
    fontSize: Math.max(18, screenWidth * 0.02),
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
  },
  
  card: {
    backgroundColor: 'white',
    borderRadius: 16,
    padding: Math.max(25, screenWidth * 0.022),
    minHeight: Math.max(180, screenHeight * 0.28),
    width: Math.max(260, screenWidth * 0.32),
    alignItems: 'center',
    justifyContent: 'center',
    elevation: 6,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.3,
    shadowRadius: 6,
    borderWidth: 2,
    borderColor: '#e0e0e0',
  },
  
  cardIcon: {
    marginBottom: 12,
  },
  
  iconText: {
    fontSize: Math.max(42, screenWidth * 0.05),
  },
  
  cardTitle: {
    fontSize: Math.max(20, screenWidth * 0.025),
    fontWeight: 'bold',
    marginBottom: 8,
    textAlign: 'center',
    color: '#333',
  },
  
  cardSubtitle: {
    fontSize: Math.max(14, screenWidth * 0.018),
    color: '#666',
    textAlign: 'center',
    lineHeight: 20,
  },

  // Device Setup
  setupContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },

  demoToggle: {
    backgroundColor: 'white',
    borderRadius: 16,
    padding: 30,
    marginBottom: 40,
    borderWidth: 3,
    borderColor: '#ddd',
    minWidth: 350,
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
    fontSize: 32,
    marginRight: 20,
    color: '#4CAF50',
    fontWeight: 'bold',
  },

  demoToggleTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 5,
  },

  demoToggleSubtitle: {
    fontSize: 16,
    color: '#666',
  },

  deviceStatus: {
    flexDirection: 'row',
    alignItems: 'center',
  },

  deviceTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginRight: 10,
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

  // Calibration
  calibrationContainer: {
    flex: 1,
  },

  section: {
    marginBottom: 30,
  },

  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 15,
    color: '#333',
  },

  circleTypeContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginBottom: 20,
  },

  circleTypeButton: {
    backgroundColor: '#f5f5f5',
    borderRadius: 8,
    padding: 15,
    minWidth: 100,
    alignItems: 'center',
    borderWidth: 2,
    borderColor: '#ddd',
  },

  circleTypeActive: {
    backgroundColor: '#1976D2',
    borderColor: '#1976D2',
  },

  circleTypeText: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#333',
    textAlign: 'center',
  },

  circleTypeTextActive: {
    color: 'white',
  },

  radiusText: {
    fontSize: 12,
    color: '#666',
    marginTop: 5,
  },

  calibrationButton: {
    backgroundColor: '#1976D2',
    padding: 20,
    borderRadius: 12,
    marginBottom: 15,
    alignItems: 'center',
  },

  calibrationButtonSuccess: {
    backgroundColor: '#4CAF50',
  },

  calibrationButtonText: {
    color: 'white',
    fontSize: 18,
    fontWeight: 'bold',
  },

  verificationResult: {
    padding: 15,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 10,
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
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 5,
  },

  verificationDetail: {
    fontSize: 14,
    color: '#666',
  },
  
  // Measurement
  measurementSection: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  
  measureButton: {
    backgroundColor: '#1976D2',
    paddingHorizontal: Math.max(35, screenWidth * 0.045),
    paddingVertical: Math.max(18, screenHeight * 0.022),
    borderRadius: 12,
    marginBottom: Math.max(35, screenHeight * 0.045),
    elevation: 4,
    minWidth: Math.max(240, screenWidth * 0.28),
  },
  
  disabledButton: {
    backgroundColor: '#ccc',
  },
  
  measureButtonText: {
    color: 'white',
    fontSize: Math.max(18, screenWidth * 0.022),
    fontWeight: 'bold',
    textAlign: 'center',
  },
  
  display: {
    backgroundColor: '#f8f9fa',
    borderRadius: 16,
    padding: Math.max(25, screenWidth * 0.035),
    alignItems: 'center',
    minWidth: Math.max(280, screenWidth * 0.35),
    minHeight: Math.max(130, screenHeight * 0.18),
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: '#e9ecef',
  },
  
  label: {
    fontSize: Math.max(16, screenWidth * 0.02),
    color: '#666',
    marginBottom: 12,
  },
  
  value: {
    fontSize: Math.max(36, screenWidth * 0.045),
    fontWeight: 'bold',
    color: '#1976D2',
    textAlign: 'center',
  },
  
  // Bottom navigation
  bottomNav: {
    backgroundColor: '#e3f2fd',
    paddingVertical: Math.max(10, screenHeight * 0.012),
    paddingHorizontal: Math.max(20, screenWidth * 0.025),
    borderTopWidth: 1,
    borderTopColor: '#ddd',
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  
  navButton: {
    backgroundColor: '#757575',
    paddingHorizontal: Math.max(25, screenWidth * 0.032),
    paddingVertical: Math.max(12, screenHeight * 0.015),
    borderRadius: 8,
    minWidth: Math.max(100, screenWidth * 0.12),
  },

  primaryButton: {
    backgroundColor: '#1976D2',
  },
  
  navButtonText: {
    color: 'white',
    fontSize: Math.max(16, screenWidth * 0.019),
    fontWeight: 'bold',
    textAlign: 'center',
  },
});

export default PolyFieldApp;