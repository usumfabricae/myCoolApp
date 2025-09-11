# OpenCV Camera Stream Android App

This Android application captures camera frames in real-time, processes them using OpenCV, and displays the processed video stream on the device screen. The application is specifically designed for Android 10 (API 29) with enhanced privacy controls, background activity restrictions, and modern security features while maintaining backward compatibility to Android 5.0 (API 21).

## Project Structure

```
app/
├── src/main/java/com/example/opencvcamerastream/
│   ├── MainActivity.java                    # Main activity and lifecycle management
│   ├── camera/
│   │   └── CameraManager.java              # Camera2 API operations
│   ├── processing/
│   │   └── OpenCVProcessor.java            # OpenCV image processing
│   └── display/
│       └── DisplayManager.java             # TextureView display management
opencv/                                      # OpenCV Android SDK module
```

## Setup Instructions

### 1. OpenCV Android SDK Setup

To complete the project setup, you need to add the OpenCV Android SDK:

1. Download OpenCV Android SDK from https://opencv.org/releases/
2. Extract the downloaded archive
3. Copy the contents of the `sdk/java` folder to the `opencv/` directory in this project
4. The opencv module should contain:
   - `src/main/java/` with OpenCV Java classes
   - `src/main/jniLibs/` with native libraries
   - `src/main/aidl/` with AIDL files

### 2. Requirements Addressed

This project structure addresses the following requirements:

- **Requirement 1.1**: Camera permissions configured in AndroidManifest.xml
- **Requirement 5.1**: Minimum SDK 21 (Android 5.0) for broad device compatibility
- **Requirement 6.1**: Target SDK 29 (Android 10) for enhanced privacy and security features
- **Requirement 6.2**: Android 10 privacy compliance in camera permissions

### 3. Android 10 Specific Features

The application incorporates Android 10 (API 29) specific enhancements:

- **Enhanced Privacy Controls**: Compliant with Android 10's stricter camera permission model
- **Background Activity Restrictions**: Proper handling of background limitations
- **Scoped Storage**: No legacy external storage usage (requestLegacyExternalStorage=false)
- **Camera2 Full Support**: Utilizes modern Camera2 API features available in Android 10
- **Runtime Permission Improvements**: Enhanced permission request flows

### 4. Implementation Status

✅ **All Core Implementation Tasks Completed** (Tasks 1-13):
- ✅ Task 1: Android project structure and dependencies setup
- ✅ Task 2: Camera permission handling with Android 10 compliance
- ✅ Task 3: OpenCV initialization and processing framework
- ✅ Task 4: Camera2 API integration
- ✅ Task 5: Display system with TextureView
- ✅ Task 6: Camera capture with OpenCV processing pipeline
- ✅ Task 7: Frame buffer management and memory optimization
- ✅ Task 8: Error handling and recovery mechanisms
- ✅ Task 9: Performance monitoring and optimization
- ✅ Task 10: Multiple OpenCV processing modes
- ✅ Task 11: Comprehensive lifecycle management
- ✅ Task 12: Android 10 specific compliance and validation
- ✅ Task 13: Codemagic CI/CD pipeline configuration

🔄 **Current Task**: Task 14 - Git workflow and CI/CD integration validation

## Build Requirements

- Android Studio Arctic Fox or later
- Android SDK 21 or higher (minimum)
- Target Android SDK 29 (Android 10) for optimal compatibility
- OpenCV Android SDK 4.5.0 or later
- Device running Android 10 (API 29) recommended for full feature testing

## 🚀 CI/CD with Codemagic (Exclusive Build Platform)

**⚠️ IMPORTANT**: This project uses **exclusive CI/CD operation** through Codemagic platform. All builds, tests, and deployments must be performed through the CI/CD pipeline. Local builds and tests are not permitted per project requirements 7.2 and 8.6.

### 📋 CI/CD Requirements Compliance

This setup addresses:
- **Requirement 7**: Codemagic CI/CD platform for all builds and tests
- **Requirement 8**: Git workflow integration for triggering CI/CD pipelines

### 🔧 Codemagic Workflows

The `codemagic.yaml` file defines three exclusive workflows:

#### 1. Development Workflow (`android-workflow`)
- **Triggers**: Push to `main`, `develop`, or `feature/*` branches
- **Actions**: 
  - Compile debug APK
  - Execute unit tests through Codemagic
  - Run Android lint checks
  - Generate test and lint reports
  - Validate Android 10 compatibility
- **Artifacts**: APK files, test reports, lint results

#### 2. Release Workflow (`android-release-workflow`)
- **Triggers**: Version tags matching `v*.*.*` pattern
- **Actions**:
  - Run comprehensive test suite through Codemagic
  - Build release APK using secure environment
  - Generate release artifacts with build metadata
- **Artifacts**: Release APK, build metadata, mapping files

#### 3. Test-Only Workflow (`android-test-workflow`)
- **Triggers**: Pull requests
- **Actions**:
  - Execute unit tests with coverage through Codemagic
  - Run lint checks and Android 10 validation
  - Generate test coverage reports
- **Artifacts**: Test results, coverage reports

### 🛠️ Setup and Validation

#### Before Committing Changes
Validate your CI/CD setup using the provided scripts:

**Windows (PowerShell)**:
```powershell
cd myCoolApp
.\scripts\validate-cicd-setup.ps1
```

**Linux/Mac**:
```bash
cd myCoolApp
./scripts/validate-cicd-setup.sh
```

#### Git Workflow for CI/CD Triggering
```bash
# 1. Commit changes with descriptive message
git add .
git commit -m "feat: implement camera permission handling with Android 10 compliance"

# 2. Push to trigger CI/CD workflow
git push origin main

# 3. For releases, create and push version tag
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

### 📊 Automated Validation

The CI/CD pipeline automatically validates:
- ✅ Target SDK 29 (Android 10) compliance
- ✅ Camera permissions declared in manifest
- ✅ Android 10 privacy compliance features
- ✅ Unit test execution and coverage
- ✅ Code quality through lint checks
- ✅ Build compilation success

### 📚 Documentation

For detailed CI/CD information, see:
- [CICD_README.md](CICD_README.md) - Comprehensive CI/CD setup guide
- [GIT_WORKFLOW.md](GIT_WORKFLOW.md) - Git workflow and branching strategy

### ⚠️ No Local Builds Permitted

**Important**: Local build commands are provided for reference only. All actual builds and tests must be performed through Codemagic:

```bash
# ❌ DO NOT RUN LOCALLY - For reference only
# ./gradlew testDebugUnitTest
# ./gradlew assembleDebug
# ./gradlew lintDebug

# ✅ CORRECT APPROACH - Use git workflow to trigger CI/CD
git add .
git commit -m "your changes"
git push origin branch-name
```

## Testing

The project includes comprehensive unit tests for:
- **Permission Handling**: Android 10 compliant camera permission flows
- **MainActivity Integration**: Activity lifecycle and permission callbacks
- **Android 10 Specific Features**: Enhanced privacy controls and background restrictions

Test coverage reports are generated automatically in CI and can be viewed in the artifacts.