package com.example.distancecamera;

import android.graphics.Bitmap;
import androidx.camera.core.ImageProxy;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.nio.ByteBuffer;

public class OpenCVUtils {
    
    /**
     * Convert ImageProxy to OpenCV Mat
     */
    public static Mat imageProxyToMat(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        Mat yuv = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth(), CvType.CV_8UC1);
        yuv.put(0, 0, nv21);
        
        Mat rgb = new Mat();
        Imgproc.cvtColor(yuv, rgb, Imgproc.COLOR_YUV2RGB_NV21);
        
        Mat gray = new Mat();
        Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY);
        
        return gray;
    }
    
    /**
     * Convert Bitmap to OpenCV Mat (grayscale)
     */
    public static Mat bitmapToGrayMat(Bitmap bitmap) {
        Mat mat = new Mat();
        Utils.bitmapToMat(bitmap, mat);
        
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
        
        return gray;
    }
    
    /**
     * Camera calibration parameters for common smartphone cameras
     */
    public static class CameraParams {
        public static final double FOCAL_LENGTH_MM = 3.67;  // iPhone/Android typical
        public static final double SENSOR_WIDTH_MM = 5.76;  // iPhone/Android typical
        public static final double SENSOR_HEIGHT_MM = 4.29; // iPhone/Android typical
        
        /**
         * Calculate focal length in pixels
         */
        public static double getFocalLengthPixels(int imageWidth) {
            return (FOCAL_LENGTH_MM * imageWidth) / SENSOR_WIDTH_MM;
        }
        
        /**
         * Get camera matrix for OpenCV operations
         */
        public static Mat getCameraMatrix(int imageWidth, int imageHeight) {
            double fx = getFocalLengthPixels(imageWidth);
            double fy = getFocalLengthPixels(imageWidth); // Assume square pixels
            double cx = imageWidth / 2.0;
            double cy = imageHeight / 2.0;
            
            Mat cameraMatrix = Mat.eye(3, 3, CvType.CV_64FC1);
            cameraMatrix.put(0, 0, fx);
            cameraMatrix.put(1, 1, fy);
            cameraMatrix.put(0, 2, cx);
            cameraMatrix.put(1, 2, cy);
            
            return cameraMatrix;
        }
    }
}