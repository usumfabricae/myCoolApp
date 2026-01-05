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
import com.example.opencvcamerastream.processing.VisualOdometryProcessor;

/**
 * OpenCVProcessor handles all OpenCV image processing operations
 * 
 * This class provides:
 * - OpenCV initialization and setup
 * - Frame conversion utilities (Image to Mat, Mat to Bitmap)
 * - Basic image processing operations (grayscale conversion)
 * - Error handling and fallback mechanisms
 * - Performance optimization for Android 10 compliance
 * - Framebuffer copy optimization (Requirement 13.5)
 * 
 * OPTIMIZATION STRATEGY:
 * - Eliminated unnecessary clone() calls in passthrough mode
 * - Removed clone() calls in fallback scenarios where input frame can be returned directly
 * - Use in-place OpenCV operations where supported
 * - Minimize Mat allocations and copies to reduce CPU usage
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
        SHARPEN,        // Sharpening filter
        VISUAL_ODOMETRY // Visual odometry distance measurement
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
        
        // Visual odometry parameters
        public boolean enableVisualOdometry = false;
        public boolean enableDistanceDisplay = true;
        public boolean enableCalibrationMode = false;
        public String calibrationFilePath = null;
        
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
    
    // Visual odometry components
    private VisualOdometryProcessor visualOdometryProcessor;
    private Mat previousFrame = null;
    private boolean isVisualOdometryEnabled = false;
    
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
        long matCopyCount = 0; // Track Mat copy operations for optimization monitoring
        
        double getAverageProcessingTime() {
            return totalFrames > 0 ? (double) totalProcessingTime / totalFrames : 0;
        }
        
        double getAverageMatCopiesPerFrame() {
            return totalFrames > 0 ? (double) matCopyCount / totalFrames : 0;
        }
        
        void recordProcessing(long processingTime) {
            totalFrames++;
            totalProcessingTime += processingTime;
            maxProcessingTime = Math.max(maxProcessingTime, processingTime);
        }
        
        void recordMatCopy() {
            matCopyCount++;
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
            
            // Visual odometry will be initialized separately when needed
            
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
                    // Optimization: Return input frame directly instead of cloning
                    processedFrame = inputFrame;
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
                        // Optimization: Return input frame directly instead of cloning
                        return inputFrame;
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
                // Optimization: Return input frame directly instead of cloning
                return inputFrame;
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
                // Optimization: Return input frame directly instead of cloning
                processedFrame = inputFrame;
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
                
            case VISUAL_ODOMETRY:
                processedFrame = applyVisualOdometry(inputFrame);
                break;
                
            default:
                // Optimization: Return input frame directly instead of cloning
                processedFrame = inputFrame;
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
            // Optimization: Return input frame directly instead of cloning
            return inputFrame;
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
                metrics.recordMatCopy(); // Track Mat allocation
                Imgproc.cvtColor(inputFrame, grayFrame, Imgproc.COLOR_RGB2GRAY);
            } else if (inputFrame.channels() == 4) {
                // RGBA to Grayscale
                metrics.recordMatCopy(); // Track Mat allocation
                Imgproc.cvtColor(inputFrame, grayFrame, Imgproc.COLOR_RGBA2GRAY);
            } else {
                // Already grayscale or single channel - use input frame directly
                grayFrame = inputFrame;
            }
            
            // CRITICAL FIX: Convert single-channel grayscale back to multi-channel for display
            // This prevents the OpenCV assertion failure in Utils.matToBitmap()
            if (grayFrame.channels() == 1) {
                metrics.recordMatCopy(); // Track Mat allocation
                Imgproc.cvtColor(grayFrame, displayFrame, Imgproc.COLOR_GRAY2BGR);
                if (grayFrame != inputFrame) { // Only release if it's not the input frame
                    grayFrame.release();
                }
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
                metrics.recordMatCopy(); // Track Mat allocation
                Imgproc.cvtColor(inputFrame, grayFrame, Imgproc.COLOR_RGB2GRAY);
            } else if (inputFrame.channels() == 4) {
                metrics.recordMatCopy(); // Track Mat allocation
                Imgproc.cvtColor(inputFrame, grayFrame, Imgproc.COLOR_RGBA2GRAY);
            } else {
                grayFrame = inputFrame;
            }
            
            // Apply Canny edge detection
            metrics.recordMatCopy(); // Track Mat allocation
            Imgproc.Canny(grayFrame, edgeFrame, 
                    config.cannyLowThreshold, 
                    config.cannyHighThreshold, 
                    config.cannyApertureSize);
            
            // Convert back to 3-channel for display consistency (BGR format for bitmap compatibility)
            Mat colorEdgeFrame = new Mat();
            metrics.recordMatCopy(); // Track Mat allocation
            Imgproc.cvtColor(edgeFrame, colorEdgeFrame, Imgproc.COLOR_GRAY2BGR);
            
            // Clean up intermediate matrices
            if (grayFrame != inputFrame) { // Only release if it's not the input frame
                grayFrame.release();
            }
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
                metrics.recordMatCopy(); // Track Mat allocation
                Imgproc.cvtColor(inputFrame, hsvFrame, Imgproc.COLOR_RGB2HSV);
            } else if (inputFrame.channels() == 4) {
                // RGBA to HSV (convert to RGB first)
                Mat rgbFrame = new Mat();
                metrics.recordMatCopy(); // Track Mat allocation
                Imgproc.cvtColor(inputFrame, rgbFrame, Imgproc.COLOR_RGBA2RGB);
                metrics.recordMatCopy(); // Track Mat allocation
                Imgproc.cvtColor(rgbFrame, hsvFrame, Imgproc.COLOR_RGB2HSV);
                rgbFrame.release();
            } else {
                // Single channel - convert to RGB first, then HSV
                Mat rgbFrame = new Mat();
                metrics.recordMatCopy(); // Track Mat allocation
                Imgproc.cvtColor(inputFrame, rgbFrame, Imgproc.COLOR_GRAY2RGB);
                metrics.recordMatCopy(); // Track Mat allocation
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
                metrics.recordMatCopy(); // Track Mat allocation
                Imgproc.cvtColor(inputFrame, labFrame, Imgproc.COLOR_RGB2Lab);
            } else if (inputFrame.channels() == 4) {
                // RGBA to LAB (convert to RGB first)
                Mat rgbFrame = new Mat();
                metrics.recordMatCopy(); // Track Mat allocation
                Imgproc.cvtColor(inputFrame, rgbFrame, Imgproc.COLOR_RGBA2RGB);
                metrics.recordMatCopy(); // Track Mat allocation
                Imgproc.cvtColor(rgbFrame, labFrame, Imgproc.COLOR_RGB2Lab);
                rgbFrame.release();
            } else {
                // Single channel - convert to RGB first, then LAB
                Mat rgbFrame = new Mat();
                metrics.recordMatCopy(); // Track Mat allocation
                Imgproc.cvtColor(inputFrame, rgbFrame, Imgproc.COLOR_GRAY2RGB);
                metrics.recordMatCopy(); // Track Mat allocation
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
        try {
            // Ensure kernel size is odd and positive
            int kernelSize = Math.max(1, config.blurKernelSize);
            if (kernelSize % 2 == 0) {
                kernelSize += 1; // Make it odd
            }
            
            // Optimization: Use in-place processing when possible
            // For blur operations, we can safely modify the input frame directly
            org.opencv.core.Size kernelSizeObj = new org.opencv.core.Size(kernelSize, kernelSize);
            Mat blurredFrame = new Mat();
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
     * Initialize visual odometry processor with Samsung S9 camera defaults
     * Requirements: 14.8
     */
    private void initializeVisualOdometry() {
        try {
            visualOdometryProcessor = new VisualOdometryProcessor();
            visualOdometryProcessor.initialize(); // Initialize OpenCV components
            
            // Set Samsung S9 camera defaults (approximate values)
            // Samsung S9 main camera specifications:
            // - Sensor: Sony IMX345 (1/2.55" sensor)
            // - Focal length: 26mm equivalent (4.25mm actual)
            // - Resolution: 4032x3024 (12MP), but camera preview typically uses 1920x1080 or 1280x720
            // - Pixel size: ~1.4μm
            
            // Create default camera matrix for Samsung S9 at 1280x720 resolution
            Mat defaultCameraMatrix = Mat.eye(3, 3, org.opencv.core.CvType.CV_64F);
            
            // Focal length calculation: f_pixels = f_mm * sensor_width_pixels / sensor_width_mm
            // For Samsung S9: f_pixels ≈ 4.25 * 1280 / 5.76 ≈ 945 pixels (horizontal)
            double focalLengthX = 945.0;  // Horizontal focal length in pixels
            double focalLengthY = 945.0;  // Vertical focal length in pixels (assuming square pixels)
            double principalPointX = 640.0;  // Image center X (1280/2)
            double principalPointY = 360.0;  // Image center Y (720/2)
            
            // Set camera matrix values
            defaultCameraMatrix.put(0, 0, focalLengthX);  // fx
            defaultCameraMatrix.put(1, 1, focalLengthY);  // fy
            defaultCameraMatrix.put(0, 2, principalPointX);  // cx
            defaultCameraMatrix.put(1, 2, principalPointY);  // cy
            
            // Create default distortion coefficients (minimal distortion for Samsung S9)
            Mat defaultDistCoeffs = Mat.zeros(5, 1, org.opencv.core.CvType.CV_64F);
            // Samsung S9 has good lens quality, so minimal distortion
            defaultDistCoeffs.put(0, 0, -0.1);   // k1 (radial distortion)
            defaultDistCoeffs.put(1, 0, 0.05);   // k2 (radial distortion)
            defaultDistCoeffs.put(2, 0, 0.0);    // p1 (tangential distortion)
            defaultDistCoeffs.put(3, 0, 0.0);    // p2 (tangential distortion)
            defaultDistCoeffs.put(4, 0, 0.0);    // k3 (radial distortion)
            
            // Set camera intrinsics
            visualOdometryProcessor.setCameraIntrinsics(defaultCameraMatrix, defaultDistCoeffs);
            
            // Set checkerboard pattern for calibration (standard 9x6 pattern)
            visualOdometryProcessor.setCheckerboardPattern(new org.opencv.core.Size(9, 6), 25.0f);
            
            // Set distance callback
            visualOdometryProcessor.setDistanceCallback(new VisualOdometryProcessor.DistanceCallback() {
                @Override
                public void onDistanceComputed(@NonNull VisualOdometryProcessor.DistanceResult result) {
                    Log.d(TAG, "Visual odometry distance: " + result.toString());
                    
                    // Notify callback if available
                    if (callback != null && callback instanceof VisualOdometryCallback) {
                        ((VisualOdometryCallback) callback).onDistanceComputed(result);
                    }
                }
                
                @Override
                public void onInsufficientFeatures(int matchCount) {
                    Log.w(TAG, "Insufficient features for visual odometry: " + matchCount);
                    
                    if (callback != null && callback instanceof VisualOdometryCallback) {
                        ((VisualOdometryCallback) callback).onInsufficientFeatures(matchCount);
                    }
                }
                
                @Override
                public void onProcessingError(@NonNull Exception error) {
                    Log.e(TAG, "Visual odometry processing error", error);
                    
                    if (callback != null && callback instanceof VisualOdometryCallback) {
                        ((VisualOdometryCallback) callback).onVisualOdometryError(error);
                    }
                }
            });
            
            Log.d(TAG, "Visual odometry processor initialized with Samsung S9 defaults");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize visual odometry processor", e);
            visualOdometryProcessor = null;
        }
    }
    
    /**
     * Apply visual odometry processing to compute 3D distance
     * Requirements: 14.1, 14.2, 14.5
     */
    private Mat applyVisualOdometry(@NonNull Mat inputFrame) {
        try {
            if (visualOdometryProcessor == null) {
                Log.w(TAG, "Visual odometry processor not initialized");
                return inputFrame;
            }
            
            // Convert to grayscale for feature detection
            Mat grayFrame = convertToGrayscale(inputFrame);
            
            // Process frame pair if we have a previous frame
            if (previousFrame != null && !previousFrame.empty()) {
                visualOdometryProcessor.processFramePair(previousFrame, grayFrame);
            }
            
            // Store current frame as previous for next iteration
            if (previousFrame != null) {
                previousFrame.release();
            }
            previousFrame = grayFrame.clone();
            
            // Clean up temporary grayscale frame if it's different from input
            if (grayFrame != inputFrame) {
                grayFrame.release();
            }
            
            // Return visualization frame if feature visualization is enabled
            if (visualOdometryProcessor.isFeatureVisualizationEnabled()) {
                Mat visualizationFrame = visualOdometryProcessor.getVisualizationFrame();
                if (visualizationFrame != null && !visualizationFrame.empty()) {
                    Log.v(TAG, "Returning feature visualization frame");
                    return visualizationFrame.clone(); // Return copy for display
                }
            }
            
            // Return original frame for display (visual odometry works in background)
            return inputFrame;
            
        } catch (Exception e) {
            Log.e(TAG, "Error in visual odometry processing", e);
            return inputFrame;
        }
    }
    
    /**
     * Enable or disable visual odometry processing
     */
    public void setVisualOdometryEnabled(boolean enabled) {
        isVisualOdometryEnabled = enabled;
        
        if (enabled && visualOdometryProcessor == null) {
            // Initialize visual odometry processor when first enabled
            initializeVisualOdometry();
        }
        
        Log.d(TAG, "Visual odometry " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if visual odometry is enabled
     */
    public boolean isVisualOdometryEnabled() {
        return isVisualOdometryEnabled && visualOdometryProcessor != null;
    }
    
    /**
     * Enable or disable feature visualization overlay
     * 
     * @param enabled true to show features and matches on video, false to hide
     */
    public void setFeatureVisualizationEnabled(boolean enabled) {
        if (visualOdometryProcessor != null) {
            visualOdometryProcessor.setFeatureVisualizationEnabled(enabled);
            Log.d(TAG, "Feature visualization " + (enabled ? "enabled" : "disabled"));
        } else {
            Log.w(TAG, "Cannot set feature visualization - visual odometry processor not initialized");
        }
    }
    
    /**
     * Check if feature visualization is enabled
     * 
     * @return true if feature visualization is enabled
     */
    public boolean isFeatureVisualizationEnabled() {
        return visualOdometryProcessor != null && visualOdometryProcessor.isFeatureVisualizationEnabled();
    }
    
    /**
     * Get visual odometry processor for direct access
     */
    public VisualOdometryProcessor getVisualOdometryProcessor() {
        return visualOdometryProcessor;
    }
    
    /**
     * Perform camera calibration using collected images
     * Requirements: 14.8
     */
    public VisualOdometryProcessor.CalibrationResult performCameraCalibration() {
        if (visualOdometryProcessor == null) {
            Log.w(TAG, "Visual odometry processor not initialized");
            return new VisualOdometryProcessor.CalibrationResult(
                new Mat(), new Mat(), Double.MAX_VALUE, 0, null, false);
        }
        
        return visualOdometryProcessor.performCameraCalibration();
    }
    
    /**
     * Add calibration image for camera calibration
     * Requirements: 14.8
     */
    public boolean addCalibrationImage(@NonNull Mat image) {
        if (visualOdometryProcessor == null) {
            Log.w(TAG, "Visual odometry processor not initialized");
            return false;
        }
        
        return visualOdometryProcessor.addCalibrationImage(image);
    }
    
    /**
     * Save calibration results to file
     * Requirements: 14.8
     */
    public boolean saveCalibrationToFile(@NonNull String filePath, 
                                       @NonNull VisualOdometryProcessor.CalibrationResult result) {
        if (visualOdometryProcessor == null) {
            Log.w(TAG, "Visual odometry processor not initialized");
            return false;
        }
        
        return visualOdometryProcessor.saveCalibrationToFile(filePath, result);
    }
    
    /**
     * Load calibration results from file
     * Requirements: 14.8
     */
    public VisualOdometryProcessor.CalibrationResult loadCalibrationFromFile(@NonNull String filePath) {
        if (visualOdometryProcessor == null) {
            initializeVisualOdometry();
        }
        
        if (visualOdometryProcessor == null) {
            Log.w(TAG, "Visual odometry processor not initialized");
            return new VisualOdometryProcessor.CalibrationResult(
                new Mat(), new Mat(), Double.MAX_VALUE, 0, null, false);
        }
        
        return visualOdometryProcessor.loadCalibrationFromFile(filePath);
    }
    
    /**
     * Extended callback interface for visual odometry
     */
    public interface VisualOdometryCallback extends ProcessingCallback {
        void onDistanceComputed(@NonNull VisualOdometryProcessor.DistanceResult result);
        void onInsufficientFeatures(int matchCount);
        void onVisualOdometryError(@NonNull Exception error);
    }
    
    /**
     * Release resources
     * Requirement 5.4: Properly release resources when backgrounded
     */
    public void release() {
        Log.d(TAG, "Releasing OpenCV processor resources");
        
        // Release visual odometry resources
        if (visualOdometryProcessor != null) {
            visualOdometryProcessor.release();
            visualOdometryProcessor = null;
        }
        
        if (previousFrame != null) {
            previousFrame.release();
            previousFrame = null;
        }
        
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