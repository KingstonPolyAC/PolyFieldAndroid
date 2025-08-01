package com.polyfield;

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

public class PolyFieldModule extends ReactContextBaseJavaModule {
    private static final String TAG = "PolyFieldModule";
    private Gson gson = new Gson();
    private boolean isInitialized = false;

    public PolyFieldModule(ReactApplicationContext reactContext) {
        super(reactContext);
        initializeIfNeeded();
    }

    @Override
    public String getName() {
        return "PolyField";
    }

    private void initializeIfNeeded() {
        if (!isInitialized) {
            try {
                Mobile.initialize();
                isInitialized = true;
                Log.i(TAG, "PolyField Mobile initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize PolyField Mobile", e);
            }
        }
    }

    @ReactMethod
    public void setDemoMode(boolean enabled, Promise promise) {
        try {
            initializeIfNeeded();
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
            initializeIfNeeded();
            boolean demoMode = Mobile.getDemoMode();
            promise.resolve(demoMode);
        } catch (Exception e) {
            Log.e(TAG, "Error getting demo mode", e);
            promise.reject("DEMO_MODE_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void getCalibration(String deviceType, Promise promise) {
        try {
            initializeIfNeeded();
            String calibrationJson = Mobile.getCalibrationJSON(deviceType);
            JsonObject calibrationData = JsonParser.parseString(calibrationJson).getAsJsonObject();

            WritableMap result = jsonObjectToWritableMap(calibrationData);
            promise.resolve(result);

        } catch (Exception e) {
            Log.e(TAG, "Error getting calibration for " + deviceType, e);
            promise.reject("CALIBRATION_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void saveCalibration(String deviceType, ReadableMap calibrationData, Promise promise) {
        try {
            initializeIfNeeded();

            // Convert ReadableMap to JSON string
            JsonObject jsonData = readableMapToJsonObject(calibrationData);
            String jsonString = gson.toJson(jsonData);

            Mobile.saveCalibrationJSON(deviceType, jsonString);
            promise.resolve(true);

            Log.i(TAG, "Calibration saved for " + deviceType);

        } catch (Exception e) {
            Log.e(TAG, "Error saving calibration for " + deviceType, e);
            promise.reject("CALIBRATION_SAVE_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void setCircleCentre(String deviceType, Promise promise) {
        try {
            initializeIfNeeded();
            String resultJson = Mobile.setCircleCentre(deviceType);
            JsonObject resultData = JsonParser.parseString(resultJson).getAsJsonObject();

            WritableMap result = jsonObjectToWritableMap(resultData);
            promise.resolve(result);

            Log.i(TAG, "Circle centre set for " + deviceType);

        } catch (Exception e) {
            Log.e(TAG, "Error setting circle centre for " + deviceType, e);
            promise.reject("CENTRE_SET_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void verifyCircleEdge(String deviceType, Promise promise) {
        try {
            initializeIfNeeded();
            String resultJson = Mobile.verifyCircleEdge(deviceType);
            JsonObject resultData = JsonParser.parseString(resultJson).getAsJsonObject();

            WritableMap result = jsonObjectToWritableMap(resultData);
            promise.resolve(result);

            Log.i(TAG, "Circle edge verified for " + deviceType);

        } catch (Exception e) {
            Log.e(TAG, "Error verifying circle edge for " + deviceType, e);
            promise.reject("EDGE_VERIFY_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void measureThrow(String deviceType, Promise promise) {
        try {
            initializeIfNeeded();
            String distance = Mobile.measureThrow(deviceType);

            // Return both the formatted distance and raw number
            WritableMap result = Arguments.createMap();
            result.putString("distance", distance);
            result.putDouble("meters", Double.parseDouble(distance));
            result.putString("formatted", distance + " m");

            promise.resolve(result);

            Log.i(TAG, "Throw measured: " + distance + " m");

        } catch (Exception e) {
            Log.e(TAG, "Error measuring throw for " + deviceType, e);
            promise.reject("THROW_MEASURE_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void measureWind(Promise promise) {
        try {
            initializeIfNeeded();
            String windSpeed = Mobile.measureWind();

            WritableMap result = Arguments.createMap();
            result.putString("windSpeed", windSpeed);
            result.putString("formatted", windSpeed + " m/s");

            promise.resolve(result);

            Log.i(TAG, "Wind measured: " + windSpeed + " m/s");

        } catch (Exception e) {
            Log.e(TAG, "Error measuring wind", e);
            promise.reject("WIND_MEASURE_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void getThrowCoordinates(Promise promise) {
        try {
            initializeIfNeeded();
            String coordinatesJson = Mobile.getThrowCoordinatesJSON();

            // Parse and convert to React Native format
            Object coordinatesData = gson.fromJson(coordinatesJson, Object.class);
            WritableMap result = Arguments.createMap();
            result.putString("coordinates", coordinatesJson);

            promise.resolve(result);

        } catch (Exception e) {
            Log.e(TAG, "Error getting throw coordinates", e);
            promise.reject("COORDINATES_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void getThrowStatistics(String circleType, Promise promise) {
        try {
            initializeIfNeeded();
            String statsJson = Mobile.getThrowStatisticsJSON(circleType);

            if (statsJson.contains("error")) {
                WritableMap errorResult = Arguments.createMap();
                errorResult.putString("error", "No throws found for " + circleType);
                promise.resolve(errorResult);
                return;
            }

            JsonObject statsData = JsonParser.parseString(statsJson).getAsJsonObject();
            WritableMap result = jsonObjectToWritableMap(statsData);

            promise.resolve(result);

        } catch (Exception e) {
            Log.e(TAG, "Error getting throw statistics for " + circleType, e);
            promise.reject("STATISTICS_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void clearThrowCoordinates(Promise promise) {
        try {
            initializeIfNeeded();
            Mobile.clearThrowCoordinates();
            promise.resolve(true);

            Log.i(TAG, "Throw coordinates cleared");

        } catch (Exception e) {
            Log.e(TAG, "Error clearing throw coordinates", e);
            promise.reject("CLEAR_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void getUKARadius(String circleType, Promise promise) {
        try {
            initializeIfNeeded();
            double radius = Mobile.getUKARadius(circleType);
            promise.resolve(radius);

        } catch (Exception e) {
            Log.e(TAG, "Error getting UKA radius for " + circleType, e);
            promise.reject("RADIUS_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void getTolerance(String circleType, Promise promise) {
        try {
            initializeIfNeeded();
            double tolerance = Mobile.getTolerance(circleType);
            promise.resolve(tolerance);

        } catch (Exception e) {
            Log.e(TAG, "Error getting tolerance for " + circleType, e);
            promise.reject("TOLERANCE_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void resetCalibration(String deviceType, Promise promise) {
        try {
            initializeIfNeeded();
            Mobile.resetCalibration(deviceType);
            promise.resolve(true);

            Log.i(TAG, "Calibration reset for " + deviceType);

        } catch (Exception e) {
            Log.e(TAG, "Error resetting calibration for " + deviceType, e);
            promise.reject("RESET_ERROR", e.getMessage(), e);
        }
    }

    // Helper methods to convert between React Native and JSON formats
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
                } else {
                    // For arrays or complex objects, convert to string
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
}