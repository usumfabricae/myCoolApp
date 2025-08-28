package com.example.opencvcamerastream.error;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.util.Log;
import androidx.annotation.NonNull;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance monitoring system for graceful degradation
 * 
 * This class provides:
 * - Memory usage monitoring
 * - Frame processing time tracking
 * - Device performance classification
 * - Automatic quality adjustment recommendations
 * - Low-performance device detection
 * 
 * Requirements addressed:
 * - 4.4: Reduce processing complexity to maintain stability when memory is low
 * - 5.3: Automatically adjust processing parameters for optimal performance on lower-end devices
 */
public class PerformanceMonitor {
    
    private static final String TAG = "PerformanceMonitor";
    
    // Performance thresholds
    private static final long MEMORY_WARNING_THRESHOLD = 80; // 80% memory usage
    private static final long MEMORY_CRITICAL_THRESHOLD = 90; // 90% memory usage
    private static final long PROCESSING_TIME_WARNING_MS = 50; // 50ms processing time
    private static final long PROCESSING_TIME_CRITICAL_MS = 100; // 100ms processing time
    private static final int LOW_MEMORY_DEVICE_THRESHOLD_MB = 1024; // 1GB RAM
    
    // Performance levels
    public enum PerformanceLevel {
        HIGH,       // High-end device, full processing
        MEDIUM,     // Mid-range device, moderate processing
        LOW,        // Low-end device, basic processing
        CRITICAL    // Critical performance, minimal processing
    }
    
    // Performance metrics
    public static class PerformanceMetrics {
        public long totalMemoryMB;
        public long availableMemoryMB;
        public long usedMemoryMB;
        public double memoryUsagePercent;
        public long averageProcessingTimeMs;
        public long maxProcessingTimeMs;
        public int frameDropCount;
        public PerformanceLevel currentLevel;
        public boolean isLowMemoryDevice;
        
        @Override
        public String toString() {
            return String.format("PerformanceMetrics{memory=%.1f%%, avgProcessing=%dms, level=%s, lowMemDevice=%s}",
                    memoryUsagePercent, averageProcessingTimeMs, currentLevel, isLowMemoryDevice);
        }
    }
    
    // Performance callback interface
    public interface PerformanceCallback {
        void onPerformanceLevelChanged(@NonNull PerformanceLevel newLevel, @NonNull PerformanceLevel oldLevel);
        void onMemoryWarning(long usedMemoryMB, long totalMemoryMB);
        void onMemoryCritical(long usedMemoryMB, long totalMemoryMB);
        void onProcessingTimeWarning(long processingTimeMs);
        void onFrameDropRecommended(@NonNull String reason);
    }
    
    private final Context context;
    private final ActivityManager activityManager;
    private PerformanceCallback callback;
    
    // Performance tracking
    private PerformanceLevel currentPerformanceLevel = PerformanceLevel.HIGH;
    private boolean isLowMemoryDevice = false;
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicLong processingFrameCount = new AtomicLong(0);
    private final AtomicLong maxProcessingTime = new AtomicLong(0);
    private final AtomicLong frameDropCount = new AtomicLong(0);
    
    // Memory info cache
    private long lastMemoryCheckTime = 0;
    private static final long MEMORY_CHECK_INTERVAL_MS = 1000; // Check every second
    private PerformanceMetrics cachedMetrics;
    
    public PerformanceMonitor(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        
        // Initialize device classification
        classifyDevice();
        
        Log.d(TAG, "PerformanceMonitor initialized, low memory device: " + isLowMemoryDevice);
    }
    
    /**
     * Set performance callback
     */
    public void setPerformanceCallback(@NonNull PerformanceCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Record frame processing time
     */
    public void recordProcessingTime(long processingTimeMs) {
        totalProcessingTime.addAndGet(processingTimeMs);
        processingFrameCount.incrementAndGet();
        
        // Update max processing time
        long currentMax = maxProcessingTime.get();
        while (processingTimeMs > currentMax) {
            if (maxProcessingTime.compareAndSet(currentMax, processingTimeMs)) {
                break;
            }
            currentMax = maxProcessingTime.get();
        }
        
        // Check for processing time warnings
        if (processingTimeMs > PROCESSING_TIME_CRITICAL_MS) {
            Log.w(TAG, "Critical processing time: " + processingTimeMs + "ms");
            if (callback != null) {
                callback.onProcessingTimeWarning(processingTimeMs);
            }
            adjustPerformanceLevel(PerformanceLevel.CRITICAL);
        } else if (processingTimeMs > PROCESSING_TIME_WARNING_MS) {
            Log.w(TAG, "Warning processing time: " + processingTimeMs + "ms");
            if (callback != null) {
                callback.onProcessingTimeWarning(processingTimeMs);
            }
            if (currentPerformanceLevel == PerformanceLevel.HIGH) {
                adjustPerformanceLevel(PerformanceLevel.MEDIUM);
            }
        }
    }
    
    /**
     * Record frame drop
     */
    public void recordFrameDrop(@NonNull String reason) {
        frameDropCount.incrementAndGet();
        Log.d(TAG, "Frame dropped: " + reason);
        
        if (callback != null) {
            callback.onFrameDropRecommended(reason);
        }
    }
    
    /**
     * Get current performance metrics
     */
    public PerformanceMetrics getCurrentMetrics() {
        long currentTime = System.currentTimeMillis();
        
        // Use cached metrics if recent
        if (cachedMetrics != null && (currentTime - lastMemoryCheckTime) < MEMORY_CHECK_INTERVAL_MS) {
            return cachedMetrics;
        }
        
        // Update memory metrics
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        
        long totalMemoryMB = memoryInfo.totalMem / (1024 * 1024);
        long availableMemoryMB = memoryInfo.availMem / (1024 * 1024);
        long usedMemoryMB = totalMemoryMB - availableMemoryMB;
        double memoryUsagePercent = (double) usedMemoryMB / totalMemoryMB * 100;
        
        // Calculate processing metrics
        long frameCount = processingFrameCount.get();
        long avgProcessingTime = frameCount > 0 ? totalProcessingTime.get() / frameCount : 0;
        
        // Create metrics object
        PerformanceMetrics metrics = new PerformanceMetrics();
        metrics.totalMemoryMB = totalMemoryMB;
        metrics.availableMemoryMB = availableMemoryMB;
        metrics.usedMemoryMB = usedMemoryMB;
        metrics.memoryUsagePercent = memoryUsagePercent;
        metrics.averageProcessingTimeMs = avgProcessingTime;
        metrics.maxProcessingTimeMs = maxProcessingTime.get();
        metrics.frameDropCount = (int) frameDropCount.get();
        metrics.currentLevel = currentPerformanceLevel;
        metrics.isLowMemoryDevice = isLowMemoryDevice;
        
        // Check memory thresholds
        if (memoryUsagePercent > MEMORY_CRITICAL_THRESHOLD) {
            Log.w(TAG, "Critical memory usage: " + String.format("%.1f%%", memoryUsagePercent));
            if (callback != null) {
                callback.onMemoryCritical(usedMemoryMB, totalMemoryMB);
            }
            adjustPerformanceLevel(PerformanceLevel.CRITICAL);
        } else if (memoryUsagePercent > MEMORY_WARNING_THRESHOLD) {
            Log.w(TAG, "Warning memory usage: " + String.format("%.1f%%", memoryUsagePercent));
            if (callback != null) {
                callback.onMemoryWarning(usedMemoryMB, totalMemoryMB);
            }
            if (currentPerformanceLevel == PerformanceLevel.HIGH) {
                adjustPerformanceLevel(PerformanceLevel.MEDIUM);
            }
        }
        
        // Cache metrics
        cachedMetrics = metrics;
        lastMemoryCheckTime = currentTime;
        
        return metrics;
    }
    
    /**
     * Get recommended processing configuration based on current performance
     */
    public ProcessingRecommendation getProcessingRecommendation() {
        PerformanceMetrics metrics = getCurrentMetrics();
        
        ProcessingRecommendation recommendation = new ProcessingRecommendation();
        
        switch (currentPerformanceLevel) {
            case HIGH:
                recommendation.enableAdvancedProcessing = true;
                recommendation.maxProcessingTimeMs = 50;
                recommendation.frameSkipRatio = 0;
                recommendation.processingQuality = 1.0f;
                break;
                
            case MEDIUM:
                recommendation.enableAdvancedProcessing = true;
                recommendation.maxProcessingTimeMs = 75;
                recommendation.frameSkipRatio = 1; // Skip every other frame
                recommendation.processingQuality = 0.8f;
                break;
                
            case LOW:
                recommendation.enableAdvancedProcessing = false;
                recommendation.maxProcessingTimeMs = 100;
                recommendation.frameSkipRatio = 2; // Skip 2 out of 3 frames
                recommendation.processingQuality = 0.6f;
                break;
                
            case CRITICAL:
                recommendation.enableAdvancedProcessing = false;
                recommendation.maxProcessingTimeMs = 150;
                recommendation.frameSkipRatio = 4; // Skip 4 out of 5 frames
                recommendation.processingQuality = 0.4f;
                break;
        }
        
        return recommendation;
    }
    
    /**
     * Processing recommendation configuration
     */
    public static class ProcessingRecommendation {
        public boolean enableAdvancedProcessing = true;
        public int maxProcessingTimeMs = 50;
        public int frameSkipRatio = 0; // 0 = no skipping, 1 = skip every other frame, etc.
        public float processingQuality = 1.0f; // 0.0 to 1.0
        
        @Override
        public String toString() {
            return String.format("ProcessingRecommendation{advanced=%s, maxTime=%dms, skipRatio=%d, quality=%.1f}",
                    enableAdvancedProcessing, maxProcessingTimeMs, frameSkipRatio, processingQuality);
        }
    }
    
    /**
     * Force performance level adjustment
     */
    public void adjustPerformanceLevel(@NonNull PerformanceLevel newLevel) {
        if (newLevel != currentPerformanceLevel) {
            PerformanceLevel oldLevel = currentPerformanceLevel;
            currentPerformanceLevel = newLevel;
            
            Log.i(TAG, "Performance level changed: " + oldLevel + " -> " + newLevel);
            
            if (callback != null) {
                callback.onPerformanceLevelChanged(newLevel, oldLevel);
            }
        }
    }
    
    /**
     * Reset performance level to optimal based on device capabilities
     */
    public void resetPerformanceLevel() {
        PerformanceLevel optimalLevel = isLowMemoryDevice ? PerformanceLevel.LOW : PerformanceLevel.HIGH;
        adjustPerformanceLevel(optimalLevel);
        
        Log.d(TAG, "Performance level reset to optimal: " + optimalLevel);
    }
    
    /**
     * Check if device is classified as low-performance
     */
    public boolean isLowPerformanceDevice() {
        return isLowMemoryDevice || currentPerformanceLevel == PerformanceLevel.LOW || 
               currentPerformanceLevel == PerformanceLevel.CRITICAL;
    }
    
    /**
     * Get current performance level
     */
    public PerformanceLevel getCurrentPerformanceLevel() {
        return currentPerformanceLevel;
    }
    
    /**
     * Reset all performance counters
     */
    public void resetCounters() {
        totalProcessingTime.set(0);
        processingFrameCount.set(0);
        maxProcessingTime.set(0);
        frameDropCount.set(0);
        cachedMetrics = null;
        
        Log.d(TAG, "Performance counters reset");
    }
    
    // Private helper methods
    
    /**
     * Release performance monitor resources
     * Called during activity destruction
     */
    public void release() {
        Log.d(TAG, "Releasing PerformanceMonitor resources");
        
        // Clear callback to prevent memory leaks
        callback = null;
        
        // Reset all counters
        resetCounters();
        
        // Clear cached metrics
        cachedMetrics = null;
        lastMemoryCheckTime = 0;
        
        // Reset performance level to initial state
        currentPerformanceLevel = isLowMemoryDevice ? PerformanceLevel.LOW : PerformanceLevel.HIGH;
        
        Log.i(TAG, "PerformanceMonitor resources released");
    }
    
    private void classifyDevice() {
        try {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            
            long totalMemoryMB = memoryInfo.totalMem / (1024 * 1024);
            isLowMemoryDevice = totalMemoryMB < LOW_MEMORY_DEVICE_THRESHOLD_MB;
            
            // Also check if system reports low memory
            if (activityManager.isLowRamDevice()) {
                isLowMemoryDevice = true;
            }
            
            // Set initial performance level based on device classification
            currentPerformanceLevel = isLowMemoryDevice ? PerformanceLevel.LOW : PerformanceLevel.HIGH;
            
            Log.i(TAG, "Device classified - Total memory: " + totalMemoryMB + "MB, " +
                      "Low memory device: " + isLowMemoryDevice + 
                      ", Initial performance level: " + currentPerformanceLevel);
                      
        } catch (Exception e) {
            Log.e(TAG, "Error classifying device", e);
            // Default to safe settings
            isLowMemoryDevice = true;
            currentPerformanceLevel = PerformanceLevel.LOW;
        }
    }
}