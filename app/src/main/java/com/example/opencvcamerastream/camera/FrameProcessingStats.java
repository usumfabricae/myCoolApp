package com.example.opencvcamerastream.camera;

import java.util.ArrayList;
import java.util.List;

/**
 * Frame processing statistics tracker
 * 
 * Tracks frame processing metrics including frame count, drop rate,
 * and processing time statistics.
 * 
 * Requirements: NFR-001, NFR-003
 */
public class FrameProcessingStats {
    
    public long totalFramesProcessed = 0;
    public long totalFramesDropped = 0;
    public long averageProcessingTimeMs = 0;
    public long minProcessingTimeMs = Long.MAX_VALUE;
    public long maxProcessingTimeMs = 0;
    
    private List<Long> processingTimes = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 100;
    
    /**
     * Record a successfully processed frame
     * @param processingTimeMs Time taken to process the frame
     */
    public void recordFrameProcessed(long processingTimeMs) {
        totalFramesProcessed++;
        
        // Update min/max
        minProcessingTimeMs = Math.min(minProcessingTimeMs, processingTimeMs);
        maxProcessingTimeMs = Math.max(maxProcessingTimeMs, processingTimeMs);
        
        // Track processing times for average calculation
        processingTimes.add(processingTimeMs);
        if (processingTimes.size() > MAX_HISTORY_SIZE) {
            processingTimes.remove(0);
        }
        
        // Calculate average
        long sum = 0;
        for (long time : processingTimes) {
            sum += time;
        }
        averageProcessingTimeMs = sum / processingTimes.size();
    }
    
    /**
     * Record a dropped frame
     */
    public void recordFrameDropped() {
        totalFramesDropped++;
    }
    
    /**
     * Get frame drop rate as percentage
     * @return Drop rate (0.0 to 100.0)
     */
    public double getFrameDropRate() {
        if (totalFramesProcessed + totalFramesDropped == 0) {
            return 0.0;
        }
        return (totalFramesDropped * 100.0) / (totalFramesProcessed + totalFramesDropped);
    }
    
    /**
     * Get detailed statistics summary
     * @return Detailed summary string
     */
    public String getDetailedSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Frame Processing Statistics\n");
        summary.append("===========================\n");
        summary.append("Total Processed: ").append(totalFramesProcessed).append("\n");
        summary.append("Total Dropped: ").append(totalFramesDropped).append("\n");
        summary.append("Drop Rate: ").append(String.format("%.2f%%", getFrameDropRate())).append("\n");
        summary.append("Avg Processing Time: ").append(averageProcessingTimeMs).append("ms\n");
        summary.append("Min Processing Time: ").append(minProcessingTimeMs == Long.MAX_VALUE ? "N/A" : minProcessingTimeMs).append("ms\n");
        summary.append("Max Processing Time: ").append(maxProcessingTimeMs).append("ms\n");
        
        return summary.toString();
    }
    
    /**
     * Reset all statistics
     */
    public void reset() {
        totalFramesProcessed = 0;
        totalFramesDropped = 0;
        averageProcessingTimeMs = 0;
        minProcessingTimeMs = Long.MAX_VALUE;
        maxProcessingTimeMs = 0;
        processingTimes.clear();
    }
    
    @Override
    public String toString() {
        return "FrameProcessingStats{" +
                "processed=" + totalFramesProcessed +
                ", dropped=" + totalFramesDropped +
                ", dropRate=" + String.format("%.2f%%", getFrameDropRate()) +
                ", avgTime=" + averageProcessingTimeMs + "ms" +
                '}';
    }
}
