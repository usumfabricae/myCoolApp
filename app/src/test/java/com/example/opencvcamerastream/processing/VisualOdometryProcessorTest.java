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
}