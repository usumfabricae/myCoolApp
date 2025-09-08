#!/bin/bash
# Script to verify build outputs exist before artifact collection

echo "=== VERIFYING BUILD OUTPUTS ==="

# Check for APK files
echo "Searching for APK files..."
APK_COUNT=$(find . -name "*.apk" -type f | wc -l)
echo "APK files found: $APK_COUNT"

if [ "$APK_COUNT" -gt 0 ]; then
    echo "APK files:"
    find . -name "*.apk" -type f -exec ls -lh {} \;
else
    echo "❌ No APK files found!"
    echo "Checking build/outputs directory structure:"
    find . -path "*/build/outputs*" -type d | head -10
fi

# Check for test results
echo ""
echo "Searching for test results..."
TEST_COUNT=$(find . -name "TEST-*.xml" -type f | wc -l)
echo "Test result files found: $TEST_COUNT"

# Check for lint results
echo ""
echo "Searching for lint results..."
LINT_COUNT=$(find . -name "lint-results*.xml" -o -name "lint-results*.html" | wc -l)
echo "Lint result files found: $LINT_COUNT"

# Check for coverage reports
echo ""
echo "Searching for coverage reports..."
COVERAGE_COUNT=$(find . -path "*/jacoco*" -name "*.xml" -o -path "*/jacoco*" -name "*.html" | wc -l)
echo "Coverage report files found: $COVERAGE_COUNT"

# Summary
echo ""
echo "=== BUILD OUTPUT SUMMARY ==="
echo "APK files: $APK_COUNT"
echo "Test results: $TEST_COUNT"
echo "Lint results: $LINT_COUNT"
echo "Coverage reports: $COVERAGE_COUNT"

TOTAL_FILES=$((APK_COUNT + TEST_COUNT + LINT_COUNT + COVERAGE_COUNT))
echo "Total artifact files: $TOTAL_FILES"

if [ "$TOTAL_FILES" -eq 0 ]; then
    echo "❌ No build artifacts found! Build may have failed."
    exit 1
else
    echo "✅ Build artifacts found successfully!"
    exit 0
fi