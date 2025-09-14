# OpenCV Setup Guide

## Issue: OpenCV Shared Library Loading Error

You're encountering this error because the OpenCV Android SDK native libraries (.so files) are missing from the project.

## Quick Fix Options

### Option 1: Run the Setup Script (Recommended)

The project includes an automated setup script that will download and configure OpenCV:

```bash
# From the project root directory
./scripts/setup-opencv.sh
```

This script will:
- Download OpenCV Android SDK 4.8.0
- Copy native libraries to the correct locations
- Fix compilation issues
- Configure the build properly

### Option 2: Manual OpenCV SDK Installation

If the script doesn't work, follow these manual steps:

1. **Download OpenCV Android SDK**
   - Go to https://opencv.org/releases/
   - Download OpenCV 4.8.0 Android SDK
   - Extract the zip file

2. **Copy Native Libraries**
   ```bash
   # Copy native libraries to opencv module
   cp -r OpenCV-android-sdk/sdk/native/libs/* myCoolApp/opencv/src/main/jniLibs/
   ```

3. **Copy Java Sources**
   ```bash
   # Copy Java sources to opencv module
   cp -r OpenCV-android-sdk/sdk/java/src/* myCoolApp/opencv/src/main/java/
   ```

4. **Verify Structure**
   Your opencv module should have this structure:
   ```
   opencv/
   ├── src/main/
   │   ├── java/org/opencv/...
   │   ├── jniLibs/
   │   │   ├── arm64-v8a/libopencv_java4.so
   │   │   ├── armeabi-v7a/libopencv_java4.so
   │   │   ├── x86/libopencv_java4.so
   │   │   └── x86_64/libopencv_java4.so
   │   └── AndroidManifest.xml
   └── build.gradle
   ```

### Option 3: Alternative OpenCV Integration

If you continue having issues, consider using OpenCV Manager approach:

1. **Update MainActivity OpenCV Loading**
   ```java
   // In MainActivity, update the OpenCV loader callback
   private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
       @Override
       public void onManagerConnected(int status) {
           switch (status) {
               case LoaderCallbackInterface.SUCCESS:
                   Log.d(TAG, "OpenCV loaded successfully");
                   // Initialize your OpenCV processor here
                   break;
               default:
                   super.onManagerConnected(status);
                   break;
           }
       }
   };
   ```

2. **Update onResume method**
   ```java
   @Override
   public void onResume() {
       super.onResume();
       if (!OpenCVLoader.initDebug()) {
           OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
       } else {
           mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
       }
   }
   ```

## Troubleshooting

### Common Issues:

1. **"Library not found" error**
   - Ensure native libraries are in the correct jniLibs directories
   - Check that the .so files match your target architecture

2. **"Class not found" error**
   - Ensure OpenCV Java sources are properly copied
   - Verify the opencv module is included in settings.gradle

3. **Build errors**
   - Clean and rebuild the project
   - Sync Gradle files
   - Check that all dependencies are properly configured

### Verification Steps:

1. **Check Native Libraries**
   ```bash
   find opencv/src/main/jniLibs -name "*.so"
   ```
   Should show libopencv_java4.so for each architecture.

2. **Check Java Classes**
   ```bash
   find opencv/src/main/java -name "Mat.java"
   ```
   Should find the OpenCV Mat class.

3. **Test Build**
   ```bash
   ./gradlew assembleDebug
   ```
   Should build without OpenCV-related errors.

## CI/CD Considerations

Since this project uses exclusive CI/CD through Codemagic, the OpenCV setup should be automated in the CI pipeline. The setup script is designed to work in CI environments.

## Next Steps

After fixing the OpenCV setup:

1. Clean and rebuild the project
2. Test OpenCV initialization in the app
3. Verify camera functionality works properly
4. Run the comprehensive test suite

## Support

If you continue having issues:
1. Check the CI/CD logs for detailed error messages
2. Verify your device architecture matches the available .so files
3. Consider using OpenCV Manager for dynamic loading