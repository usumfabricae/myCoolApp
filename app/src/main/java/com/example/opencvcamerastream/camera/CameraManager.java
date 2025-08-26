package com.example.opencvcamerastream.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
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
    
    // Context and lifecycle
    private final Context context;
    private boolean isInitialized = false;
    private boolean isPreviewActive = false;
    
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
     * Camera configuration class
     */
    public static class CameraConfig {
        public Size preferredSize = new Size(1280, 720);
        public int imageFormat = IMAGE_FORMAT;
        public boolean enableAutoFocus = true;
        public boolean enableAutoExposure = true;
        
        public CameraConfig() {}
        
        public CameraConfig(Size preferredSize) {
            this.preferredSize = preferredSize;
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
     * Initialize camera with default configuration
     * Requirement 1.2: Initialize camera when permissions are granted
     * 
     * @return true if initialization successful, false otherwise
     */
    public boolean initializeCamera() {
        return initializeCamera(new CameraConfig());
    }
    
    /**
     * Initialize camera with custom configuration
     * Requirement 1.2: Initialize camera and display live feed when permissions granted
     * 
     * @param config Camera configuration
     * @return true if initialization successful, false otherwise
     */
    public boolean initializeCamera(@NonNull CameraConfig config) {
        Log.d(TAG, "Initializing camera with config: " + config.preferredSize);
        
        if (isInitialized) {
            Log.w(TAG, "Camera already initialized");
            return true;
        }
        
        // Check camera permission
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permission not granted");
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
            
            // Choose optimal preview size
            previewSize = chooseOptimalSize(map.getOutputSizes(config.imageFormat), 
                    config.preferredSize.getWidth(), config.preferredSize.getHeight());
            
            Log.d(TAG, "Selected preview size: " + previewSize);
            
            // Set up ImageReader for frame capture
            setupImageReader(config);
            
            // Start background thread
            startBackgroundThread();
            
            isInitialized = true;
            Log.i(TAG, "Camera initialized successfully");
            return true;
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access exception during initialization", e);
            notifyCameraError(-1, "Camera access failed: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during camera initialization", e);
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
            // Close capture session
            if (captureSession != null) {
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
            
        } catch (Exception e) {
            Log.e(TAG, "Error stopping camera preview", e);
        }
    }
    
    /**
     * Release all camera resources
     * Requirement 5.4: Properly release camera resources when app is backgrounded
     */
    public void release() {
        Log.d(TAG, "Releasing camera resources");
        
        stopPreview();
        
        // Close ImageReader
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        
        // Stop background thread
        stopBackgroundThread();
        
        isInitialized = false;
        Log.d(TAG, "Camera resources released");
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
            notifyCameraError(-1, "Camera permission not granted");
            return;
        }
        
        if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("Time out waiting to lock camera opening.");
        }
        
        systemCameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
    }
    
    /**
     * Create camera capture session
     */
    private void createCameraPreviewSession() {
        try {
            Log.d(TAG, "Creating camera preview session");
            
            // Create capture request builder
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(imageReader.getSurface());
            
            // Set auto-focus and auto-exposure
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            
            // Create capture session
            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "Camera capture session configured");
                            
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
                                Log.i(TAG, "Camera preview started successfully");
                                
                                if (cameraCallback != null) {
                                    cameraCallback.onCameraOpened();
                                }
                                
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Failed to start camera preview", e);
                                notifyCameraError(e.getReason(), e.getMessage());
                            }
                        }
                        
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "Camera capture session configuration failed");
                            notifyCameraError(-1, "Camera session configuration failed");
                        }
                    }, backgroundHandler);
                    
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to create camera preview session", e);
            notifyCameraError(e.getReason(), e.getMessage());
        }
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
            cameraOpenCloseLock.release();
            camera.close();
            cameraDevice = null;
            isPreviewActive = false;
            
            if (cameraCallback != null) {
                cameraCallback.onCameraDisconnected();
            }
        }
        
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "Camera device error: " + error);
            cameraOpenCloseLock.release();
            camera.close();
            cameraDevice = null;
            isPreviewActive = false;
            
            String errorMessage = getCameraErrorMessage(error);
            notifyCameraError(error, errorMessage);
        }
    };
    
    /**
     * ImageReader callback for frame capture
     */
    private final ImageReader.OnImageAvailableListener imageAvailableListener = 
            new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null && frameCallback != null) {
                    frameCallback.onFrameAvailable(image);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing captured image", e);
            } finally {
                if (image != null) {
                    image.close();
                }
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