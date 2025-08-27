package com.example.opencvcamerastream;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.view.View;
import android.widget.AdapterView;
import com.example.opencvcamerastream.permissions.PermissionHandler;
import com.example.opencvcamerastream.processing.OpenCVProcessor;
import com.example.opencvcamerastream.processing.FrameProcessor;
import com.example.opencvcamerastream.error.ErrorHandler;
import com.example.opencvcamerastream.error.ErrorDialogManager;
import com.example.opencvcamerastream.error.PerformanceMonitor;
import com.example.opencvcamerastream.performance.PerformanceMetricsCollector;
import com.example.opencvcamerastream.performance.PerformanceDisplayManager;
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
    
    // Processing mode selection
    private Spinner processingModeSpinner;
    private ArrayAdapter<String> processingModeAdapter;
    
    // Additional components referenced in the code
    private com.example.opencvcamerastream.processing.FrameProcessor frameProcessor;
    private com.example.opencvcamerastream.display.DisplayManager displayManager;
    
    // Error handling and performance monitoring
    private ErrorHandler errorHandler;
    private ErrorDialogManager errorDialogManager;
    private PerformanceMonitor performanceMonitor;
    private PerformanceMetricsCollector performanceMetricsCollector;
    private PerformanceDisplayManager performanceDisplayManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Log.d(TAG, "MainActivity created");
        
        // Initialize error handling system
        initializeErrorHandling();
        
        // Initialize permission handler with Android 10 compliance
        initializePermissionHandler();
        
        // Initialize OpenCV processor
        initializeOpenCVProcessor();
        
        // Initialize display manager
        initializeDisplayManager();
        
        // Initialize processing mode selection interface
        initializeProcessingModeSelection();
        
        // TODO: Set up Camera2 API with privacy controls (Task 4)
    }
    
    /**
     * Initialize error handling and performance monitoring system
     * Requirement 4.1, 4.2, 4.3, 4.4: Comprehensive error handling and recovery
     */
    private void initializeErrorHandling() {
        Log.d(TAG, "Initializing error handling system");
        
        // Initialize error handler
        errorHandler = new ErrorHandler(this);
        errorHandler.setErrorCallback(new ErrorHandler.ErrorCallback() {
            @Override
            public void onError(@NonNull ErrorHandler.ErrorInfo errorInfo) {
                Log.w(TAG, "Error handled: " + errorInfo.category + " - " + errorInfo.message);
                
                // Show error dialog on UI thread
                runOnUiThread(() -> {
                    if (errorDialogManager != null && !errorDialogManager.isDialogShowing()) {
                        errorDialogManager.showErrorDialog(errorInfo, new ErrorDialogManager.DialogActionCallback() {
                            @Override
                            public void onRetryRequested() {
                                handleErrorRetry(errorInfo);
                            }
                            
                            @Override
                            public void onSettingsRequested() {
                                // ErrorDialogManager handles opening settings
                                Log.d(TAG, "User requested to open settings");
                            }
                            
                            @Override
                            public void onDismissed() {
                                Log.d(TAG, "Error dialog dismissed");
                            }
                            
                            @Override
                            public void onFallbackAccepted() {
                                handleErrorFallback(errorInfo);
                            }
                        });
                    }
                });
            }
            
            @Override
            public void onRecoveryAttempt(@NonNull ErrorHandler.ErrorInfo errorInfo, int attemptNumber) {
                Log.i(TAG, "Recovery attempt " + attemptNumber + " for " + errorInfo.category);
                
                runOnUiThread(() -> {
                    if (errorDialogManager != null) {
                        errorDialogManager.showRecoveryDialog(
                                "Attempting to recover from " + errorInfo.category.name().toLowerCase() + " error",
                                attemptNumber);
                    }
                });
            }
            
            @Override
            public void onRecoverySuccess(@NonNull ErrorHandler.ErrorInfo errorInfo, int totalAttempts) {
                Log.i(TAG, "Recovery successful after " + totalAttempts + " attempts for " + errorInfo.category);
                
                runOnUiThread(() -> {
                    if (errorDialogManager != null) {
                        errorDialogManager.showRecoverySuccessDialog(
                                errorInfo.category.name().toLowerCase() + " error recovered",
                                totalAttempts);
                    }
                    Toast.makeText(MainActivity.this, "System recovered successfully", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onRecoveryFailed(@NonNull ErrorHandler.ErrorInfo errorInfo, int totalAttempts) {
                Log.w(TAG, "Recovery failed after " + totalAttempts + " attempts for " + errorInfo.category);
                
                runOnUiThread(() -> {
                    if (errorDialogManager != null) {
                        errorDialogManager.showRecoveryFailureDialog(
                                "Unable to recover from " + errorInfo.category.name().toLowerCase() + " error",
                                totalAttempts,
                                null);
                    }
                });
            }
        });
        
        // Initialize error dialog manager
        errorDialogManager = new ErrorDialogManager(this);
        
        // Initialize performance monitor
        performanceMonitor = new PerformanceMonitor(this);
        performanceMonitor.setPerformanceCallback(new PerformanceMonitor.PerformanceCallback() {
            @Override
            public void onPerformanceLevelChanged(@NonNull PerformanceMonitor.PerformanceLevel newLevel, 
                                                @NonNull PerformanceMonitor.PerformanceLevel oldLevel) {
                Log.i(TAG, "Performance level changed: " + oldLevel + " -> " + newLevel);
                
                runOnUiThread(() -> {
                    String message = "Performance adjusted to " + newLevel.name().toLowerCase() + " level";
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                });
                
                // Update processing configuration based on new performance level
                updateProcessingConfiguration(newLevel);
            }
            
            @Override
            public void onMemoryWarning(long usedMemoryMB, long totalMemoryMB) {
                Log.w(TAG, "Memory warning: " + usedMemoryMB + "MB / " + totalMemoryMB + "MB");
                
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Memory usage high, optimizing performance", 
                            Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onMemoryCritical(long usedMemoryMB, long totalMemoryMB) {
                Log.e(TAG, "Critical memory usage: " + usedMemoryMB + "MB / " + totalMemoryMB + "MB");
                
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Critical memory usage, reducing processing quality", 
                            Toast.LENGTH_LONG).show();
                });
            }
            
            @Override
            public void onProcessingTimeWarning(long processingTimeMs) {
                Log.w(TAG, "Processing time warning: " + processingTimeMs + "ms");
            }
            
            @Override
            public void onFrameDropRecommended(@NonNull String reason) {
                Log.d(TAG, "Frame drop recommended: " + reason);
            }
        });
        
        // Initialize performance metrics collector
        performanceMetricsCollector = new PerformanceMetricsCollector(performanceMonitor);
        performanceMetricsCollector.setCallback(new PerformanceMetricsCollector.PerformanceMetricsCallback() {
            @Override
            public void onFrameRateUpdate(@NonNull PerformanceMetricsCollector.FrameRateMetrics metrics) {
                // Update performance display
                if (performanceDisplayManager != null) {
                    PerformanceMonitor.PerformanceMetrics systemMetrics = performanceMonitor.getCurrentMetrics();
                    performanceDisplayManager.updateMetrics(metrics, systemMetrics);
                }
            }
            
            @Override
            public void onPerformanceAdjustment(@NonNull PerformanceMetricsCollector.PerformanceAdjustment adjustment) {
                Log.i(TAG, "Performance adjustment applied: " + adjustment);
                
                runOnUiThread(() -> {
                    String message = "Performance adjusted: " + adjustment.reason;
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                });
                
                // Show adjustment in performance display
                if (performanceDisplayManager != null) {
                    performanceDisplayManager.showPerformanceAdjustment(adjustment);
                }
                
                // Apply adjustment to processing pipeline
                applyPerformanceAdjustment(adjustment);
            }
            
            @Override
            public void onFrameDropRecommended(@NonNull String reason) {
                Log.d(TAG, "Frame drop recommended by metrics collector: " + reason);
            }
        });
        
        // Initialize performance display manager
        performanceDisplayManager = new PerformanceDisplayManager(this);
        
        Log.d(TAG, "Error handling system initialized");
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
        
        // Set error handler and performance monitor for OpenCV processor
        if (errorHandler != null) {
            openCVProcessor.setErrorHandler(errorHandler);
        }
        if (performanceMonitor != null) {
            openCVProcessor.setPerformanceMonitor(performanceMonitor);
        }
        
        // Create frame processor for camera-to-display pipeline integration
        frameProcessor = new com.example.opencvcamerastream.processing.FrameProcessor(openCVProcessor);
        
        // Set up frame processor callback to handle processed frames
        frameProcessor.setProcessingCallback(new com.example.opencvcamerastream.processing.FrameProcessor.ProcessingCallback() {
            @Override
            public void onFrameProcessed(@NonNull org.opencv.core.Mat processedFrame, @NonNull android.media.Image originalImage, long processingTimeMs) {
                Log.v(TAG, "Frame processed in " + processingTimeMs + "ms");
                
                // Display processed frame on UI thread
                runOnUiThread(() -> {
                    if (displayManager != null && displayManager.isDisplayReady()) {
                        displayManager.updateFrame(processedFrame);
                    }
                });
                
                // Clean up resources
                processedFrame.release();
                originalImage.close();
            }
            
            @Override
            public void onProcessingFailed(@NonNull Exception error, @androidx.annotation.Nullable android.media.Image originalImage) {
                Log.e(TAG, "Frame processing failed", error);
                
                // Requirement 4.3: Fall back to displaying unprocessed frames
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Processing error, showing original frame", Toast.LENGTH_SHORT).show();
                });
                
                // Try to convert original image to Mat and display it
                if (originalImage != null) {
                    try {
                        org.opencv.core.Mat originalMat = com.example.opencvcamerastream.processing.OpenCVProcessor.imageToMat(originalImage);
                        
                        runOnUiThread(() -> {
                            if (displayManager != null && displayManager.isDisplayReady()) {
                                displayManager.updateFrame(originalMat);
                            }
                        });
                        
                        originalMat.release();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to convert original image for display", e);
                    } finally {
                        originalImage.close();
                    }
                } else if (originalImage != null) {
                    originalImage.close();
                }
            }
            
            @Override
            public void onFrameDropped(@NonNull android.media.Image droppedImage) {
                Log.v(TAG, "Frame dropped due to queue overflow");
                droppedImage.close();
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
     * Initialize processing mode selection interface
     * Requirement 2.2: Create processing mode selection interface
     */
    private void initializeProcessingModeSelection() {
        Log.d(TAG, "Initializing processing mode selection");
        
        processingModeSpinner = findViewById(R.id.processingModeSpinner);
        if (processingModeSpinner == null) {
            Log.e(TAG, "Processing mode spinner not found in layout");
            return;
        }
        
        // Create adapter with processing mode options
        String[] processingModes = getResources().getStringArray(R.array.processing_modes);
        processingModeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, processingModes);
        processingModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        // Set adapter to spinner
        processingModeSpinner.setAdapter(processingModeAdapter);
        
        // Set default selection (Grayscale)
        processingModeSpinner.setSelection(1); // Index 1 = Grayscale
        
        // Set up selection listener
        processingModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onProcessingModeSelected(position);
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
        
        Log.d(TAG, "Processing mode selection initialized");
    }
    
    /**
     * Handle processing mode selection
     * Updates OpenCV processor configuration based on selected mode
     */
    private void onProcessingModeSelected(int position) {
        if (openCVProcessor == null) {
            Log.w(TAG, "OpenCV processor not initialized, cannot change processing mode");
            return;
        }
        
        OpenCVProcessor.ProcessingMode selectedMode;
        String modeName;
        
        // Map spinner position to processing mode
        switch (position) {
            case 0: // Passthrough
                selectedMode = OpenCVProcessor.ProcessingMode.PASSTHROUGH;
                modeName = "Passthrough";
                break;
            case 1: // Grayscale
                selectedMode = OpenCVProcessor.ProcessingMode.GRAYSCALE;
                modeName = "Grayscale";
                break;
            case 2: // Edge Detection
                selectedMode = OpenCVProcessor.ProcessingMode.EDGE_DETECTION;
                modeName = "Edge Detection";
                break;
            case 3: // HSV Color
                selectedMode = OpenCVProcessor.ProcessingMode.COLOR_HSV;
                modeName = "HSV Color";
                break;
            case 4: // LAB Color
                selectedMode = OpenCVProcessor.ProcessingMode.COLOR_LAB;
                modeName = "LAB Color";
                break;
            case 5: // Blur
                selectedMode = OpenCVProcessor.ProcessingMode.BLUR;
                modeName = "Blur";
                break;
            case 6: // Sharpen
                selectedMode = OpenCVProcessor.ProcessingMode.SHARPEN;
                modeName = "Sharpen";
                break;
            default:
                selectedMode = OpenCVProcessor.ProcessingMode.GRAYSCALE;
                modeName = "Grayscale";
                break;
        }
        
        // Update processing configuration
        OpenCVProcessor.ProcessingConfig config = new OpenCVProcessor.ProcessingConfig();
        config.mode = selectedMode;
        config.enablePerformanceOptimization = true;
        config.maxProcessingTimeMs = 50;
        
        // Set processing-specific parameters
        switch (selectedMode) {
            case EDGE_DETECTION:
                config.cannyLowThreshold = 50.0;
                config.cannyHighThreshold = 150.0;
                config.cannyApertureSize = 3;
                break;
            case BLUR:
                config.blurKernelSize = 15;
                config.blurSigmaX = 0.0;
                config.blurSigmaY = 0.0;
                break;
            case SHARPEN:
                config.sharpenStrength = 1.0f;
                break;
        }
        
        openCVProcessor.setProcessingConfig(config);
        
        Log.i(TAG, "Processing mode changed to: " + modeName);
        Toast.makeText(this, "Processing mode: " + modeName, Toast.LENGTH_SHORT).show();
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
                        
                        // Start frame processor
                        if (frameProcessor != null && frameProcessor.start()) {
                            Log.d(TAG, "Frame processor started successfully");
                        } else {
                            Log.e(TAG, "Failed to start frame processor");
                        }
                        
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
        
        // Restart frame processor if OpenCV is initialized
        if (isOpenCVInitialized && frameProcessor != null && !frameProcessor.isProcessing()) {
            Log.d(TAG, "Restarting frame processor on resume");
            frameProcessor.start();
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
        
        // Stop frame processor to save resources
        if (frameProcessor != null && frameProcessor.isProcessing()) {
            Log.d(TAG, "Stopping frame processor due to app backgrounding");
            frameProcessor.stop();
        }
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
    
    // Frame processing pipeline
    private com.example.opencvcamerastream.processing.FrameProcessor frameProcessor;
    
    /**
     * Initialize camera components after permission is granted
     */
    private void initializeCameraComponents() {
        Log.d(TAG, "Initializing camera components");
        
        if (cameraManager == null) {
            // Initialize camera manager
            cameraManager = new com.example.opencvcamerastream.camera.CameraManager(this);
            
            // Set error handler for camera manager
            if (errorHandler != null) {
                cameraManager.setErrorHandler(errorHandler);
            }
            
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
                    if (isOpenCVInitialized && openCVProcessor != null && frameProcessor != null) {
                        frameProcessor.processFrameAsync(frame);
                    } else {
                        Log.v(TAG, "Frame available but processing pipeline not ready");
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
        // For now, we show error message and potentially exit
        Log.w(TAG, "Handling permission denial, permanently denied: " + isPermanentlyDenied);
        
        if (isPermanentlyDenied) {
            // Show dialog directing user to settings
            runOnUiThread(() -> {
                if (errorDialogManager != null) {
                    ErrorHandler.ErrorInfo errorInfo = new ErrorHandler.ErrorInfo(
                        ErrorHandler.ErrorCategory.PERMISSION,
                        "Camera permission permanently denied",
                        "Please enable camera permission in Settings to use this app",
                        null
                    );
                    errorDialogManager.showErrorDialog(errorInfo, null);
                }
            });
        }
    }
    
    /**
     * Update processing configuration based on performance level
     * Requirement 5.3: Automatically adjust processing parameters for optimal performance
     */
    private void updateProcessingConfiguration(@NonNull PerformanceMonitor.PerformanceLevel level) {
        Log.d(TAG, "Updating processing configuration for level: " + level);
        
        if (openCVProcessor != null) {
            PerformanceMonitor.ProcessingRecommendation recommendation = 
                performanceMonitor.getProcessingRecommendation();
            
            // Create new processing config based on recommendation
            OpenCVProcessor.ProcessingConfig config = new OpenCVProcessor.ProcessingConfig();
            
            switch (level) {
                case HIGH:
                    config.mode = OpenCVProcessor.ProcessingMode.GRAYSCALE;
                    config.enablePerformanceOptimization = true;
                    config.maxProcessingTimeMs = 50;
                    break;
                    
                case MEDIUM:
                    config.mode = OpenCVProcessor.ProcessingMode.GRAYSCALE;
                    config.enablePerformanceOptimization = true;
                    config.maxProcessingTimeMs = 75;
                    break;
                    
                case LOW:
                    config.mode = OpenCVProcessor.ProcessingMode.PASSTHROUGH;
                    config.enablePerformanceOptimization = true;
                    config.maxProcessingTimeMs = 100;
                    break;
                    
                case CRITICAL:
                    config.mode = OpenCVProcessor.ProcessingMode.PASSTHROUGH;
                    config.enablePerformanceOptimization = true;
                    config.maxProcessingTimeMs = 150;
                    break;
            }
            
            openCVProcessor.setProcessingConfig(config);
            Log.i(TAG, "Processing configuration updated: " + config.mode + 
                      ", maxTime: " + config.maxProcessingTimeMs + "ms");
        }
    }
    
    /**
     * Apply performance adjustment from metrics collector
     */
    private void applyPerformanceAdjustment(@NonNull PerformanceMetricsCollector.PerformanceAdjustment adjustment) {
        Log.d(TAG, "Applying performance adjustment: " + adjustment);
        
        // Update frame processor settings if available
        if (frameProcessor != null) {
            // Apply frame dropping settings
            if (performanceMetricsCollector != null) {
                performanceMetricsCollector.setFrameDroppingEnabled(adjustment.enableFrameDropping);
                performanceMetricsCollector.setFrameSkipRatio(adjustment.frameSkipRatio);
            }
        }
        
        // Update OpenCV processor settings
        if (openCVProcessor != null) {
            OpenCVProcessor.ProcessingConfig config = new OpenCVProcessor.ProcessingConfig();
            
            // Adjust processing mode based on quality reduction
            if (adjustment.qualityReduction <= 0.5f) {
                config.mode = OpenCVProcessor.ProcessingMode.PASSTHROUGH;
            } else {
                config.mode = OpenCVProcessor.ProcessingMode.GRAYSCALE;
            }
            
            config.enablePerformanceOptimization = true;
            config.maxProcessingTimeMs = adjustment.maxProcessingTimeMs;
            
            openCVProcessor.setProcessingConfig(config);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        isAppInForeground = true;
        
        Log.d(TAG, "Activity resumed, initializing OpenCV and checking camera permissions");
        
        // Start performance monitoring
        if (performanceMetricsCollector != null) {
            performanceMetricsCollector.startMonitoring();
        }
        
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
        
        // Restart frame processor if OpenCV is initialized
        if (isOpenCVInitialized && frameProcessor != null && !frameProcessor.isProcessing()) {
            Log.d(TAG, "Restarting frame processor on resume");
            frameProcessor.start();
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
        
        // Stop performance monitoring
        if (performanceMetricsCollector != null) {
            performanceMetricsCollector.stopMonitoring();
        }
        
        // Requirement 5.4: Properly release camera resources when app is backgrounded
        if (cameraManager != null) {
            Log.d(TAG, "Stopping camera preview due to app backgrounding");
            cameraManager.stopPreview();
        }
        
        // Stop frame processor to save resources
        if (frameProcessor != null && frameProcessor.isProcessing()) {
            Log.d(TAG, "Stopping frame processor due to app backgrounding");
            frameProcessor.stop();
        }
    }'ll just log and show a message
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
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        Log.d(TAG, "Activity destroyed, releasing all resources");
        
        // Release camera resources completely
        if (cameraManager != null) {
            cameraManager.release();
            cameraManager = null;
        }
        
        // Stop and release frame processor
        if (frameProcessor != null) {
            frameProcessor.stop();
            frameProcessor = null;
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
        
        // Release error handling resources
        if (errorDialogManager != null) {
            errorDialogManager.release();
            errorDialogManager = null;
        }
        
        Log.d(TAG, "All resources released");
    }
    
    /**
     * Check if the app is currently in foreground
     * Useful for Android 10 background activity restrictions
     */
    public boolean isAppInForeground() {
        return isAppInForeground;
    }
    
    /**
     * Handle error retry requests from user
     */
    private void handleErrorRetry(@NonNull ErrorHandler.ErrorInfo errorInfo) {
        Log.d(TAG, "Handling error retry for: " + errorInfo.category);
        
        switch (errorInfo.category) {
            case CAMERA_PERMISSION:
                // Request camera permission again
                if (permissionHandler != null) {
                    permissionHandler.requestCameraPermission();
                }
                break;
                
            case CAMERA_HARDWARE:
                // Attempt camera reconnection
                if (cameraManager != null) {
                    cameraManager.attemptReconnection();
                }
                break;
                
            case OPENCV_PROCESSING:
                // Reset OpenCV processor and exit fallback mode
                if (openCVProcessor != null && openCVProcessor.isFallbackMode()) {
                    openCVProcessor.exitFallbackMode();
                }
                break;
                
            case DISPLAY_ERROR:
                // Reinitialize display manager
                initializeDisplayManager();
                break;
                
            case SYSTEM_ERROR:
                // General system recovery - restart components
                restartSystemComponents();
                break;
                
            default:
                Log.w(TAG, "No specific retry handler for: " + errorInfo.category);
                break;
        }
    }
    
    /**
     * Handle error fallback acceptance from user
     */
    private void handleErrorFallback(@NonNull ErrorHandler.ErrorInfo errorInfo) {
        Log.d(TAG, "Handling error fallback for: " + errorInfo.category);
        
        switch (errorInfo.category) {
            case OPENCV_PROCESSING:
                // Continue with unprocessed frames
                Toast.makeText(this, "Showing original camera feed", Toast.LENGTH_SHORT).show();
                break;
                
            case MEMORY_PRESSURE:
                // Accept performance degradation
                Toast.makeText(this, "Performance optimized for current conditions", Toast.LENGTH_SHORT).show();
                break;
                
            default:
                Log.d(TAG, "Fallback accepted for: " + errorInfo.category);
                break;
        }
    }
    
    /**
     * Update processing configuration based on performance level
     */
    private void updateProcessingConfiguration(@NonNull PerformanceMonitor.PerformanceLevel performanceLevel) {
        if (openCVProcessor == null) {
            return;
        }
        
        // Get processing recommendation
        PerformanceMonitor.ProcessingRecommendation recommendation = 
                performanceMonitor.getProcessingRecommendation();
        
        // Update OpenCV processor configuration
        OpenCVProcessor.ProcessingConfig config = new OpenCVProcessor.ProcessingConfig();
        config.enablePerformanceOptimization = true;
        config.maxProcessingTimeMs = recommendation.maxProcessingTimeMs;
        
        // Adjust processing mode based on performance level
        switch (performanceLevel) {
            case HIGH:
                config.mode = OpenCVProcessor.ProcessingMode.GRAYSCALE;
                break;
            case MEDIUM:
                config.mode = OpenCVProcessor.ProcessingMode.GRAYSCALE;
                break;
            case LOW:
            case CRITICAL:
                config.mode = OpenCVProcessor.ProcessingMode.PASSTHROUGH;
                break;
        }
        
        openCVProcessor.setProcessingConfig(config);
        
        Log.d(TAG, "Processing configuration updated for performance level: " + performanceLevel);
    }
    
    /**
     * Restart system components for recovery
     */
    private void restartSystemComponents() {
        Log.d(TAG, "Restarting system components for recovery");
        
        try {
            // Stop current operations
            if (cameraManager != null && cameraManager.isPreviewActive()) {
                cameraManager.stopPreview();
            }
            
            if (frameProcessor != null && frameProcessor.isProcessing()) {
                frameProcessor.stop();
            }
            
            // Wait a moment
            new android.os.Handler().postDelayed(() -> {
                // Restart components if app is still in foreground
                if (isAppInForeground) {
                    if (permissionHandler != null && permissionHandler.isCameraPermissionGranted()) {
                        initializeCameraComponents();
                    }
                    
                    if (frameProcessor != null && isOpenCVInitialized) {
                        frameProcessor.start();
                    }
                }
            }, 1000);
            
        } catch (Exception e) {
            Log.e(TAG, "Error during system component restart", e);
            if (errorHandler != null) {
                errorHandler.handleSystemError(e, "component restart");
            }
        }
    }
}