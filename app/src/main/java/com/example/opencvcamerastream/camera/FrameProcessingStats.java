package com.example.opencvcamerastream.camera;

import androidx.annotation.NonNull;

/**
 * Frame processing statistics for camera operations
 * 
 * Requirements addressed:
 * - NFR-001: Frame rate monitoring and tracking
 * - NFR-003: Processing latency measurement
 */
public class FrameProcessingStats {
    
    // Frame counters
    public long totalFramesProcessed = 0;
    public long totalFramesDropped = 0;
    public long totalFramesCaptured = 0;
    
    // Timing statistics
    public long totalProcessingTimeMs = 0;
    public long minProcessingTimeMs = Long.MAX_VALUE;
    public long maxProcessingTimeMs = 0;
    public long averageProcessingTimeMs = 0;
    
    // Frame rate statistics
    public double currentFrameRate = 0.0;
    public double averageFrameRate = 0.0;
    public double targetFrameRate = 30.0;
    
    // Session timing
    public long sessionStartTime = System.currentTimeMillis();
    public long lastFrameTime = 0;
    public long sessionDurationMs = 0;
    
    // Performance indicators
    public boolean meetingFrameRateTarget = false;
    public boolean meetingLatencyTarget = false;
    
    /**
     * Update statistics with new frame processing time
     * Requirements: NFR-001, NFR-003
     */
    public void recordFrameProcessed(long processingTimeMs) {
        totalFramesProcessed++;
        totalProcessingTimeMs += processingTimeMs;
        
        // Update min/max processing times
        if (processingTimeMs < minProcessingTimeMs) {
            minProcessingTimeMs = processingTimeMs;
        }
        if (processingTimeMs > maxProcessingTimeMs) {
            maxProcessingTimeMs = processingTimeMs;
        }
        
        // Calculate average processing time
        averageProcessingTimeMs = totalProcessingTimeMs / totalFramesProcessed;
        
        // Update frame rate
        long currentTime = System.currentTimeMillis();
        if (lastFrameTime > 0) {
            long frameInterval = currentTime - lastFrameTime;
            if (frameInterval > 0) {
                currentFrameRate = 1000.0 / frameInterval;
            }
        }
        lastFrameTime = currentTime;
        
        // Calculate session-based average frame rate
        sessionDurationMs = currentTime - sessionStartTime;
        if (sessionDurationMs > 0) {
            averageFrameRate = (totalFramesProcessed * 1000.0) / sessionDurationMs;
        }
        
        // Check performance targets
        meetingFrameRateTarget = currentFrameRate >= targetFrameRate;
        meetingLatencyTarget = processingTimeMs < 100; // 100ms target
    }
    
    /**
     * Record a dropped frame
     * Requirements: NFR-001
     */
    public void recordFrameDropped() {
        totalFramesDropped++;
    }
    
    /**
     * Record a captured frame (before processing)
     * Requirements: NFR-001
     */
    public void recordFrameCaptured() {
        totalFramesCaptured++;
    }
    
    /**
     * Get frame drop rate as percentage
     * Requirements: NFR-001
     */
    public double getFrameDropRate() {
        if (totalFramesCaptured == 0) {
            return 0.0;
        }
        return (double) totalFramesDropped / totalFramesCaptured * 100.0;
    }
    
    /**
     * Get processing efficiency (frames processed vs captured)
     * Requirements: NFR-001
     */
    public double getProcessingEfficiency() {
        if (totalFramesCaptured == 0) {
            return 0.0;
        }
        return (double) totalFramesProcessed / totalFramesCaptured * 100.0;
    }
    
    /**
     * Check if performance is optimal
     * Requirements: NFR-001, NFR-003
     */
    public boolean isPerformanceOptimal() {
        return meetingFrameRateTarget && meetingLatencyTarget && getFrameDropRate() < 5.0;
    }
    
    /**
     * Reset all statistics
     */
    public void reset() {
        totalFramesProcessed = 0;
        totalFramesDropped = 0;
        totalFramesCaptured = 0;
        totalProcessingTimeMs = 0;
        minProcessingTimeMs = Long.MAX_VALUE;
        maxProcessingTimeMs = 0;
        averageProcessingTimeMs = 0;
        currentFrameRate = 0.0;
        averageFrameRate = 0.0;
        sessionStartTime = System.currentTimeMillis();
        lastFrameTime = 0;
        sessionDurationMs = 0;
        meetingFrameRateTarget = false;
        meetingLatencyTarget = false;
    }
    
    /**
     * Get detailed statistics summary
     */
    @NonNull
    public String getDetailedSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Frame Processing Statistics:\n");
        summary.append(String.format("  Frames Captured: %d\n", totalFramesCaptured));
        summary.append(String.format("  Frames Processed: %d\n", totalFramesProcessed));
        summary.append(String.format("  Frames Dropped: %d (%.1f%%)\n", totalFramesDropped, getFrameDropRate()));
        summary.append(String.format("  Processing Efficiency: %.1f%%\n", getProcessingEfficiency()));
        summary.append(String.format("  Current Frame Rate: %.1f FPS\n", currentFrameRate));
        summary.append(String.format("  Average Frame Rate: %.1f FPS\n", averageFrameRate));
        summary.append(String.format("  Target Frame Rate: %.1f FPS\n", targetFrameRate));
        summary.append(String.format("  Average Processing Time: %d ms\n", averageProcessingTimeMs));
        summary.append(String.format("  Min Processing Time: %d ms\n", minProcessingTimeMs == Long.MAX_VALUE ? 0 : minProcessingTimeMs));
        summary.append(String.format("  Max Processing Time: %d ms\n", maxProcessingTimeMs));
        summary.append(String.format("  Session Duration: %.1f seconds\n", sessionDurationMs / 1000.0));
        summary.append(String.format("  Performance Optimal: %s\n", isPerformanceOptimal() ? "Yes" : "No"));
        return summary.toString();
    }
    
    @Override
    public String toString() {
        return String.format("FrameProcessingStats{processed=%d, dropped=%d, fps=%.1f, avgLatency=%dms}", 
                totalFramesProcessed, totalFramesDropped, currentFrameRate, averageProcessingTimeMs);
    }
}