package com.example.opencvcamerastream.error;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ErrorHandler
 * 
 * Tests error handling and recovery mechanisms:
 * - Camera permission error handling
 * - Camera hardware error handling with retry
 * - OpenCV processing error handling with fallback
 * - Memory pressure handling with graceful degradation
 * - Automatic retry mechanisms with exponential backoff
 */
@RunWith(RobolectricTestRunner.class)
public class ErrorHandlerTest {
    
    private ErrorHandler errorHandler;
    private Context context;
    
    @Mock
    private ErrorHandler.ErrorCallback mockCallback;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        context = RuntimeEnvironment.getApplication();
        errorHandler = new ErrorHandler(context);
        errorHandler.setErrorCallback(mockCallback);
    }
    
    @Test
    public void testCameraPermissionError_NotPermanentlyDenied() {
        // Test camera permission error handling
        ErrorHandler.ErrorInfo errorInfo = errorHandler.handleCameraPermissionError(false);
        
        assertNotNull(errorInfo);
        assertEquals(ErrorHandler.ErrorCategory.CAMERA_PERMISSION, errorInfo.category);
        assertEquals(ErrorHandler.ErrorSeverity.MEDIUM, errorInfo.severity);
        assertEquals(ErrorHandler.RecoveryStrategy.RETRY, errorInfo.recoveryStrategy);
        assertTrue(errorInfo.userMessage.contains("permission"));
        
        // Verify callback was called
        verify(mockCallback).onError(errorInfo);
    }
    
    @Test
    public void testCameraPermissionError_PermanentlyDenied() {
        // Test permanently denied camera permission
        ErrorHandler.ErrorInfo errorInfo = errorHandler.handleCameraPermissionError(true);
        
        assertNotNull(errorInfo);
        assertEquals(ErrorHandler.ErrorCategory.CAMERA_PERMISSION, errorInfo.category);
        assertEquals(ErrorHandler.ErrorSeverity.HIGH, errorInfo.severity);
        assertEquals(ErrorHandler.RecoveryStrategy.USER_INTERVENTION, errorInfo.recoveryStrategy);
        assertTrue(errorInfo.userMessage.contains("Settings"));
        
        verify(mockCallback).onError(errorInfo);
    }
    
    @Test
    public void testCameraHardwareError() {
        // Test camera hardware error handling
        int errorCode = 1; // ERROR_CAMERA_DEVICE
        String errorMessage = "Camera device error";
        Exception cause = new RuntimeException("Test exception");
        
        ErrorHandler.ErrorInfo errorInfo = errorHandler.handleCameraHardwareError(
                errorCode, errorMessage, cause);
        
        assertNotNull(errorInfo);
        assertEquals(ErrorHandler.ErrorCategory.CAMERA_HARDWARE, errorInfo.category);
        assertEquals(ErrorHandler.ErrorSeverity.HIGH, errorInfo.severity);
        assertEquals(ErrorHandler.RecoveryStrategy.RETRY_WITH_BACKOFF, errorInfo.recoveryStrategy);
        assertEquals(cause, errorInfo.cause);
        assertTrue(errorInfo.message.contains(errorMessage));
        
        verify(mockCallback).onError(errorInfo);
    }
    
    @Test
    public void testOpenCVProcessingError_WithFallback() {
        // Test OpenCV processing error with fallback capability
        Exception processingError = new RuntimeException("Processing failed");
        
        ErrorHandler.ErrorInfo errorInfo = errorHandler.handleOpenCVProcessingError(
                processingError, true);
        
        assertNotNull(errorInfo);
        assertEquals(ErrorHandler.ErrorCategory.OPENCV_PROCESSING, errorInfo.category);
        assertEquals(ErrorHandler.ErrorSeverity.MEDIUM, errorInfo.severity);
        assertEquals(ErrorHandler.RecoveryStrategy.FALLBACK, errorInfo.recoveryStrategy);
        assertEquals(processingError, errorInfo.cause);
        assertTrue(errorInfo.userMessage.contains("original camera feed"));
        
        verify(mockCallback).onError(errorInfo);
    }
    
    @Test
    public void testOpenCVProcessingError_WithoutFallback() {
        // Test OpenCV processing error without fallback capability
        Exception processingError = new RuntimeException("Processing failed");
        
        ErrorHandler.ErrorInfo errorInfo = errorHandler.handleOpenCVProcessingError(
                processingError, false);
        
        assertNotNull(errorInfo);
        assertEquals(ErrorHandler.ErrorCategory.OPENCV_PROCESSING, errorInfo.category);
        assertEquals(ErrorHandler.ErrorSeverity.MEDIUM, errorInfo.severity);
        assertEquals(ErrorHandler.RecoveryStrategy.RETRY, errorInfo.recoveryStrategy);
        assertEquals(processingError, errorInfo.cause);
        
        verify(mockCallback).onError(errorInfo);
    }
    
    @Test
    public void testMemoryPressure_MediumSeverity() {
        // Test memory pressure handling - medium severity
        long currentMemory = 850 * 1024 * 1024; // 850MB
        long maxMemory = 1024 * 1024 * 1024; // 1GB
        
        ErrorHandler.ErrorInfo errorInfo = errorHandler.handleMemoryPressure(
                currentMemory, maxMemory);
        
        assertNotNull(errorInfo);
        assertEquals(ErrorHandler.ErrorCategory.MEMORY_PRESSURE, errorInfo.category);
        assertEquals(ErrorHandler.ErrorSeverity.MEDIUM, errorInfo.severity);
        assertEquals(ErrorHandler.RecoveryStrategy.GRACEFUL_DEGRADATION, errorInfo.recoveryStrategy);
        assertTrue(errorInfo.message.contains("83.0%")); // 850/1024 * 100
        
        verify(mockCallback).onError(errorInfo);
        assertTrue(errorHandler.isPerformanceDegraded());
    }
    
    @Test
    public void testMemoryPressure_HighSeverity() {
        // Test memory pressure handling - high severity
        long currentMemory = 950 * 1024 * 1024; // 950MB
        long maxMemory = 1024 * 1024 * 1024; // 1GB
        
        ErrorHandler.ErrorInfo errorInfo = errorHandler.handleMemoryPressure(
                currentMemory, maxMemory);
        
        assertNotNull(errorInfo);
        assertEquals(ErrorHandler.ErrorCategory.MEMORY_PRESSURE, errorInfo.category);
        assertEquals(ErrorHandler.ErrorSeverity.HIGH, errorInfo.severity);
        assertEquals(ErrorHandler.RecoveryStrategy.GRACEFUL_DEGRADATION, errorInfo.recoveryStrategy);
        
        verify(mockCallback).onError(errorInfo);
        assertTrue(errorHandler.isPerformanceDegraded());
    }
    
    @Test
    public void testDisplayError() {
        // Test display error handling
        Exception displayError = new RuntimeException("Display failed");
        
        ErrorHandler.ErrorInfo errorInfo = errorHandler.handleDisplayError(displayError);
        
        assertNotNull(errorInfo);
        assertEquals(ErrorHandler.ErrorCategory.DISPLAY_ERROR, errorInfo.category);
        assertEquals(ErrorHandler.ErrorSeverity.MEDIUM, errorInfo.severity);
        assertEquals(ErrorHandler.RecoveryStrategy.RETRY, errorInfo.recoveryStrategy);
        assertEquals(displayError, errorInfo.cause);
        
        verify(mockCallback).onError(errorInfo);
    }
    
    @Test
    public void testSystemError() {
        // Test system error handling
        Exception systemError = new RuntimeException("System failure");
        String context = "test context";
        
        ErrorHandler.ErrorInfo errorInfo = errorHandler.handleSystemError(systemError, context);
        
        assertNotNull(errorInfo);
        assertEquals(ErrorHandler.ErrorCategory.SYSTEM_ERROR, errorInfo.category);
        assertEquals(ErrorHandler.ErrorSeverity.HIGH, errorInfo.severity);
        assertEquals(ErrorHandler.RecoveryStrategy.RETRY, errorInfo.recoveryStrategy);
        assertEquals(systemError, errorInfo.cause);
        assertTrue(errorInfo.message.contains(context));
        
        verify(mockCallback).onError(errorInfo);
    }
    
    @Test
    public void testResetErrorCounters() {
        // First trigger some errors to set performance degradation
        errorHandler.handleMemoryPressure(950 * 1024 * 1024, 1024 * 1024 * 1024);
        assertTrue(errorHandler.isPerformanceDegraded());
        
        // Reset counters
        errorHandler.resetErrorCounters();
        
        // Verify performance degradation is lifted
        assertFalse(errorHandler.isPerformanceDegraded());
        assertEquals(0, errorHandler.getCameraRetryCount());
        assertEquals(0, errorHandler.getProcessingRetryCount());
    }
    
    @Test
    public void testRetryConfiguration() {
        // Test custom retry configuration
        ErrorHandler.RetryConfig customConfig = new ErrorHandler.RetryConfig(5, 500);
        ErrorHandler customErrorHandler = new ErrorHandler(context, customConfig);
        
        assertNotNull(customErrorHandler);
        assertEquals(0, customErrorHandler.getCameraRetryCount());
        assertEquals(0, customErrorHandler.getProcessingRetryCount());
    }
    
    @Test
    public void testErrorInfoTimestamp() {
        // Test that error info contains timestamp
        long beforeTime = System.currentTimeMillis();
        
        ErrorHandler.ErrorInfo errorInfo = errorHandler.handleDisplayError(
                new RuntimeException("Test"));
        
        long afterTime = System.currentTimeMillis();
        
        assertTrue(errorInfo.timestamp >= beforeTime);
        assertTrue(errorInfo.timestamp <= afterTime);
    }
    
    @Test
    public void testConsecutiveErrorTracking() {
        // Test that consecutive errors trigger performance degradation
        assertFalse(errorHandler.isPerformanceDegraded());
        
        // Trigger multiple processing errors
        for (int i = 0; i < 6; i++) {
            errorHandler.handleOpenCVProcessingError(
                    new RuntimeException("Error " + i), true);
        }
        
        // Should trigger performance degradation after multiple errors
        assertTrue(errorHandler.isPerformanceDegraded());
    }
    
    @Test
    public void testErrorCallback_Null() {
        // Test that null callback doesn't cause issues
        errorHandler.setErrorCallback(null);
        
        // Should not throw exception
        ErrorHandler.ErrorInfo errorInfo = errorHandler.handleDisplayError(
                new RuntimeException("Test"));
        
        assertNotNull(errorInfo);
    }
}