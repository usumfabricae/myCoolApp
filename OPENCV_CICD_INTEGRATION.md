# OpenCV CI/CD Integration Guide

## Overview

This document explains how OpenCV libraries are automatically downloaded and integrated during the CI/CD build process to resolve runtime errors like:

```
java.lang.UnsatisfiedLinkError: dlopen failed: library "libc++_shared.so" not found
```

## CI/CD Pipeline OpenCV Integration

### Automatic Download Process

The Codemagic CI/CD pipeline includes these OpenCV-specific steps:

1. **Download and setup OpenCV Android SDK**
   - Downloads OpenCV 4.8.0 from GitHub releases
   - Extracts Java sources to `opencv/src/main/java/`
   - Copies native libraries to `opencv/src/main/jniLibs/`
   - Creates missing interfaces and fixes compilation issues

2. **Validate OpenCV integration**
   - Verifies Java sources are present
   - Checks native libraries for all architectures
   - Validates specific required libraries:
     - `libopencv_java4.so`
     - `libc++_shared.so`

3. **Verify OpenCV libraries in APK**
   - Builds test APK
   - Extracts and verifies OpenCV libraries are included
   - Ensures runtime dependencies are satisfied

### Required Libraries

The CI/CD pipeline ensures these critical libraries are included:

#### Native Libraries (per architecture)
- `libopencv_java4.so` - Main OpenCV JNI interface
- `libc++_shared.so` - C++ standard library
- Additional OpenCV core libraries

#### Java Classes
- `org.opencv.core.Mat` - Matrix operations
- `org.opencv.android.OpenCVLoader` - Initialization
- `org.opencv.android.Utils` - Utility functions
- Complete OpenCV Java API

## Build Configuration

### OpenCV Module Configuration

The `opencv/build.gradle` is configured to properly include native libraries:

```gradle
sourceSets {
    main {
        jniLibs.srcDirs = ['src/main/jniLibs']
    }
}

packagingOptions {
    pickFirst '**/libc++_shared.so'
    pickFirst '**/libopencv_java4.so'
    pickFirst '**/libopencv_java3.so'
}
```

### App Module Configuration

The `app/build.gradle` includes proper native library handling:

```gradle
packagingOptions {
    pickFirst '**/libc++_shared.so'
    pickFirst '**/libjsc.so'
    pickFirst '**/libopencv_java4.so'
    pickFirst '**/libopencv_java3.so'
}

sourceSets {
    main {
        jniLibs.srcDirs = ['src/main/jniLibs']
    }
}
```

## Triggering OpenCV Download

### Automatic Trigger

OpenCV libraries are automatically downloaded when you push code:

```bash
git add .
git commit -m "feat: trigger OpenCV download in CI/CD"
git push origin main
```

### CI/CD Workflows

All three workflows include OpenCV setup:

1. **Development Workflow** (`main`, `develop`, `feature/*` branches)
2. **Release Workflow** (version tags `v*.*.*`)
3. **Test Workflow** (pull requests)

## Verification Process

### CI/CD Validation

The pipeline performs comprehensive validation:

1. **File Count Verification**
   ```bash
   JAVA_FILES=$(find opencv/src/main/java -name "*.java" | wc -l)
   NATIVE_LIBS=$(find opencv/src/main/jniLibs -name "*.so" | wc -l)
   ```

2. **Required Library Check**
   ```bash
   # Validates presence of critical libraries
   find opencv/src/main/jniLibs -name "libopencv_java4.so"
   find opencv/src/main/jniLibs -name "libc++_shared.so"
   ```

3. **APK Content Verification**
   ```bash
   # Extracts APK and verifies libraries are included
   unzip app-debug.apk
   find . -name "libopencv_java4.so"
   ```

### Architecture Support

Libraries are included for all Android architectures:
- `arm64-v8a` (64-bit ARM - most modern devices)
- `armeabi-v7a` (32-bit ARM - older devices)
- `x86` (32-bit Intel - emulators)
- `x86_64` (64-bit Intel - emulators)

## Troubleshooting

### If Runtime Error Persists

1. **Check CI/CD Logs**
   - Verify "Download and setup OpenCV Android SDK" step succeeded
   - Check "Validate OpenCV integration" step passed
   - Confirm "Verify OpenCV libraries in APK" step completed

2. **Verify APK Source**
   - Ensure APK was built by CI/CD pipeline
   - Check APK was downloaded from Codemagic artifacts
   - Avoid using locally built APKs

3. **Check Build Artifacts**
   - Download APK from Codemagic artifacts
   - Verify APK size (should be larger with OpenCV libraries)
   - Check build logs for OpenCV-related errors

### Common Issues

#### Issue: "Cannot load library opencv_java4"
**Solution**: APK missing native libraries
- Verify CI/CD pipeline completed successfully
- Check OpenCV setup step in build logs
- Ensure APK downloaded from CI/CD artifacts

#### Issue: "library libc++_shared.so not found" or "bad ELF magic"
**Solution**: Static C++ standard library linking
- The build is configured to use `ANDROID_STL=c++_static`
- This statically links the C++ standard library into `libopencv_java4.so`
- Eliminates the need for separate `libc++_shared.so` file
- Prevents corruption issues with downloaded/copied library files

**Configuration**:
```gradle
externalNativeBuild {
    cmake {
        arguments "-DANDROID_STL=c++_static"
    }
}

packagingOptions {
    exclude '**/libc++_shared.so'  // Force static linking
}
```

## Local Development

### Important Note

This project uses **exclusive CI/CD operation**. Local builds are not permitted for production.

### For Development Testing Only

If needed for local development (not production):

```bash
# Windows
.\scripts\download-opencv-libs-only.ps1

# Linux/Mac
./scripts/download-opencv-libs-only.sh
```

**Remember**: All production builds must use CI/CD pipeline.

## Next Steps

1. **Push Code**: Trigger CI/CD pipeline with OpenCV download
2. **Monitor Build**: Check Codemagic logs for OpenCV setup success
3. **Download APK**: Use APK from CI/CD artifacts (not local build)
4. **Test Device**: Install CI/CD built APK on Android device
5. **Verify Fix**: Confirm runtime error is resolved

The CI/CD pipeline ensures OpenCV libraries are properly downloaded, configured, and included in every build, resolving the runtime library loading issues.