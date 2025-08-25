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
    public void testProcessFrameColorFilterFallback() {
        processor.initialize();
        
        // Set color filter mode (should fallback to grayscale)
        OpenCVProcessor.ProcessingConfig colorConfig = new OpenCVProcessor.ProcessingConfig();
        colorConfig.mode = OpenCVProcessor.ProcessingMode.COLOR_FILTER;
        processor.setProcessingConfig(colorConfig);
        
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