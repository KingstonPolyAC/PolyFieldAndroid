package com.polyfieldandroid;

import android.content.res.Configuration;
import android.util.DisplayMetrics;
import com.facebook.react.ReactActivity;
import com.facebook.react.ReactActivityDelegate;
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint;
import com.facebook.react.defaults.DefaultReactActivityDelegate;

public class MainActivity extends ReactActivity {

  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  @Override
  protected String getMainComponentName() {
    return "PolyFieldAndroid";
  }

  @Override
  protected void onCreate(android.os.Bundle savedInstanceState) {
    adjustDisplayDensity();
    super.onCreate(savedInstanceState);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    adjustDisplayDensity();
  }

  private void adjustDisplayDensity() {
    DisplayMetrics metrics = getResources().getDisplayMetrics();
    float screenWidthDp = metrics.widthPixels / metrics.density;
    float screenHeightDp = metrics.heightPixels / metrics.density;
    Configuration config = getResources().getConfiguration();
    
    // Calculate scale to fix layout overlaps while maintaining proportions
    float scale = 1.0f;
    
    // Check orientation and adjust scaling to fix specific overlap issues
    if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
      // Portrait: reduce scale slightly to give more room for throws/horizontal jumps buttons
      if (screenHeightDp < 700) {
        scale = 0.95f; // Slightly smaller to prevent button overlap
      } else {
        scale = 1.0f;
      }
    } else {
      // Landscape: reduce scale to prevent circle selection buttons overlapping title
      if (screenHeightDp < 500) {
        scale = 0.9f; // Smaller scale for compact landscape
      } else {
        scale = 0.95f;
      }
    }
    
    // Apply scaling with consideration for screen density
    float finalScale = scale;
    if (screenWidthDp >= 600) {
      // Tablet: can handle slightly larger elements
      finalScale *= 1.1f;
    }
    
    metrics.density *= finalScale;
    metrics.scaledDensity *= finalScale;
    getResources().updateConfiguration(config, metrics);
  }

  /**
   * Returns the instance of the {@link ReactActivityDelegate}. Here we use a util class {@link
   * DefaultReactActivityDelegate} which allows you to easily enable Fabric and Concurrent React
   * (aka React 18) with two boolean flags.
   */
  @Override
  protected ReactActivityDelegate createReactActivityDelegate() {
    return new DefaultReactActivityDelegate(
        this,
        getMainComponentName(),
        // If you opted-in for the New Architecture, we enable the Fabric Renderer.
        DefaultNewArchitectureEntryPoint.getFabricEnabled());
  }
}
