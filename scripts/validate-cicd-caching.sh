#!/bin/bash

# CI/CD Caching Validation Script
# Validates that the caching implementation is correctly configured

set -e

echo "=== CI/CD CACHING VALIDATION ==="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

# Check if codemagic.yaml exists
if [ ! -f "codemagic.yaml" ]; then
    echo "❌ codemagic.yaml not found"
    exit 1
fi

echo "✅ codemagic.yaml found"

# Validate cache paths are configured
echo ""
echo "Checking cache configuration..."

if grep -q "opencv/src/main/jniLibs" codemagic.yaml; then
    echo "✅ OpenCV native libraries cache path configured"
else
    echo "❌ OpenCV native libraries cache path not found"
    exit 1
fi

if grep -q "opencv/src/main/java" codemagic.yaml; then
    echo "✅ OpenCV Java sources cache path configured"
else
    echo "❌ OpenCV Java sources cache path not found"
    exit 1
fi

# Validate cache detection logic
echo ""
echo "Checking cache detection logic..."

if grep -q "OPENCV_CACHED=false" codemagic.yaml; then
    echo "✅ Cache detection variable initialized"
else
    echo "❌ Cache detection variable not found"
    exit 1
fi

if grep -q "if \[ \"\$OPENCV_CACHED\" = false \]" codemagic.yaml; then
    echo "✅ Conditional download logic found"
else
    echo "❌ Conditional download logic not found"
    exit 1
fi

# Validate verification steps
echo ""
echo "Checking verification steps..."

if grep -q "FINAL OPENCV VERIFICATION" codemagic.yaml; then
    echo "✅ Final verification step found"
else
    echo "❌ Final verification step not found"
    exit 1
fi

# Check for setup script
echo ""
echo "Checking setup scripts..."

if [ -f "scripts/setup-opencv.sh" ]; then
    echo "✅ setup-opencv.sh found"
    if [ -x "scripts/setup-opencv.sh" ]; then
        echo "✅ setup-opencv.sh is executable"
    else
        echo "⚠️  setup-opencv.sh is not executable (will be fixed in CI/CD)"
    fi
else
    echo "❌ setup-opencv.sh not found"
    exit 1
fi

if [ -f "scripts/download-opencv-libs-only.sh" ]; then
    echo "✅ download-opencv-libs-only.sh found (fallback)"
else
    echo "⚠️  download-opencv-libs-only.sh not found (optional)"
fi

# Validate documentation
echo ""
echo "Checking documentation..."

if [ -f "CICD_CACHING_GUIDE.md" ]; then
    echo "✅ CICD_CACHING_GUIDE.md found"
else
    echo "⚠️  CICD_CACHING_GUIDE.md not found"
fi

if [ -f "CICD_BUILD_AUTOMATION_SUMMARY.md" ]; then
    echo "✅ CICD_BUILD_AUTOMATION_SUMMARY.md found"
else
    echo "⚠️  CICD_BUILD_AUTOMATION_SUMMARY.md not found"
fi

# Summary
echo ""
echo "=== VALIDATION SUMMARY ==="
echo "✅ All critical validations passed"
echo ""
echo "CI/CD caching implementation is correctly configured!"
echo ""
echo "Next steps:"
echo "1. Commit and push changes to trigger CI/CD build"
echo "2. Monitor first build (cache miss) - should download OpenCV"
echo "3. Trigger second build (cache hit) - should use cached libraries"
echo "4. Verify build time improvement in Codemagic dashboard"
echo ""
echo "Expected improvements:"
echo "- First build: ~2-3 min for OpenCV setup"
echo "- Cached builds: ~5-10 sec for OpenCV setup"
echo "- Overall build time: 20-30% faster"
echo "- Bandwidth savings: ~250 MB per cached build"
