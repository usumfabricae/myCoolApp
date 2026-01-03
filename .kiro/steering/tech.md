# Technology Stack & Build System

## Build System
- **Gradle**: 8.14.1 with Gradle Wrapper
- **Android Gradle Plugin**: 8.1.2
- **Java**: Version 17 (required for AGP 8.1.2)
- **Android Studio**: Arctic Fox or later
- Build System is only available as part of automated CI/CD

## Android Configuration
- **Compile SDK**: 34
- **Target SDK**: 29 (Android 10 for enhanced privacy features)
- **Min SDK**: 21 (Android 5.0 for broad compatibility)
- **Namespace**: `com.example.opencvcamerastream`

## Core Dependencies
- **AndroidX AppCompat**: 1.6.1
- **Material Design**: 1.10.0
- **ConstraintLayout**: 2.1.4
- **OpenCV Android SDK**: 4.5.0+ (external module)

## Testing Framework
- **Unit Testing**: JUnit 4.13.2, Mockito 4.11.0, Robolectric 4.10.3
- **Instrumentation Testing**: AndroidX Test, Espresso 3.5.1
- **Coverage**: JaCoCo 0.8.8
- **Performance Testing**: Custom performance regression tests
- **Memory Testing**: Memory leak detection and validation

## Native Libraries
- **NDK**: C++_shared standard library required for OpenCV
- **Architectures**: armeabi-v7a, arm64-v8a, x86, x86_64
- **OpenCV Native**: libopencv_java4.so
- **Loading Strategy**: Sequential dependency loading with fallback mechanisms
- **Acquisition**: Automated via shell scripts (`download-opencv-libs-only.sh/.ps1`)

## Enhanced Performance Optimizations

### Zero-Copy Processing Pipeline
- **Memory Management**: Ownership transfer instead of defensive cloning
- **Buffer Optimization**: Eliminated pooled buffer copies (5-6 → 2 copies per frame)
- **In-Place Operations**: OpenCV operations on same source/destination Mat
- **Thread Safety**: Proper synchronization without defensive copying

### Build Optimizations
- **Parallel builds**: Enabled
- **Build caching**: Enabled with library validation
- **Configuration on demand**: Enabled
- **R8 full mode**: Enabled for release builds with native library preservation
- **Non-transitive R classes**: Enabled
- **Native Library Packaging**: Optimized with pickFirst and doNotStrip configurations

### Performance Targets
- **Memory Usage**: < 50 MB during operation
- **Frame Rate**: 30 FPS camera capture, 60 FPS UI responsiveness
- **Processing Latency**: < 100ms per frame
- **CPU Usage**: Framebuffer operations < 30% of total processing time
- **Error Recovery**: < 2s recovery time

## CI/CD Platform
- **Exclusive Platform**: Codemagic (all builds must use CI/CD)
- **Local Builds**: NOT PERMITTED per project requirements
- **Configuration**: `codemagic.yaml` with library acquisition integration
- **Pre-build Scripts**: Automated OpenCV library download and validation

## Enhanced Development Workflow

### Library Acquisition (Automated)
```bash
# Executed automatically in CI/CD pipeline
./scripts/download-opencv-libs-only.sh    # Download OpenCV SDK
./scripts/setup-opencv.sh                 # Extract and validate libraries
./scripts/validate-native-libraries.sh    # Verify library completeness
```

### CI/CD Workflow (Required Approach)
```bash
# Commit and push to trigger CI/CD with library acquisition
git add .
git commit -m "feat: your changes"
git push origin main

# Create release with automated library validation
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

### Validation Scripts
```bash
# Windows
.\scripts\validate-cicd-setup.ps1
.\scripts\validate-native-libraries.ps1

# Linux/Mac
./scripts/validate-cicd-setup.sh
./scripts/validate-native-libraries.sh
./scripts/run-comprehensive-tests.sh
```

### Reference Commands (CI/CD Only)
You can't run gradlew locally. It can only be run as part of CI/CD
```bash
# These run in CI/CD pipeline only - DO NOT run locally
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew connectedAndroidTest
```

### ADB Commands (Local Development)
```bash
# Device management
adb devices                           # List connected devices
adb connect <device_ip>:5555         # Connect to device over WiFi
adb disconnect                       # Disconnect from device

# App installation and management
adb install app/build/outputs/apk/debug/app-debug.apk
adb install -r app-debug.apk         # Reinstall existing app
adb uninstall com.example.opencvcamerastream

# Debugging and logs
adb logcat                           # View system logs
adb logcat -s "OpenCVCameraStream"   # Filter app logs
adb logcat -c                        # Clear log buffer

# Camera and permissions testing
adb shell pm grant com.example.opencvcamerastream android.permission.CAMERA
adb shell pm revoke com.example.opencvcamerastream android.permission.CAMERA
adb shell dumpsys camera             # Check camera service status

# Performance monitoring (Enhanced)
adb shell top -p $(adb shell pidof com.example.opencvcamerastream)
adb shell dumpsys meminfo com.example.opencvcamerastream
adb shell dumpsys gfxinfo com.example.opencvcamerastream framestats
adb shell dumpsys cpuinfo | grep com.example.opencvcamerastream

# File operations
adb push <local_file> /sdcard/       # Push file to device
adb pull /sdcard/<file> .            # Pull file from device
adb shell ls /data/data/com.example.opencvcamerastream/

# UI Control Testing
adb shell input tap 500 1800         # Simulate toggle button tap
adb shell input tap 700 1800         # Simulate rotation button tap
adb shell am start -n com.example.opencvcamerastream/.MainActivity
```

## Enhanced Testing Commands

### Performance Testing
```bash
# Memory leak detection
adb shell dumpsys meminfo com.example.opencvcamerastream --package

# Frame rate monitoring
adb shell dumpsys gfxinfo com.example.opencvcamerastream framestats

# CPU profiling
adb shell simpleperf record -p $(adb shell pidof com.example.opencvcamerastream)
```

### Error Recovery Testing
```bash
# Simulate camera disconnection
adb shell am broadcast -a android.hardware.camera.action.NEW_PICTURE

# Test permission revocation
adb shell pm revoke com.example.opencvcamerastream android.permission.CAMERA
adb shell am start -n com.example.opencvcamerastream/.MainActivity
```

## Visual Odometry Technology Stack (NEW)

### Computer Vision Libraries
- **Feature Detection**: OpenCV ORB (Oriented FAST and Rotated BRIEF)
- **Feature Matching**: FLANN-based matcher with ratio test filtering
- **Geometric Estimation**: Essential matrix with RANSAC outlier rejection
- **3D Reconstruction**: SVD decomposition for rotation/translation extraction

### Mathematical Libraries
- **Matrix Operations**: OpenCV Mat with optimized SIMD operations
- **Linear Algebra**: Built-in OpenCV mathematical functions
- **Coordinate Transforms**: 3D transformation matrices and quaternions

### Performance Optimizations
- **Multi-threading**: OpenCV parallel processing capabilities
- **SIMD Instructions**: Automatic vectorization for feature detection
- **Memory Management**: Efficient Mat lifecycle with ownership transfer
- **Adaptive Processing**: Dynamic feature count based on performance

## Build System Integration

### Native Library Management
```gradle
android {
    packagingOptions {
        pickFirst '**/libc++_shared.so'
        pickFirst '**/libopencv_java4.so'
        doNotStrip '**/libc++_shared.so'
        doNotStrip '**/libopencv_java4.so'
    }
    
    sourceSets {
        main {
            jniLibs.srcDirs = ['src/main/jniLibs']
        }
    }
    
    // Performance optimizations
    buildFeatures {
        renderScript false
        aidl false
        shaders false
    }
}
```

### Collect application execution logs
```bash
# Enhanced log collection with performance metrics
adb shell 'logcat -d --pid $(ps -ef | grep com.example.opencvcamerastream | grep -v grep| awk ''{print $2}'') | grep -E "(OpenCV|Performance|Error|Memory)"'

# Visual odometry specific logs
adb shell 'logcat -d -s "VisualOdometry" "FeatureDetection" "DistanceComputation"'
```
