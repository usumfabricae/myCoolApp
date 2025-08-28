# Comprehensive CI/CD Configuration Validation Script for PowerShell
# Validates Codemagic configuration, build system, and Android 10 compatibility

Write-Host "=== CODEMAGIC CI/CD CONFIGURATION VALIDATION ===" -ForegroundColor Cyan
Write-Host "Validation timestamp: $((Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'))"
Write-Host ""

# Initialize validation results
$ValidationErrors = 0
$ValidationWarnings = 0

# Function to log validation results
function Log-Error {
    param([string]$Message)
    Write-Host "❌ ERROR: $Message" -ForegroundColor Red
    $script:ValidationErrors++
}

function Log-Warning {
    param([string]$Message)
    Write-Host "⚠️  WARNING: $Message" -ForegroundColor Yellow
    $script:ValidationWarnings++
}

function Log-Success {
    param([string]$Message)
    Write-Host "✅ SUCCESS: $Message" -ForegroundColor Green
}

# Validate project structure
Write-Host "=== PROJECT STRUCTURE VALIDATION ===" -ForegroundColor Cyan

if (-not (Test-Path "codemagic.yaml")) {
    Log-Error "codemagic.yaml not found in project root"
} else {
    Log-Success "codemagic.yaml found"
}

if (-not (Test-Path "build.gradle")) {
    Log-Error "build.gradle not found in project root"
} else {
    Log-Success "build.gradle found"
}

if (-not (Test-Path "settings.gradle")) {
    Log-Error "settings.gradle not found in project root"
} else {
    Log-Success "settings.gradle found"
}

if (-not (Test-Path "app" -PathType Container)) {
    Log-Error "app module directory not found"
} else {
    Log-Success "app module directory found"
}

if (-not (Test-Path "app/build.gradle")) {
    Log-Error "app/build.gradle not found"
} else {
    Log-Success "app/build.gradle found"
}

# Validate Gradle configuration
Write-Host ""
Write-Host "=== GRADLE CONFIGURATION VALIDATION ===" -ForegroundColor Cyan

# Check Android Gradle Plugin version
if (Test-Path "build.gradle") {
    $buildGradleContent = Get-Content "build.gradle" -Raw
    if ($buildGradleContent -match "com\.android\.application.*8\.1\.2") {
        Log-Success "Android Gradle Plugin 8.1.2 configured"
    } else {
        Log-Error "Android Gradle Plugin 8.1.2 not found"
        Write-Host "Current AGP configuration:"
        $buildGradleContent | Select-String "com\.android\.application" | ForEach-Object { Write-Host $_.Line }
    }
}

# Check repository configuration in settings.gradle
if (Test-Path "settings.gradle") {
    $settingsGradleContent = Get-Content "settings.gradle" -Raw
    if ($settingsGradleContent -match "FAIL_ON_PROJECT_REPOS") {
        Log-Success "FAIL_ON_PROJECT_REPOS mode configured"
    } else {
        Log-Error "FAIL_ON_PROJECT_REPOS mode not configured in settings.gradle"
    }
    
    if ($settingsGradleContent -match "google\(\)" -and $settingsGradleContent -match "mavenCentral\(\)") {
        Log-Success "Required repositories configured in settings.gradle"
    } else {
        Log-Error "Required repositories (Google, Maven Central) not properly configured in settings.gradle"
    }
}

# Check for conflicting project-level repositories
if (Test-Path "build.gradle") {
    $buildGradleContent = Get-Content "build.gradle" -Raw
    if ($buildGradleContent -match "repositories\s*\{[\s\S]*?(google\(\)|mavenCentral\(\))") {
        Log-Warning "Found repositories in project-level build.gradle - may conflict with FAIL_ON_PROJECT_REPOS"
    }
}

# Validate Android 10 compatibility
Write-Host ""
Write-Host "=== ANDROID 10 COMPATIBILITY VALIDATION ===" -ForegroundColor Cyan

# Check target SDK
if (Test-Path "app/build.gradle") {
    $appBuildGradleContent = Get-Content "app/build.gradle" -Raw
    if ($appBuildGradleContent -match "targetSdk 29") {
        Log-Success "Target SDK 29 (Android 10) configured"
    } else {
        Log-Error "Target SDK should be 29 for Android 10 compatibility"
        Write-Host "Current target SDK:"
        $appBuildGradleContent | Select-String "targetSdk" | ForEach-Object { Write-Host $_.Line }
    }
}

# Check required permissions
if (Test-Path "app/src/main/AndroidManifest.xml") {
    $manifestContent = Get-Content "app/src/main/AndroidManifest.xml" -Raw
    if ($manifestContent -match "android\.permission\.CAMERA") {
        Log-Success "Camera permission declared in AndroidManifest.xml"
    } else {
        Log-Error "Camera permission not found in AndroidManifest.xml"
    }
    
    # Check for legacy external storage (should be avoided for Android 10)
    if ($manifestContent -match "android:requestLegacyExternalStorage") {
        Log-Warning "Legacy external storage flag found - ensure scoped storage compliance"
    } else {
        Log-Success "No legacy external storage flags found"
    }
} else {
    Log-Error "AndroidManifest.xml not found"
}

# Validate Codemagic configuration
Write-Host ""
Write-Host "=== CODEMAGIC CONFIGURATION VALIDATION ===" -ForegroundColor Cyan

if (Test-Path "codemagic.yaml") {
    $codemagicContent = Get-Content "codemagic.yaml" -Raw
    
    # Check Java 17 configuration
    if ($codemagicContent -match "java:\s*17") {
        Log-Success "Java 17 configured in codemagic.yaml"
    } else {
        Log-Error "Java 17 not configured in codemagic.yaml"
    }
    
    # Check for all three workflow types
    if ($codemagicContent -match "android-development-workflow") {
        Log-Success "Development workflow configured"
    } else {
        Log-Error "Development workflow not found"
    }
    
    if ($codemagicContent -match "android-release-workflow") {
        Log-Success "Release workflow configured"
    } else {
        Log-Error "Release workflow not found"
    }
    
    if ($codemagicContent -match "android-test-workflow") {
        Log-Success "Test-only workflow configured"
    } else {
        Log-Error "Test-only workflow not found"
    }
    
    # Check triggering configuration
    if ($codemagicContent -match "branch_patterns") {
        Log-Success "Branch patterns configured for triggering"
    } else {
        Log-Warning "Branch patterns not configured"
    }
    
    if ($codemagicContent -match "tag_patterns") {
        Log-Success "Tag patterns configured for release workflow"
    } else {
        Log-Warning "Tag patterns not configured for release workflow"
    }
    
    # Check artifact collection
    if ($codemagicContent -match "artifacts:") {
        Log-Success "Artifact collection configured"
    } else {
        Log-Warning "Artifact collection not configured"
    }
    
    # Check email notifications
    if ($codemagicContent -match "email:") {
        Log-Success "Email notifications configured"
    } else {
        Log-Warning "Email notifications not configured"
    }
}

# Validate Gradle wrapper setup
Write-Host ""
Write-Host "=== GRADLE WRAPPER VALIDATION ===" -ForegroundColor Cyan

if (Test-Path "scripts/setup-gradle-wrapper.sh") {
    Log-Success "Gradle wrapper setup script found"
    
    $setupScriptContent = Get-Content "scripts/setup-gradle-wrapper.sh" -Raw
    if ($setupScriptContent -match "8\.14\.1") {
        Log-Success "Gradle 8.14.1 configured in setup script"
    } else {
        Log-Warning "Gradle 8.14.1 not explicitly configured in setup script"
    }
} else {
    Log-Error "Gradle wrapper setup script not found"
}

if (Test-Path "gradlew") {
    Log-Success "gradlew file present"
} else {
    Log-Warning "gradlew file not present (will be created by setup script)"
}

if (Test-Path "gradle/wrapper/gradle-wrapper.properties") {
    Log-Success "gradle-wrapper.properties present"
    
    $wrapperPropsContent = Get-Content "gradle/wrapper/gradle-wrapper.properties" -Raw
    if ($wrapperPropsContent -match "gradle-8") {
        Log-Success "Gradle 8.x configured in wrapper properties"
    } else {
        Log-Warning "Gradle 8.x not configured in wrapper properties"
    }
} else {
    Log-Warning "gradle-wrapper.properties not present (will be created by setup script)"
}

# Validate OpenCV integration
Write-Host ""
Write-Host "=== OPENCV INTEGRATION VALIDATION ===" -ForegroundColor Cyan

if (Test-Path "opencv" -PathType Container) {
    Log-Success "OpenCV module directory found"
    
    if (Test-Path "opencv/build.gradle") {
        Log-Success "OpenCV build.gradle found"
    } else {
        Log-Error "OpenCV build.gradle not found"
    }
} else {
    Log-Error "OpenCV module directory not found"
}

if (Test-Path "settings.gradle") {
    $settingsContent = Get-Content "settings.gradle" -Raw
    if ($settingsContent -match ":opencv") {
        Log-Success "OpenCV module included in settings.gradle"
    } else {
        Log-Error "OpenCV module not included in settings.gradle"
    }
}

if (Test-Path "app/build.gradle") {
    $appBuildContent = Get-Content "app/build.gradle" -Raw
    if ($appBuildContent -match "implementation project\(':opencv'\)") {
        Log-Success "App module depends on OpenCV"
    } else {
        Log-Error "App module does not depend on OpenCV"
    }
}

# Validate test configuration
Write-Host ""
Write-Host "=== TEST CONFIGURATION VALIDATION ===" -ForegroundColor Cyan

if (Test-Path "app/build.gradle") {
    $appBuildContent = Get-Content "app/build.gradle" -Raw
    
    if ($appBuildContent -match "testImplementation") {
        Log-Success "Unit test dependencies configured"
    } else {
        Log-Warning "Unit test dependencies not found"
    }
    
    if ($appBuildContent -match "androidTestImplementation") {
        Log-Success "Android test dependencies configured"
    } else {
        Log-Warning "Android test dependencies not found"
    }
    
    if ($appBuildContent -match "jacoco") {
        Log-Success "Code coverage (Jacoco) configured"
    } else {
        Log-Warning "Code coverage (Jacoco) not configured"
    }
}

# Generate validation summary
Write-Host ""
Write-Host "=== VALIDATION SUMMARY ===" -ForegroundColor Cyan
Write-Host "Validation completed at: $((Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'))"
Write-Host "Total errors: $ValidationErrors"
Write-Host "Total warnings: $ValidationWarnings"

if ($ValidationErrors -eq 0) {
    if ($ValidationWarnings -eq 0) {
        Write-Host "✅ All validations passed successfully!" -ForegroundColor Green
        Write-Host "CI/CD configuration is ready for use." -ForegroundColor Green
    } else {
        Write-Host "⚠️  Validation passed with $ValidationWarnings warnings." -ForegroundColor Yellow
        Write-Host "CI/CD configuration should work but may have minor issues." -ForegroundColor Yellow
    }
    exit 0
} else {
    Write-Host "❌ Validation failed with $ValidationErrors errors and $ValidationWarnings warnings." -ForegroundColor Red
    Write-Host "Please fix the errors before using the CI/CD configuration." -ForegroundColor Red
    exit 1
}