package com.polyfieldandroid;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Space;
import android.widget.RelativeLayout;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

// USB and permissions imports
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import java.util.HashMap;
import java.util.Iterator;

// Using mobile.Mobile with full package name to avoid conflicts

public class MainActivity extends Activity {

    private TextView headerTitle;
    private TextView demoStatusText;
    private TextView statusText;
    private TextView coordinatesText;
    private LinearLayout circleButtonsLayout;
    private LinearLayout measurementButtonsLayout;
    private LinearLayout mainContent;
    private View headerView;
    
    private String currentCircleType = "SHOT";
    private boolean isDemoMode = true;
    private String currentScreen = "SELECT_EVENT_TYPE";
    private String eventType = "Throws"; // Default to Throws
    
    // USB and device management
    private UsbManager usbManager;
    private UsbDevice connectedDevice;
    private PendingIntent permissionIntent;
    private static final String ACTION_USB_PERMISSION = "com.polyfieldandroid.USB_PERMISSION";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private BroadcastReceiver usbReceiver;
    
    // Original color scheme from React Native
    private static final int PRIMARY_BLUE = Color.parseColor("#1976D2");
    private static final int BACKGROUND_GRAY = Color.parseColor("#f5f5f5");
    private static final int CARD_WHITE = Color.WHITE;
    private static final int DEMO_YELLOW = Color.parseColor("#ffeb3b");
    private static final int SUCCESS_GREEN = Color.parseColor("#4CAF50");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adjustDisplayDensity();
        
        // Initialize USB manager and permissions
        initializeUSB();
        
        setupUI();
        
        // Initialize the Go Mobile module (demo mode for UI testing)
        updateStatus("Demo mode - Mobile library initialized for UI testing");
        
        // Check for connected USB devices
        checkConnectedUSBDevices();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        adjustDisplayDensity();
    }

    private double getDemoUKARadius(String circleType) {
        switch (circleType) {
            case "SHOT": return 1.065; // 2.13m diameter
            case "DISCUS": return 1.25; // 2.5m diameter  
            case "HAMMER": return 1.065; // 2.13m diameter
            case "JAVELIN_ARC": return 4.0; // 8m diameter arc
            default: return 1.0;
        }
    }

    private void adjustDisplayDensity() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float screenWidthDp = metrics.widthPixels / metrics.density;
        float screenHeightDp = metrics.heightPixels / metrics.density;
        Configuration config = getResources().getConfiguration();
        
        float scale = 1.0f;
        
        // Determine if this is a tablet (>= 600dp width is typically considered tablet)
        boolean isTablet = screenWidthDp >= 600;
        
        if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (isTablet) {
                scale = 1.2f; // Larger elements for tablet in portrait
            } else if (screenHeightDp < 700) {
                scale = 0.85f; // Smaller for compact phones
            } else {
                scale = 1.0f; // Standard phones
            }
        } else { // Landscape
            if (isTablet) {
                scale = 1.1f; // Slightly larger for tablet landscape
            } else if (screenHeightDp < 400) {
                scale = 0.8f; // Very compact landscape (phones)
            } else {
                scale = 0.9f; // Standard landscape
            }
        }
        
        metrics.density *= scale;
        metrics.scaledDensity *= scale;
        getResources().updateConfiguration(config, metrics);
    }

    private void setupUI() {
        try {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            float screenWidthDp = metrics.widthPixels / metrics.density;
            float screenHeightDp = metrics.heightPixels / metrics.density;
            
            // Create main container with original background color
            LinearLayout rootLayout = new LinearLayout(this);
            rootLayout.setOrientation(LinearLayout.VERTICAL);
            rootLayout.setBackgroundColor(BACKGROUND_GRAY);
            rootLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.MATCH_PARENT));
            
            // Create header matching original design
            createHeader(rootLayout, screenWidthDp, screenHeightDp);
            
            // Create main content area
            ScrollView scrollView = new ScrollView(this);
            scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                0, 1.0f));
            scrollView.setFillViewport(true); // Important for proper scrolling
            
            mainContent = new LinearLayout(this);
            mainContent.setOrientation(LinearLayout.VERTICAL);
            mainContent.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT)); // Explicit layout params for ScrollView child
            int padding = (int)(Math.max(20, screenWidthDp * 0.025) * metrics.density);
            mainContent.setPadding(padding, padding, padding, padding);
            
            // Background debug removed - layout is working
        
        // Show the appropriate screen based on currentScreen
        switch (currentScreen) {
            case "SELECT_EVENT_TYPE":
                setupEventSelectionScreen(screenWidthDp, screenHeightDp);
                break;
            case "DEVICE_SETUP":
                setupDeviceScreen(screenWidthDp, screenHeightDp);
                break;
            case "CALIBRATION_SELECT_CIRCLE":
                setupCircleSelectionScreen(screenWidthDp, screenHeightDp);
                break;
            case "CALIBRATION_SET_CENTRE":
                setupCalibrationCentreScreen(screenWidthDp, screenHeightDp);
                break;
            case "CALIBRATION_VERIFY_EDGE":
                setupCalibrationEdgeScreen(screenWidthDp, screenHeightDp);
                break;
            case "CALIBRATION_EDGE_RESULTS":
                setupEdgeResultsScreen(screenWidthDp, screenHeightDp);
                break;
            case "MEASUREMENT":
                setupMeasurementScreen(screenWidthDp, screenHeightDp);
                break;
        }
        
        scrollView.addView(mainContent);
        rootLayout.addView(scrollView);
        
        // Add bottom navigation for screens that need it
        View bottomNav = createBottomNavigation(screenWidthDp, screenHeightDp);
        if (bottomNav != null) {
            rootLayout.addView(bottomNav);
        }
        
            setContentView(rootLayout);
        } catch (Exception e) {
            // If setupUI fails, create a simple error screen
            LinearLayout errorLayout = new LinearLayout(this);
            errorLayout.setOrientation(LinearLayout.VERTICAL);
            errorLayout.setBackgroundColor(Color.YELLOW);
            errorLayout.setPadding(50, 50, 50, 50);
            
            TextView errorText = new TextView(this);
            errorText.setText("SETUP ERROR: " + e.getMessage() + "\nScreen: " + currentScreen);
            errorText.setTextColor(Color.BLACK);
            errorText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            errorLayout.addView(errorText);
            
            setContentView(errorLayout);
            e.printStackTrace(); // Log the full stack trace
        }
    }
    
    private void createHeader(LinearLayout parent, float screenWidthDp, float screenHeightDp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        
        // Create header container
        RelativeLayout headerLayout = new RelativeLayout(this);
        int headerHeight = (int)(80 * metrics.density); // Fixed reasonable height
        headerLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, headerHeight));
        headerLayout.setBackgroundColor(PRIMARY_BLUE);
        
        // Add shadow/elevation effect
        headerLayout.setElevation(12f);
        
        int horizontalPadding = (int)(20 * metrics.density);
        headerLayout.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        
        // Header title
        headerTitle = new TextView(this);
        headerTitle.setText("PolyField by KACPH");
        headerTitle.setTextColor(Color.WHITE);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(24, screenWidthDp * 0.025f));
        headerTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        
        RelativeLayout.LayoutParams titleParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        titleParams.addRule(RelativeLayout.CENTER_VERTICAL);
        titleParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        headerTitle.setLayoutParams(titleParams);
        
        // Demo status indicator
        demoStatusText = new TextView(this);
        updateDemoStatus();
        demoStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(18, screenWidthDp * 0.02f));
        demoStatusText.setTypeface(null, android.graphics.Typeface.BOLD);
        
        // Create rounded background for demo status
        GradientDrawable demoBackground = new GradientDrawable();
        demoBackground.setCornerRadius(15 * metrics.density);
        demoBackground.setColor(Color.parseColor("#33ffeb3b")); // Semi-transparent yellow
        demoStatusText.setBackground(demoBackground);
        
        int padding = (int)(6 * metrics.density);
        int paddingH = (int)(12 * metrics.density);
        demoStatusText.setPadding(paddingH, padding, paddingH, padding);
        
        RelativeLayout.LayoutParams statusParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        statusParams.addRule(RelativeLayout.CENTER_VERTICAL);
        statusParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        demoStatusText.setLayoutParams(statusParams);
        
        headerLayout.addView(headerTitle);
        headerLayout.addView(demoStatusText);
        parent.addView(headerLayout);
        
        headerView = headerLayout;
    }
    
    private void updateDemoStatus() {
        if (demoStatusText != null) {
            demoStatusText.setText(isDemoMode ? "DEMO" : "REAL");
            demoStatusText.setTextColor(isDemoMode ? DEMO_YELLOW : SUCCESS_GREEN);
        }
    }
    
    private void setupEventSelectionScreen(float screenWidthDp, float screenHeightDp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        
        mainContent.removeAllViews();
        
        // Title
        TextView title = new TextView(this);
        title.setText("Select Event Type");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(24, screenWidthDp * 0.028f));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.parseColor("#333333"));
        title.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = (int)(Math.max(20, screenHeightDp * 0.025) * metrics.density);
        title.setLayoutParams(titleParams);
        mainContent.addView(title);
        
        // Card container
        LinearLayout cardContainer = new LinearLayout(this);
        cardContainer.setOrientation(LinearLayout.HORIZONTAL);
        cardContainer.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0);
        containerParams.weight = 1;
        containerParams.topMargin = (int)(20 * metrics.density);
        cardContainer.setLayoutParams(containerParams);
        
        // Throws card
        LinearLayout throwsCard = createEventCard("🥇", "Throws", 
            "Shot Put, Discus,\nHammer, Javelin", screenWidthDp, screenHeightDp);
        throwsCard.setOnClickListener(v -> navigateToDeviceSetup("Throws"));
        
        // Horizontal Jumps card  
        LinearLayout jumpsCard = createEventCard("🏃", "Horizontal Jumps",
            "Long Jump, Triple Jump,\nPole Vault", screenWidthDp, screenHeightDp);
        jumpsCard.setOnClickListener(v -> navigateToDeviceSetup("Horizontal Jumps"));
        
        cardContainer.addView(throwsCard);
        cardContainer.addView(jumpsCard);
        
        mainContent.addView(cardContainer);
    }
    
    private LinearLayout createEventCard(String icon, String title, String subtitle, 
                                        float screenWidthDp, float screenHeightDp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundColor(CARD_WHITE);
        
        // Card styling matching original
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(CARD_WHITE);
        cardBackground.setCornerRadius(20 * metrics.density);
        cardBackground.setStroke((int)(3 * metrics.density), Color.parseColor("#e0e0e0"));
        card.setBackground(cardBackground);
        card.setElevation(8f);
        
        // Card dimensions and margins
        int cardWidth = (int)(Math.max(280, screenWidthDp * 0.35) * metrics.density);
        int cardHeight = (int)(Math.max(200, screenHeightDp * 0.3) * metrics.density);
        int cardPadding = (int)(Math.max(30, screenWidthDp * 0.025) * metrics.density);
        int cardMargin = (int)(20 * metrics.density);
        
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(cardWidth, cardHeight);
        cardParams.setMargins(cardMargin, cardMargin, cardMargin, cardMargin);
        card.setLayoutParams(cardParams);
        card.setPadding(cardPadding, cardPadding, cardPadding, cardPadding);
        
        // Icon
        TextView iconText = new TextView(this);
        iconText.setText(icon);
        iconText.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(48, screenWidthDp * 0.055f));
        iconText.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconParams.bottomMargin = (int)(15 * metrics.density);
        iconText.setLayoutParams(iconParams);
        
        // Title
        TextView titleText = new TextView(this);
        titleText.setText(title);
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(22, screenWidthDp * 0.028f));
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setTextColor(Color.parseColor("#333333"));
        titleText.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = (int)(10 * metrics.density);
        titleText.setLayoutParams(titleParams);
        
        // Subtitle
        TextView subtitleText = new TextView(this);
        subtitleText.setText(subtitle);
        subtitleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(16, screenWidthDp * 0.02f));
        subtitleText.setTextColor(Color.parseColor("#666666"));
        subtitleText.setGravity(Gravity.CENTER);
        subtitleText.setLineSpacing(0, 1.4f);
        
        card.addView(iconText);
        card.addView(titleText);
        card.addView(subtitleText);
        
        return card;
    }
    
    private void navigateToDeviceSetup(String selectedEventType) {
        this.eventType = selectedEventType; // Set the event type
        currentScreen = "DEVICE_SETUP";
        setupUI(); // Refresh the UI
    }
    
    private void setupDeviceScreen(float screenWidthDp, float screenHeightDp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        mainContent.removeAllViews();
        
        // Title
        TextView title = new TextView(this);
        title.setText("Device Setup");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(24, screenWidthDp * 0.028f));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.parseColor("#333333"));
        title.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = (int)(Math.max(25, screenHeightDp * 0.03) * metrics.density);
        title.setLayoutParams(titleParams);
        mainContent.addView(title);
        
        // Demo mode toggle
        LinearLayout demoToggle = createDemoToggle(screenWidthDp, screenHeightDp);
        mainContent.addView(demoToggle);
        
        // Device Configuration Cards
        if (eventType.equals("Throws")) {
            // EDM Device Configuration
            LinearLayout edmCard = createDeviceConfigCard("EDM Device", "Distance Measurement", screenWidthDp);
            mainContent.addView(edmCard);
        } else if (eventType.equals("Horizontal Jumps")) {
            // Wind Gauge Configuration
            LinearLayout windCard = createDeviceConfigCard("Wind Gauge", "Wind Speed & Direction", screenWidthDp);
            mainContent.addView(windCard);
        }
        
        // Continue button - conditional based on event type
        Button continueBtn = createPrimaryButton(getDeviceContinueButtonText(), screenWidthDp);
        continueBtn.setOnClickListener(v -> navigateFromDeviceSetup());
        
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        continueParams.topMargin = (int)(40 * metrics.density);
        continueParams.gravity = Gravity.CENTER_HORIZONTAL;
        continueBtn.setLayoutParams(continueParams);
        mainContent.addView(continueBtn);
    }

    private LinearLayout createDeviceConfigCard(String deviceName, String description, float screenWidthDp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(CARD_WHITE);
        
        // Card styling
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(CARD_WHITE);
        cardBackground.setCornerRadius(15 * metrics.density);
        cardBackground.setStroke((int)(1 * metrics.density), Color.parseColor("#dddddd"));
        card.setBackground(cardBackground);
        card.setElevation(3f);
        
        int padding = (int)(20 * metrics.density);
        card.setPadding(padding, padding, padding, padding);
        
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = (int)(20 * metrics.density);
        card.setLayoutParams(cardParams);
        
        // Device Name
        TextView nameText = new TextView(this);
        nameText.setText(deviceName);
        nameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);
        nameText.setTextColor(Color.parseColor("#333333"));
        
        // Description
        TextView descText = new TextView(this);
        descText.setText(description);
        descText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        descText.setTextColor(Color.parseColor("#666666"));
        
        // Connection Status
        TextView statusText = new TextView(this);
        String status = isDemoMode ? "Simulated" : "Disconnected";
        int statusColor = isDemoMode ? Color.parseColor("#FF9800") : Color.parseColor("#f44336");
        statusText.setText("Status: " + status);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        statusText.setTextColor(statusColor);
        statusText.setTypeface(null, android.graphics.Typeface.BOLD);
        
        // Connection Type Selection (Serial/Network)
        TextView connTypeLabel = new TextView(this);
        connTypeLabel.setText("Connection Type:");
        connTypeLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        connTypeLabel.setTextColor(Color.parseColor("#333333"));
        
        LinearLayout connTypeLayout = new LinearLayout(this);
        connTypeLayout.setOrientation(LinearLayout.HORIZONTAL);
        connTypeLayout.setGravity(Gravity.CENTER);
        
        Button serialBtn = createConnectionTypeButton("Serial", true);
        Button networkBtn = createConnectionTypeButton("Network", false);
        
        connTypeLayout.addView(serialBtn);
        connTypeLayout.addView(networkBtn);
        
        // Connection Settings (placeholder for now)
        TextView settingsText = new TextView(this);
        settingsText.setText("COM Port: Auto-detect");
        settingsText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        settingsText.setTextColor(Color.parseColor("#666666"));
        
        // Connect Button
        Button connectBtn = createPrimaryButton(isDemoMode ? "Demo Mode" : "Connect", screenWidthDp);
        connectBtn.setEnabled(!isDemoMode);
        connectBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Device connection: " + status, Toast.LENGTH_SHORT).show();
        });
        
        // Add margins between elements
        LinearLayout.LayoutParams elementMargin = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        elementMargin.topMargin = (int)(8 * metrics.density);
        
        descText.setLayoutParams(elementMargin);
        statusText.setLayoutParams(elementMargin);
        connTypeLabel.setLayoutParams(elementMargin);
        connTypeLayout.setLayoutParams(elementMargin);
        settingsText.setLayoutParams(elementMargin);
        connectBtn.setLayoutParams(elementMargin);
        
        card.addView(nameText);
        card.addView(descText);
        card.addView(statusText);
        card.addView(connTypeLabel);
        card.addView(connTypeLayout);
        card.addView(settingsText);
        card.addView(connectBtn);
        
        return card;
    }

    private Button createConnectionTypeButton(String text, boolean isSelected) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        
        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setColor(isSelected ? PRIMARY_BLUE : Color.parseColor("#e0e0e0"));
        buttonBackground.setCornerRadius(8 * metrics.density);
        button.setBackground(buttonBackground);
        
        button.setTextColor(isSelected ? Color.WHITE : Color.parseColor("#333333"));
        
        int padding = (int)(10 * metrics.density);
        button.setPadding(padding, padding, padding, padding);
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        btnParams.rightMargin = (int)(5 * metrics.density);
        button.setLayoutParams(btnParams);
        
        return button;
    }

    private String getDeviceContinueButtonText() {
        if (eventType.equals("Throws")) {
            return "Continue to Calibration";
        } else {
            return "Start Measurements";
        }
    }

    private void navigateFromDeviceSetup() {
        if (eventType.equals("Throws")) {
            currentScreen = "CALIBRATION_SELECT_CIRCLE";
        } else {
            currentScreen = "MEASUREMENT";
        }
        setupUI();
    }
    
    private LinearLayout createDemoToggle(float screenWidthDp, float screenHeightDp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        
        LinearLayout demoContainer = new LinearLayout(this);
        demoContainer.setOrientation(LinearLayout.HORIZONTAL);
        demoContainer.setGravity(Gravity.CENTER);
        demoContainer.setBackgroundColor(CARD_WHITE);
        
        // Create rounded background with border
        GradientDrawable toggleBackground = new GradientDrawable();
        toggleBackground.setColor(isDemoMode ? Color.parseColor("#f1f8e9") : CARD_WHITE);
        toggleBackground.setCornerRadius(20 * metrics.density);
        toggleBackground.setStroke((int)(3 * metrics.density), 
            isDemoMode ? SUCCESS_GREEN : Color.parseColor("#dddddd"));
        demoContainer.setBackground(toggleBackground);
        demoContainer.setElevation(4f);
        
        int padding = (int)(35 * metrics.density);
        int minWidth = (int)(400 * metrics.density);
        demoContainer.setPadding(padding, padding, padding, padding);
        demoContainer.setMinimumWidth(minWidth);
        
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.bottomMargin = (int)(40 * metrics.density);
        containerParams.gravity = Gravity.CENTER_HORIZONTAL;
        demoContainer.setLayoutParams(containerParams);
        
        // Icon
        TextView icon = new TextView(this);
        icon.setText("⚙️");
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconParams.rightMargin = (int)(25 * metrics.density);
        icon.setLayoutParams(iconParams);
        
        // Text container
        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        
        TextView title = new TextView(this);
        title.setText(isDemoMode ? "Demo Mode Active" : "Real Mode Active");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.parseColor("#333333"));
        
        TextView subtitle = new TextView(this);
        subtitle.setText(isDemoMode ? "Using simulated device data" : "Using real device connections");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        subtitle.setTextColor(Color.parseColor("#666666"));
        
        textContainer.addView(title);
        textContainer.addView(subtitle);
        
        demoContainer.addView(icon);
        demoContainer.addView(textContainer);
        
        demoContainer.setOnClickListener(v -> toggleDemoMode());
        
        return demoContainer;
    }
    
    private Button createPrimaryButton(String text, float screenWidthDp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(18, screenWidthDp * 0.021f));
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        
        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setColor(PRIMARY_BLUE);
        buttonBackground.setCornerRadius(12 * metrics.density);
        button.setBackground(buttonBackground);
        button.setElevation(3f);
        
        int paddingH = (int)(Math.max(30, screenWidthDp * 0.035) * metrics.density);
        int paddingV = (int)(15 * metrics.density);
        int minWidth = (int)(Math.max(120, screenWidthDp * 0.14) * metrics.density);
        
        button.setPadding(paddingH, paddingV, paddingH, paddingV);
        button.setMinWidth(minWidth);
        
        return button;
    }
    
    private void setupCircleSelectionScreen(float screenWidthDp, float screenHeightDp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        mainContent.removeAllViews();
        
        // TEST: Add visible test content to verify layout is working
        TextView testText = new TextView(this);
        testText.setText("CIRCLE SELECTION TEST - Layout Working!");
        testText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        testText.setTextColor(Color.WHITE);
        testText.setBackgroundColor(Color.parseColor("#ff6600"));
        testText.setPadding(30, 30, 30, 30);
        testText.setGravity(Gravity.CENTER);
        mainContent.addView(testText);
        
        // Title
        TextView title = new TextView(this);
        title.setText("Select Circle Type");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(24, screenWidthDp * 0.028f));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.parseColor("#333333"));
        title.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = (int)(Math.max(25, screenHeightDp * 0.03) * metrics.density);
        title.setLayoutParams(titleParams);
        mainContent.addView(title);
        
        // Circle type grid
        LinearLayout circleGrid = new LinearLayout(this);
        circleGrid.setOrientation(LinearLayout.VERTICAL);
        circleGrid.setGravity(Gravity.CENTER);
        
        // First row
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER);
        
        LinearLayout shotCard = createCircleCard("SHOT", "1.065m radius", "±5mm tolerance");
        LinearLayout discusCard = createCircleCard("DISCUS", "1.25m radius", "±5mm tolerance");
        
        row1.addView(shotCard);
        row1.addView(discusCard);
        
        // Second row
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        
        LinearLayout hammerCard = createCircleCard("HAMMER", "1.065m radius", "±5mm tolerance");
        LinearLayout javelinCard = createCircleCard("JAVELIN_ARC", "4.0m radius", "±5mm tolerance");
        
        row2.addView(hammerCard);
        row2.addView(javelinCard);
        
        circleGrid.addView(row1);
        circleGrid.addView(row2);
        mainContent.addView(circleGrid);
        
        // Continue button
        Button continueBtn = createPrimaryButton("Continue to Measurement", screenWidthDp);
        continueBtn.setOnClickListener(v -> {
            currentScreen = "CALIBRATION_SET_CENTRE";
            setupUI();
        });
        
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        continueParams.topMargin = (int)(40 * metrics.density);
        continueParams.gravity = Gravity.CENTER_HORIZONTAL;
        continueBtn.setLayoutParams(continueParams);
        mainContent.addView(continueBtn);
    }
    
    private LinearLayout createCircleCard(String circleType, String radius, String tolerance) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        
        boolean isSelected = circleType.equals(currentCircleType);
        
        // Card styling
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(isSelected ? PRIMARY_BLUE : Color.parseColor("#f8f9fa"));
        cardBackground.setCornerRadius(20 * metrics.density);
        cardBackground.setStroke((int)(4 * metrics.density), 
            isSelected ? PRIMARY_BLUE : Color.parseColor("#dee2e6"));
        card.setBackground(cardBackground);
        card.setElevation(isSelected ? 8f : 4f);
        
        int padding = (int)(25 * metrics.density);
        int margin = (int)(10 * metrics.density);
        int minWidth = (int)(170 * metrics.density);
        
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(minWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(margin, margin, margin, margin);
        card.setLayoutParams(cardParams);
        card.setPadding(padding, padding, padding, padding);
        
        // Title
        TextView titleText = new TextView(this);
        titleText.setText(circleType.replace("_", " "));
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        titleText.setTextColor(isSelected ? Color.WHITE : Color.parseColor("#333333"));
        titleText.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = (int)(8 * metrics.density);
        titleText.setLayoutParams(titleParams);
        
        // Radius
        TextView radiusText = new TextView(this);
        radiusText.setText(radius);
        radiusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        radiusText.setTypeface(null, android.graphics.Typeface.BOLD);
        radiusText.setTextColor(isSelected ? Color.parseColor("#e3f2fd") : Color.parseColor("#666666"));
        radiusText.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams radiusParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        radiusParams.bottomMargin = (int)(5 * metrics.density);
        radiusText.setLayoutParams(radiusParams);
        
        // Tolerance
        TextView toleranceText = new TextView(this);
        toleranceText.setText(tolerance);
        toleranceText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        toleranceText.setTextColor(isSelected ? Color.parseColor("#bbdefb") : Color.parseColor("#999999"));
        toleranceText.setGravity(Gravity.CENTER);
        
        card.addView(titleText);
        card.addView(radiusText);
        card.addView(toleranceText);
        
        card.setOnClickListener(v -> selectCircleType(circleType));
        
        return card;
    }
    
    private void setupMeasurementScreen(float screenWidthDp, float screenHeightDp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        mainContent.removeAllViews();
        
        // Title
        TextView title = new TextView(this);
        title.setText("Measurement - " + currentCircleType.replace("_", " "));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(24, screenWidthDp * 0.028f));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.parseColor("#333333"));
        title.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = (int)(Math.max(25, screenHeightDp * 0.03) * metrics.density);
        title.setLayoutParams(titleParams);
        mainContent.addView(title);
        
        // Measurement buttons
        String[] operations = {
            "Set Circle Centre", "Verify Circle Edge", "Measure Throw",
            "Measure Wind", "Get Statistics", "Export CSV"
        };
        
        for (String operation : operations) {
            Button btn = createMeasurementButton(operation, screenWidthDp);
            btn.setOnClickListener(v -> performOperation(operation));
            mainContent.addView(btn);
        }
        
        // Status display
        TextView statusLabel = new TextView(this);
        statusLabel.setText("Status:");
        statusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        statusLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        statusLabel.setTextColor(Color.parseColor("#333333"));
        
        LinearLayout.LayoutParams statusLabelParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusLabelParams.topMargin = (int)(30 * metrics.density);
        statusLabelParams.bottomMargin = (int)(10 * metrics.density);
        statusLabel.setLayoutParams(statusLabelParams);
        mainContent.addView(statusLabel);
        
        statusText = new TextView(this);
        statusText.setText("Ready for measurements");
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        statusText.setBackgroundColor(Color.LTGRAY);
        statusText.setPadding((int)(10 * metrics.density), (int)(10 * metrics.density), 
                            (int)(10 * metrics.density), (int)(10 * metrics.density));
        statusText.setTextColor(Color.parseColor("#333333"));
        
        GradientDrawable statusBackground = new GradientDrawable();
        statusBackground.setColor(Color.LTGRAY);
        statusBackground.setCornerRadius(8 * metrics.density);
        statusText.setBackground(statusBackground);
        mainContent.addView(statusText);
        
        // Coordinates display
        TextView coordLabel = new TextView(this);
        coordLabel.setText("Throw Coordinates:");
        coordLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        coordLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        coordLabel.setTextColor(Color.parseColor("#333333"));
        
        LinearLayout.LayoutParams coordLabelParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        coordLabelParams.topMargin = (int)(20 * metrics.density);
        coordLabelParams.bottomMargin = (int)(10 * metrics.density);
        coordLabel.setLayoutParams(coordLabelParams);
        mainContent.addView(coordLabel);
        
        coordinatesText = new TextView(this);
        coordinatesText.setText("No coordinates yet");
        coordinatesText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        coordinatesText.setBackgroundColor(Color.LTGRAY);
        coordinatesText.setPadding((int)(10 * metrics.density), (int)(10 * metrics.density), 
                                 (int)(10 * metrics.density), (int)(10 * metrics.density));
        coordinatesText.setTextColor(Color.parseColor("#333333"));
        coordinatesText.setMinLines(4);
        
        GradientDrawable coordBackground = new GradientDrawable();
        coordBackground.setColor(Color.LTGRAY);
        coordBackground.setCornerRadius(8 * metrics.density);
        coordinatesText.setBackground(coordBackground);
        mainContent.addView(coordinatesText);
    }
    
    private Button createMeasurementButton(String text, float screenWidthDp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        
        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setColor(PRIMARY_BLUE);
        buttonBackground.setCornerRadius(15 * metrics.density);
        button.setBackground(buttonBackground);
        button.setElevation(4f);
        
        int paddingH = (int)(40 * metrics.density);
        int paddingV = (int)(18 * metrics.density);
        int minWidth = (int)(280 * metrics.density);
        
        button.setPadding(paddingH, paddingV, paddingH, paddingV);
        button.setMinWidth(minWidth);
        
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = (int)(8 * metrics.density);
        buttonParams.bottomMargin = (int)(8 * metrics.density);
        buttonParams.gravity = Gravity.CENTER_HORIZONTAL;
        button.setLayoutParams(buttonParams);
        
        return button;
    }

    private void addSpace(LinearLayout parent, int heightDp) {
        Space space = new Space(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            (int) (heightDp * getResources().getDisplayMetrics().density));
        space.setLayoutParams(params);
        parent.addView(space);
    }

    private void toggleDemoMode() {
        isDemoMode = !isDemoMode;
        updateDemoStatus();
        
        try {
            mobile.Mobile.setDemoMode(isDemoMode);
            if (statusText != null) {
                updateStatus("Demo mode " + (isDemoMode ? "enabled" : "disabled"));
            }
        } catch (Exception e) {
            if (statusText != null) {
                updateStatus("Demo mode " + (isDemoMode ? "enabled" : "disabled") + " (local only)");
            }
        }
        
        // Refresh the UI to update demo toggle appearance
        setupUI();
    }

    private void selectCircleType(String circleType) {
        currentCircleType = circleType;
        
        try {
            // Get UKA radius for selected circle
            double radius = getDemoUKARadius(currentCircleType);
            if (statusText != null) {
                updateStatus("Selected " + currentCircleType + " (radius: " + radius + "m)");
            }
        } catch (Exception e) {
            if (statusText != null) {
                updateStatus("Selected " + currentCircleType);
            }
        }
        
        // Refresh the UI to update card selection appearance
        setupUI();
    }

    private void performOperation(String operation) {
        updateStatus("Performing: " + operation + "...");
        
        try {
            String result = "";
            switch (operation) {
                case "Set Circle Centre":
                    result = "Centre set for EDM (DEMO)";
                    break;
                case "Verify Circle Edge":
                    result = "Circle edge verified within tolerance (DEMO)";
                    break;
                case "Measure Throw":
                    result = mobile.Mobile.measureThrow("edm");
                    updateCoordinates();
                    break;
                case "Measure Wind":
                    result = "Wind: 2.3m/s at 180° (DEMO)";
                    break;
                case "Get Statistics":
                    result = mobile.Mobile.getThrowStatistics(currentCircleType);
                    break;
                case "Export CSV":
                    result = "CSV exported successfully (DEMO)";
                    if (result != null && !result.isEmpty()) {
                        Toast.makeText(this, "CSV data ready", Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
            
            if (result != null && !result.isEmpty()) {
                updateStatus("Success: " + result);
            } else {
                updateStatus(operation + " completed");
            }
            
        } catch (Exception e) {
            updateStatus("Error: " + e.getMessage());
        }
    }

    private void updateStatus(String message) {
        if (statusText != null) {
            statusText.setText(message);
        }
    }

    private void updateCoordinates() {
        try {
            String coords = mobile.Mobile.getThrowCoordinates();
            if (coords != null && !coords.isEmpty()) {
                coordinatesText.setText(coords);
            }
        } catch (Exception e) {
            coordinatesText.setText("Error getting coordinates: " + e.getMessage());
        }
    }

    private View createBottomNavigation(float screenWidthDp, float screenHeightDp) {
        // Show bottom navigation for device setup, calibration and measurement screens  
        if (!currentScreen.equals("DEVICE_SETUP") && !currentScreen.startsWith("CALIBRATION") && !currentScreen.equals("MEASUREMENT")) {
            return null;
        }
        
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        
        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setBackgroundColor(Color.parseColor("#e3f2fd"));
        bottomNav.setGravity(Gravity.CENTER_VERTICAL);
        
        int paddingV = (int)(Math.max(15, screenHeightDp * 0.018) * metrics.density);
        int paddingH = (int)(Math.max(25, screenWidthDp * 0.03) * metrics.density);
        bottomNav.setPadding(paddingH, paddingV, paddingH, paddingV);
        
        // Add top border
        GradientDrawable navBackground = new GradientDrawable();
        navBackground.setColor(Color.parseColor("#e3f2fd"));
        navBackground.setStroke((int)(1 * metrics.density), Color.parseColor("#dddddd"));
        bottomNav.setBackground(navBackground);
        
        // Back button
        Button backBtn = createNavButton("← Back", false);
        backBtn.setOnClickListener(v -> navigateBack());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        backBtn.setLayoutParams(backParams);
        
        // Spacer to push buttons apart
        View spacer = new View(this);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        spacer.setLayoutParams(spacerParams);
        
        // Next/Continue button (context sensitive)
        Button nextBtn = createNavButton(getNextButtonText(), true);
        nextBtn.setOnClickListener(v -> navigateForward());
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nextBtn.setLayoutParams(nextParams);
        
        bottomNav.addView(backBtn);
        bottomNav.addView(spacer);
        bottomNav.addView(nextBtn);
        
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bottomNav.setLayoutParams(navParams);
        
        return bottomNav;
    }
    
    private Button createNavButton(String text, boolean isPrimary) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        
        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setColor(isPrimary ? PRIMARY_BLUE : Color.parseColor("#757575"));
        buttonBackground.setCornerRadius(12 * metrics.density);
        button.setBackground(buttonBackground);
        
        int paddingH = (int)(30 * metrics.density);
        int paddingV = (int)(15 * metrics.density);
        button.setPadding(paddingH, paddingV, paddingH, paddingV);
        
        return button;
    }
    
    private String getNextButtonText() {
        switch (currentScreen) {
            case "DEVICE_SETUP": 
                return eventType.equals("Throws") ? "Next →" : "Start →";
            case "CALIBRATION_SET_CENTRE": return "Next →";
            case "CALIBRATION_VERIFY_EDGE": return "Next →";
            case "CALIBRATION_EDGE_RESULTS": return "Start →";
            case "MEASUREMENT": return "New Event";
            default: return "Continue →";
        }
    }
    
    private void navigateBack() {
        switch (currentScreen) {
            case "DEVICE_SETUP":
                currentScreen = "SELECT_EVENT_TYPE";
                break;
            case "CALIBRATION_SELECT_CIRCLE":
                currentScreen = "DEVICE_SETUP";
                break;
            case "CALIBRATION_SET_CENTRE":
                currentScreen = "CALIBRATION_SELECT_CIRCLE";
                break;
            case "CALIBRATION_VERIFY_EDGE":
                currentScreen = "CALIBRATION_SET_CENTRE";
                break;
            case "CALIBRATION_EDGE_RESULTS":
                currentScreen = "CALIBRATION_VERIFY_EDGE";
                break;
            case "MEASUREMENT":
                if (eventType.equals("Throws")) {
                    currentScreen = "CALIBRATION_EDGE_RESULTS"; // Back to calibration results
                } else {
                    currentScreen = "DEVICE_SETUP"; // Back to device setup for jumps
                }
                break;
            default:
                currentScreen = "SELECT_EVENT_TYPE";
                break;
        }
        setupUI();
    }
    
    private void navigateForward() {
        switch (currentScreen) {
            case "DEVICE_SETUP":
                navigateFromDeviceSetup();
                break;
            case "CALIBRATION_SET_CENTRE":
                currentScreen = "CALIBRATION_VERIFY_EDGE";
                break;
            case "CALIBRATION_VERIFY_EDGE":
                currentScreen = "CALIBRATION_EDGE_RESULTS";
                break;
            case "CALIBRATION_EDGE_RESULTS":
                currentScreen = "MEASUREMENT";
                break;
            case "MEASUREMENT":
                // Reset to event selection for new event
                currentScreen = "SELECT_EVENT_TYPE";
                eventType = "Throws"; // Reset to default
                break;
        }
        setupUI();
    }

    private void setupCalibrationCentreScreen(float screenWidthDp, float screenHeightDp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        mainContent.removeAllViews();
        
        // Title
        TextView title = new TextView(this);
        title.setText("Step 2: Set Circle Centre");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(24, screenWidthDp * 0.028f));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.parseColor("#333333"));
        title.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = (int)(Math.max(25, screenHeightDp * 0.03) * metrics.density);
        title.setLayoutParams(titleParams);
        mainContent.addView(title);
        
        // Instructions
        TextView instructions = new TextView(this);
        instructions.setText("Place the EDM device at the exact centre of the circle and press 'Set Centre'.");
        instructions.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        instructions.setTextColor(Color.parseColor("#666666"));
        instructions.setGravity(Gravity.CENTER);
        instructions.setLineSpacing(0, 1.3f);
        
        LinearLayout.LayoutParams instrParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        instrParams.bottomMargin = (int)(40 * metrics.density);
        instructions.setLayoutParams(instrParams);
        mainContent.addView(instructions);
        
        // Set Centre button
        Button setCentreBtn = createPrimaryButton("Set Centre", screenWidthDp);
        setCentreBtn.setOnClickListener(v -> {
            updateStatus("Centre set for EDM (DEMO)");
            Toast.makeText(this, "Centre set successfully!", Toast.LENGTH_SHORT).show();
        });
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.gravity = Gravity.CENTER_HORIZONTAL;
        setCentreBtn.setLayoutParams(btnParams);
        mainContent.addView(setCentreBtn);
    }

    private void setupCalibrationEdgeScreen(float screenWidthDp, float screenHeightDp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        mainContent.removeAllViews();
        
        // Title
        TextView title = new TextView(this);
        title.setText("Step 3: Verify Circle Edge");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(24, screenWidthDp * 0.028f));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.parseColor("#333333"));
        title.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = (int)(Math.max(25, screenHeightDp * 0.03) * metrics.density);
        title.setLayoutParams(titleParams);
        mainContent.addView(title);
        
        // Instructions
        TextView instructions = new TextView(this);
        double radius = getDemoUKARadius(currentCircleType);
        instructions.setText(String.format("Move the EDM device to the edge of the %s circle (%.3fm radius) and press 'Verify Edge'.", 
            currentCircleType.replace("_", " "), radius));
        instructions.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        instructions.setTextColor(Color.parseColor("#666666"));
        instructions.setGravity(Gravity.CENTER);
        instructions.setLineSpacing(0, 1.3f);
        
        LinearLayout.LayoutParams instrParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        instrParams.bottomMargin = (int)(40 * metrics.density);
        instructions.setLayoutParams(instrParams);
        mainContent.addView(instructions);
        
        // Verify Edge button
        Button verifyBtn = createPrimaryButton("Verify Edge", screenWidthDp);
        verifyBtn.setOnClickListener(v -> {
            updateStatus("Circle edge verified within tolerance (DEMO)");
            Toast.makeText(this, "Edge verified successfully!", Toast.LENGTH_SHORT).show();
        });
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.gravity = Gravity.CENTER_HORIZONTAL;
        verifyBtn.setLayoutParams(btnParams);
        mainContent.addView(verifyBtn);
    }

    private void setupEdgeResultsScreen(float screenWidthDp, float screenHeightDp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        mainContent.removeAllViews();
        
        // Title
        TextView title = new TextView(this);
        title.setText("Calibration Results");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(24, screenWidthDp * 0.028f));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.parseColor("#333333"));
        title.setGravity(Gravity.CENTER);
        
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = (int)(Math.max(25, screenHeightDp * 0.03) * metrics.density);
        title.setLayoutParams(titleParams);
        mainContent.addView(title);
        
        // Results card
        LinearLayout resultsCard = new LinearLayout(this);
        resultsCard.setOrientation(LinearLayout.VERTICAL);
        resultsCard.setGravity(Gravity.CENTER);
        resultsCard.setBackgroundColor(Color.parseColor("#e8f5e8"));
        
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(Color.parseColor("#e8f5e8"));
        cardBackground.setCornerRadius(15 * metrics.density);
        cardBackground.setStroke((int)(2 * metrics.density), SUCCESS_GREEN);
        resultsCard.setBackground(cardBackground);
        
        int padding = (int)(30 * metrics.density);
        resultsCard.setPadding(padding, padding, padding, padding);
        
        TextView resultTitle = new TextView(this);
        resultTitle.setText("✅ Calibration Successful");
        resultTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        resultTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        resultTitle.setTextColor(SUCCESS_GREEN);
        resultTitle.setGravity(Gravity.CENTER);
        
        TextView resultDetails = new TextView(this);
        double radius = getDemoUKARadius(currentCircleType);
        resultDetails.setText(String.format("Circle type: %s\\nRadius: %.3fm\\nTolerance: ±5mm\\nStatus: Within tolerance", 
            currentCircleType.replace("_", " "), radius));
        resultDetails.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        resultDetails.setTextColor(Color.parseColor("#333333"));
        resultDetails.setGravity(Gravity.CENTER);
        resultDetails.setLineSpacing(0, 1.4f);
        
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = (int)(15 * metrics.density);
        resultDetails.setLayoutParams(detailParams);
        
        resultsCard.addView(resultTitle);
        resultsCard.addView(resultDetails);
        
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = (int)(40 * metrics.density);
        resultsCard.setLayoutParams(cardParams);
        mainContent.addView(resultsCard);
    }
    
    // USB Device Management Methods
    
    private void initializeUSB() {
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        permissionIntent = PendingIntent.getBroadcast(
            this, 0, new Intent(ACTION_USB_PERMISSION), 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Create USB broadcast receiver
        usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                
                if (ACTION_USB_PERMISSION.equals(action)) {
                    synchronized (this) {
                        UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            if (device != null) {
                                connectedDevice = device;
                                onUSBDevicePermissionGranted(device);
                            }
                        } else {
                            updateStatus("USB permission denied for device");
                        }
                    }
                } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    onUSBDeviceAttached(device);
                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    onUSBDeviceDetached(device);
                }
            }
        };
        
        // Register USB broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);
        
        // Request runtime permissions
        requestRuntimePermissions();
    }
    
    private void requestRuntimePermissions() {
        // Check and request location permissions (required for some USB devices)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                }, PERMISSION_REQUEST_CODE);
        }
    }
    
    private void checkConnectedUSBDevices() {
        if (usbManager == null) return;
        
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList.isEmpty()) {
            updateStatus("No USB devices detected");
            return;
        }
        
        // Check each connected device
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        boolean foundSerial = false;
        
        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            if (isUSBSerialDevice(device)) {
                foundSerial = true;
                updateStatus(String.format("Found USB serial device: %s (VID: %04X, PID: %04X)", 
                    device.getProductName() != null ? device.getProductName() : "Unknown",
                    device.getVendorId(), device.getProductId()));
                
                // Request permission for this device
                if (!usbManager.hasPermission(device)) {
                    usbManager.requestPermission(device, permissionIntent);
                } else {
                    connectedDevice = device;
                    onUSBDevicePermissionGranted(device);
                }
                break; // Use first serial device found
            }
        }
        
        if (!foundSerial) {
            updateStatus("No compatible USB serial devices found");
        }
    }
    
    private boolean isUSBSerialDevice(UsbDevice device) {
        int vendorId = device.getVendorId();
        int productId = device.getProductId();
        
        // Check for FTDI devices (0x0403 = 1027 decimal)
        if (vendorId == 1027) {
            return productId == 24577 || productId == 24596 || productId == 24582; // FT232R, FT232H, FT2232D
        }
        
        // Check for Prolific PL2303 (0x067b = 1659 decimal)
        if (vendorId == 1659 && productId == 8963) {
            return true;
        }
        
        // Check for Silicon Labs CP2102 (0x10c4 = 4292 decimal)
        if (vendorId == 4292 && productId == 60000) {
            return true;
        }
        
        return false;
    }
    
    private void onUSBDeviceAttached(UsbDevice device) {
        if (isUSBSerialDevice(device)) {
            updateStatus("USB serial device attached: " + 
                (device.getProductName() != null ? device.getProductName() : "Unknown"));
            
            if (!usbManager.hasPermission(device)) {
                usbManager.requestPermission(device, permissionIntent);
            } else {
                connectedDevice = device;
                onUSBDevicePermissionGranted(device);
            }
        }
    }
    
    private void onUSBDeviceDetached(UsbDevice device) {
        if (device.equals(connectedDevice)) {
            connectedDevice = null;
            updateStatus("USB device disconnected - switching to demo mode");
            isDemoMode = true;
        }
    }
    
    private void onUSBDevicePermissionGranted(UsbDevice device) {
        updateStatus("USB device connected: " + 
            (device.getProductName() != null ? device.getProductName() : "Serial Device"));
        
        // Switch out of demo mode when real device is connected
        isDemoMode = false;
        
        // TODO: Initialize serial communication with the device
        // This would typically involve opening a serial connection
        // and setting up communication protocols
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                updateStatus("Runtime permissions granted");
            } else {
                updateStatus("Some permissions denied - functionality may be limited");
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usbReceiver != null) {
            unregisterReceiver(usbReceiver);
        }
    }
    
    // USB Serial Communication Helper Methods
    
    public boolean isUSBDeviceConnected() {
        return connectedDevice != null && usbManager.hasPermission(connectedDevice);
    }
    
    public String getConnectedDeviceInfo() {
        if (connectedDevice == null) {
            return "No device connected";
        }
        
        return String.format("Device: %s\nVendor ID: %04X\nProduct ID: %04X\nSerial: %s",
            connectedDevice.getProductName() != null ? connectedDevice.getProductName() : "Unknown",
            connectedDevice.getVendorId(),
            connectedDevice.getProductId(),
            connectedDevice.getSerialNumber() != null ? connectedDevice.getSerialNumber() : "Unknown");
    }
}
