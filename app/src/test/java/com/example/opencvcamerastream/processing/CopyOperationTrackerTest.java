package com.example.opencvcamerastream.processing;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * Unit tests for CopyOperationTracker
 * 
 * Tests Requirements 13.1, 13.2:
 * - Validate copy operation tracking
 * - Verify performance metrics calculation
 * - Test optimization status reporting
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class CopyOperationTrackerTest {
    
    private CopyOperationTracker tracker;
    
    @Before
    public void setUp() {
        tracker = new CopyOperationTracker();
    }
    
    @Test
    public void testInitialState() {
        CopyOperationTracker.CopyOperationMetrics metrics = tracker.getMetrics();
        
        assertNotNull("Metrics should not be null", metrics);
        assertEquals("Initial frame count should be 0", 0, metrics.getTotalFrames());
        assertEquals("Initial copy count should be 0", 0, metrics.getTotalCopies());
        assertEquals("Initial time should be 0", 0, metrics.getTotalTimeMs());
        assertEquals("Initial average copies per frame should be 0", 0.0, metrics.getAverageCopiesPerFrame(), 0.01);
    }
    
    @Test
    public void testRecordCopyOperation() {
        // Record some copy operations
        tracker.recordCopyOperation(CopyOperationTracker.CopyType.IMAGE_TO_MAT, 5, 1024);
        tracker.recordCopyOperation(CopyOperationTracker.CopyType.MAT_TO_BITMAP, 3, 2048);
        tracker.recordCopyOperation(CopyOperationTracker.CopyType.MAT_CLONE, 2, 512);
        
        CopyOperationTracker.CopyOperationMetrics metrics = tracker.getMetrics();
        
        assertEquals("Total copies should be 3", 3, metrics.getTotalCopies());
        assertEquals("Total time should be 10ms", 10, metrics.getTotalTimeMs());
        assertEquals("Average time per copy should be 3.33ms", 3.33, metrics.getAverageTimePerCopy(), 0.01);
        
        // Check individual copy type counts
        assertEquals("IMAGE_TO_MAT count should be 1", 1, metrics.getCopyCount(CopyOperationTracker.CopyType.IMAGE_TO_MAT));
        assertEquals("MAT_TO_BITMAP count should be 1", 1, metrics.getCopyCount(CopyOperationTracker.CopyType.MAT_TO_BITMAP));
        assertEquals("MAT_CLONE count should be 1", 1, metrics.getCopyCount(CopyOperationTracker.CopyType.MAT_CLONE));
        
        // Check individual copy type times
        assertEquals("IMAGE_TO_MAT time should be 5ms", 5, metrics.getCopyTime(CopyOperationTracker.CopyType.IMAGE_TO_MAT));
        assertEquals("MAT_TO_BITMAP time should be 3ms", 3, metrics.getCopyTime(CopyOperationTracker.CopyType.MAT_TO_BITMAP));
        assertEquals("MAT_CLONE time should be 2ms", 2, metrics.getCopyTime(CopyOperationTracker.CopyType.MAT_CLONE));
    }
    
    @Test
    public void testRecordCopyOperationWithAutoTiming() {
        long startTime = System.currentTimeMillis();
        
        // Simulate some processing time
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // Ignore
        }
        
        tracker.recordCopyOperation(CopyOperationTracker.CopyType.IMAGE_TO_MAT, startTime, 1024);
        
        CopyOperationTracker.CopyOperationMetrics metrics = tracker.getMetrics();
        
        assertEquals("Total copies should be 1", 1, metrics.getTotalCopies());
        assertTrue("Total time should be greater than 0", metrics.getTotalTimeMs() > 0);
        assertEquals("IMAGE_TO_MAT count should be 1", 1, metrics.getCopyCount(CopyOperationTracker.CopyType.IMAGE_TO_MAT));
    }
    
    @Test
    public void testFrameTracking() {
        // Start multiple frames and record operations
        for (int i = 0; i < 5; i++) {
            tracker.startFrame();
            tracker.recordCopyOperation(CopyOperationTracker.CopyType.IMAGE_TO_MAT, 5, 1024);
            tracker.recordCopyOperation(CopyOperationTracker.CopyType.MAT_TO_BITMAP, 3, 2048);
        }
        
        CopyOperationTracker.CopyOperationMetrics metrics = tracker.getMetrics();
        
        assertEquals("Frame count should be 5", 5, metrics.getTotalFrames());
        assertEquals("Total copies should be 10", 10, metrics.getTotalCopies());
        assertEquals("Average copies per frame should be 2.0", 2.0, metrics.getAverageCopiesPerFrame(), 0.01);
        assertEquals("Total time should be 40ms", 40, metrics.getTotalTimeMs());
        assertEquals("Average time per frame should be 8ms", 8.0, metrics.getAverageTimePerFrame(), 0.01);
    }
    
    @Test
    public void testOptimizationTargetsMet() {
        // Simulate optimal performance (2 copies per frame)
        for (int i = 0; i < 10; i++) {
            tracker.startFrame();
            tracker.recordCopyOperation(CopyOperationTracker.CopyType.IMAGE_TO_MAT, 5, 1024);
            tracker.recordCopyOperation(CopyOperationTracker.CopyType.MAT_TO_BITMAP, 3, 2048);
        }
        
        CopyOperationTracker.CopyOperationMetrics metrics = tracker.getMetrics();
        
        assertTrue("Optimization targets should be met", metrics.meetsOptimizationTargets());
        assertEquals("Average copies per frame should be 2.0", 2.0, metrics.getAverageCopiesPerFrame(), 0.01);
        
        double cpuReduction = metrics.getEstimatedCpuReduction();
        assertTrue("CPU reduction should be significant", cpuReduction > 50.0);
        
        String status = metrics.getOptimizationStatus();
        assertTrue("Status should indicate OPTIMAL", status.contains("OPTIMAL"));
    }
    
    @Test
    public void testOptimizationTargetsNotMet() {
        // Simulate poor performance (5 copies per frame)
        for (int i = 0; i < 10; i++) {
            tracker.startFrame();
            tracker.recordCopyOperation(CopyOperationTracker.CopyType.IMAGE_TO_MAT, 5, 1024);
            tracker.recordCopyOperation(CopyOperationTracker.CopyType.MAT_TO_BUFFER, 3, 1024);
            tracker.recordCopyOperation(CopyOperationTracker.CopyType.BUFFER_TO_MAT, 3, 1024);
            tracker.recordCopyOperation(CopyOperationTracker.CopyType.MAT_CLONE, 2, 1024);
            tracker.recordCopyOperation(CopyOperationTracker.CopyType.MAT_TO_BITMAP, 3, 2048);
        }
        
        CopyOperationTracker.CopyOperationMetrics metrics = tracker.getMetrics();
        
        assertFalse("Optimization targets should not be met", metrics.meetsOptimizationTargets());
        assertEquals("Average copies per frame should be 5.0", 5.0, metrics.getAverageCopiesPerFrame(), 0.01);
        
        double cpuReduction = metrics.getEstimatedCpuReduction();
        assertTrue("CPU reduction should be minimal or zero", cpuReduction >= 0);
        
        String status = metrics.getOptimizationStatus();
        assertTrue("Status should indicate NEEDS_OPTIMIZATION", status.contains("NEEDS_OPTIMIZATION"));
    }
    
    @Test
    public void testCpuReductionCalculation() {
        // Test different scenarios
        
        // Scenario 1: Optimal (2 copies per frame)
        CopyOperationTracker.CopyOperationMetrics optimal = 
                new CopyOperationTracker.CopyOperationMetrics(10, 20, 100, 
                        java.util.Map.of(), java.util.Map.of());
        
        double optimalReduction = optimal.getEstimatedCpuReduction();
        assertTrue("Optimal scenario should have high CPU reduction", optimalReduction > 60.0);
        
        // Scenario 2: Baseline (5.5 copies per frame)
        CopyOperationTracker.CopyOperationMetrics baseline = 
                new CopyOperationTracker.CopyOperationMetrics(10, 55, 200, 
                        java.util.Map.of(), java.util.Map.of());
        
        double baselineReduction = baseline.getEstimatedCpuReduction();
        assertEquals("Baseline scenario should have no reduction", 0.0, baselineReduction, 0.01);
        
        // Scenario 3: Worse than baseline (6 copies per frame)
        CopyOperationTracker.CopyOperationMetrics worse = 
                new CopyOperationTracker.CopyOperationMetrics(10, 60, 250, 
                        java.util.Map.of(), java.util.Map.of());
        
        double worseReduction = worse.getEstimatedCpuReduction();
        assertEquals("Worse than baseline should have no reduction", 0.0, worseReduction, 0.01);
    }
    
    @Test
    public void testOptimizationStatusCategories() {
        // Test OPTIMAL status (2.0 copies per frame)
        CopyOperationTracker.CopyOperationMetrics optimal = 
                new CopyOperationTracker.CopyOperationMetrics(10, 20, 100, 
                        java.util.Map.of(), java.util.Map.of());
        assertTrue("Should be OPTIMAL", optimal.getOptimizationStatus().contains("OPTIMAL"));
        
        // Test GOOD status (2.5 copies per frame)
        CopyOperationTracker.CopyOperationMetrics good = 
                new CopyOperationTracker.CopyOperationMetrics(10, 25, 120, 
                        java.util.Map.of(), java.util.Map.of());
        assertTrue("Should be GOOD", good.getOptimizationStatus().contains("GOOD"));
        
        // Test MODERATE status (3.5 copies per frame)
        CopyOperationTracker.CopyOperationMetrics moderate = 
                new CopyOperationTracker.CopyOperationMetrics(10, 35, 150, 
                        java.util.Map.of(), java.util.Map.of());
        assertTrue("Should be MODERATE", moderate.getOptimizationStatus().contains("MODERATE"));
        
        // Test NEEDS_OPTIMIZATION status (5.0 copies per frame)
        CopyOperationTracker.CopyOperationMetrics needsOpt = 
                new CopyOperationTracker.CopyOperationMetrics(10, 50, 200, 
                        java.util.Map.of(), java.util.Map.of());
        assertTrue("Should be NEEDS_OPTIMIZATION", needsOpt.getOptimizationStatus().contains("NEEDS_OPTIMIZATION"));
    }
    
    @Test
    public void testResetMetrics() {
        // Record some operations
        tracker.startFrame();
        tracker.recordCopyOperation(CopyOperationTracker.CopyType.IMAGE_TO_MAT, 5, 1024);
        tracker.recordCopyOperation(CopyOperationTracker.CopyType.MAT_TO_BITMAP, 3, 2048);
        
        // Verify data exists
        CopyOperationTracker.CopyOperationMetrics beforeReset = tracker.getMetrics();
        assertTrue("Should have data before reset", beforeReset.getTotalFrames() > 0);
        assertTrue("Should have copies before reset", beforeReset.getTotalCopies() > 0);
        
        // Reset metrics
        tracker.resetMetrics();
        
        // Verify data is cleared
        CopyOperationTracker.CopyOperationMetrics afterReset = tracker.getMetrics();
        assertEquals("Frame count should be 0 after reset", 0, afterReset.getTotalFrames());
        assertEquals("Copy count should be 0 after reset", 0, afterReset.getTotalCopies());
        assertEquals("Time should be 0 after reset", 0, afterReset.getTotalTimeMs());
        
        // Verify individual counters are reset
        for (CopyOperationTracker.CopyType type : CopyOperationTracker.CopyType.values()) {
            assertEquals("Copy count for " + type + " should be 0", 0, afterReset.getCopyCount(type));
            assertEquals("Copy time for " + type + " should be 0", 0, afterReset.getCopyTime(type));
        }
    }
    
    @Test
    public void testCopyTypeDescriptions() {
        // Verify all copy types have descriptions
        for (CopyOperationTracker.CopyType type : CopyOperationTracker.CopyType.values()) {
            assertNotNull("Copy type " + type + " should have description", type.getDescription());
            assertFalse("Copy type " + type + " description should not be empty", 
                    type.getDescription().isEmpty());
        }
        
        // Test specific descriptions
        assertEquals("IMAGE_TO_MAT description", "Image→Mat", 
                CopyOperationTracker.CopyType.IMAGE_TO_MAT.getDescription());
        assertEquals("MAT_TO_BITMAP description", "Mat→Bitmap", 
                CopyOperationTracker.CopyType.MAT_TO_BITMAP.getDescription());
        assertEquals("DEFENSIVE_CLONE description", "Defensive clone", 
                CopyOperationTracker.CopyType.DEFENSIVE_CLONE.getDescription());
    }
    
    @Test
    public void testMetricsToString() {
        // Record some operations
        for (int i = 0; i < 5; i++) {
            tracker.startFrame();
            tracker.recordCopyOperation(CopyOperationTracker.CopyType.IMAGE_TO_MAT, 5, 1024);
            tracker.recordCopyOperation(CopyOperationTracker.CopyType.MAT_TO_BITMAP, 3, 2048);
        }
        
        CopyOperationTracker.CopyOperationMetrics metrics = tracker.getMetrics();
        String metricsString = metrics.toString();
        
        assertNotNull("Metrics string should not be null", metricsString);
        assertTrue("Should contain frame count", metricsString.contains("frames=5"));
        assertTrue("Should contain copy count", metricsString.contains("copies=10"));
        assertTrue("Should contain average copies", metricsString.contains("2.0/frame"));
        assertTrue("Should contain time", metricsString.contains("40ms"));
        assertTrue("Should contain status", metricsString.contains("status="));
    }
}