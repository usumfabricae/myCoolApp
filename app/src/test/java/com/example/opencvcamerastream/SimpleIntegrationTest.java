package com.example.opencvcamerastream;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import com.example.opencvcamerastream.error.ErrorHandler;
import com.example.opencvcamerastream.error.PerformanceMonitor;
import com.example.opencvcamerastream.error.ErrorDialogManager;
import com.example.opencvcamerastream.processing.FrameBuffer;

import static org.junit.Assert.*;

/**
 * Simple integration tests to verify basic functionality
 */
@RunWith(RobolectricTestRunner.class)
public class SimpleIntegrationTest {
    
    private Context context;
    
    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }
    
    @Test
    public void testErrorHandlerCreation() {
        ErrorHandler errorHandler = new ErrorHandler(context);
        assertNotNull("ErrorHandler should be created", errorHandler);
        
        // Test basic functionality
        ErrorHandler.ErrorInfo errorInfo = errorHandler.handleCameraPermissionError(false);
        assertNotNull("ErrorInfo should be created", errorInfo);
        assertEquals("Error category should match", ErrorHandler.ErrorCategory.CAMERA_PERMISSION, errorInfo.category);
        
        // Clean up
        errorHandler.release();
    }
    
    @Test
    public void testPerformanceMonitorCreation() {
        PerformanceMonitor performanceMonitor = new PerformanceMonitor(context);
        assertNotNull("PerformanceMonitor should be created", performanceMonitor);
        
        // Test basic functionality
        PerformanceMonitor.PerformanceLevel level = performanceMonitor.getCurrentPerformanceLevel();
        assertNotNull("Performance level should not be null", level);
        
        PerformanceMonitor.PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
        assertNotNull("Metrics should not be null", metrics);
        
        // Clean up
        performanceMonitor.release();
    }
    
    @Test
    public void testErrorDialogManagerCreation() {
        ErrorDialogManager dialogManager = new ErrorDialogManager(context);
        assertNotNull("ErrorDialogManager should be created", dialogManager);
        
        // Test basic functionality (dialog showing will fail in test environment, but that's expected)
        assertFalse("Dialog should not be showing initially", dialogManager.isDialogShowing());
        
        // Clean up
        dialogManager.release();
    }
    
    @Test
    public void testFrameBufferCreation() {
        FrameBuffer frameBuffer = new FrameBuffer();
        assertNotNull("FrameBuffer should be created", frameBuffer);
        
        // Test basic functionality
        FrameBuffer.BufferStats stats = frameBuffer.getStats();
        assertNotNull("Buffer stats should not be null", stats);
        
        // Clean up
        frameBuffer.clear();
    }
    
    @Test
    public void testErrorHandlerIntegration() {
        ErrorHandler errorHandler = new ErrorHandler(context);
        PerformanceMonitor performanceMonitor = new PerformanceMonitor(context);
        
        // Test error handling
        ErrorHandler.ErrorInfo cameraError = errorHandler.handleCameraHardwareError(1, "Test error", null);
        assertNotNull("Camera error should be handled", cameraError);
        assertEquals("Error category should be camera hardware", ErrorHandler.ErrorCategory.CAMERA_HARDWARE, cameraError.category);
        
        ErrorHandler.ErrorInfo processingError = errorHandler.handleOpenCVProcessingError(new RuntimeException("Test"), true);
        assertNotNull("Processing error should be handled", processingError);
        assertEquals("Error category should be processing", ErrorHandler.ErrorCategory.OPENCV_PROCESSING, processingError.category);
        
        // Test performance monitoring
        performanceMonitor.recordProcessingTime(50);
        PerformanceMonitor.ProcessingRecommendation recommendation = performanceMonitor.getProcessingRecommendation();
        assertNotNull("Recommendation should not be null", recommendation);
        
        // Clean up
        errorHandler.release();
        performanceMonitor.release();
    }
}