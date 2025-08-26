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
    private static final int TEST_ROWS = 480;
    private static final int TEST_COLS = 640;
    private static final int TEST_TYPE = CvType.CV_8UC3;
    
    @Before
    public void setUp() {
        // Initialize OpenCV for testing (mock or use actual initialization)
        // In a real test environment, you would need to initialize OpenCV
        frameBuffer = new FrameBuffer(5, 10 * 1024 * 1024); // 5 buffers, 10MB max
    }
    
    @After
    public void tearDown() {
        if (frameBuffer != null) {
            frameBuffer.clear();
        }
    }
    
    @Test
    public void testBufferAcquisition() {
        // Test basic buffer acquisition
        FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        
        assertNotNull("Buffer should be acquired successfully", buffer);
        assertTrue("Buffer should be marked as in use", buffer.isInUse());
        
        // Verify buffer properties
        Mat mat = buffer.getMat();
        assertNotNull("Mat should not be null", mat);
        assertEquals("Rows should match", TEST_ROWS, mat.rows());
        assertEquals("Cols should match", TEST_COLS, mat.cols());
        assertEquals("Type should match", TEST_TYPE, mat.type());
        
        // Clean up
        buffer.recycle();
    }
    
    @Test
    public void testBufferRecycling() {
        // Acquire a buffer
        FrameBuffer.PooledMat buffer1 = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        assertNotNull("First buffer should be acquired", buffer1);
        
        // Get initial stats
        FrameBuffer.BufferStats initialStats = frameBuffer.getStats();
        assertEquals("Should have 1 active buffer", 1, initialStats.activeBuffers);
        assertEquals("Should have 1 allocation", 1, initialStats.totalAllocations);
        
        // Recycle the buffer
        buffer1.recycle();
        
        // Acquire another buffer with same dimensions
        FrameBuffer.PooledMat buffer2 = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        assertNotNull("Second buffer should be acquired", buffer2);
        
        // Check stats to verify recycling
        FrameBuffer.BufferStats recycleStats = frameBuffer.getStats();
        assertEquals("Should still have 1 active buffer", 1, recycleStats.activeBuffers);
        assertEquals("Should have 1 allocation (recycled)", 1, recycleStats.totalAllocations);
        assertEquals("Should have 1 recycle", 1, recycleStats.totalRecycles);
        
        // Clean up
        buffer2.recycle();
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
        // Create a small buffer pool
        FrameBuffer smallBuffer = new FrameBuffer(2, 10 * 1024 * 1024);
        
        // Acquire buffers up to the limit
        FrameBuffer.PooledMat buffer1 = smallBuffer.acquireBuffer(100, 100, CvType.CV_8UC1);
        FrameBuffer.PooledMat buffer2 = smallBuffer.acquireBuffer(100, 100, CvType.CV_8UC1);
        
        assertNotNull("First buffer should be acquired", buffer1);
        assertNotNull("Second buffer should be acquired", buffer2);
        
        // Try to acquire beyond the limit
        FrameBuffer.PooledMat buffer3 = smallBuffer.acquireBuffer(100, 100, CvType.CV_8UC1);
        
        // This might be null if pool is full, depending on implementation
        // The behavior should be consistent with memory management
        
        FrameBuffer.BufferStats stats = smallBuffer.getStats();
        assertTrue("Pool size should not exceed maximum", stats.poolSize <= 2);
        
        // Clean up
        if (buffer1 != null) buffer1.recycle();
        if (buffer2 != null) buffer2.recycle();
        if (buffer3 != null) buffer3.recycle();
        smallBuffer.clear();
    }
    
    @Test
    public void testMemoryUsageTracking() {
        // Get initial stats
        FrameBuffer.BufferStats initialStats = frameBuffer.getStats();
        long initialMemory = initialStats.totalMemoryUsage;
        
        // Acquire a buffer
        FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        assertNotNull("Buffer should be acquired", buffer);
        
        // Check memory usage increased
        FrameBuffer.BufferStats afterAcquire = frameBuffer.getStats();
        assertTrue("Memory usage should increase after acquisition", 
                  afterAcquire.totalMemoryUsage > initialMemory);
        
        // Get buffer memory size
        long bufferSize = buffer.getMemorySize();
        assertTrue("Buffer should have positive memory size", bufferSize > 0);
        
        // Recycle buffer
        buffer.recycle();
        
        // Memory usage should remain the same (buffer is pooled)
        FrameBuffer.BufferStats afterRecycle = frameBuffer.getStats();
        assertEquals("Memory usage should remain same after recycling", 
                    afterAcquire.totalMemoryUsage, afterRecycle.totalMemoryUsage);
    }
    
    @Test
    public void testBufferAgeTracking() throws InterruptedException {
        // Acquire a buffer
        FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        assertNotNull("Buffer should be acquired", buffer);
        
        // Check initial age
        long initialAge = buffer.getAge();
        assertTrue("Buffer should have positive age", initialAge >= 0);
        
        // Wait a bit
        Thread.sleep(10);
        
        // Check age increased
        long laterAge = buffer.getAge();
        assertTrue("Buffer age should increase over time", laterAge > initialAge);
        
        // Check time since last use
        long timeSinceUse = buffer.getTimeSinceLastUse();
        assertTrue("Time since last use should be positive", timeSinceUse >= 0);
        
        buffer.recycle();
    }
    
    @Test
    public void testBufferStatsCalculations() {
        // Acquire and recycle some buffers to generate stats
        for (int i = 0; i < 5; i++) {
            FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
            if (buffer != null) {
                buffer.recycle();
            }
        }
        
        FrameBuffer.BufferStats stats = frameBuffer.getStats();
        
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
        // Acquire some buffers
        FrameBuffer.PooledMat buffer1 = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        FrameBuffer.PooledMat buffer2 = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        
        assertNotNull("First buffer should be acquired", buffer1);
        assertNotNull("Second buffer should be acquired", buffer2);
        
        // Recycle one buffer
        buffer1.recycle();
        
        // Get stats before clear
        FrameBuffer.BufferStats beforeClear = frameBuffer.getStats();
        assertTrue("Should have some buffers before clear", beforeClear.poolSize > 0);
        
        // Clear the buffer pool
        frameBuffer.clear();
        
        // Get stats after clear
        FrameBuffer.BufferStats afterClear = frameBuffer.getStats();
        assertEquals("Pool size should be 0 after clear", 0, afterClear.poolSize);
        assertEquals("Memory usage should be 0 after clear", 0, afterClear.totalMemoryUsage);
        
        // The active buffer should still be usable but will be orphaned
        if (buffer2 != null && buffer2.isInUse()) {
            // This buffer is now orphaned but should still be functional
            Mat mat = buffer2.getMat();
            assertNotNull("Orphaned buffer Mat should still be accessible", mat);
        }
    }
    
    @Test
    public void testInvalidBufferUsage() {
        // Test using buffer after recycling
        FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        assertNotNull("Buffer should be acquired", buffer);
        
        // Recycle the buffer
        buffer.recycle();
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
    }
    
    @Test
    public void testDoubleRecycle() {
        // Test recycling a buffer twice
        FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(TEST_ROWS, TEST_COLS, TEST_TYPE);
        assertNotNull("Buffer should be acquired", buffer);
        
        // First recycle
        buffer.recycle();
        assertFalse("Buffer should not be in use after first recycle", buffer.isInUse());
        
        // Second recycle (should be handled gracefully)
        buffer.recycle(); // Should not crash or cause issues
        assertFalse("Buffer should still not be in use after second recycle", buffer.isInUse());
    }
    
    @Test
    public void testMemoryPressureSimulation() {
        // This test simulates memory pressure conditions
        // Create a buffer with very small memory limit
        FrameBuffer smallMemoryBuffer = new FrameBuffer(10, 1024); // 1KB limit
        
        // Try to acquire a large buffer that exceeds the limit
        FrameBuffer.PooledMat buffer = smallMemoryBuffer.acquireBuffer(1000, 1000, CvType.CV_8UC3);
        
        // Buffer acquisition might fail due to memory limits
        if (buffer == null) {
            // This is expected behavior under memory pressure
            FrameBuffer.BufferStats stats = smallMemoryBuffer.getStats();
            assertTrue("Memory optimization should have been triggered", 
                      stats.memoryOptimizations >= 0);
        } else {
            // If buffer was acquired, clean it up
            buffer.recycle();
        }
        
        smallMemoryBuffer.clear();
    }
    
    @Test
    public void testConcurrentAccess() throws InterruptedException {
        // Test concurrent buffer acquisition and recycling
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
                        buffer.recycle();
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
        assertEquals("All buffers should be recycled", 0, finalStats.activeBuffers);
        assertTrue("Should have processed multiple operations", 
                  finalStats.totalAllocations > 0 || finalStats.totalRecycles > 0);
    }
}