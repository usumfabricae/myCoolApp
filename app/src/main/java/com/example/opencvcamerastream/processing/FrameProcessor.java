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
    private final FrameBuffer frameBuffer;
    private ProcessingCallback processingCallback;
    
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
     */
    public interface ProcessingCallback {
        /**
         * Called when a frame has been successfully processed
         * @param processedFrame The processed OpenCV Mat
         * @param originalImage The original camera Image (for cleanup)
         * @param processingTimeMs Time taken to process the frame
         */
        void onFrameProcessed(@NonNull Mat processedFrame, @NonNull Image originalImage, long processingTimeMs);
        
        /**
         * Called when frame processing fails
         * @param error The error that occurred
         * @param originalImage The original camera Image (for cleanup)
         */
        void onProcessingFailed(@NonNull Exception error, @Nullable Image originalImage);
        
        /**
         * Called when a frame is dropped due to queue overflow
         * @param droppedImage The dropped camera Image (for cleanup)
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
        Log.d(TAG, "FrameProcessor created with FrameBuffer");
    }
    
    /**
     * Constructor with custom FrameBuffer configuration
     * @param openCVProcessor The OpenCV processor to use for frame processing
     * @param frameBuffer Custom FrameBuffer instance
     */
    public FrameProcessor(@NonNull OpenCVProcessor openCVProcessor, @NonNull FrameBuffer frameBuffer) {
        this.openCVProcessor = openCVProcessor;
        this.frameBuffer = frameBuffer;
        Log.d(TAG, "FrameProcessor created with custom FrameBuffer");
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
            // Start background processing thread
            processingThread = new HandlerThread(PROCESSING_THREAD_NAME);
            processingThread.start();
            processingHandler = new Handler(processingThread.getLooper());
            
            // Start the processing loop
            isShutdown.set(false);
            isProcessing.set(true);
            
            // Post the processing runnable
            processingHandler.post(processingRunnable);
            
            Log.i(TAG, "Frame processing pipeline started successfully");
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
        
        // Log final performance metrics
        logPerformanceMetrics(true);
        frameBuffer.logStatus();
        
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
     * @param frameData The frame data to process
     */
    private void processFrameInternal(@NonNull FrameData frameData) {
        Image image = frameData.image;
        long startTime = System.currentTimeMillis();
        FrameBuffer.PooledMat inputBuffer = null;
        FrameBuffer.PooledMat outputBuffer = null;
        
        try {
            // Convert Image to Mat using temporary buffer
            Mat tempMat = OpenCVProcessor.imageToMat(image);
            
            if (tempMat.empty()) {
                Log.w(TAG, "Converted Mat is empty, skipping frame");
                if (processingCallback != null) {
                    processingCallback.onProcessingFailed(
                        new RuntimeException("Converted Mat is empty"), image);
                } else {
                    image.close();
                }
                tempMat.release();
                return;
            }
            
            // Acquire buffer for input Mat
            inputBuffer = frameBuffer.acquireBuffer(tempMat.rows(), tempMat.cols(), tempMat.type());
            if (inputBuffer == null) {
                Log.w(TAG, "Failed to acquire input buffer, using temporary Mat");
                // Fall back to direct processing without pooling
                processWithoutPooling(tempMat, image, startTime);
                return;
            }
            
            // Copy data to pooled buffer
            tempMat.copyTo(inputBuffer.getMat());
            tempMat.release(); // Release temporary Mat
            
            // Process the frame with OpenCV
            Mat processedMat = openCVProcessor.processFrame(inputBuffer.getMat());
            
            if (processedMat == null || processedMat.empty()) {
                Log.w(TAG, "Processed Mat is null or empty, using original");
                processedMat = inputBuffer.getMat().clone();
            }
            
            // Acquire buffer for output if different from input
            if (processedMat != inputBuffer.getMat()) {
                outputBuffer = frameBuffer.acquireBuffer(processedMat.rows(), processedMat.cols(), processedMat.type());
                if (outputBuffer != null) {
                    processedMat.copyTo(outputBuffer.getMat());
                    processedMat.release(); // Release temporary processed Mat
                    processedMat = outputBuffer.getMat();
                }
            } else {
                // Processed Mat is the same as input Mat (in-place processing)
                outputBuffer = inputBuffer;
                inputBuffer = null; // Prevent double recycling
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            totalFramesProcessed++;
            
            Log.v(TAG, "Frame processed successfully in " + processingTime + "ms (with pooling)");
            
            // Notify callback with processed frame
            if (processingCallback != null) {
                // Create a copy for the callback since we need to recycle the buffer
                Mat callbackMat = processedMat.clone();
                processingCallback.onFrameProcessed(callbackMat, image, processingTime);
            } else {
                image.close();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing frame", e);
            
            if (processingCallback != null) {
                processingCallback.onProcessingFailed(e, image);
            } else {
                image.close();
            }
        } finally {
            // Recycle buffers back to pool
            if (inputBuffer != null) {
                inputBuffer.recycle();
            }
            if (outputBuffer != null && outputBuffer != inputBuffer) {
                outputBuffer.recycle();
            }
        }
    }
    
    /**
     * Fallback processing without buffer pooling
     */
    private void processWithoutPooling(@NonNull Mat inputMat, @NonNull Image image, long startTime) {
        try {
            // Process the frame with OpenCV
            Mat processedMat = openCVProcessor.processFrame(inputMat);
            
            if (processedMat == null || processedMat.empty()) {
                Log.w(TAG, "Processed Mat is null or empty, using original");
                processedMat = inputMat.clone();
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            totalFramesProcessed++;
            
            Log.v(TAG, "Frame processed successfully in " + processingTime + "ms (without pooling)");
            
            // Notify callback with processed frame
            if (processingCallback != null) {
                processingCallback.onFrameProcessed(processedMat, image, processingTime);
            } else {
                // No callback, clean up resources
                processedMat.release();
                image.close();
            }
            
            // Clean up input Mat
            inputMat.release();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in fallback processing", e);
            inputMat.release();
            
            if (processingCallback != null) {
                processingCallback.onProcessingFailed(e, image);
            } else {
                image.close();
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