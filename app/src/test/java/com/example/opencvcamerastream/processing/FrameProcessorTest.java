package com.example.opencvcamerastream.processing;

import android.media.Image;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opencv.core.Mat;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mockStatic;
import org.mockito.MockedStatic;

/**
 * Unit tests for FrameProcessor
 * 
 * Tests the integration between camera capture and OpenCV processing pipeline:
 * - Background thread for OpenCV operations
 * - Frame processing queue to handle timing
 * - Connection between ImageReader callback and OpenCV processing
 * - Processed frame callback to display system
 * - Proper threading to maintain UI responsiveness
 * 
 * Requirements tested:
 * - 2.1: Pass camera frame to OpenCV for processing
 * - 2.3: Ensure proper threading to maintain UI responsiveness
 * - 3.3: Add processed frame callback to display system
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Android 10 compatibility
public class FrameProcessorTest {
    
    @Mock
    private OpenCVProcessor mockOpenCVProcessor;
    
    @Mock
    private Image mockImage;
    
    @Mock
    private Mat mockInputMat;
    
    @Mock
    private Mat mockProcessedMat;
    
    @Mock
    private FrameProcessor.ProcessingCallback mockCallback;
    
    private FrameProcessor frameProcessor;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Set up mock OpenCV processor
        when(mockOpenCVProcessor.isInitialized()).thenReturn(true);
        when(mockOpenCVProcessor.processFrame(any(Mat.class))).thenReturn(mockProcessedMat);
        
        // Set up mock image
        when(mockImage.getWidth()).thenReturn(1280);
        when(mockImage.getHeight()).thenReturn(720);
        
        // Set up mock mats
        when(mockInputMat.empty()).thenReturn(false);
        when(mockProcessedMat.empty()).thenReturn(false);
        
        frameProcessor = new FrameProcessor(mockOpenCVProcessor);
        frameProcessor.setProcessingCallback(mockCallback);
    }
    
    @Test
    public void testFrameProcessorCreation() {
        // Test that frame processor can be created with OpenCV processor
        assertNotNull("FrameProcessor should be created", frameProcessor);
        assertFalse("Should not be processing initially", frameProcessor.isProcessing());
        assertEquals("Queue should be empty initially", 0, frameProcessor.getQueueSize());
    }
    
    @Test
    public void testStartProcessingPipeline() {
        // Test starting the processing pipeline
        // Requirement 2.3: Ensure proper threading to maintain UI responsiveness
        
        boolean started = frameProcessor.start();
        
        assertTrue("Processing pipeline should start successfully", started);
        assertTrue("Should be processing after start", frameProcessor.isProcessing());
    }
    
    @Test
    public void testStartFailsWithUninitializedProcessor() {
        // Test that start fails when OpenCV processor is not initialized
        when(mockOpenCVProcessor.isInitialized()).thenReturn(false);
        
        boolean started = frameProcessor.start();
        
        assertFalse("Processing pipeline should fail to start", started);
        assertFalse("Should not be processing", frameProcessor.isProcessing());
    }
    
    @Test
    public void testStopProcessingPipeline() {
        // Test stopping the processing pipeline
        frameProcessor.start();
        assertTrue("Should be processing", frameProcessor.isProcessing());
        
        frameProcessor.stop();
        
        assertFalse("Should not be processing after stop", frameProcessor.isProcessing());
        assertEquals("Queue should be empty after stop", 0, frameProcessor.getQueueSize());
    }
    
    @Test
    public void testProcessFrameAsyncWhenNotRunning() {
        // Test that frames are properly closed when processor is not running
        frameProcessor.processFrameAsync(mockImage);
        
        // Verify image is closed when processor is not running
        verify(mockImage, timeout(1000)).close();
        assertEquals("Queue should remain empty", 0, frameProcessor.getQueueSize());
    }
    
    @Test
    public void testProcessFrameAsyncWhenRunning() throws InterruptedException {
        // Test frame processing when pipeline is running
        // Requirement 2.1: Pass camera frame to OpenCV for processing
        
        // Mock static method for image to mat conversion
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(mockImage))
                       .thenReturn(mockInputMat);
            
            frameProcessor.start();
            frameProcessor.processFrameAsync(mockImage);
            
            // Wait for processing to complete
            Thread.sleep(100);
            
            // Verify OpenCV processing was called
            verify(mockOpenCVProcessor, timeout(1000)).processFrame(mockInputMat);
            
            // Verify callback was called with processed frame
            // Requirement 3.3: Add processed frame callback to display system
            verify(mockCallback, timeout(1000)).onFrameProcessed(
                eq(mockProcessedMat), eq(mockImage), anyLong());
        }
    }
    
    @Test
    public void testFrameQueueOverflow() {
        // Test frame dropping when queue is full
        frameProcessor.start();
        
        // Fill the queue beyond capacity
        for (int i = 0; i < 10; i++) {
            Image mockImageInstance = mock(Image.class);
            frameProcessor.processFrameAsync(mockImageInstance);
        }
        
        // Verify that frames are dropped when queue is full
        verify(mockCallback, atLeastOnce()).onFrameDropped(any(Image.class));
    }
    
    @Test
    public void testProcessingErrorHandling() throws InterruptedException {
        // Test error handling during frame processing
        // Mock static method to throw exception
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(mockImage))
                       .thenThrow(new RuntimeException("Conversion failed"));
            
            frameProcessor.start();
            frameProcessor.processFrameAsync(mockImage);
            
            // Wait for processing to complete
            Thread.sleep(100);
            
            // Verify error callback was called
            verify(mockCallback, timeout(1000)).onProcessingFailed(
                any(RuntimeException.class), eq(mockImage));
        }
    }
    
    @Test
    public void testEmptyMatHandling() throws InterruptedException {
        // Test handling of empty Mat from image conversion
        when(mockInputMat.empty()).thenReturn(true);
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(mockImage))
                       .thenReturn(mockInputMat);
            
            frameProcessor.start();
            frameProcessor.processFrameAsync(mockImage);
            
            // Wait for processing to complete
            Thread.sleep(100);
            
            // Verify error callback was called for empty Mat
            verify(mockCallback, timeout(1000)).onProcessingFailed(
                any(RuntimeException.class), eq(mockImage));
        }
    }
    
    @Test
    public void testPerformanceMetrics() {
        // Test performance metrics collection
        FrameProcessor.ProcessingStats stats = frameProcessor.getProcessingStats();
        
        assertNotNull("Stats should not be null", stats);
        assertEquals("Initial frames received should be 0", 0, stats.framesReceived);
        assertEquals("Initial frames processed should be 0", 0, stats.framesProcessed);
        assertEquals("Initial frames dropped should be 0", 0, stats.framesDropped);
        assertEquals("Initial queue size should be 0", 0, stats.currentQueueSize);
    }
    
    @Test
    public void testProcessingStatsCalculations() {
        // Test processing statistics calculations
        FrameProcessor.ProcessingStats stats = new FrameProcessor.ProcessingStats(100, 95, 5, 2);
        
        assertEquals("Drop rate should be 5%", 5.0, stats.getDropRate(), 0.01);
        assertEquals("Processing rate should be 95%", 95.0, stats.getProcessingRate(), 0.01);
    }
    
    @Test
    public void testProcessingStatsWithZeroFrames() {
        // Test processing statistics with zero frames
        FrameProcessor.ProcessingStats stats = new FrameProcessor.ProcessingStats(0, 0, 0, 0);
        
        assertEquals("Drop rate should be 0% with no frames", 0.0, stats.getDropRate(), 0.01);
        assertEquals("Processing rate should be 0% with no frames", 0.0, stats.getProcessingRate(), 0.01);
    }
    
    @Test
    public void testMultipleStartStopCycles() {
        // Test multiple start/stop cycles
        for (int i = 0; i < 3; i++) {
            boolean started = frameProcessor.start();
            assertTrue("Should start successfully on cycle " + i, started);
            assertTrue("Should be processing on cycle " + i, frameProcessor.isProcessing());
            
            frameProcessor.stop();
            assertFalse("Should not be processing after stop on cycle " + i, frameProcessor.isProcessing());
        }
    }
    
    @Test
    public void testCallbackNullHandling() {
        // Test behavior when callback is null
        frameProcessor.setProcessingCallback(null);
        frameProcessor.start();
        
        // Should not crash when processing frame without callback
        frameProcessor.processFrameAsync(mockImage);
        
        // Verify image is still closed properly
        verify(mockImage, timeout(1000)).close();
    }
    
    @Test
    public void testProcessingTimeoutScenario() throws InterruptedException {
        // Test processing timeout handling
        // Mock a slow processing operation
        when(mockOpenCVProcessor.processFrame(any(Mat.class))).thenAnswer(invocation -> {
            Thread.sleep(100); // Simulate slow processing
            return mockProcessedMat;
        });
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(mockImage))
                       .thenReturn(mockInputMat);
            
            frameProcessor.start();
            frameProcessor.processFrameAsync(mockImage);
            
            // Wait for processing to complete
            Thread.sleep(200);
            
            // Verify processing completed (timeout handling is in OpenCVProcessor)
            verify(mockOpenCVProcessor, timeout(1000)).processFrame(mockInputMat);
        }
    }
}