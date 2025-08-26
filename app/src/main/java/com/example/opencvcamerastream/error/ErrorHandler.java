package com.example.opencvcamerastream.error;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralized error handling and recovery system
 * 
 * This class provides:
 * - Centralized error logging and reporting
 * - Automatic retry mechanisms with exponential backoff
 * - Error categorization and appropriate recovery strategies
 * - User-friendly error message generation
 * - Performance degradation handling for low-end devices
 * 
 * Requirements addressed:
 * - 4.1: Display informative error messages and request permissions again
 * - 4.2: Attempt to reconnect automatically when camera becomes unavailable
 * - 4.3: Display error message and fall back to displaying unprocessed frames
 * - 4.4: Reduce processing complexity to maintain stability when memory is low
 */
public class ErrorHandler {
    
    private static final String TAG = "ErrorHandler";
    
    // Error categories
    public enum ErrorCategory {
        CAMERA_PERMISSION,
        CAMERA_HARDWARE,
        OPENCV_PROCESSING,
        MEMORY_PRESSURE,
        DISPLAY_ERROR,
        SYSTEM_ERROR
    }
    
    // Error severity levels
    public enum ErrorSeverity {
        LOW,        // Warning, app continues normally
        MEDIUM,     // Error handled, some functionality may be limited
        HIGH,       // Critical error, major functionality affected
        CRITICAL    // App may need to restart or close
    }
    
    // Recovery strategies
    public enum RecoveryStrategy {
        NONE,           // No automatic recovery
        RETRY,          // Simple retry
        RETRY_WITH_BACKOFF, // Retry with exponential backoff
        FALLBACK,       // Use fallback functionality
        GRACEFUL_DEGRADATION, // Reduce functionality to maintain stability
        USER_INTERVENTION   // Requires user action
    }
    
    // Error information container
    public static class ErrorInfo {
        public final ErrorCategory category;
        public final ErrorSeverity severity;
        public final String message;
        public final String userMessage;
        public final Throwable cause;
        public final RecoveryStrategy recoveryStrategy;
        public final long timestamp;
        
        public ErrorInfo(ErrorCategory category, ErrorSeverity severity, 
                        String message, String userMessage, Throwable cause,
                        RecoveryStrategy recoveryStrategy) {
            this.category = category;
            this.severity = severity;
            this.message = message;
            this.userMessage = userMessage;
            this.cause = cause;
            this.recoveryStrategy = recoveryStrategy;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    // Error callback interface
    public interface ErrorCallback {
        void onError(@NonNull ErrorInfo errorInfo);
        void onRecoveryAttempt(@NonNull ErrorInfo errorInfo, int attemptNumber);
        void onRecoverySuccess(@NonNull ErrorInfo errorInfo, int totalAttempts);
        void onRecoveryFailed(@NonNull ErrorInfo errorInfo, int totalAttempts);
    }
    
    // Retry configuration
    public static class RetryConfig {
        public int maxRetries = 3;
        public long initialDelayMs = 1000;
        public double backoffMultiplier = 2.0;
        public long maxDelayMs = 30000;
        
        public RetryConfig() {}
        
        public RetryConfig(int maxRetries, long initialDelayMs) {
            this.maxRetries = maxRetries;
            this.initialDelayMs = initialDelayMs;
        }
    }
    
    private final Context context;
    private ErrorCallback errorCallback;
    private final RetryConfig defaultRetryConfig;
    
    // Retry tracking
    private final AtomicInteger cameraRetryCount = new AtomicInteger(0);
    private final AtomicInteger processingRetryCount = new AtomicInteger(0);
    private long lastCameraRetryTime = 0;
    private long lastProcessingRetryTime = 0;
    
    // Performance degradation tracking
    private boolean isPerformanceDegraded = false;
    private int consecutiveErrors = 0;
    private static final int MAX_CONSECUTIVE_ERRORS = 5;
    
    public ErrorHandler(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.defaultRetryConfig = new RetryConfig();
    }
    
    public ErrorHandler(@NonNull Context context, @NonNull RetryConfig retryConfig) {
        this.context = context.getApplicationContext();
        this.defaultRetryConfig = retryConfig;
    }
    
    /**
     * Set error callback for notifications
     */
    public void setErrorCallback(@Nullable ErrorCallback callback) {
        this.errorCallback = callback;
    }
    
    /**
     * Handle camera permission errors
     * Requirement 4.1: Display informative error messages and request permissions again
     */
    public ErrorInfo handleCameraPermissionError(boolean isPermanentlyDenied) {
        String message = isPermanentlyDenied ? 
            "Camera permission permanently denied" : 
            "Camera permission denied";
            
        String userMessage = isPermanentlyDenied ?
            "Camera permission is required. Please enable it in Settings." :
            "Camera permission is needed to capture video. Please grant permission.";
            
        RecoveryStrategy strategy = isPermanentlyDenied ? 
            RecoveryStrategy.USER_INTERVENTION : 
            RecoveryStrategy.RETRY;
            
        ErrorInfo errorInfo = new ErrorInfo(
            ErrorCategory.CAMERA_PERMISSION,
            isPermanentlyDenied ? ErrorSeverity.HIGH : ErrorSeverity.MEDIUM,
            message,
            userMessage,
            null,
            strategy
        );
        
        Log.w(TAG, "Camera permission error: " + message);
        notifyError(errorInfo);
        
        return errorInfo;
    }
    
    /**
     * Handle camera hardware errors with automatic retry
     * Requirement 4.2: Attempt to reconnect automatically when camera becomes unavailable
     */
    public ErrorInfo handleCameraHardwareError(int errorCode, @Nullable String errorMessage, 
                                              @Nullable Throwable cause) {
        String message = "Camera hardware error: " + errorCode + 
                        (errorMessage != null ? " - " + errorMessage : "");
        String userMessage = getCameraErrorUserMessage(errorCode);
        
        ErrorInfo errorInfo = new ErrorInfo(
            ErrorCategory.CAMERA_HARDWARE,
            ErrorSeverity.HIGH,
            message,
            userMessage,
            cause,
            RecoveryStrategy.RETRY_WITH_BACKOFF
        );
        
        Log.e(TAG, message, cause);
        notifyError(errorInfo);
        
        // Attempt automatic recovery if within retry limits
        if (shouldAttemptCameraRetry()) {
            attemptCameraRecovery(errorInfo);
        }
        
        return errorInfo;
    }
    
    /**
     * Handle OpenCV processing errors with fallback
     * Requirement 4.3: Display error message and fall back to displaying unprocessed frames
     */
    public ErrorInfo handleOpenCVProcessingError(@NonNull Exception error, boolean canFallback) {
        String message = "OpenCV processing error: " + error.getMessage();
        String userMessage = canFallback ? 
            "Processing temporarily unavailable. Showing original camera feed." :
            "Video processing error occurred.";
            
        RecoveryStrategy strategy = canFallback ? 
            RecoveryStrategy.FALLBACK : 
            RecoveryStrategy.RETRY;
            
        ErrorInfo errorInfo = new ErrorInfo(
            ErrorCategory.OPENCV_PROCESSING,
            ErrorSeverity.MEDIUM,
            message,
            userMessage,
            error,
            strategy
        );
        
        Log.w(TAG, message, error);
        notifyError(errorInfo);
        
        // Track consecutive processing errors
        consecutiveErrors++;
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            handlePerformanceDegradation();
        }
        
        return errorInfo;
    }
    
    /**
     * Handle memory pressure situations
     * Requirement 4.4: Reduce processing complexity to maintain stability when memory is low
     */
    public ErrorInfo handleMemoryPressure(long currentMemoryUsage, long maxMemoryUsage) {
        double memoryUsagePercent = (double) currentMemoryUsage / maxMemoryUsage * 100;
        
        String message = String.format("Memory pressure detected: %.1f%% usage", memoryUsagePercent);
        String userMessage = "Optimizing performance due to memory constraints.";
        
        ErrorInfo errorInfo = new ErrorInfo(
            ErrorCategory.MEMORY_PRESSURE,
            memoryUsagePercent > 90 ? ErrorSeverity.HIGH : ErrorSeverity.MEDIUM,
            message,
            userMessage,
            null,
            RecoveryStrategy.GRACEFUL_DEGRADATION
        );
        
        Log.w(TAG, message);
        notifyError(errorInfo);
        
        // Trigger performance degradation
        handlePerformanceDegradation();
        
        return errorInfo;
    }
    
    /**
     * Handle display errors
     */
    public ErrorInfo handleDisplayError(@NonNull Exception error) {
        String message = "Display error: " + error.getMessage();
        String userMessage = "Display issue detected. Attempting to recover.";
        
        ErrorInfo errorInfo = new ErrorInfo(
            ErrorCategory.DISPLAY_ERROR,
            ErrorSeverity.MEDIUM,
            message,
            userMessage,
            error,
            RecoveryStrategy.RETRY
        );
        
        Log.e(TAG, message, error);
        notifyError(errorInfo);
        
        return errorInfo;
    }
    
    /**
     * Handle general system errors
     */
    public ErrorInfo handleSystemError(@NonNull Exception error, @NonNull String context) {
        String message = "System error in " + context + ": " + error.getMessage();
        String userMessage = "An unexpected error occurred. The app will attempt to recover.";
        
        ErrorInfo errorInfo = new ErrorInfo(
            ErrorCategory.SYSTEM_ERROR,
            ErrorSeverity.HIGH,
            message,
            userMessage,
            error,
            RecoveryStrategy.RETRY
        );
        
        Log.e(TAG, message, error);
        notifyError(errorInfo);
        
        return errorInfo;
    }
    
    /**
     * Reset error counters on successful operation
     */
    public void resetErrorCounters() {
        consecutiveErrors = 0;
        cameraRetryCount.set(0);
        processingRetryCount.set(0);
        
        if (isPerformanceDegraded) {
            isPerformanceDegraded = false;
            Log.i(TAG, "Performance degradation lifted - normal operation resumed");
        }
    }
    
    /**
     * Check if performance is currently degraded
     */
    public boolean isPerformanceDegraded() {
        return isPerformanceDegraded;
    }
    
    /**
     * Get current retry count for camera operations
     */
    public int getCameraRetryCount() {
        return cameraRetryCount.get();
    }
    
    /**
     * Get current retry count for processing operations
     */
    public int getProcessingRetryCount() {
        return processingRetryCount.get();
    }
    
    // Private helper methods
    
    private void notifyError(@NonNull ErrorInfo errorInfo) {
        if (errorCallback != null) {
            errorCallback.onError(errorInfo);
        }
    }
    
    private boolean shouldAttemptCameraRetry() {
        long currentTime = System.currentTimeMillis();
        int retryCount = cameraRetryCount.get();
        
        // Check if we've exceeded max retries
        if (retryCount >= defaultRetryConfig.maxRetries) {
            return false;
        }
        
        // Check if enough time has passed since last retry (exponential backoff)
        long requiredDelay = calculateBackoffDelay(retryCount);
        return (currentTime - lastCameraRetryTime) >= requiredDelay;
    }
    
    private void attemptCameraRecovery(@NonNull ErrorInfo errorInfo) {
        int attemptNumber = cameraRetryCount.incrementAndGet();
        lastCameraRetryTime = System.currentTimeMillis();
        
        Log.i(TAG, "Attempting camera recovery, attempt " + attemptNumber);
        
        if (errorCallback != null) {
            errorCallback.onRecoveryAttempt(errorInfo, attemptNumber);
        }
        
        // The actual recovery logic will be handled by the calling component
        // This method just tracks the attempt
    }
    
    private void handlePerformanceDegradation() {
        if (!isPerformanceDegraded) {
            isPerformanceDegraded = true;
            Log.w(TAG, "Performance degradation activated due to consecutive errors or memory pressure");
        }
    }
    
    private long calculateBackoffDelay(int retryCount) {
        long delay = (long) (defaultRetryConfig.initialDelayMs * 
                           Math.pow(defaultRetryConfig.backoffMultiplier, retryCount));
        return Math.min(delay, defaultRetryConfig.maxDelayMs);
    }
    
    private String getCameraErrorUserMessage(int errorCode) {
        // Map camera error codes to user-friendly messages
        switch (errorCode) {
            case 1: // ERROR_CAMERA_DEVICE
                return "Camera device error. Please try again.";
            case 2: // ERROR_CAMERA_DISABLED
                return "Camera is disabled. Please check device settings.";
            case 3: // ERROR_CAMERA_IN_USE
                return "Camera is being used by another app. Please close other camera apps.";
            case 4: // ERROR_CAMERA_SERVICE
                return "Camera service error. Please restart the app.";
            case 5: // ERROR_MAX_CAMERAS_IN_USE
                return "Too many camera apps are running. Please close other apps.";
            default:
                return "Camera error occurred. Please try again.";
        }
    }
}