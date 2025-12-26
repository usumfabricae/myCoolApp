package com.example.opencvcamerastream.performance;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

/**
 * Unit tests for CpuUsageProfiler
 * 
 * Tests cover:
 * - Monitoring lifecycle (start/stop)
 * - CPU measurement recording
 * - Frame processing measurement
 * - Baseline capture and comparison
 * - Performance validation against targets
 * - Memory measurement tracking
 * - Error handling and edge cases
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class CpuUsageProfilerTest {
    
    private CpuUsageProfiler profiler;
    
    @Before
    public void setUp() {
        profiler = new CpuUsageProfiler();
    }
    
    @After
    public void tearDown() {
        // Clean up any monitoring that might be active
        try {
            profiler.stopMonitoring();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
    
    // Monitoring Lifecycle Tests
    
    @Test
    public void testStartMonitoring_InitializesCorrectly() {
        // Test that monitoring can be started
        profiler.startMonitoring();
        
        // Give it a moment to initialize
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Stop monitoring
        CpuUsageProfiler.CpuUsageMetrics metrics = profiler.stopMonitoring();
        assertNotNull("Metrics should be returned when stopping", metrics);
    }
    
    @Test
    public void testStopMonitoring_WithoutStart_ReturnsNull() {
        // Test stopping monitoring when it wasn't started
        CpuUsageProfiler.CpuUsageMetrics metrics = profiler.stopMonitoring();
        assertNull("Should return null when monitoring wasn't started", metrics);
    }
    
    @Test
    public void testDoubleStart_DoesNotCrash() {
        // Test starting monitoring twice
        profiler.startMonitoring();
        profiler.startMonitoring(); // Should not crash
        
        CpuUsageProfiler.CpuUsageMetrics metrics = profiler.stopMonitoring();
        assertNotNull("Should still return metrics", metrics);
    }
    
    @Test
    public void testDoubleStop_DoesNotCrash() {
        // Test stopping monitoring twice
        profiler.startMonitoring();
        CpuUsageProfiler.CpuUsageMetrics metrics1 = profiler.stopMonitoring();
        CpuUsageProfiler.CpuUsageMetrics metrics2 = profiler.stopMonitoring(); // Should not crash
        
        assertNotNull("First stop should return metrics", metrics1);
        assertNull("Second stop should return null", metrics2);
    }
    
    // Frame Processing Tests
    
    @Test
    public void testRecordFrameProcessing_UpdatesMetrics() {
        profiler.startMonitoring();
        
        // Record some frame processing
        long startTime = System.currentTimeMillis() - 50; // 50ms ago
        profiler.recordFrameProcessingEnd(startTime, true);
        profiler.recordFrameProcessingEnd(startTime - 30, false);
        
        // Give it a moment to process
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        CpuUsageProfiler.CpuUsageMetrics metrics = profiler.stopMonitoring();
        assertNotNull("Metrics should be available", metrics);
        assertTrue("Should have recorded frames", metrics.totalFrames > 0);
    }
    
    @Test
    public void testRecordFrameProcessing_WithoutMonitoring_DoesNotCrash() {
        // Test recording frame processing when monitoring is not active
        long startTime = System.currentTimeMillis() - 50;
        profiler.recordFrameProcessingEnd(startTime, true); // Should not crash
        
        CpuUsageProfiler.CpuUsageMetrics metrics = profiler.getCurrentMetrics();
        assertNotNull("Should return metrics even without monitoring", metrics);
    }
    
    // Memory Tracking Tests
    
    @Test
    public void testRecordMemoryAllocation_UpdatesMetrics() {
        profiler.startMonitoring();
        
        // Record memory allocations
        profiler.recordMemoryAllocation(1024 * 1024); // 1MB
        profiler.recordMemoryAllocation(512 * 1024);  // 512KB
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        CpuUsageProfiler.CpuUsageMetrics metrics = profiler.stopMonitoring();
        assertNotNull("Metrics should be available", metrics);
        assertTrue("Should track memory usage", metrics.totalMemoryMB >= 0);
    }
    
    @Test
    public void testRecordGarbageCollection_UpdatesMetrics() {
        profiler.startMonitoring();
        
        // Record GC events
        profiler.recordGarbageCollection();
        profiler.recordGarbageCollection();
        profiler.recordGarbageCollection();
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        CpuUsageProfiler.CpuUsageMetrics metrics = profiler.stopMonitoring();
        assertNotNull("Metrics should be available", metrics);
        assertTrue("Should track GC frequency", metrics.gcFrequencyPerSecond >= 0);
    }
    
    // Baseline and Validation Tests
    
    @Test
    public void testSetBaselineMetrics_StoresCorrectly() {
        // Create baseline metrics
        CpuUsageProfiler.CpuUsageMetrics baseline = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 50.0, 25.0, 100.0, 80, 120, 50, 2.0, 100, 120);
        
        profiler.setBaselineMetrics(baseline);
        
        // Validation should work now
        profiler.startMonitoring();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        CpuUsageProfiler.OptimizationResults results = profiler.validateOptimization();
        profiler.stopMonitoring();
        
        assertNotNull("Should return validation results", results);
        assertEquals("Baseline should match", baseline, results.baseline);
    }
    
    @Test
    public void testValidateOptimization_WithoutBaseline_ReturnsNull() {
        profiler.startMonitoring();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        CpuUsageProfiler.OptimizationResults results = profiler.validateOptimization();
        profiler.stopMonitoring();
        
        assertNull("Should return null without baseline", results);
    }
    
    @Test
    public void testValidateOptimization_WithGoodOptimization_MeetsTargets() {
        // Set high baseline (poor performance)
        CpuUsageProfiler.CpuUsageMetrics baseline = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 80.0, 40.0, 150.0, 100, 200, 50, 5.0, 200, 250);
        profiler.setBaselineMetrics(baseline);
        
        profiler.startMonitoring();
        
        // Simulate good performance (low latency, low CPU)
        for (int i = 0; i < 10; i++) {
            long startTime = System.currentTimeMillis() - 25; // 25ms processing time
            profiler.recordFrameProcessingEnd(startTime, true);
        }
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        CpuUsageProfiler.OptimizationResults results = profiler.validateOptimization();
        profiler.stopMonitoring();
        
        assertNotNull("Should return validation results", results);
        assertTrue("Should show improvement in latency", results.latencyImprovementMs > 0);
    }
    
    @Test
    public void testValidateOptimization_WithPoorOptimization_DoesNotMeetTargets() {
        // Set low baseline (good performance)
        CpuUsageProfiler.CpuUsageMetrics baseline = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 20.0, 15.0, 50.0, 40, 60, 50, 1.0, 50, 60);
        profiler.setBaselineMetrics(baseline);
        
        profiler.startMonitoring();
        
        // Simulate poor performance (high latency)
        for (int i = 0; i < 10; i++) {
            long startTime = System.currentTimeMillis() - 120; // 120ms processing time
            profiler.recordFrameProcessingEnd(startTime, false);
        }
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        CpuUsageProfiler.OptimizationResults results = profiler.validateOptimization();
        profiler.stopMonitoring();
        
        assertNotNull("Should return validation results", results);
        assertFalse("Should not meet all targets with poor performance", results.meetsAllTargets);
    }
    
    // Metrics Tests
    
    @Test
    public void testGetCurrentMetrics_WithoutMonitoring_ReturnsValidMetrics() {
        CpuUsageProfiler.CpuUsageMetrics metrics = profiler.getCurrentMetrics();
        
        assertNotNull("Should return metrics", metrics);
        assertEquals("Should have zero frames initially", 0, metrics.totalFrames);
        assertTrue("Duration should be non-negative", metrics.measurementDurationMs >= 0);
    }
    
    @Test
    public void testGetCurrentMetrics_WithMonitoring_ReturnsUpdatedMetrics() {
        profiler.startMonitoring();
        
        // Record some activity
        long startTime = System.currentTimeMillis() - 50;
        profiler.recordFrameProcessingEnd(startTime, true);
        profiler.recordMemoryAllocation(1024);
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        CpuUsageProfiler.CpuUsageMetrics metrics = profiler.getCurrentMetrics();
        profiler.stopMonitoring();
        
        assertNotNull("Should return metrics", metrics);
        assertTrue("Should have recorded frames", metrics.totalFrames > 0);
        assertTrue("Duration should be positive", metrics.measurementDurationMs > 0);
    }
    
    // CpuUsageMetrics Tests
    
    @Test
    public void testCpuUsageMetrics_MeetsFramebufferTarget() {
        CpuUsageProfiler.CpuUsageMetrics metrics = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 50.0, 25.0, 100.0, 80, 120, 50, 2.0, 100, 120);
        
        assertTrue("Should meet framebuffer target with 25%", metrics.meetsFramebufferTarget());
        
        CpuUsageProfiler.CpuUsageMetrics highMetrics = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 50.0, 35.0, 100.0, 80, 120, 50, 2.0, 100, 120);
        
        assertFalse("Should not meet framebuffer target with 35%", highMetrics.meetsFramebufferTarget());
    }
    
    @Test
    public void testCpuUsageMetrics_MeetsLatencyTarget() {
        CpuUsageProfiler.CpuUsageMetrics baseline = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 50.0, 25.0, 100.0, 80, 120, 50, 2.0, 100, 120);
        
        CpuUsageProfiler.CpuUsageMetrics improved = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 40.0, 20.0, 70.0, 60, 90, 50, 1.5, 90, 100);
        
        assertTrue("Should meet latency target with 30ms improvement", improved.meetsLatencyTarget(baseline));
        
        CpuUsageProfiler.CpuUsageMetrics notImproved = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 45.0, 22.0, 95.0, 75, 115, 50, 1.8, 95, 110);
        
        assertFalse("Should not meet latency target with 5ms improvement", notImproved.meetsLatencyTarget(baseline));
    }
    
    @Test
    public void testCpuUsageMetrics_MeetsCpuReductionTarget() {
        CpuUsageProfiler.CpuUsageMetrics baseline = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 100.0, 40.0, 100.0, 80, 120, 50, 2.0, 100, 120);
        
        CpuUsageProfiler.CpuUsageMetrics improved = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 50.0, 20.0, 70.0, 60, 90, 50, 1.5, 90, 100);
        
        assertTrue("Should meet CPU reduction target with 50% reduction", improved.meetsCpuReductionTarget(baseline));
        
        CpuUsageProfiler.CpuUsageMetrics notImproved = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 80.0, 30.0, 90.0, 70, 110, 50, 1.8, 95, 110);
        
        assertFalse("Should not meet CPU reduction target with 20% reduction", notImproved.meetsCpuReductionTarget(baseline));
    }
    
    // OptimizationResults Tests
    
    @Test
    public void testOptimizationResults_CalculatesImprovements() {
        CpuUsageProfiler.CpuUsageMetrics baseline = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 100.0, 40.0, 100.0, 80, 120, 50, 2.0, 100, 120);
        
        CpuUsageProfiler.CpuUsageMetrics optimized = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 60.0, 20.0, 70.0, 60, 90, 50, 1.0, 80, 90);
        
        CpuUsageProfiler.OptimizationResults results = new CpuUsageProfiler.OptimizationResults(baseline, optimized);
        
        assertEquals("CPU reduction should be 40%", 40.0, results.cpuReductionPercentage, 0.1);
        assertEquals("Latency improvement should be 30ms", 30.0, results.latencyImprovementMs, 0.1);
        assertEquals("Memory reduction should be 20%", 20.0, results.memoryReductionPercentage, 0.1);
        assertEquals("GC frequency reduction should be 1.0", 1.0, results.gcFrequencyReduction, 0.1);
    }
    
    @Test
    public void testOptimizationResults_MeetsAllTargets() {
        CpuUsageProfiler.CpuUsageMetrics baseline = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 100.0, 40.0, 100.0, 80, 120, 50, 2.0, 100, 120);
        
        CpuUsageProfiler.CpuUsageMetrics optimized = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 50.0, 20.0, 70.0, 60, 90, 50, 1.0, 80, 90);
        
        CpuUsageProfiler.OptimizationResults results = new CpuUsageProfiler.OptimizationResults(baseline, optimized);
        
        assertTrue("Should meet all targets with good optimization", results.meetsAllTargets);
        assertNotNull("Should have validation summary", results.validationSummary);
        assertTrue("Summary should contain success indicators", results.validationSummary.contains("✓"));
    }
    
    // Callback Tests
    
    @Test
    public void testCallback_ReceivesUpdates() {
        CpuUsageProfiler.CpuUsageCallback mockCallback = mock(CpuUsageProfiler.CpuUsageCallback.class);
        profiler.setCallback(mockCallback);
        
        profiler.startMonitoring();
        
        try {
            Thread.sleep(200); // Wait for some callbacks
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        profiler.stopMonitoring();
        
        // Verify callback was called
        verify(mockCallback, atLeastOnce()).onCpuUsageUpdate(any(CpuUsageProfiler.CpuUsageMetrics.class));
    }
    
    // Resource Management Tests
    
    @Test
    public void testRelease_CleansUpResources() {
        profiler.startMonitoring();
        
        // Release should stop monitoring and clean up
        profiler.release();
        
        // Should be able to start again after release
        profiler.startMonitoring();
        CpuUsageProfiler.CpuUsageMetrics metrics = profiler.stopMonitoring();
        assertNotNull("Should work after release", metrics);
    }
}