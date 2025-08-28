package com.example.opencvcamerastream.performance;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.opencvcamerastream.R;
import com.example.opencvcamerastream.error.PerformanceMonitor;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * Performance display manager for showing real-time performance metrics
 * 
 * This class provides:
 * - Real-time FPS display
 * - Processing time visualization
 * - Memory usage monitoring display
 * - Performance level indication
 * - Frame drop statistics
 * - Toggle functionality for performance overlay
 * 
 * Requirements addressed:
 * - 1.3: Display frame rate monitoring for at least 10 FPS
 * - 2.3: Show processing time within 50ms requirement
 * - 5.1, 5.3: Performance optimization visualization
 */
public class PerformanceDisplayManager {
    
    private static final String TAG = "PerformanceDisplayManager";
    
    // UI elements
    private final Activity activity;
    private View performanceOverlay;
    private TextView fpsText;
    private TextView processingTimeText;
    private TextView memoryUsageText;
    private TextView performanceLevelText;
    private TextView frameDropText;
    private FloatingActionButton performanceToggleFab;
    
    // State
    private boolean isOverlayVisible = false;
    private PerformanceMetricsCollector.FrameRateMetrics lastMetrics;
    
    public PerformanceDisplayManager(@NonNull Activity activity) {
        this.activity = activity;
        initializeViews();
    }
    
    /**
     * Initialize UI views
     */
    private void initializeViews() {
        performanceOverlay = activity.findViewById(R.id.performanceOverlay);
        fpsText = activity.findViewById(R.id.fpsText);
        processingTimeText = activity.findViewById(R.id.processingTimeText);
        memoryUsageText = activity.findViewById(R.id.memoryUsageText);
        performanceLevelText = activity.findViewById(R.id.performanceLevelText);
        frameDropText = activity.findViewById(R.id.frameDropText);
        performanceToggleFab = activity.findViewById(R.id.performanceToggleFab);
        
        if (performanceToggleFab != null) {
            performanceToggleFab.setOnClickListener(v -> togglePerformanceOverlay());
        }
        
        // Initially hide the overlay
        if (performanceOverlay != null) {
            performanceOverlay.setVisibility(View.GONE);
        }
    }
    
    /**
     * Toggle performance overlay visibility
     */
    public void togglePerformanceOverlay() {
        if (performanceOverlay != null) {
            isOverlayVisible = !isOverlayVisible;
            performanceOverlay.setVisibility(isOverlayVisible ? View.VISIBLE : View.GONE);
            
            // Update FAB icon or state if needed
            if (performanceToggleFab != null) {
                performanceToggleFab.setAlpha(isOverlayVisible ? 1.0f : 0.7f);
            }
        }
    }
    
    /**
     * Show performance overlay
     */
    public void showPerformanceOverlay() {
        if (performanceOverlay != null && !isOverlayVisible) {
            togglePerformanceOverlay();
        }
    }
    
    /**
     * Hide performance overlay
     */
    public void hidePerformanceOverlay() {
        if (performanceOverlay != null && isOverlayVisible) {
            togglePerformanceOverlay();
        }
    }
    
    /**
     * Check if performance overlay is visible
     */
    public boolean isPerformanceOverlayVisible() {
        return isOverlayVisible;
    }
    
    /**
     * Update performance metrics display
     */
    public void updateMetrics(@NonNull PerformanceMetricsCollector.FrameRateMetrics metrics, 
                             @Nullable PerformanceMonitor.PerformanceMetrics systemMetrics) {
        if (!isOverlayVisible) return;
        
        lastMetrics = metrics;
        
        activity.runOnUiThread(() -> {
            updateFpsDisplay(metrics);
            updateProcessingTimeDisplay(metrics);
            updateMemoryDisplay(systemMetrics);
            updatePerformanceLevelDisplay(metrics);
            updateFrameDropDisplay(metrics);
        });
    }
    
    /**
     * Update FPS display
     */
    private void updateFpsDisplay(@NonNull PerformanceMetricsCollector.FrameRateMetrics metrics) {
        if (fpsText != null) {
            String fpsDisplay = String.format("FPS: %.1f (avg: %.1f)", metrics.currentFps, metrics.averageFps);
            fpsText.setText(fpsDisplay);
            
            // Color code based on performance
            int color;
            if (metrics.currentFps >= 25) {
                color = 0xFF4CAF50; // Green - good performance
            } else if (metrics.currentFps >= 15) {
                color = 0xFFFF9800; // Orange - moderate performance
            } else if (metrics.currentFps >= 10) {
                color = 0xFFFF5722; // Red-orange - poor performance
            } else {
                color = 0xFFF44336; // Red - critical performance
            }
            fpsText.setTextColor(color);
        }
    }
    
    /**
     * Update processing time display
     */
    private void updateProcessingTimeDisplay(@NonNull PerformanceMetricsCollector.FrameRateMetrics metrics) {
        if (processingTimeText != null) {
            String processingDisplay = String.format("Processing: %dms (max: %dms)", 
                    metrics.averageProcessingTimeMs, metrics.maxProcessingTimeMs);
            processingTimeText.setText(processingDisplay);
            
            // Color code based on processing time
            int color;
            if (metrics.averageProcessingTimeMs <= 33) { // ~30 FPS
                color = 0xFF4CAF50; // Green
            } else if (metrics.averageProcessingTimeMs <= 50) { // Requirement 2.3
                color = 0xFFFF9800; // Orange
            } else if (metrics.averageProcessingTimeMs <= 100) { // ~10 FPS
                color = 0xFFFF5722; // Red-orange
            } else {
                color = 0xFFF44336; // Red
            }
            processingTimeText.setTextColor(color);
        }
    }
    
    /**
     * Update memory usage display
     */
    private void updateMemoryDisplay(@Nullable PerformanceMonitor.PerformanceMetrics systemMetrics) {
        if (memoryUsageText != null) {
            if (systemMetrics != null) {
                String memoryDisplay = String.format("Memory: %.1f%% (%dMB/%dMB)", 
                        systemMetrics.memoryUsagePercent, 
                        systemMetrics.usedMemoryMB, 
                        systemMetrics.totalMemoryMB);
                memoryUsageText.setText(memoryDisplay);
                
                // Color code based on memory usage
                int color;
                if (systemMetrics.memoryUsagePercent <= 60) {
                    color = 0xFF4CAF50; // Green
                } else if (systemMetrics.memoryUsagePercent <= 80) {
                    color = 0xFFFF9800; // Orange
                } else if (systemMetrics.memoryUsagePercent <= 90) {
                    color = 0xFFFF5722; // Red-orange
                } else {
                    color = 0xFFF44336; // Red
                }
                memoryUsageText.setTextColor(color);
            } else {
                memoryUsageText.setText("Memory: --");
                memoryUsageText.setTextColor(0xFFFFFFFF); // White
            }
        }
    }
    
    /**
     * Update performance level display
     */
    private void updatePerformanceLevelDisplay(@NonNull PerformanceMetricsCollector.FrameRateMetrics metrics) {
        if (performanceLevelText != null) {
            String levelDisplay = "Level: " + metrics.currentLevel.name();
            performanceLevelText.setText(levelDisplay);
            
            // Color code based on performance level
            int color;
            switch (metrics.currentLevel) {
                case HIGH:
                    color = 0xFF4CAF50; // Green
                    break;
                case MEDIUM:
                    color = 0xFFFF9800; // Orange
                    break;
                case LOW:
                    color = 0xFFFF5722; // Red-orange
                    break;
                case CRITICAL:
                    color = 0xFFF44336; // Red
                    break;
                default:
                    color = 0xFFFFFFFF; // White
                    break;
            }
            performanceLevelText.setTextColor(color);
        }
    }
    
    /**
     * Update frame drop display
     */
    private void updateFrameDropDisplay(@NonNull PerformanceMetricsCollector.FrameRateMetrics metrics) {
        if (frameDropText != null) {
            String dropDisplay;
            if (metrics.totalFrames > 0) {
                dropDisplay = String.format("Drops: %d/%d (%.1f%%)", 
                        metrics.droppedFrames, metrics.totalFrames, metrics.dropRate);
            } else {
                dropDisplay = "Drops: 0/0 (0.0%)";
            }
            
            if (metrics.isFrameDropping) {
                dropDisplay += " [ACTIVE]";
            }
            
            frameDropText.setText(dropDisplay);
            
            // Color code based on drop rate
            int color;
            if (metrics.dropRate <= 5.0f) {
                color = 0xFF4CAF50; // Green - low drop rate
            } else if (metrics.dropRate <= 15.0f) {
                color = 0xFFFF9800; // Orange - moderate drop rate
            } else if (metrics.dropRate <= 30.0f) {
                color = 0xFFFF5722; // Red-orange - high drop rate
            } else {
                color = 0xFFF44336; // Red - very high drop rate
            }
            frameDropText.setTextColor(color);
        }
    }
    
    /**
     * Show performance adjustment notification
     */
    public void showPerformanceAdjustment(@NonNull PerformanceMetricsCollector.PerformanceAdjustment adjustment) {
        // Temporarily show overlay if hidden to display the adjustment
        boolean wasVisible = isOverlayVisible;
        if (!wasVisible) {
            showPerformanceOverlay();
        }
        
        // Flash the performance level text to indicate change
        if (performanceLevelText != null) {
            activity.runOnUiThread(() -> {
                performanceLevelText.setAlpha(0.3f);
                performanceLevelText.animate()
                        .alpha(1.0f)
                        .setDuration(500)
                        .start();
            });
        }
        
        // Auto-hide overlay after a delay if it wasn't originally visible
        if (!wasVisible) {
            activity.runOnUiThread(() -> {
                new android.os.Handler().postDelayed(() -> {
                    if (!wasVisible) {
                        hidePerformanceOverlay();
                    }
                }, 3000); // Hide after 3 seconds
            });
        }
    }
    
    /**
     * Get last recorded metrics
     */
    @Nullable
    public PerformanceMetricsCollector.FrameRateMetrics getLastMetrics() {
        return lastMetrics;
    }
    
    /**
     * Handle activity onResume lifecycle event
     * Restore performance display state
     */
    public void onResume() {
        // Performance overlay state is maintained
        // Just ensure UI elements are properly initialized
        if (performanceToggleFab != null) {
            performanceToggleFab.setAlpha(isOverlayVisible ? 1.0f : 0.7f);
        }
        
        // Refresh display with last known metrics if available
        if (lastMetrics != null) {
            updateFpsDisplay(lastMetrics);
            updateProcessingTimeDisplay(lastMetrics);
            updatePerformanceLevelDisplay(lastMetrics);
            updateFrameDropDisplay(lastMetrics);
        }
    }
    
    /**
     * Handle activity onPause lifecycle event
     * Prepare for background state
     */
    public void onPause() {
        // Keep overlay state but stop any pending animations
        if (performanceLevelText != null) {
            performanceLevelText.clearAnimation();
        }
        
        // Clear any pending UI updates
        clearPendingUpdates();
    }
    
    /**
     * Clear any pending UI updates to prevent memory leaks
     */
    public void clearPendingUpdates() {
        // Remove any pending UI updates from the handler
        activity.runOnUiThread(() -> {
            // Clear animations
            if (performanceLevelText != null) {
                performanceLevelText.clearAnimation();
            }
        });
    }
    
    /**
     * Release performance display manager resources
     */
    public void release() {
        // Clear any pending UI updates
        clearPendingUpdates();
        
        // Clear references to prevent memory leaks
        performanceOverlay = null;
        fpsText = null;
        processingTimeText = null;
        memoryUsageText = null;
        performanceLevelText = null;
        frameDropText = null;
        performanceToggleFab = null;
        
        // Clear state
        lastMetrics = null;
        isOverlayVisible = false;
    }
    
    /**
     * Reset display to initial state
     */
    public void reset() {
        activity.runOnUiThread(() -> {
            if (fpsText != null) {
                fpsText.setText("FPS: --");
                fpsText.setTextColor(0xFFFFFFFF);
            }
            if (processingTimeText != null) {
                processingTimeText.setText("Processing: --ms");
                processingTimeText.setTextColor(0xFFFFFFFF);
            }
            if (memoryUsageText != null) {
                memoryUsageText.setText("Memory: --%");
                memoryUsageText.setTextColor(0xFFFFFFFF);
            }
            if (performanceLevelText != null) {
                performanceLevelText.setText("Level: --");
                performanceLevelText.setTextColor(0xFFFFFFFF);
            }
            if (frameDropText != null) {
                frameDropText.setText("Drops: --");
                frameDropText.setTextColor(0xFFFFFFFF);
            }
        });
        
        lastMetrics = null;
    }
}