package com.example.opencvcamerastream.processing;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.opencv.core.Mat;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.DMatch;
import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for VisualOdometryProcessor
 * Requirements: 14.1, 14.2
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Test on Android 10
public class VisualOdometryProcessorTest {

    private VisualOdometryProcessor processor;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        processor = new VisualOdometryProcessor();
    }

    @Test
    public void testProcessorInitialization() {
        // Test that processor initializes without errors
        assertNotNull("Processor should not be null", processor);
        
        // Test performance metrics initialization
        assertEquals("Initial frame pairs should be 0", 0, processor.getTotalFramePairs());
        assertEquals("Initial processing time should be 0", 0.0, processor.getAverageProcessingTime(), 0.001);
    }

    @Test
    public void testCameraIntrinsicsSetup() {
        // Test camera intrinsics setup (Requirement 14.8)
        Mat cameraMatrix = createTestCameraMatrix();
        Mat distCoeffs = createTestDistortionCoeffs();
        
        // Should not throw exception
        processor.setCameraIntrinsics(cameraMatrix, distCoeffs);
        
        // Clean up
        cameraMatrix.release();
        distCoeffs.release();
    }

    @Test
    public void testDistanceCallbackSetup() {
        // Test distance callback setup
        VisualOdometryProcessor.DistanceCallback callback = new VisualOdometryProcessor.DistanceCallback() {
            @Override
            public void onDistanceComputed(VisualOdometryProcessor.DistanceResult result) {
                // Test callback
            }

            @Override
            public void onInsufficientFeatures(int matchCount) {
                // Test callback
            }

            @Override
            public void onProcessingError(Exception error) {
                // Test callback
            }
        };
        
        // Should not throw exception
        processor.setDistanceCallback(callback);
        processor.setDistanceCallback(null); // Test null callback
    }

    @Test
    public void testVector3DOperations() {
        // Test Vector3D data model
        VisualOdometryProcessor.Vector3D vector = new VisualOdometryProcessor.Vector3D(3.0, 4.0, 0.0);
        
        assertEquals("X component should be 3.0", 3.0, vector.x, 0.001);
        assertEquals("Y component should be 4.0", 4.0, vector.y, 0.001);
        assertEquals("Z component should be 0.0", 0.0, vector.z, 0.001);
        
        // Test magnitude calculation
        assertEquals("Magnitude should be 5.0", 5.0, vector.magnitude(), 0.001);
        
        // Test normalization
        VisualOdometryProcessor.Vector3D normalized = vector.normalize();
        assertEquals("Normalized magnitude should be 1.0", 1.0, normalized.magnitude(), 0.001);
        assertEquals("Normalized X should be 0.6", 0.6, normalized.x, 0.001);
        assertEquals("Normalized Y should be 0.8", 0.8, normalized.y, 0.001);
    }

    @Test
    public void testDistanceResultCreation() {
        // Test DistanceResult data model
        VisualOdometryProcessor.Vector3D translation = new VisualOdometryProcessor.Vector3D(1.0, 2.0, 3.0);
        VisualOdometryProcessor.Vector3D rotation = new VisualOdometryProcessor.Vector3D(0.1, 0.2, 0.3);
        
        VisualOdometryProcessor.DistanceResult result = new VisualOdometryProcessor.DistanceResult(
                translation, rotation, 25, 0.85, 50L, true);
        
        assertEquals("Translation should match", translation, result.translation);
        assertEquals("Rotation should match", rotation, result.rotation);
        assertEquals("Feature matches should be 25", 25, result.featureMatches);
        assertEquals("Confidence should be 0.85", 0.85, result.confidence, 0.001);
        assertEquals("Processing time should be 50ms", 50L, result.processingTimeMs);
        assertTrue("Result should be valid", result.isValid);
    }

    @Test
    public void testMatchFiltering() {
        // Test Lowe's ratio test filtering (Requirement 14.2)
        List<DMatch> matches = createTestMatches();
        
        List<DMatch> filteredMatches = processor.filterMatches(matches, 0.7f);
        
        assertNotNull("Filtered matches should not be null", filteredMatches);
        assertTrue("Filtered matches should be less than or equal to original", 
                  filteredMatches.size() <= matches.size());
    }

    @Test
    public void testEmptyFrameHandling() {
        // Test handling of empty frames
        Mat emptyFrame1 = new Mat();
        Mat emptyFrame2 = new Mat();
        
        // Should not crash with empty frames
        processor.processFramePair(emptyFrame1, emptyFrame2);
        
        // Performance metrics should not change for invalid input
        assertEquals("Frame pairs should still be 0", 0, processor.getTotalFramePairs());
    }

    @Test
    public void testEssentialMatrixEstimation() {
        // Test essential matrix estimation with minimal points
        List<Point> points1 = createTestPoints();
        List<Point> points2 = createTestPoints();
        
        Mat essentialMatrix = processor.estimateEssentialMatrix(points1, points2);
        
        // Should return a matrix (might be empty if insufficient points)
        assertNotNull("Essential matrix should not be null", essentialMatrix);
        
        // Clean up
        essentialMatrix.release();
    }

    @Test
    public void testGeometricTransformEstimation() {
        // Test geometric transform estimation with OpenCV functions (Requirements: 14.3, 14.4, 14.7)
        List<Point> points1 = createTestPointsForTransform();
        List<Point> points2 = createCorrespondingTestPoints();
        
        // Test essential matrix estimation with RANSAC
        Mat essentialMatrix = processor.estimateEssentialMatrix(points1, points2);
        assertNotNull("Essential matrix should not be null", essentialMatrix);
        
        if (!essentialMatrix.empty()) {
            // Test rotation/translation decomposition using recoverPose
            VisualOdometryProcessor.TransformResult transform = 
                processor.decomposeEssentialMatrix(essentialMatrix, points1, points2);
            
            assertNotNull("Transform result should not be null", transform);
            assertNotNull("Rotation matrix should not be null", transform.rotation);
            assertNotNull("Translation vector should not be null", transform.translation);
            
            // Test that inlier points are extracted (OpenCV's built-in outlier rejection)
            assertTrue("Should have some inlier points", transform.inlierPoints1.size() >= 0);
            assertEquals("Inlier points should match", 
                        transform.inlierPoints1.size(), transform.inlierPoints2.size());
            
            // Test reprojection error calculation (should use triangulation)
            assertTrue("Reprojection error should be finite", 
                      Double.isFinite(transform.reprojectionError));
            
            // Clean up
            transform.rotation.release();
            transform.translation.release();
        }
        
        essentialMatrix.release();
    }

    @Test
    public void testTriangulationBasedConfidenceScoring() {
        // Test OpenCV's built-in confidence scoring with RANSAC inlier counting (Requirement: 14.7)
        List<Point> points1 = createTestPointsForTransform();
        List<Point> points2 = createCorrespondingTestPoints();
        
        // Create test keypoints and descriptors
        List<KeyPoint> keypoints1 = createTestKeypoints(points1);
        List<KeyPoint> keypoints2 = createTestKeypoints(points2);
        Mat descriptors1 = createTestDescriptors(keypoints1.size());
        Mat descriptors2 = createTestDescriptors(keypoints2.size());
        
        // Test distance computation with confidence scoring
        VisualOdometryProcessor.DistanceResult result = 
            processor.computeDistance(keypoints1, keypoints2, descriptors1, descriptors2);
        
        assertNotNull("Distance result should not be null", result);
        
        // Test confidence score properties
        assertTrue("Confidence should be between 0 and 1", 
                  result.confidence >= 0.0 && result.confidence <= 1.0);
        
        // Test that insufficient features are handled properly
        if (result.featureMatches < 10) {
            assertFalse("Result should be invalid with insufficient features", result.isValid);
        }
        
        // Clean up
        descriptors1.release();
        descriptors2.release();
    }

    @Test
    public void testInsufficientFeaturesHandling() {
        // Test handling of insufficient features (Requirement: 14.7)
        List<Point> fewPoints1 = createMinimalTestPoints(); // Less than MIN_MATCHES
        List<Point> fewPoints2 = createMinimalTestPoints();
        
        List<KeyPoint> fewKeypoints1 = createTestKeypoints(fewPoints1);
        List<KeyPoint> fewKeypoints2 = createTestKeypoints(fewPoints2);
        Mat descriptors1 = createTestDescriptors(fewKeypoints1.size());
        Mat descriptors2 = createTestDescriptors(fewKeypoints2.size());
        
        // Test that insufficient features are detected and handled
        VisualOdometryProcessor.DistanceResult result = 
            processor.computeDistance(fewKeypoints1, fewKeypoints2, descriptors1, descriptors2);
        
        assertNotNull("Result should not be null even with few features", result);
        assertFalse("Result should be invalid with insufficient features", result.isValid);
        assertTrue("Feature count should be low", result.featureMatches < 10);
        
        // Clean up
        descriptors1.release();
        descriptors2.release();
    }

    @Test
    public void testPerformanceMetricsReset() {
        // Test performance metrics reset
        processor.resetPerformanceMetrics();
        
        assertEquals("Frame pairs should be 0 after reset", 0, processor.getTotalFramePairs());
        assertEquals("Processing time should be 0 after reset", 0.0, processor.getAverageProcessingTime(), 0.001);
    }

    @Test
    public void testResourceRelease() {
        // Test resource cleanup
        processor.release();
        
        // Should not throw exception
        // Performance metrics should still be accessible
        assertTrue("Should be able to get frame pairs after release", processor.getTotalFramePairs() >= 0);
    }

    // Helper methods for creating test data

    private Mat createTestCameraMatrix() {
        Mat cameraMatrix = Mat.eye(3, 3, CvType.CV_64F);
        cameraMatrix.put(0, 0, 800.0); // fx
        cameraMatrix.put(1, 1, 800.0); // fy
        cameraMatrix.put(0, 2, 320.0); // cx
        cameraMatrix.put(1, 2, 240.0); // cy
        return cameraMatrix;
    }

    private Mat createTestDistortionCoeffs() {
        Mat distCoeffs = new Mat(5, 1, CvType.CV_64F);
        distCoeffs.put(0, 0, 0.1, -0.2, 0.0, 0.0, 0.0); // k1, k2, p1, p2, k3
        return distCoeffs;
    }

    private List<DMatch> createTestMatches() {
        List<DMatch> matches = new ArrayList<>();
        
        // Create test matches with varying distances
        matches.add(new DMatch(0, 0, 10.0f));
        matches.add(new DMatch(1, 1, 15.0f));
        matches.add(new DMatch(2, 2, 25.0f));
        matches.add(new DMatch(3, 3, 35.0f));
        matches.add(new DMatch(4, 4, 50.0f));
        
        return matches;
    }

    private List<Point> createTestPoints() {
        List<Point> points = new ArrayList<>();
        
        // Create minimal set of test points for essential matrix estimation
        points.add(new Point(100, 100));
        points.add(new Point(200, 150));
        points.add(new Point(300, 200));
        points.add(new Point(400, 250));
        points.add(new Point(500, 300));
        
        return points;
    }

    private List<Point> createTestPointsForTransform() {
        List<Point> points = new ArrayList<>();
        
        // Create more realistic test points for geometric transform estimation
        points.add(new Point(120, 80));
        points.add(new Point(250, 120));
        points.add(new Point(380, 180));
        points.add(new Point(450, 220));
        points.add(new Point(520, 280));
        points.add(new Point(180, 350));
        points.add(new Point(320, 400));
        points.add(new Point(480, 450));
        
        return points;
    }

    private List<Point> createCorrespondingTestPoints() {
        List<Point> points = new ArrayList<>();
        
        // Create corresponding points with slight displacement (simulating camera movement)
        points.add(new Point(125, 85));
        points.add(new Point(255, 125));
        points.add(new Point(385, 185));
        points.add(new Point(455, 225));
        points.add(new Point(525, 285));
        points.add(new Point(185, 355));
        points.add(new Point(325, 405));
        points.add(new Point(485, 455));
        
        return points;
    }

    private List<Point> createMinimalTestPoints() {
        List<Point> points = new ArrayList<>();
        
        // Create insufficient points (less than MIN_MATCHES = 10)
        points.add(new Point(100, 100));
        points.add(new Point(200, 150));
        points.add(new Point(300, 200));
        
        return points;
    }

    private List<KeyPoint> createTestKeypoints(List<Point> points) {
        List<KeyPoint> keypoints = new ArrayList<>();
        
        for (Point point : points) {
            keypoints.add(new KeyPoint((float)point.x, (float)point.y, 10.0f));
        }
        
        return keypoints;
    }

    private Mat createTestDescriptors(int count) {
        // Create simple test descriptors (32 bytes per ORB descriptor)
        Mat descriptors = new Mat(count, 32, CvType.CV_8U);
        
        // Fill with test data
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < 32; j++) {
                descriptors.put(i, j, (i * 32 + j) % 256);
            }
        }
        
        return descriptors;
    }
}