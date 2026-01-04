package com.example.opencvcamerastream.build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for native library validation logic
 * Tests the validation rules that are implemented in the Gradle build scripts
 * Requirements: Req-1 (library inclusion), Req-5 (robust acquisition system)
 */
@RunWith(JUnit4.class)
public class NativeLibraryValidationTest {

    private static final String[] REQUIRED_LIBRARIES = {
        "libc++_shared.so",
        "libopencv_java4.so"
    };

    private static final String[] SUPPORTED_ARCHITECTURES = {
        "armeabi-v7a",
        "arm64-v8a",
        "x86",
        "x86_64"
    };

    private File testJniLibsDir;

    @Before
    public void setUp() {
        // Create a temporary test directory structure
        testJniLibsDir = new File(System.getProperty("java.io.tmpdir"), "test-jniLibs-" + System.currentTimeMillis());
        testJniLibsDir.mkdirs();
    }

    @Test
    public void testRequiredLibrariesList() {
        // Verify that we have the correct required libraries defined
        assertEquals("Should have exactly 2 required libraries", 2, REQUIRED_LIBRARIES.length);
        assertTrue("Should require libc++_shared.so", 
            Arrays.asList(REQUIRED_LIBRARIES).contains("libc++_shared.so"));
        assertTrue("Should require libopencv_java4.so", 
            Arrays.asList(REQUIRED_LIBRARIES).contains("libopencv_java4.so"));
    }

    @Test
    public void testSupportedArchitecturesList() {
        // Verify that we have all required architectures defined
        assertEquals("Should support exactly 4 architectures", 4, SUPPORTED_ARCHITECTURES.length);
        List<String> archList = Arrays.asList(SUPPORTED_ARCHITECTURES);
        assertTrue("Should support armeabi-v7a", archList.contains("armeabi-v7a"));
        assertTrue("Should support arm64-v8a", archList.contains("arm64-v8a"));
        assertTrue("Should support x86", archList.contains("x86"));
        assertTrue("Should support x86_64", archList.contains("x86_64"));
    }

    @Test
    public void testValidateLibraryPresence_AllPresent() {
        // Setup: Create all required libraries for all architectures
        for (String arch : SUPPORTED_ARCHITECTURES) {
            File archDir = new File(testJniLibsDir, arch);
            archDir.mkdirs();
            for (String lib : REQUIRED_LIBRARIES) {
                File libFile = new File(archDir, lib);
                try {
                    libFile.createNewFile();
                    // Write some dummy data to ensure file is not empty
                    java.nio.file.Files.write(libFile.toPath(), "dummy".getBytes());
                } catch (Exception e) {
                    fail("Failed to create test library file: " + e.getMessage());
                }
            }
        }

        // Validate
        ValidationResult result = validateLibraries(testJniLibsDir);
        
        assertTrue("Validation should pass when all libraries are present", result.isValid());
        assertEquals("Should have no errors", 0, result.getErrors().size());
        assertEquals("Should find all libraries", 
            SUPPORTED_ARCHITECTURES.length * REQUIRED_LIBRARIES.length, 
            result.getFoundLibrariesCount());
    }

    @Test
    public void testValidateLibraryPresence_MissingArchitecture() {
        // Setup: Create libraries for all architectures except x86
        for (String arch : SUPPORTED_ARCHITECTURES) {
            if (arch.equals("x86")) continue; // Skip x86
            
            File archDir = new File(testJniLibsDir, arch);
            archDir.mkdirs();
            for (String lib : REQUIRED_LIBRARIES) {
                File libFile = new File(archDir, lib);
                try {
                    libFile.createNewFile();
                    java.nio.file.Files.write(libFile.toPath(), "dummy".getBytes());
                } catch (Exception e) {
                    fail("Failed to create test library file: " + e.getMessage());
                }
            }
        }

        // Validate
        ValidationResult result = validateLibraries(testJniLibsDir);
        
        assertFalse("Validation should fail when an architecture is missing", result.isValid());
        assertEquals("Should have errors for missing x86 libraries", 
            REQUIRED_LIBRARIES.length, result.getErrors().size());
        assertTrue("Error message should mention x86", 
            result.getErrors().get(0).contains("x86"));
    }

    @Test
    public void testValidateLibraryPresence_MissingSpecificLibrary() {
        // Setup: Create all libraries except libc++_shared.so for arm64-v8a
        for (String arch : SUPPORTED_ARCHITECTURES) {
            File archDir = new File(testJniLibsDir, arch);
            archDir.mkdirs();
            for (String lib : REQUIRED_LIBRARIES) {
                if (arch.equals("arm64-v8a") && lib.equals("libc++_shared.so")) {
                    continue; // Skip this specific library
                }
                File libFile = new File(archDir, lib);
                try {
                    libFile.createNewFile();
                    java.nio.file.Files.write(libFile.toPath(), "dummy".getBytes());
                } catch (Exception e) {
                    fail("Failed to create test library file: " + e.getMessage());
                }
            }
        }

        // Validate
        ValidationResult result = validateLibraries(testJniLibsDir);
        
        assertFalse("Validation should fail when a specific library is missing", result.isValid());
        assertEquals("Should have exactly 1 error", 1, result.getErrors().size());
        assertTrue("Error should mention libc++_shared.so", 
            result.getErrors().get(0).contains("libc++_shared.so"));
        assertTrue("Error should mention arm64-v8a", 
            result.getErrors().get(0).contains("arm64-v8a"));
    }

    @Test
    public void testValidateLibraryPresence_EmptyLibraryFile() {
        // Setup: Create all libraries but leave one empty
        for (String arch : SUPPORTED_ARCHITECTURES) {
            File archDir = new File(testJniLibsDir, arch);
            archDir.mkdirs();
            for (String lib : REQUIRED_LIBRARIES) {
                File libFile = new File(archDir, lib);
                try {
                    libFile.createNewFile();
                    // Only write data for non-arm64-v8a libc++_shared.so
                    if (!(arch.equals("arm64-v8a") && lib.equals("libc++_shared.so"))) {
                        java.nio.file.Files.write(libFile.toPath(), "dummy".getBytes());
                    }
                    // Leave arm64-v8a/libc++_shared.so empty
                } catch (Exception e) {
                    fail("Failed to create test library file: " + e.getMessage());
                }
            }
        }

        // Validate
        ValidationResult result = validateLibraries(testJniLibsDir);
        
        assertFalse("Validation should fail when a library file is empty", result.isValid());
        assertTrue("Should have at least 1 error for empty file", result.getErrors().size() >= 1);
    }

    @Test
    public void testValidateLibraryPresence_NoLibrariesAtAll() {
        // Setup: Create a fresh empty jniLibs directory for this test
        File emptyTestDir = new File(System.getProperty("java.io.tmpdir"), 
            "test-empty-jniLibs-" + System.currentTimeMillis());
        emptyTestDir.mkdirs();

        // Validate
        ValidationResult result = validateLibraries(emptyTestDir);
        
        assertFalse("Validation should fail when no libraries are present", result.isValid());
        assertEquals("Should have errors for all libraries across all architectures", 
            SUPPORTED_ARCHITECTURES.length * REQUIRED_LIBRARIES.length, 
            result.getErrors().size());
        
        // Cleanup
        deleteDirectory(emptyTestDir);
    }

    @Test
    public void testValidateLibraryPresence_DirectoryDoesNotExist() {
        // Setup: Use a non-existent directory
        File nonExistentDir = new File(testJniLibsDir, "non-existent");

        // Validate
        ValidationResult result = validateLibraries(nonExistentDir);
        
        assertFalse("Validation should fail when directory does not exist", result.isValid());
        assertTrue("Should have errors", result.getErrors().size() > 0);
    }

    @Test
    public void testArchitectureSpecificValidation() {
        // Test that validation correctly identifies which architecture is missing libraries
        for (String missingArch : SUPPORTED_ARCHITECTURES) {
            // Setup: Create a fresh test directory
            File testDir = new File(System.getProperty("java.io.tmpdir"), 
                "test-jniLibs-arch-" + missingArch + "-" + System.currentTimeMillis());
            testDir.mkdirs();
            
            // Create libraries for all architectures except the missing one
            for (String arch : SUPPORTED_ARCHITECTURES) {
                if (arch.equals(missingArch)) continue;
                
                File archDir = new File(testDir, arch);
                archDir.mkdirs();
                for (String lib : REQUIRED_LIBRARIES) {
                    File libFile = new File(archDir, lib);
                    try {
                        libFile.createNewFile();
                        java.nio.file.Files.write(libFile.toPath(), "dummy".getBytes());
                    } catch (Exception e) {
                        fail("Failed to create test library file: " + e.getMessage());
                    }
                }
            }

            // Validate
            ValidationResult result = validateLibraries(testDir);
            
            assertFalse("Validation should fail for missing " + missingArch, result.isValid());
            
            // Check that all errors mention the missing architecture
            for (String error : result.getErrors()) {
                assertTrue("Error should mention missing architecture " + missingArch, 
                    error.contains(missingArch));
            }
            
            // Cleanup
            deleteDirectory(testDir);
        }
    }

    /**
     * Validates libraries in the given jniLibs directory
     * This mimics the logic in the Gradle validation task
     */
    private ValidationResult validateLibraries(File jniLibsDir) {
        List<String> errors = new ArrayList<>();
        int foundCount = 0;

        if (!jniLibsDir.exists()) {
            for (String arch : SUPPORTED_ARCHITECTURES) {
                for (String lib : REQUIRED_LIBRARIES) {
                    errors.add("Missing library: " + lib + " for architecture: " + arch);
                }
            }
            return new ValidationResult(false, errors, 0);
        }

        for (String arch : SUPPORTED_ARCHITECTURES) {
            File archDir = new File(jniLibsDir, arch);
            if (!archDir.exists()) {
                for (String lib : REQUIRED_LIBRARIES) {
                    errors.add("Missing library: " + lib + " for architecture: " + arch);
                }
                continue;
            }

            for (String lib : REQUIRED_LIBRARIES) {
                File libFile = new File(archDir, lib);
                if (!libFile.exists() || libFile.length() == 0) {
                    errors.add("Missing library: " + lib + " for architecture: " + arch);
                } else {
                    foundCount++;
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, foundCount);
    }

    /**
     * Helper method to recursively delete a directory
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    /**
     * Simple class to hold validation results
     */
    private static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final int foundLibrariesCount;

        public ValidationResult(boolean valid, List<String> errors, int foundLibrariesCount) {
            this.valid = valid;
            this.errors = errors;
            this.foundLibrariesCount = foundLibrariesCount;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public int getFoundLibrariesCount() {
            return foundLibrariesCount;
        }
    }
}
