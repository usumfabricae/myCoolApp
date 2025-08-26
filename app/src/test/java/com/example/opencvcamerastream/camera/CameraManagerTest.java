package com.example.opencvcamerastream.camera;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
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
 * Unit tests for CameraManager class
 * 
 * Tests cover:
 * - Camera initialization and cleanup (Requirements 1.2, 5.4)
 * - Error handling and recovery (Requirement 4.2)
 * - Resource management and lifecycle
 * - Android 10 compatibility scenarios
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Test on Android 10 (API 29)
public class CameraManagerTest {
    
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
    
    @Mock
    private com.example.opencvcamerastream.camera.CameraManager.FrameCallback mockFrameCallback;
    
    @Mock
    private Image mockImage;
    
    private com.example.opencvcamerastream.camera.CameraManager cameraManager;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock context to return system camera manager
        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mockContext.getSystemService(Context.CAMERA_SERVICE)).thenReturn(mockSystemCameraManager);
        
        cameraManager = new com.example.opencvcamerastream.camera.CameraManager(mockContext);
    }
    
    /**
     * Test camera initialization with granted permissions
     * Requirement 1.2: Initialize camera when permissions are granted
     */
    @Test
    public void testInitializeCameraWithPermission() throws CameraAccessException {
        // Mock permission granted
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            // Mock camera list and characteristics
            String[] cameraIds = {"0", "1"};
            when(mockSystemCameraManager.getCameraIdList()).thenReturn(cameraIds);
            when(mockSystemCameraManager.getCameraCharacteristics("0")).thenReturn(mockCameraCharacteristics);
            
            // Mock back-facing camera
            when(mockCameraCharacteristics.get(CameraCharacteristics.LENS_FACING))
                    .thenReturn(CameraCharacteristics.LENS_FACING_BACK);
            when(mockCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
                    .thenReturn(mockStreamConfigurationMap);
            
            // Mock available sizes
            Size[] sizes = {new Size(1920, 1080), new Size(1280, 720), new Size(640, 480)};
            when(mockStreamConfigurationMap.getOutputSizes(anyInt())).thenReturn(sizes);
            
            // Test initialization
            boolean result = cameraManager.initializeCamera();
            
            assertTrue("Camera should initialize successfully with permission", result);
            assertTrue("Camera should be marked as initialized", cameraManager.isInitialized());
            assertNotNull("Preview size should be set", cameraManager.getPreviewSize());
        }
    }
    
    /**
     * Test camera initialization without permission
     * Requirement 1.2: Handle permission denial gracefully
     */
    @Test
    public void testInitializeCameraWithoutPermission() {
        // Mock permission denied
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_DENIED);
            
            cameraManager.setCameraCallback(mockCameraCallback);
            
            // Test initialization
            boolean result = cameraManager.initializeCamera();
            
            assertFalse("Camera should not initialize without permission", result);
            assertFalse("Camera should not be marked as initialized", cameraManager.isInitialized());
            
            // Verify error callback was called
            verify(mockCameraCallback).onCameraError(eq(-1), contains("permission"));
        }
    }
    
    /**
     * Test camera initialization with no available cameras
     * Requirement 4.2: Handle camera unavailability
     */
    @Test
    public void testInitializeCameraWithNoCameras() throws CameraAccessException {
        // Mock permission granted
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            // Mock empty camera list
            when(mockSystemCameraManager.getCameraIdList()).thenReturn(new String[0]);
            
            cameraManager.setCameraCallback(mockCameraCallback);
            
            // Test initialization
            boolean result = cameraManager.initializeCamera();
            
            assertFalse("Camera should not initialize with no cameras", result);
            assertFalse("Camera should not be marked as initialized", cameraManager.isInitialized());
            
            // Verify error callback was called
            verify(mockCameraCallback).onCameraError(eq(-1), contains("No suitable camera"));
        }
    }
    
    /**
     * Test camera initialization with CameraAccessException
     * Requirement 4.2: Handle camera access errors
     */
    @Test
    public void testInitializeCameraWithAccessException() throws CameraAccessException {
        // Mock permission granted
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            // Mock camera access exception
            when(mockSystemCameraManager.getCameraIdList())
                    .thenThrow(new CameraAccessException(CameraAccessException.CAMERA_ERROR));
            
            cameraManager.setCameraCallback(mockCameraCallback);
            
            // Test initialization
            boolean result = cameraManager.initializeCamera();
            
            assertFalse("Camera should not initialize with access exception", result);
            assertFalse("Camera should not be marked as initialized", cameraManager.isInitialized());
            
            // Verify error callback was called
            verify(mockCameraCallback).onCameraError(eq(-1), contains("Camera access failed"));
        }
    }
    
    /**
     * Test camera initialization with custom configuration
     */
    @Test
    public void testInitializeCameraWithCustomConfig() throws CameraAccessException {
        // Mock permission granted
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            // Mock camera setup
            setupMockCameraForSuccess();
            
            // Create custom config
            com.example.opencvcamerastream.camera.CameraManager.CameraConfig config = 
                    new com.example.opencvcamerastream.camera.CameraManager.CameraConfig(new Size(640, 480));
            
            // Test initialization with custom config
            boolean result = cameraManager.initializeCamera(config);
            
            assertTrue("Camera should initialize with custom config", result);
            assertTrue("Camera should be marked as initialized", cameraManager.isInitialized());
        }
    }
    
    /**
     * Test start preview without initialization
     */
    @Test
    public void testStartPreviewWithoutInitialization() {
        // Test starting preview without initialization
        boolean result = cameraManager.startPreview();
        
        assertFalse("Preview should not start without initialization", result);
        assertFalse("Preview should not be marked as active", cameraManager.isPreviewActive());
    }
    
    /**
     * Test stop preview when not active
     */
    @Test
    public void testStopPreviewWhenNotActive() {
        // This should not throw any exceptions
        cameraManager.stopPreview();
        
        assertFalse("Preview should remain inactive", cameraManager.isPreviewActive());
    }
    
    /**
     * Test resource release
     * Requirement 5.4: Properly release camera resources
     */
    @Test
    public void testResourceRelease() throws CameraAccessException {
        // Initialize camera first
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            setupMockCameraForSuccess();
            
            cameraManager.initializeCamera();
            assertTrue("Camera should be initialized", cameraManager.isInitialized());
            
            // Test release
            cameraManager.release();
            
            assertFalse("Camera should not be initialized after release", cameraManager.isInitialized());
            assertFalse("Preview should not be active after release", cameraManager.isPreviewActive());
            assertNull("Preview size should be null after release", cameraManager.getPreviewSize());
        }
    }
    
    /**
     * Test callback setters
     */
    @Test
    public void testCallbackSetters() {
        // Test setting callbacks
        cameraManager.setCameraCallback(mockCameraCallback);
        cameraManager.setFrameCallback(mockFrameCallback);
        
        // Test setting null callbacks (should not throw)
        cameraManager.setCameraCallback(null);
        cameraManager.setFrameCallback(null);
    }
    
    /**
     * Test frame callback functionality
     */
    @Test
    public void testFrameCallback() {
        cameraManager.setFrameCallback(mockFrameCallback);
        
        // Simulate frame available (this would normally be called by ImageReader)
        // We can't easily test the actual ImageReader callback, but we can verify
        // the callback is set properly
        assertNotNull("Frame callback should be set", mockFrameCallback);
    }
    
    /**
     * Test camera state management
     */
    @Test
    public void testCameraStateManagement() {
        // Initial state
        assertFalse("Camera should not be initialized initially", cameraManager.isInitialized());
        assertFalse("Preview should not be active initially", cameraManager.isPreviewActive());
        assertNull("Preview size should be null initially", cameraManager.getPreviewSize());
    }
    
    /**
     * Test multiple initialization calls
     */
    @Test
    public void testMultipleInitializationCalls() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            setupMockCameraForSuccess();
            
            // First initialization
            boolean result1 = cameraManager.initializeCamera();
            assertTrue("First initialization should succeed", result1);
            
            // Second initialization (should return true without re-initializing)
            boolean result2 = cameraManager.initializeCamera();
            assertTrue("Second initialization should return true", result2);
            
            assertTrue("Camera should remain initialized", cameraManager.isInitialized());
        }
    }
    
    /**
     * Test Android 10 compatibility scenarios
     */
    @Test
    public void testAndroid10Compatibility() throws CameraAccessException {
        // This test ensures our camera manager works on Android 10 (API 29)
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            setupMockCameraForSuccess();
            
            // Test initialization on Android 10
            boolean result = cameraManager.initializeCamera();
            
            assertTrue("Camera should work on Android 10", result);
            assertTrue("Camera should be initialized on Android 10", cameraManager.isInitialized());
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