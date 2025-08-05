# PolyField

<div align="center">
  <img src="claude/logo.svg" alt="PolyField Logo" width="200"/>
  
  **Professional Athletic Field Measurement Application**
  
  *Precision measurement tools for track and field athletics*
</div>

---

## 📱 About PolyField

PolyField is a professional React Native Android application designed for precise athletic field measurements. Built specifically for track and field events, it provides real-time measurement capabilities using Electronic Distance Measurement (EDM) devices, with comprehensive calibration and data visualization features.

### 🎯 Key Features

- **🔴 Live/Demo Mode Toggle** - Switch between real device measurements and simulated data
- **📐 Precision Measurement** - EDM device integration for accurate distance readings
- **🎪 Event Support** - Specialized workflows for throws, jumps, and other field events
- **📊 Heat Map Visualization** - Visual landing pattern analysis for throws
- **📈 Session Statistics** - Real-time performance tracking and analytics
- **🔧 Device Management** - Support for serial and network-connected EDM devices
- **📱 Tablet Optimized** - Landscape-only interface designed for field use

---

## 🚀 Technology Stack

- **Frontend**: React Native with TypeScript
- **Backend**: Go with gomobile for device communication
- **Platform**: Android (minimum SDK 21)
- **Architecture**: Native module bridge for Go-Android integration
- **Device Integration**: Serial (USB) and Network (TCP/IP) communication

---

## 📋 Prerequisites

- **Node.js** (v16 or higher)
- **React Native CLI**
- **Android Studio** with SDK 34+
- **Go** (v1.19 or higher)
- **gomobile** tool for Go mobile development
- **Java JDK 11** or higher

---

## 🛠️ Installation & Setup

### 1. Clone the Repository
```bash
git clone https://github.com/KingstonPolyAC/PolyFieldAndroid.git
cd PolyFieldAndroid
```

### 2. Install Dependencies
```bash
npm install
```

### 3. Build Go Mobile Library
```bash
cd android/go-mobile
go mod tidy
gomobile bind -target=android .
cd ../..
```

### 4. Android Setup
```bash
cd android
./gradlew clean
./gradlew assembleDebug
```

### 5. Install on Device
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 Usage

### Quick Start Guide

1. **Launch PolyField** on your Android tablet
2. **Select Event Type** - Choose between Throws, Jumps, or other field events
3. **Configure Devices** - Set up EDM device connections (Serial/Network)
4. **Calibration** - Perform field calibration using known reference points
5. **Measurement** - Take live measurements or use demo mode for testing
6. **Analysis** - View heat maps and session statistics

### Device Connection

#### Serial Devices (USB)
- Connect EDM device via USB-to-Serial adapter
- PolyField automatically detects available ports
- Descriptive naming: "USB Serial Adapter (ttyUSB0)"

#### Network Devices (TCP/IP)
- Configure IP address and port
- Built-in ping test for connectivity verification
- Real-time connection status monitoring

---

## 🏗️ Architecture

```
PolyField/
├── App.tsx                 # Main React Native application
├── android/
│   ├── app/
│   │   ├── src/main/java/com/polyfieldandroid/
│   │   │   ├── EDMModule.java          # Device communication
│   │   │   ├── GoMobileModule.java     # Go bridge
│   │   │   └── MainApplication.java    # App configuration
│   │   └── libs/
│   │       ├── mobile.aar              # Go mobile library
│   │       └── polyfield.aar           # PolyField Go library
│   └── go-mobile/
│       ├── mobile.go                   # Go implementation
│       └── go.mod                      # Go dependencies
├── claude/
│   └── polyfield.png                   # App logo/branding
└── README.md                           # This file
```

---

## 🎨 Features in Detail

### Live vs Demo Mode
- **Live Mode**: Connects to real EDM devices for actual measurements
- **Demo Mode**: Generates realistic simulated data for training/testing
- **Validation**: Live mode requires device connection before calibration

### Measurement Workflow
1. **Event Selection**: Choose measurement type and parameters
2. **Device Setup**: Configure and test device connections
3. **Calibration**: Set reference points and validate accuracy
4. **Measurement**: Perform live measurements with real-time feedback
5. **Analysis**: Review heat maps and statistical data

### Heat Map Visualization
- Real-time landing pattern visualization
- Statistical overlays (average, best, total throws)
- Coordinate system with distance markers
- Export capabilities for further analysis

---

## 🔧 Development

### Building for Release
```bash
cd android
./gradlew assembleRelease
```

Release APK location: `android/app/build/outputs/apk/release/app-release.apk`

### Code Structure
- **TypeScript**: Main application logic in App.tsx
- **Java Native Modules**: Device communication bridge
- **Go Backend**: gomobile library for device protocols
- **Android Resources**: Proper manifest, styles, and branding

---

## 📦 Release Information

- **Current Version**: 1.0
- **Package Name**: com.polyfieldandroid
- **Min SDK**: 21 (Android 5.0)
- **Target SDK**: 34 (Android 14)
- **Orientation**: Landscape only
- **File Size**: ~33MB

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🏃‍♂️ About Kingston Polytechnic Athletic Club

PolyField is developed by Kingston Polytechnic Athletic Club for professional track and field measurement applications. Our goal is to provide precise, reliable tools for athletic performance analysis and competition management.

---

## 📞 Support

For technical support or feature requests:
- 🐛 **Issues**: [GitHub Issues](https://github.com/KingstonPolyAC/PolyFieldAndroid/issues)
- 📧 **Contact**: Open an issue for direct support
- 📚 **Documentation**: See inline code comments and this README

---

<div align="center">
  <strong>Built with ❤️ for Track & Field Athletics</strong>
  <br>
  <em>Precision • Performance • Professional</em>
</div>