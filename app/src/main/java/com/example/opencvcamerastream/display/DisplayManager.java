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
        Log.d(TAG, "DisplayManager created");
    }
    
    /**
     * Set up the TextureView for display
     * Requirement 3.1: Display processed frames on screen
     * 
     * @param textureView The TextureView to use for display
     * @return true if setup was successful, false otherwise
     */
    public boolean setupDisplay(@NonNull TextureView textureView) {
        Log.d(TAG, "Setting up display with TextureView");
        
        this.textureView = textureView;
        this.textureView.setSurfaceTextureListener(this);
        
        // If the TextureView is already available, set up immediately
        if (textureView.isAvailable()) {
            Log.d(TAG, "TextureView already available, setting up surface");
            onSurfaceTextureAvailable(textureView.getSurfaceTexture(), 
                    textureView.getWidth(), textureView.getHeight());
        }
        
        Log.i(TAG, "Display setup completed");
        return true;
    }
    
    /**
     * Update the display with a new processed frame
     * Requirements 3.1, 3.3: Display processed frame within 16ms for 60 FPS UI
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
        if (processedFrame == null || processedFrame.empty() || 
            processedFrame.width() <= 0 || processedFrame.height() <= 0) {
            Log.w(TAG, "Invalid processed frame - skipping display update");
            return false;
        }
        
        long startTime = System.currentTimeMillis();
        
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
                updateTransformMatrix();
                Log.d(TAG, "Frame dimensions updated: " + frameWidth + "x" + frameHeight);
            }
            
            // Draw bitmap to surface
            Canvas canvas = surface.lockCanvas(null);
            if (canvas != null) {
                try {
                    // Clear canvas
                    canvas.drawColor(android.graphics.Color.BLACK);
                    
                    // Apply transform matrix to maintain aspect ratio
                    canvas.setMatrix(transformMatrix);
                    
                    // Draw the bitmap
                    canvas.drawBitmap(bitmap, 0, 0, null);
                    
                } finally {
                    surface.unlockCanvasAndPost(canvas);
                }
            }
            
            // Recycle bitmap to free memory
            bitmap.recycle();
            
            // Track performance
            long updateTime = System.currentTimeMillis() - startTime;
            trackPerformance(updateTime);
            
            // Requirement 3.3: Update display within 16ms
            if (updateTime > 16) {
                Log.w(TAG, "Frame update took " + updateTime + "ms (>16ms target)");
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
     * Convert OpenCV Mat to Android Bitmap with enhanced safety and format handling
     * 
     * @param mat The OpenCV Mat to convert
     * @return Bitmap or null if conversion failed
     */
    @Nullable
    private Bitmap matToBitmap(@NonNull Mat mat) {
        // Thread-safe Mat cloning to prevent concurrent modification
        Mat safeMat = null;
        Mat convertedMat = null;
        
        try {
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
                
                // Create a safe clone to prevent memory corruption
                safeMat = mat.clone();
            }
            
            // Ensure proper Mat format for bitmap conversion
            convertedMat = ensureCompatibleFormat(safeMat);
            
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
            
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException in Mat to Bitmap conversion", e);
            return createFallbackBitmap();
        } catch (Exception e) {
            Log.e(TAG, "Critical error in Mat to Bitmap conversion", e);
            return createFallbackBitmap();
        } finally {
            // Clean up temporary Mat objects
            if (safeMat != null) {
                safeMat.release();
            }
            if (convertedMat != null && convertedMat != safeMat) {
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
     * 
     * @param newWidth New display width
     * @param newHeight New display height
     */
    public void handleOrientationChange(int newWidth, int newHeight) {
        Log.d(TAG, "Handling orientation change: " + newWidth + "x" + newHeight);
        
        displayWidth = newWidth;
        displayHeight = newHeight;
        
        updateTransformMatrix();
        
        Log.i(TAG, "Orientation change handled, new display size: " + displayWidth + "x" + displayHeight);
    }
    
    /**
     * Update the transform matrix to maintain aspect ratio
     * Requirement 3.2: Maintain original aspect ratio of the camera
     */
    private void updateTransformMatrix() {
        if (frameWidth == 0 || frameHeight == 0 || displayWidth == 0 || displayHeight == 0) {
            return;
        }
        
        transformMatrix.reset();
        
        // Calculate scaling factors
        float scaleX = (float) displayWidth / frameWidth;
        float scaleY = (float) displayHeight / frameHeight;
        
        // Use the smaller scale to maintain aspect ratio (fit inside display)
        float scale = Math.min(scaleX, scaleY);
        
        // Calculate translation to center the image
        float translateX = (displayWidth - frameWidth * scale) / 2f;
        float translateY = (displayHeight - frameHeight * scale) / 2f;
        
        // Apply transformations
        transformMatrix.postScale(scale, scale);
        transformMatrix.postTranslate(translateX, translateY);
        
        Log.d(TAG, "Transform matrix updated - scale: " + scale + 
                ", translate: (" + translateX + ", " + translateY + ")");
    }
    
    /**
     * Track performance metrics
     */
    private void trackPerformance(long updateTime) {
        frameCount++;
        
        if (frameCount % FPS_CALCULATION_INTERVAL == 0) {
            long currentTime = System.currentTimeMillis();
            if (lastFrameTime > 0) {
                long timeDiff = currentTime - lastFrameTime;
                float fps = (FPS_CALCULATION_INTERVAL * 1000f) / timeDiff;
                
                if (displayCallback != null) {
                    displayCallback.onPerformanceUpdate(fps, updateTime);
                }
                
                Log.v(TAG, "Display performance - FPS: " + String.format("%.1f", fps) + 
                        ", Last update: " + updateTime + "ms");
            }
            lastFrameTime = currentTime;
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
     */
    public void onResume() {
        Log.d(TAG, "DisplayManager onResume - restoring display state");
        
        // Reset performance tracking
        frameCount = 0;
        lastFrameTime = 0;
        
        // If TextureView is available, ensure surface is ready
        if (textureView != null && textureView.isAvailable()) {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture != null && surface == null) {
                Log.d(TAG, "Recreating surface after resume");
                surface = new Surface(surfaceTexture);
                isDisplayReady = true;
                
                if (displayCallback != null) {
                    displayCallback.onDisplayReady();
                }
            }
        }
        
        Log.i(TAG, "DisplayManager resume completed");
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
        frameCount = 0;
        lastFrameTime = 0;
        
        // Clear transform matrix
        transformMatrix.reset();
        
        Log.d(TAG, "Pending display updates cleared");
    }
    
    /**
     * Release display resources completely
     * Called during activity destruction
     */
    public void release() {
        Log.d(TAG, "Releasing display resources completely");
        
        isDisplayReady = false;
        
        // Clear performance tracking
        frameCount = 0;
        lastFrameTime = 0;
        
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
        transformMatrix.reset();
        
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
        
        updateTransformMatrix();
        
        if (displayCallback != null) {
            displayCallback.onDisplayReady();
        }
        
        Log.i(TAG, "Display surface ready for rendering");
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