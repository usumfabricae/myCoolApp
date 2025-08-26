#!/bin/bash

# Script to set up Gradle wrapper for Android project
# This downloads the gradle-wrapper.jar file needed for the wrapper to work

echo "Setting up Gradle wrapper..."

# Create gradle wrapper directory if it doesn't exist
mkdir -p gradle/wrapper

# Download gradle-wrapper.jar if it doesn't exist
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "Downloading gradle-wrapper.jar..."
    curl -L -o gradle/wrapper/gradle-wrapper.jar \
        https://github.com/gradle/gradle/raw/v8.0.0/gradle/wrapper/gradle-wrapper.jar
    
    if [ $? -eq 0 ]; then
        echo "✅ Successfully downloaded gradle-wrapper.jar"
    else
        echo "❌ Failed to download gradle-wrapper.jar"
        echo "Will use system gradle instead"
        exit 0
    fi
else
    echo "✅ gradle-wrapper.jar already exists"
fi

# Make gradlew executable
if [ -f "gradlew" ]; then
    chmod +x gradlew
    echo "✅ Made gradlew executable"
else
    echo "❌ gradlew file not found"
fi

echo "Gradle wrapper setup complete"