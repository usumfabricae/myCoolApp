package com.example.opencvcamerastream.display;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DisplayManager
 * Requirements: 3.1, 3.2, 3.3, 1.4
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29) // Test on Android 10
public class DisplayManagerTest {

    @Mock
    private Context mockContext;
    
    @Mock
    private TextureView mockTextureView;
    
    @Mock
    private SurfaceTexture mockSurfaceTexture;
    
    private DisplayManager displayManager;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        displayManager = new DisplayManager(mockContext);
    }

    @Test
    public void testDisplaySetup() {
        // Test display setup (Requirement 3.1)
        when(mockTextureView.getSurfaceTexture()).thenReturn(mockSurfaceTexture);
        
        boolean result = displayManager.setupDisplay(mockTextureView);
        
        assertTrue("Display setup should succeed", result);
        assertEquals("TextureView should be set", mockTextureView, displayManager.getTextureView());
    }

    @Test
    public void testFrameUpdate() {
        // Test frame update functionality (Requirement 3.1)
        displayManager.setupDisplay(mockTextureView);
        Bitmap testBitmap = createTestBitmap();
        
        boolean result = displayManager.updateFrame(testBitmap);
        
        assertTrue("Frame update should succeed", result);
    }

    @Test
    public void testAspectRatioMaintenance() {
        // Test aspect ratio maintenance (Requirement 3.2)
        displayManager.setupDisplay(mockTextureView);
        
        int originalWidth = 1920;
        int originalHeight = 1080;
        
        displayManager.setOriginalDimensions(originalWidth, originalHeight);
        
        assertEquals("Original width should be maintained", 
                    originalWidth, displayManager.getOriginalWidth());
        assertEquals("Original height should be maintained", 
                    originalHeight, displayManager.getOriginalHeight());
    }

    @Test
    public void testOrientationChange() {
        // Test orientation change handling (Requirement 1.4)
        displayManager.setupDisplay(mockTextureView);
        
        int initialRotation = displayManager.getDisplayRotation();
        displayManager.handleOrientationChange(90);
        
        assertNotEquals("Display rotation should change", 
                       initialRotation, displayManager.getDisplayRotation());
    }

    @Test
    public void testDisplayUpdatePerformance() {
        // Test display update performance (Requirement 3.3)
        displayManager.setupDisplay(mockTextureView);
        Bitmap testBitmap = createTestBitmap();
        
        long startTime = System.currentTimeMillis();
        displayManager.updateFrame(testBitmap);
        long updateTime = System.currentTimeMillis() - startTime;
        
        // Display update should complete within 16ms for 60 FPS
        assertTrue("Display update should complete within 16ms", updateTime < 16);
    }

    @Test
    public void testNullFrameHandling() {
        // Test null frame handling
        displayManager.setupDisplay(mockTextureView);
        
        boolean result = displayManager.updateFrame(null);
        
        assertFalse("Should handle null frame gracefully", result);
    }

    @Test
    public void testTextureViewListener() {
        // Test TextureView surface texture listener
        TextureView.SurfaceTextureListener listener = displayManager.getSurfaceTextureListener();
        
        assertNotNull("Surface texture listener should not be null", listener);
        
        // Test listener callbacks
        listener.onSurfaceTextureAvailable(mockSurfaceTexture, 100, 100);
        assertTrue("Should handle surface available", displayManager.isSurfaceAvailable());
        
        boolean destroyed = listener.onSurfaceTextureDestroyed(mockSurfaceTexture);
        assertFalse("Should handle surface destruction", displayManager.isSurfaceAvailable());
    }

    @Test
    public void testDisplayScaling() {
        // Test display scaling for different screen sizes
        displayManager.setupDisplay(mockTextureView);
        
        displayManager.setDisplayDimensions(800, 600);
        displayManager.setOriginalDimensions(1920, 1080);
        
        float scaleX = displayManager.getScaleX();
        float scaleY = displayManager.getScaleY();
        
        assertTrue("Scale X should be positive", scaleX > 0);
        assertTrue("Scale Y should be positive", scaleY > 0);
    }

    @Test
    public void testHardwareAcceleration() {
        // Test hardware acceleration availability
        when(mockTextureView.isHardwareAccelerated()).thenReturn(true);
        displayManager.setupDisplay(mockTextureView);
        
        boolean hwAccelerated = displayManager.isHardwareAccelerated();
        
        assertTrue("Hardware acceleration should be available", hwAccelerated);
    }

    @Test
    public void testDisplayCleanup() {
        // Test display cleanup
        displayManager.setupDisplay(mockTextureView);
        
        displayManager.cleanup();
        
        assertNull("TextureView should be null after cleanup", displayManager.getTextureView());
        assertFalse("Surface should not be available after cleanup", displayManager.isSurfaceAvailable());
    }

    @Test
    public void testFrameRateTracking() {
        // Test frame rate tracking for performance monitoring
        displayManager.setupDisplay(mockTextureView);
        Bitmap testBitmap = createTestBitmap();
        
        // Update multiple frames
        for (int i = 0; i < 5; i++) {
            displayManager.updateFrame(testBitmap);
            try {
                Thread.sleep(16); // Simulate 60 FPS timing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        float fps = displayManager.getCurrentFPS();
        assertTrue("FPS should be tracked", fps >= 0);
    }

    private Bitmap createTestBitmap() {
        return Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
    }
}