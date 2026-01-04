package com.example.opencvcamerastream;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.view.View;
import android.view.TextureView;
import android.widget.AdapterView;
import android.widget.ToggleButton;
import android.widget.Button;
import com.example.opencvcamerastream.permissions.PermissionHandler;
import com.example.opencvcamerastream.processing.OpenCVProcessor;
import com.example.opencvcamerastream.processing.FrameProcessor;
import com.example.opencvcamerastream.error.ErrorHandler;
import com.example.opencvcamerastream.error.ErrorDialogManager;
import com.example.opencvcamerastream.error.PerformanceMonitor;
import com.example.opencvcamerastream.performance.PerformanceMetricsCollector;
import com.example.opencvcamerastream.performance.PerformanceDisplayManager;
import com.example.opencvcamerastream.performance.PerformanceMeasurementManager;
import com.example.opencvcamerastream.performance.CpuUsageProfiler;
import com.example.opencvcamerastream.processing.CopyOperationTracker;
import com.example.opencvcamerastream.processing.ZeroCopyProcessor;
import com.example.opencvcamerastream.compliance.Android10ComplianceValidator;
import com.example.opencvcamerastream.compliance.Android10TestUtils;
import com.example.opencvcamerastream.processing.VisualOdometryProcessor;
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
    
    // UI Controls for camera visualization and rotation
    private ToggleButton toggleCameraVisualization;
    private Button btnRotateCamera;
    private TextureView textureView;
    private boolean isVisualizationEnabled = true;
    private int currentRotation = 0; // 0, 90, 180, 270
    
    // Visual odometry UI controls
    private android.widget.LinearLayout visualOdometryControlPanel;
    private Button btnStartCalibration;
    private Button btnStopCalibration;
    private Button btnPerformCalibration;
    private Button btnSaveCalibration;
    private Button btnLoadCalibration;
    private Button btnClearCalibration;
    private android.widget.TextView txtCalibrationStatus;
    private android.widget.TextView txtDistanceDisplay;
    private boolean isCalibrationMode = false;
    private int calibrationImageCount = 0;
    
    // Additional components referenced in the code
    private com.example.opencvcamerastream.processing.FrameProcessor frameProcessor;
    private com.example.opencvcamerastream.display.DisplayManager displayManager;
    private com.example.opencvcamerastream.camera.CameraManager cameraManager;
    
    // Error handling and performance monitoring
    private ErrorHandler errorHandler;
    private ErrorDialogManager errorDialogManager;
    private PerformanceMonitor performanceMonitor;
    private PerformanceMetricsCollector performanceMetricsCollector;
    private PerformanceDisplayManager performanceDisplayManager;
    
    // Performance measurement system (Task 25)
    private PerformanceMeasurementManager performanceMeasurementManager;
    private CopyOperationTracker copyOperationTracker;
    private ZeroCopyProcessor zeroCopyProcessor;
    private boolean isPerformanceMeasurementActive = false;
    
    // Android 10 compliance validation
    private Android10ComplianceValidator complianceValidator;

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
        
        // Initialize camera manager
        initializeCameraManager();
        
        // Initialize processing mode selection interface
        initializeProcessingModeSelection();
        
        // Initialize Android 10 compliance validation
        initializeAndroid10Compliance();
        
        // Initialize UI controls for camera visualization and rotation
        initializeUIControls();
        
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
        
        // Initialize performance measurement system (Task 25)
        initializePerformanceMeasurement();
        
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
                
                // Record performance metrics
                if (performanceMetricsCollector != null) {
                    performanceMetricsCollector.recordFrameProcessed(processingTimeMs);
                }
                
                // Display processed frame on UI thread
                runOnUiThread(() -> {
                    if (displayManager != null && displayManager.isDisplayReady()) {
                        // DisplayManager will handle the Mat internally and create its own copy if needed
                        displayManager.updateFrame(processedFrame);
                    }
                    
                    // OWNERSHIP: Release Mat after use (Task 21 - ownership transfer pattern)
                    // We received ownership from FrameProcessor, must release when done
                    processedFrame.release();
                });
                
                // OWNERSHIP: Close Image after use (ownership transfer pattern)
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
                            // Clean up Mat AFTER display update
                            originalMat.release();
                        });
                        
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
                
                // Record frame drop in performance metrics
                if (performanceMetricsCollector != null) {
                    performanceMetricsCollector.recordFrameDropped("Queue overflow");
                }
                
                droppedImage.close();
            }
        });
        
        // Set up visual odometry callback for OpenCV processor
        openCVProcessor.setProcessingCallback(new OpenCVProcessor.VisualOdometryCallback() {
            @Override
            public void onFrameProcessed(@NonNull org.opencv.core.Mat processedFrame, long processingTimeMs) {
                // This is handled by FrameProcessor callback above
            }
            
            @Override
            public void onProcessingError(@NonNull Exception error, @androidx.annotation.Nullable org.opencv.core.Mat originalFrame) {
                // This is handled by FrameProcessor callback above
            }
            
            @Override
            public void onProcessingTimeout(@NonNull org.opencv.core.Mat originalFrame, long timeoutMs) {
                // This is handled by FrameProcessor callback above
            }
            
            @Override
            public void onDistanceComputed(@NonNull VisualOdometryProcessor.DistanceResult result) {
                Log.d(TAG, "Distance computed: " + result.toString());
                
                // Update distance display on UI thread
                runOnUiThread(() -> {
                    if (txtDistanceDisplay != null && result.isValid) {
                        String distanceText = String.format(getString(R.string.distance_display),
                                result.translation.x * 100,  // Convert to cm
                                result.translation.y * 100,  // Convert to cm
                                result.translation.z * 100); // Convert to cm
                        txtDistanceDisplay.setText(distanceText);
                        txtDistanceDisplay.setVisibility(android.view.View.VISIBLE);
                    }
                });
            }
            
            @Override
            public void onInsufficientFeatures(int matchCount) {
                Log.w(TAG, "Insufficient features: " + matchCount);
                
                runOnUiThread(() -> {
                    if (txtDistanceDisplay != null) {
                        txtDistanceDisplay.setText(getString(R.string.visual_odometry_insufficient_features));
                        txtDistanceDisplay.setVisibility(android.view.View.VISIBLE);
                    }
                });
            }
            
            @Override
            public void onVisualOdometryError(@NonNull Exception error) {
                Log.e(TAG, "Visual odometry error", error);
                
                runOnUiThread(() -> {
                    if (txtDistanceDisplay != null) {
                        txtDistanceDisplay.setText(getString(R.string.visual_odometry_error));
                        txtDistanceDisplay.setVisibility(android.view.View.VISIBLE);
                    }
                    Toast.makeText(MainActivity.this, getString(R.string.visual_odometry_error), Toast.LENGTH_SHORT).show();
                });
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
     * Initialize camera manager and set up camera callbacks
     * Requirement 1.1: Access device camera
     */
    private void initializeCameraManager() {
        Log.d(TAG, "Initializing camera components");
        
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
                    // Try to add calibration image if in calibration mode
                    if (isCalibrationMode) {
                        try {
                            // Convert Image to Mat for calibration
                            org.opencv.core.Mat frameMat = com.example.opencvcamerastream.processing.OpenCVProcessor.imageToMat(frame);
                            tryAddCalibrationImage(frameMat);
                            frameMat.release();
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing calibration image", e);
                        }
                    }
                    
                    // Continue with normal frame processing
                    frameProcessor.processFrameAsync(frame);
                } else {
                    Log.v(TAG, "Frame available but processing pipeline not ready, closing frame");
                    frame.close();
                }
            }
        });
        
        Log.d(TAG, "Camera components initialized");
    }
    
    /**
     * Initialize camera when OpenCV is ready and permissions are granted
     */
    private void initializeCameraWhenReady() {
        if (cameraManager != null && isOpenCVInitialized && permissionHandler != null && 
            permissionHandler.isCameraPermissionGranted()) {
            
            Log.d(TAG, "Initializing camera - OpenCV ready and permissions granted");
            
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
        } else {
            Log.d(TAG, "Camera initialization deferred - waiting for OpenCV and permissions");
        }
    }
    
    /**
     * Attempt to reconnect camera after error or disconnection
     * Requirement 4.2: Attempt to reconnect automatically when camera becomes unavailable
     */
    private void attemptCameraReconnection() {
        Log.d(TAG, "Attempting camera reconnection");
        
        if (cameraManager != null) {
            // Use the camera manager's built-in reconnection logic instead of manual release/reinit
            cameraManager.attemptReconnection();
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
     * Initialize UI controls for camera visualization toggle and rotation
     * Requirement 6: Camera visualization control
     * Requirement 7: Camera rotation control
     */
    private void initializeUIControls() {
        Log.d(TAG, "Initializing UI controls for camera visualization and rotation");
        
        // Get references to UI controls
        toggleCameraVisualization = findViewById(R.id.toggleCameraVisualization);
        btnRotateCamera = findViewById(R.id.btnRotateCamera);
        textureView = findViewById(R.id.textureView);
        
        if (toggleCameraVisualization == null) {
            Log.e(TAG, "Camera visualization toggle button not found in layout");
            return;
        }
        
        if (btnRotateCamera == null) {
            Log.e(TAG, "Rotate camera button not found in layout");
            return;
        }
        
        // Restore saved UI state
        restoreUIState();
        
        // Set up camera visualization toggle listener
        toggleCameraVisualization.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isVisualizationEnabled = isChecked;
            onCameraVisualizationToggled(isChecked);
        });
        
        // Set up rotation button listener
        btnRotateCamera.setOnClickListener(v -> onRotateButtonClicked());
        
        Log.d(TAG, "UI controls initialized successfully");
    }
    
    /**
     * Handle camera visualization toggle
     * Requirement 6.1: Toggle camera display visibility
     * Requirement 6.2: Continue processing pipeline when visualization is disabled
     */
    private void onCameraVisualizationToggled(boolean isEnabled) {
        Log.d(TAG, "Camera visualization toggled: " + (isEnabled ? "SHOWN" : "HIDDEN"));
        
        if (textureView != null) {
            // Animate visibility change
            textureView.animate()
                    .alpha(isEnabled ? 1.0f : 0.0f)
                    .setDuration(300)
                    .start();
            
            // Update visibility after animation
            textureView.setVisibility(isEnabled ? View.VISIBLE : View.INVISIBLE);
        }
        
        // Update display manager to continue processing
        if (displayManager != null) {
            displayManager.setVisualizationEnabled(isEnabled);
        }
        
        // Update frame processor to track visualization state
        if (frameProcessor != null) {
            frameProcessor.setVisualizationEnabled(isEnabled);
        }
        
        // Save state
        saveUIState();
        
        // Show feedback
        String message = isEnabled ? "Camera display shown" : "Camera display hidden";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Handle rotation button click
     * Requirement 7.1: Rotate display 90 degrees clockwise
     * Requirement 7.2: Cycle through 0°, 90°, 180°, 270°
     */
    private void onRotateButtonClicked() {
        // Cycle to next rotation (0 -> 90 -> 180 -> 270 -> 0)
        currentRotation = (currentRotation + 90) % 360;
        
        Log.d(TAG, "Camera display rotated to " + currentRotation + " degrees");
        
        // Apply rotation to display
        if (displayManager != null) {
            displayManager.rotateDisplay(currentRotation);
        }
        
        // Save state
        saveUIState();
        
        // Show feedback
        Toast.makeText(this, "Rotated to " + currentRotation + "°", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Save UI state to SharedPreferences
     * Requirement 6.3: Save visualization state
     * Requirement 7.3: Save rotation state
     */
    private void saveUIState() {
        android.content.SharedPreferences prefs = getSharedPreferences("ui_state", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        
        editor.putBoolean("visualization_enabled", isVisualizationEnabled);
        editor.putInt("rotation_degrees", currentRotation);
        editor.apply();
        
        Log.d(TAG, "UI state saved: visualization=" + isVisualizationEnabled + ", rotation=" + currentRotation);
    }
    
    /**
     * Restore UI state from SharedPreferences
     * Requirement 6.3: Restore visualization state on app restart
     * Requirement 7.3: Restore rotation state on app restart
     */
    private void restoreUIState() {
        android.content.SharedPreferences prefs = getSharedPreferences("ui_state", MODE_PRIVATE);
        
        isVisualizationEnabled = prefs.getBoolean("visualization_enabled", true);
        currentRotation = prefs.getInt("rotation_degrees", 0);
        
        // Update UI controls to reflect saved state
        if (toggleCameraVisualization != null) {
            toggleCameraVisualization.setChecked(isVisualizationEnabled);
        }
        
        // Apply saved rotation
        if (displayManager != null) {
            displayManager.rotateDisplay(currentRotation);
        }
        
        Log.d(TAG, "UI state restored: visualization=" + isVisualizationEnabled + ", rotation=" + currentRotation);
    }
    
    /**
     * Show or hide visual odometry controls
     * Requirements: 14.6, 14.8
     */
    private void showVisualOdometryControls(boolean show) {
        // Initialize visual odometry controls if not already done
        if (visualOdometryControlPanel == null) {
            initializeVisualOdometryControls();
        }
        
        if (visualOdometryControlPanel != null) {
            visualOdometryControlPanel.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
        }
        
        // Also show/hide the status panel
        android.widget.LinearLayout statusPanel = findViewById(R.id.visualOdometryStatusPanel);
        if (statusPanel != null) {
            statusPanel.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
        }
        
        Log.d(TAG, "Visual odometry controls " + (show ? "shown" : "hidden"));
    }
    
    /**
     * Initialize visual odometry control panel
     * Requirements: 14.6, 14.8
     */
    private void initializeVisualOdometryControls() {
        try {
            // Get references to layout-defined controls
            visualOdometryControlPanel = findViewById(R.id.visualOdometryControlPanel);
            android.widget.LinearLayout statusPanel = findViewById(R.id.visualOdometryStatusPanel);
            
            // Get calibration buttons
            btnStartCalibration = findViewById(R.id.btnStartCalibration);
            btnStopCalibration = findViewById(R.id.btnStopCalibration);
            btnPerformCalibration = findViewById(R.id.btnPerformCalibration);
            btnSaveCalibration = findViewById(R.id.btnSaveCalibration);
            btnLoadCalibration = findViewById(R.id.btnLoadCalibration);
            btnClearCalibration = findViewById(R.id.btnClearCalibration);
            
            // Get status and distance display
            txtCalibrationStatus = findViewById(R.id.txtCalibrationStatus);
            txtDistanceDisplay = findViewById(R.id.txtDistanceDisplay);
            
            // Set up button listeners
            if (btnStartCalibration != null) {
                btnStartCalibration.setOnClickListener(v -> onStartCalibration());
            }
            if (btnStopCalibration != null) {
                btnStopCalibration.setOnClickListener(v -> onStopCalibration());
            }
            if (btnPerformCalibration != null) {
                btnPerformCalibration.setOnClickListener(v -> onPerformCalibration());
            }
            if (btnSaveCalibration != null) {
                btnSaveCalibration.setOnClickListener(v -> onSaveCalibration());
            }
            if (btnLoadCalibration != null) {
                btnLoadCalibration.setOnClickListener(v -> onLoadCalibration());
            }
            if (btnClearCalibration != null) {
                btnClearCalibration.setOnClickListener(v -> onClearCalibration());
            }
            
            // Set initial button states
            updateCalibrationButtonStates();
            
            Log.d(TAG, "Visual odometry controls initialized");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize visual odometry controls", e);
        }
    }
    
    /**
     * Start camera calibration mode
     * Requirements: 14.8
     */
    private void onStartCalibration() {
        isCalibrationMode = true;
        calibrationImageCount = 0;
        
        if (openCVProcessor != null && openCVProcessor.getVisualOdometryProcessor() != null) {
            openCVProcessor.getVisualOdometryProcessor().clearCalibrationImages();
        }
        
        updateCalibrationButtonStates();
        updateCalibrationStatus("Calibration started - Show checkerboard to camera");
        
        Toast.makeText(this, getString(R.string.calibration_mode_enabled), Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Camera calibration mode started");
    }
    
    /**
     * Stop camera calibration mode
     * Requirements: 14.8
     */
    private void onStopCalibration() {
        isCalibrationMode = false;
        
        updateCalibrationButtonStates();
        updateCalibrationStatus("Calibration stopped - " + calibrationImageCount + " images collected");
        
        Toast.makeText(this, getString(R.string.calibration_mode_disabled), Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Camera calibration mode stopped");
    }
    
    /**
     * Perform camera calibration using collected images
     * Requirements: 14.8
     */
    private void onPerformCalibration() {
        if (openCVProcessor == null) {
            Toast.makeText(this, "OpenCV processor not initialized", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Perform calibration in background thread
        new Thread(() -> {
            try {
                VisualOdometryProcessor.CalibrationResult result = openCVProcessor.performCameraCalibration();
                
                runOnUiThread(() -> {
                    if (result.isValid) {
                        String message = String.format(getString(R.string.calibration_completed), result.reprojectionError);
                        updateCalibrationStatus(message);
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        Log.i(TAG, "Camera calibration completed: " + result.toString());
                    } else {
                        updateCalibrationStatus(getString(R.string.calibration_failed));
                        Toast.makeText(MainActivity.this, getString(R.string.calibration_failed), Toast.LENGTH_LONG).show();
                        Log.w(TAG, "Camera calibration failed");
                    }
                    
                    updateCalibrationButtonStates();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error performing calibration", e);
                runOnUiThread(() -> {
                    updateCalibrationStatus("Calibration error: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Calibration error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    /**
     * Save calibration results to file
     * Requirements: 14.8
     */
    private void onSaveCalibration() {
        if (openCVProcessor == null) {
            Toast.makeText(this, "OpenCV processor not initialized", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Save calibration in background thread
        new Thread(() -> {
            try {
                // First perform calibration to get current results
                VisualOdometryProcessor.CalibrationResult result = openCVProcessor.performCameraCalibration();
                
                if (result.isValid) {
                    String filePath = getFilesDir().getAbsolutePath() + "/camera_calibration.yaml";
                    boolean saved = openCVProcessor.saveCalibrationToFile(filePath, result);
                    
                    runOnUiThread(() -> {
                        if (saved) {
                            updateCalibrationStatus("Calibration saved to file");
                            Toast.makeText(MainActivity.this, getString(R.string.calibration_saved), Toast.LENGTH_SHORT).show();
                            Log.i(TAG, "Calibration saved to: " + filePath);
                        } else {
                            updateCalibrationStatus("Failed to save calibration");
                            Toast.makeText(MainActivity.this, "Failed to save calibration", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        updateCalibrationStatus("No valid calibration to save");
                        Toast.makeText(MainActivity.this, "No valid calibration to save", Toast.LENGTH_SHORT).show();
                    });
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error saving calibration", e);
                runOnUiThread(() -> {
                    updateCalibrationStatus("Save error: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Save error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    /**
     * Load calibration results from file
     * Requirements: 14.8
     */
    private void onLoadCalibration() {
        if (openCVProcessor == null) {
            Toast.makeText(this, "OpenCV processor not initialized", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Load calibration in background thread
        new Thread(() -> {
            try {
                String filePath = getFilesDir().getAbsolutePath() + "/camera_calibration.yaml";
                VisualOdometryProcessor.CalibrationResult result = openCVProcessor.loadCalibrationFromFile(filePath);
                
                runOnUiThread(() -> {
                    if (result.isValid) {
                        String message = "Calibration loaded (RMS: " + String.format("%.3f", result.reprojectionError) + ")";
                        updateCalibrationStatus(message);
                        Toast.makeText(MainActivity.this, getString(R.string.calibration_loaded), Toast.LENGTH_SHORT).show();
                        Log.i(TAG, "Calibration loaded from: " + filePath);
                    } else {
                        updateCalibrationStatus("Failed to load calibration");
                        Toast.makeText(MainActivity.this, "Failed to load calibration", Toast.LENGTH_SHORT).show();
                    }
                    
                    updateCalibrationButtonStates();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error loading calibration", e);
                runOnUiThread(() -> {
                    updateCalibrationStatus("Load error: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Load error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    /**
     * Clear collected calibration images
     * Requirements: 14.8
     */
    private void onClearCalibration() {
        if (openCVProcessor != null && openCVProcessor.getVisualOdometryProcessor() != null) {
            openCVProcessor.getVisualOdometryProcessor().clearCalibrationImages();
        }
        
        calibrationImageCount = 0;
        updateCalibrationStatus("Calibration images cleared");
        updateCalibrationButtonStates();
        
        Toast.makeText(this, "Calibration images cleared", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Calibration images cleared");
    }
    
    /**
     * Update calibration button states based on current mode
     */
    private void updateCalibrationButtonStates() {
        if (btnStartCalibration != null) {
            btnStartCalibration.setEnabled(!isCalibrationMode);
        }
        if (btnStopCalibration != null) {
            btnStopCalibration.setEnabled(isCalibrationMode);
        }
        if (btnPerformCalibration != null) {
            btnPerformCalibration.setEnabled(!isCalibrationMode && calibrationImageCount >= 3);
        }
        if (btnSaveCalibration != null) {
            btnSaveCalibration.setEnabled(!isCalibrationMode);
        }
        if (btnLoadCalibration != null) {
            btnLoadCalibration.setEnabled(!isCalibrationMode);
        }
        if (btnClearCalibration != null) {
            btnClearCalibration.setEnabled(!isCalibrationMode);
        }
    }
    
    /**
     * Update calibration status display
     */
    private void updateCalibrationStatus(String status) {
        if (txtCalibrationStatus != null) {
            txtCalibrationStatus.setText(status);
        }
        Log.d(TAG, "Calibration status: " + status);
    }
    
    /**
     * Add calibration image when in calibration mode
     * This method should be called from the camera frame callback
     * Requirements: 14.8
     */
    private void tryAddCalibrationImage(org.opencv.core.Mat frame) {
        if (!isCalibrationMode || openCVProcessor == null) {
            return;
        }
        
        // Add calibration image in background thread to avoid blocking camera
        new Thread(() -> {
            try {
                boolean added = openCVProcessor.addCalibrationImage(frame);
                
                runOnUiThread(() -> {
                    if (added) {
                        calibrationImageCount++;
                        String message = String.format(getString(R.string.calibration_image_added), 
                                calibrationImageCount, 10); // Target 10 images
                        updateCalibrationStatus(message);
                        updateCalibrationButtonStates();
                        
                        // Auto-stop calibration after collecting enough images
                        if (calibrationImageCount >= 10) {
                            onStopCalibration();
                        }
                    } else {
                        updateCalibrationStatus(getString(R.string.calibration_image_rejected));
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error adding calibration image", e);
            }
        }).start();
    }
    
    /**
     * Initialize Android 10 compliance validation
     * Validates all Android 10 requirements: 6.1, 6.2, 6.3, 6.4
     */
    private void initializeAndroid10Compliance() {
        Log.d(TAG, "Initializing Android 10 compliance validation");
        
        complianceValidator = new Android10ComplianceValidator(this);
        
        // Run initial compliance validation
        Android10ComplianceValidator.ComplianceResult result = complianceValidator.validateCompliance();
        
        Log.i(TAG, "Android 10 compliance validation result: " + result.summary);
        
        if (!result.isCompliant) {
            Log.w(TAG, "Android 10 compliance issues found:");
            for (Android10ComplianceValidator.ComplianceIssue issue : result.issues) {
                Log.w(TAG, "  " + issue.toString());
            }
            
            // Show compliance issues to user if critical
            long criticalIssues = result.issues.stream()
                    .filter(issue -> issue.severity == Android10ComplianceValidator.ComplianceIssue.Severity.CRITICAL)
                    .count();
            
            if (criticalIssues > 0) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Android 10 compliance issues detected. Check logs for details.", 
                            Toast.LENGTH_LONG).show();
                });
            }
        } else {
            Log.i(TAG, "Android 10 compliance validation passed");
        }
        
        // Validate scoped storage compliance specifically
        boolean temporaryFileCompliance = complianceValidator.validateTemporaryFileOperations();
        Log.i(TAG, "Scoped storage compliance (temporary files): " + 
                (temporaryFileCompliance ? "PASSED" : "FAILED"));
        
        // Test camera privacy controls
        boolean cameraPrivacyCompliance = complianceValidator.testCameraPrivacyControls(this);
        Log.i(TAG, "Camera privacy controls compliance: " + 
                (cameraPrivacyCompliance ? "PASSED" : "FAILED"));
        
        Log.d(TAG, "Android 10 compliance validation initialized");
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
            case 7: // Visual Odometry
                selectedMode = OpenCVProcessor.ProcessingMode.VISUAL_ODOMETRY;
                modeName = "Visual Odometry";
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
            case VISUAL_ODOMETRY:
                config.enableVisualOdometry = true;
                config.enableDistanceDisplay = true;
                config.enableCalibrationMode = false;
                // Set default calibration file path
                config.calibrationFilePath = getFilesDir().getAbsolutePath() + "/camera_calibration.yaml";
                break;
        }
        
        openCVProcessor.setProcessingConfig(config);
        
        // Enable/disable visual odometry based on mode
        if (selectedMode == OpenCVProcessor.ProcessingMode.VISUAL_ODOMETRY) {
            openCVProcessor.setVisualOdometryEnabled(true);
            showVisualOdometryControls(true);
            Toast.makeText(this, getString(R.string.visual_odometry_enabled), Toast.LENGTH_SHORT).show();
        } else {
            openCVProcessor.setVisualOdometryEnabled(false);
            showVisualOdometryControls(false);
        }
        
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
                        
                        // Start performance monitoring
                        if (performanceMetricsCollector != null) {
                            performanceMetricsCollector.startMonitoring();
                            Log.d(TAG, "Performance monitoring started");
                        }
                        
                        // Show performance overlay by default for debugging
                        if (performanceDisplayManager != null) {
                            performanceDisplayManager.showPerformanceOverlay();
                            Log.d(TAG, "Performance overlay shown");
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
            Toast.makeText(this, "OpenCV libraries not found. Camera will work without processing.\n" +
                    "To enable processing, please run the OpenCV setup script.", 
                    Toast.LENGTH_LONG).show();
        });
        
        Log.w(TAG, "OpenCV initialization failed, app will continue with limited functionality");
        Log.i(TAG, "To fix this issue, run: ./scripts/setup-opencv.sh or ./scripts/fix-opencv-immediate.sh");
        
        // Continue with camera initialization even without OpenCV
        if (permissionHandler != null && permissionHandler.isCameraPermissionGranted()) {
            initializeCameraComponents();
        }
        
        // Show performance overlay even without OpenCV for basic camera stats
        if (performanceDisplayManager != null) {
            performanceDisplayManager.showPerformanceOverlay();
            performanceDisplayManager.reset(); // Show empty stats initially
            Log.d(TAG, "Performance overlay shown without OpenCV processing");
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        isAppInForeground = true;
        
        Log.d(TAG, "Activity resumed, initializing OpenCV and checking camera permissions");
        
        // Initialize OpenCV with system library pre-loading
        initializeOpenCVWithSystemLibraries();
        
        // Check and request camera permission when app comes to foreground
        // This handles Android 10 background activity restrictions
        if (permissionHandler != null) {
            permissionHandler.requestCameraPermission();
        }
        
        // Run Android 10 compliance tests on resume
        runAndroid10ComplianceTests();
        
        // Restart frame processor if OpenCV is initialized
        if (isOpenCVInitialized && frameProcessor != null && !frameProcessor.isProcessing()) {
            Log.d(TAG, "Restarting frame processor on resume");
            frameProcessor.start();
        }
        
        // Restart performance monitoring if not already running
        if (performanceMetricsCollector != null && isOpenCVInitialized) {
            performanceMetricsCollector.startMonitoring();
            Log.d(TAG, "Performance monitoring restarted on resume");
        }
        
        // Update performance display manager lifecycle
        if (performanceDisplayManager != null) {
            performanceDisplayManager.onResume();
        }
        
        // Restart camera preview if it was stopped and we have permissions
        if (cameraManager != null && cameraManager.isInitialized() && 
            !cameraManager.isPreviewActive() && permissionHandler != null && 
            permissionHandler.isCameraPermissionGranted()) {
            Log.d(TAG, "Restarting camera preview on resume");
            // Use a small delay to avoid race conditions with camera lifecycle
            new android.os.Handler().postDelayed(() -> {
                if (isAppInForeground && cameraManager != null && cameraManager.isInitialized() && 
                    !cameraManager.isPreviewActive()) {
                    cameraManager.startPreview();
                }
            }, 100);
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
        
        // Stop performance monitoring to save resources
        if (performanceMetricsCollector != null) {
            performanceMetricsCollector.stopMonitoring();
            Log.d(TAG, "Performance monitoring stopped due to app backgrounding");
        }
        
        // Update performance display manager lifecycle
        if (performanceDisplayManager != null) {
            performanceDisplayManager.onPause();
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
        
        // Test camera privacy controls after permission granted (Requirement 6.2)
        testCameraPrivacyControls();
        
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
                
                // Start performance monitoring when camera preview starts
                if (performanceMetricsCollector != null && isOpenCVInitialized) {
                    performanceMetricsCollector.startMonitoring();
                    Log.d(TAG, "Performance monitoring started with camera preview");
                }
                
                // Show performance overlay for debugging
                if (performanceDisplayManager != null) {
                    performanceDisplayManager.showPerformanceOverlay();
                    Log.d(TAG, "Performance overlay shown with camera preview");
                }
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
                        ErrorHandler.ErrorCategory.CAMERA_PERMISSION,
                        ErrorHandler.ErrorSeverity.HIGH,
                        "Camera permission permanently denied",
                        "Please enable camera permission in Settings to use this app",
                        null,
                        ErrorHandler.RecoveryStrategy.USER_INTERVENTION
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
    protected void onDestroy() {
        super.onDestroy();
        
        Log.d(TAG, "Activity destroyed - performing comprehensive resource cleanup");
        
        // Stop all monitoring and processing first
        if (performanceMetricsCollector != null) {
            performanceMetricsCollector.stopMonitoring();
            performanceMetricsCollector.release();
            performanceMetricsCollector = null;
            Log.d(TAG, "Performance metrics collector released");
        }
        
        // Release performance display manager
        if (performanceDisplayManager != null) {
            performanceDisplayManager.release();
            performanceDisplayManager = null;
            Log.d(TAG, "Performance display manager released");
        }
        
        // Stop and release frame processor with proper thread cleanup
        if (frameProcessor != null) {
            if (frameProcessor.isProcessing()) {
                frameProcessor.stop();
            }
            frameProcessor.stop(); // Use stop() method instead of release()
            frameProcessor = null;
            Log.d(TAG, "Frame processor released with thread cleanup");
        }
        
        // Release camera resources completely with proper thread cleanup
        if (cameraManager != null) {
            cameraManager.release();
            cameraManager = null;
            Log.d(TAG, "Camera manager released with thread cleanup");
        }
        
        // Release OpenCV processor resources and cleanup native memory
        if (openCVProcessor != null) {
            openCVProcessor.release();
            openCVProcessor = null;
            isOpenCVInitialized = false;
            Log.d(TAG, "OpenCV processor released with native memory cleanup");
        }
        
        // Release display manager resources and surface cleanup
        if (displayManager != null) {
            displayManager.release();
            displayManager = null;
            Log.d(TAG, "Display manager released with surface cleanup");
        }
        
        // Release performance measurement system (Task 25)
        if (performanceMeasurementManager != null) {
            performanceMeasurementManager.release();
            performanceMeasurementManager = null;
            Log.d(TAG, "Performance measurement manager released");
        }
        
        if (copyOperationTracker != null) {
            copyOperationTracker.resetMetrics();
            copyOperationTracker = null;
            Log.d(TAG, "Copy operation tracker released");
        }
        
        if (zeroCopyProcessor != null) {
            zeroCopyProcessor.release();
            zeroCopyProcessor = null;
            Log.d(TAG, "Zero-copy processor released");
        }
        
        // Release error handling resources
        if (errorDialogManager != null) {
            errorDialogManager.release();
            errorDialogManager = null;
            Log.d(TAG, "Error dialog manager released");
        }
        
        if (errorHandler != null) {
            errorHandler.release();
            errorHandler = null;
            Log.d(TAG, "Error handler released");
        }
        
        if (performanceMonitor != null) {
            performanceMonitor.release();
            performanceMonitor = null;
            Log.d(TAG, "Performance monitor released");
        }
        
        // Clear permission handler
        if (permissionHandler != null) {
            permissionHandler.release();
            permissionHandler = null;
            Log.d(TAG, "Permission handler released");
        }
        
        // Force garbage collection to help with memory cleanup
        System.gc();
        
        Log.i(TAG, "All resources released - comprehensive cleanup completed");
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
    
    /**
     * Run comprehensive Android 10 compliance tests
     * Tests all requirements: 6.1, 6.2, 6.3, 6.4
     */
    private void runAndroid10ComplianceTests() {
        if (complianceValidator == null) {
            Log.w(TAG, "Compliance validator not initialized, skipping tests");
            return;
        }
        
        Log.d(TAG, "Running Android 10 compliance tests");
        
        // Run comprehensive tests using test utils
        java.util.List<Android10TestUtils.TestResult> testResults = 
                Android10TestUtils.runComprehensiveTests(this);
        
        // Log test results
        for (Android10TestUtils.TestResult result : testResults) {
            if (result.passed) {
                Log.i(TAG, "Android 10 compliance test PASSED: " + result.message + 
                        " (Requirement: " + result.requirement + ")");
            } else {
                Log.w(TAG, "Android 10 compliance test FAILED: " + result.message + 
                        " (Requirement: " + result.requirement + ")");
            }
        }
        
        // Generate and log compliance report
        String complianceReport = Android10TestUtils.generateComplianceReport(this);
        Log.i(TAG, "Android 10 Compliance Report:\n" + complianceReport);
        
        // Check if all tests passed
        boolean allTestsPassed = testResults.stream().allMatch(result -> result.passed);
        
        if (allTestsPassed) {
            Log.i(TAG, "All Android 10 compliance tests passed");
        } else {
            Log.w(TAG, "Some Android 10 compliance tests failed - check logs for details");
            
            // Count failed tests by requirement
            java.util.Map<String, Long> failuresByRequirement = testResults.stream()
                    .filter(result -> !result.passed)
                    .collect(java.util.stream.Collectors.groupingBy(
                            result -> result.requirement,
                            java.util.stream.Collectors.counting()));
            
            for (java.util.Map.Entry<String, Long> entry : failuresByRequirement.entrySet()) {
                Log.w(TAG, "Requirement " + entry.getKey() + " has " + entry.getValue() + " failed test(s)");
            }
        }
    }
    
    /**
     * Validate Android 10 scoped storage compliance for temporary file operations
     * Requirement 6.1: Validate scoped storage compliance
     */
    private void validateScopedStorageCompliance() {
        if (complianceValidator == null) {
            Log.w(TAG, "Compliance validator not initialized");
            return;
        }
        
        boolean isCompliant = complianceValidator.validateTemporaryFileOperations();
        
        if (isCompliant) {
            Log.i(TAG, "Scoped storage compliance validated - using app-specific directories");
        } else {
            Log.w(TAG, "Scoped storage compliance issue - temporary file operations may not be compliant");
        }
    }
    
    /**
     * Test camera privacy controls and permission flows for Android 10
     * Requirement 6.2: Test camera privacy controls and permission flows
     */
    private void testCameraPrivacyControls() {
        if (complianceValidator == null) {
            Log.w(TAG, "Compliance validator not initialized");
            return;
        }
        
        boolean isCompliant = complianceValidator.testCameraPrivacyControls(this);
        
        if (isCompliant) {
            Log.i(TAG, "Camera privacy controls compliance validated");
        } else {
            Log.w(TAG, "Camera privacy controls compliance issue detected");
        }
        
        // Also test using test utils for more detailed validation
        Android10TestUtils.TestResult result = Android10TestUtils.testCameraPrivacyControls(this);
        Log.i(TAG, "Camera privacy controls test result: " + result.toString());
    }
    
    /**
     * Verify background activity restrictions are properly handled
     * Requirement 6.3: Verify background activity restrictions
     */
    private void verifyBackgroundActivityRestrictions() {
        Log.d(TAG, "Verifying background activity restrictions compliance");
        
        // Test background activity restrictions using test utils
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            Android10TestUtils.TestResult result = 
                    Android10TestUtils.testBackgroundActivityRestrictions(this);
            Log.i(TAG, "Background activity restrictions test result: " + result.toString());
        } else {
            Log.i(TAG, "Background activity restrictions not required for API < 29");
        }
        
        // Verify that camera operations are properly managed during background transitions
        if (cameraManager != null) {
            boolean isPreviewActive = cameraManager.isPreviewActive();
            boolean isAppInForeground = this.isAppInForeground;
            
            if (!isAppInForeground && isPreviewActive) {
                Log.w(TAG, "Potential background activity restriction violation: " +
                        "camera preview active while app in background");
            } else {
                Log.i(TAG, "Background activity restrictions properly enforced for camera");
            }
        }
    }
    
    /**
     * Implement Android 10 enhanced location and camera privacy control tests
     * Requirement 6.4: Enhanced location and camera privacy control tests
     */
    private void testEnhancedPrivacyControls() {
        Log.d(TAG, "Testing Android 10 enhanced privacy controls");
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            Android10TestUtils.TestResult result = 
                    Android10TestUtils.testEnhancedPrivacyControls(this);
            Log.i(TAG, "Enhanced privacy controls test result: " + result.toString());
            
            // Test privacy messaging integration
            if (permissionHandler != null) {
                try {
                    permissionHandler.showAndroid10PrivacyNotice();
                    Log.i(TAG, "Android 10 privacy notice integration working correctly");
                } catch (Exception e) {
                    Log.w(TAG, "Android 10 privacy notice integration issue", e);
                }
            }
        } else {
            Log.i(TAG, "Enhanced privacy controls not required for API < 29");
        }
    }
    
    /**
     * Initialize performance measurement system (Task 25)
     * Sets up comprehensive CPU usage profiling and optimization validation
     */
    private void initializePerformanceMeasurement() {
        Log.d(TAG, "Initializing performance measurement system for Task 25");
        
        try {
            // Initialize copy operation tracker
            copyOperationTracker = new CopyOperationTracker();
            
            // Initialize zero-copy processor (will be set up when OpenCV is ready)
            // zeroCopyProcessor will be initialized in OpenCV callback
            
            // Initialize performance measurement manager
            performanceMeasurementManager = new PerformanceMeasurementManager(this);
            performanceMeasurementManager.setCallback(new PerformanceMeasurementManager.PerformanceMeasurementCallback() {
                @Override
                public void onMeasurementPhaseChanged(@NonNull PerformanceMeasurementManager.MeasurementPhase phase) {
                    Log.i(TAG, "Performance measurement phase changed to: " + phase.getDescription());
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, 
                                "Performance measurement: " + phase.getDescription(), 
                                Toast.LENGTH_SHORT).show();
                    });
                }
                
                @Override
                public void onMeasurementUpdate(@NonNull String update) {
                    Log.d(TAG, "Performance measurement update: " + update);
                    // Update performance display if available
                    if (performanceDisplayManager != null) {
                        // performanceDisplayManager.showMeasurementUpdate(update); // Method not implemented yet
                    }
                }
                
                @Override
                public void onMeasurementCompleted(@NonNull PerformanceMeasurementManager.PerformanceMeasurementResults results) {
                    Log.i(TAG, "Performance measurement completed: " + results);
                    
                    runOnUiThread(() -> {
                        String message = results.allTargetsMet ? 
                                "✓ All optimization targets met!" : 
                                "⚠ Some optimization targets not met";
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    });
                    
                    // Show detailed results in performance display
                    if (performanceDisplayManager != null) {
                        // performanceDisplayManager.showMeasurementResults(results); // Method not implemented yet
                    }
                }
                
                @Override
                public void onMeasurementError(@NonNull String error, @androidx.annotation.Nullable Exception exception) {
                    Log.e(TAG, "Performance measurement error: " + error, exception);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, 
                                "Performance measurement error: " + error, 
                                Toast.LENGTH_LONG).show();
                    });
                }
            });
            
            Log.i(TAG, "Performance measurement system initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize performance measurement system", e);
            if (errorHandler != null) {
                errorHandler.handleSystemError(e, "performance measurement initialization");
            }
        }
    }
    
    /**
     * Start baseline performance measurement (before optimization)
     * Call this method to begin measuring performance before applying optimizations
     */
    public void startBaselinePerformanceMeasurement() {
        if (performanceMeasurementManager != null) {
            Log.i(TAG, "Starting baseline performance measurement");
            performanceMeasurementManager.startBaselineMeasurement();
        } else {
            Log.w(TAG, "Performance measurement manager not initialized");
        }
    }
    
    /**
     * Complete baseline measurement and start optimized measurement
     * Call this method after applying optimizations to measure improved performance
     */
    public void startOptimizedPerformanceMeasurement() {
        if (performanceMeasurementManager != null) {
            Log.i(TAG, "Completing baseline and starting optimized performance measurement");
            performanceMeasurementManager.completeBaselineMeasurement();
            
            // Set up components for optimized measurement
            if (copyOperationTracker != null && zeroCopyProcessor != null && frameProcessor != null) {
                performanceMeasurementManager.setComponents(copyOperationTracker, zeroCopyProcessor, frameProcessor);
            }
            
            performanceMeasurementManager.startOptimizedMeasurement();
        } else {
            Log.w(TAG, "Performance measurement manager not initialized");
        }
    }
    
    /**
     * Complete optimized measurement and generate validation report
     * Call this method to finalize performance measurement and generate the report
     */
    public void completePerformanceMeasurement() {
        if (performanceMeasurementManager != null) {
            Log.i(TAG, "Completing optimized performance measurement");
            performanceMeasurementManager.completeOptimizedMeasurement();
        } else {
            Log.w(TAG, "Performance measurement manager not initialized");
        }
    }
    
    /**
     * Check if performance measurement is currently active
     */
    public boolean isPerformanceMeasurementActive() {
        return performanceMeasurementManager != null && performanceMeasurementManager.isMeasuring();
    }
    
    /**
     * Get current performance measurement phase
     */
    @androidx.annotation.Nullable
    public PerformanceMeasurementManager.MeasurementPhase getCurrentMeasurementPhase() {
        return performanceMeasurementManager != null ? 
                performanceMeasurementManager.getCurrentPhase() : null;
    }

    /**
     * Initialize OpenCV with proper libc++_shared.so handling
     * This method ensures OpenCV can find the required dynamic C++ standard library
     */
    private void initializeOpenCVWithSystemLibraries() {
        Log.d(TAG, "Initializing OpenCV with dynamic C++ standard library linking");
        
        try {
            // Log library environment for debugging
            logLibraryEnvironment();
            
            // Diagnose available libraries
            diagnoseAvailableLibraries();
            
            // Explicitly try to load OpenCV libraries in correct order
            boolean openCVInitialized = false;
            
            // First, try to manually load the OpenCV library with dependencies
            try {
                Log.d(TAG, "Attempting manual OpenCV library loading...");
                
                // Try to load libc++_shared.so first
                try {
                    System.loadLibrary("c++_shared");
                    Log.d(TAG, "✅ Manually loaded libc++_shared.so");
                } catch (UnsatisfiedLinkError e) {
                    Log.w(TAG, "❌ Failed to manually load libc++_shared.so: " + e.getMessage());
                    Log.w(TAG, "This confirms libc++_shared.so is NOT included in the APK");
                    
                    // Try alternative library names
                    String[] alternativeNames = {"c++_shared", "stdc++", "gnustl_shared"};
                    boolean loaded = false;
                    for (String altName : alternativeNames) {
                        try {
                            System.loadLibrary(altName);
                            Log.d(TAG, "✅ Successfully loaded alternative library: " + altName);
                            loaded = true;
                            break;
                        } catch (UnsatisfiedLinkError altE) {
                            Log.d(TAG, "Alternative " + altName + " also failed: " + altE.getMessage());
                        }
                    }
                    
                    if (!loaded) {
                        Log.e(TAG, "❌ No C++ standard library could be loaded - trying asset extraction");
                        
                        // Try to extract and load from assets as last resort
                        if (extractAndLoadLibraryFromAssets("libc++_shared.so")) {
                            Log.i(TAG, "✅ Successfully extracted and loaded libc++_shared.so from assets");
                        } else {
                            Log.e(TAG, "❌ Asset extraction also failed - OpenCV will definitely fail");
                        }
                    }
                }
                
                // Try to load OpenCV library directly
                try {
                    System.loadLibrary("opencv_java4");
                    Log.d(TAG, "✅ Manually loaded libopencv_java4.so");
                    
                    // If manual loading succeeded, try OpenCV initialization
                    if (OpenCVLoader.initDebug()) {
                        Log.d(TAG, "OpenCV initialized successfully after manual library loading");
                        openCVInitialized = true;
                        handleOpenCVInitializationSuccess();
                    }
                } catch (UnsatisfiedLinkError e) {
                    Log.w(TAG, "❌ Failed to manually load libopencv_java4.so: " + e.getMessage());
                }
                
            } catch (Exception e) {
                Log.w(TAG, "Manual library loading failed: " + e.getMessage());
            }
            
            // If manual loading didn't work, try standard OpenCV initialization
            if (!openCVInitialized) {
                try {
                    // Try static initialization first (uses bundled libraries)
                    if (OpenCVLoader.initDebug()) {
                        Log.d(TAG, "OpenCV initialized successfully with static loading");
                        openCVInitialized = true;
                        handleOpenCVInitializationSuccess();
                    } else {
                        Log.d(TAG, "Static OpenCV initialization failed, trying async initialization");
                    }
                } catch (UnsatisfiedLinkError e) {
                    Log.w(TAG, "Static OpenCV initialization failed with UnsatisfiedLinkError: " + e.getMessage());
                    Log.i(TAG, "This suggests the OpenCV libraries are not properly included in the APK");
                } catch (Exception e) {
                    Log.w(TAG, "Static OpenCV initialization failed with exception: " + e.getMessage());
                }
            }
            
            // If static initialization failed, try async initialization (uses OpenCV Manager)
            if (!openCVInitialized) {
                Log.d(TAG, "Attempting async OpenCV initialization via OpenCV Manager");
                try {
                    OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, new BaseLoaderCallback(this) {
                        @Override
                        public void onManagerConnected(int status) {
                            switch (status) {
                                case LoaderCallbackInterface.SUCCESS:
                                    Log.d(TAG, "OpenCV loaded successfully via OpenCV Manager");
                                    handleOpenCVInitializationSuccess();
                                    break;
                                case LoaderCallbackInterface.INIT_FAILED:
                                    Log.e(TAG, "OpenCV initialization failed via OpenCV Manager");
                                    handleOpenCVInitializationFailure();
                                    break;
                                case LoaderCallbackInterface.INSTALL_CANCELED:
                                    Log.w(TAG, "OpenCV Manager installation was canceled");
                                    handleOpenCVInitializationFailure();
                                    break;
                                case LoaderCallbackInterface.INCOMPATIBLE_MANAGER_VERSION:
                                    Log.e(TAG, "Incompatible OpenCV Manager version");
                                    handleOpenCVInitializationFailure();
                                    break;
                                case LoaderCallbackInterface.MARKET_ERROR:
                                    Log.e(TAG, "Google Play Market error during OpenCV Manager installation");
                                    handleOpenCVInitializationFailure();
                                    break;
                                default:
                                    Log.e(TAG, "Unknown OpenCV Manager status: " + status);
                                    handleOpenCVInitializationFailure();
                                    break;
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Async OpenCV initialization also failed: " + e.getMessage());
                    handleOpenCVInitializationFailure();
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Critical error during OpenCV initialization: " + e.getMessage());
            handleOpenCVInitializationFailure();
        }
    }
    
    /**
     * Handle successful OpenCV initialization
     */
    private void handleOpenCVInitializationSuccess() {
        Log.i(TAG, "🎉 OpenCV initialization completed successfully!");
        Log.i(TAG, "✅ OpenCV is now ready for image processing");
        isOpenCVInitialized = true;
        
        // Test OpenCV functionality
        try {
            // Try to create a simple OpenCV Mat to verify it's working
            org.opencv.core.Mat testMat = new org.opencv.core.Mat(100, 100, org.opencv.core.CvType.CV_8UC3);
            Log.i(TAG, "✅ OpenCV Mat creation test successful - OpenCV is fully functional!");
            Log.i(TAG, "✅ OpenCV version: " + org.opencv.core.Core.VERSION);
            testMat.release();
        } catch (Exception e) {
            Log.w(TAG, "⚠️ OpenCV Mat test failed, but initialization reported success: " + e.getMessage());
        }
        
        // Initialize OpenCV processor now that OpenCV is ready
        if (openCVProcessor != null) {
            openCVProcessor.initialize();
            Log.d(TAG, "✅ OpenCV processor initialized");
            
            // Initialize zero-copy processor for performance measurement (Task 25)
            if (zeroCopyProcessor == null) {
                zeroCopyProcessor = new ZeroCopyProcessor(openCVProcessor);
                if (zeroCopyProcessor.initialize()) {
                    Log.d(TAG, "✅ Zero-copy processor initialized for performance measurement");
                    
                    // Set up performance measurement components
                    if (performanceMeasurementManager != null && copyOperationTracker != null && frameProcessor != null) {
                        performanceMeasurementManager.setComponents(copyOperationTracker, zeroCopyProcessor, frameProcessor);
                        Log.d(TAG, "✅ Performance measurement components configured");
                    }
                } else {
                    Log.w(TAG, "⚠️ Zero-copy processor initialization failed");
                }
            }
        }
        
        // Start frame processing if camera is ready
        if (frameProcessor != null && cameraManager != null && cameraManager.isInitialized()) {
            frameProcessor.start();
            Log.d(TAG, "✅ Frame processor started");
        }
        
        runOnUiThread(() -> {
            Toast.makeText(this, "🎉 OpenCV ready - camera processing enabled!", Toast.LENGTH_LONG).show();
        });
    }
    

    
    /**
     * Pre-load system libraries that OpenCV depends on
     */
    private void preloadSystemLibraries() {
        Log.d(TAG, "Pre-loading system libraries for OpenCV");
        
        // List of libraries to try pre-loading
        String[] systemLibraries = {
            "c++_shared",  // libc++_shared.so (required by OpenCV)
            "log",         // liblog.so (Android logging)
            "z",           // libz.so (compression)
            "dl"           // libdl.so (dynamic loading)
        };
        
        for (String libName : systemLibraries) {
            try {
                System.loadLibrary(libName);
                Log.d(TAG, "Successfully pre-loaded system library: " + libName);
                
                // Special handling for libc++_shared
                if ("c++_shared".equals(libName)) {
                    Log.i(TAG, "✅ libc++_shared.so loaded successfully - OpenCV should work");
                }
            } catch (UnsatisfiedLinkError e) {
                Log.d(TAG, "Could not pre-load system library " + libName + ": " + e.getMessage());
                
                // Special handling for libc++_shared failure
                if ("c++_shared".equals(libName)) {
                    Log.w(TAG, "❌ Failed to load libc++_shared.so - OpenCV will likely fail");
                    Log.w(TAG, "Error details: " + e.getMessage());
                    Log.w(TAG, "This suggests libc++_shared.so is not properly included in the APK");
                }
            } catch (Exception e) {
                Log.w(TAG, "Unexpected error pre-loading " + libName + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Log library environment for debugging
     */
    private void logLibraryEnvironment() {
        Log.d(TAG, "=== NATIVE LIBRARY ENVIRONMENT ===");
        Log.d(TAG, "Using shared C++ standard library linking (ANDROID_STL=c++_shared)");
        Log.d(TAG, "java.library.path: " + System.getProperty("java.library.path"));
        
        // Test if we can load libc++_shared.so
        try {
            System.loadLibrary("c++_shared");
            Log.d(TAG, "libc++_shared.so: SUCCESSFULLY LOADED");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "libc++_shared.so: FAILED TO LOAD - " + e.getMessage());
            Log.d(TAG, "This may cause OpenCV loading to fail");
        }
    }
    
    /**
     * Diagnose what native libraries are available in the APK
     */
    private void diagnoseAvailableLibraries() {
        Log.d(TAG, "=== DIAGNOSING AVAILABLE NATIVE LIBRARIES ===");
        
        // Get the application's native library directory
        String nativeLibraryDir = getApplicationInfo().nativeLibraryDir;
        Log.d(TAG, "Native library directory: " + nativeLibraryDir);
        
        // Try to list what's actually available
        try {
            java.io.File libDir = new java.io.File(nativeLibraryDir);
            if (libDir.exists() && libDir.isDirectory()) {
                java.io.File[] files = libDir.listFiles();
                if (files != null) {
                    Log.d(TAG, "Available native libraries in APK:");
                    for (java.io.File file : files) {
                        if (file.getName().endsWith(".so")) {
                            Log.d(TAG, "  - " + file.getName() + " (" + file.length() + " bytes)");
                        }
                    }
                } else {
                    Log.w(TAG, "No files found in native library directory");
                }
            } else {
                Log.w(TAG, "Native library directory does not exist or is not a directory");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to list native libraries: " + e.getMessage());
        }
        
        // Test specific libraries we expect
        String[] expectedLibraries = {"c++_shared", "opencv_java4", "opencv_java3"};
        for (String libName : expectedLibraries) {
            try {
                System.loadLibrary(libName);
                Log.d(TAG, "✅ Library " + libName + " is available and loadable");
            } catch (UnsatisfiedLinkError e) {
                Log.w(TAG, "❌ Library " + libName + " is NOT available: " + e.getMessage());
            }
        }
    }
    
    /**
     * Extract and load a native library from assets as a fallback
     */
    private boolean extractAndLoadLibraryFromAssets(String libraryFileName) {
        try {
            Log.d(TAG, "Attempting to extract " + libraryFileName + " from assets");
            
            // Determine the correct architecture
            String arch = System.getProperty("os.arch");
            String abiDir;
            if (arch != null && arch.contains("aarch64")) {
                abiDir = "arm64-v8a";
            } else if (arch != null && arch.contains("arm")) {
                abiDir = "armeabi-v7a";
            } else if (arch != null && arch.contains("x86_64")) {
                abiDir = "x86_64";
            } else {
                abiDir = "x86";
            }
            
            String assetPath = "native_libs/" + abiDir + "/" + libraryFileName;
            Log.d(TAG, "Looking for asset: " + assetPath);
            
            // Check if the asset exists
            try {
                java.io.InputStream inputStream = getAssets().open(assetPath);
                inputStream.close();
                Log.d(TAG, "✅ Found " + libraryFileName + " in assets");
            } catch (java.io.IOException e) {
                Log.w(TAG, "❌ " + libraryFileName + " not found in assets: " + e.getMessage());
                return false;
            }
            
            // Extract to internal storage
            java.io.File internalDir = new java.io.File(getFilesDir(), "native_libs");
            if (!internalDir.exists()) {
                internalDir.mkdirs();
            }
            
            java.io.File extractedLib = new java.io.File(internalDir, libraryFileName);
            
            // Extract the library
            try (java.io.InputStream inputStream = getAssets().open(assetPath);
                 java.io.FileOutputStream outputStream = new java.io.FileOutputStream(extractedLib)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                Log.d(TAG, "✅ Extracted " + libraryFileName + " to " + extractedLib.getAbsolutePath());
            }
            
            // Try to load the extracted library
            System.load(extractedLib.getAbsolutePath());
            Log.d(TAG, "✅ Successfully loaded " + libraryFileName + " from extracted file");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to extract and load " + libraryFileName + " from assets: " + e.getMessage());
            return false;
        }
    }
}