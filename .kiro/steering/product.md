# Product Overview

## OpenCV Camera Stream Android App

Real-time camera capture and processing application for Android devices using OpenCV computer vision library.

### Core Functionality
- Captures camera frames in real-time using Camera2 API
- Processes video frames with OpenCV image processing algorithms
- Displays processed video stream on device screen via TextureView
- Supports multiple OpenCV processing modes (edge detection, filters, etc.)

### Target Platform
- **Primary Target**: Android 10 (API 29) with enhanced privacy controls
- **Minimum Support**: Android 5.0 (API 21) for broad device compatibility
- **Architecture Support**: ARM64, ARMv7, x86, x86_64

### Key Features
- Android 10 privacy compliance with enhanced camera permission handling
- Background activity restrictions compliance
- Scoped storage compliance (no legacy external storage)
- Performance monitoring and memory optimization
- Comprehensive error handling and recovery mechanisms
- Real-time frame buffer management

### Compliance Requirements
- Android 10 enhanced privacy controls
- Camera permission model compliance
- Background activity restrictions
- No external storage usage (app-specific directories only)
- Runtime permission improvements