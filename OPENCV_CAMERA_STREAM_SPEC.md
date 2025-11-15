# OpenCV Camera Stream Android App - Technical Specification

## Project Overview

Real-time camera capture and processing application for Android devices using OpenCV computer vision library with enhanced UI controls and Android 10 compliance.

### Version: 2.0
### Last Updated: November 15, 2025
### Status: In Development

---

## 1. Requirements

### 1.1 Functional Requirements

#### Core Camera Functionality
- **FR-001**: Capture camera frames in real-time using Camera2 API
- **FR-002**: Process video frames with OpenCV image processing algorithms
- **FR-003**: Display processed video stream on device screen via TextureView
- **FR-004**: Support multiple OpenCV processing modes (edge detection, filters, etc.)
- **FR-005**: Handle camera permission requests with Android 10 compliance

#### New UI Controls (Based on User Request)
- **FR-006**: Provide toggle button to enable/disable camera visualization
- **FR-007**: Implement 90-degree clockwise rotation control for camera display
- **FR-008**: Maintain processing pipeline even when visualization is disabled
- **FR-009**: Persist UI control states across app sessions

#### Error Handling & Recovery (Based on Log Analysis)
- **FR-010**: Implement robust camera frame processing error recovery
- **FR-011**: Handle "Error processing captured image" scenarios gracefully
- **FR-012**: Provide user feedback for camera processing issues
- **FR-013**: Implement automatic retry mechanisms for failed operations

### 1.2 Non-Functional Requirements

#### Performance Requirements
- **NFR-001**: Maintain 30 FPS camera capture rate
- **NFR-002**: Keep memory usage under 50 MB (current: ~17 MB baseline)
- **NFR-003**: Process frames with <100ms latency
- **NFR-004**: Handle rotation changes without frame drops

#### Android 10 Compliance
- **NFR-005**: Enhanced camera permission handling
- **NFR-006**: Background activity restrictions compliance
- **NFR-007**: Scoped storage compliance (app-specific directories only)
- **NFR-008**: Runtime permission improvements

#### Quality Requirements
- **NFR-009**: Zero tolerance for crashes during normal operation
- **NFR-010**: Graceful degradation when camera is unavailable
- **NFR-011**: Comprehensive error logging for debugging

---

## 2. System Design

### 2.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    MainActivity                         │
│  ┌─────────────────┐  ┌─────────────────┐             │
│  │   UI Controls   │  │  Error Handler  │             │
│  │  - Toggle Btn   │  │  - Recovery     │             │
│  │  - Rotate Btn   │  │  - Logging      │             │
│  └─────────────────┘  └─────────────────┘             │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                 CameraManager                           │
│  - Camera2 API Integration                              │
│  - Frame Capture Pipeline                               │
│  - Error Recovery Logic                                 │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│               OpenCVProcessor                           │
│  - Image Processing Pipeline                            │
│  - Format Conversion                                    │
│  - Error Handling for Processing Failures              │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│               DisplayManager                            │
│  - TextureView Management                               │
│  - Rotation Handling                                    │
│  - Visibility Toggle                                    │
│  - Frame Buffer Management                              │
└─────────────────────────────────────────────────────────┘
```

### 2.2 Component Design

#### 2.2.1 MainActivity Enhancements
```java
public class MainActivity extends AppCompatActivity {
    // New UI Controls
    private ToggleButton cameraVisualizationToggle;
    private Button rotationButton;
    
    // State Management
    private boolean isVisualizationEnabled = true;
    private int currentRotation = 0; // 0, 90, 180, 270
    
    // Error Recovery
    private ErrorRecoveryManager errorRecoveryManager;
}
```

#### 2.2.2 DisplayManager Updates
```java
public class DisplayManager {
    // Rotation Management
    public void rotateDisplay(int degrees);
    public void setVisualizationEnabled(boolean enabled);
    
    // Error Handling
    public void handleProcessingError(Exception error);
    public boolean recoverFromError();
}
```

#### 2.2.3 New ErrorRecoveryManager
```java
public class ErrorRecoveryManager {
    // Based on log analysis findings
    public void handleCameraProcessingError();
    public void retryOperation(Runnable operation, int maxRetries);
    public void logErrorForAnalysis(String error, Exception exception);
}
```

### 2.3 Data Flow

```
Camera2 API → Frame Capture → OpenCV Processing → Display Buffer
     │              │               │                    │
     │              │               │                    ▼
     │              │               │            ┌─────────────┐
     │              │               │            │ Rotation    │
     │              │               │            │ Transform   │
     │              │               │            └─────────────┘
     │              │               │                    │
     │              │               │                    ▼
     │              │               │            ┌─────────────┐
     │              │               │            │ Visibility  │
     │              │               │            │ Control     │
     │              │               │            └─────────────┘
     │              │               │                    │
     │              │               │                    ▼
     │              │               │              TextureView
     │              │               │
     ▼              ▼               ▼
┌─────────────────────────────────────┐
│        Error Recovery Pipeline      │
│  - Capture Errors                   │
│  - Processing Errors                │
│  - Display Errors                   │
└─────────────────────────────────────┘
```

---

## 3. Implementation Tasks

### 3.1 Phase 1: Core Infrastructure (Priority: High)

#### Task 1.1: Error Recovery System
**Estimated Effort**: 2 days
**Dependencies**: Log analysis findings

**Subtasks**:
- [ ] Create `ErrorRecoveryManager` class
- [ ] Implement camera processing error handling
- [ ] Add retry mechanisms for failed operations
- [ ] Integrate comprehensive error logging
- [ ] Add error recovery unit tests

**Acceptance Criteria**:
- Camera processing errors are caught and handled gracefully
- Failed operations retry up to 3 times before failing
- All errors are logged with context for analysis
- App remains stable during error conditions

#### Task 1.2: Enhanced CameraManager
**Estimated Effort**: 3 days
**Dependencies**: Task 1.1

**Subtasks**:
- [ ] Refactor camera frame processing pipeline
- [ ] Add buffer management improvements
- [ ] Implement frame processing error recovery
- [ ] Add performance monitoring hooks
- [ ] Create camera manager unit tests

**Acceptance Criteria**:
- Frame processing errors are eliminated
- Memory usage remains under 25 MB during operation
- 30 FPS capture rate maintained
- Automatic recovery from camera disconnection

### 3.2 Phase 2: UI Controls Implementation (Priority: High)

#### Task 2.1: Camera Visualization Toggle
**Estimated Effort**: 1.5 days
**Dependencies**: Task 1.2

**Subtasks**:
- [ ] Add toggle button to main layout
- [ ] Implement visibility control logic
- [ ] Maintain processing pipeline when hidden
- [ ] Add state persistence (SharedPreferences)
- [ ] Create UI interaction tests

**Acceptance Criteria**:
- Toggle button controls TextureView visibility
- Camera processing continues when visualization is off
- Toggle state persists across app restarts
- Smooth transition animations

#### Task 2.2: Camera Rotation Control
**Estimated Effort**: 2 days
**Dependencies**: Task 2.1

**Subtasks**:
- [ ] Add rotation button to main layout
- [ ] Implement 90-degree rotation logic
- [ ] Handle matrix transformations for display
- [ ] Add rotation state persistence
- [ ] Optimize rotation performance
- [ ] Create rotation unit tests

**Acceptance Criteria**:
- Button rotates camera view by 90 degrees clockwise
- Rotation cycles through 0°, 90°, 180°, 270°
- No frame drops during rotation
- Rotation state persists across sessions

### 3.3 Phase 3: DisplayManager Enhancements (Priority: Medium)

#### Task 3.1: Advanced Display Controls
**Estimated Effort**: 2 days
**Dependencies**: Task 2.2

**Subtasks**:
- [ ] Refactor DisplayManager for new controls
- [ ] Implement efficient rotation algorithms
- [ ] Add frame buffer optimization
- [ ] Integrate with error recovery system
- [ ] Performance testing and optimization

**Acceptance Criteria**:
- Rotation transformations are hardware-accelerated
- Memory usage for display buffers is optimized
- Error recovery integrates with display operations
- 60 FPS UI responsiveness maintained

### 3.4 Phase 4: Testing & Optimization (Priority: Medium)

#### Task 4.1: Comprehensive Testing
**Estimated Effort**: 3 days
**Dependencies**: All previous tasks

**Subtasks**:
- [ ] Unit tests for all new components
- [ ] Integration tests for UI controls
- [ ] Performance testing with profiling
- [ ] Error scenario testing
- [ ] Android 10 compliance testing

**Acceptance Criteria**:
- 90%+ code coverage for new features
- All error scenarios tested and handled
- Performance benchmarks meet requirements
- Android 10 compliance verified

#### Task 4.2: Log Analysis Integration
**Estimated Effort**: 1 day
**Dependencies**: Task 4.1

**Subtasks**:
- [ ] Integrate log collection scripts with build
- [ ] Add automated error pattern detection
- [ ] Create performance monitoring dashboard
- [ ] Document troubleshooting procedures

**Acceptance Criteria**:
- Automated log collection in CI/CD pipeline
- Real-time error detection and alerting
- Performance metrics tracking
- Comprehensive troubleshooting guide

---

## 4. Technical Specifications

### 4.1 UI Layout Updates

#### New Control Layout
```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center"
    android:padding="16dp">
    
    <ToggleButton
        android:id="@+id/toggle_camera_visualization"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textOn="Hide Camera"
        android:textOff="Show Camera"
        android:layout_marginEnd="16dp" />
    
    <Button
        android:id="@+id/btn_rotate_camera"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Rotate 90°"
        android:drawableStart="@drawable/ic_rotate_right" />
        
</LinearLayout>
```

### 4.2 Performance Targets

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| Memory Usage | < 50 MB | ~17 MB | ✅ Good |
| Frame Rate | 30 FPS | TBD | 🔄 Testing |
| Processing Latency | < 100ms | TBD | 🔄 Testing |
| Error Recovery Time | < 2s | TBD | 🔄 Implementation |

### 4.3 Error Handling Strategy

Based on log analysis findings:

1. **Camera Processing Errors**
   - Implement circuit breaker pattern
   - Automatic retry with exponential backoff
   - Fallback to lower resolution if needed

2. **Memory Management**
   - Frame buffer pooling
   - Automatic garbage collection triggers
   - Memory leak detection

3. **UI Responsiveness**
   - Async processing for all camera operations
   - UI thread protection
   - Progress indicators for long operations

---

## 5. Testing Strategy

### 5.1 Unit Testing
- All new classes and methods
- Error recovery scenarios
- State persistence logic
- Performance critical paths

### 5.2 Integration Testing
- Camera → Processing → Display pipeline
- UI control interactions
- Error recovery integration
- Android 10 permission flows

### 5.3 Performance Testing
- Memory usage profiling
- Frame rate measurement
- Rotation performance
- Error recovery timing

### 5.4 User Acceptance Testing
- UI control usability
- Error message clarity
- Performance perception
- Feature completeness

---

## 6. Deployment Strategy

### 6.1 CI/CD Integration
- Automated testing in Codemagic pipeline
- Performance regression detection
- Log analysis integration
- Automated APK generation

### 6.2 Rollout Plan
1. **Alpha**: Internal testing with log collection
2. **Beta**: Limited user testing with analytics
3. **Production**: Gradual rollout with monitoring

### 6.3 Monitoring & Analytics
- Crash reporting integration
- Performance metrics collection
- User interaction analytics
- Error pattern analysis

---

## 7. Risk Assessment

### 7.1 Technical Risks
| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Camera processing errors | High | Medium | Robust error recovery |
| Performance degradation | Medium | Low | Continuous profiling |
| UI responsiveness issues | Medium | Low | Async operations |
| Memory leaks | High | Low | Automated testing |

### 7.2 Timeline Risks
- **Dependency on error resolution**: High priority for Phase 1
- **Testing complexity**: Allocate sufficient time for Phase 4
- **Performance optimization**: May require additional iteration

---

## 8. Success Criteria

### 8.1 Functional Success
- [ ] Camera visualization toggle works reliably
- [ ] 90-degree rotation functions correctly
- [ ] Error recovery eliminates processing failures
- [ ] UI controls are intuitive and responsive

### 8.2 Technical Success
- [ ] Zero crashes during normal operation
- [ ] Memory usage stays under targets
- [ ] Performance meets or exceeds requirements
- [ ] Android 10 compliance maintained

### 8.3 Quality Success
- [ ] 90%+ test coverage achieved
- [ ] All error scenarios handled gracefully
- [ ] User feedback is positive
- [ ] Log analysis shows improved stability

---

## Appendix A: Log Analysis Findings

Based on recent log collection (2025-11-15):

### Key Issues Identified
1. **Camera Processing Error**: "Error processing captured image" - HIGH priority
2. **Memory Usage**: 17 MB baseline - within acceptable range
3. **Permissions**: Camera permission properly granted

### Recommendations Implemented
- Enhanced error recovery for camera processing
- Memory usage monitoring and optimization
- Comprehensive logging for future analysis

### Monitoring Strategy
- Automated log collection during testing
- Real-time error pattern detection
- Performance metrics tracking