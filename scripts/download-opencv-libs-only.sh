#!/bin/bash

# Quick OpenCV Libraries Download Script
# Downloads only the essential native libraries needed to fix the immediate runtime error

set -e

echo "=== QUICK OPENCV LIBRARIES DOWNLOAD ==="

# Configuration
OPENCV_VERSION="4.8.0"
OPENCV_ANDROID_SDK_URL="https://github.com/opencv/opencv/releases/download/${OPENCV_VERSION}/opencv-${OPENCV_VERSION}-android-sdk.zip"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
TEMP_DIR="$PROJECT_ROOT/temp-opencv-libs"

echo "Downloading OpenCV libraries to fix runtime error..."
echo "OpenCV version: $OPENCV_VERSION"

# Create temp directory
mkdir -p "$TEMP_DIR"
cd "$TEMP_DIR"

# Download OpenCV SDK
echo "Downloading OpenCV Android SDK..."
if command -v curl >/dev/null 2>&1; then
    curl -L -o "opencv-android-sdk.zip" "$OPENCV_ANDROID_SDK_URL"
elif command -v wget >/dev/null 2>&1; then
    wget -O "opencv-android-sdk.zip" "$OPENCV_ANDROID_SDK_URL"
else
    echo "❌ Error: Neither curl nor wget available"
    exit 1
fi

# Extract
echo "Extracting OpenCV SDK..."
unzip -q "opencv-android-sdk.zip"

# Find extracted directory
OPENCV_DIR=$(find . -name "OpenCV-android-sdk" -type d | head -1)
if [ -z "$OPENCV_DIR" ]; then
    echo "❌ Could not find extracted OpenCV directory"
    exit 1
fi

# Copy native libraries
echo "Copying native libraries..."
NATIVE_LIBS="$OPENCV_DIR/sdk/native/libs"
if [ -d "$NATIVE_LIBS" ]; then
    # Ensure target directories exist
    mkdir -p "$PROJECT_ROOT/opencv/src/main/jniLibs"
    
    # Copy all architecture libraries
    cp -r "$NATIVE_LIBS"/* "$PROJECT_ROOT/opencv/src/main/jniLibs/"
    
    echo "✅ Native libraries copied successfully"
    
    # Show what was copied
    echo "Libraries copied:"
    for arch in "$PROJECT_ROOT/opencv/src/main/jniLibs"/*; do
        if [ -d "$arch" ]; then
            arch_name=$(basename "$arch")
            lib_count=$(find "$arch" -name "*.so" | wc -l)
            echo "  $arch_name: $lib_count libraries"
            
            # Show specific libraries for arm64-v8a (most common)
            if [ "$arch_name" = "arm64-v8a" ]; then
                echo "    Key libraries:"
                find "$arch" -name "*.so" | head -5 | while read lib; do
                    echo "      $(basename "$lib")"
                done
            fi
        fi
    done
else
    echo "❌ Native libraries not found at $NATIVE_LIBS"
    exit 1
fi

# Copy essential Java classes if they don't exist
JAVA_SRC="$OPENCV_DIR/sdk/java/src"
if [ -d "$JAVA_SRC" ] && [ ! -f "$PROJECT_ROOT/opencv/src/main/java/org/opencv/core/Mat.java" ]; then
    echo "Copying essential OpenCV Java classes..."
    mkdir -p "$PROJECT_ROOT/opencv/src/main/java"
    cp -r "$JAVA_SRC"/* "$PROJECT_ROOT/opencv/src/main/java/"
    echo "✅ Java classes copied"
fi

# Clean up
cd "$PROJECT_ROOT"
rm -rf "$TEMP_DIR"

echo ""
echo "✅ OpenCV libraries download completed!"
echo ""
echo "Next steps:"
echo "1. Build your project: ./gradlew assembleDebug"
echo "2. Install and test the APK on your device"
echo ""
echo "The runtime error should now be resolved."