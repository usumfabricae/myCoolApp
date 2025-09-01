package com.example.opencvcamerastream.error;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.*;

/**
 * Basic test to verify we can create instances
 */
@RunWith(RobolectricTestRunner.class)
public class BasicInstanceTest {
    
    private Context context;
    
    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }
    
    @Test
    public void testCanCreateErrorHandler() {
        ErrorHandler errorHandler = new ErrorHandler(context);
        assertNotNull("ErrorHandler should be created", errorHandler);
    }
    
    @Test
    public void testCanCreatePerformanceMonitor() {
        PerformanceMonitor performanceMonitor = new PerformanceMonitor(context);
        assertNotNull("PerformanceMonitor should be created", performanceMonitor);
    }
}