package com.example.opencvcamerastream.performance;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.opencvcamerastream.error.PerformanceMonitor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance metrics collector for frame rate monitoring and optimization
 * 
 * This class provides:
 * - Real-time frame rate monitoring and display
 * - Processing time tracking and analysis
 * - Automatic quality adjustment based on performance
 * - Frame dropping mechanism for maintaining target FPS
 * - Performance optimization recommendations
 * 
 * Requirements addressed:
 * - 1.3: Maintain smooth frame rate of at least 10 FPS
 * - 2.3: Return processed frame within 50ms
 * - 5.1: Initialize within 3 seconds
 * - 5.3: Automatically adjust processing parameters for optimal performance
 */
public class PerformanceMetricsCollector {
    
    private static final String TAG = "PerformanceMetricsCollector";
    
    // Performance targets and thresholds
    private static final int TARGET_FPS = 30;
    private static final int MIN_FPS = 10; // Requirement 1.3
    private static final int MAX_PROCESSING_TIME_MS = 50; // Requirement 2.3
    private static final int FRAME_RATE_CALCULATION_WINDOW_MS = 1000; // 1 second window
    private static final int PERFORMANCE_UPDATE_INTERVAL_MS = 500; // Update UI every 500ms
    
    // Frame dropping thresholds
    private static final int FRAME_DROP_THRESHOLD_MS = 33; // ~30 FPS
    private static final int AGGRESSIVE_FRAME_DROP_THRESHOLD_MS = 100; // ~10 FPS
    
    // Performance metrics
    public static class FrameRateMetrics {
        public float currentFps = 0.0f;
        public float averageFps = 0.0f;
        public long totalFrames = 0;
        public long droppedFrames = 0;
        public float dropRate = 0.0f;
        public long averageProcessingTimeMs = 0;
        public long maxProcessingTimeMs = 0;
        public PerformanceMonitor.PerformanceLevel currentLevel = PerformanceMonitor.PerformanceLevel.HIGH;
        public boolean isFrameDropping = false;
        
        @Override
        public String toString() {
            return String.format("FrameRateMetrics{fps=%.1f, avgFps=%.1f, drops=%d/%d (%.1f%%), " +
                    "avgProcessing=%dms, level=%s, dropping=%s}",
                    currentFps, averageFps, droppedFrames, totalFrames, dropRate,
                    averageProcessingTimeMs, currentLevel, isFrameDropping);
        }
    }
    
    // Performance callback interface
    public interface PerformanceMetricsCallback {
        void onFrameRateUpdate(@NonNull FrameRateMetrics metrics);
        void onPerformanceAdjustment(@NonNull PerformanceAdjustment adjustment);
        void onFrameDropRecommended(@NonNull String reason);
    }
    
    // Performance adjustment recommendations
    public static class PerformanceAdjustment {
        public PerformanceMonitor.PerformanceLevel recommendedLevel;
        public boolean enableFrameDropping;
        public int frameSkipRatio; // 0 = no skipping, 1 = skip every other frame, etc.
        public float qualityReduction; // 0.0 to 1.0, where 1.0 is full quality
        public int maxProcessingTimeMs;
        public String reason;
        
        public PerformanceAdjustment(@NonNull PerformanceMonitor.PerformanceLevel level, 
                                   boolean enableDropping, int skipRatio, float quality, 
                                   int maxTime, @NonNull String reason) {
            this.recommendedLevel = level;
            this.enableFrameDropping = enableDropping;
            this.frameSkipRatio = skipRatio;
            this.qualityReduction = quality;
            this.maxProcessingTimeMs = maxTime;
            this.reason = reason;
        }
        
        @Override
        public String toString() {
            return String.format("PerformanceAdjustment{level=%s, dropping=%s, skipRatio=%d, " +
                    "quality=%.1f, maxTime=%dms, reason='%s'}",
                    recommendedLevel, enableFrameDropping, frameSkipRatio, 
                    qualityReduction, maxProcessingTimeMs, reason);
        }
    }
    
    private final PerformanceMonitor performanceMonitor;
    private PerformanceMetricsCallback callback;
    
    // Frame rate tracking
    private final AtomicLong frameCount = new AtomicLong(0);
    private final AtomicLong droppedFrameCount = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicLong maxProcessingTime = new AtomicLong(0);
    private final AtomicInteger frameSkipCounter = new AtomicInteger(0);
    
    // Timing tracking
    private long lastFrameTime = 0;
    private long lastFpsCalculationTime = 0;
    private long framesInWindow = 0;
    private float currentFps = 0.0f;
    private float averageFps = 0.0f;
    
    // Performance state
    private boolean isFrameDroppingEnabled = false;
    private int currentFrameSkipRatio = 0;
    private PerformanceMonitor.PerformanceLevel lastRecommendedLevel = PerformanceMonitor.PerformanceLevel.HIGH;
    
    // UI update handler
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable performanceUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updatePerformanceMetrics();
            uiHandler.postDelayed(this, PERFORMANCE_UPDATE_INTERVAL_MS);
        }
    };
    
    private boolean isMonitoring = false;
    
    public PerformanceMetricsCollector(@NonNull PerformanceMonitor performanceMonitor) {
        this.performanceMonitor = performanceMonitor;
        Log.d(TAG, "PerformanceMetricsCollector initialized");
    }
    
    /**
     * Set performance metrics callback
     */
    public void setCallback(@Nullable PerformanceMetricsCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Start performance monitoring
     */
    public void startMonitoring() {
        if (!isMonitoring) {
            isMonitoring = true;
            lastFrameTime = System.currentTimeMillis();
            lastFpsCalculationTime = lastFrameTime;
            
            // Start periodic UI updates
            uiHandler.post(performanceUpdateRunnable);
            
            Log.d(TAG, "Performance monitoring started");
        }
    }
    
    /**
     * Stop performance monitoring
     */
    public void stopMonitoring() {
        if (isMonitoring) {
            isMonitoring = false;
            uiHandler.removeCallbacks(performanceUpdateRunnable);
            Log.d(TAG, "Performance monitoring stopped");
        }
    }
    
    /**
     * Record frame processing completion
     */
    public void recordFrameProcessed(long processingTimeMs) {
        if (!isMonitoring) return;
        
        long currentTime = System.currentTimeMillis();
        frameCount.incrementAndGet();
        totalProcessingTime.addAndGet(processingTimeMs);
        
        // Update max processing time
        long currentMax = maxProcessingTime.get();
        while (processingTimeMs > currentMax) {
            if (maxProcessingTime.compareAndSet(currentMax, processingTimeMs)) {
                break;
            }
            currentMax = maxProcessingTime.get();
        }
        
        // Record processing time with performance monitor
        performanceMonitor.recordProcessingTime(processingTimeMs);
        
        // Check if frame dropping should be recommended
        checkFrameDropRecommendation(processingTimeMs, currentTime);
        
        lastFrameTime = currentTime;
    }
    
    /**
     * Record frame drop
     */
    public void recordFrameDropped(@NonNull String reason) {
        if (!isMonitoring) return;
        
        droppedFrameCount.incrementAndGet();
        performanceMonitor.recordFrameDrop(reason);
        
        if (callback != null) {
            callback.onFrameDropRecommended(reason);
        }
        
        Log.v(TAG, "Frame dropped: " + reason);
    }
    
    /**
     * Check if frame should be dropped based on performance
     */
    public boolean shouldDropFrame() {
        if (!isFrameDroppingEnabled || currentFrameSkipRatio <= 0) {
            return false;
        }
        
        int skipCount = frameSkipCounter.incrementAndGet();
        boolean shouldDrop = (skipCount % (currentFrameSkipRatio + 1)) != 0;
        
        if (shouldDrop) {
            recordFrameDropped("Performance optimization - skip ratio " + currentFrameSkipRatio);
        }
        
        return shouldDrop;
    }
    
    /**
     * Get current frame rate metrics
     */
    public FrameRateMetrics getCurrentMetrics() {
        FrameRateMetrics metrics = new FrameRateMetrics();
        
        long totalFrames = frameCount.get();
        long droppedFrames = droppedFrameCount.get();
        
        metrics.currentFps = currentFps;
        metrics.averageFps = averageFps;
        metrics.totalFrames = totalFrames;
        metrics.droppedFrames = droppedFrames;
        metrics.dropRate = totalFrames > 0 ? (float) droppedFrames / totalFrames * 100 : 0;
        metrics.averageProcessingTimeMs = totalFrames > 0 ? totalProcessingTime.get() / totalFrames : 0;
        metrics.maxProcessingTimeMs = maxProcessingTime.get();
        metrics.currentLevel = performanceMonitor.getCurrentPerformanceLevel();
        metrics.isFrameDropping = isFrameDroppingEnabled;
        
        return metrics;
    }
    
    /**
     * Reset all performance counters
     */
    public void resetCounters() {
        frameCount.set(0);
        droppedFrameCount.set(0);
        totalProcessingTime.set(0);
        maxProcessingTime.set(0);
        frameSkipCounter.set(0);
        
        currentFps = 0.0f;
        averageFps = 0.0f;
        framesInWindow = 0;
        
        lastFrameTime = System.currentTimeMillis();
        lastFpsCalculationTime = lastFrameTime;
        
        Log.d(TAG, "Performance counters reset");
    }
    
    /**
     * Enable or disable frame dropping
     */
    public void setFrameDroppingEnabled(boolean enabled) {
        isFrameDroppingEnabled = enabled;
        Log.d(TAG, "Frame dropping " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Set frame skip ratio for performance optimization
     */
    public void setFrameSkipRatio(int skipRatio) {
        currentFrameSkipRatio = Math.max(0, skipRatio);
        frameSkipCounter.set(0); // Reset counter when ratio changes
        Log.d(TAG, "Frame skip ratio set to " + currentFrameSkipRatio);
    }
    
    // Private helper methods
    
    private void updatePerformanceMetrics() {
        long currentTime = System.currentTimeMillis();
        long timeDelta = currentTime - lastFpsCalculationTime;
        
        if (timeDelta >= FRAME_RATE_CALCULATION_WINDOW_MS) {
            // Calculate current FPS
            long currentFrameCount = frameCount.get();
            long framesDelta = currentFrameCount - framesInWindow;
            currentFps = (float) framesDelta * 1000 / timeDelta;
            
            // Update average FPS (exponential moving average)
            if (averageFps == 0.0f) {
                averageFps = currentFps;
            } else {
                averageFps = averageFps * 0.8f + currentFps * 0.2f;
            }
            
            framesInWindow = currentFrameCount;
            lastFpsCalculationTime = currentTime;
            
            // Check for performance adjustments
            checkPerformanceAdjustment();
        }
        
        // Update callback with current metrics
        if (callback != null) {
            callback.onFrameRateUpdate(getCurrentMetrics());
        }
    }
    
    private void checkFrameDropRecommendation(long processingTimeMs, long currentTime) {
        // Check if processing is taking too long
        if (processingTimeMs > MAX_PROCESSING_TIME_MS) {
            String reason = "Processing time exceeded " + MAX_PROCESSING_TIME_MS + "ms (" + processingTimeMs + "ms)";
            
            if (!isFrameDroppingEnabled) {
                setFrameDroppingEnabled(true);
                setFrameSkipRatio(1); // Start with skipping every other frame
            } else if (processingTimeMs > AGGRESSIVE_FRAME_DROP_THRESHOLD_MS) {
                // Increase frame dropping for very slow processing
                setFrameSkipRatio(Math.min(4, currentFrameSkipRatio + 1));
            }
        }
        
        // Check frame timing
        long timeSinceLastFrame = currentTime - lastFrameTime;
        if (timeSinceLastFrame > FRAME_DROP_THRESHOLD_MS) {
            String reason = "Frame timing exceeded " + FRAME_DROP_THRESHOLD_MS + "ms (" + timeSinceLastFrame + "ms)";
            
            if (!isFrameDroppingEnabled) {
                setFrameDroppingEnabled(true);
                setFrameSkipRatio(1);
            }
        }
    }
    
    private void checkPerformanceAdjustment() {
        PerformanceMonitor.PerformanceLevel currentLevel = performanceMonitor.getCurrentPerformanceLevel();
        PerformanceAdjustment adjustment = null;
        
        // Check if FPS is below minimum threshold
        if (currentFps < MIN_FPS && currentFps > 0) {
            adjustment = createPerformanceAdjustment(
                PerformanceMonitor.PerformanceLevel.CRITICAL,
                true, 4, 0.4f, 150,
                "FPS below minimum threshold (" + String.format("%.1f", currentFps) + " < " + MIN_FPS + ")"
            );
        }
        // Check if FPS is low but above minimum
        else if (currentFps < TARGET_FPS * 0.5f && currentFps > 0) {
            adjustment = createPerformanceAdjustment(
                PerformanceMonitor.PerformanceLevel.LOW,
                true, 2, 0.6f, 100,
                "FPS significantly below target (" + String.format("%.1f", currentFps) + " < " + (TARGET_FPS * 0.5f) + ")"
            );
        }
        // Check if FPS is moderately low
        else if (currentFps < TARGET_FPS * 0.8f && currentFps > 0) {
            adjustment = createPerformanceAdjustment(
                PerformanceMonitor.PerformanceLevel.MEDIUM,
                true, 1, 0.8f, 75,
                "FPS below target (" + String.format("%.1f", currentFps) + " < " + (TARGET_FPS * 0.8f) + ")"
            );
        }
        // Check if performance is good and we can reduce frame dropping
        else if (currentFps >= TARGET_FPS && isFrameDroppingEnabled) {
            adjustment = createPerformanceAdjustment(
                PerformanceMonitor.PerformanceLevel.HIGH,
                false, 0, 1.0f, 50,
                "FPS stable, reducing frame dropping (" + String.format("%.1f", currentFps) + " >= " + TARGET_FPS + ")"
            );
        }
        
        // Apply adjustment if needed and different from last recommendation
        if (adjustment != null && adjustment.recommendedLevel != lastRecommendedLevel) {
            applyPerformanceAdjustment(adjustment);
            lastRecommendedLevel = adjustment.recommendedLevel;
            
            if (callback != null) {
                callback.onPerformanceAdjustment(adjustment);
            }
        }
    }
    
    private PerformanceAdjustment createPerformanceAdjustment(
            PerformanceMonitor.PerformanceLevel level, boolean enableDropping, 
            int skipRatio, float quality, int maxTime, String reason) {
        return new PerformanceAdjustment(level, enableDropping, skipRatio, quality, maxTime, reason);
    }
    
    private void applyPerformanceAdjustment(PerformanceAdjustment adjustment) {
        Log.i(TAG, "Applying performance adjustment: " + adjustment);
        
        // Update performance level
        performanceMonitor.adjustPerformanceLevel(adjustment.recommendedLevel);
        
        // Update frame dropping settings
        setFrameDroppingEnabled(adjustment.enableFrameDropping);
        setFrameSkipRatio(adjustment.frameSkipRatio);
    }
}