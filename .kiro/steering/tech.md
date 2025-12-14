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

## Native Libraries
- **NDK**: C++_shared standard library required for OpenCV
- **Architectures**: armeabi-v7a, arm64-v8a, x86, x86_64
- **OpenCV Native**: libopencv_java4.so

## CI/CD Platform
- **Exclusive Platform**: Codemagic (all builds must use CI/CD)
- **Local Builds**: NOT PERMITTED per project requirements
- **Configuration**: `codemagic.yaml`

## Common Commands

### CI/CD Workflow (Required Approach)
```bash
# Commit and push to trigger CI/CD
git add .
git commit -m "feat: your changes"
git push origin main

# Create release
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

### Validation Scripts
```bash
# Windows
.\scripts\validate-cicd-setup.ps1

# Linux/Mac
./scripts/validate-cicd-setup.sh
```

### Reference Commands (CI/CD Only)
You can't run gradlew locally. It can only be run as part of CI/CD
```bash
# These run in CI/CD pipeline only - DO NOT run locally
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
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

# Performance monitoring
adb shell top -p $(adb shell pidof com.example.opencvcamerastream)
adb shell dumpsys meminfo com.example.opencvcamerastream

# File operations
adb push <local_file> /sdcard/       # Push file to device
adb pull /sdcard/<file> .            # Pull file from device
adb shell ls /data/data/com.example.opencvcamerastream/
```

## Build Optimizations
- **Parallel builds**: Enabled
- **Build caching**: Enabled
- **Configuration on demand**: Enabled
- **R8 full mode**: Enabled for release builds
- **Non-transitive R classes**: Enabled



### Collect application execution logs
adb shell 'logcat -d --pid $(ps -ef | grep com.example.opencvcamerastream | grep -v grep| awk ''{print $2}'')'
