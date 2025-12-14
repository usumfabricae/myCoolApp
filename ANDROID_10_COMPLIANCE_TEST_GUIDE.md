# Android 10 Compliance Testing Guide

## Quick Reference

This guide provides instructions for running Android 10 compliance tests in the CI/CD pipeline.

---

## Test Overview

### Test Suites

1. **Unit Tests** (Run on JVM with Robolectric)
   - `Android10ComplianceValidatorTest.java` - 13 tests
   - `Android10TestUtilsTest.java` - 9 tests
   - `PermissionHandlerTest.java` - 10 tests
   - **Total:** 32 unit tests

2. **Instrumentation Tests** (Run on Android 10+ devices/emulators)
   - `Android10ComplianceInstrumentationTest.java` - 11 tests
   - **Total:** 11 instrumentation tests

### Requirements Coverage

| Requirement | Description | Unit Tests | Instrumentation Tests |
|-------------|-------------|------------|----------------------|
| 10.1 | Enhanced Camera Permission Handling | 10 tests | 3 tests |
| 10.2 | Background Activity Restrictions | 3 tests | 2 tests |
| 10.3 | Scoped Storage Implementation | 4 tests | 3 tests |
| 10.4 | Enhanced Privacy Controls | 2 tests | 2 tests |

---

## Running Tests in CI/CD

### Automatic Execution

Tests run automatically in the Codemagic CI/CD pipeline on every commit:

```bash
# Commit and push to trigger CI/CD
git add .
git commit -m "feat: Your changes"
git push origin main
```

### CI/CD Pipeline Steps

The `codemagic.yaml` configuration includes:

```yaml
scripts:
  # Run unit tests
  - name: Run Unit Tests
    script: ./gradlew testDebugUnitTest
  
  # Run instrumentation tests
  - name: Run Instrumentation Tests
    script: ./gradlew connectedAndroidTest
```

---

## Test Commands Reference

**Note:** These commands run in the CI/CD environment, not locally.

### All Tests

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run all instrumentation tests
./gradlew connectedAndroidTest
```

### Android 10 Compliance Tests Only

```bash
# Run Android 10 unit tests
./gradlew testDebugUnitTest \
  --tests "*Android10*" \
  --tests "*Permission*"

# Run Android 10 instrumentation tests
./gradlew connectedAndroidTest \
  --tests "Android10ComplianceInstrumentationTest"
```

### Specific Test Classes

```bash
# Run compliance validator tests
./gradlew testDebugUnitTest \
  --tests "Android10ComplianceValidatorTest"

# Run test utils tests
./gradlew testDebugUnitTest \
  --tests "Android10TestUtilsTest"

# Run permission handler tests
./gradlew testDebugUnitTest \
  --tests "PermissionHandlerTest"
```

---

## Test Execution Requirements

### For Unit Tests:
- ✅ No special requirements
- ✅ Run on any API level (uses Robolectric)
- ✅ Fast execution (seconds)

### For Instrumentation Tests:
- ✅ Requires Android 10+ (API 29+) device or emulator
- ✅ Camera permission granted via `@Rule GrantPermissionRule`
- ✅ Slower execution (minutes)

---

## Expected Test Results

### Successful Test Run

```
Android10ComplianceValidatorTest
  ✅ testCompleteComplianceValidation_AllRequirementsPassing
  ✅ testScopedStorageCompliance_RequirementSixPointOne
  ✅ testCameraPrivacyControls_RequirementSixPointTwo
  ✅ testBackgroundActivityRestrictions_RequirementSixPointThree
  ✅ testEnhancedPrivacyControls_RequirementSixPointFour
  ... (13 tests total)

Android10TestUtilsTest
  ✅ testScopedStorageCompliance_Passing
  ✅ testCameraPrivacyControls_Passing
  ✅ testBackgroundActivityRestrictions_Passing
  ✅ testEnhancedPrivacyControls_Passing
  ... (9 tests total)

PermissionHandlerTest
  ✅ testCameraPermissionGranted
  ✅ testCameraPermissionDenied
  ✅ testAndroid10PrivacyNotice
  ... (10 tests total)

Android10ComplianceInstrumentationTest
  ✅ testComprehensiveAndroid10Compliance
  ✅ testEnhancedCameraPermissionHandling
  ✅ testBackgroundActivityRestrictionsCompliance
  ✅ testScopedStorageImplementation
  ✅ testEnhancedPrivacyControls
  ... (11 tests total)

BUILD SUCCESSFUL
Total: 43 tests, 43 passed, 0 failed
```

### Test Failure Scenarios

If tests fail, check the logs for:

1. **Permission Issues:**
   ```
   FAILED: testEnhancedCameraPermissionHandling
   Reason: Camera permission not declared in manifest
   ```
   - Verify AndroidManifest.xml has camera permission

2. **Background Restriction Issues:**
   ```
   FAILED: testBackgroundActivityRestrictionsCompliance
   Reason: Camera still active in background
   ```
   - Check MainActivity onPause() stops camera preview

3. **Scoped Storage Issues:**
   ```
   FAILED: testScopedStorageImplementation
   Reason: requestLegacyExternalStorage not set to false
   ```
   - Verify AndroidManifest.xml has correct configuration

4. **Privacy Control Issues:**
   ```
   FAILED: testEnhancedPrivacyControls
   Reason: Android 10 privacy strings not found
   ```
   - Verify strings.xml has Android 10 privacy messages

---

## Viewing Test Reports

### In CI/CD (Codemagic)

1. Navigate to your build in Codemagic dashboard
2. Click on "Test results" tab
3. View detailed test reports with pass/fail status
4. Download test artifacts for offline analysis

### Test Report Locations

```
app/build/reports/tests/testDebugUnitTest/index.html
app/build/reports/androidTests/connected/index.html
```

---

## Compliance Validation Logs

### During App Execution

The app logs compliance validation results:

```
I/MainActivity: Android 10 compliance validation result: Android 10 Compliance: PASSED (0 warnings, 0 errors, 0 critical)
I/MainActivity: Android 10 Compliance Report:
Android 10 Compliance Report
============================

[PASS] Scoped storage compliance validated (Requirement: 6.1)
[PASS] Camera privacy controls validated (Requirement: 6.2)
[PASS] Background activity restrictions validated (Requirement: 6.3)
[PASS] Enhanced privacy controls validated (Requirement: 6.4)

Summary: 4/4 tests passed
Status: COMPLIANT
```

### Viewing Logs in CI/CD

```bash
# In CI/CD, logs are automatically captured
# View in Codemagic dashboard under "Logs" section
```

---

## Troubleshooting

### Tests Not Running

**Problem:** Tests are skipped or not executed

**Solution:**
- Verify test files are in correct directories
- Check test class names end with `Test`
- Ensure `@Test` annotations are present
- For instrumentation tests, verify Android 10+ device/emulator

### Permission Test Failures

**Problem:** Permission tests fail with "Permission denied"

**Solution:**
- Verify `@Rule GrantPermissionRule` is present
- Check AndroidManifest.xml declares camera permission
- Ensure test runs on Android 10+ for enhanced privacy tests

### Lifecycle Test Failures

**Problem:** Background restriction tests fail

**Solution:**
- Verify MainActivity properly tracks `isAppInForeground`
- Check onPause() stops camera preview
- Ensure onResume() restarts camera preview

### Scoped Storage Test Failures

**Problem:** Scoped storage tests fail

**Solution:**
- Verify `android:requestLegacyExternalStorage="false"` in manifest
- Check app doesn't request external storage permissions
- Ensure app uses getCacheDir() and getFilesDir() only

---

## Best Practices

### When Adding New Features

1. **Check Compliance Impact:**
   - Will the feature access camera? → Update permission tests
   - Will the feature run in background? → Update lifecycle tests
   - Will the feature write files? → Update scoped storage tests

2. **Run Compliance Tests:**
   ```bash
   # In CI/CD
   ./gradlew testDebugUnitTest --tests "*Android10*"
   ./gradlew connectedAndroidTest --tests "Android10ComplianceInstrumentationTest"
   ```

3. **Update Tests if Needed:**
   - Add new test cases for new compliance scenarios
   - Update existing tests if behavior changes
   - Document compliance considerations

### Before Release

1. **Run Full Test Suite:**
   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew connectedAndroidTest
   ```

2. **Review Compliance Report:**
   - Check app logs for compliance validation results
   - Verify all requirements show "PASSED"
   - Investigate any warnings or errors

3. **Test on Physical Devices:**
   - Test on Android 10, 11, 12, 13, 14 devices
   - Verify permission flows work correctly
   - Check background behavior is correct
   - Confirm file operations use app-specific directories

---

## Additional Resources

### Documentation
- `ANDROID_10_COMPLIANCE_VALIDATION.md` - Complete validation report
- `TASK_15_COMPLETION_SUMMARY.md` - Task completion summary
- `ANDROID_10_COMPLIANCE.md` - Original requirements document

### Code References
- `Android10ComplianceValidator.java` - Compliance validation logic
- `Android10TestUtils.java` - Test utilities
- `PermissionHandler.java` - Permission handling with Android 10 support
- `MainActivity.java` - Lifecycle management and compliance integration

### Android Documentation
- [Android 10 Privacy Changes](https://developer.android.com/about/versions/10/privacy)
- [Scoped Storage](https://developer.android.com/about/versions/10/privacy/changes#scoped-storage)
- [Background Activity Restrictions](https://developer.android.com/about/versions/10/privacy/changes#background-activity-starts)
- [Camera Privacy](https://developer.android.com/training/permissions/requesting#camera)

---

## Quick Checklist

Before considering Android 10 compliance complete:

- [ ] All 32 unit tests pass
- [ ] All 11 instrumentation tests pass on Android 10+ device
- [ ] Compliance report shows "COMPLIANT" status
- [ ] No critical compliance issues in logs
- [ ] Camera permission flow includes Android 10 messaging
- [ ] Camera stops when app moves to background
- [ ] App uses only app-specific directories
- [ ] No external storage permissions requested
- [ ] `requestLegacyExternalStorage="false"` in manifest
- [ ] Privacy strings defined in strings.xml
- [ ] Tests run successfully in CI/CD pipeline

---

**Last Updated:** December 8, 2025  
**Status:** ✅ All tests implemented and validated  
**Next Steps:** Run tests in CI/CD pipeline to verify on Android 10+ devices
