# Project Structure & Organization

## Root Directory Layout
```
├── app/                          # Main Android application module
├── opencv/                       # OpenCV Android SDK module
├── gradle/                       # Gradle wrapper files
├── scripts/                      # Build and validation scripts
├── .kiro/specs/                  # Feature specifications and design documents
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
│   │   │   ├── MainActivity.java              # Main activity & lifecycle with UI controls
│   │   │   ├── camera/
│   │   │   │   ├── CameraManager.java         # Enhanced Camera2 API with error recovery
│   │   │   │   ├── CameraCapabilities.java    # Device capability detection
│   │   │   │   └── FrameProcessingStats.java  # Performance metrics collection
│   │   │   ├── processing/
│   │   │   │   ├── OpenCVProcessor.java       # Optimized OpenCV processing
│   │   │   │   ├── FrameProcessor.java        # Zero-copy frame processing pipeline
│   │   │   │   ├── FrameBuffer.java           # Optimized buffer management
│   │   │   │   ├── ZeroCopyProcessor.java     # Memory-optimized processing
│   │   │   │   └── VisualOdometryProcessor.java # 3D distance tracking (NEW)
│   │   │   ├── display/
│   │   │   │   └── DisplayManager.java        # Enhanced TextureView with rotation/toggle
│   │   │   ├── permissions/
│   │   │   │   └── PermissionHandler.java     # Android 10 permission handling
│   │   │   ├── compliance/
│   │   │   │   ├── Android10ComplianceValidator.java # Compliance validation
│   │   │   │   └── Android10TestUtils.java    # Testing utilities
│   │   │   ├── performance/
│   │   │   │   ├── PerformanceMetricsCollector.java # Real-time monitoring
│   │   │   │   ├── PerformanceMeasurementManager.java # Performance management
│   │   │   │   └── CpuUsageProfiler.java      # CPU usage optimization
│   │   │   ├── error/
│   │   │   │   ├── ErrorHandler.java          # Circuit breaker error recovery
│   │   │   │   ├── ErrorDialogManager.java    # User-friendly error dialogs
│   │   │   │   └── PerformanceMonitor.java    # Performance-based error detection
│   │   │   └── monitoring/
│   │   │       ├── RealTimeMonitor.java       # Real-time system monitoring
│   │   │       └── PerformanceDashboard.java  # Performance metrics dashboard
│   │   ├── res/                               # Android resources with enhanced UI
│   │   │   ├── layout/                        # UI layouts with toggle/rotation controls
│   │   │   ├── drawable/                      # UI graphics and backgrounds
│   │   │   └── values/                        # Strings, colors, themes
│   │   ├── jniLibs/                          # Native libraries (populated by scripts)
│   │   │   ├── arm64-v8a/                    # ARM64 native libraries
│   │   │   ├── armeabi-v7a/                  # ARMv7 native libraries
│   │   │   ├── x86/                          # x86 native libraries
│   │   │   └── x86_64/                       # x86_64 native libraries
│   │   └── AndroidManifest.xml               # App manifest with enhanced permissions
│   ├── test/                                 # Unit tests (updated for optimized pipeline)
│   │   └── java/com/example/opencvcamerastream/
│   │       ├── processing/                   # Zero-copy processing tests
│   │       ├── camera/                       # Enhanced camera manager tests
│   │       ├── performance/                  # Performance regression tests
│   │       ├── error/                        # Error recovery tests
│   │       └── integration/                  # Integration tests
│   └── androidTest/                          # Instrumentation tests
│       └── java/com/example/opencvcamerastream/
│           ├── EndToEndCameraDisplayTest.java # Complete pipeline testing
│           ├── UIControlIntegrationTest.java  # UI control integration
│           ├── PerformanceTestSuite.java     # Performance validation
│           └── DeviceCompatibilityTest.java  # Multi-device testing
├── build.gradle                              # App-level build config with optimizations
├── proguard-rules.pro                        # ProGuard configuration
└── lint-baseline.xml                         # Lint baseline
```

## OpenCV Module Structure (`opencv/`)
```
opencv/
├── src/main/
│   ├── java/org/opencv/                      # OpenCV Java classes
│   ├── jniLibs/                             # OpenCV native libraries (auto-populated)
│   │   ├── arm64-v8a/                       # libc++_shared.so, libopencv_java4.so
│   │   ├── armeabi-v7a/                     # libc++_shared.so, libopencv_java4.so
│   │   ├── x86/                             # libc++_shared.so, libopencv_java4.so
│   │   └── x86_64/                          # libc++_shared.so, libopencv_java4.so
│   └── aidl/                                # AIDL interface files
├── build.gradle                             # OpenCV module config
└── lint-baseline.xml                        # OpenCV lint baseline
```

## Package Organization
- **Base Package**: `com.example.opencvcamerastream`
- **Camera Operations**: `.camera` - Enhanced Camera2 API with error recovery
- **Image Processing**: `.processing` - Optimized OpenCV algorithms with zero-copy processing
- **Display Management**: `.display` - Enhanced UI with rotation and visibility controls
- **Permissions**: `.permissions` - Android 10 permission handling
- **Compliance**: `.compliance` - Android 10 specific features and validation
- **Performance**: `.performance` - Real-time monitoring and optimization
- **Error Handling**: `.error` - Circuit breaker recovery mechanisms
- **Monitoring**: `.monitoring` - Real-time system monitoring and dashboards

## Enhanced Architecture Components

### Native Library Management
- **Library Acquisition**: Automated download via shell scripts
- **Sequential Loading**: Dependency-ordered library loading
- **Error Recovery**: Multiple loading strategies with fallback
- **Validation**: Build-time and runtime library validation

### Zero-Copy Processing Pipeline
- **Optimized FrameProcessor**: Eliminates unnecessary Mat copies
- **Ownership Transfer**: Mat ownership semantics instead of defensive cloning
- **In-Place Operations**: OpenCV operations on same source/destination Mat
- **Memory Management**: Reduced GC pressure and memory allocations

### Enhanced UI Controls
- **State Persistence**: SharedPreferences integration
- **Hardware Acceleration**: GPU-accelerated transformations
- **Smooth Animations**: Non-blocking UI transitions
- **Performance Monitoring**: Real-time UI performance metrics

## Configuration Files
- **Build Config**: `build.gradle` (project & module level) with performance optimizations
- **Dependencies**: `settings.gradle` with FAIL_ON_PROJECT_REPOS
- **Properties**: `gradle.properties` with Java 17 optimizations
- **CI/CD**: `codemagic.yaml` with library acquisition integration
- **Lint**: `lint.xml` and module-specific baselines
- **Specifications**: `.kiro/specs/` with requirements, design, and tasks

## Scripts Directory (`scripts/`)
- **Library Management**: `download-opencv-libs-only.sh/.ps1`, `setup-opencv.sh`
- **CI/CD Validation**: `validate-cicd-setup.sh/.ps1`
- **Build Validation**: `validate-build-system.sh`
- **Native Library Validation**: `validate-native-libraries.sh/.ps1`
- **Performance Testing**: `run-comprehensive-tests.sh`

## Naming Conventions
- **Classes**: PascalCase (e.g., `CameraManager`, `ZeroCopyProcessor`, `VisualOdometryProcessor`)
- **Methods**: camelCase (e.g., `initializeCamera`, `processFrameInPlace`, `computeDistance`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `CAMERA_PERMISSION_CODE`, `MAX_RETRY_ATTEMPTS`)
- **Resources**: snake_case (e.g., `activity_main`, `camera_preview`, `toggle_button_background`)
- **Packages**: lowercase (e.g., `camera`, `processing`, `display`, `monitoring`)

## Architecture Pattern
- **Enhanced Main Activity**: Central lifecycle management with UI controls and state persistence
- **Specialized Manager Classes**: Camera, Display, Processing, Error, Performance managers
- **Zero-Copy Processing**: Optimized memory management and ownership transfer
- **Circuit Breaker Error Recovery**: Automatic failure detection and recovery
- **Real-Time Monitoring**: Performance metrics and system health monitoring
- **Separation of Concerns**: Each package handles specific functionality with clear interfaces
- **Android 10 Compliance**: Dedicated compliance and permissions packages with validation