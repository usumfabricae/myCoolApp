package com.example.opencvcamerastream.performance;

import android.os.Debug;
import android.os.Process;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * CpuUsageProfiler measures and validates CPU usage reduction from framebuffer optimizations
 * 
 * Requirements addressed:
 * - Req-13.6: Measure framebuffer operation CPU percentage (target: <30%)
 * - Task 25: Profile CPU usage before and after optimization
 * - Task 25: Validate frame processing latency improvement (target: 20-30ms reduction)
 * - Task 25: Measure memory pressure and GC frequency reduction
 * 
 * MEASUREMENT STRATEGY:
 * - Continuous CPU usage monitoring during frame processing
 * - Separate tracking of framebuffer operations vs total processing
 * - Memory pressure monitoring through GC frequency tracking
 * - Frame processing latency measurement with statistical analysis
 * - Before/after optimization comparison with detailed metrics
 */
public class CpuUsageProfiler {
    
    private static final String TAG = "CpuUsageProfiler";
    
    // CPU measurement configuration
    private static final int CPU_SAMPLE_INTERVAL_MS = 100; // Sample every 100ms
    private static final int MEASUREMENT_WINDOW_MS = 5000; // 5-second measurement windows
    private static final int MAX_SAMPLES_PER_WINDOW = MEASUREMENT_WINDOW_MS / CPU_SAMPLE_INTERVAL_MS;
    
    // Performance targets from requirements
    private static final double TARGET_FRAMEBUFFER_CPU_PERCENTAGE = 30.0; // <30% target
    private static final long TARGET_LATENCY_REDUCTION_MS = 25; // 20-30ms reduction target
    private static final double TARGET_TOTAL_CPU_REDUCTION = 40.0; // 40-50% reduction target
    
    // CPU usage tracking
    private final AtomicLong totalCpuTimeUs = new AtomicLong(0);
    private final AtomicLong framebufferCpuTimeUs = new AtomicLong(0);
    private final AtomicLong measurementStartTime = new AtomicLong(0);
    private final AtomicLong lastCpuMeasurement = new AtomicLong(0);
    
    // Frame processing latency tracking
    private final AtomicLong totalFrameLatency = new AtomicLong(0);
    private final AtomicLong frameCount = new AtomicLong(0);
    private final AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxLatency = new AtomicLong(0);
    
    // Memory pressure tracking
    private final AtomicLong gcCount = new AtomicLong(0);
    private final AtomicLong totalMemoryAllocated = new AtomicLong(0);
    private final AtomicLong peakMemoryUsage = new AtomicLong(0);
    
    // Baseline measurements (before optimization)
    private final AtomicReference<CpuUsageMetrics> baselineMetrics = new AtomicReference<>();
    
    // Monitoring state
    private volatile boolean isMonitoring = false;
    private ScheduledExecutorService cpuMonitorExecutor;
    private CpuUsageCallback callback;
    
    /**
     * Callback interface for CPU usage updates
     */
    public interface CpuUsageCallback {
        void onCpuUsageUpdate(@NonNull CpuUsageMetrics metrics);
        void onOptimizationValidated(@NonNull OptimizationResults results);
        void onPerformanceAlert(@NonNull String alert, @NonNull CpuUsageMetrics metrics);
    }
    
    /**
     * Comprehensive CPU usage metrics
     */
    public static class CpuUsageMetrics {
        public final long measurementDurationMs;
        public final double totalCpuPercentage;
        public final double framebufferCpuPercentage;
        public final double averageFrameLatencyMs;
        public final long minFrameLatencyMs;
        public final long maxFrameLatencyMs;
        public final long totalFrames;
        public final double gcFrequencyPerSecond;
        public final long totalMemoryMB;
        public final long peakMemoryMB;
        public final long timestamp;
        
        public CpuUsageMetrics(long duration, double totalCpu, double framebufferCpu,
                             double avgLatency, long minLatency, long maxLatency,
                             long frames, double gcFreq, long totalMem, long peakMem) {
            this.measurementDurationMs = duration;
            this.totalCpuPercentage = totalCpu;
            this.framebufferCpuPercentage = framebufferCpu;
            this.averageFrameLatencyMs = avgLatency;
            this.minFrameLatencyMs = minLatency;
            this.maxFrameLatencyMs = maxLatency;
            this.totalFrames = frames;
            this.gcFrequencyPerSecond = gcFreq;
            this.totalMemoryMB = totalMem;
            this.peakMemoryMB = peakMem;
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean meetsFramebufferTarget() {
            return framebufferCpuPercentage <= TARGET_FRAMEBUFFER_CPU_PERCENTAGE;
        }
        
        public boolean meetsLatencyTarget(CpuUsageMetrics baseline) {
            if (baseline == null) return false;
            double improvement = baseline.averageFrameLatencyMs - this.averageFrameLatencyMs;
            return improvement >= TARGET_LATENCY_REDUCTION_MS;
        }
        
        public boolean meetsCpuReductionTarget(CpuUsageMetrics baseline) {
            if (baseline == null) return false;
            double reduction = ((baseline.totalCpuPercentage - this.totalCpuPercentage) / baseline.totalCpuPercentage) * 100;
            return reduction >= TARGET_TOTAL_CPU_REDUCTION;
        }
        
        @Override
        public String toString() {
            return String.format("CpuMetrics{totalCpu=%.1f%%, framebufferCpu=%.1f%%, " +
                    "avgLatency=%.1fms, frames=%d, gcFreq=%.2f/s, memory=%dMB}",
                    totalCpuPercentage, framebufferCpuPercentage, averageFrameLatencyMs,
                    totalFrames, gcFrequencyPerSecond, totalMemoryMB);
        }
    }
    
    /**
     * Optimization validation results
     */
    public static class OptimizationResults {
        public final CpuUsageMetrics baseline;
        public final CpuUsageMetrics optimized;
        public final double cpuReductionPercentage;
        public final double latencyImprovementMs;
        public final double memoryReductionPercentage;
        public final double gcFrequencyReduction;
        public final boolean meetsAllTargets;
        public final String validationSummary;
        
        public OptimizationResults(@NonNull CpuUsageMetrics baseline, @NonNull CpuUsageMetrics optimized) {
            this.baseline = baseline;
            this.optimized = optimized;
            
            // Calculate improvements
            this.cpuReductionPercentage = ((baseline.totalCpuPercentage - optimized.totalCpuPercentage) / baseline.totalCpuPercentage) * 100;
            this.latencyImprovementMs = baseline.averageFrameLatencyMs - optimized.averageFrameLatencyMs;
            this.memoryReductionPercentage = ((baseline.totalMemoryMB - optimized.totalMemoryMB) / (double) baseline.totalMemoryMB) * 100;
            this.gcFrequencyReduction = baseline.gcFrequencyPerSecond - optimized.gcFrequencyPerSecond;
            
            // Check if all targets are met
            boolean framebufferTarget = optimized.meetsFramebufferTarget();
            boolean latencyTarget = optimized.meetsLatencyTarget(baseline);
            boolean cpuTarget = optimized.meetsCpuReductionTarget(baseline);
            this.meetsAllTargets = framebufferTarget && latencyTarget && cpuTarget;
            
            // Generate validation summary
            this.validationSummary = generateValidationSummary(framebufferTarget, latencyTarget, cpuTarget);
        }
        
        private String generateValidationSummary(boolean framebufferTarget, boolean latencyTarget, boolean cpuTarget) {
            StringBuilder summary = new StringBuilder();
            summary.append("Optimization Validation Results:\n");
            summary.append(String.format("• CPU Reduction: %.1f%% %s (target: %.1f%%)\n", 
                    cpuReductionPercentage, cpuTarget ? "✓" : "✗", TARGET_TOTAL_CPU_REDUCTION));
            summary.append(String.format("• Latency Improvement: %.1fms %s (target: %dms)\n", 
                    latencyImprovementMs, latencyTarget ? "✓" : "✗", TARGET_LATENCY_REDUCTION_MS));
            summary.append(String.format("• Framebuffer CPU: %.1f%% %s (target: <%.1f%%)\n", 
                    optimized.framebufferCpuPercentage, framebufferTarget ? "✓" : "✗", TARGET_FRAMEBUFFER_CPU_PERCENTAGE));
            summary.append(String.format("• Memory Reduction: %.1f%%\n", memoryReductionPercentage));
            summary.append(String.format("• GC Frequency Reduction: %.2f/s\n", gcFrequencyReduction));
            summary.append(String.format("Overall: %s", meetsAllTargets ? "ALL TARGETS MET ✓" : "TARGETS NOT MET ✗"));
            return summary.toString();
        }
        
        @Override
        public String toString() {
            return String.format("OptimizationResults{cpuReduction=%.1f%%, latencyImprovement=%.1fms, " +
                    "memoryReduction=%.1f%%, targetsMetr=%s}",
                    cpuReductionPercentage, latencyImprovementMs, memoryReductionPercentage, meetsAllTargets);
        }
    }
    
    public CpuUsageProfiler() {
        Log.d(TAG, "CpuUsageProfiler initialized with targets: " +
                "framebuffer CPU <" + TARGET_FRAMEBUFFER_CPU_PERCENTAGE + "%, " +
                "latency reduction " + TARGET_LATENCY_REDUCTION_MS + "ms, " +
                "total CPU reduction " + TARGET_TOTAL_CPU_REDUCTION + "%");
    }
    
    /**
     * Set callback for CPU usage updates
     */
    public void setCallback(@Nullable CpuUsageCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Start CPU usage monitoring
     */
    public void startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "CPU monitoring already started");
            return;
        }
        
        Log.d(TAG, "Starting CPU usage monitoring");
        isMonitoring = true;
        measurementStartTime.set(System.currentTimeMillis());
        
        // Reset counters
        resetCounters();
        
        // Start CPU monitoring thread
        cpuMonitorExecutor = Executors.newSingleThreadScheduledExecutor();
        cpuMonitorExecutor.scheduleAtFixedRate(this::sampleCpuUsage, 
                0, CPU_SAMPLE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        Log.i(TAG, "CPU usage monitoring started");
    }
    
    /**
     * Stop CPU usage monitoring and return final metrics
     */
    public CpuUsageMetrics stopMonitoring() {
        if (!isMonitoring) {
            Log.w(TAG, "CPU monitoring not started");
            return null;
        }
        
        Log.d(TAG, "Stopping CPU usage monitoring");
        isMonitoring = false;
        
        if (cpuMonitorExecutor != null) {
            cpuMonitorExecutor.shutdown();
            try {
                if (!cpuMonitorExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    cpuMonitorExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cpuMonitorExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            cpuMonitorExecutor = null;
        }
        
        CpuUsageMetrics finalMetrics = getCurrentMetrics();
        Log.i(TAG, "CPU usage monitoring stopped. Final metrics: " + finalMetrics);
        
        return finalMetrics;
    }
    
    /**
     * Record frame processing start time
     */
    public void recordFrameProcessingStart() {
        // This will be called by the frame processor to mark the start of processing
        // The actual timing is handled in recordFrameProcessingEnd
    }
    
    /**
     * Record frame processing completion with latency
     */
    public void recordFrameProcessingEnd(long processingStartTime, boolean isFramebufferOperation) {
        long latency = System.currentTimeMillis() - processingStartTime;
        
        frameCount.incrementAndGet();
        totalFrameLatency.addAndGet(latency);
        
        // Update min/max latency
        updateMinLatency(latency);
        updateMaxLatency(latency);
        
        // If this was a framebuffer operation, add to framebuffer CPU time
        if (isFramebufferOperation) {
            framebufferCpuTimeUs.addAndGet(latency * 1000); // Convert to microseconds
        }
        
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Frame processing recorded: " + latency + "ms" + 
                    (isFramebufferOperation ? " (framebuffer)" : ""));
        }
    }
    
    /**
     * Record memory allocation for tracking memory pressure
     */
    public void recordMemoryAllocation(long bytes) {
        totalMemoryAllocated.addAndGet(bytes);
        
        // Update peak memory usage
        long currentMemory = getCurrentMemoryUsage();
        updatePeakMemory(currentMemory);
    }
    
    /**
     * Record garbage collection event
     */
    public void recordGarbageCollection() {
        gcCount.incrementAndGet();
        
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "GC event recorded, total: " + gcCount.get());
        }
    }
    
    /**
     * Set baseline metrics for comparison (before optimization)
     */
    public void setBaselineMetrics(@NonNull CpuUsageMetrics baseline) {
        baselineMetrics.set(baseline);
        Log.i(TAG, "Baseline metrics set: " + baseline);
    }
    
    /**
     * Validate optimization results against baseline
     */
    public OptimizationResults validateOptimization() {
        CpuUsageMetrics baseline = baselineMetrics.get();
        if (baseline == null) {
            Log.w(TAG, "No baseline metrics available for validation");
            return null;
        }
        
        CpuUsageMetrics current = getCurrentMetrics();
        OptimizationResults results = new OptimizationResults(baseline, current);
        
        Log.i(TAG, "Optimization validation completed: " + results);
        
        if (callback != null) {
            callback.onOptimizationValidated(results);
        }
        
        return results;
    }
    
    /**
     * Get current CPU usage metrics
     */
    public CpuUsageMetrics getCurrentMetrics() {
        long duration = System.currentTimeMillis() - measurementStartTime.get();
        long frames = frameCount.get();
        
        // Calculate CPU percentages
        double totalCpu = calculateTotalCpuPercentage(duration);
        double framebufferCpu = calculateFramebufferCpuPercentage(duration);
        
        // Calculate latency metrics
        double avgLatency = frames > 0 ? (double) totalFrameLatency.get() / frames : 0;
        long minLat = minLatency.get() == Long.MAX_VALUE ? 0 : minLatency.get();
        long maxLat = maxLatency.get();
        
        // Calculate GC frequency
        double gcFreq = duration > 0 ? (gcCount.get() * 1000.0) / duration : 0;
        
        // Get memory usage
        long totalMem = totalMemoryAllocated.get() / (1024 * 1024); // Convert to MB
        long peakMem = peakMemoryUsage.get() / (1024 * 1024); // Convert to MB
        
        return new CpuUsageMetrics(duration, totalCpu, framebufferCpu, avgLatency, 
                minLat, maxLat, frames, gcFreq, totalMem, peakMem);
    }
    
    /**
     * Reset all performance counters
     */
    public void resetCounters() {
        totalCpuTimeUs.set(0);
        framebufferCpuTimeUs.set(0);
        totalFrameLatency.set(0);
        frameCount.set(0);
        minLatency.set(Long.MAX_VALUE);
        maxLatency.set(0);
        gcCount.set(0);
        totalMemoryAllocated.set(0);
        peakMemoryUsage.set(0);
        lastCpuMeasurement.set(0);
        
        Log.d(TAG, "Performance counters reset");
    }
    
    // Private helper methods
    
    private void sampleCpuUsage() {
        if (!isMonitoring) return;
        
        try {
            long cpuTime = getCurrentCpuTime();
            if (cpuTime > 0) {
                totalCpuTimeUs.addAndGet(cpuTime);
            }
            
            // Check for performance alerts
            CpuUsageMetrics current = getCurrentMetrics();
            checkPerformanceAlerts(current);
            
            // Update callback
            if (callback != null) {
                callback.onCpuUsageUpdate(current);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error sampling CPU usage", e);
        }
    }
    
    private long getCurrentCpuTime() {
        try {
            // Read CPU time from /proc/self/stat
            BufferedReader reader = new BufferedReader(new FileReader("/proc/self/stat"));
            String line = reader.readLine();
            reader.close();
            
            if (line != null) {
                String[] parts = line.split(" ");
                if (parts.length > 15) {
                    // utime (14th field) + stime (15th field) in clock ticks
                    long utime = Long.parseLong(parts[13]);
                    long stime = Long.parseLong(parts[14]);
                    
                    // Convert clock ticks to microseconds (assuming 100 Hz)
                    return (utime + stime) * 10000; // 1 tick = 10ms = 10000μs
                }
            }
        } catch (IOException | NumberFormatException e) {
            Log.w(TAG, "Failed to read CPU time from /proc/self/stat", e);
        }
        
        // Fallback to Debug.threadCpuTimeNanos if available
        try {
            return Debug.threadCpuTimeNanos() / 1000; // Convert to microseconds
        } catch (Exception e) {
            Log.w(TAG, "Failed to get thread CPU time", e);
        }
        
        return 0;
    }
    
    private double calculateTotalCpuPercentage(long durationMs) {
        if (durationMs <= 0) return 0;
        
        long totalCpu = totalCpuTimeUs.get();
        long totalTime = durationMs * 1000; // Convert to microseconds
        
        return totalTime > 0 ? (totalCpu * 100.0) / totalTime : 0;
    }
    
    private double calculateFramebufferCpuPercentage(long durationMs) {
        if (durationMs <= 0) return 0;
        
        long framebufferCpu = framebufferCpuTimeUs.get();
        long totalTime = durationMs * 1000; // Convert to microseconds
        
        return totalTime > 0 ? (framebufferCpu * 100.0) / totalTime : 0;
    }
    
    private long getCurrentMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
    
    private void updateMinLatency(long latency) {
        long current = minLatency.get();
        while (latency < current) {
            if (minLatency.compareAndSet(current, latency)) {
                break;
            }
            current = minLatency.get();
        }
    }
    
    private void updateMaxLatency(long latency) {
        long current = maxLatency.get();
        while (latency > current) {
            if (maxLatency.compareAndSet(current, latency)) {
                break;
            }
            current = maxLatency.get();
        }
    }
    
    private void updatePeakMemory(long memory) {
        long current = peakMemoryUsage.get();
        while (memory > current) {
            if (peakMemoryUsage.compareAndSet(current, memory)) {
                break;
            }
            current = peakMemoryUsage.get();
        }
    }
    
    private void checkPerformanceAlerts(CpuUsageMetrics metrics) {
        // Alert if framebuffer CPU usage exceeds target
        if (metrics.framebufferCpuPercentage > TARGET_FRAMEBUFFER_CPU_PERCENTAGE) {
            String alert = String.format("Framebuffer CPU usage %.1f%% exceeds target %.1f%%", 
                    metrics.framebufferCpuPercentage, TARGET_FRAMEBUFFER_CPU_PERCENTAGE);
            
            if (callback != null) {
                callback.onPerformanceAlert(alert, metrics);
            }
        }
        
        // Alert if frame latency is too high
        if (metrics.averageFrameLatencyMs > 100) { // 100ms is very high
            String alert = String.format("Frame latency %.1fms is very high", 
                    metrics.averageFrameLatencyMs);
            
            if (callback != null) {
                callback.onPerformanceAlert(alert, metrics);
            }
        }
        
        // Alert if GC frequency is too high
        if (metrics.gcFrequencyPerSecond > 5.0) { // More than 5 GCs per second
            String alert = String.format("GC frequency %.1f/s is very high", 
                    metrics.gcFrequencyPerSecond);
            
            if (callback != null) {
                callback.onPerformanceAlert(alert, metrics);
            }
        }
    }
    
    /**
     * Release profiler resources
     */
    public void release() {
        Log.d(TAG, "Releasing CpuUsageProfiler resources");
        
        if (isMonitoring) {
            stopMonitoring();
        }
        
        callback = null;
        baselineMetrics.set(null);
        resetCounters();
        
        Log.i(TAG, "CpuUsageProfiler resources released");
    }
}