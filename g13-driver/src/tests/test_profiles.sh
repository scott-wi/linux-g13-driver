#!/bin/bash
set -euo pipefail
source_dir=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
repo_dir=$(CDPATH= cd -- "$source_dir/../.." && pwd)
temp=$(mktemp -d)
trap 'rm -rf -- "$temp"' EXIT
mkdir -p "$temp/classes" "$temp/java" "$temp/native"
mapfile -t java_sources < <(find "$repo_dir/g13-config-tool/src/main/java" "$repo_dir/g13-config-tool/src/test/java" -name '*.java' -print)
javac --release 17 -d "$temp/classes" "${java_sources[@]}"
java -Djava.awt.headless=true -cp "$temp/classes" com.booker.g13.ProfileImportTest "$temp/java" "$@"
XDG_CONFIG_HOME="$temp/gui-config" java -Djava.awt.headless=true \
    -cp "$temp/classes:$repo_dir/g13-config-tool/src/main/resources" com.booker.g13.ProfileGuiTest "${PROFILE_TEST_SCREENSHOT:-$temp/gui.png}"
"${CXX:-c++}" -std=c++17 -pthread -I"$source_dir/cpp" "$source_dir/tests/test_profile_actions.cpp" \
    "$source_dir/cpp/ConfigPath.cpp" "$source_dir/cpp/G13Action.cpp" "$source_dir/cpp/MacroAction.cpp" "$source_dir/cpp/G13.cpp" "$source_dir/cpp/Macro.cpp" "$source_dir/cpp/PassThroughAction.cpp" -lusb-1.0 -o "$temp/test-actions"
timeout 10 "$temp/test-actions" "$temp/native"
