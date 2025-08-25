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

### 4. Next Steps

The project is now ready for implementation of the remaining tasks:
- Task 2: Implement camera permission handling
- Task 3: Create OpenCV initialization and processing framework
- Task 4: Implement Camera2 API integration
- And subsequent tasks...

## Build Requirements

- Android Studio Arctic Fox or later
- Android SDK 21 or higher (minimum)
- Target Android SDK 29 (Android 10) for optimal compatibility
- OpenCV Android SDK 4.5.0 or later
- Device running Android 10 (API 29) recommended for full feature testing

## CI/CD with Codemagic

This project is configured for automated building and testing using Codemagic. The `codemagic.yaml` file defines three workflows:

### 1. Main Development Workflow (`android-workflow`)
- **Triggers**: Push to main/develop branches, pull requests
- **Actions**: 
  - Builds debug APK
  - Runs unit tests
  - Performs lint checks
  - Generates test reports
- **Artifacts**: APK files, test reports, lint results

### 2. Release Workflow (`android-release-workflow`)
- **Triggers**: Git tags matching `v*.*.*` pattern
- **Actions**:
  - Runs comprehensive test suite
  - Builds release APK (currently unsigned)
  - Generates release artifacts with build info
- **Artifacts**: Release APK, build metadata

### 3. Test-Only Workflow (`android-test-workflow`)
- **Triggers**: Pull requests
- **Actions**:
  - Runs unit tests with coverage
  - Validates Android 10 compatibility
  - Generates coverage reports
- **Artifacts**: Test results, coverage reports

### Setup Instructions for Codemagic

1. **Connect Repository**: Link your Git repository to Codemagic
2. **Configure Environment**: 
   - Update email notifications in `codemagic.yaml`
   - Add any required environment variables
3. **OpenCV Setup**: Ensure OpenCV module is properly committed to the repository
4. **Optional Signing**: For release builds, configure app signing:
   - Add keystore file as encrypted environment variable
   - Update signing configuration in `codemagic.yaml`

### Local Testing Commands

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Run tests with coverage
./gradlew testDebugUnitTest jacocoTestReport

# Run lint checks
./gradlew lintDebug

# Build debug APK
./gradlew assembleDebug
```

### Android 10 Compatibility Validation

The CI pipeline automatically validates:
- Target SDK is set to 29 (Android 10)
- Required camera permissions are declared
- Android 10 privacy compliance features are implemented

## Testing

The project includes comprehensive unit tests for:
- **Permission Handling**: Android 10 compliant camera permission flows
- **MainActivity Integration**: Activity lifecycle and permission callbacks
- **Android 10 Specific Features**: Enhanced privacy controls and background restrictions

Test coverage reports are generated automatically in CI and can be viewed in the artifacts.