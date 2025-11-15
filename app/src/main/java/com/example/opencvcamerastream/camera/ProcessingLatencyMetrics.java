package com.example.opencvcamerastream.camera;

import androidx.annotation.NonNull;

/**
 * Processing latency metrics for camera operations
 * 
 * Requirements addressed:
 * - NFR-003: Processing latency measurement (<100ms target)
 */
public class ProcessingLatencyMetrics {
    
    // Latency measurements
    public long currentLatencyMs = 0;
    public long averageLatencyMs = 0;
    public long minLatencyMs = Long.MAX_VALUE;
    public long maxLatencyMs = 0;
    
    // Performance targets
    public static final long LATENCY_TARGET_MS = 100;
    public static final long LATENCY_WARNING_MS = 75;
    
    // Performance indicators
    public boolean meetingLatencyTarget = false;
    
    // Statistics
    public long measurementCount = 0;
    public long totalLatencyMs = 0;
    
    /**
     * Record a new latency measurement
     * Requirements: NFR-003
     */
    public void recordLatency(long latencyMs) {
        currentLatencyMs = latencyMs;
        measurementCount++;
        totalLatencyMs += latencyMs;
        
        // Update min/max
        if (latencyMs < minLatencyMs) {
            minLatencyMs = latencyMs;
        }
        if (latencyMs > maxLatencyMs) {
            maxLatencyMs = latencyMs;
        }
        
        // Calculate average
        averageLatencyMs = totalLatencyMs / measurementCount;
        
        // Check target compliance
        meetingLatencyTarget = latencyMs < LATENCY_TARGET_MS;
    }
    
    /**
     * Check if latency is within target
     * Requirements: NFR-003
     */
    public boolean isWithinTarget() {
        return currentLatencyMs < LATENCY_TARGET_MS;
    }
    
    /**
     * Check if latency is at warning level
     * Requirements: NFR-003
     */
    public boolean isAtWarningLevel() {
        return currentLatencyMs >= LATENCY_WARNING_MS && currentLatencyMs < LATENCY_TARGET_MS;
    }
    
    /**
     * Check if latency is above target
     * Requirements: NFR-003
     */
    public boolean isAboveTarget() {
        return currentLatencyMs >= LATENCY_TARGET_MS;
    }
    
    /**
     * Get latency status description
     */
    @NonNull
    public String getLatencyStatus() {
        if (isAboveTarget()) {
            return "ABOVE_TARGET";
        } else if (isAtWarningLevel()) {
            return "WARNING";
        } else {
            return "OPTIMAL";
        }
    }
    
    /**
     * Get performance score (0.0 to 1.0)
     * Requirements: NFR-009
     */
    public double getPerformanceScore() {
        if (currentLatencyMs == 0) {
            return 1.0;
        }
        
        // Score based on how close to target we are
        if (currentLatencyMs <= LATENCY_WARNING_MS) {
            return 1.0;
        } else if (currentLatencyMs <= LATENCY_TARGET_MS) {
            return 0.8;
        } else if (currentLatencyMs <= LATENCY_TARGET_MS * 1.5) {
            return 0.6;
        } else if (currentLatencyMs <= LATENCY_TARGET_MS * 2) {
            return 0.4;
        } else {
            return 0.2;
        }
    }
    
    /**
     * Reset all metrics
     */
    public void reset() {
        currentLatencyMs = 0;
        averageLatencyMs = 0;
        minLatencyMs = Long.MAX_VALUE;
        maxLatencyMs = 0;
        meetingLatencyTarget = false;
        measurementCount = 0;
        totalLatencyMs = 0;
    }
    
    /**
     * Get latency metrics summary
     */
    @NonNull
    public String getLatencyMetricsSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Processing Latency Metrics:\n");
        summary.append(String.format("  Current Latency: %d ms\n", currentLatencyMs));
        summary.append(String.format("  Average Latency: %d ms\n", averageLatencyMs));
        summary.append(String.format("  Min Latency: %d ms\n", minLatencyMs == Long.MAX_VALUE ? 0 : minLatencyMs));
        summary.append(String.format("  Max Latency: %d ms\n", maxLatencyMs));
        summary.append(String.format("  Target: <%d ms - %s\n", LATENCY_TARGET_MS, isWithinTarget() ? "✓" : "✗"));
        summary.append(String.format("  Status: %s\n", getLatencyStatus()));
        summary.append(String.format("  Performance Score: %.1f%%\n", getPerformanceScore() * 100));
        summary.append(String.format("  Measurements: %d\n", measurementCount));
        return summary.toString();
    }
    
    @Override
    public String toString() {
        return String.format("ProcessingLatencyMetrics{current=%dms, avg=%dms, target=%s, score=%.1f%%}", 
                currentLatencyMs, averageLatencyMs, isWithinTarget() ? "✓" : "✗", getPerformanceScore() * 100);
    }
}