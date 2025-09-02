#!/bin/bash

echo "🔥 Building PolyField with Native Kotlin EDM Calculations"
echo "=================================================="
echo "✅ Enhanced decimal seconds precision"
echo "✅ Corrected trigonometric formulas (hd = sd * cos(90° - va))"
echo "✅ No Go Mobile dependency - pure Kotlin"
echo "=================================================="

# Clean build
echo "🧹 Cleaning previous build..."
./gradlew clean

# Build debug APK
echo "🔨 Building debug APK..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "🎉 BUILD SUCCESSFUL!"
    echo "=================================================="
    echo "✅ Native Kotlin EDM calculations integrated"
    echo "✅ Go Mobile dependency removed"
    echo "✅ Javelin arc measurements should now be accurate (8.000m)"
    echo ""
    echo "📱 APK Location: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "🔧 New Methods Available:"
    echo "   - setCentreNative()"
    echo "   - verifyEdgeNative()"
    echo "   - measureThrowNative()"
    echo "   - getCalibrationStateNative()"
    echo "   - resetCalibrationNative()"
    echo "=================================================="
else
    echo ""
    echo "❌ BUILD FAILED"
    echo "Check the error messages above for details."
    exit 1
fi