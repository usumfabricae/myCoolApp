package com.example.distancecamera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    // Camera parameters now handled by OpenCVUtils.CameraParams
    
    private PreviewView previewView;
    private TextView altitudeText;
    private EditText speedInput;
    private Button captureButton;
    private OpenCVAltitudeCalculator altitudeCalculator;
    private AutoCaptureAltimeter autoCaptureAltimeter;
    private ImageCapture imageCapture;
    private boolean isAutoCapturing = false;
    
    private BaseLoaderCallback openCVLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == LoaderCallbackInterface.SUCCESS) {
                Log.d("OpenCV", "OpenCV loaded successfully");
                altitudeCalculator = new OpenCVAltitudeCalculator();
            } else {
                super.onManagerConnected(status);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        previewView = findViewById(R.id.previewView);
        altitudeText = findViewById(R.id.altitudeText);
        speedInput = findViewById(R.id.speedInput);
        captureButton = findViewById(R.id.captureButton);
        
        captureButton.setOnClickListener(v -> toggleAutoCapture());
        
        // Display minimum detectable height
        double minHeight = AutoCaptureAltimeter.getMinimumDetectableHeight();
        altitudeText.setText(String.format("Ready - Min detectable height: %.0f m\nAt 40 km/h, 0.1s intervals", minHeight));
        captureButton.setText("Start Auto Capture");
        
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_PERMISSIONS);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                altitudeText.setText("Camera permission required");
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
        
        // Initialize auto-capture altimeter
        autoCaptureAltimeter = new AutoCaptureAltimeter(imageCapture, new AutoCaptureAltimeter.AltitudeCallback() {
            @Override
            public void onAltitudeCalculated(double altitude, double confidence) {
                runOnUiThread(() -> {
                    altitudeText.setText(String.format(
                        "Altitude: %.0f m\nConfidence: %.1f%%\nCapturing every 0.1s", 
                        altitude, confidence * 100));
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> altitudeText.setText("Error: " + error));
            }
        });
    }

    private void toggleAutoCapture() {
        if (altitudeCalculator == null) {
            altitudeText.setText("OpenCV not loaded yet");
            return;
        }
        
        String speedStr = speedInput.getText().toString();
        if (speedStr.isEmpty()) {
            speedInput.setText("40");
            speedStr = "40";
        }
        
        double airplaneSpeed = Double.parseDouble(speedStr);
        
        if (!isAutoCapturing) {
            autoCaptureAltimeter.startAutoCapture(airplaneSpeed);
            isAutoCapturing = true;
            captureButton.setText("Stop Auto Capture");
            altitudeText.setText("Starting auto capture every 0.1 seconds...");
        } else {
            autoCaptureAltimeter.stopAutoCapture();
            isAutoCapturing = false;
            captureButton.setText("Start Auto Capture");
            double minHeight = AutoCaptureAltimeter.getMinimumDetectableHeight();
            altitudeText.setText(String.format("Stopped - Min detectable height: %.0f m", minHeight));
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, openCVLoaderCallback);
        } else {
            openCVLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }
    
    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
}