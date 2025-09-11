package com.example.opencvcamerastream.camera;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CameraManager
 * Requirements: 1.2, 1.4, 4.2, 5.4
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Test on Android 10
public class CameraManagerTest {

    @Mock
    private Context mockContext;
    
    @Mock
    private CameraManager mockSystemCameraManager;
    
    @Mock
    private CameraDevice mockCameraDevice;
    
    @Mock
    private Handler mockHandler;
    
    private com.example.opencvcamerastream.camera.CameraManager cameraManager;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockContext.getSystemService(Context.CAMERA_SERVICE)).thenReturn(mockSystemCameraManager);
        cameraManager = new com.example.opencvcamerastream.camera.CameraManager(mockContext);
    }

    @Test
    public void testCameraInitialization() throws CameraAccessException {
        // Test camera initialization (Requirement 1.2)
        String[] cameraIds = {"0"};
        when(mockSystemCameraManager.getCameraIdList()).thenReturn(cameraIds);
        
        boolean result = cameraManager.initializeCamera();
        
        assertTrue("Camera should initialize successfully", result);
        verify(mockSystemCameraManager).getCameraIdList();
    }

    @Test
    public void testCameraInitializationFailure() throws CameraAccessException {
        // Test camera initialization failure handling (Requirement 4.2)
        when(mockSystemCameraManager.getCameraIdList()).thenThrow(new CameraAccessException(CameraAccessException.CAMERA_ERROR));
        
        boolean result = cameraManager.initializeCamera();
        
        assertFalse("Camera initialization should fail gracefully", result);
    }

    @Test
    public void testStartPreview() {
        // Test camera preview start (Requirement 1.2)
        cameraManager.setCameraDevice(mockCameraDevice);
        
        boolean result = cameraManager.startPreview();
        
        assertTrue("Preview should start successfully", result);
    }

    @Test
    public void testStopPreview() {
        // Test camera preview stop and resource cleanup (Requirement 5.4)
        cameraManager.setCameraDevice(mockCameraDevice);
        
        cameraManager.stopPreview();
        
        // Verify cleanup is called
        verify(mockCameraDevice, timeout(1000)).close();
    }

    @Test
    public void testCameraDeviceStateCallback() {
        // Test camera device state callbacks (Requirement 4.2)
        CameraDevice.StateCallback callback = cameraManager.getCameraStateCallback();
        
        assertNotNull("State callback should not be null", callback);
        
        // Test opened callback
        callback.onOpened(mockCameraDevice);
        assertEquals("Camera device should be set", mockCameraDevice, cameraManager.getCameraDevice());
        
        // Test error callback
        callback.onError(mockCameraDevice, CameraDevice.StateCallback.ERROR_CAMERA_DEVICE);
        // Should handle error gracefully without crashing
    }

    @Test
    public void testOrientationHandling() {
        // Test orientation change handling (Requirement 1.4)
        int initialOrientation = cameraManager.getDisplayRotation();
        
        cameraManager.updateDisplayRotation(90);
        
        assertNotEquals("Display rotation should be updated", initialOrientation, cameraManager.getDisplayRotation());
    }

    @Test
    public void testCameraResourceCleanup() {
        // Test proper resource cleanup (Requirement 5.4)
        cameraManager.setCameraDevice(mockCameraDevice);
        
        cameraManager.cleanup();
        
        verify(mockCameraDevice).close();
        assertNull("Camera device should be null after cleanup", cameraManager.getCameraDevice());
    }

    @Test
    public void testCameraPermissionCheck() {
        // Test camera permission validation
        when(mockContext.checkSelfPermission(android.Manifest.permission.CAMERA))
            .thenReturn(android.content.pm.PackageManager.PERMISSION_GRANTED);
        
        boolean hasPermission = cameraManager.hasCameraPermission();
        
        assertTrue("Should have camera permission", hasPermission);
    }

    @Test
    public void testCameraUnavailableHandling() throws CameraAccessException {
        // Test camera unavailable scenario (Requirement 4.2)
        when(mockSystemCameraManager.getCameraIdList()).thenReturn(new String[0]);
        
        boolean result = cameraManager.initializeCamera();
        
        assertFalse("Should handle no available cameras", result);
    }
}