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
    public void testPerformanceMonitorClassStructure() {
        // Test PerformanceMonitor class structure without instantiation
        try {
            // Test class loading
            Class<?> performanceMonitorClass = PerformanceMonitor.class;
            assertNotNull("PerformanceMonitor class should be loadable", performanceMonitorClass);
            
            // Test enum access
            PerformanceMonitor.PerformanceLevel highLevel = PerformanceMonitor.PerformanceLevel.HIGH;
            assertNotNull("HIGH performance level should exist", highLevel);
            
            // Test static inner class access
            PerformanceMonitor.PerformanceMetrics metrics = new PerformanceMonitor.PerformanceMetrics();
            assertNotNull("Should be able to create PerformanceMetrics", metrics);
            
            PerformanceMonitor.ProcessingRecommendation recommendation = new PerformanceMonitor.ProcessingRecommendation();
            assertNotNull("Should be able to create ProcessingRecommendation", recommendation);
            
            System.out.println("PerformanceMonitor class structure verification passed");
            
        } catch (Exception e) {
            fail("PerformanceMonitor class structure test failed: " + e.getMessage());
        }
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
        try {
            ErrorHandler errorHandler = new ErrorHandler(context);
            assertNotNull("ErrorHandler should be created", errorHandler);
            
            // Test error handling
            ErrorHandler.ErrorInfo cameraError = errorHandler.handleCameraHardwareError(1, "Test error", null);
            assertNotNull("Camera error should be handled", cameraError);
            assertEquals("Error category should be camera hardware", ErrorHandler.ErrorCategory.CAMERA_HARDWARE, cameraError.category);
            
            ErrorHandler.ErrorInfo processingError = errorHandler.handleOpenCVProcessingError(new RuntimeException("Test"), true);
            assertNotNull("Processing error should be handled", processingError);
            assertEquals("Error category should be processing", ErrorHandler.ErrorCategory.OPENCV_PROCESSING, processingError.category);
            
            // Clean up
            errorHandler.release();
            
            // Note: PerformanceMonitor integration skipped due to Android service dependencies in test environment
            System.out.println("ErrorHandler integration test passed. PerformanceMonitor skipped due to test environment limitations.");
            
        } catch (Exception e) {
            fail("ErrorHandler integration test failed: " + e.getMessage());
        }
    }
}