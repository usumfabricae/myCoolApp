package com.example.distancecamera;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.core.content.ContextCompat;
import java.util.concurrent.Executor;

public class AutoCaptureAltimeter {
    
    private ImageCapture imageCapture;
    private OpenCVAltitudeCalculator calculator;
    private Handler captureHandler;
    private Runnable captureRunnable;
    private boolean isCapturing = false;
    
    private OpenCVAltitudeCalculator.ImageData previousImage = null;
    private double airplaneSpeed = 11.11; // 40 km/h = 11.11 m/s
    private AltitudeCallback callback;
    
    public interface AltitudeCallback {
        void onAltitudeCalculated(double altitude, double confidence);
        void onError(String error);
    }
    
    public AutoCaptureAltimeter(ImageCapture imageCapture, AltitudeCallback callback) {
        this.imageCapture = imageCapture;
        this.callback = callback;
        this.calculator = new OpenCVAltitudeCalculator();
        this.captureHandler = new Handler(Looper.getMainLooper());
    }
    
    public void startAutoCapture(double speedKmh) {
        this.airplaneSpeed = speedKmh / 3.6; // Convert to m/s
        this.isCapturing = true;
        
        captureRunnable = new Runnable() {
            @Override
            public void run() {
                if (isCapturing) {
                    captureImage();
                    captureHandler.postDelayed(this, 100); // 0.1 second = 100ms
                }
            }
        };
        
        captureHandler.post(captureRunnable);
    }
    
    public void stopAutoCapture() {
        isCapturing = false;
        if (captureRunnable != null) {
            captureHandler.removeCallbacks(captureRunnable);
        }
        previousImage = null;
    }
    
    private void captureImage() {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(null),
            new ImageCapture.OnImageCapturedCallback() {
                @Override
                public void onCaptureSuccess(ImageProxy image) {
                    processImage(image);
                    image.close();
                }
                
                @Override
                public void onError(ImageCaptureException exception) {
                    callback.onError("Capture failed: " + exception.getMessage());
                }
            }
        );
    }
    
    private void processImage(ImageProxy imageProxy) {
        Bitmap bitmap = AltitudeCalculator.imageProxyToBitmap(imageProxy);
        long timestamp = System.currentTimeMillis();
        
        OpenCVAltitudeCalculator.ImageData currentImage = 
            new OpenCVAltitudeCalculator.ImageData(bitmap, timestamp, airplaneSpeed);
        
        if (previousImage != null) {
            double altitude = calculator.calculateAltitude(
                previousImage, currentImage,
                OpenCVUtils.CameraParams.FOCAL_LENGTH_MM,
                OpenCVUtils.CameraParams.SENSOR_WIDTH_MM
            );
            
            // Calculate confidence based on time interval accuracy
            double timeDelta = (currentImage.timestamp - previousImage.timestamp) / 1000.0;
            double expectedDelta = 0.1; // 0.1 seconds
            double timeAccuracy = 1.0 - Math.abs(timeDelta - expectedDelta) / expectedDelta;
            double confidence = Math.max(0, Math.min(1, timeAccuracy));
            
            callback.onAltitudeCalculated(altitude, confidence);
        }
        
        previousImage = currentImage;
    }
    
    /**
     * Calculate theoretical minimum height for current settings
     */
    public static double getMinimumDetectableHeight() {
        return MinimumHeightCalculator.calculateMinimumHeight(
            40.0,    // 40 km/h
            0.1,     // 0.1 seconds
            1.0,     // 1 pixel minimum displacement
            3.67,    // Focal length mm
            5.76,    // Sensor width mm
            1920     // Image width pixels
        );
    }
}