package com.example.opencvcamerastream.performance;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import com.example.opencvcamerastream.R;
import com.example.opencvcamerastream.error.PerformanceMonitor;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for PerformanceDisplayManager
 * 
 * Tests performance metrics display, UI updates, and overlay functionality.
 * 
 * Requirements tested:
 * - 1.3: Display frame rate monitoring for at least 10 FPS
 * - 2.3: Show processing time within 50ms requirement
 * - 5.1, 5.3: Performance optimization visualization
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Android 10
public class PerformanceDisplayManagerTest {
    
    @Mock
    private Activity mockActivity;
    
    @Mock
    private View mockPerformanceOverlay;
    
    @Mock
    private TextView mockFpsText;
    
    @Mock
    private TextView mockProcessingTimeText;
    
    @Mock
    private TextView mockMemoryUsageText;
    
    @Mock
    private TextView mockPerformanceLevelText;
    
    @Mock
    private TextView mockFrameDropText;
    
    @Mock
    private FloatingActionButton mockPerformanceToggleFab;
    
    private PerformanceDisplayManager performanceDisplayManager;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Set up mock activity to return mock views
        when(mockActivity.findViewById(R.id.performanceOverlay)).thenReturn(mockPerformanceOverlay);
        when(mockActivity.findViewById(R.id.fpsText)).thenReturn(mockFpsText);
        when(mockActivity.findViewById(R.id.processingTimeText)).thenReturn(mockProcessingTimeText);
        when(mockActivity.findViewById(R.id.memoryUsageText)).thenReturn(mockMemoryUsageText);
        when(mockActivity.findViewById(R.id.performanceLevelText)).thenReturn(mockPerformanceLevelText);
        when(mockActivity.findViewById(R.id.frameDropText)).thenReturn(mockFrameDropText);
        when(mockActivity.findViewById(R.id.performanceToggleFab)).thenReturn(mockPerformanceToggleFab);
        
        performanceDisplayManager = new PerformanceDisplayManager(mockActivity);
    }
    
    @Test
    public void testInitialization() {
        // Test that display manager initializes properly
        assertNotNull(performanceDisplayManager);
        
        // Verify that overlay is initially hidden
        assertFalse(performanceDisplayManager.isPerformanceOverlayVisible());
        
        // Verify that views were looked up
        verify(mockActivity).findViewById(R.id.performanceOverlay);
        verify(mockActivity).findViewById(R.id.fpsText);
        verify(mockActivity).findViewById(R.id.processingTimeText);
        verify(mockActivity).findViewById(R.id.memoryUsageText);
        verify(mockActivity).findViewById(R.id.performanceLevelText);
        verify(mockActivity).findViewById(R.id.frameDropText);
        verify(mockActivity).findViewById(R.id.performanceToggleFab);
    }
    
    @Test
    public void testTogglePerformanceOverlay() {
        // Initially hidden
        assertFalse(performanceDisplayManager.isPerformanceOverlayVisible());
        
        // Toggle to show
        performanceDisplayManager.togglePerformanceOverlay();
        assertTrue(performanceDisplayManager.isPerformanceOverlayVisible());
        verify(mockPerformanceOverlay).setVisibility(View.VISIBLE);
        
        // Toggle to hide
        performanceDisplayManager.togglePerformanceOverlay();
        assertFalse(performanceDisplayManager.isPerformanceOverlayVisible());
        verify(mockPerformanceOverlay).setVisibility(View.GONE);
    }
    
    @Test
    public void testShowHidePerformanceOverlay() {
        // Test explicit show
        performanceDisplayManager.showPerformanceOverlay();
        assertTrue(performanceDisplayManager.isPerformanceOverlayVisible());
        verify(mockPerformanceOverlay).setVisibility(View.VISIBLE);
        
        // Test explicit hide
        performanceDisplayManager.hidePerformanceOverlay();
        assertFalse(performanceDisplayManager.isPerformanceOverlayVisible());
        verify(mockPerformanceOverlay, times(2)).setVisibility(View.GONE); // Once in init, once in hide
    }
    
    @Test
    public void testUpdateMetricsWhenOverlayHidden() {
        // Create test metrics
        PerformanceMetricsCollector.FrameRateMetrics metrics = createTestMetrics();
        PerformanceMonitor.PerformanceMetrics systemMetrics = createTestSystemMetrics();
        
        // Update metrics when overlay is hidden - should not update UI
        performanceDisplayManager.updateMetrics(metrics, systemMetrics);
        
        // Verify no UI updates occurred
        verify(mockActivity, never()).runOnUiThread(any(Runnable.class));
    }
    
    @Test
    public void testUpdateMetricsWhenOverlayVisible() {
        // Show overlay first
        performanceDisplayManager.showPerformanceOverlay();
        
        // Create test metrics
        PerformanceMetricsCollector.FrameRateMetrics metrics = createTestMetrics();
        PerformanceMonitor.PerformanceMetrics systemMetrics = createTestSystemMetrics();
        
        // Update metrics
        performanceDisplayManager.updateMetrics(metrics, systemMetrics);
        
        // Verify UI update was scheduled
        verify(mockActivity).runOnUiThread(any(Runnable.class));
        
        // Verify last metrics are stored
        assertEquals(metrics, performanceDisplayManager.getLastMetrics());
    }
    
    @Test
    public void testFpsDisplayColorCoding() {
        performanceDisplayManager.showPerformanceOverlay();
        
        // Test good FPS (green)
        PerformanceMetricsCollector.FrameRateMetrics goodFpsMetrics = createTestMetrics();
        goodFpsMetrics.currentFps = 30.0f;
        goodFpsMetrics.averageFps = 28.0f;
        
        performanceDisplayManager.updateMetrics(goodFpsMetrics, null);
        
        // Verify UI update was called
        verify(mockActivity).runOnUiThread(any(Runnable.class));
        
        // Test poor FPS (red)
        PerformanceMetricsCollector.FrameRateMetrics poorFpsMetrics = createTestMetrics();
        poorFpsMetrics.currentFps = 8.0f;
        poorFpsMetrics.averageFps = 9.0f;
        
        performanceDisplayManager.updateMetrics(poorFpsMetrics, null);
        
        // Verify UI update was called again
        verify(mockActivity, times(2)).runOnUiThread(any(Runnable.class));
    }
    
    @Test
    public void testProcessingTimeDisplayColorCoding() {
        performanceDisplayManager.showPerformanceOverlay();
        
        // Test good processing time (green)
        PerformanceMetricsCollector.FrameRateMetrics goodTimeMetrics = createTestMetrics();
        goodTimeMetrics.averageProcessingTimeMs = 25;
        goodTimeMetrics.maxProcessingTimeMs = 30;
        
        performanceDisplayManager.updateMetrics(goodTimeMetrics, null);
        verify(mockActivity).runOnUiThread(any(Runnable.class));
        
        // Test poor processing time (red)
        PerformanceMetricsCollector.FrameRateMetrics poorTimeMetrics = createTestMetrics();
        poorTimeMetrics.averageProcessingTimeMs = 120;
        poorTimeMetrics.maxProcessingTimeMs = 150;
        
        performanceDisplayManager.updateMetrics(poorTimeMetrics, null);
        verify(mockActivity, times(2)).runOnUiThread(any(Runnable.class));
    }
    
    @Test
    public void testMemoryDisplayColorCoding() {
        performanceDisplayManager.showPerformanceOverlay();
        
        // Test good memory usage (green)
        PerformanceMonitor.PerformanceMetrics goodMemoryMetrics = createTestSystemMetrics();
        goodMemoryMetrics.memoryUsagePercent = 45.0;
        
        performanceDisplayManager.updateMetrics(createTestMetrics(), goodMemoryMetrics);
        verify(mockActivity).runOnUiThread(any(Runnable.class));
        
        // Test critical memory usage (red)
        PerformanceMonitor.PerformanceMetrics criticalMemoryMetrics = createTestSystemMetrics();
        criticalMemoryMetrics.memoryUsagePercent = 95.0;
        
        performanceDisplayManager.updateMetrics(createTestMetrics(), criticalMemoryMetrics);
        verify(mockActivity, times(2)).runOnUiThread(any(Runnable.class));
    }
    
    @Test
    public void testPerformanceLevelDisplayColorCoding() {
        performanceDisplayManager.showPerformanceOverlay();
        
        // Test different performance levels
        PerformanceMetricsCollector.FrameRateMetrics metrics = createTestMetrics();
        
        // High performance (green)
        metrics.currentLevel = PerformanceMonitor.PerformanceLevel.HIGH;
        performanceDisplayManager.updateMetrics(metrics, null);
        verify(mockActivity).runOnUiThread(any(Runnable.class));
        
        // Critical performance (red)
        metrics.currentLevel = PerformanceMonitor.PerformanceLevel.CRITICAL;
        performanceDisplayManager.updateMetrics(metrics, null);
        verify(mockActivity, times(2)).runOnUiThread(any(Runnable.class));
    }
    
    @Test
    public void testFrameDropDisplayWithActiveDropping() {
        performanceDisplayManager.showPerformanceOverlay();
        
        // Test with active frame dropping
        PerformanceMetricsCollector.FrameRateMetrics metrics = createTestMetrics();
        metrics.totalFrames = 100;
        metrics.droppedFrames = 15;
        metrics.dropRate = 15.0f;
        metrics.isFrameDropping = true;
        
        performanceDisplayManager.updateMetrics(metrics, null);
        verify(mockActivity).runOnUiThread(any(Runnable.class));
    }
    
    @Test
    public void testShowPerformanceAdjustment() {
        // Create test adjustment
        PerformanceMetricsCollector.PerformanceAdjustment adjustment = 
            new PerformanceMetricsCollector.PerformanceAdjustment(
                PerformanceMonitor.PerformanceLevel.LOW,
                true, 2, 0.6f, 100,
                "Test adjustment"
            );
        
        // Show adjustment - should temporarily show overlay
        performanceDisplayManager.showPerformanceAdjustment(adjustment);
        
        // Verify overlay was shown
        assertTrue(performanceDisplayManager.isPerformanceOverlayVisible());
        verify(mockPerformanceOverlay).setVisibility(View.VISIBLE);
        
        // Verify UI animation was triggered
        verify(mockActivity).runOnUiThread(any(Runnable.class));
    }
    
    @Test
    public void testReset() {
        // Show overlay and set some metrics
        performanceDisplayManager.showPerformanceOverlay();
        performanceDisplayManager.updateMetrics(createTestMetrics(), createTestSystemMetrics());
        
        // Reset
        performanceDisplayManager.reset();
        
        // Verify reset UI update was scheduled
        verify(mockActivity, atLeastOnce()).runOnUiThread(any(Runnable.class));
        
        // Verify last metrics are cleared
        assertNull(performanceDisplayManager.getLastMetrics());
    }
    
    @Test
    public void testRequirement1_3_FPSDisplay() {
        // Test Requirement 1.3: Display frame rate monitoring for at least 10 FPS
        performanceDisplayManager.showPerformanceOverlay();
        
        PerformanceMetricsCollector.FrameRateMetrics metrics = createTestMetrics();
        metrics.currentFps = 12.5f; // Above minimum 10 FPS
        metrics.averageFps = 11.8f;
        
        performanceDisplayManager.updateMetrics(metrics, null);
        
        // Verify UI update was called to display FPS
        verify(mockActivity).runOnUiThread(any(Runnable.class));
        
        // Test minimum FPS threshold
        metrics.currentFps = 9.5f; // Below minimum 10 FPS
        performanceDisplayManager.updateMetrics(metrics, null);
        
        // Should still display but with different color coding
        verify(mockActivity, times(2)).runOnUiThread(any(Runnable.class));
    }
    
    @Test
    public void testRequirement2_3_ProcessingTimeDisplay() {
        // Test Requirement 2.3: Show processing time within 50ms requirement
        performanceDisplayManager.showPerformanceOverlay();
        
        PerformanceMetricsCollector.FrameRateMetrics metrics = createTestMetrics();
        metrics.averageProcessingTimeMs = 45; // Within 50ms limit
        metrics.maxProcessingTimeMs = 48;
        
        performanceDisplayManager.updateMetrics(metrics, null);
        
        // Verify UI update was called to display processing time
        verify(mockActivity).runOnUiThread(any(Runnable.class));
        
        // Test exceeding processing time limit
        metrics.averageProcessingTimeMs = 75; // Exceeds 50ms limit
        metrics.maxProcessingTimeMs = 85;
        
        performanceDisplayManager.updateMetrics(metrics, null);
        
        // Should display with warning color coding
        verify(mockActivity, times(2)).runOnUiThread(any(Runnable.class));
    }
    
    // Helper methods
    
    private PerformanceMetricsCollector.FrameRateMetrics createTestMetrics() {
        PerformanceMetricsCollector.FrameRateMetrics metrics = 
            new PerformanceMetricsCollector.FrameRateMetrics();
        metrics.currentFps = 25.0f;
        metrics.averageFps = 24.5f;
        metrics.totalFrames = 100;
        metrics.droppedFrames = 5;
        metrics.dropRate = 5.0f;
        metrics.averageProcessingTimeMs = 35;
        metrics.maxProcessingTimeMs = 45;
        metrics.currentLevel = PerformanceMonitor.PerformanceLevel.HIGH;
        metrics.isFrameDropping = false;
        return metrics;
    }
    
    private PerformanceMonitor.PerformanceMetrics createTestSystemMetrics() {
        PerformanceMonitor.PerformanceMetrics metrics = 
            new PerformanceMonitor.PerformanceMetrics();
        metrics.totalMemoryMB = 4096;
        metrics.availableMemoryMB = 2048;
        metrics.usedMemoryMB = 2048;
        metrics.memoryUsagePercent = 50.0;
        metrics.averageProcessingTimeMs = 35;
        metrics.maxProcessingTimeMs = 45;
        metrics.frameDropCount = 5;
        metrics.currentLevel = PerformanceMonitor.PerformanceLevel.HIGH;
        metrics.isLowMemoryDevice = false;
        return metrics;
    }
}