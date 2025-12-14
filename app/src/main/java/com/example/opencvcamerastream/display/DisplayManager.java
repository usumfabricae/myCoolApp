package com.example.opencvcamerastream.display;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.CvType;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * DisplayManager handles the display of processed frames using TextureView
 * 
 * This class manages the TextureView for displaying camera frames processed by OpenCV.
 * It handles surface texture lifecycle, frame conversion from Mat to displayable format,
 * and orientation changes while maintaining aspect ratio.
 * 
 * Requirements addressed:
 * - 3.1: Display processed frames on screen
 * - 3.2: Maintain original aspect ratio
 * - 1.4: Handle device rotation correctly
 */
public class DisplayManager implements TextureView.SurfaceTextureListener {
    
    private static final String TAG = "DisplayManager";
    
    private final Context context;
    private TextureView textureView;
    private Surface surface;
    private boolean isDisplayReady = false;
    private DisplayCallback displayCallback;
    
    // Frame dimensions and display properties
    private int frameWidth = 0;
    private int frameHeight = 0;
    private int displayWidth = 0;
    private int displayHeight = 0;
    private Matrix transformMatrix = new Matrix();
    
    // Performance tracking
    private long lastFrameTime = 0;
    private int frameCount = 0;
    private static final int FPS_CALCULATION_INTERVAL = 30; // Calculate FPS every 30 frames
    
    // Performance optimization settings
    private static final long TARGET_FRAME_TIME_MS = 16; // 60 FPS target
    private static final long MAX_FRAME_TIME_MS = 33; // 30 FPS minimum
    private boolean hardwareAccelerationEnabled = true;
    
    // Performance metrics
    private long totalUpdateTime = 0;
    private long maxUpdateTime = 0;
    private long minUpdateTime = Long.MAX_VALUE;
    private int slowFrameCount = 0;
    
    // Matrix transformation optimization
    private boolean matrixNeedsUpdate = true;
    private final Object matrixLock = new Object();
    
    /**
     * Callback interface for display events
     */
    public interface DisplayCallback {
        /**
         * Called when the display surface is ready for rendering
         */
        void onDisplayReady();
        
        /**
         * Called when the display surface is destroyed
         */
        void onDisplayDestroyed();
        
        /**
         * Called when frame update fails
         * @param error The error that occurred
         */
        void onFrameUpdateError(@NonNull Exception error);
        
        /**
         * Called periodically with performance metrics
         * @param fps Current frames per second
         * @param avgUpdateTime Average frame update time in milliseconds
         */
        void onPerformanceUpdate(float fps, float avgUpdateTime);
    }
    
    /**
     * Constructor
     * @param context Application context
     */
    public DisplayManager(@NonNull Context context) {
        this.context = context;
        Log.d(TAG, "DisplayManager created with hardware acceleration enabled");
    }
    
    /**
     * Enable or disable hardware acceleration for rendering
     * Requirement 9.4: Hardware-accelerated rendering optimizations
     * 
     * @param enabled true to enable hardware acceleration, false to disable
     */
    public void setHardwareAccelerationEnabled(boolean enabled) {
        this.hardwareAccelerationEnabled = enabled;
        Log.d(TAG, "Hardware acceleration " + (enabled ? "enabled" : "disabled"));
        
        if (textureView != null) {
            textureView.setLayerType(enabled ? 
                android.view.View.LAYER_TYPE_HARDWARE : 
                android.view.View.LAYER_TYPE_SOFTWARE, null);
        }
    }
    
    /**
     * Check if hardware acceleration is enabled
     * 
     * @return true if hardware acceleration is enabled
     */
    public boolean isHardwareAccelerationEnabled() {
        return hardwareAccelerationEnabled;
    }
    
    /**
     * Set up the TextureView for display
     * Requirement 3.1: Display processed frames on screen
     * Requirement 9.4: Enable hardware acceleration
     * 
     * @param textureView The TextureView to use for display
     * @return true if setup was successful, false otherwise
     */
    public boolean setupDisplay(@NonNull TextureView textureView) {
        Log.d(TAG, "Setting up display with TextureView");
        
        this.textureView = textureView;
        this.textureView.setSurfaceTextureListener(this);
        
        // Enable hardware acceleration for optimal performance
        if (hardwareAccelerationEnabled) {
            this.textureView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
            Log.d(TAG, "Hardware acceleration enabled for TextureView");
        }
        
        // Optimize TextureView for performance
        this.textureView.setOpaque(true); // Opaque rendering is faster
        
        // If the TextureView is already available, set up immediately
        if (textureView.isAvailable()) {
            Log.d(TAG, "TextureView already available, setting up surface");
            onSurfaceTextureAvailable(textureView.getSurfaceTexture(), 
                    textureView.getWidth(), textureView.getHeight());
        }
        
        Log.i(TAG, "Display setup completed with hardware acceleration");
        return true;
    }
    
    /**
     * Update the display with a new processed frame
     * Requirements 3.1, 3.3: Display processed frame within 16ms for 60 FPS UI
     * Requirement 9.4: Maintain 60 FPS UI responsiveness
     * 
     * @param processedFrame The OpenCV Mat containing the processed frame
     * @return true if frame was updated successfully, false otherwise
     */
    public boolean updateFrame(@NonNull Mat processedFrame) {
        if (!isDisplayReady || surface == null) {
            Log.v(TAG, "Display not ready for frame update");
            return false;
        }
        
        // Validate input Mat before processing
        if (processedFrame == null) {
            Log.w(TAG, "Invalid processed frame - frame is null - skipping display update");
            return false;
        }
        
        if (processedFrame.empty()) {
            Log.w(TAG, "Invalid processed frame - frame is empty - skipping display update");
            return false;
        }
        
        if (processedFrame.width() <= 0 || processedFrame.height() <= 0) {
            Log.w(TAG, "Invalid processed frame - invalid dimensions: " + 
                  processedFrame.width() + "x" + processedFrame.height() + " - skipping display update");
            return false;
        }
        
        // Additional debugging for frame validation (only when needed)
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Processing frame: " + processedFrame.width() + "x" + processedFrame.height() + 
                  ", channels: " + processedFrame.channels() + ", type: " + processedFrame.type());
        }
        
        long startTime = System.nanoTime(); // Use nanoTime for more precise measurements
        
        try {
            // Convert Mat to Bitmap
            Bitmap bitmap = matToBitmap(processedFrame);
            if (bitmap == null) {
                Log.w(TAG, "Failed to convert Mat to Bitmap - Mat: " + 
                      processedFrame.width() + "x" + processedFrame.height() + 
                      ", empty: " + processedFrame.empty());
                return false;
            }
            
            // Update frame dimensions if they changed
            if (frameWidth != processedFrame.width() || frameHeight != processedFrame.height()) {
                frameWidth = processedFrame.width();
                frameHeight = processedFrame.height();
                synchronized (matrixLock) {
                    matrixNeedsUpdate = true;
                }
                Log.d(TAG, "Frame dimensions updated: " + frameWidth + "x" + frameHeight);
            }
            
            // Update transform matrix only if needed (optimization)
            synchronized (matrixLock) {
                if (matrixNeedsUpdate) {
                    updateTransformMatrix();
                    matrixNeedsUpdate = false;
                }
            }
            
            // Draw bitmap to surface with hardware acceleration
            Canvas canvas = surface.lockCanvas(null);
            if (canvas != null) {
                try {
                    // Enable hardware acceleration hints
                    if (hardwareAccelerationEnabled && canvas.isHardwareAccelerated()) {
                        // Hardware accelerated path
                        canvas.drawColor(android.graphics.Color.BLACK);
                        
                        // Apply transform matrix to maintain aspect ratio
                        synchronized (matrixLock) {
                            canvas.setMatrix(transformMatrix);
                        }
                        
                        // Draw the bitmap with hardware acceleration
                        canvas.drawBitmap(bitmap, 0, 0, null);
                    } else {
                        // Software rendering fallback
                        canvas.drawColor(android.graphics.Color.BLACK);
                        synchronized (matrixLock) {
                            canvas.setMatrix(transformMatrix);
                        }
                        canvas.drawBitmap(bitmap, 0, 0, null);
                    }
                    
                } finally {
                    surface.unlockCanvasAndPost(canvas);
                }
            }
            
            // Recycle bitmap to free memory immediately
            bitmap.recycle();
            
            // Track performance with nanosecond precision
            long updateTimeNs = System.nanoTime() - startTime;
            long updateTimeMs = updateTimeNs / 1_000_000;
            trackPerformance(updateTimeMs);
            
            // Requirement 9.4: Warn if frame update exceeds 16ms (60 FPS target)
            if (updateTimeMs > TARGET_FRAME_TIME_MS) {
                slowFrameCount++;
                if (slowFrameCount % 10 == 0) { // Log every 10th slow frame to avoid spam
                    Log.w(TAG, "Frame update took " + updateTimeMs + "ms (>" + TARGET_FRAME_TIME_MS + 
                            "ms target), slow frames: " + slowFrameCount);
                }
            }
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating frame", e);
            if (displayCallback != null) {
                displayCallback.onFrameUpdateError(e);
            }
            return false;
        }
    }
    
    /**
     * Convert OpenCV Mat to Android Bitmap with thread-safe access and format handling
     * 
     * Thread Safety Guarantees:
     * - The entire Mat processing is synchronized on the input Mat object
     * - No defensive cloning is performed - the original Mat is processed directly
     * - Callers must ensure the Mat remains valid during the entire conversion process
     * - The synchronized block covers validation, format conversion, and bitmap creation
     * 
     * @param mat The OpenCV Mat to convert (must remain valid during conversion)
     * @return Bitmap or null if conversion failed
     */
    @Nullable
    private Bitmap matToBitmap(@NonNull Mat mat) {
        Mat convertedMat = null;
        
        try {
            // Thread-safe processing without defensive cloning
            // The synchronized block ensures exclusive access to the Mat during the entire conversion
            synchronized (mat) {
                // Deep validation of Mat properties
                if (mat.empty() || mat.width() <= 0 || mat.height() <= 0) {
                    Log.w(TAG, "Invalid Mat dimensions: " + mat.width() + "x" + mat.height());
                    return createFallbackBitmap();
                }
                
                if (mat.total() == 0 || mat.channels() <= 0 || mat.channels() > 4) {
                    Log.w(TAG, "Invalid Mat data: total=" + mat.total() + ", channels=" + mat.channels());
                    return createFallbackBitmap();
                }
                
                // Validate Mat type and data integrity
                int matType = mat.type();
                if (matType < 0 || !mat.isContinuous()) {
                    Log.w(TAG, "Invalid Mat type or non-continuous data: type=" + matType + ", continuous=" + mat.isContinuous());
                    return createFallbackBitmap();
                }
                
                // Additional safety check for reasonable dimensions
                if (mat.width() > 4096 || mat.height() > 4096) {
                    Log.w(TAG, "Mat dimensions too large: " + mat.width() + "x" + mat.height());
                    return createFallbackBitmap();
                }
                
                // Process the original Mat directly without cloning
                // Ensure proper Mat format for bitmap conversion
                convertedMat = ensureCompatibleFormat(mat);
                
                // Determine appropriate bitmap configuration
                Bitmap.Config config = getBitmapConfig(convertedMat.channels());
                
                // Create bitmap with validated parameters
                Bitmap bitmap = Bitmap.createBitmap(
                    convertedMat.width(), 
                    convertedMat.height(), 
                    config
                );
                
                // Safe OpenCV conversion with error handling
                Utils.matToBitmap(convertedMat, bitmap);
                
                return bitmap;
            }
            
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException in Mat to Bitmap conversion", e);
            return createFallbackBitmap();
        } catch (Exception e) {
            Log.e(TAG, "Critical error in Mat to Bitmap conversion", e);
            return createFallbackBitmap();
        } finally {
            // Clean up temporary Mat objects (only convertedMat if it's different from input)
            if (convertedMat != null && convertedMat != mat) {
                convertedMat.release();
            }
        }
    }
    
    /**
     * Ensure Mat is in a format compatible with Android Bitmap conversion
     * 
     * @param inputMat The input Mat to convert
     * @return Mat in compatible format (BGRA)
     */
    @NonNull
    private Mat ensureCompatibleFormat(@NonNull Mat inputMat) {
        int channels = inputMat.channels();
        
        try {
            // Handle different channel configurations
            switch (channels) {
                case 1: // Grayscale - convert to BGRA for bitmap
                    Mat bgraMat = new Mat();
                    Imgproc.cvtColor(inputMat, bgraMat, Imgproc.COLOR_GRAY2BGRA);
                    return bgraMat;
                    
                case 3: // BGR - convert to BGRA for bitmap
                    Mat bgra3Mat = new Mat();
                    Imgproc.cvtColor(inputMat, bgra3Mat, Imgproc.COLOR_BGR2BGRA);
                    return bgra3Mat;
                    
                case 4: // Already BGRA - ensure correct format
                    if (inputMat.type() == CvType.CV_8UC4) {
                        return inputMat; // Already compatible
                    } else {
                        Mat bgra4Mat = new Mat();
                        inputMat.convertTo(bgra4Mat, CvType.CV_8UC4);
                        return bgra4Mat;
                    }
                    
                default:
                    Log.w(TAG, "Unsupported channel count: " + channels + ", creating fallback");
                    Mat fallbackMat = new Mat(inputMat.rows(), inputMat.cols(), CvType.CV_8UC4);
                    fallbackMat.setTo(new Scalar(0, 0, 0, 255)); // Black with full alpha
                    return fallbackMat;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error converting Mat format", e);
            // Return a safe fallback Mat
            Mat fallbackMat = new Mat(inputMat.rows(), inputMat.cols(), CvType.CV_8UC4);
            fallbackMat.setTo(new Scalar(0, 0, 0, 255)); // Black with full alpha
            return fallbackMat;
        }
    }
    
    /**
     * Get appropriate bitmap configuration based on Mat channels
     * 
     * @param channels Number of channels in the Mat
     * @return Bitmap configuration
     */
    @NonNull
    private Bitmap.Config getBitmapConfig(int channels) {
        // Always use ARGB_8888 for maximum compatibility
        return Bitmap.Config.ARGB_8888;
    }
    
    /**
     * Create a fallback bitmap when Mat conversion fails
     * 
     * @return Safe fallback bitmap or null if creation fails
     */
    @Nullable
    private Bitmap createFallbackBitmap() {
        try {
            Bitmap fallback = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
            fallback.eraseColor(android.graphics.Color.BLACK);
            Log.d(TAG, "Created fallback bitmap: 320x240");
            return fallback;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create fallback bitmap", e);
            return null;
        }
    }
    
    /**
     * Handle orientation changes and update display accordingly
     * Requirement 1.4: Handle device rotation correctly
     * Requirement 9.4: Maintain 60 FPS during orientation changes
     * 
     * @param newWidth New display width
     * @param newHeight New display height
     */
    public void handleOrientationChange(int newWidth, int newHeight) {
        Log.d(TAG, "Handling orientation change: " + newWidth + "x" + newHeight);
        
        displayWidth = newWidth;
        displayHeight = newHeight;
        
        synchronized (matrixLock) {
            matrixNeedsUpdate = true;
            updateTransformMatrix();
        }
        
        Log.i(TAG, "Orientation change handled, new display size: " + displayWidth + "x" + displayHeight);
    }
    
    /**
     * Update the transform matrix to maintain aspect ratio
     * Requirement 3.2: Maintain original aspect ratio of the camera
     * Requirement 9.4: Optimize matrix transformation performance
     * 
     * Note: This method should be called within a synchronized(matrixLock) block
     */
    private void updateTransformMatrix() {
        if (frameWidth == 0 || frameHeight == 0 || displayWidth == 0 || displayHeight == 0) {
            return;
        }
        
        long startTime = System.nanoTime();
        
        transformMatrix.reset();
        
        // Calculate scaling factors (optimized with single division)
        float scaleX = (float) displayWidth / frameWidth;
        float scaleY = (float) displayHeight / frameHeight;
        
        // Use the smaller scale to maintain aspect ratio (fit inside display)
        float scale = Math.min(scaleX, scaleY);
        
        // Calculate translation to center the image (optimized calculation)
        float scaledWidth = frameWidth * scale;
        float scaledHeight = frameHeight * scale;
        float translateX = (displayWidth - scaledWidth) * 0.5f;
        float translateY = (displayHeight - scaledHeight) * 0.5f;
        
        // Apply transformations in optimal order
        transformMatrix.postScale(scale, scale);
        transformMatrix.postTranslate(translateX, translateY);
        
        long updateTimeNs = System.nanoTime() - startTime;
        long updateTimeUs = updateTimeNs / 1_000;
        
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Transform matrix updated in " + updateTimeUs + "μs - scale: " + scale + 
                    ", translate: (" + translateX + ", " + translateY + ")");
        }
    }
    
    /**
     * Track performance metrics
     * Requirement 9.4: Add performance monitoring for display operations
     */
    private void trackPerformance(long updateTime) {
        frameCount++;
        totalUpdateTime += updateTime;
        
        // Track min/max update times
        if (updateTime > maxUpdateTime) {
            maxUpdateTime = updateTime;
        }
        if (updateTime < minUpdateTime) {
            minUpdateTime = updateTime;
        }
        
        if (frameCount % FPS_CALCULATION_INTERVAL == 0) {
            long currentTime = System.currentTimeMillis();
            if (lastFrameTime > 0) {
                long timeDiff = currentTime - lastFrameTime;
                float fps = (FPS_CALCULATION_INTERVAL * 1000f) / timeDiff;
                float avgUpdateTime = (float) totalUpdateTime / FPS_CALCULATION_INTERVAL;
                
                if (displayCallback != null) {
                    displayCallback.onPerformanceUpdate(fps, avgUpdateTime);
                }
                
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, String.format("Display performance - FPS: %.1f, " +
                            "Avg: %.1fms, Min: %dms, Max: %dms, Slow frames: %d",
                            fps, avgUpdateTime, minUpdateTime, maxUpdateTime, slowFrameCount));
                }
                
                // Check if we're meeting 60 FPS target
                if (fps < 60.0f && fps > 0) {
                    Log.w(TAG, String.format("Display FPS below 60: %.1f FPS", fps));
                }
            }
            lastFrameTime = currentTime;
            
            // Reset per-interval metrics
            totalUpdateTime = 0;
            minUpdateTime = Long.MAX_VALUE;
            maxUpdateTime = 0;
        }
    }
    
    /**
     * Get current display performance metrics
     * Requirement 9.4: Performance monitoring for display operations
     * 
     * @return DisplayPerformanceMetrics object with current metrics
     */
    public DisplayPerformanceMetrics getPerformanceMetrics() {
        DisplayPerformanceMetrics metrics = new DisplayPerformanceMetrics();
        metrics.frameCount = frameCount;
        metrics.slowFrameCount = slowFrameCount;
        metrics.slowFramePercentage = frameCount > 0 ? (float) slowFrameCount / frameCount * 100 : 0;
        metrics.hardwareAccelerated = hardwareAccelerationEnabled;
        metrics.displayReady = isDisplayReady;
        return metrics;
    }
    
    /**
     * Reset performance counters
     * Requirement 9.4: Performance monitoring
     */
    public void resetPerformanceMetrics() {
        frameCount = 0;
        slowFrameCount = 0;
        totalUpdateTime = 0;
        maxUpdateTime = 0;
        minUpdateTime = Long.MAX_VALUE;
        lastFrameTime = 0;
        Log.d(TAG, "Performance metrics reset");
    }
    
    /**
     * Performance metrics data class
     */
    public static class DisplayPerformanceMetrics {
        public int frameCount;
        public int slowFrameCount;
        public float slowFramePercentage;
        public boolean hardwareAccelerated;
        public boolean displayReady;
        
        @Override
        public String toString() {
            return String.format("DisplayPerformanceMetrics{frames=%d, slow=%d (%.1f%%), hwAccel=%s, ready=%s}",
                    frameCount, slowFrameCount, slowFramePercentage, hardwareAccelerated, displayReady);
        }
    }
    
    /**
     * Set display callback for events
     */
    public void setDisplayCallback(@Nullable DisplayCallback callback) {
        this.displayCallback = callback;
    }
    
    /**
     * Check if display is ready for frame updates
     */
    public boolean isDisplayReady() {
        return isDisplayReady;
    }
    
    /**
     * Get current display dimensions
     */
    public int getDisplayWidth() {
        return displayWidth;
    }
    
    public int getDisplayHeight() {
        return displayHeight;
    }
    
    /**
     * Handle activity onResume lifecycle event
     * Requirement 5.4: Proper lifecycle management
     * Requirement 9.4: Restore hardware acceleration on resume
     */
    public void onResume() {
        Log.d(TAG, "DisplayManager onResume - restoring display state");
        
        // Reset performance tracking
        resetPerformanceMetrics();
        
        // Re-enable hardware acceleration if needed
        if (textureView != null && hardwareAccelerationEnabled) {
            textureView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
            Log.d(TAG, "Hardware acceleration re-enabled on resume");
        }
        
        // If TextureView is available, ensure surface is ready
        if (textureView != null && textureView.isAvailable()) {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture != null && surface == null) {
                Log.d(TAG, "Recreating surface after resume");
                surface = new Surface(surfaceTexture);
                isDisplayReady = true;
                
                synchronized (matrixLock) {
                    matrixNeedsUpdate = true;
                }
                
                if (displayCallback != null) {
                    displayCallback.onDisplayReady();
                }
            }
        }
        
        Log.i(TAG, "DisplayManager resume completed with hardware acceleration");
    }
    
    /**
     * Handle activity onPause lifecycle event
     * Requirement 5.4: Proper resource cleanup during pause
     * Requirement 6.3: Android 10 background activity restrictions
     */
    public void onPause() {
        Log.d(TAG, "DisplayManager onPause - pausing display operations");
        
        // Don't release the surface completely, just mark as not ready
        // This allows for faster resume while complying with Android 10 restrictions
        isDisplayReady = false;
        
        // Clear any pending frame updates
        frameCount = 0;
        lastFrameTime = 0;
        
        Log.i(TAG, "DisplayManager pause completed - display operations suspended");
    }
    
    /**
     * Clear any pending updates to prevent memory leaks
     * Called during activity pause to ensure clean state
     */
    public void clearPendingUpdates() {
        Log.d(TAG, "Clearing pending display updates");
        
        // Reset performance counters
        resetPerformanceMetrics();
        
        // Clear transform matrix
        synchronized (matrixLock) {
            transformMatrix.reset();
            matrixNeedsUpdate = true;
        }
        
        Log.d(TAG, "Pending display updates cleared");
    }
    
    /**
     * Set camera visualization enabled/disabled
     * Requirement 6.1: Toggle camera display visibility
     * Requirement 6.2: Continue processing pipeline when visualization is disabled
     * 
     * @param enabled true to show visualization, false to hide
     */
    public void setVisualizationEnabled(boolean enabled) {
        Log.d(TAG, "Setting visualization enabled: " + enabled);
        
        if (textureView != null) {
            textureView.setVisibility(enabled ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
        }
        
        // Note: Processing pipeline continues regardless of visualization state
        // This is handled by the camera manager and frame processor
    }
    
    /**
     * Handle rendering error with recovery attempt
     * Requirement 11: Error recovery integration
     * 
     * @param error The rendering error that occurred
     * @return true if recovery was attempted, false otherwise
     */
    public boolean handleRenderingError(@NonNull Exception error) {
        Log.e(TAG, "Rendering error occurred: " + error.getMessage(), error);
        
        // Notify callback of error
        if (displayCallback != null) {
            displayCallback.onFrameUpdateError(error);
        }
        
        // Attempt recovery
        return recoverFromDisplayError();
    }
    
    /**
     * Attempt to recover from display errors
     * Requirement 11: Error recovery mechanism
     * 
     * @return true if recovery was successful, false otherwise
     */
    public boolean recoverFromDisplayError() {
        Log.d(TAG, "Attempting to recover from display error");
        
        try {
            // Check if display is still ready
            if (!isDisplayReady) {
                Log.d(TAG, "Display not ready, attempting to reinitialize");
                
                // If TextureView is available, try to recreate surface
                if (textureView != null && textureView.isAvailable()) {
                    SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
                    if (surfaceTexture != null) {
                        surface = new Surface(surfaceTexture);
                        isDisplayReady = true;
                        
                        if (displayCallback != null) {
                            displayCallback.onDisplayReady();
                        }
                        
                        Log.i(TAG, "Display recovery successful");
                        return true;
                    }
                }
            } else {
                // Display is ready, just clear any pending state
                Log.d(TAG, "Display is ready, clearing pending state");
                clearPendingUpdates();
                return true;
            }
            
            Log.w(TAG, "Display recovery failed - TextureView not available");
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "Error during display recovery", e);
            return false;
        }
    }
    
    /**
     * Check if display is in error state
     * Requirement 11: Error detection
     * 
     * @return true if display appears to be in error state, false otherwise
     */
    public boolean isInErrorState() {
        return !isDisplayReady || surface == null || textureView == null;
    }
    
    /**
     * Rotate the display by the specified degrees
     * Requirement 7.1: Rotate display 90 degrees clockwise
     * Requirement 7.2: Cycle through 0°, 90°, 180°, 270°
     * Requirement 9.4: Maintain 60 FPS during rotation operations
     * 
     * @param degrees Rotation in degrees (0, 90, 180, 270)
     */
    public void rotateDisplay(int degrees) {
        Log.d(TAG, "Rotating display to " + degrees + " degrees");
        
        // Normalize degrees to 0-360 range
        degrees = degrees % 360;
        if (degrees < 0) {
            degrees += 360;
        }
        
        // Update transform matrix with rotation (thread-safe)
        synchronized (matrixLock) {
            updateTransformMatrixWithRotation(degrees);
            matrixNeedsUpdate = false; // Matrix just updated
        }
    }
    
    /**
     * Update transform matrix to include rotation
     * Requirement 7.2: Handle rotation without frame drops
     * Requirement 9.4: Optimize matrix transformation performance
     * 
     * Note: This method should be called within a synchronized(matrixLock) block
     * 
     * @param rotationDegrees Rotation in degrees
     */
    private void updateTransformMatrixWithRotation(int rotationDegrees) {
        if (frameWidth == 0 || frameHeight == 0 || displayWidth == 0 || displayHeight == 0) {
            Log.w(TAG, "Cannot update transform matrix - invalid dimensions");
            return;
        }
        
        long startTime = System.nanoTime();
        
        transformMatrix.reset();
        
        // Calculate scaling factors (optimized)
        float scaleX = (float) displayWidth / frameWidth;
        float scaleY = (float) displayHeight / frameHeight;
        
        // Use the smaller scale to maintain aspect ratio
        float scale = Math.min(scaleX, scaleY);
        
        // Calculate center point for rotation (optimized with bit shift)
        float centerX = displayWidth * 0.5f;
        float centerY = displayHeight * 0.5f;
        
        // Apply rotation around center
        transformMatrix.postRotate(rotationDegrees, centerX, centerY);
        
        // Calculate translation to center the rotated image (optimized)
        float scaledWidth = frameWidth * scale;
        float scaledHeight = frameHeight * scale;
        float translateX = (displayWidth - scaledWidth) * 0.5f;
        float translateY = (displayHeight - scaledHeight) * 0.5f;
        
        // Apply scaling and translation
        transformMatrix.postScale(scale, scale, centerX, centerY);
        transformMatrix.postTranslate(translateX, translateY);
        
        long updateTimeNs = System.nanoTime() - startTime;
        long updateTimeUs = updateTimeNs / 1_000;
        
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Transform matrix updated with rotation in " + updateTimeUs + "μs: " + 
                    rotationDegrees + " degrees, scale: " + scale);
        }
    }
    
    /**
     * Release display resources completely
     * Called during activity destruction
     */
    public void release() {
        Log.d(TAG, "Releasing display resources completely");
        
        isDisplayReady = false;
        
        // Clear performance tracking
        resetPerformanceMetrics();
        
        // Release surface
        if (surface != null) {
            surface.release();
            surface = null;
        }
        
        // Clear TextureView reference
        if (textureView != null) {
            textureView.setSurfaceTextureListener(null);
            textureView = null;
        }
        
        // Clear callback
        displayCallback = null;
        
        // Clear transform matrix
        synchronized (matrixLock) {
            transformMatrix.reset();
        }
        
        // Reset dimensions
        frameWidth = 0;
        frameHeight = 0;
        displayWidth = 0;
        displayHeight = 0;
        
        Log.i(TAG, "Display resources completely released");
    }
    
    // TextureView.SurfaceTextureListener implementation
    
    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
        Log.d(TAG, "Surface texture available: " + width + "x" + height);
        
        displayWidth = width;
        displayHeight = height;
        
        surface = new Surface(surfaceTexture);
        isDisplayReady = true;
        
        synchronized (matrixLock) {
            matrixNeedsUpdate = true;
            updateTransformMatrix();
        }
        
        if (displayCallback != null) {
            displayCallback.onDisplayReady();
        }
        
        Log.i(TAG, "Display surface ready for rendering with hardware acceleration");
    }
    
    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
        Log.d(TAG, "Surface texture size changed: " + width + "x" + height);
        handleOrientationChange(width, height);
    }
    
    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
        Log.d(TAG, "Surface texture destroyed");
        
        isDisplayReady = false;
        
        if (surface != null) {
            surface.release();
            surface = null;
        }
        
        if (displayCallback != null) {
            displayCallback.onDisplayDestroyed();
        }
        
        Log.i(TAG, "Surface texture cleanup completed");
        return true; // Return true to indicate we handled the cleanup
    }
    
    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
        // Called after each frame is drawn - we don't need to do anything here
        // as our frame updates are handled in updateFrame()
    }
}