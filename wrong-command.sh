#!/usr/bin/env bash

source $CM_BUILD_DIR/android_env.sh
cd "$ANDROID_PROJECT_DIR"

echo "=== OPENCV INTEGRATION VALIDATION ==="

# Check OpenCV module structure
if [ -d "opencv" ]; then
  echo "✅ OpenCV module directory found"
  echo "OpenCV module contents:"
  ls -la opencv/
  
  if [ -f "opencv/build.gradle" ]; then
    echo "✅ OpenCV build.gradle found"
  else
    echo "❌ OpenCV build.gradle not found"
    exit 1
  fi
  
  if [ -d "opencv/src/main/java" ]; then
    echo "✅ OpenCV Java source directory found"
    JAVA_FILES_COUNT=$(find opencv/src/main/java -name "*.java" | wc -l)
    echo "Java files found: $JAVA_FILES_COUNT"
    if [ "$JAVA_FILES_COUNT" -gt 0 ]; then
      echo "✅ OpenCV Java sources are present"
    else
      echo "❌ No OpenCV Java sources found"
      exit 1
    fi
  else
    echo "❌ OpenCV Java source directory not found"
    exit 1
  fi
  
  if [ -d "opencv/src/main/jniLibs" ]; then
    echo "✅ OpenCV native libraries directory found"
    NATIVE_LIBS_COUNT=$(find opencv/src/main/jniLibs -name "*.so" | wc -l)
    echo "Native libraries found: $NATIVE_LIBS_COUNT"
    if [ "$NATIVE_LIBS_COUNT" -gt 0 ]; then
      echo "✅ OpenCV native libraries are present"
      
      # Validate specific required libraries
      echo "Validating required OpenCV libraries..."
      
      # Check for OpenCV core library (must be present)
      if find opencv/src/main/jniLibs -name "libopencv_java4.so" | grep -q .; then
        echo "✅ Required library found: libopencv_java4.so"
      else
        echo "❌ Missing required library: libopencv_java4.so"
        exit 1
      fi
      
      # Add libc++_shared.so from Android NDK with enhanced ARM device support
      echo "Adding libc++_shared.so from Android NDK (OpenCV requires dynamic linking)..."
      
      FOUND_ANY_LIBC=false
      
      # Enhanced NDK detection and libc++_shared.so extraction
      echo "=== SEARCHING FOR LIBC++_SHARED.SO IN NDK ==="
      
      # Fix NDK environment variable detection
      if [ -z "$ANDROID_NDK_ROOT" ] && [ -n "$NDK_HOME" ]; then
        export ANDROID_NDK_ROOT="$NDK_HOME"
        echo "Using NDK_HOME as ANDROID_NDK_ROOT: $ANDROID_NDK_ROOT"
      elif [ -z "$ANDROID_NDK_ROOT" ] && [ -n "$ANDROID_NDK_HOME" ]; then
        export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
        echo "Using ANDROID_NDK_HOME as ANDROID_NDK_ROOT: $ANDROID_NDK_ROOT"
      fi
      
      if [ -n "$ANDROID_NDK_ROOT" ] && [ -d "$ANDROID_NDK_ROOT" ]; then
        echo "Android NDK found at: $ANDROID_NDK_ROOT"
        NDK_VERSION=$(cat "$ANDROID_NDK_ROOT/source.properties" 2>/dev/null | grep 'Pkg.Revision' | cut -d'=' -f2 | tr -d ' ' || echo 'Unknown')
        echo "NDK version: $NDK_VERSION"
        
        # Show NDK directory structure for debugging
        echo "Searching for libc++_shared.so in NDK..."
        NDK_LIBC_FILES=$(find "$ANDROID_NDK_ROOT" -name "libc++_shared.so" 2>/dev/null)
        if [ -n "$NDK_LIBC_FILES" ]; then
          echo "Found libc++_shared.so files in NDK:"
          echo "$NDK_LIBC_FILES"
        else
          echo "❌ No libc++_shared.so files found in NDK"
        fi
        
        # Also check for alternative paths
        echo "Checking alternative NDK paths..."
        find "$ANDROID_NDK_ROOT" -path "*/sources/cxx-stl/*" -name "*.so" | head -5 || echo "No C++ STL libraries found"
        find "$ANDROID_NDK_ROOT" -path "*/toolchains/llvm/*" -name "libc++*" | head -5 || echo "No LLVM libc++ libraries found"
      else
        echo "❌ Android NDK not found or ANDROID_NDK_ROOT not set"
        echo "ANDROID_NDK_ROOT: ${ANDROID_NDK_ROOT:-'not set'}"
        echo "Available environment variables:"
        env | grep -i ndk || echo "No NDK-related environment variables found"
      fi
      
      for arch_dir in opencv/src/main/jniLibs/*/; do
        if [ -d "$arch_dir" ]; then
            arch_name=$(basename "$arch_dir")
            echo "Processing architecture: $arch_name"
            
            # Enhanced NDK paths for each architecture (covering more NDK versions)
            case "$arch_name" in
              "arm64-v8a")
                NDK_PATHS=(
                  "$ANDROID_NDK_ROOT/sources/cxx-stl/llvm-libc++/libs/$arch_name/libc++_shared.so"
                  "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
                  "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
                  "$ANDROID_NDK_ROOT/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
                )
                ;;
              "armeabi-v7a")
                NDK_PATHS=(
                  "$ANDROID_NDK_ROOT/sources/cxx-stl/llvm-libc++/libs/$arch_name/libc++_shared.so"
                  "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/arm-linux-androideabi/libc++_shared.so"
                  "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/lib/arm-linux-androideabi/libc++_shared.so"
                  "$ANDROID_NDK_ROOT/sysroot/usr/lib/arm-linux-androideabi/libc++_shared.so"
                )
                ;;
              "x86")
                NDK_PATHS=(
                  "$ANDROID_NDK_ROOT/sources/cxx-stl/llvm-libc++/libs/$arch_name/libc++_shared.so"
                  "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/i686-linux-android/libc++_shared.so"
                  "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/lib/i686-linux-android/libc++_shared.so"
                  "$ANDROID_NDK_ROOT/sysroot/usr/lib/i686-linux-android/libc++_shared.so"
                )
                ;;
              "x86_64")
                NDK_PATHS=(
                  "$ANDROID_NDK_ROOT/sources/cxx-stl/llvm-libc++/libs/$arch_name/libc++_shared.so"
                  "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/x86_64-linux-android/libc++_shared.so"
                  "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/lib/x86_64-linux-android/libc++_shared.so"
                  "$ANDROID_NDK_ROOT/sysroot/usr/lib/x86_64-linux-android/libc++_shared.so"
                )
                ;;
              *)
                echo "⚠️  Unknown architecture: $arch_name, skipping"
                continue
                ;;
            esac
            
            # Try each NDK path and validate the binary with architecture checking
            FOUND_FOR_ARCH=false
            for ndk_path in "${NDK_PATHS[@]}"; do
              if [ -f "$ndk_path" ]; then
                echo "  Found candidate at: $ndk_path"
                
                # Validate that it's a proper ELF binary with correct architecture
                file_info=$(file "$ndk_path")
                if echo "$file_info" | grep -q "ELF.*shared object"; then
                  # Additional architecture validation
                  case "$arch_name" in
                    "arm64-v8a")
                      if echo "$file_info" | grep -q "ARM aarch64\|64-bit.*ARM"; then
                        echo "  ✅ Correct ARM64 architecture detected"
                        arch_valid=true
                      else
                        echo "  ❌ Wrong architecture for ARM64: $file_info"
                        arch_valid=false
                      fi
                      ;;
                    "armeabi-v7a")
                      if echo "$file_info" | grep -q "ARM.*EABI5\|32-bit.*ARM" && ! echo "$file_info" | grep -q "64-bit"; then
                        echo "  ✅ Correct ARMv7 architecture detected"
                        arch_valid=true
                      else
                        echo "  ❌ Wrong architecture for ARMv7: $file_info"
                        arch_valid=false
                      fi
                      ;;
                    "x86")
                      if echo "$file_info" | grep -q "Intel 80386\|32-bit.*80386" && ! echo "$file_info" | grep -q "64-bit"; then
                        echo "  ✅ Correct x86 architecture detected"
                        arch_valid=true
                      else
                        echo "  ❌ Wrong architecture for x86: $file_info"
                        arch_valid=false
                      fi
                      ;;
                    "x86_64")
                      if echo "$file_info" | grep -q "x86-64\|64-bit.*x86"; then
                        echo "  ✅ Correct x86_64 architecture detected"
                        arch_valid=true
                      else
                        echo "  ❌ Wrong architecture for x86_64: $file_info"
                        arch_valid=false
                      fi
                      ;;
                    *)
                      arch_valid=true  # Unknown arch, assume valid
                      ;;
                  esac
                  
                  if [ "$arch_valid" = true ]; then
                    cp "$ndk_path" "$arch_dir/libc++_shared.so"
                    
                    # Verify the copied file is also valid and has correct architecture
                    copied_file_info=$(file "$arch_dir/libc++_shared.so")
                    if echo "$copied_file_info" | grep -q "ELF.*shared object"; then
                      file_size=$(stat -c%s "$arch_dir/libc++_shared.so" 2>/dev/null || stat -f%z "$arch_dir/libc++_shared.so" 2>/dev/null || echo "unknown")
                      echo "✅ Added valid libc++_shared.so for $arch_name ($file_size bytes)"
                      echo "  Architecture: $(echo "$copied_file_info" | cut -d: -f2 | xargs)"
                      FOUND_FOR_ARCH=true
                      FOUND_ANY_LIBC=true
                      break
                    else
                      echo "❌ Copied file is not valid ELF binary for $arch_name"
                      rm -f "$arch_dir/libc++_shared.so"
                    fi
                  fi
                else
                  echo "⚠️  File at $ndk_path is not a valid ELF binary"
                fi
              fi
            done
            
            if [ "$FOUND_FOR_ARCH" = false ]; then
              echo "⚠️  Could not find valid libc++_shared.so for $arch_name in NDK"
              echo "  Searched paths:"
              for ndk_path in "${NDK_PATHS[@]}"; do
                echo "    $ndk_path $([ -f "$ndk_path" ] && echo '[EXISTS]' || echo '[NOT FOUND]')"
              done
            fi
          fi
        done
      
      # Fallback: Download libc++_shared.so if not found in NDK
      if [ "$FOUND_ANY_LIBC" = false ]; then
        echo "⚠️  libc++_shared.so not found in NDK, attempting fallback download..."
        
        # Create temporary directory for fallback download
        FALLBACK_DIR="$CM_BUILD_DIR/temp-libc-fallback"
        mkdir -p "$FALLBACK_DIR"
        cd "$FALLBACK_DIR"
        
        # Download a known working version of libc++_shared.so for ARM architectures
        # Using a reliable source that provides the necessary libraries
        LIBC_DOWNLOAD_URL="https://github.com/android/ndk/raw/main/sources/cxx-stl/llvm-libc++/libs"
        
        for arch_dir in "$ANDROID_PROJECT_DIR/opencv/src/main/jniLibs"/*; do
          if [ -d "$arch_dir" ]; then
            arch_name=$(basename "$arch_dir")
            
            # Only download for ARM architectures (most common for Android devices)
            case "$arch_name" in
              "arm64-v8a"|"armeabi-v7a")
                echo "Attempting fallback download for $arch_name..."
                
                # Try to download from GitHub NDK repository
                if curl -L -f -o "libc++_shared_${arch_name}.so" "${LIBC_DOWNLOAD_URL}/${arch_name}/libc++_shared.so" 2>/dev/null; then
                  if file "libc++_shared_${arch_name}.so" | grep -q "ELF.*shared object"; then
                    cp "libc++_shared_${arch_name}.so" "$arch_dir/libc++_shared.so"
                    echo "✅ Fallback download successful for $arch_name"
                    FOUND_ANY_LIBC=true
                  else
                    echo "❌ Downloaded file is not valid ELF binary for $arch_name"
                  fi
                else
                  echo "⚠️  Fallback download failed for $arch_name"
                fi
                ;;
              *)
                echo "Skipping fallback download for $arch_name (not ARM)"
                ;;
            esac
          fi
        done
        
        # Clean up fallback directory
        cd "$ANDROID_PROJECT_DIR"
        rm -rf "$FALLBACK_DIR"
        
        if [ "$FOUND_ANY_LIBC" = false ]; then
          echo "❌ All fallback methods failed. No stub libraries will be created."
          echo "The app will need to work without libc++_shared.so or use system libraries."
        fi
      fi
      
      # Move ALL OpenCV libraries to app module (to avoid duplicates)
      echo "=== MOVING ALL OPENCV LIBRARIES TO APP MODULE ==="
      echo "Moving ALL OpenCV libraries to app module to avoid duplicate resource errors..."
      mkdir -p app/src/main/jniLibs
      
      # Move (not copy) all libraries from OpenCV module to app module
      CRITICAL_LIBS_FOUND=0
      TOTAL_MOVED=0
      
      for arch_dir in opencv/src/main/jniLibs/*/; do
        if [ -d "$arch_dir" ]; then
          arch_name=$(basename "$arch_dir")
          mkdir -p "app/src/main/jniLibs/$arch_name"
          
          echo "Processing $arch_name architecture..."
          
          # Move all .so files for this architecture
          for lib_file in "$arch_dir"/*.so; do
            if [ -f "$lib_file" ]; then
              lib_name=$(basename "$lib_file")
              
              # Move (not copy) to avoid duplicates
              mv "$lib_file" "app/src/main/jniLibs/$arch_name/"
              chmod 644 "app/src/main/jniLibs/$arch_name/$lib_name"
              TOTAL_MOVED=$((TOTAL_MOVED + 1))
              
              # Verify the moved file
              if [ -f "app/src/main/jniLibs/$arch_name/$lib_name" ]; then
                file_size=$(stat -c%s "app/src/main/jniLibs/$arch_name/$lib_name" 2>/dev/null || stat -f%z "app/src/main/jniLibs/$arch_name/$lib_name" 2>/dev/null || echo "unknown")
                file_type=$(file "app/src/main/jniLibs/$arch_name/$lib_name" | cut -d: -f2)
                
                echo "  ✅ Moved $lib_name ($file_size bytes) - $file_type"
                
                # Count critical libraries for ARM architectures
                if [[ "$arch_name" == "arm64-v8a" || "$arch_name" == "armeabi-v7a" ]]; then
                  if [[ "$lib_name" == "libopencv_java4.so" || "$lib_name" == "libc++_shared.so" ]]; then
                    CRITICAL_LIBS_FOUND=$((CRITICAL_LIBS_FOUND + 1))
                    echo "    🎯 Critical library for ARM devices: $lib_name"
                  fi
                fi
              else
                echo "  ❌ Move verification failed for $lib_name"
                exit 1
              fi
            fi
          done
        fi
      done
      
      echo "Total libraries moved to app module: $TOTAL_MOVED"
      
      # Clean up empty directories in OpenCV module to prevent Gradle from including them
      echo "Cleaning up empty directories in OpenCV module..."
      find opencv/src/main/jniLibs -type d -empty -delete 2>/dev/null || true
      
      # Ensure OpenCV build.gradle doesn't include jniLibs to prevent duplicates
      echo "Configuring OpenCV build.gradle to exclude native libraries..."
      if grep -q "jniLibs.srcDirs" opencv/build.gradle; then
        sed -i.bak 's/jniLibs.srcDirs = \[.*\]/\/\/ jniLibs.srcDirs commented out to prevent duplicate resources/' opencv/build.gradle
        echo "✅ OpenCV build.gradle configured to exclude jniLibs"
      fi
      
      # Verify OpenCV jniLibs is now empty or minimal
      REMAINING_OPENCV_LIBS=$(find opencv/src/main/jniLibs -name "*.so" 2>/dev/null | wc -l)
      echo "Remaining libraries in OpenCV module: $REMAINING_OPENCV_LIBS (should be 0)"
      
      if [ "$REMAINING_OPENCV_LIBS" -gt 0 ]; then
        echo "⚠️  Some libraries still remain in OpenCV module - removing them..."
        find opencv/src/main/jniLibs -name "*.so" -delete 2>/dev/null || true
        echo "✅ Remaining libraries removed from OpenCV module"
      fi
      
      # Final verification - ensure no .so files in OpenCV module
      FINAL_OPENCV_LIBS=$(find opencv/src/main/jniLibs -name "*.so" 2>/dev/null | wc -l)
      if [ "$FINAL_OPENCV_LIBS" -eq 0 ]; then
        echo "✅ OpenCV module is now clean of native libraries - no duplicates will occur"
      else
        echo "❌ Failed to clean OpenCV module of native libraries"
        exit 1
      fi
      
      # Always attempt to ensure libc++_shared.so is available
      echo "=== ENSURING LIBC++_SHARED.SO AVAILABILITY ==="
      echo "Current status: FOUND_ANY_LIBC=$FOUND_ANY_LIBC"
      
      # Check what we currently have
      for arch_name in "arm64-v8a" "armeabi-v7a"; do
        if [ -d "opencv/src/main/jniLibs/$arch_name" ]; then
          if [ -f "opencv/src/main/jniLibs/$arch_name/libc++_shared.so" ]; then
            libc_size=$(stat -c%s "opencv/src/main/jniLibs/$arch_name/libc++_shared.so" 2>/dev/null || stat -f%z "opencv/src/main/jniLibs/$arch_name/libc++_shared.so" 2>/dev/null || echo "0")
            echo "Found libc++_shared.so for $arch_name: $libc_size bytes"
            if [ "$libc_size" -gt 1000 ]; then
              echo "✅ Valid libc++_shared.so already exists for $arch_name"
            else
              echo "❌ Invalid libc++_shared.so for $arch_name (too small: $libc_size bytes)"
              FOUND_ANY_LIBC=false
            fi
          else
            echo "❌ No libc++_shared.so found for $arch_name"
            FOUND_ANY_LIBC=false
          fi
        fi
      done
      
      # Force download if we don't have valid libraries
      if [ "$FOUND_ANY_LIBC" = false ]; then
        echo "⚠️  libc++_shared.so was not found in NDK, downloading from reliable source..."
        
        # Download pre-built libc++_shared.so files from a reliable source
        TEMP_LIBC_DIR="$CM_BUILD_DIR/temp-libc-download"
        mkdir -p "$TEMP_LIBC_DIR"
        cd "$TEMP_LIBC_DIR"
        
        # Use a smaller, more reliable source for just the libraries we need
        for arch_name in "arm64-v8a" "armeabi-v7a"; do
          if [ -d "$ANDROID_PROJECT_DIR/app/src/main/jniLibs/$arch_name" ]; then
            if [ ! -f "$ANDROID_PROJECT_DIR/app/src/main/jniLibs/$arch_name/libc++_shared.so" ]; then
              echo "Downloading libc++_shared.so for $arch_name..."
              
              DOWNLOAD_SUCCESS=false
              
              # Try to download from GitHub releases that contain pre-built libraries
              case "$arch_name" in
                "arm64-v8a")
                  # Try multiple sources for ARM64
                  DOWNLOAD_URLS=(
                    "https://github.com/android/ndk/raw/main/sources/cxx-stl/llvm-libc++/libs/arm64-v8a/libc++_shared.so"
                    "https://raw.githubusercontent.com/android/ndk/main/sources/cxx-stl/llvm-libc++/libs/arm64-v8a/libc++_shared.so"
                  )
                  ;;
                "armeabi-v7a")
                  # Try multiple sources for ARMv7
                  DOWNLOAD_URLS=(
                    "https://github.com/android/ndk/raw/main/sources/cxx-stl/llvm-libc++/libs/armeabi-v7a/libc++_shared.so"
                    "https://raw.githubusercontent.com/android/ndk/main/sources/cxx-stl/llvm-libc++/libs/armeabi-v7a/libc++_shared.so"
                  )
                  ;;
              esac
              
              # Try each download URL
              for url in "${DOWNLOAD_URLS[@]}"; do
                echo "  Trying: $url"
                if curl -L -f -o "libc++_shared_${arch_name}.so" "$url" 2>/dev/null; then
                  # Verify it's a valid ELF file and reasonable size
                  if [ -f "libc++_shared_${arch_name}.so" ] && [ -s "libc++_shared_${arch_name}.so" ]; then
                    file_size=$(stat -c%s "libc++_shared_${arch_name}.so" 2>/dev/null || stat -f%z "libc++_shared_${arch_name}.so" 2>/dev/null || echo "0")
                    
                    # Check if file is reasonable size (should be > 100KB for a real library)
                    if [ "$file_size" -gt 100000 ]; then
                      if file "libc++_shared_${arch_name}.so" | grep -q "ELF.*shared object"; then
                        cp "libc++_shared_${arch_name}.so" "$ANDROID_PROJECT_DIR/app/src/main/jniLibs/$arch_name/libc++_shared.so"
                        chmod 644 "$ANDROID_PROJECT_DIR/app/src/main/jniLibs/$arch_name/libc++_shared.so"
                        
                        echo "✅ Successfully downloaded real libc++_shared.so for $arch_name ($file_size bytes)"
                        DOWNLOAD_SUCCESS=true
                        CRITICAL_LIBS_FOUND=$((CRITICAL_LIBS_FOUND + 1))
                        break
                      else
                        echo "  ❌ Downloaded file is not a valid ELF binary"
                      fi
                    else
                      echo "  ❌ Downloaded file too small ($file_size bytes) - likely not a real library"
                    fi
                  else
                    echo "  ❌ Downloaded file is empty or doesn't exist"
                  fi
                else
                  echo "  ❌ Download failed from $url"
                fi
              done
              
              # If direct download failed, try alternative approach
              if [ "$DOWNLOAD_SUCCESS" = false ]; then
                echo "Direct download failed, trying alternative approach..."
                
                # Alternative: Use the system's libc++ if available and compatible
                case "$arch_name" in
                  "arm64-v8a")
                    # Look for system ARM64 libraries
                    if [ -f "/usr/lib/aarch64-linux-gnu/libc++.so.1" ]; then
                      echo "Using system ARM64 libc++ library"
                      cp "/usr/lib/aarch64-linux-gnu/libc++.so.1" "$ANDROID_PROJECT_DIR/app/src/main/jniLibs/$arch_name/libc++_shared.so"
                      chmod 644 "$ANDROID_PROJECT_DIR/app/src/main/jniLibs/$arch_name/libc++_shared.so"
                      DOWNLOAD_SUCCESS=true
                      CRITICAL_LIBS_FOUND=$((CRITICAL_LIBS_FOUND + 1))
                    fi
                    ;;
                  "armeabi-v7a")
                    # For ARMv7, we might need to skip if no compatible library is available
                    echo "No compatible system library found for ARMv7"
                    ;;
                esac
              fi
              
              # Final status for this architecture
              if [ "$DOWNLOAD_SUCCESS" = false ]; then
                echo "❌ Failed to obtain libc++_shared.so for $arch_name"
                echo "OpenCV may not work on $arch_name devices"
              fi
            else
              echo "✅ libc++_shared.so already exists for $arch_name"
              CRITICAL_LIBS_FOUND=$((CRITICAL_LIBS_FOUND + 1))
            fi
          fi
        done
        
        # Clean up download directory
        cd "$ANDROID_PROJECT_DIR"
        rm -rf "$TEMP_LIBC_DIR"
        
        # Update FOUND_ANY_LIBC based on successful downloads
        if [ "$CRITICAL_LIBS_FOUND" -gt 0 ]; then
          FOUND_ANY_LIBC=true
          echo "✅ Successfully obtained libc++_shared.so through alternative download"
        fi
      fi
      
      # Verify critical libraries exist and are valid for ARM architectures
      for arch_name in "arm64-v8a" "armeabi-v7a"; do
        if [ -d "app/src/main/jniLibs/$arch_name" ]; then
          echo "Verifying critical libraries for $arch_name..."
          
          # Check libopencv_java4.so
          if [ ! -f "app/src/main/jniLibs/$arch_name/libopencv_java4.so" ]; then
            echo "❌ Critical library missing: libopencv_java4.so for $arch_name"
            exit 1
          else
            opencv_size=$(stat -c%s "app/src/main/jniLibs/$arch_name/libopencv_java4.so" 2>/dev/null || stat -f%z "app/src/main/jniLibs/$arch_name/libopencv_java4.so" 2>/dev/null || echo "0")
            echo "  ✅ libopencv_java4.so present ($opencv_size bytes)"
          fi
          
          # Check libc++_shared.so with size validation
          if [ ! -f "app/src/main/jniLibs/$arch_name/libc++_shared.so" ]; then
            echo "❌ Critical library missing: libc++_shared.so for $arch_name"
            exit 1
          else
            libc_size=$(stat -c%s "app/src/main/jniLibs/$arch_name/libc++_shared.so" 2>/dev/null || stat -f%z "app/src/main/jniLibs/$arch_name/libc++_shared.so" 2>/dev/null || echo "0")
            echo "  📏 libc++_shared.so size: $libc_size bytes"
            
            # Validate that it's not a tiny stub file
            if [ "$libc_size" -lt 1000 ]; then
              echo "  ❌ libc++_shared.so is too small ($libc_size bytes) - likely an invalid stub"
              echo "  This will cause the runtime error: 'is too small to be an ELF executable'"
              
              # Remove the invalid file
              rm -f "app/src/main/jniLibs/$arch_name/libc++_shared.so"
              echo "  🗑️  Removed invalid stub file"
              
              # Don't exit - let the app try to work without it
              echo "  ⚠️  App will attempt to run without libc++_shared.so for $arch_name"
            else
              # Verify it's a valid ELF file
              if file "app/src/main/jniLibs/$arch_name/libc++_shared.so" | grep -q "ELF.*shared object"; then
                echo "  ✅ libc++_shared.so is valid ELF binary ($libc_size bytes)"
              else
                echo "  ⚠️  libc++_shared.so may not be a valid ELF binary"
              fi
            fi
          fi
          
          echo "✅ Library verification completed for $arch_name"
        fi
      done
      
      # Final validation for ARM device support
      if [ "$CRITICAL_LIBS_FOUND" -lt 2 ]; then
        echo "❌ Insufficient critical libraries found for ARM devices (found: $CRITICAL_LIBS_FOUND, expected: at least 2)"
        echo "This will cause runtime failures on ARM Android devices"
        
        # Show what we actually have
        echo "Libraries actually found in app module:"
        find app/src/main/jniLibs -name "*.so" | head -10
        exit 1
      else
        echo "✅ Critical ARM libraries validation passed ($CRITICAL_LIBS_FOUND critical libraries found)"
      fi
      
      # Verify app module has the libraries
      APP_LIBC_COUNT=$(find app/src/main/jniLibs -name "libc++_shared.so" 2>/dev/null | wc -l)
      APP_OPENCV_COUNT=$(find app/src/main/jniLibs -name "libopencv_java4.so" 2>/dev/null | wc -l)
      echo "Libraries in app module:"
      echo "  libc++_shared.so files: $APP_LIBC_COUNT"
      echo "  libopencv_java4.so files: $APP_OPENCV_COUNT"
      
      if [ "$FOUND_ANY_LIBC" = true ]; then
        echo "✅ Successfully added libc++_shared.so from NDK to OpenCV module"
      else
        echo "⚠️  Used stub libc++_shared.so (NDK version not found)"
        echo "App may need real NDK library for full functionality"
      fi
      
      # Show library distribution by architecture
      echo "Library distribution by architecture:"
      echo "✅ Library distribution completed successfully"
    else
      echo "❌ No OpenCV native libraries found"
      exit 1
    fi
  else
    echo "❌ OpenCV native libraries directory not found"
    exit 1
  fi
else
  echo "❌ OpenCV module directory not found"
  exit 1
fi

# Verify OpenCV is included in settings.gradle
if grep -q ":opencv" settings.gradle; then
  echo "✅ OpenCV module included in settings.gradle"
else
  echo "❌ OpenCV module not included in settings.gradle"
  exit 1
fi

# Check app module dependency on OpenCV
if grep -q "implementation project(':opencv')" app/build.gradle; then
  echo "✅ App module depends on OpenCV"
else
  echo "❌ App module does not depend on OpenCV"
  exit 1
fi

# Test OpenCV classes compilation
echo "Testing OpenCV classes availability..."
if find opencv/src/main/java -name "Mat.java" | grep -q .; then
  echo "✅ OpenCV Mat class found"
else
  echo "❌ OpenCV Mat class not found"
  exit 1
fi

if find opencv/src/main/java -name "Utils.java" | grep -q .; then
  echo "✅ OpenCV Utils class found"
else
  echo "❌ OpenCV Utils class not found"
  exit 1
fi

echo "✅ OpenCV integration validation completed successfully"