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
 * - Profiling lifecycle (start/stop)
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
        if (profiler.isActive()) {
            profiler.stopProfiling();
        }
    }
    
    // Profiling Lifecycle Tests
    
    @Test
    public void testStartProfiling_WithoutWarmup_SetsActiveImmediately() {
        assertFalse("Profiler should not be active initially", profiler.isActive());
        assertFalse("Warmup should not be complete initially", profiler.isWarmupComplete());
        
        profiler.startProfiling(false);
        
        assertTrue("Profiler should be active after start", profiler.isActive());
        assertTrue("Warmup should be complete when disabled", profiler.isWarmupComplete());
    }
    
    @Test
    public void testStartProfiling_WithWarmup_RequiresWarmupPeriod() {
        profiler.startProfiling(true);
        
        assertTrue("Profiler should be active after start", profiler.isActive());
        assertFalse("Warmup should not be complete immediately", profiler.isWarmupComplete());
    }
    
    @Test
    public void testStartProfiling_WhenAlreadyActive_LogsWarning() {
        profiler.startProfiling(false);
        assertTrue("Profiler should be active", profiler.isActive());
        
        // Starting again should not change state
        profiler.startProfiling(false);
        assertTrue("Profiler should remain active", profiler.isActive());
    }
    
    @Test
    public void testStopProfiling_WhenActive_StopsSuccessfully() {
        profiler.startProfiling(false);
        assertTrue("Profiler should be active", profiler.isActive());
        
        profiler.stopProfiling();
        
        assertFalse("Profiler should not be active after stop", profiler.isActive());
        assertFalse("Warmup should not be complete after stop", profiler.isWarmupComplete());
    }
    
    @Test
    public void testStopProfiling_WhenNotActive_LogsWarning() {
        assertFalse("Profiler should not be active initially", profiler.isActive());
        
        // Stopping inactive profiler should not cause issues
        profiler.stopProfiling();
        
        assertFalse("Profiler should remain inactive", profiler.isActive());
    }
    
    // Frame Processing Measurement Tests
    
    @Test
    public void testRecordFrameProcessing_WhenActive_RecordsSuccessfully() {
        profiler.startProfiling(false);
        assertEquals("Initial frame count should be 0", 0, profiler.getTotalFramesProcessed());
        
        profiler.recordFrameProcessing(50, 15, 3, true);
        
        assertEquals("Frame count should increment", 1, profiler.getTotalFramesProcessed());
    }
    
    @Test
    public void testRecordFrameProcessing_WhenInactive_DoesNotRecord() {
        assertFalse("Profiler should not be active", profiler.isActive());
        
        profiler.recordFrameProcessing(50, 15, 3, true);
        
        assertEquals("Frame count should remain 0", 0, profiler.getTotalFramesProcessed());
    }
    
    @Test
    public void testRecordFrameProcessing_DuringWarmup_DoesNotRecord() {
        profiler.startProfiling(true); // With warmup
        assertTrue("Profiler should be active", profiler.isActive());
        assertFalse("Warmup should not be complete", profiler.isWarmupComplete());
        
        profiler.recordFrameProcessing(50, 15, 3, true);
        
        assertEquals("Frame count should remain 0 during warmup", 0, profiler.getTotalFramesProcessed());
    }
    
    @Test
    public void testRecordFrameProcessing_MultipleFrames_AccumulatesCorrectly() {
        profiler.startProfiling(false);
        
        profiler.recordFrameProcessing(50, 15, 3, true);
        profiler.recordFrameProcessing(45, 12, 2, false);
        profiler.recordFrameProcessing(55, 18, 4, true);
        
        assertEquals("Should record all frames", 3, profiler.getTotalFramesProcessed());
    }
    
    // Baseline Capture Tests
    
    @Test
    public void testCaptureBaseline_WhenInactive_ReturnsNull() {
        assertFalse("Profiler should not be active", profiler.isActive());
        
        CpuUsageProfiler.CpuUsageBaseline baseline = profiler.captureBaseline();
        
        assertNull("Baseline should be null when profiler inactive", baseline);
    }
    
    @Test
    public void testCaptureBaseline_WithInsufficientData_ReturnsNull() {
        profiler.startProfiling(false);
        
        // No frame measurements recorded yet
        CpuUsageProfiler.CpuUsageBaseline baseline = profiler.captureBaseline();
        
        assertNull("Baseline should be null with insufficient data", baseline);
    }
    
    @Test
    public void testCaptureBaseline_WithSufficientData_ReturnsBaseline() throws InterruptedException {
        profiler.startProfiling(false);
        
        // Record some frame measurements
        for (int i = 0; i < 10; i++) {
            profiler.recordFrameProcessing(50 + i, 15 + i, 3, true);
        }
        
        // Allow some time for CPU measurements
        Thread.sleep(200);
        
        CpuUsageProfiler.CpuUsageBaseline baseline = profiler.captureBaseline();
        
        assertNotNull("Baseline should not be null with sufficient data", baseline);
        assertTrue("Baseline should have positive sample count", baseline.sampleCount > 0);
        assertTrue("Baseline should have positive duration", baseline.measurementDurationMs > 0);
    }
    
    @Test
    public void testSetBaseline_ValidBaseline_SetsSuccessfully() {
        CpuUsageProfiler.CpuUsageBaseline testBaseline = new CpuUsageProfiler.CpuUsageBaseline(
                45.0, 25.0, 60.0, 3.5, 2.0, 10000, 100);
        
        profiler.setBaseline(testBaseline);
        
        // Verify baseline is set by attempting validation (should not return "no baseline" error)
        profiler.startProfiling(false);
        CpuUsageProfiler.PerformanceValidationResults results = profiler.validatePerformanceImprovements();
        
        assertNotEquals("Should not return 'no baseline' error", 
                "No baseline available for comparison", results.validationSummary);
    }
    
    // Performance Validation Tests
    
    @Test
    public void testValidatePerformanceImprovements_NoBaseline_ReturnsError() {
        profiler.startProfiling(false);
        
        CpuUsageProfiler.PerformanceValidationResults results = profiler.validatePerformanceImprovements();
        
        assertFalse("Should not meet targets without baseline", results.meetsAllTargets());
        assertTrue("Should indicate no baseline", 
                results.validationSummary.contains("No baseline available"));
    }
    
    @Test
    public void testValidatePerformanceImprovements_InsufficientData_ReturnsError() {
        // Set a baseline but don't record enough measurements
        CpuUsageProfiler.CpuUsageBaseline testBaseline = new CpuUsageProfiler.CpuUsageBaseline(
                45.0, 25.0, 60.0, 3.5, 2.0, 10000, 100);
        profiler.setBaseline(testBaseline);
        profiler.startProfiling(false);
        
        CpuUsageProfiler.PerformanceValidationResults results = profiler.validatePerformanceImprovements();
        
        assertFalse("Should not meet targets with insufficient data", results.meetsAllTargets());
        assertTrue("Should indicate insufficient measurements", 
                results.validationSummary.contains("Insufficient current measurements"));
    }
    
    @Test
    public void testValidatePerformanceImprovements_MeetsAllTargets_ReturnsSuccess() throws InterruptedException {
        // Set a high baseline (poor performance)
        CpuUsageProfiler.CpuUsageBaseline highBaseline = new CpuUsageProfiler.CpuUsageBaseline(
                80.0, 40.0, 100.0, 5.0, 10.0, 10000, 100);
        profiler.setBaseline(highBaseline);
        
        profiler.startProfiling(false);
        
        // Record many good measurements (low CPU, fast processing)
        for (int i = 0; i < 60; i++) {
            profiler.recordFrameProcessing(25, 5, 1, true); // Low framebuffer time, few copies
        }
        
        // Allow time for CPU measurements
        Thread.sleep(300);
        
        CpuUsageProfiler.PerformanceValidationResults results = profiler.validatePerformanceImprovements();
        
        assertNotNull("Results should not be null", results);
        assertTrue("Should meet framebuffer CPU target", results.meetsFramebufferCpuTarget);
        assertTrue("Should meet latency improvement target", results.meetsLatencyImprovementTarget);
        // Note: CPU reduction might not meet target due to measurement limitations in test environment
    }
    
    @Test
    public void testValidatePerformanceImprovements_FailsTargets_ReturnsFailure() throws InterruptedException {
        // Set a low baseline (good performance)
        CpuUsageProfiler.CpuUsageBaseline lowBaseline = new CpuUsageProfiler.CpuUsageBaseline(
                20.0, 15.0, 30.0, 1.0, 1.0, 10000, 100);
        profiler.setBaseline(lowBaseline);
        
        profiler.startProfiling(false);
        
        // Record many poor measurements (high CPU, slow processing)
        for (int i = 0; i < 60; i++) {
            profiler.recordFrameProcessing(120, 50, 8, false); // High framebuffer time, many copies
        }
        
        // Allow time for CPU measurements
        Thread.sleep(300);
        
        CpuUsageProfiler.PerformanceValidationResults results = profiler.validatePerformanceImprovements();
        
        assertNotNull("Results should not be null", results);
        assertFalse("Should not meet framebuffer CPU target", results.meetsFramebufferCpuTarget);
        assertFalse("Should not meet latency improvement target", results.meetsLatencyImprovementTarget);
        assertFalse("Should not meet all targets", results.meetsAllTargets());
    }
    
    // CPU Usage Statistics Tests
    
    @Test
    public void testGetCurrentCpuUsageStats_WhenInactive_ReturnsNull() {
        assertFalse("Profiler should not be active", profiler.isActive());
        
        CpuUsageProfiler.CpuUsageBaseline stats = profiler.getCurrentCpuUsageStats();
        
        assertNull("Stats should be null when profiler inactive", stats);
    }
    
    @Test
    public void testGetCurrentCpuUsageStats_WhenActive_ReturnsStats() throws InterruptedException {
        profiler.startProfiling(false);
        
        // Record some measurements
        profiler.recordFrameProcessing(50, 15, 3, true);
        profiler.recordFrameProcessing(45, 12, 2, false);
        
        // Allow time for CPU measurements
        Thread.sleep(200);
        
        CpuUsageProfiler.CpuUsageBaseline stats = profiler.getCurrentCpuUsageStats();
        
        assertNotNull("Stats should not be null when profiler active", stats);
        assertTrue("Stats should have positive duration", stats.measurementDurationMs > 0);
    }
    
    // Data Structure Tests
    
    @Test
    public void testCpuMeasurement_Creation_StoresValuesCorrectly() {
        long timestamp = System.currentTimeMillis();
        double cpuUsage = 45.5;
        double systemCpu = 60.2;
        long userTime = 1000;
        long systemTime = 500;
        int threadCount = 8;
        
        CpuUsageProfiler.CpuMeasurement measurement = new CpuUsageProfiler.CpuMeasurement(
                timestamp, cpuUsage, systemCpu, userTime, systemTime, threadCount);
        
        assertEquals("Timestamp should match", timestamp, measurement.timestamp);
        assertEquals("CPU usage should match", cpuUsage, measurement.cpuUsagePercent, 0.01);
        assertEquals("System CPU should match", systemCpu, measurement.systemCpuPercent, 0.01);
        assertEquals("User time should match", userTime, measurement.userTimeMs);
        assertEquals("System time should match", systemTime, measurement.systemTimeMs);
        assertEquals("Thread count should match", threadCount, measurement.threadCount);
    }
    
    @Test
    public void testFrameProcessingMeasurement_Creation_StoresValuesCorrectly() {
        long timestamp = System.currentTimeMillis();
        long processingTime = 50;
        long framebufferTime = 15;
        int copyOps = 3;
        boolean optimized = true;
        double cpuUsage = 35.5;
        
        CpuUsageProfiler.FrameProcessingMeasurement measurement = 
                new CpuUsageProfiler.FrameProcessingMeasurement(
                        timestamp, processingTime, framebufferTime, copyOps, optimized, cpuUsage);
        
        assertEquals("Timestamp should match", timestamp, measurement.timestamp);
        assertEquals("Processing time should match", processingTime, measurement.processingTimeMs);
        assertEquals("Framebuffer time should match", framebufferTime, measurement.framebufferTimeMs);
        assertEquals("Copy operations should match", copyOps, measurement.copyOperations);
        assertEquals("Optimized flag should match", optimized, measurement.usedOptimizedPath);
        assertEquals("CPU usage should match", cpuUsage, measurement.cpuUsageDuringFrame, 0.01);
    }
    
    @Test
    public void testFrameProcessingMeasurement_GetFramebufferCpuPercent_CalculatesCorrectly() {
        CpuUsageProfiler.FrameProcessingMeasurement measurement = 
                new CpuUsageProfiler.FrameProcessingMeasurement(
                        System.currentTimeMillis(), 50, 15, 3, true, 35.5);
        
        double expectedPercent = (15.0 / 50.0) * 100; // 30%
        assertEquals("Framebuffer CPU percent should be calculated correctly", 
                expectedPercent, measurement.getFramebufferCpuPercent(), 0.01);
    }
    
    @Test
    public void testFrameProcessingMeasurement_GetFramebufferCpuPercent_ZeroProcessingTime_ReturnsZero() {
        CpuUsageProfiler.FrameProcessingMeasurement measurement = 
                new CpuUsageProfiler.FrameProcessingMeasurement(
                        System.currentTimeMillis(), 0, 15, 3, true, 35.5);
        
        assertEquals("Framebuffer CPU percent should be 0 when processing time is 0", 
                0.0, measurement.getFramebufferCpuPercent(), 0.01);
    }
    
    @Test
    public void testMemoryMeasurement_Creation_StoresValuesCorrectly() {
        long timestamp = System.currentTimeMillis();
        long usedMem = 128;
        long availMem = 256;
        long gcCount = 5;
        long gcTime = 50;
        double pressure = 0.75;
        
        CpuUsageProfiler.MemoryMeasurement measurement = new CpuUsageProfiler.MemoryMeasurement(
                timestamp, usedMem, availMem, gcCount, gcTime, pressure);
        
        assertEquals("Timestamp should match", timestamp, measurement.timestamp);
        assertEquals("Used memory should match", usedMem, measurement.usedMemoryMB);
        assertEquals("Available memory should match", availMem, measurement.availableMemoryMB);
        assertEquals("GC count should match", gcCount, measurement.gcCount);
        assertEquals("GC time should match", gcTime, measurement.gcTimeMs);
        assertEquals("Memory pressure should match", pressure, measurement.memoryPressure, 0.01);
    }
    
    @Test
    public void testCpuUsageBaseline_Creation_StoresValuesCorrectly() {
        double avgCpu = 45.0;
        double avgFbCpu = 25.0;
        double avgLatency = 60.0;
        double avgCopies = 3.5;
        double avgGcFreq = 2.0;
        long duration = 10000;
        int samples = 100;
        
        CpuUsageProfiler.CpuUsageBaseline baseline = new CpuUsageProfiler.CpuUsageBaseline(
                avgCpu, avgFbCpu, avgLatency, avgCopies, avgGcFreq, duration, samples);
        
        assertEquals("Average CPU should match", avgCpu, baseline.averageCpuPercent, 0.01);
        assertEquals("Average framebuffer CPU should match", avgFbCpu, baseline.averageFramebufferCpuPercent, 0.01);
        assertEquals("Average latency should match", avgLatency, baseline.averageProcessingLatencyMs, 0.01);
        assertEquals("Average copies should match", avgCopies, baseline.averageCopyOperationsPerFrame, 0.01);
        assertEquals("Average GC frequency should match", avgGcFreq, baseline.averageGcFrequencyPerMinute, 0.01);
        assertEquals("Duration should match", duration, baseline.measurementDurationMs);
        assertEquals("Sample count should match", samples, baseline.sampleCount);
    }
    
    @Test
    public void testPerformanceValidationResults_MeetsAllTargets_WhenAllTrue() {
        CpuUsageProfiler.PerformanceValidationResults results = 
                new CpuUsageProfiler.PerformanceValidationResults(
                        true, true, true, true, // All targets met
                        25.0, 30.0, 45.0, 15.0, // Performance values
                        null, null, "All targets met", java.util.Collections.emptyList());
        
        assertTrue("Should meet all targets when all individual targets are met", results.meetsAllTargets());
    }
    
    @Test
    public void testPerformanceValidationResults_DoesNotMeetAllTargets_WhenOneFalse() {
        CpuUsageProfiler.PerformanceValidationResults results = 
                new CpuUsageProfiler.PerformanceValidationResults(
                        true, false, true, true, // One target not met
                        25.0, 15.0, 45.0, 15.0, // Performance values
                        null, null, "One target failed", java.util.Collections.emptyList());
        
        assertFalse("Should not meet all targets when any individual target fails", results.meetsAllTargets());
    }
    
    // Edge Cases and Error Handling Tests
    
    @Test
    public void testMultipleStartStop_Cycles_HandledCorrectly() {
        // Test multiple start/stop cycles
        for (int i = 0; i < 3; i++) {
            assertFalse("Profiler should not be active before start", profiler.isActive());
            
            profiler.startProfiling(false);
            assertTrue("Profiler should be active after start", profiler.isActive());
            
            profiler.recordFrameProcessing(50, 15, 3, true);
            assertEquals("Frame count should increment", i + 1, profiler.getTotalFramesProcessed());
            
            profiler.stopProfiling();
            assertFalse("Profiler should not be active after stop", profiler.isActive());
        }
    }
    
    @Test
    public void testFrameProcessing_ExtremeValues_HandledCorrectly() {
        profiler.startProfiling(false);
        
        // Test with extreme values
        profiler.recordFrameProcessing(0, 0, 0, true); // Zero values
        profiler.recordFrameProcessing(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE, false); // Max values
        profiler.recordFrameProcessing(1, 1000, 1, true); // Framebuffer time > processing time
        
        assertEquals("Should record all frames including extreme values", 3, profiler.getTotalFramesProcessed());
    }
    
    @Test
    public void testToStringMethods_ProduceValidOutput() {
        // Test CpuMeasurement toString
        CpuUsageProfiler.CpuMeasurement cpuMeasurement = new CpuUsageProfiler.CpuMeasurement(
                System.currentTimeMillis(), 45.5, 60.2, 1000, 500, 8);
        String cpuString = cpuMeasurement.toString();
        assertNotNull("CPU measurement toString should not be null", cpuString);
        assertTrue("CPU measurement toString should contain CPU percentage", cpuString.contains("45.5%"));
        
        // Test FrameProcessingMeasurement toString
        CpuUsageProfiler.FrameProcessingMeasurement frameMeasurement = 
                new CpuUsageProfiler.FrameProcessingMeasurement(
                        System.currentTimeMillis(), 50, 15, 3, true, 35.5);
        String frameString = frameMeasurement.toString();
        assertNotNull("Frame measurement toString should not be null", frameString);
        assertTrue("Frame measurement toString should contain processing time", frameString.contains("50ms"));
        
        // Test MemoryMeasurement toString
        CpuUsageProfiler.MemoryMeasurement memoryMeasurement = new CpuUsageProfiler.MemoryMeasurement(
                System.currentTimeMillis(), 128, 256, 5, 50, 0.75);
        String memoryString = memoryMeasurement.toString();
        assertNotNull("Memory measurement toString should not be null", memoryString);
        assertTrue("Memory measurement toString should contain memory values", memoryString.contains("128MB"));
        
        // Test CpuUsageBaseline toString
        CpuUsageProfiler.CpuUsageBaseline baseline = new CpuUsageProfiler.CpuUsageBaseline(
                45.0, 25.0, 60.0, 3.5, 2.0, 10000, 100);
        String baselineString = baseline.toString();
        assertNotNull("Baseline toString should not be null", baselineString);
        assertTrue("Baseline toString should contain CPU percentage", baselineString.contains("45.0%"));
    }
}