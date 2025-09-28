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
        EDGE_DETECTION, // Canny edge detection
        COLOR_HSV,      // HSV color space conversion
        COLOR_LAB,      // LAB color space conversion
        BLUR,           // Gaussian blur filter
        SHARPEN         // Sharpening filter
    }
    
    // Processing configuration
    public static class ProcessingConfig {
        public ProcessingMode mode = ProcessingMode.GRAYSCALE;
        public boolean enablePerformanceOptimization = true;
        public int maxProcessingTimeMs = 50; // Requirement 2.3: within 50ms
        
        // Edge detection parameters
        public double cannyLowThreshold = 50.0;
        public double cannyHighThreshold = 150.0;
        public int cannyApertureSize = 3;
        
        // Blur parameters
        public int blurKernelSize = 15;
        public double blurSigmaX = 0.0;
        public double blurSigmaY = 0.0;
        
        // Sharpen parameters
        public float sharpenStrength = 1.0f;
        
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
    public static class PerformanceMetrics {
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
            return createValidFallbackFrame(inputFrame);
        }
        
        // Validate input frame
        if (inputFrame == null || inputFrame.empty() || 
            inputFrame.width() <= 0 || inputFrame.height() <= 0) {
            Log.w(TAG, "Invalid input frame, creating fallback");
            return createValidFallbackFrame(inputFrame);
        }
        
        // Check if in fallback mode due to consecutive errors
        if (fallbackMode) {
            Log.d(TAG, "In fallback mode, returning original frame");
            return createValidFallbackFrame(inputFrame);
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
            if (inputFrame != null && !inputFrame.empty() && 
                inputFrame.width() > 0 && inputFrame.height() > 0) {
                return inputFrame.clone();
            } else {
                Log.e(TAG, "Input frame is invalid, creating empty fallback Mat");
                // Create a minimal valid Mat as last resort
                Mat fallbackMat = new Mat(1, 1, org.opencv.core.CvType.CV_8UC3);
                fallbackMat.setTo(new org.opencv.core.Scalar(0, 0, 0));
                return fallbackMat;
            }
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
                processedFrame = applyEdgeDetection(inputFrame);
                break;
                
            case COLOR_HSV:
                processedFrame = convertToHSV(inputFrame);
                break;
                
            case COLOR_LAB:
                processedFrame = convertToLAB(inputFrame);
                break;
                
            case BLUR:
                processedFrame = applyBlur(inputFrame);
                break;
                
            case SHARPEN:
                processedFrame = applySharpen(inputFrame);
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
     * Create a valid fallback frame when input is invalid
     */
    private Mat createValidFallbackFrame(@Nullable Mat inputFrame) {
        if (inputFrame != null && !inputFrame.empty() && 
            inputFrame.width() > 0 && inputFrame.height() > 0) {
            return inputFrame.clone();
        }
        
        // Create a minimal valid black frame as fallback
        Log.w(TAG, "Creating minimal fallback frame");
        Mat fallbackMat = new Mat(240, 320, org.opencv.core.CvType.CV_8UC3);
        fallbackMat.setTo(new org.opencv.core.Scalar(0, 0, 0));
        return fallbackMat;
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
     * Convert frame to grayscale but maintain multi-channel format for display compatibility
     * Requirement 2.2: Apply basic image processing operations
     */
    private Mat convertToGrayscale(@NonNull Mat inputFrame) {
        Mat grayFrame = new Mat();
        Mat displayFrame = new Mat();
        
        try {
            // Validate input
            if (inputFrame.empty() || inputFrame.width() <= 0 || inputFrame.height() <= 0) {
                Log.w(TAG, "Invalid input frame for grayscale conversion");
                return createValidFallbackFrame(inputFrame);
            }
            
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
            
            // CRITICAL FIX: Convert single-channel grayscale back to multi-channel for display
            // This prevents the OpenCV assertion failure in Utils.matToBitmap()
            if (grayFrame.channels() == 1) {
                Imgproc.cvtColor(grayFrame, displayFrame, Imgproc.COLOR_GRAY2BGR);
                grayFrame.release();
            } else {
                displayFrame = grayFrame;
            }
            
            // Validate output
            if (displayFrame.empty() || displayFrame.width() <= 0 || displayFrame.height() <= 0) {
                Log.w(TAG, "Grayscale conversion produced invalid result");
                displayFrame.release();
                return createValidFallbackFrame(inputFrame);
            }
            
            return displayFrame;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting to grayscale", e);
            if (grayFrame != null) grayFrame.release();
            if (displayFrame != null) displayFrame.release();
            throw e;
        }
    }
    
    /**
     * Apply Canny edge detection
     * Requirement 2.2: Apply basic image processing operations (edge detection)
     */
    private Mat applyEdgeDetection(@NonNull Mat inputFrame) {
        Mat edgeFrame = new Mat();
        Mat grayFrame = new Mat();
        
        try {
            // Convert to grayscale first if needed
            if (inputFrame.channels() == 3) {
                Imgproc.cvtColor(inputFrame, grayFrame, Imgproc.COLOR_RGB2GRAY);
            } else if (inputFrame.channels() == 4) {
                Imgproc.cvtColor(inputFrame, grayFrame, Imgproc.COLOR_RGBA2GRAY);
            } else {
                grayFrame = inputFrame.clone();
            }
            
            // Apply Canny edge detection
            Imgproc.Canny(grayFrame, edgeFrame, 
                    config.cannyLowThreshold, 
                    config.cannyHighThreshold, 
                    config.cannyApertureSize);
            
            // Convert back to 3-channel for display consistency (BGR format for bitmap compatibility)
            Mat colorEdgeFrame = new Mat();
            Imgproc.cvtColor(edgeFrame, colorEdgeFrame, Imgproc.COLOR_GRAY2BGR);
            
            // Clean up intermediate matrices
            grayFrame.release();
            edgeFrame.release();
            
            return colorEdgeFrame;
            
        } catch (Exception e) {
            Log.e(TAG, "Error applying edge detection", e);
            // Clean up on error
            if (grayFrame != null) grayFrame.release();
            if (edgeFrame != null) edgeFrame.release();
            throw e;
        }
    }
    
    /**
     * Convert frame to HSV color space
     * Requirement 2.2: Apply basic image processing operations (color space conversion)
     */
    private Mat convertToHSV(@NonNull Mat inputFrame) {
        Mat hsvFrame = new Mat();
        
        try {
            if (inputFrame.channels() == 3) {
                // RGB to HSV
                Imgproc.cvtColor(inputFrame, hsvFrame, Imgproc.COLOR_RGB2HSV);
            } else if (inputFrame.channels() == 4) {
                // RGBA to HSV (convert to RGB first)
                Mat rgbFrame = new Mat();
                Imgproc.cvtColor(inputFrame, rgbFrame, Imgproc.COLOR_RGBA2RGB);
                Imgproc.cvtColor(rgbFrame, hsvFrame, Imgproc.COLOR_RGB2HSV);
                rgbFrame.release();
            } else {
                // Single channel - convert to RGB first, then HSV
                Mat rgbFrame = new Mat();
                Imgproc.cvtColor(inputFrame, rgbFrame, Imgproc.COLOR_GRAY2RGB);
                Imgproc.cvtColor(rgbFrame, hsvFrame, Imgproc.COLOR_RGB2HSV);
                rgbFrame.release();
            }
            
            return hsvFrame;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting to HSV", e);
            throw e;
        }
    }
    
    /**
     * Convert frame to LAB color space
     * Requirement 2.2: Apply basic image processing operations (color space conversion)
     */
    private Mat convertToLAB(@NonNull Mat inputFrame) {
        Mat labFrame = new Mat();
        
        try {
            if (inputFrame.channels() == 3) {
                // RGB to LAB
                Imgproc.cvtColor(inputFrame, labFrame, Imgproc.COLOR_RGB2Lab);
            } else if (inputFrame.channels() == 4) {
                // RGBA to LAB (convert to RGB first)
                Mat rgbFrame = new Mat();
                Imgproc.cvtColor(inputFrame, rgbFrame, Imgproc.COLOR_RGBA2RGB);
                Imgproc.cvtColor(rgbFrame, labFrame, Imgproc.COLOR_RGB2Lab);
                rgbFrame.release();
            } else {
                // Single channel - convert to RGB first, then LAB
                Mat rgbFrame = new Mat();
                Imgproc.cvtColor(inputFrame, rgbFrame, Imgproc.COLOR_GRAY2RGB);
                Imgproc.cvtColor(rgbFrame, labFrame, Imgproc.COLOR_RGB2Lab);
                rgbFrame.release();
            }
            
            return labFrame;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting to LAB", e);
            throw e;
        }
    }
    
    /**
     * Apply Gaussian blur filter
     * Requirement 2.2: Apply basic image processing operations (blur filter)
     */
    private Mat applyBlur(@NonNull Mat inputFrame) {
        Mat blurredFrame = new Mat();
        
        try {
            // Ensure kernel size is odd and positive
            int kernelSize = Math.max(1, config.blurKernelSize);
            if (kernelSize % 2 == 0) {
                kernelSize += 1; // Make it odd
            }
            
            // Apply Gaussian blur
            org.opencv.core.Size kernelSizeObj = new org.opencv.core.Size(kernelSize, kernelSize);
            Imgproc.GaussianBlur(inputFrame, blurredFrame, kernelSizeObj, 
                    config.blurSigmaX, config.blurSigmaY);
            
            return blurredFrame;
            
        } catch (Exception e) {
            Log.e(TAG, "Error applying blur", e);
            throw e;
        }
    }
    
    /**
     * Apply sharpening filter
     * Requirement 2.2: Apply basic image processing operations (sharpen filter)
     */
    private Mat applySharpen(@NonNull Mat inputFrame) {
        Mat sharpenedFrame = new Mat();
        Mat blurredFrame = new Mat();
        
        try {
            // Create a slightly blurred version
            org.opencv.core.Size kernelSize = new org.opencv.core.Size(3, 3);
            Imgproc.GaussianBlur(inputFrame, blurredFrame, kernelSize, 1.0, 1.0);
            
            // Create sharpening mask: original - blurred
            Mat mask = new Mat();
            org.opencv.core.Core.subtract(inputFrame, blurredFrame, mask);
            
            // Apply sharpening: original + strength * mask
            org.opencv.core.Core.addWeighted(inputFrame, 1.0, mask, config.sharpenStrength, 0, sharpenedFrame);
            
            // Clean up intermediate matrices
            blurredFrame.release();
            mask.release();
            
            return sharpenedFrame;
            
        } catch (Exception e) {
            Log.e(TAG, "Error applying sharpen", e);
            // Clean up on error
            if (blurredFrame != null) blurredFrame.release();
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