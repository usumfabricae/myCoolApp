package com.example.opencvcamerastream.error;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.opencvcamerastream.R;

/**
 * Manages user-friendly error dialogs and recovery actions
 * 
 * This class provides:
 * - User-friendly error message dialogs
 * - Appropriate action buttons for different error types
 * - Navigation to system settings when needed
 * - Retry mechanisms with user confirmation
 * 
 * Requirements addressed:
 * - 4.1: Display informative error messages and request permissions again
 * - 4.2: User-friendly error messages for camera failures
 * - 4.3: Error message display for OpenCV processing failures
 * - 4.4: User notification for performance degradation
 */
public class ErrorDialogManager {
    
    private static final String TAG = "ErrorDialogManager";
    
    private final Context context;
    private AlertDialog currentDialog;
    
    // Dialog action callbacks
    public interface DialogActionCallback {
        void onRetryRequested();
        void onSettingsRequested();
        void onDismissed();
        void onFallbackAccepted();
    }
    
    public ErrorDialogManager(@NonNull Context context) {
        this.context = context;
    }
    
    /**
     * Show error dialog based on error information
     */
    public void showErrorDialog(@NonNull ErrorHandler.ErrorInfo errorInfo, 
                               @Nullable DialogActionCallback callback) {
        dismissCurrentDialog();
        
        switch (errorInfo.category) {
            case CAMERA_PERMISSION:
                showCameraPermissionDialog(errorInfo, callback);
                break;
            case CAMERA_HARDWARE:
                showCameraHardwareDialog(errorInfo, callback);
                break;
            case OPENCV_PROCESSING:
                showProcessingErrorDialog(errorInfo, callback);
                break;
            case MEMORY_PRESSURE:
                showMemoryPressureDialog(errorInfo, callback);
                break;
            case DISPLAY_ERROR:
                showDisplayErrorDialog(errorInfo, callback);
                break;
            case SYSTEM_ERROR:
                showSystemErrorDialog(errorInfo, callback);
                break;
            default:
                showGenericErrorDialog(errorInfo, callback);
                break;
        }
    }
    
    /**
     * Show camera permission error dialog
     * Requirement 4.1: Display informative error messages and request permissions again
     */
    private void showCameraPermissionDialog(@NonNull ErrorHandler.ErrorInfo errorInfo,
                                          @Nullable DialogActionCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Camera Permission Required");
        builder.setMessage(errorInfo.userMessage);
        builder.setCancelable(false);
        
        if (errorInfo.recoveryStrategy == ErrorHandler.RecoveryStrategy.USER_INTERVENTION) {
            // Permission permanently denied - guide to settings
            builder.setPositiveButton("Open Settings", (dialog, which) -> {
                if (callback != null) {
                    callback.onSettingsRequested();
                }
                openAppSettings();
                dialog.dismiss();
            });
            
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                if (callback != null) {
                    callback.onDismissed();
                }
                dialog.dismiss();
            });
        } else {
            // Permission can be requested again
            builder.setPositiveButton("Grant Permission", (dialog, which) -> {
                if (callback != null) {
                    callback.onRetryRequested();
                }
                dialog.dismiss();
            });
            
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                if (callback != null) {
                    callback.onDismissed();
                }
                dialog.dismiss();
            });
        }
        
        currentDialog = builder.create();
        currentDialog.show();
        
        Log.d(TAG, "Camera permission dialog shown");
    }
    
    /**
     * Show camera hardware error dialog
     * Requirement 4.2: User-friendly error messages for camera failures
     */
    private void showCameraHardwareDialog(@NonNull ErrorHandler.ErrorInfo errorInfo,
                                        @Nullable DialogActionCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Camera Error");
        builder.setMessage(errorInfo.userMessage);
        builder.setCancelable(true);
        
        if (errorInfo.recoveryStrategy == ErrorHandler.RecoveryStrategy.RETRY_WITH_BACKOFF) {
            builder.setPositiveButton("Retry", (dialog, which) -> {
                if (callback != null) {
                    callback.onRetryRequested();
                }
                dialog.dismiss();
            });
        }
        
        builder.setNegativeButton("OK", (dialog, which) -> {
            if (callback != null) {
                callback.onDismissed();
            }
            dialog.dismiss();
        });
        
        currentDialog = builder.create();
        currentDialog.show();
        
        Log.d(TAG, "Camera hardware error dialog shown");
    }
    
    /**
     * Show OpenCV processing error dialog
     * Requirement 4.3: Error message display for OpenCV processing failures
     */
    private void showProcessingErrorDialog(@NonNull ErrorHandler.ErrorInfo errorInfo,
                                         @Nullable DialogActionCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Processing Error");
        builder.setMessage(errorInfo.userMessage);
        builder.setCancelable(true);
        
        if (errorInfo.recoveryStrategy == ErrorHandler.RecoveryStrategy.FALLBACK) {
            builder.setPositiveButton("Continue", (dialog, which) -> {
                if (callback != null) {
                    callback.onFallbackAccepted();
                }
                dialog.dismiss();
            });
        } else {
            builder.setPositiveButton("Retry", (dialog, which) -> {
                if (callback != null) {
                    callback.onRetryRequested();
                }
                dialog.dismiss();
            });
        }
        
        builder.setNegativeButton("OK", (dialog, which) -> {
            if (callback != null) {
                callback.onDismissed();
            }
            dialog.dismiss();
        });
        
        currentDialog = builder.create();
        currentDialog.show();
        
        Log.d(TAG, "Processing error dialog shown");
    }
    
    /**
     * Show memory pressure dialog
     * Requirement 4.4: User notification for performance degradation
     */
    private void showMemoryPressureDialog(@NonNull ErrorHandler.ErrorInfo errorInfo,
                                        @Nullable DialogActionCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Performance Optimization");
        builder.setMessage(errorInfo.userMessage + "\n\nThe app will reduce processing quality to maintain smooth operation.");
        builder.setCancelable(true);
        
        builder.setPositiveButton("OK", (dialog, which) -> {
            if (callback != null) {
                callback.onFallbackAccepted();
            }
            dialog.dismiss();
        });
        
        currentDialog = builder.create();
        currentDialog.show();
        
        Log.d(TAG, "Memory pressure dialog shown");
    }
    
    /**
     * Show display error dialog
     */
    private void showDisplayErrorDialog(@NonNull ErrorHandler.ErrorInfo errorInfo,
                                      @Nullable DialogActionCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Display Error");
        builder.setMessage(errorInfo.userMessage);
        builder.setCancelable(true);
        
        builder.setPositiveButton("Retry", (dialog, which) -> {
            if (callback != null) {
                callback.onRetryRequested();
            }
            dialog.dismiss();
        });
        
        builder.setNegativeButton("OK", (dialog, which) -> {
            if (callback != null) {
                callback.onDismissed();
            }
            dialog.dismiss();
        });
        
        currentDialog = builder.create();
        currentDialog.show();
        
        Log.d(TAG, "Display error dialog shown");
    }
    
    /**
     * Show system error dialog
     */
    private void showSystemErrorDialog(@NonNull ErrorHandler.ErrorInfo errorInfo,
                                     @Nullable DialogActionCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("System Error");
        builder.setMessage(errorInfo.userMessage);
        builder.setCancelable(true);
        
        builder.setPositiveButton("Retry", (dialog, which) -> {
            if (callback != null) {
                callback.onRetryRequested();
            }
            dialog.dismiss();
        });
        
        builder.setNegativeButton("OK", (dialog, which) -> {
            if (callback != null) {
                callback.onDismissed();
            }
            dialog.dismiss();
        });
        
        currentDialog = builder.create();
        currentDialog.show();
        
        Log.d(TAG, "System error dialog shown");
    }
    
    /**
     * Show generic error dialog
     */
    private void showGenericErrorDialog(@NonNull ErrorHandler.ErrorInfo errorInfo,
                                      @Nullable DialogActionCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Error");
        builder.setMessage(errorInfo.userMessage);
        builder.setCancelable(true);
        
        builder.setPositiveButton("OK", (dialog, which) -> {
            if (callback != null) {
                callback.onDismissed();
            }
            dialog.dismiss();
        });
        
        currentDialog = builder.create();
        currentDialog.show();
        
        Log.d(TAG, "Generic error dialog shown");
    }
    
    /**
     * Show recovery progress dialog
     */
    public void showRecoveryDialog(@NonNull String message, int attemptNumber) {
        dismissCurrentDialog();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Recovering...");
        builder.setMessage(message + "\n\nAttempt " + attemptNumber);
        builder.setCancelable(false);
        
        currentDialog = builder.create();
        currentDialog.show();
        
        Log.d(TAG, "Recovery dialog shown for attempt " + attemptNumber);
    }
    
    /**
     * Show recovery success dialog
     */
    public void showRecoverySuccessDialog(@NonNull String message, int totalAttempts) {
        dismissCurrentDialog();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Recovery Successful");
        builder.setMessage(message + "\n\nRecovered after " + totalAttempts + " attempt(s).");
        builder.setCancelable(true);
        
        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        
        currentDialog = builder.create();
        currentDialog.show();
        
        // Auto-dismiss after 3 seconds
        new android.os.Handler().postDelayed(() -> {
            if (currentDialog != null && currentDialog.isShowing()) {
                currentDialog.dismiss();
            }
        }, 3000);
        
        Log.d(TAG, "Recovery success dialog shown");
    }
    
    /**
     * Show recovery failure dialog
     */
    public void showRecoveryFailureDialog(@NonNull String message, int totalAttempts,
                                        @Nullable DialogActionCallback callback) {
        dismissCurrentDialog();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Recovery Failed");
        builder.setMessage(message + "\n\nFailed after " + totalAttempts + " attempt(s).");
        builder.setCancelable(true);
        
        builder.setPositiveButton("OK", (dialog, which) -> {
            if (callback != null) {
                callback.onDismissed();
            }
            dialog.dismiss();
        });
        
        currentDialog = builder.create();
        currentDialog.show();
        
        Log.d(TAG, "Recovery failure dialog shown");
    }
    
    /**
     * Dismiss current dialog if showing
     */
    public void dismissCurrentDialog() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
            currentDialog = null;
        }
    }
    
    /**
     * Open app settings for permission management
     */
    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", context.getPackageName(), null);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            
            Log.d(TAG, "Opened app settings for permission management");
        } catch (Exception e) {
            Log.e(TAG, "Failed to open app settings", e);
            
            // Fallback to general settings
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception fallbackError) {
                Log.e(TAG, "Failed to open settings", fallbackError);
            }
        }
    }
    
    /**
     * Check if a dialog is currently showing
     */
    public boolean isDialogShowing() {
        return currentDialog != null && currentDialog.isShowing();
    }
    
    /**
     * Release resources
     */
    public void release() {
        dismissCurrentDialog();
        Log.d(TAG, "ErrorDialogManager resources released");
    }
}