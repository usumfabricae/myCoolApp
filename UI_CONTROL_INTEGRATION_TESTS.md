# UI Control Integration Tests

## Overview

Comprehensive integration tests for UI controls (camera visualization toggle and rotation) have been implemented to validate Requirements 6, 7, and 9.

## Test File Location

`app/src/androidTest/java/com/example/opencvcamerastream/UIControlIntegrationTest.java`

## Tests Implemented

### 1. Camera Visualization Toggle Tests

#### `testCameraVisualizationToggleWithPipeline()`
- **Requirements**: Req-6.1, Req-6.2
- **Tests**:
  - Toggle button exists and is displayed
  - Initial state is enabled (checked)
  - Clicking toggle disables visualization
  - TextureView visibility changes appropriately
  - Camera pipeline continues processing when visualization is disabled
  - Toggle can be re-enabled
  - No crashes during toggle operations

#### `testVisualizationToggleDoesNotInterruptProcessing()`
- **Requirements**: Req-6.2
- **Tests**:
  - Camera processing continues when visualization is disabled
  - Processing resumes immediately when visualization is re-enabled
  - No interruption to camera pipeline during toggle cycle

### 2. Rotation Control Tests

#### `testRotationControlWithFrameProcessing()`
- **Requirements**: Req-7.1, Req-7.2, Req-7.3
- **Tests**:
  - Rotation button exists and is displayed
  - Rotation cycles through 0° → 90° → 180° → 270° → 0°
  - Frame processing continues without drops during rotation
  - No crashes during full rotation cycle

#### `testRapidRotations()`
- **Requirements**: Req-7.3, Req-9.4
- **Tests**:
  - Multiple rapid rotations (8 consecutive clicks)
  - Activity remains stable after rapid rotations
  - Processing continues normally after rapid operations

#### `testRotationWithVisualizationDisabled()`
- **Requirements**: Req-7.3
- **Tests**:
  - Rotation works when visualization is disabled
  - Rotation state is preserved when visualization is re-enabled

### 3. State Persistence Tests

#### `testStatePersistenceAcrossLifecycle()`
- **Requirements**: Req-6.3, Req-7.4
- **Tests**:
  - Visualization state is saved to SharedPreferences
  - Rotation state is saved to SharedPreferences
  - State is restored correctly on app restart
  - Saved values match expected values (visualization=false, rotation=180°)

#### `testStatePersistenceAcrossPauseResume()`
- **Requirements**: Req-6.3
- **Tests**:
  - State persists when app goes to background
  - State persists when app returns to foreground
  - No crashes during lifecycle transitions

#### `testUIStateConsistencyAcrossMultipleLifecycleTransitions()`
- **Requirements**: Req-6.3
- **Tests**:
  - State remains consistent through multiple pause/resume cycles
  - Activity survives multiple lifecycle transitions
  - UI state integrity is maintained

### 4. Performance Tests

#### `testPerformanceImpactOfUIControls()`
- **Requirements**: Req-9.1, Req-9.4
- **Tests**:
  - Visualization toggle completes in < 100ms
  - Rotation completes in < 100ms
  - Activity remains responsive after rapid UI operations
  - UI maintains 60 FPS responsiveness

### 5. Integration Tests

#### `testUIControlsWithDeviceOrientationChange()`
- **Requirements**: Req-7.3
- **Tests**:
  - UI controls work after device orientation change
  - State persists through orientation changes
  - Controls remain visible and functional in landscape/portrait

#### `testUIControlsAfterErrorRecovery()`
- **Requirements**: Req-11
- **Tests**:
  - UI controls work after error recovery scenarios
  - Controls remain functional after pause/resume cycle

#### `testConcurrentUIControlOperations()`
- **Requirements**: Req-9.4
- **Tests**:
  - Activity handles concurrent toggle and rotation operations
  - No race conditions or crashes with rapid concurrent operations

## Running the Tests

### Via CI/CD (Required Method)

Since this project uses **Codemagic for all builds**, tests must be run through the CI/CD pipeline:

1. **Commit and push the test file**:
   ```bash
   git add app/src/androidTest/java/com/example/opencvcamerastream/UIControlIntegrationTest.java
   git commit -m "test: Add UI control integration tests for Req-6, Req-7, Req-9"
   git push origin main
   ```

2. **Monitor the Codemagic build**:
   - The CI/CD pipeline will automatically run all instrumentation tests
   - Check the Codemagic dashboard for test results
   - Tests run on real devices/emulators in the CI environment

3. **View test results**:
   - Test reports will be available in the Codemagic build artifacts
   - Check for test execution logs and pass/fail status

### Test Execution in CI/CD

The tests will be executed as part of the standard Android instrumentation test suite:
```bash
./gradlew connectedAndroidTest
```

This command runs in the CI/CD environment with:
- Connected Android devices or emulators
- Camera permission pre-granted
- Full UI automation support

## Test Coverage

### Requirements Coverage

| Requirement | Test Coverage | Tests |
|-------------|---------------|-------|
| Req-6.1 | ✅ Toggle camera display visibility | testCameraVisualizationToggleWithPipeline |
| Req-6.2 | ✅ Continue processing when disabled | testVisualizationToggleDoesNotInterruptProcessing |
| Req-6.3 | ✅ State persistence | testStatePersistenceAcrossLifecycle, testStatePersistenceAcrossPauseResume |
| Req-7.1 | ✅ Rotate 90° clockwise | testRotationControlWithFrameProcessing |
| Req-7.2 | ✅ Cycle through rotations | testRotationControlWithFrameProcessing |
| Req-7.3 | ✅ No frame drops | testRapidRotations, testRotationWithVisualizationDisabled |
| Req-7.4 | ✅ Save rotation state | testStatePersistenceAcrossLifecycle |
| Req-9.1 | ✅ Maintain 30 FPS | testPerformanceImpactOfUIControls |
| Req-9.4 | ✅ UI responsive at 60 FPS | testPerformanceImpactOfUIControls, testRapidRotations |
| Req-11 | ✅ Error recovery | testUIControlsAfterErrorRecovery |

### Test Scenarios Covered

- ✅ Camera visualization toggle with camera pipeline
- ✅ Rotation control with frame processing
- ✅ State persistence across app lifecycle events
- ✅ Performance impact of UI control operations
- ✅ Rapid UI operations
- ✅ Concurrent operations
- ✅ Device orientation changes
- ✅ Error recovery scenarios
- ✅ Multiple lifecycle transitions

## Test Characteristics

### Test Type
- **Instrumentation Tests**: Run on actual Android devices/emulators
- **Integration Tests**: Test UI controls integrated with camera pipeline
- **UI Tests**: Use Espresso for UI interaction

### Test Dependencies
- AndroidX Test Framework
- Espresso for UI automation
- JUnit 4
- Camera permission pre-granted via `@Rule`

### Test Duration
- Individual tests: 5-15 seconds each
- Full suite: ~3-5 minutes (depending on device performance)

## Expected Test Results

All tests should **PASS** when:
1. Camera permission is granted
2. Device has a working camera
3. OpenCV libraries are properly included
4. UI controls are properly implemented in MainActivity
5. DisplayManager handles rotation and visibility correctly

## Troubleshooting

### If Tests Fail

1. **Check camera permission**: Ensure camera permission is granted in CI/CD environment
2. **Verify UI elements**: Ensure `toggleCameraVisualization` and `btnRotateCamera` exist in layout
3. **Check SharedPreferences**: Verify state persistence implementation in MainActivity
4. **Review logs**: Check Codemagic build logs for specific failure reasons

### Common Issues

- **Timing issues**: Tests include appropriate `Thread.sleep()` calls for async operations
- **UI thread violations**: All UI operations use Espresso's thread-safe mechanisms
- **State cleanup**: Each test clears SharedPreferences in `@Before` setup

## Integration with Existing Tests

These tests complement existing test suites:
- `AutomatedUITest.java`: General UI interaction tests
- `EndToEndCameraDisplayTest.java`: Complete camera-to-display flow tests
- `DeviceCompatibilityTest.java`: Device-specific compatibility tests

The new `UIControlIntegrationTest` specifically focuses on:
- UI control functionality
- State persistence
- Performance impact
- Integration with camera pipeline

## Next Steps

After CI/CD execution:
1. Review test results in Codemagic dashboard
2. Address any failing tests
3. Verify all requirements are met
4. Update task status to complete
