// Example modifications for your CameraManager to support higher frame rates

public class CameraManager {
    // Configurable frame rate instead of hard-coded 30 FPS
    private static final long TARGET_FRAME_INTERVAL_MS_60FPS = 16; // ~60 FPS
    private static final long TARGET_FRAME_INTERVAL_MS_30FPS = 33; // ~30 FPS
    
    private int targetFps = 60; // Configurable target FPS
    
    // Method to set desired frame rate
    public void setTargetFrameRate(int fps) {
        this.targetFps = fps;
        Log.d(TAG, "Target frame rate set to: " + fps + " FPS");
    }
    
    // Modified capture session setup with FPS configuration
    private void createCameraPreviewSession() {
        try {
            // Create capture request builder
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            previewRequestBuilder.addTarget(imageReader.getSurface());
            
            // Configure FPS range based on target
            Range<Integer> fpsRange = new Range<>(targetFps, targetFps);
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            
            // Optimize for performance
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO); // Better for video
            previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON);
            
            // Disable unnecessary processing for speed
            previewRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE,
                    CaptureRequest.NOISE_REDUCTION_MODE_OFF);
            previewRequestBuilder.set(CaptureRequest.EDGE_MODE,
                    CaptureRequest.EDGE_MODE_OFF);
            
            // Create capture session...
            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()),
                    sessionStateCallback, backgroundHandler);
                    
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to create high-performance camera session", e);
        }
    }
    
    // Check camera capabilities for supported frame rates
    private Range<Integer>[] getSupportedFpsRanges() {
        try {
            CameraCharacteristics characteristics = 
                systemCameraManager.getCameraCharacteristics(cameraId);
            return characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to get FPS ranges", e);
            return new Range[0];
        }
    }
    
    // Modified frame throttling based on target FPS
    private boolean shouldSkipFrame(long currentTime) {
        long targetInterval = 1000 / targetFps; // Dynamic interval based on target FPS
        return lastFrameTime > 0 && (currentTime - lastFrameTime) < targetInterval;
    }
    
    // High-speed capture session for 120+ FPS (if supported)
    private void createHighSpeedCaptureSession() {
        try {
            CameraCharacteristics characteristics = 
                systemCameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = 
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            
            Range<Integer>[] highSpeedRanges = map.getHighSpeedVideoFpsRanges();
            
            if (highSpeedRanges.length > 0) {
                // Find suitable high-speed range
                Range<Integer> selectedRange = null;
                for (Range<Integer> range : highSpeedRanges) {
                    if (range.getUpper() >= targetFps) {
                        selectedRange = range;
                        break;
                    }
                }
                
                if (selectedRange != null) {
                    Log.d(TAG, "Using high-speed capture: " + selectedRange);
                    
                    // Create high-speed session
                    cameraDevice.createConstrainedHighSpeedCaptureSession(
                        Arrays.asList(imageReader.getSurface()),
                        new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                setupHighSpeedCapture(session, selectedRange);
                            }
                            
                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                Log.e(TAG, "High-speed session configuration failed");
                            }
                        }, backgroundHandler);
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to create high-speed session", e);
        }
    }
}