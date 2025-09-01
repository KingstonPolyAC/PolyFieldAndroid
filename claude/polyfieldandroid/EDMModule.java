package com.polyfieldandroid;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonArray;

import mobile.Mobile;

import android.util.Log;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbConstants;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.app.PendingIntent;
import java.util.HashMap;

public class EDMModule extends ReactContextBaseJavaModule {
    private static final String TAG = "EDMModule";
    private static final String ACTION_USB_PERMISSION = "com.polyfieldandroid.USB_PERMISSION";
    private Gson gson = new Gson();
    private Promise pendingPermissionPromise = null;

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
    public void listUsbDevices(Promise promise) {
        try {
            UsbManager usbManager = (UsbManager) getReactApplicationContext().getSystemService(Context.USB_SERVICE);
            if (usbManager == null) {
                promise.reject("USB_ERROR", "UsbManager not available");
                return;
            }
            
            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            WritableArray devices = Arguments.createArray();
            
            for (UsbDevice device : deviceList.values()) {
                WritableMap deviceInfo = Arguments.createMap();
                deviceInfo.putString("deviceName", device.getDeviceName());
                deviceInfo.putString("manufacturerName", device.getManufacturerName() != null ? device.getManufacturerName() : "Unknown");
                deviceInfo.putString("productName", device.getProductName() != null ? device.getProductName() : "Unknown");
                deviceInfo.putInt("vendorId", device.getVendorId());
                deviceInfo.putInt("productId", device.getProductId());
                deviceInfo.putInt("deviceClass", device.getDeviceClass());
                deviceInfo.putString("port", device.getDeviceName()); // Use device name as port identifier
                
                // Create description for user
                String description = String.format("%s - %s (VID:%04X PID:%04X)", 
                    device.getManufacturerName() != null ? device.getManufacturerName() : "Unknown",
                    device.getProductName() != null ? device.getProductName() : "USB Device",
                    device.getVendorId(),
                    device.getProductId()
                );
                deviceInfo.putString("description", description);
                
                devices.pushMap(deviceInfo);
                Log.d(TAG, "Found USB device: " + description + " at " + device.getDeviceName());
            }
            
            WritableMap result = Arguments.createMap();
            result.putArray("ports", devices);
            result.putString("method", "android_usb_host_api");
            result.putInt("count", devices.size());
            
            promise.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error listing USB devices", e);
            promise.reject("USB_ERROR", e.getMessage(), e);
        }
    }
    
    @ReactMethod
    public void requestUsbPermission(String deviceName, Promise promise) {
        try {
            UsbManager usbManager = (UsbManager) getReactApplicationContext().getSystemService(Context.USB_SERVICE);
            if (usbManager == null) {
                promise.reject("USB_ERROR", "UsbManager not available");
                return;
            }
            
            // Find the device by name
            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            UsbDevice targetDevice = null;
            
            for (UsbDevice device : deviceList.values()) {
                if (device.getDeviceName().equals(deviceName)) {
                    targetDevice = device;
                    break;
                }
            }
            
            if (targetDevice == null) {
                promise.reject("USB_ERROR", "Device not found: " + deviceName);
                return;
            }
            
            // Check if we already have permission
            if (usbManager.hasPermission(targetDevice)) {
                WritableMap result = Arguments.createMap();
                result.putString("status", "already_granted");
                result.putString("deviceName", deviceName);
                promise.resolve(result);
                return;
            }
            
            // Store the promise for when permission is received
            pendingPermissionPromise = promise;
            
            // Register broadcast receiver for permission result
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            getReactApplicationContext().registerReceiver(usbPermissionReceiver, filter);
            
            // Request permission
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                getReactApplicationContext(),
                0,
                new Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE
            );
            
            usbManager.requestPermission(targetDevice, permissionIntent);
            Log.d(TAG, "USB permission requested for device: " + deviceName);
            
        } catch (Exception e) {
            Log.e(TAG, "Error requesting USB permission", e);
            promise.reject("USB_PERMISSION_ERROR", e.getMessage(), e);
        }
    }
    
    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    
                    if (pendingPermissionPromise != null) {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            WritableMap result = Arguments.createMap();
                            result.putString("status", "granted");
                            result.putString("deviceName", device != null ? device.getDeviceName() : "unknown");
                            pendingPermissionPromise.resolve(result);
                            Log.d(TAG, "USB permission granted for device: " + (device != null ? device.getDeviceName() : "unknown"));
                        } else {
                            pendingPermissionPromise.reject("USB_PERMISSION_DENIED", "User denied USB permission");
                            Log.d(TAG, "USB permission denied by user");
                        }
                        pendingPermissionPromise = null;
                    }
                    
                    // Unregister receiver
                    try {
                        getReactApplicationContext().unregisterReceiver(usbPermissionReceiver);
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to unregister USB permission receiver", e);
                    }
                }
            }
        }
    };
    
    @ReactMethod
    public void connectUsbDevice(String deviceType, String deviceName, Promise promise) {
        try {
            UsbManager usbManager = (UsbManager) getReactApplicationContext().getSystemService(Context.USB_SERVICE);
            if (usbManager == null) {
                promise.reject("USB_ERROR", "UsbManager not available");
                return;
            }
            
            // Find the device by name
            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            UsbDevice targetDevice = null;
            
            for (UsbDevice device : deviceList.values()) {
                if (device.getDeviceName().equals(deviceName)) {
                    targetDevice = device;
                    break;
                }
            }
            
            if (targetDevice == null) {
                promise.reject("USB_ERROR", "Device not found: " + deviceName);
                return;
            }
            
            // Check if we have permission
            if (!usbManager.hasPermission(targetDevice)) {
                promise.reject("USB_PERMISSION_ERROR", "USB permission not granted for device: " + deviceName);
                return;
            }
            
            // Open the device connection
            UsbDeviceConnection connection = usbManager.openDevice(targetDevice);
            if (connection == null) {
                promise.reject("USB_CONNECTION_ERROR", "Failed to open USB device connection");
                return;
            }
            
            // Find the first available interface (usually the serial interface for FTDI devices)
            UsbInterface usbInterface = null;
            for (int i = 0; i < targetDevice.getInterfaceCount(); i++) {
                UsbInterface iface = targetDevice.getInterface(i);
                if (iface.getInterfaceClass() == 0xFF || // Vendor specific (FTDI)
                    iface.getInterfaceClass() == 0x02) { // Communication Device Class
                    usbInterface = iface;
                    break;
                }
            }
            
            if (usbInterface == null) {
                connection.close();
                promise.reject("USB_INTERFACE_ERROR", "No suitable USB interface found");
                return;
            }
            
            // Claim the interface
            if (!connection.claimInterface(usbInterface, true)) {
                connection.close();
                promise.reject("USB_INTERFACE_ERROR", "Failed to claim USB interface");
                return;
            }
            
            Log.d(TAG, "Successfully connected to USB device via Android USB Host API: " + deviceName);
            
            // Register the device with the Go Mobile library as a USB Android device
            try {
                String mobileResultJson = Mobile.registerUSBDevice(deviceType, deviceName);
                JsonObject mobileResultData = JsonParser.parseString(mobileResultJson).getAsJsonObject();
                
                if (mobileResultData.has("error")) {
                    // USB connection succeeded but Mobile library registration failed
                    connection.releaseInterface(usbInterface);
                    connection.close();
                    promise.reject("MOBILE_REGISTRATION_ERROR", "USB connected but Mobile library registration failed: " + mobileResultData.get("error").getAsString());
                    return;
                }
                
                Log.d(TAG, "USB device registered with Mobile library: " + mobileResultJson);
            } catch (Exception mobileError) {
                // USB connection succeeded but Mobile library registration failed
                connection.releaseInterface(usbInterface);
                connection.close();
                promise.reject("MOBILE_REGISTRATION_ERROR", "USB connected but Mobile library registration failed: " + mobileError.getMessage());
                return;
            }
            
            // Store connection info - both USB and Mobile library are now connected
            WritableMap result = Arguments.createMap();
            result.putString("success", "true");
            result.putString("message", "Connected to " + deviceType + " via USB Host API and registered with Mobile library");
            result.putString("deviceName", deviceName);
            result.putString("method", "android_usb_host_api_with_mobile");
            result.putInt("interfaceCount", targetDevice.getInterfaceCount());
            result.putString("manufacturerName", targetDevice.getManufacturerName());
            result.putString("productName", targetDevice.getProductName());
            
            promise.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error connecting to USB device", e);
            promise.reject("USB_CONNECTION_ERROR", e.getMessage(), e);
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
                String error = readingData.get("error").getAsString();
                
                // Check if this is a USB delegation request
                if ("USB_ANDROID_DELEGATE".equals(error)) {
                    // Handle USB communication via Android USB Host API
                    getUSBEDMReading(deviceType, promise);
                    return;
                }
                
                promise.reject("EDM_READING_ERROR", error);
                return;
            }
            
            WritableMap result = jsonObjectToWritableMap(readingData);
            promise.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error getting EDM reading", e);
            promise.reject("EDM_READING_ERROR", e.getMessage(), e);
        }
    }

    private void getUSBEDMReading(String deviceType, Promise promise) {
        try {
            Log.d(TAG, "Getting real EDM reading via USB for device: " + deviceType);
            
            // Find the USB device connection
            UsbManager usbManager = (UsbManager) getReactApplicationContext().getSystemService(Context.USB_SERVICE);
            if (usbManager == null) {
                promise.reject("USB_ERROR", "UsbManager not available");
                return;
            }
            
            // Find connected EDM device (assuming it's the only USB device)
            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            UsbDevice edmDevice = null;
            for (UsbDevice device : deviceList.values()) {
                // Look for FTDI device (common for EDM devices)
                if (device.getVendorId() == 0x0403) { // FTDI vendor ID
                    edmDevice = device;
                    break;
                }
            }
            
            if (edmDevice == null) {
                promise.reject("USB_DEVICE_NOT_FOUND", "EDM USB device not found");
                return;
            }
            
            // Open connection
            UsbDeviceConnection connection = usbManager.openDevice(edmDevice);
            if (connection == null) {
                promise.reject("USB_CONNECTION_ERROR", "Failed to open USB device connection");
                return;
            }
            
            try {
                // Find and claim the interface
                UsbInterface usbInterface = null;
                for (int i = 0; i < edmDevice.getInterfaceCount(); i++) {
                    UsbInterface iface = edmDevice.getInterface(i);
                    if (iface.getInterfaceClass() == 0xFF || iface.getInterfaceClass() == 0x02) {
                        usbInterface = iface;
                        break;
                    }
                }
                
                if (usbInterface == null || !connection.claimInterface(usbInterface, true)) {
                    connection.close();
                    promise.reject("USB_INTERFACE_ERROR", "Failed to claim USB interface");
                    return;
                }
                
                // Find endpoints first
                UsbEndpoint outEndpoint = null;
                UsbEndpoint inEndpoint = null;
                
                for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                    UsbEndpoint endpoint = usbInterface.getEndpoint(i);
                    if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                        outEndpoint = endpoint;
                    } else if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                        inEndpoint = endpoint;
                    }
                }
                
                if (outEndpoint == null || inEndpoint == null) {
                    connection.releaseInterface(usbInterface);
                    connection.close();
                    promise.reject("USB_ENDPOINT_ERROR", "Failed to find USB endpoints");
                    return;
                }
                
                // Configure serial parameters FIRST for Mato MTS-602R (9600-8-1-0)
                try {
                    Log.d(TAG, "Configuring FTDI for Mato MTS-602R...");
                    
                    // Reset the device
                    int result = connection.controlTransfer(0x40, 0, 0, 0, null, 0, 1000);
                    Log.d(TAG, "Reset device result: " + result);
                    
                    // Set baud rate to 9600
                    int baudValue = 0x4138; // 9600 baud for FTDI
                    result = connection.controlTransfer(0x40, 3, baudValue, 0, null, 0, 1000);
                    Log.d(TAG, "Set baud rate 9600 result: " + result);
                    
                    // Set data characteristics: 8 data bits, 1 stop bit, no parity
                    result = connection.controlTransfer(0x40, 4, 0x0008, 0, null, 0, 1000);
                    Log.d(TAG, "Set data format (8-1-0) result: " + result);
                    
                    // Set flow control to none
                    result = connection.controlTransfer(0x40, 2, 0, 0, null, 0, 1000);
                    Log.d(TAG, "Set flow control result: " + result);
                    
                    // Brief pause after configuration
                    Thread.sleep(100);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Failed to configure serial parameters: " + e.getMessage());
                    connection.releaseInterface(usbInterface);
                    connection.close();
                    promise.reject("SERIAL_CONFIG_ERROR", "Failed to configure serial parameters: " + e.getMessage());
                    return;
                }
                
                // Try different EDM commands for Mato MTS-602R
                byte[][] possibleCommands = {
                    {0x11, 0x0d, 0x0a},           // Standard command
                    {0x11},                       // Just 0x11
                    {0x05},                       // ENQ enquiry  
                    "MEAS\r\n".getBytes(),        // Text command
                    "GET\r\n".getBytes(),         // Alternative text command
                    {0x11, 0x0d},                 // Without LF
                };
                
                String[] commandNames = {
                    "Standard (0x11 0x0d 0x0a)",
                    "Simple (0x11)",
                    "ENQ (0x05)", 
                    "MEAS text command",
                    "GET text command",
                    "0x11 0x0d (no LF)"
                };
                
                byte[] edmCommand = possibleCommands[0]; // Back to standard 0x11, 0x0d, 0x0a
                String commandName = commandNames[0];
                
                Log.d(TAG, "Sending " + commandName + " to Mato MTS-602R: " + java.util.Arrays.toString(edmCommand));
                
                // Send command
                int bytesSent = connection.bulkTransfer(outEndpoint, edmCommand, edmCommand.length, 5000);
                if (bytesSent < 0) {
                    connection.releaseInterface(usbInterface);
                    connection.close();
                    promise.reject("USB_SEND_ERROR", "Failed to send EDM command to MTS-602R");
                    return;
                }
                
                Log.d(TAG, "EDM command sent successfully to MTS-602R, bytes: " + bytesSent);
                
                // MTS-602R needs minimum 3 seconds to process and respond
                Log.d(TAG, "Waiting 3 seconds for MTS-602R to process command...");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Sleep interrupted: " + e.getMessage());
                }
                
                // Then read response with additional timeout for safety
                byte[] buffer = new byte[1024];
                int bytesRead = connection.bulkTransfer(inEndpoint, buffer, buffer.length, 10000);
                if (bytesRead < 0) {
                    connection.releaseInterface(usbInterface);
                    connection.close();
                    promise.reject("USB_READ_ERROR", "Failed to read EDM response after timeout");
                    return;
                }
                
                Log.d(TAG, "EDM response received (" + bytesRead + " bytes)");
                
                // Parse response - handle MTS-602R format
                String response = new String(buffer, 0, bytesRead, "UTF-8").trim();
                Log.d(TAG, "Raw MTS-602R response: '" + response + "' (length: " + bytesRead + " bytes)");
                
                // Clean up the response - remove backticks, quotes, and unwanted characters
                response = response.replaceAll("[`'\"]", "").trim();
                Log.d(TAG, "Cleaned response: '" + response + "'");
                
                // Parse standard EDM format: "0008390 1001021 3080834 83"
                try {
                    WritableMap result = Arguments.createMap();
                    
                    if (response.contains(" ")) {
                        String[] parts = response.trim().split("\\s+");
                        Log.d(TAG, "Found " + parts.length + " parts: " + java.util.Arrays.toString(parts));
                        
                        if (parts.length >= 4) {
                            // Parse standard EDM format
                            // Distance: 0008390 = 8390mm slope distance
                            double slopeDistance = Double.parseDouble(parts[0]);
                            
                            // Vertical Angle: 1001021 = 100°10'21" in DDDMMSS format
                            String vazStr = parts[1];
                            double vaz = parseDDDMMSSAngle(vazStr);
                            
                            // Horizontal Angle: 3080834 = 308°08'34" in DDDMMSS format  
                            String harStr = parts[2];
                            double har = parseDDDMMSSAngle(harStr);
                            
                            // Status code in parts[3] (ignore for now)
                            
                            result.putDouble("slopeDistanceMm", slopeDistance);
                            result.putDouble("vAzDecimal", vaz);
                            result.putDouble("hARDecimal", har);
                            
                            Log.d(TAG, "Parsed EDM reading - SD:" + slopeDistance + "mm VAZ:" + vaz + "° HAR:" + har + "°");
                        } else {
                            throw new NumberFormatException("Not enough parts in EDM response (expected 4): " + parts.length);
                        }
                    } else {
                        throw new NumberFormatException("Invalid EDM response format (no spaces): " + response);
                    }
                    
                    connection.releaseInterface(usbInterface);
                    connection.close();
                    
                    promise.resolve(result);
                    
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse MTS-602R response: " + response, e);
                    connection.releaseInterface(usbInterface);
                    connection.close();
                    promise.reject("EDM_PARSE_ERROR", "Failed to parse MTS-602R response '" + response + "': " + e.getMessage());
                    return;
                }
                
            } catch (Exception e) {
                connection.close();
                throw e;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting USB EDM reading", e);
            promise.reject("USB_EDM_READING_ERROR", e.getMessage(), e);
        }
    }
    
    private void measureThrowViaUSB(String deviceType, double stationX, double stationY, double targetRadius, Promise promise) {
        Log.d(TAG, "Measuring throw distance from circle edge - target radius: " + targetRadius + "m");
        Log.d(TAG, "EDM station position: X=" + stationX + " Y=" + stationY);
        
        try {
            // Get validated double EDM reading with 10-second timeout
            WritableMap reading = getUSBEDMReadingDirectWithTimeout(deviceType, 10000);
            
            if (reading == null) {
                promise.reject("USB_MEASURE_ERROR", "Failed to get reliable EDM reading within 10 seconds");
                return;
            }
            
            double slopeDistanceMm = reading.getDouble("slopeDistanceMm");
            double vazDecimal = reading.getDouble("vAzDecimal");
            double harDecimal = reading.getDouble("hARDecimal");
            
            Log.d(TAG, "Throw measurement raw values: SD=" + slopeDistanceMm + "mm VAZ=" + vazDecimal + "° HAR=" + harDecimal + "°");
            
            // Calculate throw landing point coordinates relative to EDM station (same as verify edge)
            double sdMeters = slopeDistanceMm / 1000.0;
            double vazRad = Math.toRadians(vazDecimal);
            double harRad = Math.toRadians(harDecimal);
            
            // Calculate horizontal distance and landing coordinates relative to EDM station
            double horizontalDistance = sdMeters * Math.sin(vazRad);
            double landingXFromEDM = horizontalDistance * Math.cos(harRad);
            double landingYFromEDM = horizontalDistance * Math.sin(harRad);
            
            Log.d(TAG, "Landing point relative to EDM: X=" + landingXFromEDM + " Y=" + landingYFromEDM);
            
            // Calculate absolute landing coordinates (relative to circle centre at 0,0)
            double landingX = stationX + landingXFromEDM;
            double landingY = stationY + landingYFromEDM;
            
            Log.d(TAG, "Landing point absolute coordinates: X=" + landingX + " Y=" + landingY);
            
            // Calculate distance from circle centre (0,0) to landing point
            double distanceFromCentre = Math.sqrt(landingX * landingX + landingY * landingY);
            
            // Calculate throw distance from circle edge
            double throwDistanceFromEdge = distanceFromCentre - targetRadius;
            
            Log.d(TAG, "Distance from centre: " + distanceFromCentre + "m, throw distance from edge: " + throwDistanceFromEdge + "m");
            
            // Return result with throw distance and landing coordinates
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            result.putBoolean("real_device", true);
            result.putDouble("slopeDistanceMm", slopeDistanceMm);
            result.putDouble("vAzDecimal", vazDecimal);
            result.putDouble("hARDecimal", harDecimal);
            result.putDouble("distance", throwDistanceFromEdge); // Main throw distance from edge
            result.putDouble("distanceFromCentre", distanceFromCentre); // Distance from circle centre
            result.putDouble("landingX", landingX); // Landing coordinates for debugging
            result.putDouble("landingY", landingY);
            result.putString("message", "Throw measured relative to circle edge");
            
            Log.i(TAG, "Throw measured - distance from edge: " + throwDistanceFromEdge + "m (centre distance: " + distanceFromCentre + "m)");
            promise.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error measuring throw distance", e);
            promise.reject("USB_THROW_ERROR", "Failed to measure throw with USB device: " + e.getMessage());
        }
    }
    
    private double parseDDDMMSSAngle(String angleStr) {
        // Parse DDDMMSS format to decimal degrees (e.g. 1001021 = 100°10'21")
        if (angleStr.length() < 6 || angleStr.length() > 7) {
            throw new NumberFormatException("Invalid angle string length: " + angleStr.length() + " for '" + angleStr + "'");
        }
        
        // Pad with leading zero if needed to make it 7 digits
        if (angleStr.length() == 6) {
            angleStr = "0" + angleStr;
        }
        
        try {
            int degrees = Integer.parseInt(angleStr.substring(0, 3));
            int minutes = Integer.parseInt(angleStr.substring(3, 5));
            int seconds = Integer.parseInt(angleStr.substring(5, 7));
            
            if (minutes >= 60 || seconds >= 60) {
                throw new NumberFormatException("Invalid angle values (MM or SS >= 60) in '" + angleStr + "'");
            }
            
            double decimal = degrees + (minutes / 60.0) + (seconds / 3600.0);
            Log.d(TAG, "Parsed angle " + angleStr + " as " + degrees + "°" + minutes + "'" + seconds + "\" = " + decimal + "°");
            
            return decimal;
        } catch (Exception e) {
            throw new NumberFormatException("Failed to parse angle '" + angleStr + "': " + e.getMessage());
        }
    }
    
    private void setCentreViaUSB(String deviceType, Promise promise) {
        // Get real EDM reading from USB device and calculate centre position
        Log.d(TAG, "Setting centre using real USB EDM reading");
        
        try {
            // Get EDM reading directly
            WritableMap readingMap = getUSBEDMReadingDirect(deviceType);
            
            // Parse the reading data
            double slopeDistanceMm = readingMap.getDouble("slopeDistanceMm");
            double vazDecimal = readingMap.getDouble("vAzDecimal");
            double harDecimal = readingMap.getDouble("hARDecimal");
            
            Log.d(TAG, "USB reading for centre: SD=" + slopeDistanceMm + " VAZ=" + vazDecimal + " HAR=" + harDecimal);
            
            // Calculate station coordinates (same logic as Go Mobile library)
            double sdMeters = slopeDistanceMm / 1000.0;
            double vazRad = Math.toRadians(vazDecimal);
            double harRad = Math.toRadians(harDecimal);
            
            double horizontalDistance = sdMeters * Math.sin(vazRad);
            double stationX = -horizontalDistance * Math.cos(harRad);
            double stationY = -horizontalDistance * Math.sin(harRad);
            
            Log.d(TAG, "Calculated station position: X=" + stationX + " Y=" + stationY);
            
            // Return calculated coordinates with real device data
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            result.putBoolean("real_device", true);
            result.putDouble("slopeDistanceMm", slopeDistanceMm);
            result.putDouble("vAzDecimal", vazDecimal);
            result.putDouble("hARDecimal", harDecimal);
            result.putDouble("stationX", stationX);
            result.putDouble("stationY", stationY);
            result.putString("message", "Centre set using real USB MTS-602R reading");
            
            Log.i(TAG, "Centre set using real device - X:" + stationX + " Y:" + stationY + " from SD:" + slopeDistanceMm + "mm");
            promise.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting USB reading or calculating centre", e);
            promise.reject("USB_CENTRE_ERROR", "Failed to set centre with USB device: " + e.getMessage());
        }
    }
    
    private void verifyEdgeViaUSB(String deviceType, double targetRadius, double stationX, double stationY, Promise promise) {
        Log.d(TAG, "Verifying edge relative to centre circle - target radius: " + targetRadius + "m");
        Log.d(TAG, "EDM station position: X=" + stationX + " Y=" + stationY);
        
        try {
            // Get validated double EDM reading with 10-second timeout  
            WritableMap reading = getUSBEDMReadingDirectWithTimeout(deviceType, 10000);
            
            if (reading == null) {
                promise.reject("USB_EDGE_ERROR", "Failed to get reliable EDM reading within 10 seconds");
                return;
            }
            
            double slopeDistanceMm = reading.getDouble("slopeDistanceMm");
            double vazDecimal = reading.getDouble("vAzDecimal");
            double harDecimal = reading.getDouble("hARDecimal");
            
            Log.d(TAG, "Edge verification raw values: SD=" + slopeDistanceMm + "mm VAZ=" + vazDecimal + "° HAR=" + harDecimal + "°");
            
            // Calculate edge point coordinates relative to EDM station
            double sdMeters = slopeDistanceMm / 1000.0;
            double vazRad = Math.toRadians(vazDecimal);
            double harRad = Math.toRadians(harDecimal);
            
            // Calculate horizontal distance and edge coordinates relative to EDM station
            double horizontalDistance = sdMeters * Math.sin(vazRad);
            double edgeXFromEDM = horizontalDistance * Math.cos(harRad);
            double edgeYFromEDM = horizontalDistance * Math.sin(harRad);
            
            Log.d(TAG, "Edge point relative to EDM: X=" + edgeXFromEDM + " Y=" + edgeYFromEDM);
            
            // Calculate absolute edge coordinates (relative to circle centre at 0,0)
            // Station coordinates are relative to centre, so edge = station + reading
            double edgeX = stationX + edgeXFromEDM;
            double edgeY = stationY + edgeYFromEDM;
            
            Log.d(TAG, "Edge point absolute coordinates: X=" + edgeX + " Y=" + edgeY);
            
            // Calculate distance from circle centre (0,0) to edge point
            double measuredRadius = Math.sqrt(edgeX * edgeX + edgeY * edgeY);
            
            Log.d(TAG, "Measured radius from centre to edge: " + measuredRadius + "m");
            
            // Calculate tolerance check and difference in mm
            double radiusErrorM = Math.abs(measuredRadius - targetRadius);
            double differenceMm = radiusErrorM * 1000.0; // Convert to mm
            boolean toleranceCheck = differenceMm <= 5.0; // 5mm tolerance for most circles
            
            Log.d(TAG, "Edge verification results: measured=" + measuredRadius + "m target=" + targetRadius + "m diff=" + differenceMm + "mm tolerance=" + toleranceCheck);
            
            // Return result with field names matching React Native expectations
            WritableMap result = Arguments.createMap();
            result.putBoolean("success", true);
            result.putBoolean("real_device", true);
            result.putDouble("slopeDistanceMm", slopeDistanceMm);
            result.putDouble("vAzDecimal", vazDecimal);
            result.putDouble("hARDecimal", harDecimal);
            result.putDouble("measuredRadius", measuredRadius);
            result.putDouble("targetRadius", targetRadius);
            result.putDouble("differenceMm", differenceMm);
            result.putBoolean("toleranceCheck", toleranceCheck);
            result.putDouble("edgeX", edgeX); // For debugging
            result.putDouble("edgeY", edgeY); // For debugging
            result.putString("message", "Edge verified relative to circle centre");
            
            Log.i(TAG, "Edge verified - measured radius:" + measuredRadius + "m target:" + targetRadius + "m difference:" + differenceMm + "mm");
            promise.resolve(result);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in edge verification", e);
            promise.reject("USB_EDGE_ERROR", "Failed to verify edge: " + e.getMessage());
        }
    }

    @ReactMethod
    public void setCentre(String deviceType, Promise promise) {
        try {
            String resultJson = Mobile.setCentre(deviceType);
            JsonObject resultData = JsonParser.parseString(resultJson).getAsJsonObject();
            
            if (resultData.has("error")) {
                String error = resultData.get("error").getAsString();
                
                // Check if this is a USB delegation request
                if ("USB_ANDROID_DELEGATE".equals(error)) {
                    // Handle USB communication via Android USB Host API
                    setCentreViaUSB(deviceType, promise);
                    return;
                }
                
                promise.reject("SET_CENTRE_ERROR", error);
                return;
            }
            
            WritableMap result = jsonObjectToWritableMap(resultData);
            promise.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error setting centre", e);
            promise.reject("SET_CENTRE_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void verifyEdge(String deviceType, double targetRadius, ReadableMap stationCoordinates, Promise promise) {
        try {
            Log.d(TAG, "Starting edge verification for device: " + deviceType + ", target radius: " + targetRadius);
            
            // Extract station coordinates  
            double stationX = stationCoordinates.hasKey("x") ? stationCoordinates.getDouble("x") : 0.0;
            double stationY = stationCoordinates.hasKey("y") ? stationCoordinates.getDouble("y") : 0.0;
            Log.d(TAG, "Station coordinates: X=" + stationX + " Y=" + stationY);
            
            // For USB devices, bypass Mobile library and go directly to USB communication
            Log.d(TAG, "Delegating directly to USB edge verification with station coords");
            verifyEdgeViaUSB(deviceType, targetRadius, stationX, stationY, promise);
            
        } catch (Exception e) {
            Log.e(TAG, "Error verifying edge", e);
            promise.reject("VERIFY_EDGE_ERROR", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void measureThrow(String deviceType, ReadableMap stationCoordinates, double targetRadius, Promise promise) {
        try {
            Log.d(TAG, "Measuring throw - bypassing Go Mobile library and using direct USB delegation");
            
            // Extract station coordinates  
            double stationX = stationCoordinates.hasKey("x") ? stationCoordinates.getDouble("x") : 0.0;
            double stationY = stationCoordinates.hasKey("y") ? stationCoordinates.getDouble("y") : 0.0;
            Log.d(TAG, "Station coordinates for throw: X=" + stationX + " Y=" + stationY + ", target radius=" + targetRadius);
            
            // Go directly to USB communication since we're using USB delegation for calibration too
            // This bypasses the calibration check in the Go library that doesn't know about USB calibration
            measureThrowViaUSB(deviceType, stationX, stationY, targetRadius, promise);
            
        } catch (Exception e) {
            Log.e(TAG, "Error measuring throw", e);
            promise.reject("MEASURE_THROW_ERROR", e.getMessage(), e);
        }
    }
    
    private WritableMap getUSBEDMReadingDirectWithTimeout(String deviceType, int timeoutMs) {
        Log.d(TAG, "Getting reliable EDM reading via USB with timeout: " + timeoutMs + "ms");
        
        final Exception[] exception = {null};
        final WritableMap[] result = {null};
        
        Thread readingThread = new Thread(() -> {
            try {
                result[0] = getUSBEDMReadingDirect(deviceType);
            } catch (Exception e) {
                Log.e(TAG, "Error in timeout reading thread", e);
                exception[0] = e;
            }
        });
        
        readingThread.start();
        
        try {
            readingThread.join(timeoutMs);
        } catch (InterruptedException e) {
            Log.e(TAG, "Reading thread interrupted", e);
            return null;
        }
        
        if (readingThread.isAlive()) {
            readingThread.interrupt();
            Log.e(TAG, "EDM reading timeout after " + timeoutMs + "ms");
            return null;
        }
        
        if (exception[0] != null) {
            Log.e(TAG, "Exception in reading thread", exception[0]);
            return null;
        }
        
        return result[0];
    }

    private WritableMap getUSBEDMReadingDirect(String deviceType) throws Exception {
        Log.d(TAG, "Getting reliable EDM reading via USB with two-reading validation for device: " + deviceType);
        
        // Take two readings with 250ms delay and compare within 3mm tolerance
        EDMReading reading1 = triggerSingleUSBEDMRead(deviceType);
        Log.d(TAG, "First reading: SD=" + reading1.slopeDistanceMm + "mm VAZ=" + reading1.vazDecimal + "° HAR=" + reading1.harDecimal + "°");
        
        // No delay - start second reading immediately after first response for speed
        
        EDMReading reading2 = triggerSingleUSBEDMRead(deviceType);
        Log.d(TAG, "Second reading: SD=" + reading2.slopeDistanceMm + "mm VAZ=" + reading2.vazDecimal + "° HAR=" + reading2.harDecimal + "°");
        
        // Compare readings within 3mm tolerance (sdToleranceMm from working Go code)
        double slopeDifference = Math.abs(reading1.slopeDistanceMm - reading2.slopeDistanceMm);
        Log.d(TAG, "Slope distance difference: " + slopeDifference + "mm (tolerance: 3.0mm)");
        
        if (slopeDifference <= 3.0) {
            // Readings are consistent - average them
            double avgSlopeDistance = (reading1.slopeDistanceMm + reading2.slopeDistanceMm) / 2.0;
            double avgVaz = (reading1.vazDecimal + reading2.vazDecimal) / 2.0;
            double avgHar = (reading1.harDecimal + reading2.harDecimal) / 2.0;
            
            Log.d(TAG, "Readings consistent - averaged: SD=" + avgSlopeDistance + "mm VAZ=" + avgVaz + "° HAR=" + avgHar + "°");
            
            WritableMap result = Arguments.createMap();
            result.putDouble("slopeDistanceMm", avgSlopeDistance);
            result.putDouble("vAzDecimal", avgVaz);
            result.putDouble("hARDecimal", avgHar);
            
            return result;
        } else {
            throw new Exception("Readings inconsistent. R1(SD): " + Math.round(reading1.slopeDistanceMm) + "mm, R2(SD): " + Math.round(reading2.slopeDistanceMm) + "mm");
        }
    }
    
    private static class EDMReading {
        double slopeDistanceMm;
        double vazDecimal;
        double harDecimal;
        
        EDMReading(double slopeDistanceMm, double vazDecimal, double harDecimal) {
            this.slopeDistanceMm = slopeDistanceMm;
            this.vazDecimal = vazDecimal;
            this.harDecimal = harDecimal;
        }
    }
    
    private EDMReading triggerSingleUSBEDMRead(String deviceType) throws Exception {
        Log.d(TAG, "Taking single EDM reading via USB for device: " + deviceType);
        
        // Find the USB device connection
        UsbManager usbManager = (UsbManager) getReactApplicationContext().getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            throw new Exception("UsbManager not available");
        }
        
        // Find connected EDM device (assuming it's the only USB device)
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        UsbDevice edmDevice = null;
        for (UsbDevice device : deviceList.values()) {
            // Look for FTDI device (common for EDM devices)
            if (device.getVendorId() == 0x0403) { // FTDI vendor ID
                edmDevice = device;
                break;
            }
        }
        
        if (edmDevice == null) {
            throw new Exception("EDM USB device not found");
        }
        
        // Open connection
        UsbDeviceConnection connection = usbManager.openDevice(edmDevice);
        if (connection == null) {
            throw new Exception("Failed to open USB device connection");
        }
        
        try {
            // Find and claim the interface
            UsbInterface usbInterface = null;
            for (int i = 0; i < edmDevice.getInterfaceCount(); i++) {
                UsbInterface iface = edmDevice.getInterface(i);
                if (iface.getInterfaceClass() == 0xFF || iface.getInterfaceClass() == 0x02) {
                    usbInterface = iface;
                    break;
                }
            }
            
            if (usbInterface == null || !connection.claimInterface(usbInterface, true)) {
                connection.close();
                throw new Exception("Failed to claim USB interface");
            }
            
            // Find endpoints
            UsbEndpoint outEndpoint = null;
            UsbEndpoint inEndpoint = null;
            
            for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                UsbEndpoint endpoint = usbInterface.getEndpoint(i);
                if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    outEndpoint = endpoint;
                } else if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    inEndpoint = endpoint;
                }
            }
            
            if (outEndpoint == null || inEndpoint == null) {
                connection.releaseInterface(usbInterface);
                connection.close();
                throw new Exception("Failed to find USB endpoints");
            }
            
            // Configure serial parameters for EDM device (9600-8-1-0)
            try {
                Log.d(TAG, "Configuring FTDI for EDM device...");
                
                // Reset the device
                int result = connection.controlTransfer(0x40, 0, 0, 0, null, 0, 1000);
                Log.d(TAG, "Reset device result: " + result);
                
                // Set baud rate to 9600
                int baudValue = 0x4138; // 9600 baud for FTDI
                result = connection.controlTransfer(0x40, 3, baudValue, 0, null, 0, 1000);
                Log.d(TAG, "Set baud rate 9600 result: " + result);
                
                // Set data characteristics: 8 data bits, 1 stop bit, no parity
                result = connection.controlTransfer(0x40, 4, 0x0008, 0, null, 0, 1000);
                Log.d(TAG, "Set data format (8-1-0) result: " + result);
                
                // Set flow control to none
                result = connection.controlTransfer(0x40, 2, 0, 0, null, 0, 1000);
                Log.d(TAG, "Set flow control result: " + result);
                
                // Brief pause after configuration
                Thread.sleep(100);
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to configure serial parameters: " + e.getMessage());
                connection.releaseInterface(usbInterface);
                connection.close();
                throw new Exception("Failed to configure serial parameters: " + e.getMessage());
            }
            
            // Send EDM command 0x11, 0x0d, 0x0a
            byte[] edmCommand = {0x11, 0x0d, 0x0a};
            
            Log.d(TAG, "Sending standard EDM command: " + java.util.Arrays.toString(edmCommand));
            
            // Send command
            int bytesSent = connection.bulkTransfer(outEndpoint, edmCommand, edmCommand.length, 5000);
            if (bytesSent < 0) {
                connection.releaseInterface(usbInterface);
                connection.close();
                throw new Exception("Failed to send EDM command");
            }
            
            Log.d(TAG, "EDM command sent successfully, bytes: " + bytesSent);
            
            // MTS-602R needs minimum 3 seconds to process and respond
            Log.d(TAG, "Waiting 3 seconds for MTS-602R to process command...");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Log.w(TAG, "Sleep interrupted: " + e.getMessage());
            }
            
            // Then read response with additional timeout for safety
            byte[] buffer = new byte[1024];
            int bytesRead = connection.bulkTransfer(inEndpoint, buffer, buffer.length, 10000);
            if (bytesRead < 0) {
                connection.releaseInterface(usbInterface);
                connection.close();
                throw new Exception("Failed to read EDM response after timeout");
            }
            
            Log.d(TAG, "EDM response received (" + bytesRead + " bytes)");
            
            // Parse response
            String response = new String(buffer, 0, bytesRead, "UTF-8").trim();
            Log.d(TAG, "Raw EDM response: '" + response + "' (length: " + bytesRead + " bytes)");
            
            // Clean up the response - remove backticks, quotes, and unwanted characters  
            response = response.replaceAll("[`'\"]", "").trim();
            Log.d(TAG, "Cleaned response: '" + response + "'");
            
            // Parse standard EDM format: "0008390 1001021 3080834 83"
            if (response.contains(" ")) {
                String[] parts = response.trim().split("\\s+");
                Log.d(TAG, "Found " + parts.length + " parts: " + java.util.Arrays.toString(parts));
                
                if (parts.length >= 4) {
                    // Parse standard EDM format
                    // Distance: 0008390 = 8390mm slope distance
                    double slopeDistance = Double.parseDouble(parts[0]);
                    
                    // Vertical Angle: 1001021 = 100°10'21" in DDDMMSS format
                    String vazStr = parts[1];
                    double vaz = parseDDDMMSSAngle(vazStr);
                    
                    // Horizontal Angle: 3080834 = 308°08'34" in DDDMMSS format  
                    String harStr = parts[2];
                    double har = parseDDDMMSSAngle(harStr);
                    
                    // Status code in parts[3] (ignore for now)
                    
                    Log.d(TAG, "Parsed single EDM reading - SD:" + slopeDistance + "mm VAZ:" + vaz + "° HAR:" + har + "°");
                    
                    connection.releaseInterface(usbInterface);
                    connection.close();
                    
                    return new EDMReading(slopeDistance, vaz, har);
                } else {
                    connection.releaseInterface(usbInterface);
                    connection.close();
                    throw new NumberFormatException("Not enough parts in EDM response (expected 4): " + parts.length);
                }
            } else {
                connection.releaseInterface(usbInterface);
                connection.close();
                throw new NumberFormatException("Invalid EDM response format (no spaces): " + response);
            }
            
        } catch (Exception e) {
            connection.close();
            throw e;
        }
    }

    // --- Helper methods ---
    
    private WritableMap jsonObjectToWritableMap(JsonObject jsonObject) {
        WritableMap map = Arguments.createMap();
        
        for (String key : jsonObject.keySet()) {
            JsonElement element = jsonObject.get(key);
            
            if (element.isJsonNull()) {
                map.putNull(key);
            } else if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isBoolean()) {
                    map.putBoolean(key, primitive.getAsBoolean());
                } else if (primitive.isNumber()) {
                    map.putDouble(key, primitive.getAsDouble());
                } else {
                    map.putString(key, primitive.getAsString());
                }
            } else if (element.isJsonObject()) {
                map.putMap(key, jsonObjectToWritableMap(element.getAsJsonObject()));
            } else if (element.isJsonArray()) {
                map.putArray(key, jsonArrayToWritableArray(element.getAsJsonArray()));
            }
        }
        
        return map;
    }
    
    private WritableArray jsonArrayToWritableArray(JsonArray jsonArray) {
        WritableArray array = Arguments.createArray();
        
        for (JsonElement element : jsonArray) {
            if (element.isJsonNull()) {
                array.pushNull();
            } else if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isBoolean()) {
                    array.pushBoolean(primitive.getAsBoolean());
                } else if (primitive.isNumber()) {
                    array.pushDouble(primitive.getAsDouble());
                } else {
                    array.pushString(primitive.getAsString());
                }
            } else if (element.isJsonObject()) {
                array.pushMap(jsonObjectToWritableMap(element.getAsJsonObject()));
            } else if (element.isJsonArray()) {
                array.pushArray(jsonArrayToWritableArray(element.getAsJsonArray()));
            }
        }
        
        return array;
    }
}
