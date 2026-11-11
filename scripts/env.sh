#!/bin/sh
set -eu

. "$(dirname "$0")/common.sh"

if [ -z "${ANDROID_SDK_ROOT:-}" ]; then
    for candidate in \
        "${ANDROID_HOME:-}" \
        "$HOME/Android/Sdk" \
        "$HOME/Library/Android/sdk"
    do
        if [ -n "$candidate" ] && [ -d "$candidate" ]; then
            ANDROID_SDK_ROOT=$candidate
            break
        fi
    done
fi

if [ -z "${BUILD_TOOLS_DIR:-}" ]; then
    if [ -z "${ANDROID_SDK_ROOT:-}" ] || [ ! -d "$ANDROID_SDK_ROOT" ]; then
        echo "Android SDK not found. Set ANDROID_SDK_ROOT or BUILD_TOOLS_DIR." >&2
        exit 1
    fi
    BUILD_TOOLS_DIR=$(find "$ANDROID_SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 \
        -type d 2>/dev/null | sort -V | tail -n 1)
fi

if [ -z "${ANDROID_JAR:-}" ]; then
    if [ -z "${ANDROID_SDK_ROOT:-}" ] || [ ! -d "$ANDROID_SDK_ROOT" ]; then
        echo "Android SDK not found. Set ANDROID_SDK_ROOT or ANDROID_JAR." >&2
        exit 1
    fi
    ANDROID_JAR=$(find "$ANDROID_SDK_ROOT/platforms" -mindepth 2 -maxdepth 2 \
        -type f -name android.jar 2>/dev/null | sort -V | tail -n 1)
    if [ -n "${ANDROID_JAR:-}" ]; then
        ANDROID_JAR=$(echo "$ANDROID_JAR" | tail -n 1)
    fi
fi

if [ -z "${BUILD_TOOLS_DIR:-}" ] || [ ! -x "$BUILD_TOOLS_DIR/aapt2" ]; then
    echo "Android build-tools not found. Set BUILD_TOOLS_DIR." >&2
    exit 1
fi

if [ -z "${ANDROID_JAR:-}" ] || [ ! -f "$ANDROID_JAR" ]; then
    echo "android.jar not found. Set ANDROID_JAR." >&2
    exit 1
fi

export ANDROID_SDK_ROOT BUILD_TOOLS_DIR ANDROID_JAR
