package com.example.opencvcamerastream;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import com.example.opencvcamerastream.error.PerformanceMonitor;

import static org.junit.Assert.*;

/**
 * Basic instance creation tests to verify core classes can be instantiated
 */
@RunWith(RobolectricTestRunner.class)
public class BasicInstanceTest {
    
    private Context context;
    
    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }
    
    @Test
    public void testCanCreatePerformanceMonitor() {
        // Test that PerformanceMonitor can be created successfully
        PerformanceMonitor performanceMonitor = new PerformanceMonitor(context);
        assertNotNull("PerformanceMonitor should be created successfully", performanceMonitor);
        
        // Test basic functionality
        assertEquals("Initial performance level should be HIGH or LOW based on device", 
                    true, performanceMonitor.getCurrentPerformanceLevel() != null);
        
        // Clean up
        performanceMonitor.release();
    }
}