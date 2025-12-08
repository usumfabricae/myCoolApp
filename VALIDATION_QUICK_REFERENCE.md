# Native Library Validation - Quick Reference

## Quick Commands

### Validate Libraries (Manual)
```bash
# Linux/Mac
./scripts/validate-native-libraries.sh

# Windows
.\scripts\validate-native-libraries.ps1
```

### Download Missing Libraries
```bash
# Linux/Mac
./scripts/download-opencv-libs-only.sh

# Windows
.\scripts\download-opencv-libs-only.ps1
```

### Build (Validation Runs Automatically)
```bash
# CI/CD only - local builds not permitted
./gradlew assembleDebug
```

## What Gets Validated

### Required Libraries (per architecture)
- ✅ `libc++_shared.so` - C++ Standard Library
- ✅ `libopencv_java4.so` - OpenCV JNI Bridge

### Architectures Checked
- ✅ `armeabi-v7a` - 32-bit ARM
- ✅ `arm64-v8a` - 64-bit ARM  
- ✅ `x86` - 32-bit x86
- ✅ `x86_64` - 64-bit x86

## When Validation Runs

### Automatic (CI/CD)
- Before `assembleDebug`
- Before `assembleRelease`
- Before any `package*` tasks

### Manual
- Run validation scripts anytime
- No build required

## Common Scenarios

### ✅ All Libraries Present
```
✅ All required native libraries are present!

arm64-v8a:
  ✅ libc++_shared.so: Found
  ✅ libopencv_java4.so: Found
...
```
**Action**: None - proceed with build

### ❌ Missing Libraries
```
❌ Validation Failed!

Errors:
  - Missing library: libc++_shared.so for architecture: arm64-v8a
```
**Action**: Run library acquisition script

### ⚠️ Duplicate Libraries
```
⚠️ Warnings:
  - Duplicate library: libc++_shared.so found in multiple locations
```
**Action**: None - handled automatically by Gradle

## Troubleshooting

### Problem: Validation fails
**Solution**:
```bash
./scripts/download-opencv-libs-only.sh
./scripts/validate-native-libraries.sh
```

### Problem: Libraries downloaded but validation still fails
**Solution**:
```bash
# Clean and re-download
rm -rf opencv/src/main/jniLibs/*
./scripts/download-opencv-libs-only.sh
```

### Problem: Build fails in CI/CD
**Check**:
1. CI/CD logs for validation output
2. Verify library download script ran
3. Check cache status

## Exit Codes

- `0` - Validation passed
- `1` - Validation failed

## Integration Points

### Gradle Build
- Task: `validateNativeLibraries`
- Location: `app/build.gradle`
- Trigger: Automatic before packaging

### CI/CD Pipeline
- Script: `validate-native-libraries.sh`
- Location: `scripts/`
- Trigger: Manual or pre-build step

### Unit Tests
- Class: `NativeLibraryValidationTest`
- Location: `app/src/test/.../build/`
- Trigger: `./gradlew test`

## Key Files

```
app/build.gradle                          # Gradle validation task
opencv/build.gradle                       # OpenCV module validation
scripts/validate-native-libraries.sh      # Shell validation script
scripts/validate-native-libraries.ps1     # PowerShell validation script
scripts/download-opencv-libs-only.sh      # Library download script
NATIVE_LIBRARY_VALIDATION.md              # Full documentation
```

## Quick Checks

### Check if libraries exist
```bash
# Linux/Mac
find opencv/src/main/jniLibs -name "*.so"

# Windows
Get-ChildItem -Path opencv\src\main\jniLibs -Filter *.so -Recurse
```

### Count libraries per architecture
```bash
# Linux/Mac
for arch in armeabi-v7a arm64-v8a x86 x86_64; do
  echo "$arch: $(find opencv/src/main/jniLibs/$arch -name "*.so" 2>/dev/null | wc -l) libraries"
done
```

### Check library sizes
```bash
# Linux/Mac
find opencv/src/main/jniLibs -name "*.so" -exec ls -lh {} \;
```

## Best Practices

1. ✅ Always validate before building
2. ✅ Run validation in CI/CD pre-build step
3. ✅ Don't commit .so files to git
4. ✅ Use caching in CI/CD for libraries
5. ✅ Re-validate after OpenCV version updates

## Support

For detailed information, see:
- [NATIVE_LIBRARY_VALIDATION.md](NATIVE_LIBRARY_VALIDATION.md)
- [BUILD_TIME_VALIDATION_SUMMARY.md](BUILD_TIME_VALIDATION_SUMMARY.md)
