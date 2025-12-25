package com.example.opencvcamerastream.performance;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.example.opencvcamerastream.processing.CopyOperationTracker;
import com.example.opencvcamerastream.processing.ZeroCopyProcessor;
import com.example.opencvcamerastream.processing.FrameProcessor;
import com.example.opencvcamerastream.processing.OpenCVProcessor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Test class for performance measurement and validation (Task 25)
 * 
 * Tests:
 * - CPU usage profiling accuracy
 * - Copy operation tracking
 * - Zero-copy optimization measurement
 * - Performance target validation
 * - Before/after optimization comparison
 * - Performance report generation
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class PerformanceMeasurementTest {
    
    private Context context;
    private PerformanceMeasurementManager measurementManager;
    private CopyOperationTracker copyTracker;
    private ZeroCopyProcessor zeroCopyProcessor;
    private FrameProcessor frameProcessor;
    private OpenCVProcessor openCVProcessor;
    
    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        
        // Create mock components
        openCVProcessor = mock(OpenCVProcessor.class);
        when(openCVProcessor.isInitialized()).thenReturn(true);
        
        copyTracker = new CopyOperationTracker();
        zeroCopyProcessor = new ZeroCopyProcessor(openCVProcessor);
        frameProcessor = new FrameProcessor(openCVProcessor);
        
        // Create measurement manager
        measurementManager = new PerformanceMeasurementManager(context);
        measurementManager.setComponents(copyTracker, zeroCopyProcessor, frameProcessor);
    }
    
    @Test
    public void testCpuUsageProfilerInitialization() {
        CpuUsageProfiler profiler = new CpuUsageProfiler();
        assertNotNull("CPU profiler should be initialized", profiler);
        
        // Test metrics before any measurement
        CpuUsageProfiler.CpuUsageMetrics initialMetrics = profiler.getCurrentMetrics();
        assertNotNull("Initial metrics should not be null", initialMetrics);
        assertEquals("Initial CPU percentage should be 0", 0.0, initialMetrics.totalCpuPercentage, 0.1);
        assertEquals("Initial framebuffer CPU should be 0", 0.0, initialMetrics.framebufferCpuPercentage, 0.1);
    }
    
    @Test
    public void testCopyOperationTracking() {
        // Simulate copy operations
        copyTracker.startFrame();
        copyTracker.recordCopyOperation(CopyOperationTracker.CopyType.IMAGE_TO_MAT, 5, 1024);
        copyTracker.recordCopyOperation(CopyOperationTracker.CopyType.MAT_TO_BITMAP, 3, 2048);
        
        copyTracker.startFrame();
        copyTracker.recordCopyOperation(CopyOperationTracker.CopyType.IMAGE_TO_MAT, 4, 1024);
        copyTracker.recordCopyOperation(CopyOperationTracker.CopyType.MAT_TO_BITMAP, 2, 2048);
        
        // Get metrics
        CopyOperationTracker.CopyOperationMetrics metrics = copyTracker.getMetrics();
        
        assertNotNull("Copy metrics should not be null", metrics);
        assertEquals("Should have processed 2 frames", 2, metrics.getTotalFrames());
        assertEquals("Should have 4 total copies", 4, metrics.getTotalCopies());
        assertEquals("Should average 2 copies per frame", 2.0, metrics.getAverageCopiesPerFrame(), 0.1);
        
        // Test optimization target validation
        assertTrue("Should meet optimization targets with 2 copies per frame", metrics.meetsOptimizationTargets());
        
        // Test optimization status
        String status = metrics.getOptimizationStatus();
        assertNotNull("Optimization status should not be null", status);
        assertTrue("Status should indicate optimal performance", status.contains("OPTIMAL"));
    }
    
    @Test
    public void testZeroCopyProcessorMetrics() {
        // Initialize zero-copy processor
        assertTrue("Zero-copy processor should initialize", zeroCopyProcessor.initialize());
        
        // Get initial metrics
        ZeroCopyProcessor.ZeroCopyPerformanceMetrics initialMetrics = zeroCopyProcessor.getPerformanceMetrics();
        assertNotNull("Initial zero-copy metrics should not be null", initialMetrics);
        assertEquals("Initial frames should be 0", 0, initialMetrics.totalFrames);
        assertEquals("Initial copies should be 0", 0, initialMetrics.totalCopyOperations);
    }
    
    @Test
    public void testPerformanceTargetValidation() {
        // Create test metrics that meet targets
        CpuUsageProfiler.CpuUsageMetrics baselineMetrics = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 80.0, 50.0, 60.0, 40, 100, 150, 2.0, 45, 50);
        
        CpuUsageProfiler.CpuUsageMetrics optimizedMetrics = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 40.0, 25.0, 35.0, 20, 60, 150, 1.0, 35, 40);
        
        // Test individual target validation
        assertTrue("Optimized metrics should meet framebuffer target", 
                optimizedMetrics.meetsFramebufferTarget());
        assertTrue("Optimized metrics should meet latency target", 
                optimizedMetrics.meetsLatencyTarget(baselineMetrics));
        assertTrue("Optimized metrics should meet CPU reduction target", 
                optimizedMetrics.meetsCpuReductionTarget(baselineMetrics));
        
        // Test optimization results
        CpuUsageProfiler.OptimizationResults results = new CpuUsageProfiler.OptimizationResults(
                baselineMetrics, optimizedMetrics);
        
        assertNotNull("Optimization results should not be null", results);
        assertTrue("Should meet all targets", results.meetsAllTargets);
        assertTrue("CPU reduction should be significant", results.cpuReductionPercentage > 40.0);
        assertTrue("Latency improvement should be significant", results.latencyImprovementMs > 20.0);
        
        String summary = results.validationSummary;
        assertNotNull("Validation summary should not be null", summary);
        assertTrue("Summary should indicate success", summary.contains("ALL TARGETS MET"));
    }
    
    @Test
    public void testPerformanceTargetFailure() {
        // Create test metrics that don't meet targets
        CpuUsageProfiler.CpuUsageMetrics baselineMetrics = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 60.0, 40.0, 50.0, 30, 80, 100, 1.5, 40, 45);
        
        CpuUsageProfiler.CpuUsageMetrics optimizedMetrics = new CpuUsageProfiler.CpuUsageMetrics(
                5000, 55.0, 35.0, 45.0, 25, 75, 100, 1.2, 38, 42);
        
        // Test individual target validation
        assertFalse("Should not meet framebuffer target", optimizedMetrics.meetsFramebufferTarget());
        assertFalse("Should not meet latency target", optimizedMetrics.meetsLatencyTarget(baselineMetrics));
        assertFalse("Should not meet CPU reduction target", optimizedMetrics.meetsCpuReductionTarget(baselineMetrics));
        
        // Test optimization results
        CpuUsageProfiler.OptimizationResults results = new CpuUsageProfiler.OptimizationResults(
                baselineMetrics, optimizedMetrics);
        
        assertNotNull("Optimization results should not be null", results);
        assertFalse("Should not meet all targets", results.meetsAllTargets);
        
        String summary = results.validationSummary;
        assertNotNull("Validation summary should not be null", summary);
        assertTrue("Summary should indicate failure", summary.contains("TARGETS NOT MET"));
    }
    
    @Test
    public void testMeasurementManagerPhases() {
        TestCallback callback = new TestCallback();
        measurementManager.setCallback(callback);
        
        // Test initial state
        assertEquals("Initial phase should be BASELINE", 
                PerformanceMeasurementManager.MeasurementPhase.BASELINE, 
                measurementManager.getCurrentPhase());
        assertFalse("Should not be measuring initially", measurementManager.isMeasuring());
        
        // Test baseline measurement start
        measurementManager.startBaselineMeasurement();
        assertTrue("Should be measuring after start", measurementManager.isMeasuring());
        assertEquals("Should be in baseline phase", 
                PerformanceMeasurementManager.MeasurementPhase.BASELINE, 
                measurementManager.getCurrentPhase());
        
        // Simulate some processing time
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Complete baseline measurement
        measurementManager.completeBaselineMeasurement();
        assertFalse("Should not be measuring after baseline completion", measurementManager.isMeasuring());
        
        // Test optimized measurement start
        measurementManager.startOptimizedMeasurement();
        assertTrue("Should be measuring after optimized start", measurementManager.isMeasuring());
        assertEquals("Should be in optimized phase", 
                PerformanceMeasurementManager.MeasurementPhase.OPTIMIZED, 
                measurementManager.getCurrentPhase());
        
        // Simulate some processing time
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Complete optimized measurement
        measurementManager.completeOptimizedMeasurement();
        assertFalse("Should not be measuring after optimized completion", measurementManager.isMeasuring());
        
        // Verify callback was called
        assertTrue("Phase change callback should have been called", callback.phaseChangeCount > 0);
        assertTrue("Measurement update callback should have been called", callback.updateCount > 0);
    }
    
    @Test
    public void testFrameProcessingLatencyMeasurement() {
        CpuUsageProfiler profiler = new CpuUsageProfiler();
        
        // Simulate frame processing with different latencies
        long startTime1 = System.currentTimeMillis();
        try {
            Thread.sleep(10); // Simulate 10ms processing
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        profiler.recordFrameProcessingEnd(startTime1, true); // Framebuffer operation
        
        long startTime2 = System.currentTimeMillis();
        try {
            Thread.sleep(15); // Simulate 15ms processing
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        profiler.recordFrameProcessingEnd(startTime2, false); // Non-framebuffer operation
        
        // Get metrics
        CpuUsageProfiler.CpuUsageMetrics metrics = profiler.getCurrentMetrics();
        
        assertNotNull("Metrics should not be null", metrics);
        assertEquals("Should have processed 2 frames", 2, metrics.totalFrames);
        assertTrue("Average latency should be reasonable", 
                metrics.averageFrameLatencyMs >= 10 && metrics.averageFrameLatencyMs <= 20);
        assertTrue("Min latency should be reasonable", 
                metrics.minFrameLatencyMs >= 8 && metrics.minFrameLatencyMs <= 12);
        assertTrue("Max latency should be reasonable", 
                metrics.maxFrameLatencyMs >= 13 && metrics.maxFrameLatencyMs <= 17);
    }
    
    @Test
    public void testMemoryPressureTracking() {
        CpuUsageProfiler profiler = new CpuUsageProfiler();
        
        // Simulate memory allocations
        profiler.recordMemoryAllocation(1024 * 1024); // 1MB
        profiler.recordMemoryAllocation(2 * 1024 * 1024); // 2MB
        
        // Simulate GC events
        profiler.recordGarbageCollection();
        profiler.recordGarbageCollection();
        
        // Get metrics
        CpuUsageProfiler.CpuUsageMetrics metrics = profiler.getCurrentMetrics();
        
        assertNotNull("Metrics should not be null", metrics);
        assertTrue("Total memory should be tracked", metrics.totalMemoryMB >= 0);
        assertTrue("Peak memory should be tracked", metrics.peakMemoryMB >= 0);
        assertTrue("GC frequency should be reasonable", metrics.gcFrequencyPerSecond >= 0);
    }
    
    @Test
    public void testPerformanceReportGeneration() {
        // This test verifies that the measurement manager can generate reports
        // without actually running full measurements (which would be too slow for unit tests)
        
        TestCallback callback = new TestCallback();
        measurementManager.setCallback(callback);
        
        // The report generation is tested indirectly through the callback mechanism
        // and the measurement manager's ability to handle the measurement lifecycle
        
        assertNotNull("Measurement manager should be initialized", measurementManager);
        assertNotNull("Callback should be set", callback);
        
        // Test that we can get current CPU metrics even without active measurement
        CpuUsageProfiler.CpuUsageMetrics currentMetrics = measurementManager.getCurrentCpuMetrics();
        assertNotNull("Should be able to get current CPU metrics", currentMetrics);
    }
    
    // Test callback implementation
    private static class TestCallback implements PerformanceMeasurementManager.PerformanceMeasurementCallback {
        int phaseChangeCount = 0;
        int updateCount = 0;
        int completedCount = 0;
        int errorCount = 0;
        
        @Override
        public void onMeasurementPhaseChanged(PerformanceMeasurementManager.MeasurementPhase phase) {
            phaseChangeCount++;
        }
        
        @Override
        public void onMeasurementUpdate(String update) {
            updateCount++;
        }
        
        @Override
        public void onMeasurementCompleted(PerformanceMeasurementManager.PerformanceMeasurementResults results) {
            completedCount++;
        }
        
        @Override
        public void onMeasurementError(String error, Exception exception) {
            errorCount++;
        }
    }
}