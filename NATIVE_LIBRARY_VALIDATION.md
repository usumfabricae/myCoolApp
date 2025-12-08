# Native Library Validation System

## Overview

The Native Library Validation system ensures that all required OpenCV native libraries are present before APK packaging. This prevents runtime errors caused by missing native libraries and provides clear, actionable error messages when libraries are missing.

**Requirements Addressed:**
- **Req-1**: Automatic inclusion of all required native libraries in the APK
- **Req-5**: Robust library acquisition system with validation

## Components

### 1. Gradle Build-Time Validation

#### App Module Validation (`app/build.gradle`)

The `validateNativeLibraries` Gradle task runs automatically before any packaging or assembly tasks:

```gradle
task validateNativeLibraries {
    description = 'Validates that all required OpenCV native libraries are present'
    group = 'verification'
    // ... validation logic
}
```

**Features:**
- Checks for required libraries: `libc++_shared.so`, `libopencv_java4.so`
- Validates all supported architectures: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`
- Scans both app and opencv module jniLibs directories
- Detects duplicate libraries and warns (handled by packagingOptions)
- Fails build with clear error messages if libraries are missing
- Provides solution instructions when validation fails

**Automatic Execution:**
The task is automatically triggered before:
- `assembleDebug`
- `assembleRelease`
- `packageDebug`
- `packageRelease`
- Any other packaging tasks

#### OpenCV Module Validation (`opencv/build.gradle`)

The `validateOpenCVLibraries` task provides supplementary validation for the opencv module:

```gradle
task validateOpenCVLibraries {
    description = 'Validates OpenCV native libraries in the opencv module'
    group = 'verification'
    // ... validation logic
}
```

**Features:**
- Checks opencv module jniLibs directory
- Provides informational output (warnings only, no build failure)
- Useful for debugging library location issues

### 2. Standalone Validation Scripts

#### Shell Script (`scripts/validate-native-libraries.sh`)

Cross-platform shell script for manual validation:

```bash
./scripts/validate-native-libraries.sh
```

**Features:**
- Validates library presence across all architectures
- Color-coded output (green for success, red for errors, yellow for warnings)
- Detailed library information (size, location)
- Exit code 0 for success, 1 for failure
- Can be integrated into CI/CD pipelines

#### PowerShell Script (`scripts/validate-native-libraries.ps1`)

Windows-specific PowerShell script:

```powershell
.\scripts\validate-native-libraries.ps1
```

**Features:**
- Same functionality as shell script
- Windows-native implementation
- Color-coded console output
- Proper error handling and exit codes

### 3. Unit Tests

#### Validation Logic Tests (`app/src/test/java/com/example/opencvcamerastream/build/NativeLibraryValidationTest.java`)

Comprehensive unit tests for validation logic:

**Test Coverage:**
- Required libraries list validation
- Supported architectures list validation
- All libraries present scenario
- Missing architecture scenario
- Missing specific library scenario
- Empty library file detection
- No libraries present scenario
- Non-existent directory handling
- Architecture-specific validation

**Running Tests:**
```bash
# Run validation tests
./gradlew test --tests "*NativeLibraryValidationTest"

# Run all tests
./gradlew test
```

## Required Libraries

The validation system checks for these libraries:

| Library | Purpose | Required |
|---------|---------|----------|
| `libc++_shared.so` | C++ Standard Library | Yes |
| `libopencv_java4.so` | OpenCV JNI Bridge | Yes |

## Supported Architectures

All libraries must be present for these architectures:

| Architecture | Description | Common Devices |
|--------------|-------------|----------------|
| `armeabi-v7a` | 32-bit ARM | Older Android devices |
| `arm64-v8a` | 64-bit ARM | Modern Android devices |
| `x86` | 32-bit x86 | Emulators, tablets |
| `x86_64` | 64-bit x86 | Emulators, tablets |

## Validation Process

### Build-Time Validation Flow

```
Build Started
    ↓
validateNativeLibraries Task Triggered
    ↓
Scan jniLibs Directories
    ↓
Check Each Architecture
    ↓
Check Each Required Library
    ↓
┌─────────────────────────┐
│ All Libraries Present?  │
└─────────────────────────┘
    ↓ Yes              ↓ No
    ↓                  ↓
Continue Build    Fail Build with Error Message
                       ↓
                  Display Solution Instructions
```

### Validation Checks

For each architecture and library combination, the validation:

1. **Existence Check**: Verifies the file exists
2. **Size Check**: Ensures the file is not empty (size > 0)
3. **Location Tracking**: Records where the library was found
4. **Duplicate Detection**: Warns if library exists in multiple locations

## Error Messages

### Missing Library Error

```
❌ Validation Failed!

Errors:
  - Missing library: libc++_shared.so for architecture: arm64-v8a
  - Missing library: libopencv_java4.so for architecture: arm64-v8a

==========================================
SOLUTION: Run the library acquisition script
==========================================

The required OpenCV native libraries are missing.
Please run one of the following scripts to download them:

  Linux/Mac:  ./scripts/download-opencv-libs-only.sh
  Windows:    .\scripts\download-opencv-libs-only.ps1
  Or:         ./scripts/setup-opencv.sh

These scripts will download and extract the OpenCV Android SDK
and place the native libraries in the correct directories.

After running the script, rebuild your project.
==========================================
```

### Duplicate Library Warning

```
⚠️  Warnings:
  - Duplicate library: libc++_shared.so for architecture: arm64-v8a found in multiple locations

Note: Duplicate libraries will be handled by packagingOptions.pickFirst
```

## Integration with CI/CD

### Codemagic Integration

The validation is automatically integrated into the Codemagic build pipeline:

```yaml
scripts:
  - name: Download OpenCV Libraries
    script: |
      chmod +x scripts/download-opencv-libs-only.sh
      ./scripts/download-opencv-libs-only.sh
  
  - name: Validate Native Libraries
    script: |
      chmod +x scripts/validate-native-libraries.sh
      ./scripts/validate-native-libraries.sh
  
  - name: Build APK
    script: |
      ./gradlew assembleDebug
      # validateNativeLibraries runs automatically before assembly
```

### Manual Validation

Before building locally or in CI/CD:

```bash
# Validate libraries
./scripts/validate-native-libraries.sh

# If validation fails, download libraries
./scripts/download-opencv-libs-only.sh

# Validate again
./scripts/validate-native-libraries.sh

# Build
./gradlew assembleDebug
```

## Troubleshooting

### Issue: Validation fails with "Missing library" errors

**Solution:**
1. Run the library acquisition script:
   ```bash
   ./scripts/download-opencv-libs-only.sh
   ```
2. Verify libraries were downloaded:
   ```bash
   ./scripts/validate-native-libraries.sh
   ```
3. Rebuild the project

### Issue: Duplicate library warnings

**Cause:** Libraries exist in both app and opencv module jniLibs directories.

**Impact:** No build failure - Gradle's `packagingOptions.pickFirst` handles this automatically.

**Solution (Optional):** Remove libraries from one location to eliminate warnings.

### Issue: Validation passes but runtime error occurs

**Possible Causes:**
1. Library corruption during download
2. Architecture mismatch
3. OpenCV version incompatibility

**Solution:**
1. Delete existing libraries:
   ```bash
   rm -rf opencv/src/main/jniLibs/*
   rm -rf app/src/main/jniLibs/*
   ```
2. Re-download libraries:
   ```bash
   ./scripts/download-opencv-libs-only.sh
   ```
3. Validate and rebuild:
   ```bash
   ./scripts/validate-native-libraries.sh
   ./gradlew clean assembleDebug
   ```

### Issue: Validation script fails on Windows

**Solution:**
Use the PowerShell version:
```powershell
.\scripts\validate-native-libraries.ps1
```

If execution policy prevents running scripts:
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
.\scripts\validate-native-libraries.ps1
```

## Best Practices

1. **Always validate before building:**
   - Run validation scripts before starting a build
   - Especially important in CI/CD environments

2. **Keep libraries in one location:**
   - Prefer app module jniLibs directory
   - Avoid duplicating libraries across modules

3. **Automate in CI/CD:**
   - Include validation as a pre-build step
   - Fail fast if libraries are missing

4. **Version control:**
   - Do NOT commit native libraries to git (they're large)
   - Use .gitignore to exclude .so files
   - Download libraries during build process

5. **Regular updates:**
   - Update OpenCV version in download scripts
   - Re-validate after version updates
   - Test on all target architectures

## Architecture-Specific Notes

### ARM64 (arm64-v8a)
- Most common architecture for modern Android devices
- Required for devices running Android 10+
- Largest library files

### ARMv7 (armeabi-v7a)
- Legacy 32-bit ARM architecture
- Still required for older devices
- Smaller library files than ARM64

### x86/x86_64
- Primarily for emulators and tablets
- Less common in production devices
- Important for development and testing

## Performance Impact

The validation task has minimal performance impact:

- **Execution Time:** < 1 second
- **Build Time Impact:** Negligible
- **CI/CD Impact:** Adds ~1 second to build pipeline

The validation prevents much more costly runtime failures and debugging time.

## Future Enhancements

Potential improvements to the validation system:

1. **Checksum Validation:** Verify library integrity using checksums
2. **Version Detection:** Detect and report OpenCV version from libraries
3. **Size Validation:** Check that library sizes are within expected ranges
4. **Dependency Analysis:** Validate library dependencies are satisfied
5. **Automated Repair:** Automatically download missing libraries during build

## Related Documentation

- [OpenCV Setup Guide](OPENCV_SETUP_GUIDE.md)
- [CI/CD Build Automation](CICD_BUILD_AUTOMATION_SUMMARY.md)
- [Build System Validation](BUILD_SYSTEM_VALIDATION_REPORT.md)
- [Native Library Loading](OPENCV_LIBRARY_SOLUTION.md)

## Support

For issues or questions about native library validation:

1. Check this documentation
2. Review error messages carefully
3. Run validation scripts for detailed diagnostics
4. Check CI/CD logs for validation output
5. Verify library acquisition scripts are up to date
