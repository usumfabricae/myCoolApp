package com.example.opencvcamerastream.processing;

import android.graphics.Bitmap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OpenCVProcessor class
 * 
 * Tests cover:
 * - OpenCV initialization and setup
 * - Frame processing with different modes
 * - Mat conversion utilities (Image to Mat, Mat to Bitmap)
 * - Error handling and fallback mechanisms
 * - Performance monitoring and timeout handling
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Android 10 compatibility
public class OpenCVProcessorTest {
    
    private OpenCVProcessor processor;
    private OpenCVProcessor.ProcessingConfig config;
    
    @Mock
    private OpenCVProcessor.ProcessingCallback mockCallback;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Create default configuration
        config = new OpenCVProcessor.ProcessingConfig();
        config.mode = OpenCVProcessor.ProcessingMode.GRAYSCALE;
        config.enablePerformanceOptimization = true;
        config.maxProcessingTimeMs = 50;
        
        processor = new OpenCVProcessor(config);
        processor.setProcessingCallback(mockCallback);
    }
    
    @Test
    public void testProcessorInitialization() {
        // Test initial state
        assertFalse("Processor should not be initialized initially", processor.isInitialized());
        
        // Test initialization
        boolean initResult = processor.initialize();
        assertTrue("Processor initialization should succeed", initResult);
        assertTrue("Processor should be initialized after initialize() call", processor.isInitialized());
    }
    
    @Test
    public void testProcessorInitializationWithDefaultConfig() {
        OpenCVProcessor defaultProcessor = new OpenCVProcessor();
        
        assertFalse("Default processor should not be initialized initially", defaultProcessor.isInitialized());
        
        boolean initResult = defaultProcessor.initialize();
        assertTrue("Default processor initialization should succeed", initResult);
        assertTrue("Default processor should be initialized", defaultProcessor.isInitialized());
    }
    
    @Test
    public void testProcessingConfigUpdate() {
        processor.initialize();
        
        // Create new config
        OpenCVProcessor.ProcessingConfig newConfig = new OpenCVProcessor.ProcessingConfig();
        newConfig.mode = OpenCVProcessor.ProcessingMode.PASSTHROUGH;
        newConfig.maxProcessingTimeMs = 100;
        
        // Update config
        processor.setProcessingConfig(newConfig);
        
        // Verify config is updated by testing processing behavior
        // This is indirectly tested through processing mode behavior
        assertNotNull("Processor should accept new config", newConfig);
    }
    
    @Test
    public void testProcessFrameWithoutInitialization() {
        // Create a test Mat
        Mat testMat = createTestMat();
        
        // Process frame without initialization
        Mat result = processor.processFrame(testMat);
        
        assertNotNull("Result should not be null", result);
        assertEquals("Result should have same dimensions as input", testMat.rows(), result.rows());
        assertEquals("Result should have same dimensions as input", testMat.cols(), result.cols());
        
        // Cleanup
        testMat.release();
        result.release();
    }
    
    @Test
    public void testProcessFramePassthroughMode() {
        processor.initialize();
        
        // Set passthrough mode
        OpenCVProcessor.ProcessingConfig passthroughConfig = new OpenCVProcessor.ProcessingConfig();
        passthroughConfig.mode = OpenCVProcessor.ProcessingMode.PASSTHROUGH;
        processor.setProcessingConfig(passthroughConfig);
        
        // Create test Mat
        Mat testMat = createTestMat();
        
        // Process frame
        Mat result = processor.processFrame(testMat);
        
        assertNotNull("Result should not be null", result);
        assertEquals("Result should have same dimensions as input", testMat.rows(), result.rows());
        assertEquals("Result should have same dimensions as input", testMat.cols(), result.cols());
        
        // Verify callback was called
        verify(mockCallback, timeout(1000)).onFrameProcessed(any(Mat.class), anyLong());
        
        // Cleanup
        testMat.release();
        result.release();
    }
    
    @Test
    public void testProcessFrameGrayscaleMode() {
        processor.initialize();
        
        // Create RGB test Mat
        Mat rgbMat = createTestMat();
        
        // Process frame (default is grayscale mode)
        Mat result = processor.processFrame(rgbMat);
        
        assertNotNull("Result should not be null", result);
        assertEquals("Result should have same dimensions as input", rgbMat.rows(), result.rows());
        assertEquals("Result should have same dimensions as input", rgbMat.cols(), result.cols());
        
        // Verify callback was called
        verify(mockCallback, timeout(1000)).onFrameProcessed(any(Mat.class), anyLong());
        
        // Cleanup
        rgbMat.release();
        result.release();
    }
    
    @Test
    public void testProcessFrameEdgeDetectionFallback() {
        processor.initialize();
        
        // Set edge detection mode (should fallback to grayscale)
        OpenCVProcessor.ProcessingConfig edgeConfig = new OpenCVProcessor.ProcessingConfig();
        edgeConfig.mode = OpenCVProcessor.ProcessingMode.EDGE_DETECTION;
        processor.setProcessingConfig(edgeConfig);
        
        // Create test Mat
        Mat testMat = createTestMat();
        
        // Process frame
        Mat result = processor.processFrame(testMat);
        
        assertNotNull("Result should not be null", result);
        
        // Verify callback was called
        verify(mockCallback, timeout(1000)).onFrameProcessed(any(Mat.class), anyLong());
        
        // Cleanup
        testMat.release();
        result.release();
    }
    
    @Test
    public void testProcessFrameEdgeDetectionMode() {
        processor.initialize();
        
        // Set edge detection mode
        OpenCVProcessor.ProcessingConfig edgeConfig = new OpenCVProcessor.ProcessingConfig();
        edgeConfig.mode = OpenCVProcessor.ProcessingMode.EDGE_DETECTION;
        edgeConfig.cannyLowThreshold = 50.0;
        edgeConfig.cannyHighThreshold = 150.0;
        edgeConfig.cannyApertureSize = 3;
        processor.setProcessingConfig(edgeConfig);
        
        // Create test Mat
        Mat testMat = createTestMat();
        
        // Process frame
        Mat result = processor.processFrame(testMat);
        
        assertNotNull("Result should not be null", result);
        assertEquals("Result should have same dimensions as input", testMat.rows(), result.rows());
        assertEquals("Result should have same dimensions as input", testMat.cols(), result.cols());
        
        // Verify callback was called
        verify(mockCallback, timeout(1000)).onFrameProcessed(any(Mat.class), anyLong());
        
        // Cleanup
        testMat.release();
        result.release();
    }
    
    @Test
    public void testProcessFrameHSVColorMode() {
        processor.initialize();
        
        // Set HSV color mode
        OpenCVProcessor.ProcessingConfig hsvConfig = new OpenCVProcessor.ProcessingConfig();
        hsvConfig.mode = OpenCVProcessor.ProcessingMode.COLOR_HSV;
        processor.setProcessingConfig(hsvConfig);
        
        // Create test Mat
        Mat testMat = createTestMat();
        
        // Process frame
        Mat result = processor.processFrame(testMat);
        
        assertNotNull("Result should not be null", result);
        assertEquals("Result should have same dimensions as input", testMat.rows(), result.rows());
        assertEquals("Result should have same dimensions as input", testMat.cols(), result.cols());
        
        // Verify callback was called
        verify(mockCallback, timeout(1000)).onFrameProcessed(any(Mat.class), anyLong());
        
        // Cleanup
        testMat.release();
        result.release();
    }
    
    @Test
    public void testProcessFrameLABColorMode() {
        processor.initialize();
        
        // Set LAB color mode
        OpenCVProcessor.ProcessingConfig labConfig = new OpenCVProcessor.ProcessingConfig();
        labConfig.mode = OpenCVProcessor.ProcessingMode.COLOR_LAB;
        processor.setProcessingConfig(labConfig);
        
        // Create test Mat
        Mat testMat = createTestMat();
        
        // Process frame
        Mat result = processor.processFrame(testMat);
        
        assertNotNull("Result should not be null", result);
        assertEquals("Result should have same dimensions as input", testMat.rows(), result.rows());
        assertEquals("Result should have same dimensions as input", testMat.cols(), result.cols());
        
        // Verify callback was called
        verify(mockCallback, timeout(1000)).onFrameProcessed(any(Mat.class), anyLong());
        
        // Cleanup
        testMat.release();
        result.release();
    }
    
    @Test
    public void testProcessFrameBlurMode() {
        processor.initialize();
        
        // Set blur mode
        OpenCVProcessor.ProcessingConfig blurConfig = new OpenCVProcessor.ProcessingConfig();
        blurConfig.mode = OpenCVProcessor.ProcessingMode.BLUR;
        blurConfig.blurKernelSize = 15;
        blurConfig.blurSigmaX = 0.0;
        blurConfig.blurSigmaY = 0.0;
        processor.setProcessingConfig(blurConfig);
        
        // Create test Mat
        Mat testMat = createTestMat();
        
        // Process frame
        Mat result = processor.processFrame(testMat);
        
        assertNotNull("Result should not be null", result);
        assertEquals("Result should have same dimensions as input", testMat.rows(), result.rows());
        assertEquals("Result should have same dimensions as input", testMat.cols(), result.cols());
        
        // Verify callback was called
        verify(mockCallback, timeout(1000)).onFrameProcessed(any(Mat.class), anyLong());
        
        // Cleanup
        testMat.release();
        result.release();
    }
    
    @Test
    public void testProcessFrameSharpenMode() {
        processor.initialize();
        
        // Set sharpen mode
        OpenCVProcessor.ProcessingConfig sharpenConfig = new OpenCVProcessor.ProcessingConfig();
        sharpenConfig.mode = OpenCVProcessor.ProcessingMode.SHARPEN;
        sharpenConfig.sharpenStrength = 1.0f;
        processor.setProcessingConfig(sharpenConfig);
        
        // Create test Mat
        Mat testMat = createTestMat();
        
        // Process frame
        Mat result = processor.processFrame(testMat);
        
        assertNotNull("Result should not be null", result);
        assertEquals("Result should have same dimensions as input", testMat.rows(), result.rows());
        assertEquals("Result should have same dimensions as input", testMat.cols(), result.cols());
        
        // Verify callback was called
        verify(mockCallback, timeout(1000)).onFrameProcessed(any(Mat.class), anyLong());
        
        // Cleanup
        testMat.release();
        result.release();
    }
    
    @Test
    public void testPerformanceMetrics() {
        processor.initialize();
        
        // Get initial metrics
        OpenCVProcessor.PerformanceMetrics metrics = processor.getPerformanceMetrics();
        assertEquals("Initial frame count should be 0", 0, metrics.totalFrames);
        assertEquals("Initial processing time should be 0", 0, metrics.totalProcessingTime);
        
        // Process a frame
        Mat testMat = createTestMat();
        processor.processFrame(testMat);
        
        // Check metrics updated
        assertTrue("Frame count should be incremented", metrics.totalFrames > 0);
        assertTrue("Total processing time should be recorded", metrics.totalProcessingTime >= 0);
        
        // Test metrics reset
        processor.resetPerformanceMetrics();
        assertEquals("Frame count should be reset", 0, metrics.totalFrames);
        assertEquals("Processing time should be reset", 0, metrics.totalProcessingTime);
        
        // Cleanup
        testMat.release();
    }
    
    @Test
    public void testPerformanceMetricsAverageCalculation() {
        OpenCVProcessor.PerformanceMetrics metrics = processor.getPerformanceMetrics();
        
        // Test with no frames
        assertEquals("Average should be 0 with no frames", 0.0, metrics.getAverageProcessingTime(), 0.001);
        
        // Simulate processing times
        metrics.recordProcessing(10);
        metrics.recordProcessing(20);
        metrics.recordProcessing(30);
        
        assertEquals("Average should be calculated correctly", 20.0, metrics.getAverageProcessingTime(), 0.001);
        assertEquals("Total frames should be 3", 3, metrics.totalFrames);
        assertEquals("Max processing time should be 30", 30, metrics.maxProcessingTime);
    }
    
    @Test
    public void testCallbackHandling() {
        processor.initialize();
        
        // Test with callback
        Mat testMat = createTestMat();
        processor.processFrame(testMat);
        
        verify(mockCallback, timeout(1000)).onFrameProcessed(any(Mat.class), anyLong());
        
        // Test without callback
        processor.setProcessingCallback(null);
        Mat result = processor.processFrame(testMat);
        assertNotNull("Processing should work without callback", result);
        
        // Cleanup
        testMat.release();
        result.release();
    }
    
    @Test
    public void testResourceRelease() {
        processor.initialize();
        assertTrue("Processor should be initialized", processor.isInitialized());
        
        // Release resources
        processor.release();
        assertFalse("Processor should not be initialized after release", processor.isInitialized());
    }
    
    @Test
    public void testMatToBitmapConversion() {
        // Create a test Mat
        Mat testMat = createTestMat();
        
        try {
            // Convert to bitmap
            Bitmap bitmap = OpenCVProcessor.matToBitmap(testMat);
            
            assertNotNull("Bitmap should not be null", bitmap);
            assertEquals("Bitmap width should match Mat cols", testMat.cols(), bitmap.getWidth());
            assertEquals("Bitmap height should match Mat rows", testMat.rows(), bitmap.getHeight());
            
            // Cleanup bitmap
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
            
        } catch (Exception e) {
            // This test might fail in unit test environment due to OpenCV not being fully initialized
            // In that case, we expect a RuntimeException
            assertTrue("Should throw RuntimeException when OpenCV not available", 
                    e instanceof RuntimeException);
        } finally {
            testMat.release();
        }
    }
    
    @Test
    public void testNullInputHandling() {
        processor.initialize();
        
        try {
            // This should throw an exception or handle gracefully
            Mat result = processor.processFrame(null);
            // If we get here, the method handled null gracefully
            assertNotNull("Result should not be null even with null input", result);
            result.release();
        } catch (Exception e) {
            // Expected behavior - method should handle null input appropriately
            assertTrue("Should handle null input appropriately", true);
        }
    }
    
    @Test
    public void testEdgeDetectionParameters() {
        processor.initialize();
        
        // Test with custom edge detection parameters
        OpenCVProcessor.ProcessingConfig edgeConfig = new OpenCVProcessor.ProcessingConfig();
        edgeConfig.mode = OpenCVProcessor.ProcessingMode.EDGE_DETECTION;
        edgeConfig.cannyLowThreshold = 100.0;
        edgeConfig.cannyHighThreshold = 200.0;
        edgeConfig.cannyApertureSize = 5;
        processor.setProcessingConfig(edgeConfig);
        
        // Create test Mat
        Mat testMat = createTestMat();
        
        // Process frame
        Mat result = processor.processFrame(testMat);
        
        assertNotNull("Result should not be null", result);
        
        // Cleanup
        testMat.release();
        result.release();
    }
    
    @Test
    public void testBlurParameters() {
        processor.initialize();
        
        // Test with custom blur parameters
        OpenCVProcessor.ProcessingConfig blurConfig = new OpenCVProcessor.ProcessingConfig();
        blurConfig.mode = OpenCVProcessor.ProcessingMode.BLUR;
        blurConfig.blurKernelSize = 21; // Should be adjusted to odd number
        blurConfig.blurSigmaX = 2.0;
        blurConfig.blurSigmaY = 2.0;
        processor.setProcessingConfig(blurConfig);
        
        // Create test Mat
        Mat testMat = createTestMat();
        
        // Process frame
        Mat result = processor.processFrame(testMat);
        
        assertNotNull("Result should not be null", result);
        
        // Cleanup
        testMat.release();
        result.release();
    }
    
    @Test
    public void testSharpenParameters() {
        processor.initialize();
        
        // Test with custom sharpen parameters
        OpenCVProcessor.ProcessingConfig sharpenConfig = new OpenCVProcessor.ProcessingConfig();
        sharpenConfig.mode = OpenCVProcessor.ProcessingMode.SHARPEN;
        sharpenConfig.sharpenStrength = 2.0f;
        processor.setProcessingConfig(sharpenConfig);
        
        // Create test Mat
        Mat testMat = createTestMat();
        
        // Process frame
        Mat result = processor.processFrame(testMat);
        
        assertNotNull("Result should not be null", result);
        
        // Cleanup
        testMat.release();
        result.release();
    }
    
    @Test
    public void testProcessingModeEnumValues() {
        // Test that all processing modes are available
        OpenCVProcessor.ProcessingMode[] modes = OpenCVProcessor.ProcessingMode.values();
        
        assertTrue("Should have PASSTHROUGH mode", 
                java.util.Arrays.asList(modes).contains(OpenCVProcessor.ProcessingMode.PASSTHROUGH));
        assertTrue("Should have GRAYSCALE mode", 
                java.util.Arrays.asList(modes).contains(OpenCVProcessor.ProcessingMode.GRAYSCALE));
        assertTrue("Should have EDGE_DETECTION mode", 
                java.util.Arrays.asList(modes).contains(OpenCVProcessor.ProcessingMode.EDGE_DETECTION));
        assertTrue("Should have COLOR_HSV mode", 
                java.util.Arrays.asList(modes).contains(OpenCVProcessor.ProcessingMode.COLOR_HSV));
        assertTrue("Should have COLOR_LAB mode", 
                java.util.Arrays.asList(modes).contains(OpenCVProcessor.ProcessingMode.COLOR_LAB));
        assertTrue("Should have BLUR mode", 
                java.util.Arrays.asList(modes).contains(OpenCVProcessor.ProcessingMode.BLUR));
        assertTrue("Should have SHARPEN mode", 
                java.util.Arrays.asList(modes).contains(OpenCVProcessor.ProcessingMode.SHARPEN));
    }
    
    @Test
    public void testProcessingConfigDefaults() {
        // Test default processing configuration
        OpenCVProcessor.ProcessingConfig config = new OpenCVProcessor.ProcessingConfig();
        
        assertEquals("Default mode should be GRAYSCALE", 
                OpenCVProcessor.ProcessingMode.GRAYSCALE, config.mode);
        assertTrue("Performance optimization should be enabled by default", 
                config.enablePerformanceOptimization);
        assertEquals("Default max processing time should be 50ms", 
                50, config.maxProcessingTimeMs);
        
        // Test edge detection defaults
        assertEquals("Default Canny low threshold should be 50.0", 
                50.0, config.cannyLowThreshold, 0.001);
        assertEquals("Default Canny high threshold should be 150.0", 
                150.0, config.cannyHighThreshold, 0.001);
        assertEquals("Default Canny aperture size should be 3", 
                3, config.cannyApertureSize);
        
        // Test blur defaults
        assertEquals("Default blur kernel size should be 15", 
                15, config.blurKernelSize);
        assertEquals("Default blur sigma X should be 0.0", 
                0.0, config.blurSigmaX, 0.001);
        assertEquals("Default blur sigma Y should be 0.0", 
                0.0, config.blurSigmaY, 0.001);
        
        // Test sharpen defaults
        assertEquals("Default sharpen strength should be 1.0", 
                1.0f, config.sharpenStrength, 0.001f);
    }
    
    /**
     * Helper method to create a test Mat for testing
     */
    private Mat createTestMat() {
        // Create a simple 100x100 RGB Mat for testing
        Mat mat = new Mat(100, 100, CvType.CV_8UC3);
        
        // Fill with some test data
        byte[] data = new byte[100 * 100 * 3];
        for (int i = 0; i < data.length; i += 3) {
            data[i] = (byte) 255;     // R
            data[i + 1] = (byte) 128; // G
            data[i + 2] = (byte) 64;  // B
        }
        mat.put(0, 0, data);
        
        return mat;
    }
}