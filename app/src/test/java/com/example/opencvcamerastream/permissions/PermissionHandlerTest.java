package com.example.opencvcamerastream.permissions;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.opencvcamerastream.R;
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
 * Unit tests for PermissionHandler class
 * 
 * Tests cover:
 * - Basic permission checking
 * - Permission request flows
 * - Android 10 specific scenarios
 * - Callback handling
 * - Error scenarios
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.Q}) // Test with Android 10
public class PermissionHandlerTest {
    
    @Mock
    private Activity mockActivity;
    
    @Mock
    private PermissionHandler.PermissionCallback mockCallback;
    
    private PermissionHandler permissionHandler;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock activity context methods
        when(mockActivity.getPackageName()).thenReturn("com.example.opencvcamerastream");
        when(mockActivity.getString(R.string.app_name)).thenReturn("OpenCV Camera Stream");
        when(mockActivity.getString(R.string.camera_permission_rationale))
                .thenReturn("This app needs camera access to capture and process video frames in real-time.");
        when(mockActivity.getString(R.string.android_10_camera_privacy_message))
                .thenReturn("Android 10 provides enhanced camera privacy controls.");
        
        permissionHandler = new PermissionHandler(mockActivity);
        permissionHandler.setPermissionCallback(mockCallback);
    }
    
    @Test
    public void testIsCameraPermissionGranted_WhenPermissionGranted_ReturnsTrue() {
        try (MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class)) {
            contextCompatMock.when(() -> ContextCompat.checkSelfPermission(
                    mockActivity, Manifest.permission.CAMERA))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            assertTrue("Should return true when camera permission is granted", 
                    permissionHandler.isCameraPermissionGranted());
        }
    }
    
    @Test
    public void testIsCameraPermissionGranted_WhenPermissionDenied_ReturnsFalse() {
        try (MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class)) {
            contextCompatMock.when(() -> ContextCompat.checkSelfPermission(
                    mockActivity, Manifest.permission.CAMERA))
                    .thenReturn(PackageManager.PERMISSION_DENIED);
            
            assertFalse("Should return false when camera permission is denied", 
                    permissionHandler.isCameraPermissionGranted());
        }
    }
    
    @Test
    public void testRequestCameraPermission_WhenAlreadyGranted_CallsOnPermissionGranted() {
        try (MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class)) {
            contextCompatMock.when(() -> ContextCompat.checkSelfPermission(
                    mockActivity, Manifest.permission.CAMERA))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            permissionHandler.requestCameraPermission();
            
            verify(mockCallback).onPermissionGranted();
            verify(mockCallback, never()).onPermissionDenied(anyBoolean());
        }
    }
    
    @Test
    public void testRequestCameraPermission_WhenRationaleNeeded_RequestsDirectly() {
        try (MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
             MockedStatic<ActivityCompat> activityCompatMock = mockStatic(ActivityCompat.class)) {
            
            contextCompatMock.when(() -> ContextCompat.checkSelfPermission(
                    mockActivity, Manifest.permission.CAMERA))
                    .thenReturn(PackageManager.PERMISSION_DENIED);
            
            activityCompatMock.when(() -> ActivityCompat.shouldShowRequestPermissionRationale(
                    mockActivity, Manifest.permission.CAMERA))
                    .thenReturn(true);
            
            permissionHandler.requestCameraPermission();
            
            // Should show rationale dialog (tested separately)
            // Verify no immediate callback is made
            verify(mockCallback, never()).onPermissionGranted();
            verify(mockCallback, never()).onPermissionDenied(anyBoolean());
        }
    }
    
    @Test
    public void testRequestCameraPermission_FirstTimeRequest_RequestsDirectly() {
        try (MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class);
             MockedStatic<ActivityCompat> activityCompatMock = mockStatic(ActivityCompat.class)) {
            
            contextCompatMock.when(() -> ContextCompat.checkSelfPermission(
                    mockActivity, Manifest.permission.CAMERA))
                    .thenReturn(PackageManager.PERMISSION_DENIED);
            
            activityCompatMock.when(() -> ActivityCompat.shouldShowRequestPermissionRationale(
                    mockActivity, Manifest.permission.CAMERA))
                    .thenReturn(false);
            
            permissionHandler.requestCameraPermission();
            
            // Should request permission directly
            activityCompatMock.verify(() -> ActivityCompat.requestPermissions(
                    eq(mockActivity),
                    eq(new String[]{Manifest.permission.CAMERA}),
                    eq(PermissionHandler.CAMERA_PERMISSION_REQUEST_CODE)
            ));
        }
    }
    
    @Test
    public void testHandlePermissionResult_WhenGranted_CallsOnPermissionGranted() {
        String[] permissions = {Manifest.permission.CAMERA};
        int[] grantResults = {PackageManager.PERMISSION_GRANTED};
        
        permissionHandler.handlePermissionResult(
                PermissionHandler.CAMERA_PERMISSION_REQUEST_CODE, 
                permissions, 
                grantResults
        );
        
        verify(mockCallback).onPermissionGranted();
        verify(mockCallback, never()).onPermissionDenied(anyBoolean());
    }
    
    @Test
    public void testHandlePermissionResult_WhenDeniedNotPermanently_CallsOnPermissionDenied() {
        try (MockedStatic<ActivityCompat> activityCompatMock = mockStatic(ActivityCompat.class)) {
            activityCompatMock.when(() -> ActivityCompat.shouldShowRequestPermissionRationale(
                    mockActivity, Manifest.permission.CAMERA))
                    .thenReturn(true); // Not permanently denied
            
            String[] permissions = {Manifest.permission.CAMERA};
            int[] grantResults = {PackageManager.PERMISSION_DENIED};
            
            permissionHandler.handlePermissionResult(
                    PermissionHandler.CAMERA_PERMISSION_REQUEST_CODE, 
                    permissions, 
                    grantResults
            );
            
            verify(mockCallback).onPermissionDenied(false);
        }
    }
    
    @Test
    public void testHandlePermissionResult_WhenPermanentlyDenied_CallsOnPermissionDeniedWithTrue() {
        try (MockedStatic<ActivityCompat> activityCompatMock = mockStatic(ActivityCompat.class)) {
            activityCompatMock.when(() -> ActivityCompat.shouldShowRequestPermissionRationale(
                    mockActivity, Manifest.permission.CAMERA))
                    .thenReturn(false); // Permanently denied
            
            String[] permissions = {Manifest.permission.CAMERA};
            int[] grantResults = {PackageManager.PERMISSION_DENIED};
            
            permissionHandler.handlePermissionResult(
                    PermissionHandler.CAMERA_PERMISSION_REQUEST_CODE, 
                    permissions, 
                    grantResults
            );
            
            verify(mockCallback).onPermissionDenied(true);
        }
    }
    
    @Test
    public void testHandlePermissionResult_WrongRequestCode_DoesNothing() {
        String[] permissions = {Manifest.permission.CAMERA};
        int[] grantResults = {PackageManager.PERMISSION_GRANTED};
        
        permissionHandler.handlePermissionResult(
                9999, // Wrong request code
                permissions, 
                grantResults
        );
        
        verify(mockCallback, never()).onPermissionGranted();
        verify(mockCallback, never()).onPermissionDenied(anyBoolean());
    }
    
    @Test
    public void testHandlePermissionResult_EmptyResults_CallsOnPermissionDenied() {
        String[] permissions = {Manifest.permission.CAMERA};
        int[] grantResults = {}; // Empty results
        
        permissionHandler.handlePermissionResult(
                PermissionHandler.CAMERA_PERMISSION_REQUEST_CODE, 
                permissions, 
                grantResults
        );
        
        verify(mockCallback).onPermissionDenied(anyBoolean());
    }
    
    @Test
    public void testSetPermissionCallback_UpdatesCallback() {
        PermissionHandler.PermissionCallback newCallback = mock(PermissionHandler.PermissionCallback.class);
        
        permissionHandler.setPermissionCallback(newCallback);
        
        // Test that new callback is used
        try (MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class)) {
            contextCompatMock.when(() -> ContextCompat.checkSelfPermission(
                    mockActivity, Manifest.permission.CAMERA))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            permissionHandler.requestCameraPermission();
            
            verify(newCallback).onPermissionGranted();
            verify(mockCallback, never()).onPermissionGranted();
        }
    }
    
    @Test
    public void testNullCallback_DoesNotCrash() {
        permissionHandler.setPermissionCallback(null);
        
        try (MockedStatic<ContextCompat> contextCompatMock = mockStatic(ContextCompat.class)) {
            contextCompatMock.when(() -> ContextCompat.checkSelfPermission(
                    mockActivity, Manifest.permission.CAMERA))
                    .thenReturn(PackageManager.PERMISSION_GRANTED);
            
            // Should not crash with null callback
            permissionHandler.requestCameraPermission();
        }
    }
}