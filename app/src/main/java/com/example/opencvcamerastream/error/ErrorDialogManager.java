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
         * @param errorInfo The original error information
         */
        void onRetry(@NonNull ErrorHandler.ErrorInfo errorInfo);
        
        /**
         * Called when user chooses to dismiss the error
         * @param errorInfo The original error information
         */
        void onDismiss(@NonNull ErrorHandler.ErrorInfo errorInfo);
        
        /**
         * Called when user chooses to open settings (for permission errors)
         * @param errorInfo The original error information
         */
        void onOpenSettings(@NonNull ErrorHandler.ErrorInfo errorInfo);
        
        /**
         * Called when user chooses to use fallback functionality
         * @param errorInfo The original error information
         */
        void onUseFallback(@NonNull ErrorHandler.ErrorInfo errorInfo);
    }
    
    public ErrorDialogManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
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
                        currentCallback.onRetry(errorInfo);
                    }
                    dismissCurrentDialog();
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> {
                    if (currentCallback != null) {
                        currentCallback.onDismiss(errorInfo);
                    }
                    dismissCurrentDialog();
                });
                break;
                
            case FALLBACK:
                builder.setPositiveButton("Use Basic Mode", (dialog, which) -> {
                    if (currentCallback != null) {
                        currentCallback.onUseFallback(errorInfo);
                    }
                    dismissCurrentDialog();
                });
                builder.setNegativeButton("Retry", (dialog, which) -> {
                    if (currentCallback != null) {
                        currentCallback.onRetry(errorInfo);
                    }
                    dismissCurrentDialog();
                });
                builder.setNeutralButton("Cancel", (dialog, which) -> {
                    if (currentCallback != null) {
                        currentCallback.onDismiss(errorInfo);
                    }
                    dismissCurrentDialog();
                });
                break;
                
            case USER_INTERVENTION:
                if (errorInfo.category == ErrorHandler.ErrorCategory.CAMERA_PERMISSION) {
                    builder.setPositiveButton("Open Settings", (dialog, which) -> {
                        if (currentCallback != null) {
                            currentCallback.onOpenSettings(errorInfo);
                        }
                        dismissCurrentDialog();
                    });
                }
                builder.setNegativeButton("Cancel", (dialog, which) -> {
                    if (currentCallback != null) {
                        currentCallback.onDismiss(errorInfo);
                    }
                    dismissCurrentDialog();
                });
                break;
                
            case GRACEFUL_DEGRADATION:
                builder.setPositiveButton("OK", (dialog, which) -> {
                    if (currentCallback != null) {
                        currentCallback.onDismiss(errorInfo);
                    }
                    dismissCurrentDialog();
                });
                break;
                
            case NONE:
            default:
                builder.setPositiveButton("OK", (dialog, which) -> {
                    if (currentCallback != null) {
                        currentCallback.onDismiss(errorInfo);
                    }
                    dismissCurrentDialog();
                });
                break;
        }
    }
}