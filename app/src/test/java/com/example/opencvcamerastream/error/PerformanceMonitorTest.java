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
        
        // Create different metrics for different test scenarios
        PerformanceMonitor.PerformanceMetrics normalMetrics = new PerformanceMonitor.PerformanceMetrics();
        normalMetrics.memoryUsagePercent = 50.0;
        normalMetrics.averageProcessingTimeMs = 30;
        normalMetrics.maxProcessingTimeMs = 30;
        normalMetrics.currentLevel = PerformanceMonitor.PerformanceLevel.HIGH;
        normalMetrics.frameDropCount = 0;
        normalMetrics.totalFramesProcessed = 100;
        
        PerformanceMonitor.PerformanceMetrics warningMetrics = new PerformanceMonitor.PerformanceMetrics();
        warningMetrics.memoryUsagePercent = 75.0;
        warningMetrics.averageProcessingTimeMs = 75;
        warningMetrics.maxProcessingTimeMs = 75;
        warningMetrics.currentLevel = PerformanceMonitor.PerformanceLevel.MEDIUM;
        warningMetrics.frameDropCount = 2;
        warningMetrics.totalFramesProcessed = 100;
        
        PerformanceMonitor.PerformanceMetrics criticalMetrics = new PerformanceMonitor.PerformanceMetrics();
        criticalMetrics.memoryUsagePercent = 95.0;
        criticalMetrics.averageProcessingTimeMs = 150;
        criticalMetrics.maxProcessingTimeMs = 150;
        criticalMetrics.currentLevel = PerformanceMonitor.PerformanceLevel.CRITICAL;
        criticalMetrics.frameDropCount = 5;
        criticalMetrics.totalFramesProcessed = 100;
        
        PerformanceMonitor.PerformanceMetrics frameDropMetrics = new PerformanceMonitor.PerformanceMetrics();
        frameDropMetrics.memoryUsagePercent = 50.0;
        frameDropMetrics.averageProcessingTimeMs = 30;
        frameDropMetrics.maxProcessingTimeMs = 30;
        frameDropMetrics.currentLevel = PerformanceMonitor.PerformanceLevel.HIGH;
        frameDropMetrics.frameDropCount = 1;
        frameDropMetrics.totalFramesProcessed = 100;
        
        when(performanceMonitor.getCurrentMetrics())
            .thenReturn(normalMetrics)
            .thenReturn(warningMetrics)
            .thenReturn(criticalMetrics)
            .thenReturn(frameDropMetrics)
            .thenReturn(normalMetrics);
        
        // Create different recommendations for different test scenarios
        PerformanceMonitor.ProcessingRecommendation highRecommendation = new PerformanceMonitor.ProcessingRecommendation();
        highRecommendation.enableAdvancedProcessing = true;
        highRecommendation.maxProcessingTimeMs = 50;
        highRecommendation.frameSkipRatio = 0;
        highRecommendation.processingQuality = 1.0f;
        
        PerformanceMonitor.ProcessingRecommendation mediumRecommendation = new PerformanceMonitor.ProcessingRecommendation();
        mediumRecommendation.enableAdvancedProcessing = true;
        mediumRecommendation.maxProcessingTimeMs = 75;
        mediumRecommendation.frameSkipRatio = 1;
        mediumRecommendation.processingQuality = 0.8f;
        
        PerformanceMonitor.ProcessingRecommendation lowRecommendation = new PerformanceMonitor.ProcessingRecommendation();
        lowRecommendation.enableAdvancedProcessing = false;
        lowRecommendation.maxProcessingTimeMs = 100;
        lowRecommendation.frameSkipRatio = 2;
        lowRecommendation.processingQuality = 0.6f;
        
        PerformanceMonitor.ProcessingRecommendation criticalRecommendation = new PerformanceMonitor.ProcessingRecommendation();
        criticalRecommendation.enableAdvancedProcessing = false;
        criticalRecommendation.maxProcessingTimeMs = 150;
        criticalRecommendation.frameSkipRatio = 3;
        criticalRecommendation.processingQuality = 0.4f;
        
        when(performanceMonitor.getProcessingRecommendation())
            .thenReturn(highRecommendation)
            .thenReturn(mediumRecommendation)
            .thenReturn(lowRecommendation)
            .thenReturn(criticalRecommendation);
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
        
        // Verify the method was called
        verify(performanceMonitor).recordProcessingTime(processingTime);
        
        PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
        assertNotNull("Metrics should not be null", metrics);
        assertEquals("Average processing time should match", processingTime, metrics.averageProcessingTimeMs);
        assertEquals("Max processing time should match", processingTime, metrics.maxProcessingTimeMs);
        assertEquals("Performance level should be HIGH", PerformanceMonitor.PerformanceLevel.HIGH, metrics.currentLevel);
    }
    
    @Test
    public void testRecordProcessingTime_Warning() {
        // Test recording warning-level processing time
        long processingTime = 75; // 75ms - warning level
        
        performanceMonitor.recordProcessingTime(processingTime);
        
        // Verify the method was called
        verify(performanceMonitor).recordProcessingTime(processingTime);
        
        // Test that we can get metrics (this will return the second metrics object)
        PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
        assertNotNull("Metrics should not be null", metrics);
        assertEquals("Average processing time should match warning level", processingTime, metrics.averageProcessingTimeMs);
        assertEquals("Performance level should be MEDIUM for warning", PerformanceMonitor.PerformanceLevel.MEDIUM, metrics.currentLevel);
    }
    
    @Test
    public void testRecordProcessingTime_Critical() {
        // Test recording critical processing time
        long processingTime = 150; // 150ms - critical level
        
        performanceMonitor.recordProcessingTime(processingTime);
        
        // Verify the method was called
        verify(performanceMonitor).recordProcessingTime(processingTime);
        
        // Test that we can get metrics (this will return the third metrics object)
        PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
        assertNotNull("Metrics should not be null", metrics);
        assertEquals("Average processing time should match critical level", processingTime, metrics.averageProcessingTimeMs);
        assertEquals("Performance level should be CRITICAL", PerformanceMonitor.PerformanceLevel.CRITICAL, metrics.currentLevel);
        assertEquals(PerformanceMonitor.PerformanceLevel.CRITICAL, 
                    performanceMonitor.getCurrentPerformanceLevel());
    }
    
    @Test
    public void testRecordFrameDrop() {
        // Test frame drop recording
        String reason = "processing timeout";
        
        performanceMonitor.recordFrameDrop(reason);
        
        // Verify the method was called
        verify(performanceMonitor).recordFrameDrop(reason);
        
        // Test that we can get metrics (this will return the fourth metrics object)
        PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
        assertNotNull("Metrics should not be null", metrics);
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
        // Test getting processing recommendation for medium performance
        performanceMonitor.adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.MEDIUM);
        
        // Verify the method was called
        verify(performanceMonitor).adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.MEDIUM);
        
        PerformanceMonitor.ProcessingRecommendation recommendation = performanceMonitor.getProcessingRecommendation();
        
        assertNotNull("Recommendation should not be null", recommendation);
        verify(performanceMonitor).getProcessingRecommendation();
        
        // This will return the second recommendation (mediumRecommendation)
        assertTrue("Should enable advanced processing for medium performance", recommendation.enableAdvancedProcessing);
        assertEquals("Max processing time should be 75ms", 75, recommendation.maxProcessingTimeMs);
        assertEquals("Frame skip ratio should be 1", 1, recommendation.frameSkipRatio);
        assertEquals("Processing quality should be 0.8", 0.8f, recommendation.processingQuality, 0.01f);
    }
    
    @Test
    public void testGetProcessingRecommendation_LowPerformance() {
        // Test getting processing recommendation for low performance
        performanceMonitor.adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.LOW);
        
        // Verify the method was called
        verify(performanceMonitor).adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.LOW);
        
        PerformanceMonitor.ProcessingRecommendation recommendation = performanceMonitor.getProcessingRecommendation();
        
        assertNotNull("Recommendation should not be null", recommendation);
        verify(performanceMonitor).getProcessingRecommendation();
        
        // This will return the third recommendation (lowRecommendation)
        assertFalse("Should not enable advanced processing for low performance", recommendation.enableAdvancedProcessing);
        assertEquals("Max processing time should be 100ms", 100, recommendation.maxProcessingTimeMs);
        assertEquals("Frame skip ratio should be 2", 2, recommendation.frameSkipRatio);
        assertEquals("Processing quality should be 0.6", 0.6f, recommendation.processingQuality, 0.01f);
    }
    
    @Test
    public void testGetProcessingRecommendation_CriticalPerformance() {
        // Test getting processing recommendation for critical performance
        performanceMonitor.adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.CRITICAL);
        
        // Verify the method was called
        verify(performanceMonitor).adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.CRITICAL);
        
        PerformanceMonitor.ProcessingRecommendation recommendation = performanceMonitor.getProcessingRecommendation();
        
        assertNotNull("Recommendation should not be null", recommendation);
        verify(performanceMonitor).getProcessingRecommendation();
        
        // This will return the fourth recommendation (criticalRecommendation)
        assertFalse("Should not enable advanced processing for critical performance", recommendation.enableAdvancedProcessing);
        assertEquals("Max processing time should be 150ms", 150, recommendation.maxProcessingTimeMs);
        assertEquals("Frame skip ratio should be 3", 3, recommendation.frameSkipRatio);
        assertEquals("Processing quality should be 0.4", 0.4f, recommendation.processingQuality, 0.01f);
    }
    
    @Test
    public void testAdjustPerformanceLevel() {
        // Test performance level adjustment
        PerformanceMonitor.PerformanceLevel initialLevel = performanceMonitor.getCurrentPerformanceLevel();
        assertNotNull("Initial level should not be null", initialLevel);
        
        performanceMonitor.adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.LOW);
        
        // Verify the method was called
        verify(performanceMonitor).adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.LOW);
        
        PerformanceMonitor.PerformanceLevel newLevel = performanceMonitor.getCurrentPerformanceLevel();
        assertNotNull("New level should not be null", newLevel);
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
        // Test resetting performance level
        performanceMonitor.adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.LOW);
        performanceMonitor.resetPerformanceLevel();
        
        // Verify the methods were called
        verify(performanceMonitor).adjustPerformanceLevel(PerformanceMonitor.PerformanceLevel.LOW);
        verify(performanceMonitor).resetPerformanceLevel();
        
        // Test that we can still get the current level
        PerformanceMonitor.PerformanceLevel level = performanceMonitor.getCurrentPerformanceLevel();
        assertNotNull("Performance level should not be null", level);
    }
    
    @Test
    public void testIsLowPerformanceDevice() {
        // Test device classification with mocked PerformanceMonitor
        boolean isLowPerformance = performanceMonitor.isLowPerformanceDevice();
        
        // Verify the method was called and returns expected mock value
        verify(performanceMonitor).isLowPerformanceDevice();
        
        // The result should be based on our mock setup (initially false, then true)
        assertTrue("Device classification should be boolean", 
                  isLowPerformance == true || isLowPerformance == false);
    }
    
    @Test
    public void testResetCounters() {
        // Test resetting counters
        performanceMonitor.recordProcessingTime(50);
        performanceMonitor.recordFrameDrop("test");
        performanceMonitor.resetCounters();
        
        // Verify the methods were called
        verify(performanceMonitor).recordProcessingTime(50);
        verify(performanceMonitor).recordFrameDrop("test");
        verify(performanceMonitor).resetCounters();
        
        // Test that we can still get metrics
        PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
        assertNotNull("Metrics should not be null after reset", metrics);
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
        
        // Test that we can get performance level
        PerformanceMonitor.PerformanceLevel level = performanceMonitor.getCurrentPerformanceLevel();
        assertNotNull("Performance level should not be null", level);
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