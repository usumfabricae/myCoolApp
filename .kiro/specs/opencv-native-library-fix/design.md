# Design Document

## Overview

The OpenCV native library loading failure is caused by missing `libc++_shared.so` and `libopencv_java4.so` files in the APK. The current build configuration expects these libraries to be present in the `opencv/src/main/jniLibs` directories, but they are empty. This design addresses the issue through a multi-layered approach: proper library acquisition, build system configuration, and robust runtime loading.

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                         │
├─────────────────────────────────────────────────────────────┤
│  Native Library Manager                                     │
│  ├── Library Loader (Sequential dependency loading)        │
│  ├── Error Handler (Fallback strategies)                   │
│  └── Diagnostics (Library detection & validation)          │
├─────────────────────────────────────────────────────────────┤
│                    Build System Layer                       │
│  ├── Library Acquisition (Download/Extract)                │
│  ├── Architecture Validation (Multi-ABI support)          │
│  └── Packaging Configuration (APK inclusion)               │
├─────────────────────────────────────────────────────────────┤
│                    Native Libraries                         │
│  ├── libc++_shared.so (C++ Standard Library)              │
│  └── libopencv_java4.so (OpenCV JNI Bridge)               │
└─────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. Native Library Manager

**Purpose:** Centralized management of native library loading with proper dependency ordering and error handling.

**Interface:**
```java
public class NativeLibraryManager {
    public static boolean loadOpenCVLibraries();
    public static boolean isLibraryAvailable(String libraryName);
    public static String getDiagnosticInfo();
    public static void setLoadingStrategy(LoadingStrategy strategy);
}
```

**Key Features:**
- Sequential loading: `libc++_shared.so` → `libopencv_java4.so`
- Multiple loading strategies (System.loadLibrary, System.load with full path)
- Comprehensive error reporting with specific failure reasons
- Library availability detection before loading attempts

### 2. Library Acquisition System

**Purpose:** Automated download and extraction of OpenCV native libraries during build process.

**Implementation Strategy:**
- Gradle task to download OpenCV Android SDK if not present
- Extraction of native libraries to correct architecture directories
- Validation of library completeness and architecture compatibility
- Integration with existing build configuration

### 3. Build System Configuration

**Purpose:** Ensure proper inclusion and packaging of native libraries in APK.

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
            jniLibs.srcDirs = ['src/main/jniLibs', '../opencv/src/main/jniLibs']
        }
    }
}
```

### 4. Runtime Loading Strategy

**Purpose:** Robust library loading with fallback mechanisms and detailed diagnostics.

**Loading Sequence:**
1. **Pre-loading Validation:** Check if libraries exist in APK
2. **Dependency Loading:** Load `libc++_shared.so` first
3. **OpenCV Loading:** Load `libopencv_java4.so` after C++ runtime
4. **Fallback Strategies:** Try alternative loading methods if primary fails
5. **Error Reporting:** Provide detailed diagnostics for troubleshooting

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
}
```

## Error Handling

### Error Categories

1. **Missing Library Files**
   - Detection: APK inspection during startup
   - Recovery: Display user-friendly error with download instructions
   - Logging: Detailed file system diagnostics

2. **Dependency Loading Failures**
   - Detection: UnsatisfiedLinkError during System.loadLibrary()
   - Recovery: Try alternative loading strategies
   - Logging: Specific library and error code information

3. **Architecture Mismatches**
   - Detection: Compare device ABI with available libraries
   - Recovery: Attempt loading compatible architecture
   - Logging: Device ABI vs available library architectures

4. **Version Conflicts**
   - Detection: Library version validation
   - Recovery: Use most compatible version available
   - Logging: Version information for all detected libraries

### Error Recovery Strategies

```java
public enum LoadingStrategy {
    SYSTEM_LOAD_LIBRARY,    // Standard Android loading
    DIRECT_PATH_LOAD,       // Load with full file path
    EXTRACTED_LOAD,         // Extract to temp and load
    FALLBACK_VERSION        // Try older OpenCV versions
}
```

## Testing Strategy

### Unit Tests
- Library detection logic validation
- Loading sequence verification
- Error handling path testing
- Diagnostic information accuracy

### Integration Tests
- End-to-end library loading on different architectures
- OpenCV initialization after successful library loading
- Camera functionality validation post-loading
- Error recovery mechanism testing

### Device Testing
- Multi-architecture device testing (ARM64, ARM32, x86)
- Different Android versions (API 21-34)
- Various device manufacturers and configurations
- Performance impact measurement

### Build System Tests
- Library acquisition automation
- APK content validation
- Architecture-specific library inclusion
- Build reproducibility across environments

## Implementation Phases

### Phase 1: Library Acquisition
- Implement Gradle task for OpenCV SDK download
- Extract native libraries to correct directories
- Validate library completeness and architecture support

### Phase 2: Native Library Manager
- Create centralized library loading system
- Implement sequential dependency loading
- Add comprehensive error handling and diagnostics

### Phase 3: Build System Integration
- Update build configuration for proper library packaging
- Add validation steps to ensure library inclusion
- Implement architecture-specific build validation

### Phase 4: Runtime Optimization
- Add library pre-loading validation
- Implement fallback loading strategies
- Enhance error reporting and user feedback

### Phase 5: Testing and Validation
- Comprehensive testing across architectures and devices
- Performance optimization and memory usage validation
- Documentation and troubleshooting guide creation