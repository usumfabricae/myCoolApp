package com.example.opencvcamerastream.processing;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.opencv.core.Mat;
import org.opencv.core.CvType;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * FrameBuffer provides efficient memory management for OpenCV Mat objects using object pooling
 * 
 * This class implements:
 * - Object pooling to reduce allocations and garbage collection
 * - Automatic buffer cleanup and size management
 * - Memory usage monitoring and optimization
 * - Buffer recycling mechanism for efficient memory use
 * 
 * Requirements addressed:
 * - 3.4: Optimize frame processing to prevent crashes when memory usage exceeds 80%
 * - 5.2: Use efficient memory management to prevent leaks
 * - 5.3: Automatically adjust processing parameters for optimal performance on lower-end devices
 */
public class FrameBuffer {
    
    private static final String TAG = "FrameBuffer";
    
    // Pool configuration
    private static final int DEFAULT_INITIAL_POOL_SIZE = 5;
    private static final int DEFAULT_MAX_POOL_SIZE = 15;
    private static final int DEFAULT_MAX_BUFFER_SIZE_MB = 50; // 50MB max buffer pool size
    private static final long CLEANUP_INTERVAL_MS = 30000; // 30 seconds
    private static final long MEMORY_CHECK_INTERVAL_MS = 5000; // 5 seconds
    private static final double MEMORY_PRESSURE_THRESHOLD = 0.8; // 80% memory usage
    
    // Pool management
    private final ConcurrentLinkedQueue<PooledMat> availableBuffers = new ConcurrentLinkedQueue<>();
    private final AtomicInteger poolSize = new AtomicInteger(0);
    private final AtomicInteger activeBuffers = new AtomicInteger(0);
    private final AtomicLong totalMemoryUsage = new AtomicLong(0);
    
    // Configuration
    private final int maxPoolSize;
    private final long maxBufferSizeBytes;
    private final MemoryMonitor memoryMonitor;
    
    // Performance tracking
    private final AtomicLong totalAllocations = new AtomicLong(0);
    private final AtomicLong totalRecycles = new AtomicLong(0);
    private final AtomicLong totalCleanups = new AtomicLong(0);
    private final AtomicLong memoryOptimizations = new AtomicLong(0);
    
    // Cleanup management
    private volatile long lastCleanupTime = System.currentTimeMillis();
    private volatile long lastMemoryCheckTime = System.currentTimeMillis();
    
    /**
     * Pooled Mat wrapper that tracks usage and enables recycling
     */
    public static class PooledMat {
        private final Mat mat;
        private final long allocationTime;
        private final int originalType;
        private final int originalRows;
        private final int originalCols;
        private volatile boolean isInUse = false;
        private volatile long lastUsedTime;
        private final FrameBuffer parentPool;
        
        private PooledMat(@NonNull Mat mat, @NonNull FrameBuffer parentPool) {
            this.mat = mat;
            this.parentPool = parentPool;
            this.allocationTime = System.currentTimeMillis();
            this.lastUsedTime = allocationTime;
            this.originalType = mat.type();
            this.originalRows = mat.rows();
            this.originalCols = mat.cols();
        }
        
        /**
         * Get the underlying Mat object
         * @return The OpenCV Mat
         */
        @NonNull
        public Mat getMat() {
            if (!isInUse) {
                throw new IllegalStateException("PooledMat is not currently in use");
            }
            return mat;
        }
        
        /**
         * Check if this buffer is currently in use
         * @return true if in use, false if available for recycling
         */
        public boolean isInUse() {
            return isInUse;
        }
        
        /**
         * Get the memory size of this buffer in bytes
         * @return Memory size in bytes
         */
        public long getMemorySize() {
            if (mat.empty()) {
                return 0;
            }
            return mat.total() * mat.elemSize();
        }
        
        /**
         * Get the age of this buffer in milliseconds
         * @return Age since allocation
         */
        public long getAge() {
            return System.currentTimeMillis() - allocationTime;
        }
        
        /**
         * Get time since last use in milliseconds
         * @return Time since last use
         */
        public long getTimeSinceLastUse() {
            return System.currentTimeMillis() - lastUsedTime;
        }
        
        /**
         * Check if this buffer is compatible with the given dimensions and type
         * @param rows Required rows
         * @param cols Required columns
         * @param type Required OpenCV type
         * @return true if compatible, false otherwise
         */
        public boolean isCompatible(int rows, int cols, int type) {
            return originalRows == rows && originalCols == cols && originalType == type;
        }
        
        /**
         * Recycle this buffer back to the pool
         * This method should be called when the buffer is no longer needed
         */
        public void recycle() {
            if (!isInUse) {
                Log.w(TAG, "Attempting to recycle buffer that is not in use");
                return;
            }
            
            parentPool.recycleBuffer(this);
        }
        
        /**
         * Mark this buffer as in use
         */
        private void markInUse() {
            isInUse = true;
            lastUsedTime = System.currentTimeMillis();
        }
        
        /**
         * Mark this buffer as available
         */
        private void markAvailable() {
            isInUse = false;
        }
        
        /**
         * Release the underlying Mat resources
         */
        private void release() {
            if (mat != null && !mat.empty()) {
                mat.release();
            }
        }
    }
    
    /**
     * Memory monitoring and optimization
     */
    private static class MemoryMonitor {
        private final Runtime runtime = Runtime.getRuntime();
        
        /**
         * Get current memory usage as a percentage of max heap
         * @return Memory usage percentage (0.0 to 1.0)
         */
        public double getMemoryUsagePercentage() {
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            return (double) usedMemory / maxMemory;
        }
        
        /**
         * Check if system is under memory pressure
         * @return true if memory usage exceeds threshold
         */
        public boolean isMemoryPressure() {
            return getMemoryUsagePercentage() > MEMORY_PRESSURE_THRESHOLD;
        }
        
        /**
         * Get available memory in bytes
         * @return Available memory
         */
        public long getAvailableMemory() {
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            return maxMemory - (totalMemory - freeMemory);
        }
        
        /**
         * Suggest garbage collection if needed
         */
        public void suggestGC() {
            if (isMemoryPressure()) {
                System.gc();
            }
        }
    }
    
    /**
     * Buffer statistics for monitoring and debugging
     */
    public static class BufferStats {
        public final int poolSize;
        public final int activeBuffers;
        public final int availableBuffers;
        public final long totalMemoryUsage;
        public final long totalAllocations;
        public final long totalRecycles;
        public final long totalCleanups;
        public final long memoryOptimizations;
        public final double memoryUsagePercentage;
        public final boolean isMemoryPressure;
        
        private BufferStats(int poolSize, int activeBuffers, int availableBuffers,
                           long totalMemoryUsage, long totalAllocations, long totalRecycles,
                           long totalCleanups, long memoryOptimizations,
                           double memoryUsagePercentage, boolean isMemoryPressure) {
            this.poolSize = poolSize;
            this.activeBuffers = activeBuffers;
            this.availableBuffers = availableBuffers;
            this.totalMemoryUsage = totalMemoryUsage;
            this.totalAllocations = totalAllocations;
            this.totalRecycles = totalRecycles;
            this.totalCleanups = totalCleanups;
            this.memoryOptimizations = memoryOptimizations;
            this.memoryUsagePercentage = memoryUsagePercentage;
            this.isMemoryPressure = isMemoryPressure;
        }
        
        public double getRecycleRate() {
            return totalAllocations.get() > 0 ? 
                (double) totalRecycles.get() / totalAllocations.get() : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "BufferStats{pool=%d, active=%d, available=%d, memory=%.2fMB, " +
                "allocations=%d, recycles=%d, cleanups=%d, optimizations=%d, " +
                "memUsage=%.1f%%, pressure=%s, recycleRate=%.1f%%}",
                poolSize, activeBuffers, availableBuffers, totalMemoryUsage / (1024.0 * 1024.0),
                totalAllocations, totalRecycles, totalCleanups, memoryOptimizations,
                memoryUsagePercentage * 100, isMemoryPressure, getRecycleRate() * 100
            );
        }
    }
    
    /**
     * Default constructor with standard configuration
     */
    public FrameBuffer() {
        this(DEFAULT_MAX_POOL_SIZE, DEFAULT_MAX_BUFFER_SIZE_MB * 1024 * 1024);
    }
    
    /**
     * Constructor with custom configuration
     * @param maxPoolSize Maximum number of buffers in the pool
     * @param maxBufferSizeBytes Maximum total memory usage for the buffer pool
     */
    public FrameBuffer(int maxPoolSize, long maxBufferSizeBytes) {
        this.maxPoolSize = Math.max(1, maxPoolSize);
        this.maxBufferSizeBytes = Math.max(1024 * 1024, maxBufferSizeBytes); // At least 1MB
        this.memoryMonitor = new MemoryMonitor();
        
        Log.d(TAG, "FrameBuffer created - maxPoolSize: " + this.maxPoolSize + 
                   ", maxBufferSize: " + (this.maxBufferSizeBytes / (1024 * 1024)) + "MB");
        
        // Pre-allocate initial buffers
        initializePool();
    }
    
    /**
     * Initialize the pool with some initial buffers
     */
    private void initializePool() {
        // We'll create initial buffers when first requested since we don't know the dimensions yet
        Log.d(TAG, "Pool initialization deferred until first buffer request");
    }
    
    /**
     * Acquire a buffer from the pool
     * @param rows Number of rows needed
     * @param cols Number of columns needed
     * @param type OpenCV type (e.g., CvType.CV_8UC3)
     * @return A pooled Mat buffer, or null if allocation fails
     */
    @Nullable
    public PooledMat acquireBuffer(int rows, int cols, int type) {
        // Check memory pressure before allocation
        checkMemoryPressure();
        
        // Try to find a compatible buffer in the pool
        PooledMat buffer = findCompatibleBuffer(rows, cols, type);
        
        if (buffer != null) {
            // Reuse existing buffer
            buffer.markInUse();
            activeBuffers.incrementAndGet();
            totalRecycles.incrementAndGet();
            
            Log.v(TAG, "Reused buffer from pool - " + rows + "x" + cols + ", type: " + type);
            return buffer;
        }
        
        // No compatible buffer found, create new one if pool has space
        if (canAllocateNewBuffer(rows, cols, type)) {
            buffer = createNewBuffer(rows, cols, type);
            
            if (buffer != null) {
                buffer.markInUse();
                poolSize.incrementAndGet();
                activeBuffers.incrementAndGet();
                totalAllocations.incrementAndGet();
                
                long bufferSize = buffer.getMemorySize();
                totalMemoryUsage.addAndGet(bufferSize);
                
                Log.v(TAG, "Created new buffer - " + rows + "x" + cols + ", type: " + type + 
                           ", size: " + (bufferSize / 1024) + "KB");
                return buffer;
            }
        }
        
        Log.w(TAG, "Failed to acquire buffer - pool full or memory pressure");
        return null;
    }
    
    /**
     * Find a compatible buffer in the available pool
     */
    @Nullable
    private PooledMat findCompatibleBuffer(int rows, int cols, int type) {
        PooledMat buffer;
        while ((buffer = availableBuffers.poll()) != null) {
            if (buffer.isCompatible(rows, cols, type)) {
                return buffer;
            } else {
                // Buffer not compatible, put it back for potential cleanup
                availableBuffers.offer(buffer);
                break; // Avoid infinite loop
            }
        }
        return null;
    }
    
    /**
     * Check if we can allocate a new buffer
     */
    private boolean canAllocateNewBuffer(int rows, int cols, int type) {
        // Check pool size limit
        if (poolSize.get() >= maxPoolSize) {
            return false;
        }
        
        // Estimate memory usage for new buffer
        long estimatedSize = (long) rows * cols * CvType.channels(type) * CvType.depth(type);
        
        // Check memory limits
        if (totalMemoryUsage.get() + estimatedSize > maxBufferSizeBytes) {
            return false;
        }
        
        // Check system memory pressure
        if (memoryMonitor.isMemoryPressure()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Create a new buffer
     */
    @Nullable
    private PooledMat createNewBuffer(int rows, int cols, int type) {
        try {
            Mat mat = new Mat(rows, cols, type);
            return new PooledMat(mat, this);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create new buffer", e);
            return null;
        }
    }
    
    /**
     * Recycle a buffer back to the pool
     * @param buffer The buffer to recycle
     */
    private void recycleBuffer(@NonNull PooledMat buffer) {
        if (!buffer.isInUse()) {
            Log.w(TAG, "Attempting to recycle buffer that is not in use");
            return;
        }
        
        buffer.markAvailable();
        activeBuffers.decrementAndGet();
        
        // Add back to available pool if there's space
        if (availableBuffers.size() < maxPoolSize / 2) {
            availableBuffers.offer(buffer);
            Log.v(TAG, "Buffer recycled to pool");
        } else {
            // Pool is full, release the buffer
            releaseBuffer(buffer);
            Log.v(TAG, "Buffer released due to full pool");
        }
        
        // Trigger cleanup if needed
        performPeriodicMaintenance();
    }
    
    /**
     * Release a buffer and its resources
     */
    private void releaseBuffer(@NonNull PooledMat buffer) {
        long bufferSize = buffer.getMemorySize();
        buffer.release();
        
        poolSize.decrementAndGet();
        totalMemoryUsage.addAndGet(-bufferSize);
        totalCleanups.incrementAndGet();
    }
    
    /**
     * Check for memory pressure and optimize if needed
     */
    private void checkMemoryPressure() {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastMemoryCheckTime > MEMORY_CHECK_INTERVAL_MS) {
            lastMemoryCheckTime = currentTime;
            
            if (memoryMonitor.isMemoryPressure()) {
                Log.w(TAG, "Memory pressure detected, optimizing buffer pool");
                optimizeMemoryUsage();
                memoryOptimizations.incrementAndGet();
            }
        }
    }
    
    /**
     * Optimize memory usage by cleaning up old or unused buffers
     */
    private void optimizeMemoryUsage() {
        Log.d(TAG, "Optimizing memory usage");
        
        // Suggest garbage collection
        memoryMonitor.suggestGC();
        
        // Clean up old buffers more aggressively
        cleanupOldBuffers(true);
        
        // Reduce pool size if needed
        int targetSize = Math.max(1, maxPoolSize / 2);
        while (availableBuffers.size() > targetSize) {
            PooledMat buffer = availableBuffers.poll();
            if (buffer != null) {
                releaseBuffer(buffer);
            }
        }
        
        Log.d(TAG, "Memory optimization complete - pool size: " + poolSize.get() + 
                   ", memory usage: " + (totalMemoryUsage.get() / (1024 * 1024)) + "MB");
    }
    
    /**
     * Perform periodic maintenance tasks
     */
    private void performPeriodicMaintenance() {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            lastCleanupTime = currentTime;
            cleanupOldBuffers(false);
        }
    }
    
    /**
     * Clean up old or unused buffers
     * @param aggressive Whether to be more aggressive in cleanup
     */
    private void cleanupOldBuffers(boolean aggressive) {
        long maxAge = aggressive ? 10000 : 60000; // 10s aggressive, 60s normal
        long currentTime = System.currentTimeMillis();
        int cleanedCount = 0;
        
        // Clean up available buffers that are too old
        PooledMat buffer;
        while ((buffer = availableBuffers.poll()) != null) {
            if (buffer.getTimeSinceLastUse() > maxAge) {
                releaseBuffer(buffer);
                cleanedCount++;
            } else {
                // Buffer is still fresh, put it back
                availableBuffers.offer(buffer);
                break;
            }
        }
        
        if (cleanedCount > 0) {
            Log.d(TAG, "Cleaned up " + cleanedCount + " old buffers (aggressive: " + aggressive + ")");
        }
    }
    
    /**
     * Get current buffer statistics
     * @return BufferStats object with current metrics
     */
    @NonNull
    public BufferStats getStats() {
        return new BufferStats(
            poolSize.get(),
            activeBuffers.get(),
            availableBuffers.size(),
            totalMemoryUsage.get(),
            totalAllocations.get(),
            totalRecycles.get(),
            totalCleanups.get(),
            memoryOptimizations.get(),
            memoryMonitor.getMemoryUsagePercentage(),
            memoryMonitor.isMemoryPressure()
        );
    }
    
    /**
     * Clear all buffers and release resources
     * This should be called when the buffer pool is no longer needed
     */
    public void clear() {
        Log.d(TAG, "Clearing buffer pool");
        
        // Release all available buffers
        PooledMat buffer;
        int releasedCount = 0;
        while ((buffer = availableBuffers.poll()) != null) {
            releaseBuffer(buffer);
            releasedCount++;
        }
        
        // Reset counters
        poolSize.set(0);
        totalMemoryUsage.set(0);
        
        Log.i(TAG, "Buffer pool cleared - released " + releasedCount + " buffers. " +
                   "Final stats: " + getStats());
    }
    
    /**
     * Log current buffer pool status
     */
    public void logStatus() {
        BufferStats stats = getStats();
        Log.i(TAG, "Buffer pool status: " + stats);
    }
}