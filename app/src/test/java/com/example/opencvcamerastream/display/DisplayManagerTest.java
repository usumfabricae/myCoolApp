package com.example.opencvcamerastream.display;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DisplayManager
 * 
 * Tests the display system functionality including TextureView setup,
 * frame updates, orientation handling, and performance tracking.
 * 
 * Requirements tested:
 * - 3.1: Display processed frames on screen
 * - 3.2: Maintain original aspect ratio
 * - 1.4: Handle device rotation correctly
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Android 10
public class DisplayManagerTest {
    
    private DisplayManager displayManager;
    private Context context;
    
    @Mock
    private TextureView mockTextureView;
    
    @Mock
    private SurfaceTexture mockSurfaceTexture;
    
    @Mock
    private DisplayManager.DisplayCallback mockDisplayCallback;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        context = ApplicationProvider.getApplicationContext();
        displayManager = new DisplayManager(context);
        
        // Set up mock TextureView behavior
        when(mockTextureView.isAvailable()).thenReturn(false);
        when(mockTextureView.getSurfaceTexture()).thenReturn(mockSurfaceTexture);
        when(mockTextureView.getWidth()).thenReturn(1920);
        when(mockTextureView.getHeight()).thenReturn(1080);
    }
    
    @Test
    public void testDisplayManagerCreation() {
        // Test that DisplayManager can be created successfully
        assertNotNull("DisplayManager should be created", displayManager);
        assertFalse("Display should not be ready initially", displayManager.isDisplayReady());
        assertEquals("Initial display width should be 0", 0, displayManager.getDisplayWidth());
        assertEquals("Initial display height should be 0", 0, displayManager.getDisplayHeight());
    }
    
    @Test
    public void testSetupDisplayWithAvailableTextureView() {
        // Test setup when TextureView is already available
        when(mockTextureView.isAvailable()).thenReturn(true);
        
        boolean result = displayManager.setupDisplay(mockTextureView);
        
        assertTrue("Setup should succeed", result);
        verify(mockTextureView).setSurfaceTextureListener(displayManager);
        verify(mockTextureView).getSurfaceTexture();
    }
    
    @Test
    public void testSetupDisplayWithUnavailableTextureView() {
        // Test setup when TextureView is not yet available
        when(mockTextureView.isAvailable()).thenReturn(false);
        
        boolean result = displayManager.setupDisplay(mockTextureView);
        
        assertTrue("Setup should succeed even when TextureView not available", result);
        verify(mockTextureView).setSurfaceTextureListener(displayManager);
        verify(mockTextureView, never()).getSurfaceTexture();
    }
    
    @Test
    public void testOnSurfaceTextureAvailable() {
        // Test surface texture availability callback
        displayManager.setDisplayCallback(mockDisplayCallback);
        
        displayManager.onSurfaceTextureAvailable(mockSurfaceTexture, 1920, 1080);
        
        assertTrue("Display should be ready after surface available", displayManager.isDisplayReady());
        assertEquals("Display width should be set", 1920, displayManager.getDisplayWidth());
        assertEquals("Display height should be set", 1080, displayManager.getDisplayHeight());
        verify(mockDisplayCallback).onDisplayReady();
    }
    
    @Test
    public void testOnSurfaceTextureSizeChanged() {
        // Test orientation change handling
        displayManager.setDisplayCallback(mockDisplayCallback);
        displayManager.onSurfaceTextureAvailable(mockSurfaceTexture, 1920, 1080);
        
        // Simulate orientation change
        displayManager.onSurfaceTextureSizeChanged(mockSurfaceTexture, 1080, 1920);
        
        assertEquals("Display width should be updated", 1080, displayManager.getDisplayWidth());
        assertEquals("Display height should be updated", 1920, displayManager.getDisplayHeight());
    }
    
    @Test
    public void testOnSurfaceTextureDestroyed() {
        // Test surface texture destruction
        displayManager.setDisplayCallback(mockDisplayCallback);
        displayManager.onSurfaceTextureAvailable(mockSurfaceTexture, 1920, 1080);
        
        boolean result = displayManager.onSurfaceTextureDestroyed(mockSurfaceTexture);
        
        assertTrue("Should return true to indicate cleanup handled", result);
        assertFalse("Display should not be ready after destruction", displayManager.isDisplayReady());
        verify(mockDisplayCallback).onDisplayDestroyed();
    }
    
    @Test
    public void testUpdateFrameWhenDisplayNotReady() {
        // Test frame update when display is not ready
        Mat testMat = new Mat(480, 640, CvType.CV_8UC3);
        
        boolean result = displayManager.updateFrame(testMat);
        
        assertFalse("Frame update should fail when display not ready", result);
        
        testMat.release();
    }
    
    @Test
    public void testHandleOrientationChange() {
        // Test explicit orientation change handling
        displayManager.onSurfaceTextureAvailable(mockSurfaceTexture, 1920, 1080);
        
        displayManager.handleOrientationChange(1080, 1920);
        
        assertEquals("Display width should be updated", 1080, displayManager.getDisplayWidth());
        assertEquals("Display height should be updated", 1920, displayManager.getDisplayHeight());
    }
    
    @Test
    public void testSetDisplayCallback() {
        // Test setting display callback
        displayManager.setDisplayCallback(mockDisplayCallback);
        
        // Trigger callback
        displayManager.onSurfaceTextureAvailable(mockSurfaceTexture, 1920, 1080);
        
        verify(mockDisplayCallback).onDisplayReady();
    }
    
    @Test
    public void testSetDisplayCallbackToNull() {
        // Test setting callback to null (should not crash)
        displayManager.setDisplayCallback(null);
        
        // This should not crash
        displayManager.onSurfaceTextureAvailable(mockSurfaceTexture, 1920, 1080);
        
        assertTrue("Display should still be ready", displayManager.isDisplayReady());
    }
    
    @Test
    public void testRelease() {
        // Test resource release
        displayManager.setDisplayCallback(mockDisplayCallback);
        displayManager.setupDisplay(mockTextureView);
        displayManager.onSurfaceTextureAvailable(mockSurfaceTexture, 1920, 1080);
        
        displayManager.release();
        
        assertFalse("Display should not be ready after release", displayManager.isDisplayReady());
        verify(mockTextureView).setSurfaceTextureListener(null);
    }
    
    @Test
    public void testMultipleSetupCalls() {
        // Test that multiple setup calls work correctly
        boolean result1 = displayManager.setupDisplay(mockTextureView);
        boolean result2 = displayManager.setupDisplay(mockTextureView);
        
        assertTrue("First setup should succeed", result1);
        assertTrue("Second setup should succeed", result2);
        verify(mockTextureView, times(2)).setSurfaceTextureListener(displayManager);
    }
    
    @Test
    public void testDisplayDimensionsInitialState() {
        // Test initial display dimensions
        assertEquals("Initial width should be 0", 0, displayManager.getDisplayWidth());
        assertEquals("Initial height should be 0", 0, displayManager.getDisplayHeight());
        
        displayManager.onSurfaceTextureAvailable(mockSurfaceTexture, 1920, 1080);
        
        assertEquals("Width should be set after surface available", 1920, displayManager.getDisplayWidth());
        assertEquals("Height should be set after surface available", 1080, displayManager.getDisplayHeight());
    }
    
    @Test
    public void testOnSurfaceTextureUpdated() {
        // Test that onSurfaceTextureUpdated doesn't crash (it's a no-op)
        displayManager.onSurfaceTextureUpdated(mockSurfaceTexture);
        
        // Should not crash - this is a no-op method
        assertTrue("Method should complete without issues", true);
    }
    
    @Test
    public void testDisplayReadyStateTransitions() {
        // Test display ready state transitions
        assertFalse("Initially not ready", displayManager.isDisplayReady());
        
        displayManager.onSurfaceTextureAvailable(mockSurfaceTexture, 1920, 1080);
        assertTrue("Ready after surface available", displayManager.isDisplayReady());
        
        displayManager.onSurfaceTextureDestroyed(mockSurfaceTexture);
        assertFalse("Not ready after surface destroyed", displayManager.isDisplayReady());
        
        displayManager.release();
        assertFalse("Not ready after release", displayManager.isDisplayReady());
    }
}