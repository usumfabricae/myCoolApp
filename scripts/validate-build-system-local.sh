#!/bin/bash

# Local Build System Validation Script for Task 15
# Validates build configuration files that can be checked without Java/Gradle runtime
# This script runs locally and prepares for CI/CD validation

echo "=== LOCAL BUILD SYSTEM VALIDATION - TASK 15 ==="
echo "Validation Date: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "Environment: Local (Configuration Files Only)"

# Exit on any error for strict validation
set -e

# Validation results tracking
VALIDATION_RESULTS=()
VALIDATION_ERRORS=()

# Function to log validation results
log_validation() {
    local test_name="$1"
    local status="$2"
    local message="$3"
    
    if [ "$status" = "PASS" ]; then
        echo "✅ $test_name: $message"
        VALIDATION_RESULTS+=("PASS: $test_name - $message")
    else
        echo "❌ $test_name: $message"
        VALIDATION_RESULTS+=("FAIL: $test_name - $message")
        VALIDATION_ERRORS+=("$test_name: $message")
    fi
}

echo ""
echo "=== 1. GRADLE WRAPPER CONFIGURATION VALIDATION ==="

# Check gradle-wrapper.properties exists and has correct version
if [ -f "gradle/wrapper/gradle-wrapper.properties" ]; then
    if grep -q "gradle-8\.14\.1" gradle/wrapper/gradle-wrapper.properties; then
        log_validation "Gradle Version Config" "PASS" "Gradle 8.14.1 configured in wrapper properties"
    else
        GRADLE_VERSION=$(grep "distributionUrl" gradle/wrapper/gradle-wrapper.properties | sed 's/.*gradle-\([0-9.]*\)-.*/\1/' || echo "unknown")
        log_validation "Gradle Version Config" "FAIL" "Expected Gradle 8.14.1, found: $GRADLE_VERSION"
    fi
    
    # Check distribution URL format (handle escaped colon)
    if grep -q "gradle-8\.14\.1-bin\.zip" gradle/wrapper/gradle-wrapper.properties; then
        log_validation "Gradle Distribution URL" "PASS" "Correct Gradle distribution URL configured"
    else
        log_validation "Gradle Distribution URL" "FAIL" "Gradle distribution URL may be incorrect"
    fi
else
    log_validation "Gradle Wrapper Properties" "FAIL" "gradle-wrapper.properties not found"
fi

# Check gradle-wrapper.jar exists and is not empty
if [ -f "gradle/wrapper/gradle-wrapper.jar" ] && [ -s "gradle/wrapper/gradle-wrapper.jar" ]; then
    JAR_SIZE=$(stat -c%s gradle/wrapper/gradle-wrapper.jar 2>/dev/null || stat -f%z gradle/wrapper/gradle-wrapper.jar 2>/dev/null || wc -c < gradle/wrapper/gradle-wrapper.jar)
    log_validation "Gradle Wrapper JAR" "PASS" "gradle-wrapper.jar exists and is not empty ($JAR_SIZE bytes)"
else
    log_validation "Gradle Wrapper JAR" "FAIL" "gradle-wrapper.jar missing or empty"
fi

# Check gradlew scripts exist
if [ -f "gradlew" ]; then
    log_validation "Gradle Wrapper Script (Unix)" "PASS" "gradlew exists"
else
    log_validation "Gradle Wrapper Script (Unix)" "FAIL" "gradlew missing"
fi

if [ -f "gradlew.bat" ]; then
    log_validation "Gradle Wrapper Script (Windows)" "PASS" "gradlew.bat exists"
else
    log_validation "Gradle Wrapper Script (Windows)" "FAIL" "gradlew.bat missing"
fi

echo ""
echo "=== 2. ANDROID GRADLE PLUGIN CONFIGURATION VALIDATION ==="

# Check Android Gradle Plugin version in build.gradle
if [ -f "build.gradle" ]; then
    if grep -q "com.android.application.*8\.1\.2" build.gradle; then
        log_validation "Android Gradle Plugin Version" "PASS" "AGP 8.1.2 configured in build.gradle"
    else
        AGP_VERSION=$(grep "com.android.application" build.gradle | sed 's/.*version.*\([0-9.]*\).*/\1/' || echo "not found")
        log_validation "Android Gradle Plugin Version" "FAIL" "Expected AGP 8.1.2, found: $AGP_VERSION"
    fi
    
    # Check for library plugin consistency
    if grep -q "com.android.library.*8\.1\.2" build.gradle; then
        log_validation "Android Library Plugin Version" "PASS" "Android Library Plugin 8.1.2 configured"
    else
        log_validation "Android Library Plugin Version" "FAIL" "Android Library Plugin version inconsistent with Application Plugin"
    fi
else
    log_validation "Root Build Gradle" "FAIL" "build.gradle not found"
fi

echo ""
echo "=== 3. REPOSITORY CONFIGURATION VALIDATION ==="

# Check settings.gradle for centralized repository management
if [ -f "settings.gradle" ]; then
    if grep -q "FAIL_ON_PROJECT_REPOS" settings.gradle; then
        log_validation "Repository Mode" "PASS" "FAIL_ON_PROJECT_REPOS mode configured in settings.gradle"
    else
        log_validation "Repository Mode" "FAIL" "FAIL_ON_PROJECT_REPOS mode not found in settings.gradle"
    fi
    
    # Check required repositories
    if grep -q "google()" settings.gradle && grep -q "mavenCentral()" settings.gradle; then
        log_validation "Required Repositories" "PASS" "Google and Maven Central repositories configured in settings.gradle"
    else
        log_validation "Required Repositories" "FAIL" "Required repositories not properly configured in settings.gradle"
    fi
    
    # Check plugin management repositories
    if grep -q "gradlePluginPortal()" settings.gradle; then
        log_validation "Plugin Repositories" "PASS" "Gradle Plugin Portal configured for plugin management"
    else
        log_validation "Plugin Repositories" "FAIL" "Gradle Plugin Portal not configured"
    fi
    
    # Check module includes
    if grep -q "include ':app'" settings.gradle && grep -q "include ':opencv'" settings.gradle; then
        log_validation "Module Configuration" "PASS" "App and OpenCV modules properly included"
    else
        log_validation "Module Configuration" "FAIL" "Required modules not properly included"
    fi
else
    log_validation "Settings Gradle" "FAIL" "settings.gradle not found"
fi

# Check for conflicting repositories in project-level build.gradle
if [ -f "build.gradle" ]; then
    if grep -A 10 -B 2 "repositories {" build.gradle | grep -v "^--$" | grep -q "google()\|mavenCentral()"; then
        log_validation "Repository Conflicts" "FAIL" "Found repositories in project-level build.gradle - conflicts with FAIL_ON_PROJECT_REPOS"
    else
        log_validation "Repository Conflicts" "PASS" "No conflicting repositories in project-level build.gradle"
    fi
fi

echo ""
echo "=== 4. ANDROID APP CONFIGURATION VALIDATION ==="

# Check app/build.gradle configuration
if [ -f "app/build.gradle" ]; then
    # Check target SDK
    if grep -q "targetSdk 29" app/build.gradle; then
        log_validation "Target SDK" "PASS" "Target SDK 29 (Android 10) configured"
    else
        TARGET_SDK=$(grep "targetSdk" app/build.gradle | sed 's/.*targetSdk \([0-9]*\).*/\1/' || echo "not found")
        log_validation "Target SDK" "FAIL" "Expected target SDK 29, found: $TARGET_SDK"
    fi
    
    # Check minimum SDK
    if grep -q "minSdk 21" app/build.gradle; then
        log_validation "Minimum SDK" "PASS" "Minimum SDK 21 (Android 5.0) configured for backward compatibility"
    else
        MIN_SDK=$(grep "minSdk" app/build.gradle | sed 's/.*minSdk \([0-9]*\).*/\1/' || echo "not found")
        log_validation "Minimum SDK" "FAIL" "Expected minimum SDK 21, found: $MIN_SDK"
    fi
    
    # Check compile SDK
    if grep -q "compileSdk 34" app/build.gradle; then
        log_validation "Compile SDK" "PASS" "Compile SDK 34 configured for latest features"
    else
        COMPILE_SDK=$(grep "compileSdk" app/build.gradle | sed 's/.*compileSdk \([0-9]*\).*/\1/' || echo "not found")
        log_validation "Compile SDK" "FAIL" "Expected compile SDK 34, found: $COMPILE_SDK"
    fi
    
    # Check OpenCV dependency
    if grep -q "implementation project(':opencv')" app/build.gradle; then
        log_validation "OpenCV Dependency" "PASS" "OpenCV module dependency configured"
    else
        log_validation "OpenCV Dependency" "FAIL" "OpenCV module dependency not found"
    fi
    
    # Check Jacoco configuration
    if grep -q "apply plugin: 'jacoco'" app/build.gradle; then
        log_validation "Code Coverage" "PASS" "Jacoco code coverage plugin configured"
    else
        log_validation "Code Coverage" "FAIL" "Jacoco code coverage plugin not configured"
    fi
else
    log_validation "App Build Gradle" "FAIL" "app/build.gradle not found"
fi

echo ""
echo "=== 5. GRADLE PROPERTIES OPTIMIZATION VALIDATION ==="

if [ -f "gradle.properties" ]; then
    # Check JVM arguments (accept both 2GB and 4GB configurations)
    JVM_ARGS=$(grep "org.gradle.jvmargs" gradle.properties || echo "not found")
    if echo "$JVM_ARGS" | grep -q "Xmx4096m"; then
        log_validation "Gradle JVM Args" "PASS" "Gradle JVM arguments optimized for 4GB heap with G1GC"
    elif echo "$JVM_ARGS" | grep -q "Xmx2048m"; then
        log_validation "Gradle JVM Args" "PASS" "Gradle JVM arguments configured for 2GB heap"
    else
        log_validation "Gradle JVM Args" "FAIL" "Gradle JVM arguments not optimally configured: $JVM_ARGS"
    fi
    
    # Check AndroidX usage
    if grep -q "android.useAndroidX=true" gradle.properties; then
        log_validation "AndroidX Usage" "PASS" "AndroidX enabled for modern Android development"
    else
        log_validation "AndroidX Usage" "FAIL" "AndroidX not enabled in gradle.properties"
    fi
    
    # Check lint configuration
    if grep -q "android.lint.abortOnError=false" gradle.properties; then
        log_validation "Lint Configuration" "PASS" "Lint configured to not abort on errors (CI-friendly)"
    else
        log_validation "Lint Configuration" "FAIL" "Lint not configured for CI/CD environment"
    fi
    
    # Check R class optimization
    if grep -q "android.nonTransitiveRClass=true" gradle.properties; then
        log_validation "R Class Optimization" "PASS" "Non-transitive R class optimization enabled"
    else
        log_validation "R Class Optimization" "FAIL" "R class optimization not enabled"
    fi
else
    log_validation "Gradle Properties" "FAIL" "gradle.properties not found"
fi

echo ""
echo "=== 6. AUTOMATED SETUP SCRIPTS VALIDATION ==="

# Check setup scripts exist and are executable
SCRIPTS=("setup-gradle-wrapper.sh" "setup-opencv.sh" "verify-build-outputs.sh" "validate-build-system.sh")

for script in "${SCRIPTS[@]}"; do
    if [ -f "scripts/$script" ]; then
        log_validation "Script: $script" "PASS" "$script exists"
    else
        log_validation "Script: $script" "FAIL" "$script missing"
    fi
done

echo ""
echo "=== 7. CI/CD CONFIGURATION VALIDATION ==="

# Check codemagic.yaml configuration
if [ -f "codemagic.yaml" ]; then
    # Check Java 17 configuration
    if grep -q "java: 17" codemagic.yaml; then
        log_validation "CI Java Version" "PASS" "Java 17 configured in codemagic.yaml"
    else
        log_validation "CI Java Version" "FAIL" "Java 17 not configured in codemagic.yaml"
    fi
    
    # Check dynamic project discovery
    if grep -q "DYNAMIC ANDROID PROJECT DISCOVERY" codemagic.yaml; then
        log_validation "Dynamic Project Discovery" "PASS" "Dynamic project discovery configured"
    else
        log_validation "Dynamic Project Discovery" "FAIL" "Dynamic project discovery not configured"
    fi
    
    # Check flexible gradle execution
    if grep -q "FLEXIBLE GRADLE EXECUTION" codemagic.yaml; then
        log_validation "Flexible Gradle Execution" "PASS" "Flexible gradle execution configured"
    else
        log_validation "Flexible Gradle Execution" "FAIL" "Flexible gradle execution not configured"
    fi
    
    # Check build environment debugging
    if grep -q "COMPREHENSIVE BUILD ENVIRONMENT DEBUG" codemagic.yaml; then
        log_validation "Build Environment Debugging" "PASS" "Comprehensive debugging configured"
    else
        log_validation "Build Environment Debugging" "FAIL" "Build environment debugging not configured"
    fi
    
    # Check workflow definitions
    WORKFLOWS=("android-development-workflow" "android-release-workflow" "android-test-workflow")
    for workflow in "${WORKFLOWS[@]}"; do
        if grep -q "$workflow:" codemagic.yaml; then
            log_validation "Workflow: $workflow" "PASS" "$workflow defined in codemagic.yaml"
        else
            log_validation "Workflow: $workflow" "FAIL" "$workflow not defined in codemagic.yaml"
        fi
    done
else
    log_validation "Codemagic Configuration" "FAIL" "codemagic.yaml not found"
fi

echo ""
echo "=== 8. PROJECT STRUCTURE VALIDATION ==="

# Check essential directories and files
ESSENTIAL_DIRS=("app" "app/src" "app/src/main" "app/src/main/java" "opencv" "gradle" "gradle/wrapper" "scripts")
for dir in "${ESSENTIAL_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        log_validation "Directory: $dir" "PASS" "$dir exists"
    else
        log_validation "Directory: $dir" "FAIL" "$dir missing"
    fi
done

ESSENTIAL_FILES=("build.gradle" "settings.gradle" "gradle.properties" "gradlew" "gradlew.bat" "app/build.gradle")
for file in "${ESSENTIAL_FILES[@]}"; do
    if [ -f "$file" ]; then
        log_validation "File: $file" "PASS" "$file exists"
    else
        log_validation "File: $file" "FAIL" "$file missing"
    fi
done

echo ""
echo "=== LOCAL VALIDATION SUMMARY ==="
echo "Total validations performed: ${#VALIDATION_RESULTS[@]}"
echo "Validation errors found: ${#VALIDATION_ERRORS[@]}"

echo ""
echo "=== DETAILED RESULTS ==="
for result in "${VALIDATION_RESULTS[@]}"; do
    echo "$result"
done

if [ ${#VALIDATION_ERRORS[@]} -gt 0 ]; then
    echo ""
    echo "=== ERRORS THAT NEED ATTENTION ==="
    for error in "${VALIDATION_ERRORS[@]}"; do
        echo "❌ $error"
    done
    echo ""
    echo "❌ LOCAL BUILD SYSTEM VALIDATION FAILED"
    echo "Note: Java/Gradle runtime validation will be performed in CI/CD environment"
    exit 1
else
    echo ""
    echo "✅ ALL LOCAL BUILD SYSTEM VALIDATIONS PASSED"
    echo "✅ Configuration files are properly set up for CI/CD"
    echo "✅ Java 17 + Android Gradle Plugin 8.1.2 + Gradle 8.14.1 configuration ready"
    echo "✅ Android 10 target compatibility configured"
    echo "✅ Centralized repository management validated"
    echo "✅ Automated setup scripts are present"
    echo "✅ CI/CD configuration is complete"
    echo ""
    echo "🚀 Ready for CI/CD validation with Java/Gradle runtime"
fi

echo ""
echo "=== LOCAL BUILD SYSTEM VALIDATION COMPLETED ==="