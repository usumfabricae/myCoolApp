# OpenCV Camera Stream - Application Log Collection Script (Windows)
# Collects comprehensive application logs for local analysis

param(
    [string]$OutputDir = "logs",
    [string]$AppPackage = "com.example.opencvcamerastream",
    [int]$LogDurationSeconds = 30,
    [switch]$IncludeSystemLogs,
    [switch]$ClearLogsFirst
)

# Create logs directory
$LogsPath = Join-Path $PWD $OutputDir
if (!(Test-Path $LogsPath)) {
    New-Item -ItemType Directory -Path $LogsPath -Force | Out-Null
}

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
Write-Host "Starting log collection for $AppPackage at $Timestamp" -ForegroundColor Green

# Clear existing logs if requested
if ($ClearLogsFirst) {
    Write-Host "Clearing existing device logs..." -ForegroundColor Yellow
    adb logcat -c
    Start-Sleep 2
}

# Check if device is connected
$DeviceCheck = adb devices
if ($DeviceCheck -match "device$") {
    Write-Host "Device connected successfully" -ForegroundColor Green
} else {
    Write-Host "No device connected. Please connect an Android device." -ForegroundColor Red
    exit 1
}

# Get app process ID
Write-Host "Getting application process information..." -ForegroundColor Cyan
$ProcessInfo = adb shell "ps -ef | grep $AppPackage | grep -v grep"
$ProcessInfo | Out-File -FilePath "$LogsPath/process_info_$Timestamp.txt" -Encoding UTF8

# Collect application-specific logs
Write-Host "Collecting application logs for $LogDurationSeconds seconds..." -ForegroundColor Cyan
$AppLogFile = "$LogsPath/app_logs_$Timestamp.txt"

# Start background log collection
$LogJob = Start-Job -ScriptBlock {
    param($AppPackage, $LogFile, $Duration)
    $EndTime = (Get-Date).AddSeconds($Duration)
    
    # Collect filtered app logs
    & adb logcat -s "OpenCVCameraStream" "AndroidRuntime" "System.err" | 
        ForEach-Object {
            if ((Get-Date) -lt $EndTime) {
                $_ | Out-File -FilePath $LogFile -Append -Encoding UTF8
            }
        }
} -ArgumentList $AppPackage, $AppLogFile, $LogDurationSeconds

# Collect camera-specific logs
Write-Host "Collecting camera system logs..." -ForegroundColor Cyan
$CameraLogFile = "$LogsPath/camera_logs_$Timestamp.txt"
adb logcat -d -s "CameraService" "Camera2" "CameraManager" | Out-File -FilePath $CameraLogFile -Encoding UTF8

# Collect OpenCV-specific logs
Write-Host "Collecting OpenCV logs..." -ForegroundColor Cyan
$OpenCVLogFile = "$LogsPath/opencv_logs_$Timestamp.txt"
adb logcat -d -s "OpenCV" "cv" "native" | Out-File -FilePath $OpenCVLogFile -Encoding UTF8

# Collect memory information
Write-Host "Collecting memory information..." -ForegroundColor Cyan
$MemoryFile = "$LogsPath/memory_info_$Timestamp.txt"
adb shell "dumpsys meminfo $AppPackage" | Out-File -FilePath $MemoryFile -Encoding UTF8

# Collect camera service status
Write-Host "Collecting camera service status..." -ForegroundColor Cyan
$CameraStatusFile = "$LogsPath/camera_status_$Timestamp.txt"
adb shell "dumpsys camera" | Out-File -FilePath $CameraStatusFile -Encoding UTF8

# Collect app permissions
Write-Host "Collecting app permissions..." -ForegroundColor Cyan
$PermissionsFile = "$LogsPath/permissions_$Timestamp.txt"
adb shell "dumpsys package $AppPackage | grep permission" | Out-File -FilePath $PermissionsFile -Encoding UTF8

# Collect system logs if requested
if ($IncludeSystemLogs) {
    Write-Host "Collecting system logs..." -ForegroundColor Cyan
    $SystemLogFile = "$LogsPath/system_logs_$Timestamp.txt"
    adb logcat -d | Out-File -FilePath $SystemLogFile -Encoding UTF8
}

# Wait for app log collection to complete
Write-Host "Waiting for log collection to complete..." -ForegroundColor Yellow
Wait-Job $LogJob | Out-Null
Receive-Job $LogJob | Out-Null
Remove-Job $LogJob

# Collect crash logs
Write-Host "Collecting crash logs..." -ForegroundColor Cyan
$CrashLogFile = "$LogsPath/crash_logs_$Timestamp.txt"
adb logcat -d -s "AndroidRuntime" "FATAL" "DEBUG" | Out-File -FilePath $CrashLogFile -Encoding UTF8

# Collect device information
Write-Host "Collecting device information..." -ForegroundColor Cyan
$DeviceInfoFile = "$LogsPath/device_info_$Timestamp.txt"
@"
Device Information - $Timestamp
================================

Build Information:
"@ | Out-File -FilePath $DeviceInfoFile -Encoding UTF8

adb shell "getprop ro.build.version.release" | ForEach-Object { "Android Version: $_" } | Out-File -FilePath $DeviceInfoFile -Append -Encoding UTF8
adb shell "getprop ro.build.version.sdk" | ForEach-Object { "SDK Version: $_" } | Out-File -FilePath $DeviceInfoFile -Append -Encoding UTF8
adb shell "getprop ro.product.model" | ForEach-Object { "Device Model: $_" } | Out-File -FilePath $DeviceInfoFile -Append -Encoding UTF8
adb shell "getprop ro.product.manufacturer" | ForEach-Object { "Manufacturer: $_" } | Out-File -FilePath $DeviceInfoFile -Append -Encoding UTF8

# Create summary report
Write-Host "Creating summary report..." -ForegroundColor Cyan
$SummaryFile = "$LogsPath/log_collection_summary_$Timestamp.txt"
@"
OpenCV Camera Stream - Log Collection Summary
============================================
Collection Time: $Timestamp
App Package: $AppPackage
Log Duration: $LogDurationSeconds seconds
Output Directory: $LogsPath

Collected Files:
- Application Logs: app_logs_$Timestamp.txt
- Camera Logs: camera_logs_$Timestamp.txt
- OpenCV Logs: opencv_logs_$Timestamp.txt
- Memory Info: memory_info_$Timestamp.txt
- Camera Status: camera_status_$Timestamp.txt
- Permissions: permissions_$Timestamp.txt
- Crash Logs: crash_logs_$Timestamp.txt
- Device Info: device_info_$Timestamp.txt
- Process Info: process_info_$Timestamp.txt
$(if ($IncludeSystemLogs) { "- System Logs: system_logs_$Timestamp.txt" })

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
"@ | Out-File -FilePath $SummaryFile -Encoding UTF8

Write-Host "`nLog collection completed successfully!" -ForegroundColor Green
Write-Host "Logs saved to: $LogsPath" -ForegroundColor Green
Write-Host "Summary report: $SummaryFile" -ForegroundColor Green

# Display file sizes
Write-Host "`nCollected log files:" -ForegroundColor Cyan
Get-ChildItem $LogsPath -Filter "*$Timestamp*" | ForEach-Object {
    $SizeKB = [math]::Round($_.Length / 1KB, 2)
    Write-Host "  $($_.Name) - $SizeKB KB" -ForegroundColor White
}