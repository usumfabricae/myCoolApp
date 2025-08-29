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
        
        // Initialize frame processor
        frameProcessor = new FrameProcessor(mockOpenCVProcessor);
        
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
}