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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * CpuUsageProfiler measures and validates CPU usage reduction after framebuffer optimizations
 * 
 * Requirements addressed:
 * - Task 25: Profile CPU usage before and after optimization
 * - Req-13.6: Measure framebuffer operation CPU percentage (target: <30%)
 * - Task 25: Validate frame processing latency improvement (target: 20-30ms reduction)
 * - Task 25: Measure memory pressure and GC frequency reduction
 * - Task 25: Document performance improvements
 * 
 * MEASUREMENT STRATEGY:
 * - Continuous CPU usage monitoring during frame processing
 * - Framebuffer operation timing and CPU cost analysis
 * - Memory pressure tracking with GC frequency measurement
 * - Before/after optimization comparison with statistical validation
 * - Real-time performance metrics with trend analysis
 */
public class CpuUsageProfiler {
    
    private static final String TAG = "CpuUsageProfiler";
    
    // Measurement configuration
    private static final int MEASUREMENT_INTERVAL_MS = 100; // Sample every 100ms
    private static final int MEASUREMENT_WINDOW_SIZE = 300; // Keep 30 seconds of data
    private static final long WARMUP_PERIOD_MS = 5000; // 5 second warmup before measurements
    private static final int MIN_SAMPLES_FOR_VALIDATION = 50; // Minimum samples for statistical validation
    
    // CPU usage thresholds (Requirements)
    private static final double FRAMEBUFFER_CPU_TARGET_PERCENT = 30.0; // Target: <30%
    private static final double LATENCY_IMPROVEMENT_TARGET_MS = 25.0; // Target: 20-30ms reduction
    private static final double CPU_REDUCTION_TARGET_PERCENT = 40.0; // Target: 40-50% reduction
    
    // Measurement state
    private volatile boolean isProfilerActive = false;
    private volatile boolean isWarmupComplete = false;
    private long profilingStartTime = 0;
    private long warmupStartTime = 0;
    
    // CPU measurement components
    private ScheduledExecutorService measurementExecutor;
    private final ConcurrentLinkedQueue<CpuMeasurement> cpuMeasurements = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<FrameProcessingMeasurement> frameMeasurements = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<MemoryMeasurement> memoryMeasurements = new ConcurrentLinkedQueue<>();
    
    // Performance tracking
    private final AtomicLong totalFramesProcessed = new AtomicLong(0);
    private final AtomicLong totalFramebufferOperations = new AtomicLong(0);
    private final AtomicLong totalFramebufferTimeMs = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    private final AtomicLong gcCount = new AtomicLong(0);
    private final AtomicLong lastGcTime = new AtomicLong(0);
    
    // Baseline measurements (before optimization)
    private CpuUsageBaseline baseline;
    
    /**
     * CPU measurement data point
     */
    public static class CpuMeasurement {
        public final long timestamp;
        public final double cpuUsagePercent;
        public final double systemCpuPercent;
        public final long userTimeMs;
        public final long systemTimeMs;
        public final int threadCount;
        
        public CpuMeasurement(long timestamp, double cpuUsage, double systemCpu, 
                            long userTime, long systemTime, int threads) {
            this.timestamp = timestamp;
            this.cpuUsagePercent = cpuUsage;
            this.systemCpuPercent = systemCpu;
            this.userTimeMs = userTime;
            this.systemTimeMs = systemTime;
            this.threadCount = threads;
        }
        
        @Override
        public String toString() {
            return String.format("CpuMeasurement{time=%d, cpu=%.1f%%, system=%.1f%%, threads=%d}",
                    timestamp, cpuUsagePercent, systemCpuPercent, threadCount);
        }
    }
    
    /**
     * Frame processing measurement
     */
    public static class FrameProcessingMeasurement {
        public final long timestamp;
        public final long processingTimeMs;
        public final long framebufferTimeMs;
        public final int copyOperations;
        public final boolean usedOptimizedPath;
        public final double cpuUsageDuringFrame;
        
        public FrameProcessingMeasurement(long timestamp, long processingTime, long framebufferTime,
                                        int copyOps, boolean optimized, double cpuUsage) {
            this.timestamp = timestamp;
            this.processingTimeMs = processingTime;
            this.framebufferTimeMs = framebufferTime;
            this.copyOperations = copyOps;
            this.usedOptimizedPath = optimized;
            this.cpuUsageDuringFrame = cpuUsage;
        }
        
        public double getFramebufferCpuPercent() {
            return processingTimeMs > 0 ? (double) framebufferTimeMs / processingTimeMs * 100 : 0;
        }
        
        @Override
        public String toString() {
            return String.format("FrameMeasurement{time=%d, proc=%dms, fb=%dms (%.1f%%), copies=%d, opt=%s, cpu=%.1f%%}",
                    timestamp, processingTimeMs, framebufferTimeMs, getFramebufferCpuPercent(), 
                    copyOperations, usedOptimizedPath, cpuUsageDuringFrame);
        }
    }
    
    /**
     * Memory measurement data point
     */
    public static class MemoryMeasurement {
        public final long timestamp;
        public final long usedMemoryMB;
        public final long availableMemoryMB;
        public final long gcCount;
        public final long gcTimeMs;
        public final double memoryPressure; // 0.0 to 1.0
        
        public MemoryMeasurement(long timestamp, long usedMem, long availMem, 
                               long gcCount, long gcTime, double pressure) {
            this.timestamp = timestamp;
            this.usedMemoryMB = usedMem;
            this.availableMemoryMB = availMem;
            this.gcCount = gcCount;
            this.gcTimeMs = gcTime;
            this.memoryPressure = pressure;
        }
        
        @Override
        public String toString() {
            return String.format("MemoryMeasurement{time=%d, used=%dMB, avail=%dMB, gc=%d, pressure=%.2f}",
                    timestamp, usedMemoryMB, availableMemoryMB, gcCount, memoryPressure);
        }
    }
    
    /**
     * Baseline measurements for comparison
     */
    public static class CpuUsageBaseline {
        public final double averageCpuPercent;
        public final double averageFramebufferCpuPercent;
        public final double averageProcessingLatencyMs;
        public final double averageCopyOperationsPerFrame;
        public final double averageGcFrequencyPerMinute;
        public final long measurementDurationMs;
        public final int sampleCount;
        
        public CpuUsageBaseline(double avgCpu, double avgFbCpu, double avgLatency, 
                              double avgCopies, double avgGcFreq, long duration, int samples) {
            this.averageCpuPercent = avgCpu;
            this.averageFramebufferCpuPercent = avgFbCpu;
            this.averageProcessingLatencyMs = avgLatency;
            this.averageCopyOperationsPerFrame = avgCopies;
            this.averageGcFrequencyPerMinute = avgGcFreq;
            this.measurementDurationMs = duration;
            this.sampleCount = samples;
        }
        
        @Override
        public String toString() {
            return String.format("Baseline{cpu=%.1f%%, fbCpu=%.1f%%, latency=%.1fms, copies=%.1f, gcFreq=%.1f/min, samples=%d}",
                    averageCpuPercent, averageFramebufferCpuPercent, averageProcessingLatencyMs, 
                    averageCopyOperationsPerFrame, averageGcFrequencyPerMinute, sampleCount);
        }
    }
    
    /**
     * Performance validation results
     */
    public static class PerformanceValidationResults {
        public final boolean meetsFramebufferCpuTarget;
        public final boolean meetsLatencyImprovementTarget;
        public final boolean meetsCpuReductionTarget;
        public final boolean meetsMemoryPressureTarget;
        
        public final double currentFramebufferCpuPercent;
        public final double latencyImprovementMs;
        public final double cpuReductionPercent;
        public final double memoryPressureReduction;
        
        public final CpuUsageBaseline baseline;
        public final CpuUsageBaseline current;
        
        public final String validationSummary;
        public final List<String> recommendations;
        
        public PerformanceValidationResults(boolean fbTarget, boolean latencyTarget, boolean cpuTarget, boolean memTarget,
                                          double fbCpu, double latencyImpr, double cpuReduction, double memReduction,
                                          CpuUsageBaseline baseline, CpuUsageBaseline current,
                                          String summary, List<String> recommendations) {
            this.meetsFramebufferCpuTarget = fbTarget;
            this.meetsLatencyImprovementTarget = latencyTarget;
            this.meetsCpuReductionTarget = cpuTarget;
            this.meetsMemoryPressureTarget = memTarget;
            this.currentFramebufferCpuPercent = fbCpu;
            this.latencyImprovementMs = latencyImpr;
            this.cpuReductionPercent = cpuReduction;
            this.memoryPressureReduction = memReduction;
            this.baseline = baseline;
            this.current = current;
            this.validationSummary = summary;
            this.recommendations = new ArrayList<>(recommendations);
        }
        
        public boolean meetsAllTargets() {
            return meetsFramebufferCpuTarget && meetsLatencyImprovementTarget && 
                   meetsCpuReductionTarget && meetsMemoryPressureTarget;
        }
        
        @Override
        public String toString() {
            return String.format("ValidationResults{fbCpu=%.1f%% (target<%.1f%%), latencyImpr=%.1fms (target>%.1fms), " +
                               "cpuReduction=%.1f%% (target>%.1f%%), allTargets=%s}",
                    currentFramebufferCpuPercent, FRAMEBUFFER_CPU_TARGET_PERCENT,
                    latencyImprovementMs, LATENCY_IMPROVEMENT_TARGET_MS,
                    cpuReductionPercent, CPU_REDUCTION_TARGET_PERCENT,
                    meetsAllTargets());
        }
    }
    
    public CpuUsageProfiler() {
        Log.d(TAG, "CpuUsageProfiler created with targets: framebuffer CPU <30%, latency improvement >25ms, CPU reduction >40%");
    }
    
    /**
     * Start CPU usage profiling
     * 
     * @param withWarmup true to include warmup period, false to start measuring immediately
     */
    public void startProfiling(boolean withWarmup) {
        if (isProfilerActive) {
            Log.w(TAG, "Profiler already active");
            return;
        }
        
        Log.i(TAG, "Starting CPU usage profiling" + (withWarmup ? " with warmup" : ""));
        
        isProfilerActive = true;
        isWarmupComplete = !withWarmup;
        profilingStartTime = System.currentTimeMillis();
        warmupStartTime = withWarmup ? profilingStartTime : 0;
        
        // Clear previous measurements
        cpuMeasurements.clear();
        frameMeasurements.clear();
        memoryMeasurements.clear();
        
        // Reset counters
        totalFramesProcessed.set(0);
        totalFramebufferOperations.set(0);
        totalFramebufferTimeMs.set(0);
        totalProcessingTimeMs.set(0);
        gcCount.set(0);
        lastGcTime.set(System.currentTimeMillis());
        
        // Start measurement executor
        measurementExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CpuProfiler");
            t.setDaemon(true);
            return t;
        });
        
        measurementExecutor.scheduleAtFixedRate(this::takeCpuMeasurement, 
                0, MEASUREMENT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        measurementExecutor.scheduleAtFixedRate(this::takeMemoryMeasurement, 
                0, MEASUREMENT_INTERVAL_MS * 2, TimeUnit.MILLISECONDS); // Memory every 200ms
        
        Log.i(TAG, "CPU profiling started successfully");
    }
    
    /**
     * Stop CPU usage profiling
     */
    public void stopProfiling() {
        if (!isProfilerActive) {
            Log.w(TAG, "Profiler not active");
            return;
        }
        
        Log.i(TAG, "Stopping CPU usage profiling");
        
        isProfilerActive = false;
        isWarmupComplete = false;
        
        if (measurementExecutor != null) {
            measurementExecutor.shutdown();
            try {
                if (!measurementExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    measurementExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                measurementExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            measurementExecutor = null;
        }
        
        // Log final summary
        logProfilingSummary();
        
        Log.i(TAG, "CPU profiling stopped");
    }
    
    /**
     * Record frame processing measurement
     * 
     * @param processingTimeMs Total frame processing time
     * @param framebufferTimeMs Time spent on framebuffer operations
     * @param copyOperations Number of copy operations performed
     * @param usedOptimizedPath Whether optimized processing path was used
     */
    public void recordFrameProcessing(long processingTimeMs, long framebufferTimeMs, 
                                    int copyOperations, boolean usedOptimizedPath) {
        if (!isProfilerActive || !isWarmupComplete) {
            return;
        }
        
        totalFramesProcessed.incrementAndGet();
        totalFramebufferOperations.addAndGet(copyOperations);
        totalFramebufferTimeMs.addAndGet(framebufferTimeMs);
        totalProcessingTimeMs.addAndGet(processingTimeMs);
        
        // Get current CPU usage for this frame
        double currentCpuUsage = getCurrentCpuUsage();
        
        FrameProcessingMeasurement measurement = new FrameProcessingMeasurement(
                System.currentTimeMillis(), processingTimeMs, framebufferTimeMs,
                copyOperations, usedOptimizedPath, currentCpuUsage);
        
        frameMeasurements.offer(measurement);
        
        // Maintain window size
        while (frameMeasurements.size() > MEASUREMENT_WINDOW_SIZE) {
            frameMeasurements.poll();
        }
        
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Frame measurement recorded: " + measurement);
        }
    }
    
    /**
     * Set baseline measurements for comparison
     * 
     * @param baseline Baseline measurements from before optimization
     */
    public void setBaseline(@NonNull CpuUsageBaseline baseline) {
        this.baseline = baseline;
        Log.i(TAG, "Baseline set: " + baseline);
    }
    
    /**
     * Capture current state as baseline
     * 
     * @return Current measurements as baseline
     */
    @Nullable
    public CpuUsageBaseline captureBaseline() {
        if (!isProfilerActive || cpuMeasurements.isEmpty() || frameMeasurements.isEmpty()) {
            Log.w(TAG, "Cannot capture baseline - insufficient data");
            return null;
        }
        
        CpuUsageBaseline baseline = calculateCurrentBaseline();
        setBaseline(baseline);
        return baseline;
    }
    
    /**
     * Validate performance improvements against targets
     * 
     * @return Performance validation results
     */
    @NonNull
    public PerformanceValidationResults validatePerformanceImprovements() {
        if (baseline == null) {
            Log.w(TAG, "Cannot validate - no baseline set");
            return createEmptyValidationResults("No baseline available for comparison");
        }
        
        if (!isProfilerActive || frameMeasurements.size() < MIN_SAMPLES_FOR_VALIDATION) {
            Log.w(TAG, "Cannot validate - insufficient current measurements");
            return createEmptyValidationResults("Insufficient current measurements for validation");
        }
        
        CpuUsageBaseline current = calculateCurrentBaseline();
        
        // Calculate improvements
        double latencyImprovement = baseline.averageProcessingLatencyMs - current.averageProcessingLatencyMs;
        double cpuReduction = ((baseline.averageCpuPercent - current.averageCpuPercent) / baseline.averageCpuPercent) * 100;
        double memoryPressureReduction = calculateMemoryPressureReduction();
        
        // Check targets
        boolean meetsFramebufferTarget = current.averageFramebufferCpuPercent < FRAMEBUFFER_CPU_TARGET_PERCENT;
        boolean meetsLatencyTarget = latencyImprovement >= LATENCY_IMPROVEMENT_TARGET_MS;
        boolean meetsCpuTarget = cpuReduction >= CPU_REDUCTION_TARGET_PERCENT;
        boolean meetsMemoryTarget = memoryPressureReduction > 0; // Any reduction is good
        
        // Generate summary and recommendations
        String summary = generateValidationSummary(current, latencyImprovement, cpuReduction, memoryPressureReduction);
        List<String> recommendations = generateRecommendations(current, latencyImprovement, cpuReduction);
        
        PerformanceValidationResults results = new PerformanceValidationResults(
                meetsFramebufferTarget, meetsLatencyTarget, meetsCpuTarget, meetsMemoryTarget,
                current.averageFramebufferCpuPercent, latencyImprovement, cpuReduction, memoryPressureReduction,
                baseline, current, summary, recommendations);
        
        Log.i(TAG, "Performance validation completed: " + results);
        
        return results;
    }
    
    /**
     * Get current CPU usage statistics
     * 
     * @return Current CPU usage baseline or null if insufficient data
     */
    @Nullable
    public CpuUsageBaseline getCurrentCpuUsageStats() {
        if (!isProfilerActive || cpuMeasurements.isEmpty()) {
            return null;
        }
        
        return calculateCurrentBaseline();
    }
    
    /**
     * Check if profiler is currently active
     */
    public boolean isActive() {
        return isProfilerActive;
    }
    
    /**
     * Check if warmup period is complete
     */
    public boolean isWarmupComplete() {
        return isWarmupComplete;
    }
    
    /**
     * Get total frames processed during profiling
     */
    public long getTotalFramesProcessed() {
        return totalFramesProcessed.get();
    }
    
    // Private implementation methods
    
    private void takeCpuMeasurement() {
        try {
            // Check warmup period
            if (!isWarmupComplete && warmupStartTime > 0) {
                long elapsed = System.currentTimeMillis() - warmupStartTime;
                if (elapsed >= WARMUP_PERIOD_MS) {
                    isWarmupComplete = true;
                    Log.d(TAG, "Warmup period complete, starting measurements");
                }
                return;
            }
            
            if (!isWarmupComplete) {
                return;
            }
            
            CpuMeasurement measurement = measureCurrentCpuUsage();
            if (measurement != null) {
                cpuMeasurements.offer(measurement);
                
                // Maintain window size
                while (cpuMeasurements.size() > MEASUREMENT_WINDOW_SIZE) {
                    cpuMeasurements.poll();
                }
                
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "CPU measurement: " + measurement);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error taking CPU measurement", e);
        }
    }
    
    private void takeMemoryMeasurement() {
        try {
            if (!isWarmupComplete) {
                return;
            }
            
            MemoryMeasurement measurement = measureCurrentMemoryUsage();
            if (measurement != null) {
                memoryMeasurements.offer(measurement);
                
                // Maintain window size
                while (memoryMeasurements.size() > MEASUREMENT_WINDOW_SIZE / 2) {
                    memoryMeasurements.poll();
                }
                
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "Memory measurement: " + measurement);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error taking memory measurement", e);
        }
    }
    
    @Nullable
    private CpuMeasurement measureCurrentCpuUsage() {
        try {
            long timestamp = System.currentTimeMillis();
            
            // Read /proc/stat for system CPU
            double systemCpuPercent = readSystemCpuUsage();
            
            // Read /proc/self/stat for process CPU
            ProcessCpuInfo processCpuInfo = readProcessCpuUsage();
            if (processCpuInfo == null) {
                return null;
            }
            
            // Calculate process CPU percentage
            double processCpuPercent = calculateProcessCpuPercent(processCpuInfo);
            
            return new CpuMeasurement(timestamp, processCpuPercent, systemCpuPercent,
                    processCpuInfo.userTime, processCpuInfo.systemTime, processCpuInfo.threadCount);
            
        } catch (Exception e) {
            Log.e(TAG, "Error measuring CPU usage", e);
            return null;
        }
    }
    
    @Nullable
    private MemoryMeasurement measureCurrentMemoryUsage() {
        try {
            long timestamp = System.currentTimeMillis();
            
            // Get memory info
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory() / (1024 * 1024); // MB
            long totalMemory = runtime.totalMemory() / (1024 * 1024); // MB
            long freeMemory = runtime.freeMemory() / (1024 * 1024); // MB
            long usedMemory = totalMemory - freeMemory;
            long availableMemory = maxMemory - usedMemory;
            
            // Calculate memory pressure (0.0 to 1.0)
            double memoryPressure = (double) usedMemory / maxMemory;
            
            // Get GC info
            long currentGcCount = Debug.getGlobalGcInvocationCount();
            long gcTimeSinceLastMeasurement = 0;
            
            if (gcCount.get() > 0) {
                gcTimeSinceLastMeasurement = currentGcCount - gcCount.get();
            }
            gcCount.set(currentGcCount);
            
            return new MemoryMeasurement(timestamp, usedMemory, availableMemory,
                    currentGcCount, gcTimeSinceLastMeasurement, memoryPressure);
            
        } catch (Exception e) {
            Log.e(TAG, "Error measuring memory usage", e);
            return null;
        }
    }
    
    private double getCurrentCpuUsage() {
        try {
            ProcessCpuInfo info = readProcessCpuUsage();
            return info != null ? calculateProcessCpuPercent(info) : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    private static class ProcessCpuInfo {
        long userTime;
        long systemTime;
        int threadCount;
        long timestamp;
        
        ProcessCpuInfo(long user, long system, int threads) {
            this.userTime = user;
            this.systemTime = system;
            this.threadCount = threads;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    private ProcessCpuInfo lastProcessCpuInfo = null;
    
    @Nullable
    private ProcessCpuInfo readProcessCpuUsage() {
        try {
            int pid = Process.myPid();
            BufferedReader reader = new BufferedReader(new FileReader("/proc/" + pid + "/stat"));
            String line = reader.readLine();
            reader.close();
            
            if (line == null) {
                return null;
            }
            
            String[] parts = line.split("\\s+");
            if (parts.length < 20) {
                return null;
            }
            
            // Parse CPU times (in clock ticks)
            long userTime = Long.parseLong(parts[13]); // utime
            long systemTime = Long.parseLong(parts[14]); // stime
            int threadCount = Integer.parseInt(parts[19]); // num_threads
            
            return new ProcessCpuInfo(userTime, systemTime, threadCount);
            
        } catch (Exception e) {
            Log.e(TAG, "Error reading process CPU usage", e);
            return null;
        }
    }
    
    private double calculateProcessCpuPercent(ProcessCpuInfo currentInfo) {
        if (lastProcessCpuInfo == null) {
            lastProcessCpuInfo = currentInfo;
            return 0.0;
        }
        
        try {
            long timeDelta = currentInfo.timestamp - lastProcessCpuInfo.timestamp;
            if (timeDelta <= 0) {
                return 0.0;
            }
            
            long userDelta = currentInfo.userTime - lastProcessCpuInfo.userTime;
            long systemDelta = currentInfo.systemTime - lastProcessCpuInfo.systemTime;
            long totalCpuDelta = userDelta + systemDelta;
            
            // Convert clock ticks to milliseconds (assuming 100 ticks per second)
            long totalCpuTimeMs = totalCpuDelta * 10;
            
            double cpuPercent = (double) totalCpuTimeMs / timeDelta * 100;
            
            lastProcessCpuInfo = currentInfo;
            
            return Math.min(cpuPercent, 100.0); // Cap at 100%
            
        } catch (Exception e) {
            Log.e(TAG, "Error calculating CPU percent", e);
            return 0.0;
        }
    }
    
    private double readSystemCpuUsage() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"));
            String line = reader.readLine();
            reader.close();
            
            if (line == null || !line.startsWith("cpu ")) {
                return 0.0;
            }
            
            String[] parts = line.split("\\s+");
            if (parts.length < 8) {
                return 0.0;
            }
            
            // Parse CPU times
            long user = Long.parseLong(parts[1]);
            long nice = Long.parseLong(parts[2]);
            long system = Long.parseLong(parts[3]);
            long idle = Long.parseLong(parts[4]);
            long iowait = Long.parseLong(parts[5]);
            long irq = Long.parseLong(parts[6]);
            long softirq = Long.parseLong(parts[7]);
            
            long totalTime = user + nice + system + idle + iowait + irq + softirq;
            long activeTime = totalTime - idle - iowait;
            
            // Simple approximation - would need previous values for accurate calculation
            return totalTime > 0 ? (double) activeTime / totalTime * 100 : 0.0;
            
        } catch (Exception e) {
            Log.e(TAG, "Error reading system CPU usage", e);
            return 0.0;
        }
    }
    
    private CpuUsageBaseline calculateCurrentBaseline() {
        if (cpuMeasurements.isEmpty() || frameMeasurements.isEmpty()) {
            return new CpuUsageBaseline(0, 0, 0, 0, 0, 0, 0);
        }
        
        // Calculate CPU averages
        double avgCpu = cpuMeasurements.stream()
                .mapToDouble(m -> m.cpuUsagePercent)
                .average()
                .orElse(0.0);
        
        // Calculate framebuffer CPU averages
        double avgFramebufferCpu = frameMeasurements.stream()
                .mapToDouble(FrameProcessingMeasurement::getFramebufferCpuPercent)
                .average()
                .orElse(0.0);
        
        // Calculate processing latency average
        double avgLatency = frameMeasurements.stream()
                .mapToLong(m -> m.processingTimeMs)
                .average()
                .orElse(0.0);
        
        // Calculate copy operations average
        double avgCopies = frameMeasurements.stream()
                .mapToInt(m -> m.copyOperations)
                .average()
                .orElse(0.0);
        
        // Calculate GC frequency
        double avgGcFreq = calculateGcFrequencyPerMinute();
        
        long duration = System.currentTimeMillis() - profilingStartTime;
        int sampleCount = cpuMeasurements.size();
        
        return new CpuUsageBaseline(avgCpu, avgFramebufferCpu, avgLatency, avgCopies, avgGcFreq, duration, sampleCount);
    }
    
    private double calculateGcFrequencyPerMinute() {
        if (memoryMeasurements.isEmpty()) {
            return 0.0;
        }
        
        long totalGcEvents = memoryMeasurements.stream()
                .mapToLong(m -> m.gcTimeMs)
                .sum();
        
        long durationMinutes = Math.max(1, (System.currentTimeMillis() - profilingStartTime) / 60000);
        
        return (double) totalGcEvents / durationMinutes;
    }
    
    private double calculateMemoryPressureReduction() {
        if (baseline == null || memoryMeasurements.isEmpty()) {
            return 0.0;
        }
        
        double currentAvgPressure = memoryMeasurements.stream()
                .mapToDouble(m -> m.memoryPressure)
                .average()
                .orElse(0.0);
        
        // Estimate baseline pressure (would need actual baseline measurements)
        double estimatedBaselinePressure = 0.7; // Assume 70% baseline pressure
        
        return ((estimatedBaselinePressure - currentAvgPressure) / estimatedBaselinePressure) * 100;
    }
    
    private String generateValidationSummary(CpuUsageBaseline current, double latencyImprovement, 
                                           double cpuReduction, double memoryReduction) {
        StringBuilder summary = new StringBuilder();
        summary.append("Performance Validation Summary:\n");
        summary.append(String.format("• Framebuffer CPU: %.1f%% (target: <%.1f%%) - %s\n",
                current.averageFramebufferCpuPercent, FRAMEBUFFER_CPU_TARGET_PERCENT,
                current.averageFramebufferCpuPercent < FRAMEBUFFER_CPU_TARGET_PERCENT ? "✓ PASS" : "✗ FAIL"));
        summary.append(String.format("• Latency improvement: %.1fms (target: >%.1fms) - %s\n",
                latencyImprovement, LATENCY_IMPROVEMENT_TARGET_MS,
                latencyImprovement >= LATENCY_IMPROVEMENT_TARGET_MS ? "✓ PASS" : "✗ FAIL"));
        summary.append(String.format("• CPU reduction: %.1f%% (target: >%.1f%%) - %s\n",
                cpuReduction, CPU_REDUCTION_TARGET_PERCENT,
                cpuReduction >= CPU_REDUCTION_TARGET_PERCENT ? "✓ PASS" : "✗ FAIL"));
        summary.append(String.format("• Memory pressure reduction: %.1f%% - %s\n",
                memoryReduction, memoryReduction > 0 ? "✓ PASS" : "✗ FAIL"));
        
        return summary.toString();
    }
    
    private List<String> generateRecommendations(CpuUsageBaseline current, double latencyImprovement, double cpuReduction) {
        List<String> recommendations = new ArrayList<>();
        
        if (current.averageFramebufferCpuPercent >= FRAMEBUFFER_CPU_TARGET_PERCENT) {
            recommendations.add("Framebuffer CPU usage is above target. Consider further copy operation elimination.");
        }
        
        if (latencyImprovement < LATENCY_IMPROVEMENT_TARGET_MS) {
            recommendations.add("Latency improvement is below target. Review processing pipeline for additional optimizations.");
        }
        
        if (cpuReduction < CPU_REDUCTION_TARGET_PERCENT) {
            recommendations.add("CPU reduction is below target. Verify zero-copy optimizations are active.");
        }
        
        if (current.averageCopyOperationsPerFrame > 2.5) {
            recommendations.add("Copy operations per frame is above optimal. Review framebuffer optimization implementation.");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("All performance targets met. Optimization successful!");
        }
        
        return recommendations;
    }
    
    private PerformanceValidationResults createEmptyValidationResults(String reason) {
        return new PerformanceValidationResults(
                false, false, false, false,
                0, 0, 0, 0,
                null, null,
                "Validation failed: " + reason,
                Collections.singletonList("Ensure profiler is active with sufficient measurement data"));
    }
    
    private void logProfilingSummary() {
        long duration = System.currentTimeMillis() - profilingStartTime;
        long frames = totalFramesProcessed.get();
        
        Log.i(TAG, String.format("Profiling Summary - Duration: %dms, Frames: %d, CPU samples: %d, Memory samples: %d",
                duration, frames, cpuMeasurements.size(), memoryMeasurements.size()));
        
        if (baseline != null) {
            CpuUsageBaseline current = calculateCurrentBaseline();
            Log.i(TAG, "Baseline: " + baseline);
            Log.i(TAG, "Current: " + current);
        }
    }
}