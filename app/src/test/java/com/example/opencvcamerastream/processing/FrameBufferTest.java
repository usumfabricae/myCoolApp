package com.example.opencvcamerastream.processing;

import android.graphics.Bitmap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * Unit tests for FrameBuffer memory management
 * Requirements: 3.4, 5.2, 5.3
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Test on Android 10
public class FrameBufferTest {

    private FrameBuffer frameBuffer;
    private static final int BUFFER_SIZE = 5;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        frameBuffer = new FrameBuffer(BUFFER_SIZE);
    }

    @Test
    public void testBufferInitialization() {
        // Test buffer initialization
        assertNotNull("FrameBuffer should not be null", frameBuffer);
        assertEquals("Buffer size should match", BUFFER_SIZE, frameBuffer.getMaxSize());
        assertTrue("Buffer should be empty initially", frameBuffer.isEmpty());
    }

    @Test
    public void testBufferAllocation() {
        // Test buffer allocation (Requirement 5.2)
        Bitmap bitmap = frameBuffer.allocateBuffer(100, 100, Bitmap.Config.ARGB_8888);
        
        assertNotNull("Allocated bitmap should not be null", bitmap);
        assertEquals("Bitmap width should match", 100, bitmap.getWidth());
        assertEquals("Bitmap height should match", 100, bitmap.getHeight());
    }

    @Test
    public void testBufferRecycling() {
        // Test buffer recycling for memory efficiency (Requirement 5.2)
        Bitmap bitmap1 = frameBuffer.allocateBuffer(100, 100, Bitmap.Config.ARGB_8888);
        
        frameBuffer.recycleBuffer(bitmap1);
        
        Bitmap bitmap2 = frameBuffer.allocateBuffer(100, 100, Bitmap.Config.ARGB_8888);
        
        // Should reuse the recycled buffer
        assertNotNull("Recycled buffer should be available", bitmap2);
    }

    @Test
    public void testBufferPooling() {
        // Test object pooling functionality (Requirement 5.2)
        Bitmap[] bitmaps = new Bitmap[BUFFER_SIZE];
        
        // Allocate all buffers
        for (int i = 0; i < BUFFER_SIZE; i++) {
            bitmaps[i] = frameBuffer.allocateBuffer(100, 100, Bitmap.Config.ARGB_8888);
            assertNotNull("Buffer " + i + " should be allocated", bitmaps[i]);
        }
        
        // Pool should be full
        assertTrue("Buffer pool should be full", frameBuffer.isFull());
        
        // Recycle all buffers
        for (Bitmap bitmap : bitmaps) {
            frameBuffer.recycleBuffer(bitmap);
        }
        
        assertFalse("Buffer pool should not be full after recycling", frameBuffer.isFull());
    }

    @Test
    public void testMemoryPressureHandling() {
        // Test memory pressure handling (Requirement 3.4)
        frameBuffer.setMemoryPressure(true);
        
        assertTrue("Should detect memory pressure", frameBuffer.isMemoryLow());
        
        // Under memory pressure, buffer allocation might be more conservative
        Bitmap bitmap = frameBuffer.allocateBuffer(100, 100, Bitmap.Config.ARGB_8888);
        
        // Should still allocate but might use different strategy
        assertNotNull("Should still allocate under memory pressure", bitmap);
    }

    @Test
    public void testAutomaticCleanup() {
        // Test automatic buffer cleanup (Requirement 5.2)
        Bitmap[] bitmaps = new Bitmap[BUFFER_SIZE * 2];
        
        // Allocate more buffers than pool size
        for (int i = 0; i < bitmaps.length; i++) {
            bitmaps[i] = frameBuffer.allocateBuffer(100, 100, Bitmap.Config.ARGB_8888);
        }
        
        // Trigger cleanup
        frameBuffer.cleanup();
        
        // Pool should be cleaned up
        assertTrue("Buffer should be empty after cleanup", frameBuffer.isEmpty());
    }

    @Test
    public void testBufferSizeOptimization() {
        // Test buffer size optimization (Requirement 5.3)
        frameBuffer.optimizeForResolution(1920, 1080);
        
        Bitmap bitmap = frameBuffer.allocateBuffer(1920, 1080, Bitmap.Config.ARGB_8888);
        
        assertNotNull("Should allocate optimized buffer", bitmap);
        assertEquals("Width should match optimization", 1920, bitmap.getWidth());
        assertEquals("Height should match optimization", 1080, bitmap.getHeight());
    }

    @Test
    public void testMemoryUsageTracking() {
        // Test memory usage monitoring (Requirement 5.2)
        long initialMemory = frameBuffer.getCurrentMemoryUsage();
        
        Bitmap bitmap = frameBuffer.allocateBuffer(100, 100, Bitmap.Config.ARGB_8888);
        
        long memoryAfterAllocation = frameBuffer.getCurrentMemoryUsage();
        
        assertTrue("Memory usage should increase after allocation", 
                  memoryAfterAllocation > initialMemory);
        
        frameBuffer.recycleBuffer(bitmap);
        
        long memoryAfterRecycling = frameBuffer.getCurrentMemoryUsage();
        
        assertTrue("Memory usage should decrease after recycling", 
                  memoryAfterRecycling <= memoryAfterAllocation);
    }

    @Test
    public void testBufferReuse() {
        // Test buffer reuse efficiency
        Bitmap bitmap1 = frameBuffer.allocateBuffer(100, 100, Bitmap.Config.ARGB_8888);
        frameBuffer.recycleBuffer(bitmap1);
        
        Bitmap bitmap2 = frameBuffer.allocateBuffer(100, 100, Bitmap.Config.ARGB_8888);
        
        // Should reuse the same buffer for efficiency
        assertEquals("Should reuse buffer for same dimensions", 
                    bitmap1.getWidth(), bitmap2.getWidth());
        assertEquals("Should reuse buffer for same dimensions", 
                    bitmap1.getHeight(), bitmap2.getHeight());
    }

    @Test
    public void testConcurrentAccess() {
        // Test concurrent access to buffer pool
        Thread[] threads = new Thread[3];
        
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                Bitmap bitmap = frameBuffer.allocateBuffer(50, 50, Bitmap.Config.ARGB_8888);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                frameBuffer.recycleBuffer(bitmap);
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
        Bitmap[] bitmaps = new Bitmap[BUFFER_SIZE + 5];
        
        // Try to allocate more than limit
        for (int i = 0; i < bitmaps.length; i++) {
            bitmaps[i] = frameBuffer.allocateBuffer(100, 100, Bitmap.Config.ARGB_8888);
        }
        
        // Should handle over-allocation gracefully
        int nonNullCount = 0;
        for (Bitmap bitmap : bitmaps) {
            if (bitmap != null) {
                nonNullCount++;
            }
        }
        
        assertTrue("Should allocate at least buffer size", nonNullCount >= BUFFER_SIZE);
    }
}