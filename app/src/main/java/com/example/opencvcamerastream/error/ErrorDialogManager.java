package com.example.opencvcamerastream.error;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Manages error dialogs and user interactions for error recovery
 * 
 * This class provides:
 * - Error dialog display and management
 * - User action callbacks for error recovery
 * - Dialog sequencing and dismissal
 * - User-friendly error message presentation
 * 
 * Requirements addressed:
 * - 4.1: Display informative error messages and request permissions again
 * - 4.2: Provide user interface for error recovery actions
 * - 4.3: Show fallback options when processing fails
 */
public class ErrorDialogManager {
    
    private static final String TAG = "ErrorDialogManager";
    
    private final Context context;
    private AlertDialog currentDialog;
    private DialogActionCallback currentCallback;
    
    /**
     * Callback interface for dialog actions
     */
    public interface DialogActionCallback {
        /**
         * Called when user chooses to retry the failed operation
         */
        void onRetryRequested();
        
        /**
         * Called when user chooses to dismiss the error
         */
        void onDismissed();
        
        /**
         * Called when user chooses to open settings (for permission errors)
         */
        void onSettingsRequested();
        
        /**
         * Called when user chooses to use fallback functionality
         */
        void onFallbackAccepted();
        
        // New methods for compatibility
        default void onRetry(@NonNull ErrorHandler.ErrorInfo errorInfo) {
            onRetryRequested();
        }
        
        default void onDismiss(@NonNull ErrorHandler.ErrorInfo errorInfo) {
            onDismissed();
        }
        
        default void onOpenSettings(@NonNull ErrorHandler.ErrorInfo errorInfo) {
            onSettingsRequested();
        }
        
        default void onUseFallback(@NonNull ErrorHandler.ErrorInfo errorInfo) {
            onFallbackAccepted();
        }
    }
    
    public ErrorDialogManager(@NonNull Context context) {
        // Keep the original context for dialogs (needs Activity context)
        this.context = context;
    }
    
    /**
     * Show an error dialog for the given error information
     * @param errorInfo The error to display
     * @param callback Callback for user actions
     */
    public void showErrorDialog(@NonNull ErrorHandler.ErrorInfo errorInfo, 
                               @Nullable DialogActionCallback callback) {
        // Dismiss any existing dialog first
        dismissCurrentDialog();
        
        this.currentCallback = callback;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getDialogTitle(errorInfo));
        builder.setMessage(errorInfo.userMessage);
        builder.setCancelable(false);
        
        // Add appropriate buttons based on error type and recovery strategy
        setupDialogButtons(builder, errorInfo);
        
        try {
            currentDialog = builder.create();
            currentDialog.show();
            
            Log.d(TAG, "Error dialog shown for: " + errorInfo.category);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to show error dialog", e);
            // Fallback: notify callback directly
            if (callback != null) {
                callback.onDismiss(errorInfo);
            }
        }
    }
    
    /**
     * Check if a dialog is currently showing
     * @return true if dialog is visible, false otherwise
     */
    public boolean isDialogShowing() {
        return currentDialog != null && currentDialog.isShowing();
    }
    
    /**
     * Dismiss the current dialog if showing
     */
    public void dismissCurrentDialog() {
        if (currentDialog != null && currentDialog.isShowing()) {
            try {
                currentDialog.dismiss();
                Log.d(TAG, "Current dialog dismissed");
            } catch (Exception e) {
                Log.w(TAG, "Error dismissing dialog", e);
            }
        }
        currentDialog = null;
        currentCallback = null;
    }
    
    /**
     * Show a recovery dialog during error recovery attempts
     * @param message Recovery message to display
     * @param attemptNumber Current attempt number
     */
    public void showRecoveryDialog(@NonNull String message, int attemptNumber) {
        dismissCurrentDialog();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Recovering...");
        builder.setMessage(message + " (Attempt " + attemptNumber + ")");
        builder.setCancelable(false);
        
        try {
            currentDialog = builder.create();
            currentDialog.show();
            Log.d(TAG, "Recovery dialog shown: " + message);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show recovery dialog", e);
        }
    }
    
    /**
     * Show a recovery success dialog
     * @param message Success message to display
     * @param totalAttempts Total number of attempts made
     */
    public void showRecoverySuccessDialog(@NonNull String message, int totalAttempts) {
        dismissCurrentDialog();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Recovery Successful");
        builder.setMessage(message + " (Recovered after " + totalAttempts + " attempts)");
        builder.setPositiveButton("OK", (dialog, which) -> dismissCurrentDialog());
        
        try {
            currentDialog = builder.create();
            currentDialog.show();
            Log.d(TAG, "Recovery success dialog shown: " + message);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show recovery success dialog", e);
        }
    }
    
    /**
     * Show a recovery failure dialog
     * @param message Failure message to display
     * @param totalAttempts Total number of attempts made
     * @param callback Optional callback for user actions
     */
    public void showRecoveryFailureDialog(@NonNull String message, int totalAttempts, 
                                        @Nullable DialogActionCallback callback) {
        dismissCurrentDialog();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Recovery Failed");
        builder.setMessage(message + " (Failed after " + totalAttempts + " attempts)");
        builder.setPositiveButton("OK", (dialog, which) -> {
            if (callback != null) {
                callback.onDismissed();
            }
            dismissCurrentDialog();
        });
        
        try {
            currentDialog = builder.create();
            currentDialog.show();
            Log.d(TAG, "Recovery failure dialog shown: " + message);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show recovery failure dialog", e);
        }
    }
    
    /**
     * Release resources and dismiss any showing dialogs
     */
    public void release() {
        Log.d(TAG, "Releasing ErrorDialogManager resources");
        dismissCurrentDialog();
    }
    
    // Private helper methods
    
    private String getDialogTitle(@NonNull ErrorHandler.ErrorInfo errorInfo) {
        switch (errorInfo.category) {
            case CAMERA_PERMISSION:
                return "Camera Permission Required";
            case CAMERA_HARDWARE:
                return "Camera Error";
            case OPENCV_PROCESSING:
                return "Processing Error";
            case MEMORY_PRESSURE:
                return "Performance Optimization";
            case DISPLAY_ERROR:
                return "Display Error";
            case SYSTEM_ERROR:
            default:
                return "Error";
        }
    }
    
    private void setupDialogButtons(@NonNull AlertDialog.Builder builder, 
                                   @NonNull ErrorHandler.ErrorInfo errorInfo) {
        switch (errorInfo.recoveryStrategy) {
            case RETRY:
            case RETRY_WITH_BACKOFF:
                builder.setPositiveButton("Retry", (dialog, which) -> {
                    if (currentCallback != null) {
                        currentCallback.onRetryRequested();
                    }
                    dismissCurrentDialog();
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> {
                    if (currentCallback != null) {
                        currentCallback.onDismissed();
                    }
                    dismissCurrentDialog();
                });
                break;
                
            case FALLBACK:
                builder.setPositiveButton("Use Basic Mode", (dialog, which) -> {
                    if (currentCallback != null) {
                        currentCallback.onFallbackAccepted();
                    }
                    dismissCurrentDialog();
                });
                builder.setNegativeButton("Retry", (dialog, which) -> {
                    if (currentCallback != null) {
                        currentCallback.onRetryRequested();
                    }
                    dismissCurrentDialog();
                });
                builder.setNeutralButton("Cancel", (dialog, which) -> {
                    if (currentCallback != null) {
                        currentCallback.onDismissed();
                    }
                    dismissCurrentDialog();
                });
                break;
                
            case USER_INTERVENTION:
                if (errorInfo.category == ErrorHandler.ErrorCategory.CAMERA_PERMISSION) {
                    builder.setPositiveButton("Open Settings", (dialog, which) -> {
                        if (currentCallback != null) {
                            currentCallback.onSettingsRequested();
                        }
                        dismissCurrentDialog();
                    });
                }
                builder.setNegativeButton("Cancel", (dialog, which) -> {
                    if (currentCallback != null) {
                        currentCallback.onDismissed();
                    }
                    dismissCurrentDialog();
                });
                break;
                
            case GRACEFUL_DEGRADATION:
                builder.setPositiveButton("OK", (dialog, which) -> {
                    if (currentCallback != null) {
                        currentCallback.onDismissed();
                    }
                    dismissCurrentDialog();
                });
                break;
                
            case NONE:
            default:
                builder.setPositiveButton("OK", (dialog, which) -> {
                    if (currentCallback != null) {
                        currentCallback.onDismissed();
                    }
                    dismissCurrentDialog();
                });
                break;
        }
    }
}