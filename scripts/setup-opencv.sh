#!/bin/bash

# OpenCV Android SDK Setup Script
# This script downloads and integrates the OpenCV Android SDK into the project

set -e  # Exit on any error

echo "=== OPENCV ANDROID SDK SETUP ==="

# Configuration
OPENCV_VERSION="4.8.0"
OPENCV_ANDROID_SDK_URL="https://github.com/opencv/opencv/releases/download/${OPENCV_VERSION}/opencv-${OPENCV_VERSION}-android-sdk.zip"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
OPENCV_DOWNLOAD_DIR="$PROJECT_ROOT/temp-opencv-download"

echo "Project root: $PROJECT_ROOT"
echo "OpenCV version: $OPENCV_VERSION"
echo "Download URL: $OPENCV_ANDROID_SDK_URL"

# Check if we're in the right directory
if [ ! -f "$PROJECT_ROOT/build.gradle" ] || [ ! -f "$PROJECT_ROOT/settings.gradle" ]; then
    echo "❌ Error: This script must be run from an Android project root or its scripts directory"
    echo "Current directory: $(pwd)"
    echo "Project root: $PROJECT_ROOT"
    exit 1
fi

# Check if opencv module exists
if [ ! -d "$PROJECT_ROOT/opencv" ]; then
    echo "❌ Error: opencv module directory not found at $PROJECT_ROOT/opencv"
    echo "Please ensure the opencv module is included in your project"
    exit 1
fi

# Create temporary download directory
echo "Creating download directory..."
mkdir -p "$OPENCV_DOWNLOAD_DIR"
cd "$OPENCV_DOWNLOAD_DIR"

# Download OpenCV Android SDK
echo "Downloading OpenCV Android SDK..."
if command -v curl >/dev/null 2>&1; then
    if ! curl -L -o "opencv-android-sdk.zip" "$OPENCV_ANDROID_SDK_URL"; then
        echo "❌ Failed to download OpenCV Android SDK with curl"
        exit 1
    fi
elif command -v wget >/dev/null 2>&1; then
    if ! wget -O "opencv-android-sdk.zip" "$OPENCV_ANDROID_SDK_URL"; then
        echo "❌ Failed to download OpenCV Android SDK with wget"
        exit 1
    fi
else
    echo "❌ Error: Neither curl nor wget is available for downloading"
    exit 1
fi

echo "✅ OpenCV Android SDK downloaded successfully"

# Extract the SDK
echo "Extracting OpenCV Android SDK..."
if ! unzip -q "opencv-android-sdk.zip"; then
    echo "❌ Failed to extract OpenCV Android SDK"
    exit 1
fi

echo "✅ OpenCV Android SDK extracted successfully"

# Find the extracted OpenCV directory
OPENCV_EXTRACTED_DIR=$(find . -name "OpenCV-android-sdk" -type d | head -1)
if [ -z "$OPENCV_EXTRACTED_DIR" ]; then
    echo "❌ Could not find extracted OpenCV directory"
    echo "Available directories:"
    ls -la
    exit 1
fi

echo "Found OpenCV directory: $OPENCV_EXTRACTED_DIR"

# Navigate back to project directory
cd "$PROJECT_ROOT"

# Backup existing opencv module content
if [ -d "opencv/src/main/java" ] && [ "$(find opencv/src/main/java -name '*.java' | wc -l)" -gt 0 ]; then
    echo "Backing up existing OpenCV module content..."
    mkdir -p "opencv-backup-$(date +%Y%m%d-%H%M%S)"
    cp -r opencv/src opencv-backup-$(date +%Y%m%d-%H%M%S)/
fi

# Prepare opencv module directory
echo "Preparing opencv module directory..."
rm -rf opencv/src/main/java/*
rm -rf opencv/src/main/jniLibs/*
rm -rf opencv/src/main/res/*

# Copy OpenCV Java sources to opencv module
echo "Copying OpenCV Java sources..."
OPENCV_JAVA_SRC="$OPENCV_DOWNLOAD_DIR/$OPENCV_EXTRACTED_DIR/sdk/java/src"
if [ -d "$OPENCV_JAVA_SRC" ]; then
    mkdir -p opencv/src/main/java
    cp -r "$OPENCV_JAVA_SRC"/* opencv/src/main/java/
    echo "✅ OpenCV Java sources copied successfully"
else
    echo "❌ OpenCV Java sources not found at $OPENCV_JAVA_SRC"
    echo "Available directories:"
    find "$OPENCV_DOWNLOAD_DIR/$OPENCV_EXTRACTED_DIR" -type d | head -10
    exit 1
fi

# Copy OpenCV native libraries
echo "Copying OpenCV native libraries..."
OPENCV_NATIVE_LIBS="$OPENCV_DOWNLOAD_DIR/$OPENCV_EXTRACTED_DIR/sdk/native/libs"
if [ -d "$OPENCV_NATIVE_LIBS" ]; then
    mkdir -p opencv/src/main/jniLibs
    cp -r "$OPENCV_NATIVE_LIBS"/* opencv/src/main/jniLibs/
    echo "✅ OpenCV native libraries copied successfully"
    echo "Available architectures:"
    ls -la opencv/src/main/jniLibs/
else
    echo "❌ OpenCV native libraries not found at $OPENCV_NATIVE_LIBS"
    exit 1
fi

# Copy OpenCV resources if they exist
OPENCV_RESOURCES="$OPENCV_DOWNLOAD_DIR/$OPENCV_EXTRACTED_DIR/sdk/java/res"
if [ -d "$OPENCV_RESOURCES" ] && [ "$(find "$OPENCV_RESOURCES" -type f | wc -l)" -gt 0 ]; then
    echo "Copying OpenCV resources..."
    mkdir -p opencv/src/main/res
    cp -r "$OPENCV_RESOURCES"/* opencv/src/main/res/
    echo "✅ OpenCV resources copied successfully"
else
    echo "ℹ️  No OpenCV resources found (this is normal)"
fi

# Update opencv module build.gradle to include proper configurations
echo "Updating OpenCV module build.gradle..."
cat > opencv/build.gradle << 'EOF'
plugins {
    id 'com.android.library'
}

android {
    namespace 'org.opencv'
    compileSdk 34

    defaultConfig {
        minSdk 21
        targetSdk 34

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles "consumer-rules.pro"
        
        ndk {
            abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'
        }
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    
    sourceSets {
        main {
            jniLibs.srcDirs = ['src/main/jniLibs']
        }
    }
}

dependencies {
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
EOF

echo "✅ OpenCV module build.gradle updated"

# Verify the setup
echo "=== OPENCV SETUP VERIFICATION ==="
JAVA_FILES_COUNT=$(find opencv/src/main/java -name "*.java" | wc -l)
NATIVE_LIBS_COUNT=$(find opencv/src/main/jniLibs -name "*.so" | wc -l)

echo "OpenCV Java files: $JAVA_FILES_COUNT"
echo "OpenCV native libraries: $NATIVE_LIBS_COUNT"

if [ "$JAVA_FILES_COUNT" -gt 0 ] && [ "$NATIVE_LIBS_COUNT" -gt 0 ]; then
    echo "✅ OpenCV setup verification passed"
else
    echo "❌ OpenCV setup verification failed"
    exit 1
fi

# Show some key files
echo ""
echo "Key OpenCV classes found:"
find opencv/src/main/java -name "Mat.java" -o -name "Utils.java" -o -name "OpenCVLoader.java" | head -5

echo ""
echo "Native libraries by architecture:"
for arch in opencv/src/main/jniLibs/*/; do
    if [ -d "$arch" ]; then
        arch_name=$(basename "$arch")
        lib_count=$(find "$arch" -name "*.so" | wc -l)
        echo "  $arch_name: $lib_count libraries"
    fi
done

# Clean up download directory
echo ""
echo "Cleaning up download directory..."
rm -rf "$OPENCV_DOWNLOAD_DIR"

echo ""
echo "✅ OpenCV Android SDK setup completed successfully!"
echo ""
echo "Next steps:"
echo "1. Sync your project in Android Studio"
echo "2. Build your project to verify OpenCV integration"
echo "3. Initialize OpenCV in your application code"