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
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Convert Mat to Bitmap
            Bitmap bitmap = matToBitmap(processedFrame);
            if (bitmap == null) {
                Log.w(TAG, "Failed to convert Mat to Bitmap");
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
     * Convert OpenCV Mat to Android Bitmap
     * 
     * @param mat The OpenCV Mat to convert
     * @return Bitmap or null if conversion failed
     */
    @Nullable
    private Bitmap matToBitmap(@NonNull Mat mat) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(mat.width(), mat.height(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, bitmap);
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert Mat to Bitmap", e);
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
     * Release display resources
     */
    public void release() {
        Log.d(TAG, "Releasing display resources");
        
        isDisplayReady = false;
        
        if (surface != null) {
            surface.release();
            surface = null;
        }
        
        if (textureView != null) {
            textureView.setSurfaceTextureListener(null);
            textureView = null;
        }
        
        displayCallback = null;
        
        Log.i(TAG, "Display resources released");
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