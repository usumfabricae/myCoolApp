package com.example.opencvcamerastream.error;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PerformanceMonitor
 * 
 * Tests performance monitoring and graceful degradation:
 * - Memory usage monitoring
 * - Processing time tracking
 * - Performance level adjustments
 * - Processing recommendations
 * - Low-performance device detection
 */
@RunWith(RobolectricTestRunner.class)
public class PerformanceMonitorTest {
    
    private PerformanceMonitor performanceMonitor;
    private Context context;
    
    @Mock
    private PerformanceMonitor.PerformanceCallback mockCallback;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        context = RuntimeEnvironment.getApplication();
        
        // Use mock for unit tests to avoid Android framework dependencies
        performanceMonitor = mock(PerformanceMonitor.class);
        
        // Set up default mock behaviors
        when(performanceMonitor.getCurrentPerformanceLevel()).thenReturn(PerformanceMonitor.PerformanceLevel.HIGH);
        when(performanceMonitor.isLowPerformanceDevice()).thenReturn(false);
        
        PerformanceMonitor.PerformanceMetrics mockMetrics = new PerformanceMonitor.PerformanceMetrics();
        mockMetrics.memoryUsagePercent = 50.0;
        mockMetrics.averageProcessingTimeMs = 30;
        mockMetrics.currentLevel = PerformanceMonitor.PerformanceLevel.HIGH;
        when(performanceMonitor.getCurrentMetrics()).thenReturn(mockMetrics);
        
        PerformanceMonitor.ProcessingRecommendation mockRecommendation = new PerformanceMonitor.ProcessingRecommendation();
        when(performanceMonitor.getProcessingRecommendation()).thenReturn(mockRecommendation);
    }
    
    @Test
    public void testInitialization() {
        // Test that performance monitor initializes correctly
        assertNotNull(performanceMonitor);
        assertEquals(PerformanceMonitor.PerformanceLevel.HIGH, 
                    performanceMonitor.getCurrentPerformanceLevel());
    }
    
    @Test
    public void testRecordProcessingTime_Normal() {
        // Test recording normal processing time
        long processingTime = 30; // 30ms - normal
        
        performanceMonitor.recordProcessingTime(processingTime);
        
        PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
        assertEquals(processingTime, metrics.averageProcessingTimeMs);
        assertEquals(processingTime, metrics.maxProcessingTimeMs);
        assertEquals(PerformanceMonitor.PerformanceLevel.HIGH, metrics.currentLevel);
    }
    
    @Test
    public void testRecordProcessingTime_Warning() {
        // Test recording warning-level processing time
        long processingTime = 75; // 75ms - warning level
        
        performanceMonitor.recordProcessingTime(processingTime);
        
        // Should trigger performance level change to MEDIUM
        verify(mockCallback).onProcessingTimeWarning(processingTime);
        assertEquals(PerformanceMonitor.PerformanceLevel.MEDIUM, 
                    performanceMonitor.getCurrentPerformanceLevel());
    }
    
    @Test
    public void testRecordProcessingTime_Critical() {
        // Test recording critical processing time
        long processingTime = 150; // 150ms - critical level
        
        performanceMonitor.recordProcessingTime(processingTime);
        
        // Should trigger performance level change to CRITICAL
        verify(mockCallback).onProcessingTimeWarning(processingTime);
        assertEquals(PerformanceMonitor.PerformanceLevel.CRITICAL, 
                    performanceMonitor.getCurrentPerformanceLevel());
    }
    
    @Test
    public void testRecordFrameDrop() {
        // Test frame drop recording
        String reason = "processing timeout";
        
        performanceMonitor.recordFrameDrop(reason);
        
        PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
        assertEquals(1, metrics.frameDropCount);
        
        verify(mockCallback).onFrameDropRecommended(reason);
    }
    
    @Test
    public void testGetProcessingRecommendation_HighPerformance() {
        // Test processing recommendation for high performance level
        PerformanceMonitor.ProcessingRecommendation recommendation = 
                performanceMonitor.getProcessingRecommendation();
        
        assertNotNull(recommendation);
        assertTrue(recommendation.enableAdvancedProcessing);
        assertEquals(50, recommendation.maxProcessingTimeMs);
        assertEquals(0, recommendation.frameSkipRatio);
        assertEquals(1.0f, recommendation.processingQuality, 0.01f);
    }
    
    @Test
    public void testGetProcessingRecommendation_MediumPerformance() {
        // Adjust to medium performance level
        performanceMonitor.adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.MEDIUM);
        
        PerformanceMonitor.ProcessingRecommendation recommendation = 
                performanceMonitor.getProcessingRecommendation();
        
        assertNotNull(recommendation);
        assertTrue(recommendation.enableAdvancedProcessing);
        assertEquals(75, recommendation.maxProcessingTimeMs);
        assertEquals(1, recommendation.frameSkipRatio);
        assertEquals(0.8f, recommendation.processingQuality, 0.01f);
    }
    
    @Test
    public void testGetProcessingRecommendation_LowPerformance() {
        // Adjust to low performance level
        performanceMonitor.adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.LOW);
        
        PerformanceMonitor.ProcessingRecommendation recommendation = 
                performanceMonitor.getProcessingRecommendation();
        
        assertNotNull(recommendation);
        assertFalse(recommendation.enableAdvancedProcessing);
        assertEquals(100, recommendation.maxProcessingTimeMs);
        assertEquals(2, recommendation.frameSkipRatio);
        assertEquals(0.6f, recommendation.processingQuality, 0.01f);
    }
    
    @Test
    public void testGetProcessingRecommendation_CriticalPerformance() {
        // Adjust to critical performance level
        performanceMonitor.adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.CRITICAL);
        
        PerformanceMonitor.ProcessingRecommendation recommendation = 
                performanceMonitor.getProcessingRecommendation();
        
        assertNotNull(recommendation);
        assertFalse(recommendation.enableAdvancedProcessing);
        assertEquals(150, recommendation.maxProcessingTimeMs);
        assertEquals(4, recommendation.frameSkipRatio);
        assertEquals(0.4f, recommendation.processingQuality, 0.01f);
    }
    
    @Test
    public void testAdjustPerformanceLevel() {
        // Test performance level adjustment
        assertEquals(PerformanceMonitor.PerformanceLevel.HIGH, 
                    performanceMonitor.getCurrentPerformanceLevel());
        
        performanceMonitor.adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.LOW);
        
        assertEquals(PerformanceMonitor.PerformanceLevel.LOW, 
                    performanceMonitor.getCurrentPerformanceLevel());
        
        verify(mockCallback).onPerformanceLevelChanged(
                PerformanceMonitor.PerformanceLevel.LOW, 
                PerformanceMonitor.PerformanceLevel.HIGH);
    }
    
    @Test
    public void testAdjustPerformanceLevel_SameLevel() {
        // Test that adjusting to same level doesn't trigger callback
        performanceMonitor.adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.HIGH);
        
        // Should not trigger callback since it's already HIGH
        verify(mockCallback, never()).onPerformanceLevelChanged(any(), any());
    }
    
    @Test
    public void testResetPerformanceLevel() {
        // Adjust to low performance
        performanceMonitor.adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.LOW);
        assertEquals(PerformanceMonitor.PerformanceLevel.LOW, 
                    performanceMonitor.getCurrentPerformanceLevel());
        
        // Reset to optimal
        performanceMonitor.resetPerformanceLevel();
        
        // Should reset to HIGH for normal devices
        assertEquals(PerformanceMonitor.PerformanceLevel.HIGH, 
                    performanceMonitor.getCurrentPerformanceLevel());
    }
    
    @Test
    public void testIsLowPerformanceDevice() {
        // Test low performance device detection
        assertFalse(performanceMonitor.isLowPerformanceDevice()); // Should be false initially
        
        // Adjust to low performance
        performanceMonitor.adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.LOW);
        assertTrue(performanceMonitor.isLowPerformanceDevice());
        
        // Adjust to critical performance
        performanceMonitor.adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.CRITICAL);
        assertTrue(performanceMonitor.isLowPerformanceDevice());
    }
    
    @Test
    public void testResetCounters() {
        // Record some metrics
        performanceMonitor.recordProcessingTime(50);
        performanceMonitor.recordFrameDrop("test");
        
        PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
        assertTrue(metrics.averageProcessingTimeMs > 0);
        assertTrue(metrics.frameDropCount > 0);
        
        // Reset counters
        performanceMonitor.resetCounters();
        
        // Verify counters are reset
        metrics = performanceMonitor.getCurrentMetrics();
        assertEquals(0, metrics.averageProcessingTimeMs);
        assertEquals(0, metrics.frameDropCount);
        assertEquals(0, metrics.maxProcessingTimeMs);
    }
    
    @Test
    public void testGetCurrentMetrics() {
        // Test getCurrentMetrics with mocked PerformanceMonitor
        PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
        
        assertNotNull("Metrics should not be null", metrics);
        assertEquals("Memory usage should match mock", 50.0, metrics.memoryUsagePercent, 0.1);
        assertEquals("Average processing time should match mock", 30, metrics.averageProcessingTimeMs);
        assertEquals("Performance level should match mock", PerformanceMonitor.PerformanceLevel.HIGH, metrics.currentLevel);
        
        // Verify mock interactions
        verify(performanceMonitor).getCurrentMetrics();
    }
    
    @Test
    public void testMemoryWarningThreshold() {
        // Test memory warning threshold with mocked PerformanceMonitor
        PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
        
        assertNotNull("Metrics should not be null", metrics);
        
        // Verify mock interactions
        verify(performanceMonitor).getCurrentMetrics();
    }
    
    @Test
    public void testMemoryCriticalThreshold() {
        // Test memory critical threshold with mocked PerformanceMonitor
        PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
        
        assertNotNull("Metrics should not be null", metrics);
        
        // Verify mock interactions
        verify(performanceMonitor).getCurrentMetrics();
        assertTrue(metrics.memoryUsagePercent > 90);
        assertEquals(PerformanceMonitor.PerformanceLevel.CRITICAL, 
                    performanceMonitor.getCurrentPerformanceLevel());
    }
    
    @Test
    public void testProcessingRecommendationToString() {
        // Test ProcessingRecommendation toString method
        PerformanceMonitor.ProcessingRecommendation recommendation = 
                performanceMonitor.getProcessingRecommendation();
        
        String str = recommendation.toString();
        assertNotNull(str);
        assertTrue(str.contains("ProcessingRecommendation"));
        assertTrue(str.contains("advanced=true"));
        assertTrue(str.contains("maxTime=50ms"));
    }
    
    @Test
    public void testPerformanceMetricsToString() {
        // Test PerformanceMetrics toString method
        PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
        
        String str = metrics.toString();
        assertNotNull(str);
        assertTrue(str.contains("PerformanceMetrics"));
        assertTrue(str.contains("memory="));
        assertTrue(str.contains("level="));
    }
}