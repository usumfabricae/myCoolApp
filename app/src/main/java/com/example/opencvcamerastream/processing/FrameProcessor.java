package com.example.opencvcamerastream.processing;

import android.graphics.Bitmap;
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
    
    // Visual odometry components (Task 30)
    private VisualOdometryProcessor visualOdometryProcessor;
    private Mat previousFrameMat = null; // Previous frame reference for visual odometry
    private volatile boolean useVisualOdometry = false; // Enable visual odometry processing
    private VisualOdometryCallback visualOdometryCallback;
    
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
     * Callback interface for visual odometry results
     * Requirements: 14.6
     */
    public interface VisualOdometryCallback {
        /**
         * Called when visual odometry distance computation is complete
         * 
         * @param result The distance computation result
         */
        void onDistanceComputed(@NonNull VisualOdometryProcessor.DistanceResult result);
        
        /**
         * Called when insufficient features are detected for reliable computation
         * 
         * @param matchCount Number of feature matches found
         */
        void onInsufficientFeatures(int matchCount);
        
        /**
         * Called when visual odometry processing fails
         * 
         * @param error The error that occurred
         */
        void onVisualOdometryError(@NonNull Exception error);
    }
    
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
        
        // Visual odometry processor will be initialized lazily when OpenCV is ready
        this.visualOdometryProcessor = null;
        
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
        
        // Visual odometry processor will be initialized lazily when OpenCV is ready
        this.visualOdometryProcessor = null;
        
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
     * Set the visual odometry callback
     * Requirements: 14.6
     * @param callback Callback for visual odometry events
     */
    public void setVisualOdometryCallback(@Nullable VisualOdometryCallback callback) {
        this.visualOdometryCallback = callback;
    }
    
    /**
     * Initialize visual odometry processor when OpenCV is ready
     * This should be called after OpenCV native libraries are loaded
     * Requirements: 14.1, 14.2, 14.8
     */
    public void initializeVisualOdometry() {
        if (visualOdometryProcessor == null) {
            try {
                visualOdometryProcessor = new VisualOdometryProcessor();
                visualOdometryProcessor.initialize(); // Initialize OpenCV components
                Log.d(TAG, "Visual odometry processor initialized successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize visual odometry processor", e);
                visualOdometryProcessor = null;
            }
        }
    }
    
    /**
     * Check if visual odometry is available
     * @return true if visual odometry processor is initialized
     */
    public boolean isVisualOdometryAvailable() {
        return visualOdometryProcessor != null;
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
        
        // Release visual odometry processor (Task 30)
        visualOdometryProcessor.release();
        
        // Release previous frame reference
        if (previousFrameMat != null) {
            previousFrameMat.release();
            previousFrameMat = null;
        }
        
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
     * Enhanced with visual odometry integration (Task 30)
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
                
                // Process visual odometry if enabled (Task 30)
                processVisualOdometryIfEnabled(processedMat);
                
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
     * Enhanced with visual odometry integration (Task 30)
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
            
            // Process visual odometry if enabled (Task 30)
            processVisualOdometryIfEnabled(processedMat);
            
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
     * Process visual odometry if enabled (Task 30)
     * Requirements: 14.1, 14.2
     * 
     * This method implements frame pair processing using OpenCV's built-in functions
     * while maintaining compatibility with existing zero-copy optimization.
     * 
     * @param currentFrameMat The current processed frame Mat
     */
    private void processVisualOdometryIfEnabled(@NonNull Mat currentFrameMat) {
        if (!useVisualOdometry || visualOdometryCallback == null) {
            // Update previous frame reference for next iteration
            updatePreviousFrameReference(currentFrameMat);
            return;
        }
        
        try {
            if (previousFrameMat != null && !previousFrameMat.empty()) {
                // Use OpenCV's efficient Mat copying and memory management
                // Create working copies to avoid modifying the original frames
                Mat previousFrameCopy = previousFrameMat.clone();
                Mat currentFrameCopy = currentFrameMat.clone();
                
                // Set up visual odometry callback to handle results
                visualOdometryProcessor.setDistanceCallback(new VisualOdometryProcessor.DistanceCallback() {
                    @Override
                    public void onDistanceComputed(@NonNull VisualOdometryProcessor.DistanceResult result) {
                        Log.d(TAG, "Visual odometry distance computed: " + result);
                        if (visualOdometryCallback != null) {
                            visualOdometryCallback.onDistanceComputed(result);
                        }
                        
                        // Clean up working copies
                        previousFrameCopy.release();
                        currentFrameCopy.release();
                    }
                    
                    @Override
                    public void onInsufficientFeatures(int matchCount) {
                        Log.w(TAG, "Visual odometry: insufficient features (" + matchCount + ")");
                        if (visualOdometryCallback != null) {
                            visualOdometryCallback.onInsufficientFeatures(matchCount);
                        }
                        
                        // Clean up working copies
                        previousFrameCopy.release();
                        currentFrameCopy.release();
                    }
                    
                    @Override
                    public void onProcessingError(@NonNull Exception error) {
                        Log.e(TAG, "Visual odometry processing error", error);
                        if (visualOdometryCallback != null) {
                            visualOdometryCallback.onVisualOdometryError(error);
                        }
                        
                        // Clean up working copies
                        previousFrameCopy.release();
                        currentFrameCopy.release();
                    }
                });
                
                // Process frame pair using OpenCV's built-in functions
                // This implements frame pair processing as required by Task 30
                visualOdometryProcessor.processFramePair(previousFrameCopy, currentFrameCopy);
                
            } else {
                Log.v(TAG, "Visual odometry: no previous frame available, skipping");
            }
            
            // Update previous frame reference for next iteration
            updatePreviousFrameReference(currentFrameMat);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in visual odometry processing", e);
            if (visualOdometryCallback != null) {
                visualOdometryCallback.onVisualOdometryError(e);
            }
            
            // Still update previous frame reference to continue processing
            updatePreviousFrameReference(currentFrameMat);
        }
    }
    
    /**
     * Update previous frame reference using OpenCV's efficient Mat copying
     * Requirements: 14.1, 14.2
     * 
     * This method maintains the cv::Mat previous frame reference as required by Task 30
     * while using OpenCV's efficient memory management.
     * 
     * @param currentFrameMat The current frame to store as previous frame
     */
    private void updatePreviousFrameReference(@NonNull Mat currentFrameMat) {
        try {
            // Release previous frame if it exists
            if (previousFrameMat != null) {
                previousFrameMat.release();
            }
            
            // Use OpenCV's efficient Mat copying - clone to maintain independent reference
            // This ensures compatibility with existing zero-copy optimization
            if (!currentFrameMat.empty()) {
                previousFrameMat = currentFrameMat.clone();
                Log.v(TAG, "Previous frame reference updated: " + 
                          previousFrameMat.rows() + "x" + previousFrameMat.cols() + 
                          " channels=" + previousFrameMat.channels());
            } else {
                previousFrameMat = null;
                Log.w(TAG, "Current frame is empty, clearing previous frame reference");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating previous frame reference", e);
            // Ensure previousFrameMat is in a clean state
            if (previousFrameMat != null) {
                previousFrameMat.release();
                previousFrameMat = null;
            }
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
     * Enable or disable visual odometry processing (Task 30)
     * Requirements: 14.1, 14.2
     * 
     * @param enabled true to enable visual odometry, false to disable
     */
    public void setVisualOdometryEnabled(boolean enabled) {
        useVisualOdometry = enabled;
        Log.d(TAG, "Visual odometry " + (enabled ? "enabled" : "disabled"));
        
        // Clear previous frame reference when disabling to free memory
        if (!enabled && previousFrameMat != null) {
            previousFrameMat.release();
            previousFrameMat = null;
            Log.d(TAG, "Previous frame reference cleared");
        }
    }
    
    /**
     * Check if visual odometry processing is enabled
     * Requirements: 14.1, 14.2
     * 
     * @return true if visual odometry is enabled, false otherwise
     */
    public boolean isVisualOdometryEnabled() {
        return useVisualOdometry;
    }
    
    /**
     * Set camera intrinsic parameters for visual odometry
     * Requirements: 14.8
     * 
     * @param cameraMatrix 3x3 camera matrix containing focal lengths and principal point
     * @param distCoeffs Distortion coefficients (can be null if no distortion correction needed)
     */
    public void setCameraIntrinsics(@NonNull Mat cameraMatrix, @Nullable Mat distCoeffs) {
        visualOdometryProcessor.setCameraIntrinsics(cameraMatrix, distCoeffs);
        Log.d(TAG, "Camera intrinsics set for visual odometry");
    }
    
    /**
     * Get visual odometry performance metrics
     * Requirements: 14.1, 14.2
     * 
     * @return Performance metrics including processing time and frame pair count
     */
    public VisualOdometryPerformanceMetrics getVisualOdometryMetrics() {
        return new VisualOdometryPerformanceMetrics(
            visualOdometryProcessor.getTotalFramePairs(),
            visualOdometryProcessor.getAverageProcessingTime(),
            previousFrameMat != null
        );
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
    
    /**
     * Visual odometry performance metrics class (Task 30)
     * Requirements: 14.1, 14.2
     */
    public static class VisualOdometryPerformanceMetrics {
        public final int totalFramePairs;
        public final double averageProcessingTimeMs;
        public final boolean hasPreviousFrame;
        
        public VisualOdometryPerformanceMetrics(int framePairs, double avgTime, boolean hasPrevious) {
            this.totalFramePairs = framePairs;
            this.averageProcessingTimeMs = avgTime;
            this.hasPreviousFrame = hasPrevious;
        }
        
        @Override
        public String toString() {
            return String.format("VisualOdometryMetrics{pairs=%d, avgTime=%.2fms, hasPrevious=%b}",
                    totalFramePairs, averageProcessingTimeMs, hasPreviousFrame);
        }
    }
}