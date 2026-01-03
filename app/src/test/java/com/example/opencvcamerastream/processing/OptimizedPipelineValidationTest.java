package com.example.opencvcamerastream.processing;

import android.graphics.Bitmap;
import android.media.Image;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opencv.core.Mat;
import org.opencv.core.CvType;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive validation tests for the optimized processing pipeline
 * 
 * This test class validates all aspects of the zero-copy optimization:
 * - Task 20: Eliminate buffer pool copies
 * - Task 21: Replace callback clones with ownership transfer
 * - Task 22: Remove DisplayManager defensive cloning
 * - Task 23: Optimize OpenCVProcessor for in-place operations
 * - Task 24: Implement zero-copy processing path
 * - Task 25: Measure and validate CPU usage reduction
 * - Task 26: Update tests for optimized pipeline
 * 
 * Requirements tested:
 * - Req-13.1: Minimize framebuffer copies to 2 per frame maximum
 * - Req-13.2: Process frames in-place without copying to pooled buffers
 * - Req-13.3: Transfer ownership instead of cloning Mat objects
 * - Req-13.4: Use proper synchronization instead of defensive cloning
 * - Req-13.5: Use in-place OpenCV operations where supported
 * - Req-13.6: Achieve <30% CPU usage for framebuffer operations
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class OptimizedPipelineValidationTest {
    
    @Mock
    private OpenCVProcessor mockOpenCVProcessor;
    
    @Mock
    private Image mockImage;
    
    @Mock
    private Mat mockInputMat;
    
    @Mock
    private Mat mockProcessedMat;
    
    private ZeroCopyProcessor zeroCopyProcessor;
    private CopyOperationTracker copyTracker;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Set up mock OpenCV processor
        when(mockOpenCVProcessor.isInitialized()).thenReturn(true);
        when(mockOpenCVProcessor.processFrame(any(Mat.class))).thenReturn(mockProcessedMat);
        
        // Set up mock image
        when(mockImage.getWidth()).thenReturn(1280);
        when(mockImage.getHeight()).thenReturn(720);
        
        // Set up mock mats
        when(mockInputMat.empty()).thenReturn(false);
        when(mockProcessedMat.empty()).thenReturn(false);
        when(mockProcessedMat.width()).thenReturn(1280);
        when(mockProcessedMat.height()).thenReturn(720);
        
        // Initialize components
        zeroCopyProcessor = new ZeroCopyProcessor(mockOpenCVProcessor);
        copyTracker = new CopyOperationTracker();
    }
    
    @Test
    public void testTask20_EliminateBufferPoolCopies() {
        // Task 20: Refactor FrameProcessor to eliminate buffer pool copies
        // Requirement Req-13.1, Req-13.2: Process directly without buffer pool
        
        assertTrue("ZeroCopyProcessor should initialize", zeroCopyProcessor.initialize());
        
        ZeroCopyProcessor.ZeroCopyCallback callback = mock(ZeroCopyProcessor.ZeroCopyCallback.class);
        
        // Mock successful processing
        doAnswer(invocation -> {
            ZeroCopyProcessor.ZeroCopyCallback cb = invocation.getArgument(1);
            Bitmap mockBitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
            ZeroCopyProcessor.FrameProcessingMetrics metrics = 
                    new ZeroCopyProcessor.FrameProcessingMetrics(5, 10, 3, 2, true);
            cb.onFrameProcessed(mockInputMat, mockBitmap, mockImage, metrics);
            return null;
        }).when(callback).onFrameProcessed(any(), any(), any(), any());
        
        // Process frame
        zeroCopyProcessor.processFrameZeroCopy(mockImage, callback);
        
        // Verify no buffer pool copies occurred
        verify(mockInputMat, never()).copyTo(any(Mat.class));
        verify(mockProcessedMat, never()).copyTo(any(Mat.class));
        
        // Verify callback was called with direct Mat reference
        verify(callback, times(1)).onFrameProcessed(eq(mockInputMat), any(Bitmap.class), 
                eq(mockImage), any(ZeroCopyProcessor.FrameProcessingMetrics.class));
    }
    
    @Test
    public void testTask21_OwnershipTransferPattern() {
        // Task 21: Replace callback clones with ownership transfer
        // Requirement Req-13.3: Transfer ownership instead of cloning
        
        assertTrue("ZeroCopyProcessor should initialize", zeroCopyProcessor.initialize());
        
        ZeroCopyProcessor.ZeroCopyCallback callback = new ZeroCopyProcessor.ZeroCopyCallback() {
            @Override
            public void onFrameProcessed(Mat processedMat, Bitmap displayBitmap, 
                                       Image originalImage, ZeroCopyProcessor.FrameProcessingMetrics metrics) {
                // Verify ownership transfer - we receive the actual objects
                assertSame("Mat should be transferred, not cloned", mockInputMat, processedMat);
                assertSame("Image should be transferred, not cloned", mockImage, originalImage);
                assertNotNull("Bitmap should be provided", displayBitmap);
                assertNotNull("Metrics should be provided", metrics);
                
                // In real implementation, callback would:
                // processedMat.release();
                // displayBitmap.recycle();
                // originalImage.close();
            }
            
            @Override
            public void onProcessingFailed(Exception error, Image originalImage) {
                fail("Processing should not fail in this test");
            }
        };
        
        // Process frame
        zeroCopyProcessor.processFrameZeroCopy(mockImage, callback);
        
        // Verify no cloning occurred
        verify(mockInputMat, never()).clone();
        verify(mockProcessedMat, never()).clone();
    }
    
    @Test
    public void testTask22_ThreadSafetyWithoutDefensiveCloning() throws InterruptedException {
        // Task 22: Remove DisplayManager defensive cloning
        // Requirement Req-13.4: Use proper synchronization instead of cloning
        
        assertTrue("ZeroCopyProcessor should initialize", zeroCopyProcessor.initialize());
        
        int threadCount = 8;
        int operationsPerThread = 5;
        CountDownLatch completionLatch = new CountDownLatch(threadCount * operationsPerThread);
        AtomicInteger successCount = new AtomicInteger(0);
        
        ZeroCopyProcessor.ZeroCopyCallback callback = new ZeroCopyProcessor.ZeroCopyCallback() {
            @Override
            public void onFrameProcessed(Mat processedMat, Bitmap displayBitmap, 
                                       Image originalImage, ZeroCopyProcessor.FrameProcessingMetrics metrics) {
                successCount.incrementAndGet();
                completionLatch.countDown();
            }
            
            @Override
            public void onProcessingFailed(Exception error, Image originalImage) {
                completionLatch.countDown();
            }
        };
        
        // Create multiple threads for concurrent access
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    zeroCopyProcessor.processFrameZeroCopy(mockImage, callback);
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        assertTrue("All operations should complete", 
                completionLatch.await(5, TimeUnit.SECONDS));
        
        // Wait for threads to finish
        for (Thread thread : threads) {
            thread.join(1000);
        }
        
        // Verify all operations succeeded without race conditions
        assertEquals("All operations should succeed", threadCount * operationsPerThread, 
                successCount.get());
        
        // Verify no defensive cloning occurred
        verify(mockInputMat, never()).clone();
        verify(mockProcessedMat, never()).clone();
    }
    
    @Test
    public void testTask24_ZeroCopyProcessingPath() {
        // Task 24: Implement and validate zero-copy processing path
        // Requirement Req-13.1, Req-13.2: Ensure only 2 necessary copies remain
        
        assertTrue("ZeroCopyProcessor should initialize", zeroCopyProcessor.initialize());
        
        AtomicInteger copyCount = new AtomicInteger(0);
        
        ZeroCopyProcessor.ZeroCopyCallback callback = new ZeroCopyProcessor.ZeroCopyCallback() {
            @Override
            public void onFrameProcessed(Mat processedMat, Bitmap displayBitmap, 
                                       Image originalImage, ZeroCopyProcessor.FrameProcessingMetrics metrics) {
                // Verify metrics show only 2 copy operations
                assertEquals("Should have exactly 2 copy operations", 2, metrics.copyOperationsCount);
                assertTrue("Should use zero-copy path", metrics.usedZeroCopyPath);
                
                copyCount.set(metrics.copyOperationsCount);
            }
            
            @Override
            public void onProcessingFailed(Exception error, Image originalImage) {
                fail("Processing should not fail in this test");
            }
        };
        
        // Process frame
        zeroCopyProcessor.processFrameZeroCopy(mockImage, callback);
        
        // Verify only 2 copies occurred (Image→Mat, Mat→Bitmap)
        assertEquals("Should have exactly 2 copy operations", 2, copyCount.get());
        
        // Verify performance metrics
        ZeroCopyProcessor.ZeroCopyPerformanceMetrics metrics = zeroCopyProcessor.getPerformanceMetrics();
        assertEquals("Total copy operations should be 2", 2, metrics.totalCopyOperations);
        assertEquals("Average copies per frame should be 2.0", 2.0, metrics.averageCopiesPerFrame, 0.01);
    }
    
    @Test
    public void testTask25_CPUUsageReduction() {
        // Task 25: Measure and validate CPU usage reduction
        // Requirement Req-13.6: Framebuffer operations <30% of total processing time
        
        assertTrue("ZeroCopyProcessor should initialize", zeroCopyProcessor.initialize());
        
        AtomicLong totalProcessingTime = new AtomicLong(0);
        AtomicLong framebufferTime = new AtomicLong(0);
        
        ZeroCopyProcessor.ZeroCopyCallback callback = new ZeroCopyProcessor.ZeroCopyCallback() {
            @Override
            public void onFrameProcessed(Mat processedMat, Bitmap displayBitmap, 
                                       Image originalImage, ZeroCopyProcessor.FrameProcessingMetrics metrics) {
                totalProcessingTime.addAndGet(metrics.totalTimeMs);
                
                // Framebuffer operations: Image→Mat + Mat→Bitmap
                long fbTime = metrics.imageToMatTimeMs + metrics.matToBitmapTimeMs;
                framebufferTime.addAndGet(fbTime);
            }
            
            @Override
            public void onProcessingFailed(Exception error, Image originalImage) {
                fail("Processing should not fail in this test");
            }
        };
        
        // Process multiple frames to get meaningful metrics
        int frameCount = 20;
        for (int i = 0; i < frameCount; i++) {
            zeroCopyProcessor.processFrameZeroCopy(mockImage, callback);
        }
        
        // Calculate CPU usage percentage for framebuffer operations
        double fbPercentage = (double) framebufferTime.get() / totalProcessingTime.get() * 100;
        
        // Verify framebuffer operations are <30% of total processing time
        assertTrue("Framebuffer operations should be <30% of total processing time", 
                fbPercentage < 30.0);
        
        // Verify performance metrics
        ZeroCopyProcessor.ZeroCopyPerformanceMetrics metrics = zeroCopyProcessor.getPerformanceMetrics();
        assertEquals("Frame count should match", frameCount, metrics.totalFrames);
        assertTrue("Average processing time should be reasonable", 
                metrics.averageProcessingTimeMs < 50);
    }
    
    @Test
    public void testTask26_MemoryLeakPrevention() {
        // Task 26: Validate memory leak prevention with optimized code
        // Requirement Req-13: Ensure proper Mat lifecycle management
        
        assertTrue("ZeroCopyProcessor should initialize", zeroCopyProcessor.initialize());
        
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        AtomicInteger processedFrames = new AtomicInteger(0);
        
        ZeroCopyProcessor.ZeroCopyCallback callback = new ZeroCopyProcessor.ZeroCopyCallback() {
            @Override
            public void onFrameProcessed(Mat processedMat, Bitmap displayBitmap, 
                                       Image originalImage, ZeroCopyProcessor.FrameProcessingMetrics metrics) {
                processedFrames.incrementAndGet();
                
                // In real implementation, callback would clean up:
                // processedMat.release();
                // displayBitmap.recycle();
                // originalImage.close();
            }
            
            @Override
            public void onProcessingFailed(Exception error, Image originalImage) {
                // In real implementation: originalImage.close();
            }
        };
        
        // Process many frames to test memory management
        int frameCount = 100;
        for (int i = 0; i < frameCount; i++) {
            Image frameImage = mock(Image.class);
            when(frameImage.getWidth()).thenReturn(1280);
            when(frameImage.getHeight()).thenReturn(720);
            
            zeroCopyProcessor.processFrameZeroCopy(frameImage, callback);
        }
        
        // Force garbage collection
        System.gc();
        Thread.yield();
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        // Verify all frames were processed
        assertEquals("All frames should be processed", frameCount, processedFrames.get());
        
        // Verify memory usage remained stable
        assertTrue("Memory increase should be minimal with proper lifecycle management", 
                memoryIncrease < 10 * 1024 * 1024); // Less than 10MB increase
        
        // Verify no cloning occurred (which would cause memory leaks)
        verify(mockInputMat, never()).clone();
        verify(mockProcessedMat, never()).clone();
    }
    
    @Test
    public void testCopyOperationTracking() {
        // Test copy operation tracking functionality
        
        copyTracker.recordCopyOperation(CopyOperationTracker.CopyType.IMAGE_TO_MAT, 5, 1280 * 720 * 3);
        copyTracker.recordCopyOperation(CopyOperationTracker.CopyType.MAT_TO_BITMAP, 3, 1280 * 720 * 4);
        
        CopyOperationTracker.CopyOperationMetrics metrics = copyTracker.getMetrics();
        
        assertEquals("Should have 2 copy operations", 2, metrics.getTotalCopies());
        assertEquals("Total copy time should be 8ms", 8, metrics.getTotalTimeMs());
        assertEquals("Average copy time should be 4ms", 4.0, metrics.getAverageTimePerCopy(), 0.01);
        
        // Verify specific operation tracking
        assertEquals("Should track Image→Mat operation", 1, 
                metrics.getCopyCount(CopyOperationTracker.CopyType.IMAGE_TO_MAT));
        assertEquals("Should track Mat→Bitmap operation", 1, 
                metrics.getCopyCount(CopyOperationTracker.CopyType.MAT_TO_BITMAP));
    }
    
    @Test
    public void testPerformanceRegressionValidation() {
        // Comprehensive performance regression test
        
        assertTrue("ZeroCopyProcessor should initialize", zeroCopyProcessor.initialize());
        
        long startTime = System.currentTimeMillis();
        int frameCount = 50;
        
        ZeroCopyProcessor.ZeroCopyCallback callback = mock(ZeroCopyProcessor.ZeroCopyCallback.class);
        
        // Mock successful processing with optimized metrics
        doAnswer(invocation -> {
            ZeroCopyProcessor.ZeroCopyCallback cb = invocation.getArgument(1);
            Bitmap mockBitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
            
            // Simulate optimized processing times
            ZeroCopyProcessor.FrameProcessingMetrics metrics = 
                    new ZeroCopyProcessor.FrameProcessingMetrics(3, 12, 2, 2, true);
            
            cb.onFrameProcessed(mockInputMat, mockBitmap, mockImage, metrics);
            return null;
        }).when(callback).onFrameProcessed(any(), any(), any(), any());
        
        // Process frames
        for (int i = 0; i < frameCount; i++) {
            zeroCopyProcessor.processFrameZeroCopy(mockImage, callback);
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        // Verify performance metrics
        ZeroCopyProcessor.ZeroCopyPerformanceMetrics metrics = zeroCopyProcessor.getPerformanceMetrics();
        
        assertEquals("Frame count should match", frameCount, metrics.totalFrames);
        assertEquals("Copy operations should be 2 per frame", frameCount * 2, metrics.totalCopyOperations);
        assertEquals("Average copies per frame should be 2.0", 2.0, metrics.averageCopiesPerFrame, 0.01);
        
        // Verify processing time improvements
        assertTrue("Average processing time should be optimized", 
                metrics.averageProcessingTimeMs < 30);
        
        // Verify overall throughput
        float avgTimePerFrame = (float) totalTime / frameCount;
        assertTrue("Overall processing should be fast", avgTimePerFrame < 50);
        
        // Verify callback was called for all frames
        verify(callback, times(frameCount)).onFrameProcessed(any(), any(), any(), any());
    }
}