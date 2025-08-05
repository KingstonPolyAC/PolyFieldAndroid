package com.polyfieldandroid;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import mobile.Mobile;

import android.util.Log;

public class EDMModule extends ReactContextBaseJavaModule {
    private static final String TAG = "EDMModule";
    private Gson gson = new Gson();

    public EDMModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "EDMModule";
    }

    // --- Basic Functions ---
    @ReactMethod
    public void helloWorld(Promise promise) {
        try {
            String result = Mobile.helloWorld();
            promise.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error in helloWorld", e);
            promise.reject("HELLO_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void getCircleRadius(String circleType, Promise promise) {
        try {
            double radius = Mobile.getCircleRadius(circleType);
            promise.resolve(radius);
        } catch (Exception e) {
            Log.e(TAG, "Error getting circle radius", e);
            promise.reject("RADIUS_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void getToleranceForCircle(String circleType, Promise promise) {
        try {
            double tolerance = Mobile.getToleranceForCircle(circleType);
            promise.resolve(tolerance);
        } catch (Exception e) {
            Log.e(TAG, "Error getting tolerance", e);
            promise.reject("TOLERANCE_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void calculateDistance(double x1, double y1, double x2, double y2, Promise promise) {
        try {
            double distance = Mobile.calculateDistance(x1, y1, x2, y2);
            promise.resolve(distance);
        } catch (Exception e) {
            Log.e(TAG, "Error calculating distance", e);
            promise.reject("CALCULATION_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void validateThrowCoordinates(double x, double y, double circleRadius, Promise promise) {
        try {
            boolean isValid = Mobile.validateThrowCoordinates(x, y, circleRadius);
            promise.resolve(isValid);
        } catch (Exception e) {
            Log.e(TAG, "Error validating coordinates", e);
            promise.reject("VALIDATION_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void validateCalibration(String circleType, double measuredRadius, Promise promise) {
        try {
            boolean isValid = Mobile.validateCalibration(circleType, measuredRadius);
            promise.resolve(isValid);
        } catch (Exception e) {
            Log.e(TAG, "Error validating calibration", e);
            promise.reject("CALIBRATION_ERROR", e.getMessage(), e);
        }
    }

    // --- Demo Mode Functions ---
    @ReactMethod
    public void setDemoMode(boolean enabled, Promise promise) {
        try {
            Mobile.setDemoMode(enabled);
            promise.resolve(enabled);
            Log.i(TAG, "Demo mode set to: " + enabled);
        } catch (Exception e) {
            Log.e(TAG, "Error setting demo mode", e);
            promise.reject("DEMO_MODE_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void getDemoMode(Promise promise) {
        try {
            boolean demoMode = Mobile.getDemoMode();
            promise.resolve(demoMode);
        } catch (Exception e) {
            Log.e(TAG, "Error getting demo mode", e);
            promise.reject("DEMO_MODE_ERROR", e.getMessage(), e);
        }
    }

    // --- Device Connection Functions ---
    @ReactMethod
    public void listSerialPorts(Promise promise) {
        try {
            String portsJson = Mobile.listSerialPorts();
            JsonObject portsData = JsonParser.parseString(portsJson).getAsJsonObject();
            
            if (portsData.has("error")) {
                promise.reject("SERIAL_ERROR", portsData.get("error").getAsString());
                return;
            }
            
            WritableMap result = jsonObjectToWritableMap(portsData);
            promise.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error listing serial ports", e);
            promise.reject("SERIAL_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void connectSerialDevice(String deviceType, String portName, Promise promise) {
        try {
            String resultJson = Mobile.connectSerialDevice(deviceType, portName);
            JsonObject resultData = JsonParser.parseString(resultJson).getAsJsonObject();
            
            if (resultData.has("error")) {
                promise.reject("CONNECTION_ERROR", resultData.get("error").getAsString());
                return;
            }
            
            WritableMap result = jsonObjectToWritableMap(resultData);
            promise.resolve(result);
            Log.i(TAG, "Serial device connected: " + deviceType + " on " + portName);
        } catch (Exception e) {
            Log.e(TAG, "Error connecting serial device", e);
            promise.reject("CONNECTION_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void connectNetworkDevice(String deviceType, String ipAddress, int port, Promise promise) {
        try {
            String resultJson = Mobile.connectNetworkDevice(deviceType, ipAddress, port);
            JsonObject resultData = JsonParser.parseString(resultJson).getAsJsonObject();
            
            if (resultData.has("error")) {
                promise.reject("CONNECTION_ERROR", resultData.get("error").getAsString());
                return;
            }
            
            WritableMap result = jsonObjectToWritableMap(resultData);
            promise.resolve(result);
            Log.i(TAG, "Network device connected: " + deviceType + " at " + ipAddress + ":" + port);
        } catch (Exception e) {
            Log.e(TAG, "Error connecting network device", e);
            promise.reject("CONNECTION_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void disconnectDevice(String deviceType, Promise promise) {
        try {
            String resultJson = Mobile.disconnectDevice(deviceType);
            JsonObject resultData = JsonParser.parseString(resultJson).getAsJsonObject();
            
            if (resultData.has("error")) {
                promise.reject("DISCONNECT_ERROR", resultData.get("error").getAsString());
                return;
            }
            
            WritableMap result = jsonObjectToWritableMap(resultData);
            promise.resolve(result);
            Log.i(TAG, "Device disconnected: " + deviceType);
        } catch (Exception e) {
            Log.e(TAG, "Error disconnecting device", e);
            promise.reject("DISCONNECT_ERROR", e.getMessage(), e);
        }
    }

    // --- EDM Reading Functions ---
    @ReactMethod
    public void getReliableEDMReading(String deviceType, Promise promise) {
        try {
            String readingJson = Mobile.getReliableEDMReading(deviceType);
            JsonObject readingData = JsonParser.parseString(readingJson).getAsJsonObject();
            
            if (readingData.has("error")) {
                promise.reject("EDM_READING_ERROR", readingData.get("error").getAsString());
                return;
            }
            
            WritableMap result = jsonObjectToWritableMap(readingData);
            promise.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error getting EDM reading", e);
            promise.reject("EDM_READING_ERROR", e.getMessage(), e);
        }
    }

    // --- Calibration Functions ---
    @ReactMethod
    public void getCalibration(String deviceType, Promise promise) {
        try {
            String calibrationJson = Mobile.getCalibration(deviceType);
            JsonObject calibrationData = JsonParser.parseString(calibrationJson).getAsJsonObject();
            
            WritableMap result = jsonObjectToWritableMap(calibrationData);
            promise.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error getting calibration", e);
            promise.reject("CALIBRATION_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void saveCalibration(String deviceType, ReadableMap calibrationData, Promise promise) {
        try {
            JsonObject jsonData = readableMapToJsonObject(calibrationData);
            String jsonString = gson.toJson(jsonData);
            
            String resultJson = Mobile.saveCalibration(deviceType, jsonString);
            JsonObject resultData = JsonParser.parseString(resultJson).getAsJsonObject();
            
            if (resultData.has("error")) {
                promise.reject("CALIBRATION_SAVE_ERROR", resultData.get("error").getAsString());
                return;
            }
            
            promise.resolve(true);
            Log.i(TAG, "Calibration saved for " + deviceType);
        } catch (Exception e) {
            Log.e(TAG, "Error saving calibration", e);
            promise.reject("CALIBRATION_SAVE_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void resetCalibration(String deviceType, Promise promise) {
        try {
            String resultJson = Mobile.resetCalibration(deviceType);
            JsonObject resultData = JsonParser.parseString(resultJson).getAsJsonObject();
            
            if (resultData.has("error")) {
                promise.reject("CALIBRATION_RESET_ERROR", resultData.get("error").getAsString());
                return;
            }
            
            promise.resolve(true);
            Log.i(TAG, "Calibration reset for " + deviceType);
        } catch (Exception e) {
            Log.e(TAG, "Error resetting calibration", e);
            promise.reject("CALIBRATION_RESET_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void setCentre(String deviceType, Promise promise) {
        try {
            String resultJson = Mobile.setCentre(deviceType);
            JsonObject resultData = JsonParser.parseString(resultJson).getAsJsonObject();
            
            if (resultData.has("error")) {
                promise.reject("CENTRE_SET_ERROR", resultData.get("error").getAsString());
                return;
            }
            
            WritableMap result = jsonObjectToWritableMap(resultData);
            promise.resolve(result);
            Log.i(TAG, "Centre set for " + deviceType);
        } catch (Exception e) {
            Log.e(TAG, "Error setting centre", e);
            promise.reject("CENTRE_SET_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void verifyEdge(String deviceType, double targetRadius, Promise promise) {
        try {
            String resultJson = Mobile.verifyEdge(deviceType, targetRadius);
            JsonObject resultData = JsonParser.parseString(resultJson).getAsJsonObject();
            
            if (resultData.has("error")) {
                promise.reject("EDGE_VERIFY_ERROR", resultData.get("error").getAsString());
                return;
            }
            
            WritableMap result = jsonObjectToWritableMap(resultData);
            promise.resolve(result);
            Log.i(TAG, "Edge verified for " + deviceType);
        } catch (Exception e) {
            Log.e(TAG, "Error verifying edge", e);
            promise.reject("EDGE_VERIFY_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void measureThrow(String deviceType, Promise promise) {
        try {
            String resultJson = Mobile.measureThrow(deviceType);
            JsonObject resultData = JsonParser.parseString(resultJson).getAsJsonObject();
            
            if (resultData.has("error")) {
                promise.reject("THROW_MEASURE_ERROR", resultData.get("error").getAsString());
                return;
            }
            
            WritableMap result = jsonObjectToWritableMap(resultData);
            promise.resolve(result);
            Log.i(TAG, "Throw measured for " + deviceType);
        } catch (Exception e) {
            Log.e(TAG, "Error measuring throw", e);
            promise.reject("THROW_MEASURE_ERROR", e.getMessage(), e);
        }
    }

    // --- Wind Measurement Functions ---
    @ReactMethod
    public void measureWind(Promise promise) {
        try {
            double windSpeed = Mobile.measureWind();
            promise.resolve(windSpeed);
        } catch (Exception e) {
            Log.e(TAG, "Error measuring wind", e);
            promise.reject("WIND_MEASURE_ERROR", e.getMessage(), e);
        }
    }

    // --- Coordinate and Statistics Functions ---
    @ReactMethod
    public void getThrowCoordinates(Promise promise) {
        try {
            String coordinatesJson = Mobile.getThrowCoordinates();
            JsonObject coordinatesData = JsonParser.parseString(coordinatesJson).getAsJsonObject();
            
            WritableMap result = jsonObjectToWritableMap(coordinatesData);
            promise.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error getting throw coordinates", e);
            promise.reject("COORDINATES_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void getThrowStatistics(String circleType, Promise promise) {
        try {
            String statsJson = Mobile.getThrowStatistics(circleType);
            JsonObject statsData = JsonParser.parseString(statsJson).getAsJsonObject();
            
            if (statsData.has("error")) {
                WritableMap errorResult = Arguments.createMap();
                errorResult.putString("error", statsData.get("error").getAsString());
                promise.resolve(errorResult);
                return;
            }
            
            WritableMap result = jsonObjectToWritableMap(statsData);
            promise.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error getting throw statistics", e);
            promise.reject("STATISTICS_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void clearThrowCoordinates(Promise promise) {
        try {
            Mobile.clearThrowCoordinates();
            promise.resolve(true);
            Log.i(TAG, "Throw coordinates cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing throw coordinates", e);
            promise.reject("CLEAR_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void calculateCircleAccuracy(double measuredRadius, double targetRadius, Promise promise) {
        try {
            String resultJson = Mobile.calculateCircleAccuracy(measuredRadius, targetRadius);
            JsonObject resultData = JsonParser.parseString(resultJson).getAsJsonObject();
            
            WritableMap result = jsonObjectToWritableMap(resultData);
            promise.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error calculating circle accuracy", e);
            promise.reject("ACCURACY_ERROR", e.getMessage(), e);
        }
    }

    // --- Helper Methods ---
    private WritableMap jsonObjectToWritableMap(JsonObject jsonObject) {
        WritableMap map = Arguments.createMap();

        for (String key : jsonObject.keySet()) {
            try {
                if (jsonObject.get(key).isJsonNull()) {
                    map.putNull(key);
                } else if (jsonObject.get(key).isJsonPrimitive()) {
                    if (jsonObject.get(key).getAsJsonPrimitive().isBoolean()) {
                        map.putBoolean(key, jsonObject.get(key).getAsBoolean());
                    } else if (jsonObject.get(key).getAsJsonPrimitive().isNumber()) {
                        map.putDouble(key, jsonObject.get(key).getAsDouble());
                    } else {
                        map.putString(key, jsonObject.get(key).getAsString());
                    }
                } else if (jsonObject.get(key).isJsonObject()) {
                    map.putMap(key, jsonObjectToWritableMap(jsonObject.get(key).getAsJsonObject()));
                } else if (jsonObject.get(key).isJsonArray()) {
                    // Handle arrays by converting to string for now
                    map.putString(key, jsonObject.get(key).toString());
                } else {
                    map.putString(key, jsonObject.get(key).toString());
                }
            } catch (Exception e) {
                Log.w(TAG, "Error converting field " + key + ": " + e.getMessage());
                map.putString(key, jsonObject.get(key).toString());
            }
        }

        return map;
    }

    private JsonObject readableMapToJsonObject(ReadableMap readableMap) {
        JsonObject jsonObject = new JsonObject();

        for (String key : readableMap.toHashMap().keySet()) {
            try {
                switch (readableMap.getType(key)) {
                    case Boolean:
                        jsonObject.addProperty(key, readableMap.getBoolean(key));
                        break;
                    case Number:
                        jsonObject.addProperty(key, readableMap.getDouble(key));
                        break;
                    case String:
                        jsonObject.addProperty(key, readableMap.getString(key));
                        break;
                    case Map:
                        jsonObject.add(key, readableMapToJsonObject(readableMap.getMap(key)));
                        break;
                    case Null:
                        jsonObject.add(key, null);
                        break;
                    default:
                        Log.w(TAG, "Unknown type for key " + key);
                        break;
                }
            } catch (Exception e) {
                Log.w(TAG, "Error converting field " + key + ": " + e.getMessage());
            }
        }

        return jsonObject;
    }

    @ReactMethod
    public void getAvailableSerialPorts(Promise promise) {
        try {
            // Get list of available serial ports from the system
            java.io.File devDir = new java.io.File("/dev");
            java.util.List<java.util.Map<String, String>> ports = new java.util.ArrayList<>();
            
            if (devDir.exists() && devDir.isDirectory()) {
                java.io.File[] files = devDir.listFiles();
                if (files != null) {
                    for (java.io.File file : files) {
                        String name = file.getName();
                        String port = "/dev/" + name;
                        String description = getPortDescription(name);
                        
                        // Look for common serial device patterns
                        if (name.startsWith("ttyUSB") || 
                            name.startsWith("ttyACM") || 
                            name.startsWith("ttyS") ||
                            name.startsWith("ttyO") ||
                            name.startsWith("ttyAMA")) {
                            
                            java.util.Map<String, String> portInfo = new java.util.HashMap<>();
                            portInfo.put("port", port);
                            portInfo.put("description", description);
                            ports.add(portInfo);
                        }
                    }
                }
            }
            
            // Add some common ports that might not be visible
            String[] commonPorts = {"/dev/ttyUSB0", "/dev/ttyUSB1", "/dev/ttyACM0", "/dev/ttyS0"};
            for (String port : commonPorts) {
                if (!containsPort(ports, port)) {
                    java.io.File portFile = new java.io.File(port);
                    if (portFile.exists()) {
                        java.util.Map<String, String> portInfo = new java.util.HashMap<>();
                        portInfo.put("port", port);
                        portInfo.put("description", getPortDescription(port.substring(5))); // Remove "/dev/"
                        ports.add(portInfo);
                    }
                }
            }
            
            // Convert to React Native array of objects
            com.facebook.react.bridge.WritableArray array = com.facebook.react.bridge.Arguments.createArray();
            for (java.util.Map<String, String> portInfo : ports) {
                com.facebook.react.bridge.WritableMap portMap = com.facebook.react.bridge.Arguments.createMap();
                portMap.putString("port", portInfo.get("port"));
                portMap.putString("description", portInfo.get("description"));
                array.pushMap(portMap);
            }
            
            promise.resolve(array);
            Log.i(TAG, "Found " + ports.size() + " serial ports");
        } catch (Exception e) {
            Log.e(TAG, "Error getting serial ports", e);
            promise.reject("SERIAL_PORTS_ERROR", e.getMessage(), e);
        }
    }

    private String getPortDescription(String portName) {
        if (portName.startsWith("ttyUSB")) {
            return "USB Serial Adapter (" + portName + ")";
        } else if (portName.startsWith("ttyACM")) {
            return "USB Communication Device (" + portName + ")";
        } else if (portName.startsWith("ttyS")) {
            return "Hardware Serial Port (" + portName + ")";
        } else if (portName.startsWith("ttyO") || portName.startsWith("ttyAMA")) {
            return "System Serial Port (" + portName + ")";
        } else {
            return portName;
        }
    }

    private boolean containsPort(java.util.List<java.util.Map<String, String>> ports, String port) {
        for (java.util.Map<String, String> portInfo : ports) {
            if (port.equals(portInfo.get("port"))) {
                return true;
            }
        }
        return false;
    }

    @ReactMethod
    public void pingNetworkDevice(String ipAddress, int port, Promise promise) {
        try {
            // Test network connectivity by attempting to reach the device
            java.net.InetAddress inet = java.net.InetAddress.getByName(ipAddress);
            
            // Test if host is reachable (ping-like test)
            boolean isReachable = inet.isReachable(5000); // 5 second timeout
            
            WritableMap result = Arguments.createMap();
            result.putBoolean("reachable", isReachable);
            result.putString("ip", ipAddress);
            result.putInt("port", port);
            
            if (isReachable) {
                // Try to connect to the specific port
                try {
                    java.net.Socket socket = new java.net.Socket();
                    socket.connect(new java.net.InetSocketAddress(ipAddress, port), 3000); // 3 second timeout
                    socket.close();
                    result.putBoolean("portOpen", true);
                    result.putString("status", "Device responding on " + ipAddress + ":" + port);
                } catch (Exception e) {
                    result.putBoolean("portOpen", false);
                    result.putString("status", "Host reachable but port " + port + " not responding");
                }
            } else {
                result.putBoolean("portOpen", false);
                result.putString("status", "Host " + ipAddress + " not reachable");
            }
            
            promise.resolve(result);
            Log.i(TAG, "Network test for " + ipAddress + ":" + port + " - Reachable: " + isReachable);
        } catch (Exception e) {
            WritableMap errorResult = Arguments.createMap();
            errorResult.putBoolean("reachable", false);
            errorResult.putBoolean("portOpen", false);
            errorResult.putString("status", "Network test failed: " + e.getMessage());
            errorResult.putString("ip", ipAddress);
            errorResult.putInt("port", port);
            
            promise.resolve(errorResult); // Resolve with error info rather than reject
            Log.e(TAG, "Error testing network connectivity", e);
        }
    }
}