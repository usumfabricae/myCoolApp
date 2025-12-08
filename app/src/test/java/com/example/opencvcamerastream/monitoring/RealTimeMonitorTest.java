package com.example.opencvcamerastream.monitoring;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.example.opencvcamerastream.error.ErrorHandler;
import com.example.opencvcamerastream.error.PerformanceMonitor;
import com.example.opencvcamerastream.performance.PerformanceMetricsCollector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Unit tests for RealTimeMonitor
 * 
 * Tests:
 * - Event recording and retrieval
 * - Alert triggering
 * - Log export functionality
 * - Integration with ErrorHandler
 * - Monitoring statistics
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class RealTimeMonitorTest {
    
    private Context context;
    private ErrorHandler errorHandler;
    private PerformanceMonitor performanceMonitor;
    private PerformanceMetricsCollector metricsCollector;
    private RealTimeMonitor realTimeMonitor;
    
    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        errorHandler = new ErrorHandler(context);
        performanceMonitor = new PerformanceMonitor(context);
        metricsCollector = new PerformanceMetricsCollector(performanceMonitor);
        realTimeMonitor = new RealTimeMonitor(context, errorHandler, performanceMonitor, metricsCollector);
    }
    
    @After
    public void tearDown() {
        if (realTimeMonitor != null) {
            realTimeMonitor.release();
        }
        if (metricsCollector != null) {
            metricsCollector.release();
        }
        if (performanceMonitor != null) {
            performanceMonitor.release();
        }
        if (errorHandler != null) {
            errorHandler.release();
        }
    }
    
    @Test
    public void testEventRecording() {
        // Record events
        realTimeMonitor.recordEvent(RealTimeMonitor.AlertLevel.INFO, "Test", "Test message", null);
        realTimeMonitor.recordEvent(RealTimeMonitor.AlertLevel.WARNING, "Test", "Warning message", "Details");
        realTimeMonitor.recordEvent(RealTimeMonitor.AlertLevel.ERROR, "Test", "Error message", null);
        
        // Verify events are recorded
        List<RealTimeMonitor.MonitoringEvent> events = realTimeMonitor.getRecentEvents(10);
        assertEquals(3, events.size());
        
        // Verify event details
        RealTimeMonitor.MonitoringEvent firstEvent = events.get(0);
        assertEquals(RealTimeMonitor.AlertLevel.INFO, firstEvent.level);
        assertEquals("Test", firstEvent.category);
        assertEquals("Test message", firstEvent.message);
    }
    
    @Test
    public void testAlertTriggering() throws InterruptedException {
        final AtomicBoolean alertTriggered = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);
        
        // Set alert callback
        realTimeMonitor.setAlertCallback(new RealTimeMonitor.AlertCallback() {
            @Override
            public void onAlert(RealTimeMonitor.MonitoringEvent event) {
                alertTriggered.set(true);
                latch.countDown();
            }
            
            @Override
            public void onCriticalAlert(String message, List<RealTimeMonitor.MonitoringEvent> recentEvents) {
                // Not tested in this test
            }
        });
        
        // Record critical event
        realTimeMonitor.recordEvent(RealTimeMonitor.AlertLevel.CRITICAL, "Test", "Critical error", null);
        
        // Wait for alert
        assertTrue("Alert should be triggered", latch.await(2, TimeUnit.SECONDS));
        assertTrue("Alert callback should be called", alertTriggered.get());
    }
    
    @Test
    public void testCriticalAlertThreshold() throws InterruptedException {
        final AtomicInteger criticalAlertCount = new AtomicInteger(0);
        final CountDownLatch latch = new CountDownLatch(1);
        
        // Set alert callback
        realTimeMonitor.setAlertCallback(new RealTimeMonitor.AlertCallback() {
            @Override
            public void onAlert(RealTimeMonitor.MonitoringEvent event) {
                // Not tested in this test
            }
            
            @Override
            public void onCriticalAlert(String message, List<RealTimeMonitor.MonitoringEvent> recentEvents) {
                criticalAlertCount.incrementAndGet();
                latch.countDown();
            }
        });
        
        // Start monitoring
        realTimeMonitor.startMonitoring();
        
        // Record multiple critical events to trigger threshold
        for (int i = 0; i < 6; i++) {
            realTimeMonitor.recordEvent(RealTimeMonitor.AlertLevel.CRITICAL, "Test", 
                "Critical error " + i, null);
        }
        
        // Wait for critical alert (monitoring check runs every 5 seconds)
        assertTrue("Critical alert should be triggered", latch.await(10, TimeUnit.SECONDS));
        assertTrue("Critical alert callback should be called", criticalAlertCount.get() > 0);
        
        realTimeMonitor.stopMonitoring();
    }
    
    @Test
    public void testLogExport() {
        // Record some events
        realTimeMonitor.recordEvent(RealTimeMonitor.AlertLevel.INFO, "Test", "Info message", null);
        realTimeMonitor.recordEvent(RealTimeMonitor.AlertLevel.ERROR, "Test", "Error message", "Details");
        
        // Export logs
        File logFile = realTimeMonitor.exportLogs();
        
        // Verify log file was created
        assertNotNull("Log file should be created", logFile);
        assertTrue("Log file should exist", logFile.exists());
        assertTrue("Log file should not be empty", logFile.length() > 0);
        
        // Clean up
        logFile.delete();
    }
    
    @Test
    public void testMonitoringStats() {
        // Record events of different levels
        realTimeMonitor.recordEvent(RealTimeMonitor.AlertLevel.INFO, "Test", "Info 1", null);
        realTimeMonitor.recordEvent(RealTimeMonitor.AlertLevel.INFO, "Test", "Info 2", null);
        realTimeMonitor.recordEvent(RealTimeMonitor.AlertLevel.WARNING, "Test", "Warning 1", null);
        realTimeMonitor.recordEvent(RealTimeMonitor.AlertLevel.ERROR, "Test", "Error 1", null);
        realTimeMonitor.recordEvent(RealTimeMonitor.AlertLevel.CRITICAL, "Test", "Critical 1", null);
        
        // Get stats
        RealTimeMonitor.MonitoringStats stats = realTimeMonitor.getStats();
        
        // Verify stats
        assertEquals(5, stats.totalEvents);
        assertEquals(2, stats.infoCount);
        assertEquals(1, stats.warningCount);
        assertEquals(1, stats.errorCount);
        assertEquals(1, stats.criticalCount);
    }
    
    @Test
    public void testErrorHandlerIntegration() throws InterruptedException {
        final AtomicBoolean eventRecorded = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);
        
        // Set alert callback to verify integration
        realTimeMonitor.setAlertCallback(new RealTimeMonitor.AlertCallback() {
            @Override
            public void onAlert(RealTimeMonitor.MonitoringEvent event) {
                if (event.category.equals("CAMERA_HARDWARE")) {
                    eventRecorded.set(true);
                    latch.countDown();
                }
            }
            
            @Override
            public void onCriticalAlert(String message, List<RealTimeMonitor.MonitoringEvent> recentEvents) {
                // Not tested in this test
            }
        });
        
        // Trigger error through ErrorHandler
        errorHandler.handleCameraHardwareError(1, "Test camera error", null);
        
        // Wait for event to be recorded
        assertTrue("Event should be recorded through ErrorHandler integration", 
            latch.await(2, TimeUnit.SECONDS));
        assertTrue("Event should be recorded", eventRecorded.get());
        
        // Verify event was recorded
        List<RealTimeMonitor.MonitoringEvent> events = realTimeMonitor.getRecentEvents(10);
        assertTrue("Should have at least one event", events.size() > 0);
        
        boolean foundCameraEvent = false;
        for (RealTimeMonitor.MonitoringEvent event : events) {
            if (event.category.equals("CAMERA_HARDWARE")) {
                foundCameraEvent = true;
                break;
            }
        }
        assertTrue("Should find camera hardware event", foundCameraEvent);
    }
    
    @Test
    public void testClearEvents() {
        // Record events
        realTimeMonitor.recordEvent(RealTimeMonitor.AlertLevel.INFO, "Test", "Message 1", null);
        realTimeMonitor.recordEvent(RealTimeMonitor.AlertLevel.ERROR, "Test", "Message 2", null);
        
        // Verify events exist
        List<RealTimeMonitor.MonitoringEvent> events = realTimeMonitor.getRecentEvents(10);
        assertTrue("Should have events", events.size() > 0);
        
        // Clear events
        realTimeMonitor.clearEvents();
        
        // Verify events are cleared
        events = realTimeMonitor.getRecentEvents(10);
        assertEquals("Should have no events after clear", 0, events.size());
        
        // Verify stats are reset
        RealTimeMonitor.MonitoringStats stats = realTimeMonitor.getStats();
        assertEquals(0, stats.totalEvents);
        assertEquals(0, stats.criticalErrors);
        assertEquals(0, stats.highErrors);
    }
    
    @Test
    public void testMonitoringStartStop() {
        // Initially not monitoring
        RealTimeMonitor.MonitoringStats stats = realTimeMonitor.getStats();
        assertFalse("Should not be monitoring initially", stats.isMonitoring);
        
        // Start monitoring
        realTimeMonitor.startMonitoring();
        stats = realTimeMonitor.getStats();
        assertTrue("Should be monitoring after start", stats.isMonitoring);
        
        // Stop monitoring
        realTimeMonitor.stopMonitoring();
        stats = realTimeMonitor.getStats();
        assertFalse("Should not be monitoring after stop", stats.isMonitoring);
    }
    
    @Test
    public void testAutoExport() {
        // Enable auto-export
        realTimeMonitor.setAutoExportEnabled(true);
        
        // Verify auto-export is enabled
        RealTimeMonitor.MonitoringStats stats = realTimeMonitor.getStats();
        assertTrue("Auto-export should be enabled", stats.autoExportEnabled);
        
        // Disable auto-export
        realTimeMonitor.setAutoExportEnabled(false);
        stats = realTimeMonitor.getStats();
        assertFalse("Auto-export should be disabled", stats.autoExportEnabled);
    }
}
