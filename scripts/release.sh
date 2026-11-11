#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

"$ROOT/scripts/build-all.sh"

mkdir -p "$ROOT/release"
find "$ROOT/release" -mindepth 1 -maxdepth 1 -delete
cp "$ROOT/build/TvZoom.apk" "$ROOT/release/TvZoom.apk"
cp "$ROOT/build/tvzoom.dex" "$ROOT/release/tvzoom.dex"

(
    cd "$ROOT/release"
    sha256sum TvZoom.apk tvzoom.dex > SHA256SUMS
)

cat "$ROOT/release/SHA256SUMS"
