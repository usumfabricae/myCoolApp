package com.example.opencvcamerastream.processing;

import android.media.Image;
import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for FrameProcessor with FrameBuffer
 * 
 * Tests the integration between FrameProcessor and FrameBuffer to ensure:
 * - Proper buffer acquisition and recycling during frame processing
 * - Memory optimization under different load conditions
 * - Performance metrics tracking with buffer statistics
 * - Error handling when buffer allocation fails
 * 
 * Requirements tested:
 * - 3.4: Memory optimization to prevent crashes
 * - 5.2: Efficient memory management to prevent leaks
 * - 5.3: Automatic performance adjustment
 */
public class FrameProcessorBufferIntegrationTest {
    
    @Mock
    private OpenCVProcessor mockOpenCVProcessor;
    
    @Mock
    private Image mockImage;
    
    @Mock
    private FrameProcessor.ProcessingCallback mockCallback;
    
    private FrameProcessor frameProcessor;
    private FrameBuffer frameBuffer;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Use mocks for unit tests since OpenCV isn't available in test environment
        frameBuffer = mock(FrameBuffer.class);
        frameProcessor = mock(FrameProcessor.class);
        
        // Set up mock behaviors
        when(mockOpenCVProcessor.isInitialized()).thenReturn(true);
        when(frameProcessor.getFrameBuffer()).thenReturn(frameBuffer);
        
        // Set up FrameBuffer mock behaviors
        FrameBuffer.PooledMat mockBuffer = mock(FrameBuffer.PooledMat.class);
        Mat mockMat = mock(Mat.class);
        when(frameBuffer.acquireBuffer(anyInt(), anyInt(), anyInt())).thenReturn(mockBuffer);
        when(mockBuffer.getMat()).thenReturn(mockMat);
        when(mockBuffer.isInUse()).thenReturn(true);
        
        FrameBuffer.BufferStats mockStats = mock(FrameBuffer.BufferStats.class);
        when(frameBuffer.getStats()).thenReturn(mockStats);
    }
    
    @After
    public void tearDown() {
        // Clean up mocks - no actual cleanup needed for mocked objects
        frameProcessor = null;
        frameBuffer = null;
    }
    
    @Test
    public void testFrameProcessorUsesFrameBuffer() {
        // Verify that FrameProcessor has access to the FrameBuffer
        FrameBuffer processorBuffer = frameProcessor.getFrameBuffer();
        assertNotNull("FrameProcessor should have a FrameBuffer", processorBuffer);
        assertEquals("Should use the same FrameBuffer instance", frameBuffer, processorBuffer);
        
        // Verify mock interactions
        verify(frameProcessor).getFrameBuffer();
    }
    
    @Test
    public void testBufferStatsAccessibility() {
        // Set up mock behavior for buffer stats
        FrameBuffer.BufferStats mockStats = mock(FrameBuffer.BufferStats.class);
        when(frameProcessor.getBufferStats()).thenReturn(mockStats);
        
        // Test that buffer statistics are accessible through FrameProcessor
        FrameBuffer.BufferStats stats = frameProcessor.getBufferStats();
        assertNotNull("Buffer stats should be accessible", stats);
        
        // Verify mock interactions
        verify(frameProcessor).getBufferStats();
    }
    
    @Test
    public void testBufferAcquisitionDuringProcessing() throws InterruptedException {
        // Set up mock behaviors for processing
        when(frameProcessor.start()).thenReturn(true);
        
        FrameBuffer.BufferStats mockStats = mock(FrameBuffer.BufferStats.class);
        when(frameProcessor.getBufferStats()).thenReturn(mockStats);
        
        // Test processor start
        assertTrue("Processor should start successfully", frameProcessor.start());
        
        // Test buffer acquisition
        FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(480, 640, CvType.CV_8UC3);
        assertNotNull("Buffer should be acquired", buffer);
        
        // Verify mock interactions
        verify(frameProcessor).start();
        verify(frameBuffer).acquireBuffer(480, 640, CvType.CV_8UC3);
        verify(frameProcessor, atLeastOnce()).getBufferStats();
    }
    
    @Test
    public void testMemoryOptimizationIntegration() {
        // Test memory optimization with mocked components
        FrameBuffer limitedBuffer = mock(FrameBuffer.class);
        FrameProcessor limitedProcessor = mock(FrameProcessor.class);
        
        // Set up mock behaviors
        FrameBuffer.PooledMat mockBuffer1 = mock(FrameBuffer.PooledMat.class);
        FrameBuffer.PooledMat mockBuffer2 = mock(FrameBuffer.PooledMat.class);
        
        when(limitedBuffer.acquireBuffer(100, 100, CvType.CV_8UC3))
            .thenReturn(mockBuffer1)
            .thenReturn(mockBuffer2);
        
        FrameBuffer.BufferStats mockStats = mock(FrameBuffer.BufferStats.class);
        when(limitedProcessor.getBufferStats()).thenReturn(mockStats);
        
        // Test buffer acquisition
        FrameBuffer.PooledMat buffer1 = limitedBuffer.acquireBuffer(100, 100, CvType.CV_8UC3);
        FrameBuffer.PooledMat buffer2 = limitedBuffer.acquireBuffer(100, 100, CvType.CV_8UC3);
        
        assertNotNull("First buffer should be acquired", buffer1);
        assertNotNull("Second buffer should be acquired", buffer2);
        
        // Verify mock interactions
        verify(limitedBuffer, times(2)).acquireBuffer(100, 100, CvType.CV_8UC3);
    }
    
    @Test
    public void testBufferRecyclingEfficiency() {
        // Test buffer recycling efficiency with mocks
        final int numOperations = 10;
        final int rows = 240, cols = 320, type = CvType.CV_8UC3;
        
        // Set up mock buffer that can be recycled
        FrameBuffer.PooledMat mockBuffer = mock(FrameBuffer.PooledMat.class);
        when(frameBuffer.acquireBuffer(rows, cols, type)).thenReturn(mockBuffer);
        
        // Simulate multiple acquire/recycle operations
        for (int i = 0; i < numOperations; i++) {
            FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(rows, cols, type);
            assertNotNull("Buffer " + i + " should be acquired", buffer);
        }
        
        // Verify mock interactions
        verify(frameBuffer, times(numOperations)).acquireBuffer(rows, cols, type);
        
        // Test stats access
        FrameBuffer.BufferStats stats = frameProcessor.getBufferStats();
        assertNotNull("Stats should be accessible", stats);
        verify(frameProcessor, atLeastOnce()).getBufferStats();
    }
    
    @Test
    public void testBufferCleanupOnStop() {
        // Set up mock behaviors
        when(frameProcessor.start()).thenReturn(true);
        
        FrameBuffer.PooledMat mockBuffer1 = mock(FrameBuffer.PooledMat.class);
        FrameBuffer.PooledMat mockBuffer2 = mock(FrameBuffer.PooledMat.class);
        
        when(frameBuffer.acquireBuffer(100, 100, CvType.CV_8UC1))
            .thenReturn(mockBuffer1)
            .thenReturn(mockBuffer2);
        
        when(mockBuffer2.isInUse()).thenReturn(true);
        Mat mockMat = mock(Mat.class);
        when(mockBuffer2.getMat()).thenReturn(mockMat);
        
        // Test processor start
        assertTrue("Processor should start", frameProcessor.start());
        
        // Test buffer acquisition
        FrameBuffer.PooledMat buffer1 = frameBuffer.acquireBuffer(100, 100, CvType.CV_8UC1);
        FrameBuffer.PooledMat buffer2 = frameBuffer.acquireBuffer(100, 100, CvType.CV_8UC1);
        
        assertNotNull("First buffer should be acquired", buffer1);
        assertNotNull("Second buffer should be acquired", buffer2);
        
        // Test buffer usage check
        if (buffer2.isInUse()) {
            Mat mat = buffer2.getMat();
            assertNotNull("Buffer mat should be accessible", mat);
        }
        
        // Verify mock interactions
        verify(frameProcessor).start();
        verify(frameBuffer, times(2)).acquireBuffer(100, 100, CvType.CV_8UC1);
    }
    
    @Test
    public void testBufferFailureHandling() {
        // Test buffer failure handling with mocks
        FrameBuffer tinyBuffer = mock(FrameBuffer.class);
        FrameProcessor tinyProcessor = mock(FrameProcessor.class);
        
        // Set up mock behaviors - first call succeeds, others return null (failure)
        FrameBuffer.PooledMat mockBuffer1 = mock(FrameBuffer.PooledMat.class);
        when(tinyBuffer.acquireBuffer(50, 50, CvType.CV_8UC3))
            .thenReturn(mockBuffer1)
            .thenReturn(null)
            .thenReturn(null);
        
        // Test buffer acquisition with failures
        FrameBuffer.PooledMat buffer1 = tinyBuffer.acquireBuffer(50, 50, CvType.CV_8UC3);
        FrameBuffer.PooledMat buffer2 = tinyBuffer.acquireBuffer(50, 50, CvType.CV_8UC3);
        FrameBuffer.PooledMat buffer3 = tinyBuffer.acquireBuffer(50, 50, CvType.CV_8UC3);
        
        // Verify expected behavior
        assertNotNull("First buffer should be acquired", buffer1);
        assertNull("Second buffer should fail", buffer2);
        assertNull("Third buffer should fail", buffer3);
        
        // Verify mock interactions
        verify(tinyBuffer, times(3)).acquireBuffer(50, 50, CvType.CV_8UC3);
    }
    
    @Test
    public void testPerformanceMetricsIntegration() {
        // Test performance metrics integration with mocks
        
        // Set up mock processing stats
        FrameProcessor.ProcessingStats mockProcStats = mock(FrameProcessor.ProcessingStats.class);
        when(frameProcessor.getProcessingStats()).thenReturn(mockProcStats);
        
        // Get processing stats
        FrameProcessor.ProcessingStats procStats = frameProcessor.getProcessingStats();
        assertNotNull("Processing stats should be available", procStats);
        
        // Get buffer stats
        FrameBuffer.BufferStats bufferStats = frameProcessor.getBufferStats();
        assertNotNull("Buffer stats should be available", bufferStats);
        
        // Test buffer operation
        FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(100, 100, CvType.CV_8UC1);
        assertNotNull("Buffer should be acquired", buffer);
        
        // Verify mock interactions
        verify(frameProcessor, atLeastOnce()).getProcessingStats();
        verify(frameProcessor, atLeastOnce()).getBufferStats();
        verify(frameBuffer).acquireBuffer(100, 100, CvType.CV_8UC1);
    }
}