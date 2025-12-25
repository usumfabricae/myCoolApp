package com.example.opencvcamerastream.performance;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.opencvcamerastream.processing.CopyOperationTracker;
import com.example.opencvcamerastream.processing.ZeroCopyProcessor;
import com.example.opencvcamerastream.processing.FrameProcessor;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PerformanceMeasurementManager coordinates comprehensive performance measurement and validation
 * for Task 25: Measure and validate CPU usage reduction
 * 
 * This class integrates:
 * - CPU usage profiling with CpuUsageProfiler
 * - Copy operation tracking with CopyOperationTracker
 * - Zero-copy optimization metrics with ZeroCopyProcessor
 * - Frame processing latency measurement
 * - Memory pressure and GC frequency monitoring
 * - Before/after optimization comparison
 * - Performance documentation and reporting
 * 
 * Requirements addressed:
 * - Req-13.6: Measure framebuffer operation CPU percentage (target: <30%)
 * - Task 25: Profile CPU usage before and after optimization
 * - Task 25: Validate frame processing latency improvement (target: 20-30ms reduction)
 * - Task 25: Measure memory pressure and GC frequency reduction
 * - Task 25: Document performance improvements
 */
public class PerformanceMeasurementManager implements 
        CpuUsageProfiler.CpuUsageCallback,
        PerformanceMetricsCollector.PerformanceMetricsCallback {
    
    private static final String TAG = "PerformanceMeasurementManager";
    
    // Measurement phases
    public enum MeasurementPhase {
        BASELINE("Baseline (Before Optimization)"),
        OPTIMIZED("Optimized (After Optimization)"),
        VALIDATION("Validation and Comparison");
        
        private final String description;
        
        MeasurementPhase(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // Performance measurement results
    public static class PerformanceMeasurementResults {
        public final CpuUsageProfiler.CpuUsageMetrics baselineMetrics;
        public final CpuUsageProfiler.CpuUsageMetrics optimizedMetrics;
        public final CopyOperationTracker.CopyOperationMetrics copyMetrics;
        public final ZeroCopyProcessor.ZeroCopyPerformanceMetrics zeroCopyMetrics;
        public final CpuUsageProfiler.OptimizationResults optimizationResults;
        public final String performanceReport;
        public final boolean allTargetsMet;
        public final long measurementTimestamp;
        
        public PerformanceMeasurementResults(
                @Nullable CpuUsageProfiler.CpuUsageMetrics baseline,
                @Nullable CpuUsageProfiler.CpuUsageMetrics optimized,
                @Nullable CopyOperationTracker.CopyOperationMetrics copy,
                @Nullable ZeroCopyProcessor.ZeroCopyPerformanceMetrics zeroCopy,
                @Nullable CpuUsageProfiler.OptimizationResults optimization,
                @NonNull String report) {
            this.baselineMetrics = baseline;
            this.optimizedMetrics = optimized;
            this.copyMetrics = copy;
            this.zeroCopyMetrics = zeroCopy;
            this.optimizationResults = optimization;
            this.performanceReport = report;
            this.allTargetsMet = optimization != null && optimization.meetsAllTargets;
            this.measurementTimestamp = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return String.format("PerformanceMeasurementResults{allTargetsMet=%s, timestamp=%s}",
                    allTargetsMet, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(new Date(measurementTimestamp)));
        }
    }
    
    // Callback interface for measurement updates
    public interface PerformanceMeasurementCallback {
        void onMeasurementPhaseChanged(@NonNull MeasurementPhase phase);
        void onMeasurementUpdate(@NonNull String update);
        void onMeasurementCompleted(@NonNull PerformanceMeasurementResults results);
        void onMeasurementError(@NonNull String error, @Nullable Exception exception);
    }
    
    private final Context context;
    private final CpuUsageProfiler cpuProfiler;
    private final PerformanceMetricsCollector metricsCollector;
    private PerformanceMeasurementCallback callback;
    
    // Component references for measurement
    private CopyOperationTracker copyTracker;
    private ZeroCopyProcessor zeroCopyProcessor;
    private FrameProcessor frameProcessor;
    
    // Measurement state
    private final AtomicBoolean isMeasuring = new AtomicBoolean(false);
    private final AtomicLong measurementStartTime = new AtomicLong(0);
    private volatile MeasurementPhase currentPhase = MeasurementPhase.BASELINE;
    
    // Results storage
    private CpuUsageProfiler.CpuUsageMetrics baselineMetrics;
    private CpuUsageProfiler.CpuUsageMetrics optimizedMetrics;
    private CopyOperationTracker.CopyOperationMetrics copyMetrics;
    private ZeroCopyProcessor.ZeroCopyPerformanceMetrics zeroCopyMetrics;
    
    // UI update handler
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    
    public PerformanceMeasurementManager(@NonNull Context context) {
        this.context = context;
        this.cpuProfiler = new CpuUsageProfiler();
        this.metricsCollector = new PerformanceMetricsCollector(
                new com.example.opencvcamerastream.error.PerformanceMonitor());
        
        // Set up callbacks
        cpuProfiler.setCallback(this);
        metricsCollector.setCallback(this);
        
        Log.d(TAG, "PerformanceMeasurementManager initialized");
    }
    
    /**
     * Set callback for measurement updates
     */
    public void setCallback(@Nullable PerformanceMeasurementCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Set component references for measurement
     */
    public void setComponents(@Nullable CopyOperationTracker copyTracker,
                            @Nullable ZeroCopyProcessor zeroCopyProcessor,
                            @Nullable FrameProcessor frameProcessor) {
        this.copyTracker = copyTracker;
        this.zeroCopyProcessor = zeroCopyProcessor;
        this.frameProcessor = frameProcessor;
        
        Log.d(TAG, "Performance measurement components set");
    }
    
    /**
     * Start baseline measurement (before optimization)
     */
    public void startBaselineMeasurement() {
        if (isMeasuring.get()) {
            Log.w(TAG, "Measurement already in progress");
            return;
        }
        
        Log.i(TAG, "Starting baseline performance measurement");
        
        currentPhase = MeasurementPhase.BASELINE;
        isMeasuring.set(true);
        measurementStartTime.set(System.currentTimeMillis());
        
        // Reset all counters
        resetAllCounters();
        
        // Start CPU profiling
        cpuProfiler.startMonitoring();
        
        // Start metrics collection
        metricsCollector.startMonitoring();
        
        // Notify callback
        notifyPhaseChanged(currentPhase);
        notifyMeasurementUpdate("Baseline measurement started - measuring performance before optimization");
        
        Log.i(TAG, "Baseline measurement started successfully");
    }
    
    /**
     * Complete baseline measurement and prepare for optimization measurement
     */
    public void completeBaselineMeasurement() {
        if (!isMeasuring.get() || currentPhase != MeasurementPhase.BASELINE) {
            Log.w(TAG, "Baseline measurement not in progress");
            return;
        }
        
        Log.i(TAG, "Completing baseline performance measurement");
        
        // Stop monitoring and collect baseline metrics
        baselineMetrics = cpuProfiler.stopMonitoring();
        metricsCollector.stopMonitoring();
        
        if (baselineMetrics != null) {
            cpuProfiler.setBaselineMetrics(baselineMetrics);
            Log.i(TAG, "Baseline metrics captured: " + baselineMetrics);
            notifyMeasurementUpdate("Baseline measurement completed: " + baselineMetrics.toString());
        } else {
            Log.e(TAG, "Failed to capture baseline metrics");
            notifyMeasurementError("Failed to capture baseline metrics", null);
        }
        
        isMeasuring.set(false);
        Log.i(TAG, "Baseline measurement completed");
    }
    
    /**
     * Start optimized measurement (after optimization)
     */
    public void startOptimizedMeasurement() {
        if (isMeasuring.get()) {
            Log.w(TAG, "Measurement already in progress");
            return;
        }
        
        if (baselineMetrics == null) {
            Log.e(TAG, "Cannot start optimized measurement without baseline");
            notifyMeasurementError("Baseline measurement required before optimized measurement", null);
            return;
        }
        
        Log.i(TAG, "Starting optimized performance measurement");
        
        currentPhase = MeasurementPhase.OPTIMIZED;
        isMeasuring.set(true);
        measurementStartTime.set(System.currentTimeMillis());
        
        // Reset counters for optimized measurement
        resetAllCounters();
        
        // Start CPU profiling
        cpuProfiler.startMonitoring();
        
        // Start metrics collection
        metricsCollector.startMonitoring();
        
        // Notify callback
        notifyPhaseChanged(currentPhase);
        notifyMeasurementUpdate("Optimized measurement started - measuring performance after optimization");
        
        Log.i(TAG, "Optimized measurement started successfully");
    }
    
    /**
     * Complete optimized measurement and perform validation
     */
    public void completeOptimizedMeasurement() {
        if (!isMeasuring.get() || currentPhase != MeasurementPhase.OPTIMIZED) {
            Log.w(TAG, "Optimized measurement not in progress");
            return;
        }
        
        Log.i(TAG, "Completing optimized performance measurement");
        
        // Stop monitoring and collect optimized metrics
        optimizedMetrics = cpuProfiler.stopMonitoring();
        metricsCollector.stopMonitoring();
        
        if (optimizedMetrics != null) {
            Log.i(TAG, "Optimized metrics captured: " + optimizedMetrics);
            notifyMeasurementUpdate("Optimized measurement completed: " + optimizedMetrics.toString());
            
            // Perform validation
            performValidation();
        } else {
            Log.e(TAG, "Failed to capture optimized metrics");
            notifyMeasurementError("Failed to capture optimized metrics", null);
        }
        
        isMeasuring.set(false);
        Log.i(TAG, "Optimized measurement completed");
    }
    
    /**
     * Perform comprehensive validation of optimization results
     */
    private void performValidation() {
        Log.i(TAG, "Performing optimization validation");
        
        currentPhase = MeasurementPhase.VALIDATION;
        notifyPhaseChanged(currentPhase);
        
        try {
            // Collect additional metrics from components
            if (copyTracker != null) {
                copyMetrics = copyTracker.getMetrics();
                Log.d(TAG, "Copy operation metrics: " + copyMetrics);
            }
            
            if (zeroCopyProcessor != null) {
                zeroCopyMetrics = zeroCopyProcessor.getPerformanceMetrics();
                Log.d(TAG, "Zero-copy metrics: " + zeroCopyMetrics);
            }
            
            // Validate optimization results
            CpuUsageProfiler.OptimizationResults optimizationResults = cpuProfiler.validateOptimization();
            
            if (optimizationResults != null) {
                Log.i(TAG, "Optimization validation completed: " + optimizationResults);
                
                // Generate comprehensive performance report
                String performanceReport = generatePerformanceReport(optimizationResults);
                
                // Create final results
                PerformanceMeasurementResults results = new PerformanceMeasurementResults(
                        baselineMetrics, optimizedMetrics, copyMetrics, zeroCopyMetrics,
                        optimizationResults, performanceReport);
                
                // Save report to file
                savePerformanceReport(performanceReport);
                
                // Notify completion
                notifyMeasurementCompleted(results);
                
                Log.i(TAG, "Performance measurement and validation completed successfully");
                
            } else {
                Log.e(TAG, "Optimization validation failed");
                notifyMeasurementError("Optimization validation failed", null);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during validation", e);
            notifyMeasurementError("Error during validation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate comprehensive performance report
     */
    private String generatePerformanceReport(CpuUsageProfiler.OptimizationResults optimizationResults) {
        StringBuilder report = new StringBuilder();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        
        report.append("=== PERFORMANCE MEASUREMENT REPORT ===\n");
        report.append("Generated: ").append(dateFormat.format(new Date())).append("\n");
        report.append("Task: 25. Measure and validate CPU usage reduction\n\n");
        
        // Executive Summary
        report.append("EXECUTIVE SUMMARY\n");
        report.append("================\n");
        report.append(optimizationResults.validationSummary).append("\n\n");
        
        // Detailed Metrics Comparison
        report.append("DETAILED METRICS COMPARISON\n");
        report.append("===========================\n");
        
        if (baselineMetrics != null && optimizedMetrics != null) {
            report.append("Baseline Metrics (Before Optimization):\n");
            report.append("  • Total CPU Usage: ").append(String.format("%.1f%%", baselineMetrics.totalCpuPercentage)).append("\n");
            report.append("  • Framebuffer CPU: ").append(String.format("%.1f%%", baselineMetrics.framebufferCpuPercentage)).append("\n");
            report.append("  • Average Frame Latency: ").append(String.format("%.1fms", baselineMetrics.averageFrameLatencyMs)).append("\n");
            report.append("  • Total Frames: ").append(baselineMetrics.totalFrames).append("\n");
            report.append("  • GC Frequency: ").append(String.format("%.2f/s", baselineMetrics.gcFrequencyPerSecond)).append("\n");
            report.append("  • Memory Usage: ").append(baselineMetrics.totalMemoryMB).append("MB\n\n");
            
            report.append("Optimized Metrics (After Optimization):\n");
            report.append("  • Total CPU Usage: ").append(String.format("%.1f%%", optimizedMetrics.totalCpuPercentage)).append("\n");
            report.append("  • Framebuffer CPU: ").append(String.format("%.1f%%", optimizedMetrics.framebufferCpuPercentage)).append("\n");
            report.append("  • Average Frame Latency: ").append(String.format("%.1fms", optimizedMetrics.averageFrameLatencyMs)).append("\n");
            report.append("  • Total Frames: ").append(optimizedMetrics.totalFrames).append("\n");
            report.append("  • GC Frequency: ").append(String.format("%.2f/s", optimizedMetrics.gcFrequencyPerSecond)).append("\n");
            report.append("  • Memory Usage: ").append(optimizedMetrics.totalMemoryMB).append("MB\n\n");
        }
        
        // Copy Operation Analysis
        if (copyMetrics != null) {
            report.append("COPY OPERATION ANALYSIS\n");
            report.append("=======================\n");
            report.append("  • Total Frames Processed: ").append(copyMetrics.getTotalFrames()).append("\n");
            report.append("  • Average Copies per Frame: ").append(String.format("%.1f", copyMetrics.getAverageCopiesPerFrame())).append("\n");
            report.append("  • Optimization Status: ").append(copyMetrics.getOptimizationStatus()).append("\n");
            report.append("  • Estimated CPU Reduction: ").append(String.format("%.1f%%", copyMetrics.getEstimatedCpuReduction())).append("\n\n");
        }
        
        // Zero-Copy Optimization Analysis
        if (zeroCopyMetrics != null) {
            report.append("ZERO-COPY OPTIMIZATION ANALYSIS\n");
            report.append("===============================\n");
            report.append("  • Total Frames: ").append(zeroCopyMetrics.totalFrames).append("\n");
            report.append("  • Average Copies per Frame: ").append(String.format("%.1f", zeroCopyMetrics.averageCopiesPerFrame)).append("\n");
            report.append("  • Average Processing Time: ").append(String.format("%.1fms", zeroCopyMetrics.averageProcessingTimeMs)).append("\n");
            report.append("  • Estimated CPU Usage Reduction: ").append(String.format("%.1f%%", zeroCopyMetrics.cpuUsageReduction)).append("\n\n");
        }
        
        // Target Achievement Analysis
        report.append("TARGET ACHIEVEMENT ANALYSIS\n");
        report.append("===========================\n");
        report.append("Requirements from Req-13.6 and Task 25:\n");
        report.append("  • Framebuffer CPU <30%: ").append(optimizedMetrics != null && optimizedMetrics.meetsFramebufferTarget() ? "✓ ACHIEVED" : "✗ NOT ACHIEVED").append("\n");
        report.append("  • Latency reduction 20-30ms: ").append(optimizedMetrics != null && optimizedMetrics.meetsLatencyTarget(baselineMetrics) ? "✓ ACHIEVED" : "✗ NOT ACHIEVED").append("\n");
        report.append("  • Total CPU reduction 40-50%: ").append(optimizedMetrics != null && optimizedMetrics.meetsCpuReductionTarget(baselineMetrics) ? "✓ ACHIEVED" : "✗ NOT ACHIEVED").append("\n");
        report.append("  • Copy operations ≤2 per frame: ").append(copyMetrics != null && copyMetrics.meetsOptimizationTargets() ? "✓ ACHIEVED" : "✗ NOT ACHIEVED").append("\n\n");
        
        // Recommendations
        report.append("RECOMMENDATIONS\n");
        report.append("===============\n");
        if (optimizationResults.meetsAllTargets) {
            report.append("✓ All optimization targets have been met. The framebuffer copy optimization\n");
            report.append("  has successfully reduced CPU usage and improved frame processing latency.\n");
            report.append("  Continue monitoring performance to ensure sustained improvements.\n");
        } else {
            report.append("✗ Some optimization targets were not met. Consider the following:\n");
            if (optimizedMetrics != null && !optimizedMetrics.meetsFramebufferTarget()) {
                report.append("  • Further reduce framebuffer copy operations\n");
                report.append("  • Investigate remaining Mat clone() and copyTo() operations\n");
            }
            if (optimizedMetrics != null && !optimizedMetrics.meetsLatencyTarget(baselineMetrics)) {
                report.append("  • Optimize OpenCV processing algorithms\n");
                report.append("  • Consider hardware acceleration for matrix operations\n");
            }
            if (optimizedMetrics != null && !optimizedMetrics.meetsCpuReductionTarget(baselineMetrics)) {
                report.append("  • Profile remaining CPU hotspots\n");
                report.append("  • Optimize thread synchronization and memory allocation\n");
            }
        }
        
        report.append("\n=== END OF REPORT ===\n");
        
        return report.toString();
    }
    
    /**
     * Save performance report to file
     */
    private void savePerformanceReport(String report) {
        try {
            File reportsDir = new File(context.getFilesDir(), "performance_reports");
            if (!reportsDir.exists()) {
                reportsDir.mkdirs();
            }
            
            SimpleDateFormat fileFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String filename = "performance_report_" + fileFormat.format(new Date()) + ".txt";
            File reportFile = new File(reportsDir, filename);
            
            FileWriter writer = new FileWriter(reportFile);
            writer.write(report);
            writer.close();
            
            Log.i(TAG, "Performance report saved to: " + reportFile.getAbsolutePath());
            notifyMeasurementUpdate("Performance report saved to: " + reportFile.getAbsolutePath());
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to save performance report", e);
            notifyMeasurementError("Failed to save performance report: " + e.getMessage(), e);
        }
    }
    
    /**
     * Reset all performance counters
     */
    private void resetAllCounters() {
        cpuProfiler.resetCounters();
        metricsCollector.resetCounters();
        
        if (copyTracker != null) {
            copyTracker.resetMetrics();
        }
        
        if (zeroCopyProcessor != null) {
            zeroCopyProcessor.resetPerformanceMetrics();
        }
        
        Log.d(TAG, "All performance counters reset");
    }
    
    /**
     * Get current measurement phase
     */
    public MeasurementPhase getCurrentPhase() {
        return currentPhase;
    }
    
    /**
     * Check if measurement is currently in progress
     */
    public boolean isMeasuring() {
        return isMeasuring.get();
    }
    
    /**
     * Get current CPU usage metrics (if monitoring)
     */
    @Nullable
    public CpuUsageProfiler.CpuUsageMetrics getCurrentCpuMetrics() {
        return cpuProfiler.getCurrentMetrics();
    }
    
    // Callback implementations
    
    @Override
    public void onCpuUsageUpdate(@NonNull CpuUsageProfiler.CpuUsageMetrics metrics) {
        // Update UI with current CPU metrics
        String update = String.format("CPU: %.1f%% (framebuffer: %.1f%%), Latency: %.1fms",
                metrics.totalCpuPercentage, metrics.framebufferCpuPercentage, metrics.averageFrameLatencyMs);
        notifyMeasurementUpdate(update);
    }
    
    @Override
    public void onOptimizationValidated(@NonNull CpuUsageProfiler.OptimizationResults results) {
        Log.i(TAG, "Optimization validation received: " + results);
    }
    
    @Override
    public void onPerformanceAlert(@NonNull String alert, @NonNull CpuUsageProfiler.CpuUsageMetrics metrics) {
        Log.w(TAG, "Performance alert: " + alert);
        notifyMeasurementUpdate("ALERT: " + alert);
    }
    
    @Override
    public void onFrameRateUpdate(@NonNull PerformanceMetricsCollector.FrameRateMetrics metrics) {
        // Update UI with frame rate metrics
        String update = String.format("FPS: %.1f, Drops: %d/%d (%.1f%%)",
                metrics.currentFps, metrics.droppedFrames, metrics.totalFrames, metrics.dropRate);
        notifyMeasurementUpdate(update);
    }
    
    @Override
    public void onPerformanceAdjustment(@NonNull PerformanceMetricsCollector.PerformanceAdjustment adjustment) {
        Log.i(TAG, "Performance adjustment: " + adjustment);
        notifyMeasurementUpdate("Performance adjusted: " + adjustment.reason);
    }
    
    @Override
    public void onFrameDropRecommended(@NonNull String reason) {
        Log.d(TAG, "Frame drop recommended: " + reason);
    }
    
    // Notification helpers
    
    private void notifyPhaseChanged(MeasurementPhase phase) {
        if (callback != null) {
            uiHandler.post(() -> callback.onMeasurementPhaseChanged(phase));
        }
    }
    
    private void notifyMeasurementUpdate(String update) {
        Log.d(TAG, "Measurement update: " + update);
        if (callback != null) {
            uiHandler.post(() -> callback.onMeasurementUpdate(update));
        }
    }
    
    private void notifyMeasurementCompleted(PerformanceMeasurementResults results) {
        if (callback != null) {
            uiHandler.post(() -> callback.onMeasurementCompleted(results));
        }
    }
    
    private void notifyMeasurementError(String error, @Nullable Exception exception) {
        Log.e(TAG, "Measurement error: " + error, exception);
        if (callback != null) {
            uiHandler.post(() -> callback.onMeasurementError(error, exception));
        }
    }
    
    /**
     * Release all resources
     */
    public void release() {
        Log.d(TAG, "Releasing PerformanceMeasurementManager resources");
        
        if (isMeasuring.get()) {
            cpuProfiler.stopMonitoring();
            metricsCollector.stopMonitoring();
            isMeasuring.set(false);
        }
        
        cpuProfiler.release();
        metricsCollector.release();
        
        callback = null;
        copyTracker = null;
        zeroCopyProcessor = null;
        frameProcessor = null;
        
        baselineMetrics = null;
        optimizedMetrics = null;
        copyMetrics = null;
        zeroCopyMetrics = null;
        
        Log.i(TAG, "PerformanceMeasurementManager resources released");
    }
}