package com.example.opencvcamerastream.camera;

import android.util.Size;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.opencvcamerastream.error.PerformanceMonitor;

/**
 * Comprehensive camera performance report
 * 
 * Requirements addressed:
 * - NFR-001: Frame rate monitoring (30 FPS target)
 * - NFR-002: Memory usage tracking (<50 MB target)
 * - NFR-003: Processing latency measurement (<100ms target)
 */
public class CameraPerformanceReport {
    
    // Frame processing statistics
    @Nullable
    public FrameProcessingStats frameStats;
    
    // Performance metrics from monitor
    @Nullable
    public PerformanceMonitor.PerformanceMetrics performanceMetrics;
    
    // Current frame rate
    public double currentFrameRate = 0.0;
    
    // Performance target compliance
    public boolean meetingFrameRateTarget = false;
    public boolean meetingMemoryTarget = false;
    public boolean meetingLatencyTarget = false;
    
    // Camera state information
    public boolean isInitialized = false;
    public boolean isPreviewActive = false;
    @Nullable
    public Size previewSize;
    public int reconnectionAttempts = 0;
    
    // Timestamps
    public long reportGeneratedAt = System.currentTimeMillis();
    public long sessionStartTime = 0;
    
    /**
     * Check if all performance targets are being met
     * Requirements: NFR-001, NFR-002, NFR-003
     */
    public boolean isPerformanceOptimal() {
        return meetingFrameRateTarget && meetingMemoryTarget && meetingLatencyTarget;
    }
    
    /**
     * Get overall performance score (0.0 to 1.0)
     * Requirements: NFR-009
     */
    public double getPerformanceScore() {
        int targetsMetCount = 0;
        int totalTargets = 3;
        
        if (meetingFrameRateTarget) targetsMetCount++;
        if (meetingMemoryTarget) targetsMetCount++;
        if (meetingLatencyTarget) targetsMetCount++;
        
        return (double) targetsMetCount / totalTargets;
    }
    
    /**
     * Get performance summary string
     */
    @NonNull
    public String getPerformanceSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Camera Performance Report:\n");
        summary.append(String.format("  Frame Rate: %.1f FPS (Target: 30 FPS) - %s\n", 
                currentFrameRate, meetingFrameRateTarget ? "✓" : "✗"));
        
        if (performanceMetrics != null) {
            summary.append(String.format("  Memory Usage: %d MB (Target: <50 MB) - %s\n", 
                    performanceMetrics.usedMemoryMB, meetingMemoryTarget ? "✓" : "✗"));
            summary.append(String.format("  Processing Latency: %d ms (Target: <100 ms) - %s\n", 
                    performanceMetrics.averageProcessingTimeMs, meetingLatencyTarget ? "✓" : "✗"));
        }
        
        summary.append(String.format("  Overall Score: %.1f%% (%s)\n", 
                getPerformanceScore() * 100, isPerformanceOptimal() ? "Optimal" : "Needs Improvement"));
        
        if (frameStats != null) {
            summary.append(String.format("  Frames Processed: %d\n", frameStats.totalFramesProcessed));
            summary.append(String.format("  Frames Dropped: %d\n", frameStats.totalFramesDropped));
            if (frameStats.totalFramesProcessed > 0) {
                double dropRate = (double) frameStats.totalFramesDropped / frameStats.totalFramesProcessed * 100;
                summary.append(String.format("  Drop Rate: %.1f%%\n", dropRate));
            }
        }
        
        return summary.toString();
    }
    
    @Override
    public String toString() {
        return String.format("CameraPerformanceReport{fps=%.1f, optimal=%s, score=%.1f%%}", 
                currentFrameRate, isPerformanceOptimal(), getPerformanceScore() * 100);
    }
}