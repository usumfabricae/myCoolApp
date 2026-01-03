# Product Overview

## OpenCV Camera Stream Android App

Real-time camera capture and processing application for Android devices using OpenCV computer vision library with advanced native library management, enhanced UI controls, and visual odometry capabilities.

### Core Functionality
- Captures camera frames in real-time using Camera2 API
- Processes video frames with OpenCV image processing algorithms
- Displays processed video stream on device screen via TextureView
- Supports multiple OpenCV processing modes (edge detection, filters, etc.)
- **Enhanced UI Controls**: Camera visualization toggle and 90-degree rotation controls
- **Visual Odometry**: 3D distance tracking between camera frames using feature matching
- **Zero-Copy Processing**: Optimized pipeline reducing framebuffer operations from 5-6 to 2 copies per frame

### Target Platform
- **Primary Target**: Android 10 (API 29) with enhanced privacy controls
- **Minimum Support**: Android 5.0 (API 21) for broad device compatibility
- **Architecture Support**: ARM64, ARMv7, x86, x86_64

### Key Features

#### Native Library Management
- **Robust Loading System**: Sequential dependency loading (libc++_shared.so → libopencv_java4.so)
- **Automated Acquisition**: Shell scripts for OpenCV SDK download and extraction
- **Fallback Strategies**: Multiple loading approaches with comprehensive error handling
- **Build Integration**: CI/CD pipeline integration with library validation

#### Enhanced Camera Controls
- **Visualization Toggle**: Hide/show camera display while maintaining processing pipeline
- **Rotation Control**: 90-degree clockwise rotation with state persistence
- **State Management**: SharedPreferences integration for UI state persistence
- **Smooth Transitions**: Hardware-accelerated animations without processing interruption

#### Performance Optimization
- **Zero-Copy Processing**: Minimized framebuffer copies (target: 2 copies per frame)
- **Memory Management**: Reduced memory pressure and GC frequency
- **CPU Optimization**: Framebuffer operations <30% of total processing time
- **Hardware Acceleration**: GPU-accelerated matrix transformations and rendering

#### Error Recovery System
- **Circuit Breaker Pattern**: Automatic failure detection and recovery
- **Retry Mechanisms**: Exponential backoff for transient failures
- **Comprehensive Logging**: Detailed diagnostics and error categorization
- **User-Friendly Messages**: Clear error reporting with recovery suggestions

#### Visual Odometry (NEW)
- **Feature Detection**: ORB-based keypoint extraction for robust tracking
- **3D Distance Computation**: Real-time camera movement estimation (x, y, z axes)
- **Geometric Transforms**: Essential matrix estimation with RANSAC outlier rejection
- **Camera Calibration**: Intrinsic parameter support for accurate 3D reconstruction
- **Real-time Display**: Distance overlay with data export capabilities

### Performance Targets
- **Memory Usage**: < 50 MB during operation
- **Frame Rate**: 30 FPS camera capture, 60 FPS UI responsiveness
- **Processing Latency**: < 100ms per frame
- **Error Recovery**: < 2s recovery time
- **CPU Usage**: Framebuffer operations < 30% of total processing

### Compliance Requirements
- Android 10 enhanced privacy controls
- Camera permission model compliance
- Background activity restrictions
- No external storage usage (app-specific directories only)
- Runtime permission improvements
- Scoped storage compliance with app-specific directories