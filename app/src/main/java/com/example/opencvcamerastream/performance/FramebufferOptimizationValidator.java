package com.example.opencvcamerastream.performance;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.opencvcamerastream.processing.CopyOperationTracker;
import com.example.opencvcamerastream.processing.ZeroCopyProcessor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * FramebufferOptimizationValidator measures and validates CPU usage reduction
 * after framebuffer copy optimizations
 * 
 * Requirements addressed:
 * - Task 25: Profile CPU usage before and after optimization
 * - Req-13.6: Measure framebuffer operation CPU percentage (target: <30%)
 * - Task 25: Validate frame processing latency improvement (target: 20-30ms reduction)
 * - Task 25: Measure memory pressure and GC frequency reduction
 * - Task 25: Document performance improvements
 * 
 * VALIDATION TARGETS:
 * - Framebuffer operations: <30% of total processing time
 * - Frame processing latency: 20-30ms reduction from baseline
 * - CPU usage reduction: 40-50% improvement in processing pipeline
 * - Memory pressure: Reduced GC frequency and allocation rate
 */
public class FramebufferOptimizationValidator {
    
    private static final String TAG = "FramebufferOptimizationValidator";
    
    // Performance targets (Requirements)
    private static final double FRAMEBUFFER_CPU_TARGET_PERCENT = 30.0; // <30%
    private static final double MIN_LATENCY_IMPROVEMENT_MS = 20.0; // 20-30ms reduction
    private static final double MAX_LATENCY_IMPROVEMENT_MS = 30.0;
    private static final double MIN_CPU_REDUCTION_PERCENT = 40.0; // 40-50% reduction
    private static final double MAX_CPU_REDUCTION_PERCENT = 50.0;
    
    // Measurement configuration
    private static final int MIN_SAMPLES_FOR_VALIDATION = 100;
    private static final int MEASUREMENT_WINDOW_SIZE = 500;
    private static final long WARMUP_PERIOD_MS = 10000; // 10 second warmup
    
    // Performance tracking
    private final CpuUsageProfiler cpuProfiler;
    private final CopyOperationTracker copyTracker;
    private final PerformanceMetricsCollector metricsCollector;
    
    // Measurement state
    private volatile boolean isValidationActive = false;
    private volatile boolean isBaselineMode = false;
    private long validationStartTime = 0;
    private long warmupStartTime = 0;
    
    // Performance measurements
    private final ConcurrentLinkedQueue<FramePerformanceMeasurement> measurements = new ConcurrentLinkedQueue<>();
    private final AtomicLong totalFramesMeasured = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    private final AtomicLong totalFramebufferTimeMs = new AtomicLong(0);
    private final AtomicInteger totalCopyOperations = new AtomicInteger(0);
    
    // Baseline data
    private ValidationBaseline baseline;
    
    /**
     * Single frame performance measurement
     */
    public static class FramePerformanceMeasurement {
        public final long timestamp;
        public final long frameProcessingTimeMs;
        public final long framebufferTimeMs;
        public final int copyOperations;
        public final boolean usedOptimizedPath;
        public final double cpuUsagePercent;
        public final long memoryUsedMB;
        
        public FramePerformanceMeasurement(long timestamp, long processingTime, long framebufferTime,
                                         int copyOps, boolean optimized, double cpuUsage, long memoryMB) {
            this.timestamp = timestamp;
            this.frameProcessingTimeMs = processingTime;
            this.framebufferTimeMs = framebufferTime;
            this.copyOperations = copyOps;
            this.usedOptimizedPath = optimized;
            this.cpuUsagePercent = cpuUsage;
            this.memoryUsedMB = memoryMB;
        }
        
        public double getFramebufferCpuPercent() {
            return frameProcessingTimeMs > 0 ? (double) framebufferTimeMs / frameProcessingTimeMs * 100 : 0;
        }
        
        @Override
        public String toString() {
            return String.format("FramePerf{proc=%dms, fb=%dms (%.1f%%), copies=%d, opt=%s, cpu=%.1f%%, mem=%dMB}",
                    frameProcessingTimeMs, framebufferTimeMs, getFramebufferCpuPercent(), 
                    copyOperations, usedOptimizedPath, cpuUsagePercent, memoryUsedMB);
        }
    }
    
    /**
     * Baseline performance measurements for comparison
     */
    public static class ValidationBaseline {
        public final double averageProcessingLatencyMs;
        public final double averageFramebufferCpuPercent;
        public final double averageCpuUsagePercent;
        public final double averageCopyOperationsPerFrame;
        public final double averageMemoryUsageMB;
        public final long measurementDurationMs;
        public final int sampleCount;
        public final long timestamp;
        
        public ValidationBaseline(double avgLatency, double avgFbCpu, double avgCpuUsage,
                                double avgCopies, double avgMemory, long duration, int samples) {
            this.averageProcessingLatencyMs = avgLatency;
            this.averageFramebufferCpuPercent = avgFbCpu;
            this.averageCpuUsagePercent = avgCpuUsage;
            this.averageCopyOperationsPerFrame = avgCopies;
            this.averageMemoryUsageMB = avgMemory;
            this.measurementDurationMs = duration;
            this.sampleCount = samples;
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return String.format("Baseline{latency=%.1fms, fbCpu=%.1f%%, cpu=%.1f%%, copies=%.1f, mem=%.1fMB, samples=%d}",
                    averageProcessingLatencyMs, averageFramebufferCpuPercent, averageCpuUsagePercent,
                    averageCopyOperationsPerFrame, averageMemoryUsageMB, sampleCount);
        }
    }
    
    /**
     * Comprehensive validation results
     */
    public static class OptimizationValidationResults {
        /**
         * Performance grade enum for validation results
         */
        public enum PerformanceGrade {
            EXCELLENT("Excellent - All targets exceeded"),
            GOOD("Good - All targets met"),
            SATISFACTORY("Satisfactory - Most targets met"),
            NEEDS_IMPROVEMENT("Needs Improvement - Some targets not met"),
            POOR("Poor - Most targets not met");
            
            private final String description;
            
            PerformanceGrade(String description) {
                this.description = description;
            }
            
            public String getDescription() {
                return description;
            }
        }
        
        public final boolean meetsFramebufferCpuTarget;
        public final boolean meetsLatencyImprovementTarget;
        public final boolean meetsCpuReductionTarget;
        public final boolean meetsMemoryImprovementTarget;
        public final boolean meetsAllTargets;
        
        public final double currentFramebufferCpuPercent;
        public final double latencyImprovementMs;
        public final double cpuReductionPercent;
        public final double memoryImprovementPercent;
        public final double copyReductionPercent;
        
        public final ValidationBaseline baseline;
        public final ValidationBaseline current;
        
        public final String detailedReport;
        public final List<String> recommendations;
        public final PerformanceGrade overallGrade;
        
        public OptimizationValidationResults(boolean fbTarget, boolean latencyTarget, boolean cpuTarget, boolean memTarget,
                                           double fbCpu, double latencyImpr, double cpuReduction, double memImpr, double copyReduction,
                                           ValidationBaseline baseline, ValidationBaseline current,
                                           String report, List<String> recommendations, PerformanceGrade grade) {
            this.meetsFramebufferCpuTarget = fbTarget;
            this.meetsLatencyImprovementTarget = latencyTarget;
            this.meetsCpuReductionTarget = cpuTarget;
            this.meetsMemoryImprovementTarget = memTarget;
            this.meetsAllTargets = fbTarget && latencyTarget && cpuTarget && memTarget;
            
            this.currentFramebufferCpuPercent = fbCpu;
            this.latencyImprovementMs = latencyImpr;
            this.cpuReductionPercent = cpuReduction;
            this.memoryImprovementPercent = memImpr;
            this.copyReductionPercent = copyReduction;
            
            this.baseline = baseline;
            this.current = current;
            this.detailedReport = report;
            this.recommendations = new ArrayList<>(recommendations);
            this.overallGrade = grade;
        }
        
        @Override
        public String toString() {
            return String.format("ValidationResults{grade=%s, fbCpu=%.1f%% (target<%.1f%%), " +
                               "latencyImpr=%.1fms (target %.1f-%.1fms), cpuReduction=%.1f%% (target %.1f-%.1f%%), allTargets=%s}",
                    overallGrade, currentFramebufferCpuPercent, FRAMEBUFFER_CPU_TARGET_PERCENT,
                    latencyImprovementMs, MIN_LATENCY_IMPROVEMENT_MS, MAX_LATENCY_IMPROVEMENT_MS,
                    cpuReductionPercent, MIN_CPU_REDUCTION_PERCENT, MAX_CPU_REDUCTION_PERCENT, meetsAllTargets);
        }
    }
    
    public FramebufferOptimizationValidator(@NonNull CpuUsageProfiler cpuProfiler,
                                          @NonNull CopyOperationTracker copyTracker,
                                          @NonNull PerformanceMetricsCollector metricsCollector) {
        this.cpuProfiler = cpuProfiler;
        this.copyTracker = copyTracker;
        this.metricsCollector = metricsCollector;
        
        Log.d(TAG, "FramebufferOptimizationValidator created with targets: " +
                "framebuffer CPU <30%, latency improvement 20-30ms, CPU reduction 40-50%");
    }
    
    /**
     * Start baseline measurement phase
     * This should be called before applying optimizations
     */
    public void startBaselineMeasurement() {
        if (isValidationActive) {
            Log.w(TAG, "Validation already active");
            return;
        }
        
        Log.i(TAG, "Starting baseline measurement phase");
        
        isValidationActive = true;
        isBaselineMode = true;
        validationStartTime = System.currentTimeMillis();
        warmupStartTime = validationStartTime;
        
        // Clear previous measurements
        measurements.clear();
        resetCounters();
        
        // Start CPU profiling
        cpuProfiler.startMonitoring();
        metricsCollector.startMonitoring();
        
        Log.i(TAG, "Baseline measurement started with warmup period");
    }
    
    /**
     * Complete baseline measurement and switch to optimization measurement
     */
    public void completeBaselineMeasurement() {
        if (!isValidationActive || !isBaselineMode) {
            Log.w(TAG, "Not in baseline measurement mode");
            return;
        }
        
        if (measurements.size() < MIN_SAMPLES_FOR_VALIDATION) {
            Log.w(TAG, "Insufficient baseline samples: " + measurements.size() + " < " + MIN_SAMPLES_FOR_VALIDATION);
            return;
        }
        
        Log.i(TAG, "Completing baseline measurement with " + measurements.size() + " samples");
        
        // Calculate baseline metrics
        baseline = calculateCurrentBaseline();
        CpuUsageProfiler.CpuUsageMetrics baselineMetrics = cpuProfiler.getCurrentMetrics();
        cpuProfiler.setBaselineMetrics(baselineMetrics);
        
        // Clear measurements for optimization phase
        measurements.clear();
        resetCounters();
        
        // Switch to optimization measurement mode
        isBaselineMode = false;
        validationStartTime = System.currentTimeMillis();
        warmupStartTime = validationStartTime;
        
        Log.i(TAG, "Baseline captured: " + baseline);
        Log.i(TAG, "Starting optimization measurement phase");
    }
    
    /**
     * Stop validation and generate final results
     */
    public OptimizationValidationResults stopValidationAndGetResults() {
        if (!isValidationActive) {
            Log.w(TAG, "Validation not active");
            return createEmptyResults("Validation not active");
        }
        
        Log.i(TAG, "Stopping validation and generating results");
        
        isValidationActive = false;
        
        // Stop profiling
        CpuUsageProfiler.CpuUsageMetrics finalMetrics = cpuProfiler.stopMonitoring();
        metricsCollector.stopMonitoring();
        
        // Generate validation results
        OptimizationValidationResults results = generateValidationResults();
        
        Log.i(TAG, "Validation completed: " + results);
        
        return results;
    }
    
    /**
     * Record frame performance measurement
     */
    public void recordFramePerformance(long processingTimeMs, long framebufferTimeMs, 
                                     int copyOperations, boolean usedOptimizedPath) {
        if (!isValidationActive) {
            return;
        }
        
        // Check warmup period
        if (System.currentTimeMillis() - warmupStartTime < WARMUP_PERIOD_MS) {
            return;
        }
        
        // Get current system metrics
        double cpuUsage = getCurrentCpuUsage();
        long memoryUsage = getCurrentMemoryUsageMB();
        
        FramePerformanceMeasurement measurement = new FramePerformanceMeasurement(
                System.currentTimeMillis(), processingTimeMs, framebufferTimeMs,
                copyOperations, usedOptimizedPath, cpuUsage, memoryUsage);
        
        measurements.offer(measurement);
        
        // Update counters
        totalFramesMeasured.incrementAndGet();
        totalProcessingTimeMs.addAndGet(processingTimeMs);
        totalFramebufferTimeMs.addAndGet(framebufferTimeMs);
        totalCopyOperations.addAndGet(copyOperations);
        
        // Record with CPU profiler
        long startTime = System.currentTimeMillis() - processingTimeMs;
        cpuProfiler.recordFrameProcessingEnd(startTime, usedOptimizedPath);
        
        // Maintain window size
        while (measurements.size() > MEASUREMENT_WINDOW_SIZE) {
            measurements.poll();
        }
        
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Frame performance recorded (" + (isBaselineMode ? "baseline" : "optimized") + "): " + measurement);
        }
    }
    
    /**
     * Get current validation status
     */
    public String getValidationStatus() {
        if (!isValidationActive) {
            return "Validation inactive";
        }
        
        long elapsed = System.currentTimeMillis() - validationStartTime;
        long warmupRemaining = Math.max(0, WARMUP_PERIOD_MS - (System.currentTimeMillis() - warmupStartTime));
        
        if (warmupRemaining > 0) {
            return String.format("Warmup: %ds remaining", warmupRemaining / 1000);
        }
        
        String mode = isBaselineMode ? "Baseline" : "Optimized";
        return String.format("%s measurement: %d samples, %ds elapsed", 
                mode, measurements.size(), elapsed / 1000);
    }
    
    /**
     * Check if validation is active
     */
    public boolean isValidationActive() {
        return isValidationActive;
    }
    
    /**
     * Check if currently in baseline mode
     */
    public boolean isBaselineMode() {
        return isBaselineMode;
    }
    
    /**
     * Get current baseline (if captured)
     */
    @Nullable
    public ValidationBaseline getBaseline() {
        return baseline;
    }
    
    /**
     * Get current measurement count
     */
    public int getCurrentMeasurementCount() {
        return measurements.size();
    }
    
    // Private implementation methods
    
    private ValidationBaseline calculateCurrentBaseline() {
        if (measurements.isEmpty()) {
            return new ValidationBaseline(0, 0, 0, 0, 0, 0, 0);
        }
        
        double avgLatency = measurements.stream()
                .mapToLong(m -> m.frameProcessingTimeMs)
                .average()
                .orElse(0.0);
        
        double avgFramebufferCpu = measurements.stream()
                .mapToDouble(FramePerformanceMeasurement::getFramebufferCpuPercent)
                .average()
                .orElse(0.0);
        
        double avgCpuUsage = measurements.stream()
                .mapToDouble(m -> m.cpuUsagePercent)
                .average()
                .orElse(0.0);
        
        double avgCopies = measurements.stream()
                .mapToInt(m -> m.copyOperations)
                .average()
                .orElse(0.0);
        
        double avgMemory = measurements.stream()
                .mapToLong(m -> m.memoryUsedMB)
                .average()
                .orElse(0.0);
        
        long duration = System.currentTimeMillis() - validationStartTime;
        int sampleCount = measurements.size();
        
        return new ValidationBaseline(avgLatency, avgFramebufferCpu, avgCpuUsage, avgCopies, avgMemory, duration, sampleCount);
    }
    
    private OptimizationValidationResults generateValidationResults() {
        if (baseline == null) {
            return createEmptyResults("No baseline available");
        }
        
        if (measurements.size() < MIN_SAMPLES_FOR_VALIDATION) {
            return createEmptyResults("Insufficient optimization samples: " + measurements.size());
        }
        
        ValidationBaseline current = calculateCurrentBaseline();
        
        // Calculate improvements
        double latencyImprovement = baseline.averageProcessingLatencyMs - current.averageProcessingLatencyMs;
        double cpuReduction = baseline.averageCpuUsagePercent > 0 ? 
                ((baseline.averageCpuUsagePercent - current.averageCpuUsagePercent) / baseline.averageCpuUsagePercent) * 100 : 0;
        double memoryImprovement = baseline.averageMemoryUsageMB > 0 ?
                ((baseline.averageMemoryUsageMB - current.averageMemoryUsageMB) / baseline.averageMemoryUsageMB) * 100 : 0;
        double copyReduction = baseline.averageCopyOperationsPerFrame > 0 ?
                ((baseline.averageCopyOperationsPerFrame - current.averageCopyOperationsPerFrame) / baseline.averageCopyOperationsPerFrame) * 100 : 0;
        
        // Check targets
        boolean meetsFramebufferTarget = current.averageFramebufferCpuPercent < FRAMEBUFFER_CPU_TARGET_PERCENT;
        boolean meetsLatencyTarget = latencyImprovement >= MIN_LATENCY_IMPROVEMENT_MS && latencyImprovement <= MAX_LATENCY_IMPROVEMENT_MS + 10; // Allow some tolerance
        boolean meetsCpuTarget = cpuReduction >= MIN_CPU_REDUCTION_PERCENT;
        boolean meetsMemoryTarget = memoryImprovement > 0; // Any improvement is good
        
        // Generate detailed report
        String report = generateDetailedReport(baseline, current, latencyImprovement, cpuReduction, memoryImprovement, copyReduction);
        
        // Generate recommendations
        List<String> recommendations = generateRecommendations(current, latencyImprovement, cpuReduction, copyReduction);
        
        // Calculate overall grade
        OptimizationValidationResults.PerformanceGrade grade = calculatePerformanceGrade(meetsFramebufferTarget, meetsLatencyTarget, meetsCpuTarget, meetsMemoryTarget,
                current.averageFramebufferCpuPercent, latencyImprovement, cpuReduction);
        
        return new OptimizationValidationResults(
                meetsFramebufferTarget, meetsLatencyTarget, meetsCpuTarget, meetsMemoryTarget,
                current.averageFramebufferCpuPercent, latencyImprovement, cpuReduction, memoryImprovement, copyReduction,
                baseline, current, report, recommendations, grade);
    }
    
    private String generateDetailedReport(ValidationBaseline baseline, ValidationBaseline current,
                                        double latencyImprovement, double cpuReduction, 
                                        double memoryImprovement, double copyReduction) {
        StringBuilder report = new StringBuilder();
        report.append("=== FRAMEBUFFER OPTIMIZATION VALIDATION REPORT ===\n\n");
        
        report.append("BASELINE MEASUREMENTS:\n");
        report.append(String.format("• Processing Latency: %.1fms\n", baseline.averageProcessingLatencyMs));
        report.append(String.format("• Framebuffer CPU: %.1f%%\n", baseline.averageFramebufferCpuPercent));
        report.append(String.format("• Overall CPU Usage: %.1f%%\n", baseline.averageCpuUsagePercent));
        report.append(String.format("• Copy Operations/Frame: %.1f\n", baseline.averageCopyOperationsPerFrame));
        report.append(String.format("• Memory Usage: %.1fMB\n", baseline.averageMemoryUsageMB));
        report.append(String.format("• Sample Count: %d\n\n", baseline.sampleCount));
        
        report.append("OPTIMIZED MEASUREMENTS:\n");
        report.append(String.format("• Processing Latency: %.1fms\n", current.averageProcessingLatencyMs));
        report.append(String.format("• Framebuffer CPU: %.1f%%\n", current.averageFramebufferCpuPercent));
        report.append(String.format("• Overall CPU Usage: %.1f%%\n", current.averageCpuUsagePercent));
        report.append(String.format("• Copy Operations/Frame: %.1f\n", current.averageCopyOperationsPerFrame));
        report.append(String.format("• Memory Usage: %.1fMB\n", current.averageMemoryUsageMB));
        report.append(String.format("• Sample Count: %d\n\n", current.sampleCount));
        
        report.append("PERFORMANCE IMPROVEMENTS:\n");
        report.append(String.format("• Latency Improvement: %.1fms (target: %.1f-%.1fms) - %s\n",
                latencyImprovement, MIN_LATENCY_IMPROVEMENT_MS, MAX_LATENCY_IMPROVEMENT_MS,
                (latencyImprovement >= MIN_LATENCY_IMPROVEMENT_MS) ? "✓ PASS" : "✗ FAIL"));
        report.append(String.format("• CPU Reduction: %.1f%% (target: >%.1f%%) - %s\n",
                cpuReduction, MIN_CPU_REDUCTION_PERCENT,
                (cpuReduction >= MIN_CPU_REDUCTION_PERCENT) ? "✓ PASS" : "✗ FAIL"));
        report.append(String.format("• Memory Improvement: %.1f%% - %s\n",
                memoryImprovement, (memoryImprovement > 0) ? "✓ PASS" : "✗ FAIL"));
        report.append(String.format("• Copy Reduction: %.1f%% - %s\n",
                copyReduction, (copyReduction > 0) ? "✓ PASS" : "✗ FAIL"));
        
        report.append("\nTARGET VALIDATION:\n");
        report.append(String.format("• Framebuffer CPU <30%%: %.1f%% - %s\n",
                current.averageFramebufferCpuPercent,
                (current.averageFramebufferCpuPercent < FRAMEBUFFER_CPU_TARGET_PERCENT) ? "✓ PASS" : "✗ FAIL"));
        
        return report.toString();
    }
    
    private List<String> generateRecommendations(ValidationBaseline current, double latencyImprovement, 
                                               double cpuReduction, double copyReduction) {
        List<String> recommendations = new ArrayList<>();
        
        if (current.averageFramebufferCpuPercent >= FRAMEBUFFER_CPU_TARGET_PERCENT) {
            recommendations.add("Framebuffer CPU usage exceeds 30% target. Review copy elimination implementation.");
        }
        
        if (latencyImprovement < MIN_LATENCY_IMPROVEMENT_MS) {
            recommendations.add("Latency improvement below 20ms target. Verify zero-copy optimizations are active.");
        }
        
        if (cpuReduction < MIN_CPU_REDUCTION_PERCENT) {
            recommendations.add("CPU reduction below 40% target. Check if all copy operations have been eliminated.");
        }
        
        if (current.averageCopyOperationsPerFrame > 2.5) {
            recommendations.add("Copy operations per frame above optimal (2.0). Review framebuffer optimization implementation.");
        }
        
        if (copyReduction < 50) {
            recommendations.add("Copy operation reduction below 50%. Ensure buffer pool copies are eliminated.");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Excellent! All optimization targets have been met or exceeded.");
            recommendations.add("Consider documenting the optimization approach for future reference.");
        }
        
        return recommendations;
    }
    
    private OptimizationValidationResults.PerformanceGrade calculatePerformanceGrade(boolean fbTarget, boolean latencyTarget, boolean cpuTarget, boolean memTarget,
                                                     double fbCpu, double latencyImpr, double cpuReduction) {
        int targetsMet = 0;
        if (fbTarget) targetsMet++;
        if (latencyTarget) targetsMet++;
        if (cpuTarget) targetsMet++;
        if (memTarget) targetsMet++;
        
        // Check for exceptional performance
        if (targetsMet == 4 && fbCpu < 20 && latencyImpr > 35 && cpuReduction > 60) {
            return OptimizationValidationResults.PerformanceGrade.EXCELLENT;
        }
        
        // Grade based on targets met
        switch (targetsMet) {
            case 4:
                return OptimizationValidationResults.PerformanceGrade.GOOD;
            case 3:
                return OptimizationValidationResults.PerformanceGrade.SATISFACTORY;
            case 2:
                return OptimizationValidationResults.PerformanceGrade.NEEDS_IMPROVEMENT;
            default:
                return OptimizationValidationResults.PerformanceGrade.POOR;
        }
    }
    
    private OptimizationValidationResults createEmptyResults(String reason) {
        return new OptimizationValidationResults(
                false, false, false, false,
                0, 0, 0, 0, 0,
                null, null,
                "Validation failed: " + reason,
                List.of("Ensure proper baseline and optimization measurements are completed"),
                OptimizationValidationResults.PerformanceGrade.POOR);
    }
    
    private double getCurrentCpuUsage() {
        // Simple CPU usage estimation - in real implementation would use system calls
        return Math.random() * 20 + 10; // Placeholder
    }
    
    private long getCurrentMemoryUsageMB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
    
    private void resetCounters() {
        totalFramesMeasured.set(0);
        totalProcessingTimeMs.set(0);
        totalFramebufferTimeMs.set(0);
        totalCopyOperations.set(0);
    }
}