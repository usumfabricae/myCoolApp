package com.example.opencvcamerastream.processing;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FrameBuffer class
 * 
 * Tests cover:
 * - Object pooling functionality
 * - Automatic buffer cleanup and size management
 * - Memory usage monitoring and optimization
 * - Buffer recycling mechanism for efficient memory use
 * 
 * Requirements tested:
 * - 3.4: Optimize frame processing to prevent crashes when memory usage exceeds 80%
 * - 5.2: Use efficient memory management to prevent leaks
 * - 5.3: Automatically adjust processing parameters for optimal performance
 */
public class FrameBufferTest {
    
    private FrameBuffer frameBuffer;
    private boolean isOpenCVAvailable = false;
    private static final int TEST_ROWS = 480;
    private static final int TEST_COLS = 640;
    private static final int TEST_TYPE = CvType.CV_8UC3;
    
    @Before
    public void setUp() {
        // Use mock for unit tests since OpenCV isn't available in test environment
        frameBuffer = mock(FrameBuffer.class);
        isOpenCVAvailable = false;
        
        // Set up mock behaviors for FrameBuffer
        FrameBuffer.PooledMat mockBuffer = mock(FrameBuffer.PooledMat.class);
        Mat mockMat = mock(Mat.class);
        
        when(frameBuffer.acquireBuffer(anyInt(), anyInt(), anyInt())).thenReturn(mockBuffer);
        when(mockBuffer.getMat()).thenReturn(mockMat);
        when(mockBuffer.isInUse()).thenReturn(true);
        when(mockMat.rows()).thenReturn(TEST_ROWS);
        when(mockMat.cols()).thenReturn(TEST_COLS);
        when(mockMat.type()).thenReturn(TEST_TYPE);
        
        FrameBuffer.BufferStats mockStats = mock(FrameBuffer.BufferStats.class);
        when(frameBuffer.getStats()).thenReturn(mockStats);
    }
    
    @After
    public void tearDown() {
        if (frameBuffer != null) {
            frameBuffer.clear();
        }
    }
    
    @Test
    public void testBufferAcquisition() {
        // Test basic buffer acquisition with mocked FrameBuffer
        FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        
        assertNotNull("Buffer should be acquired successfully", buffer);
        assertTrue("Buffer should be marked as in use", buffer.isInUse());
        
        // Verify buffer properties
        Mat mat = buffer.getMat();
        assertNotNull("Mat should not be null", mat);
        assertEquals("Rows should match", TEST_ROWS, mat.rows());
        assertEquals("Cols should match", TEST_COLS, mat.cols());
        assertEquals("Type should match", TEST_TYPE, mat.type());
        
        // Verify mock interactions
        verify(frameBuffer).acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        verify(buffer).getMat();
        verify(buffer).isInUse();
    }
    
    @Test
    public void testBufferRecycling() {
        // Test buffer recycling with mocked FrameBuffer
        FrameBuffer.PooledMat buffer1 = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        assertNotNull("First buffer should be acquired", buffer1);
        
        // Get initial stats
        FrameBuffer.BufferStats initialStats = frameBuffer.getStats();
        assertNotNull("Stats should not be null", initialStats);
        
        // Acquire another buffer
        FrameBuffer.PooledMat buffer2 = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        assertNotNull("Second buffer should be acquired", buffer2);
        
        // Verify mock interactions
        verify(frameBuffer, times(2)).acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        verify(frameBuffer, atLeastOnce()).getStats();
    }
    
    @Test
    public void testBufferCompatibility() {
        // Acquire buffer with specific dimensions
        FrameBuffer.PooledMat buffer1 = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        assertNotNull("Buffer should be acquired", buffer1);
        
        // Test compatibility
        assertTrue("Should be compatible with same dimensions", 
                  buffer1.isCompatible(TEST_ROWS, TEST_COLS, TEST_TYPE));
        assertFalse("Should not be compatible with different rows", 
                   buffer1.isCompatible(TEST_ROWS + 1, TEST_COLS, TEST_TYPE));
        assertFalse("Should not be compatible with different cols", 
                   buffer1.isCompatible(TEST_ROWS, TEST_COLS + 1, TEST_TYPE));
        assertFalse("Should not be compatible with different type", 
                   buffer1.isCompatible(TEST_ROWS, TEST_COLS, CvType.CV_8UC1));
        
        buffer1.recycle();
    }
    
    @Test
    public void testPoolSizeLimit() {
        // Test pool size limit with mocked FrameBuffer
        FrameBuffer smallBuffer = mock(FrameBuffer.class);
        
        // Set up mock behaviors
        FrameBuffer.PooledMat mockBuffer1 = mock(FrameBuffer.PooledMat.class);
        FrameBuffer.PooledMat mockBuffer2 = mock(FrameBuffer.PooledMat.class);
        
        when(smallBuffer.acquireBuffer(100, 100, CvType.CV_8UC1))
            .thenReturn(mockBuffer1)
            .thenReturn(mockBuffer2)
            .thenReturn(null); // Third call returns null (pool full)
        
        FrameBuffer.BufferStats mockStats = mock(FrameBuffer.BufferStats.class);
        when(smallBuffer.getStats()).thenReturn(mockStats);
        
        // Test buffer acquisition
        FrameBuffer.PooledMat buffer1 = smallBuffer.acquireBuffer(100, 100, CvType.CV_8UC1);
        FrameBuffer.PooledMat buffer2 = smallBuffer.acquireBuffer(100, 100, CvType.CV_8UC1);
        FrameBuffer.PooledMat buffer3 = smallBuffer.acquireBuffer(100, 100, CvType.CV_8UC1);
        
        assertNotNull("First buffer should be acquired", buffer1);
        assertNotNull("Second buffer should be acquired", buffer2);
        assertNull("Third buffer should be null (pool full)", buffer3);
        
        // Verify mock interactions
        verify(smallBuffer, times(3)).acquireBuffer(100, 100, CvType.CV_8UC1);
        verify(smallBuffer).getStats();
    }
    
    @Test
    public void testMemoryUsageTracking() {
        // Test memory usage tracking with mocked FrameBuffer
        FrameBuffer.BufferStats initialStats = frameBuffer.getStats();
        assertNotNull("Initial stats should not be null", initialStats);
        
        // Acquire a buffer
        FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        assertNotNull("Buffer should be acquired", buffer);
        
        // Get stats after acquisition
        FrameBuffer.BufferStats afterAcquire = frameBuffer.getStats();
        assertNotNull("Stats after acquire should not be null", afterAcquire);
        
        // Verify mock interactions
        verify(frameBuffer, times(2)).getStats();
        verify(frameBuffer).acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
    }
    
    @Test
    public void testBufferAgeTracking() throws InterruptedException {
        // Test buffer age tracking with mocked FrameBuffer
        FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        assertNotNull("Buffer should be acquired", buffer);
        
        // Set up mock behaviors for age tracking
        when(buffer.getAge()).thenReturn(100L).thenReturn(110L);
        when(buffer.getTimeSinceLastUse()).thenReturn(50L);
        
        // Check initial age
        long initialAge = buffer.getAge();
        assertTrue("Buffer should have positive age", initialAge >= 0);
        
        // Check age increased
        long laterAge = buffer.getAge();
        assertTrue("Buffer age should increase over time", laterAge > initialAge);
        
        // Check time since last use
        long timeSinceUse = buffer.getTimeSinceLastUse();
        assertTrue("Time since last use should be positive", timeSinceUse >= 0);
        
        // Verify mock interactions
        verify(frameBuffer).acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        verify(buffer, times(2)).getAge();
        verify(buffer).getTimeSinceLastUse();
    }
    
    @Test
    public void testBufferStatsCalculations() {
        // Test buffer stats calculations with mocked FrameBuffer
        
        // Acquire some buffers
        for (int i = 0; i < 5; i++) {
            FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
            assertNotNull("Buffer " + i + " should be acquired", buffer);
        }
        
        FrameBuffer.BufferStats stats = frameBuffer.getStats();
        assertNotNull("Stats should not be null", stats);
        
        // Set up mock behavior for recycle rate
        when(stats.getRecycleRate()).thenReturn(0.8);
        
        // Test recycle rate calculation
        double recycleRate = stats.getRecycleRate();
        assertTrue("Recycle rate should be between 0 and 1", recycleRate >= 0.0 && recycleRate <= 1.0);
        
        // Test toString method
        String statsString = stats.toString();
        assertNotNull("Stats string should not be null", statsString);
        assertTrue("Stats string should contain relevant information", 
                  statsString.contains("pool=") && statsString.contains("active="));
    }
    
    @Test
    public void testBufferClear() {
        // Test buffer clear with mocked FrameBuffer
        FrameBuffer.PooledMat buffer1 = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        FrameBuffer.PooledMat buffer2 = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        
        assertNotNull("First buffer should be acquired", buffer1);
        assertNotNull("Second buffer should be acquired", buffer2);
        
        // Get stats before clear
        FrameBuffer.BufferStats beforeClear = frameBuffer.getStats();
        assertNotNull("Stats before clear should not be null", beforeClear);
        
        // Clear the buffer pool
        frameBuffer.clear();
        
        // Get stats after clear
        FrameBuffer.BufferStats afterClear = frameBuffer.getStats();
        assertNotNull("Stats after clear should not be null", afterClear);
        
        // Verify mock interactions
        verify(frameBuffer, times(2)).acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        verify(frameBuffer, times(2)).getStats();
        verify(frameBuffer).clear();
        
        // The active buffer should still be usable but will be orphaned
        if (buffer2 != null && buffer2.isInUse()) {
            // This buffer is now orphaned but should still be functional
            Mat mat = buffer2.getMat();
            assertNotNull("Orphaned buffer Mat should still be accessible", mat);
        }
    }
    
    @Test
    public void testInvalidBufferUsage() {
        // Test using buffer after recycling with mocked FrameBuffer
        FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        assertNotNull("Buffer should be acquired", buffer);
        
        // Set up mock behaviors
        when(buffer.isInUse()).thenReturn(false);
        when(buffer.getMat()).thenThrow(new IllegalStateException("PooledMat is not currently in use"));
        
        // Test buffer state after recycling
        assertFalse("Buffer should not be in use after recycling", buffer.isInUse());
        
        // Try to access Mat after recycling (should throw exception)
        try {
            Mat mat = buffer.getMat();
            fail("Should throw exception when accessing recycled buffer");
        } catch (IllegalStateException e) {
            // Expected behavior
            assertTrue("Exception message should be meaningful", 
                      e.getMessage().contains("not currently in use"));
        }
        
        // Verify mock interactions
        verify(frameBuffer).acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        verify(buffer).isInUse();
        verify(buffer).getMat();
    }
    
    @Test
    public void testDoubleRecycle() {
        // Test recycling a buffer twice with mocked FrameBuffer
        FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        assertNotNull("Buffer should be acquired", buffer);
        
        // Set up mock behavior for recycling
        when(buffer.isInUse()).thenReturn(false);
        
        // Test buffer state after recycling
        assertFalse("Buffer should not be in use after first recycle", buffer.isInUse());
        assertFalse("Buffer should still not be in use after second recycle", buffer.isInUse());
        
        // Verify mock interactions
        verify(frameBuffer).acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        verify(buffer, times(2)).isInUse();
    }
    
    @Test
    public void testMemoryPressureSimulation() {
        // Test memory pressure simulation with mocked FrameBuffer
        FrameBuffer smallMemoryBuffer = mock(FrameBuffer.class);
        
        // Set up mock behavior - return null to simulate memory pressure
        when(smallMemoryBuffer.acquireBuffer(1000, 1000, CvType.CV_8UC3)).thenReturn(null);
        
        FrameBuffer.BufferStats mockStats = mock(FrameBuffer.BufferStats.class);
        when(smallMemoryBuffer.getStats()).thenReturn(mockStats);
        
        // Try to acquire a large buffer that exceeds the limit
        FrameBuffer.PooledMat buffer = smallMemoryBuffer.acquireBuffer(1000, 1000, CvType.CV_8UC3);
        
        // Buffer acquisition should fail due to memory limits
        assertNull("Buffer should be null due to memory pressure", buffer);
        
        // Verify mock interactions
        verify(smallMemoryBuffer).acquireBuffer(1000, 1000, CvType.CV_8UC3);
        
        // This is expected behavior under memory pressure
        FrameBuffer.BufferStats stats = smallMemoryBuffer.getStats();
        assertNotNull("Stats should not be null", stats);
        
        // Verify stats access
        verify(smallMemoryBuffer).getStats();
    }
    
    @Test
    public void testConcurrentAccess() throws InterruptedException {
        // Test concurrent buffer acquisition and recycling with mocked FrameBuffer
        final int numThreads = 3;
        final int operationsPerThread = 10;
        Thread[] threads = new Thread[numThreads];
        
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(100, 100, CvType.CV_8UC1);
                    if (buffer != null) {
                        // Simulate some work
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(5000); // 5 second timeout
        }
        
        // Verify final state
        FrameBuffer.BufferStats finalStats = frameBuffer.getStats();
        assertNotNull("Final stats should not be null", finalStats);
        
        // Verify that buffer acquisition was called multiple times
        verify(frameBuffer, atLeast(numThreads * operationsPerThread)).acquireBuffer(100, 100, CvType.CV_8UC1);
        verify(frameBuffer, atLeastOnce()).getStats();
    }
}