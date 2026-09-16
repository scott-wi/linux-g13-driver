#!/bin/sh
set -eu
release_dir=$(CDPATH= cd -- "$(dirname -- "$(readlink -f -- "$0")")/.." && pwd)
PATH="$release_dir/bin:$PATH"
export PATH
exec "$release_dir/libexec/linux-g13-driver" "$@"
