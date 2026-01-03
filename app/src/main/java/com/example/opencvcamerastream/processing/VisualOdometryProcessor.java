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
import org.opencv.core.Point2f;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.ORB;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.FlannBasedMatcher;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;

import java.util.ArrayList;
import java.util.List;

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
    private static final int MAX_FEATURES = 500;
    private static final float RATIO_THRESHOLD = 0.7f;
    private static final int MIN_MATCHES = 10;
    
    // RANSAC parameters for essential matrix estimation
    private static final double RANSAC_THRESHOLD = 1.0;
    private static final double RANSAC_CONFIDENCE = 0.999;
    
    // OpenCV feature detector and matcher
    private ORB orbDetector;
    private BFMatcher bfMatcher;
    private FlannBasedMatcher flannMatcher;
    
    // Camera intrinsic parameters
    private Mat cameraMatrix;
    private Mat distortionCoeffs;
    private boolean hasIntrinsics = false;
    
    // Callback interface for distance results
    private DistanceCallback distanceCallback;
    
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
        public final List<Point2f> inlierPoints1, inlierPoints2;
        public final double reprojectionError;
        
        public TransformResult(Mat rotation, Mat translation,
                             List<Point2f> inlierPoints1, List<Point2f> inlierPoints2,
                             double reprojectionError) {
            this.rotation = rotation;
            this.translation = translation;
            this.inlierPoints1 = inlierPoints1;
            this.inlierPoints2 = inlierPoints2;
            this.reprojectionError = reprojectionError;
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
     * Constructor - initializes OpenCV feature detector and matcher
     */
    public VisualOdometryProcessor() {
        initialize();
    }
    
    /**
     * Initialize OpenCV components using built-in functions
     */
    private void initialize() {
        try {
            // Create ORB feature detector using OpenCV built-in function
            // ORB is efficient for mobile devices and provides good performance
            orbDetector = ORB.create(MAX_FEATURES);
            
            // Create BF (Brute Force) matcher for robust matching
            // Using NORM_HAMMING for ORB descriptors (binary descriptors)
            bfMatcher = BFMatcher.create(Core.NORM_HAMMING, true);
            
            // Create FLANN-based matcher as alternative (faster for large descriptor sets)
            flannMatcher = FlannBasedMatcher.create();
            
            Log.d(TAG, "VisualOdometryProcessor initialized with ORB detector and BF/FLANN matchers");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize VisualOdometryProcessor", e);
            throw new RuntimeException("VisualOdometryProcessor initialization failed", e);
        }
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
     * Process a pair of frames to compute 3D distance
     * This is the main entry point for visual odometry computation
     * 
     * @param previousFrame Previous camera frame (grayscale or color)
     * @param currentFrame Current camera frame (grayscale or color)
     */
    public void processFramePair(@NonNull Mat previousFrame, @NonNull Mat currentFrame) {
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
            List<Point2f> points1 = new ArrayList<>();
            List<Point2f> points2 = new ArrayList<>();
            
            for (DMatch match : goodMatches) {
                Point kp1 = keypoints1.get(match.queryIdx).pt;
                Point kp2 = keypoints2.get(match.trainIdx).pt;
                points1.add(new Point2f((float)kp1.x, (float)kp1.y));
                points2.add(new Point2f((float)kp2.x, (float)kp2.y));
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
            
            // Compute 3D distance from translation vector
            Vector3D translation = computeTranslationDistance(transform.rotation, transform.translation);
            
            // Convert rotation matrix to rotation vector for easier interpretation
            Mat rotationVector = new Mat();
            Calib3d.Rodrigues(transform.rotation, rotationVector);
            
            double[] rotData = new double[3];
            rotationVector.get(0, 0, rotData);
            Vector3D rotation = new Vector3D(rotData[0], rotData[1], rotData[2]);
            
            // Calculate confidence based on reprojection error and number of inliers
            double confidence = calculateConfidence(transform.reprojectionError, 
                                                  transform.inlierPoints1.size(), 
                                                  goodMatches.size());
            
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
    public Mat estimateEssentialMatrix(@NonNull List<Point2f> points1, @NonNull List<Point2f> points2) {
        if (points1.size() != points2.size() || points1.size() < 5) {
            Log.w(TAG, "Insufficient points for essential matrix estimation: " + points1.size());
            return new Mat();
        }
        
        try {
            // Convert points to MatOfPoint2f for OpenCV functions
            MatOfPoint2f matPoints1 = new MatOfPoint2f();
            MatOfPoint2f matPoints2 = new MatOfPoint2f();
            matPoints1.fromList(points1);
            matPoints2.fromList(points2);
            
            Mat essentialMatrix;
            
            if (hasIntrinsics && cameraMatrix != null) {
                // Use camera intrinsics for accurate estimation
                Mat mask = new Mat();
                essentialMatrix = Calib3d.findEssentialMat(matPoints1, matPoints2, 
                                                         cameraMatrix, 
                                                         Calib3d.RANSAC, 
                                                         RANSAC_CONFIDENCE, 
                                                         RANSAC_THRESHOLD, 
                                                         mask);
                mask.release();
                
                Log.d(TAG, "Essential matrix estimated with camera intrinsics");
            } else {
                // Use estimated focal length (assume principal point at image center)
                // This is a fallback when camera intrinsics are not available
                double focalLength = 800.0; // Estimated focal length for typical mobile camera
                Point2f principalPoint = new Point2f(320, 240); // Estimated principal point
                
                Mat estimatedCameraMatrix = Mat.eye(3, 3, org.opencv.core.CvType.CV_64F);
                estimatedCameraMatrix.put(0, 0, focalLength);
                estimatedCameraMatrix.put(1, 1, focalLength);
                estimatedCameraMatrix.put(0, 2, principalPoint.x);
                estimatedCameraMatrix.put(1, 2, principalPoint.y);
                
                Mat mask = new Mat();
                essentialMatrix = Calib3d.findEssentialMat(matPoints1, matPoints2,
                                                         estimatedCameraMatrix,
                                                         Calib3d.RANSAC,
                                                         RANSAC_CONFIDENCE,
                                                         RANSAC_THRESHOLD,
                                                         mask);
                
                estimatedCameraMatrix.release();
                mask.release();
                
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
     * 
     * @param essentialMatrix 3x3 essential matrix
     * @param points1 Points from first frame
     * @param points2 Corresponding points from second frame
     * @return TransformResult containing rotation, translation, and inlier points
     */
    public TransformResult decomposeEssentialMatrix(@NonNull Mat essentialMatrix,
                                                   @NonNull List<Point2f> points1,
                                                   @NonNull List<Point2f> points2) {
        try {
            // Convert points to MatOfPoint2f
            MatOfPoint2f matPoints1 = new MatOfPoint2f();
            MatOfPoint2f matPoints2 = new MatOfPoint2f();
            matPoints1.fromList(points1);
            matPoints2.fromList(points2);
            
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
            int inlierCount = Calib3d.recoverPose(essentialMatrix, matPoints1, matPoints2,
                                                 cameraMatrixToUse, rotation, translation, mask);
            
            // Extract inlier points
            List<Point2f> inlierPoints1 = new ArrayList<>();
            List<Point2f> inlierPoints2 = new ArrayList<>();
            
            byte[] maskArray = new byte[(int)mask.total()];
            mask.get(0, 0, maskArray);
            
            for (int i = 0; i < maskArray.length && i < points1.size(); i++) {
                if (maskArray[i] != 0) {
                    inlierPoints1.add(points1.get(i));
                    inlierPoints2.add(points2.get(i));
                }
            }
            
            // Calculate reprojection error (simplified)
            double reprojectionError = calculateReprojectionError(inlierPoints1, inlierPoints2,
                                                                rotation, translation, cameraMatrixToUse);
            
            Log.d(TAG, "Essential matrix decomposed: inliers=" + inlierCount + 
                      ", reprojection_error=" + reprojectionError);
            
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
            
            // Extract translation components using OpenCV Core.norm for magnitude
            double[] translationData = new double[3];
            translation.get(0, 0, translationData);
            
            // The translation vector represents the direction and relative magnitude
            // For absolute scale, we would need additional information (stereo, known object size, etc.)
            Vector3D translationVector = new Vector3D(translationData[0], translationData[1], translationData[2]);
            
            // Calculate magnitude using OpenCV's norm function
            double magnitude = Core.norm(translation);
            
            Log.v(TAG, "Translation distance computed: " + translationVector + 
                      ", magnitude=" + magnitude);
            
            return translationVector;
            
        } catch (Exception e) {
            Log.e(TAG, "Error computing translation distance", e);
            return new Vector3D(0, 0, 0);
        }
    }
    
    /**
     * Convert frame to grayscale if needed
     */
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
     * Calculate simplified reprojection error
     */
    private double calculateReprojectionError(@NonNull List<Point2f> points1, 
                                            @NonNull List<Point2f> points2,
                                            @NonNull Mat rotation, 
                                            @NonNull Mat translation,
                                            @NonNull Mat cameraMatrix) {
        if (points1.isEmpty() || points2.isEmpty()) {
            return Double.MAX_VALUE;
        }
        
        try {
            // Simplified reprojection error calculation
            // In a full implementation, this would project 3D points back to image plane
            // For now, we use the average distance between corresponding points as a proxy
            
            double totalError = 0.0;
            int count = Math.min(points1.size(), points2.size());
            
            for (int i = 0; i < count; i++) {
                Point2f p1 = points1.get(i);
                Point2f p2 = points2.get(i);
                
                double dx = p1.x - p2.x;
                double dy = p1.y - p2.y;
                double distance = Math.sqrt(dx * dx + dy * dy);
                
                totalError += distance;
            }
            
            return count > 0 ? totalError / count : Double.MAX_VALUE;
            
        } catch (Exception e) {
            Log.e(TAG, "Error calculating reprojection error", e);
            return Double.MAX_VALUE;
        }
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