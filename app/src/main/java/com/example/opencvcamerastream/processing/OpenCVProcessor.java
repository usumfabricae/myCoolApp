package com.example.opencvcamerastream.processing;

import android.graphics.Bitmap;
import android.media.Image;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import java.nio.ByteBuffer;

import com.example.opencvcamerastream.error.ErrorHandler;
import com.example.opencvcamerastream.error.PerformanceMonitor;

/**
 * OpenCVProcessor handles all OpenCV image processing operations
 * 
 * This class provides:
 * - OpenCV initialization and setup
 * - Frame conversion utilities (Image to Mat, Mat to Bitmap)
 * - Basic image processing operations (grayscale conversion)
 * - Error handling and fallback mechanisms
 * - Performance optimization for Android 10 compliance
 */
public class OpenCVProcessor {
    
    private static final String TAG = "OpenCVProcessor";
    
    // Processing modes
    public enum ProcessingMode {
        PASSTHROUGH,    // No processing, original frame
        GRAYSCALE,      // Convert to grayscale
        EDGE_DETECTION, // Canny edge detection (future implementation)
        COLOR_FILTER    // Color space transformations (future implementation)
    }
    
    // Processing configuration
    public static class ProcessingConfig {
        public ProcessingMode mode = ProcessingMode.GRAYSCALE;
        public boolean enablePerformanceOptimization = true;
        public int maxProcessingTimeMs = 50; // Requirement 2.3: within 50ms
        
        public ProcessingConfig() {}
        
        public ProcessingConfig(ProcessingMode mode) {
            this.mode = mode;
        }
    }
    
    // Processing callback interface
    public interface ProcessingCallback {
        void onFrameProcessed(@NonNull Mat processedFrame, long processingTimeMs);
        void onProcessingError(@NonNull Exception error, @Nullable Mat originalFrame);
        void onProcessingTimeout(@NonNull Mat originalFrame, long timeoutMs);
    }
    
    private ProcessingConfig config;
    private ProcessingCallback callback;
    private boolean isInitialized = false;
    private long lastProcessingTime = 0;
    
    // Error handling and performance monitoring
    private ErrorHandler errorHandler;
    private PerformanceMonitor performanceMonitor;
    private boolean fallbackMode = false;
    private int consecutiveErrors = 0;
    private static final int MAX_CONSECUTIVE_ERRORS = 5;
    
    // Performance monitoring
    private static class PerformanceMetrics {
        long totalFrames = 0;
        long totalProcessingTime = 0;
        long maxProcessingTime = 0;
        long timeoutCount = 0;
        long errorCount = 0;
        
        double getAverageProcessingTime() {
            return totalFrames > 0 ? (double) totalProcessingTime / totalFrames : 0;
        }
        
        void recordProcessing(long processingTime) {
            totalFrames++;
            totalProcessingTime += processingTime;
            maxProcessingTime = Math.max(maxProcessingTime, processingTime);
        }
        
        void recordTimeout() {
            timeoutCount++;
        }
        
        void recordError() {
            errorCount++;
        }
    }
    
    private final PerformanceMetrics metrics = new PerformanceMetrics();
    
    public OpenCVProcessor() {
        this.config = new ProcessingConfig();
    }
    
    public OpenCVProcessor(@NonNull ProcessingConfig config) {
        this.config = config;
    }
    
    /**
     * Set error handler for processing operations
     */
    public void setErrorHandler(@Nullable ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }
    
    /**
     * Set performance monitor for optimization
     */
    public void setPerformanceMonitor(@Nullable PerformanceMonitor performanceMonitor) {
        this.performanceMonitor = performanceMonitor;
    }
    
    /**
     * Initialize OpenCV processor
     * Requirement 5.1: Initialize within 3 seconds
     */
    public boolean initialize() {
        Log.d(TAG, "Initializing OpenCV processor");
        
        try {
            long startTime = System.currentTimeMillis();
            
            // OpenCV initialization will be handled by MainActivity's OpenCV loader callback
            // This method sets up the processor's internal state
            
            isInitialized = true;
            
            long initTime = System.currentTimeMillis() - startTime;
            Log.d(TAG, "OpenCV processor initialized in " + initTime + "ms");
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize OpenCV processor", e);
            isInitialized = false;
            return false;
        }
    }
    
    /**
     * Set processing configuration
     */
    public void setProcessingConfig(@NonNull ProcessingConfig config) {
        this.config = config;
        Log.d(TAG, "Processing config updated: mode=" + config.mode);
    }
    
    /**
     * Set processing callback
     */
    public void setProcessingCallback(@Nullable ProcessingCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Check if processor is initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * Check if processor is in fallback mode
     */
    public boolean isFallbackMode() {
        return fallbackMode;
    }
    
    /**
     * Exit fallback mode and resume normal processing
     */
    public void exitFallbackMode() {
        if (fallbackMode) {
            fallbackMode = false;
            consecutiveErrors = 0;
            Log.i(TAG, "Exited fallback mode, resuming normal processing");
        }
    }
    
    /**
     * Process camera frame with error handling and fallback
     * Requirement 2.1: Pass frame to OpenCV for processing
     * Requirement 2.3: Return processed frame within 50ms
     * Requirement 4.3: Fall back to original frame on processing failure
     */
    public Mat processFrame(@NonNull Mat inputFrame) {
        if (!isInitialized) {
            Log.w(TAG, "Processor not initialized, returning original frame");
            return inputFrame.clone();
        }
        
        // Check if in fallback mode due to consecutive errors
        if (fallbackMode) {
            Log.d(TAG, "In fallback mode, returning original frame");
            return inputFrame.clone();
        }
        
        long startTime = System.currentTimeMillis();
        Mat processedFrame = null;
        
        try {
            // Get performance recommendations if available
            PerformanceMonitor.ProcessingRecommendation recommendation = null;
            if (performanceMonitor != null) {
                recommendation = performanceMonitor.getProcessingRecommendation();
                
                // Adjust processing based on performance level
                if (!recommendation.enableAdvancedProcessing) {
                    Log.d(TAG, "Advanced processing disabled due to performance constraints");
                    processedFrame = inputFrame.clone();
                } else {
                    // Check for timeout before processing
                    if (config.enablePerformanceOptimization && 
                        lastProcessingTime > recommendation.maxProcessingTimeMs) {
                        Log.w(TAG, "Previous processing exceeded timeout, skipping frame");
                        metrics.recordTimeout();
                        if (performanceMonitor != null) {
                            performanceMonitor.recordFrameDrop("processing timeout");
                        }
                        if (callback != null) {
                            callback.onProcessingTimeout(inputFrame, lastProcessingTime);
                        }
                        return inputFrame.clone();
                    }
                    
                    // Apply processing based on mode and performance level
                    processedFrame = applyProcessing(inputFrame, recommendation);
                }
            } else {
                // No performance monitor - use default processing
                processedFrame = applyProcessing(inputFrame, null);
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            lastProcessingTime = processingTime;
            metrics.recordProcessing(processingTime);
            
            // Record processing time with performance monitor
            if (performanceMonitor != null) {
                performanceMonitor.recordProcessingTime(processingTime);
            }
            
            Log.v(TAG, "Frame processed in " + processingTime + "ms, mode: " + config.mode);
            
            // Check if processing exceeded timeout
            int maxTime = recommendation != null ? recommendation.maxProcessingTimeMs : config.maxProcessingTimeMs;
            if (processingTime > maxTime) {
                Log.w(TAG, "Processing exceeded timeout: " + processingTime + "ms > " + maxTime + "ms");
                metrics.recordTimeout();
                if (callback != null) {
                    callback.onProcessingTimeout(inputFrame, processingTime);
                }
            }
            
            // Reset consecutive error count on success
            if (consecutiveErrors > 0) {
                consecutiveErrors = 0;
                if (errorHandler != null) {
                    errorHandler.resetErrorCounters();
                }
            }
            
            if (callback != null) {
                callback.onFrameProcessed(processedFrame, processingTime);
            }
            
            return processedFrame;
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing frame", e);
            metrics.recordError();
            consecutiveErrors++;
            
            // Handle error through error handler
            if (errorHandler != null) {
                errorHandler.handleOpenCVProcessingError(e, true);
            }
            
            if (callback != null) {
                callback.onProcessingError(e, inputFrame);
            }
            
            // Check if we should enter fallback mode
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                Log.w(TAG, "Entering fallback mode due to consecutive errors: " + consecutiveErrors);
                fallbackMode = true;
            }
            
            // Requirement 4.3: Fall back to original frame on processing failure
            return inputFrame != null ? inputFrame.clone() : new Mat();
        }
    }
    
    /**
     * Apply processing based on mode and performance recommendations
     */
    private Mat applyProcessing(@NonNull Mat inputFrame, 
                               @Nullable PerformanceMonitor.ProcessingRecommendation recommendation) {
        Mat processedFrame;
        
        switch (config.mode) {
            case PASSTHROUGH:
                processedFrame = inputFrame.clone();
                break;
                
            case GRAYSCALE:
                processedFrame = convertToGrayscale(inputFrame);
                break;
                
            case EDGE_DETECTION:
                // Future implementation for task 10
                Log.d(TAG, "Edge detection not yet implemented, using grayscale");
                processedFrame = convertToGrayscale(inputFrame);
                break;
                
            case COLOR_FILTER:
                // Future implementation for task 10
                Log.d(TAG, "Color filter not yet implemented, using grayscale");
                processedFrame = convertToGrayscale(inputFrame);
                break;
                
            default:
                processedFrame = inputFrame.clone();
                break;
        }
        
        // Apply quality adjustment if recommended
        if (recommendation != null && recommendation.processingQuality < 1.0f) {
            processedFrame = applyQualityReduction(processedFrame, recommendation.processingQuality);
        }
        
        return processedFrame;
    }
    
    /**
     * Apply quality reduction for performance optimization
     */
    private Mat applyQualityReduction(@NonNull Mat inputFrame, float qualityFactor) {
        if (qualityFactor >= 1.0f) {
            return inputFrame;
        }
        
        try {
            // Reduce resolution based on quality factor
            int newWidth = (int) (inputFrame.width() * qualityFactor);
            int newHeight = (int) (inputFrame.height() * qualityFactor);
            
            Mat resizedFrame = new Mat();
            org.opencv.imgproc.Imgproc.resize(inputFrame, resizedFrame, 
                    new org.opencv.core.Size(newWidth, newHeight));
            
            Log.v(TAG, "Applied quality reduction: " + qualityFactor + 
                      ", new size: " + newWidth + "x" + newHeight);
            
            return resizedFrame;
            
        } catch (Exception e) {
            Log.e(TAG, "Error applying quality reduction", e);
            return inputFrame;
        }
    }
    
    /**
     * Convert frame to grayscale
     * Requirement 2.2: Apply basic image processing operations
     */
    private Mat convertToGrayscale(@NonNull Mat inputFrame) {
        Mat grayFrame = new Mat();
        
        try {
            if (inputFrame.channels() == 3) {
                // RGB to Grayscale
                Imgproc.cvtColor(inputFrame, grayFrame, Imgproc.COLOR_RGB2GRAY);
            } else if (inputFrame.channels() == 4) {
                // RGBA to Grayscale
                Imgproc.cvtColor(inputFrame, grayFrame, Imgproc.COLOR_RGBA2GRAY);
            } else {
                // Already grayscale or single channel
                grayFrame = inputFrame.clone();
            }
            
            return grayFrame;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting to grayscale", e);
            throw e;
        }
    }
    
    /**
     * Convert Android Image to OpenCV Mat
     * Utility method for Camera2 API integration
     */
    public static Mat imageToMat(@NonNull Image image) {
        try {
            // Get image planes
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();
            
            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();
            
            byte[] nv21 = new byte[ySize + uSize + vSize];
            
            // Copy Y, U, V data
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);
            
            // Create Mat from YUV data
            Mat yuvMat = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth(), CvType.CV_8UC1);
            yuvMat.put(0, 0, nv21);
            
            // Convert YUV to RGB
            Mat rgbMat = new Mat();
            Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21);
            
            // Clean up
            yuvMat.release();
            
            return rgbMat;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting Image to Mat", e);
            throw new RuntimeException("Failed to convert Image to Mat", e);
        }
    }
    
    /**
     * Convert OpenCV Mat to Android Bitmap
     * Utility method for display integration
     */
    public static Bitmap matToBitmap(@NonNull Mat mat) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, bitmap);
            return bitmap;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting Mat to Bitmap", e);
            throw new RuntimeException("Failed to convert Mat to Bitmap", e);
        }
    }
    
    /**
     * Get performance metrics
     */
    public PerformanceMetrics getPerformanceMetrics() {
        return metrics;
    }
    
    /**
     * Reset performance metrics
     */
    public void resetPerformanceMetrics() {
        synchronized (metrics) {
            metrics.totalFrames = 0;
            metrics.totalProcessingTime = 0;
            metrics.maxProcessingTime = 0;
            metrics.timeoutCount = 0;
            metrics.errorCount = 0;
        }
        Log.d(TAG, "Performance metrics reset");
    }
    
    /**
     * Release resources
     * Requirement 5.4: Properly release resources when backgrounded
     */
    public void release() {
        Log.d(TAG, "Releasing OpenCV processor resources");
        
        isInitialized = false;
        callback = null;
        
        // Log final performance metrics
        Log.i(TAG, "Final performance metrics - " +
                "Frames: " + metrics.totalFrames +
                ", Avg time: " + String.format("%.2f", metrics.getAverageProcessingTime()) + "ms" +
                ", Max time: " + metrics.maxProcessingTime + "ms" +
                ", Timeouts: " + metrics.timeoutCount +
                ", Errors: " + metrics.errorCount);
    }
}