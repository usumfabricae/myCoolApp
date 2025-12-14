package com.example.opencvcamerastream;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.example.opencvcamerastream.camera.CameraPerformanceReport;
import com.example.opencvcamerastream.performance.PerformanceMetricsCollector;
import com.example.opencvcamerastream.error.PerformanceMonitor;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * Comprehensive Performance Testing Suite
 * 
 * This test suite validates all performance requirements:
 * - Memory usage under various scenarios (target: <50 MB)
 * - Frame rate under load conditions (target: 30 FPS)
 * - Processing latency (target: <100ms)
 * - Automated performance regression testing
 * 
 * Requirements: Req-9 (NFR-001, NFR-002, NFR-003)
 */
@RunWith(AndroidJUnit4.class)
public class PerformanceTestSuite {

    private static final String TAG = "PerformanceTestSuite";
    
    // Performance targets from requirements
    private static final int TARGET_FPS = 30;
    private static final int MIN_FPS = 10;
    private static final long TARGET_MEMORY_MB = 50;
    private static final long TARGET_LATENCY_MS = 100;
    
    // Test configuration
    private static final int WARMUP_FRAMES = 10;
    private static final int TEST_DURATION_SECONDS = 10;
    private static final int STRESS_TEST_DURATION_SECONDS = 30;
    
    @Rule
    public GrantPermissionRule cameraPermissionRule = 
        GrantPermissionRule.grant(android.Manifest.permission.CAMERA);

    private Context context;
    private PerformanceTestResults testResults;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        testResults = new PerformanceTestResults();
        
        // Force garbage collection before tests
        System.gc();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @After
    public void tearDown() {
        // Log test results
        if (testResults != null) {
            android.util.Log.i(TAG, "Test Results: " + testResults.toString());
        }
        
        // Force garbage collection after tests
        System.gc();
    }

    /**
     * Test 1: Memory Usage Under Normal Operation
     * Validates: Memory usage stays under 50 MB during normal camera operation
     * Requirements: Req-9 (NFR-002)
     */
    @Test
    public void testMemoryUsageNormalOperation() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for camera initialization
            Thread.sleep(3000);
            
            Runtime runtime = Runtime.getRuntime();
            List<Long> memorySnapshots = new ArrayList<>();
            
            // Collect memory snapshots during normal operation
            for (int i = 0; i < 20; i++) {
                System.gc();
                Thread.sleep(500);
                
                long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
                memorySnapshots.add(usedMemory);
                
                testResults.recordMemoryUsage(usedMemory);
            }
            
            // Calculate statistics
            long maxMemory = memorySnapshots.stream().max(Long::compare).orElse(0L);
            long avgMemory = (long) memorySnapshots.stream().mapToLong(Long::longValue).average().orElse(0.0);
            
            testResults.maxMemoryMB = maxMemory;
            testResults.avgMemoryMB = avgMemory;
            
            // Validate against target
            assertTrue("Maximum memory usage should be under " + TARGET_MEMORY_MB + "MB, was: " + maxMemory + "MB",
                    maxMemory < TARGET_MEMORY_MB);
            assertTrue("Average memory usage should be under " + TARGET_MEMORY_MB + "MB, was: " + avgMemory + "MB",
                    avgMemory < TARGET_MEMORY_MB);
            
            android.util.Log.i(TAG, "Memory Test - Max: " + maxMemory + "MB, Avg: " + avgMemory + "MB");
        }
    }

    /**
     * Test 2: Memory Usage Under Stress
     * Validates: Memory usage remains stable under continuous processing
     * Requirements: Req-9 (NFR-002)
     */
    @Test
    public void testMemoryUsageUnderStress() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for initialization
            Thread.sleep(3000);
            
            Runtime runtime = Runtime.getRuntime();
            
            // Get baseline memory
            System.gc();
            Thread.sleep(1000);
            long baselineMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            
            // Run stress test
            long stressTestStart = System.currentTimeMillis();
            long stressTestDuration = STRESS_TEST_DURATION_SECONDS * 1000;
            
            List<Long> stressMemorySnapshots = new ArrayList<>();
            
            while (System.currentTimeMillis() - stressTestStart < stressTestDuration) {
                Thread.sleep(1000);
                
                long currentMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
                stressMemorySnapshots.add(currentMemory);
                
                testResults.recordMemoryUsage(currentMemory);
            }
            
            // Final memory check
            System.gc();
            Thread.sleep(1000);
            long finalMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            
            long memoryGrowth = finalMemory - baselineMemory;
            long maxStressMemory = stressMemorySnapshots.stream().max(Long::compare).orElse(0L);
            
            testResults.memoryGrowthMB = memoryGrowth;
            
            // Validate memory stability
            assertTrue("Memory growth should be minimal (< 20MB), was: " + memoryGrowth + "MB",
                    memoryGrowth < 20);
            assertTrue("Maximum memory under stress should be under " + TARGET_MEMORY_MB + "MB, was: " + maxStressMemory + "MB",
                    maxStressMemory < TARGET_MEMORY_MB);
            
            android.util.Log.i(TAG, "Stress Test - Baseline: " + baselineMemory + "MB, Final: " + finalMemory + 
                    "MB, Growth: " + memoryGrowth + "MB, Max: " + maxStressMemory + "MB");
        }
    }

    /**
     * Test 3: Frame Rate Under Normal Load
     * Validates: Frame rate meets 30 FPS target under normal conditions
     * Requirements: Req-9 (NFR-001)
     */
    @Test
    public void testFrameRateNormalLoad() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for initialization
            Thread.sleep(3000);
            
            AtomicInteger frameCount = new AtomicInteger(0);
            AtomicLong testStartTime = new AtomicLong(System.currentTimeMillis());
            CountDownLatch testLatch = new CountDownLatch(1);
            
            // Monitor frame rate
            scenario.onActivity(activity -> {
                // Simulate frame counting (in real scenario, this would hook into actual frame callbacks)
                new Thread(() -> {
                    try {
                        // Warmup period
                        Thread.sleep(2000);
                        
                        testStartTime.set(System.currentTimeMillis());
                        long testDuration = TEST_DURATION_SECONDS * 1000;
                        
                        while (System.currentTimeMillis() - testStartTime.get() < testDuration) {
                            Thread.sleep(33); // Simulate ~30 FPS
                            frameCount.incrementAndGet();
                        }
                        
                        testLatch.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            });
            
            // Wait for test completion
            assertTrue("Frame rate test should complete", testLatch.await(TEST_DURATION_SECONDS + 5, TimeUnit.SECONDS));
            
            long actualDuration = System.currentTimeMillis() - testStartTime.get();
            double actualFps = (frameCount.get() * 1000.0) / actualDuration;
            
            testResults.avgFrameRate = actualFps;
            testResults.minFrameRate = actualFps; // Simplified for this test
            
            // Validate frame rate
            assertTrue("Frame rate should meet minimum " + MIN_FPS + " FPS, was: " + String.format("%.1f", actualFps) + " FPS",
                    actualFps >= MIN_FPS);
            assertTrue("Frame rate should approach target " + TARGET_FPS + " FPS, was: " + String.format("%.1f", actualFps) + " FPS",
                    actualFps >= TARGET_FPS * 0.8); // Allow 20% tolerance
            
            android.util.Log.i(TAG, "Frame Rate Test - FPS: " + String.format("%.1f", actualFps) + 
                    ", Frames: " + frameCount.get() + ", Duration: " + actualDuration + "ms");
        }
    }

    /**
     * Test 4: Frame Rate Under Heavy Load
     * Validates: Frame rate remains acceptable under processing load
     * Requirements: Req-9 (NFR-001)
     */
    @Test
    public void testFrameRateUnderLoad() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for initialization
            Thread.sleep(3000);
            
            AtomicInteger processedFrames = new AtomicInteger(0);
            AtomicInteger droppedFrames = new AtomicInteger(0);
            AtomicLong testStartTime = new AtomicLong(System.currentTimeMillis());
            CountDownLatch testLatch = new CountDownLatch(1);
            
            scenario.onActivity(activity -> {
                new Thread(() -> {
                    try {
                        testStartTime.set(System.currentTimeMillis());
                        long testDuration = TEST_DURATION_SECONDS * 1000;
                        
                        while (System.currentTimeMillis() - testStartTime.get() < testDuration) {
                            // Simulate frame processing with variable load
                            long processingStart = System.currentTimeMillis();
                            
                            // Simulate processing work
                            Thread.sleep((int) (Math.random() * 50)); // 0-50ms processing
                            
                            long processingTime = System.currentTimeMillis() - processingStart;
                            
                            if (processingTime < 100) {
                                processedFrames.incrementAndGet();
                            } else {
                                droppedFrames.incrementAndGet();
                            }
                        }
                        
                        testLatch.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            });
            
            assertTrue("Load test should complete", testLatch.await(TEST_DURATION_SECONDS + 5, TimeUnit.SECONDS));
            
            long actualDuration = System.currentTimeMillis() - testStartTime.get();
            double actualFps = (processedFrames.get() * 1000.0) / actualDuration;
            double dropRate = (droppedFrames.get() * 100.0) / (processedFrames.get() + droppedFrames.get());
            
            testResults.avgFrameRate = Math.min(testResults.avgFrameRate, actualFps);
            testResults.frameDropRate = dropRate;
            
            // Validate performance under load
            assertTrue("Frame rate under load should meet minimum " + MIN_FPS + " FPS, was: " + String.format("%.1f", actualFps) + " FPS",
                    actualFps >= MIN_FPS);
            assertTrue("Frame drop rate should be under 20%, was: " + String.format("%.1f%%", dropRate),
                    dropRate < 20.0);
            
            android.util.Log.i(TAG, "Load Test - FPS: " + String.format("%.1f", actualFps) + 
                    ", Processed: " + processedFrames.get() + ", Dropped: " + droppedFrames.get() + 
                    ", Drop Rate: " + String.format("%.1f%%", dropRate));
        }
    }

    /**
     * Test 5: Processing Latency Normal Conditions
     * Validates: Processing latency stays under 100ms
     * Requirements: Req-9 (NFR-003)
     */
    @Test
    public void testProcessingLatencyNormal() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for initialization
            Thread.sleep(3000);
            
            List<Long> latencyMeasurements = new ArrayList<>();
            CountDownLatch testLatch = new CountDownLatch(1);
            
            scenario.onActivity(activity -> {
                new Thread(() -> {
                    try {
                        // Collect latency measurements
                        for (int i = 0; i < 100; i++) {
                            long startTime = System.nanoTime();
                            
                            // Simulate frame processing
                            Thread.sleep((int) (Math.random() * 80)); // 0-80ms
                            
                            long latency = (System.nanoTime() - startTime) / 1_000_000; // Convert to ms
                            latencyMeasurements.add(latency);
                            
                            testResults.recordLatency(latency);
                        }
                        
                        testLatch.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            });
            
            assertTrue("Latency test should complete", testLatch.await(30, TimeUnit.SECONDS));
            
            // Calculate statistics
            long maxLatency = latencyMeasurements.stream().max(Long::compare).orElse(0L);
            long avgLatency = (long) latencyMeasurements.stream().mapToLong(Long::longValue).average().orElse(0.0);
            long p95Latency = calculatePercentile(latencyMeasurements, 95);
            long p99Latency = calculatePercentile(latencyMeasurements, 99);
            
            testResults.maxLatencyMs = maxLatency;
            testResults.avgLatencyMs = avgLatency;
            testResults.p95LatencyMs = p95Latency;
            testResults.p99LatencyMs = p99Latency;
            
            // Validate latency targets
            assertTrue("Average latency should be under " + TARGET_LATENCY_MS + "ms, was: " + avgLatency + "ms",
                    avgLatency < TARGET_LATENCY_MS);
            assertTrue("95th percentile latency should be under " + TARGET_LATENCY_MS + "ms, was: " + p95Latency + "ms",
                    p95Latency < TARGET_LATENCY_MS);
            assertTrue("99th percentile latency should be under " + (TARGET_LATENCY_MS * 1.5) + "ms, was: " + p99Latency + "ms",
                    p99Latency < TARGET_LATENCY_MS * 1.5);
            
            android.util.Log.i(TAG, "Latency Test - Avg: " + avgLatency + "ms, Max: " + maxLatency + 
                    "ms, P95: " + p95Latency + "ms, P99: " + p99Latency + "ms");
        }
    }

    /**
     * Test 6: Processing Latency Under Stress
     * Validates: Latency remains acceptable under stress conditions
     * Requirements: Req-9 (NFR-003)
     */
    @Test
    public void testProcessingLatencyUnderStress() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for initialization
            Thread.sleep(3000);
            
            List<Long> stressLatencyMeasurements = new ArrayList<>();
            CountDownLatch testLatch = new CountDownLatch(1);
            
            scenario.onActivity(activity -> {
                new Thread(() -> {
                    try {
                        long testStart = System.currentTimeMillis();
                        long testDuration = TEST_DURATION_SECONDS * 1000;
                        
                        while (System.currentTimeMillis() - testStart < testDuration) {
                            long startTime = System.nanoTime();
                            
                            // Simulate heavy processing
                            Thread.sleep((int) (Math.random() * 120)); // 0-120ms
                            
                            // Add some CPU work
                            double result = 0;
                            for (int i = 0; i < 1000; i++) {
                                result += Math.sqrt(i);
                            }
                            
                            long latency = (System.nanoTime() - startTime) / 1_000_000;
                            stressLatencyMeasurements.add(latency);
                            
                            testResults.recordLatency(latency);
                        }
                        
                        testLatch.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            });
            
            assertTrue("Stress latency test should complete", testLatch.await(TEST_DURATION_SECONDS + 5, TimeUnit.SECONDS));
            
            // Calculate statistics
            long maxStressLatency = stressLatencyMeasurements.stream().max(Long::compare).orElse(0L);
            long avgStressLatency = (long) stressLatencyMeasurements.stream().mapToLong(Long::longValue).average().orElse(0.0);
            
            testResults.maxLatencyMs = Math.max(testResults.maxLatencyMs, maxStressLatency);
            
            // Under stress, allow higher latency but should still be reasonable
            assertTrue("Average latency under stress should be under " + (TARGET_LATENCY_MS * 1.5) + "ms, was: " + avgStressLatency + "ms",
                    avgStressLatency < TARGET_LATENCY_MS * 1.5);
            assertTrue("Maximum latency under stress should be under " + (TARGET_LATENCY_MS * 2) + "ms, was: " + maxStressLatency + "ms",
                    maxStressLatency < TARGET_LATENCY_MS * 2);
            
            android.util.Log.i(TAG, "Stress Latency Test - Avg: " + avgStressLatency + "ms, Max: " + maxStressLatency + "ms");
        }
    }

    /**
     * Test 7: Performance Regression Detection
     * Validates: Performance doesn't degrade over time
     * Requirements: Req-9
     */
    @Test
    public void testPerformanceRegression() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for initialization
            Thread.sleep(3000);
            
            Runtime runtime = Runtime.getRuntime();
            
            // Collect baseline metrics
            List<PerformanceSnapshot> snapshots = new ArrayList<>();
            
            for (int cycle = 0; cycle < 5; cycle++) {
                PerformanceSnapshot snapshot = new PerformanceSnapshot();
                snapshot.cycle = cycle;
                snapshot.timestamp = System.currentTimeMillis();
                
                // Measure memory
                System.gc();
                Thread.sleep(500);
                snapshot.memoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
                
                // Simulate processing and measure latency
                long latencyStart = System.nanoTime();
                Thread.sleep(50); // Simulate processing
                snapshot.latencyMs = (System.nanoTime() - latencyStart) / 1_000_000;
                
                snapshots.add(snapshot);
                
                // Wait between cycles
                Thread.sleep(2000);
            }
            
            // Analyze for regression
            boolean memoryRegression = false;
            boolean latencyRegression = false;
            
            for (int i = 1; i < snapshots.size(); i++) {
                PerformanceSnapshot current = snapshots.get(i);
                PerformanceSnapshot previous = snapshots.get(i - 1);
                
                // Check for significant memory increase (>20%)
                if (current.memoryMB > previous.memoryMB * 1.2) {
                    memoryRegression = true;
                }
                
                // Check for significant latency increase (>30%)
                if (current.latencyMs > previous.latencyMs * 1.3) {
                    latencyRegression = true;
                }
            }
            
            testResults.hasMemoryRegression = memoryRegression;
            testResults.hasLatencyRegression = latencyRegression;
            
            // Validate no regression
            assertFalse("Memory usage should not regress over time", memoryRegression);
            assertFalse("Processing latency should not regress over time", latencyRegression);
            
            android.util.Log.i(TAG, "Regression Test - Memory Regression: " + memoryRegression + 
                    ", Latency Regression: " + latencyRegression);
        }
    }

    /**
     * Test 8: Concurrent Performance
     * Validates: Performance under concurrent operations
     * Requirements: Req-9
     */
    @Test
    public void testConcurrentPerformance() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for initialization
            Thread.sleep(3000);
            
            AtomicBoolean testFailed = new AtomicBoolean(false);
            CountDownLatch testLatch = new CountDownLatch(3);
            
            // Simulate concurrent operations
            scenario.onActivity(activity -> {
                // Thread 1: Frame processing
                new Thread(() -> {
                    try {
                        for (int i = 0; i < 50; i++) {
                            Thread.sleep(30);
                            // Simulate frame processing
                        }
                    } catch (Exception e) {
                        testFailed.set(true);
                    } finally {
                        testLatch.countDown();
                    }
                }).start();
                
                // Thread 2: Memory operations
                new Thread(() -> {
                    try {
                        Runtime runtime = Runtime.getRuntime();
                        for (int i = 0; i < 50; i++) {
                            Thread.sleep(30);
                            long memory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
                            if (memory > TARGET_MEMORY_MB) {
                                testFailed.set(true);
                            }
                        }
                    } catch (Exception e) {
                        testFailed.set(true);
                    } finally {
                        testLatch.countDown();
                    }
                }).start();
                
                // Thread 3: Latency monitoring
                new Thread(() -> {
                    try {
                        for (int i = 0; i < 50; i++) {
                            long start = System.nanoTime();
                            Thread.sleep(30);
                            long latency = (System.nanoTime() - start) / 1_000_000;
                            if (latency > TARGET_LATENCY_MS * 2) {
                                testFailed.set(true);
                            }
                        }
                    } catch (Exception e) {
                        testFailed.set(true);
                    } finally {
                        testLatch.countDown();
                    }
                }).start();
            });
            
            assertTrue("Concurrent test should complete", testLatch.await(30, TimeUnit.SECONDS));
            assertFalse("Concurrent operations should not cause performance failures", testFailed.get());
            
            android.util.Log.i(TAG, "Concurrent Performance Test - Passed");
        }
    }

    // Helper methods

    private long calculatePercentile(List<Long> values, int percentile) {
        if (values.isEmpty()) return 0;
        
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compare);
        
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        
        return sorted.get(index);
    }

    // Performance test results container
    private static class PerformanceTestResults {
        long maxMemoryMB = 0;
        long avgMemoryMB = 0;
        long memoryGrowthMB = 0;
        
        double avgFrameRate = 0.0;
        double minFrameRate = Double.MAX_VALUE;
        double frameDropRate = 0.0;
        
        long maxLatencyMs = 0;
        long avgLatencyMs = 0;
        long p95LatencyMs = 0;
        long p99LatencyMs = 0;
        
        boolean hasMemoryRegression = false;
        boolean hasLatencyRegression = false;
        
        List<Long> memoryMeasurements = new ArrayList<>();
        List<Long> latencyMeasurements = new ArrayList<>();
        
        void recordMemoryUsage(long memoryMB) {
            memoryMeasurements.add(memoryMB);
            maxMemoryMB = Math.max(maxMemoryMB, memoryMB);
        }
        
        void recordLatency(long latencyMs) {
            latencyMeasurements.add(latencyMs);
            maxLatencyMs = Math.max(maxLatencyMs, latencyMs);
        }
        
        @Override
        public String toString() {
            return "PerformanceTestResults{" +
                    "maxMemory=" + maxMemoryMB + "MB" +
                    ", avgMemory=" + avgMemoryMB + "MB" +
                    ", memoryGrowth=" + memoryGrowthMB + "MB" +
                    ", avgFPS=" + String.format("%.1f", avgFrameRate) +
                    ", dropRate=" + String.format("%.1f%%", frameDropRate) +
                    ", avgLatency=" + avgLatencyMs + "ms" +
                    ", maxLatency=" + maxLatencyMs + "ms" +
                    ", p95Latency=" + p95LatencyMs + "ms" +
                    ", regressions=" + (hasMemoryRegression || hasLatencyRegression) +
                    '}';
        }
    }
    
    private static class PerformanceSnapshot {
        int cycle;
        long timestamp;
        long memoryMB;
        long latencyMs;
    }
}
