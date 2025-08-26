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
        
        // Initialize display manager
        initializeDisplayManager();
        
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
                // Pass processed frame to display manager
                if (displayManager != null && displayManager.isDisplayReady()) {
                    displayManager.updateFrame(processedFrame);
                }
            }
            
            @Override
            public void onProcessingError(@NonNull Exception error, @androidx.annotation.Nullable org.opencv.core.Mat originalFrame) {
                Log.e(TAG, "OpenCV processing error", error);
                // Requirement 4.3: Fall back to displaying unprocessed frames
                Toast.makeText(MainActivity.this, "Processing error, showing original frame", Toast.LENGTH_SHORT).show();
                
                // Display original frame if available
                if (originalFrame != null && displayManager != null && displayManager.isDisplayReady()) {
                    displayManager.updateFrame(originalFrame);
                }
            }
            
            @Override
            public void onProcessingTimeout(@NonNull org.opencv.core.Mat originalFrame, long timeoutMs) {
                Log.w(TAG, "Processing timeout: " + timeoutMs + "ms");
                // Continue with original frame
                if (displayManager != null && displayManager.isDisplayReady()) {
                    displayManager.updateFrame(originalFrame);
                }
            }
        });
        
        Log.d(TAG, "OpenCV processor created, waiting for OpenCV library initialization");
    }
    
    /**
     * Initialize display manager with TextureView
     * Requirement 3.1: Display processed frames on screen
     */
    private void initializeDisplayManager() {
        Log.d(TAG, "Initializing display manager");
        
        displayManager = new com.example.opencvcamerastream.display.DisplayManager(this);
        
        // Set up display callback
        displayManager.setDisplayCallback(new com.example.opencvcamerastream.display.DisplayManager.DisplayCallback() {
            @Override
            public void onDisplayReady() {
                Log.d(TAG, "Display ready for rendering");
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Display ready", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onDisplayDestroyed() {
                Log.d(TAG, "Display destroyed");
            }
            
            @Override
            public void onFrameUpdateError(@NonNull Exception error) {
                Log.e(TAG, "Frame update error", error);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Display error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onPerformanceUpdate(float fps, float avgUpdateTime) {
                Log.v(TAG, "Display performance - FPS: " + String.format("%.1f", fps) + 
                        ", Avg update time: " + String.format("%.1f", avgUpdateTime) + "ms");
            }
        });
        
        // Set up TextureView
        android.view.TextureView textureView = findViewById(R.id.textureView);
        if (textureView != null) {
            if (displayManager.setupDisplay(textureView)) {
                Log.i(TAG, "Display manager initialized successfully");
            } else {
                Log.e(TAG, "Failed to initialize display manager");
                Toast.makeText(this, "Failed to initialize display", Toast.LENGTH_LONG).show();
            }
        } else {
            Log.e(TAG, "TextureView not found in layout");
            Toast.makeText(this, "Display setup error", Toast.LENGTH_LONG).show();
        }
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
        
        // Restart camera preview if it was stopped and we have permissions
        if (cameraManager != null && cameraManager.isInitialized() && 
            !cameraManager.isPreviewActive() && permissionHandler != null && 
            permissionHandler.isCameraPermissionGranted()) {
            Log.d(TAG, "Restarting camera preview on resume");
            cameraManager.startPreview();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        isAppInForeground = false;
        
        Log.d(TAG, "Activity paused");
        
        // Requirement 5.4: Properly release camera resources when app is backgrounded
        if (cameraManager != null) {
            Log.d(TAG, "Stopping camera preview due to app backgrounding");
            cameraManager.stopPreview();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        Log.d(TAG, "Activity destroyed, releasing all resources");
        
        // Release camera resources completely
        if (cameraManager != null) {
            cameraManager.release();
            cameraManager = null;
        }
        
        // Release OpenCV processor resources
        if (openCVProcessor != null) {
            openCVProcessor.release();
            openCVProcessor = null;
        }
        
        // Release display manager resources
        if (displayManager != null) {
            displayManager.release();
            displayManager = null;
        }
        
        Log.d(TAG, "All resources released");
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
    
    // Camera components
    private com.example.opencvcamerastream.camera.CameraManager cameraManager;
    
    // Display components
    private com.example.opencvcamerastream.display.DisplayManager displayManager;
    
    /**
     * Initialize camera components after permission is granted
     */
    private void initializeCameraComponents() {
        Log.d(TAG, "Initializing camera components");
        
        if (cameraManager == null) {
            // Initialize camera manager
            cameraManager = new com.example.opencvcamerastream.camera.CameraManager(this);
            
            // Set up camera callbacks
            cameraManager.setCameraCallback(new com.example.opencvcamerastream.camera.CameraManager.CameraCallback() {
                @Override
                public void onCameraOpened() {
                    Log.d(TAG, "Camera opened successfully");
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Camera ready", Toast.LENGTH_SHORT).show();
                    });
                }
                
                @Override
                public void onCameraClosed() {
                    Log.d(TAG, "Camera closed");
                }
                
                @Override
                public void onCameraError(int error, String message) {
                    Log.e(TAG, "Camera error: " + error + ", message: " + message);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Camera error: " + message, Toast.LENGTH_LONG).show();
                    });
                    
                    // Requirement 4.2: Attempt to reconnect automatically
                    if (isAppInForeground && permissionHandler != null && 
                        permissionHandler.isCameraPermissionGranted()) {
                        Log.d(TAG, "Attempting to reconnect camera in 2 seconds");
                        new android.os.Handler().postDelayed(() -> {
                            if (isAppInForeground) {
                                attemptCameraReconnection();
                            }
                        }, 2000);
                    }
                }
                
                @Override
                public void onCameraDisconnected() {
                    Log.w(TAG, "Camera disconnected");
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Camera disconnected", Toast.LENGTH_SHORT).show();
                    });
                    
                    // Requirement 4.2: Attempt to reconnect automatically
                    if (isAppInForeground && permissionHandler != null && 
                        permissionHandler.isCameraPermissionGranted()) {
                        Log.d(TAG, "Attempting to reconnect camera after disconnection");
                        attemptCameraReconnection();
                    }
                }
            });
            
            // Set up frame callback for OpenCV processing
            cameraManager.setFrameCallback(new com.example.opencvcamerastream.camera.CameraManager.FrameCallback() {
                @Override
                public void onFrameAvailable(@androidx.annotation.NonNull android.media.Image frame) {
                    // Process frame with OpenCV if initialized
                    if (isOpenCVInitialized && openCVProcessor != null) {
                        // TODO: Convert Image to Mat and process (will be implemented in task 6)
                        Log.v(TAG, "Frame available for processing: " + frame.getWidth() + "x" + frame.getHeight());
                    } else {
                        Log.v(TAG, "Frame available but OpenCV not ready");
                    }
                }
            });
        }
        
        // Initialize camera
        if (cameraManager.initializeCamera()) {
            Log.d(TAG, "Camera initialized, starting preview");
            if (cameraManager.startPreview()) {
                Log.i(TAG, "Camera preview started successfully");
            } else {
                Log.e(TAG, "Failed to start camera preview");
                Toast.makeText(this, "Failed to start camera preview", Toast.LENGTH_LONG).show();
            }
        } else {
            Log.e(TAG, "Failed to initialize camera");
            Toast.makeText(this, "Failed to initialize camera", Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Attempt to reconnect camera after error or disconnection
     * Requirement 4.2: Attempt to reconnect automatically when camera becomes unavailable
     */
    private void attemptCameraReconnection() {
        Log.d(TAG, "Attempting camera reconnection");
        
        if (cameraManager != null) {
            // Release current resources
            cameraManager.release();
            
            // Reinitialize
            if (cameraManager.initializeCamera()) {
                if (cameraManager.startPreview()) {
                    Log.i(TAG, "Camera reconnected successfully");
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Camera reconnected", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    Log.w(TAG, "Camera reconnection failed at preview start");
                }
            } else {
                Log.w(TAG, "Camera reconnection failed at initialization");
            }
        }
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