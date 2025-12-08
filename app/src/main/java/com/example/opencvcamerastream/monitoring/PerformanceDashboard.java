package com.example.opencvcamerastream.monitoring;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.opencvcamerastream.error.PerformanceMonitor;
import com.example.opencvcamerastream.performance.PerformanceMetricsCollector;
import java.util.Locale;

/**
 * Performance metrics dashboard UI component
 * 
 * This class provides:
 * - Real-time performance metrics display
 * - Visual indicators for performance levels
 * - Memory usage monitoring
 * - Frame rate display
 * - Error count tracking
 * 
 * Requirements addressed:
 * - Req-11: Comprehensive error logging and recovery
 * - Req-12: Automated testing and validation
 */
public class PerformanceDashboard extends LinearLayout {
    
    private static final String TAG = "PerformanceDashboard";
    
    // UI Components
    private TextView fpsTextView;
    private TextView memoryTextView;
    private TextView processingTimeTextView;
    private TextView performanceLevelTextView;
    private TextView errorCountTextView;
    private TextView statusTextView;
    
    // Metrics sources
    private PerformanceMonitor performanceMonitor;
    private PerformanceMetricsCollector metricsCollector;
    private RealTimeMonitor realTimeMonitor;
    
    // Update state
    private boolean isVisible = false;
    
    public PerformanceDashboard(@NonNull Context context) {
        super(context);
        init();
    }
    
    public PerformanceDashboard(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public PerformanceDashboard(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        setOrientation(VERTICAL);
        setBackgroundColor(Color.argb(200, 0, 0, 0)); // Semi-transparent black
        setPadding(16, 16, 16, 16);
        setGravity(Gravity.START);
        
        // Create UI components
        createTextViews();
        
        // Initially hidden
        setVisibility(View.GONE);
    }
    
    private void createTextViews() {
        // FPS display
        fpsTextView = createMetricTextView("FPS: --");
        addView(fpsTextView);
        
        // Memory usage display
        memoryTextView = createMetricTextView("Memory: --");
        addView(memoryTextView);
        
        // Processing time display
        processingTimeTextView = createMetricTextView("Processing: --");
        addView(processingTimeTextView);
        
        // Performance level display
        performanceLevelTextView = createMetricTextView("Level: --");
        addView(performanceLevelTextView);
        
        // Error count display
        errorCountTextView = createMetricTextView("Errors: --");
        addView(errorCountTextView);
        
        // Status display
        statusTextView = createMetricTextView("Status: --");
        addView(statusTextView);
    }
    
    private TextView createMetricTextView(String initialText) {
        TextView textView = new TextView(getContext());
        textView.setText(initialText);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(12);
        textView.setPadding(0, 4, 0, 4);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textView.setLayoutParams(params);
        
        return textView;
    }
    
    /**
     * Set metrics sources
     */
    public void setMetricsSources(@NonNull PerformanceMonitor performanceMonitor,
                                  @NonNull PerformanceMetricsCollector metricsCollector,
                                  @NonNull RealTimeMonitor realTimeMonitor) {
        this.performanceMonitor = performanceMonitor;
        this.metricsCollector = metricsCollector;
        this.realTimeMonitor = realTimeMonitor;
    }
    
    /**
     * Show the dashboard
     */
    public void show() {
        if (!isVisible) {
            setVisibility(View.VISIBLE);
            isVisible = true;
        }
    }
    
    /**
     * Hide the dashboard
     */
    public void hide() {
        if (isVisible) {
            setVisibility(View.GONE);
            isVisible = false;
        }
    }
    
    /**
     * Toggle dashboard visibility
     */
    public void toggle() {
        if (isVisible) {
            hide();
        } else {
            show();
        }
    }
    
    /**
     * Update dashboard with current metrics
     */
    public void updateMetrics() {
        if (!isVisible || performanceMonitor == null || metricsCollector == null || realTimeMonitor == null) {
            return;
        }
        
        // Get current metrics
        PerformanceMetricsCollector.FrameRateMetrics frameMetrics = metricsCollector.getCurrentMetrics();
        PerformanceMonitor.PerformanceMetrics perfMetrics = performanceMonitor.getCurrentMetrics();
        RealTimeMonitor.MonitoringStats monitoringStats = realTimeMonitor.getStats();
        
        // Update FPS
        updateFpsDisplay(frameMetrics);
        
        // Update memory
        updateMemoryDisplay(perfMetrics);
        
        // Update processing time
        updateProcessingTimeDisplay(frameMetrics);
        
        // Update performance level
        updatePerformanceLevelDisplay(perfMetrics);
        
        // Update error count
        updateErrorCountDisplay(monitoringStats);
        
        // Update status
        updateStatusDisplay(frameMetrics, perfMetrics);
    }
    
    private void updateFpsDisplay(PerformanceMetricsCollector.FrameRateMetrics metrics) {
        String fpsText = String.format(Locale.US, "FPS: %.1f (avg: %.1f)", 
            metrics.currentFps, metrics.averageFps);
        fpsTextView.setText(fpsText);
        
        // Color code based on FPS
        if (metrics.currentFps >= 25) {
            fpsTextView.setTextColor(Color.GREEN);
        } else if (metrics.currentFps >= 15) {
            fpsTextView.setTextColor(Color.YELLOW);
        } else if (metrics.currentFps > 0) {
            fpsTextView.setTextColor(Color.RED);
        } else {
            fpsTextView.setTextColor(Color.WHITE);
        }
    }
    
    private void updateMemoryDisplay(PerformanceMonitor.PerformanceMetrics metrics) {
        String memoryText = String.format(Locale.US, "Memory: %dMB / %dMB (%.1f%%)",
            metrics.usedMemoryMB, metrics.totalMemoryMB, metrics.memoryUsagePercent);
        memoryTextView.setText(memoryText);
        
        // Color code based on memory usage
        if (metrics.memoryUsagePercent < 70) {
            memoryTextView.setTextColor(Color.GREEN);
        } else if (metrics.memoryUsagePercent < 85) {
            memoryTextView.setTextColor(Color.YELLOW);
        } else {
            memoryTextView.setTextColor(Color.RED);
        }
    }
    
    private void updateProcessingTimeDisplay(PerformanceMetricsCollector.FrameRateMetrics metrics) {
        String processingText = String.format(Locale.US, "Processing: %dms (max: %dms)",
            metrics.averageProcessingTimeMs, metrics.maxProcessingTimeMs);
        processingTimeTextView.setText(processingText);
        
        // Color code based on processing time
        if (metrics.averageProcessingTimeMs < 50) {
            processingTimeTextView.setTextColor(Color.GREEN);
        } else if (metrics.averageProcessingTimeMs < 100) {
            processingTimeTextView.setTextColor(Color.YELLOW);
        } else {
            processingTimeTextView.setTextColor(Color.RED);
        }
    }
    
    private void updatePerformanceLevelDisplay(PerformanceMonitor.PerformanceMetrics metrics) {
        String levelText = "Level: " + metrics.currentLevel.name();
        if (metrics.isLowMemoryDevice) {
            levelText += " (Low-end device)";
        }
        performanceLevelTextView.setText(levelText);
        
        // Color code based on performance level
        switch (metrics.currentLevel) {
            case HIGH:
                performanceLevelTextView.setTextColor(Color.GREEN);
                break;
            case MEDIUM:
                performanceLevelTextView.setTextColor(Color.YELLOW);
                break;
            case LOW:
                performanceLevelTextView.setTextColor(Color.rgb(255, 165, 0)); // Orange
                break;
            case CRITICAL:
                performanceLevelTextView.setTextColor(Color.RED);
                break;
        }
    }
    
    private void updateErrorCountDisplay(RealTimeMonitor.MonitoringStats stats) {
        String errorText = String.format(Locale.US, "Errors: %d critical, %d high, %d total",
            stats.criticalCount, stats.errorCount, stats.totalEvents);
        errorCountTextView.setText(errorText);
        
        // Color code based on error count
        if (stats.criticalCount > 0) {
            errorCountTextView.setTextColor(Color.RED);
        } else if (stats.errorCount > 0) {
            errorCountTextView.setTextColor(Color.YELLOW);
        } else {
            errorCountTextView.setTextColor(Color.GREEN);
        }
    }
    
    private void updateStatusDisplay(PerformanceMetricsCollector.FrameRateMetrics frameMetrics,
                                    PerformanceMonitor.PerformanceMetrics perfMetrics) {
        String status;
        int statusColor;
        
        // Determine overall status
        if (frameMetrics.currentFps < 10 && frameMetrics.currentFps > 0) {
            status = "Status: CRITICAL - Low FPS";
            statusColor = Color.RED;
        } else if (perfMetrics.memoryUsagePercent > 90) {
            status = "Status: CRITICAL - High Memory";
            statusColor = Color.RED;
        } else if (perfMetrics.currentLevel == PerformanceMonitor.PerformanceLevel.CRITICAL) {
            status = "Status: CRITICAL - Performance Degraded";
            statusColor = Color.RED;
        } else if (frameMetrics.currentFps < 20 && frameMetrics.currentFps > 0) {
            status = "Status: WARNING - Reduced FPS";
            statusColor = Color.YELLOW;
        } else if (perfMetrics.memoryUsagePercent > 80) {
            status = "Status: WARNING - High Memory";
            statusColor = Color.YELLOW;
        } else if (frameMetrics.isFrameDropping) {
            status = "Status: OPTIMIZING - Frame Dropping Active";
            statusColor = Color.YELLOW;
        } else {
            status = "Status: NORMAL - All Systems OK";
            statusColor = Color.GREEN;
        }
        
        statusTextView.setText(status);
        statusTextView.setTextColor(statusColor);
    }
}
