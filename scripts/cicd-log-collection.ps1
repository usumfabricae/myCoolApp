# OpenCV Camera Stream - CI/CD Log Collection Integration (Windows)
# Collects logs during CI/CD builds for automated analysis

param(
    [string]$OutputDir = "logs/cicd",
    [string]$AppPackage = "com.example.opencvcamerastream",
    [string]$BuildId = $env:CM_BUILD_ID,
    [string]$Timestamp = (Get-Date -Format "yyyyMMdd_HHmmss")
)

Write-Host "=== CI/CD LOG COLLECTION ===" -ForegroundColor Green
Write-Host "Build ID: $BuildId"
Write-Host "Timestamp: $Timestamp"
Write-Host "Output directory: $OutputDir"

# Create output directory
if (!(Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

# Check if running in CI/CD environment
$IsCICD = $false
if ($env:CM_BUILD_ID) {
    Write-Host "Running in Codemagic CI/CD environment" -ForegroundColor Cyan
    $IsCICD = $true
} else {
    Write-Host "Running in local environment" -ForegroundColor Cyan
    $BuildId = "local"
}

# Collect build logs
Write-Host "Collecting build logs..." -ForegroundColor Cyan
$BuildLogFile = "$OutputDir/build_${BuildId}_${Timestamp}.log"

if ($IsCICD) {
    # In CI/CD, capture gradle build output
    @"
Build ID: $BuildId
Timestamp: $Timestamp
Branch: $($env:CM_BRANCH)
Commit: $($env:CM_COMMIT)
================================
"@ | Out-File -FilePath $BuildLogFile -Encoding UTF8
    
    # Capture last 1000 lines of build output if available
    if (Test-Path "$env:CM_BUILD_DIR/build.log") {
        Get-Content "$env:CM_BUILD_DIR/build.log" -Tail 1000 | Out-File -FilePath $BuildLogFile -Append -Encoding UTF8
    }
}

# Check if device is connected (for instrumentation test logs)
$DeviceCheck = adb devices 2>$null
if ($DeviceCheck -match "device$") {
    Write-Host "Device connected - collecting device logs" -ForegroundColor Cyan
    
    # Collect application logs
    Write-Host "Collecting application logs..." -ForegroundColor Cyan
    adb logcat -d -s "OpenCVCameraStream" "AndroidRuntime" "System.err" | Out-File -FilePath "$OutputDir/app_logs_${BuildId}_${Timestamp}.txt" -Encoding UTF8
    
    # Collect crash logs
    Write-Host "Collecting crash logs..." -ForegroundColor Cyan
    adb logcat -d -s "AndroidRuntime" "FATAL" "DEBUG" | Out-File -FilePath "$OutputDir/crash_logs_${BuildId}_${Timestamp}.txt" -Encoding UTF8
    
    # Collect camera logs
    Write-Host "Collecting camera logs..." -ForegroundColor Cyan
    adb logcat -d -s "CameraService" "Camera2" "CameraManager" | Out-File -FilePath "$OutputDir/camera_logs_${BuildId}_${Timestamp}.txt" -Encoding UTF8
    
    # Collect OpenCV logs
    Write-Host "Collecting OpenCV logs..." -ForegroundColor Cyan
    adb logcat -d -s "OpenCV" "cv" "native" | Out-File -FilePath "$OutputDir/opencv_logs_${BuildId}_${Timestamp}.txt" -Encoding UTF8
    
    # Collect memory info
    Write-Host "Collecting memory information..." -ForegroundColor Cyan
    adb shell "dumpsys meminfo $AppPackage" | Out-File -FilePath "$OutputDir/memory_info_${BuildId}_${Timestamp}.txt" -Encoding UTF8
} else {
    Write-Host "No device connected - skipping device log collection" -ForegroundColor Yellow
}

# Collect test results if available
if (Test-Path "app/build/outputs/androidTest-results") {
    Write-Host "Collecting test results..." -ForegroundColor Cyan
    Copy-Item -Path "app/build/outputs/androidTest-results" -Destination "$OutputDir/test_results_${BuildId}_${Timestamp}/" -Recurse -ErrorAction SilentlyContinue
}

# Collect test reports if available
if (Test-Path "app/build/reports/androidTests") {
    Write-Host "Collecting test reports..." -ForegroundColor Cyan
    Copy-Item -Path "app/build/reports/androidTests" -Destination "$OutputDir/test_reports_${BuildId}_${Timestamp}/" -Recurse -ErrorAction SilentlyContinue
}

# Create summary
$SummaryFile = "$OutputDir/collection_summary_${BuildId}_${Timestamp}.txt"
@"
CI/CD Log Collection Summary
============================
Build ID: $BuildId
Timestamp: $Timestamp
Environment: $(if ($IsCICD) { "CI/CD (Codemagic)" } else { "Local" })
Branch: $($env:CM_BRANCH)
Commit: $($env:CM_COMMIT)

Collected Files:
"@ | Out-File -FilePath $SummaryFile -Encoding UTF8

# List collected files
Get-ChildItem $OutputDir -Filter "*${BuildId}*${Timestamp}*" | ForEach-Object {
    $SizeKB = [math]::Round($_.Length / 1KB, 2)
    "  - $($_.Name) ($SizeKB KB)" | Out-File -FilePath $SummaryFile -Append -Encoding UTF8
}

Write-Host "`nLog collection completed successfully!" -ForegroundColor Green
Write-Host "Summary: $SummaryFile" -ForegroundColor Green
$FileCount = (Get-ChildItem $OutputDir -Filter "*${BuildId}*${Timestamp}*").Count
Write-Host "Total files collected: $FileCount" -ForegroundColor Green
