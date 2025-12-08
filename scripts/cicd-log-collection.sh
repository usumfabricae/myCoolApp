#!/bin/bash
# OpenCV Camera Stream - CI/CD Log Collection Integration
# Collects logs during CI/CD builds for automated analysis

set -e

# Configuration
OUTPUT_DIR="${CI_LOG_DIR:-logs/cicd}"
APP_PACKAGE="${APP_PACKAGE:-com.example.opencvcamerastream}"
BUILD_ID="${CM_BUILD_ID:-local}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

echo "=== CI/CD LOG COLLECTION ==="
echo "Build ID: $BUILD_ID"
echo "Timestamp: $TIMESTAMP"
echo "Output directory: $OUTPUT_DIR"

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Check if running in CI/CD environment
if [ -n "$CM_BUILD_ID" ]; then
    echo "Running in Codemagic CI/CD environment"
    IS_CICD=true
else
    echo "Running in local environment"
    IS_CICD=false
fi

# Collect build logs
echo "Collecting build logs..."
BUILD_LOG_FILE="$OUTPUT_DIR/build_${BUILD_ID}_${TIMESTAMP}.log"

if [ "$IS_CICD" = true ]; then
    # In CI/CD, capture gradle build output
    echo "Build ID: $BUILD_ID" > "$BUILD_LOG_FILE"
    echo "Timestamp: $TIMESTAMP" >> "$BUILD_LOG_FILE"
    echo "Branch: ${CM_BRANCH:-unknown}" >> "$BUILD_LOG_FILE"
    echo "Commit: ${CM_COMMIT:-unknown}" >> "$BUILD_LOG_FILE"
    echo "================================" >> "$BUILD_LOG_FILE"
    
    # Capture last 1000 lines of build output if available
    if [ -f "$CM_BUILD_DIR/build.log" ]; then
        tail -1000 "$CM_BUILD_DIR/build.log" >> "$BUILD_LOG_FILE"
    fi
fi

# Check if device is connected (for instrumentation test logs)
if adb devices 2>/dev/null | grep -q "device$"; then
    echo "Device connected - collecting device logs"
    
    # Collect application logs
    echo "Collecting application logs..."
    adb logcat -d -s "OpenCVCameraStream" "AndroidRuntime" "System.err" > "$OUTPUT_DIR/app_logs_${BUILD_ID}_${TIMESTAMP}.txt" 2>/dev/null || true
    
    # Collect crash logs
    echo "Collecting crash logs..."
    adb logcat -d -s "AndroidRuntime" "FATAL" "DEBUG" > "$OUTPUT_DIR/crash_logs_${BUILD_ID}_${TIMESTAMP}.txt" 2>/dev/null || true
    
    # Collect camera logs
    echo "Collecting camera logs..."
    adb logcat -d -s "CameraService" "Camera2" "CameraManager" > "$OUTPUT_DIR/camera_logs_${BUILD_ID}_${TIMESTAMP}.txt" 2>/dev/null || true
    
    # Collect OpenCV logs
    echo "Collecting OpenCV logs..."
    adb logcat -d -s "OpenCV" "cv" "native" > "$OUTPUT_DIR/opencv_logs_${BUILD_ID}_${TIMESTAMP}.txt" 2>/dev/null || true
    
    # Collect memory info
    echo "Collecting memory information..."
    adb shell "dumpsys meminfo $APP_PACKAGE" > "$OUTPUT_DIR/memory_info_${BUILD_ID}_${TIMESTAMP}.txt" 2>/dev/null || true
else
    echo "No device connected - skipping device log collection"
fi

# Collect test results if available
if [ -d "app/build/outputs/androidTest-results" ]; then
    echo "Collecting test results..."
    cp -r app/build/outputs/androidTest-results "$OUTPUT_DIR/test_results_${BUILD_ID}_${TIMESTAMP}/" 2>/dev/null || true
fi

# Collect test reports if available
if [ -d "app/build/reports/androidTests" ]; then
    echo "Collecting test reports..."
    cp -r app/build/reports/androidTests "$OUTPUT_DIR/test_reports_${BUILD_ID}_${TIMESTAMP}/" 2>/dev/null || true
fi

# Create summary
SUMMARY_FILE="$OUTPUT_DIR/collection_summary_${BUILD_ID}_${TIMESTAMP}.txt"
cat > "$SUMMARY_FILE" << EOF
CI/CD Log Collection Summary
============================
Build ID: $BUILD_ID
Timestamp: $TIMESTAMP
Environment: $([ "$IS_CICD" = true ] && echo "CI/CD (Codemagic)" || echo "Local")
Branch: ${CM_BRANCH:-unknown}
Commit: ${CM_COMMIT:-unknown}

Collected Files:
EOF

# List collected files
for file in "$OUTPUT_DIR"/*"${BUILD_ID}"*"${TIMESTAMP}"*; do
    if [ -f "$file" ]; then
        size=$(du -h "$file" | cut -f1)
        basename_file=$(basename "$file")
        echo "  - $basename_file ($size)" >> "$SUMMARY_FILE"
    fi
done

echo ""
echo "Log collection completed successfully!"
echo "Summary: $SUMMARY_FILE"
echo "Total files collected: $(ls -1 "$OUTPUT_DIR"/*"${BUILD_ID}"*"${TIMESTAMP}"* 2>/dev/null | wc -l)"
