#!/bin/bash

# Comprehensive CI/CD Configuration Validation Script
# Validates Codemagic configuration, build system, and Android 10 compatibility

echo "=== CODEMAGIC CI/CD CONFIGURATION VALIDATION ==="
echo "Validation timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo ""

# Initialize validation results
VALIDATION_ERRORS=0
VALIDATION_WARNINGS=0

# Function to log validation results
log_error() {
    echo "❌ ERROR: $1"
    ((VALIDATION_ERRORS++))
}

log_warning() {
    echo "⚠️  WARNING: $1"
    ((VALIDATION_WARNINGS++))
}

log_success() {
    echo "✅ SUCCESS: $1"
}

# Validate project structure
echo "=== PROJECT STRUCTURE VALIDATION ==="

if [ ! -f "codemagic.yaml" ]; then
    log_error "codemagic.yaml not found in project root"
else
    log_success "codemagic.yaml found"
fi

if [ ! -f "build.gradle" ]; then
    log_error "build.gradle not found in project root"
else
    log_success "build.gradle found"
fi

if [ ! -f "settings.gradle" ]; then
    log_error "settings.gradle not found in project root"
else
    log_success "settings.gradle found"
fi

if [ ! -d "app" ]; then
    log_error "app module directory not found"
else
    log_success "app module directory found"
fi

if [ ! -f "app/build.gradle" ]; then
    log_error "app/build.gradle not found"
else
    log_success "app/build.gradle found"
fi

# Validate Gradle configuration
echo ""
echo "=== GRADLE CONFIGURATION VALIDATION ==="

# Check Android Gradle Plugin version
if grep -q "com.android.application.*8\.1\.2" build.gradle; then
    log_success "Android Gradle Plugin 8.1.2 configured"
else
    log_error "Android Gradle Plugin 8.1.2 not found"
    echo "Current AGP configuration:"
    grep "com.android.application" build.gradle || echo "No AGP configuration found"
fi

# Check repository configuration in settings.gradle
if grep -q "FAIL_ON_PROJECT_REPOS" settings.gradle; then
    log_success "FAIL_ON_PROJECT_REPOS mode configured"
else
    log_error "FAIL_ON_PROJECT_REPOS mode not configured in settings.gradle"
fi

if grep -q "google()" settings.gradle && grep -q "mavenCentral()" settings.gradle; then
    log_success "Required repositories configured in settings.gradle"
else
    log_error "Required repositories (Google, Maven Central) not properly configured in settings.gradle"
fi

# Check for conflicting project-level repositories
if grep -A 10 -B 2 "repositories {" build.gradle | grep -v "^--$" | grep -q "google()\|mavenCentral()"; then
    log_warning "Found repositories in project-level build.gradle - may conflict with FAIL_ON_PROJECT_REPOS"
fi

# Validate Android 10 compatibility
echo ""
echo "=== ANDROID 10 COMPATIBILITY VALIDATION ==="

# Check target SDK
if grep -q "targetSdk 29" app/build.gradle; then
    log_success "Target SDK 29 (Android 10) configured"
else
    log_error "Target SDK should be 29 for Android 10 compatibility"
    echo "Current target SDK:"
    grep "targetSdk" app/build.gradle || echo "Target SDK not found"
fi

# Check required permissions
if [ -f "app/src/main/AndroidManifest.xml" ]; then
    if grep -q "android.permission.CAMERA" app/src/main/AndroidManifest.xml; then
        log_success "Camera permission declared in AndroidManifest.xml"
    else
        log_error "Camera permission not found in AndroidManifest.xml"
    fi
    
    # Check for legacy external storage (should be avoided for Android 10)
    if grep -q "android:requestLegacyExternalStorage" app/src/main/AndroidManifest.xml; then
        log_warning "Legacy external storage flag found - ensure scoped storage compliance"
    else
        log_success "No legacy external storage flags found"
    fi
else
    log_error "AndroidManifest.xml not found"
fi

# Validate Codemagic configuration
echo ""
echo "=== CODEMAGIC CONFIGURATION VALIDATION ==="

if [ -f "codemagic.yaml" ]; then
    # Check Java 17 configuration
    if grep -q "java: 17" codemagic.yaml; then
        log_success "Java 17 configured in codemagic.yaml"
    else
        log_error "Java 17 not configured in codemagic.yaml"
    fi
    
    # Check for all three workflow types
    if grep -q "android-development-workflow" codemagic.yaml; then
        log_success "Development workflow configured"
    else
        log_error "Development workflow not found"
    fi
    
    if grep -q "android-release-workflow" codemagic.yaml; then
        log_success "Release workflow configured"
    else
        log_error "Release workflow not found"
    fi
    
    if grep -q "android-test-workflow" codemagic.yaml; then
        log_success "Test-only workflow configured"
    else
        log_error "Test-only workflow not found"
    fi
    
    # Check triggering configuration
    if grep -q "branch_patterns" codemagic.yaml; then
        log_success "Branch patterns configured for triggering"
    else
        log_warning "Branch patterns not configured"
    fi
    
    if grep -q "tag_patterns" codemagic.yaml; then
        log_success "Tag patterns configured for release workflow"
    else
        log_warning "Tag patterns not configured for release workflow"
    fi
    
    # Check artifact collection
    if grep -q "artifacts:" codemagic.yaml; then
        log_success "Artifact collection configured"
    else
        log_warning "Artifact collection not configured"
    fi
    
    # Check email notifications
    if grep -q "email:" codemagic.yaml; then
        log_success "Email notifications configured"
    else
        log_warning "Email notifications not configured"
    fi
fi

# Validate Gradle wrapper setup
echo ""
echo "=== GRADLE WRAPPER VALIDATION ==="

if [ -f "scripts/setup-gradle-wrapper.sh" ]; then
    log_success "Gradle wrapper setup script found"
    
    if grep -q "8.14.1" scripts/setup-gradle-wrapper.sh; then
        log_success "Gradle 8.14.1 configured in setup script"
    else
        log_warning "Gradle 8.14.1 not explicitly configured in setup script"
    fi
else
    log_error "Gradle wrapper setup script not found"
fi

if [ -f "gradlew" ]; then
    log_success "gradlew file present"
    if [ -x "gradlew" ]; then
        log_success "gradlew is executable"
    else
        log_warning "gradlew is not executable"
    fi
else
    log_warning "gradlew file not present (will be created by setup script)"
fi

if [ -f "gradle/wrapper/gradle-wrapper.properties" ]; then
    log_success "gradle-wrapper.properties present"
    
    if grep -q "gradle-8" gradle/wrapper/gradle-wrapper.properties; then
        log_success "Gradle 8.x configured in wrapper properties"
    else
        log_warning "Gradle 8.x not configured in wrapper properties"
    fi
else
    log_warning "gradle-wrapper.properties not present (will be created by setup script)"
fi

# Validate OpenCV integration
echo ""
echo "=== OPENCV INTEGRATION VALIDATION ==="

if [ -d "opencv" ]; then
    log_success "OpenCV module directory found"
    
    if [ -f "opencv/build.gradle" ]; then
        log_success "OpenCV build.gradle found"
    else
        log_error "OpenCV build.gradle not found"
    fi
else
    log_error "OpenCV module directory not found"
fi

if grep -q ":opencv" settings.gradle; then
    log_success "OpenCV module included in settings.gradle"
else
    log_error "OpenCV module not included in settings.gradle"
fi

if grep -q "implementation project(':opencv')" app/build.gradle; then
    log_success "App module depends on OpenCV"
else
    log_error "App module does not depend on OpenCV"
fi

# Validate test configuration
echo ""
echo "=== TEST CONFIGURATION VALIDATION ==="

if grep -q "testImplementation" app/build.gradle; then
    log_success "Unit test dependencies configured"
else
    log_warning "Unit test dependencies not found"
fi

if grep -q "androidTestImplementation" app/build.gradle; then
    log_success "Android test dependencies configured"
else
    log_warning "Android test dependencies not found"
fi

if grep -q "jacoco" app/build.gradle; then
    log_success "Code coverage (Jacoco) configured"
else
    log_warning "Code coverage (Jacoco) not configured"
fi

# Generate validation summary
echo ""
echo "=== VALIDATION SUMMARY ==="
echo "Validation completed at: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "Total errors: $VALIDATION_ERRORS"
echo "Total warnings: $VALIDATION_WARNINGS"

if [ $VALIDATION_ERRORS -eq 0 ]; then
    if [ $VALIDATION_WARNINGS -eq 0 ]; then
        echo "✅ All validations passed successfully!"
        echo "CI/CD configuration is ready for use."
    else
        echo "⚠️  Validation passed with $VALIDATION_WARNINGS warnings."
        echo "CI/CD configuration should work but may have minor issues."
    fi
    exit 0
else
    echo "❌ Validation failed with $VALIDATION_ERRORS errors and $VALIDATION_WARNINGS warnings."
    echo "Please fix the errors before using the CI/CD configuration."
    exit 1
fi