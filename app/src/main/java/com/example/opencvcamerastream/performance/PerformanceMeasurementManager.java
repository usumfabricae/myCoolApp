package com.example.opencvcamerastream.performance;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.opencvcamerastream.processing.CopyOperationTracker;
import com.example.opencvcamerastream.processing.ZeroCopyProcessor;
import com.example.opencvcamerastream.processing.FrameProcessor;
import com.example.opencvcamerastream.error.PerformanceMonitor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * PerformanceMeasurementManager coordinates comprehensive performance measurement and validation
 * 
 * Requirements addressed:
 * - Task 25: Profile CPU usage before and after optimization
 * - Task 25: Measure framebuffer operation CPU percentage (target: <30%)
 * - Task 25: Validate frame processing latency improvement (target: 20-30ms reduction)
 * - Task 25: Measure memory pressure and GC frequency reduction
 * - Task 25: Document performance improvements
 * 
 * INTEGRATION STRATEGY:
 * - Coordinates CpuUsageProfiler, CopyOperationTracker, and ZeroCopyProcessor
 * - Provides unified measurement interface for all performance components
 * - Generates comprehensive performance reports with before/after comparison
 * - Validates optimization targets and provides actionable recommendations
 * - Exports detailed performance data for analysis and documentation
 */
public class PerformanceMeasurementManager {
    
    private static final String TAG = "PerformanceMeasurementManager";
    
    // Measurement phases
    public enum MeasurementPhase {
        BASELINE,       // Before optimization measurements
        OPTIMIZED,      // After optimization measurements
        COMPARISON      // Comparative analysis phase
    }
    
    // Measurement state
    private volatile MeasurementPhase currentPhase = MeasurementPhase.BASELINE;
    private final AtomicBoolean isMeasurementActive = new AtomicBoolean(false);
    private final AtomicLong measurementStartTime = new AtomicLong(0);
    
    // Performance measurement components
    private final CpuUsageProfiler cpuProfiler;
    private final PerformanceMonitor performanceMonitor;
    private final Context context;
    
    // Component references for measurement integration
    private CopyOperationTracker copyTracker;
    private ZeroCopyProcessor zeroCopyProcessor;
    private FrameProcessor frameProcessor;
    
    // Measurement results
    private CpuUsageProfiler.CpuUsageBaseline baselineResults;
    private CpuUsageProfiler.CpuUsageBaseline optimizedResults;
    private CpuUsageProfiler.PerformanceValidationResults validationResults;
    
    // Performance tracking
    private final AtomicLong totalMeasurementFrames = new AtomicLong(0);
    private final AtomicLong baselineFrames = new AtomicLong(0);
    private final AtomicLong optimizedFrames = new AtomicLong(0);
    
    /**
     * Comprehensive performance measurement results
     */
    public static class ComprehensivePerformanceResults {
        public final CpuUsageProfiler.CpuUsageBaseline baseline;
        public final CpuUsageProfiler.CpuUsageBaseline optimized;
        public final CpuUsageProfiler.PerformanceValidationResults validation;
        public final CopyOperationTracker.CopyOperationMetrics copyMetrics;
        public final ZeroCopyProcessor.ZeroCopyPerformanceMetrics zeroCopyMetrics;
        public final PerformanceMonitor.PerformanceMetrics systemMetrics;
        public final String detailedReport;
        public final boolean allTargetsMet;
        
        public ComprehensivePerformanceResults(
                CpuUsageProfiler.CpuUsageBaseline baseline,
                CpuUsageProfiler.CpuUsageBaseline optimized,
                CpuUsageProfiler.PerformanceValidationResults validation,
                CopyOperationTracker.CopyOperationMetrics copyMetrics,
                ZeroCopyProcessor.ZeroCopyPerformanceMetrics zeroCopyMetrics,
                PerformanceMonitor.PerformanceMetrics systemMetrics,
                String report) {
            this.baseline = baseline;
            this.optimized = optimized;
            this.validation = validation;
            this.copyMetrics = copyMetrics;
            this.zeroCopyMetrics = zeroCopyMetrics;
            this.systemMetrics = systemMetrics;
            this.detailedReport = report;
            this.allTargetsMet = validation != null && validation.meetsAllTargets();
        }
        
        @Override
        public String toString() {
            return String.format("ComprehensiveResults{allTargets=%s, cpuReduction=%.1f%%, latencyImpr=%.1fms, fbCpu=%.1f%%}",
                    allTargetsMet, 
                    validation != null ? validation.cpuReductionPercent : 0,
                    validation != null ? validation.latencyImprovementMs : 0,
                    validation != null ? validation.currentFramebufferCpuPercent : 0);
        }
    }
    
    public PerformanceMeasurementManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.cpuProfiler = new CpuUsageProfiler();
        this.performanceMonitor = new PerformanceMonitor(context);
        
        Log.d(TAG, "PerformanceMeasurementManager created for comprehensive performance validation");
    }
    
    /**
     * Set component references for integrated measurement
     * 
     * @param copyTracker Copy operation tracker
     * @param zeroCopyProcessor Zero-copy processor
     * @param frameProcessor Frame processor
     */
    public void setComponents(@Nullable CopyOperationTracker copyTracker,
                            @Nullable ZeroCopyProcessor zeroCopyProcessor,
                            @Nullable FrameProcessor frameProcessor) {
        this.copyTracker = copyTracker;
        this.zeroCopyProcessor = zeroCopyProcessor;
        this.frameProcessor = frameProcessor;
        
        Log.d(TAG, "Performance measurement components configured");
    }
    
    /**
     * Start baseline measurement phase (before optimization)
     * 
     * @param durationMs Duration to measure baseline performance
     */
    public void startBaselineMeasurement(long durationMs) {
        if (isMeasurementActive.get()) {
            Log.w(TAG, "Measurement already active");
            return;
        }
        
        Log.i(TAG, "Starting baseline measurement phase for " + durationMs + "ms");
        
        currentPhase = MeasurementPhase.BASELINE;
        isMeasurementActive.set(true);
        measurementStartTime.set(System.currentTimeMillis());
        
        // Reset counters
        totalMeasurementFrames.set(0);
        baselineFrames.set(0);
        optimizedFrames.set(0);
        
        // Reset component metrics
        if (copyTracker != null) {
            copyTracker.resetMetrics();
        }
        if (zeroCopyProcessor != null) {
            zeroCopyProcessor.resetPerformanceMetrics();
        }
        performanceMonitor.resetCounters();
        
        // Start CPU profiling with warmup
        cpuProfiler.startProfiling(true);
        
        // Schedule automatic phase transition
        schedulePhaseTransition(durationMs);
        
        Log.i(TAG, "Baseline measurement started successfully");
    }
    
    /**
     * Transition to optimized measurement phase
     * 
     * @param durationMs Duration to measure optimized performance
     */
    public void startOptimizedMeasurement(long durationMs) {
        if (!isMeasurementActive.get()) {
            Log.w(TAG, "No active measurement to transition");
            return;
        }
        
        if (currentPhase != MeasurementPhase.BASELINE) {
            Log.w(TAG, "Not in baseline phase, cannot transition to optimized");
            return;
        }
        
        Log.i(TAG, "Transitioning to optimized measurement phase for " + durationMs + "ms");
        
        // Capture baseline results
        baselineResults = cpuProfiler.captureBaseline();
        baselineFrames.set(cpuProfiler.getTotalFramesProcessed());
        
        if (baselineResults != null) {
            Log.i(TAG, "Baseline captured: " + baselineResults);
        } else {
            Log.w(TAG, "Failed to capture baseline results");
        }
        
        // Transition to optimized phase
        currentPhase = MeasurementPhase.OPTIMIZED;
        
        // Reset CPU profiler for optimized measurements
        cpuProfiler.stopProfiling();
        cpuProfiler.startProfiling(true);
        
        // Schedule completion
        schedulePhaseTransition(durationMs);
        
        Log.i(TAG, "Optimized measurement phase started");
    }
    
    /**
     * Complete measurement and generate comprehensive results
     * 
     * @return Comprehensive performance results
     */
    @NonNull
    public ComprehensivePerformanceResults completeMeasurement() {
        if (!isMeasurementActive.get()) {
            Log.w(TAG, "No active measurement to complete");
            return createEmptyResults("No active measurement");
        }
        
        Log.i(TAG, "Completing performance measurement and generating results");
        
        // Capture optimized results
        if (currentPhase == MeasurementPhase.OPTIMIZED) {
            optimizedResults = cpuProfiler.getCurrentCpuUsageStats();
            optimizedFrames.set(cpuProfiler.getTotalFramesProcessed());
        }
        
        // Stop profiling
        cpuProfiler.stopProfiling();
        isMeasurementActive.set(false);
        currentPhase = MeasurementPhase.COMPARISON;
        
        // Generate validation results
        validationResults = cpuProfiler.validatePerformanceImprovements();
        
        // Collect component metrics
        CopyOperationTracker.CopyOperationMetrics copyMetrics = 
                copyTracker != null ? copyTracker.getMetrics() : null;
        ZeroCopyProcessor.ZeroCopyPerformanceMetrics zeroCopyMetrics = 
                zeroCopyProcessor != null ? zeroCopyProcessor.getPerformanceMetrics() : null;
        PerformanceMonitor.PerformanceMetrics systemMetrics = performanceMonitor.getCurrentMetrics();
        
        // Generate detailed report
        String detailedReport = generateDetailedReport(copyMetrics, zeroCopyMetrics, systemMetrics);
        
        // Create comprehensive results
        ComprehensivePerformanceResults results = new ComprehensivePerformanceResults(
                baselineResults, optimizedResults, validationResults,
                copyMetrics, zeroCopyMetrics, systemMetrics, detailedReport);
        
        Log.i(TAG, "Performance measurement completed: " + results);
        
        return results;
    }
    
    /**
     * Record frame processing for performance measurement
     * 
     * @param processingTimeMs Total processing time
     * @param framebufferTimeMs Framebuffer operation time
     * @param copyOperations Number of copy operations
     * @param usedOptimizedPath Whether optimized path was used
     */
    public void recordFrameProcessing(long processingTimeMs, long framebufferTimeMs,
                                    int copyOperations, boolean usedOptimizedPath) {
        if (!isMeasurementActive.get() || !cpuProfiler.isWarmupComplete()) {
            return;
        }
        
        totalMeasurementFrames.incrementAndGet();
        
        // Record with CPU profiler
        cpuProfiler.recordFrameProcessing(processingTimeMs, framebufferTimeMs, 
                copyOperations, usedOptimizedPath);
        
        // Record with performance monitor
        performanceMonitor.recordProcessingTime(processingTimeMs);
        
        // Update phase-specific counters
        if (currentPhase == MeasurementPhase.BASELINE) {
            baselineFrames.incrementAndGet();
        } else if (currentPhase == MeasurementPhase.OPTIMIZED) {
            optimizedFrames.incrementAndGet();
        }
        
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, String.format("Frame recorded - Phase: %s, Processing: %dms, Framebuffer: %dms, Copies: %d, Optimized: %s",
                    currentPhase, processingTimeMs, framebufferTimeMs, copyOperations, usedOptimizedPath));
        }
    }
    
    /**
     * Export performance results to file
     * 
     * @param results Performance results to export
     * @param filename Output filename
     * @return true if export successful, false otherwise
     */
    public boolean exportPerformanceResults(@NonNull ComprehensivePerformanceResults results, 
                                          @NonNull String filename) {
        try {
            File outputDir = new File(context.getExternalFilesDir(null), "performance_reports");
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                Log.e(TAG, "Failed to create output directory");
                return false;
            }
            
            File outputFile = new File(outputDir, filename);
            FileWriter writer = new FileWriter(outputFile);
            
            // Write comprehensive report
            writer.write("# OpenCV Camera Stream Performance Measurement Report\n\n");
            writer.write("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "\n\n");
            
            writer.write("## Executive Summary\n\n");
            writer.write("All Performance Targets Met: " + (results.allTargetsMet ? "✓ YES" : "✗ NO") + "\n\n");
            
            if (results.validation != null) {
                writer.write("### Key Metrics\n");
                writer.write("- CPU Reduction: " + String.format("%.1f%%", results.validation.cpuReductionPercent) + "\n");
                writer.write("- Latency Improvement: " + String.format("%.1fms", results.validation.latencyImprovementMs) + "\n");
                writer.write("- Framebuffer CPU Usage: " + String.format("%.1f%%", results.validation.currentFramebufferCpuPercent) + "\n");
                writer.write("- Memory Pressure Reduction: " + String.format("%.1f%%", results.validation.memoryPressureReduction) + "\n\n");
            }
            
            writer.write("## Detailed Results\n\n");
            writer.write(results.detailedReport);
            
            writer.write("\n## Raw Data\n\n");
            writer.write("### Baseline Measurements\n");
            if (results.baseline != null) {
                writer.write(results.baseline.toString() + "\n\n");
            }
            
            writer.write("### Optimized Measurements\n");
            if (results.optimized != null) {
                writer.write(results.optimized.toString() + "\n\n");
            }
            
            writer.write("### Copy Operation Metrics\n");
            if (results.copyMetrics != null) {
                writer.write(results.copyMetrics.toString() + "\n\n");
            }
            
            writer.write("### Zero-Copy Performance Metrics\n");
            if (results.zeroCopyMetrics != null) {
                writer.write(results.zeroCopyMetrics.toString() + "\n\n");
            }
            
            writer.write("### System Performance Metrics\n");
            if (results.systemMetrics != null) {
                writer.write(results.systemMetrics.toString() + "\n\n");
            }
            
            writer.close();
            
            Log.i(TAG, "Performance results exported to: " + outputFile.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to export performance results", e);
            return false;
        }
    }
    
    /**
     * Get current measurement phase
     */
    public MeasurementPhase getCurrentPhase() {
        return currentPhase;
    }
    
    /**
     * Check if measurement is currently active
     */
    public boolean isMeasurementActive() {
        return isMeasurementActive.get();
    }
    
    /**
     * Get total frames measured
     */
    public long getTotalFramesMeasured() {
        return totalMeasurementFrames.get();
    }
    
    /**
     * Get baseline measurement results (if available)
     */
    @Nullable
    public CpuUsageProfiler.CpuUsageBaseline getBaselineResults() {
        return baselineResults;
    }
    
    /**
     * Get optimized measurement results (if available)
     */
    @Nullable
    public CpuUsageProfiler.CpuUsageBaseline getOptimizedResults() {
        return optimizedResults;
    }
    
    /**
     * Get validation results (if available)
     */
    @Nullable
    public CpuUsageProfiler.PerformanceValidationResults getValidationResults() {
        return validationResults;
    }
    
    /**
     * Release resources
     */
    public void release() {
        Log.d(TAG, "Releasing PerformanceMeasurementManager resources");
        
        if (isMeasurementActive.get()) {
            cpuProfiler.stopProfiling();
            isMeasurementActive.set(false);
        }
        
        performanceMonitor.release();
        
        // Clear references
        copyTracker = null;
        zeroCopyProcessor = null;
        frameProcessor = null;
        
        // Clear results
        baselineResults = null;
        optimizedResults = null;
        validationResults = null;
        
        Log.i(TAG, "PerformanceMeasurementManager resources released");
    }
    
    // Private implementation methods
    
    private void schedulePhaseTransition(long durationMs) {
        // Simple implementation - in a real app, you might use a ScheduledExecutorService
        new Thread(() -> {
            try {
                Thread.sleep(durationMs);
                
                if (currentPhase == MeasurementPhase.BASELINE) {
                    // Auto-transition to optimized phase
                    startOptimizedMeasurement(durationMs);
                } else if (currentPhase == MeasurementPhase.OPTIMIZED) {
                    // Auto-complete measurement
                    completeMeasurement();
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Phase transition interrupted");
            }
        }).start();
    }
    
    private String generateDetailedReport(CopyOperationTracker.CopyOperationMetrics copyMetrics,
                                        ZeroCopyProcessor.ZeroCopyPerformanceMetrics zeroCopyMetrics,
                                        PerformanceMonitor.PerformanceMetrics systemMetrics) {
        StringBuilder report = new StringBuilder();
        
        report.append("### Performance Validation Results\n\n");
        if (validationResults != null) {
            report.append(validationResults.validationSummary).append("\n");
            
            report.append("#### Recommendations\n");
            for (String recommendation : validationResults.recommendations) {
                report.append("- ").append(recommendation).append("\n");
            }
            report.append("\n");
        }
        
        report.append("### Copy Operation Analysis\n\n");
        if (copyMetrics != null) {
            report.append("- Total Frames: ").append(copyMetrics.getTotalFrames()).append("\n");
            report.append("- Average Copies per Frame: ").append(String.format("%.1f", copyMetrics.getAverageCopiesPerFrame())).append("\n");
            report.append("- Optimization Status: ").append(copyMetrics.getOptimizationStatus()).append("\n");
            report.append("- Estimated CPU Reduction: ").append(String.format("%.1f%%", copyMetrics.getEstimatedCpuReduction())).append("\n\n");
        }
        
        report.append("### Zero-Copy Optimization Results\n\n");
        if (zeroCopyMetrics != null) {
            report.append("- Total Frames Processed: ").append(zeroCopyMetrics.totalFrames).append("\n");
            report.append("- Average Processing Time: ").append(String.format("%.1fms", zeroCopyMetrics.averageProcessingTimeMs)).append("\n");
            report.append("- CPU Usage Reduction: ").append(String.format("%.1f%%", zeroCopyMetrics.cpuUsageReduction)).append("\n");
            report.append("- Image→Mat Conversions: ").append(zeroCopyMetrics.imageToMatConversions).append("\n");
            report.append("- Mat→Bitmap Conversions: ").append(zeroCopyMetrics.matToBitmapConversions).append("\n\n");
        }
        
        report.append("### System Performance Metrics\n\n");
        if (systemMetrics != null) {
            report.append("- Memory Usage: ").append(String.format("%.1f%%", systemMetrics.memoryUsagePercent)).append("\n");
            report.append("- Average Processing Time: ").append(systemMetrics.averageProcessingTimeMs).append("ms\n");
            report.append("- Performance Level: ").append(systemMetrics.currentLevel).append("\n");
            report.append("- Low Memory Device: ").append(systemMetrics.isLowMemoryDevice).append("\n\n");
        }
        
        report.append("### Measurement Summary\n\n");
        report.append("- Total Measurement Frames: ").append(totalMeasurementFrames.get()).append("\n");
        report.append("- Baseline Frames: ").append(baselineFrames.get()).append("\n");
        report.append("- Optimized Frames: ").append(optimizedFrames.get()).append("\n");
        report.append("- Measurement Duration: ").append(System.currentTimeMillis() - measurementStartTime.get()).append("ms\n");
        
        return report.toString();
    }
    
    private ComprehensivePerformanceResults createEmptyResults(String reason) {
        return new ComprehensivePerformanceResults(
                null, null, null, null, null, null,
                "Performance measurement failed: " + reason);
    }
}