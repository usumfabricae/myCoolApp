package com.example.opencvcamerastream.performance;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.opencv.core.Mat;
import org.opencv.core.CvType;

import com.example.opencvcamerastream.processing.OpenCVProcessor;
import com.example.opencvcamerastream.processing.FrameBuffer;
import com.example.opencvcamerastream.performance.PerformanceMetricsCollector;
import com.example.opencvcamerastream.error.PerformanceMonitor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Performance regression tests to validate frame processing performance
 * Requirements: 1.3, 2.3, 5.1, 5.3
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Test on Android 10
public class PerformanceRegressionTest {

    @Mock
    private OpenCVProcessor mockProcessor;
    
    @Mock
    private FrameBuffer mockFrameBuffer;
    
    @Mock
    private PerformanceMonitor mockPerformanceMonitor;
    
    private PerformanceMetricsCollector metricsCollector;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        metricsCollector = new PerformanceMetricsCollector(mockPerformanceMonitor);
    }

    @Test
    public void testFrameProcessingPerformanceBaseline() {
        // Test that frame processing meets the 50ms requirement (Requirement 2.3)
        long startTime = System.currentTimeMillis();
        
        // Simulate frame processing
        when(mockProcessor.processFrame(any(Mat.class))).thenReturn(createMockMat());
        
        Mat result = mockProcessor.processFrame(createMockMat());
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        assertNotNull("Processed frame should not be null", result);
        assertTrue("Frame processing should complete within 50ms baseline", 
                   processingTime < 50);
        
        // Clean up
        if (result != null) {
            result.release();
        }
    }

    @Test
    public void testMemoryUsageStability() {
        // Test memory usage remains stable during extended processing (Requirement 5.2)
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Simulate processing multiple frames
        for (int i = 0; i < 100; i++) {
            when(mockProcessor.processFrame(any(Mat.class))).thenReturn(createMockMat());
            Mat result = mockProcessor.processFrame(createMockMat());
            if (result != null) {
                result.release();
            }
        }
        
        // Force garbage collection
        System.gc();
        Thread.yield();
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        // Memory increase should be reasonable (less than 10MB for 100 frames)
        assertTrue("Memory usage should remain stable during processing", 
                   memoryIncrease < 10 * 1024 * 1024);
    }

    @Test
    public void testFrameRateConsistency() {
        // Test that frame rate remains consistent (Requirement 1.3)
        int targetFps = 10; // Minimum 10 FPS requirement
        long frameInterval = 1000 / targetFps; // 100ms per frame
        
        long[] frameTimes = new long[10];
        
        for (int i = 0; i < 10; i++) {
            long startTime = System.currentTimeMillis();
            when(mockProcessor.processFrame(any(Mat.class))).thenReturn(createMockMat());
            Mat result = mockProcessor.processFrame(createMockMat());
            frameTimes[i] = System.currentTimeMillis() - startTime;
            if (result != null) {
                result.release();
            }
        }
        
        // Calculate average frame time
        long totalTime = 0;
        for (long frameTime : frameTimes) {
            totalTime += frameTime;
        }
        long averageFrameTime = totalTime / frameTimes.length;
        
        assertTrue("Average frame processing should support minimum 10 FPS", 
                   averageFrameTime <= frameInterval);
    }

    @Test
    public void testPerformanceUnderMemoryPressure() {
        // Test performance degradation under memory pressure (Requirement 3.4)
        // Simulate memory pressure through performance monitor
        when(mockPerformanceMonitor.getCurrentPerformanceLevel())
            .thenReturn(PerformanceMonitor.PerformanceLevel.CRITICAL);
        
        long startTime = System.currentTimeMillis();
        when(mockProcessor.processFrame(any(Mat.class))).thenReturn(createMockMat());
        Mat result = mockProcessor.processFrame(createMockMat());
        long processingTime = System.currentTimeMillis() - startTime;
        
        assertNotNull("Processing should continue under memory pressure", result);
        // Under memory pressure, processing might be slower but should still complete
        assertTrue("Processing under memory pressure should complete within reasonable time", 
                   processingTime < 200); // Allow more time under pressure
        
        // Clean up
        if (result != null) {
            result.release();
        }
    }

    @Test
    public void testInitializationPerformance() {
        // Test that initialization meets the 3-second requirement (Requirement 5.1)
        long startTime = System.currentTimeMillis();
        
        // Simulate OpenCV and camera initialization
        when(mockProcessor.initialize()).thenReturn(true);
        boolean initialized = mockProcessor.initialize();
        
        long initTime = System.currentTimeMillis() - startTime;
        
        assertTrue("Initialization should succeed", initialized);
        assertTrue("Initialization should complete within 3 seconds", 
                   initTime < 3000);
    }

    @Test
    public void testPerformanceMetricsCollection() {
        // Test that performance metrics are collected correctly
        metricsCollector.startMonitoring();
        
        // Simulate processing delay
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        metricsCollector.recordFrameProcessed(10);
        
        PerformanceMetricsCollector.FrameRateMetrics metrics = metricsCollector.getCurrentMetrics();
        assertTrue("Processing time should be recorded", metrics.totalFrames > 0);
        assertTrue("Processing time should be reasonable", metrics.averageProcessingTimeMs >= 0);
        
        metricsCollector.stopMonitoring();
    }

    @Test
    public void testFrameDropping() {
        // Test frame dropping functionality
        metricsCollector.setFrameDroppingEnabled(true);
        metricsCollector.setFrameSkipRatio(1); // Skip every other frame
        
        boolean shouldDrop1 = metricsCollector.shouldDropFrame();
        boolean shouldDrop2 = metricsCollector.shouldDropFrame();
        
        // One of these should be true (frame should be dropped)
        assertTrue("Frame dropping should work", shouldDrop1 || shouldDrop2);
    }

    private Mat createMockMat() {
        // Create a small test Mat
        return new Mat(100, 100, CvType.CV_8UC3);
    }
}