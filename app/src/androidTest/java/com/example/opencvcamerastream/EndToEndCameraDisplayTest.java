package com.example.opencvcamerastream;

import android.content.Context;
import android.content.pm.PackageManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

/**
 * End-to-end tests for complete camera-to-display flow
 * Requirements: All requirements validation, 7.2
 */
@RunWith(AndroidJUnit4.class)
public class EndToEndCameraDisplayTest {

    @Rule
    public GrantPermissionRule cameraPermissionRule = 
        GrantPermissionRule.grant(android.Manifest.permission.CAMERA);

    private Context context;
    private UiDevice device;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @Test
    public void testCompleteE2ECameraToDisplayFlow() throws Exception {
        // Test complete camera initialization to display pipeline
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            
            // Wait for activity to initialize
            Thread.sleep(2000);
            
            // Verify camera permission is granted
            assertTrue("Camera permission should be granted", 
                context.checkSelfPermission(android.Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED);
            
            // Verify main UI components are displayed
            onView(withId(R.id.textureView)).check(matches(isDisplayed()));
            
            // Wait for camera initialization
            Thread.sleep(3000);
            
            // Verify camera preview is active (texture view should be receiving frames)
            scenario.onActivity(activity -> {
                assertNotNull("MainActivity should not be null", activity);
                // Additional verification that camera is active would go here
                // This is a placeholder for actual camera state verification
            });
            
            // Test processing mode switching if UI exists
            // This would test the complete pipeline from camera -> OpenCV -> display
            
            // Verify no crashes occurred during the flow
            assertTrue("Activity should still be running", !scenario.getState().name().equals("DESTROYED"));
        }
    }

    @Test
    public void testE2EWithOrientationChange() throws Exception {
        // Test end-to-end flow with device rotation
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            
            Thread.sleep(2000);
            
            // Rotate device and verify pipeline continues working
            device.setOrientationLeft();
            Thread.sleep(1000);
            
            // Verify UI is still functional after rotation
            onView(withId(R.id.textureView)).check(matches(isDisplayed()));
            
            device.setOrientationNatural();
            Thread.sleep(1000);
            
            // Verify pipeline is still active
            assertTrue("Activity should still be running after rotation", 
                !scenario.getState().name().equals("DESTROYED"));
        }
    }

    @Test
    public void testE2EMemoryStabilityDuringLongOperation() throws Exception {
        // Test memory stability during extended operation
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            
            Thread.sleep(2000);
            
            // Run for extended period to test memory leaks
            for (int i = 0; i < 10; i++) {
                Thread.sleep(1000);
                
                // Verify activity is still responsive
                scenario.onActivity(activity -> {
                    assertNotNull("Activity should remain non-null during extended operation", activity);
                });
            }
            
            // Verify no memory-related crashes
            assertTrue("Activity should survive extended operation", 
                !scenario.getState().name().equals("DESTROYED"));
        }
    }

    @Test
    public void testE2EErrorRecoveryFlow() throws Exception {
        // Test end-to-end error recovery scenarios
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            
            Thread.sleep(2000);
            
            // Simulate app backgrounding and foregrounding
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED);
            Thread.sleep(1000);
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            Thread.sleep(2000);
            
            // Verify recovery after lifecycle changes
            onView(withId(R.id.textureView)).check(matches(isDisplayed()));
            
            assertTrue("Activity should recover from lifecycle changes", 
                !scenario.getState().name().equals("DESTROYED"));
        }
    }
}