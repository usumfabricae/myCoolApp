package com.example.opencvcamerastream.camera;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Log;
import android.util.Range;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for detecting camera capabilities and supported configurations
 * 
 * This class helps determine what video modes and frame rates are supported
 * by the device's camera hardware.
 */
public class CameraCapabilities {
    
    private static final String TAG = "CameraCapabilities";
    
    private final CameraManager cameraManager;
    
    public CameraCapabilities(@NonNull Context context) {
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }
    
    /**
     * Camera capability information
     */
    public static class CameraInfo {
        public final String cameraId;
        public final boolean supportsHighSpeedVideo;
        public final Size[] highSpeedVideoSizes;
        public final Range<Integer>[] availableFpsRanges;
        public final Size[] outputSizes;
        public final int maxFps;
        public final boolean supports1080p60;
        public final boolean supports720p60;
        
        public CameraInfo(String cameraId, boolean supportsHighSpeed, Size[] highSpeedSizes,
                         Range<Integer>[] fpsRanges, Size[] outputSizes) {
            this.cameraId = cameraId;
            this.supportsHighSpeedVideo = supportsHighSpeed;
            this.highSpeedVideoSizes = highSpeedSizes != null ? highSpeedSizes : new Size[0];
            this.availableFpsRanges = fpsRanges != null ? fpsRanges : new Range[0];
            this.outputSizes = outputSizes != null ? outputSizes : new Size[0];
            
            // Calculate max FPS
            int maxFpsValue = 30; // Default
            if (fpsRanges != null) {
                for (Range<Integer> range : fpsRanges) {
                    maxFpsValue = Math.max(maxFpsValue, range.getUpper());
                }
            }
            this.maxFps = maxFpsValue;
            
            // Check for specific high-speed capabilities
            this.supports1080p60 = supportsResolutionAndFps(1920, 1080, 60);
            this.supports720p60 = supportsResolutionAndFps(1280, 720, 60);
        }
        
        private boolean supportsResolutionAndFps(int width, int height, int fps) {
            // Check if resolution is available in high-speed sizes
            boolean hasResolution = false;
            for (Size size : highSpeedVideoSizes) {
                if (size.getWidth() == width && size.getHeight() == height) {
                    hasResolution = true;
                    break;
                }
            }
            
            // Check if FPS is available
            boolean hasFps = false;
            for (Range<Integer> range : availableFpsRanges) {
                if (range.getUpper() >= fps) {
                    hasFps = true;
                    break;
                }
            }
            
            return hasResolution && hasFps;
        }
        
        @Override
        public String toString() {
            return String.format("Camera %s: HighSpeed=%s, MaxFPS=%d, 1080p60=%s, 720p60=%s, HSizes=%d, FPSRanges=%d",
                    cameraId, supportsHighSpeedVideo, maxFps, supports1080p60, supports720p60,
                    highSpeedVideoSizes.length, availableFpsRanges.length);
        }
    }
    
    /**
     * Get capabilities for all available cameras
     */
    @NonNull
    public List<CameraInfo> getAllCameraCapabilities() {
        List<CameraInfo> capabilities = new ArrayList<>();
        
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            
            for (String cameraId : cameraIds) {
                CameraInfo info = getCameraCapabilities(cameraId);
                if (info != null) {
                    capabilities.add(info);
                }
            }
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to get camera list", e);
        }
        
        return capabilities;
    }
    
    /**
     * Get capabilities for a specific camera
     */
    @Nullable
    public CameraInfo getCameraCapabilities(@NonNull String cameraId) {
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            
            if (map == null) {
                Log.w(TAG, "StreamConfigurationMap is null for camera " + cameraId);
                return null;
            }
            
            // Get high-speed video sizes
            Size[] highSpeedSizes = map.getHighSpeedVideoSizes();
            boolean supportsHighSpeed = highSpeedSizes != null && highSpeedSizes.length > 0;
            
            // Get available FPS ranges
            Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            
            // Get output sizes
            Size[] outputSizes = map.getOutputSizes(android.graphics.ImageFormat.YUV_420_888);
            
            return new CameraInfo(cameraId, supportsHighSpeed, highSpeedSizes, fpsRanges, outputSizes);
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to get camera characteristics for " + cameraId, e);
            return null;
        }
    }
    
    /**
     * Find the best camera for high-speed video
     */
    @Nullable
    public CameraInfo getBestHighSpeedCamera() {
        List<CameraInfo> allCameras = getAllCameraCapabilities();
        
        // Prefer back-facing camera with 1080p60 support
        for (CameraInfo camera : allCameras) {
            if (camera.supports1080p60) {
                try {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(camera.cameraId);
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        Log.i(TAG, "Found back-facing camera with 1080p60 support: " + camera.cameraId);
                        return camera;
                    }
                } catch (CameraAccessException e) {
                    Log.w(TAG, "Failed to check camera facing for " + camera.cameraId, e);
                }
            }
        }
        
        // Fall back to any camera with high-speed video support
        for (CameraInfo camera : allCameras) {
            if (camera.supportsHighSpeedVideo) {
                Log.i(TAG, "Found camera with high-speed video support: " + camera.cameraId);
                return camera;
            }
        }
        
        // Fall back to first available camera
        if (!allCameras.isEmpty()) {
            Log.i(TAG, "Using first available camera: " + allCameras.get(0).cameraId);
            return allCameras.get(0);
        }
        
        return null;
    }
    
    /**
     * Log detailed camera capabilities for debugging
     */
    public void logAllCameraCapabilities() {
        Log.i(TAG, "=== CAMERA CAPABILITIES REPORT ===");
        
        List<CameraInfo> allCameras = getAllCameraCapabilities();
        
        for (CameraInfo camera : allCameras) {
            Log.i(TAG, camera.toString());
            
            // Log high-speed video sizes
            if (camera.highSpeedVideoSizes.length > 0) {
                Log.i(TAG, "  High-speed video sizes: " + Arrays.toString(camera.highSpeedVideoSizes));
            }
            
            // Log FPS ranges
            if (camera.availableFpsRanges.length > 0) {
                StringBuilder fpsInfo = new StringBuilder("  FPS ranges: ");
                for (Range<Integer> range : camera.availableFpsRanges) {
                    fpsInfo.append(range.getLower()).append("-").append(range.getUpper()).append(" ");
                }
                Log.i(TAG, fpsInfo.toString());
            }
            
            // Log some output sizes
            if (camera.outputSizes.length > 0) {
                Log.i(TAG, "  Output sizes count: " + camera.outputSizes.length);
                // Log first few sizes
                for (int i = 0; i < Math.min(5, camera.outputSizes.length); i++) {
                    Size size = camera.outputSizes[i];
                    Log.i(TAG, "    " + size.getWidth() + "x" + size.getHeight());
                }
            }
        }
        
        Log.i(TAG, "=== END CAMERA CAPABILITIES REPORT ===");
    }
    
    /**
     * Check if device supports 60 FPS at 1920x1080
     */
    public boolean supports1080p60() {
        CameraInfo bestCamera = getBestHighSpeedCamera();
        return bestCamera != null && bestCamera.supports1080p60;
    }
    
    /**
     * Check if device supports 60 FPS at 1280x720
     */
    public boolean supports720p60() {
        CameraInfo bestCamera = getBestHighSpeedCamera();
        return bestCamera != null && bestCamera.supports720p60;
    }
    
    /**
     * Get recommended camera configuration based on device capabilities
     */
    @NonNull
    public com.example.opencvcamerastream.camera.CameraManager.CameraConfig getRecommendedConfig() {
        CameraInfo bestCamera = getBestHighSpeedCamera();
        
        if (bestCamera != null && bestCamera.supports1080p60) {
            Log.i(TAG, "Recommending 1080p60 configuration");
            return com.example.opencvcamerastream.camera.CameraManager.CameraConfig.createHighSpeedVideoConfig();
        } else if (bestCamera != null && bestCamera.supports720p60) {
            Log.i(TAG, "Recommending 720p60 configuration");
            com.example.opencvcamerastream.camera.CameraManager.CameraConfig config = 
                com.example.opencvcamerastream.camera.CameraManager.CameraConfig.createHighSpeedVideoConfig();
            config.preferredSize = new Size(1280, 720);
            return config;
        } else {
            Log.i(TAG, "Recommending standard video configuration");
            return com.example.opencvcamerastream.camera.CameraManager.CameraConfig.createStandardVideoConfig();
        }
    }
}