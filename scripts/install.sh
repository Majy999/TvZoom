#!/bin/sh
set -eu

. "$(dirname "$0")/common.sh"

ADB=${ADB:-adb}
SERIAL=${ADB_SERIAL:-}
PKG=com.jypony.tvzoom
APK=${TVZOOM_APK:-}
DEX=${TVZOOM_DEX:-}

adb_cmd() {
    if [ -n "$SERIAL" ]; then
        "$ADB" -s "$SERIAL" "$@"
    else
        "$ADB" "$@"
    fi
}

if [ -z "$APK" ]; then
    if [ -f "$ROOT/build/TvZoom.apk" ]; then
        APK="$ROOT/build/TvZoom.apk"
    else
        APK="$ROOT/release/TvZoom.apk"
    fi
fi
if [ -z "$DEX" ]; then
    if [ -f "$ROOT/build/tvzoom.dex" ]; then
        DEX="$ROOT/build/tvzoom.dex"
    else
        DEX="$ROOT/release/tvzoom.dex"
    fi
fi

if [ ! -f "$APK" ] || [ ! -f "$DEX" ]; then
    echo "Missing TvZoom.apk or tvzoom.dex." >&2
    echo "Run ./scripts/build-all.sh, or use the files in release/." >&2
    exit 1
fi

adb_cmd install -r "$APK"
adb_cmd push "$DEX" /data/local/tmp/tvzoom.dex
adb_cmd shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS
adb_cmd shell am start -n "$PKG/.MainActivity"
