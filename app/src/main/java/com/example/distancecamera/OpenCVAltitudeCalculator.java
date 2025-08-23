package com.example.distancecamera;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.features2d.*;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.List;

public class OpenCVAltitudeCalculator {
    
    private ORB orbDetector;
    private DescriptorMatcher matcher;
    
    public OpenCVAltitudeCalculator() {
        orbDetector = ORB.create(500); // Max 500 features
        matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
    }
    
    public static class ImageData {
        public Mat image;
        public long timestamp;
        public double airplaneSpeed;
        
        public ImageData(Bitmap bitmap, long timestamp, double airplaneSpeed) {
            this.image = OpenCVUtils.bitmapToGrayMat(bitmap);
            this.timestamp = timestamp;
            this.airplaneSpeed = airplaneSpeed;
        }
    }
    
    /**
     * Calculate altitude using OpenCV feature matching
     */
    public double calculateAltitude(ImageData img1, ImageData img2, 
                                  double focalLengthMm, double sensorWidthMm) {
        
        // Calculate baseline (ground distance)
        double timeDelta = (img2.timestamp - img1.timestamp) / 1000.0;
        double baseline = img1.airplaneSpeed * timeDelta;
        
        // Detect and match features
        MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
        MatOfKeyPoint keypoints2 = new MatOfKeyPoint();
        Mat descriptors1 = new Mat();
        Mat descriptors2 = new Mat();
        
        orbDetector.detectAndCompute(img1.image, new Mat(), keypoints1, descriptors1);
        orbDetector.detectAndCompute(img2.image, new Mat(), keypoints2, descriptors2);
        
        if (descriptors1.rows() == 0 || descriptors2.rows() == 0) {
            return baseline / Math.tan(Math.toRadians(30)); // Fallback
        }
        
        // Match features
        MatOfDMatch matches = new MatOfDMatch();
        matcher.match(descriptors1, descriptors2, matches);
        
        // Filter good matches
        List<DMatch> goodMatches = filterGoodMatches(matches.toArray());
        
        if (goodMatches.size() < 10) {
            return baseline / Math.tan(Math.toRadians(30)); // Fallback
        }
        
        // Calculate average displacement
        KeyPoint[] kp1 = keypoints1.toArray();
        KeyPoint[] kp2 = keypoints2.toArray();
        
        double totalDisplacement = 0;
        int validMatches = 0;
        
        for (DMatch match : goodMatches) {
            Point p1 = kp1[match.queryIdx].pt;
            Point p2 = kp2[match.trainIdx].pt;
            
            double displacement = Math.abs(p2.x - p1.x); // Horizontal displacement
            
            // Filter out very small or very large displacements
            if (displacement > 2 && displacement < img1.image.cols() / 4) {
                totalDisplacement += displacement;
                validMatches++;
            }
        }
        
        if (validMatches == 0) return baseline / Math.tan(Math.toRadians(30));
        
        double avgDisplacement = totalDisplacement / validMatches;
        
        // Use camera parameters for accurate focal length
        double focalLengthPixels = OpenCVUtils.CameraParams.getFocalLengthPixels(img1.image.cols());
        
        // Calculate altitude: H = (f * B) / p
        double altitude = (focalLengthPixels * baseline) / avgDisplacement;
        
        // Clamp to reasonable values (10m to 10km)
        return Math.max(10, Math.min(10000, altitude));
    }
    
    private List<DMatch> filterGoodMatches(DMatch[] matches) {
        List<DMatch> goodMatches = new ArrayList<>();
        
        // Find min distance
        float minDist = Float.MAX_VALUE;
        for (DMatch match : matches) {
            if (match.distance < minDist) {
                minDist = match.distance;
            }
        }
        
        // Keep matches with distance < 2.5 * min_distance (more selective)
        float threshold = Math.max(2.5f * minDist, 25.0f);
        for (DMatch match : matches) {
            if (match.distance <= threshold) {
                goodMatches.add(match);
            }
        }
        
        // Sort by distance and keep only best matches
        goodMatches.sort((a, b) -> Float.compare(a.distance, b.distance));
        int maxMatches = Math.min(50, goodMatches.size());
        return goodMatches.subList(0, maxMatches);
        
        return goodMatches;
    }
}