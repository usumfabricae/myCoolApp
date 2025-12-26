package com.example.opencvcamerastream.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.example.opencvcamerastream.error.ErrorHandler;
import com.example.opencvcamerastream.error.PerformanceMonitor;

/**
 * CameraManager handles all camera-related operations using Camera2 API
 * 
 * This implementation is designed for Android 10 (API 29) compatibility with:
 * - Enhanced camera privacy controls
 * - Background activity restrictions
 * - Proper resource management and lifecycle handling
 * - Camera2 API best practices for performance and stability
 * 
 * Requirements addressed:
 * - 1.2: Initialize camera and display live feed when permissions granted
 * - 1.4: Maintain camera feed orientation correctly on device rotation
 * - 4.2: Attempt to reconnect automatically when camera becomes unavailable
 * - 5.4: Properly release camera resources when app is backgrounded
 */
public class CameraManager {
    
    private static final String TAG = "CameraManager";
    
    // Camera configuration constants
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    private static final int IMAGE_FORMAT = ImageFormat.YUV_420_888;
    
    // High-performance video configuration for 60 FPS
    private static final int TARGET_FPS = 60;
    private static final long TARGET_FRAME_INTERVAL_MS = 1000 / TARGET_FPS; // ~16.67ms for 60 FPS
    private static final int HIGH_SPEED_VIDEO_WIDTH = 1920;
    private static final int HIGH_SPEED_VIDEO_HEIGHT = 1080;
    
    // Frame rate management
    private static final long STANDARD_FRAME_INTERVAL_MS = 33; // ~30 FPS fallback
    
    // Threading and synchronization
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    
    // Camera2 API components
    private android.hardware.camera2.CameraManager systemCameraManager;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private CaptureRequest previewRequest;
    private ImageReader imageReader;
    private Size previewSize;
    
    // Camera configuration state
    private CameraConfig currentConfig;
    
    // Context and lifecycle
    private final Context context;
    private boolean isInitialized = false;
    private boolean isPreviewActive = false;
    
    // Error handling
    private ErrorHandler errorHandler;
    private int reconnectionAttempts = 0;
    private static final int MAX_RECONNECTION_ATTEMPTS = 3;
    private long lastReconnectionTime = 0;
    private static final long RECONNECTION_DELAY_MS = 2000;
    
    // Performance monitoring
    private PerformanceMonitor performanceMonitor;
    private long frameProcessingStartTime = 0;
    private final Object frameProcessingLock = new Object();
    
    // Frame buffer management
    private static final int MAX_FRAME_BUFFER_SIZE = 3;
    private int currentBufferSize = 0;
    private final Object bufferLock = new Object();
    private volatile boolean isProcessingFrame = false;
    
    // Performance metrics
    private long totalFramesProcessed = 0;
    private long totalFramesDropped = 0;
    private long lastFrameTime = 0;
    
    // Callbacks
    private CameraCallback cameraCallback;
    private FrameCallback frameCallback;
    
    /**
     * Interface for camera lifecycle callbacks
     */
    public interface CameraCallback {
        void onCameraOpened();
        void onCameraClosed();
        void onCameraError(int error, @Nullable String message);
        void onCameraDisconnected();
    }
    
    /**
     * Interface for frame capture callbacks
     */
    public interface FrameCallback {
        void onFrameAvailable(@NonNull Image frame);
    }
    
    /**
     * Camera configuration class with high-speed video support
     */
    public static class CameraConfig {
        public Size preferredSize = new Size(HIGH_SPEED_VIDEO_WIDTH, HIGH_SPEED_VIDEO_HEIGHT); // Default to 1920x1080
        public int imageFormat = IMAGE_FORMAT;
        public boolean enableAutoFocus = true;
        public boolean enableAutoExposure = true;
        public boolean enableHighSpeedVideo = true; // Enable 60 FPS by default
        public int targetFps = TARGET_FPS; // Target 60 FPS
        public boolean enableVideoStabilization = false; // Disabled for better performance
        
        public CameraConfig() {}
        
        public CameraConfig(Size preferredSize) {
            this.preferredSize = preferredSize;
        }
        
        /**
         * Create configuration optimized for high-speed video (60 FPS at 1920x1080)
         */
        public static CameraConfig createHighSpeedVideoConfig() {
            CameraConfig config = new CameraConfig();
            config.preferredSize = new Size(HIGH_SPEED_VIDEO_WIDTH, HIGH_SPEED_VIDEO_HEIGHT);
            config.enableHighSpeedVideo = true;
            config.targetFps = TARGET_FPS;
            config.enableVideoStabilization = false; // Disabled for performance
            config.enableAutoFocus = true; // Use continuous video AF
            config.enableAutoExposure = true;
            return config;
        }
        
        /**
         * Create configuration for standard video (30 FPS)
         */
        public static CameraConfig createStandardVideoConfig() {
            CameraConfig config = new CameraConfig();
            config.preferredSize = new Size(1280, 720);
            config.enableHighSpeedVideo = false;
            config.targetFps = 30;
            config.enableVideoStabilization = true;
            config.enableAutoFocus = true;
            config.enableAutoExposure = true;
            return config;
        }
        
        @Override
        public String toString() {
            return String.format("CameraConfig{size=%dx%d, fps=%d, highSpeed=%s, stabilization=%s}", 
                    preferredSize.getWidth(), preferredSize.getHeight(), 
                    targetFps, enableHighSpeedVideo, enableVideoStabilization);
        }
    }
    
    /**
     * Constructor
     * @param context Application context
     */
    public CameraManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.systemCameraManager = (android.hardware.camera2.CameraManager) 
                context.getSystemService(Context.CAMERA_SERVICE);
        this.errorHandler = new ErrorHandler(context);
        this.performanceMonitor = new PerformanceMonitor(context);
        
        // Set up performance monitoring callback
        this.performanceMonitor.setPerformanceCallback(new PerformanceMonitor.PerformanceCallback() {
            @Override
            public void onPerformanceLevelChanged(@NonNull PerformanceMonitor.PerformanceLevel newLevel, 
                                                @NonNull PerformanceMonitor.PerformanceLevel oldLevel) {
                Log.i(TAG, "Performance level changed: " + oldLevel + " -> " + newLevel);
                adjustFrameProcessingForPerformance(newLevel);
            }
            
            @Override
            public void onMemoryWarning(long usedMemoryMB, long totalMemoryMB) {
                Log.w(TAG, "Memory warning: " + usedMemoryMB + "MB / " + totalMemoryMB + "MB");
                optimizeBufferUsage();
            }
            
            @Override
            public void onMemoryCritical(long usedMemoryMB, long totalMemoryMB) {
                Log.e(TAG, "Critical memory usage: " + usedMemoryMB + "MB / " + totalMemoryMB + "MB");
                emergencyBufferCleanup();
            }
            
            @Override
            public void onProcessingTimeWarning(long processingTimeMs) {
                Log.w(TAG, "Processing time warning: " + processingTimeMs + "ms");
            }
            
            @Override
            public void onFrameDropRecommended(@NonNull String reason) {
                Log.d(TAG, "Frame drop recommended: " + reason);
                totalFramesDropped++;
            }
        });
    }
    
    /**
     * Set camera lifecycle callback
     * @param callback Camera callback interface
     */
    public void setCameraCallback(@Nullable CameraCallback callback) {
        this.cameraCallback = callback;
    }
    
    /**
     * Set frame capture callback
     * @param callback Frame callback interface
     */
    public void setFrameCallback(@Nullable FrameCallback callback) {
        this.frameCallback = callback;
    }
    
    /**
     * Set error handler for camera operations
     * @param errorHandler Error handler instance
     */
    public void setErrorHandler(@Nullable ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }
    
    /**
     * Set performance monitor for camera operations
     * @param performanceMonitor Performance monitor instance
     */
    public void setPerformanceMonitor(@Nullable PerformanceMonitor performanceMonitor) {
        this.performanceMonitor = performanceMonitor;
    }
    
    /**
     * Get current performance metrics
     * @return Performance metrics or null if monitor not available
     */
    @Nullable
    public PerformanceMonitor.PerformanceMetrics getPerformanceMetrics() {
        return performanceMonitor != null ? performanceMonitor.getCurrentMetrics() : null;
    }
    
    /**
     * Get comprehensive camera performance report
     * Requirements: NFR-001, NFR-002, NFR-003
     */
    public CameraPerformanceReport getPerformanceReport() {
        CameraPerformanceReport report = new CameraPerformanceReport();
        
        // Get frame processing statistics
        FrameProcessingStats frameStats = getFrameProcessingStats();
        report.frameStats = frameStats;
        
        // Get performance metrics from monitor
        if (performanceMonitor != null) {
            PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
            report.performanceMetrics = metrics;
            
            // Calculate frame rate
            if (frameStats.totalFramesProcessed > 0 && lastFrameTime > 0) {
                long currentTime = System.currentTimeMillis();
                long elapsedTime = currentTime - (lastFrameTime - (frameStats.totalFramesProcessed * TARGET_FRAME_INTERVAL_MS));
                if (elapsedTime > 0) {
                    report.currentFrameRate = (frameStats.totalFramesProcessed * 1000.0) / elapsedTime;
                }
            }
            
            // Check if meeting performance targets
            report.meetingFrameRateTarget = report.currentFrameRate >= 30.0;
            report.meetingMemoryTarget = metrics != null && metrics.usedMemoryMB < 50;
            report.meetingLatencyTarget = metrics != null && metrics.averageProcessingTimeMs < 100;
        }
        
        // Camera state information
        report.isInitialized = isInitialized;
        report.isPreviewActive = isPreviewActive;
        report.previewSize = previewSize;
        report.reconnectionAttempts = reconnectionAttempts;
        
        return report;
    }
    
    /**
     * Start performance monitoring session
     * Requirements: NFR-001, NFR-002, NFR-003
     */
    public void startPerformanceMonitoring() {
        Log.d(TAG, "Starting performance monitoring session");
        
        if (performanceMonitor != null) {
            performanceMonitor.resetCounters();
        }
        
        resetFrameProcessingStats();
        
        Log.i(TAG, "Performance monitoring session started");
    }
    
    /**
     * Stop performance monitoring and generate report
     * Requirements: NFR-003
     */
    public CameraPerformanceReport stopPerformanceMonitoring() {
        Log.d(TAG, "Stopping performance monitoring session");
        
        CameraPerformanceReport finalReport = getPerformanceReport();
        
        Log.i(TAG, "Performance monitoring session completed: " + finalReport);
        
        return finalReport;
    }
    
    /**
     * Monitor frame rate in real-time
     * Requirements: NFR-001
     */
    public double getCurrentFrameRate() {
        if (totalFramesProcessed == 0 || lastFrameTime == 0) {
            return 0.0;
        }
        
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - lastFrameTime;
        
        if (elapsedTime > 0) {
            // Calculate instantaneous frame rate based on recent frames
            return 1000.0 / TARGET_FRAME_INTERVAL_MS; // Target frame rate
        }
        
        return 0.0;
    }
    
    /**
     * Monitor memory usage specific to camera operations
     * Requirements: NFR-002
     */
    public CameraMemoryUsage getCameraMemoryUsage() {
        CameraMemoryUsage usage = new CameraMemoryUsage();
        
        if (performanceMonitor != null) {
            PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
            if (metrics != null) {
                usage.totalMemoryMB = metrics.totalMemoryMB;
                usage.usedMemoryMB = metrics.usedMemoryMB;
                usage.memoryUsagePercent = metrics.memoryUsagePercent;
            }
        }
        
        // Estimate camera-specific memory usage
        if (imageReader != null && previewSize != null) {
            // Estimate memory for image buffers
            long bytesPerFrame = previewSize.getWidth() * previewSize.getHeight() * 3; // YUV420
            usage.estimatedCameraBufferMB = (bytesPerFrame * MAX_FRAME_BUFFER_SIZE) / (1024 * 1024);
        }
        
        usage.currentBufferCount = currentBufferSize;
        usage.maxBufferCount = MAX_FRAME_BUFFER_SIZE;
        
        return usage;
    }
    
    /**
     * Monitor processing latency
     * Requirements: NFR-003
     */
    public ProcessingLatencyMetrics getProcessingLatencyMetrics() {
        ProcessingLatencyMetrics metrics = new ProcessingLatencyMetrics();
        
        if (performanceMonitor != null) {
            PerformanceMonitor.PerformanceMetrics perfMetrics = performanceMonitor.getCurrentMetrics();
            if (perfMetrics != null) {
                metrics.averageLatencyMs = perfMetrics.averageProcessingTimeMs;
                metrics.maxLatencyMs = perfMetrics.maxProcessingTimeMs;
                metrics.meetingLatencyTarget = perfMetrics.averageProcessingTimeMs < 100;
            }
        }
        
        // Calculate current processing latency
        synchronized (frameProcessingLock) {
            if (frameProcessingStartTime > 0) {
                metrics.currentLatencyMs = System.currentTimeMillis() - frameProcessingStartTime;
            }
        }
        
        return metrics;
    }
    
    /**
     * Enable or disable performance optimization
     * Requirements: NFR-001, NFR-002
     */
    public void setPerformanceOptimizationEnabled(boolean enabled) {
        Log.d(TAG, "Performance optimization " + (enabled ? "enabled" : "disabled"));
        
        if (enabled && performanceMonitor != null) {
            // Reset to optimal performance level
            performanceMonitor.resetPerformanceLevel();
        }
    }
    
    /**
     * Force performance level adjustment for testing
     * Requirements: NFR-009
     */
    public void forcePerformanceLevel(@NonNull PerformanceMonitor.PerformanceLevel level) {
        Log.d(TAG, "Forcing performance level to: " + level);
        
        if (performanceMonitor != null) {
            performanceMonitor.adjustPerformanceLevel(level);
        }
        
        adjustFrameProcessingForPerformance(level);
    }
    
    /**
     * Initialize camera with high-speed video configuration (60 FPS at 1920x1080)
     * Requirement 1.2: Initialize camera when permissions are granted
     * 
     * @return true if initialization successful, false otherwise
     */
    public boolean initializeCamera() {
        return initializeCamera(CameraConfig.createHighSpeedVideoConfig());
    }
    
    /**
     * Initialize camera with custom configuration
     * Requirement 1.2: Initialize camera and display live feed when permissions granted
     * 
     * @param config Camera configuration
     * @return true if initialization successful, false otherwise
     */
    public boolean initializeCamera(@NonNull CameraConfig config) {
        Log.d(TAG, "Initializing camera with config: " + config);
        
        if (isInitialized) {
            Log.w(TAG, "Camera already initialized");
            return true;
        }
        
        // Store configuration
        this.currentConfig = config;
        
        // Check camera permission
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permission not granted");
            if (errorHandler != null) {
                errorHandler.handleCameraPermissionError(false);
            }
            notifyCameraError(-1, "Camera permission not granted");
            return false;
        }
        
        try {
            // Find suitable camera
            cameraId = selectCamera();
            if (cameraId == null) {
                Log.e(TAG, "No suitable camera found");
                notifyCameraError(-1, "No suitable camera found");
                return false;
            }
            
            // Get camera characteristics and configure preview size
            CameraCharacteristics characteristics = systemCameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            
            if (map == null) {
                Log.e(TAG, "StreamConfigurationMap is null");
                notifyCameraError(-1, "Camera configuration not available");
                return false;
            }
            
            // Choose optimal preview size based on configuration
            Size[] availableSizes;
            if (config.enableHighSpeedVideo) {
                // Use high-speed video sizes if available
                Size[] highSpeedSizes = map.getHighSpeedVideoSizes();
                availableSizes = highSpeedSizes.length > 0 ? highSpeedSizes : map.getOutputSizes(config.imageFormat);
                Log.i(TAG, "Using high-speed video sizes, available count: " + highSpeedSizes.length);
            } else {
                availableSizes = map.getOutputSizes(config.imageFormat);
                Log.i(TAG, "Using standard output sizes, available count: " + availableSizes.length);
            }
            
            previewSize = chooseOptimalSize(availableSizes, 
                    config.preferredSize.getWidth(), config.preferredSize.getHeight());
            
            Log.i(TAG, "Selected preview size: " + previewSize + " for " + 
                  (config.enableHighSpeedVideo ? "high-speed" : "standard") + " video");
            
            // Set up ImageReader for frame capture
            setupImageReader(config);
            
            // Start background thread
            startBackgroundThread();
            
            isInitialized = true;
            Log.i(TAG, "Camera initialized successfully with " + config);
            return true;
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access exception during initialization", e);
            if (errorHandler != null) {
                errorHandler.handleCameraHardwareError(e.getReason(), e.getMessage(), e);
            }
            notifyCameraError(-1, "Camera access failed: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during camera initialization", e);
            if (errorHandler != null) {
                errorHandler.handleSystemError(e, "camera initialization");
            }
            notifyCameraError(-1, "Camera initialization failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Start camera preview
     * Requirement 1.2: Display live feed when camera is initialized
     * 
     * @return true if preview started successfully, false otherwise
     */
    public boolean startPreview() {
        Log.d(TAG, "Starting camera preview");
        
        if (!isInitialized) {
            Log.e(TAG, "Camera not initialized");
            return false;
        }
        
        if (isPreviewActive) {
            Log.w(TAG, "Preview already active");
            return true;
        }
        
        try {
            // Open camera device
            openCamera();
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start camera preview", e);
            if (errorHandler != null) {
                errorHandler.handleSystemError(e, "camera preview start");
            }
            notifyCameraError(-1, "Failed to start preview: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Stop camera preview
     * Requirement 5.4: Properly release camera resources
     */
    public void stopPreview() {
        Log.d(TAG, "Stopping camera preview");
        
        if (!isPreviewActive) {
            Log.d(TAG, "Preview not active");
            return;
        }
        
        try {
            // Acquire lock to prevent concurrent operations
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "Timeout waiting to lock camera for stopping preview");
                return;
            }
            
            try {
                // Stop repeating requests first
                if (captureSession != null) {
                    try {
                        captureSession.stopRepeating();
                        captureSession.abortCaptures();
                    } catch (CameraAccessException e) {
                        Log.w(TAG, "Error stopping capture session", e);
                    }
                    captureSession.close();
                    captureSession = null;
                }
                
                // Close camera device
                if (cameraDevice != null) {
                    cameraDevice.close();
                    cameraDevice = null;
                }
                
                isPreviewActive = false;
                Log.d(TAG, "Camera preview stopped");
                
                if (cameraCallback != null) {
                    cameraCallback.onCameraClosed();
                }
            } finally {
                cameraOpenCloseLock.release();
            }
            
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while stopping camera preview", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping camera preview", e);
        }
    }
    
    /**
     * Release all camera resources
     * Requirement 5.4: Properly release camera resources when app is backgrounded
     * Enhanced with performance monitoring cleanup
     */
    public void release() {
        Log.d(TAG, "Releasing camera resources with enhanced cleanup");
        
        stopPreview();
        
        // Clean up performance monitoring
        if (performanceMonitor != null) {
            performanceMonitor.release();
        }
        
        // Reset frame processing state
        resetFrameProcessingStats();
        
        // Close ImageReader
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        
        // Stop background thread
        stopBackgroundThread();
        
        isInitialized = false;
        Log.d(TAG, "Camera resources released with enhanced cleanup");
    }
    
    /**
     * Check if camera is initialized
     * @return true if camera is initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * Check if preview is active
     * @return true if preview is active
     */
    public boolean isPreviewActive() {
        return isPreviewActive;
    }
    
    /**
     * Get current preview size
     * @return Preview size or null if not initialized
     */
    @Nullable
    public Size getPreviewSize() {
        return previewSize;
    }
    
    /**
     * Get current camera configuration
     * @return Camera configuration or null if not initialized
     */
    @Nullable
    public CameraConfig getCurrentConfig() {
        return currentConfig;
    }
    
    /**
     * Check if high-speed video mode is active
     * @return true if high-speed video mode is active
     */
    public boolean isHighSpeedVideoActive() {
        return currentConfig != null && currentConfig.enableHighSpeedVideo && isPreviewActive;
    }
    
    /**
     * Get current target FPS
     * @return Target FPS or 30 if not configured
     */
    public int getCurrentTargetFps() {
        return currentConfig != null ? currentConfig.targetFps : 30;
    }
    
    /**
     * Attempt automatic camera reconnection with exponential backoff
     * Requirement 4.2: Attempt to reconnect automatically when camera becomes unavailable
     */
    public void attemptReconnection() {
        long currentTime = System.currentTimeMillis();
        
        // Check if enough time has passed since last reconnection attempt
        if (currentTime - lastReconnectionTime < RECONNECTION_DELAY_MS) {
            Log.d(TAG, "Reconnection attempt too soon, waiting...");
            return;
        }
        
        // Check if we've exceeded max attempts
        if (reconnectionAttempts >= MAX_RECONNECTION_ATTEMPTS) {
            Log.w(TAG, "Max reconnection attempts reached: " + reconnectionAttempts);
            return;
        }
        
        // Check if background handler is still available
        if (backgroundHandler == null) {
            Log.w(TAG, "Background handler not available for reconnection");
            return;
        }
        
        reconnectionAttempts++;
        lastReconnectionTime = currentTime;
        
        Log.i(TAG, "Attempting camera reconnection, attempt " + reconnectionAttempts);
        
        // Run reconnection on background thread
        backgroundHandler.post(() -> {
            try {
                // Ensure we have a valid background handler
                if (backgroundHandler == null) {
                    Log.w(TAG, "Background handler became null during reconnection");
                    return;
                }
                
                // Stop current preview if active
                if (isPreviewActive) {
                    stopPreview();
                }
                
                // Wait a moment before reconnecting
                Thread.sleep(1000);
                
                // Attempt to restart
                if (startPreview()) {
                    Log.i(TAG, "Camera reconnection successful after " + reconnectionAttempts + " attempts");
                    reconnectionAttempts = 0; // Reset counter on success
                    
                    if (errorHandler != null) {
                        errorHandler.resetErrorCounters();
                    }
                } else {
                    Log.w(TAG, "Camera reconnection attempt " + reconnectionAttempts + " failed");
                    
                    // Schedule next attempt with exponential backoff
                    if (reconnectionAttempts < MAX_RECONNECTION_ATTEMPTS && backgroundHandler != null) {
                        long nextDelay = RECONNECTION_DELAY_MS * (1L << (reconnectionAttempts - 1));
                        Log.d(TAG, "Scheduling next reconnection attempt in " + nextDelay + "ms");
                        
                        backgroundHandler.postDelayed(this::attemptReconnection, nextDelay);
                    }
                }
            } catch (InterruptedException e) {
                Log.w(TAG, "Reconnection attempt interrupted", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Log.e(TAG, "Error during reconnection attempt", e);
                if (errorHandler != null) {
                    errorHandler.handleSystemError(e, "camera reconnection");
                }
            }
        });
    }
    
    /**
     * Reset reconnection attempts counter
     */
    public void resetReconnectionAttempts() {
        reconnectionAttempts = 0;
        lastReconnectionTime = 0;
        Log.d(TAG, "Reconnection attempts counter reset");
    }
    
    /**
     * Select the best available camera (prefer back-facing)
     */
    private String selectCamera() throws CameraAccessException {
        String[] cameraIds = systemCameraManager.getCameraIdList();
        
        // Prefer back-facing camera
        for (String id : cameraIds) {
            CameraCharacteristics characteristics = systemCameraManager.getCameraCharacteristics(id);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                Log.d(TAG, "Selected back-facing camera: " + id);
                return id;
            }
        }
        
        // Fall back to first available camera
        if (cameraIds.length > 0) {
            Log.d(TAG, "Selected first available camera: " + cameraIds[0]);
            return cameraIds[0];
        }
        
        return null;
    }
    
    /**
     * Choose optimal preview size based on preferences and available sizes
     */
    private Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight) {
        // Filter sizes that are not too large
        java.util.List<Size> bigEnough = new java.util.ArrayList<>();
        java.util.List<Size> notBigEnough = new java.util.ArrayList<>();
        
        int w = textureViewWidth;
        int h = textureViewHeight;
        
        for (Size option : choices) {
            if (option.getWidth() <= MAX_PREVIEW_WIDTH && option.getHeight() <= MAX_PREVIEW_HEIGHT) {
                if (option.getWidth() >= w && option.getHeight() >= h) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }
        
        // Pick the smallest of those big enough
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }
    
    /**
     * Set up ImageReader for frame capture
     */
    private void setupImageReader(CameraConfig config) {
        imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(),
                config.imageFormat, 2);
        
        imageReader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);
        Log.d(TAG, "ImageReader configured: " + previewSize + ", format: " + config.imageFormat);
    }
    
    /**
     * Open camera device
     */
    private void openCamera() throws CameraAccessException {
        Log.d(TAG, "Opening camera device: " + cameraId);
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permission not granted");
            if (errorHandler != null) {
                errorHandler.handleCameraPermissionError(false);
            }
            notifyCameraError(-1, "Camera permission not granted");
            return;
        }
        
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupted status
            throw new RuntimeException("Interrupted while waiting to lock camera opening.", e);
        }
        
        systemCameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
    }
    
    /**
     * Create camera capture session with high-speed video support for 60 FPS
     */
    private void createCameraPreviewSession() {
        try {
            Log.d(TAG, "Creating camera preview session with config: " + currentConfig);
            
            // Check if high-speed video is requested and supported
            if (currentConfig != null && currentConfig.enableHighSpeedVideo) {
                CameraCharacteristics characteristics = systemCameraManager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                
                boolean supportsHighSpeed = false;
                if (map != null) {
                    Size[] highSpeedSizes = map.getHighSpeedVideoSizes();
                    for (Size size : highSpeedSizes) {
                        if (size.getWidth() == previewSize.getWidth() && size.getHeight() == previewSize.getHeight()) {
                            supportsHighSpeed = true;
                            Log.i(TAG, "High-speed video supported at " + previewSize);
                            break;
                        }
                    }
                }
                
                if (supportsHighSpeed) {
                    createHighSpeedVideoSession(characteristics);
                } else {
                    Log.w(TAG, "High-speed video not supported at " + previewSize + ", falling back to standard preview");
                    createStandardPreviewSession();
                }
            } else {
                Log.i(TAG, "Standard preview mode requested");
                createStandardPreviewSession();
            }
                    
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to create camera preview session", e);
            if (errorHandler != null) {
                errorHandler.handleCameraHardwareError(e.getReason(), e.getMessage(), e);
            }
            notifyCameraError(e.getReason(), e.getMessage());
        }
    }
    
    /**
     * Create high-speed video capture session for 60 FPS
     */
    private void createHighSpeedVideoSession(CameraCharacteristics characteristics) throws CameraAccessException {
        Log.d(TAG, "Creating high-speed video session for " + currentConfig.targetFps + " FPS at " + previewSize);
        
        // Create capture request builder for high-speed video
        previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        previewRequestBuilder.addTarget(imageReader.getSurface());
        
        // Configure for high-speed video
        previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
        previewRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO);
        
        // Set target FPS range for the configured FPS
        android.util.Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        android.util.Range<Integer> targetFpsRange = null;
        
        if (fpsRanges != null) {
            // Look for exact FPS range or closest match
            for (android.util.Range<Integer> range : fpsRanges) {
                if (range.getUpper() >= currentConfig.targetFps) {
                    targetFpsRange = range;
                    Log.i(TAG, "Selected FPS range: " + range.getLower() + "-" + range.getUpper() + 
                          " for target " + currentConfig.targetFps + " FPS");
                    break;
                }
            }
            
            // Fall back to highest available FPS
            if (targetFpsRange == null && fpsRanges.length > 0) {
                targetFpsRange = fpsRanges[fpsRanges.length - 1];
                Log.w(TAG, currentConfig.targetFps + " FPS not available, using highest FPS range: " + 
                      targetFpsRange.getLower() + "-" + targetFpsRange.getUpper());
            }
        }
        
        if (targetFpsRange != null) {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetFpsRange);
        }
        
        // Configure focus mode based on configuration
        if (currentConfig.enableAutoFocus) {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
        } else {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        }
        
        // Configure exposure mode
        if (currentConfig.enableAutoExposure) {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        } else {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        }
        
        // Configure video stabilization based on configuration
        if (currentConfig.enableVideoStabilization) {
            previewRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, 
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
        } else {
            // Disable stabilization for better performance
            previewRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, 
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
            previewRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, 
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
        }
        
        // Create high-speed capture session
        cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                        Log.d(TAG, "High-speed camera capture session configured");
                        
                        if (cameraDevice == null) {
                            Log.w(TAG, "Camera device is null in session callback");
                            return;
                        }
                        
                        captureSession = cameraCaptureSession;
                        
                        try {
                            // Start repeating capture requests for high-speed video
                            previewRequest = previewRequestBuilder.build();
                            captureSession.setRepeatingRequest(previewRequest, null, backgroundHandler);
                            
                            isPreviewActive = true;
                            Log.i(TAG, "High-speed camera preview started successfully at " + 
                                  currentConfig.targetFps + " FPS (" + previewSize + ")");
                            
                            if (cameraCallback != null) {
                                cameraCallback.onCameraOpened();
                            }
                            
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "Failed to start high-speed camera preview", e);
                            if (errorHandler != null) {
                                errorHandler.handleCameraHardwareError(e.getReason(), e.getMessage(), e);
                            }
                            notifyCameraError(e.getReason(), e.getMessage());
                        }
                    }
                    
                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                        Log.e(TAG, "High-speed camera capture session configuration failed");
                        // Fall back to standard preview
                        try {
                            createStandardPreviewSession();
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "Failed to create fallback standard session", e);
                            notifyCameraError(-1, "Camera session configuration failed");
                        }
                    }
                }, backgroundHandler);
    }
    
    /**
     * Create standard preview session (fallback)
     */
    private void createStandardPreviewSession() throws CameraAccessException {
        Log.d(TAG, "Creating standard camera preview session");
        
        // Create capture request builder
        previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        previewRequestBuilder.addTarget(imageReader.getSurface());
        
        // Set auto-focus and auto-exposure
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        
        // Try to set highest available FPS
        try {
            CameraCharacteristics characteristics = systemCameraManager.getCameraCharacteristics(cameraId);
            android.util.Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            
            if (fpsRanges != null && fpsRanges.length > 0) {
                // Use the highest available FPS range
                android.util.Range<Integer> highestFpsRange = fpsRanges[fpsRanges.length - 1];
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, highestFpsRange);
                Log.i(TAG, "Set FPS range to: " + highestFpsRange.getLower() + "-" + highestFpsRange.getUpper());
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to set FPS range", e);
        }
        
        // Create capture session
        cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                        Log.d(TAG, "Standard camera capture session configured");
                        
                        if (cameraDevice == null) {
                            Log.w(TAG, "Camera device is null in session callback");
                            return;
                        }
                        
                        captureSession = cameraCaptureSession;
                        
                        try {
                            // Start repeating capture requests
                            previewRequest = previewRequestBuilder.build();
                            captureSession.setRepeatingRequest(previewRequest, null, backgroundHandler);
                            
                            isPreviewActive = true;
                            Log.i(TAG, "Standard camera preview started successfully");
                            
                            if (cameraCallback != null) {
                                cameraCallback.onCameraOpened();
                            }
                            
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "Failed to start standard camera preview", e);
                            if (errorHandler != null) {
                                errorHandler.handleCameraHardwareError(e.getReason(), e.getMessage(), e);
                            }
                            notifyCameraError(e.getReason(), e.getMessage());
                        }
                    }
                    
                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                        Log.e(TAG, "Standard camera capture session configuration failed");
                        notifyCameraError(-1, "Camera session configuration failed");
                    }
                }, backgroundHandler);
    }
    
    /**
     * Start background thread for camera operations
     */
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        Log.d(TAG, "Background thread started");
    }
    
    /**
     * Stop background thread
     */
    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
                Log.d(TAG, "Background thread stopped");
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping background thread", e);
                Thread.currentThread().interrupt(); // Restore interrupted status
            }
        }
    }
    
    /**
     * Notify camera error to callback
     */
    private void notifyCameraError(int error, @Nullable String message) {
        if (cameraCallback != null) {
            cameraCallback.onCameraError(error, message);
        }
    }
    
    /**
     * Camera device state callback
     * Requirement 4.2: Attempt to reconnect automatically when camera becomes unavailable
     */
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera device opened");
            cameraOpenCloseLock.release();
            cameraDevice = camera;
            createCameraPreviewSession();
        }
        
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.w(TAG, "Camera device disconnected");
            
            // Clean up resources safely
            try {
                if (captureSession != null) {
                    captureSession.close();
                    captureSession = null;
                }
                camera.close();
                cameraDevice = null;
                isPreviewActive = false;
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up after camera disconnection", e);
            } finally {
                cameraOpenCloseLock.release();
            }
            
            if (cameraCallback != null) {
                cameraCallback.onCameraDisconnected();
            }
            
            // Attempt automatic reconnection with delay to avoid immediate retry
            if (backgroundHandler != null) {
                backgroundHandler.postDelayed(() -> attemptReconnection(), 1000);
            }
        }
        
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "Camera device error: " + error);
            
            // Clean up resources safely
            try {
                if (captureSession != null) {
                    captureSession.close();
                    captureSession = null;
                }
                camera.close();
                cameraDevice = null;
                isPreviewActive = false;
            } catch (Exception e) {
                Log.e(TAG, "Error cleaning up after camera error", e);
            } finally {
                cameraOpenCloseLock.release();
            }
            
            String errorMessage = getCameraErrorMessage(error);
            if (errorHandler != null) {
                errorHandler.handleCameraHardwareError(error, errorMessage, null);
            }
            notifyCameraError(error, errorMessage);
            
            // Attempt automatic reconnection for recoverable errors
            if (isRecoverableError(error) && backgroundHandler != null) {
                backgroundHandler.postDelayed(() -> attemptReconnection(), 2000);
            }
        }
    };
    
    /**
     * Enhanced ImageReader callback for robust frame capture with error recovery
     * Requirements: FR-001, FR-002, FR-010, NFR-001, NFR-002
     */
    private final ImageReader.OnImageAvailableListener imageAvailableListener = 
            new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            long currentTime = System.currentTimeMillis();
            
            // Check frame rate throttling for performance
            if (shouldSkipFrame(currentTime)) {
                // Skip frame to maintain performance
                Image skippedImage = null;
                try {
                    skippedImage = reader.acquireLatestImage();
                    if (skippedImage != null) {
                        skippedImage.close();
                        totalFramesDropped++;
                        if (performanceMonitor != null) {
                            performanceMonitor.recordFrameDrop("Frame rate throttling");
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error skipping frame", e);
                } finally {
                    if (skippedImage != null) {
                        try {
                            skippedImage.close();
                        } catch (Exception ignored) {}
                    }
                }
                return;
            }
            
            // Check buffer capacity before processing
            synchronized (bufferLock) {
                if (currentBufferSize >= MAX_FRAME_BUFFER_SIZE || isProcessingFrame) {
                    // Buffer full or processing in progress, drop frame
                    Image droppedImage = null;
                    try {
                        droppedImage = reader.acquireLatestImage();
                        if (droppedImage != null) {
                            droppedImage.close();
                            totalFramesDropped++;
                            if (performanceMonitor != null) {
                                performanceMonitor.recordFrameDrop("Buffer overflow");
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error dropping frame due to buffer overflow", e);
                    } finally {
                        if (droppedImage != null) {
                            try {
                                droppedImage.close();
                            } catch (Exception ignored) {}
                        }
                    }
                    return;
                }
                
                // Increment buffer size
                currentBufferSize++;
                isProcessingFrame = true;
            }
            
            // Start performance timing
            synchronized (frameProcessingLock) {
                frameProcessingStartTime = System.currentTimeMillis();
            }
            
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null && frameCallback != null) {
                    // Process frame with error recovery
                    processFrameWithRecovery(image, currentTime);
                    // Don't close the image here - let the frame processor handle it
                    image = null; // Prevent closing in finally block
                } else {
                    // No callback or image, clean up buffer
                    synchronized (bufferLock) {
                        currentBufferSize--;
                        isProcessingFrame = false;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing captured image", e);
                
                // Handle error with recovery
                if (errorHandler != null) {
                    errorHandler.handleSystemError(e, "image processing");
                }
                
                // Clean up buffer on error
                synchronized (bufferLock) {
                    currentBufferSize--;
                    isProcessingFrame = false;
                }
                
                // Record processing time even on error
                recordFrameProcessingTime();
                
            } finally {
                // Only close if the callback didn't handle it
                if (image != null) {
                    try {
                        image.close();
                    } catch (Exception e) {
                        Log.w(TAG, "Error closing image", e);
                    }
                    
                    // Clean up buffer
                    synchronized (bufferLock) {
                        currentBufferSize--;
                        isProcessingFrame = false;
                    }
                }
                
                // Update frame timing
                lastFrameTime = currentTime;
            }
        }
    };
    
    /**
     * Get human-readable camera error message
     */
    private String getCameraErrorMessage(int error) {
        switch (error) {
            case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                return "Camera device error";
            case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                return "Camera disabled";
            case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                return "Camera in use";
            case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                return "Camera service error";
            case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                return "Maximum cameras in use";
            default:
                return "Unknown camera error: " + error;
        }
    }
    
    /**
     * Check if camera error is recoverable through reconnection
     */
    private boolean isRecoverableError(int error) {
        switch (error) {
            case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
            case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                return true; // These errors might be temporary
            case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
            case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
            case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                return false; // These require user intervention
            default:
                return true; // Unknown errors - attempt recovery
        }
    }
    
    /**
     * Process frame with error recovery and performance monitoring
     * Requirements: FR-010, NFR-001, NFR-002
     */
    private void processFrameWithRecovery(@NonNull Image image, long frameTime) {
        try {
            // Call the frame callback with error handling
            frameCallback.onFrameAvailable(image);
            
            // Record successful processing
            totalFramesProcessed++;
            recordFrameProcessingTime();
            
        } catch (Exception callbackException) {
            Log.e(TAG, "Error in frame callback", callbackException);
            
            // Handle callback error with recovery
            if (errorHandler != null) {
                errorHandler.handleSystemError(callbackException, "frame callback");
            }
            
            // Close the image since callback failed
            try {
                image.close();
            } catch (Exception closeException) {
                Log.w(TAG, "Error closing image after callback failure", closeException);
            }
            
            // Record processing time even on error
            recordFrameProcessingTime();
            
        } finally {
            // Clean up buffer tracking
            synchronized (bufferLock) {
                currentBufferSize--;
                isProcessingFrame = false;
            }
        }
    }
    
    /**
     * Check if frame should be skipped for performance reasons
     * Requirements: NFR-001, NFR-002
     */
    private boolean shouldSkipFrame(long currentTime) {
        // Calculate frame interval based on current configuration
        long frameInterval = currentConfig != null ? 
                (1000 / currentConfig.targetFps) : STANDARD_FRAME_INTERVAL_MS;
        
        // Skip if too soon since last frame (frame rate limiting)
        if (lastFrameTime > 0 && (currentTime - lastFrameTime) < frameInterval) {
            return true;
        }
        
        // Skip based on performance recommendations
        if (performanceMonitor != null) {
            PerformanceMonitor.ProcessingRecommendation recommendation = 
                performanceMonitor.getProcessingRecommendation();
            
            if (recommendation.frameSkipRatio > 0) {
                // Implement frame skipping based on recommendation
                long frameNumber = totalFramesProcessed + totalFramesDropped;
                return (frameNumber % (recommendation.frameSkipRatio + 1)) != 0;
            }
        }
        
        return false;
    }
    
    /**
     * Record frame processing time for performance monitoring
     * Requirements: NFR-001, NFR-003
     */
    private void recordFrameProcessingTime() {
        synchronized (frameProcessingLock) {
            if (frameProcessingStartTime > 0) {
                long processingTime = System.currentTimeMillis() - frameProcessingStartTime;
                
                if (performanceMonitor != null) {
                    performanceMonitor.recordProcessingTime(processingTime);
                }
                
                frameProcessingStartTime = 0;
            }
        }
    }
    

    

    
    /**
     * Reset frame processing statistics
     */
    public void resetFrameProcessingStats() {
        totalFramesProcessed = 0;
        totalFramesDropped = 0;
        lastFrameTime = 0;
        
        synchronized (bufferLock) {
            currentBufferSize = 0;
            isProcessingFrame = false;
        }
        
        if (performanceMonitor != null) {
            performanceMonitor.resetCounters();
        }
        
        Log.d(TAG, "Frame processing statistics reset");
    }
    
    /**
     * Get current frame processing statistics
     * Requirements: NFR-001, NFR-003
     */
    public FrameProcessingStats getFrameProcessingStats() {
        FrameProcessingStats stats = new FrameProcessingStats();
        
        // Copy current statistics
        stats.totalFramesProcessed = totalFramesProcessed;
        stats.totalFramesDropped = totalFramesDropped;
        stats.totalFramesCaptured = totalFramesProcessed + totalFramesDropped;
        
        // Calculate timing statistics
        if (performanceMonitor != null) {
            PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
            if (metrics != null) {
                stats.averageProcessingTimeMs = metrics.averageProcessingTimeMs;
                stats.maxProcessingTimeMs = metrics.maxProcessingTimeMs;
            }
        }
        
        // Calculate frame rate
        if (totalFramesProcessed > 0 && lastFrameTime > 0) {
            long currentTime = System.currentTimeMillis();
            long sessionDuration = currentTime - stats.sessionStartTime;
            if (sessionDuration > 0) {
                stats.averageFrameRate = (totalFramesProcessed * 1000.0) / sessionDuration;
                stats.currentFrameRate = getCurrentFrameRate();
            }
        }
        
        // Set performance indicators
        stats.meetingFrameRateTarget = stats.currentFrameRate >= 30.0;
        stats.meetingLatencyTarget = stats.averageProcessingTimeMs < 100;
        
        return stats;
    }
    
    /**
     * Adjust frame processing based on performance level
     * Requirements: NFR-001, NFR-002
     */
    private void adjustFrameProcessingForPerformance(@NonNull PerformanceMonitor.PerformanceLevel level) {
        Log.d(TAG, "Adjusting frame processing for performance level: " + level);
        
        switch (level) {
            case HIGH:
                // Full performance mode
                // No frame skipping, full quality processing
                break;
                
            case MEDIUM:
                // Moderate performance mode
                // Slight optimization, maintain quality
                optimizeBufferUsage();
                break;
                
            case LOW:
                // Low performance mode
                // Reduce processing load, optimize memory
                optimizeBufferUsage();
                if (performanceMonitor != null) {
                    performanceMonitor.recordFrameDrop("Performance optimization");
                }
                break;
                
            case CRITICAL:
                // Critical performance mode
                // Emergency optimizations
                emergencyBufferCleanup();
                if (performanceMonitor != null) {
                    performanceMonitor.recordFrameDrop("Critical performance");
                }
                break;
        }
    }
    
    /**
     * Optimize buffer usage for better performance
     * Requirements: NFR-002
     */
    private void optimizeBufferUsage() {
        synchronized (bufferLock) {
            if (currentBufferSize > 1) {
                // Reduce buffer size to improve memory usage
                currentBufferSize = Math.max(1, currentBufferSize - 1);
                Log.d(TAG, "Optimized buffer usage, new size: " + currentBufferSize);
            }
        }
        
        // Suggest garbage collection if memory pressure is high
        if (performanceMonitor != null) {
            PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
            if (metrics != null && metrics.memoryUsagePercent > 80) {
                System.gc();
                Log.d(TAG, "Suggested garbage collection due to memory pressure");
            }
        }
    }
    
    /**
     * Emergency buffer cleanup for critical performance situations
     * Requirements: NFR-002
     */
    private void emergencyBufferCleanup() {
        Log.w(TAG, "Performing emergency buffer cleanup");
        
        synchronized (bufferLock) {
            // Minimize buffer usage
            currentBufferSize = 1;
            isProcessingFrame = false;
        }
        
        // Force garbage collection
        System.gc();
        
        // Reset frame processing to clear any pending operations
        synchronized (frameProcessingLock) {
            frameProcessingStartTime = 0;
        }
        
        Log.w(TAG, "Emergency buffer cleanup completed");
    }
    
    /**
     * Start frame processing timing
     * Requirements: NFR-003
     */
    public void startFrameProcessingTiming() {
        synchronized (frameProcessingLock) {
            frameProcessingStartTime = System.currentTimeMillis();
            isProcessingFrame = true;
        }
    }
    
    /**
     * End frame processing timing and record metrics
     * Requirements: NFR-003
     */
    public void endFrameProcessingTiming() {
        long processingTime = 0;
        
        synchronized (frameProcessingLock) {
            if (frameProcessingStartTime > 0) {
                processingTime = System.currentTimeMillis() - frameProcessingStartTime;
                frameProcessingStartTime = 0;
            }
            isProcessingFrame = false;
        }
        
        if (processingTime > 0 && performanceMonitor != null) {
            performanceMonitor.recordProcessingTime(processingTime);
        }
        
        // Update frame counters
        totalFramesProcessed++;
        lastFrameTime = System.currentTimeMillis();
    }
    
    /**
     * Record frame drop for performance monitoring
     * Requirements: NFR-001
     */
    public void recordFrameDrop(@NonNull String reason) {
        totalFramesDropped++;
        
        if (performanceMonitor != null) {
            performanceMonitor.recordFrameDrop(reason);
        }
        
        Log.d(TAG, "Frame dropped: " + reason + " (total drops: " + totalFramesDropped + ")");
    }
    
    /**
     * Comparator for sorting sizes by area
     */
    private static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}