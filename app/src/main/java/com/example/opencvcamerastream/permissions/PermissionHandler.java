package com.example.opencvcamerastream.permissions;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.opencvcamerastream.R;

/**
 * Handles camera permission requests with Android 10 privacy compliance.
 * 
 * This class provides comprehensive permission handling including:
 * - Runtime permission requests
 * - Permission rationale dialogs
 * - Android 10 enhanced privacy controls
 * - Graceful handling of denied permissions
 * - Settings navigation for permanently denied permissions
 */
public class PermissionHandler {
    
    public static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    
    private final Activity activity;
    private PermissionCallback callback;
    
    /**
     * Interface for permission result callbacks
     */
    public interface PermissionCallback {
        void onPermissionGranted();
        void onPermissionDenied(boolean isPermanentlyDenied);
        void onPermissionRationaleRequired();
    }
    
    public PermissionHandler(@NonNull Activity activity) {
        this.activity = activity;
    }
    
    /**
     * Set the callback for permission results
     */
    public void setPermissionCallback(PermissionCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Check if camera permission is granted
     */
    public boolean isCameraPermissionGranted() {
        return ContextCompat.checkSelfPermission(activity, CAMERA_PERMISSION) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * Request camera permission with Android 10 compliance
     */
    public void requestCameraPermission() {
        if (isCameraPermissionGranted()) {
            if (callback != null) {
                callback.onPermissionGranted();
            }
            return;
        }
        
        // Check if we should show rationale (Android 10 enhanced privacy)
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, CAMERA_PERMISSION)) {
            showPermissionRationale();
        } else {
            // First time request or permanently denied
            requestPermissionDirectly();
        }
    }
    
    /**
     * Show permission rationale dialog with Android 10 privacy information
     */
    private void showPermissionRationale() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.camera_permission_required)
                .setMessage(getPermissionRationaleMessage())
                .setPositiveButton(R.string.grant_permission, (dialog, which) -> {
                    requestPermissionDirectly();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    if (callback != null) {
                        callback.onPermissionDenied(false);
                    }
                })
                .setCancelable(false)
                .show();
    }
    
    /**
     * Get permission rationale message with Android 10 specific information
     */
    private String getPermissionRationaleMessage() {
        String baseMessage = activity.getString(R.string.camera_permission_rationale);
        
        // Add Android 10 specific privacy information
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String android10Message = activity.getString(R.string.android_10_camera_privacy_message);
            return baseMessage + "\n\n" + android10Message;
        }
        
        return baseMessage;
    }
    
    /**
     * Request permission directly from the system
     */
    private void requestPermissionDirectly() {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{CAMERA_PERMISSION},
                CAMERA_PERMISSION_REQUEST_CODE
        );
    }
    
    /**
     * Handle permission request result
     * Call this from Activity's onRequestPermissionsResult
     */
    public void handlePermissionResult(int requestCode, @NonNull String[] permissions, 
                                     @NonNull int[] grantResults) {
        if (requestCode != CAMERA_PERMISSION_REQUEST_CODE) {
            return;
        }
        
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted
            if (callback != null) {
                callback.onPermissionGranted();
            }
        } else {
            // Permission denied
            boolean isPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, CAMERA_PERMISSION);
            
            if (isPermanentlyDenied) {
                showPermanentlyDeniedDialog();
            } else {
                showPermissionDeniedDialog();
            }
            
            if (callback != null) {
                callback.onPermissionDenied(isPermanentlyDenied);
            }
        }
    }
    
    /**
     * Show dialog when permission is denied but not permanently
     */
    private void showPermissionDeniedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.camera_permission_required)
                .setMessage(R.string.camera_permission_denied)
                .setPositiveButton(R.string.retry, (dialog, which) -> {
                    requestCameraPermission();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    // User chose to cancel, app cannot function
                })
                .setCancelable(false)
                .show();
    }
    
    /**
     * Show dialog when permission is permanently denied
     */
    private void showPermanentlyDeniedDialog() {
        String appName = activity.getString(R.string.app_name);
        String message = activity.getString(R.string.camera_permission_permanently_denied, appName);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.camera_permission_required)
                .setMessage(message)
                .setPositiveButton(R.string.go_to_settings, (dialog, which) -> {
                    openAppSettings();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    // User chose to cancel, app cannot function
                })
                .setCancelable(false)
                .show();
    }
    
    /**
     * Open app settings for manual permission grant
     */
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivity(intent);
    }
    
    /**
     * Show Android 10 privacy notice (optional, for enhanced user experience)
     */
    public void showAndroid10PrivacyNotice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.android_10_privacy_notice)
                    .setMessage(R.string.android_10_camera_privacy_message)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }
    
    /**
     * Release permission handler resources
     * Called during activity destruction
     */
    public void release() {
        // Clear callback to prevent memory leaks
        callback = null;
    }
}