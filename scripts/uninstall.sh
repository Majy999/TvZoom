#!/bin/sh
set -eu

. "$(dirname "$0")/common.sh"

ADB=${ADB:-adb}
SERIAL=${ADB_SERIAL:-}
PKG=com.jypony.tvzoom

adb_cmd() {
    if [ -n "$SERIAL" ]; then
        "$ADB" -s "$SERIAL" "$@"
    else
        "$ADB" "$@"
    fi
}

adb_cmd shell \
    'if [ -f /data/local/tmp/tvzoom.dex ]; then CLASSPATH=/data/local/tmp/tvzoom.dex app_process /system/bin tvzoom.TvZoom reset; fi' \
    || true
adb_cmd uninstall "$PKG" || true
adb_cmd shell rm -f /data/local/tmp/tvzoom.dex
