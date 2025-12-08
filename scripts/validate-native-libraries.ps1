# Native Library Validation Script (PowerShell)
# Validates that all required OpenCV native libraries are present before building
# Requirements: Req-1 (library inclusion), Req-5 (robust acquisition system)

$ErrorActionPreference = "Stop"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Native Library Validation Script" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Configuration
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir

# Define required libraries
$RequiredLibs = @("libc++_shared.so", "libopencv_java4.so")

# Define supported architectures
$Architectures = @("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

# Directories to check
$JniLibsDirs = @(
    "$ProjectRoot\app\src\main\jniLibs",
    "$ProjectRoot\opencv\src\main\jniLibs"
)

# Validation results
$ValidationErrors = @()
$ValidationWarnings = @()
$FoundLibraries = @()

Write-Host "Checking for required native libraries..."
Write-Host ""

# Function to check if a library exists
function Test-Library {
    param(
        [string]$Arch,
        [string]$Lib
    )
    
    $foundCount = 0
    $foundLocations = @()
    
    foreach ($jniLibsDir in $JniLibsDirs) {
        $libPath = Join-Path $jniLibsDir "$Arch\$Lib"
        if (Test-Path $libPath) {
            $fileInfo = Get-Item $libPath
            if ($fileInfo.Length -gt 0) {
                $foundCount++
                $foundLocations += $jniLibsDir
                $size = $fileInfo.Length
                $script:FoundLibraries += "$Arch/$Lib`: Found in $jniLibsDir ($size bytes)"
            }
        }
    }
    
    if ($foundCount -eq 0) {
        $script:ValidationErrors += "Missing library: $Lib for architecture: $Arch"
        return $false
    }
    elseif ($foundCount -gt 1) {
        $script:ValidationWarnings += "Duplicate library: $Lib for architecture: $Arch found in $foundCount locations"
        return $true
    }
    else {
        return $true
    }
}

# Perform validation
Write-Host "Validation Results:"
Write-Host "-------------------"
Write-Host ""

foreach ($arch in $Architectures) {
    Write-Host "${arch}:"
    foreach ($lib in $RequiredLibs) {
        if (Test-Library -Arch $arch -Lib $lib) {
            Write-Host "  " -NoNewline
            Write-Host "✅" -ForegroundColor Green -NoNewline
            Write-Host " $lib`: Found"
        }
        else {
            Write-Host "  " -NoNewline
            Write-Host "❌" -ForegroundColor Red -NoNewline
            Write-Host " $lib`: NOT FOUND"
        }
    }
    Write-Host ""
}

# Print warnings
if ($ValidationWarnings.Count -gt 0) {
    Write-Host "⚠️  Warnings:" -ForegroundColor Yellow
    foreach ($warning in $ValidationWarnings) {
        Write-Host "  - $warning"
    }
    Write-Host ""
    Write-Host "Note: Duplicate libraries will be handled by Gradle packagingOptions.pickFirst"
    Write-Host ""
}

# Print errors and exit if validation failed
if ($ValidationErrors.Count -gt 0) {
    Write-Host "❌ Validation Failed!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Errors:"
    foreach ($error in $ValidationErrors) {
        Write-Host "  - $error"
    }
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Yellow
    Write-Host "SOLUTION: Run the library acquisition script" -ForegroundColor Yellow
    Write-Host "==========================================" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "The required OpenCV native libraries are missing."
    Write-Host "Please run one of the following scripts to download them:"
    Write-Host ""
    Write-Host "  Windows:    .\scripts\download-opencv-libs-only.ps1"
    Write-Host "  Linux/Mac:  ./scripts/download-opencv-libs-only.sh"
    Write-Host "  Or:         ./scripts/setup-opencv.sh"
    Write-Host ""
    Write-Host "These scripts will download and extract the OpenCV Android SDK"
    Write-Host "and place the native libraries in the correct directories."
    Write-Host ""
    Write-Host "After running the script, re-run this validation."
    Write-Host "==========================================" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

Write-Host "✅ All required native libraries are present!" -ForegroundColor Green
Write-Host ""
Write-Host "Library Details:"
foreach ($libInfo in $FoundLibraries) {
    Write-Host "  $libInfo"
}
Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Validation Successful" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

exit 0
