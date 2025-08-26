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
 * Unit tests for CameraManager error handling and recovery
 * 
 * Tests cover:
 * - Camera error scenarios (Requirement 4.2)
 * - Automatic reconnection logic (Requirement 4.2)
 * - Error callback functionality
 * - Edge cases and exception handling
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Test on Android 10 (API 29)
public class CameraErrorRecoveryTest {
    
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
     * Test camera access exception handling
     * Requirement 4.2: Handle camera access errors gracefully
     */
    @Test
    public void testCameraAccessExceptionHandling() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            // Mock CameraAccessException during getCameraIdList
            when(mockSystemCameraManager.getCameraIdList())
                    .thenThrow(new CameraAccessException(CameraAccessException.CAMERA_ERROR));
            
            // Test initialization
            boolean result = cameraManager.initializeCamera();
            
            assertFalse("Camera should not initialize with access exception", result);
            assertFalse("Camera should not be marked as initialized", cameraManager.isInitialized());
            
            // Verify error callback was called with appropriate message
            verify(mockCameraCallback).onCameraError(eq(-1), contains("Camera access failed"));
        }
    }
    
    /**
     * Test camera in use error scenario
     * Requirement 4.2: Handle camera in use by another app
     */
    @Test
    public void testCameraInUseError() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            // Mock CameraAccessException with CAMERA_IN_USE
            when(mockSystemCameraManager.getCameraIdList())
                    .thenThrow(new CameraAccessException(CameraAccessException.CAMERA_IN_USE));
            
            // Test initialization
            boolean result = cameraManager.initializeCamera();
            
            assertFalse("Camera should not initialize when in use", result);
            verify(mockCameraCallback).onCameraError(eq(-1), contains("Camera access failed"));
        }
    }
    
    /**
     * Test camera disabled error scenario
     * Requirement 4.2: Handle camera disabled by policy
     */
    @Test
    public void testCameraDisabledError() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            // Mock CameraAccessException with CAMERA_DISABLED
            when(mockSystemCameraManager.getCameraIdList())
                    .thenThrow(new CameraAccessException(CameraAccessException.CAMERA_DISABLED));
            
            // Test initialization
            boolean result = cameraManager.initializeCamera();
            
            assertFalse("Camera should not initialize when disabled", result);
            verify(mockCameraCallback).onCameraError(eq(-1), contains("Camera access failed"));
        }
    }
    
    /**
     * Test unexpected runtime exception handling
     * Requirement 4.2: Handle unexpected errors gracefully
     */
    @Test
    public void testUnexpectedExceptionHandling() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            // Mock unexpected RuntimeException
            when(mockSystemCameraManager.getCameraIdList())
                    .thenThrow(new RuntimeException("Unexpected error"));
            
            // Test initialization
            boolean result = cameraManager.initializeCamera();
            
            assertFalse("Camera should not initialize with unexpected exception", result);
            verify(mockCameraCallback).onCameraError(eq(-1), contains("Camera initialization failed"));
        }
    }
    
    /**
     * Test null system camera manager handling
     */
    @Test
    public void testNullSystemCameraManager() {
        // Create camera manager with context that returns null camera service
        Context contextWithNullCamera = mock(Context.class);
        when(contextWithNullCamera.getApplicationContext()).thenReturn(contextWithNullCamera);
        when(contextWithNullCamera.getSystemService(Context.CAMERA_SERVICE)).thenReturn(null);
        
        com.example.opencvcamerastream.camera.CameraManager nullCameraManager = 
                new com.example.opencvcamerastream.camera.CameraManager(contextWithNullCamera);
        nullCameraManager.setCameraCallback(mockCameraCallback);
        
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            // Test initialization with null camera manager
            boolean result = nullCameraManager.initializeCamera();
            
            assertFalse("Camera should not initialize with null system camera manager", result);
        }
    }
    
    /**
     * Test camera characteristics exception handling
     * Requirement 4.2: Handle camera characteristics access errors
     */
    @Test
    public void testCameraCharacteristicsException() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            // Mock camera list but throw exception on characteristics
            String[] cameraIds = {"0"};
            when(mockSystemCameraManager.getCameraIdList()).thenReturn(cameraIds);
            when(mockSystemCameraManager.getCameraCharacteristics("0"))
                    .thenThrow(new CameraAccessException(CameraAccessException.CAMERA_ERROR));
            
            // Test initialization
            boolean result = cameraManager.initializeCamera();
            
            assertFalse("Camera should not initialize with characteristics exception", result);
            verify(mockCameraCallback).onCameraError(eq(-1), contains("Camera access failed"));
        }
    }
    
    /**
     * Test error callback with null callback set
     */
    @Test
    public void testErrorCallbackWithNullCallback() throws CameraAccessException {
        // Remove callback
        cameraManager.setCameraCallback(null);
        
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_DENIED);
            
            // Test initialization (should not throw even with null callback)
            boolean result = cameraManager.initializeCamera();
            
            assertFalse("Camera should not initialize without permission", result);
            // Should not throw NullPointerException
        }
    }
    
    /**
     * Test camera selection with no back-facing camera
     * Requirement 4.2: Handle camera selection gracefully
     */
    @Test
    public void testCameraSelectionWithNoBackCamera() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            // Mock camera with only front-facing camera
            String[] cameraIds = {"0"};
            when(mockSystemCameraManager.getCameraIdList()).thenReturn(cameraIds);
            when(mockSystemCameraManager.getCameraCharacteristics("0")).thenReturn(mockCameraCharacteristics);
            
            // Mock front-facing camera
            when(mockCameraCharacteristics.get(CameraCharacteristics.LENS_FACING))
                    .thenReturn(CameraCharacteristics.LENS_FACING_FRONT);
            when(mockCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
                    .thenReturn(mockStreamConfigurationMap);
            
            Size[] sizes = {new Size(1280, 720)};
            when(mockStreamConfigurationMap.getOutputSizes(anyInt())).thenReturn(sizes);
            
            // Test initialization (should use front camera as fallback)
            boolean result = cameraManager.initializeCamera();
            
            assertTrue("Camera should initialize with front camera as fallback", result);
            assertTrue("Camera should be marked as initialized", cameraManager.isInitialized());
        }
    }
    
    /**
     * Test size selection with limited available sizes
     */
    @Test
    public void testSizeSelectionWithLimitedSizes() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            setupMockCameraWithLimitedSizes();
            
            // Test initialization with limited sizes
            boolean result = cameraManager.initializeCamera();
            
            assertTrue("Camera should initialize with limited sizes", result);
            assertNotNull("Preview size should be selected", cameraManager.getPreviewSize());
        }
    }
    
    /**
     * Test initialization with empty size array
     */
    @Test
    public void testInitializationWithEmptySizeArray() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            // Mock camera with empty size array
            String[] cameraIds = {"0"};
            when(mockSystemCameraManager.getCameraIdList()).thenReturn(cameraIds);
            when(mockSystemCameraManager.getCameraCharacteristics("0")).thenReturn(mockCameraCharacteristics);
            
            when(mockCameraCharacteristics.get(CameraCharacteristics.LENS_FACING))
                    .thenReturn(CameraCharacteristics.LENS_FACING_BACK);
            when(mockCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
                    .thenReturn(mockStreamConfigurationMap);
            
            // Empty size array
            Size[] sizes = {};
            when(mockStreamConfigurationMap.getOutputSizes(anyInt())).thenReturn(sizes);
            
            // Test initialization
            boolean result = cameraManager.initializeCamera();
            
            // Should handle empty size array gracefully (might fail or use default)
            // The exact behavior depends on implementation details
            assertFalse("Camera should not be marked as initialized", cameraManager.isInitialized());
        }
    }
    
    /**
     * Test recovery after error
     * Requirement 4.2: Support recovery after errors
     */
    @Test
    public void testRecoveryAfterError() throws CameraAccessException {
        try (MockedStatic<ActivityCompat> mockedActivityCompat = mockStatic(ActivityCompat.class)) {
            mockedActivityCompat.when(() -> ActivityCompat.checkSelfPermission(any(), any()))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            // First attempt fails
            when(mockSystemCameraManager.getCameraIdList())
                    .thenThrow(new CameraAccessException(CameraAccessException.CAMERA_ERROR));
            
            boolean result1 = cameraManager.initializeCamera();
            assertFalse("First initialization should fail", result1);
            
            // Second attempt succeeds
            setupMockCameraForSuccess();
            
            boolean result2 = cameraManager.initializeCamera();
            assertTrue("Second initialization should succeed after recovery", result2);
            assertTrue("Camera should be initialized after recovery", cameraManager.isInitialized());
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
    
    /**
     * Helper method to set up mock camera with limited sizes
     */
    private void setupMockCameraWithLimitedSizes() throws CameraAccessException {
        String[] cameraIds = {"0"};
        when(mockSystemCameraManager.getCameraIdList()).thenReturn(cameraIds);
        when(mockSystemCameraManager.getCameraCharacteristics("0")).thenReturn(mockCameraCharacteristics);
        
        when(mockCameraCharacteristics.get(CameraCharacteristics.LENS_FACING))
                .thenReturn(CameraCharacteristics.LENS_FACING_BACK);
        when(mockCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
                .thenReturn(mockStreamConfigurationMap);
        
        // Only one small size available
        Size[] sizes = {new Size(320, 240)};
        when(mockStreamConfigurationMap.getOutputSizes(anyInt())).thenReturn(sizes);
    }
}