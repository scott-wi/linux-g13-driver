#!/bin/sh
set -eu
release_dir=$(CDPATH= cd -- "$(dirname -- "$(readlink -f -- "$0")")/.." && pwd)
exec java -jar "$release_dir/share/java/Linux-G13-GUI.jar" "$@"
