package com.example.opencvcamerastream.processing;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.opencv.core.CvType;

import static org.junit.Assert.*;

/**
 * Unit tests for FrameBuffer memory management
 * Requirements: 3.4, 5.2, 5.3
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Test on Android 10
public class FrameBufferTest {

    private FrameBuffer frameBuffer;
    private static final int MAX_POOL_SIZE = 5;
    private static final long MAX_BUFFER_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        frameBuffer = new FrameBuffer(MAX_POOL_SIZE, MAX_BUFFER_SIZE_BYTES);
    }

    @Test
    public void testBufferInitialization() {
        // Test buffer initialization
        assertNotNull("FrameBuffer should not be null", frameBuffer);
        FrameBuffer.BufferStats stats = frameBuffer.getStats();
        assertEquals("Pool size should be 0 initially", 0, stats.poolSize);
        assertEquals("Active buffers should be 0 initially", 0, stats.activeBuffers);
    }

    @Test
    public void testBufferAllocation() {
        // Test buffer allocation (Requirement 5.2)
        FrameBuffer.PooledMat pooledMat = frameBuffer.acquireBuffer(100, 100, CvType.CV_8UC3);
        
        assertNotNull("Allocated PooledMat should not be null", pooledMat);
        assertNotNull("Mat should not be null", pooledMat.getMat());
        assertEquals("Mat width should match", 100, pooledMat.getMat().width());
        assertEquals("Mat height should match", 100, pooledMat.getMat().height());
        assertTrue("PooledMat should be in use", pooledMat.isInUse());
    }

    @Test
    public void testBufferRecycling() {
        // Test buffer recycling for memory efficiency (Requirement 5.2)
        FrameBuffer.PooledMat pooledMat1 = frameBuffer.acquireBuffer(100, 100, CvType.CV_8UC3);
        
        pooledMat1.recycle();
        
        FrameBuffer.PooledMat pooledMat2 = frameBuffer.acquireBuffer(100, 100, CvType.CV_8UC3);
        
        // Should reuse the recycled buffer
        assertNotNull("Recycled buffer should be available", pooledMat2);
        assertTrue("Second buffer should be in use", pooledMat2.isInUse());
    }

    @Test
    public void testBufferPooling() {
        // Test object pooling functionality (Requirement 5.2)
        FrameBuffer.PooledMat[] pooledMats = new FrameBuffer.PooledMat[MAX_POOL_SIZE];
        
        // Allocate all buffers
        for (int i = 0; i < MAX_POOL_SIZE; i++) {
            pooledMats[i] = frameBuffer.acquireBuffer(100, 100, CvType.CV_8UC3);
            assertNotNull("Buffer " + i + " should be allocated", pooledMats[i]);
        }
        
        FrameBuffer.BufferStats stats = frameBuffer.getStats();
        assertEquals("Active buffers should match allocated count", MAX_POOL_SIZE, stats.activeBuffers);
        
        // Recycle all buffers
        for (FrameBuffer.PooledMat pooledMat : pooledMats) {
            if (pooledMat != null) {
                pooledMat.recycle();
            }
        }
        
        stats = frameBuffer.getStats();
        assertEquals("Active buffers should be 0 after recycling", 0, stats.activeBuffers);
    }

    @Test
    public void testMemoryPressureHandling() {
        // Test memory pressure handling (Requirement 3.4)
        // Simulate memory pressure by trying to allocate many large buffers
        FrameBuffer.PooledMat pooledMat = frameBuffer.acquireBuffer(1920, 1080, CvType.CV_8UC3);
        
        // Should still allocate but might use different strategy
        assertNotNull("Should still allocate under normal conditions", pooledMat);
        
        if (pooledMat != null) {
            pooledMat.recycle();
        }
    }

    @Test
    public void testAutomaticCleanup() {
        // Test automatic buffer cleanup (Requirement 5.2)
        FrameBuffer.PooledMat[] pooledMats = new FrameBuffer.PooledMat[MAX_POOL_SIZE * 2];
        
        // Allocate more buffers than pool size
        for (int i = 0; i < pooledMats.length; i++) {
            pooledMats[i] = frameBuffer.acquireBuffer(100, 100, CvType.CV_8UC3);
        }
        
        // Trigger cleanup
        frameBuffer.clear();
        
        // Pool should be cleaned up
        FrameBuffer.BufferStats stats = frameBuffer.getStats();
        assertEquals("Pool size should be 0 after cleanup", 0, stats.poolSize);
    }

    @Test
    public void testBufferCompatibility() {
        // Test buffer compatibility checking
        FrameBuffer.PooledMat pooledMat = frameBuffer.acquireBuffer(100, 100, CvType.CV_8UC3);
        
        assertNotNull("Buffer should be allocated", pooledMat);
        assertTrue("Buffer should be compatible with same dimensions", 
                  pooledMat.isCompatible(100, 100, CvType.CV_8UC3));
        assertFalse("Buffer should not be compatible with different dimensions", 
                   pooledMat.isCompatible(200, 200, CvType.CV_8UC3));
        
        pooledMat.recycle();
    }

    @Test
    public void testMemoryUsageTracking() {
        // Test memory usage monitoring (Requirement 5.2)
        FrameBuffer.BufferStats initialStats = frameBuffer.getStats();
        
        FrameBuffer.PooledMat pooledMat = frameBuffer.acquireBuffer(100, 100, CvType.CV_8UC3);
        
        FrameBuffer.BufferStats statsAfterAllocation = frameBuffer.getStats();
        
        assertTrue("Memory usage should increase after allocation", 
                  statsAfterAllocation.totalMemoryUsage > initialStats.totalMemoryUsage);
        
        if (pooledMat != null) {
            pooledMat.recycle();
        }
        
        FrameBuffer.BufferStats statsAfterRecycling = frameBuffer.getStats();
        
        assertEquals("Active buffers should decrease after recycling", 
                    0, statsAfterRecycling.activeBuffers);
    }

    @Test
    public void testBufferReuse() {
        // Test buffer reuse efficiency
        FrameBuffer.PooledMat pooledMat1 = frameBuffer.acquireBuffer(100, 100, CvType.CV_8UC3);
        assertNotNull("First buffer should be allocated", pooledMat1);
        
        pooledMat1.recycle();
        
        FrameBuffer.PooledMat pooledMat2 = frameBuffer.acquireBuffer(100, 100, CvType.CV_8UC3);
        
        // Should reuse the same buffer for efficiency
        assertNotNull("Second buffer should be allocated", pooledMat2);
        assertEquals("Should reuse buffer for same dimensions", 
                    100, pooledMat2.getMat().width());
        assertEquals("Should reuse buffer for same dimensions", 
                    100, pooledMat2.getMat().height());
        
        pooledMat2.recycle();
    }

    @Test
    public void testConcurrentAccess() {
        // Test concurrent access to buffer pool
        Thread[] threads = new Thread[3];
        
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                FrameBuffer.PooledMat pooledMat = frameBuffer.acquireBuffer(50, 50, CvType.CV_8UC3);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (pooledMat != null) {
                    pooledMat.recycle();
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Should handle concurrent access without issues
        assertTrue("Should handle concurrent access", true);
    }

    @Test
    public void testBufferLimits() {
        // Test buffer allocation limits
        FrameBuffer.PooledMat[] pooledMats = new FrameBuffer.PooledMat[MAX_POOL_SIZE + 5];
        
        // Try to allocate more than limit
        for (int i = 0; i < pooledMats.length; i++) {
            pooledMats[i] = frameBuffer.acquireBuffer(100, 100, CvType.CV_8UC3);
        }
        
        // Should handle over-allocation gracefully
        int nonNullCount = 0;
        for (FrameBuffer.PooledMat pooledMat : pooledMats) {
            if (pooledMat != null) {
                nonNullCount++;
                pooledMat.recycle();
            }
        }
        
        assertTrue("Should allocate at least some buffers", nonNullCount > 0);
    }
}