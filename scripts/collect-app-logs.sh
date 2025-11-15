#!/bin/bash
# OpenCV Camera Stream - Application Log Collection Script (Linux/Mac)
# Collects comprehensive application logs for local analysis

set -e

# Default parameters
OUTPUT_DIR="logs"
APP_PACKAGE="com.example.opencvcamerastream"
LOG_DURATION=30
INCLUDE_SYSTEM_LOGS=false
CLEAR_LOGS_FIRST=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --output-dir)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --package)
            APP_PACKAGE="$2"
            shift 2
            ;;
        --duration)
            LOG_DURATION="$2"
            shift 2
            ;;
        --include-system)
            INCLUDE_SYSTEM_LOGS=true
            shift
            ;;
        --clear-first)
            CLEAR_LOGS_FIRST=true
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  --output-dir DIR     Output directory for logs (default: logs)"
            echo "  --package PACKAGE    App package name (default: com.example.opencvcamerastream)"
            echo "  --duration SECONDS   Log collection duration (default: 30)"
            echo "  --include-system     Include full system logs"
            echo "  --clear-first        Clear existing logs before collection"
            echo "  --help               Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Create logs directory
LOGS_PATH="$(pwd)/$OUTPUT_DIR"
mkdir -p "$LOGS_PATH"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
echo "Starting log collection for $APP_PACKAGE at $TIMESTAMP"

# Clear existing logs if requested
if [ "$CLEAR_LOGS_FIRST" = true ]; then
    echo "Clearing existing device logs..."
    adb logcat -c
    sleep 2
fi

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "Error: No device connected. Please connect an Android device."
    exit 1
fi

echo "Device connected successfully"

# Get app process ID and info
echo "Getting application process information..."
adb shell "ps -ef | grep $APP_PACKAGE | grep -v grep" > "$LOGS_PATH/process_info_$TIMESTAMP.txt" || true

# Function to collect logs in background
collect_app_logs() {
    local log_file="$1"
    local duration="$2"
    
    timeout "$duration" adb logcat -s "OpenCVCameraStream" "AndroidRuntime" "System.err" > "$log_file" 2>/dev/null || true
}

# Start application log collection in background
echo "Collecting application logs for $LOG_DURATION seconds..."
APP_LOG_FILE="$LOGS_PATH/app_logs_$TIMESTAMP.txt"
collect_app_logs "$APP_LOG_FILE" "${LOG_DURATION}s" &
LOG_PID=$!

# Collect camera-specific logs
echo "Collecting camera system logs..."
adb logcat -d -s "CameraService" "Camera2" "CameraManager" > "$LOGS_PATH/camera_logs_$TIMESTAMP.txt" 2>/dev/null || true

# Collect OpenCV-specific logs
echo "Collecting OpenCV logs..."
adb logcat -d -s "OpenCV" "cv" "native" > "$LOGS_PATH/opencv_logs_$TIMESTAMP.txt" 2>/dev/null || true

# Collect memory information
echo "Collecting memory information..."
adb shell "dumpsys meminfo $APP_PACKAGE" > "$LOGS_PATH/memory_info_$TIMESTAMP.txt" 2>/dev/null || true

# Collect camera service status
echo "Collecting camera service status..."
adb shell "dumpsys camera" > "$LOGS_PATH/camera_status_$TIMESTAMP.txt" 2>/dev/null || true

# Collect app permissions
echo "Collecting app permissions..."
adb shell "dumpsys package $APP_PACKAGE | grep permission" > "$LOGS_PATH/permissions_$TIMESTAMP.txt" 2>/dev/null || true

# Collect system logs if requested
if [ "$INCLUDE_SYSTEM_LOGS" = true ]; then
    echo "Collecting system logs..."
    adb logcat -d > "$LOGS_PATH/system_logs_$TIMESTAMP.txt" 2>/dev/null || true
fi

# Wait for app log collection to complete
echo "Waiting for log collection to complete..."
wait $LOG_PID

# Collect crash logs
echo "Collecting crash logs..."
adb logcat -d -s "AndroidRuntime" "FATAL" "DEBUG" > "$LOGS_PATH/crash_logs_$TIMESTAMP.txt" 2>/dev/null || true

# Collect device information
echo "Collecting device information..."
DEVICE_INFO_FILE="$LOGS_PATH/device_info_$TIMESTAMP.txt"
cat > "$DEVICE_INFO_FILE" << EOF
Device Information - $TIMESTAMP
================================

Build Information:
Android Version: $(adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
SDK Version: $(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')
Device Model: $(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')
Manufacturer: $(adb shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r')
EOF

# Create summary report
echo "Creating summary report..."
SUMMARY_FILE="$LOGS_PATH/log_collection_summary_$TIMESTAMP.txt"
cat > "$SUMMARY_FILE" << EOF
OpenCV Camera Stream - Log Collection Summary
============================================
Collection Time: $TIMESTAMP
App Package: $APP_PACKAGE
Log Duration: $LOG_DURATION seconds
Output Directory: $LOGS_PATH

Collected Files:
- Application Logs: app_logs_$TIMESTAMP.txt
- Camera Logs: camera_logs_$TIMESTAMP.txt
- OpenCV Logs: opencv_logs_$TIMESTAMP.txt
- Memory Info: memory_info_$TIMESTAMP.txt
- Camera Status: camera_status_$TIMESTAMP.txt
- Permissions: permissions_$TIMESTAMP.txt
- Crash Logs: crash_logs_$TIMESTAMP.txt
- Device Info: device_info_$TIMESTAMP.txt
- Process Info: process_info_$TIMESTAMP.txt
$([ "$INCLUDE_SYSTEM_LOGS" = true ] && echo "- System Logs: system_logs_$TIMESTAMP.txt")

Analysis Tips:
1. Check crash_logs for fatal errors and exceptions
2. Review app_logs for application-specific issues
3. Examine camera_logs for Camera2 API problems
4. Check memory_info for memory leaks or OOM issues
5. Review permissions for Android 10 compliance issues

Common Error Patterns to Look For:
- Camera permission denials
- OpenCV initialization failures
- Memory allocation errors
- Native library loading issues
- Background activity restrictions
EOF

echo
echo "Log collection completed successfully!"
echo "Logs saved to: $LOGS_PATH"
echo "Summary report: $SUMMARY_FILE"

# Display file sizes
echo
echo "Collected log files:"
for file in "$LOGS_PATH"/*"$TIMESTAMP"*; do
    if [ -f "$file" ]; then
        size=$(du -h "$file" | cut -f1)
        basename_file=$(basename "$file")
        echo "  $basename_file - $size"
    fi
done