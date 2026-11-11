#!/bin/sh
set -eu

. "$(dirname "$0")/env.sh"

OUT="$ROOT/build/app"
APK_OUT="$ROOT/build/TvZoom.apk"
KEYSTORE="${TVZOOM_KEYSTORE:-$ROOT/build/tvzoom.keystore}"
MANIFEST="$OUT/AndroidManifest.xml"

mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/apk"
find "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/apk" -mindepth 1 -delete

sed 's#<manifest xmlns:android="http://schemas.android.com/apk/res/android"#<manifest xmlns:android="http://schemas.android.com/apk/res/android"\
    package="com.jypony.tvzoom"#' \
    "$ROOT/app/src/main/AndroidManifest.xml" > "$MANIFEST"

"$BUILD_TOOLS_DIR/aapt2" compile \
    --dir "$ROOT/app/src/main/res" \
    -o "$OUT/res/resources.zip"

"$BUILD_TOOLS_DIR/aapt2" link \
    -o "$OUT/apk/unsigned.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$MANIFEST" \
    --java "$OUT/gen" \
    --min-sdk-version 26 \
    --target-sdk-version 34 \
    "$OUT/res/resources.zip"

javac -source 8 -target 8 -Xlint:-options \
    -d "$OUT/classes" \
    -cp "$ANDROID_JAR" \
    $(find "$ROOT/app/src/main/java" "$OUT/gen" "$ROOT/third_party/AdbLib/src" \
        -name '*.java')

"$BUILD_TOOLS_DIR/d8" \
    --release \
    --min-api 26 \
    --lib "$ANDROID_JAR" \
    --output "$OUT/dex" \
    $(find "$OUT/classes" -name '*.class')

cp "$OUT/apk/unsigned.apk" "$OUT/apk/unaligned.apk"
jar uf "$OUT/apk/unaligned.apk" -C "$OUT/dex" classes.dex
"$BUILD_TOOLS_DIR/zipalign" -f 4 \
    "$OUT/apk/unaligned.apk" "$OUT/apk/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -storepass tvzoom123 \
        -keypass tvzoom123 \
        -alias tvzoom \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=TvZoom, OU=Home, O=Home, L=Shanghai, S=Shanghai, C=CN" \
        >/dev/null 2>&1
fi

"$BUILD_TOOLS_DIR/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:tvzoom123 \
    --key-pass pass:tvzoom123 \
    --out "$APK_OUT" \
    "$OUT/apk/aligned.apk"

"$BUILD_TOOLS_DIR/apksigner" verify --verbose "$APK_OUT" | head -20
ls -lh "$APK_OUT"
