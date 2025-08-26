package com.example.opencvcamerastream.processing;

import android.util.Log;
import androidx.annotation.NonNull;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * Demonstration class showing proper usage of FrameBuffer with memory optimization
 * 
 * This class provides examples of:
 * - Proper buffer acquisition and recycling
 * - Memory monitoring integration
 * - Performance optimization techniques
 * - Error handling for memory pressure situations
 * 
 * Requirements demonstrated:
 * - 3.4: Memory optimization to prevent crashes
 * - 5.2: Efficient memory management
 * - 5.3: Automatic performance adjustment
 */
public class FrameBufferDemo {
    
    private static final String TAG = "FrameBufferDemo";
    
    /**
     * Demonstrate basic buffer usage
     */
    public static void demonstrateBasicUsage() {
        Log.d(TAG, "=== Basic FrameBuffer Usage Demo ===");
        
        // Create a frame buffer
        FrameBuffer frameBuffer = new FrameBuffer();
        
        try {
            // Log initial memory status
            MemoryMonitoringUtils.logMemoryStatus(TAG);
            
            // Acquire a buffer
            FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(480, 640, CvType.CV_8UC3);
            
            if (buffer != null) {
                Log.d(TAG, "Buffer acquired successfully");
                
                // Use the buffer
                Mat mat = buffer.getMat();
                Log.d(TAG, "Mat dimensions: " + mat.rows() + "x" + mat.cols() + 
                           ", type: " + mat.type() + ", size: " + 
                           (buffer.getMemorySize() / 1024) + "KB");
                
                // Simulate some processing
                mat.setTo(org.opencv.core.Scalar.all(128)); // Fill with gray
                
                // Always recycle when done
                buffer.recycle();
                Log.d(TAG, "Buffer recycled");
                
            } else {
                Log.w(TAG, "Failed to acquire buffer - memory pressure or pool full");
            }
            
            // Log buffer statistics
            frameBuffer.logStatus();
            
        } finally {
            // Always clean up
            frameBuffer.clear();
            Log.d(TAG, "FrameBuffer cleared");
        }
    }
    
    /**
     * Demonstrate buffer recycling efficiency
     */
    public static void demonstrateRecycling() {
        Log.d(TAG, "=== Buffer Recycling Demo ===");
        
        FrameBuffer frameBuffer = new FrameBuffer();
        
        try {
            final int numFrames = 10;
            final int rows = 240, cols = 320, type = CvType.CV_8UC3;
            
            Log.d(TAG, "Processing " + numFrames + " frames with recycling");
            
            for (int i = 0; i < numFrames; i++) {
                FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(rows, cols, type);
                
                if (buffer != null) {
                    // Simulate frame processing
                    Mat mat = buffer.getMat();
                    mat.setTo(org.opencv.core.Scalar.all(i * 25)); // Different values
                    
                    Log.v(TAG, "Processed frame " + i + ", buffer age: " + buffer.getAge() + "ms");
                    
                    // Recycle immediately
                    buffer.recycle();
                } else {
                    Log.w(TAG, "Failed to acquire buffer for frame " + i);
                }
            }
            
            // Show recycling efficiency
            FrameBuffer.BufferStats stats = frameBuffer.getStats();
            Log.i(TAG, "Recycling efficiency: " + String.format("%.1f%%", stats.getRecycleRate() * 100) +
                       " (allocations: " + stats.totalAllocations + 
                       ", recycles: " + stats.totalRecycles + ")");
            
        } finally {
            frameBuffer.clear();
        }
    }
    
    /**
     * Demonstrate memory pressure handling
     */
    public static void demonstrateMemoryPressure() {
        Log.d(TAG, "=== Memory Pressure Handling Demo ===");
        
        // Create a buffer with limited memory to simulate pressure
        FrameBuffer limitedBuffer = new FrameBuffer(3, 2 * 1024 * 1024); // 2MB limit
        
        try {
            Log.d(TAG, "Creating buffers until memory pressure...");
            
            int bufferCount = 0;
            FrameBuffer.PooledMat[] buffers = new FrameBuffer.PooledMat[10];
            
            // Try to acquire many large buffers
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = limitedBuffer.acquireBuffer(500, 500, CvType.CV_8UC3);
                
                if (buffers[i] != null) {
                    bufferCount++;
                    Log.d(TAG, "Acquired buffer " + i + ", memory usage: " + 
                           (limitedBuffer.getStats().totalMemoryUsage / (1024 * 1024)) + "MB");
                } else {
                    Log.w(TAG, "Failed to acquire buffer " + i + " - memory pressure detected");
                    break;
                }
                
                // Log memory status
                MemoryMonitoringUtils.logMemoryStatus(TAG);
            }
            
            Log.i(TAG, "Successfully acquired " + bufferCount + " buffers before hitting limits");
            
            // Show optimization recommendations
            String[] recommendations = MemoryMonitoringUtils.getOptimizationRecommendations();
            Log.d(TAG, "Memory optimization recommendations:");
            for (String recommendation : recommendations) {
                Log.d(TAG, "  - " + recommendation);
            }
            
            // Clean up acquired buffers
            for (int i = 0; i < bufferCount; i++) {
                if (buffers[i] != null) {
                    buffers[i].recycle();
                }
            }
            
            // Show final stats
            limitedBuffer.logStatus();
            
        } finally {
            limitedBuffer.clear();
        }
    }
    
    /**
     * Demonstrate proper error handling
     */
    public static void demonstrateErrorHandling() {
        Log.d(TAG, "=== Error Handling Demo ===");
        
        FrameBuffer frameBuffer = new FrameBuffer();
        
        try {
            // Acquire a buffer
            FrameBuffer.PooledMat buffer = frameBuffer.acquireBuffer(100, 100, CvType.CV_8UC1);
            
            if (buffer != null) {
                Log.d(TAG, "Buffer acquired for error handling demo");
                
                // Use the buffer normally
                Mat mat = buffer.getMat();
                mat.setTo(org.opencv.core.Scalar.all(255));
                
                // Recycle the buffer
                buffer.recycle();
                Log.d(TAG, "Buffer recycled normally");
                
                // Try to use the buffer after recycling (should fail gracefully)
                try {
                    Mat invalidMat = buffer.getMat(); // Should throw exception
                    Log.e(TAG, "ERROR: Should not be able to access recycled buffer!");
                } catch (IllegalStateException e) {
                    Log.d(TAG, "Correctly caught exception when accessing recycled buffer: " + 
                           e.getMessage());
                }
                
                // Try to recycle again (should be handled gracefully)
                buffer.recycle(); // Should not crash
                Log.d(TAG, "Double recycle handled gracefully");
                
            } else {
                Log.w(TAG, "Could not acquire buffer for error handling demo");
            }
            
        } finally {
            frameBuffer.clear();
        }
    }
    
    /**
     * Run all demonstrations
     */
    public static void runAllDemos() {
        Log.i(TAG, "Starting FrameBuffer demonstrations...");
        
        try {
            demonstrateBasicUsage();
            Thread.sleep(100);
            
            demonstrateRecycling();
            Thread.sleep(100);
            
            demonstrateMemoryPressure();
            Thread.sleep(100);
            
            demonstrateErrorHandling();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Demo interrupted");
        }
        
        Log.i(TAG, "FrameBuffer demonstrations completed");
        
        // Final memory status
        MemoryMonitoringUtils.logMemoryStatus(TAG);
    }
}