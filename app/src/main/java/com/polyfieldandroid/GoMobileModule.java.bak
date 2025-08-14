package com.polyfieldandroid;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import mobile.Mobile; // Import your Go package

public class GoMobileModule extends ReactContextBaseJavaModule {

    private static final String TAG = "GoMobileModule";
    private Gson gson = new Gson();

    public GoMobileModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "GoMobileModule";
    }

    @ReactMethod
    public void helloWorld(Promise promise) {
        try {
            String result = Mobile.helloWorld();
            promise.resolve(result);
        } catch (Exception e) {
            promise.reject("GO_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void getCircleRadius(String circleType, Promise promise) {
        try {
            double radius = Mobile.getCircleRadius(circleType);
            promise.resolve(radius);
        } catch (Exception e) {
            promise.reject("GO_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void calculateDistance(double x1, double y1, double x2, double y2, Promise promise) {
        try {
            double distance = Mobile.calculateDistance(x1, y1, x2, y2);
            promise.resolve(distance);
        } catch (Exception e) {
            promise.reject("CALCULATION_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void validateThrowCoordinates(double x, double y, double circleRadius, Promise promise) {
        try {
            boolean isValid = Mobile.validateThrowCoordinates(x, y, circleRadius);
            promise.resolve(isValid);
        } catch (Exception e) {
            promise.reject("VALIDATION_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void calculateCircleAccuracy(double measuredRadius, double targetRadius, Promise promise) {
        try {
            String jsonResult = Mobile.calculateCircleAccuracy(measuredRadius, targetRadius);
            JsonObject jsonObject = JsonParser.parseString(jsonResult).getAsJsonObject();

            WritableMap result = Arguments.createMap();
            result.putBoolean("withinTolerance", jsonObject.get("withinTolerance").getAsBoolean());
            result.putDouble("differenceInMm", jsonObject.get("differenceInMm").getAsDouble());
            result.putDouble("measuredRadius", jsonObject.get("measuredRadius").getAsDouble());
            result.putDouble("targetRadius", jsonObject.get("targetRadius").getAsDouble());

            promise.resolve(result);
        } catch (Exception e) {
            promise.reject("ACCURACY_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void setCentre(String circleType, Promise promise) {
        try {
            String jsonResult = Mobile.setCentre(circleType);
            JsonObject jsonObject = JsonParser.parseString(jsonResult).getAsJsonObject();

            WritableMap result = Arguments.createMap();
            result.putBoolean("success", jsonObject.get("success").getAsBoolean());
            result.putDouble("slopeDistanceMm", jsonObject.get("slopeDistanceMm").getAsDouble());
            result.putDouble("vAzDecimal", jsonObject.get("vAzDecimal").getAsDouble());
            result.putDouble("hARDecimal", jsonObject.get("hARDecimal").getAsDouble());
            result.putString("message", jsonObject.get("message").getAsString());

            promise.resolve(result);
        } catch (Exception e) {
            promise.reject("CENTRE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void verifyEdge(String circleType, double targetRadius, Promise promise) {
        try {
            String jsonResult = Mobile.verifyEdge(circleType, targetRadius);
            JsonObject jsonObject = JsonParser.parseString(jsonResult).getAsJsonObject();

            WritableMap result = Arguments.createMap();
            result.putBoolean("success", jsonObject.get("success").getAsBoolean());
            result.putBoolean("toleranceCheck", jsonObject.get("toleranceCheck").getAsBoolean());
            result.putDouble("differenceMm", jsonObject.get("differenceMm").getAsDouble());
            result.putDouble("measuredRadius", jsonObject.get("measuredRadius").getAsDouble());
            result.putString("message", jsonObject.get("message").getAsString());

            promise.resolve(result);
        } catch (Exception e) {
            promise.reject("EDGE_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void measureThrow(String circleType, Promise promise) {
        try {
            String jsonResult = Mobile.measureThrow(circleType);
            JsonObject jsonObject = JsonParser.parseString(jsonResult).getAsJsonObject();

            WritableMap result = Arguments.createMap();
            result.putDouble("distance", jsonObject.get("distance").getAsDouble());
            result.putDouble("x", jsonObject.get("x").getAsDouble());
            result.putDouble("y", jsonObject.get("y").getAsDouble());
            result.putString("timestamp", jsonObject.get("timestamp").getAsString());

            promise.resolve(result);
        } catch (Exception e) {
            promise.reject("THROW_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void measureWind(Promise promise) {
        try {
            double windSpeed = Mobile.measureWind();
            promise.resolve(windSpeed);
        } catch (Exception e) {
            promise.reject("WIND_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void validateCalibration(String circleType, double measuredRadius, Promise promise) {
        try {
            boolean isValid = Mobile.validateCalibration(circleType, measuredRadius);
            promise.resolve(isValid);
        } catch (Exception e) {
            promise.reject("CALIBRATION_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void getToleranceForCircle(String circleType, Promise promise) {
        try {
            double tolerance = Mobile.getToleranceForCircle(circleType);
            promise.resolve(tolerance);
        } catch (Exception e) {
            promise.reject("TOLERANCE_ERROR", e.getMessage());
        }
    }
}