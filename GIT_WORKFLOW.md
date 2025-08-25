# Git Workflow and CI/CD Integration

This document describes the git workflow and CI/CD integration for the Android Camera OpenCV Stream project.

## Overview

All builds, tests, and deployments are performed exclusively through Codemagic CI/CD platform. Local builds and tests are not permitted to ensure consistency and compliance with project requirements.

## Branch Strategy

### Main Branches
- **main**: Production-ready code
  - Triggers: Full build and test workflow
  - Protection: Requires PR approval and passing CI checks
  - Deployment: Automatic artifact generation

- **develop**: Integration branch for ongoing development
  - Triggers: Development workflow (build + test)
  - Used for: Feature integration and testing

### Supporting Branches
- **feature/***: Feature development branches
  - Triggers: Test-only workflow on PR creation
  - Naming: `feature/task-description` (e.g., `feature/camera-permission-handling`)
  - Lifecycle: Created from develop, merged back to develop

- **hotfix/***: Emergency fixes
  - Triggers: Same workflow as features
  - Naming: `hotfix/issue-description`
  - Lifecycle: Created from main, merged to both main and develop

## Workflow Triggers

### Development Workflow
**Triggered by**: Push to main, develop, or feature/* branches
**Includes**:
- Compile debug APK
- Execute unit tests
- Run Android lint checks
- Generate test and lint reports
- Android 10 compatibility validation

### Release Workflow
**Triggered by**: Version tags (v*.*.*)
**Includes**:
- Run comprehensive test suite
- Build release APK
- Generate release artifacts and build metadata
- Email notifications

### Test-Only Workflow
**Triggered by**: Pull requests
**Includes**:
- Execute unit tests with coverage reporting
- Run lint checks
- Android 10 compatibility validation
- Generate coverage reports

## Required Git Operations

### 1. Committing Changes
```bash
# Stage your changes
git add .

# Commit with descriptive message
git commit -m "feat: implement camera permission handling with Android 10 compliance

- Add PermissionHandler class with Android 10 privacy features
- Implement permission rationale dialogs
- Add comprehensive unit tests for permission scenarios
- Update MainActivity to integrate permission handling

Addresses requirements: 1.1, 4.1, 6.2, 6.4"
```

### 2. Pushing Changes
```bash
# Push to trigger CI/CD workflow
git push origin feature/camera-permission-handling
```

### 3. Creating Pull Requests
1. Push feature branch to repository
2. Create PR from feature branch to develop
3. CI/CD automatically triggers test-only workflow
4. Review and merge after CI passes

### 4. Release Process
```bash
# Create and push version tag
git tag -a v1.0.0 -m "Release version 1.0.0 - Initial camera streaming functionality"
git push origin v1.0.0
```

## Commit Message Format

Use conventional commit format for consistency:

```
<type>(<scope>): <description>

<body>

<footer>
```

### Types
- **feat**: New feature
- **fix**: Bug fix
- **docs**: Documentation changes
- **style**: Code style changes (formatting, etc.)
- **refactor**: Code refactoring
- **test**: Adding or updating tests
- **chore**: Build process or auxiliary tool changes

### Examples
```bash
git commit -m "feat(camera): implement Camera2 API integration

- Add CameraManager class with Camera2 API setup
- Implement camera device state callbacks
- Set up ImageReader for frame capture
- Add proper camera resource cleanup

Addresses requirements: 1.2, 1.4, 4.2, 5.4"

git commit -m "test(permissions): add Android 10 permission handling tests

- Add unit tests for PermissionHandler class
- Test Android 10 specific permission scenarios
- Add integration tests for MainActivity permission flow

Addresses requirement: 7.2"

git commit -m "fix(opencv): resolve memory leak in frame processing

- Fix buffer cleanup in OpenCVProcessor
- Add proper Mat disposal in processing pipeline
- Update unit tests to verify memory management

Addresses requirement: 5.2"
```

## CI/CD Validation

### Automatic Checks
All pushes and PRs automatically validate:
- ✅ Target SDK 29 (Android 10) compliance
- ✅ Camera permission declaration in manifest
- ✅ Unit test execution and coverage
- ✅ Android lint checks
- ✅ Build compilation success

### Artifacts Generated
- APK files (debug/release)
- Test reports (JUnit XML, HTML)
- Lint reports (XML, HTML)
- Code coverage reports (Jacoco)
- Build metadata and ProGuard mapping files

## Email Notifications

Configured recipients receive notifications for:
- ✅ Successful builds (development and release workflows)
- ❌ Failed builds (all workflows)
- 📊 Test results and coverage reports

## Best Practices

### Before Committing
1. Ensure code follows Android best practices
2. Add/update unit tests for new functionality
3. Verify Android 10 compliance requirements
4. Write descriptive commit messages

### Before Pushing
1. Rebase feature branch on latest develop
2. Ensure all local changes are committed
3. Verify branch naming follows convention

### Pull Request Guidelines
1. Create PR with descriptive title and description
2. Reference related requirements and tasks
3. Wait for CI/CD validation to pass
4. Address any review feedback
5. Squash commits if requested

## Troubleshooting

### Build Failures
1. Check Codemagic build logs for specific errors
2. Verify all dependencies are properly declared
3. Ensure Android 10 compatibility requirements are met
4. Check for lint errors and warnings

### Test Failures
1. Review test reports in CI artifacts
2. Ensure all tests are compatible with Codemagic environment
3. Verify mock configurations work in CI environment
4. Check for Android version compatibility issues

### Permission Issues
1. Verify camera permissions in AndroidManifest.xml
2. Ensure Android 10 privacy compliance
3. Check permission handling unit tests
4. Validate permission rationale messages

## Contact and Support

For CI/CD issues or questions:
1. Check Codemagic build logs and artifacts
2. Review this workflow documentation
3. Consult project requirements and design documents
4. Contact project maintainers if issues persist

---

**Remember**: All builds and tests must go through Codemagic. Local builds and tests are not permitted per project requirements 7.2 and 8.6.