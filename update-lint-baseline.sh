#!/bin/bash
# Script to update lint baseline files

echo "Updating lint baseline files..."

# Update OpenCV module baseline
./gradlew :opencv:updateLintBaseline

# Update app module baseline  
./gradlew :app:updateLintBaseline

echo "Lint baseline files updated successfully!"