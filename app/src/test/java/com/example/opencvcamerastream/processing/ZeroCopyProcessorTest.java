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

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ZeroCopyProcessor
 * 
 * Tests Requirements 13.1, 13.2:
 * - Validate zero-copy processing path implementation
 * - Verify copy operation reduction
 * - Test thread safety
 * - Validate performance metrics
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class ZeroCopyProcessorTest {
    
    @Mock
    private OpenCVProcessor mockOpenCVProcessor;
    
    @Mock
    private Image mockImage;
    
    @Mock
    private ZeroCopyProcessor.ZeroCopyCallback mockCallback;
    
    private ZeroCopyProcessor zeroCopyProcessor;
    private Mat testMat;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Initialize OpenCV for testing (mock)
        when(mockOpenCVProcessor.isInitialized()).thenReturn(true);
        
        // Create test Mat
        testMat = new Mat(240, 320, CvType.CV_8UC3);
        testMat.setTo(new org.opencv.core.Scalar(100, 150, 200));
        
        // Mock OpenCV processor behavior
        when(mockOpenCVProcessor.processFrame(any(Mat.class))).thenReturn(testMat);
        
        // Create zero-copy processor
        zeroCopyProcessor = new ZeroCopyProcessor(mockOpenCVProcessor);
    }
    
    @Test
    public void testInitialization() {
        // Test initialization
        assertTrue("ZeroCopyProcessor should initialize successfully", 
                zeroCopyProcessor.initialize());
        assertTrue("ZeroCopyProcessor should be initialized", 
                zeroCopyProcessor.isInitialized());
    }
    
    @Test
    public void testInitializationFailsWhenOpenCVNotInitialized() {
        // Mock OpenCV processor not initialized
        when(mockOpenCVProcessor.isInitialized()).thenReturn(false);
        
        ZeroCopyProcessor processor = new ZeroCopyProcessor(mockOpenCVProcessor);
        assertFalse("ZeroCopyProcessor should fail to initialize when OpenCV not ready", 
                processor.initialize());
        assertFalse("ZeroCopyProcessor should not be initialized", 
                processor.isInitialized());
    }
    
    @Test
    public void testProcessFrameZeroCopySuccess() {
        // Initialize processor
        assertTrue(zeroCopyProcessor.initialize());
        
        // Mock successful processing
        doAnswer(invocation -> {
            ZeroCopyProcessor.ZeroCopyCallback callback = invocation.getArgument(1);
            
            // Create mock bitmap
            Bitmap mockBitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
            
            // Create metrics
            ZeroCopyProcessor.FrameProcessingMetrics metrics = 
                    new ZeroCopyProcessor.FrameProcessingMetrics(5, 10, 3, 2, true);
            
            // Call success callback
            callback.onFrameProcessed(testMat, mockBitmap, mockImage, metrics);
            return null;
        }).when(mockCallback).onFrameProcessed(any(), any(), any(), any());
        
        // Process frame
        zeroCopyProcessor.processFrameZeroCopy(mockImage, mockCallback);
        
        // Verify callback was called
        verify(mockCallback, times(1)).onFrameProcessed(any(Mat.class), any(Bitmap.class), 
                eq(mockImage), any(ZeroCopyProcessor.FrameProcessingMetrics.class));
        verify(mockCallback, never()).onProcessingFailed(any(), any());
    }
    
    @Test
    public void testProcessFrameZeroCopyFailure() {
        // Initialize processor
        assertTrue(zeroCopyProcessor.initialize());
        
        // Mock processing failure
        RuntimeException testException = new RuntimeException("Test processing failure");
        doAnswer(invocation -> {
            ZeroCopyProcessor.ZeroCopyCallback callback = invocation.getArgument(1);
            callback.onProcessingFailed(testException, mockImage);
            return null;
        }).when(mockCallback).onProcessingFailed(any(), any());
        
        // Process frame
        zeroCopyProcessor.processFrameZeroCopy(mockImage, mockCallback);
        
        // Verify failure callback was called
        verify(mockCallback, times(1)).onProcessingFailed(eq(testException), eq(mockImage));
        verify(mockCallback, never()).onFrameProcessed(any(), any(), any(), any());
    }
    
    @Test
    public void testProcessFrameWhenNotInitialized() {
        // Don't initialize processor
        assertFalse(zeroCopyProcessor.isInitialized());
        
        // Process frame
        zeroCopyProcessor.processFrameZeroCopy(mockImage, mockCallback);
        
        // Verify failure callback was called
        verify(mockCallback, times(1)).onProcessingFailed(any(IllegalStateException.class), eq(mockImage));
        verify(mockCallback, never()).onFrameProcessed(any(), any(), any(), any());
    }
    
    @Test
    public void testPerformanceMetricsInitialState() {
        ZeroCopyProcessor.ZeroCopyPerformanceMetrics metrics = zeroCopyProcessor.getPerformanceMetrics();
        
        assertNotNull("Performance metrics should not be null", metrics);
        assertEquals("Initial frame count should be 0", 0, metrics.totalFrames);
        assertEquals("Initial copy operations should be 0", 0, metrics.totalCopyOperations);
        assertEquals("Initial processing time should be 0", 0, metrics.totalProcessingTimeMs);
        assertEquals("Initial average copies per frame should be 0", 0.0, metrics.averageCopiesPerFrame, 0.01);
    }
    
    @Test
    public void testPerformanceMetricsAfterProcessing() {
        // Initialize processor
        assertTrue(zeroCopyProcessor.initialize());
        
        // Mock successful processing with metrics
        doAnswer(invocation -> {
            ZeroCopyProcessor.ZeroCopyCallback callback = invocation.getArgument(1);
            
            Bitmap mockBitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
            ZeroCopyProcessor.FrameProcessingMetrics frameMetrics = 
                    new ZeroCopyProcessor.FrameProcessingMetrics(5, 10, 3, 2, true);
            
            callback.onFrameProcessed(testMat, mockBitmap, mockImage, frameMetrics);
            return null;
        }).when(mockCallback).onFrameProcessed(any(), any(), any(), any());
        
        // Process multiple frames
        for (int i = 0; i < 5; i++) {
            zeroCopyProcessor.processFrameZeroCopy(mockImage, mockCallback);
        }
        
        // Check metrics
        ZeroCopyProcessor.ZeroCopyPerformanceMetrics metrics = zeroCopyProcessor.getPerformanceMetrics();
        
        assertEquals("Frame count should be 5", 5, metrics.totalFrames);
        assertEquals("Copy operations should be 10 (2 per frame)", 10, metrics.totalCopyOperations);
        assertEquals("Average copies per frame should be 2.0", 2.0, metrics.averageCopiesPerFrame, 0.01);
        assertTrue("Processing time should be greater than 0", metrics.totalProcessingTimeMs > 0);
    }
    
    @Test
    public void testResetPerformanceMetrics() {
        // Initialize and process some frames
        assertTrue(zeroCopyProcessor.initialize());
        
        doAnswer(invocation -> {
            ZeroCopyProcessor.ZeroCopyCallback callback = invocation.getArgument(1);
            Bitmap mockBitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
            ZeroCopyProcessor.FrameProcessingMetrics frameMetrics = 
                    new ZeroCopyProcessor.FrameProcessingMetrics(5, 10, 3, 2, true);
            callback.onFrameProcessed(testMat, mockBitmap, mockImage, frameMetrics);
            return null;
        }).when(mockCallback).onFrameProcessed(any(), any(), any(), any());
        
        zeroCopyProcessor.processFrameZeroCopy(mockImage, mockCallback);
        
        // Verify metrics are not zero
        ZeroCopyProcessor.ZeroCopyPerformanceMetrics beforeReset = zeroCopyProcessor.getPerformanceMetrics();
        assertTrue("Metrics should have data before reset", beforeReset.totalFrames > 0);
        
        // Reset metrics
        zeroCopyProcessor.resetPerformanceMetrics();
        
        // Verify metrics are reset
        ZeroCopyProcessor.ZeroCopyPerformanceMetrics afterReset = zeroCopyProcessor.getPerformanceMetrics();
        assertEquals("Frame count should be 0 after reset", 0, afterReset.totalFrames);
        assertEquals("Copy operations should be 0 after reset", 0, afterReset.totalCopyOperations);
        assertEquals("Processing time should be 0 after reset", 0, afterReset.totalProcessingTimeMs);
    }
    
    @Test
    public void testFrameProcessingMetricsCalculation() {
        // Test metrics calculation
        ZeroCopyProcessor.FrameProcessingMetrics metrics = 
                new ZeroCopyProcessor.FrameProcessingMetrics(5, 10, 3, 2, true);
        
        assertEquals("Image to Mat time should be 5ms", 5, metrics.imageToMatTimeMs);
        assertEquals("Processing time should be 10ms", 10, metrics.processingTimeMs);
        assertEquals("Mat to Bitmap time should be 3ms", 3, metrics.matToBitmapTimeMs);
        assertEquals("Total time should be 18ms", 18, metrics.totalTimeMs);
        assertEquals("Copy operations should be 2", 2, metrics.copyOperationsCount);
        assertTrue("Should use zero-copy path", metrics.usedZeroCopyPath);
        
        // Test toString
        String metricsString = metrics.toString();
        assertNotNull("Metrics string should not be null", metricsString);
        assertTrue("Metrics string should contain total time", metricsString.contains("total=18ms"));
        assertTrue("Metrics string should contain copy count", metricsString.contains("copies=2"));
        assertTrue("Metrics string should contain zero-copy flag", metricsString.contains("zeroCopy=true"));
    }
    
    @Test
    public void testZeroCopyPerformanceMetricsCalculation() {
        // Test performance metrics calculation
        ZeroCopyProcessor.ZeroCopyPerformanceMetrics metrics = 
                new ZeroCopyProcessor.ZeroCopyPerformanceMetrics(10, 25, 180, 10, 10);
        
        assertEquals("Total frames should be 10", 10, metrics.totalFrames);
        assertEquals("Total copy operations should be 25", 25, metrics.totalCopyOperations);
        assertEquals("Average copies per frame should be 2.5", 2.5, metrics.averageCopiesPerFrame, 0.01);
        assertEquals("Total processing time should be 180ms", 180, metrics.totalProcessingTimeMs);
        assertEquals("Average processing time should be 18ms", 18.0, metrics.averageProcessingTimeMs, 0.01);
        assertEquals("Image to Mat conversions should be 10", 10, metrics.imageToMatConversions);
        assertEquals("Mat to Bitmap conversions should be 10", 10, metrics.matToBitmapConversions);
        
        // Test CPU usage reduction calculation
        // With 2.5 copies per frame vs target of 2.0, should have some reduction
        assertTrue("CPU usage reduction should be calculated", metrics.cpuUsageReduction >= 0);
        
        // Test toString
        String metricsString = metrics.toString();
        assertNotNull("Metrics string should not be null", metricsString);
        assertTrue("Metrics string should contain frame count", metricsString.contains("frames=10"));
        assertTrue("Metrics string should contain average copies", metricsString.contains("avgCopies=2.5"));
    }
    
    @Test
    public void testRelease() {
        // Initialize processor
        assertTrue(zeroCopyProcessor.initialize());
        assertTrue(zeroCopyProcessor.isInitialized());
        
        // Release processor
        zeroCopyProcessor.release();
        
        // Verify state after release
        assertFalse("Processor should not be initialized after release", 
                zeroCopyProcessor.isInitialized());
    }
    
    @Test
    public void testConcurrentAccess() throws InterruptedException {
        // Initialize processor
        assertTrue(zeroCopyProcessor.initialize());
        
        // Mock successful processing
        doAnswer(invocation -> {
            ZeroCopyProcessor.ZeroCopyCallback callback = invocation.getArgument(1);
            Bitmap mockBitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
            ZeroCopyProcessor.FrameProcessingMetrics frameMetrics = 
                    new ZeroCopyProcessor.FrameProcessingMetrics(5, 10, 3, 2, true);
            callback.onFrameProcessed(testMat, mockBitmap, mockImage, frameMetrics);
            return null;
        }).when(mockCallback).onFrameProcessed(any(), any(), any(), any());
        
        // Create multiple threads to test concurrent access
        int threadCount = 4;
        int framesPerThread = 10;
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < framesPerThread; j++) {
                    zeroCopyProcessor.processFrameZeroCopy(mockImage, mockCallback);
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(5000); // 5 second timeout
        }
        
        // Verify all frames were processed
        ZeroCopyProcessor.ZeroCopyPerformanceMetrics metrics = zeroCopyProcessor.getPerformanceMetrics();
        assertEquals("All frames should be processed", threadCount * framesPerThread, metrics.totalFrames);
        
        // Verify callback was called correct number of times
        verify(mockCallback, times(threadCount * framesPerThread))
                .onFrameProcessed(any(), any(), any(), any());
    }
    
    @Test
    public void testOwnershipTransferSemantics() {
        // Test Task 21: Verify ownership transfer semantics in zero-copy processor
        // Requirement Req-13.3: Replace callback clones with ownership transfer
        
        assertTrue(zeroCopyProcessor.initialize());
        
        // Mock callback to verify ownership transfer
        doAnswer(invocation -> {
            Mat receivedMat = invocation.getArgument(0);
            Bitmap receivedBitmap = invocation.getArgument(1);
            Image receivedImage = invocation.getArgument(2);
            
            // Verify we received the actual objects (not clones)
            assertSame("Mat should be transferred, not cloned", testMat, receivedMat);
            assertSame("Image should be transferred, not cloned", mockImage, receivedImage);
            assertNotNull("Bitmap should be provided", receivedBitmap);
            
            // In real implementation, callback would:
            // receivedMat.release();
            // receivedBitmap.recycle();
            // receivedImage.close();
            
            return null;
        }).when(mockCallback).onFrameProcessed(any(), any(), any(), any());
        
        // Process frame
        zeroCopyProcessor.processFrameZeroCopy(mockImage, mockCallback);
        
        // Verify callback was called with ownership transfer
        verify(mockCallback, times(1)).onFrameProcessed(any(), any(), any(), any());
    }
    
    @Test
    public void testThreadSafetyWithoutDefensiveCloning() throws InterruptedException {
        // Test Task 22: Verify thread safety without defensive cloning
        // Requirement Req-13.4: Remove DisplayManager defensive cloning
        
        assertTrue(zeroCopyProcessor.initialize());
        
        // Mock processing to simulate concurrent access
        doAnswer(invocation -> {
            ZeroCopyProcessor.ZeroCopyCallback callback = invocation.getArgument(1);
            
            // Simulate some processing time
            Thread.sleep(10);
            
            Bitmap mockBitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
            ZeroCopyProcessor.FrameProcessingMetrics frameMetrics = 
                    new ZeroCopyProcessor.FrameProcessingMetrics(5, 10, 3, 2, true);
            
            callback.onFrameProcessed(testMat, mockBitmap, mockImage, frameMetrics);
            return null;
        }).when(mockCallback).onFrameProcessed(any(), any(), any(), any());
        
        // Create concurrent access scenario
        int concurrentOperations = 20;
        Thread[] threads = new Thread[concurrentOperations];
        
        for (int i = 0; i < concurrentOperations; i++) {
            threads[i] = new Thread(() -> {
                zeroCopyProcessor.processFrameZeroCopy(mockImage, mockCallback);
            });
        }
        
        // Start all threads simultaneously
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            thread.join(2000);
        }
        
        // Verify all operations completed successfully
        verify(mockCallback, times(concurrentOperations))
                .onFrameProcessed(any(), any(), any(), any());
        
        // Verify no failures occurred due to race conditions
        verify(mockCallback, never()).onProcessingFailed(any(), any());
    }
    
    @Test
    public void testCopyOperationTracking() {
        // Test Task 24: Verify copy operation tracking for performance validation
        // Requirement Req-13.1, Req-13.2: Validate copy operation reduction
        
        assertTrue(zeroCopyProcessor.initialize());
        
        // Mock successful processing with specific copy count
        doAnswer(invocation -> {
            ZeroCopyProcessor.ZeroCopyCallback callback = invocation.getArgument(1);
            Bitmap mockBitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
            
            // Report exactly 2 copy operations (target for zero-copy optimization)
            ZeroCopyProcessor.FrameProcessingMetrics frameMetrics = 
                    new ZeroCopyProcessor.FrameProcessingMetrics(5, 10, 3, 2, true);
            
            callback.onFrameProcessed(testMat, mockBitmap, mockImage, frameMetrics);
            return null;
        }).when(mockCallback).onFrameProcessed(any(), any(), any(), any());
        
        // Process multiple frames
        int frameCount = 10;
        for (int i = 0; i < frameCount; i++) {
            zeroCopyProcessor.processFrameZeroCopy(mockImage, mockCallback);
        }
        
        // Verify copy operation tracking
        ZeroCopyProcessor.ZeroCopyPerformanceMetrics metrics = zeroCopyProcessor.getPerformanceMetrics();
        
        assertEquals("Frame count should match", frameCount, metrics.totalFrames);
        assertEquals("Total copy operations should be 2 per frame", frameCount * 2, metrics.totalCopyOperations);
        assertEquals("Average copies per frame should be 2.0", 2.0, metrics.averageCopiesPerFrame, 0.01);
        
        // Verify zero-copy optimization target is met
        assertTrue("Should meet zero-copy target of ≤2 copies per frame", 
                metrics.averageCopiesPerFrame <= 2.0);
    }
    
    @Test
    public void testPerformanceRegressionValidation() {
        // Test Task 25: Validate CPU usage reduction and performance improvements
        // Requirement Req-13.6: Measure and validate CPU usage reduction
        
        assertTrue(zeroCopyProcessor.initialize());
        
        // Mock processing with performance metrics
        doAnswer(invocation -> {
            ZeroCopyProcessor.ZeroCopyCallback callback = invocation.getArgument(1);
            Bitmap mockBitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
            
            // Simulate optimized processing times (20-30ms improvement)
            ZeroCopyProcessor.FrameProcessingMetrics frameMetrics = 
                    new ZeroCopyProcessor.FrameProcessingMetrics(3, 15, 2, 2, true);
            
            callback.onFrameProcessed(testMat, mockBitmap, mockImage, frameMetrics);
            return null;
        }).when(mockCallback).onFrameProcessed(any(), any(), any(), any());
        
        // Process frames and measure performance
        long startTime = System.currentTimeMillis();
        int frameCount = 50;
        
        for (int i = 0; i < frameCount; i++) {
            zeroCopyProcessor.processFrameZeroCopy(mockImage, mockCallback);
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        // Verify performance metrics
        ZeroCopyProcessor.ZeroCopyPerformanceMetrics metrics = zeroCopyProcessor.getPerformanceMetrics();
        
        assertEquals("Frame count should match", frameCount, metrics.totalFrames);
        assertTrue("Average processing time should be reasonable", 
                metrics.averageProcessingTimeMs < 50); // Target: <50ms per frame
        
        // Verify total processing time is reasonable
        float avgTimePerFrame = (float) totalTime / frameCount;
        assertTrue("Average time per frame should be optimized", avgTimePerFrame < 100);
        
        // Verify copy operation reduction
        assertTrue("Should achieve copy reduction target", metrics.averageCopiesPerFrame <= 2.0);
    }
}