#!/bin/sh
set -eu
release_dir=$(CDPATH= cd -- "$(dirname -- "$(readlink -f -- "$0")")/.." && pwd)
exec python3 "$release_dir/install.py" "$@"
