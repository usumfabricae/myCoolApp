package com.example.opencvcamerastream.compliance;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Android 10 testing utilities
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.Q)
public class Android10TestUtilsTest {
    
    @Mock
    private Context mockContext;
    
    @Mock
    private Activity mockActivity;
    
    @Mock
    private PackageManager mockPackageManager;
    
    @Mock
    private ApplicationInfo mockApplicationInfo;
    
    @Mock
    private PackageInfo mockPackageInfo;
    
    @Mock
    private Resources mockResources;
    
    @Mock
    private File mockCacheDir;
    
    @Mock
    private File mockFilesDir;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mockContext.getPackageManager()).thenReturn(mockPackageManager);
        when(mockContext.getPackageName()).thenReturn("com.example.opencvcamerastream");
        when(mockContext.getApplicationInfo()).thenReturn(mockApplicationInfo);
        when(mockContext.getResources()).thenReturn(mockResources);
        when(mockContext.getCacheDir()).thenReturn(mockCacheDir);
        when(mockContext.getFilesDir()).thenReturn(mockFilesDir);
        
        when(mockActivity.getApplicationContext()).thenReturn(mockContext);
        when(mockActivity.getPackageManager()).thenReturn(mockPackageManager);
        when(mockActivity.getPackageName()).thenReturn("com.example.opencvcamerastream");
        when(mockActivity.getResources()).thenReturn(mockResources);
        
        mockPackageInfo.requestedPermissions = new String[]{
                android.Manifest.permission.CAMERA
        };
        
        try {
            when(mockPackageManager.getPackageInfo(anyString(), anyInt())).thenReturn(mockPackageInfo);
        } catch (PackageManager.NameNotFoundException e) {
            // Won't happen in test
        }
    }
    
    @Test
    public void testScopedStorageCompliance_Passing() {
        // Mock the flags field to simulate no legacy storage request
        mockApplicationInfo.flags = 0; // No FLAG_LEGACY_EXTERNAL_STORAGE
        when(mockCacheDir.exists()).thenReturn(true);
        when(mockFilesDir.exists()).thenReturn(true);
        
        Android10TestUtils.TestResult result = Android10TestUtils.testScopedStorageCompliance(mockContext);
        
        assertTrue("Scoped storage test should pass", result.passed);
        assertEquals("Should be requirement 6.1", "6.1", result.requirement);
        assertTrue("Message should indicate validation", result.message.contains("validated"));
    }
    
    @Test
    public void testScopedStorageCompliance_LegacyStorageFailure() {
        // Mock the flags field to simulate legacy storage request
        mockApplicationInfo.flags = 0x20000000; // FLAG_LEGACY_EXTERNAL_STORAGE
        when(mockCacheDir.exists()).thenReturn(true);
        when(mockFilesDir.exists()).thenReturn(true);
        
        Android10TestUtils.TestResult result = Android10TestUtils.testScopedStorageCompliance(mockContext);
        
        assertFalse("Scoped storage test should fail with legacy storage", result.passed);
        assertEquals("Should be requirement 6.1", "6.1", result.requirement);
        assertTrue("Message should indicate legacy storage issue", 
                result.message.contains("legacy external storage"));
    }
    
    @Test
    public void testCameraPrivacyControls_Passing() {
        mockPackageInfo.requestedPermissions = new String[]{
                android.Manifest.permission.CAMERA
        };
        
        when(mockResources.getIdentifier("android_10_camera_privacy_message", "string", 
                "com.example.opencvcamerastream")).thenReturn(123);
        when(mockActivity.getString(123)).thenReturn("Privacy message");
        
        Android10TestUtils.TestResult result = Android10TestUtils.testCameraPrivacyControls(mockActivity);
        
        assertTrue("Camera privacy test should pass", result.passed);
        assertEquals("Should be requirement 6.2", "6.2", result.requirement);
    }
    
    @Test
    public void testCameraPrivacyControls_MissingPermission() {
        mockPackageInfo.requestedPermissions = new String[]{};
        
        Android10TestUtils.TestResult result = Android10TestUtils.testCameraPrivacyControls(mockActivity);
        
        assertFalse("Camera privacy test should fail without permission", result.passed);
        assertEquals("Should be requirement 6.2", "6.2", result.requirement);
        assertTrue("Message should indicate permission issue", 
                result.message.contains("not declared"));
    }
    
    @Test
    public void testBackgroundActivityRestrictions_Passing() {
        Android10TestUtils.TestResult result = Android10TestUtils.testBackgroundActivityRestrictions(mockContext);
        
        assertTrue("Background restrictions test should pass", result.passed);
        assertEquals("Should be requirement 6.3", "6.3", result.requirement);
        assertTrue("Message should indicate validation", result.message.contains("validated"));
    }
    
    @Test
    public void testEnhancedPrivacyControls_Passing() {
        Android10TestUtils.TestResult result = Android10TestUtils.testEnhancedPrivacyControls(mockContext);
        
        assertTrue("Enhanced privacy test should pass", result.passed);
        assertEquals("Should be requirement 6.4", "6.4", result.requirement);
        assertTrue("Message should indicate validation", result.message.contains("validated"));
    }
    
    @Test
    public void testComprehensiveTests_AllPassing() {
        setupPassingConfiguration();
        
        List<Android10TestUtils.TestResult> results = Android10TestUtils.runComprehensiveTests(mockActivity);
        
        assertFalse("Should have test results", results.isEmpty());
        
        long passedCount = results.stream().filter(r -> r.passed).count();
        assertEquals("All tests should pass", results.size(), passedCount);
    }
    
    @Test
    public void testGenerateComplianceReport() {
        setupPassingConfiguration();
        
        String report = Android10TestUtils.generateComplianceReport(mockActivity);
        
        assertNotNull("Report should not be null", report);
        assertTrue("Report should contain title", report.contains("Android 10 Compliance Report"));
        assertTrue("Report should contain status", report.contains("Status:"));
    }
    
    @Test
    public void testIsAndroid10OrHigher() {
        boolean result = Android10TestUtils.isAndroid10OrHigher();
        assertTrue("Should be Android 10 or higher in test", result);
    }
    
    @Test
    public void testGetAndroid10PrivacyMessage() {
        when(mockResources.getIdentifier("android_10_camera_privacy_message", "string", 
                "com.example.opencvcamerastream")).thenReturn(123);
        when(mockContext.getString(123)).thenReturn("Test privacy message");
        
        String message = Android10TestUtils.getAndroid10PrivacyMessage(mockContext);
        
        assertEquals("Should return privacy message", "Test privacy message", message);
    }
    
    @Test
    @Config(sdk = Build.VERSION_CODES.P)
    public void testAndroid9Compatibility() {
        Android10TestUtils.TestResult result = Android10TestUtils.testScopedStorageCompliance(mockContext);
        
        assertTrue("Should pass on Android 9", result.passed);
        assertTrue("Message should indicate not required", result.message.contains("not required"));
    }
    
    private void setupPassingConfiguration() {
        // Mock the flags field to simulate no legacy storage request
        mockApplicationInfo.flags = 0; // No FLAG_LEGACY_EXTERNAL_STORAGE
        when(mockCacheDir.exists()).thenReturn(true);
        when(mockFilesDir.exists()).thenReturn(true);
        
        mockPackageInfo.requestedPermissions = new String[]{
                android.Manifest.permission.CAMERA
        };
        
        when(mockResources.getIdentifier("android_10_camera_privacy_message", "string", 
                "com.example.opencvcamerastream")).thenReturn(123);
        when(mockActivity.getString(123)).thenReturn("Privacy message");
    }
}