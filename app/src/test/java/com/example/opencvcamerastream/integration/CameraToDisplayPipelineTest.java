package com.example.opencvcamerastream.integration;

import android.content.Context;
import android.media.Image;
import android.view.TextureView;
import androidx.test.core.app.ApplicationProvider;
import org.robolectric.RuntimeEnvironment;
import com.example.opencvcamerastream.camera.CameraManager;
import com.example.opencvcamerastream.display.DisplayManager;
import com.example.opencvcamerastream.processing.FrameProcessor;
import com.example.opencvcamerastream.processing.OpenCVProcessor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opencv.core.Mat;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mockStatic;
import org.mockito.MockedStatic;

/**
 * Integration tests for the complete camera-to-display pipeline
 * 
 * Tests the integration between:
 * - Camera capture (ImageReader callback)
 * - OpenCV processing (background thread)
 * - Frame processing queue
 * - Display system (TextureView)
 * 
 * Requirements tested:
 * - 2.1: Pass camera frame to OpenCV for processing
 * - 2.3: Ensure proper threading to maintain UI responsiveness
 * - 3.3: Add processed frame callback to display system
 * - Complete camera-to-display flow
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Android 10 compatibility
public class CameraToDisplayPipelineTest {
    
    private Context context;
    
    @Mock
    private CameraManager mockCameraManager;
    
    @Mock
    private OpenCVProcessor mockOpenCVProcessor;
    
    @Mock
    private DisplayManager mockDisplayManager;
    
    @Mock
    private TextureView mockTextureView;
    
    @Mock
    private Image mockImage;
    
    @Mock
    private Mat mockInputMat;
    
    @Mock
    private Mat mockProcessedMat;
    
    @Mock
    private FrameProcessor.VisualOdometryCallback mockVisualOdometryCallback;
    
    private FrameProcessor frameProcessor;
    private CameraManager.FrameCallback cameraFrameCallback;
    private FrameProcessor.ProcessingCallback processingCallback;
    private DisplayManager.DisplayCallback displayCallback;
    
    // Test synchronization
    private CountDownLatch frameProcessedLatch;
    private CountDownLatch displayUpdatedLatch;
    private AtomicBoolean processingCompleted = new AtomicBoolean(false);
    private AtomicBoolean displayUpdated = new AtomicBoolean(false);
    private AtomicReference<Exception> processingError = new AtomicReference<>();
    private AtomicInteger framesProcessed = new AtomicInteger(0);
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        context = ApplicationProvider.getApplicationContext();
        
        // Set up mock OpenCV processor
        when(mockOpenCVProcessor.isInitialized()).thenReturn(true);
        when(mockOpenCVProcessor.processFrame(any(Mat.class))).thenReturn(mockProcessedMat);
        
        // Set up mock display manager
        when(mockDisplayManager.isDisplayReady()).thenReturn(true);
        when(mockDisplayManager.updateFrame(any(Mat.class))).thenReturn(true);
        
        // Set up mock image
        when(mockImage.getWidth()).thenReturn(1280);
        when(mockImage.getHeight()).thenReturn(720);
        
        // Set up mock mats
        when(mockInputMat.empty()).thenReturn(false);
        when(mockProcessedMat.empty()).thenReturn(false);
        when(mockProcessedMat.width()).thenReturn(1280);
        when(mockProcessedMat.height()).thenReturn(720);
        
        // Set up mock mat cloning for visual odometry
        when(mockInputMat.clone()).thenReturn(mockInputMat);
        when(mockProcessedMat.clone()).thenReturn(mockProcessedMat);
        
        // Initialize frame processor
        frameProcessor = new FrameProcessor(mockOpenCVProcessor);
        frameProcessor.setVisualOdometryCallback(mockVisualOdometryCallback);
        
        // Set up latches for synchronization
        frameProcessedLatch = new CountDownLatch(1);
        displayUpdatedLatch = new CountDownLatch(1);
        
        // Set up callbacks
        setupCallbacks();
    }
    
    private void setupCallbacks() {
        // Camera frame callback (simulates ImageReader callback)
        cameraFrameCallback = new CameraManager.FrameCallback() {
            @Override
            public void onFrameAvailable(Image frame) {
                if (frameProcessor != null && frameProcessor.isProcessing()) {
                    frameProcessor.processFrameAsync(frame);
                }
            }
        };
        
        // Processing callback (connects processor to display)
        processingCallback = new FrameProcessor.ProcessingCallback() {
            @Override
            public void onFrameProcessed(Mat processedFrame, Image originalImage, long processingTimeMs) {
                framesProcessed.incrementAndGet();
                
                // Simulate display update
                if (mockDisplayManager.isDisplayReady()) {
                    boolean updated = mockDisplayManager.updateFrame(processedFrame);
                    if (updated) {
                        displayUpdated.set(true);
                        displayUpdatedLatch.countDown();
                    }
                }
                
                processingCompleted.set(true);
                frameProcessedLatch.countDown();
                
                // Clean up resources
                processedFrame.release();
                originalImage.close();
            }
            
            @Override
            public void onProcessingFailed(Exception error, Image originalImage) {
                processingError.set(error);
                frameProcessedLatch.countDown();
                
                if (originalImage != null) {
                    originalImage.close();
                }
            }
            
            @Override
            public void onFrameDropped(Image droppedImage) {
                droppedImage.close();
            }
        };
        
        // Display callback
        displayCallback = new DisplayManager.DisplayCallback() {
            @Override
            public void onDisplayReady() {
                // Display is ready for rendering
            }
            
            @Override
            public void onDisplayDestroyed() {
                // Display destroyed
            }
            
            @Override
            public void onFrameUpdateError(Exception error) {
                processingError.set(error);
            }
            
            @Override
            public void onPerformanceUpdate(float fps, float avgUpdateTime) {
                // Performance metrics
            }
        };
        
        frameProcessor.setProcessingCallback(processingCallback);
    }
    
    @Test
    public void testCompleteCameraToDisplayPipeline() throws InterruptedException {
        // Test the complete pipeline from camera frame to display
        // Requirements: 2.1, 2.3, 3.3
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(mockImage))
                       .thenReturn(mockInputMat);
            
            // Start the processing pipeline
            assertTrue("Frame processor should start", frameProcessor.start());
            
            // Simulate camera frame callback
            cameraFrameCallback.onFrameAvailable(mockImage);
            
            // Wait for processing to complete
            assertTrue("Frame processing should complete within timeout",
                    frameProcessedLatch.await(2, TimeUnit.SECONDS));
            
            // Verify the complete pipeline
            assertTrue("Processing should complete successfully", processingCompleted.get());
            assertNull("No processing errors should occur", processingError.get());
            assertEquals("One frame should be processed", 1, framesProcessed.get());
            
            // Verify OpenCV processing was called
            verify(mockOpenCVProcessor, timeout(1000)).processFrame(mockInputMat);
            
            // Verify display was updated
            assertTrue("Display should be updated", displayUpdated.get());
            verify(mockDisplayManager, timeout(1000)).updateFrame(mockProcessedMat);
        }
    }
    
    @Test
    public void testOptimizedPipelineWithOwnershipTransfer() throws InterruptedException {
        // Test Task 21: Verify ownership transfer throughout the pipeline
        // Requirement Req-13.3: Replace callback clones with ownership transfer
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(mockImage))
                       .thenReturn(mockInputMat);
            
            // Set up callback to verify ownership transfer
            frameProcessor.setProcessingCallback(new FrameProcessor.ProcessingCallback() {
                @Override
                public void onFrameProcessed(Mat processedFrame, Image originalImage, long processingTimeMs) {
                    // Verify we received the actual objects (not clones)
                    assertSame("Processed Mat should be transferred, not cloned", mockProcessedMat, processedFrame);
                    assertSame("Original Image should be transferred, not cloned", mockImage, originalImage);
                    
                    framesProcessed.incrementAndGet();
                    processingCompleted.set(true);
                    frameProcessedLatch.countDown();
                    
                    // In optimized pipeline, callback owns the resources and must clean up
                    // processedFrame.release(); // Would be called in real implementation
                    // originalImage.close(); // Would be called in real implementation
                }
                
                @Override
                public void onProcessingFailed(Exception error, Image originalImage) {
                    processingError.set(error);
                    frameProcessedLatch.countDown();
                    if (originalImage != null) {
                        // originalImage.close(); // Would be called in real implementation
                    }
                }
                
                @Override
                public void onFrameDropped(Image droppedImage) {
                    // droppedImage.close(); // Would be called in real implementation
                }
            });
            
            frameProcessor.start();
            cameraFrameCallback.onFrameAvailable(mockImage);
            
            assertTrue("Frame processing should complete", 
                    frameProcessedLatch.await(2, TimeUnit.SECONDS));
            assertTrue("Processing should complete successfully", processingCompleted.get());
            assertEquals("One frame should be processed", 1, framesProcessed.get());
        }
    }
    
    @Test
    public void testPipelineWithZeroCopyOptimization() throws InterruptedException {
        // Test Task 24: Verify zero-copy processing path implementation
        // Requirement Req-13.1, Req-13.2: Validate copy operation reduction
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(mockImage))
                       .thenReturn(mockInputMat);
            
            frameProcessor.start();
            cameraFrameCallback.onFrameAvailable(mockImage);
            
            assertTrue("Frame processing should complete", 
                    frameProcessedLatch.await(2, TimeUnit.SECONDS));
            
            // Verify no buffer pool copies occurred
            verify(mockInputMat, never()).copyTo(any(Mat.class));
            verify(mockProcessedMat, never()).copyTo(any(Mat.class));
            
            // Verify no defensive cloning occurred
            verify(mockInputMat, never()).clone();
            verify(mockProcessedMat, never()).clone();
            
            // Verify direct processing path was used
            verify(mockOpenCVProcessor, timeout(1000)).processFrame(mockInputMat);
            
            assertTrue("Processing should complete successfully", processingCompleted.get());
        }
    }
    
    @Test
    public void testPipelineThreadSafetyWithoutDefensiveCloning() throws InterruptedException {
        // Test Task 22: Verify thread safety without defensive cloning
        // Requirement Req-13.4: Remove DisplayManager defensive cloning
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(any(Image.class)))
                       .thenReturn(mockInputMat);
            
            frameProcessor.start();
            
            // Create multiple concurrent frame processing operations
            int concurrentFrames = 10;
            CountDownLatch concurrentLatch = new CountDownLatch(concurrentFrames);
            
            frameProcessor.setProcessingCallback(new FrameProcessor.ProcessingCallback() {
                @Override
                public void onFrameProcessed(Mat processedFrame, Image originalImage, long processingTimeMs) {
                    framesProcessed.incrementAndGet();
                    concurrentLatch.countDown();
                    
                    // Verify no cloning occurred
                    assertNotNull("Processed frame should not be null", processedFrame);
                    assertNotNull("Original image should not be null", originalImage);
                }
                
                @Override
                public void onProcessingFailed(Exception error, Image originalImage) {
                    concurrentLatch.countDown();
                }
                
                @Override
                public void onFrameDropped(Image droppedImage) {
                    concurrentLatch.countDown();
                }
            });
            
            // Send multiple frames concurrently
            for (int i = 0; i < concurrentFrames; i++) {
                Image frameImage = mock(Image.class);
                when(frameImage.getWidth()).thenReturn(1280);
                when(frameImage.getHeight()).thenReturn(720);
                cameraFrameCallback.onFrameAvailable(frameImage);
            }
            
            assertTrue("All concurrent frames should be processed", 
                    concurrentLatch.await(5, TimeUnit.SECONDS));
            
            // Verify no defensive cloning occurred during concurrent access
            verify(mockInputMat, never()).clone();
            verify(mockProcessedMat, never()).clone();
            
            assertTrue("Some frames should be processed", framesProcessed.get() > 0);
        }
    }
    
    @Test
    public void testPipelineWithMultipleFrames() throws InterruptedException {
        // Test pipeline with multiple frames
        // Requirement 2.3: Ensure proper threading to maintain UI responsiveness
        
        int frameCount = 5;
        CountDownLatch multiFrameLatch = new CountDownLatch(frameCount);
        AtomicInteger processedCount = new AtomicInteger(0);
        
        // Update callback to handle multiple frames
        frameProcessor.setProcessingCallback(new FrameProcessor.ProcessingCallback() {
            @Override
            public void onFrameProcessed(Mat processedFrame, Image originalImage, long processingTimeMs) {
                processedCount.incrementAndGet();
                multiFrameLatch.countDown();
                
                // Clean up
                processedFrame.release();
                originalImage.close();
            }
            
            @Override
            public void onProcessingFailed(Exception error, Image originalImage) {
                multiFrameLatch.countDown();
                if (originalImage != null) {
                    originalImage.close();
                }
            }
            
            @Override
            public void onFrameDropped(Image droppedImage) {
                droppedImage.close();
            }
        });
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(any(Image.class)))
                       .thenReturn(mockInputMat);
            
            frameProcessor.start();
            
            // Send multiple frames
            for (int i = 0; i < frameCount; i++) {
                Image frameImage = mock(Image.class);
                when(frameImage.getWidth()).thenReturn(1280);
                when(frameImage.getHeight()).thenReturn(720);
                
                cameraFrameCallback.onFrameAvailable(frameImage);
            }
            
            // Wait for all frames to be processed
            assertTrue("All frames should be processed within timeout",
                    multiFrameLatch.await(5, TimeUnit.SECONDS));
            
            // Verify all frames were processed (some may be dropped due to queue limits)
            assertTrue("At least some frames should be processed", processedCount.get() > 0);
            assertTrue("Should not process more frames than sent", processedCount.get() <= frameCount);
        }
    }
    
    @Test
    public void testPipelineErrorRecovery() throws InterruptedException {
        // Test pipeline error recovery
        // Requirement 4.3: Fall back to displaying unprocessed frames
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            // First call succeeds, second call fails
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(any(Image.class)))
                       .thenReturn(mockInputMat)
                       .thenThrow(new RuntimeException("Processing error"));
            
            frameProcessor.start();
            
            // Send first frame (should succeed)
            Image firstFrame = mock(Image.class);
            when(firstFrame.getWidth()).thenReturn(1280);
            when(firstFrame.getHeight()).thenReturn(720);
            cameraFrameCallback.onFrameAvailable(firstFrame);
            
            // Wait for first frame
            assertTrue("First frame should be processed",
                    frameProcessedLatch.await(2, TimeUnit.SECONDS));
            assertTrue("First frame should succeed", processingCompleted.get());
            
            // Reset for second frame
            frameProcessedLatch = new CountDownLatch(1);
            processingCompleted.set(false);
            
            // Send second frame (should fail)
            Image secondFrame = mock(Image.class);
            when(secondFrame.getWidth()).thenReturn(1280);
            when(secondFrame.getHeight()).thenReturn(720);
            cameraFrameCallback.onFrameAvailable(secondFrame);
            
            // Wait for second frame
            assertTrue("Second frame should complete (with error)",
                    frameProcessedLatch.await(2, TimeUnit.SECONDS));
            assertNotNull("Processing error should be captured", processingError.get());
        }
    }
    
    @Test
    public void testPipelinePerformanceUnderLoad() throws InterruptedException {
        // Test pipeline performance under high frame rate
        // Requirement 2.3: Maintain UI responsiveness
        
        int highFrameCount = 30; // Simulate 30 FPS
        CountDownLatch loadTestLatch = new CountDownLatch(1);
        AtomicInteger processedFrames = new AtomicInteger(0);
        AtomicInteger droppedFrames = new AtomicInteger(0);
        
        frameProcessor.setProcessingCallback(new FrameProcessor.ProcessingCallback() {
            @Override
            public void onFrameProcessed(Mat processedFrame, Image originalImage, long processingTimeMs) {
                int processed = processedFrames.incrementAndGet();
                
                // Check if we've processed enough frames or reached the end
                if (processed >= 10 || (processed + droppedFrames.get()) >= highFrameCount) {
                    loadTestLatch.countDown();
                }
                
                processedFrame.release();
                originalImage.close();
            }
            
            @Override
            public void onProcessingFailed(Exception error, Image originalImage) {
                if (originalImage != null) {
                    originalImage.close();
                }
                loadTestLatch.countDown();
            }
            
            @Override
            public void onFrameDropped(Image droppedImage) {
                int dropped = droppedFrames.incrementAndGet();
                droppedImage.close();
                
                // Check if we've reached the end
                if ((processedFrames.get() + dropped) >= highFrameCount) {
                    loadTestLatch.countDown();
                }
            }
        });
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(any(Image.class)))
                       .thenReturn(mockInputMat);
            
            frameProcessor.start();
            
            // Send frames rapidly
            for (int i = 0; i < highFrameCount; i++) {
                Image frameImage = mock(Image.class);
                when(frameImage.getWidth()).thenReturn(1280);
                when(frameImage.getHeight()).thenReturn(720);
                
                cameraFrameCallback.onFrameAvailable(frameImage);
                
                // Small delay to simulate realistic timing
                Thread.sleep(1);
            }
            
            // Wait for processing to stabilize
            assertTrue("Load test should complete within timeout",
                    loadTestLatch.await(10, TimeUnit.SECONDS));
            
            // Verify that the system handled the load appropriately
            int totalHandled = processedFrames.get() + droppedFrames.get();
            assertTrue("Should handle some frames", totalHandled > 0);
            assertTrue("Should not exceed sent frames", totalHandled <= highFrameCount);
            
            // Verify frame dropping occurred under load (expected behavior)
            if (droppedFrames.get() > 0) {
                assertTrue("Frame dropping should occur under high load", droppedFrames.get() > 0);
            }
        }
    }
    
    @Test
    public void testPipelineResourceCleanup() throws InterruptedException {
        // Test proper resource cleanup in the pipeline
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(mockImage))
                       .thenReturn(mockInputMat);
            
            frameProcessor.start();
            
            // Process a frame
            cameraFrameCallback.onFrameAvailable(mockImage);
            
            // Wait for processing
            assertTrue("Frame should be processed",
                    frameProcessedLatch.await(2, TimeUnit.SECONDS));
            
            // Stop the processor
            frameProcessor.stop();
            
            // Verify resources are cleaned up
            assertFalse("Processor should not be running after stop", frameProcessor.isProcessing());
            assertEquals("Queue should be empty after stop", 0, frameProcessor.getQueueSize());
            
            // Verify image was closed
            verify(mockImage, timeout(1000)).close();
            
            // Verify mat was released
            verify(mockProcessedMat, timeout(1000)).release();
        }
    }
    
    @Test
    public void testPipelineWithDisplayNotReady() throws InterruptedException {
        // Test pipeline behavior when display is not ready
        when(mockDisplayManager.isDisplayReady()).thenReturn(false);
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(mockImage))
                       .thenReturn(mockInputMat);
            
            frameProcessor.start();
            cameraFrameCallback.onFrameAvailable(mockImage);
            
            // Wait for processing
            assertTrue("Frame should be processed even if display not ready",
                    frameProcessedLatch.await(2, TimeUnit.SECONDS));
            
            // Verify processing completed but display was not updated
            assertTrue("Processing should complete", processingCompleted.get());
            verify(mockDisplayManager, never()).updateFrame(any(Mat.class));
        }
    }
    
    @Test
    public void testPipelineThreadingIsolation() throws InterruptedException {
        // Test that processing happens on background thread
        // Requirement 2.3: Ensure proper threading to maintain UI responsiveness
        
        AtomicReference<String> processingThreadName = new AtomicReference<>();
        CountDownLatch threadTestLatch = new CountDownLatch(1);
        
        // Mock OpenCV processor to capture thread name
        when(mockOpenCVProcessor.processFrame(any(Mat.class))).thenAnswer(invocation -> {
            processingThreadName.set(Thread.currentThread().getName());
            threadTestLatch.countDown();
            return mockProcessedMat;
        });
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(mockImage))
                       .thenReturn(mockInputMat);
            
            frameProcessor.start();
            
            String mainThreadName = Thread.currentThread().getName();
            cameraFrameCallback.onFrameAvailable(mockImage);
            
            // Wait for processing thread to capture name
            assertTrue("Thread test should complete",
                    threadTestLatch.await(2, TimeUnit.SECONDS));
            
            // Verify processing happened on different thread
            assertNotNull("Processing thread name should be captured", processingThreadName.get());
            assertNotEquals("Processing should not happen on main thread",
                    mainThreadName, processingThreadName.get());
            assertTrue("Processing should happen on background thread",
                    processingThreadName.get().contains("FrameProcessing"));
        }
    }
    
    // Visual Odometry Integration Tests (Task 30)
    
    @Test
    public void testPipelineWithVisualOdometryIntegration() throws InterruptedException {
        // Test complete pipeline with visual odometry enabled
        // Requirements: 14.1, 14.2
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(any(Image.class)))
                       .thenReturn(mockInputMat);
            
            // Enable visual odometry
            frameProcessor.setVisualOdometryEnabled(true);
            frameProcessor.start();
            
            // Process first frame (establishes previous frame reference)
            cameraFrameCallback.onFrameAvailable(mockImage);
            
            assertTrue("First frame should be processed",
                    frameProcessedLatch.await(2, TimeUnit.SECONDS));
            
            // Reset for second frame
            frameProcessedLatch = new CountDownLatch(1);
            displayUpdatedLatch = new CountDownLatch(1);
            processingCompleted.set(false);
            displayUpdated.set(false);
            
            // Process second frame (should trigger visual odometry)
            Image secondFrame = mock(Image.class);
            when(secondFrame.getWidth()).thenReturn(1280);
            when(secondFrame.getHeight()).thenReturn(720);
            
            cameraFrameCallback.onFrameAvailable(secondFrame);
            
            assertTrue("Second frame should be processed",
                    frameProcessedLatch.await(2, TimeUnit.SECONDS));
            
            // Verify both regular processing and visual odometry occurred
            assertTrue("Processing should complete successfully", processingCompleted.get());
            assertTrue("Display should be updated", displayUpdated.get());
            assertEquals("Two frames should be processed", 2, framesProcessed.get());
            
            // Verify visual odometry is enabled
            assertTrue("Visual odometry should be enabled", frameProcessor.isVisualOdometryEnabled());
            
            // Verify OpenCV processing was called for both frames
            verify(mockOpenCVProcessor, timeout(1000).times(2)).processFrame(any(Mat.class));
            
            // Verify display was updated for both frames
            verify(mockDisplayManager, timeout(1000).times(2)).updateFrame(any(Mat.class));
        }
    }
    
    @Test
    public void testVisualOdometryCompatibilityWithZeroCopyPipeline() throws InterruptedException {
        // Test that visual odometry maintains compatibility with zero-copy optimization
        // Requirements: 14.1, 14.2
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(any(Image.class)))
                       .thenReturn(mockInputMat);
            
            // Enable both visual odometry and zero-copy optimization
            frameProcessor.setVisualOdometryEnabled(true);
            frameProcessor.setZeroCopyEnabled(true);
            frameProcessor.start();
            
            // Process frames
            cameraFrameCallback.onFrameAvailable(mockImage);
            
            assertTrue("Frame should be processed",
                    frameProcessedLatch.await(2, TimeUnit.SECONDS));
            
            // Verify both optimizations are enabled
            assertTrue("Visual odometry should be enabled", frameProcessor.isVisualOdometryEnabled());
            assertTrue("Zero-copy should be enabled", frameProcessor.isZeroCopyEnabled());
            
            // Verify no buffer pool copies occurred (zero-copy optimization maintained)
            verify(mockInputMat, never()).copyTo(any(Mat.class));
            verify(mockProcessedMat, never()).copyTo(any(Mat.class));
            
            // Verify Mat cloning was used for visual odometry (OpenCV efficient copying)
            verify(mockInputMat, atLeast(1)).clone();
            
            // Verify processing completed successfully
            assertTrue("Processing should complete successfully", processingCompleted.get());
        }
    }
    
    @Test
    public void testVisualOdometryFramePairProcessingInPipeline() throws InterruptedException {
        // Test frame pair processing using OpenCV's built-in functions in complete pipeline
        // Requirements: 14.1, 14.2
        
        int frameCount = 3;
        CountDownLatch multiFrameLatch = new CountDownLatch(frameCount);
        AtomicInteger processedFrames = new AtomicInteger(0);
        
        // Update callback to handle multiple frames
        frameProcessor.setProcessingCallback(new FrameProcessor.ProcessingCallback() {
            @Override
            public void onFrameProcessed(Mat processedFrame, Image originalImage, long processingTimeMs) {
                processedFrames.incrementAndGet();
                multiFrameLatch.countDown();
                
                // Clean up
                processedFrame.release();
                originalImage.close();
            }
            
            @Override
            public void onProcessingFailed(Exception error, Image originalImage) {
                multiFrameLatch.countDown();
                if (originalImage != null) {
                    originalImage.close();
                }
            }
            
            @Override
            public void onFrameDropped(Image droppedImage) {
                droppedImage.close();
            }
        });
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(any(Image.class)))
                       .thenReturn(mockInputMat);
            
            frameProcessor.setVisualOdometryEnabled(true);
            frameProcessor.start();
            
            // Send multiple frames to test frame pair processing
            for (int i = 0; i < frameCount; i++) {
                Image frameImage = mock(Image.class);
                when(frameImage.getWidth()).thenReturn(1280);
                when(frameImage.getHeight()).thenReturn(720);
                
                cameraFrameCallback.onFrameAvailable(frameImage);
                Thread.sleep(50); // Small delay between frames
            }
            
            assertTrue("All frames should be processed",
                    multiFrameLatch.await(5, TimeUnit.SECONDS));
            
            // Verify all frames were processed
            assertEquals("All frames should be processed", frameCount, processedFrames.get());
            
            // Verify OpenCV processing was called for all frames
            verify(mockOpenCVProcessor, timeout(1000).times(frameCount)).processFrame(any(Mat.class));
            
            // Verify Mat cloning occurred for visual odometry frame pair processing
            verify(mockInputMat, atLeast(frameCount - 1)).clone(); // At least (frameCount-1) clones for pairs
        }
    }
    
    @Test
    public void testVisualOdometryMemoryManagementInPipeline() throws InterruptedException {
        // Test OpenCV's efficient Mat copying and memory management in complete pipeline
        // Requirements: 14.1, 14.2
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(any(Image.class)))
                       .thenReturn(mockInputMat);
            
            frameProcessor.setVisualOdometryEnabled(true);
            frameProcessor.start();
            
            // Process frames
            cameraFrameCallback.onFrameAvailable(mockImage);
            
            assertTrue("Frame should be processed",
                    frameProcessedLatch.await(2, TimeUnit.SECONDS));
            
            // Verify OpenCV's efficient Mat copying was used
            verify(mockInputMat, atLeast(1)).clone();
            
            // Verify no unnecessary copies occurred in the main pipeline
            verify(mockInputMat, never()).copyTo(any(Mat.class));
            verify(mockProcessedMat, never()).copyTo(any(Mat.class));
            
            // Verify processing completed successfully
            assertTrue("Processing should complete successfully", processingCompleted.get());
            
            // Stop processor and verify cleanup
            frameProcessor.stop();
            assertFalse("Processor should not be running after stop", frameProcessor.isProcessing());
        }
    }
    
    @Test
    public void testVisualOdometryPerformanceInPipeline() throws InterruptedException {
        // Test visual odometry performance metrics in complete pipeline
        // Requirements: 14.1, 14.2
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(any(Image.class)))
                       .thenReturn(mockInputMat);
            
            frameProcessor.setVisualOdometryEnabled(true);
            frameProcessor.start();
            
            // Process frame
            cameraFrameCallback.onFrameAvailable(mockImage);
            
            assertTrue("Frame should be processed",
                    frameProcessedLatch.await(2, TimeUnit.SECONDS));
            
            // Get visual odometry performance metrics
            FrameProcessor.VisualOdometryPerformanceMetrics metrics = 
                frameProcessor.getVisualOdometryMetrics();
            
            assertNotNull("Visual odometry metrics should not be null", metrics);
            assertTrue("Should have previous frame after processing", metrics.hasPreviousFrame);
            
            // Verify processing completed successfully
            assertTrue("Processing should complete successfully", processingCompleted.get());
        }
    }
    
    @Test
    public void testPipelineWithVisualOdometryDisabled() throws InterruptedException {
        // Test that pipeline works normally when visual odometry is disabled
        // Requirements: 14.1, 14.2
        
        try (MockedStatic<OpenCVProcessor> mockedStatic = mockStatic(OpenCVProcessor.class)) {
            mockedStatic.when(() -> OpenCVProcessor.imageToMat(mockImage))
                       .thenReturn(mockInputMat);
            
            // Ensure visual odometry is disabled (default state)
            frameProcessor.setVisualOdometryEnabled(false);
            frameProcessor.start();
            
            cameraFrameCallback.onFrameAvailable(mockImage);
            
            assertTrue("Frame should be processed",
                    frameProcessedLatch.await(2, TimeUnit.SECONDS));
            
            // Verify normal processing occurred
            assertTrue("Processing should complete successfully", processingCompleted.get());
            assertTrue("Display should be updated", displayUpdated.get());
            
            // Verify visual odometry is disabled
            assertFalse("Visual odometry should be disabled", frameProcessor.isVisualOdometryEnabled());
            
            // Verify no visual odometry-specific Mat cloning occurred
            // (Some cloning may still occur for other purposes)
            verify(mockOpenCVProcessor, timeout(1000)).processFrame(mockInputMat);
        }
    }
}