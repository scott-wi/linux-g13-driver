#!/usr/bin/env bash
set -euo pipefail
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
temp=$(mktemp -d)
trap 'rm -rf -- "$temp"' EXIT
mkdir -p "$temp/config" "$temp/runtime" "$temp/classes"
mapfile -t sources < <(find "$repo/g13-config-tool/src/main/java" \
    "$repo/g13-config-tool/src/test/java" -name '*.java' -print)
javac --release 17 -d "$temp/classes" "${sources[@]}" \
    "$repo/docs/screenshots/CaptureScreenshots.java"
XDG_CONFIG_HOME="$temp/config" XDG_RUNTIME_DIR="$temp/runtime" G13_THEME=light \
    java -Djava.awt.headless=true \
    -cp "$temp/classes:$repo/g13-config-tool/src/main/resources" \
    com.booker.g13.CaptureScreenshots "$repo/docs"
