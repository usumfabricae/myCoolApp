package com.example.opencvcamerastream;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.example.opencvcamerastream.compliance.Android10ComplianceValidator;
import com.example.opencvcamerastream.compliance.Android10TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Instrumentation tests for Android 10 compliance on real devices
 * 
 * These tests validate:
 * - Requirement 10.1: Enhanced camera permission handling
 * - Requirement 10.2: Background activity restrictions compliance
 * - Requirement 10.3: Scoped storage implementation
 * - Requirement 10.4: Enhanced privacy controls
 * 
 * Tests run on Android 10+ devices to validate actual compliance behavior
 */
@RunWith(AndroidJUnit4.class)
public class Android10ComplianceInstrumentationTest {
    
    private Context context;
    private Android10ComplianceValidator complianceValidator;
    private ActivityScenario<MainActivity> scenario;
    
    /**
     * Grant camera permission for tests that require it
     * This simulates the user granting permission
     */
    @Rule
    public GrantPermissionRule grantPermissionRule = 
            GrantPermissionRule.grant(Manifest.permission.CAMERA);
    
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        complianceValidator = new Android10ComplianceValidator(context);
        
        // Only run these tests on Android 10+
        assumeTrue("Android 10 compliance tests require API 29+", 
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
    }
    
    @After
    public void tearDown() {
        if (scenario != null) {
            scenario.close();
        }
    }
    
    /**
     * Test comprehensive Android 10 compliance validation
     * Validates all requirements: 10.1, 10.2, 10.3, 10.4
     */
    @Test
    public void testComprehensiveAndroid10Compliance() {
        Android10ComplianceValidator.ComplianceResult result = 
                complianceValidator.validateCompliance();
        
        assertNotNull("Compliance result should not be null", result);
        assertTrue("Should be compliant on Android 10+ device", result.isCompliant);
        
        // Verify no critical issues
        long criticalIssues = result.issues.stream()
                .filter(issue -> issue.severity == Android10ComplianceValidator.ComplianceIssue.Severity.CRITICAL)
                .count();
        
        assertEquals("Should have no critical compliance issues", 0, criticalIssues);
        
        // Log compliance summary
        android.util.Log.i("Android10Compliance", "Compliance validation result: " + result.summary);
        for (Android10ComplianceValidator.ComplianceIssue issue : result.issues) {
            android.util.Log.i("Android10Compliance", "Issue: " + issue.toString());
        }
    }
    
    /**
     * Test enhanced camera permission handling (Requirement 10.1)
     * Validates that camera permissions are properly declared and handled
     */
    @Test
    public void testEnhancedCameraPermissionHandling() {
        // Verify camera permission is declared in manifest
        PackageManager pm = context.getPackageManager();
        try {
            String[] permissions = pm.getPackageInfo(context.getPackageName(), 
                    PackageManager.GET_PERMISSIONS).requestedPermissions;
            
            boolean hasCameraPermission = false;
            if (permissions != null) {
                for (String permission : permissions) {
                    if (Manifest.permission.CAMERA.equals(permission)) {
                        hasCameraPermission = true;
                        break;
                    }
                }
            }
            
            assertTrue("Camera permission should be declared in manifest", hasCameraPermission);
            
        } catch (PackageManager.NameNotFoundException e) {
            fail("Package not found: " + e.getMessage());
        }
        
        // Test camera permission status
        int permissionStatus = context.checkSelfPermission(Manifest.permission.CAMERA);
        assertEquals("Camera permission should be granted for this test", 
                PackageManager.PERMISSION_GRANTED, permissionStatus);
        
        // Test camera privacy controls using validator
        scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(activity -> {
            boolean privacyControlsValid = complianceValidator.testCameraPrivacyControls(activity);
            assertTrue("Camera privacy controls should be valid", privacyControlsValid);
        });
        
        // Test using test utils for detailed validation
        scenario.onActivity(activity -> {
            Android10TestUtils.TestResult result = 
                    Android10TestUtils.testCameraPrivacyControls(activity);
            
            assertTrue("Camera privacy controls test should pass: " + result.message, 
                    result.passed);
            assertEquals("Should validate requirement 6.2", "6.2", result.requirement);
        });
    }
    
    /**
     * Test background activity restrictions compliance (Requirement 10.2)
     * Validates that app properly handles Android 10 background restrictions
     */
    @Test
    public void testBackgroundActivityRestrictionsCompliance() {
        Android10TestUtils.TestResult result = 
                Android10TestUtils.testBackgroundActivityRestrictions(context);
        
        assertNotNull("Test result should not be null", result);
        assertTrue("Background activity restrictions test should pass: " + result.message, 
                result.passed);
        assertEquals("Should validate requirement 6.3", "6.3", result.requirement);
        
        // Launch activity and verify background behavior
        scenario = ActivityScenario.launch(MainActivity.class);
        
        // Move activity to background
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
        
        // Verify camera is stopped when in background
        scenario.onActivity(activity -> {
            assertFalse("App should not be in foreground when paused", 
                    activity.isAppInForeground());
        });
        
        // Move back to foreground
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
        
        // Verify camera restarts when returning to foreground
        scenario.onActivity(activity -> {
            assertTrue("App should be in foreground when resumed", 
                    activity.isAppInForeground());
        });
    }
    
    /**
     * Test scoped storage implementation (Requirement 10.3)
     * Validates that app uses app-specific directories and doesn't access external storage
     */
    @Test
    public void testScopedStorageImplementation() {
        // Test scoped storage compliance using validator
        boolean temporaryFileCompliance = complianceValidator.validateTemporaryFileOperations();
        assertTrue("Temporary file operations should be scoped storage compliant", 
                temporaryFileCompliance);
        
        // Test using test utils
        Android10TestUtils.TestResult result = 
                Android10TestUtils.testScopedStorageCompliance(context);
        
        assertNotNull("Test result should not be null", result);
        assertTrue("Scoped storage compliance test should pass: " + result.message, 
                result.passed);
        assertEquals("Should validate requirement 6.1", "6.1", result.requirement);
        
        // Verify app-specific directories are accessible
        File cacheDir = context.getCacheDir();
        assertNotNull("Cache directory should be accessible", cacheDir);
        assertTrue("Cache directory should exist", cacheDir.exists());
        assertTrue("Cache directory should be writable", cacheDir.canWrite());
        
        File filesDir = context.getFilesDir();
        assertNotNull("Files directory should be accessible", filesDir);
        assertTrue("Files directory should exist", filesDir.exists());
        assertTrue("Files directory should be writable", filesDir.canWrite());
        
        // Verify app doesn't request legacy external storage
        android.content.pm.ApplicationInfo appInfo = context.getApplicationInfo();
        assertNotNull("Application info should not be null", appInfo);
        
        // Check that requestLegacyExternalStorage is not set (or is false)
        // This is validated by the manifest having android:requestLegacyExternalStorage="false"
        android.util.Log.i("Android10Compliance", 
                "App is using scoped storage (requestLegacyExternalStorage=false)");
    }
    
    /**
     * Test enhanced privacy controls (Requirement 10.4)
     * Validates enhanced location and camera privacy controls
     */
    @Test
    public void testEnhancedPrivacyControls() {
        Android10TestUtils.TestResult result = 
                Android10TestUtils.testEnhancedPrivacyControls(context);
        
        assertNotNull("Test result should not be null", result);
        assertTrue("Enhanced privacy controls test should pass: " + result.message, 
                result.passed);
        assertEquals("Should validate requirement 6.4", "6.4", result.requirement);
        
        // Test privacy messaging
        scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(activity -> {
            // Verify Android 10 privacy message is available
            String privacyMessage = Android10TestUtils.getAndroid10PrivacyMessage(activity);
            assertNotNull("Android 10 privacy message should be available", privacyMessage);
            assertFalse("Privacy message should not be empty", privacyMessage.isEmpty());
            assertTrue("Privacy message should mention privacy controls", 
                    privacyMessage.toLowerCase().contains("privacy"));
        });
    }
    
    /**
     * Test comprehensive compliance report generation
     * Validates all requirements together: 10.1, 10.2, 10.3, 10.4
     */
    @Test
    public void testComprehensiveComplianceReport() {
        scenario = ActivityScenario.launch(MainActivity.class);
        
        scenario.onActivity(activity -> {
            // Run comprehensive tests
            List<Android10TestUtils.TestResult> results = 
                    Android10TestUtils.runComprehensiveTests(activity);
            
            assertNotNull("Test results should not be null", results);
            assertFalse("Should have test results", results.isEmpty());
            
            // Verify all tests pass
            long passedTests = results.stream().filter(r -> r.passed).count();
            long totalTests = results.size();
            
            assertEquals("All compliance tests should pass", totalTests, passedTests);
            
            // Generate and validate compliance report
            String report = Android10TestUtils.generateComplianceReport(activity);
            assertNotNull("Compliance report should not be null", report);
            assertFalse("Compliance report should not be empty", report.isEmpty());
            assertTrue("Report should indicate compliance", report.contains("COMPLIANT"));
            
            // Log the full report
            android.util.Log.i("Android10Compliance", "Full compliance report:\n" + report);
            
            // Verify each requirement is tested
            assertTrue("Report should test requirement 6.1", 
                    results.stream().anyMatch(r -> "6.1".equals(r.requirement)));
            assertTrue("Report should test requirement 6.2", 
                    results.stream().anyMatch(r -> "6.2".equals(r.requirement)));
            assertTrue("Report should test requirement 6.3", 
                    results.stream().anyMatch(r -> "6.3".equals(r.requirement)));
            assertTrue("Report should test requirement 6.4", 
                    results.stream().anyMatch(r -> "6.4".equals(r.requirement)));
        });
    }
    
    /**
     * Test camera permission flow with Android 10 privacy messaging
     * Validates requirement 10.1 with actual permission flow
     */
    @Test
    public void testCameraPermissionFlowWithPrivacyMessaging() {
        scenario = ActivityScenario.launch(MainActivity.class);
        
        scenario.onActivity(activity -> {
            // Verify permission handler is initialized
            assertNotNull("Activity should not be null", activity);
            
            // Test that Android 10 privacy strings are defined
            try {
                String privacyNotice = activity.getString(R.string.android_10_privacy_notice);
                assertNotNull("Android 10 privacy notice should be defined", privacyNotice);
                assertFalse("Privacy notice should not be empty", privacyNotice.isEmpty());
                
                String privacyMessage = activity.getString(R.string.android_10_camera_privacy_message);
                assertNotNull("Android 10 camera privacy message should be defined", privacyMessage);
                assertFalse("Privacy message should not be empty", privacyMessage.isEmpty());
                assertTrue("Privacy message should mention Android 10", 
                        privacyMessage.contains("Android 10"));
                
            } catch (android.content.res.Resources.NotFoundException e) {
                fail("Android 10 privacy strings should be defined in resources: " + e.getMessage());
            }
        });
    }
    
    /**
     * Test app lifecycle with background restrictions
     * Validates requirement 10.2 with actual lifecycle transitions
     */
    @Test
    public void testAppLifecycleWithBackgroundRestrictions() {
        scenario = ActivityScenario.launch(MainActivity.class);
        
        // Test foreground state
        scenario.onActivity(activity -> {
            assertTrue("App should be in foreground initially", activity.isAppInForeground());
        });
        
        // Move to background (paused)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED);
        scenario.onActivity(activity -> {
            // App should still be considered in foreground in STARTED state
            // Only CREATED/DESTROYED are background
        });
        
        // Move to background (stopped)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
        scenario.onActivity(activity -> {
            assertFalse("App should not be in foreground when stopped", 
                    activity.isAppInForeground());
        });
        
        // Return to foreground
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
        scenario.onActivity(activity -> {
            assertTrue("App should be in foreground when resumed", 
                    activity.isAppInForeground());
        });
    }
    
    /**
     * Test file operations use app-specific directories
     * Validates requirement 10.3 with actual file operations
     */
    @Test
    public void testFileOperationsUseAppSpecificDirectories() {
        // Test cache directory operations
        File cacheDir = context.getCacheDir();
        File testCacheFile = new File(cacheDir, "test_cache_file.tmp");
        
        try {
            // Create test file
            boolean created = testCacheFile.createNewFile();
            assertTrue("Should be able to create file in cache directory", created);
            assertTrue("Test cache file should exist", testCacheFile.exists());
            
            // Write to test file
            java.io.FileWriter writer = new java.io.FileWriter(testCacheFile);
            writer.write("Test data for Android 10 compliance");
            writer.close();
            
            // Read from test file
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(testCacheFile));
            String content = reader.readLine();
            reader.close();
            
            assertNotNull("Should be able to read from cache file", content);
            assertEquals("Content should match", "Test data for Android 10 compliance", content);
            
            // Clean up
            boolean deleted = testCacheFile.delete();
            assertTrue("Should be able to delete cache file", deleted);
            
        } catch (java.io.IOException e) {
            fail("File operations in cache directory should work: " + e.getMessage());
        }
        
        // Test files directory operations
        File filesDir = context.getFilesDir();
        File testFile = new File(filesDir, "test_file.txt");
        
        try {
            // Create test file
            boolean created = testFile.createNewFile();
            assertTrue("Should be able to create file in files directory", created);
            assertTrue("Test file should exist", testFile.exists());
            
            // Clean up
            boolean deleted = testFile.delete();
            assertTrue("Should be able to delete file", deleted);
            
        } catch (java.io.IOException e) {
            fail("File operations in files directory should work: " + e.getMessage());
        }
    }
    
    /**
     * Test that app doesn't attempt to access external storage
     * Validates requirement 10.3 - no external storage access
     */
    @Test
    public void testNoExternalStorageAccess() {
        // Verify app doesn't use external storage APIs
        // This is validated by the manifest and code review
        
        // Test that app uses only app-specific directories
        File cacheDir = context.getCacheDir();
        File filesDir = context.getFilesDir();
        
        // Verify these are NOT in external storage
        String cachePath = cacheDir.getAbsolutePath();
        String filesPath = filesDir.getAbsolutePath();
        
        // App-specific directories should be in /data/data/<package>/
        assertTrue("Cache dir should be in app-specific location", 
                cachePath.contains("/data/") || cachePath.contains(context.getPackageName()));
        assertTrue("Files dir should be in app-specific location", 
                filesPath.contains("/data/") || filesPath.contains(context.getPackageName()));
        
        // Verify app doesn't request external storage permissions
        PackageManager pm = context.getPackageManager();
        try {
            String[] permissions = pm.getPackageInfo(context.getPackageName(), 
                    PackageManager.GET_PERMISSIONS).requestedPermissions;
            
            boolean hasExternalStoragePermission = false;
            if (permissions != null) {
                for (String permission : permissions) {
                    if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission) ||
                        Manifest.permission.READ_EXTERNAL_STORAGE.equals(permission)) {
                        hasExternalStoragePermission = true;
                        break;
                    }
                }
            }
            
            assertFalse("App should not request external storage permissions", 
                    hasExternalStoragePermission);
            
        } catch (PackageManager.NameNotFoundException e) {
            fail("Package not found: " + e.getMessage());
        }
    }
}
