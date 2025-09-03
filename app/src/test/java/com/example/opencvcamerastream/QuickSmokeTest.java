package com.example.opencvcamerastream;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import com.example.opencvcamerastream.error.ErrorHandler;
import com.example.opencvcamerastream.error.ErrorDialogManager;
import com.example.opencvcamerastream.processing.FrameBuffer;

import static org.junit.Assert.*;

/**
 * Quick smoke test to verify basic functionality without problematic dependencies
 */
@RunWith(RobolectricTestRunner.class)
public class QuickSmokeTest {
    
    private Context context;
    
    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }
    
    @Test
    public void testBasicSetup() {
        assertNotNull("Context should be available", context);
    }
    
    @Test
    public void testErrorHandlerWorks() {
        ErrorHandler errorHandler = new ErrorHandler(context);
        assertNotNull("ErrorHandler should be created", errorHandler);
        
        ErrorHandler.ErrorInfo errorInfo = errorHandler.handleCameraPermissionError(false);
        assertNotNull("ErrorInfo should be created", errorInfo);
        
        errorHandler.release();
    }
    
    @Test
    public void testErrorDialogManagerWorks() {
        ErrorDialogManager dialogManager = new ErrorDialogManager(context);
        assertNotNull("ErrorDialogManager should be created", dialogManager);
        
        assertFalse("Dialog should not be showing initially", dialogManager.isDialogShowing());
        
        dialogManager.release();
    }
    
    @Test
    public void testFrameBufferWorks() {
        FrameBuffer frameBuffer = new FrameBuffer();
        assertNotNull("FrameBuffer should be created", frameBuffer);
        
        FrameBuffer.BufferStats stats = frameBuffer.getStats();
        assertNotNull("Buffer stats should not be null", stats);
        
        frameBuffer.clear();
    }
}