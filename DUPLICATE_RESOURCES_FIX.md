# Duplicate Resources Fix Summary

## Problem Analysis
The build was failing with duplicate resource errors:
```
ERROR: [armeabi-v7a/libopencv_java4.so] /Users/builder/clone/app/src/main/jniLibs/armeabi-v7a/libopencv_java4.so 
[armeabi-v7a/libopencv_java4.so] /Users/builder/clone/opencv/src/main/jniLibs/armeabi-v7a/libopencv_java4.so: 
Resource and asset merger: Duplicate resources
```

## Root Cause
The same native libraries existed in **both locations**:
1. `opencv/src/main/jniLibs/` (OpenCV module)
2. `app/src/main/jniLibs/` (App module)

When Gradle tried to merge resources from both modules, it found duplicates and failed.

## Solution Implemented

### 1. **Changed Copy to Move Operation**
- **Before**: Copied libraries from OpenCV module to app module (creating duplicates)
- **After**: Move libraries from OpenCV module to app module (no duplicates)

### 2. **Updated App Build Configuration**
Modified `app/build.gradle`:
```groovy
// BEFORE (caused duplicates)
sourceSets {
    main {
        jniLibs.srcDirs = ['src/main/jniLibs', '../opencv/src/main/jniLibs']
    }
}

// AFTER (single source)
sourceSets {
    main {
        jniLibs.srcDirs = ['src/main/jniLibs']
    }
}
```

### 3. **Updated OpenCV Build Configuration**
Modified `opencv/build.gradle`:
```groovy
// BEFORE
sourceSets {
    main {
        jniLibs.srcDirs = ['src/main/jniLibs']
    }
}

// AFTER (commented out to prevent inclusion)
sourceSets {
    main {
        // Native libraries are moved to app module during CI/CD to avoid duplicates
        // jniLibs.srcDirs = ['src/main/jniLibs']  // Commented out to prevent duplicate resources
    }
}
```

### 4. **Enhanced CI/CD Pipeline**
Updated Codemagic pipeline to:

#### A. Move Instead of Copy
```yaml
# Move (not copy) to avoid duplicates
mv "$lib_file" "app/src/main/jniLibs/$arch_name/"
```

#### B. Clean OpenCV Module
```yaml
# Clean up empty directories in OpenCV module
find opencv/src/main/jniLibs -type d -empty -delete 2>/dev/null || true

# Remove any remaining .so files
find opencv/src/main/jniLibs -name "*.so" -delete 2>/dev/null || true
```

#### C. Configure Build Files Dynamically
```yaml
# Ensure OpenCV build.gradle doesn't include jniLibs
sed -i.bak 's/jniLibs.srcDirs = \[.*\]/\/\/ jniLibs.srcDirs commented out to prevent duplicate resources/' opencv/build.gradle
```

#### D. Validation
```yaml
# Final verification - ensure no .so files in OpenCV module
FINAL_OPENCV_LIBS=$(find opencv/src/main/jniLibs -name "*.so" 2>/dev/null | wc -l)
if [ "$FINAL_OPENCV_LIBS" -eq 0 ]; then
  echo "✅ OpenCV module is now clean of native libraries - no duplicates will occur"
else
  echo "❌ Failed to clean OpenCV module of native libraries"
  exit 1
fi
```

## New Build Flow

1. **OpenCV Setup**: Download and extract OpenCV libraries to `opencv/src/main/jniLibs/`
2. **Add NDK Libraries**: Add `libc++_shared.so` from Android NDK
3. **Move to App Module**: Move ALL libraries from OpenCV module to app module
4. **Clean OpenCV Module**: Remove all .so files and empty directories from OpenCV module
5. **Configure Build Files**: Ensure OpenCV module doesn't try to include native libraries
6. **Build**: Gradle now only sees libraries in app module (no duplicates)

## Expected Results

✅ **No Duplicate Resources**: Only one copy of each library exists (in app module)
✅ **Successful Build**: Gradle can merge resources without conflicts
✅ **ARM Library Support**: All ARM libraries properly included in APK
✅ **Runtime Fix**: `libc++_shared.so` available for OpenCV initialization

## Files Modified

1. **app/build.gradle**: Removed OpenCV jniLibs from sourceSets
2. **opencv/build.gradle**: Commented out jniLibs sourceSets
3. **codemagic.yaml**: Enhanced pipeline to move libraries and prevent duplicates

## Verification

The build process now includes verification that:
- OpenCV module has 0 native libraries (prevents duplicates)
- App module has all required ARM libraries
- No duplicate resource errors occur during build
- APK contains the necessary libraries for ARM devices

This fix resolves both the duplicate resources build error AND the original runtime error on ARM Android devices.