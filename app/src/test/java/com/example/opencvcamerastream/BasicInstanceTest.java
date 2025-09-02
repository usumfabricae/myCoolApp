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
        try {
            PerformanceMonitor performanceMonitor = new PerformanceMonitor(context);
            assertNotNull("PerformanceMonitor should be created successfully", performanceMonitor);
            
            // Test basic functionality
            PerformanceMonitor.PerformanceLevel level = performanceMonitor.getCurrentPerformanceLevel();
            assertNotNull("Performance level should not be null", level);
            
            // Clean up
            performanceMonitor.release();
        } catch (Exception e) {
            fail("PerformanceMonitor creation failed: " + e.getMessage());
        }
    }
}