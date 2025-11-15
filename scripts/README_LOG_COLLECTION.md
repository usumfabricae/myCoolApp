# Application Log Collection Guide

This directory contains scripts to collect and analyze application logs from your OpenCV Camera Stream Android app for local analysis.

## Quick Start

### Windows (PowerShell)
```powershell
# Basic log collection (30 seconds)
.\scripts\collect-app-logs.ps1

# Extended collection with system logs
.\scripts\collect-app-logs.ps1 -LogDurationSeconds 60 -IncludeSystemLogs

# Clear logs first, then collect
.\scripts\collect-app-logs.ps1 -ClearLogsFirst -LogDurationSeconds 45
```

### Linux/Mac (Bash)
```bash
# Make script executable
chmod +x scripts/collect-app-logs.sh

# Basic log collection
./scripts/collect-app-logs.sh

# Extended collection with options
./scripts/collect-app-logs.sh --duration 60 --include-system --clear-first
```

## Log Analysis

After collecting logs, analyze them with the Python script:

```bash
# Analyze logs in default 'logs' directory
python scripts/analyze-logs.py

# Analyze logs in custom directory
python scripts/analyze-logs.py /path/to/logs
```

## Prerequisites

1. **ADB (Android Debug Bridge)** - Must be installed and in PATH
2. **Connected Android Device** - USB debugging enabled
3. **Python 3.6+** - For log analysis (optional)

### Install ADB
- **Windows**: Download Android SDK Platform Tools
- **Mac**: `brew install android-platform-tools`
- **Linux**: `sudo apt install adb` or `sudo yum install android-tools`

## Collected Log Types

The scripts collect the following log categories:

### Application Logs (`app_logs_*.txt`)
- Application-specific log entries
- OpenCV processing logs
- Custom application messages

### Camera Logs (`camera_logs_*.txt`)
- Camera2 API operations
- Camera service interactions
- Camera permission events

### OpenCV Logs (`opencv_logs_*.txt`)
- OpenCV library initialization
- Image processing operations
- Native library loading

### Crash Logs (`crash_logs_*.txt`)
- Fatal exceptions and crashes
- Stack traces
- Runtime errors

### Memory Info (`memory_info_*.txt`)
- Memory usage statistics
- Heap information
- Memory allocation details

### System Information
- Device specifications
- Android version details
- App permissions status
- Camera service status

## Script Parameters

### PowerShell Script Parameters
- `-OutputDir`: Output directory (default: "logs")
- `-AppPackage`: App package name (default: "com.example.opencvcamerastream")
- `-LogDurationSeconds`: Collection duration (default: 30)
- `-IncludeSystemLogs`: Include full system logs
- `-ClearLogsFirst`: Clear existing logs before collection

### Bash Script Parameters
- `--output-dir`: Output directory
- `--package`: App package name
- `--duration`: Collection duration in seconds
- `--include-system`: Include full system logs
- `--clear-first`: Clear existing logs before collection

## Analysis Features

The Python analysis script provides:

- **Error Categorization**: Groups errors by type (crashes, camera, OpenCV, etc.)
- **Severity Assessment**: Ranks issues by severity (critical, high, medium, low)
- **Pattern Recognition**: Identifies common error patterns
- **Performance Analysis**: Detects performance issues and ANRs
- **Memory Analysis**: Identifies memory leaks and OOM conditions
- **Recommendations**: Provides actionable fix suggestions

## Common Use Cases

### Debug App Crashes
```powershell
# Collect logs during crash reproduction
.\scripts\collect-app-logs.ps1 -ClearLogsFirst -LogDurationSeconds 60

# Analyze for crashes
python scripts/analyze-logs.py
```

### Camera Permission Issues
```powershell
# Focus on camera and permission logs
.\scripts\collect-app-logs.ps1 -LogDurationSeconds 30

# Check camera_logs and permissions files
```

### Performance Problems
```powershell
# Extended collection for performance analysis
.\scripts\collect-app-logs.ps1 -LogDurationSeconds 120 -IncludeSystemLogs

# Review memory_info and performance patterns
```

### OpenCV Integration Issues
```powershell
# Collect during OpenCV operations
.\scripts\collect-app-logs.ps1 -LogDurationSeconds 45

# Focus on opencv_logs and native library loading
```

## Troubleshooting

### No Device Found
```bash
# Check device connection
adb devices

# Enable USB debugging on device
# Settings > Developer Options > USB Debugging
```

### Permission Denied
```bash
# Grant ADB permissions on device
# Allow computer access when prompted
```

### Empty Log Files
```bash
# Clear logs and try again
adb logcat -c
# Then run collection script
```

### Script Execution Policy (Windows)
```powershell
# If script execution is blocked
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

## Output Structure

```
logs/
├── app_logs_20241115_143022.txt
├── camera_logs_20241115_143022.txt
├── opencv_logs_20241115_143022.txt
├── crash_logs_20241115_143022.txt
├── memory_info_20241115_143022.txt
├── camera_status_20241115_143022.txt
├── permissions_20241115_143022.txt
├── device_info_20241115_143022.txt
├── process_info_20241115_143022.txt
├── log_collection_summary_20241115_143022.txt
└── analysis_results_20241115_143500.json
```

## Tips for Effective Log Collection

1. **Reproduce Issues**: Run the app and reproduce the problem during log collection
2. **Clear First**: Use `-ClearLogsFirst` to avoid old log entries
3. **Adequate Duration**: Allow enough time to capture the issue (30-60 seconds)
4. **Multiple Runs**: Collect logs multiple times to identify consistent patterns
5. **Document Context**: Note what actions you performed during log collection

## Integration with CI/CD

These scripts can be integrated into your Codemagic CI/CD pipeline for automated log collection during testing phases.