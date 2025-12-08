#!/bin/bash

# Native Library Validation Script
# Validates that all required OpenCV native libraries are present before building
# Requirements: Req-1 (library inclusion), Req-5 (robust acquisition system)

set -e

echo "=========================================="
echo "Native Library Validation Script"
echo "=========================================="
echo ""

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Define required libraries
REQUIRED_LIBS=("libc++_shared.so" "libopencv_java4.so")

# Define supported architectures
ARCHITECTURES=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")

# Directories to check
JNILIBS_DIRS=(
    "$PROJECT_ROOT/app/src/main/jniLibs"
    "$PROJECT_ROOT/opencv/src/main/jniLibs"
)

# Validation results
VALIDATION_ERRORS=()
VALIDATION_WARNINGS=()
FOUND_LIBRARIES=()

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "Checking for required native libraries..."
echo ""

# Function to check if a library exists
check_library() {
    local arch=$1
    local lib=$2
    local found_count=0
    local found_locations=()
    
    for jniLibsDir in "${JNILIBS_DIRS[@]}"; do
        local lib_path="$jniLibsDir/$arch/$lib"
        if [ -f "$lib_path" ] && [ -s "$lib_path" ]; then
            found_count=$((found_count + 1))
            found_locations+=("$jniLibsDir")
            local size=$(stat -f%z "$lib_path" 2>/dev/null || stat -c%s "$lib_path" 2>/dev/null || echo "unknown")
            FOUND_LIBRARIES+=("$arch/$lib: Found in $jniLibsDir ($size bytes)")
        fi
    done
    
    if [ $found_count -eq 0 ]; then
        VALIDATION_ERRORS+=("Missing library: $lib for architecture: $arch")
        return 1
    elif [ $found_count -gt 1 ]; then
        VALIDATION_WARNINGS+=("Duplicate library: $lib for architecture: $arch found in ${found_count} locations")
        return 0
    else
        return 0
    fi
}

# Perform validation
echo "Validation Results:"
echo "-------------------"
echo ""

for arch in "${ARCHITECTURES[@]}"; do
    echo "${arch}:"
    for lib in "${REQUIRED_LIBS[@]}"; do
        if check_library "$arch" "$lib"; then
            echo -e "  ${GREEN}✅${NC} $lib: Found"
        else
            echo -e "  ${RED}❌${NC} $lib: NOT FOUND"
        fi
    done
    echo ""
done

# Print warnings
if [ ${#VALIDATION_WARNINGS[@]} -gt 0 ]; then
    echo -e "${YELLOW}⚠️  Warnings:${NC}"
    for warning in "${VALIDATION_WARNINGS[@]}"; do
        echo "  - $warning"
    done
    echo ""
    echo "Note: Duplicate libraries will be handled by Gradle packagingOptions.pickFirst"
    echo ""
fi

# Print errors and exit if validation failed
if [ ${#VALIDATION_ERRORS[@]} -gt 0 ]; then
    echo -e "${RED}❌ Validation Failed!${NC}"
    echo ""
    echo "Errors:"
    for error in "${VALIDATION_ERRORS[@]}"; do
        echo "  - $error"
    done
    echo ""
    echo "=========================================="
    echo "SOLUTION: Run the library acquisition script"
    echo "=========================================="
    echo ""
    echo "The required OpenCV native libraries are missing."
    echo "Please run one of the following scripts to download them:"
    echo ""
    echo "  Linux/Mac:  ./scripts/download-opencv-libs-only.sh"
    echo "  Windows:    .\\scripts\\download-opencv-libs-only.ps1"
    echo "  Or:         ./scripts/setup-opencv.sh"
    echo ""
    echo "These scripts will download and extract the OpenCV Android SDK"
    echo "and place the native libraries in the correct directories."
    echo ""
    echo "After running the script, re-run this validation."
    echo "=========================================="
    echo ""
    exit 1
fi

echo -e "${GREEN}✅ All required native libraries are present!${NC}"
echo ""
echo "Library Details:"
for lib_info in "${FOUND_LIBRARIES[@]}"; do
    echo "  $lib_info"
done
echo ""
echo "=========================================="
echo "Validation Successful"
echo "=========================================="
echo ""

exit 0
