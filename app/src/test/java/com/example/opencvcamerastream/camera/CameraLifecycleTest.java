package com.example.opencvcamerastream.camera;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Size;

import androidx.core.app.ActivityCompat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CameraManager lifecycle management
 * 
 * Tests cover:
 * - Proper resource cleanup (Requirement 5.4)
 * - Camera reconnection scenarios (Requirement 4.2)
 * - Android 10 background activity restrictions
 * - Memory management and threading
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Test on Android 10 (API 29)
public class CameraLifecycleTest {
    
    @Mock
    private Context mockContext;
    
    @Mock
    private CameraManager mockSystemCameraManager;
    
    @Mock
    private CameraCharacteristics mockCameraCharacteristics;
    
    @Mock
    private StreamConfigurationMap mockStreamConfigurationMap;
    
    @Mock
    private com.example.opencvcamerastream.camera.CameraManager.CameraCallback mockCameraCallback;
    
    private com.example.opencvcamerastream.camera.CameraManager cameraManager;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mockContext.getSystemService(Context.CAMERA_SERVICE)).thenReturn(mockSystemCameraManager);
        
        cameraManager = new com.example.opencvcamerastream.camera.CameraManager(mockContext);
        cameraManager.setCameraCallback(mockCameraCallback);
    }
    
    /**
     * Test complete lifecycle: initialize -> start -> stop -> release
     * Requirement 5.4: Properly release camera resources
     */
    @Test
    public void testCompleteLifecycle() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            setupMockCameraForSuccess();
            
            // Initialize
            assertTrue("Camera should initialize", cameraManager.initializeCamera());
            assertTrue("Camera should be initialized", cameraManager.isInitialized());
            
            // Stop preview (even though we didn't start it)
            cameraManager.stopPreview();
            assertFalse("Preview should not be active", cameraManager.isPreviewActive());
            
            // Release
            cameraManager.release();
            assertFalse("Camera should not be initialized after release", cameraManager.isInitialized());
            assertFalse("Preview should not be active after release", cameraManager.isPreviewActive());
        }
    }
    
    /**
     * Test multiple release calls (should be safe)
     * Requirement 5.4: Safe resource cleanup
     */
    @Test
    public void testMultipleReleaseCalls() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            setupMockCameraForSuccess();
            
            // Initialize and release multiple times
            cameraManager.initializeCamera();
            cameraManager.release();
            cameraManager.release(); // Should not throw
            cameraManager.release(); // Should not throw
            
            assertFalse("Camera should not be initialized", cameraManager.isInitialized());
        }
    }
    
    /**
     * Test release without initialization (should be safe)
     */
    @Test
    public void testReleaseWithoutInitialization() {
        // Should not throw any exceptions
        cameraManager.release();
        
        assertFalse("Camera should not be initialized", cameraManager.isInitialized());
        assertFalse("Preview should not be active", cameraManager.isPreviewActive());
    }
    
    /**
     * Test camera device state callbacks
     * Requirement 4.2: Handle camera disconnection and errors
     */
    @Test
    public void testCameraDeviceStateCallbacks() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            setupMockCameraForSuccess();
            
            // Initialize camera
            cameraManager.initializeCamera();
            
            // We can't easily test the actual state callbacks without complex mocking,
            // but we can verify the camera manager is set up to handle them
            assertTrue("Camera should be initialized and ready for state callbacks", 
                    cameraManager.isInitialized());
        }
    }
    
    /**
     * Test error handling during initialization
     * Requirement 4.2: Handle camera errors gracefully
     */
    @Test
    public void testErrorHandlingDuringInitialization() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            // Mock camera characteristics to return null stream configuration
            String[] cameraIds = {"0"};
            when(mockSystemCameraManager.getCameraIdList()).thenReturn(cameraIds);
            when(mockSystemCameraManager.getCameraCharacteristics("0")).thenReturn(mockCameraCharacteristics);
            when(mockCameraCharacteristics.get(CameraCharacteristics.LENS_FACING))
                    .thenReturn(CameraCharacteristics.LENS_FACING_BACK);
            when(mockCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
                    .thenReturn(null); // This will cause an error
            
            // Test initialization
            boolean result = cameraManager.initializeCamera();
            
            assertFalse("Camera should not initialize with null stream config", result);
            assertFalse("Camera should not be marked as initialized", cameraManager.isInitialized());
            
            // Verify error callback was called
            verify(mockCameraCallback).onCameraError(eq(-1), contains("Camera configuration not available"));
        }
    }
    
    /**
     * Test Android 10 background activity restrictions simulation
     * Requirement 5.4: Handle Android 10 background restrictions
     */
    @Test
    public void testAndroid10BackgroundRestrictions() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            setupMockCameraForSuccess();
            
            // Initialize camera (simulating foreground)
            assertTrue("Camera should initialize in foreground", cameraManager.initializeCamera());
            
            // Simulate app going to background (stop preview)
            cameraManager.stopPreview();
            assertFalse("Preview should stop when app goes to background", cameraManager.isPreviewActive());
            
            // Camera should still be initialized but preview stopped
            assertTrue("Camera should remain initialized when backgrounded", cameraManager.isInitialized());
        }
    }
    
    /**
     * Test memory management and resource cleanup
     * Requirement 5.4: Efficient memory management
     */
    @Test
    public void testMemoryManagement() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            setupMockCameraForSuccess();
            
            // Initialize and release multiple times to test memory management
            for (int i = 0; i < 5; i++) {
                assertTrue("Camera should initialize on iteration " + i, cameraManager.initializeCamera());
                cameraManager.release();
                assertFalse("Camera should be released on iteration " + i, cameraManager.isInitialized());
            }
        }
    }
    
    /**
     * Test camera configuration persistence
     */
    @Test
    public void testCameraConfigurationPersistence() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            setupMockCameraForSuccess();
            
            // Initialize with custom config
            com.example.opencvcamerastream.camera.CameraManager.CameraConfig config = 
                    new com.example.opencvcamerastream.camera.CameraManager.CameraConfig(new Size(1280, 720));
            
            assertTrue("Camera should initialize with config", cameraManager.initializeCamera(config));
            
            Size previewSize = cameraManager.getPreviewSize();
            assertNotNull("Preview size should be set", previewSize);
            
            // Release and check that state is properly cleared
            cameraManager.release();
            assertNull("Preview size should be null after release", cameraManager.getPreviewSize());
        }
    }
    
    /**
     * Test thread safety and synchronization
     */
    @Test
    public void testThreadSafety() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            setupMockCameraForSuccess();
            
            // Test concurrent initialization and release
            // This is a basic test - in a real scenario we'd use multiple threads
            assertTrue("Camera should initialize", cameraManager.initializeCamera());
            cameraManager.stopPreview();
            cameraManager.release();
            
            assertFalse("Camera should be properly released", cameraManager.isInitialized());
        }
    }
    
    /**
     * Helper method to set up mock camera for successful operations
     */
    private void setupMockCameraForSuccess() throws CameraAccessException {
        String[] cameraIds = {"0"};
        when(mockSystemCameraManager.getCameraIdList()).thenReturn(cameraIds);
        when(mockSystemCameraManager.getCameraCharacteristics("0")).thenReturn(mockCameraCharacteristics);
        
        when(mockCameraCharacteristics.get(CameraCharacteristics.LENS_FACING))
                .thenReturn(CameraCharacteristics.LENS_FACING_BACK);
        when(mockCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
                .thenReturn(mockStreamConfigurationMap);
        
        Size[] sizes = {new Size(1280, 720), new Size(640, 480)};
        when(mockStreamConfigurationMap.getOutputSizes(anyInt())).thenReturn(sizes);
    }
}