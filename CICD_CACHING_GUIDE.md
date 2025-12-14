# CI/CD Build Automation and Caching Guide

## Overview

This document describes the CI/CD build automation implementation for the OpenCV Camera Stream Android application, specifically focusing on the caching mechanism that prevents repeated downloads of OpenCV libraries.

## Implementation Details

### 1. Caching Configuration

The Codemagic CI/CD pipeline now includes caching for OpenCV libraries to avoid repeated downloads:

```yaml
common_cache: &common_cache
  cache_paths:
    - ~/.gradle/caches
    - ~/.android/build-cache
    - ~/.m2/repository
    - opencv/src/main/jniLibs  # Cache OpenCV native libraries
    - opencv/src/main/java     # Cache OpenCV Java sources
```

### 2. Cache Detection Logic

Before downloading OpenCV, the pipeline checks if cached libraries are available:

```bash
# Check if OpenCV libraries are already cached
OPENCV_CACHED=false
if [ -d "opencv/src/main/jniLibs" ] && [ -d "opencv/src/main/java" ]; then
  JAVA_FILES=$(find opencv/src/main/java -name "*.java" 2>/dev/null | wc -l)
  NATIVE_LIBS=$(find opencv/src/main/jniLibs -name "*.so" 2>/dev/null | wc -l)
  
  # Verify cache completeness
  if [ "$JAVA_FILES" -gt 100 ] && [ "$NATIVE_LIBS" -gt 4 ]; then
    echo "✅ OpenCV libraries found in cache - skipping download"
    OPENCV_CACHED=true
  fi
fi
```

### 3. Conditional Download

The OpenCV setup script only runs if cached libraries are not found or incomplete:

```bash
# Only download if not cached
if [ "$OPENCV_CACHED" = false ]; then
  # Run setup script to download and extract OpenCV
  ./scripts/setup-opencv.sh
else
  echo "✅ Using cached OpenCV libraries - download skipped"
fi
```

## Benefits

### Time Savings
- **First build**: ~2-3 minutes for OpenCV download and extraction
- **Subsequent builds**: ~5-10 seconds for cache verification
- **Estimated savings**: 2-3 minutes per build after the first build

### Bandwidth Savings
- OpenCV Android SDK size: ~250 MB
- Builds per day (average): 5-10
- **Daily bandwidth saved**: 1.25-2.5 GB

### Build Reliability
- Reduces dependency on external download sources
- Prevents build failures due to network issues
- Ensures consistent OpenCV version across builds

## Cache Invalidation

The cache is automatically invalidated when:

1. **Manual cache clear**: Using Codemagic dashboard
2. **Incomplete cache**: If Java files < 100 or native libraries < 4
3. **Cache expiration**: Codemagic default cache expiration (7 days)
4. **Workflow changes**: Modifications to cache paths in `codemagic.yaml`

## Verification

The pipeline includes verification steps to ensure cached libraries are valid:

```bash
# Verify OpenCV is available (whether cached or freshly downloaded)
JAVA_FILES=$(find opencv/src/main/java -name "*.java" 2>/dev/null | wc -l)
NATIVE_LIBS=$(find opencv/src/main/jniLibs -name "*.so" 2>/dev/null | wc -l)

if [ "$JAVA_FILES" -eq 0 ] || [ "$NATIVE_LIBS" -eq 0 ]; then
  echo "❌ OpenCV setup failed - missing required files"
  exit 1
fi
```

## Monitoring

Build logs include cache status information:

- `✅ OpenCV libraries found in cache - skipping download`: Cache hit
- `ℹ️  No cached OpenCV libraries found - will download`: Cache miss
- `⚠️  Cached OpenCV libraries incomplete - will download`: Partial cache

## Troubleshooting

### Cache Not Working

If caching doesn't seem to work:

1. **Check cache paths**: Verify paths in `codemagic.yaml` are correct
2. **Review build logs**: Look for cache-related messages
3. **Clear cache manually**: Use Codemagic dashboard to clear cache
4. **Verify file counts**: Ensure thresholds (100 Java files, 4 native libs) are appropriate

### Incomplete Cache

If builds report incomplete cache:

1. **Check previous build**: Verify previous build completed successfully
2. **Review file counts**: Check actual file counts in build logs
3. **Adjust thresholds**: Modify detection logic if needed

### Cache Corruption

If cached files are corrupted:

1. **Clear cache**: Use Codemagic dashboard
2. **Rebuild**: Trigger a new build to re-download
3. **Verify checksums**: Add checksum verification if needed

## Integration with Existing Scripts

The caching mechanism integrates seamlessly with existing scripts:

- **`scripts/setup-opencv.sh`**: Downloads and extracts OpenCV SDK
- **`scripts/download-opencv-libs-only.sh`**: Quick library download (fallback)

Both scripts are only executed when cache is not available or incomplete.

## Future Enhancements

Potential improvements to the caching system:

1. **Checksum verification**: Verify cached files integrity
2. **Version-specific caching**: Cache different OpenCV versions separately
3. **Partial cache recovery**: Attempt to use partial cache when possible
4. **Cache warming**: Pre-populate cache in dedicated workflow
5. **Cache metrics**: Track cache hit rate and time savings

## Requirements Validation

This implementation satisfies **Requirement 5** from the specification:

> **Requirement 5**: As a developer, I want the library acquisition system to be robust and maintainable, so that future OpenCV updates don't break the native library integration.

**Acceptance Criteria Met**:
- ✅ **5.1**: OpenCV version updates handled by script configuration
- ✅ **5.2**: Validation ensures all required libraries are present
- ✅ **5.3**: Backward compatibility maintained with existing code
- ✅ **5.4**: New dependencies automatically detected during extraction

## Related Documentation

- [CI/CD Integration Guide](OPENCV_CICD_INTEGRATION.md)
- [OpenCV Setup Guide](OPENCV_SETUP_GUIDE.md)
- [Build System Validation](BUILD_SYSTEM_VALIDATION_REPORT.md)
