package com.example.opencvcamerastream.camera;

import java.util.ArrayList;
import java.util.List;

/**
 * Processing latency metrics tracker
 * 
 * Tracks frame processing latency including average, min, max,
 * and current latency measurements.
 * 
 * Requirements: NFR-003
 */
public class ProcessingLatencyMetrics {
    
    // Latency measurements
    public long currentLatencyMs = 0;
    public long averageLatencyMs = 0;
    public long minLatencyMs = Long.MAX_VALUE;
    public long maxLatencyMs = 0;
    public int measurementCount = 0;
    public boolean meetingLatencyTarget = true;
    
    // Target threshold
    private static final long LATENCY_TARGET_MS = 100;
    
    private List<Long> latencyHistory = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 100;
    
    /**
     * Record a latency measurement
     * @param latencyMs Latency in milliseconds
     */
    public void recordLatency(long latencyMs) {
        currentLatencyMs = latencyMs;
        measurementCount++;
        
        // Update min/max
        minLatencyMs = Math.min(minLatencyMs, latencyMs);
        maxLatencyMs = Math.max(maxLatencyMs, latencyMs);
        
        // Track history for average calculation
        latencyHistory.add(latencyMs);
        if (latencyHistory.size() > MAX_HISTORY_SIZE) {
            latencyHistory.remove(0);
        }
        
        // Calculate average
        long sum = 0;
        for (long time : latencyHistory) {
            sum += time;
        }
        averageLatencyMs = sum / latencyHistory.size();
        
        // Update meeting target flag
        meetingLatencyTarget = averageLatencyMs < LATENCY_TARGET_MS;
    }
    
    /**
     * Check if latency is above target
     * @return true if average latency > 100ms
     */
    public boolean isAboveTarget() {
        return averageLatencyMs > LATENCY_TARGET_MS;
    }
    
    /**
     * Get performance score based on latency
     * @return Score from 0.0 to 1.0
     */
    public double getPerformanceScore() {
        if (averageLatencyMs == 0) {
            return 1.0;
        }
        return Math.max(1.0 - (averageLatencyMs / (double) LATENCY_TARGET_MS), 0.0);
    }
    
    /**
     * Get latency status string
     * @return Status: OPTIMAL, WARNING, or ABOVE_TARGET
     */
    public String getLatencyStatus() {
        if (averageLatencyMs <= 50) {
            return "OPTIMAL";
        } else if (averageLatencyMs <= LATENCY_TARGET_MS) {
            return "WARNING";
        } else {
            return "ABOVE_TARGET";
        }
    }
    
    /**
     * Get latency metrics summary
     * @return Summary string
     */
    public String getLatencyMetricsSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Processing Latency Metrics\n");
        summary.append("==========================\n");
        summary.append("Current Latency: ").append(currentLatencyMs).append("ms\n");
        summary.append("Average Latency: ").append(averageLatencyMs).append("ms\n");
        summary.append("Min Latency: ").append(minLatencyMs == Long.MAX_VALUE ? "N/A" : minLatencyMs).append("ms\n");
        summary.append("Max Latency: ").append(maxLatencyMs).append("ms\n");
        summary.append("Measurement Count: ").append(measurementCount).append("\n");
        summary.append("Target: ").append(LATENCY_TARGET_MS).append("ms\n");
        summary.append("Status: ").append(getLatencyStatus()).append("\n");
        summary.append("Performance Score: ").append(String.format("%.2f", getPerformanceScore())).append("\n");
        
        return summary.toString();
    }
    
    /**
     * Reset all metrics
     */
    public void reset() {
        currentLatencyMs = 0;
        averageLatencyMs = 0;
        minLatencyMs = Long.MAX_VALUE;
        maxLatencyMs = 0;
        measurementCount = 0;
        latencyHistory.clear();
    }
    
    @Override
    public String toString() {
        return "ProcessingLatencyMetrics{" +
                "current=" + currentLatencyMs + "ms" +
                ", avg=" + averageLatencyMs + "ms" +
                ", status=" + getLatencyStatus() +
                ", score=" + String.format("%.2f", getPerformanceScore()) +
                '}';
    }
}
