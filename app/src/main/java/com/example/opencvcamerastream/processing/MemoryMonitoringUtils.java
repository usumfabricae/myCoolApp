package com.example.opencvcamerastream.processing;

import android.util.Log;
import androidx.annotation.NonNull;

/**
 * Utility class for memory monitoring and optimization
 * 
 * Provides helper methods for:
 * - System memory usage monitoring
 * - Memory pressure detection
 * - Garbage collection suggestions
 * - Memory usage reporting
 * 
 * Requirements addressed:
 * - 3.4: Monitor memory usage to prevent crashes when usage exceeds 80%
 * - 5.2: Provide memory management utilities to prevent leaks
 * - 5.3: Support performance optimization on lower-end devices
 */
public class MemoryMonitoringUtils {
    
    private static final String TAG = "MemoryMonitoringUtils";
    
    // Memory thresholds
    private static final double MEMORY_WARNING_THRESHOLD = 0.7;  // 70%
    private static final double MEMORY_CRITICAL_THRESHOLD = 0.8; // 80%
    private static final double MEMORY_EMERGENCY_THRESHOLD = 0.9; // 90%
    
    // Monitoring intervals
    private static final long MIN_GC_INTERVAL_MS = 5000; // Minimum 5 seconds between GC suggestions
    
    private static long lastGcSuggestionTime = 0;
    
    /**
     * Memory usage levels
     */
    public enum MemoryLevel {
        NORMAL,     // < 70% usage
        WARNING,    // 70-80% usage
        CRITICAL,   // 80-90% usage
        EMERGENCY   // > 90% usage
    }
    
    /**
     * Memory status information
     */
    public static class MemoryStatus {
        public final long maxMemory;
        public final long totalMemory;
        public final long freeMemory;
        public final long usedMemory;
        public final long availableMemory;
        public final double usagePercentage;
        public final MemoryLevel level;
        
        private MemoryStatus(long maxMemory, long totalMemory, long freeMemory) {
            this.maxMemory = maxMemory;
            this.totalMemory = totalMemory;
            this.freeMemory = freeMemory;
            this.usedMemory = totalMemory - freeMemory;
            this.availableMemory = maxMemory - usedMemory;
            this.usagePercentage = (double) usedMemory / maxMemory;
            this.level = determineMemoryLevel(usagePercentage);
        }
        
        private static MemoryLevel determineMemoryLevel(double usagePercentage) {
            if (usagePercentage >= MEMORY_EMERGENCY_THRESHOLD) {
                return MemoryLevel.EMERGENCY;
            } else if (usagePercentage >= MEMORY_CRITICAL_THRESHOLD) {
                return MemoryLevel.CRITICAL;
            } else if (usagePercentage >= MEMORY_WARNING_THRESHOLD) {
                return MemoryLevel.WARNING;
            } else {
                return MemoryLevel.NORMAL;
            }
        }
        
        public boolean isMemoryPressure() {
            return level == MemoryLevel.CRITICAL || level == MemoryLevel.EMERGENCY;
        }
        
        public boolean shouldOptimize() {
            return level != MemoryLevel.NORMAL;
        }
        
        @Override
        public String toString() {
            return String.format(
                "MemoryStatus{used=%.1fMB/%.1fMB (%.1f%%), available=%.1fMB, level=%s}",
                usedMemory / (1024.0 * 1024.0),
                maxMemory / (1024.0 * 1024.0),
                usagePercentage * 100,
                availableMemory / (1024.0 * 1024.0),
                level
            );
        }
    }
    
    /**
     * Get current memory status
     * @return MemoryStatus object with current memory information
     */
    @NonNull
    public static MemoryStatus getMemoryStatus() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        
        return new MemoryStatus(maxMemory, totalMemory, freeMemory);
    }
    
    /**
     * Check if system is under memory pressure
     * @return true if memory usage exceeds critical threshold
     */
    public static boolean isMemoryPressure() {
        MemoryStatus status = getMemoryStatus();
        return status.isMemoryPressure();
    }
    
    /**
     * Get current memory usage percentage
     * @return Memory usage as percentage (0.0 to 1.0)
     */
    public static double getMemoryUsagePercentage() {
        MemoryStatus status = getMemoryStatus();
        return status.usagePercentage;
    }
    
    /**
     * Suggest garbage collection if appropriate
     * This method includes throttling to prevent excessive GC calls
     * @return true if GC was suggested, false if throttled
     */
    public static boolean suggestGarbageCollection() {
        long currentTime = System.currentTimeMillis();
        
        // Throttle GC suggestions
        if (currentTime - lastGcSuggestionTime < MIN_GC_INTERVAL_MS) {
            return false;
        }
        
        MemoryStatus status = getMemoryStatus();
        
        if (status.shouldOptimize()) {
            Log.d(TAG, "Suggesting garbage collection - " + status);
            System.gc();
            lastGcSuggestionTime = currentTime;
            return true;
        }
        
        return false;
    }
    
    /**
     * Force garbage collection suggestion (bypasses throttling)
     * Use with caution - should only be called in emergency situations
     */
    public static void forceGarbageCollection() {
        MemoryStatus beforeGc = getMemoryStatus();
        Log.w(TAG, "Forcing garbage collection - " + beforeGc);
        
        System.gc();
        lastGcSuggestionTime = System.currentTimeMillis();
        
        // Log results after a brief delay
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        MemoryStatus afterGc = getMemoryStatus();
        long memoryFreed = beforeGc.usedMemory - afterGc.usedMemory;
        
        Log.i(TAG, "Garbage collection completed - freed " + 
              (memoryFreed / (1024 * 1024)) + "MB, " + afterGc);
    }
    
    /**
     * Calculate recommended buffer pool size based on available memory
     * @param basePoolSize Base pool size for normal conditions
     * @return Recommended pool size adjusted for current memory conditions
     */
    public static int getRecommendedPoolSize(int basePoolSize) {
        MemoryStatus status = getMemoryStatus();
        
        switch (status.level) {
            case EMERGENCY:
                return Math.max(1, basePoolSize / 4); // Reduce to 25%
            case CRITICAL:
                return Math.max(1, basePoolSize / 2); // Reduce to 50%
            case WARNING:
                return Math.max(1, (basePoolSize * 3) / 4); // Reduce to 75%
            case NORMAL:
            default:
                return basePoolSize;
        }
    }
    
    /**
     * Calculate recommended buffer memory limit based on available memory
     * @param baseMemoryLimit Base memory limit in bytes
     * @return Recommended memory limit adjusted for current conditions
     */
    public static long getRecommendedMemoryLimit(long baseMemoryLimit) {
        MemoryStatus status = getMemoryStatus();
        
        // Adjust based on available memory
        long availableForBuffers = Math.min(baseMemoryLimit, status.availableMemory / 4);
        
        switch (status.level) {
            case EMERGENCY:
                return Math.max(1024 * 1024, availableForBuffers / 4); // 25% of available
            case CRITICAL:
                return Math.max(1024 * 1024, availableForBuffers / 2); // 50% of available
            case WARNING:
                return Math.max(1024 * 1024, (availableForBuffers * 3) / 4); // 75% of available
            case NORMAL:
            default:
                return availableForBuffers;
        }
    }
    
    /**
     * Log current memory status
     * @param tag Log tag to use
     */
    public static void logMemoryStatus(@NonNull String tag) {
        MemoryStatus status = getMemoryStatus();
        
        switch (status.level) {
            case EMERGENCY:
                Log.e(tag, "EMERGENCY memory level: " + status);
                break;
            case CRITICAL:
                Log.w(tag, "CRITICAL memory level: " + status);
                break;
            case WARNING:
                Log.w(tag, "WARNING memory level: " + status);
                break;
            case NORMAL:
                Log.d(tag, "Memory status: " + status);
                break;
        }
    }
    
    /**
     * Check if a memory allocation of the given size is safe
     * @param allocationSize Size of the proposed allocation in bytes
     * @return true if allocation is likely safe, false if it might cause issues
     */
    public static boolean isSafeToAllocate(long allocationSize) {
        MemoryStatus status = getMemoryStatus();
        
        // Don't allocate if we're already in critical state
        if (status.level == MemoryLevel.EMERGENCY) {
            return false;
        }
        
        // Check if allocation would push us over the critical threshold
        long projectedUsage = status.usedMemory + allocationSize;
        double projectedPercentage = (double) projectedUsage / status.maxMemory;
        
        return projectedPercentage < MEMORY_CRITICAL_THRESHOLD;
    }
    
    /**
     * Get memory optimization recommendations
     * @return Array of recommendation strings
     */
    @NonNull
    public static String[] getOptimizationRecommendations() {
        MemoryStatus status = getMemoryStatus();
        
        switch (status.level) {
            case EMERGENCY:
                return new String[]{
                    "Immediately reduce buffer pool size",
                    "Release all non-essential resources",
                    "Force garbage collection",
                    "Consider reducing processing quality",
                    "Stop non-critical background tasks"
                };
            case CRITICAL:
                return new String[]{
                    "Reduce buffer pool size by 50%",
                    "Clear old cached data",
                    "Suggest garbage collection",
                    "Reduce processing parameters"
                };
            case WARNING:
                return new String[]{
                    "Reduce buffer pool size by 25%",
                    "Clean up old buffers",
                    "Monitor allocation patterns"
                };
            case NORMAL:
            default:
                return new String[]{
                    "Memory usage is normal",
                    "Continue regular operations"
                };
        }
    }
}