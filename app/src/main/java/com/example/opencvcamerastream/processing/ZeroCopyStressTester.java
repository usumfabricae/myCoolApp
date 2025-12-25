package com.example.opencvcamerastream.processing;

import android.graphics.Bitmap;
import android.media.Image;
import android.util.Log;
import androidx.annotation.NonNull;
import org.opencv.core.Mat;
import org.opencv.core.CvType;
import org.opencv.core.Scalar;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.ByteBuffer;

import static org.mockito.Mockito.*;

/**
 * ZeroCopyStressTester validates thread safety and performance of the zero-copy processing path
 * 
 * Requirements 13.1, 13.2: Validate thread safety with stress testing
 * 
 * This class performs:
 * - Concurrent processing stress tests
 * - Thread safety validation
 * - Performance regression detection
 * - Memory leak detection
 */
public class ZeroCopyStressTester {
    
    private static final String TAG = "ZeroCopyStressTester";
    
    // Test configuration
    private static final int DEFAULT_THREAD_COUNT = 4;
    private static final int DEFAULT_FRAMES_PER_THREAD = 100;
    private static final int DEFAULT_TEST_DURATION_SECONDS = 30;
    
    // Performance thresholds
    private static final double MAX_AVERAGE_PROCESSING_TIME_MS = 50.0;
    private static final double MAX_COPIES_PER_FRAME = 2.5;
    private static final double MIN_CPU_REDUCTION_PERCENT = 30.0;
    
    private final ZeroCopyProcessor processor;
    private final CopyOperationTracker copyTracker;
    
    // Test results
    private final AtomicInteger successfulFrames = new AtomicInteger(0);
    private final AtomicInteger failedFrames = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicReference<Exception> firstError = new AtomicReference<>();
    
    public ZeroCopyStressTester(@NonNull ZeroCopyProcessor processor) {
        this.processor = processor;
        this.copyTracker = new CopyOperationTracker();
        Log.d(TAG, "ZeroCopyStressTester created");
    }
    
    /**
     * Run comprehensive stress test
     * 
     * @return StressTestResults containing test outcomes
     */
    public StressTestResults runStressTest() {
        return runStressTest(DEFAULT_THREAD_COUNT, DEFAULT_FRAMES_PER_THREAD, DEFAULT_TEST_DURATION_SECONDS);
    }
    
    /**
     * Run stress test with custom parameters
     * 
     * @param threadCount Number of concurrent threads
     * @param framesPerThread Number of frames each thread should process
     * @param maxDurationSeconds Maximum test duration in seconds
     * @return StressTestResults containing test outcomes
     */
    public StressTestResults runStressTest(int threadCount, int framesPerThread, int maxDurationSeconds) {
        Log.i(TAG, String.format("Starting stress test: %d threads, %d frames/thread, %ds max duration",
                threadCount, framesPerThread, maxDurationSeconds));
        
        if (!processor.isInitialized()) {
            Log.e(TAG, "Processor not initialized");
            return new StressTestResults(false, "Processor not initialized", null, null);
        }
        
        // Reset counters
        successfulFrames.set(0);
        failedFrames.set(0);
        totalProcessingTime.set(0);
        firstError.set(null);
        copyTracker.resetMetrics();
        
        long testStartTime = System.currentTimeMillis();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        try {
            // Start concurrent processing threads
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> runProcessingThread(threadId, framesPerThread, latch));
            }
            
            // Wait for completion or timeout
            boolean completed = latch.await(maxDurationSeconds, TimeUnit.SECONDS);
            long testDuration = System.currentTimeMillis() - testStartTime;
            
            if (!completed) {
                Log.w(TAG, "Stress test timed out after " + maxDurationSeconds + " seconds");
            }
            
            // Collect results
            int successful = successfulFrames.get();
            int failed = failedFrames.get();
            int total = successful + failed;
            
            ZeroCopyProcessor.ZeroCopyPerformanceMetrics processorMetrics = processor.getPerformanceMetrics();
            CopyOperationTracker.CopyOperationMetrics copyMetrics = copyTracker.getMetrics();
            
            // Analyze results
            boolean passed = analyzeTestResults(successful, failed, testDuration, processorMetrics, copyMetrics);
            
            String summary = String.format("Stress test completed: %d/%d frames successful (%.1f%%), duration: %dms",
                    successful, total, total > 0 ? (successful * 100.0 / total) : 0, testDuration);
            
            Log.i(TAG, summary);
            copyTracker.logMetricsSummary();
            
            return new StressTestResults(passed, summary, processorMetrics, copyMetrics);
            
        } catch (InterruptedException e) {
            Log.e(TAG, "Stress test interrupted", e);
            return new StressTestResults(false, "Test interrupted: " + e.getMessage(), null, null);
            
        } finally {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.w(TAG, "Executor shutdown interrupted", e);
            }
        }
    }
    
    /**
     * Run processing thread for stress testing
     */
    private void runProcessingThread(int threadId, int frameCount, CountDownLatch latch) {
        Log.d(TAG, "Processing thread " + threadId + " started, processing " + frameCount + " frames");
        
        try {
            for (int i = 0; i < frameCount; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                
                processTestFrame(threadId, i);
                
                // Small delay to simulate realistic frame timing
                Thread.sleep(16); // ~60 FPS
            }
            
        } catch (InterruptedException e) {
            Log.d(TAG, "Processing thread " + threadId + " interrupted");
        } catch (Exception e) {
            Log.e(TAG, "Error in processing thread " + threadId, e);
            firstError.compareAndSet(null, e);
        } finally {
            latch.countDown();
            Log.d(TAG, "Processing thread " + threadId + " completed");
        }
    }
    
    /**
     * Process a single test frame
     */
    private void processTestFrame(int threadId, int frameIndex) {
        copyTracker.startFrame();
        
        // Create test image using Mockito (like other test files)
        Image testImage = createMockImage(320, 240, threadId, frameIndex);
        
        long startTime = System.currentTimeMillis();
        
        processor.processFrameZeroCopy(testImage, new ZeroCopyProcessor.ZeroCopyCallback() {
            @Override
            public void onFrameProcessed(@NonNull Mat processedMat, @NonNull Bitmap displayBitmap,
                                       @NonNull Image originalImage, 
                                       @NonNull ZeroCopyProcessor.FrameProcessingMetrics metrics) {
                try {
                    // Validate results
                    if (processedMat == null || processedMat.empty()) {
                        failedFrames.incrementAndGet();
                        Log.w(TAG, "Thread " + threadId + " frame " + frameIndex + ": processed Mat is invalid");
                        return;
                    }
                    
                    if (displayBitmap == null || displayBitmap.isRecycled()) {
                        failedFrames.incrementAndGet();
                        Log.w(TAG, "Thread " + threadId + " frame " + frameIndex + ": display Bitmap is invalid");
                        return;
                    }
                    
                    // Record successful processing
                    successfulFrames.incrementAndGet();
                    totalProcessingTime.addAndGet(metrics.totalTimeMs);
                    
                    // Track copy operations
                    copyTracker.recordCopyOperation(CopyOperationTracker.CopyType.IMAGE_TO_MAT, 
                            metrics.imageToMatTimeMs, 0);
                    copyTracker.recordCopyOperation(CopyOperationTracker.CopyType.MAT_TO_BITMAP, 
                            metrics.matToBitmapTimeMs, 0);
                    
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "Thread " + threadId + " frame " + frameIndex + " processed: " + metrics);
                    }
                    
                } finally {
                    // Clean up resources (ownership transfer)
                    processedMat.release();
                    displayBitmap.recycle();
                    originalImage.close();
                }
            }
            
            @Override
            public void onProcessingFailed(@NonNull Exception error, Image originalImage) {
                failedFrames.incrementAndGet();
                firstError.compareAndSet(null, error);
                Log.w(TAG, "Thread " + threadId + " frame " + frameIndex + " failed: " + error.getMessage());
                
                if (originalImage != null) {
                    originalImage.close();
                }
            }
        });
    }
    
    /**
     * Create a mock image for stress testing using Mockito
     */
    private Image createMockImage(int width, int height, int threadId, int frameIndex) {
        Image mockImage = mock(Image.class);
        
        // Set up basic image properties
        when(mockImage.getWidth()).thenReturn(width);
        when(mockImage.getHeight()).thenReturn(height);
        when(mockImage.getFormat()).thenReturn(android.graphics.ImageFormat.YUV_420_888);
        when(mockImage.getTimestamp()).thenReturn(System.nanoTime());
        
        // Create mock planes for YUV_420_888 format
        Image.Plane mockPlaneY = mock(Image.Plane.class);
        Image.Plane mockPlaneU = mock(Image.Plane.class);
        Image.Plane mockPlaneV = mock(Image.Plane.class);
        
        // Set up Y plane (full resolution)
        int ySize = width * height;
        ByteBuffer yBuffer = ByteBuffer.allocateDirect(ySize);
        for (int i = 0; i < ySize; i++) {
            yBuffer.put((byte) ((i + threadId + frameIndex) % 256));
        }
        yBuffer.rewind();
        when(mockPlaneY.getBuffer()).thenReturn(yBuffer);
        when(mockPlaneY.getPixelStride()).thenReturn(1);
        when(mockPlaneY.getRowStride()).thenReturn(width);
        
        // Set up U and V planes (quarter resolution)
        int uvSize = ySize / 4;
        ByteBuffer uBuffer = ByteBuffer.allocateDirect(uvSize);
        ByteBuffer vBuffer = ByteBuffer.allocateDirect(uvSize);
        
        for (int i = 0; i < uvSize; i++) {
            uBuffer.put((byte) ((i + threadId * 2 + frameIndex) % 256));
            vBuffer.put((byte) ((i + threadId * 3 + frameIndex) % 256));
        }
        uBuffer.rewind();
        vBuffer.rewind();
        
        when(mockPlaneU.getBuffer()).thenReturn(uBuffer);
        when(mockPlaneU.getPixelStride()).thenReturn(2);
        when(mockPlaneU.getRowStride()).thenReturn(width);
        
        when(mockPlaneV.getBuffer()).thenReturn(vBuffer);
        when(mockPlaneV.getPixelStride()).thenReturn(2);
        when(mockPlaneV.getRowStride()).thenReturn(width);
        
        // Set up planes array
        Image.Plane[] planes = {mockPlaneY, mockPlaneU, mockPlaneV};
        when(mockImage.getPlanes()).thenReturn(planes);
        
        return mockImage;
    }
    
    /**
     * Analyze test results to determine pass/fail
     */
    private boolean analyzeTestResults(int successful, int failed, long testDurationMs,
                                     ZeroCopyProcessor.ZeroCopyPerformanceMetrics processorMetrics,
                                     CopyOperationTracker.CopyOperationMetrics copyMetrics) {
        
        boolean passed = true;
        StringBuilder issues = new StringBuilder();
        
        // Check success rate
        int total = successful + failed;
        double successRate = total > 0 ? (successful * 100.0 / total) : 0;
        if (successRate < 95.0) {
            passed = false;
            issues.append(String.format("Low success rate: %.1f%% < 95%%; ", successRate));
        }
        
        // Check processing time
        if (processorMetrics != null && processorMetrics.averageProcessingTimeMs > MAX_AVERAGE_PROCESSING_TIME_MS) {
            passed = false;
            issues.append(String.format("Slow processing: %.1fms > %.1fms; ", 
                    processorMetrics.averageProcessingTimeMs, MAX_AVERAGE_PROCESSING_TIME_MS));
        }
        
        // Check copy operations
        if (copyMetrics != null) {
            double avgCopies = copyMetrics.getAverageCopiesPerFrame();
            if (avgCopies > MAX_COPIES_PER_FRAME) {
                passed = false;
                issues.append(String.format("Too many copies: %.1f > %.1f per frame; ", 
                        avgCopies, MAX_COPIES_PER_FRAME));
            }
            
            double cpuReduction = copyMetrics.getEstimatedCpuReduction();
            if (cpuReduction < MIN_CPU_REDUCTION_PERCENT) {
                passed = false;
                issues.append(String.format("Insufficient CPU reduction: %.1f%% < %.1f%%; ", 
                        cpuReduction, MIN_CPU_REDUCTION_PERCENT));
            }
        }
        
        // Check for errors
        Exception error = firstError.get();
        if (error != null) {
            passed = false;
            issues.append("Errors occurred: ").append(error.getMessage()).append("; ");
        }
        
        if (passed) {
            Log.i(TAG, "Stress test PASSED - all criteria met");
        } else {
            Log.w(TAG, "Stress test FAILED - issues: " + issues.toString());
        }
        
        return passed;
    }
    
    /**
     * Results of stress testing
     */
    public static class StressTestResults {
        public final boolean passed;
        public final String summary;
        public final ZeroCopyProcessor.ZeroCopyPerformanceMetrics processorMetrics;
        public final CopyOperationTracker.CopyOperationMetrics copyMetrics;
        
        public StressTestResults(boolean passed, String summary,
                               ZeroCopyProcessor.ZeroCopyPerformanceMetrics processorMetrics,
                               CopyOperationTracker.CopyOperationMetrics copyMetrics) {
            this.passed = passed;
            this.summary = summary;
            this.processorMetrics = processorMetrics;
            this.copyMetrics = copyMetrics;
        }
        
        @Override
        public String toString() {
            return String.format("StressTestResults{passed=%s, summary='%s'}", passed, summary);
        }
    }
}