# Design Document

## Overview

**STATUS: IMPLEMENTED ✅**

This design has been successfully implemented to address two critical aspects of the OpenCV camera streaming application: 

1. **Native Library Integration**: Successfully resolved the missing `libc++_shared.so` and `libopencv_java4.so` files that prevented OpenCV initialization
2. **Enhanced Camera Stream Features**: Successfully implemented UI controls, error recovery, and Android 10 compliance based on comprehensive requirements analysis

The implemented solution provides a robust foundation for OpenCV functionality while delivering an enhanced user experience with camera visualization controls, rotation capabilities, and comprehensive error handling. The implementation includes zero-copy processing optimizations that significantly reduce CPU usage and improve performance.

## Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           MainActivity                                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐ │
│  │   UI Controls   │  │  Error Handler  │  │   Native Library Manager   │ │
│  │  - Toggle Btn   │  │  - Recovery     │  │  - Sequential Loading      │ │
│  │  - Rotate Btn   │  │  - Logging      │  │  - Diagnostics             │ │
│  │  - State Mgmt   │  │  - Retry Logic  │  │  - Fallback Strategies     │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          CameraManager                                      │
│  - Camera2 API Integration          - Frame Capture Pipeline                │
│  - Enhanced Error Recovery          - Buffer Management                     │
│  - Performance Monitoring           - Android 10 Compliance                │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        OpenCVProcessor                                      │
│  - Image Processing Pipeline        - Format Conversion                     │
│  - Error Handling Integration       - Performance Optimization             │
│  - Processing Mode Support          - Memory Management                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        DisplayManager                                       │
│  - TextureView Management           - Rotation Handling                     │
│  - Visibility Toggle Control        - Frame Buffer Management              │
│  - Matrix Transformations           - Hardware Acceleration                │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Build System Layer                                   │
│  ├── Library Acquisition (Download/Extract OpenCV SDK)                     │
│  ├── Architecture Validation (Multi-ABI support)                          │
│  ├── Packaging Configuration (APK inclusion)                               │
│  └── CI/CD Integration (Codemagic pipeline)                                │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Native Libraries                                    │
│  ├── libc++_shared.so (C++ Standard Library)                              │
│  └── libopencv_java4.so (OpenCV JNI Bridge)                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. Enhanced MainActivity

**Purpose:** Central coordination of UI controls, camera lifecycle, and error handling with state persistence.

**Interface:**
```java
public class MainActivity extends AppCompatActivity {
    // UI Controls
    private ToggleButton cameraVisualizationToggle;
    private Button rotationButton;
    
    // State Management
    private boolean isVisualizationEnabled = true;
    private int currentRotation = 0; // 0, 90, 180, 270
    private SharedPreferences preferences;
    
    // Core Managers
    private NativeLibraryManager libraryManager;
    private ErrorRecoveryManager errorRecoveryManager;
    private CameraManager cameraManager;
    private DisplayManager displayManager;
    
    // Lifecycle Methods
    public void initializeComponents();
    public void setupUIControls();
    public void restoreState();
    public void saveState();
}
```

**Key Features:**
- Centralized component initialization and coordination
- UI control state persistence across app sessions
- Integration with all manager classes
- Comprehensive lifecycle management

### 2. Native Library Manager

**Purpose:** Centralized management of native library loading with proper dependency ordering and error handling.

**Interface:**
```java
public class NativeLibraryManager {
    public static boolean loadOpenCVLibraries();
    public static boolean isLibraryAvailable(String libraryName);
    public static String getDiagnosticInfo();
    public static void setLoadingStrategy(LoadingStrategy strategy);
    public static LoadingResult getLastLoadingResult();
}
```

**Key Features:**
- Sequential loading: `libc++_shared.so` → `libopencv_java4.so`
- Multiple loading strategies (System.loadLibrary, System.load with full path)
- Comprehensive error reporting with specific failure reasons
- Library availability detection before loading attempts
- Integration with error recovery system

### 3. Enhanced CameraManager

**Purpose:** Robust camera operations with error recovery, performance monitoring, and Android 10 compliance.

**Interface:**
```java
public class CameraManager {
    // Core Camera Operations
    public boolean initializeCamera();
    public void startPreview();
    public void stopPreview();
    public void releaseCamera();
    
    // Error Recovery
    public boolean recoverFromError();
    public void handleCameraDisconnection();
    
    // Performance Monitoring
    public PerformanceMetrics getPerformanceMetrics();
    public void optimizeForDevice();
    
    // Android 10 Compliance
    public boolean requestCameraPermission();
    public boolean checkBackgroundRestrictions();
}
```

**Key Features:**
- Enhanced error recovery based on log analysis findings
- Automatic retry mechanisms for camera operations
- Performance monitoring and optimization
- Android 10 privacy compliance integration

### 3a. FrameProcessor Optimization

**Purpose:** Minimize framebuffer copies to reduce CPU usage and improve performance.

**Optimization Strategy:**
```java
public class FrameProcessor {
    // Zero-copy processing where possible
    public void processFrameAsync(@NonNull Image image);
    
    // In-place processing without buffer pooling copies
    private void processFrameInPlace(@NonNull Mat inputMat);
    
    // Transfer ownership instead of cloning
    private void transferFrameOwnership(@NonNull Mat frame);
}
```

**Key Optimizations:**
1. **Eliminate pooled buffer copies**: Process directly on converted Mat instead of copying to/from buffer pool
2. **Remove callback clones**: Transfer Mat ownership to callback instead of creating defensive copies
3. **In-place operations**: Use OpenCV in-place processing where supported (e.g., cvtColor with same src/dst)
4. **Remove DisplayManager clone**: Use proper synchronization primitives instead of defensive cloning

**Expected Impact:**
- Reduce from 5-6 copies per frame to 2 copies (Image→Mat + Mat→Bitmap)
- Decrease CPU usage by 40-50% in processing pipeline
- Improve frame processing latency by 20-30ms
- Reduce memory pressure and GC frequency

### 4. DisplayManager with UI Controls

**Purpose:** Advanced display management with rotation, visibility controls, and hardware acceleration.

**Interface:**
```java
public class DisplayManager {
    // Display Control
    public void setVisualizationEnabled(boolean enabled);
    public void rotateDisplay(int degrees);
    public void updateDisplayMatrix();
    
    // Frame Management
    public void renderFrame(Mat frame);
    public void optimizeFrameBuffer();
    
    // Error Handling
    public void handleRenderingError(Exception error);
    public boolean recoverFromDisplayError();
    
    // Performance
    public void enableHardwareAcceleration();
    public DisplayMetrics getDisplayMetrics();
}
```

**Key Features:**
- 90-degree rotation with matrix transformations
- Visibility toggle while maintaining processing pipeline
- Hardware-accelerated rendering
- Frame buffer optimization and management

### 5. ErrorRecoveryManager

**Purpose:** Comprehensive error handling system based on log analysis findings.

**Interface:**
```java
public class ErrorRecoveryManager {
    // Error Recovery
    public void handleCameraProcessingError();
    public void retryOperation(Runnable operation, int maxRetries);
    public boolean recoverFromNativeLibraryError();
    
    // Logging and Diagnostics
    public void logErrorForAnalysis(String error, Exception exception);
    public String generateDiagnosticReport();
    public void enablePerformanceMonitoring();
    
    // Circuit Breaker Pattern
    public boolean isOperationAllowed(String operationType);
    public void recordFailure(String operationType);
    public void recordSuccess(String operationType);
}
```

**Key Features:**
- Circuit breaker pattern for repeated failures
- Automatic retry with exponential backoff
- Comprehensive error logging and analysis
- Integration with all system components

### 6. Library Acquisition System

**Purpose:** Automated download and extraction of OpenCV native libraries using shell scripts.

**Implementation Strategy:**
- Shell scripts (`download-opencv-libs-only.sh/.ps1` and `setup-opencv.sh`) to download OpenCV Android SDK
- Automated extraction of native libraries to correct architecture directories (`opencv/src/main/jniLibs/`)
- Validation of library completeness and architecture compatibility
- Cross-platform support (Windows PowerShell and Unix shell scripts)
- Integration with CI/CD pipeline (Codemagic) through script execution

### 7. Enhanced Build System Configuration

**Purpose:** Ensure proper inclusion and packaging of native libraries acquired through shell scripts.

**Key Configurations:**
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

**Script Integration:**
- Execute `scripts/download-opencv-libs-only.sh` or `scripts/setup-opencv.sh` before building
- Scripts populate `opencv/src/main/jniLibs/` with required native libraries
- Build system automatically includes libraries from populated directories
- CI/CD pipeline executes scripts as pre-build steps

### 8. Runtime Loading Strategy

**Purpose:** Robust library loading with fallback mechanisms and detailed diagnostics.

**Loading Sequence:**
1. **Pre-loading Validation:** Check if libraries exist in APK
2. **Dependency Loading:** Load `libc++_shared.so` first
3. **OpenCV Loading:** Load `libopencv_java4.so` after C++ runtime
4. **Fallback Strategies:** Try alternative loading methods if primary fails
5. **Error Recovery Integration:** Connect with ErrorRecoveryManager
6. **Performance Monitoring:** Track loading times and success rates
7. **Error Reporting:** Provide detailed diagnostics for troubleshooting

## Data Models

### Library Metadata
```java
public class LibraryInfo {
    private String name;
    private String architecture;
    private boolean isLoaded;
    private String loadError;
    private long fileSize;
    private String checksum;
    private long loadTime;
}
```

### Loading Result
```java
public class LoadingResult {
    private boolean success;
    private List<String> loadedLibraries;
    private List<String> failedLibraries;
    private Map<String, String> errors;
    private String diagnosticInfo;
    private long totalLoadTime;
}
```

### UI State
```java
public class UIState {
    private boolean visualizationEnabled;
    private int rotationDegrees;
    private long lastStateChange;
    private Map<String, Object> preferences;
    
    public void saveToPreferences(SharedPreferences prefs);
    public static UIState loadFromPreferences(SharedPreferences prefs);
}
```

### Performance Metrics
```java
public class PerformanceMetrics {
    private float frameRate;
    private long memoryUsage;
    private long processingLatency;
    private int errorCount;
    private long uptime;
    
    public boolean meetsTargets();
    public String generateReport();
}
```

### Error Context
```java
public class ErrorContext {
    private String errorType;
    private String component;
    private Exception exception;
    private long timestamp;
    private Map<String, String> systemInfo;
    private String recoveryAction;
}
```

## Error Handling

### Error Categories

1. **Native Library Errors**
   - Missing Library Files: APK inspection during startup
   - Dependency Loading Failures: UnsatisfiedLinkError handling
   - Architecture Mismatches: Device ABI compatibility
   - Version Conflicts: Library version validation

2. **Camera Processing Errors** (Based on Log Analysis)
   - Frame Processing Failures: "Error processing captured image"
   - Camera Disconnection: Hardware access issues
   - Permission Denials: Android 10 privacy controls
   - Buffer Overflow: Memory management issues

3. **Display Rendering Errors**
   - TextureView Failures: Surface rendering issues
   - Rotation Errors: Matrix transformation failures
   - Memory Leaks: Frame buffer management
   - Performance Degradation: Hardware acceleration issues

4. **UI Control Errors**
   - State Persistence Failures: SharedPreferences issues
   - Control Synchronization: Thread safety problems
   - Animation Glitches: UI responsiveness issues

### Error Recovery Strategies

```java
public enum LoadingStrategy {
    SYSTEM_LOAD_LIBRARY,    // Standard Android loading
    DIRECT_PATH_LOAD,       // Load with full file path
    EXTRACTED_LOAD,         // Extract to temp and load
    FALLBACK_VERSION        // Try older OpenCV versions
}

public enum RecoveryStrategy {
    IMMEDIATE_RETRY,        // Retry operation immediately
    EXPONENTIAL_BACKOFF,    // Retry with increasing delays
    CIRCUIT_BREAKER,        // Stop retrying after threshold
    FALLBACK_MODE,          // Switch to degraded functionality
    USER_INTERVENTION       // Require user action
}
```

### Circuit Breaker Implementation

```java
public class CircuitBreaker {
    private int failureCount = 0;
    private long lastFailureTime = 0;
    private State state = State.CLOSED;
    
    public enum State {
        CLOSED,     // Normal operation
        OPEN,       // Failing fast
        HALF_OPEN   // Testing recovery
    }
    
    public boolean allowOperation();
    public void recordSuccess();
    public void recordFailure();
}
```

## Testing Strategy

### Unit Tests
- Library detection logic validation
- Loading sequence verification
- Error handling path testing
- Diagnostic information accuracy
- UI control state management
- Error recovery mechanisms
- Performance metrics calculation

### Integration Tests
- End-to-end library loading on different architectures
- OpenCV initialization after successful library loading
- Camera functionality validation post-loading
- Error recovery mechanism testing
- UI control integration with camera pipeline
- Rotation and visibility toggle functionality
- State persistence across app lifecycle

### UI Tests
- Camera visualization toggle functionality
- Rotation button behavior and visual feedback
- Error dialog display and user interaction
- Performance under UI state changes
- Accessibility compliance testing

### Performance Tests
- Memory usage profiling during operation
- Frame rate measurement with UI controls
- Processing latency with rotation transformations
- Error recovery timing and impact
- Battery usage optimization

### Device Testing
- Multi-architecture device testing (ARM64, ARM32, x86)
- Different Android versions (API 21-34)
- Various device manufacturers and configurations
- Performance impact measurement
- Camera hardware compatibility
- Different screen orientations and resolutions

### Build System Tests
- Library acquisition automation
- APK content validation
- Architecture-specific library inclusion
- Build reproducibility across environments
- CI/CD pipeline integration (Codemagic)
- Automated testing in build pipeline

## Implementation Phases

### Phase 1: Core Infrastructure ✅ COMPLETED
- ✅ Validated existing shell scripts for OpenCV SDK download (`download-opencv-libs-only.sh/.ps1`, `setup-opencv.sh`)
- ✅ Created Native Library Manager with error recovery
- ✅ Established ErrorRecoveryManager foundation
- ✅ Ensured scripts extract native libraries to correct directories
- ✅ Validated library completeness and architecture support through script execution

### Phase 2: Enhanced Camera System ✅ COMPLETED
- ✅ Refactored CameraManager with error recovery
- ✅ Implemented robust frame processing pipeline
- ✅ Added performance monitoring and optimization
- ✅ Integrated with ErrorRecoveryManager
- ✅ Handled "Error processing captured image" scenarios

### Phase 3: UI Controls Implementation ✅ COMPLETED
- ✅ Added camera visualization toggle functionality
- ✅ Implemented 90-degree rotation control
- ✅ Created state persistence system
- ✅ Integrated UI controls with camera pipeline
- ✅ Added smooth transition animations

### Phase 4: Display System Enhancement ✅ COMPLETED
- ✅ Refactored DisplayManager for new controls
- ✅ Implemented efficient rotation algorithms
- ✅ Added frame buffer optimization
- ✅ Integrated hardware acceleration
- ✅ Handled visibility toggle without processing interruption

### Phase 5: Build System Integration ✅ COMPLETED
- ✅ Updated build configuration for proper library packaging from script-populated directories
- ✅ Added validation steps to ensure library inclusion after script execution
- ✅ Implemented architecture-specific build validation
- ✅ Integrated shell script execution with CI/CD pipeline (Codemagic)
- ✅ Ensured automated library acquisition through pre-build script execution

### Phase 6: Comprehensive Testing ✅ COMPLETED
- ✅ Unit tests for all new components
- ✅ Integration tests for UI controls and camera pipeline
- ✅ Performance testing with profiling
- ✅ Error scenario testing and validation
- ✅ Android 10 compliance verification

### Phase 7: Framebuffer Copy Optimization ✅ COMPLETED
- ✅ Eliminated unnecessary Mat copies in FrameProcessor
- ✅ Removed pooled buffer copy operations
- ✅ Replaced callback clones with ownership transfer
- ✅ Removed DisplayManager defensive cloning
- ✅ Implemented proper synchronization for thread safety
- ✅ Measured and validated CPU usage reduction

### Phase 8: Optimization and Monitoring ✅ COMPLETED
- ✅ Performance optimization and memory usage validation
- ✅ Log analysis integration and automation
- ✅ Real-time error detection and alerting
- ✅ Documentation and troubleshooting guide creation
- ✅ User experience enhancements
## 
UI Design Specifications

### Enhanced Main Layout

The main activity layout will be enhanced with new UI controls while maintaining the existing camera preview functionality:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- Camera Preview TextureView -->
    <TextureView
        android:id="@+id/texture_view"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toTopOf="@+id/control_panel"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <!-- Control Panel -->
    <LinearLayout
        android:id="@+id/control_panel"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center"
        android:padding="16dp"
        android:background="@color/control_panel_background"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">
        
        <ToggleButton
            android:id="@+id/toggle_camera_visualization"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textOn="Hide Camera"
            android:textOff="Show Camera"
            android:layout_marginEnd="16dp"
            android:background="@drawable/toggle_button_background" />
        
        <Button
            android:id="@+id/btn_rotate_camera"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Rotate 90°"
            android:drawableStart="@drawable/ic_rotate_right"
            android:background="@drawable/button_background" />
            
    </LinearLayout>

    <!-- Error Display Overlay -->
    <TextView
        android:id="@+id/error_display"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/error_background"
        android:textColor="@color/error_text"
        android:padding="12dp"
        android:visibility="gone"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

### UI Control Behavior

1. **Camera Visualization Toggle**
   - Controls TextureView visibility
   - Maintains camera processing pipeline when hidden
   - Smooth fade in/out animations
   - State persisted in SharedPreferences

2. **Rotation Control**
   - 90-degree clockwise rotation on each press
   - Cycles through 0°, 90°, 180°, 270°
   - Matrix transformation applied to display
   - Visual feedback during rotation
   - State persisted across app sessions

3. **Error Display**
   - Overlay for critical error messages
   - Auto-dismiss after user acknowledgment
   - Integration with ErrorRecoveryManager
   - User-friendly error descriptions

## Performance Specifications

### Target Metrics

| Metric | Target | Baseline | Monitoring |
|--------|--------|----------|------------|
| Memory Usage | < 50 MB | ~17 MB | Continuous |
| Frame Rate | 30 FPS | TBD | Real-time |
| Processing Latency | < 100ms | TBD | Per-frame |
| Error Recovery Time | < 2s | TBD | Per-incident |
| UI Responsiveness | 60 FPS | TBD | Continuous |
| Library Load Time | < 5s | TBD | Startup |

### Performance Monitoring

```java
public class PerformanceMonitor {
    private static final int SAMPLE_WINDOW = 30; // frames
    
    // Frame Rate Monitoring
    public void recordFrameTime(long frameTime);
    public float getCurrentFPS();
    
    // Memory Monitoring
    public long getCurrentMemoryUsage();
    public void checkMemoryLeaks();
    
    // Processing Latency
    public void recordProcessingStart();
    public void recordProcessingEnd();
    public long getAverageLatency();
    
    // Error Recovery Timing
    public void recordRecoveryStart(String errorType);
    public void recordRecoveryEnd(String errorType, boolean success);
}
```

### Optimization Strategies

1. **Memory Management**
   - Minimize framebuffer copies (target: 2 copies per frame maximum)
   - Eliminate defensive cloning where proper synchronization can be used
   - Transfer Mat ownership instead of cloning for callbacks
   - Automatic garbage collection triggers
   - Memory leak detection and prevention
   - Efficient bitmap recycling

2. **Processing Optimization**
   - In-place OpenCV operations to avoid intermediate Mat allocations
   - Remove unnecessary buffer pool copies in FrameProcessor
   - Hardware acceleration for matrix operations
   - Async processing for non-critical operations
   - Frame skipping during high load
   - Adaptive quality based on performance

3. **UI Optimization**
   - Hardware-accelerated animations
   - Efficient layout updates
   - Background thread for heavy operations
   - Smooth transition handling

### Framebuffer Copy Analysis

**Current State (5-6 copies per frame):**
1. Image → tempMat (conversion) - **NECESSARY**
2. tempMat → inputBuffer (copy) - **ELIMINATE**
3. inputBuffer → processedMat (processing)
4. processedMat → outputBuffer (copy) - **ELIMINATE**
5. outputBuffer → callbackMat (clone) - **ELIMINATE**
6. callbackMat → safeMat in DisplayManager (clone) - **ELIMINATE**
7. safeMat → Bitmap (conversion) - **NECESSARY**

**Target State (2 copies per frame):**
1. Image → Mat (conversion) - **NECESSARY**
2. Process in-place on same Mat
3. Mat → Bitmap (conversion) - **NECESSARY**

**Implementation Changes:**
- FrameProcessor: Remove buffer pool copy operations, process directly on converted Mat
- FrameProcessor: Transfer Mat ownership to callback instead of cloning
- DisplayManager: Use synchronized access instead of defensive cloning
- OpenCVProcessor: Ensure in-place operations where possible (same src/dst Mat)

## Android 10 Compliance Integration

### Privacy Controls
- Enhanced camera permission handling
- Background activity restrictions compliance
- Scoped storage usage (app-specific directories only)
- Runtime permission improvements

### Implementation Details
```java
public class Android10Compliance {
    // Enhanced Permission Handling
    public boolean requestCameraPermissionWithRationale();
    public void handlePermissionDenial();
    
    // Background Restrictions
    public boolean isBackgroundProcessingAllowed();
    public void optimizeForBackgroundRestrictions();
    
    // Scoped Storage
    public File getAppSpecificDirectory();
    public void migrateFromLegacyStorage();
}
```

This enhanced design integrates the native library fix with comprehensive camera stream functionality, providing a robust foundation for both technical stability and user experience enhancements.

## Visual Odometry System Design (New Feature)

### 8. VisualOdometryProcessor

**Purpose:** Compute 3D distance between subsequent camera frames using feature matching and geometric transforms.

**Interface:**
```java
public class VisualOdometryProcessor {
    // Core Processing
    public void processFramePair(Mat previousFrame, Mat currentFrame);
    public DistanceResult computeDistance(List<KeyPoint> prevKeypoints, List<KeyPoint> currKeypoints, 
                                        Mat descriptors1, Mat descriptors2);
    
    // Feature Detection and Matching
    public FeatureMatchResult detectAndMatchFeatures(Mat frame1, Mat frame2);
    public List<DMatch> filterMatches(List<DMatch> matches, float ratioThreshold);
    
    // Geometric Estimation
    public Mat estimateEssentialMatrix(List<Point2f> points1, List<Point2f> points2);
    public TransformResult decomposeEssentialMatrix(Mat essentialMatrix, List<Point2f> points1, List<Point2f> points2);
    
    // Distance Computation
    public Vector3D computeTranslationDistance(Mat rotation, Mat translation);
    public void setCameraIntrinsics(Mat cameraMatrix, Mat distCoeffs);
    
    // Callbacks
    public void setDistanceCallback(DistanceCallback callback);
}
```

**Key Features:**
- ORB feature detection for robust keypoint extraction
- FLANN-based feature matching with ratio test filtering
- Essential matrix estimation using RANSAC for outlier rejection
- SVD decomposition for rotation and translation extraction
- 3D distance computation in camera coordinate system
- Camera intrinsic parameter support for accurate reconstruction

### Data Models for Visual Odometry

```java
public class DistanceResult {
    public final Vector3D translation;     // Distance in x, y, z axes (meters)
    public final Vector3D rotation;        // Rotation in x, y, z axes (radians)
    public final int featureMatches;       // Number of feature matches used
    public final double confidence;        // Confidence score (0.0 - 1.0)
    public final long processingTimeMs;    // Time taken for computation
    public final boolean isValid;          // Whether result is reliable
}

public class Vector3D {
    public final double x, y, z;
    public double magnitude();
    public Vector3D normalize();
}

public class FeatureMatchResult {
    public final List<KeyPoint> keypoints1, keypoints2;
    public final Mat descriptors1, descriptors2;
    public final List<DMatch> matches;
    public final List<DMatch> goodMatches;  // After ratio test filtering
}

public class TransformResult {
    public final Mat rotation;      // 3x3 rotation matrix
    public final Mat translation;   // 3x1 translation vector
    public final List<Point2f> inlierPoints1, inlierPoints2;
    public final double reprojectionError;
}
```

### Integration with Existing System

The visual odometry system will integrate with the existing architecture:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           MainActivity                                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐ │
│  │   UI Controls   │  │  Error Handler  │  │   Native Library Manager   │ │
│  │  - Toggle Btn   │  │  - Recovery     │  │  - Sequential Loading      │ │
│  │  - Rotate Btn   │  │  - Logging      │  │  - Diagnostics             │ │
│  │  - Distance UI  │  │  - Retry Logic  │  │  - Fallback Strategies     │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          CameraManager                                      │
│  - Camera2 API Integration          - Frame Capture Pipeline                │
│  - Enhanced Error Recovery          - Buffer Management                     │
│  - Performance Monitoring           - Android 10 Compliance                │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        FrameProcessor                                       │
│  - OpenCV Processing Pipeline       - Zero-Copy Optimization                │
│  - Visual Odometry Integration      - Performance Optimization             │
│  - Frame Pair Management            - Memory Management                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    VisualOdometryProcessor (NEW)                           │
│  - Feature Detection & Matching    - Essential Matrix Estimation           │
│  - 3D Distance Computation          - Camera Intrinsic Support             │
│  - Transform Decomposition          - Confidence Assessment                │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        DisplayManager                                       │
│  - TextureView Management           - Rotation Handling                     │
│  - Visibility Toggle Control        - Frame Buffer Management              │
│  - Distance Overlay Display         - Hardware Acceleration                │
└─────────────────────────────────────────────────────────────────────────────┘
```