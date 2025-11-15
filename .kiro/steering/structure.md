# Project Structure & Organization

## Root Directory Layout
```
├── app/                          # Main Android application module
├── opencv/                       # OpenCV Android SDK module
├── gradle/                       # Gradle wrapper files
├── scripts/                      # Build and validation scripts
├── build.gradle                  # Project-level build configuration
├── settings.gradle               # Module configuration
├── gradle.properties             # Build optimization settings
├── codemagic.yaml               # CI/CD pipeline configuration
└── *.md                         # Documentation files
```

## App Module Structure (`app/`)
```
app/
├── src/
│   ├── main/
│   │   ├── java/com/example/opencvcamerastream/
│   │   │   ├── MainActivity.java              # Main activity & lifecycle
│   │   │   ├── camera/
│   │   │   │   └── CameraManager.java         # Camera2 API operations
│   │   │   ├── processing/
│   │   │   │   └── OpenCVProcessor.java       # OpenCV image processing
│   │   │   ├── display/
│   │   │   │   └── DisplayManager.java        # TextureView display
│   │   │   ├── permissions/                   # Android 10 permission handling
│   │   │   ├── compliance/                    # Android 10 compliance features
│   │   │   ├── performance/                   # Performance monitoring
│   │   │   └── error/                         # Error handling & recovery
│   │   ├── res/                               # Android resources
│   │   ├── jniLibs/                          # Native libraries (ARM, x86)
│   │   └── AndroidManifest.xml               # App manifest
│   ├── test/                                 # Unit tests
│   └── androidTest/                          # Instrumentation tests
├── build.gradle                              # App-level build config
├── proguard-rules.pro                        # ProGuard configuration
└── lint-baseline.xml                         # Lint baseline
```

## OpenCV Module Structure (`opencv/`)
```
opencv/
├── src/main/
│   ├── java/org/opencv/                      # OpenCV Java classes
│   ├── jniLibs/                             # OpenCV native libraries
│   └── aidl/                                # AIDL interface files
├── build.gradle                             # OpenCV module config
└── lint-baseline.xml                        # OpenCV lint baseline
```

## Package Organization
- **Base Package**: `com.example.opencvcamerastream`
- **Camera Operations**: `.camera` - Camera2 API integration
- **Image Processing**: `.processing` - OpenCV algorithms
- **Display Management**: `.display` - UI and TextureView
- **Permissions**: `.permissions` - Android 10 permission handling
- **Compliance**: `.compliance` - Android 10 specific features
- **Performance**: `.performance` - Monitoring and optimization
- **Error Handling**: `.error` - Recovery mechanisms

## Configuration Files
- **Build Config**: `build.gradle` (project & module level)
- **Dependencies**: `settings.gradle` with FAIL_ON_PROJECT_REPOS
- **Properties**: `gradle.properties` with Java 17 optimizations
- **CI/CD**: `codemagic.yaml` with exclusive build workflows
- **Lint**: `lint.xml` and module-specific baselines

## Scripts Directory (`scripts/`)
- **CI/CD Validation**: `validate-cicd-setup.sh/.ps1`
- **OpenCV Setup**: `setup-opencv.sh`
- **Build Validation**: `validate-build-system.sh`
- **Library Management**: `download-opencv-libs-only.sh/.ps1`

## Naming Conventions
- **Classes**: PascalCase (e.g., `CameraManager`, `OpenCVProcessor`)
- **Methods**: camelCase (e.g., `initializeCamera`, `processFrame`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `CAMERA_PERMISSION_CODE`)
- **Resources**: snake_case (e.g., `activity_main`, `camera_preview`)
- **Packages**: lowercase (e.g., `camera`, `processing`, `display`)

## Architecture Pattern
- **Main Activity**: Central lifecycle management and coordination
- **Manager Classes**: Specialized components (Camera, Display, Processing)
- **Separation of Concerns**: Each package handles specific functionality
- **Android 10 Compliance**: Dedicated compliance and permissions packages