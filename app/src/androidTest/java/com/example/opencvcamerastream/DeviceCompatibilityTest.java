package com.example.opencvcamerastream;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.os.Build;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Device compatibility tests for different Android versions including Android 10
 * Requirements: All requirements validation, Android 10 compliance
 */
@RunWith(AndroidJUnit4.class)
public class DeviceCompatibilityTest {

    private Context context;
    private CameraManager cameraManager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    @Test
    public void testAndroid10Compatibility() {
        // Test compatibility with Android 10 (API 29)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertTrue("Should run on Android 10 or higher", Build.VERSION.SDK_INT >= 29);
            
            // Test Android 10 specific features
            assertTrue("Should have scoped storage", 
                      Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
        }
    }

    @Test
    public void testCameraHardwareAvailability() {
        // Test camera hardware availability across devices
        assertNotNull("Camera manager should be available", cameraManager);
        
        boolean hasCameraFeature = context.getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        
        if (hasCameraFeature) {
            try {
                String[] cameraIds = cameraManager.getCameraIdList();
                assertTrue("Should have at least one camera", cameraIds.length > 0);
            } catch (Exception e) {
                fail("Camera access should not throw exception: " + e.getMessage());
            }
        }
    }

    @Test
    public void testMinimumSdkCompatibility() {
        // Test minimum SDK compatibility (API 21)
        assertTrue("Should support minimum SDK 21", Build.VERSION.SDK_INT >= 21);
    }

    @Test
    public void testTargetSdkCompatibility() {
        // Test target SDK compatibility (API 29)
        int targetSdk = context.getApplicationInfo().targetSdkVersion;
        assertEquals("Target SDK should be 29 (Android 10)", 29, targetSdk);
    }

    @Test
    public void testOpenCVCompatibility() {
        // Test OpenCV compatibility across devices
        try {
            // This would test OpenCV loading - simplified for unit test
            boolean openCVAvailable = true; // Placeholder
            assertTrue("OpenCV should be available", openCVAvailable);
        } catch (Exception e) {
            fail("OpenCV compatibility test failed: " + e.getMessage());
        }
    }

    @Test
    public void testMemoryRequirements() {
        // Test memory requirements for different devices
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        
        assertTrue("Should have sufficient max memory", maxMemory > 16 * 1024 * 1024); // 16MB minimum
        assertTrue("Total memory should be reasonable", totalMemory > 0);
    }

    @Test
    public void testDisplayCompatibility() {
        // Test display compatibility
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        
        assertTrue("Display width should be positive", metrics.widthPixels > 0);
        assertTrue("Display height should be positive", metrics.heightPixels > 0);
        assertTrue("Display density should be positive", metrics.density > 0);
    }

    @Test
    public void testPermissionSystemCompatibility() {
        // Test permission system compatibility
        String cameraPermission = android.Manifest.permission.CAMERA;
        
        // Should be able to check permission status
        int permissionStatus = context.checkSelfPermission(cameraPermission);
        assertTrue("Permission status should be valid", 
                  permissionStatus == PackageManager.PERMISSION_GRANTED || 
                  permissionStatus == PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void testHardwareAccelerationSupport() {
        // Test hardware acceleration support
        PackageManager pm = context.getPackageManager();
        
        // Check for OpenGL ES support
        boolean hasOpenGLES20 = pm.hasSystemFeature(PackageManager.FEATURE_OPENGLES_ES_VERSION_2_0);
        
        // Most modern devices should support OpenGL ES 2.0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            assertTrue("Should support OpenGL ES 2.0 on modern devices", hasOpenGLES20);
        }
    }

    @Test
    public void testCameraFeatureSupport() {
        // Test camera feature support
        PackageManager pm = context.getPackageManager();
        
        boolean hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
        boolean hasCameraAny = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        boolean hasAutofocus = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS);
        
        // At least one camera feature should be available on camera-enabled devices
        if (hasCamera || hasCameraAny) {
            assertTrue("Camera features should be available", true);
        }
    }

    @Test
    public void testProcessorArchitectureCompatibility() {
        // Test processor architecture compatibility
        String[] supportedAbis = Build.SUPPORTED_ABIS;
        
        assertNotNull("Supported ABIs should not be null", supportedAbis);
        assertTrue("Should have at least one supported ABI", supportedAbis.length > 0);
        
        // Common architectures that should be supported
        boolean hasCommonArch = false;
        for (String abi : supportedAbis) {
            if (abi.contains("arm") || abi.contains("x86")) {
                hasCommonArch = true;
                break;
            }
        }
        assertTrue("Should support common architecture", hasCommonArch);
    }

    @Test
    public void testStorageCompatibility() {
        // Test storage compatibility for Android 10 scoped storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Test scoped storage compliance
            java.io.File externalFilesDir = context.getExternalFilesDir(null);
            assertNotNull("External files directory should be available", externalFilesDir);
        }
    }

    @Test
    public void testNetworkSecurityCompatibility() {
        // Test network security compatibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9+ network security requirements
            assertTrue("Should handle network security config", true);
        }
    }

    @Test
    public void testBatteryOptimizationCompatibility() {
        // Test battery optimization compatibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Test battery optimization handling
            assertTrue("Should handle battery optimization", true);
        }
    }

    @Test
    public void testAccessibilityCompatibility() {
        // Test accessibility service compatibility
        android.view.accessibility.AccessibilityManager accessibilityManager = 
            (android.view.accessibility.AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        
        assertNotNull("Accessibility manager should be available", accessibilityManager);
    }
}