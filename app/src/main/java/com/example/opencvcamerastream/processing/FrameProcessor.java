package com.example.opencvcamerastream.processing;

import android.media.Image;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.opencv.core.Mat;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FrameProcessor handles the integration between camera capture and OpenCV processing
 * 
 * This class manages:
 * - Background thread for OpenCV operations
 * - Frame processing queue to handle timing
 * - Connection between ImageReader callback and OpenCV processing
 * - Processed frame callback to display system
 * - Proper threading to maintain UI responsiveness
 * 
 * Requirements addressed:
 * - 2.1: Pass camera frame to OpenCV for processing
 * - 2.3: Ensure proper threading to maintain UI responsiveness
 * - 3.3: Add processed frame callback to display system
 */
public class FrameProcessor {
    
    private static final String TAG = "FrameProcessor";
    
    // Threading configuration
    private static final int MAX_QUEUE_SIZE = 3; // Keep queue small to avoid latency
    private static final String PROCESSING_THREAD_NAME = "FrameProcessingThread";
    
    // Processing components
    private final OpenCVProcessor openCVProcessor;
    private final FrameBuffer frameBuffer; // Kept for backward compatibility, but no longer used for pooling
    private ProcessingCallback processingCallback;
    
    // Zero-copy optimization components (Task 24)
    private final ZeroCopyProcessor zeroCopyProcessor;
    private final CopyOperationTracker copyTracker;
    private volatile boolean useZeroCopyPath = true; // Enable zero-copy by default
    
    // Threading and queue management
    private HandlerThread processingThread;
    private Handler processingHandler;
    private final BlockingQueue<FrameData> frameQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    
    // Performance tracking
    private long totalFramesReceived = 0;
    private long totalFramesProcessed = 0;
    private long totalFramesDropped = 0;
    private long lastPerformanceLogTime = 0;
    private static final long PERFORMANCE_LOG_INTERVAL = 5000; // Log every 5 seconds
    
    // Visualization state tracking
    private final AtomicBoolean isVisualizationEnabled = new AtomicBoolean(true);
    
    /**
     * Callback interface for processed frames
     * 
     * OWNERSHIP SEMANTICS (Task 21 - Optimized):
     * - The callback receives ownership of the Mat and Image objects
     * - The callback MUST call mat.release() when done with the Mat
     * - The callback MUST call image.close() when done with the Image
     * - Failure to release resources will cause memory leaks
     */
    public interface ProcessingCallback {
        /**
         * Called when a frame has been successfully processed
         * 
         * OWNERSHIP: The callback receives ownership of both processedFrame and originalImage.
         * The callback MUST release the Mat and close the Image when done.
         * 
         * @param processedFrame The processed OpenCV Mat (caller must release)
         * @param originalImage The original camera Image (caller must close)
         * @param processingTimeMs Time taken to process the frame
         */
        void onFrameProcessed(@NonNull Mat processedFrame, @NonNull Image originalImage, long processingTimeMs);
        
        /**
         * Called when frame processing fails
         * 
         * OWNERSHIP: The callback receives ownership of originalImage if not null.
         * The callback MUST close the Image when done.
         * 
         * @param error The error that occurred
         * @param originalImage The original camera Image (caller must close, may be null)
         */
        void onProcessingFailed(@NonNull Exception error, @Nullable Image originalImage);
        
        /**
         * Called when a frame is dropped due to queue overflow
         * 
         * OWNERSHIP: The callback receives ownership of droppedImage.
         * The callback MUST close the Image when done.
         * 
         * @param droppedImage The dropped camera Image (caller must close)
         */
        void onFrameDropped(@NonNull Image droppedImage);
    }
    
    /**
     * Internal class to hold frame data in the queue
     */
    private static class FrameData {
        final Image image;
        final long timestamp;
        
        FrameData(@NonNull Image image) {
            this.image = image;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * Constructor
     * @param openCVProcessor The OpenCV processor to use for frame processing
     */
    public FrameProcessor(@NonNull OpenCVProcessor openCVProcessor) {
        this.openCVProcessor = openCVProcessor;
        this.frameBuffer = new FrameBuffer();
        
        // Initialize zero-copy optimization components (Task 24)
        this.zeroCopyProcessor = new ZeroCopyProcessor(openCVProcessor);
        this.copyTracker = new CopyOperationTracker();
        
        Log.d(TAG, "FrameProcessor created with FrameBuffer and zero-copy optimization");
    }
    
    /**
     * Constructor with custom FrameBuffer configuration
     * @param openCVProcessor The OpenCV processor to use for frame processing
     * @param frameBuffer Custom FrameBuffer instance
     */
    public FrameProcessor(@NonNull OpenCVProcessor openCVProcessor, @NonNull FrameBuffer frameBuffer) {
        this.openCVProcessor = openCVProcessor;
        this.frameBuffer = frameBuffer;
        
        // Initialize zero-copy optimization components (Task 24)
        this.zeroCopyProcessor = new ZeroCopyProcessor(openCVProcessor);
        this.copyTracker = new CopyOperationTracker();
        
        Log.d(TAG, "FrameProcessor created with custom FrameBuffer and zero-copy optimization");
    }
    
    /**
     * Set the processing callback
     * @param callback Callback for processing events
     */
    public void setProcessingCallback(@Nullable ProcessingCallback callback) {
        this.processingCallback = callback;
    }
    
    /**
     * Start the frame processing pipeline
     * @return true if started successfully, false otherwise
     */
    public boolean start() {
        Log.d(TAG, "Starting frame processing pipeline");
        
        if (isProcessing.get()) {
            Log.w(TAG, "Frame processor already running");
            return true;
        }
        
        if (!openCVProcessor.isInitialized()) {
            Log.e(TAG, "OpenCV processor not initialized");
            return false;
        }
        
        try {
            // Initialize zero-copy processor (Task 24)
            if (!zeroCopyProcessor.initialize()) {
                Log.w(TAG, "Zero-copy processor initialization failed, falling back to standard processing");
                useZeroCopyPath = false;
            }
            
            // Start background processing thread
            processingThread = new HandlerThread(PROCESSING_THREAD_NAME);
            processingThread.start();
            processingHandler = new Handler(processingThread.getLooper());
            
            // Start the processing loop
            isShutdown.set(false);
            isProcessing.set(true);
            
            // Post the processing runnable
            processingHandler.post(processingRunnable);
            
            Log.i(TAG, "Frame processing pipeline started successfully with zero-copy optimization: " + useZeroCopyPath);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start frame processing pipeline", e);
            stop();
            return false;
        }
    }
    
    /**
     * Stop the frame processing pipeline
     */
    public void stop() {
        Log.d(TAG, "Stopping frame processing pipeline");
        
        isShutdown.set(true);
        isProcessing.set(false);
        
        // Clear the frame queue and close any remaining images
        clearFrameQueue();
        
        // Stop the processing thread
        if (processingThread != null) {
            processingThread.quitSafely();
            try {
                processingThread.join(1000); // Wait up to 1 second
                processingThread = null;
                processingHandler = null;
                Log.d(TAG, "Processing thread stopped");
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while stopping processing thread", e);
            }
        }
        
        // Clear frame buffer and release resources
        frameBuffer.clear();
        
        // Release zero-copy processor (Task 24)
        zeroCopyProcessor.release();
        
        // Log final performance metrics
        logPerformanceMetrics(true);
        frameBuffer.logStatus();
        
        // Log zero-copy optimization metrics
        ZeroCopyProcessor.ZeroCopyPerformanceMetrics zeroCopyMetrics = zeroCopyProcessor.getPerformanceMetrics();
        CopyOperationTracker.CopyOperationMetrics copyMetrics = copyTracker.getMetrics();
        Log.i(TAG, "Zero-copy optimization metrics: " + zeroCopyMetrics);
        Log.i(TAG, "Copy operation metrics: " + copyMetrics);
        
        Log.i(TAG, "Frame processing pipeline stopped");
    }
    
    /**
     * Process a frame asynchronously
     * This method is called from the camera's ImageReader callback
     * 
     * @param image The camera Image to process
     */
    public void processFrameAsync(@NonNull Image image) {
        if (isShutdown.get() || !isProcessing.get()) {
            Log.v(TAG, "Frame processor not running, closing image");
            image.close();
            return;
        }
        
        totalFramesReceived++;
        
        // Try to add frame to queue
        FrameData frameData = new FrameData(image);
        
        if (!frameQueue.offer(frameData)) {
            // Queue is full, drop the frame
            totalFramesDropped++;
            Log.v(TAG, "Frame queue full, dropping frame");
            
            if (processingCallback != null) {
                processingCallback.onFrameDropped(image);
            } else {
                image.close();
            }
        }
        
        // Log performance metrics periodically
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPerformanceLogTime > PERFORMANCE_LOG_INTERVAL) {
            logPerformanceMetrics(false);
            lastPerformanceLogTime = currentTime;
        }
    }
    
    /**
     * Main processing runnable that runs on the background thread
     */
    private final Runnable processingRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Processing loop started");
            
            while (!isShutdown.get()) {
                try {
                    // Take frame from queue (blocking call)
                    FrameData frameData = frameQueue.take();
                    
                    if (frameData != null && frameData.image != null) {
                        processFrameInternal(frameData);
                    }
                    
                } catch (InterruptedException e) {
                    Log.d(TAG, "Processing thread interrupted");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error in processing loop", e);
                    // Continue processing despite errors
                }
            }
            
            Log.d(TAG, "Processing loop ended");
        }
    };
    
    /**
     * Internal method to process a single frame
     * OPTIMIZED (Task 24): Implements zero-copy processing path
     * - Image→Mat→Process→Bitmap with only 2 necessary copies
     * - Eliminated buffer pool copies for improved performance
     * - Uses ownership transfer pattern instead of defensive cloning
     * 
     * @param frameData The frame data to process
     */
    private void processFrameInternal(@NonNull FrameData frameData) {
        Image image = frameData.image;
        long startTime = System.currentTimeMillis();
        
        copyTracker.startFrame();
        
        try {
            if (useZeroCopyPath && zeroCopyProcessor.isInitialized()) {
                // Use optimized zero-copy processing path (Task 24)
                processFrameZeroCopy(image, startTime);
            } else {
                // Fallback to legacy processing path
                processFrameLegacy(image, startTime);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in frame processing", e);
            
            if (processingCallback != null) {
                processingCallback.onProcessingFailed(e, image);
            } else {
                image.close();
            }
        }
    }
    
    /**
     * Process frame using zero-copy optimization (Task 24)
     */
    private void processFrameZeroCopy(@NonNull Image image, long startTime) {
        zeroCopyProcessor.processFrameZeroCopy(image, new ZeroCopyProcessor.ZeroCopyCallback() {
            @Override
            public void onFrameProcessed(@NonNull Mat processedMat, @NonNull Bitmap displayBitmap,
                                       @NonNull Image originalImage, 
                                       @NonNull ZeroCopyProcessor.FrameProcessingMetrics metrics) {
                
                long processingTime = System.currentTimeMillis() - startTime;
                totalFramesProcessed++;
                
                // Track copy operations
                copyTracker.recordCopyOperation(CopyOperationTracker.CopyType.IMAGE_TO_MAT, 
                        metrics.imageToMatTimeMs, 0);
                copyTracker.recordCopyOperation(CopyOperationTracker.CopyType.MAT_TO_BITMAP, 
                        metrics.matToBitmapTimeMs, 0);
                
                Log.v(TAG, "Zero-copy frame processed successfully in " + processingTime + "ms: " + metrics);
                
                // Convert to legacy callback format for compatibility
                if (processingCallback != null) {
                    processingCallback.onFrameProcessed(processedMat, originalImage, processingTime);
                    // Note: processedMat ownership transferred to callback
                } else {
                    // Clean up resources if no callback
                    processedMat.release();
                    displayBitmap.recycle();
                    originalImage.close();
                }
            }
            
            @Override
            public void onProcessingFailed(@NonNull Exception error, @Nullable Image originalImage) {
                Log.w(TAG, "Zero-copy processing failed, falling back to legacy path", error);
                
                // Fallback to legacy processing
                if (originalImage != null) {
                    try {
                        processFrameLegacy(originalImage, startTime);
                    } catch (Exception fallbackError) {
                        Log.e(TAG, "Legacy fallback also failed", fallbackError);
                        if (processingCallback != null) {
                            processingCallback.onProcessingFailed(fallbackError, originalImage);
                        } else {
                            originalImage.close();
                        }
                    }
                }
            }
        });
    }
    
    /**
     * Process frame using legacy path (fallback)
     */
    private void processFrameLegacy(@NonNull Image image, long startTime) {
        Mat inputMat = null;
        
        try {
            // Convert Image to Mat - NECESSARY COPY (Image→Mat conversion)
            long copyStart = System.currentTimeMillis();
            inputMat = OpenCVProcessor.imageToMat(image);
            long copyTime = System.currentTimeMillis() - copyStart;
            copyTracker.recordCopyOperation(CopyOperationTracker.CopyType.IMAGE_TO_MAT, copyTime, 0);
            
            if (inputMat.empty()) {
                Log.w(TAG, "Converted Mat is empty, skipping frame");
                if (processingCallback != null) {
                    processingCallback.onProcessingFailed(
                        new RuntimeException("Converted Mat is empty"), image);
                } else {
                    image.close();
                }
                inputMat.release();
                return;
            }
            
            // Process the frame with OpenCV directly on inputMat
            // OPTIMIZATION: No intermediate buffer pool copy
            Mat processedMat = openCVProcessor.processFrame(inputMat);
            
            if (processedMat == null || processedMat.empty()) {
                Log.w(TAG, "Processed Mat is null or empty, using original");
                processedMat = inputMat.clone();
                copyTracker.recordCopyOperation(CopyOperationTracker.CopyType.MAT_CLONE, 0, 0);
            }
            
            // Release inputMat if it's different from processedMat
            // (in-place processing returns the same Mat)
            if (processedMat != inputMat) {
                inputMat.release();
                inputMat = null;
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            totalFramesProcessed++;
            
            Log.v(TAG, "Legacy frame processed successfully in " + processingTime + "ms");
            
            // Notify callback with processed frame
            // OPTIMIZED (Task 21): Ownership transfer pattern - no clone needed
            // Callback is now responsible for releasing the Mat when done
            if (processingCallback != null) {
                processingCallback.onFrameProcessed(processedMat, image, processingTime);
                // processedMat ownership transferred to callback - callback must release it
            } else {
                // No callback, clean up resources ourselves
                processedMat.release();
                image.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing frame in legacy path", e);
            
            // Clean up inputMat if still allocated
            if (inputMat != null) {
                inputMat.release();
            }
            
            throw e; // Re-throw to be handled by caller
        }
    }
    

    
    /**
     * Clear all frames from the queue and close the images
     */
    private void clearFrameQueue() {
        Log.d(TAG, "Clearing frame queue");
        
        int clearedCount = 0;
        FrameData frameData;
        
        while ((frameData = frameQueue.poll()) != null) {
            if (frameData.image != null) {
                frameData.image.close();
                clearedCount++;
            }
        }
        
        if (clearedCount > 0) {
            Log.d(TAG, "Cleared " + clearedCount + " frames from queue");
        }
    }
    
    /**
     * Log performance metrics
     * @param isFinal Whether this is the final log before shutdown
     */
    private void logPerformanceMetrics(boolean isFinal) {
        String logLevel = isFinal ? "Final" : "Current";
        
        Log.i(TAG, logLevel + " performance metrics - " +
                "Received: " + totalFramesReceived +
                ", Processed: " + totalFramesProcessed +
                ", Dropped: " + totalFramesDropped +
                ", Queue size: " + frameQueue.size() +
                ", Drop rate: " + String.format("%.1f%%", 
                    totalFramesReceived > 0 ? (totalFramesDropped * 100.0 / totalFramesReceived) : 0));
    }
    
    /**
     * Check if the processor is currently running
     * @return true if processing, false otherwise
     */
    public boolean isProcessing() {
        return isProcessing.get() && !isShutdown.get();
    }
    
    /**
     * Get current queue size
     * @return Number of frames waiting to be processed
     */
    public int getQueueSize() {
        return frameQueue.size();
    }
    
    /**
     * Get performance statistics
     */
    public ProcessingStats getProcessingStats() {
        return new ProcessingStats(
            totalFramesReceived,
            totalFramesProcessed,
            totalFramesDropped,
            frameQueue.size()
        );
    }
    
    /**
     * Get frame buffer statistics
     */
    public FrameBuffer.BufferStats getBufferStats() {
        return frameBuffer.getStats();
    }
    
    /**
     * Get the frame buffer instance for direct access
     */
    public FrameBuffer getFrameBuffer() {
        return frameBuffer;
    }
    
    /**
     * Enable or disable zero-copy processing path (Task 24)
     * 
     * @param enabled true to use zero-copy optimization, false to use legacy path
     */
    public void setZeroCopyEnabled(boolean enabled) {
        useZeroCopyPath = enabled && zeroCopyProcessor.isInitialized();
        Log.d(TAG, "Zero-copy processing " + (useZeroCopyPath ? "enabled" : "disabled"));
    }
    
    /**
     * Check if zero-copy processing is enabled and available
     * 
     * @return true if zero-copy processing is active
     */
    public boolean isZeroCopyEnabled() {
        return useZeroCopyPath && zeroCopyProcessor.isInitialized();
    }
    
    /**
     * Get zero-copy performance metrics (Task 24)
     * 
     * @return ZeroCopyPerformanceMetrics with optimization statistics
     */
    public ZeroCopyProcessor.ZeroCopyPerformanceMetrics getZeroCopyMetrics() {
        return zeroCopyProcessor.getPerformanceMetrics();
    }
    
    /**
     * Get copy operation tracking metrics (Task 24)
     * 
     * @return CopyOperationMetrics with detailed copy statistics
     */
    public CopyOperationTracker.CopyOperationMetrics getCopyOperationMetrics() {
        return copyTracker.getMetrics();
    }
    
    /**
     * Run stress test to validate zero-copy implementation (Task 24)
     * 
     * @return StressTestResults with validation outcomes
     */
    public ZeroCopyStressTester.StressTestResults runZeroCopyStressTest() {
        if (!zeroCopyProcessor.isInitialized()) {
            Log.w(TAG, "Cannot run stress test - zero-copy processor not initialized");
            return new ZeroCopyStressTester.StressTestResults(false, 
                    "Zero-copy processor not initialized", null, null);
        }
        
        ZeroCopyStressTester tester = new ZeroCopyStressTester(zeroCopyProcessor);
        return tester.runStressTest();
    }
    
    /**
     * Set visualization enabled/disabled
     * Requirement 6.2: Continue processing pipeline when visualization is disabled
     * 
     * @param enabled true to enable visualization, false to disable
     */
    public void setVisualizationEnabled(boolean enabled) {
        isVisualizationEnabled.set(enabled);
        Log.d(TAG, "Visualization " + (enabled ? "enabled" : "disabled") + 
                " - processing pipeline continues");
    }
    
    /**
     * Check if visualization is currently enabled
     * @return true if visualization is enabled, false otherwise
     */
    public boolean isVisualizationEnabled() {
        return isVisualizationEnabled.get();
    }
    
    /**
     * Performance statistics class
     */
    public static class ProcessingStats {
        public final long framesReceived;
        public final long framesProcessed;
        public final long framesDropped;
        public final int currentQueueSize;
        
        public ProcessingStats(long received, long processed, long dropped, int queueSize) {
            this.framesReceived = received;
            this.framesProcessed = processed;
            this.framesDropped = dropped;
            this.currentQueueSize = queueSize;
        }
        
        public double getDropRate() {
            return framesReceived > 0 ? (framesDropped * 100.0 / framesReceived) : 0;
        }
        
        public double getProcessingRate() {
            return framesReceived > 0 ? (framesProcessed * 100.0 / framesReceived) : 0;
        }
    }
}