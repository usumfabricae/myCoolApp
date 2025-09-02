package com.example.opencvcamerastream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

/**
 * Very basic test to ensure test framework is working
 */
@RunWith(RobolectricTestRunner.class)
public class BasicMockTest {
    
    @Test
    public void testBasicAssertion() {
        // This test should always pass
        assertTrue("Basic assertion should pass", true);
        assertFalse("Basic assertion should pass", false);
        assertEquals("String equality should work", "test", "test");
        assertNotNull("Object should not be null", new Object());
    }
    
    @Test
    public void testMathOperations() {
        // Test basic math to ensure JVM is working
        assertEquals("Addition should work", 4, 2 + 2);
        assertEquals("Multiplication should work", 6, 2 * 3);
        assertTrue("Comparison should work", 5 > 3);
    }
}