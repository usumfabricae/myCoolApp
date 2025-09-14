# Quick OpenCV Libraries Download Script (PowerShell)
# Downloads only the essential native libraries needed to fix the immediate runtime error

param(
    [string]$OpenCVVersion = "4.8.0"
)

Write-Host "=== QUICK OPENCV LIBRARIES DOWNLOAD ===" -ForegroundColor Green

$OpenCVUrl = "https://github.com/opencv/opencv/releases/download/$OpenCVVersion/opencv-$OpenCVVersion-android-sdk.zip"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$TempDir = Join-Path $ProjectRoot "temp-opencv-libs"

Write-Host "Downloading OpenCV libraries to fix runtime error..."
Write-Host "OpenCV version: $OpenCVVersion"

# Create temp directory
New-Item -ItemType Directory -Path $TempDir -Force | Out-Null
Set-Location $TempDir

try {
    # Download OpenCV SDK
    Write-Host "Downloading OpenCV Android SDK..."
    $ZipPath = Join-Path $TempDir "opencv-android-sdk.zip"
    
    if (Get-Command Invoke-WebRequest -ErrorAction SilentlyContinue) {
        Invoke-WebRequest -Uri $OpenCVUrl -OutFile $ZipPath -UseBasicParsing
    } else {
        Write-Error "PowerShell Invoke-WebRequest not available"
        exit 1
    }

    # Extract
    Write-Host "Extracting OpenCV SDK..."
    Expand-Archive -Path $ZipPath -DestinationPath $TempDir -Force

    # Find extracted directory
    $OpenCVDir = Get-ChildItem -Path $TempDir -Directory | Where-Object { $_.Name -like "*OpenCV-android-sdk*" } | Select-Object -First 1
    if (-not $OpenCVDir) {
        Write-Error "Could not find extracted OpenCV directory"
        exit 1
    }

    # Copy native libraries
    Write-Host "Copying native libraries..."
    $NativeLibsPath = Join-Path $OpenCVDir.FullName "sdk\native\libs"
    $TargetLibsPath = Join-Path $ProjectRoot "opencv\src\main\jniLibs"
    
    if (Test-Path $NativeLibsPath) {
        # Ensure target directory exists
        New-Item -ItemType Directory -Path $TargetLibsPath -Force | Out-Null
        
        # Copy all architecture libraries
        Copy-Item -Path "$NativeLibsPath\*" -Destination $TargetLibsPath -Recurse -Force
        
        Write-Host "✅ Native libraries copied successfully" -ForegroundColor Green
        
        # Show what was copied
        Write-Host "Libraries copied:"
        Get-ChildItem -Path $TargetLibsPath -Directory | ForEach-Object {
            $archName = $_.Name
            $libCount = (Get-ChildItem -Path $_.FullName -Filter "*.so" -Recurse).Count
            Write-Host "  $archName`: $libCount libraries"
            
            # Show specific libraries for arm64-v8a (most common)
            if ($archName -eq "arm64-v8a") {
                Write-Host "    Key libraries:"
                Get-ChildItem -Path $_.FullName -Filter "*.so" | Select-Object -First 5 | ForEach-Object {
                    Write-Host "      $($_.Name)"
                }
            }
        }
    } else {
        Write-Error "Native libraries not found at $NativeLibsPath"
        exit 1
    }

    # Copy essential Java classes if they don't exist
    $JavaSrcPath = Join-Path $OpenCVDir.FullName "sdk\java\src"
    $MatJavaPath = Join-Path $ProjectRoot "opencv\src\main\java\org\opencv\core\Mat.java"
    
    if ((Test-Path $JavaSrcPath) -and (-not (Test-Path $MatJavaPath))) {
        Write-Host "Copying essential OpenCV Java classes..."
        $TargetJavaPath = Join-Path $ProjectRoot "opencv\src\main\java"
        New-Item -ItemType Directory -Path $TargetJavaPath -Force | Out-Null
        Copy-Item -Path "$JavaSrcPath\*" -Destination $TargetJavaPath -Recurse -Force
        Write-Host "✅ Java classes copied" -ForegroundColor Green
    }

} finally {
    # Clean up
    Set-Location $ProjectRoot
    if (Test-Path $TempDir) {
        Remove-Item -Path $TempDir -Recurse -Force
    }
}

Write-Host ""
Write-Host "✅ OpenCV libraries download completed!" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:"
Write-Host "1. Build your project: .\gradlew assembleDebug"
Write-Host "2. Install and test the APK on your device"
Write-Host ""
Write-Host "The runtime error should now be resolved."