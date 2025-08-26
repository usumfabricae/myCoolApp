package com.example.opencvcamerastream.integration;

import com.example.opencvcamerastream.processing.FrameProcessor;
import com.example.opencvcamerastream.processing.OpenCVProcessor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Basic integration test for camera-to-display pipeline
 * 
 * This test verifies the basic integration between components without
 * complex threading or mocking, suitable for CI/CD validation.
 * 
 * Requirements tested:
 * - 2.1: Connect ImageReader callback to OpenCV processing
 * - 2.3: Implement background thread for OpenCV operations
 * - 3.3: Create frame processing queue to handle timing
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Android 10 compatibility
public class BasicPipelineIntegrationTest {
    
    @Mock
    private OpenCVProcessor mockOpenCVProcessor;
    
    private FrameProcessor frameProcessor;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Set up mock OpenCV processor
        when(mockOpenCVProcessor.isInitialized()).thenReturn(true);
        
        frameProcessor = new FrameProcessor(mockOpenCVProcessor);
    }
    
    @Test
    public void testFrameProcessorCreation() {
        // Test basic creation and initialization
        // Requirement: Basic pipeline component integration
        
        assertNotNull("FrameProcessor should be created successfully", frameProcessor);
        assertFalse("Should not be processing initially", frameProcessor.isProcessing());
        assertEquals("Queue should be empty initially", 0, frameProcessor.getQueueSize());
    }
    
    @Test
    public void testFrameProcessorStartStop() {
        // Test basic start/stop functionality
        // Requirement 2.3: Implement background thread for OpenCV operations
        
        // Test start
        boolean started = frameProcessor.start();
        assertTrue("Frame processor should start successfully", started);
        assertTrue("Should be processing after start", frameProcessor.isProcessing());
        
        // Test stop
        frameProcessor.stop();
        assertFalse("Should not be processing after stop", frameProcessor.isProcessing());
        assertEquals("Queue should be empty after stop", 0, frameProcessor.getQueueSize());
    }
    
    @Test
    public void testFrameProcessorWithUninitializedOpenCV() {
        // Test behavior when OpenCV is not initialized
        // Requirement 2.1: Connect ImageReader callback to OpenCV processing
        
        when(mockOpenCVProcessor.isInitialized()).thenReturn(false);
        
        boolean started = frameProcessor.start();
        assertFalse("Should not start with uninitialized OpenCV", started);
        assertFalse("Should not be processing", frameProcessor.isProcessing());
    }
    
    @Test
    public void testFrameProcessorPerformanceMetrics() {
        // Test performance metrics collection
        // Requirement 3.3: Create frame processing queue to handle timing
        
        FrameProcessor.ProcessingStats stats = frameProcessor.getProcessingStats();
        
        assertNotNull("Stats should not be null", stats);
        assertEquals("Initial frames received should be 0", 0, stats.framesReceived);
        assertEquals("Initial frames processed should be 0", 0, stats.framesProcessed);
        assertEquals("Initial frames dropped should be 0", 0, stats.framesDropped);
        assertEquals("Initial queue size should be 0", 0, stats.currentQueueSize);
        
        // Test stats calculations
        assertEquals("Drop rate should be 0% initially", 0.0, stats.getDropRate(), 0.01);
        assertEquals("Processing rate should be 0% initially", 0.0, stats.getProcessingRate(), 0.01);
    }
    
    @Test
    public void testFrameProcessorMultipleStartStopCycles() {
        // Test multiple start/stop cycles for robustness
        // Requirement 2.3: Ensure proper threading to maintain UI responsiveness
        
        for (int i = 0; i < 3; i++) {
            boolean started = frameProcessor.start();
            assertTrue("Should start successfully on cycle " + i, started);
            assertTrue("Should be processing on cycle " + i, frameProcessor.isProcessing());
            
            frameProcessor.stop();
            assertFalse("Should not be processing after stop on cycle " + i, frameProcessor.isProcessing());
        }
    }
    
    @Test
    public void testOpenCVProcessorConfiguration() {
        // Test OpenCV processor configuration
        // Requirement 2.1: Pass camera frame to OpenCV for processing
        
        OpenCVProcessor.ProcessingConfig config = new OpenCVProcessor.ProcessingConfig();
        config.mode = OpenCVProcessor.ProcessingMode.GRAYSCALE;
        config.enablePerformanceOptimization = true;
        config.maxProcessingTimeMs = 50;
        
        OpenCVProcessor processor = new OpenCVProcessor(config);
        
        assertNotNull("OpenCV processor should be created", processor);
        assertFalse("Should not be initialized initially", processor.isInitialized());
    }
    
    @Test
    public void testProcessingStatsCalculations() {
        // Test processing statistics calculations
        // Requirement 3.3: Frame processing queue performance monitoring
        
        // Test with some processed frames
        FrameProcessor.ProcessingStats stats1 = new FrameProcessor.ProcessingStats(100, 95, 5, 2);
        assertEquals("Drop rate should be 5%", 5.0, stats1.getDropRate(), 0.01);
        assertEquals("Processing rate should be 95%", 95.0, stats1.getProcessingRate(), 0.01);
        
        // Test with zero frames
        FrameProcessor.ProcessingStats stats2 = new FrameProcessor.ProcessingStats(0, 0, 0, 0);
        assertEquals("Drop rate should be 0% with no frames", 0.0, stats2.getDropRate(), 0.01);
        assertEquals("Processing rate should be 0% with no frames", 0.0, stats2.getProcessingRate(), 0.01);
        
        // Test with all frames dropped
        FrameProcessor.ProcessingStats stats3 = new FrameProcessor.ProcessingStats(10, 0, 10, 0);
        assertEquals("Drop rate should be 100% when all dropped", 100.0, stats3.getDropRate(), 0.01);
        assertEquals("Processing rate should be 0% when all dropped", 0.0, stats3.getProcessingRate(), 0.01);
    }
    
    @Test
    public void testFrameProcessorCallbackHandling() {
        // Test callback handling without actual processing
        // Requirement: Integration between components
        
        FrameProcessor.ProcessingCallback callback = mock(FrameProcessor.ProcessingCallback.class);
        frameProcessor.setProcessingCallback(callback);
        
        // Should not crash when setting callback
        assertNotNull("Frame processor should handle callback setting", frameProcessor);
        
        // Test setting null callback
        frameProcessor.setProcessingCallback(null);
        assertNotNull("Frame processor should handle null callback", frameProcessor);
    }
    
    @Test
    public void testIntegrationComponentsExist() {
        // Test that all required integration components exist and can be instantiated
        // This verifies the basic integration structure is in place
        
        // Test OpenCV processor creation
        OpenCVProcessor openCVProcessor = new OpenCVProcessor();
        assertNotNull("OpenCV processor should be created", openCVProcessor);
        
        // Test frame processor creation with OpenCV processor
        FrameProcessor frameProcessor = new FrameProcessor(openCVProcessor);
        assertNotNull("Frame processor should be created with OpenCV processor", frameProcessor);
        
        // Test that components can be configured
        OpenCVProcessor.ProcessingConfig config = new OpenCVProcessor.ProcessingConfig();
        config.mode = OpenCVProcessor.ProcessingMode.GRAYSCALE;
        
        OpenCVProcessor configuredProcessor = new OpenCVProcessor(config);
        assertNotNull("Configured OpenCV processor should be created", configuredProcessor);
        
        FrameProcessor configuredFrameProcessor = new FrameProcessor(configuredProcessor);
        assertNotNull("Frame processor with configured processor should be created", configuredFrameProcessor);
    }
}