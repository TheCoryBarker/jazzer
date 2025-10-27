#!/bin/bash

set -euo pipefail

CONFIG_FILE="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)/configuration.sh"

show_help() {
  echo "Usage:"
  echo "  source aosp_setup.sh configure --aosp_home <path> --sdk_home <path> --ndk_home <path>"
  echo "  source aosp_setup.sh setup_r8"
  echo "  source aosp_setup.sh setup_jazzer_runtime"
  echo "  source aosp_setup.sh setup"
  echo "  source aosp_setup.sh help"
  echo
  echo "Notes:"
  echo "- 'configure' writes paths to configuration.sh located next to this script."
  echo "- Subsequent commands read that file to set environment variables."
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Error: required command not found: $1" >&2
    return 1
  fi
}

abs_path() {
  cd "$1" >/dev/null 2>&1 && pwd
}

load_config() {
  if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: configuration not found at $CONFIG_FILE. Run 'configure' first." >&2
    return 1
  fi
  # shellcheck disable=SC1090
  source "$CONFIG_FILE"
}

write_config() {
  cat > "$CONFIG_FILE" <<EOF
#!/bin/bash
export AOSP_TOP_INPUT="$AOSP_TOP_INPUT"
export SDK_HOME_INPUT="$SDK_HOME_INPUT"
export NDK_HOME_INPUT="$NDK_HOME_INPUT"
EOF
  echo "Wrote configuration to $CONFIG_FILE"
}

configure_cmd() {
  local AOSP_TOP_INPUT=""
  local SDK_HOME_INPUT=""
  local NDK_HOME_INPUT=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --aosp_home=*) AOSP_TOP_INPUT="${1#*=}"; shift ;;
      --aosp_home)   AOSP_TOP_INPUT="$2"; shift 2 ;;
      --sdk_home=*)  SDK_HOME_INPUT="${1#*=}"; shift ;;
      --sdk_home)    SDK_HOME_INPUT="$2"; shift 2 ;;
      --ndk_home=*)  NDK_HOME_INPUT="${1#*=}"; shift ;;
      --ndk_home)    NDK_HOME_INPUT="$2"; shift 2 ;;
      -h|--help)     show_help; return 0 ;;
      *) echo "Unknown argument: $1"; show_help; return 1 ;;
    esac
  done

  if [ -z "${AOSP_TOP_INPUT:-}" ] || [ -z "${SDK_HOME_INPUT:-}" ] || [ -z "${NDK_HOME_INPUT:-}" ]; then
    echo "Error: --aosp_home, --sdk_home, and --ndk_home are required." >&2
    show_help
    return 1
  fi
  if [ ! -d "$AOSP_TOP_INPUT" ]; then echo "Error: AOSP path invalid: $AOSP_TOP_INPUT" >&2; return 1; fi
  if [ ! -d "$SDK_HOME_INPUT" ]; then echo "Error: SDK path invalid: $SDK_HOME_INPUT" >&2; return 1; fi
  if [ ! -d "$NDK_HOME_INPUT" ]; then echo "Error: NDK path invalid: $NDK_HOME_INPUT" >&2; return 1; fi

  AOSP_TOP_INPUT="$(abs_path "$AOSP_TOP_INPUT")"
  SDK_HOME_INPUT="$(abs_path "$SDK_HOME_INPUT")"
  NDK_HOME_INPUT="$(abs_path "$NDK_HOME_INPUT")"

  write_config

  # Prepare paths for initial r8 copy
  local JAZZER_TOP
  local AOSP_R8_PATH
  local LOCAL_R8_PATH
  local JAZZER_R8_PATH

  JAZZER_TOP="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  AOSP_R8_PATH="${AOSP_TOP_INPUT}/prebuilts/r8/r8.jar"
  LOCAL_R8_PATH="${JAZZER_TOP}/third_party/android/r8.jar"
  JAZZER_R8_PATH="$JAZZER_TOP/bazel-bin/src/main/java/com/code_intelligence/jazzer/android/r8_deploy.jar"

  # Ensure local r8.jar exists by copying from AOSP if missing
  if [ ! -f "$LOCAL_R8_PATH" ] && [ -f "$AOSP_R8_PATH" ]; then
    mkdir -p "$(dirname "$LOCAL_R8_PATH")"
    cp "$AOSP_R8_PATH" "$LOCAL_R8_PATH"
    echo "Copied initial local r8.jar from AOSP to $LOCAL_R8_PATH"
  fi

  echo "Configuration complete."
}

setup_r8_cmd() {
  load_config

  export AOSP_TOP="$(abs_path "$AOSP_TOP_INPUT")"
  export ANDROID_HOME="$(abs_path "$SDK_HOME_INPUT")"
  export ANDROID_NDK_HOME="$(abs_path "$NDK_HOME_INPUT")"

  require_cmd bazelisk

  local AOSP_R8_PATH="$AOSP_TOP/prebuilts/r8/r8.jar"
  local JAZZER_TOP="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  local JAZZER_R8_PATH="$JAZZER_TOP/bazel-bin/src/main/java/com/code_intelligence/jazzer/android/r8_deploy.jar"

  # Clean AOSP r8 prebuilts so the build picks up changes
  rm -rf "$AOSP_TOP/out/soong/.intermediates/prebuilts/r8/r8" || true
  rm -f "$AOSP_TOP/out/host/linux-x86/framework/r8.jar" || true

  # Build customized r8
  bazelisk build --config=android_arm src/main/java/com/code_intelligence/jazzer/android:r8_deploy.jar

  # Replace AOSP r8.jar with our build
  rm -f "$AOSP_R8_PATH" || true
  cp "$JAZZER_R8_PATH" "$AOSP_R8_PATH"
  echo "Updated AOSP r8.jar at $AOSP_R8_PATH"
}

setup_jazzer_runtime_cmd() {
  load_config

  export AOSP_TOP="$(abs_path "$AOSP_TOP_INPUT")"
  export ANDROID_HOME="$(abs_path "$SDK_HOME_INPUT")"
  export ANDROID_NDK_HOME="$(abs_path "$NDK_HOME_INPUT")"

  require_cmd bazelisk

  # Clean jazzer runtime related prebuilts
  rm -rf "$AOSP_TOP/out/soong/.intermediates/tools/security/fuzzing/app_lib/libjazzer_driver" || true
  rm -rf "$AOSP_TOP/out/target/product/generic_arm64/symbols/system/app/AppCory/AppCory.apk/lib/arm64-v8a/libjazzer_driver.so" || true
  rm -rf "$AOSP_TOP/out/target/product/generic_arm64/symbols/system/app" || true
  rm -rf "$AOSP_TOP/out/soong/.intermediates/tools/security/fuzzing/app_example/" || true
  rm -rf "$AOSP_TOP/out/soong/.intermediates/tools/security/fuzzing/app_lib/app_fuzz_lib" || true
  rm -rf "$AOSP_TOP/out/soong/.intermediates/tools/security/fuzzing/app_lib/jazzer_runtime" || true

  # Build jazzer android deploy jar
  bazelisk build --config=android_arm //src/main/java/com/code_intelligence/jazzer/android:jazzer_android_deploy.jar

  local JAZZER_FOR_ANDROID_OUTPUT="bazel-bin/src/main/java/com/code_intelligence/jazzer/android/jazzer_android_deploy.jar"
  if [ -f "$JAZZER_FOR_ANDROID_OUTPUT" ]; then
    local JAZZER_EXTRACT_DIR
    JAZZER_EXTRACT_DIR="$(mktemp -d 2>/dev/null || mktemp -d -t jazzer_extract)"
    if command -v unzip >/dev/null 2>&1; then
      unzip -q -o "$JAZZER_FOR_ANDROID_OUTPUT" -d "$JAZZER_EXTRACT_DIR"
    elif command -v jar >/dev/null 2>&1; then
      (cd "$JAZZER_EXTRACT_DIR" && jar xf "$(pwd -P)/$JAZZER_FOR_ANDROID_OUTPUT")
    else
      echo "Warning: neither 'unzip' nor 'jar' available to extract $JAZZER_FOR_ANDROID_OUTPUT" >&2
    fi

    # Inner jazzer.jar path
    local JAZZER_JAR_PATH
    JAZZER_JAR_PATH="$(find "$JAZZER_EXTRACT_DIR" -type f -path "*/com/code_intelligence/jazzer/jazzer.jar" | head -n1)"
    [ -n "$JAZZER_JAR_PATH" ] && export JAZZER_JAR_PATH

    # Collect .so files
    mapfile -t JAZZER_SO_FILES < <(find "$JAZZER_EXTRACT_DIR" -type f -name "*.so" | sort)
    export JAZZER_SO_COUNT="${#JAZZER_SO_FILES[@]}"
    export JAZZER_SO_PATHS="${JAZZER_SO_FILES[*]}"
    local idx=1
    for so in "${JAZZER_SO_FILES[@]}"; do
      export "JAZZER_SO_${idx}"="$so"
      idx=$((idx+1))
    done

    echo "JAZZER_JAR_PATH=$JAZZER_JAR_PATH"
    echo "JAZZER_SO_COUNT=$JAZZER_SO_COUNT"
    echo "JAZZER_SO_PATHS=$JAZZER_SO_PATHS"

    JAZZER_RUNTIME_AOSP_DIRECTORY=$AOSP_TOP/tools/security/fuzzing/jazzer
    rm -rf "$JAZZER_RUNTIME_AOSP_DIRECTORY"
    local DEST_DIR="$JAZZER_RUNTIME_AOSP_DIRECTORY/lib/arm64"
    mkdir -p "$DEST_DIR"
    # Copy all .so libraries
    for so in "${JAZZER_SO_FILES[@]}"; do
      cp -f "$so" "$DEST_DIR/"
      echo "Copied $(basename "$so") to $DEST_DIR"
    done

    # Copy jazzer.jar to the runtime directory root
    if [ -n "${JAZZER_JAR_PATH:-}" ] && [ -f "$JAZZER_JAR_PATH" ]; then
      mkdir -p "$JAZZER_RUNTIME_AOSP_DIRECTORY"
      cp -f "$JAZZER_JAR_PATH" "$JAZZER_RUNTIME_AOSP_DIRECTORY/jazzer.jar"
      echo "Copied jazzer.jar to $JAZZER_RUNTIME_AOSP_DIRECTORY"
    else
      echo "Warning: jazzer.jar not found to copy (JAZZER_JAR_PATH=$JAZZER_JAR_PATH)" >&2
    fi

    # Copy aosp_files directory tree into the runtime directory, preserving structure
    local JAZZER_TOP
    JAZZER_TOP="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local SRC_AOSP_FILES_DIR="$JAZZER_TOP/aosp_files"
    if [ -d "$SRC_AOSP_FILES_DIR" ]; then
      mkdir -p "$JAZZER_RUNTIME_AOSP_DIRECTORY"
      if command -v rsync >/dev/null 2>&1; then
        rsync -a "$SRC_AOSP_FILES_DIR/" "$JAZZER_RUNTIME_AOSP_DIRECTORY/"
      else
        cp -a "$SRC_AOSP_FILES_DIR/." "$JAZZER_RUNTIME_AOSP_DIRECTORY/"
      fi
      echo "Copied contents of $SRC_AOSP_FILES_DIR to $JAZZER_RUNTIME_AOSP_DIRECTORY (structure preserved)."
    else
      echo "Warning: aosp_files directory not found at $SRC_AOSP_FILES_DIR" >&2
    fi
  fi
}

setup_cmd() {
  setup_r8_cmd "$@"
  setup_jazzer_runtime_cmd "$@"
}

main() {
  local cmd="${1:-help}"
  shift || true
  case "$cmd" in
    help|-h|--help) show_help ;;
    configure)      configure_cmd "$@" ;;
    setup_r8)       setup_r8_cmd "$@" ;;
    setup_jazzer_runtime) setup_jazzer_runtime_cmd "$@" ;;
    setup)          setup_cmd "$@" ;;
    *) echo "Unknown command: $cmd"; show_help; return 1 ;;
  esac
}

main "$@"


