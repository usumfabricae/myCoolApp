package com.example.opencvcamerastream.camera;

/**
 * Camera memory usage tracking
 * 
 * Tracks memory usage specific to camera operations including
 * buffer management and memory status.
 * 
 * Requirements: NFR-002
 */
public class CameraMemoryUsage {
    
    // Memory metrics
    public long totalMemoryMB = 0;
    public long usedMemoryMB = 0;
    public double memoryUsagePercent = 0.0;
    
    // Camera-specific memory
    public long estimatedCameraBufferMB = 0;
    
    // Buffer management
    public int currentBufferCount = 0;
    public int maxBufferCount = 3;
    
    // Target thresholds
    private static final long MEMORY_TARGET_MB = 50;
    private static final double WARNING_THRESHOLD_PERCENT = 70.0;
    private static final double CRITICAL_THRESHOLD_PERCENT = 85.0;
    
    /**
     * Check if memory usage is within target
     * @return true if within target (< 50 MB)
     */
    public boolean isWithinTarget() {
        return usedMemoryMB < MEMORY_TARGET_MB;
    }
    
    /**
     * Check if memory usage is at warning level
     * @return true if at warning level (70-85%)
     */
    public boolean isAtWarningLevel() {
        return memoryUsagePercent >= WARNING_THRESHOLD_PERCENT && 
               memoryUsagePercent < CRITICAL_THRESHOLD_PERCENT;
    }
    
    /**
     * Check if memory usage is at critical level
     * @return true if at critical level (>= 85%)
     */
    public boolean isAtCriticalLevel() {
        return memoryUsagePercent >= CRITICAL_THRESHOLD_PERCENT;
    }
    
    /**
     * Get available memory in MB
     * @return Available memory
     */
    public long getAvailableMemoryMB() {
        return totalMemoryMB - usedMemoryMB;
    }
    
    /**
     * Get buffer utilization percentage
     * @return Buffer utilization (0-100)
     */
    public double getBufferUtilization() {
        if (maxBufferCount == 0) {
            return 0.0;
        }
        return (currentBufferCount * 100.0) / maxBufferCount;
    }
    
    /**
     * Get memory status string
     * @return Status: OPTIMAL, WARNING, CRITICAL, or ABOVE_TARGET
     */
    public String getMemoryStatus() {
        if (isAtCriticalLevel()) {
            return "CRITICAL";
        } else if (isAtWarningLevel()) {
            return "WARNING";
        } else if (!isWithinTarget()) {
            return "ABOVE_TARGET";
        } else {
            return "OPTIMAL";
        }
    }
    
    /**
     * Get memory usage summary
     * @return Summary string
     */
    public String getMemoryUsageSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Memory Usage Summary\n");
        summary.append("===================\n");
        summary.append("Total Memory: ").append(totalMemoryMB).append("MB\n");
        summary.append("Used Memory: ").append(usedMemoryMB).append("MB\n");
        summary.append("Available Memory: ").append(getAvailableMemoryMB()).append("MB\n");
        summary.append("Usage Percent: ").append(String.format("%.1f%%", memoryUsagePercent)).append("\n");
        summary.append("Camera Buffer: ").append(estimatedCameraBufferMB).append("MB\n");
        summary.append("Buffer Count: ").append(currentBufferCount).append("/").append(maxBufferCount).append("\n");
        summary.append("Status: ").append(getMemoryStatus()).append("\n");
        
        return summary.toString();
    }
    
    @Override
    public String toString() {
        return "CameraMemoryUsage{" +
                "used=" + usedMemoryMB + "MB" +
                ", total=" + totalMemoryMB + "MB" +
                ", percent=" + String.format("%.1f%%", memoryUsagePercent) +
                ", status=" + getMemoryStatus() +
                '}';
    }
}
