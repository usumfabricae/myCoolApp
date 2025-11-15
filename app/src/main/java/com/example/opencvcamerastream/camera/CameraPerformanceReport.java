package com.example.opencvcamerastream.camera;

import android.util.Size;

import androidx.annotation.Nullable;

import com.example.opencvcamerastream.error.PerformanceMonitor;

/**
 * Comprehensive camera performance report
 * 
 * Aggregates frame processing statistics, memory usage, and latency metrics
 * to provide a complete view of camera system performance.
 * 
 * Requirements: NFR-001, NFR-002, NFR-003
 */
public class CameraPerformanceReport {
    
    // Frame processing statistics
    public FrameProcessingStats frameStats;
    
    // Performance metrics from monitor
    @Nullable
    public PerformanceMonitor.PerformanceMetrics performanceMetrics;
    
    // Calculated metrics
    public double currentFrameRate = 0.0;
    public boolean meetingFrameRateTarget = false;
    public boolean meetingMemoryTarget = false;
    public boolean meetingLatencyTarget = false;
    
    // Camera state
    public boolean isInitialized = false;
    public boolean isPreviewActive = false;
    @Nullable
    public Size previewSize;
    public int reconnectionAttempts = 0;
    
    // Report metadata
    public long reportGeneratedAt = System.currentTimeMillis();
    
    /**
     * Get overall performance score (0.0 to 1.0)
     * @return Performance score
     */
    public double getPerformanceScore() {
        double score = 0.0;
        int factors = 0;
        
        // Frame rate score (30 FPS target)
        if (currentFrameRate > 0) {
            score += Math.min(currentFrameRate / 30.0, 1.0);
            factors++;
        }
        
        // Memory score (50 MB target)
        if (performanceMetrics != null && performanceMetrics.usedMemoryMB > 0) {
            score += Math.max(1.0 - (performanceMetrics.usedMemoryMB / 50.0), 0.0);
            factors++;
        }
        
        // Latency score (100ms target)
        if (performanceMetrics != null && performanceMetrics.averageProcessingTimeMs > 0) {
            score += Math.max(1.0 - (performanceMetrics.averageProcessingTimeMs / 100.0), 0.0);
            factors++;
        }
        
        return factors > 0 ? score / factors : 0.0;
    }
    
    /**
     * Get human-readable performance summary
     * @return Performance summary string
     */
    public String getPerformanceSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Camera Performance Report\n");
        summary.append("========================\n");
        
        if (frameStats != null) {
            summary.append("Frame Processing:\n");
            summary.append("  - Total Frames: ").append(frameStats.totalFramesProcessed).append("\n");
            summary.append("  - Dropped Frames: ").append(frameStats.totalFramesDropped).append("\n");
            summary.append("  - Drop Rate: ").append(String.format("%.2f%%", frameStats.getFrameDropRate())).append("\n");
            summary.append("  - Avg Processing: ").append(frameStats.averageProcessingTimeMs).append("ms\n");
        }
        
        if (performanceMetrics != null) {
            summary.append("Performance Metrics:\n");
            summary.append("  - Memory Used: ").append(performanceMetrics.usedMemoryMB).append("MB\n");
            summary.append("  - Memory Target: ").append(meetingMemoryTarget ? "✓ PASS" : "✗ FAIL").append("\n");
            summary.append("  - Avg Latency: ").append(performanceMetrics.averageProcessingTimeMs).append("ms\n");
            summary.append("  - Latency Target: ").append(meetingLatencyTarget ? "✓ PASS" : "✗ FAIL").append("\n");
        }
        
        summary.append("Frame Rate:\n");
        summary.append("  - Current: ").append(String.format("%.1f", currentFrameRate)).append(" FPS\n");
        summary.append("  - Target: ").append(meetingFrameRateTarget ? "✓ PASS" : "✗ FAIL").append("\n");
        
        summary.append("Overall Score: ").append(String.format("%.2f", getPerformanceScore())).append("\n");
        
        return summary.toString();
    }
    
    @Override
    public String toString() {
        return "CameraPerformanceReport{" +
                "frameRate=" + currentFrameRate +
                ", frameStats=" + frameStats +
                ", meetingTargets=" + (meetingFrameRateTarget && meetingMemoryTarget && meetingLatencyTarget) +
                ", score=" + String.format("%.2f", getPerformanceScore()) +
                '}';
    }
}
