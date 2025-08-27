package com.example.opencvcamerastream.performance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.example.opencvcamerastream.error.PerformanceMonitor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for PerformanceMetricsCollector
 * 
 * Tests performance monitoring, frame rate calculation, automatic quality adjustment,
 * and frame dropping mechanisms.
 * 
 * Requirements tested:
 * - 1.3: Maintain smooth frame rate of at least 10 FPS
 * - 2.3: Return processed frame within 50ms
 * - 5.1: Initialize within 3 seconds
 * - 5.3: Automatically adjust processing parameters for optimal performance
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Android 10
public class PerformanceMetricsCollectorTest {
    
    @Mock
    private PerformanceMonitor mockPerformanceMonitor;
    
    @Mock
    private PerformanceMetricsCollector.PerformanceMetricsCallback mockCallback;
    
    private PerformanceMetricsCollector performanceMetricsCollector;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Set up mock performance monitor
        when(mockPerformanceMonitor.getCurrentPerformanceLevel())
                .thenReturn(PerformanceMonitor.PerformanceLevel.HIGH);
        
        performanceMetricsCollector = new PerformanceMetricsCollector(mockPerformanceMonitor);
        performanceMetricsCollector.setCallback(mockCallback);
    }
    
    @Test
    public void testInitialization() {
        // Test that collector initializes properly
        assertNotNull(performanceMetricsCollector);
        
        // Test initial metrics
        PerformanceMetricsCollector.FrameRateMetrics metrics = performanceMetricsCollector.getCurrentMetrics();
        assertNotNull(metrics);
        assertEquals(0.0f, metrics.currentFps, 0.01f);
        assertEquals(0.0f, metrics.averageFps, 0.01f);
        assertEquals(0, metrics.totalFrames);
        assertEquals(0, metrics.droppedFrames);
        assertFalse(metrics.isFrameDropping);
    }
    
    @Test
    public void testFrameProcessingRecording() {
        // Test recording frame processing times
        performanceMetricsCollector.startMonitoring();
        
        // Record several frames with good performance
        performanceMetricsCollector.recordFrameProcessed(30); // 30ms - good
        performanceMetricsCollector.recordFrameProcessed(25); // 25ms - good
        performanceMetricsCollector.recordFrameProcessed(35); // 35ms - good
        
        PerformanceMetricsCollector.FrameRateMetrics metrics = performanceMetricsCollector.getCurrentMetrics();
        assertEquals(3, metrics.totalFrames);
        assertEquals(30, metrics.averageProcessingTimeMs); // (30+25+35)/3 = 30
        assertEquals(35, metrics.maxProcessingTimeMs);
        
        // Verify performance monitor was called
        verify(mockPerformanceMonitor, times(3)).recordProcessingTime(anyLong());
    }
    
    @Test
    public void testFrameDropRecording() {
        performanceMetricsCollector.startMonitoring();
        
        // Record frame drops
        performanceMetricsCollector.recordFrameDropped("Test drop 1");
        performanceMetricsCollector.recordFrameDropped("Test drop 2");
        
        PerformanceMetricsCollector.FrameRateMetrics metrics = performanceMetricsCollector.getCurrentMetrics();
        assertEquals(2, metrics.droppedFrames);
        
        // Verify performance monitor was called
        verify(mockPerformanceMonitor, times(2)).recordFrameDrop(anyString());
        
        // Verify callback was called
        verify(mockCallback, times(2)).onFrameDropRecommended(anyString());
    }
    
    @Test
    public void testFrameDropMechanism() {
        performanceMetricsCollector.startMonitoring();
        
        // Initially no frame dropping
        assertFalse(performanceMetricsCollector.shouldDropFrame());
        
        // Enable frame dropping with skip ratio 1 (drop every other frame)
        performanceMetricsCollector.setFrameDroppingEnabled(true);
        performanceMetricsCollector.setFrameSkipRatio(1);
        
        // Test frame dropping pattern
        assertFalse(performanceMetricsCollector.shouldDropFrame()); // Frame 1: keep
        assertTrue(performanceMetricsCollector.shouldDropFrame());  // Frame 2: drop
        assertFalse(performanceMetricsCollector.shouldDropFrame()); // Frame 3: keep
        assertTrue(performanceMetricsCollector.shouldDropFrame());  // Frame 4: drop
        
        PerformanceMetricsCollector.FrameRateMetrics metrics = performanceMetricsCollector.getCurrentMetrics();
        assertEquals(2, metrics.droppedFrames); // 2 frames were dropped
    }
    
    @Test
    public void testPerformanceAdjustmentForSlowProcessing() {
        performanceMetricsCollector.startMonitoring();
        
        // Simulate slow processing times that should trigger adjustments
        performanceMetricsCollector.recordFrameProcessed(80); // Exceeds 50ms threshold
        performanceMetricsCollector.recordFrameProcessed(90);
        performanceMetricsCollector.recordFrameProcessed(100);
        
        // Should have enabled frame dropping due to slow processing
        PerformanceMetricsCollector.FrameRateMetrics metrics = performanceMetricsCollector.getCurrentMetrics();
        assertTrue("Frame dropping should be enabled for slow processing", metrics.isFrameDropping);
        
        // Verify performance monitor was notified of slow processing
        verify(mockPerformanceMonitor, times(3)).recordProcessingTime(anyLong());
    }
    
    @Test
    public void testPerformanceAdjustmentForLowFPS() {
        performanceMetricsCollector.startMonitoring();
        
        // Mock low FPS scenario by setting up performance monitor to return LOW level
        when(mockPerformanceMonitor.getCurrentPerformanceLevel())
                .thenReturn(PerformanceMonitor.PerformanceLevel.LOW);
        
        // Simulate frames with timing that would result in low FPS
        long baseTime = System.currentTimeMillis();
        
        // Record frames with large gaps (simulating low FPS)
        for (int i = 0; i < 5; i++) {
            performanceMetricsCollector.recordFrameProcessed(40);
            // Simulate time passing between frames (more than 100ms = <10 FPS)
            try {
                Thread.sleep(120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        PerformanceMetricsCollector.FrameRateMetrics metrics = performanceMetricsCollector.getCurrentMetrics();
        assertEquals(PerformanceMonitor.PerformanceLevel.LOW, metrics.currentLevel);
    }
    
    @Test
    public void testFrameSkipRatioAdjustment() {
        performanceMetricsCollector.startMonitoring();
        performanceMetricsCollector.setFrameDroppingEnabled(true);
        
        // Test different skip ratios
        performanceMetricsCollector.setFrameSkipRatio(0); // No skipping
        assertFalse(performanceMetricsCollector.shouldDropFrame());
        assertFalse(performanceMetricsCollector.shouldDropFrame());
        
        performanceMetricsCollector.setFrameSkipRatio(2); // Skip 2 out of 3 frames
        assertFalse(performanceMetricsCollector.shouldDropFrame()); // Frame 1: keep
        assertTrue(performanceMetricsCollector.shouldDropFrame());  // Frame 2: drop
        assertTrue(performanceMetricsCollector.shouldDropFrame());  // Frame 3: drop
        assertFalse(performanceMetricsCollector.shouldDropFrame()); // Frame 4: keep
    }
    
    @Test
    public void testPerformanceMetricsCalculation() {
        performanceMetricsCollector.startMonitoring();
        
        // Record mixed performance data
        performanceMetricsCollector.recordFrameProcessed(20);
        performanceMetricsCollector.recordFrameProcessed(40);
        performanceMetricsCollector.recordFrameProcessed(60);
        performanceMetricsCollector.recordFrameDropped("Performance test");
        
        PerformanceMetricsCollector.FrameRateMetrics metrics = performanceMetricsCollector.getCurrentMetrics();
        
        assertEquals(3, metrics.totalFrames);
        assertEquals(1, metrics.droppedFrames);
        assertEquals(40, metrics.averageProcessingTimeMs); // (20+40+60)/3 = 40
        assertEquals(60, metrics.maxProcessingTimeMs);
        
        // Drop rate should be 1/3 = 33.33%
        assertEquals(33.33f, metrics.dropRate, 0.1f);
    }
    
    @Test
    public void testCounterReset() {
        performanceMetricsCollector.startMonitoring();
        
        // Record some data
        performanceMetricsCollector.recordFrameProcessed(50);
        performanceMetricsCollector.recordFrameDropped("Test");
        
        PerformanceMetricsCollector.FrameRateMetrics beforeReset = performanceMetricsCollector.getCurrentMetrics();
        assertEquals(1, beforeReset.totalFrames);
        assertEquals(1, beforeReset.droppedFrames);
        
        // Reset counters
        performanceMetricsCollector.resetCounters();
        
        PerformanceMetricsCollector.FrameRateMetrics afterReset = performanceMetricsCollector.getCurrentMetrics();
        assertEquals(0, afterReset.totalFrames);
        assertEquals(0, afterReset.droppedFrames);
        assertEquals(0, afterReset.averageProcessingTimeMs);
        assertEquals(0, afterReset.maxProcessingTimeMs);
        assertEquals(0.0f, afterReset.currentFps, 0.01f);
    }
    
    @Test
    public void testMonitoringStartStop() {
        // Test that monitoring can be started and stopped
        performanceMetricsCollector.startMonitoring();
        
        // Record frame - should work when monitoring is active
        performanceMetricsCollector.recordFrameProcessed(30);
        PerformanceMetricsCollector.FrameRateMetrics metrics = performanceMetricsCollector.getCurrentMetrics();
        assertEquals(1, metrics.totalFrames);
        
        // Stop monitoring
        performanceMetricsCollector.stopMonitoring();
        
        // Record frame - should still work but won't trigger callbacks
        performanceMetricsCollector.recordFrameProcessed(40);
        metrics = performanceMetricsCollector.getCurrentMetrics();
        assertEquals(1, metrics.totalFrames); // Should not increment when monitoring is stopped
    }
    
    @Test
    public void testPerformanceAdjustmentCallback() {
        performanceMetricsCollector.startMonitoring();
        
        // Simulate scenario that should trigger performance adjustment
        // Record many slow frames to trigger adjustment
        for (int i = 0; i < 10; i++) {
            performanceMetricsCollector.recordFrameProcessed(80); // Slow processing
            try {
                Thread.sleep(50); // Simulate time between frames
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Allow time for performance adjustment to be calculated
        try {
            Thread.sleep(600); // Wait for performance update interval
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify that performance adjustment callback was called
        verify(mockCallback, atLeastOnce()).onFrameRateUpdate(any(PerformanceMetricsCollector.FrameRateMetrics.class));
    }
    
    @Test
    public void testRequirement1_3_MinimumFPS() {
        // Test Requirement 1.3: Maintain smooth frame rate of at least 10 FPS
        performanceMetricsCollector.startMonitoring();
        
        // Simulate frames at exactly 10 FPS (100ms intervals)
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            performanceMetricsCollector.recordFrameProcessed(30);
            try {
                Thread.sleep(100); // 100ms = 10 FPS
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Allow time for FPS calculation
        try {
            Thread.sleep(1100); // Wait for FPS calculation window
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        PerformanceMetricsCollector.FrameRateMetrics metrics = performanceMetricsCollector.getCurrentMetrics();
        
        // FPS should be around 10 (within tolerance)
        assertTrue("FPS should be at least 9 (close to 10 FPS requirement)", 
                   metrics.currentFps >= 9.0f);
    }
    
    @Test
    public void testRequirement2_3_ProcessingTimeLimit() {
        // Test Requirement 2.3: Return processed frame within 50ms
        performanceMetricsCollector.startMonitoring();
        
        // Record frames within the 50ms limit
        performanceMetricsCollector.recordFrameProcessed(30);
        performanceMetricsCollector.recordFrameProcessed(45);
        performanceMetricsCollector.recordFrameProcessed(50);
        
        PerformanceMetricsCollector.FrameRateMetrics metrics = performanceMetricsCollector.getCurrentMetrics();
        
        // Average should be within limit
        assertTrue("Average processing time should be within 50ms limit", 
                   metrics.averageProcessingTimeMs <= 50);
        
        // Max should be within limit
        assertTrue("Max processing time should be within 50ms limit", 
                   metrics.maxProcessingTimeMs <= 50);
        
        // Record frame that exceeds limit - should trigger frame dropping
        performanceMetricsCollector.recordFrameProcessed(80);
        
        metrics = performanceMetricsCollector.getCurrentMetrics();
        assertTrue("Frame dropping should be enabled when processing exceeds 50ms", 
                   metrics.isFrameDropping);
    }
}