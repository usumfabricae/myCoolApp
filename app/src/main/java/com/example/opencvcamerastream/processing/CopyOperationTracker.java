package com.example.opencvcamerastream.processing;

import android.util.Log;
import androidx.annotation.NonNull;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * CopyOperationTracker monitors and tracks framebuffer copy operations
 * to measure optimization effectiveness
 * 
 * Requirements 13.1, 13.2: Track copy operations to validate optimization
 * Target: Reduce from 5-6 copies per frame to 2 copies per frame
 */
public class CopyOperationTracker {
    
    private static final String TAG = "CopyOperationTracker";
    
    // Copy operation types
    public enum CopyType {
        IMAGE_TO_MAT("Image→Mat"),
        MAT_TO_BUFFER("Mat→Buffer"),
        BUFFER_TO_MAT("Buffer→Mat"),
        MAT_CLONE("Mat.clone()"),
        MAT_COPY_TO("Mat.copyTo()"),
        MAT_TO_BITMAP("Mat→Bitmap"),
        DEFENSIVE_CLONE("Defensive clone");
        
        private final String description;
        
        CopyType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // Performance tracking
    private final AtomicLong totalFrames = new AtomicLong(0);
    private final Map<CopyType, AtomicLong> copyCounters = new ConcurrentHashMap<>();
    private final Map<CopyType, AtomicLong> copyTimeMs = new ConcurrentHashMap<>();
    
    // CPU usage estimation
    private static final double COPY_CPU_COST_MS_PER_MEGAPIXEL = 2.0; // Estimated cost
    
    public CopyOperationTracker() {
        // Initialize counters for all copy types
        for (CopyType type : CopyType.values()) {
            copyCounters.put(type, new AtomicLong(0));
            copyTimeMs.put(type, new AtomicLong(0));
        }
        Log.d(TAG, "CopyOperationTracker initialized");
    }
    
    /**
     * Record a copy operation
     * 
     * @param type The type of copy operation
     * @param durationMs Time taken for the copy operation
     * @param sizeBytes Size of data copied (for CPU usage estimation)
     */
    public void recordCopyOperation(@NonNull CopyType type, long durationMs, long sizeBytes) {
        copyCounters.get(type).incrementAndGet();
        copyTimeMs.get(type).addAndGet(durationMs);
        
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Copy operation recorded: " + type.getDescription() + 
                    " (" + durationMs + "ms, " + sizeBytes + " bytes)");
        }
    }
    
    /**
     * Record a copy operation with automatic timing calculation
     * 
     * @param type The type of copy operation
     * @param startTimeMs Start time of the operation
     * @param sizeBytes Size of data copied
     */
    public void recordCopyOperationWithTiming(@NonNull CopyType type, long startTimeMs, long sizeBytes) {
        long duration = System.currentTimeMillis() - startTimeMs;
        recordCopyOperation(type, duration, sizeBytes);
    }
    
    /**
     * Start a new frame tracking session
     */
    public void startFrame() {
        totalFrames.incrementAndGet();
    }
    
    /**
     * Get comprehensive copy operation metrics
     */
    public CopyOperationMetrics getMetrics() {
        long frames = totalFrames.get();
        
        // Calculate total copies and time
        long totalCopies = 0;
        long totalTime = 0;
        
        Map<CopyType, Long> copyStats = new ConcurrentHashMap<>();
        Map<CopyType, Long> timeStats = new ConcurrentHashMap<>();
        
        for (CopyType type : CopyType.values()) {
            long copies = copyCounters.get(type).get();
            long time = copyTimeMs.get(type).get();
            
            totalCopies += copies;
            totalTime += time;
            
            copyStats.put(type, copies);
            timeStats.put(type, time);
        }
        
        return new CopyOperationMetrics(frames, totalCopies, totalTime, copyStats, timeStats);
    }
    
    /**
     * Reset all metrics
     */
    public void resetMetrics() {
        totalFrames.set(0);
        
        for (CopyType type : CopyType.values()) {
            copyCounters.get(type).set(0);
            copyTimeMs.get(type).set(0);
        }
        
        Log.d(TAG, "Copy operation metrics reset");
    }
    
    /**
     * Log current metrics summary
     */
    public void logMetricsSummary() {
        CopyOperationMetrics metrics = getMetrics();
        Log.i(TAG, "Copy Operation Summary: " + metrics.toString());
        
        // Log detailed breakdown
        for (CopyType type : CopyType.values()) {
            long copies = metrics.getCopyCount(type);
            long time = metrics.getCopyTime(type);
            
            if (copies > 0) {
                double avgTime = (double) time / copies;
                Log.d(TAG, String.format("  %s: %d copies, %.1fms avg", 
                        type.getDescription(), copies, avgTime));
            }
        }
    }
    
    /**
     * Comprehensive metrics for copy operations
     */
    public static class CopyOperationMetrics {
        private final long totalFrames;
        private final long totalCopies;
        private final long totalTimeMs;
        private final Map<CopyType, Long> copyStats;
        private final Map<CopyType, Long> timeStats;
        
        public CopyOperationMetrics(long frames, long copies, long time,
                                  Map<CopyType, Long> copyStats, Map<CopyType, Long> timeStats) {
            this.totalFrames = frames;
            this.totalCopies = copies;
            this.totalTimeMs = time;
            this.copyStats = new ConcurrentHashMap<>(copyStats);
            this.timeStats = new ConcurrentHashMap<>(timeStats);
        }
        
        public long getTotalFrames() {
            return totalFrames;
        }
        
        public long getTotalCopies() {
            return totalCopies;
        }
        
        public double getAverageCopiesPerFrame() {
            return totalFrames > 0 ? (double) totalCopies / totalFrames : 0;
        }
        
        public long getTotalTimeMs() {
            return totalTimeMs;
        }
        
        public double getAverageTimePerFrame() {
            return totalFrames > 0 ? (double) totalTimeMs / totalFrames : 0;
        }
        
        public double getAverageTimePerCopy() {
            return totalCopies > 0 ? (double) totalTimeMs / totalCopies : 0;
        }
        
        public long getCopyCount(CopyType type) {
            return copyStats.getOrDefault(type, 0L);
        }
        
        public long getCopyTime(CopyType type) {
            return timeStats.getOrDefault(type, 0L);
        }
        
        /**
         * Calculate estimated CPU usage reduction compared to baseline
         * Baseline: 5-6 copies per frame
         * Target: 2 copies per frame
         */
        public double getEstimatedCpuReduction() {
            double avgCopies = getAverageCopiesPerFrame();
            double baselineCopies = 5.5; // Average of 5-6 copies
            double targetCopies = 2.0;
            
            if (avgCopies <= targetCopies) {
                return ((baselineCopies - avgCopies) / baselineCopies) * 100;
            } else {
                return 0; // No reduction achieved
            }
        }
        
        /**
         * Check if optimization targets are met
         */
        public boolean meetsOptimizationTargets() {
            double avgCopies = getAverageCopiesPerFrame();
            return avgCopies <= 2.5; // Allow some tolerance
        }
        
        /**
         * Get optimization status
         */
        public String getOptimizationStatus() {
            double avgCopies = getAverageCopiesPerFrame();
            double reduction = getEstimatedCpuReduction();
            
            if (avgCopies <= 2.0) {
                return String.format("OPTIMAL (%.1f copies/frame, %.1f%% CPU reduction)", 
                        avgCopies, reduction);
            } else if (avgCopies <= 3.0) {
                return String.format("GOOD (%.1f copies/frame, %.1f%% CPU reduction)", 
                        avgCopies, reduction);
            } else if (avgCopies <= 4.0) {
                return String.format("MODERATE (%.1f copies/frame, %.1f%% CPU reduction)", 
                        avgCopies, reduction);
            } else {
                return String.format("NEEDS_OPTIMIZATION (%.1f copies/frame, %.1f%% CPU reduction)", 
                        avgCopies, reduction);
            }
        }
        
        @Override
        public String toString() {
            return String.format("CopyMetrics{frames=%d, copies=%d (%.1f/frame), time=%dms (%.1fms/frame), status=%s}",
                    totalFrames, totalCopies, getAverageCopiesPerFrame(), 
                    totalTimeMs, getAverageTimePerFrame(), getOptimizationStatus());
        }
    }
}