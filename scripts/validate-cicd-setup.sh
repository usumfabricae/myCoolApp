#!/bin/bash

# CI/CD Setup Validation Script
# This script validates that the project is properly configured for Codemagic CI/CD

echo "🔍 Validating CI/CD Setup for Android Camera OpenCV Stream"
echo "============================================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Validation results
VALIDATION_PASSED=true

# Function to print validation result
validate_check() {
    local check_name="$1"
    local condition="$2"
    local error_message="$3"
    
    if [ "$condition" = "true" ]; then
        echo -e "✅ ${GREEN}$check_name${NC}"
    else
        echo -e "❌ ${RED}$check_name${NC}"
        echo -e "   ${YELLOW}$error_message${NC}"
        VALIDATION_PASSED=false
    fi
}

echo ""
echo "📋 Project Structure Validation"
echo "--------------------------------"

# Check if we're in the myCoolApp directory
if [ -f "build.gradle" ] && [ -d "app" ]; then
    validate_check "Project structure (myCoolApp)" "true" ""
else
    validate_check "Project structure (myCoolApp)" "false" "Run this script from the myCoolApp directory"
fi

# Check for Codemagic configuration
if [ -f "codemagic.yaml" ]; then
    validate_check "Codemagic configuration file" "true" ""
else
    validate_check "Codemagic configuration file" "false" "codemagic.yaml not found"
fi

# Check for Git workflow documentation
if [ -f "GIT_WORKFLOW.md" ]; then
    validate_check "Git workflow documentation" "true" ""
else
    validate_check "Git workflow documentation" "false" "GIT_WORKFLOW.md not found"
fi

echo ""
echo "🏗️ Build Configuration Validation"
echo "----------------------------------"

# Check Android target SDK
if grep -q "targetSdk 29" app/build.gradle; then
    validate_check "Target SDK 29 (Android 10)" "true" ""
else
    validate_check "Target SDK 29 (Android 10)" "false" "Target SDK should be 29 for Android 10 compliance"
fi

# Check for Jacoco plugin
if grep -q "jacoco" app/build.gradle; then
    validate_check "Jacoco test coverage plugin" "true" ""
else
    validate_check "Jacoco test coverage plugin" "false" "Jacoco plugin not configured for test coverage"
fi

# Check for test dependencies
if grep -q "testImplementation.*mockito" app/build.gradle && grep -q "testImplementation.*robolectric" app/build.gradle; then
    validate_check "Test dependencies (Mockito, Robolectric)" "true" ""
else
    validate_check "Test dependencies (Mockito, Robolectric)" "false" "Missing required test dependencies"
fi

echo ""
echo "📱 Android Configuration Validation"
echo "------------------------------------"

# Check camera permission in manifest
if grep -q "android.permission.CAMERA" app/src/main/AndroidManifest.xml; then
    validate_check "Camera permission in manifest" "true" ""
else
    validate_check "Camera permission in manifest" "false" "Camera permission not declared in AndroidManifest.xml"
fi

# Check for Android 10 privacy compliance
if grep -q "requestLegacyExternalStorage.*false" app/src/main/AndroidManifest.xml; then
    validate_check "Android 10 scoped storage compliance" "true" ""
else
    validate_check "Android 10 scoped storage compliance" "false" "Scoped storage not properly configured"
fi

echo ""
echo "🧪 Test Configuration Validation"
echo "---------------------------------"

# Check for permission handler tests
if [ -f "app/src/test/java/com/example/opencvcamerastream/permissions/PermissionHandlerTest.java" ]; then
    validate_check "Permission handler unit tests" "true" ""
else
    validate_check "Permission handler unit tests" "false" "PermissionHandlerTest.java not found"
fi

# Check for Android 10 specific tests
if [ -f "app/src/test/java/com/example/opencvcamerastream/permissions/Android10PermissionTest.java" ]; then
    validate_check "Android 10 permission tests" "true" ""
else
    validate_check "Android 10 permission tests" "false" "Android10PermissionTest.java not found"
fi

# Check for MainActivity tests
if [ -f "app/src/test/java/com/example/opencvcamerastream/MainActivityPermissionTest.java" ]; then
    validate_check "MainActivity integration tests" "true" ""
else
    validate_check "MainActivity integration tests" "false" "MainActivityPermissionTest.java not found"
fi

echo ""
echo "🔧 CI/CD Workflow Validation"
echo "-----------------------------"

# Check Codemagic workflow configuration
if grep -q "android-workflow" codemagic.yaml && grep -q "android-release-workflow" codemagic.yaml && grep -q "android-test-workflow" codemagic.yaml; then
    validate_check "Codemagic workflow definitions" "true" ""
else
    validate_check "Codemagic workflow definitions" "false" "Missing required workflow definitions in codemagic.yaml"
fi

# Check for proper myCoolApp path references
if grep -q "myCoolApp" codemagic.yaml; then
    validate_check "myCoolApp path references in CI" "true" ""
else
    validate_check "myCoolApp path references in CI" "false" "Codemagic configuration not updated for myCoolApp structure"
fi

# Check for branch patterns
if grep -q "feature/\*" codemagic.yaml && grep -q "main" codemagic.yaml && grep -q "develop" codemagic.yaml; then
    validate_check "Git branch patterns configured" "true" ""
else
    validate_check "Git branch patterns configured" "false" "Branch patterns not properly configured"
fi

# Check for tag patterns for release
if grep -q "v\*\.\*\.\*" codemagic.yaml; then
    validate_check "Release tag patterns configured" "true" ""
else
    validate_check "Release tag patterns configured" "false" "Release tag patterns not configured"
fi

echo ""
echo "📊 Final Validation Result"
echo "============================"

if [ "$VALIDATION_PASSED" = "true" ]; then
    echo -e "🎉 ${GREEN}All validations passed!${NC}"
    echo -e "   ${GREEN}Project is ready for CI/CD with Codemagic${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Commit your changes with a descriptive message"
    echo "2. Push to git repository to trigger CI/CD workflow"
    echo "3. Monitor Codemagic build results"
    echo ""
    echo "Example commands:"
    echo "  git add ."
    echo "  git commit -m 'feat: complete project setup with CI/CD integration'"
    echo "  git push origin main"
    exit 0
else
    echo -e "❌ ${RED}Validation failed!${NC}"
    echo -e "   ${YELLOW}Please fix the issues above before proceeding${NC}"
    echo ""
    echo "Common fixes:"
    echo "1. Ensure you're in the myCoolApp directory"
    echo "2. Check that all required files are present"
    echo "3. Verify Android 10 compliance settings"
    echo "4. Ensure test files are properly created"
    exit 1
fi