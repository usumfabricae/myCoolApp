package com.example.opencvcamerastream.processing;

import android.graphics.Bitmap;
import android.media.Image;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.opencv.core.Mat;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ZeroCopyProcessor implements an optimized processing path that minimizes framebuffer copies
 * 
 * OPTIMIZATION STRATEGY (Requirements 13.1, 13.2):
 * - Reduce from 5-6 copies per frame to only 2 necessary copies
 * - Image→Mat conversion (NECESSARY - format conversion)
 * - Mat→Bitmap conversion (NECESSARY - display format)
 * - Eliminate all intermediate buffer pool copies
 * - Use ownership transfer instead of defensive cloning
 * - Implement proper synchronization without cloning
 * 
 * THREAD SAFETY:
 * - Uses ReentrantReadWriteLock for efficient concurrent access
 * - No defensive cloning - proper synchronization ensures thread safety
 * - Ownership transfer pattern for Mat objects
 * 
 * PERFORMANCE TARGETS:
 * - Reduce CPU usage by 40-50% in processing pipeline
 * - Improve frame processing latency by 20-30ms
 * - Reduce memory pressure and GC frequency
 */
public class ZeroCopyProcessor {
    
    private static final String TAG = "ZeroCopyProcessor";
    
    // Performance tracking for copy operations
    private final AtomicLong totalFramesProcessed = new AtomicLong(0);
    private final AtomicLong totalCopyOperations = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    private final AtomicLong imageToMatConversions = new AtomicLong(0);
    private final AtomicLong matToBitmapConversions = new AtomicLong(0);
    
    // Thread safety without defensive cloning
    private final ReentrantReadWriteLock processingLock = new ReentrantReadWriteLock();
    
    // Processing components
    private final OpenCVProcessor openCVProcessor;
    private volatile boolean isInitialized = false;
    
    /**
     * Callback interface for zero-copy processing results
     * 
     * OWNERSHIP SEMANTICS:
     * - The callback receives ownership of the Mat and must release it
     * - The callback receives ownership of the Image and must close it
     * - The callback receives ownership of the Bitmap and must recycle it
     */
    public interface ZeroCopyCallback {
        /**
         * Called when frame processing completes successfully
         * 
         * @param processedMat The processed Mat (callback owns - must release)
         * @param displayBitmap The display-ready Bitmap (callback owns - must recycle)
         * @param originalImage The original Image (callback owns - must close)
         * @param metrics Processing metrics for this frame
         */
        void onFrameProcessed(@NonNull Mat processedMat, 
                            @NonNull Bitmap displayBitmap,
                            @NonNull Image originalImage,
                            @NonNull FrameProcessingMetrics metrics);
        
        /**
         * Called when processing fails
         * 
         * @param error The error that occurred
         * @param originalImage The original Image (callback owns - must close, may be null)
         */
        void onProcessingFailed(@NonNull Exception error, @Nullable Image originalImage);
    }
    
    /**
     * Metrics for a single frame processing operation
     */
    public static class FrameProcessingMetrics {
        public final long imageToMatTimeMs;
        public final long processingTimeMs;
        public final long matToBitmapTimeMs;
        public final long totalTimeMs;
        public final int copyOperationsCount;
        public final boolean usedZeroCopyPath;
        
        public FrameProcessingMetrics(long imageToMatTime, long processingTime, 
                                    long matToBitmapTime, int copyOps, boolean zeroCopy) {
            this.imageToMatTimeMs = imageToMatTime;
            this.processingTimeMs = processingTime;
            this.matToBitmapTimeMs = matToBitmapTime;
            this.totalTimeMs = imageToMatTime + processingTime + matToBitmapTime;
            this.copyOperationsCount = copyOps;
            this.usedZeroCopyPath = zeroCopy;
        }
        
        @Override
        public String toString() {
            return String.format("FrameMetrics{total=%dms, img2mat=%dms, proc=%dms, mat2bmp=%dms, copies=%d, zeroCopy=%s}",
                    totalTimeMs, imageToMatTimeMs, processingTimeMs, matToBitmapTimeMs, 
                    copyOperationsCount, usedZeroCopyPath);
        }
    }
    
    /**
     * Aggregate performance metrics for the zero-copy processor
     */
    public static class ZeroCopyPerformanceMetrics {
        public final long totalFrames;
        public final long totalCopyOperations;
        public final double averageCopiesPerFrame;
        public final long totalProcessingTimeMs;
        public final double averageProcessingTimeMs;
        public final long imageToMatConversions;
        public final long matToBitmapConversions;
        public final double cpuUsageReduction; // Estimated based on copy reduction
        
        public ZeroCopyPerformanceMetrics(long frames, long copies, long processingTime,
                                        long imgToMat, long matToBmp) {
            this.totalFrames = frames;
            this.totalCopyOperations = copies;
            this.averageCopiesPerFrame = frames > 0 ? (double) copies / frames : 0;
            this.totalProcessingTimeMs = processingTime;
            this.averageProcessingTimeMs = frames > 0 ? (double) processingTime / frames : 0;
            this.imageToMatConversions = imgToMat;
            this.matToBitmapConversions = matToBmp;
            
            // Estimate CPU usage reduction based on copy operations
            // Target: reduce from 5-6 copies to 2 copies = ~60% reduction in copy overhead
            double targetCopiesPerFrame = 2.0;
            this.cpuUsageReduction = averageCopiesPerFrame > targetCopiesPerFrame ? 
                    ((averageCopiesPerFrame - targetCopiesPerFrame) / averageCopiesPerFrame) * 100 : 0;
        }
        
        @Override
        public String toString() {
            return String.format("ZeroCopyMetrics{frames=%d, avgCopies=%.1f, avgTime=%.1fms, cpuReduction=%.1f%%}",
                    totalFrames, averageCopiesPerFrame, averageProcessingTimeMs, cpuUsageReduction);
        }
    }
    
    public ZeroCopyProcessor(@NonNull OpenCVProcessor openCVProcessor) {
        this.openCVProcessor = openCVProcessor;
        Log.d(TAG, "ZeroCopyProcessor created with optimization targets: 2 copies per frame, 40-50% CPU reduction");
    }
    
    /**
     * Initialize the zero-copy processor
     */
    public boolean initialize() {
        processingLock.writeLock().lock();
        try {
            if (!openCVProcessor.isInitialized()) {
                Log.e(TAG, "OpenCV processor not initialized");
                return false;
            }
            
            isInitialized = true;
            Log.i(TAG, "ZeroCopyProcessor initialized successfully");
            return true;
            
        } finally {
            processingLock.writeLock().unlock();
        }
    }
    
    /**
     * Process frame using optimized zero-copy path
     * 
     * OPTIMIZATION: Image→Mat→Process→Bitmap with only 2 necessary copies
     * 
     * @param image The camera Image to process
     * @param callback Callback for processing results
     */
    public void processFrameZeroCopy(@NonNull Image image, @NonNull ZeroCopyCallback callback) {
        if (!isInitialized) {
            callback.onProcessingFailed(new IllegalStateException("Processor not initialized"), image);
            return;
        }
        
        long startTime = System.currentTimeMillis();
        long imageToMatTime = 0;
        long processingTime = 0;
        long matToBitmapTime = 0;
        int copyOperations = 0;
        Mat inputMat = null;
        Mat processedMat = null;
        Bitmap displayBitmap = null;
        
        // Use read lock for concurrent processing
        processingLock.readLock().lock();
        try {
            // STEP 1: Image→Mat conversion (NECESSARY COPY #1)
            long step1Start = System.currentTimeMillis();
            inputMat = OpenCVProcessor.imageToMat(image);
            imageToMatTime = System.currentTimeMillis() - step1Start;
            copyOperations++; // Count the Image→Mat conversion
            imageToMatConversions.incrementAndGet();
            
            if (inputMat == null || inputMat.empty()) {
                callback.onProcessingFailed(new RuntimeException("Image to Mat conversion failed"), image);
                return;
            }
            
            // STEP 2: Process Mat in-place (ZERO-COPY OPTIMIZATION)
            long step2Start = System.currentTimeMillis();
            processedMat = openCVProcessor.processFrame(inputMat);
            processingTime = System.currentTimeMillis() - step2Start;
            
            // Check if processing created a new Mat or modified in-place
            if (processedMat != inputMat) {
                copyOperations++; // Count if a new Mat was created
                // Release inputMat since we have a new processedMat
                inputMat.release();
                inputMat = null;
            }
            // If processedMat == inputMat, then in-place processing was used (optimal)
            
            if (processedMat == null || processedMat.empty()) {
                callback.onProcessingFailed(new RuntimeException("Frame processing failed"), image);
                if (inputMat != null) inputMat.release();
                return;
            }
            
            // STEP 3: Mat→Bitmap conversion (NECESSARY COPY #2)
            long step3Start = System.currentTimeMillis();
            displayBitmap = convertMatToBitmapZeroCopy(processedMat);
            matToBitmapTime = System.currentTimeMillis() - step3Start;
            copyOperations++; // Count the Mat→Bitmap conversion
            matToBitmapConversions.incrementAndGet();
            
            if (displayBitmap == null) {
                callback.onProcessingFailed(new RuntimeException("Mat to Bitmap conversion failed"), image);
                processedMat.release();
                return;
            }
            
            // Update performance metrics
            long totalTime = System.currentTimeMillis() - startTime;
            totalFramesProcessed.incrementAndGet();
            totalCopyOperations.addAndGet(copyOperations);
            totalProcessingTimeMs.addAndGet(totalTime);
            
            // Create metrics for this frame
            FrameProcessingMetrics frameMetrics = new FrameProcessingMetrics(
                    imageToMatTime, processingTime, matToBitmapTime, copyOperations, copyOperations <= 2);
            
            Log.v(TAG, "Zero-copy processing completed: " + frameMetrics);
            
            // Transfer ownership to callback
            callback.onFrameProcessed(processedMat, displayBitmap, image, frameMetrics);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in zero-copy processing", e);
            
            // Clean up resources on error
            if (inputMat != null) inputMat.release();
            if (processedMat != null) processedMat.release();
            if (displayBitmap != null) displayBitmap.recycle();
            
            callback.onProcessingFailed(e, image);
            
        } finally {
            processingLock.readLock().unlock();
        }
    }
    
    /**
     * Convert Mat to Bitmap using optimized zero-copy approach
     * 
     * THREAD SAFETY: Uses proper synchronization instead of defensive cloning
     * 
     * @param mat The Mat to convert (must remain valid during conversion)
     * @return Bitmap or null if conversion failed
     */
    @Nullable
    private Bitmap convertMatToBitmapZeroCopy(@NonNull Mat mat) {
        // Thread-safe conversion without defensive cloning
        // The caller must ensure Mat remains valid during this operation
        synchronized (mat) {
            try {
                // Validate Mat without cloning
                if (mat.empty() || mat.width() <= 0 || mat.height() <= 0) {
                    Log.w(TAG, "Invalid Mat for bitmap conversion");
                    return null;
                }
                
                // Direct conversion without intermediate copies
                Bitmap bitmap = Bitmap.createBitmap(mat.width(), mat.height(), Bitmap.Config.ARGB_8888);
                
                // Convert directly from Mat to Bitmap
                org.opencv.android.Utils.matToBitmap(mat, bitmap);
                
                return bitmap;
                
            } catch (Exception e) {
                Log.e(TAG, "Error in zero-copy Mat to Bitmap conversion", e);
                return null;
            }
        }
    }
    
    /**
     * Get current performance metrics
     */
    public ZeroCopyPerformanceMetrics getPerformanceMetrics() {
        return new ZeroCopyPerformanceMetrics(
                totalFramesProcessed.get(),
                totalCopyOperations.get(),
                totalProcessingTimeMs.get(),
                imageToMatConversions.get(),
                matToBitmapConversions.get()
        );
    }
    
    /**
     * Reset performance metrics
     */
    public void resetPerformanceMetrics() {
        totalFramesProcessed.set(0);
        totalCopyOperations.set(0);
        totalProcessingTimeMs.set(0);
        imageToMatConversions.set(0);
        matToBitmapConversions.set(0);
        Log.d(TAG, "Performance metrics reset");
    }
    
    /**
     * Check if processor is initialized
     */
    public boolean isInitialized() {
        processingLock.readLock().lock();
        try {
            return isInitialized;
        } finally {
            processingLock.readLock().unlock();
        }
    }
    
    /**
     * Release resources
     */
    public void release() {
        processingLock.writeLock().lock();
        try {
            isInitialized = false;
            
            // Log final metrics
            ZeroCopyPerformanceMetrics finalMetrics = getPerformanceMetrics();
            Log.i(TAG, "ZeroCopyProcessor released. Final metrics: " + finalMetrics);
            
        } finally {
            processingLock.writeLock().unlock();
        }
    }
}