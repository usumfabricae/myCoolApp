# CI/CD Build Automation - Quick Reference

## Overview

The CI/CD pipeline now includes intelligent caching for OpenCV libraries, reducing build times by 20-30% after the first build.

## How It Works

### First Build (Cache Miss)
```
1. Pipeline checks for cached OpenCV libraries
2. No cache found → Downloads OpenCV SDK (~250 MB)
3. Extracts and sets up libraries (~2-3 minutes)
4. Caches libraries for future builds
5. Continues with build process
```

### Subsequent Builds (Cache Hit)
```
1. Pipeline checks for cached OpenCV libraries
2. Cache found and valid → Skips download
3. Uses cached libraries (~5-10 seconds)
4. Continues with build process
```

## Build Time Comparison

| Build Type | OpenCV Setup | Total Build | Bandwidth |
|------------|--------------|-------------|-----------|
| First Build | 2-3 min | 8-10 min | ~250 MB |
| Cached Build | 5-10 sec | 6-7 min | ~0 MB |
| **Improvement** | **95% faster** | **20-30% faster** | **100% saved** |

## Monitoring Cache Status

### Check Build Logs

Look for these messages in the "Download and setup OpenCV Android SDK" step:

**Cache Hit** (Good):
```
✅ OpenCV libraries found in cache - skipping download
✅ Using cached OpenCV libraries - download skipped
✅ OpenCV libraries ready for build (cached: true)
```

**Cache Miss** (Expected on first build):
```
ℹ️  No cached OpenCV libraries found - will download
✅ OpenCV setup script completed successfully
✅ OpenCV libraries ready for build (cached: false)
```

**Cache Incomplete** (Rare):
```
⚠️  Cached OpenCV libraries incomplete - will download
```

## Common Scenarios

### Scenario 1: First Build on New Branch
**Expected**: Cache miss, downloads OpenCV
**Action**: None required, this is normal

### Scenario 2: Regular Development Builds
**Expected**: Cache hit, uses cached libraries
**Action**: None required, enjoy faster builds!

### Scenario 3: Cache Cleared Manually
**Expected**: Cache miss, re-downloads OpenCV
**Action**: None required, cache will be repopulated

### Scenario 4: OpenCV Version Update
**Expected**: Cache miss (different version)
**Action**: Update version in `scripts/setup-opencv.sh`

## Troubleshooting

### Problem: Every Build Downloads OpenCV

**Possible Causes**:
- Cache not enabled in Codemagic settings
- Previous build failed before caching
- Cache paths incorrect in `codemagic.yaml`

**Solution**:
1. Check Codemagic cache settings
2. Verify `codemagic.yaml` cache paths
3. Ensure previous build completed successfully

### Problem: Build Fails with "OpenCV setup failed"

**Possible Causes**:
- Corrupted cache
- Network issues during download
- Incomplete previous build

**Solution**:
1. Clear cache in Codemagic dashboard
2. Trigger new build
3. Check network connectivity logs

### Problem: Build Slower Than Expected

**Possible Causes**:
- Cache miss (first build or after clear)
- Network congestion
- Codemagic infrastructure issues

**Solution**:
1. Check if cache was hit in logs
2. Verify subsequent builds are faster
3. Contact Codemagic support if persistent

## Manual Cache Management

### Clear Cache
1. Go to Codemagic dashboard
2. Select your app
3. Go to Settings → Build settings
4. Click "Clear cache"
5. Trigger new build

### Verify Cache
1. Trigger a build
2. Check logs for cache status messages
3. Compare build times with previous builds

## Performance Metrics

Track these metrics to monitor cache effectiveness:

1. **Cache Hit Rate**: % of builds using cache
   - Target: >80% after initial builds

2. **Average Build Time**: Total build duration
   - Target: 6-7 minutes (with cache)

3. **OpenCV Setup Time**: Time for OpenCV step
   - Target: 5-10 seconds (with cache)

4. **Bandwidth Usage**: Data downloaded per build
   - Target: ~0 MB (with cache)

## Best Practices

### For Developers

1. **Don't Clear Cache Unnecessarily**: Let it work automatically
2. **Monitor First Build**: Ensure it completes successfully
3. **Report Issues**: If cache isn't working, report to team
4. **Update Documentation**: Keep this guide current

### For CI/CD Maintenance

1. **Monitor Cache Hit Rate**: Track in build logs
2. **Review Failed Builds**: Check if cache-related
3. **Update Cache Paths**: If project structure changes
4. **Test Cache Invalidation**: Periodically verify it works

## Configuration Files

### Primary Configuration
- **File**: `codemagic.yaml`
- **Section**: `common_cache` → `cache_paths`
- **Paths**:
  - `opencv/src/main/jniLibs` (native libraries)
  - `opencv/src/main/java` (Java sources)

### Setup Scripts
- **Primary**: `scripts/setup-opencv.sh`
- **Fallback**: `scripts/download-opencv-libs-only.sh`

### Documentation
- **Detailed Guide**: `CICD_CACHING_GUIDE.md`
- **Implementation Summary**: `CICD_BUILD_AUTOMATION_SUMMARY.md`
- **This Guide**: `CICD_QUICK_REFERENCE.md`

## Validation

### Validate Configuration
```bash
# Run validation script
./scripts/validate-cicd-caching.sh

# Expected output:
# ✅ All critical validations passed
```

### Test Cache Behavior
```bash
# Trigger first build (cache miss)
git commit --allow-empty -m "Test cache miss"
git push origin main

# Wait for build to complete

# Trigger second build (cache hit)
git commit --allow-empty -m "Test cache hit"
git push origin main

# Compare build times in Codemagic dashboard
```

## Support

### Questions or Issues?

1. **Check Documentation**:
   - `CICD_CACHING_GUIDE.md` (detailed guide)
   - `CICD_BUILD_AUTOMATION_SUMMARY.md` (implementation details)

2. **Review Build Logs**:
   - Look for cache-related messages
   - Check for error messages

3. **Run Validation**:
   ```bash
   ./scripts/validate-cicd-caching.sh
   ```

4. **Contact Team**:
   - Report issues with build logs
   - Include cache status messages
   - Provide build IDs for investigation

## Updates and Maintenance

### When to Update

- OpenCV version changes
- Project structure changes
- Cache paths need adjustment
- Performance issues detected

### How to Update

1. Modify `codemagic.yaml` cache configuration
2. Update setup scripts if needed
3. Test with a build
4. Update documentation
5. Notify team of changes

## Related Documentation

- [CI/CD Caching Guide](CICD_CACHING_GUIDE.md) - Detailed implementation
- [CI/CD Build Automation Summary](CICD_BUILD_AUTOMATION_SUMMARY.md) - Complete overview
- [OpenCV Setup Guide](OPENCV_SETUP_GUIDE.md) - OpenCV configuration
- [Build System Validation](BUILD_SYSTEM_VALIDATION_REPORT.md) - Validation procedures

---

**Last Updated**: December 2025
**Version**: 1.0
**Status**: Production Ready
