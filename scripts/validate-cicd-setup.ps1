# CI/CD Setup Validation Script (PowerShell)
# This script validates that the project is properly configured for Codemagic CI/CD

Write-Host "Validating CI/CD Setup for Android Camera OpenCV Stream" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# Validation results
$ValidationPassed = $true

# Function to print validation result
function Validate-Check {
    param(
        [string]$CheckName,
        [bool]$Condition,
        [string]$ErrorMessage
    )
    
    if ($Condition) {
        Write-Host "✅ $CheckName" -ForegroundColor Green
    } else {
        Write-Host "❌ $CheckName" -ForegroundColor Red
        Write-Host "   $ErrorMessage" -ForegroundColor Yellow
        $script:ValidationPassed = $false
    }
}

Write-Host ""
Write-Host "Project Structure Validation" -ForegroundColor White
Write-Host "-----------------------------" -ForegroundColor White

# Check if we're in the myCoolApp directory
$ProjectStructureValid = (Test-Path "build.gradle") -and (Test-Path "app" -PathType Container)
Validate-Check "Project structure (myCoolApp)" $ProjectStructureValid "Run this script from the myCoolApp directory"

# Check for Codemagic configuration
$CodemagicConfigExists = Test-Path "codemagic.yaml"
Validate-Check "Codemagic configuration file" $CodemagicConfigExists "codemagic.yaml not found"

# Check for Git workflow documentation
$GitWorkflowExists = Test-Path "GIT_WORKFLOW.md"
Validate-Check "Git workflow documentation" $GitWorkflowExists "GIT_WORKFLOW.md not found"

Write-Host ""
Write-Host "Build Configuration Validation" -ForegroundColor White
Write-Host "-------------------------------" -ForegroundColor White

# Check Android target SDK
$BuildGradleContent = Get-Content "app/build.gradle" -Raw -ErrorAction SilentlyContinue
$TargetSdkValid = $BuildGradleContent -match "targetSdk 29"
Validate-Check "Target SDK 29 (Android 10)" $TargetSdkValid "Target SDK should be 29 for Android 10 compliance"

# Check for Jacoco plugin
$JacocoConfigured = $BuildGradleContent -match "jacoco"
Validate-Check "Jacoco test coverage plugin" $JacocoConfigured "Jacoco plugin not configured for test coverage"

# Check for test dependencies
$MockitoExists = $BuildGradleContent -match "testImplementation.*mockito"
$RobolectricExists = $BuildGradleContent -match "testImplementation.*robolectric"
$TestDepsValid = $MockitoExists -and $RobolectricExists
Validate-Check "Test dependencies (Mockito, Robolectric)" $TestDepsValid "Missing required test dependencies"

Write-Host ""
Write-Host "Android Configuration Validation" -ForegroundColor White
Write-Host "---------------------------------" -ForegroundColor White

# Check camera permission in manifest
$ManifestContent = Get-Content "app/src/main/AndroidManifest.xml" -Raw -ErrorAction SilentlyContinue
$CameraPermissionExists = $ManifestContent -match "android.permission.CAMERA"
Validate-Check "Camera permission in manifest" $CameraPermissionExists "Camera permission not declared in AndroidManifest.xml"

# Check for Android 10 privacy compliance
$ScopedStorageCompliant = $ManifestContent -match "requestLegacyExternalStorage.*false"
Validate-Check "Android 10 scoped storage compliance" $ScopedStorageCompliant "Scoped storage not properly configured"

Write-Host ""
Write-Host "Test Configuration Validation" -ForegroundColor White
Write-Host "------------------------------" -ForegroundColor White

# Check for permission handler tests
$PermissionHandlerTestExists = Test-Path "app/src/test/java/com/example/opencvcamerastream/permissions/PermissionHandlerTest.java"
Validate-Check "Permission handler unit tests" $PermissionHandlerTestExists "PermissionHandlerTest.java not found"

# Check for Android 10 specific tests
$Android10TestExists = Test-Path "app/src/test/java/com/example/opencvcamerastream/permissions/Android10PermissionTest.java"
Validate-Check "Android 10 permission tests" $Android10TestExists "Android10PermissionTest.java not found"

# Check for MainActivity tests
$MainActivityTestExists = Test-Path "app/src/test/java/com/example/opencvcamerastream/MainActivityPermissionTest.java"
Validate-Check "MainActivity integration tests" $MainActivityTestExists "MainActivityPermissionTest.java not found"

Write-Host ""
Write-Host "CI/CD Workflow Validation" -ForegroundColor White
Write-Host "-------------------------" -ForegroundColor White

# Check Codemagic workflow configuration
$CodemagicContent = Get-Content "codemagic.yaml" -Raw -ErrorAction SilentlyContinue
$WorkflowsExist = ($CodemagicContent -match "android-workflow") -and 
                  ($CodemagicContent -match "android-release-workflow") -and 
                  ($CodemagicContent -match "android-test-workflow")
Validate-Check "Codemagic workflow definitions" $WorkflowsExist "Missing required workflow definitions in codemagic.yaml"

# Check for proper myCoolApp path references
$MyCoolAppPathsExist = $CodemagicContent -match "myCoolApp"
Validate-Check "myCoolApp path references in CI" $MyCoolAppPathsExist "Codemagic configuration not updated for myCoolApp structure"

# Check for branch patterns
$BranchPatternsExist = ($CodemagicContent -match "feature/") -and 
                       ($CodemagicContent -match "main") -and 
                       ($CodemagicContent -match "develop")
Validate-Check "Git branch patterns configured" $BranchPatternsExist "Branch patterns not properly configured"

# Check for tag patterns for release
$TagPatternsExist = $CodemagicContent -match "v"
Validate-Check "Release tag patterns configured" $TagPatternsExist "Release tag patterns not configured"

Write-Host ""
Write-Host "Final Validation Result" -ForegroundColor White
Write-Host "=======================" -ForegroundColor White

if ($ValidationPassed) {
    Write-Host "All validations passed!" -ForegroundColor Green
    Write-Host "Project is ready for CI/CD with Codemagic" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor White
    Write-Host "1. Commit your changes with a descriptive message" -ForegroundColor White
    Write-Host "2. Push to git repository to trigger CI/CD workflow" -ForegroundColor White
    Write-Host "3. Monitor Codemagic build results" -ForegroundColor White
    Write-Host ""
    Write-Host "Example commands:" -ForegroundColor White
    Write-Host "  git add ." -ForegroundColor Gray
    Write-Host "  git commit -m 'feat: complete project setup with CI/CD integration'" -ForegroundColor Gray
    Write-Host "  git push origin main" -ForegroundColor Gray
    exit 0
} else {
    Write-Host "Validation failed!" -ForegroundColor Red
    Write-Host "Please fix the issues above before proceeding" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Common fixes:" -ForegroundColor White
    Write-Host "1. Ensure you are in the myCoolApp directory" -ForegroundColor White
    Write-Host "2. Check that all required files are present" -ForegroundColor White
    Write-Host "3. Verify Android 10 compliance settings" -ForegroundColor White
    Write-Host "4. Ensure test files are properly created" -ForegroundColor White
    exit 1
}