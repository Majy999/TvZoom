#!/bin/sh
set -eu

. "$(dirname "$0")/env.sh"

OUT="$ROOT/build/tvzoom-dex"
CLASSES="$OUT/classes"

mkdir -p "$CLASSES" "$OUT/dex"
find "$CLASSES" "$OUT/dex" -mindepth 1 -delete

javac -source 8 -target 8 -Xlint:-options \
    -cp "$ANDROID_JAR" \
    -d "$CLASSES" \
    "$ROOT/src/tvzoom/TvZoom.java"

"$BUILD_TOOLS_DIR/d8" \
    --release \
    --min-api 26 \
    --lib "$ANDROID_JAR" \
    --output "$OUT/dex" \
    $(find "$CLASSES" -name '*.class')

cp "$OUT/dex/classes.dex" "$ROOT/build/tvzoom.dex"
ls -lh "$ROOT/build/tvzoom.dex"
