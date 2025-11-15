package com.example.opencvcamerastream.camera;

import androidx.annotation.NonNull;

/**
 * Camera-specific memory usage tracking
 * 
 * Requirements addressed:
 * - NFR-002: Memory usage tracking (<50 MB target)
 */
public class CameraMemoryUsage {
    
    // System memory information
    public long totalMemoryMB = 0;
    public long usedMemoryMB = 0;
    public double memoryUsagePercent = 0.0;
    
    // Camera-specific memory estimates
    public long estimatedCameraBufferMB = 0;
    public int currentBufferCount = 0;
    public int maxBufferCount = 0;
    
    // Memory thresholds
    public static final long MEMORY_TARGET_MB = 50;
    public static final double MEMORY_WARNING_THRESHOLD = 80.0; // 80%
    public static final double MEMORY_CRITICAL_THRESHOLD = 90.0; // 90%
    
    /**
     * Check if memory usage is within target
     * Requirements: NFR-002
     */
    public boolean isWithinTarget() {
        return usedMemoryMB < MEMORY_TARGET_MB;
    }
    
    /**
     * Check if memory usage is at warning level
     * Requirements: NFR-002
     */
    public boolean isAtWarningLevel() {
        return memoryUsagePercent >= MEMORY_WARNING_THRESHOLD && memoryUsagePercent < MEMORY_CRITICAL_THRESHOLD;
    }
    
    /**
     * Check if memory usage is at critical level
     * Requirements: NFR-002
     */
    public boolean isAtCriticalLevel() {
        return memoryUsagePercent >= MEMORY_CRITICAL_THRESHOLD;
    }
    
    /**
     * Get memory status description
     */
    @NonNull
    public String getMemoryStatus() {
        if (isAtCriticalLevel()) {
            return "CRITICAL";
        } else if (isAtWarningLevel()) {
            return "WARNING";
        } else if (isWithinTarget()) {
            return "OPTIMAL";
        } else {
            return "ABOVE_TARGET";
        }
    }
    
    /**
     * Get buffer utilization percentage
     */
    public double getBufferUtilization() {
        if (maxBufferCount == 0) {
            return 0.0;
        }
        return (double) currentBufferCount / maxBufferCount * 100.0;
    }
    
    /**
     * Get available memory in MB
     */
    public long getAvailableMemoryMB() {
        return totalMemoryMB - usedMemoryMB;
    }
    
    /**
     * Get memory usage summary
     */
    @NonNull
    public String getMemoryUsageSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Camera Memory Usage:\n");
        summary.append(String.format("  Total Memory: %d MB\n", totalMemoryMB));
        summary.append(String.format("  Used Memory: %d MB (%.1f%%)\n", usedMemoryMB, memoryUsagePercent));
        summary.append(String.format("  Available Memory: %d MB\n", getAvailableMemoryMB()));
        summary.append(String.format("  Target: <%d MB - %s\n", MEMORY_TARGET_MB, isWithinTarget() ? "✓" : "✗"));
        summary.append(String.format("  Status: %s\n", getMemoryStatus()));
        summary.append(String.format("  Camera Buffers: %d/%d (%.1f%%) - ~%d MB\n", 
                currentBufferCount, maxBufferCount, getBufferUtilization(), estimatedCameraBufferMB));
        return summary.toString();
    }
    
    @Override
    public String toString() {
        return String.format("CameraMemoryUsage{used=%dMB/%.1f%%, buffers=%d/%d, status=%s}", 
                usedMemoryMB, memoryUsagePercent, currentBufferCount, maxBufferCount, getMemoryStatus());
    }
}