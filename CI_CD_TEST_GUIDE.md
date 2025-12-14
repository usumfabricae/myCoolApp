# CI/CD Test Guide - Build-Time Validation

## Commit Information

**Commit**: `47310cb`
**Branch**: `main`
**Message**: "feat: implement build-time native library validation (Task 12)"

## What Will Happen in CI/CD

### 1. Build Trigger
The push to `main` branch will automatically trigger the Codemagic CI/CD pipeline.

### 2. Pipeline Execution Flow

```
1. Environment Setup
   ↓
2. Download OpenCV Libraries
   └─ scripts/download-opencv-libs-only.sh
   ↓
3. Build Process Starts
   └─ ./gradlew assembleDebug
   ↓
4. ✨ NEW: validateNativeLibraries Task Runs Automatically
   ├─ Checks for libc++_shared.so
   ├─ Checks for libopencv_java4.so
   ├─ Validates all architectures
   └─ Reports results
   ↓
5. If Validation Passes → Continue Build
   If Validation Fails → Build Fails with Error Message
   ↓
6. APK Packaging
   ↓
7. Tests Run (including NativeLibraryValidationTest)
   ↓
8. Build Complete
```

### 3. Expected Validation Output

#### If Libraries Are Present (Expected):
```
==========================================
Native Library Validation
==========================================

Validation Results:
-------------------

armeabi-v7a:
  ✅ libc++_shared.so: Found in /opencv/src/main/jniLibs
  ✅ libopencv_java4.so: Found in /opencv/src/main/jniLibs

arm64-v8a:
  ✅ libc++_shared.so: Found in /opencv/src/main/jniLibs
  ✅ libopencv_java4.so: Found in /opencv/src/main/jniLibs

x86:
  ✅ libc++_shared.so: Found in /opencv/src/main/jniLibs
  ✅ libopencv_java4.so: Found in /opencv/src/main/jniLibs

x86_64:
  ✅ libc++_shared.so: Found in /opencv/src/main/jniLibs
  ✅ libopencv_java4.so: Found in /opencv/src/main/jniLibs

✅ All required native libraries are present!
==========================================
```

#### If Libraries Are Missing (Validation Fails):
```
==========================================
Native Library Validation
==========================================

❌ Validation Failed!

Errors:
  - Missing library: libc++_shared.so for architecture: arm64-v8a
  - Missing library: libopencv_java4.so for architecture: arm64-v8a

==========================================
SOLUTION: Run the library acquisition script
==========================================

The required OpenCV native libraries are missing.
Please run one of the following scripts to download them:

  Linux/Mac:  ./scripts/download-opencv-libs-only.sh
  Windows:    .\scripts\download-opencv-libs-only.ps1
  Or:         ./scripts/setup-opencv.sh
==========================================

BUILD FAILED
```

### 4. Unit Tests

The CI/CD pipeline will also run the new unit tests:

```bash
./gradlew test --tests "*NativeLibraryValidationTest"
```

**Test Cases That Will Run:**
- ✅ testRequiredLibrariesList
- ✅ testSupportedArchitecturesList
- ✅ testValidateLibraryPresence_AllPresent
- ✅ testValidateLibraryPresence_MissingArchitecture
- ✅ testValidateLibraryPresence_MissingSpecificLibrary
- ✅ testValidateLibraryPresence_EmptyLibraryFile
- ✅ testValidateLibraryPresence_NoLibrariesAtAll
- ✅ testValidateLibraryPresence_DirectoryDoesNotExist
- ✅ testArchitectureSpecificValidation

## Monitoring the Build

### Codemagic Dashboard
1. Go to your Codemagic dashboard
2. Find the build for commit `47310cb`
3. Watch the build logs in real-time

### Key Log Sections to Watch

#### 1. OpenCV Library Download
Look for:
```
=== SETTING UP OPENCV ANDROID SDK WITH CACHING ===
✅ OpenCV libraries found in cache - skipping download
```
or
```
✅ OpenCV setup script completed successfully
```

#### 2. Native Library Validation (NEW!)
Look for:
```
> Task :app:validateNativeLibraries
==========================================
Native Library Validation
==========================================
```

#### 3. Build Success/Failure
Look for:
```
BUILD SUCCESSFUL in Xs
```
or
```
BUILD FAILED
```

## What to Verify

### ✅ Success Criteria

1. **Validation Task Runs**
   - Check logs for `> Task :app:validateNativeLibraries`
   - Should run before `> Task :app:packageDebug`

2. **All Libraries Validated**
   - Should see checkmarks for all 4 architectures
   - Should see both required libraries per architecture

3. **Build Completes Successfully**
   - APK should be generated
   - Tests should pass

4. **Unit Tests Pass**
   - All 9 NativeLibraryValidationTest cases should pass

### 🔍 What to Check in Logs

```bash
# Search for validation task
grep "validateNativeLibraries" build.log

# Search for validation results
grep "Native Library Validation" build.log

# Search for test results
grep "NativeLibraryValidationTest" build.log

# Check for any failures
grep "FAILED" build.log
```

## Expected Timeline

- **Total Build Time**: ~5-10 minutes
- **Validation Time**: < 1 second
- **Impact**: Negligible (< 1% of total build time)

## Possible Outcomes

### Outcome 1: ✅ Complete Success (Most Likely)
- Validation passes
- Build succeeds
- Tests pass
- APK generated

**Action**: None - validation system working perfectly!

### Outcome 2: ⚠️ Validation Fails (Unlikely)
- Validation detects missing libraries
- Build fails with clear error message
- Solution instructions provided

**Action**: Check if OpenCV download script ran successfully

### Outcome 3: 🔧 Test Failures (Possible)
- Validation passes
- Build succeeds
- Some unit tests fail

**Action**: Review test logs to identify specific failures

## Troubleshooting

### If Build Fails at Validation

**Check**:
1. Did OpenCV download script run?
2. Are libraries in cache?
3. Check validation error messages

**Fix**:
```bash
# In CI/CD, the download script should run automatically
# If it didn't, check codemagic.yaml configuration
```

### If Tests Fail

**Check**:
1. Which specific test failed?
2. What was the error message?
3. Is it a test environment issue?

**Fix**:
- Review test logs
- May need to adjust test for CI/CD environment

## Next Steps After Build

### If Build Succeeds ✅
1. Review validation output in logs
2. Confirm all architectures validated
3. Download and test APK
4. Mark task as verified

### If Build Fails ❌
1. Review error messages
2. Check which libraries are missing
3. Verify OpenCV download script
4. Re-run build if needed

## Monitoring Commands

While build is running, you can check status:

```bash
# Check latest commit
git log -1 --oneline

# Check remote status
git fetch origin
git status

# View recent commits
git log --oneline -5
```

## Documentation References

- [NATIVE_LIBRARY_VALIDATION.md](NATIVE_LIBRARY_VALIDATION.md) - Full validation guide
- [BUILD_TIME_VALIDATION_SUMMARY.md](BUILD_TIME_VALIDATION_SUMMARY.md) - Implementation details
- [VALIDATION_QUICK_REFERENCE.md](VALIDATION_QUICK_REFERENCE.md) - Quick commands
- [CICD_BUILD_AUTOMATION_SUMMARY.md](CICD_BUILD_AUTOMATION_SUMMARY.md) - CI/CD overview

## Success Indicators

Look for these in the build logs:

✅ `> Task :app:validateNativeLibraries`
✅ `✅ All required native libraries are present!`
✅ `BUILD SUCCESSFUL`
✅ `NativeLibraryValidationTest > testValidateLibraryPresence_AllPresent PASSED`

---

**Build Status**: Check Codemagic dashboard
**Commit**: 47310cb
**Branch**: main
**Expected Result**: ✅ Build Success with Validation
