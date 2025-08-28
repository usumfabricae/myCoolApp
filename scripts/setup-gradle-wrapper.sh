#!/bin/bash

# Automated Gradle wrapper setup script for Codemagic CI/CD
# Compatible with Java 17 and Android Gradle Plugin 8.1.2
# Gradle version: 8.14.1 (optimized for Java 17 and AGP 8.1.2)

echo "=== AUTOMATED GRADLE WRAPPER SETUP ==="
echo "Target Gradle version: 8.14.1"
echo "Java compatibility: Java 17"
echo "Android Gradle Plugin compatibility: 8.1.2"

# Create gradle wrapper directory if it doesn't exist
mkdir -p gradle/wrapper

# Download gradle-wrapper.jar for Gradle 8.14.1 if it doesn't exist or is corrupted
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ] || [ ! -s "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "Downloading gradle-wrapper.jar for Gradle 8.14.1..."
    
    # Try multiple sources for gradle-wrapper.jar
    WRAPPER_URLS=(
        "https://github.com/gradle/gradle/raw/v8.14.1/gradle/wrapper/gradle-wrapper.jar"
        "https://services.gradle.org/distributions/gradle-8.14.1-wrapper.jar"
        "https://github.com/gradle/gradle/raw/v8.14/gradle/wrapper/gradle-wrapper.jar"
    )
    
    DOWNLOAD_SUCCESS=false
    for url in "${WRAPPER_URLS[@]}"; do
        echo "Trying to download from: $url"
        if curl -L -f -o gradle/wrapper/gradle-wrapper.jar "$url"; then
            echo "✅ Successfully downloaded gradle-wrapper.jar from $url"
            DOWNLOAD_SUCCESS=true
            break
        else
            echo "❌ Failed to download from $url"
        fi
    done
    
    if [ "$DOWNLOAD_SUCCESS" = false ]; then
        echo "❌ Failed to download gradle-wrapper.jar from all sources"
        echo "Will attempt to use system gradle instead"
        
        # Create a minimal gradle-wrapper.properties for system gradle fallback
        cat > gradle/wrapper/gradle-wrapper.properties << EOF
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.1-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
        echo "⚠️  Created gradle-wrapper.properties for system gradle fallback"
        exit 0
    fi
else
    echo "✅ gradle-wrapper.jar already exists and is not empty"
fi

# Verify gradle-wrapper.jar is valid
if [ -f "gradle/wrapper/gradle-wrapper.jar" ] && [ -s "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "✅ gradle-wrapper.jar is present and not empty"
else
    echo "❌ gradle-wrapper.jar is missing or empty"
    exit 1
fi

# Create or update gradle-wrapper.properties for Gradle 8.14.1
echo "Creating gradle-wrapper.properties for Gradle 8.14.1..."
cat > gradle/wrapper/gradle-wrapper.properties << EOF
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.1-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
echo "✅ Created gradle-wrapper.properties for Gradle 8.14.1"

# Make gradlew executable if it exists
if [ -f "gradlew" ]; then
    chmod +x gradlew
    echo "✅ Made gradlew executable"
    
    # Test gradlew functionality
    echo "Testing gradlew functionality..."
    if ./gradlew --version >/dev/null 2>&1; then
        echo "✅ gradlew is working correctly"
        ./gradlew --version | head -3
    else
        echo "⚠️  gradlew exists but may not be fully functional"
        echo "This may be normal if Gradle distribution needs to be downloaded"
    fi
else
    echo "⚠️  gradlew file not found in project root"
    echo "You may need to run 'gradle wrapper' to generate gradlew files"
fi

# Create gradlew files if they don't exist (for projects without wrapper)
if [ ! -f "gradlew" ] && command -v gradle >/dev/null 2>&1; then
    echo "Attempting to generate gradlew using system gradle..."
    if gradle wrapper --gradle-version 8.14.1; then
        chmod +x gradlew
        echo "✅ Generated gradlew using system gradle"
    else
        echo "❌ Failed to generate gradlew using system gradle"
    fi
fi

echo "=== GRADLE WRAPPER SETUP SUMMARY ==="
echo "Gradle wrapper directory contents:"
ls -la gradle/wrapper/ 2>/dev/null || echo "gradle/wrapper directory not accessible"

if [ -f "gradlew" ]; then
    echo "✅ gradlew file present and executable"
else
    echo "⚠️  gradlew file not present"
fi

if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "✅ gradle-wrapper.jar present ($(stat -f%z gradle/wrapper/gradle-wrapper.jar 2>/dev/null || stat -c%s gradle/wrapper/gradle-wrapper.jar 2>/dev/null || echo "unknown size") bytes)"
else
    echo "❌ gradle-wrapper.jar not present"
fi

if [ -f "gradle/wrapper/gradle-wrapper.properties" ]; then
    echo "✅ gradle-wrapper.properties present"
    echo "Configured Gradle version: $(grep 'distributionUrl' gradle/wrapper/gradle-wrapper.properties | sed 's/.*gradle-\([0-9.]*\)-.*/\1/')"
else
    echo "❌ gradle-wrapper.properties not present"
fi

echo "✅ Gradle wrapper setup completed"