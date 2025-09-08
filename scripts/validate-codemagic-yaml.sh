#!/bin/bash
# Script to validate Codemagic YAML structure

echo "=== VALIDATING CODEMAGIC YAML STRUCTURE ==="

YAML_FILE="codemagic.yaml"

if [ ! -f "$YAML_FILE" ]; then
    echo "❌ codemagic.yaml not found"
    exit 1
fi

echo "✅ codemagic.yaml found"

# Check for common structure issues
echo ""
echo "=== CHECKING FOR COMMON ISSUES ==="

# Check for artifacts under publishing (should not exist)
if grep -A 5 "publishing:" "$YAML_FILE" | grep -q "artifacts:"; then
    echo "❌ Found 'artifacts:' under 'publishing:' - this is not allowed"
    echo "   Artifacts should be at workflow level, not under publishing"
else
    echo "✅ No artifacts found under publishing sections"
fi

# Check for proper workflow-level artifacts
WORKFLOW_ARTIFACTS=$(grep -c "^    artifacts:" "$YAML_FILE")
echo "✅ Found $WORKFLOW_ARTIFACTS workflow-level artifacts sections"

# Check for email configuration
EMAIL_CONFIGS=$(grep -c "recipients:" "$YAML_FILE")
echo "✅ Found $EMAIL_CONFIGS email configurations"

# Check for proper indentation (basic check)
if grep -q "^  [^ ]" "$YAML_FILE"; then
    echo "⚠️  Potential indentation issues found (2-space indents detected)"
else
    echo "✅ Indentation looks consistent"
fi

# Check for required sections
if grep -q "workflows:" "$YAML_FILE"; then
    echo "✅ workflows section found"
else
    echo "❌ workflows section missing"
fi

if grep -q "android-development-workflow:" "$YAML_FILE"; then
    echo "✅ android-development-workflow found"
else
    echo "❌ android-development-workflow missing"
fi

echo ""
echo "=== VALIDATION COMPLETE ==="
echo "If no errors (❌) are shown above, the YAML structure should be valid."