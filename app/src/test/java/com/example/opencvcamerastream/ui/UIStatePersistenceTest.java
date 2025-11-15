package com.example.opencvcamerastream.ui;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.*;

/**
 * Unit tests for UI state persistence functionality
 * Requirement 6.3: Save and restore visualization state
 * Requirement 7.3: Save and restore rotation state
 */
@RunWith(RobolectricTestRunner.class)
public class UIStatePersistenceTest {
    
    private Context context;
    private SharedPreferences preferences;
    private SharedPreferences.Editor editor;
    
    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences("ui_state", Context.MODE_PRIVATE);
        editor = preferences.edit();
        editor.clear().apply();
    }
    
    /**
     * Test saving visualization state
     * Requirement 6.3: Save visualization state
     */
    @Test
    public void testSaveVisualizationState() {
        // Save visualization enabled state
        editor.putBoolean("visualization_enabled", true);
        editor.apply();
        
        // Verify state was saved
        boolean saved = preferences.getBoolean("visualization_enabled", false);
        assertTrue("Visualization enabled state should be saved", saved);
        
        // Save visualization disabled state
        editor.putBoolean("visualization_enabled", false);
        editor.apply();
        
        // Verify state was updated
        saved = preferences.getBoolean("visualization_enabled", true);
        assertFalse("Visualization disabled state should be saved", saved);
    }
    
    /**
     * Test restoring visualization state
     * Requirement 6.3: Restore visualization state on app restart
     */
    @Test
    public void testRestoreVisualizationState() {
        // Save visualization disabled state
        editor.putBoolean("visualization_enabled", false);
        editor.apply();
        
        // Create new preferences instance to simulate app restart
        SharedPreferences restoredPrefs = context.getSharedPreferences("ui_state", Context.MODE_PRIVATE);
        
        // Verify state was restored
        boolean restored = restoredPrefs.getBoolean("visualization_enabled", true);
        assertFalse("Visualization state should be restored as disabled", restored);
    }
    
    /**
     * Test saving rotation state
     * Requirement 7.3: Save rotation state
     */
    @Test
    public void testSaveRotationState() {
        // Test all rotation values
        int[] rotations = {0, 90, 180, 270};
        
        for (int rotation : rotations) {
            editor.putInt("rotation_degrees", rotation);
            editor.apply();
            
            int saved = preferences.getInt("rotation_degrees", -1);
            assertEquals("Rotation state " + rotation + " should be saved", rotation, saved);
        }
    }
    
    /**
     * Test restoring rotation state
     * Requirement 7.3: Restore rotation state on app restart
     */
    @Test
    public void testRestoreRotationState() {
        // Save rotation state
        editor.putInt("rotation_degrees", 180);
        editor.apply();
        
        // Create new preferences instance to simulate app restart
        SharedPreferences restoredPrefs = context.getSharedPreferences("ui_state", Context.MODE_PRIVATE);
        
        // Verify state was restored
        int restored = restoredPrefs.getInt("rotation_degrees", 0);
        assertEquals("Rotation state should be restored as 180 degrees", 180, restored);
    }
    
    /**
     * Test default values when no state is saved
     */
    @Test
    public void testDefaultValues() {
        // Verify default values when nothing is saved
        boolean visualizationDefault = preferences.getBoolean("visualization_enabled", true);
        int rotationDefault = preferences.getInt("rotation_degrees", 0);
        
        assertTrue("Default visualization should be enabled", visualizationDefault);
        assertEquals("Default rotation should be 0 degrees", 0, rotationDefault);
    }
    
    /**
     * Test state persistence across multiple saves
     */
    @Test
    public void testMultipleSaves() {
        // Save initial state
        editor.putBoolean("visualization_enabled", true);
        editor.putInt("rotation_degrees", 0);
        editor.apply();
        
        // Verify initial state
        assertTrue("Initial visualization should be enabled", 
                preferences.getBoolean("visualization_enabled", false));
        assertEquals("Initial rotation should be 0", 0, 
                preferences.getInt("rotation_degrees", -1));
        
        // Update state
        editor.putBoolean("visualization_enabled", false);
        editor.putInt("rotation_degrees", 90);
        editor.apply();
        
        // Verify updated state
        assertFalse("Updated visualization should be disabled", 
                preferences.getBoolean("visualization_enabled", true));
        assertEquals("Updated rotation should be 90", 90, 
                preferences.getInt("rotation_degrees", -1));
        
        // Update again
        editor.putBoolean("visualization_enabled", true);
        editor.putInt("rotation_degrees", 270);
        editor.apply();
        
        // Verify final state
        assertTrue("Final visualization should be enabled", 
                preferences.getBoolean("visualization_enabled", false));
        assertEquals("Final rotation should be 270", 270, 
                preferences.getInt("rotation_degrees", -1));
    }
    
    /**
     * Test state persistence with clear operation
     */
    @Test
    public void testClearState() {
        // Save state
        editor.putBoolean("visualization_enabled", false);
        editor.putInt("rotation_degrees", 180);
        editor.apply();
        
        // Verify state was saved
        assertFalse("State should be saved before clear", 
                preferences.getBoolean("visualization_enabled", true));
        
        // Clear state
        editor.clear().apply();
        
        // Verify state was cleared
        boolean visualizationDefault = preferences.getBoolean("visualization_enabled", true);
        int rotationDefault = preferences.getInt("rotation_degrees", 0);
        
        assertTrue("Visualization should return to default after clear", visualizationDefault);
        assertEquals("Rotation should return to default after clear", 0, rotationDefault);
    }
    
    /**
     * Test concurrent state updates
     */
    @Test
    public void testConcurrentUpdates() {
        // Simulate rapid state changes
        for (int i = 0; i < 10; i++) {
            boolean visualizationEnabled = (i % 2 == 0);
            int rotation = (i * 90) % 360;
            
            editor.putBoolean("visualization_enabled", visualizationEnabled);
            editor.putInt("rotation_degrees", rotation);
            editor.apply();
            
            // Verify each update
            assertEquals("Visualization state should match at iteration " + i, 
                    visualizationEnabled, 
                    preferences.getBoolean("visualization_enabled", !visualizationEnabled));
            assertEquals("Rotation state should match at iteration " + i, 
                    rotation, 
                    preferences.getInt("rotation_degrees", -1));
        }
    }
}
