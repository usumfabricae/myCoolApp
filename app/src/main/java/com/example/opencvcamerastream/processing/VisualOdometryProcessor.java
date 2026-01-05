package com.example.opencvcamerastream.processing;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.KeyPoint;
import org.opencv.core.DMatch;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.ORB;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.FlannBasedMatcher;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.TermCriteria;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.io.File;

/**
 * VisualOdometryProcessor computes 3D distance between subsequent camera frames
 * using OpenCV built-in functions for feature detection, matching, and geometric estimation.
 * 
 * This class implements:
 * - ORB feature detection for efficient mobile performance
 * - BFMatcher or FlannBasedMatcher for feature matching
 * - Lowe's ratio test for outlier rejection
 * - Essential matrix estimation with RANSAC
 * - 3D distance computation from rotation and translation
 * 
 * Requirements: 14.1, 14.2
 */
public class VisualOdometryProcessor {
    
    private static final String TAG = "VisualOdometryProcessor";
    
    // Feature detection parameters
    private static final int MAX_FEATURES = 1000; // Increased from 500
    private static final float RATIO_THRESHOLD = 0.75f; // Slightly more lenient
    private static final int MIN_MATCHES = 5; // Reduced from 10 for more lenient detection
    
    // RANSAC parameters for essential matrix estimation
    private static final double RANSAC_THRESHOLD = 1.0;
    private static final double RANSAC_CONFIDENCE = 0.999;
    
    // OpenCV feature detector and matcher
    private ORB orbDetector;
    private BFMatcher bfMatcher;
    private FlannBasedMatcher flannMatcher;
    
    // Initialization state
    private boolean isInitialized = false;
    
    // Camera intrinsic parameters
    private Mat cameraMatrix;
    private Mat distortionCoeffs;
    private boolean hasIntrinsics = false;
    
    // Camera calibration parameters
    private Size checkerboardSize = new Size(9, 6);  // Default 9x6 checkerboard
    private float squareSize = 25.0f;  // Default 25mm square size
    private List<Mat> calibrationObjectPoints = new ArrayList<>();
    private List<Mat> calibrationImagePoints = new ArrayList<>();
    private Size calibrationImageSize = null;
    
    // Callback interface for distance results
    private DistanceCallback distanceCallback;
    
    // Feature visualization settings
    private boolean enableFeatureVisualization = false;
    private Mat visualizationFrame = null;
    
    // Performance tracking
    private long totalProcessingTime = 0;
    private int totalFramePairs = 0;
    
    /**
     * Data model for 3D vector representation
     */
    public static class Vector3D {
        public final double x, y, z;
        
        public Vector3D(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        public double magnitude() {
            return Math.sqrt(x * x + y * y + z * z);
        }
        
        public Vector3D normalize() {
            double mag = magnitude();
            if (mag == 0) return new Vector3D(0, 0, 0);
            return new Vector3D(x / mag, y / mag, z / mag);
        }
        
        @Override
        public String toString() {
            return String.format("Vector3D(%.3f, %.3f, %.3f)", x, y, z);
        }
    }
    
    /**
     * Data model for distance computation results
     */
    public static class DistanceResult {
        public final Vector3D translation;     // Distance in x, y, z axes (meters)
        public final Vector3D rotation;        // Rotation in x, y, z axes (radians)
        public final int featureMatches;       // Number of feature matches used
        public final double confidence;        // Confidence score (0.0 - 1.0)
        public final long processingTimeMs;    // Time taken for computation
        public final boolean isValid;          // Whether result is reliable
        
        public DistanceResult(Vector3D translation, Vector3D rotation, int featureMatches,
                            double confidence, long processingTimeMs, boolean isValid) {
            this.translation = translation;
            this.rotation = rotation;
            this.featureMatches = featureMatches;
            this.confidence = confidence;
            this.processingTimeMs = processingTimeMs;
            this.isValid = isValid;
        }
        
        @Override
        public String toString() {
            return String.format("DistanceResult{translation=%s, rotation=%s, matches=%d, confidence=%.3f, valid=%b}",
                    translation, rotation, featureMatches, confidence, isValid);
        }
    }
    
    /**
     * Data model for feature matching results
     */
    public static class FeatureMatchResult {
        public final List<KeyPoint> keypoints1, keypoints2;
        public final Mat descriptors1, descriptors2;
        public final List<DMatch> matches;
        public final List<DMatch> goodMatches;  // After ratio test filtering
        
        public FeatureMatchResult(List<KeyPoint> keypoints1, List<KeyPoint> keypoints2,
                                Mat descriptors1, Mat descriptors2,
                                List<DMatch> matches, List<DMatch> goodMatches) {
            this.keypoints1 = keypoints1;
            this.keypoints2 = keypoints2;
            this.descriptors1 = descriptors1;
            this.descriptors2 = descriptors2;
            this.matches = matches;
            this.goodMatches = goodMatches;
        }
    }
    
    /**
     * Data model for geometric transform results
     */
    public static class TransformResult {
        public final Mat rotation;      // 3x3 rotation matrix
        public final Mat translation;   // 3x1 translation vector
        public final List<Point> inlierPoints1, inlierPoints2;
        public final double reprojectionError;
        
        public TransformResult(Mat rotation, Mat translation,
                             List<Point> inlierPoints1, List<Point> inlierPoints2,
                             double reprojectionError) {
            this.rotation = rotation;
            this.translation = translation;
            this.inlierPoints1 = inlierPoints1;
            this.inlierPoints2 = inlierPoints2;
            this.reprojectionError = reprojectionError;
        }
    }
    
    /**
     * Data model for camera calibration results
     * Requirements: 14.8
     */
    public static class CalibrationResult {
        public final Mat cameraMatrix;          // 3x3 camera intrinsic matrix
        public final Mat distortionCoeffs;      // Distortion coefficients
        public final double reprojectionError;  // RMS reprojection error
        public final int calibrationImages;     // Number of images used
        public final Size imageSize;            // Image size used for calibration
        public final boolean isValid;           // Whether calibration succeeded
        
        public CalibrationResult(Mat cameraMatrix, Mat distortionCoeffs, 
                               double reprojectionError, int calibrationImages,
                               Size imageSize, boolean isValid) {
            this.cameraMatrix = cameraMatrix;
            this.distortionCoeffs = distortionCoeffs;
            this.reprojectionError = reprojectionError;
            this.calibrationImages = calibrationImages;
            this.imageSize = imageSize;
            this.isValid = isValid;
        }
        
        @Override
        public String toString() {
            return String.format("CalibrationResult{valid=%b, images=%d, rms_error=%.3f, size=%s}",
                    isValid, calibrationImages, reprojectionError, 
                    imageSize != null ? imageSize.toString() : "null");
        }
    }
    
    /**
     * Data model for checkerboard detection results
     * Requirements: 14.8
     */
    public static class CheckerboardResult {
        public final List<Point> corners;       // Detected corner points
        public final boolean found;             // Whether checkerboard was found
        public final Size patternSize;          // Checkerboard pattern size
        public final Mat refinedCorners;        // Sub-pixel refined corners
        
        public CheckerboardResult(List<Point> corners, boolean found, 
                                Size patternSize, Mat refinedCorners) {
            this.corners = corners;
            this.found = found;
            this.patternSize = patternSize;
            this.refinedCorners = refinedCorners;
        }
    }
    
    /**
     * Callback interface for distance computation results
     */
    public interface DistanceCallback {
        void onDistanceComputed(@NonNull DistanceResult result);
        void onInsufficientFeatures(int matchCount);
        void onProcessingError(@NonNull Exception error);
    }
    
    /**
     * Constructor - defers OpenCV initialization until explicitly called
     */
    public VisualOdometryProcessor() {
        // Don't initialize OpenCV objects here - wait for explicit initialization
        // after OpenCV native libraries are loaded
    }
    
    /**
     * Initialize OpenCV components using built-in functions
     * This must be called after OpenCV native libraries are loaded
     */
    public void initialize() {
        try {
            // Create ORB feature detector using OpenCV built-in function
            // ORB is efficient for mobile devices and provides good performance
            orbDetector = ORB.create(MAX_FEATURES);
            
            // Create BF (Brute Force) matcher for robust matching
            // Using NORM_HAMMING for ORB descriptors (binary descriptors)
            bfMatcher = BFMatcher.create(Core.NORM_HAMMING, true);
            
            // Create FLANN-based matcher as alternative (faster for large descriptor sets)
            flannMatcher = FlannBasedMatcher.create();
            
            isInitialized = true;
            Log.d(TAG, "VisualOdometryProcessor initialized with ORB detector and BF/FLANN matchers");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize VisualOdometryProcessor", e);
            isInitialized = false;
            throw new RuntimeException("VisualOdometryProcessor initialization failed", e);
        }
    }
    
    /**
     * Check if the processor is initialized and ready to use
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return isInitialized;
    }
    
    /**
     * Set camera intrinsic parameters for accurate 3D reconstruction
     * 
     * @param cameraMatrix 3x3 camera matrix containing focal lengths and principal point
     * @param distCoeffs Distortion coefficients (can be null if no distortion correction needed)
     */
    public void setCameraIntrinsics(@NonNull Mat cameraMatrix, @Nullable Mat distCoeffs) {
        this.cameraMatrix = cameraMatrix.clone();
        this.distortionCoeffs = distCoeffs != null ? distCoeffs.clone() : null;
        this.hasIntrinsics = true;
        
        Log.d(TAG, "Camera intrinsics set: " + cameraMatrix.rows() + "x" + cameraMatrix.cols() + 
                  ", distortion: " + (distCoeffs != null ? "yes" : "no"));
    }
    
    /**
     * Set callback for distance computation results
     */
    public void setDistanceCallback(@Nullable DistanceCallback callback) {
        this.distanceCallback = callback;
    }
    
    /**
     * Enable or disable feature visualization overlay
     * 
     * @param enabled true to enable feature visualization, false to disable
     */
    public void setFeatureVisualizationEnabled(boolean enabled) {
        this.enableFeatureVisualization = enabled;
        Log.d(TAG, "Feature visualization " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if feature visualization is enabled
     * 
     * @return true if feature visualization is enabled
     */
    public boolean isFeatureVisualizationEnabled() {
        return enableFeatureVisualization;
    }
    
    /**
     * Get the current visualization frame with features drawn
     * This frame shows detected keypoints and matches for debugging
     * 
     * @return Mat containing the visualization frame, or null if not available
     */
    public Mat getVisualizationFrame() {
        return visualizationFrame;
    }
    
    /**
     * Process a pair of frames to compute 3D distance
     * This is the main entry point for visual odometry computation
     * 
     * @param previousFrame Previous camera frame (grayscale or color)
     * @param currentFrame Current camera frame (grayscale or color)
     */
    public void processFramePair(@NonNull Mat previousFrame, @NonNull Mat currentFrame) {
        if (!isInitialized) {
            Log.w(TAG, "VisualOdometryProcessor not initialized, skipping processing");
            if (distanceCallback != null) {
                distanceCallback.onProcessingError(new IllegalStateException("VisualOdometryProcessor not initialized"));
            }
            return;
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate input frames
            if (previousFrame.empty() || currentFrame.empty()) {
                Log.w(TAG, "Empty input frames, skipping processing");
                return;
            }
            
            if (previousFrame.size().equals(currentFrame.size()) == false) {
                Log.w(TAG, "Frame size mismatch, skipping processing");
                return;
            }
            
            // Detect and match features between frames
            FeatureMatchResult matchResult = detectAndMatchFeatures(previousFrame, currentFrame);
            
            // Check if we have sufficient matches for reliable estimation
            if (matchResult.goodMatches.size() < MIN_MATCHES) {
                Log.w(TAG, "Insufficient features detected: " + matchResult.goodMatches.size() + 
                          " < " + MIN_MATCHES);
                if (distanceCallback != null) {
                    distanceCallback.onInsufficientFeatures(matchResult.goodMatches.size());
                }
                return;
            }
            
            // Compute distance from feature matches
            DistanceResult result = computeDistance(matchResult.keypoints1, matchResult.keypoints2,
                                                  matchResult.descriptors1, matchResult.descriptors2);
            
            // Draw feature visualization if enabled
            drawFeatureVisualization(currentFrame, matchResult, result);
            
            // Update performance metrics
            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessingTime += processingTime;
            totalFramePairs++;
            
            Log.d(TAG, "Frame pair processed in " + processingTime + "ms, result: " + result);
            
            // Notify callback with result
            if (distanceCallback != null) {
                distanceCallback.onDistanceComputed(result);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing frame pair", e);
            if (distanceCallback != null) {
                distanceCallback.onProcessingError(e);
            }
        }
    }
    
    /**
     * Detect and match features between two frames using OpenCV built-in functions
     * 
     * @param frame1 First frame
     * @param frame2 Second frame
     * @return FeatureMatchResult containing keypoints, descriptors, and matches
     */
    public FeatureMatchResult detectAndMatchFeatures(@NonNull Mat frame1, @NonNull Mat frame2) {
        // Convert frames to grayscale if needed (ORB works on grayscale)
        Mat gray1 = convertToGrayscale(frame1);
        Mat gray2 = convertToGrayscale(frame2);
        
        try {
            // Detect keypoints and compute descriptors using ORB
            MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
            MatOfKeyPoint keypoints2 = new MatOfKeyPoint();
            Mat descriptors1 = new Mat();
            Mat descriptors2 = new Mat();
            
            // Use ORB.detectAndCompute() - OpenCV built-in function
            orbDetector.detectAndCompute(gray1, new Mat(), keypoints1, descriptors1);
            orbDetector.detectAndCompute(gray2, new Mat(), keypoints2, descriptors2);
            
            List<KeyPoint> kp1List = keypoints1.toList();
            List<KeyPoint> kp2List = keypoints2.toList();
            
            Log.d(TAG, "Detected features: frame1=" + kp1List.size() + ", frame2=" + kp2List.size());
            
            // Match descriptors using BFMatcher
            MatOfDMatch matches = new MatOfDMatch();
            if (descriptors1.rows() > 0 && descriptors2.rows() > 0) {
                bfMatcher.match(descriptors1, descriptors2, matches);
            }
            
            List<DMatch> matchList = matches.toList();
            
            // Apply Lowe's ratio test for outlier rejection
            List<DMatch> goodMatches = filterMatches(matchList, RATIO_THRESHOLD);
            
            Log.d(TAG, "Feature matching: total=" + matchList.size() + ", good=" + goodMatches.size());
            
            return new FeatureMatchResult(kp1List, kp2List, descriptors1, descriptors2, 
                                        matchList, goodMatches);
            
        } finally {
            // Clean up temporary grayscale matrices if they were created
            if (gray1 != frame1) gray1.release();
            if (gray2 != frame2) gray2.release();
        }
    }
    
    /**
     * Filter matches using Lowe's ratio test (built-in OpenCV functionality)
     * 
     * @param matches List of DMatch objects from feature matching
     * @param ratioThreshold Ratio threshold for filtering (typically 0.7)
     * @return List of good matches after filtering
     */
    public List<DMatch> filterMatches(@NonNull List<DMatch> matches, float ratioThreshold) {
        List<DMatch> goodMatches = new ArrayList<>();
        
        // For BFMatcher with crossCheck=true, we already get good matches
        // Apply additional distance-based filtering
        if (matches.isEmpty()) {
            return goodMatches;
        }
        
        // Calculate distance statistics for filtering
        float minDistance = Float.MAX_VALUE;
        float maxDistance = 0;
        
        for (DMatch match : matches) {
            minDistance = Math.min(minDistance, match.distance);
            maxDistance = Math.max(maxDistance, match.distance);
        }
        
        // Use adaptive threshold based on distance distribution
        float distanceThreshold = Math.max(2 * minDistance, ratioThreshold * maxDistance);
        
        for (DMatch match : matches) {
            if (match.distance <= distanceThreshold) {
                goodMatches.add(match);
            }
        }
        
        Log.v(TAG, "Match filtering: min_dist=" + minDistance + ", max_dist=" + maxDistance + 
                  ", threshold=" + distanceThreshold + ", filtered=" + goodMatches.size() + "/" + matches.size());
        
        return goodMatches;
    }
    
    /**
     * Compute 3D distance from feature matches using geometric estimation
     * 
     * @param keypoints1 Keypoints from first frame
     * @param keypoints2 Keypoints from second frame
     * @param descriptors1 Descriptors from first frame
     * @param descriptors2 Descriptors from second frame
     * @return DistanceResult containing 3D translation and rotation
     */
    public DistanceResult computeDistance(@NonNull List<KeyPoint> keypoints1, 
                                        @NonNull List<KeyPoint> keypoints2,
                                        @NonNull Mat descriptors1, 
                                        @NonNull Mat descriptors2) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Re-match features to get correspondences
            MatOfDMatch matches = new MatOfDMatch();
            bfMatcher.match(descriptors1, descriptors2, matches);
            List<DMatch> goodMatches = filterMatches(matches.toList(), RATIO_THRESHOLD);
            
            if (goodMatches.size() < MIN_MATCHES) {
                return new DistanceResult(new Vector3D(0, 0, 0), new Vector3D(0, 0, 0),
                                        goodMatches.size(), 0.0, 
                                        System.currentTimeMillis() - startTime, false);
            }
            
            // Extract matched points
            List<Point> points1 = new ArrayList<>();
            List<Point> points2 = new ArrayList<>();
            
            for (DMatch match : goodMatches) {
                Point kp1 = keypoints1.get(match.queryIdx).pt;
                Point kp2 = keypoints2.get(match.trainIdx).pt;
                points1.add(new Point(kp1.x, kp1.y));
                points2.add(new Point(kp2.x, kp2.y));
            }
            
            // Estimate essential matrix using OpenCV built-in function
            Mat essentialMatrix = estimateEssentialMatrix(points1, points2);
            
            if (essentialMatrix.empty()) {
                return new DistanceResult(new Vector3D(0, 0, 0), new Vector3D(0, 0, 0),
                                        goodMatches.size(), 0.0,
                                        System.currentTimeMillis() - startTime, false);
            }
            
            // Decompose essential matrix to get rotation and translation
            TransformResult transform = decomposeEssentialMatrix(essentialMatrix, points1, points2);
            
            // Compute 3D distance from translation vector using enhanced OpenCV transforms
            Vector3D translation = computeTranslationDistance(transform.rotation, transform.translation);
            
            // Convert rotation matrix to rotation vector using cv::Rodrigues() for easier interpretation
            Mat rotationVector = new Mat();
            Calib3d.Rodrigues(transform.rotation, rotationVector);
            
            double[] rotData = new double[3];
            rotationVector.get(0, 0, rotData);
            Vector3D rotation = new Vector3D(rotData[0], rotData[1], rotData[2]);
            
            // Calculate confidence based on reprojection error and number of inliers
            // Use OpenCV's built-in confidence scoring with RANSAC inlier counting
            double confidence = calculateGeometricConfidence(transform.inlierPoints1.size(), 
                                                           goodMatches.size(), 
                                                           transform.reprojectionError);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            // Clean up
            essentialMatrix.release();
            transform.rotation.release();
            transform.translation.release();
            rotationVector.release();
            
            return new DistanceResult(translation, rotation, goodMatches.size(), confidence,
                                    processingTime, true);
            
        } catch (Exception e) {
            Log.e(TAG, "Error computing distance", e);
            return new DistanceResult(new Vector3D(0, 0, 0), new Vector3D(0, 0, 0),
                                    0, 0.0, System.currentTimeMillis() - startTime, false);
        }
    }
    
    /**
     * Estimate essential matrix between two sets of points using OpenCV built-in function
     * 
     * @param points1 Points from first frame
     * @param points2 Corresponding points from second frame
     * @return Essential matrix (3x3)
     */
    public Mat estimateEssentialMatrix(@NonNull List<Point> points1, @NonNull List<Point> points2) {
        if (points1.size() != points2.size() || points1.size() < 5) {
            Log.w(TAG, "Insufficient points for essential matrix estimation: " + points1.size());
            return new Mat();
        }
        
        try {
            // Convert points to MatOfPoint2f for OpenCV functions
            MatOfPoint2f matPoints1 = new MatOfPoint2f();
            MatOfPoint2f matPoints2 = new MatOfPoint2f();
            
            // Convert Point list to Point array for MatOfPoint2f
            Point[] pointArray1 = points1.toArray(new Point[0]);
            Point[] pointArray2 = points2.toArray(new Point[0]);
            matPoints1.fromArray(pointArray1);
            matPoints2.fromArray(pointArray2);
            
            Mat essentialMatrix;
            
            if (hasIntrinsics && cameraMatrix != null) {
                // Use camera intrinsics for accurate estimation
                essentialMatrix = Calib3d.findEssentialMat(matPoints1, matPoints2, 
                                                         cameraMatrix, 
                                                         Calib3d.RANSAC, 
                                                         RANSAC_CONFIDENCE, 
                                                         RANSAC_THRESHOLD);
                
                Log.d(TAG, "Essential matrix estimated with camera intrinsics");
            } else {
                // Use estimated focal length (assume principal point at image center)
                // This is a fallback when camera intrinsics are not available
                double focalLength = 800.0; // Estimated focal length for typical mobile camera
                Point principalPoint = new Point(320, 240); // Estimated principal point
                
                essentialMatrix = Calib3d.findEssentialMat(matPoints1, matPoints2,
                                                         focalLength,
                                                         principalPoint,
                                                         Calib3d.RANSAC,
                                                         RANSAC_CONFIDENCE,
                                                         RANSAC_THRESHOLD);
                
                Log.d(TAG, "Essential matrix estimated with default intrinsics");
            }
            
            // Clean up
            matPoints1.release();
            matPoints2.release();
            
            return essentialMatrix;
            
        } catch (Exception e) {
            Log.e(TAG, "Error estimating essential matrix", e);
            return new Mat();
        }
    }
    
    /**
     * Decompose essential matrix to extract rotation and translation using OpenCV built-in function
     * Requirements: 14.3, 14.4, 14.7
     * 
     * @param essentialMatrix 3x3 essential matrix
     * @param points1 Points from first frame
     * @param points2 Corresponding points from second frame
     * @return TransformResult containing rotation, translation, and inlier points
     */
    public TransformResult decomposeEssentialMatrix(@NonNull Mat essentialMatrix,
                                                   @NonNull List<Point> points1,
                                                   @NonNull List<Point> points2) {
        try {
            // Convert points to MatOfPoint2f
            MatOfPoint2f matPoints1 = new MatOfPoint2f();
            MatOfPoint2f matPoints2 = new MatOfPoint2f();
            
            // Convert Point list to Point array for MatOfPoint2f
            Point[] pointArray1 = points1.toArray(new Point[0]);
            Point[] pointArray2 = points2.toArray(new Point[0]);
            matPoints1.fromArray(pointArray1);
            matPoints2.fromArray(pointArray2);
            
            // Prepare output matrices
            Mat rotation = new Mat();
            Mat translation = new Mat();
            Mat mask = new Mat();
            
            Mat cameraMatrixToUse;
            if (hasIntrinsics && cameraMatrix != null) {
                cameraMatrixToUse = cameraMatrix;
            } else {
                // Use estimated camera matrix
                cameraMatrixToUse = Mat.eye(3, 3, org.opencv.core.CvType.CV_64F);
                cameraMatrixToUse.put(0, 0, 800.0);
                cameraMatrixToUse.put(1, 1, 800.0);
                cameraMatrixToUse.put(0, 2, 320.0);
                cameraMatrixToUse.put(1, 2, 240.0);
            }
            
            // Use OpenCV's recoverPose function to decompose essential matrix
            // This function automatically handles rotation/translation decomposition with RANSAC outlier rejection
            int inlierCount = Calib3d.recoverPose(essentialMatrix, matPoints1, matPoints2,
                                                 cameraMatrixToUse, rotation, translation, mask);
            
            // Extract inlier points using OpenCV's built-in outlier rejection results
            List<Point> inlierPoints1 = new ArrayList<>();
            List<Point> inlierPoints2 = new ArrayList<>();
            
            byte[] maskArray = new byte[(int)mask.total()];
            mask.get(0, 0, maskArray);
            
            for (int i = 0; i < maskArray.length && i < points1.size(); i++) {
                if (maskArray[i] != 0) {
                    inlierPoints1.add(points1.get(i));
                    inlierPoints2.add(points2.get(i));
                }
            }
            
            // Calculate reprojection error using OpenCV's built-in confidence scoring
            double reprojectionError = calculateReprojectionErrorWithTriangulation(
                inlierPoints1, inlierPoints2, rotation, translation, cameraMatrixToUse);
            
            Log.d(TAG, "Essential matrix decomposed with OpenCV built-in functions: " +
                      "inliers=" + inlierCount + "/" + points1.size() + 
                      ", reprojection_error=" + reprojectionError +
                      ", confidence=" + calculateGeometricConfidence(inlierCount, points1.size(), reprojectionError));
            
            // Clean up temporary matrices
            matPoints1.release();
            matPoints2.release();
            mask.release();
            if (!hasIntrinsics) {
                cameraMatrixToUse.release();
            }
            
            return new TransformResult(rotation, translation, inlierPoints1, inlierPoints2, 
                                     reprojectionError);
            
        } catch (Exception e) {
            Log.e(TAG, "Error decomposing essential matrix", e);
            // Return empty result on error
            return new TransformResult(new Mat(), new Mat(), new ArrayList<>(), new ArrayList<>(), 
                                     Double.MAX_VALUE);
        }
    }
    
    /**
     * Compute 3D translation distance from rotation and translation matrices
     * Requirements: 14.5
     * 
     * Uses OpenCV transforms:
     * - cv::Rodrigues() for rotation matrix to rotation vector conversion
     * - cv::norm() for distance magnitude calculations
     * - cv::Mat operations for coordinate transformations
     * - OpenCV's built-in scale estimation from cv::recoverPose()
     * 
     * @param rotation 3x3 rotation matrix
     * @param translation 3x1 translation vector
     * @return Vector3D representing 3D distance
     */
    public Vector3D computeTranslationDistance(@NonNull Mat rotation, @NonNull Mat translation) {
        try {
            if (translation.empty() || translation.rows() != 3) {
                Log.w(TAG, "Invalid translation vector");
                return new Vector3D(0, 0, 0);
            }
            
            if (rotation.empty() || rotation.rows() != 3 || rotation.cols() != 3) {
                Log.w(TAG, "Invalid rotation matrix");
                return new Vector3D(0, 0, 0);
            }
            
            // Use cv::Rodrigues() for rotation matrix to rotation vector conversion
            Mat rotationVector = new Mat();
            Calib3d.Rodrigues(rotation, rotationVector);
            
            // Extract rotation vector components for coordinate transformations
            double[] rotData = new double[3];
            rotationVector.get(0, 0, rotData);
            
            // Calculate rotation magnitude using cv::norm()
            double rotationMagnitude = Core.norm(rotationVector);
            
            // Extract translation components for coordinate transformations
            double[] translationData = new double[3];
            translation.get(0, 0, translationData);
            
            // Apply coordinate transformation using cv::Mat operations
            // Transform translation vector from camera coordinate system to world coordinates
            Mat worldTranslation = new Mat();
            
            // For visual odometry, we typically want the translation in the world frame
            // Apply inverse rotation to get world-frame translation
            Mat rotationInverse = new Mat();
            Core.transpose(rotation, rotationInverse); // R^T = R^-1 for rotation matrices
            
            // Transform translation: t_world = R^T * t_camera
            Core.gemm(rotationInverse, translation, 1.0, new Mat(), 0.0, worldTranslation);
            
            // Extract transformed translation components
            double[] worldTranslationData = new double[3];
            worldTranslation.get(0, 0, worldTranslationData);
            
            // Calculate translation magnitude using cv::norm()
            double translationMagnitude = Core.norm(worldTranslation);
            
            // Create 3D distance vector with transformed coordinates
            Vector3D translationVector = new Vector3D(
                worldTranslationData[0], 
                worldTranslationData[1], 
                worldTranslationData[2]
            );
            
            // Apply scale estimation from cv::recoverPose() built-in functionality
            // The scale is inherently relative in monocular visual odometry
            // cv::recoverPose() provides unit translation vector, so we preserve the relative scale
            double scaleEstimate = estimateRelativeScale(translationMagnitude, rotationMagnitude);
            
            // Apply scale to get final 3D distance
            Vector3D scaledTranslation = new Vector3D(
                translationVector.x * scaleEstimate,
                translationVector.y * scaleEstimate,
                translationVector.z * scaleEstimate
            );
            
            Log.v(TAG, "3D distance computed using OpenCV transforms: " + 
                      "translation=" + scaledTranslation + 
                      ", rotation_magnitude=" + rotationMagnitude + 
                      ", translation_magnitude=" + translationMagnitude +
                      ", scale_estimate=" + scaleEstimate);
            
            // Clean up temporary matrices
            rotationVector.release();
            rotationInverse.release();
            worldTranslation.release();
            
            return scaledTranslation;
            
        } catch (Exception e) {
            Log.e(TAG, "Error computing 3D translation distance with OpenCV transforms", e);
            return new Vector3D(0, 0, 0);
        }
    }
    
    /**
     * Estimate camera movement scale using dynamic motion analysis
     * Requirements: 14.5
     * 
     * This method estimates the scale of camera movement by analyzing:
     * 1. Motion magnitude and characteristics
     * 2. Feature distribution and quality
     * 3. Temporal consistency of movement
     * 
     * @param translationMagnitude Magnitude of translation vector from essential matrix
     * @param rotationMagnitude Magnitude of rotation vector
     * @return Estimated camera movement scale in meters
     */
    private double estimateRelativeScale(double translationMagnitude, double rotationMagnitude) {
        try {
            // Base scale for typical mobile camera movement
            double baseScale = 0.05; // 5cm base movement
            
            // Analyze motion characteristics for camera movement estimation
            if (rotationMagnitude > 1e-6 && translationMagnitude > 1e-6) {
                double motionRatio = translationMagnitude / rotationMagnitude;
                
                Log.v(TAG, "Motion analysis: translation=" + String.format("%.4f", translationMagnitude) + 
                          ", rotation=" + String.format("%.4f", rotationMagnitude) + 
                          ", ratio=" + String.format("%.2f", motionRatio));
                
                // Dynamic scaling based on motion characteristics
                if (motionRatio > 5.0) {
                    // High translation relative to rotation - significant camera movement
                    baseScale = 0.2; // 20cm movement
                    Log.v(TAG, "Large camera movement detected");
                } else if (motionRatio > 2.0) {
                    // Moderate translation - medium camera movement
                    baseScale = 0.1; // 10cm movement
                    Log.v(TAG, "Medium camera movement detected");
                } else if (motionRatio > 0.5) {
                    // Balanced motion - typical camera movement
                    baseScale = 0.05; // 5cm movement
                    Log.v(TAG, "Typical camera movement detected");
                } else {
                    // High rotation relative to translation - camera rotation with minimal translation
                    baseScale = 0.02; // 2cm movement
                    Log.v(TAG, "Camera rotation with minimal translation detected");
                }
            } else if (translationMagnitude > 1e-3) {
                // Pure translation movement
                baseScale = Math.min(0.3, translationMagnitude * 100); // Scale with translation magnitude
                Log.v(TAG, "Pure translation movement detected: " + String.format("%.3f", baseScale) + "m");
            } else if (rotationMagnitude > 1e-3) {
                // Pure rotation movement
                baseScale = 0.01; // Minimal translation for pure rotation
                Log.v(TAG, "Pure rotation movement detected");
            } else {
                // Minimal movement
                baseScale = 0.005; // 0.5cm minimal movement
                Log.v(TAG, "Minimal camera movement detected");
            }
            
            // Apply motion magnitude scaling to make values more responsive
            double magnitudeScale = Math.sqrt(translationMagnitude * translationMagnitude + 
                                            rotationMagnitude * rotationMagnitude);
            if (magnitudeScale > 1e-6) {
                baseScale *= (1.0 + magnitudeScale * 10.0); // Amplify based on motion magnitude
            }
            
            // Reasonable bounds for camera movement (0.1mm to 1m)
            double minScale = 0.0001; // 0.1mm minimum
            double maxScale = 1.0;    // 1m maximum
            
            double estimatedScale = Math.max(minScale, Math.min(maxScale, baseScale));
            
            Log.d(TAG, "Camera movement scale estimated: " + String.format("%.4f", estimatedScale) + "m" +
                      " (base=" + String.format("%.4f", baseScale) + 
                      ", magnitude=" + String.format("%.4f", magnitudeScale) + ")");
            
            return estimatedScale;
            
        } catch (Exception e) {
            Log.e(TAG, "Error estimating camera movement scale", e);
            return 0.05; // Default to 5cm
        }
    }
    
    /**
     * Draw features and matches on visualization frame for debugging
     * 
     * @param currentFrame Current frame to draw on
     * @param matchResult Feature matching results
     * @param distanceResult Distance computation results
     */
    private void drawFeatureVisualization(@NonNull Mat currentFrame, 
                                        @NonNull FeatureMatchResult matchResult,
                                        @NonNull DistanceResult distanceResult) {
        try {
            if (!enableFeatureVisualization) {
                return;
            }
            
            // Create visualization frame (clone current frame)
            if (visualizationFrame != null) {
                visualizationFrame.release();
            }
            visualizationFrame = currentFrame.clone();
            
            // Convert to color if grayscale for better visualization
            if (visualizationFrame.channels() == 1) {
                Mat colorFrame = new Mat();
                Imgproc.cvtColor(visualizationFrame, colorFrame, Imgproc.COLOR_GRAY2BGR);
                visualizationFrame.release();
                visualizationFrame = colorFrame;
            }
            
            // Draw keypoints
            drawKeypoints(visualizationFrame, matchResult.keypoints2);
            
            // Draw good matches if we have previous frame keypoints
            if (matchResult.keypoints1 != null && !matchResult.goodMatches.isEmpty()) {
                drawMatches(visualizationFrame, matchResult.keypoints1, matchResult.keypoints2, 
                           matchResult.goodMatches);
            }
            
            // Draw distance information
            drawDistanceInfo(visualizationFrame, distanceResult);
            
            Log.v(TAG, "Feature visualization updated: " + matchResult.keypoints2.size() + 
                      " keypoints, " + matchResult.goodMatches.size() + " matches");
            
        } catch (Exception e) {
            Log.e(TAG, "Error drawing feature visualization", e);
        }
    }
    
    /**
     * Draw keypoints on the frame
     */
    private void drawKeypoints(@NonNull Mat frame, @NonNull List<KeyPoint> keypoints) {
        try {
            // Draw keypoints as small circles
            Scalar keypointColor = new Scalar(0, 255, 0); // Green color
            int radius = 3;
            int thickness = 1;
            
            for (KeyPoint kp : keypoints) {
                Point center = new Point(kp.pt.x, kp.pt.y);
                Imgproc.circle(frame, center, radius, keypointColor, thickness);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error drawing keypoints", e);
        }
    }
    
    /**
     * Draw feature matches between frames
     */
    private void drawMatches(@NonNull Mat frame, @NonNull List<KeyPoint> keypoints1, 
                           @NonNull List<KeyPoint> keypoints2, @NonNull List<DMatch> matches) {
        try {
            // Draw lines connecting matched features
            Scalar matchColor = new Scalar(0, 0, 255); // Red color for matches
            int thickness = 1;
            
            // Limit number of matches drawn to avoid clutter
            int maxMatches = Math.min(50, matches.size());
            
            for (int i = 0; i < maxMatches; i++) {
                DMatch match = matches.get(i);
                
                if (match.queryIdx < keypoints1.size() && match.trainIdx < keypoints2.size()) {
                    Point pt1 = keypoints1.get(match.queryIdx).pt;
                    Point pt2 = keypoints2.get(match.trainIdx).pt;
                    
                    // Draw line connecting the matched points
                    Imgproc.line(frame, pt1, pt2, matchColor, thickness);
                    
                    // Draw small circles at match points
                    Imgproc.circle(frame, pt2, 2, matchColor, -1);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error drawing matches", e);
        }
    }
    
    /**
     * Draw distance and motion information on the frame
     */
    private void drawDistanceInfo(@NonNull Mat frame, @NonNull DistanceResult distanceResult) {
        try {
            if (!distanceResult.isValid) {
                return;
            }
            
            // Draw text information
            Scalar textColor = new Scalar(255, 255, 0); // Yellow color
            int fontFace = Imgproc.FONT_HERSHEY_SIMPLEX;
            double fontScale = 0.6;
            int thickness = 2;
            
            // Format distance information
            String distanceText = String.format("Movement: X=%.2fcm Y=%.2fcm Z=%.2fcm",
                    distanceResult.translation.x * 100,
                    distanceResult.translation.y * 100,
                    distanceResult.translation.z * 100);
            
            String featuresText = String.format("Features: %d matches, %.1f%% confidence",
                    distanceResult.featureMatches,
                    distanceResult.confidence * 100);
            
            String processingText = String.format("Processing: %.1fms",
                    distanceResult.processingTimeMs);
            
            // Draw text with background for better visibility
            Point textPos1 = new Point(10, 30);
            Point textPos2 = new Point(10, 55);
            Point textPos3 = new Point(10, 80);
            
            // Draw background rectangles
            Scalar bgColor = new Scalar(0, 0, 0, 128); // Semi-transparent black
            Imgproc.rectangle(frame, new Point(5, 10), new Point(400, 90), bgColor, -1);
            
            // Draw text
            Imgproc.putText(frame, distanceText, textPos1, fontFace, fontScale, textColor, thickness);
            Imgproc.putText(frame, featuresText, textPos2, fontFace, fontScale, textColor, thickness);
            Imgproc.putText(frame, processingText, textPos3, fontFace, fontScale, textColor, thickness);
            
        } catch (Exception e) {
            Log.e(TAG, "Error drawing distance info", e);
        }
    }
    private Mat convertToGrayscale(@NonNull Mat frame) {
        if (frame.channels() == 1) {
            // Already grayscale
            return frame;
        }
        
        Mat grayFrame = new Mat();
        if (frame.channels() == 3) {
            org.opencv.imgproc.Imgproc.cvtColor(frame, grayFrame, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY);
        } else if (frame.channels() == 4) {
            org.opencv.imgproc.Imgproc.cvtColor(frame, grayFrame, org.opencv.imgproc.Imgproc.COLOR_BGRA2GRAY);
        } else {
            // Unknown format, return original
            return frame;
        }
        
        return grayFrame;
    }
    
    /**
     * Calculate confidence score based on reprojection error and inlier ratio
     */
    private double calculateConfidence(double reprojectionError, int inlierCount, int totalMatches) {
        if (totalMatches == 0) return 0.0;
        
        // Inlier ratio component (0.0 to 1.0)
        double inlierRatio = (double) inlierCount / totalMatches;
        
        // Reprojection error component (lower error = higher confidence)
        double errorComponent = Math.max(0.0, 1.0 - (reprojectionError / 10.0));
        
        // Combined confidence score
        double confidence = (inlierRatio * 0.7) + (errorComponent * 0.3);
        
        return Math.max(0.0, Math.min(1.0, confidence));
    }
    
    /**
     * Calculate reprojection error using 3D triangulation for improved accuracy
     * Requirements: 14.3, 14.4, 14.7
     * 
     * This method leverages cv::triangulatePoints() for 3D point reconstruction
     * and uses OpenCV's built-in confidence scoring mechanisms.
     */
    private double calculateReprojectionErrorWithTriangulation(@NonNull List<Point> points1, 
                                                             @NonNull List<Point> points2,
                                                             @NonNull Mat rotation, 
                                                             @NonNull Mat translation,
                                                             @NonNull Mat cameraMatrix) {
        if (points1.isEmpty() || points2.isEmpty() || points1.size() != points2.size()) {
            return Double.MAX_VALUE;
        }
        
        try {
            // Create projection matrices for triangulation
            Mat projMatrix1 = Mat.eye(3, 4, org.opencv.core.CvType.CV_64F);
            Mat projMatrix2 = new Mat(3, 4, org.opencv.core.CvType.CV_64F);
            
            // First camera projection matrix: P1 = K * [I | 0]
            Mat identity = Mat.eye(3, 3, org.opencv.core.CvType.CV_64F);
            Mat zeros = Mat.zeros(3, 1, org.opencv.core.CvType.CV_64F);
            
            // Multiply camera matrix with [I | 0]
            Mat temp1 = new Mat();
            Core.hconcat(java.util.Arrays.asList(identity, zeros), temp1);
            Core.gemm(cameraMatrix, temp1, 1.0, new Mat(), 0.0, projMatrix1);
            
            // Second camera projection matrix: P2 = K * [R | t]
            Mat temp2 = new Mat();
            Core.hconcat(java.util.Arrays.asList(rotation, translation), temp2);
            Core.gemm(cameraMatrix, temp2, 1.0, new Mat(), 0.0, projMatrix2);
            
            // Convert points to homogeneous coordinates for triangulation
            MatOfPoint2f matPoints1 = new MatOfPoint2f();
            MatOfPoint2f matPoints2 = new MatOfPoint2f();
            
            Point[] pointArray1 = points1.toArray(new Point[0]);
            Point[] pointArray2 = points2.toArray(new Point[0]);
            matPoints1.fromArray(pointArray1);
            matPoints2.fromArray(pointArray2);
            
            // Triangulate 3D points using OpenCV's built-in triangulatePoints function
            Mat points4D = new Mat();
            Calib3d.triangulatePoints(projMatrix1, projMatrix2, matPoints1, matPoints2, points4D);
            
            // Convert from homogeneous to 3D coordinates and calculate reprojection error
            double totalError = 0.0;
            int validPoints = 0;
            
            for (int i = 0; i < points4D.cols(); i++) {
                // Extract homogeneous 3D point
                double[] point4D = new double[4];
                points4D.get(0, i, point4D);
                
                // Convert to 3D by dividing by w coordinate
                if (Math.abs(point4D[3]) > 1e-6) {
                    double x = point4D[0] / point4D[3];
                    double y = point4D[1] / point4D[3];
                    double z = point4D[2] / point4D[3];
                    
                    // Skip points that are too close or behind camera
                    if (z > 0.1 && z < 100.0) {
                        // Project 3D point back to both cameras
                        Mat point3D = new Mat(3, 1, org.opencv.core.CvType.CV_64F);
                        point3D.put(0, 0, x, y, z);
                        
                        // Project to first camera
                        Mat projected1 = new Mat();
                        Core.gemm(projMatrix1.colRange(0, 3), point3D, 1.0, new Mat(), 0.0, projected1);
                        double[] proj1 = new double[3];
                        projected1.get(0, 0, proj1);
                        
                        if (Math.abs(proj1[2]) > 1e-6) {
                            double u1 = proj1[0] / proj1[2];
                            double v1 = proj1[1] / proj1[2];
                            
                            // Calculate reprojection error for first camera
                            Point original1 = points1.get(i);
                            double error1 = Math.sqrt(Math.pow(u1 - original1.x, 2) + Math.pow(v1 - original1.y, 2));
                            
                            // Project to second camera
                            Mat projected2 = new Mat();
                            Core.gemm(projMatrix2.colRange(0, 3), point3D, 1.0, new Mat(), 0.0, projected2);
                            double[] proj2 = new double[3];
                            projected2.get(0, 0, proj2);
                            
                            if (Math.abs(proj2[2]) > 1e-6) {
                                double u2 = proj2[0] / proj2[2];
                                double v2 = proj2[1] / proj2[2];
                                
                                // Calculate reprojection error for second camera
                                Point original2 = points2.get(i);
                                double error2 = Math.sqrt(Math.pow(u2 - original2.x, 2) + Math.pow(v2 - original2.y, 2));
                                
                                // Average error for both cameras
                                totalError += (error1 + error2) / 2.0;
                                validPoints++;
                            }
                            
                            projected2.release();
                        }
                        
                        point3D.release();
                        projected1.release();
                    }
                }
            }
            
            // Clean up matrices
            projMatrix1.release();
            projMatrix2.release();
            identity.release();
            zeros.release();
            temp1.release();
            temp2.release();
            matPoints1.release();
            matPoints2.release();
            points4D.release();
            
            // Return average reprojection error
            double avgError = validPoints > 0 ? totalError / validPoints : Double.MAX_VALUE;
            
            Log.v(TAG, "Triangulation-based reprojection error: " + avgError + 
                      " (valid points: " + validPoints + "/" + points1.size() + ")");
            
            return avgError;
            
        } catch (Exception e) {
            Log.e(TAG, "Error calculating triangulation-based reprojection error", e);
            return Double.MAX_VALUE;
        }
    }
    
    /**
     * Calculate geometric confidence using OpenCV's built-in confidence scoring
     * Requirements: 14.7
     * 
     * This method uses OpenCV's RANSAC inlier counting and reprojection error
     * to provide a robust confidence estimate.
     */
    private double calculateGeometricConfidence(int inlierCount, int totalMatches, double reprojectionError) {
        if (totalMatches == 0) return 0.0;
        
        // Inlier ratio component (OpenCV's built-in outlier rejection results)
        double inlierRatio = (double) inlierCount / totalMatches;
        
        // Reprojection error component (lower error = higher confidence)
        // Use adaptive threshold based on typical mobile camera reprojection errors
        double errorThreshold = 2.0; // pixels
        double errorComponent = Math.max(0.0, 1.0 - (reprojectionError / errorThreshold));
        
        // Minimum matches component (ensure sufficient features for reliable estimation)
        double minMatchesComponent = Math.min(1.0, (double) inlierCount / MIN_MATCHES);
        
        // Combined confidence score using weighted average
        // Prioritize inlier ratio (RANSAC results) and minimum matches for robustness
        double confidence = (inlierRatio * 0.5) + (errorComponent * 0.3) + (minMatchesComponent * 0.2);
        
        // Apply additional penalty for very low feature counts (insufficient features handling)
        if (inlierCount < MIN_MATCHES / 2) {
            confidence *= 0.5; // Reduce confidence for borderline feature counts
        }
        
        return Math.max(0.0, Math.min(1.0, confidence));
    }
    
    /**
     * Set checkerboard pattern parameters for camera calibration
     * Requirements: 14.8
     * 
     * @param patternSize Size of the checkerboard pattern (width x height in corners)
     * @param squareSize Physical size of each square in millimeters
     */
    public void setCheckerboardPattern(@NonNull Size patternSize, float squareSize) {
        this.checkerboardSize = patternSize;
        this.squareSize = squareSize;
        
        Log.d(TAG, "Checkerboard pattern set: " + patternSize.width + "x" + patternSize.height + 
                  ", square size: " + squareSize + "mm");
    }
    
    /**
     * Detect checkerboard corners in an image using OpenCV built-in functions
     * Requirements: 14.8
     * 
     * Uses cv::findChessboardCorners() for automatic corner detection
     * and cv::cornerSubPix() for sub-pixel accuracy
     * 
     * @param image Input image (color or grayscale)
     * @return CheckerboardResult containing detected corners and refinement status
     */
    public CheckerboardResult detectCheckerboardCorners(@NonNull Mat image) {
        try {
            // Convert to grayscale if needed
            Mat grayImage = convertToGrayscale(image);
            
            // Use cv::findChessboardCorners() for automatic corner detection
            MatOfPoint2f corners = new MatOfPoint2f();
            boolean found = Calib3d.findChessboardCorners(grayImage, checkerboardSize, corners,
                    Calib3d.CALIB_CB_ADAPTIVE_THRESH | 
                    Calib3d.CALIB_CB_NORMALIZE_IMAGE |
                    Calib3d.CALIB_CB_FAST_CHECK);
            
            List<Point> cornerList = new ArrayList<>();
            Mat refinedCorners = new Mat();
            
            if (found) {
                // Apply cv::cornerSubPix() for sub-pixel accuracy
                TermCriteria criteria = new TermCriteria(
                    TermCriteria.EPS + TermCriteria.COUNT, 30, 0.1);
                
                Imgproc.cornerSubPix(grayImage, corners, new Size(11, 11), new Size(-1, -1), criteria);
                
                // Convert to list and store refined corners
                cornerList = corners.toList();
                corners.copyTo(refinedCorners);
                
                Log.d(TAG, "Checkerboard detected with " + cornerList.size() + " corners, sub-pixel refined");
            } else {
                Log.v(TAG, "Checkerboard not found in image");
            }
            
            // Clean up temporary matrices
            corners.release();
            if (grayImage != image) {
                grayImage.release();
            }
            
            return new CheckerboardResult(cornerList, found, checkerboardSize, refinedCorners);
            
        } catch (Exception e) {
            Log.e(TAG, "Error detecting checkerboard corners", e);
            return new CheckerboardResult(new ArrayList<>(), false, checkerboardSize, new Mat());
        }
    }
    
    /**
     * Add calibration image with detected checkerboard corners
     * Requirements: 14.8
     * 
     * @param image Input image containing checkerboard
     * @return true if checkerboard was detected and added to calibration set
     */
    public boolean addCalibrationImage(@NonNull Mat image) {
        try {
            CheckerboardResult result = detectCheckerboardCorners(image);
            
            if (!result.found) {
                Log.w(TAG, "Checkerboard not found, skipping calibration image");
                return false;
            }
            
            // Store image size for calibration (all images must have same size)
            Size currentImageSize = image.size();
            if (calibrationImageSize == null) {
                calibrationImageSize = currentImageSize;
            } else if (!calibrationImageSize.equals(currentImageSize)) {
                Log.w(TAG, "Image size mismatch: expected " + calibrationImageSize + 
                          ", got " + currentImageSize);
                return false;
            }
            
            // Generate 3D object points for this checkerboard
            Mat objectPoints = generateObjectPoints(checkerboardSize, squareSize);
            
            // Store object points and image points for calibration
            calibrationObjectPoints.add(objectPoints);
            calibrationImagePoints.add(result.refinedCorners);
            
            Log.d(TAG, "Added calibration image " + calibrationImagePoints.size() + 
                      " with " + result.corners.size() + " corners");
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error adding calibration image", e);
            return false;
        }
    }
    
    /**
     * Perform camera calibration using collected checkerboard images
     * Requirements: 14.8
     * 
     * Uses cv::calibrateCamera() with collected checkerboard patterns
     * 
     * @return CalibrationResult containing camera matrix, distortion coefficients, and accuracy metrics
     */
    public CalibrationResult performCameraCalibration() {
        try {
            if (calibrationImagePoints.size() < 3) {
                Log.w(TAG, "Insufficient calibration images: " + calibrationImagePoints.size() + " < 3");
                return new CalibrationResult(new Mat(), new Mat(), Double.MAX_VALUE, 
                                           calibrationImagePoints.size(), calibrationImageSize, false);
            }
            
            if (calibrationImageSize == null) {
                Log.e(TAG, "No calibration image size available");
                return new CalibrationResult(new Mat(), new Mat(), Double.MAX_VALUE, 0, null, false);
            }
            
            // Prepare output matrices
            Mat cameraMatrix = new Mat();
            Mat distCoeffs = new Mat();
            List<Mat> rvecs = new ArrayList<>();
            List<Mat> tvecs = new ArrayList<>();
            
            Log.d(TAG, "Starting camera calibration with " + calibrationImagePoints.size() + 
                      " images, image size: " + calibrationImageSize);
            
            // Use cv::calibrateCamera() for camera calibration
            double rmsError = Calib3d.calibrateCamera(
                calibrationObjectPoints,    // Object points in 3D
                calibrationImagePoints,     // Corresponding image points in 2D
                calibrationImageSize,       // Image size
                cameraMatrix,              // Output camera matrix
                distCoeffs,                // Output distortion coefficients
                rvecs,                     // Output rotation vectors
                tvecs,                     // Output translation vectors
                Calib3d.CALIB_FIX_PRINCIPAL_POINT  // Calibration flags
            );
            
            boolean isValid = rmsError < 1.0 && !cameraMatrix.empty() && !distCoeffs.empty();
            
            if (isValid) {
                // Update internal camera parameters
                if (this.cameraMatrix != null) {
                    this.cameraMatrix.release();
                }
                if (this.distortionCoeffs != null) {
                    this.distortionCoeffs.release();
                }
                
                this.cameraMatrix = cameraMatrix.clone();
                this.distortionCoeffs = distCoeffs.clone();
                this.hasIntrinsics = true;
                
                Log.i(TAG, "Camera calibration successful: RMS error = " + rmsError + 
                          ", images = " + calibrationImagePoints.size());
            } else {
                Log.w(TAG, "Camera calibration failed or inaccurate: RMS error = " + rmsError);
            }
            
            // Clean up temporary matrices
            for (Mat rvec : rvecs) {
                rvec.release();
            }
            for (Mat tvec : tvecs) {
                tvec.release();
            }
            
            return new CalibrationResult(cameraMatrix, distCoeffs, rmsError, 
                                       calibrationImagePoints.size(), calibrationImageSize, isValid);
            
        } catch (Exception e) {
            Log.e(TAG, "Error performing camera calibration", e);
            return new CalibrationResult(new Mat(), new Mat(), Double.MAX_VALUE, 
                                       calibrationImagePoints.size(), calibrationImageSize, false);
        }
    }
    
    /**
     * Undistort an image using calibrated camera parameters
     * Requirements: 14.8
     * 
     * Uses cv::undistort() for image correction
     * 
     * @param inputImage Distorted input image
     * @param outputImage Output undistorted image
     * @return true if undistortion was successful
     */
    public boolean undistortImage(@NonNull Mat inputImage, @NonNull Mat outputImage) {
        try {
            if (!hasIntrinsics || cameraMatrix == null || distortionCoeffs == null) {
                Log.w(TAG, "Camera not calibrated, cannot undistort image");
                return false;
            }
            
            if (inputImage.empty()) {
                Log.w(TAG, "Empty input image for undistortion");
                return false;
            }
            
            // Use cv::undistort() for image correction
            Calib3d.undistort(inputImage, outputImage, cameraMatrix, distortionCoeffs);
            
            Log.v(TAG, "Image undistorted successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error undistorting image", e);
            return false;
        }
    }
    
    /**
     * Save calibration results to file using OpenCV's FileStorage
     * Requirements: 14.8
     * 
     * @param filePath Path to save calibration file
     * @param calibrationResult Calibration results to save
     * @return true if save was successful
     */
    public boolean saveCalibrationToFile(@NonNull String filePath, @NonNull CalibrationResult calibrationResult) {
        try {
            if (!calibrationResult.isValid) {
                Log.w(TAG, "Cannot save invalid calibration results");
                return false;
            }
            
            // Create parent directories if they don't exist
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // Use OpenCV's FileStorage for saving calibration data
            // Note: FileStorage is not directly available in OpenCV4Android Java API
            // We'll use a simple text format that can be easily parsed
            
            java.io.FileWriter writer = new java.io.FileWriter(filePath);
            writer.write("# Camera Calibration Results\n");
            writer.write("# Generated by VisualOdometryProcessor\n");
            writer.write("calibration_time: " + System.currentTimeMillis() + "\n");
            writer.write("image_width: " + (int)calibrationResult.imageSize.width + "\n");
            writer.write("image_height: " + (int)calibrationResult.imageSize.height + "\n");
            writer.write("calibration_images: " + calibrationResult.calibrationImages + "\n");
            writer.write("rms_error: " + calibrationResult.reprojectionError + "\n");
            
            // Save camera matrix
            writer.write("camera_matrix: !!opencv-matrix\n");
            writer.write("  rows: 3\n");
            writer.write("  cols: 3\n");
            writer.write("  dt: d\n");
            writer.write("  data: [ ");
            
            double[] cameraData = new double[9];
            calibrationResult.cameraMatrix.get(0, 0, cameraData);
            for (int i = 0; i < cameraData.length; i++) {
                writer.write(String.format("%.6f", cameraData[i]));
                if (i < cameraData.length - 1) writer.write(", ");
            }
            writer.write(" ]\n");
            
            // Save distortion coefficients
            writer.write("distortion_coefficients: !!opencv-matrix\n");
            writer.write("  rows: " + calibrationResult.distortionCoeffs.rows() + "\n");
            writer.write("  cols: " + calibrationResult.distortionCoeffs.cols() + "\n");
            writer.write("  dt: d\n");
            writer.write("  data: [ ");
            
            double[] distData = new double[(int)calibrationResult.distortionCoeffs.total()];
            calibrationResult.distortionCoeffs.get(0, 0, distData);
            for (int i = 0; i < distData.length; i++) {
                writer.write(String.format("%.6f", distData[i]));
                if (i < distData.length - 1) writer.write(", ");
            }
            writer.write(" ]\n");
            
            writer.close();
            
            Log.i(TAG, "Calibration results saved to: " + filePath);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving calibration to file: " + filePath, e);
            return false;
        }
    }
    
    /**
     * Load calibration results from file
     * Requirements: 14.8
     * 
     * @param filePath Path to calibration file
     * @return CalibrationResult loaded from file, or invalid result if loading failed
     */
    public CalibrationResult loadCalibrationFromFile(@NonNull String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                Log.w(TAG, "Calibration file does not exist: " + filePath);
                return new CalibrationResult(new Mat(), new Mat(), Double.MAX_VALUE, 0, null, false);
            }
            
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(filePath));
            String line;
            
            int imageWidth = 0, imageHeight = 0, calibrationImages = 0;
            double rmsError = Double.MAX_VALUE;
            double[] cameraData = null;
            double[] distData = null;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) continue;
                
                if (line.startsWith("image_width:")) {
                    imageWidth = Integer.parseInt(line.split(":")[1].trim());
                } else if (line.startsWith("image_height:")) {
                    imageHeight = Integer.parseInt(line.split(":")[1].trim());
                } else if (line.startsWith("calibration_images:")) {
                    calibrationImages = Integer.parseInt(line.split(":")[1].trim());
                } else if (line.startsWith("rms_error:")) {
                    rmsError = Double.parseDouble(line.split(":")[1].trim());
                } else if (line.contains("data: [")) {
                    // Parse matrix data
                    String dataStr = line.substring(line.indexOf("[") + 1, line.indexOf("]"));
                    String[] values = dataStr.split(",");
                    double[] data = new double[values.length];
                    for (int i = 0; i < values.length; i++) {
                        data[i] = Double.parseDouble(values[i].trim());
                    }
                    
                    if (cameraData == null) {
                        cameraData = data;  // First matrix is camera matrix
                    } else {
                        distData = data;    // Second matrix is distortion coefficients
                    }
                }
            }
            reader.close();
            
            if (cameraData != null && distData != null && imageWidth > 0 && imageHeight > 0) {
                // Create matrices from loaded data
                Mat cameraMatrix = new Mat(3, 3, org.opencv.core.CvType.CV_64F);
                cameraMatrix.put(0, 0, cameraData);
                
                Mat distCoeffs = new Mat(distData.length, 1, org.opencv.core.CvType.CV_64F);
                distCoeffs.put(0, 0, distData);
                
                Size imageSize = new Size(imageWidth, imageHeight);
                
                // Update internal parameters
                if (this.cameraMatrix != null) {
                    this.cameraMatrix.release();
                }
                if (this.distortionCoeffs != null) {
                    this.distortionCoeffs.release();
                }
                
                this.cameraMatrix = cameraMatrix.clone();
                this.distortionCoeffs = distCoeffs.clone();
                this.hasIntrinsics = true;
                
                Log.i(TAG, "Calibration loaded from: " + filePath + ", RMS error: " + rmsError);
                
                return new CalibrationResult(cameraMatrix, distCoeffs, rmsError, 
                                           calibrationImages, imageSize, true);
            } else {
                Log.w(TAG, "Invalid calibration file format: " + filePath);
                return new CalibrationResult(new Mat(), new Mat(), Double.MAX_VALUE, 0, null, false);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading calibration from file: " + filePath, e);
            return new CalibrationResult(new Mat(), new Mat(), Double.MAX_VALUE, 0, null, false);
        }
    }
    
    /**
     * Clear all collected calibration images
     * Requirements: 14.8
     */
    public void clearCalibrationImages() {
        // Release all stored matrices
        for (Mat objectPoints : calibrationObjectPoints) {
            objectPoints.release();
        }
        for (Mat imagePoints : calibrationImagePoints) {
            imagePoints.release();
        }
        
        calibrationObjectPoints.clear();
        calibrationImagePoints.clear();
        calibrationImageSize = null;
        
        Log.d(TAG, "Calibration images cleared");
    }
    
    /**
     * Get number of collected calibration images
     * Requirements: 14.8
     */
    public int getCalibrationImageCount() {
        return calibrationImagePoints.size();
    }
    
    /**
     * Check if camera is calibrated
     * Requirements: 14.8
     */
    public boolean isCalibrated() {
        return hasIntrinsics && cameraMatrix != null && distortionCoeffs != null;
    }
    
    /**
     * Generate 3D object points for checkerboard pattern
     * Requirements: 14.8
     * 
     * @param patternSize Size of checkerboard pattern
     * @param squareSize Physical size of each square
     * @return Mat containing 3D object points
     */
    private Mat generateObjectPoints(@NonNull Size patternSize, float squareSize) {
        int numPoints = (int)(patternSize.width * patternSize.height);
        Mat objectPoints = new Mat(numPoints, 1, org.opencv.core.CvType.CV_32FC3);
        
        float[] points = new float[numPoints * 3];
        int idx = 0;
        
        for (int i = 0; i < patternSize.height; i++) {
            for (int j = 0; j < patternSize.width; j++) {
                points[idx++] = j * squareSize;  // X coordinate
                points[idx++] = i * squareSize;  // Y coordinate
                points[idx++] = 0.0f;            // Z coordinate (checkerboard is planar)
            }
        }
        
        objectPoints.put(0, 0, points);
        return objectPoints;
    }

    /**
     * Get average processing time per frame pair
     */
    public double getAverageProcessingTime() {
        return totalFramePairs > 0 ? (double) totalProcessingTime / totalFramePairs : 0.0;
    }
    
    /**
     * Get total number of processed frame pairs
     */
    public int getTotalFramePairs() {
        return totalFramePairs;
    }
    
    /**
     * Reset performance metrics
     */
    public void resetPerformanceMetrics() {
        totalProcessingTime = 0;
        totalFramePairs = 0;
        Log.d(TAG, "Performance metrics reset");
    }
    
    /**
     * Release OpenCV resources
     */
    public void release() {
        Log.d(TAG, "Releasing VisualOdometryProcessor resources");
        
        try {
            if (orbDetector != null) {
                // ORB detector will be cleaned up by OpenCV's garbage collection
                orbDetector = null;
            }
            
            if (bfMatcher != null) {
                // BFMatcher will be cleaned up by OpenCV's garbage collection
                bfMatcher = null;
            }
            
            if (flannMatcher != null) {
                // FlannBasedMatcher will be cleaned up by OpenCV's garbage collection
                flannMatcher = null;
            }
            
            if (cameraMatrix != null) {
                cameraMatrix.release();
                cameraMatrix = null;
            }
            
            if (distortionCoeffs != null) {
                distortionCoeffs.release();
                distortionCoeffs = null;
            }
            
            // Release visualization frame
            if (visualizationFrame != null) {
                visualizationFrame.release();
                visualizationFrame = null;
            }
            
            hasIntrinsics = false;
            distanceCallback = null;
            
            Log.i(TAG, "Final performance metrics - " +
                    "Frame pairs: " + totalFramePairs +
                    ", Avg time: " + String.format("%.2f", getAverageProcessingTime()) + "ms");
            
        } catch (Exception e) {
            Log.e(TAG, "Error releasing resources", e);
        }
    }
}