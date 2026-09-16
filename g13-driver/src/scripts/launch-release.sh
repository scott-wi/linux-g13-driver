#!/bin/sh
set -eu
release_dir=$(CDPATH= cd -- "$(dirname -- "$(readlink -f -- "$0")")/.." && pwd)
exec bash "$release_dir/install.sh" "$@"
