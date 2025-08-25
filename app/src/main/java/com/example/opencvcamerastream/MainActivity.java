package com.example.opencvcamerastream;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import com.example.opencvcamerastream.permissions.PermissionHandler;
import com.example.opencvcamerastream.processing.OpenCVProcessor;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

/**
 * MainActivity for OpenCV Camera Stream Application
 * 
 * This activity is designed for Android 10 (API 29) compatibility with enhanced
 * privacy controls, background activity restrictions, and scoped storage compliance.
 * 
 * Key Android 10 Features Addressed:
 * - Enhanced camera privacy controls
 * - Background activity limitations
 * - Scoped storage requirements
 * - Runtime permission handling improvements
 */
public class MainActivity extends AppCompatActivity implements PermissionHandler.PermissionCallback {
    
    private static final String TAG = "MainActivity";
    
    private PermissionHandler permissionHandler;
    private OpenCVProcessor openCVProcessor;
    private boolean isAppInForeground = false;
    private boolean isOpenCVInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Log.d(TAG, "MainActivity created");
        
        // Initialize permission handler with Android 10 compliance
        initializePermissionHandler();
        
        // Initialize OpenCV processor
        initializeOpenCVProcessor();
        
        // TODO: Set up Camera2 API with privacy controls (Task 4)
    }
    
    /**
     * Initialize permission handler and set up callbacks
     */
    private void initializePermissionHandler() {
        permissionHandler = new PermissionHandler(this);
        permissionHandler.setPermissionCallback(this);
        
        Log.d(TAG, "Permission handler initialized");
    }
    
    /**
     * Initialize OpenCV processor with Android 10 compatibility
     * Requirement 5.1: Initialize OpenCV within 3 seconds
     */
    private void initializeOpenCVProcessor() {
        Log.d(TAG, "Initializing OpenCV processor");
        
        // Create OpenCV processor with default configuration
        OpenCVProcessor.ProcessingConfig config = new OpenCVProcessor.ProcessingConfig();
        config.mode = OpenCVProcessor.ProcessingMode.GRAYSCALE;
        config.enablePerformanceOptimization = true;
        config.maxProcessingTimeMs = 50; // Requirement 2.3
        
        openCVProcessor = new OpenCVProcessor(config);
        
        // Set up processing callback
        openCVProcessor.setProcessingCallback(new OpenCVProcessor.ProcessingCallback() {
            @Override
            public void onFrameProcessed(@NonNull org.opencv.core.Mat processedFrame, long processingTimeMs) {
                Log.v(TAG, "Frame processed in " + processingTimeMs + "ms");
                // TODO: Pass processed frame to display manager (Task 5)
            }
            
            @Override
            public void onProcessingError(@NonNull Exception error, @androidx.annotation.Nullable org.opencv.core.Mat originalFrame) {
                Log.e(TAG, "OpenCV processing error", error);
                // Requirement 4.3: Fall back to displaying unprocessed frames
                Toast.makeText(MainActivity.this, "Processing error, showing original frame", Toast.LENGTH_SHORT).show();
            }
            
            @Override
            public void onProcessingTimeout(@NonNull org.opencv.core.Mat originalFrame, long timeoutMs) {
                Log.w(TAG, "Processing timeout: " + timeoutMs + "ms");
                // Continue with original frame
            }
        });
        
        Log.d(TAG, "OpenCV processor created, waiting for OpenCV library initialization");
    }
    
    /**
     * OpenCV loader callback for Android 10 compatibility
     * Handles OpenCV library initialization
     */
    private final BaseLoaderCallback openCVLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    Log.d(TAG, "OpenCV loaded successfully");
                    
                    // Initialize the processor
                    if (openCVProcessor != null && openCVProcessor.initialize()) {
                        isOpenCVInitialized = true;
                        Log.i(TAG, "OpenCV processor initialized successfully");
                        
                        // Show success message
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "OpenCV initialized", Toast.LENGTH_SHORT).show();
                        });
                        
                        // If camera permission is already granted, proceed with camera initialization
                        if (permissionHandler != null && permissionHandler.isCameraPermissionGranted()) {
                            initializeCameraComponents();
                        }
                    } else {
                        Log.e(TAG, "Failed to initialize OpenCV processor");
                        handleOpenCVInitializationFailure();
                    }
                    break;
                    
                case LoaderCallbackInterface.INIT_FAILED:
                    Log.e(TAG, "OpenCV initialization failed");
                    handleOpenCVInitializationFailure();
                    break;
                    
                case LoaderCallbackInterface.INSTALL_CANCELED:
                    Log.w(TAG, "OpenCV installation canceled");
                    handleOpenCVInitializationFailure();
                    break;
                    
                case LoaderCallbackInterface.INCOMPATIBLE_MANAGER_VERSION:
                    Log.e(TAG, "Incompatible OpenCV Manager version");
                    handleOpenCVInitializationFailure();
                    break;
                    
                case LoaderCallbackInterface.MARKET_ERROR:
                    Log.e(TAG, "OpenCV Market error");
                    handleOpenCVInitializationFailure();
                    break;
                    
                default:
                    Log.e(TAG, "Unknown OpenCV loader status: " + status);
                    handleOpenCVInitializationFailure();
                    break;
            }
        }
    };
    
    /**
     * Handle OpenCV initialization failure
     * Requirement 4.3: Display error message and fall back to unprocessed frames
     */
    private void handleOpenCVInitializationFailure() {
        isOpenCVInitialized = false;
        
        runOnUiThread(() -> {
            Toast.makeText(this, "OpenCV initialization failed. App will show unprocessed camera frames.", 
                    Toast.LENGTH_LONG).show();
        });
        
        Log.w(TAG, "OpenCV initialization failed, app will continue with limited functionality");
        
        // Continue with camera initialization even without OpenCV
        if (permissionHandler != null && permissionHandler.isCameraPermissionGranted()) {
            initializeCameraComponents();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        isAppInForeground = true;
        
        Log.d(TAG, "Activity resumed, initializing OpenCV and checking camera permissions");
        
        // Initialize OpenCV when activity resumes
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, openCVLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            openCVLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        
        // Check and request camera permission when app comes to foreground
        // This handles Android 10 background activity restrictions
        if (permissionHandler != null) {
            permissionHandler.requestCameraPermission();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        isAppInForeground = false;
        
        Log.d(TAG, "Activity paused");
        
        // TODO: Properly release camera resources per Android 10 guidelines (Task 4)
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        Log.d(TAG, "Permission result received for request code: " + requestCode);
        
        // Handle permission results through our permission handler
        if (permissionHandler != null) {
            permissionHandler.handlePermissionResult(requestCode, permissions, grantResults);
        }
    }
    
    // PermissionHandler.PermissionCallback implementation
    
    @Override
    public void onPermissionGranted() {
        Log.d(TAG, "Camera permission granted");
        Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show();
        
        // Permission granted, proceed with camera initialization
        initializeCameraComponents();
    }
    
    @Override
    public void onPermissionDenied(boolean isPermanentlyDenied) {
        Log.w(TAG, "Camera permission denied. Permanently denied: " + isPermanentlyDenied);
        
        if (isPermanentlyDenied) {
            Toast.makeText(this, "Camera permission permanently denied. Please enable in Settings.", 
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Camera permission denied. App cannot function without camera access.", 
                    Toast.LENGTH_LONG).show();
        }
        
        // Handle permission denial - app cannot function without camera
        handlePermissionDenial(isPermanentlyDenied);
    }
    
    @Override
    public void onPermissionRationaleRequired() {
        Log.d(TAG, "Permission rationale required");
        // The permission handler will show the rationale dialog
    }
    
    /**
     * Initialize camera components after permission is granted
     */
    private void initializeCameraComponents() {
        Log.d(TAG, "Initializing camera components");
        
        // TODO: This will be implemented in subsequent tasks
        // - Initialize OpenCV (Task 3)
        // - Set up Camera2 API (Task 4)
        // - Initialize display system (Task 5)
        
        Toast.makeText(this, "Ready to initialize camera (pending implementation)", 
                Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Handle permission denial scenarios
     */
    private void handlePermissionDenial(boolean isPermanentlyDenied) {
        // For now, we'll just log and show a message
        // In a production app, you might want to:
        // - Disable camera-related UI elements
        // - Show alternative content
        // - Guide user to settings if permanently denied
        
        Log.w(TAG, "Handling permission denial. App functionality limited.");
        
        if (isPermanentlyDenied) {
            // Could show a persistent notification or different UI state
            Log.w(TAG, "User needs to manually enable permission in Settings");
        }
    }
    
    /**
     * Check if the app is currently in foreground
     * Useful for Android 10 background activity restrictions
     */
    public boolean isAppInForeground() {
        return isAppInForeground;
    }
}