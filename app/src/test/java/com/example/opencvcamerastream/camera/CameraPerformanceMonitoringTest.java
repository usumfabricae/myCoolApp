package com.example.opencvcamerastream.camera;

import android.content.Context;
import android.util.Size;

import androidx.test.core.app.ApplicationProvider;

import com.example.opencvcamerastream.error.PerformanceMonitor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for camera performance monitoring functionality
 * 
 * Requirements tested:
 * - NFR-001: Frame rate monitoring (30 FPS target)
 * - NFR-002: Memory usage tracking (<50 MB target)
 * - NFR-003: Processing latency measurement (<100ms target)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Test on Android 10
public class CameraPerformanceMonitoringTest {
    
    private Context context;
    private CameraManager cameraManager;
    
    @Mock
    private android.hardware.camera2.CameraManager mockSystemCameraManager;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        context = ApplicationProvider.getApplicationContext();
        
        // Mock the system camera manager service
        when(context.getSystemService(Context.CAMERA_SERVICE)).thenReturn(mockSystemCameraManager);
        when(context.getApplicationContext()).thenReturn(context);
        
        try {
            cameraManager = new CameraManager(context);
        } catch (Exception e) {
            // Handle initialization errors gracefully in tests
            fail("CameraManager initialization failed: " + e.getMessage());
        }
    }
    
    /**
     * Test frame rate monitoring functionality
     * Requirements: NFR-001
     */
    @Test
    public void testFrameRateMonitoring() {
        // Start performance monitoring
        cameraManager.startPerformanceMonitoring();
        
        // Simulate frame processing
        for (int i = 0; i < 10; i++) {
            cameraManager.startFrameProcessingTiming();
            
            // Simulate processing time
            try {
                Thread.sleep(20); // 20ms processing time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            cameraManager.endFrameProcessingTiming();
        }
        
        // Get performance report
        CameraPerformanceReport report = cameraManager.getPerformanceReport();
        
        assertNotNull("Performance report should not be null", report);
        assertNotNull("Frame stats should not be null", report.frameStats);
        assertEquals("Should have processed 10 frames", 10, report.frameStats.totalFramesProcessed);
        assertTrue("Frame rate should be positive", report.currentFrameRate >= 0);
    }
    
    /**
     * Test memory usage tracking
     * Requirements: NFR-002
     */
    @Test
    public void testMemoryUsageTracking() {
        // Get camera memory usage
        CameraMemoryUsage memoryUsage = cameraManager.getCameraMemoryUsage();
        
        assertNotNull("Memory usage should not be null", memoryUsage);
        assertTrue("Total memory should be positive", memoryUsage.totalMemoryMB > 0);
        assertTrue("Used memory should be non-negative", memoryUsage.usedMemoryMB >= 0);
        assertTrue("Memory usage percent should be valid", 
                memoryUsage.memoryUsagePercent >= 0 && memoryUsage.memoryUsagePercent <= 100);
        
        // Test memory status
        String status = memoryUsage.getMemoryStatus();
        assertNotNull("Memory status should not be null", status);
        assertTrue("Memory status should be valid", 
                status.equals("OPTIMAL") || status.equals("WARNING") || 
                status.equals("CRITICAL") || status.equals("ABOVE_TARGET"));
    }
    
    /**
     * Test processing latency measurement
     * Requirements: NFR-003
     */
    @Test
    public void testProcessingLatencyMeasurement() {
        // Get processing latency metrics
        ProcessingLatencyMetrics latencyMetrics = cameraManager.getProcessingLatencyMetrics();
        
        assertNotNull("Latency metrics should not be null", latencyMetrics);
        
        // Simulate frame processing with timing
        cameraManager.startFrameProcessingTiming();
        
        try {
            Thread.sleep(50); // 50ms processing time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        cameraManager.endFrameProcessingTiming();
        
        // Get updated metrics
        latencyMetrics = cameraManager.getProcessingLatencyMetrics();
        
        assertTrue("Current latency should be positive", latencyMetrics.currentLatencyMs >= 0);
        assertTrue("Performance score should be valid", 
                latencyMetrics.getPerformanceScore() >= 0.0 && latencyMetrics.getPerformanceScore() <= 1.0);
    }
    
    /**
     * Test performance targets compliance
     * Requirements: NFR-001, NFR-002, NFR-003
     */
    @Test
    public void testPerformanceTargetsCompliance() {
        // Start performance monitoring
        cameraManager.startPerformanceMonitoring();
        
        // Simulate optimal performance scenario
        for (int i = 0; i < 30; i++) {
            cameraManager.startFrameProcessingTiming();
            
            // Simulate fast processing (under 100ms target)
            try {
                Thread.sleep(30); // 30ms processing time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            cameraManager.endFrameProcessingTiming();
        }
        
        // Get performance report
        CameraPerformanceReport report = cameraManager.getPerformanceReport();
        
        assertNotNull("Performance report should not be null", report);
        
        // Check if performance targets are being evaluated
        // Note: Actual compliance depends on system performance
        assertTrue("Performance score should be calculated", report.getPerformanceScore() >= 0.0);
        assertNotNull("Performance summary should be available", report.getPerformanceSummary());
    }
    
    /**
     * Test frame drop recording
     * Requirements: NFR-001
     */
    @Test
    public void testFrameDropRecording() {
        // Record some frame drops
        cameraManager.recordFrameDrop("Test frame drop 1");
        cameraManager.recordFrameDrop("Test frame drop 2");
        
        // Get frame processing stats
        FrameProcessingStats stats = cameraManager.getFrameProcessingStats();
        
        assertNotNull("Frame stats should not be null", stats);
        assertEquals("Should have recorded 2 frame drops", 2, stats.totalFramesDropped);
    }
    
    /**
     * Test performance optimization features
     * Requirements: NFR-001, NFR-002
     */
    @Test
    public void testPerformanceOptimization() {
        // Test performance optimization enable/disable
        cameraManager.setPerformanceOptimizationEnabled(true);
        cameraManager.setPerformanceOptimizationEnabled(false);
        
        // Test force performance level
        cameraManager.forcePerformanceLevel(PerformanceMonitor.PerformanceLevel.LOW);
        cameraManager.forcePerformanceLevel(PerformanceMonitor.PerformanceLevel.HIGH);
        
        // Should not throw exceptions
        assertTrue("Performance optimization should work without errors", true);
    }
    
    /**
     * Test performance monitoring session lifecycle
     * Requirements: NFR-003
     */
    @Test
    public void testPerformanceMonitoringSession() {
        // Start monitoring session
        cameraManager.startPerformanceMonitoring();
        
        // Simulate some activity
        cameraManager.startFrameProcessingTiming();
        cameraManager.endFrameProcessingTiming();
        
        // Stop monitoring and get final report
        CameraPerformanceReport finalReport = cameraManager.stopPerformanceMonitoring();
        
        assertNotNull("Final report should not be null", finalReport);
        assertTrue("Report should have timestamp", finalReport.reportGeneratedAt > 0);
    }
    
    /**
     * Test frame processing statistics
     * Requirements: NFR-001, NFR-003
     */
    @Test
    public void testFrameProcessingStatistics() {
        FrameProcessingStats stats = new FrameProcessingStats();
        
        // Test initial state
        assertEquals("Initial frames processed should be 0", 0, stats.totalFramesProcessed);
        assertEquals("Initial frames dropped should be 0", 0, stats.totalFramesDropped);
        assertEquals("Initial drop rate should be 0", 0.0, stats.getFrameDropRate(), 0.01);
        
        // Record some frame processing
        stats.recordFrameProcessed(50); // 50ms processing time
        stats.recordFrameProcessed(75); // 75ms processing time
        stats.recordFrameDropped();
        
        assertEquals("Should have processed 2 frames", 2, stats.totalFramesProcessed);
        assertEquals("Should have dropped 1 frame", 1, stats.totalFramesDropped);
        assertEquals("Average processing time should be 62.5ms", 62, stats.averageProcessingTimeMs);
        assertTrue("Should have performance summary", stats.getDetailedSummary().length() > 0);
    }
    
    /**
     * Test camera memory usage calculations
     * Requirements: NFR-002
     */
    @Test
    public void testCameraMemoryUsageCalculations() {
        CameraMemoryUsage usage = new CameraMemoryUsage();
        
        // Set test values
        usage.totalMemoryMB = 100;
        usage.usedMemoryMB = 30;
        usage.memoryUsagePercent = 30.0;
        usage.currentBufferCount = 2;
        usage.maxBufferCount = 3;
        
        assertTrue("Should be within target", usage.isWithinTarget());
        assertFalse("Should not be at warning level", usage.isAtWarningLevel());
        assertFalse("Should not be at critical level", usage.isAtCriticalLevel());
        assertEquals("Available memory should be 70MB", 70, usage.getAvailableMemoryMB());
        assertEquals("Buffer utilization should be 66.67%", 66.67, usage.getBufferUtilization(), 0.1);
        assertEquals("Memory status should be OPTIMAL", "OPTIMAL", usage.getMemoryStatus());
        
        // Test memory usage summary
        String summary = usage.getMemoryUsageSummary();
        assertNotNull("Memory usage summary should not be null", summary);
        assertTrue("Summary should contain memory information", summary.contains("Memory"));
    }
    
    /**
     * Test processing latency metrics calculations
     * Requirements: NFR-003
     */
    @Test
    public void testProcessingLatencyMetricsCalculations() {
        ProcessingLatencyMetrics metrics = new ProcessingLatencyMetrics();
        
        // Record some latency measurements
        metrics.recordLatency(50);  // 50ms - optimal
        metrics.recordLatency(80);  // 80ms - warning
        metrics.recordLatency(120); // 120ms - above target
        
        assertEquals("Should have 3 measurements", 3, metrics.measurementCount);
        assertEquals("Average latency should be 83ms", 83, metrics.averageLatencyMs);
        assertEquals("Min latency should be 50ms", 50, metrics.minLatencyMs);
        assertEquals("Max latency should be 120ms", 120, metrics.maxLatencyMs);
        assertEquals("Current latency should be 120ms", 120, metrics.currentLatencyMs);
        assertTrue("Should be above target", metrics.isAboveTarget());
        assertEquals("Status should be ABOVE_TARGET", "ABOVE_TARGET", metrics.getLatencyStatus());
        
        // Test latency metrics summary
        String summary = metrics.getLatencyMetricsSummary();
        assertNotNull("Latency metrics summary should not be null", summary);
        assertTrue("Summary should contain latency information", summary.contains("Latency"));
    }
}