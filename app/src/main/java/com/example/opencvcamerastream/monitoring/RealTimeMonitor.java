package com.example.opencvcamerastream.monitoring;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.opencvcamerastream.error.ErrorHandler;
import com.example.opencvcamerastream.error.PerformanceMonitor;
import com.example.opencvcamerastream.performance.PerformanceMetricsCollector;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real-time monitoring and alerting system
 * 
 * This class provides:
 * - Real-time error tracking and alerting
 * - Performance metrics monitoring
 * - Critical issue detection
 * - Automated log export for analysis
 * - Integration with ErrorHandler and PerformanceMonitor
 * 
 * Requirements addressed:
 * - Req-11: Comprehensive error logging and recovery
 * - Req-12: Automated testing and validation
 */
public class RealTimeMonitor {
    
    private static final String TAG = "RealTimeMonitor";
    
    // Monitoring configuration
    private static final int MONITORING_INTERVAL_MS = 5000; // 5 seconds
    private static final int MAX_EVENTS_IN_MEMORY = 1000;
    private static final int CRITICAL_ERROR_THRESHOLD = 5; // 5 critical errors trigger alert
    private static final int HIGH_ERROR_THRESHOLD = 10; // 10 high errors trigger alert
    private static final long ALERT_COOLDOWN_MS = 60000; // 1 minute between alerts
    
    // Alert levels
    public enum AlertLevel {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
    
    // Monitoring event
    public static class MonitoringEvent {
        public final long timestamp;
        public final AlertLevel level;
        public final String category;
        public final String message;
        public final String details;
        
        public MonitoringEvent(AlertLevel level, String category, String message, String details) {
            this.timestamp = System.currentTimeMillis();
            this.level = level;
            this.category = category;
            this.message = message;
            this.details = details;
        }
        
        @Override
        public String toString() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
            return String.format("[%s] %s - %s: %s", 
                sdf.format(new Date(timestamp)), level, category, message);
        }
    }
    
    // Alert callback interface
    public interface AlertCallback {
        void onAlert(@NonNull MonitoringEvent event);
        void onCriticalAlert(@NonNull String message, @NonNull List<MonitoringEvent> recentEvents);
    }
    
    private final Context context;
    private final ErrorHandler errorHandler;
    private final PerformanceMonitor performanceMonitor;
    private final PerformanceMetricsCollector metricsCollector;
    private AlertCallback alertCallback;
    
    // Event tracking
    private final ConcurrentLinkedQueue<MonitoringEvent> eventQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger criticalErrorCount = new AtomicInteger(0);
    private final AtomicInteger highErrorCount = new AtomicInteger(0);
    private long lastAlertTime = 0;
    
    // Monitoring state
    private boolean isMonitoring = false;
    private final Handler monitoringHandler = new Handler(Looper.getMainLooper());
    private final Runnable monitoringRunnable = new Runnable() {
        @Override
        public void run() {
            performMonitoringCheck();
            if (isMonitoring) {
                monitoringHandler.postDelayed(this, MONITORING_INTERVAL_MS);
            }
        }
    };
    
    // Log export
    private File logExportDir;
    private boolean autoExportEnabled = false;
    
    public RealTimeMonitor(@NonNull Context context,
                          @NonNull ErrorHandler errorHandler,
                          @NonNull PerformanceMonitor performanceMonitor,
                          @NonNull PerformanceMetricsCollector metricsCollector) {
        this.context = context.getApplicationContext();
        this.errorHandler = errorHandler;
        this.performanceMonitor = performanceMonitor;
        this.metricsCollector = metricsCollector;
        
        // Setup log export directory
        logExportDir = new File(context.getFilesDir(), "monitoring_logs");
        if (!logExportDir.exists()) {
            logExportDir.mkdirs();
        }
        
        // Setup error handler callback
        setupErrorHandlerCallback();
        
        // Setup performance monitor callback
        setupPerformanceMonitorCallback();
        
        // Setup metrics collector callback
        setupMetricsCollectorCallback();
        
        Log.d(TAG, "RealTimeMonitor initialized");
    }
    
    /**
     * Set alert callback for notifications
     */
    public void setAlertCallback(@Nullable AlertCallback callback) {
        this.alertCallback = callback;
    }
    
    /**
     * Start real-time monitoring
     */
    public void startMonitoring() {
        if (!isMonitoring) {
            isMonitoring = true;
            monitoringHandler.post(monitoringRunnable);
            
            recordEvent(AlertLevel.INFO, "Monitoring", "Real-time monitoring started", null);
            Log.i(TAG, "Real-time monitoring started");
        }
    }
    
    /**
     * Stop real-time monitoring
     */
    public void stopMonitoring() {
        if (isMonitoring) {
            isMonitoring = false;
            monitoringHandler.removeCallbacks(monitoringRunnable);
            
            recordEvent(AlertLevel.INFO, "Monitoring", "Real-time monitoring stopped", null);
            Log.i(TAG, "Real-time monitoring stopped");
        }
    }
    
    /**
     * Enable or disable automatic log export
     */
    public void setAutoExportEnabled(boolean enabled) {
        this.autoExportEnabled = enabled;
        Log.d(TAG, "Auto export " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Record a monitoring event
     */
    public void recordEvent(@NonNull AlertLevel level, @NonNull String category, 
                           @NonNull String message, @Nullable String details) {
        MonitoringEvent event = new MonitoringEvent(level, category, message, details);
        
        // Add to queue
        eventQueue.offer(event);
        
        // Trim queue if too large
        while (eventQueue.size() > MAX_EVENTS_IN_MEMORY) {
            eventQueue.poll();
        }
        
        // Update error counters
        if (level == AlertLevel.CRITICAL) {
            criticalErrorCount.incrementAndGet();
        } else if (level == AlertLevel.ERROR) {
            highErrorCount.incrementAndGet();
        }
        
        // Log event
        logEvent(event);
        
        // Trigger alert if needed
        if (shouldTriggerAlert(level)) {
            triggerAlert(event);
        }
    }
    
    /**
     * Get recent monitoring events
     */
    public List<MonitoringEvent> getRecentEvents(int count) {
        List<MonitoringEvent> events = new ArrayList<>(eventQueue);
        int size = events.size();
        if (size <= count) {
            return events;
        }
        return events.subList(size - count, size);
    }
    
    /**
     * Export monitoring logs to file
     */
    public File exportLogs() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String filename = "monitoring_log_" + sdf.format(new Date()) + ".txt";
        File logFile = new File(logExportDir, filename);
        
        try (FileWriter writer = new FileWriter(logFile)) {
            writer.write("OpenCV Camera Stream - Monitoring Log Export\n");
            writer.write("============================================\n");
            writer.write("Export Time: " + new Date() + "\n");
            writer.write("Total Events: " + eventQueue.size() + "\n");
            writer.write("Critical Errors: " + criticalErrorCount.get() + "\n");
            writer.write("High Errors: " + highErrorCount.get() + "\n");
            writer.write("\n");
            
            // Write performance metrics
            PerformanceMonitor.PerformanceMetrics perfMetrics = performanceMonitor.getCurrentMetrics();
            writer.write("Performance Metrics:\n");
            writer.write("  Memory Usage: " + String.format("%.1f%%", perfMetrics.memoryUsagePercent) + "\n");
            writer.write("  Avg Processing Time: " + perfMetrics.averageProcessingTimeMs + "ms\n");
            writer.write("  Performance Level: " + perfMetrics.currentLevel + "\n");
            writer.write("\n");
            
            // Write frame rate metrics
            PerformanceMetricsCollector.FrameRateMetrics frameMetrics = metricsCollector.getCurrentMetrics();
            writer.write("Frame Rate Metrics:\n");
            writer.write("  Current FPS: " + String.format("%.1f", frameMetrics.currentFps) + "\n");
            writer.write("  Average FPS: " + String.format("%.1f", frameMetrics.averageFps) + "\n");
            writer.write("  Dropped Frames: " + frameMetrics.droppedFrames + "/" + frameMetrics.totalFrames + "\n");
            writer.write("\n");
            
            // Write events
            writer.write("Events:\n");
            writer.write("========\n");
            for (MonitoringEvent event : eventQueue) {
                writer.write(event.toString() + "\n");
                if (event.details != null && !event.details.isEmpty()) {
                    writer.write("  Details: " + event.details + "\n");
                }
            }
            
            Log.i(TAG, "Monitoring logs exported to: " + logFile.getAbsolutePath());
            return logFile;
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to export monitoring logs", e);
            return null;
        }
    }
    
    /**
     * Clear all monitoring events
     */
    public void clearEvents() {
        eventQueue.clear();
        criticalErrorCount.set(0);
        highErrorCount.set(0);
        Log.d(TAG, "Monitoring events cleared");
    }
    
    /**
     * Get monitoring statistics
     */
    public MonitoringStats getStats() {
        MonitoringStats stats = new MonitoringStats();
        stats.totalEvents = eventQueue.size();
        stats.criticalErrors = criticalErrorCount.get();
        stats.highErrors = highErrorCount.get();
        stats.isMonitoring = isMonitoring;
        stats.autoExportEnabled = autoExportEnabled;
        
        // Count events by level
        for (MonitoringEvent event : eventQueue) {
            switch (event.level) {
                case INFO:
                    stats.infoCount++;
                    break;
                case WARNING:
                    stats.warningCount++;
                    break;
                case ERROR:
                    stats.errorCount++;
                    break;
                case CRITICAL:
                    stats.criticalCount++;
                    break;
            }
        }
        
        return stats;
    }
    
    /**
     * Monitoring statistics
     */
    public static class MonitoringStats {
        public int totalEvents = 0;
        public int infoCount = 0;
        public int warningCount = 0;
        public int errorCount = 0;
        public int criticalCount = 0;
        public int criticalErrors = 0;
        public int highErrors = 0;
        public boolean isMonitoring = false;
        public boolean autoExportEnabled = false;
        
        @Override
        public String toString() {
            return String.format("MonitoringStats{total=%d, info=%d, warn=%d, error=%d, critical=%d}",
                    totalEvents, infoCount, warningCount, errorCount, criticalCount);
        }
    }
    
    /**
     * Release monitoring resources
     */
    public void release() {
        Log.d(TAG, "Releasing RealTimeMonitor resources");
        
        // Stop monitoring
        stopMonitoring();
        
        // Export logs if auto-export is enabled
        if (autoExportEnabled && !eventQueue.isEmpty()) {
            exportLogs();
        }
        
        // Clear callback
        alertCallback = null;
        
        // Clear events
        clearEvents();
        
        Log.i(TAG, "RealTimeMonitor resources released");
    }
    
    // Private helper methods
    
    private void setupErrorHandlerCallback() {
        errorHandler.setErrorCallback(new ErrorHandler.ErrorCallback() {
            @Override
            public void onError(@NonNull ErrorHandler.ErrorInfo errorInfo) {
                AlertLevel level = mapSeverityToAlertLevel(errorInfo.severity);
                recordEvent(level, errorInfo.category.name(), 
                           errorInfo.message, errorInfo.userMessage);
            }
            
            @Override
            public void onRecoveryAttempt(@NonNull ErrorHandler.ErrorInfo errorInfo, int attemptNumber) {
                recordEvent(AlertLevel.WARNING, "Recovery", 
                           "Recovery attempt " + attemptNumber + " for " + errorInfo.category,
                           errorInfo.message);
            }
            
            @Override
            public void onRecoverySuccess(@NonNull ErrorHandler.ErrorInfo errorInfo, int totalAttempts) {
                recordEvent(AlertLevel.INFO, "Recovery", 
                           "Recovery successful after " + totalAttempts + " attempts",
                           errorInfo.category.name());
            }
            
            @Override
            public void onRecoveryFailed(@NonNull ErrorHandler.ErrorInfo errorInfo, int totalAttempts) {
                recordEvent(AlertLevel.CRITICAL, "Recovery", 
                           "Recovery failed after " + totalAttempts + " attempts",
                           errorInfo.category.name() + ": " + errorInfo.message);
            }
        });
    }
    
    private void setupPerformanceMonitorCallback() {
        performanceMonitor.setPerformanceCallback(new PerformanceMonitor.PerformanceCallback() {
            @Override
            public void onPerformanceLevelChanged(@NonNull PerformanceMonitor.PerformanceLevel newLevel,
                                                 @NonNull PerformanceMonitor.PerformanceLevel oldLevel) {
                AlertLevel alertLevel = newLevel == PerformanceMonitor.PerformanceLevel.CRITICAL ? 
                    AlertLevel.ERROR : AlertLevel.WARNING;
                recordEvent(alertLevel, "Performance", 
                           "Performance level changed: " + oldLevel + " -> " + newLevel,
                           null);
            }
            
            @Override
            public void onMemoryWarning(long usedMemoryMB, long totalMemoryMB) {
                recordEvent(AlertLevel.WARNING, "Memory", 
                           "Memory warning: " + usedMemoryMB + "MB / " + totalMemoryMB + "MB",
                           String.format("%.1f%% memory usage", (double)usedMemoryMB / totalMemoryMB * 100));
            }
            
            @Override
            public void onMemoryCritical(long usedMemoryMB, long totalMemoryMB) {
                recordEvent(AlertLevel.CRITICAL, "Memory", 
                           "Critical memory usage: " + usedMemoryMB + "MB / " + totalMemoryMB + "MB",
                           String.format("%.1f%% memory usage", (double)usedMemoryMB / totalMemoryMB * 100));
            }
            
            @Override
            public void onProcessingTimeWarning(long processingTimeMs) {
                recordEvent(AlertLevel.WARNING, "Processing", 
                           "Processing time warning: " + processingTimeMs + "ms",
                           "Exceeds recommended threshold");
            }
            
            @Override
            public void onFrameDropRecommended(@NonNull String reason) {
                recordEvent(AlertLevel.INFO, "Performance", 
                           "Frame drop recommended", reason);
            }
        });
    }
    
    private void setupMetricsCollectorCallback() {
        metricsCollector.setCallback(new PerformanceMetricsCollector.PerformanceMetricsCallback() {
            @Override
            public void onFrameRateUpdate(@NonNull PerformanceMetricsCollector.FrameRateMetrics metrics) {
                // Only log significant changes
                if (metrics.currentFps < 10 && metrics.currentFps > 0) {
                    recordEvent(AlertLevel.WARNING, "FrameRate", 
                               "Low frame rate: " + String.format("%.1f", metrics.currentFps) + " FPS",
                               "Below minimum threshold of 10 FPS");
                }
            }
            
            @Override
            public void onPerformanceAdjustment(@NonNull PerformanceMetricsCollector.PerformanceAdjustment adjustment) {
                recordEvent(AlertLevel.INFO, "Performance", 
                           "Performance adjustment applied", adjustment.toString());
            }
            
            @Override
            public void onFrameDropRecommended(@NonNull String reason) {
                // Already handled by PerformanceMonitor callback
            }
        });
    }
    
    private void performMonitoringCheck() {
        // Check for critical error threshold
        if (criticalErrorCount.get() >= CRITICAL_ERROR_THRESHOLD) {
            triggerCriticalAlert("Critical error threshold exceeded: " + criticalErrorCount.get() + " errors");
        }
        
        // Check for high error threshold
        if (highErrorCount.get() >= HIGH_ERROR_THRESHOLD) {
            triggerCriticalAlert("High error threshold exceeded: " + highErrorCount.get() + " errors");
        }
        
        // Auto-export logs if enabled and there are critical errors
        if (autoExportEnabled && criticalErrorCount.get() > 0) {
            exportLogs();
        }
    }
    
    private boolean shouldTriggerAlert(AlertLevel level) {
        if (level != AlertLevel.CRITICAL && level != AlertLevel.ERROR) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAlertTime < ALERT_COOLDOWN_MS) {
            return false; // Cooldown period
        }
        
        return true;
    }
    
    private void triggerAlert(MonitoringEvent event) {
        lastAlertTime = System.currentTimeMillis();
        
        if (alertCallback != null) {
            alertCallback.onAlert(event);
        }
        
        Log.w(TAG, "Alert triggered: " + event);
    }
    
    private void triggerCriticalAlert(String message) {
        List<MonitoringEvent> recentEvents = getRecentEvents(20);
        
        if (alertCallback != null) {
            alertCallback.onCriticalAlert(message, recentEvents);
        }
        
        Log.e(TAG, "Critical alert: " + message);
        
        // Auto-export logs on critical alert
        if (autoExportEnabled) {
            exportLogs();
        }
    }
    
    private void logEvent(MonitoringEvent event) {
        switch (event.level) {
            case INFO:
                Log.i(TAG, event.toString());
                break;
            case WARNING:
                Log.w(TAG, event.toString());
                break;
            case ERROR:
            case CRITICAL:
                Log.e(TAG, event.toString());
                break;
        }
    }
    
    private AlertLevel mapSeverityToAlertLevel(ErrorHandler.ErrorSeverity severity) {
        switch (severity) {
            case LOW:
                return AlertLevel.INFO;
            case MEDIUM:
                return AlertLevel.WARNING;
            case HIGH:
                return AlertLevel.ERROR;
            case CRITICAL:
                return AlertLevel.CRITICAL;
            default:
                return AlertLevel.INFO;
        }
    }
}
