#!/bin/bash
set -euo pipefail
script_dir=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
. "$script_dir/release-common.sh"
root=$(CDPATH= cd -- "$script_dir/../../.." && pwd)
output=$root/dist
temporaries=()
trap 'rm -rf -- "${temporaries[@]}"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mkdir -p -- "$output"
temporary=$(mktemp -d "$output/.release-XXXXXX")
temporaries+=("$temporary")
payload=$temporary/payload
mkdir -p -- "$payload"/{bin,libexec,share/java,share/systemd,share/udev}

install -m 755 "$root/g13-driver/build/Linux-G13-Driver" "$payload/libexec/linux-g13-driver"
install -m 644 "$root/g13-config-tool/target/Linux-G13-GUI.jar" "$payload/share/java/Linux-G13-GUI.jar"
install -m 755 "$script_dir/deploy.sh" "$payload/install.sh"
install -m 644 "$script_dir/release-common.sh" "$payload/release-common.sh"
install -m 755 "$script_dir/launch-driver.sh" "$payload/bin/linux-g13-driver"
install -m 755 "$script_dir/launch-gui.sh" "$payload/bin/g13-gui"
install -m 755 "$script_dir/launch-release.sh" "$payload/bin/g13-release"
install -m 644 "$root/g13-driver/src/systemd/g13.service" "$payload/share/systemd/g13.service.in"
install -m 644 "$root/g13-driver/src/udev/99-g13.rules" "$payload/share/udev/99-g13.rules"
install -m 644 "$root/docs/releases.md" "$payload/README.md"

# The project version is the first <version> in the current, parentless POM.
version=$(sed -n '/<version>/{s/.*<version>\([^<]*\)<\/version>.*/\1/p;q;}' "$root/g13-config-tool/pom.xml")
[[ $version =~ ^[A-Za-z0-9._-]+$ ]] || die 'Cannot read project version from pom.xml'
revision=$(git -C "$root" rev-parse --short HEAD)
dirty=false
[[ -z $(git -C "$root" status --porcelain --untracked-files=normal) ]] || dirty=true
read_host
input_debug=false
[[ ${G13_INPUT_DEBUG_BUILD:-0} == 0 ]] || input_debug=true
printf 'format=3\nversion=%s\nrevision=%s\ndirty=%s\nos_id=%s\nos_version=%s\narchitecture=%s\ninput_debug=%s\n' \
    "$version" "$revision" "$dirty" "$host_os" "$host_version" "$host_arch" "$input_debug" > "$payload/release.meta"
chmod 644 "$payload/release.meta"
# Derive an immutable ID from metadata and all payload bytes, before adding the ID.
digest=$(cd "$payload" && sha256sum -- "${release_files[@]}" | sha256sum)
label="g13-$version-$host_os-$host_version-$host_arch-${digest:0:16}"
[[ $label =~ ^g13-[A-Za-z0-9._-]+$ ]] || die 'Unsupported platform identifier'
printf 'release=%s\n' "$label" >> "$payload/release.meta"
write_checksums "$payload"
verify_release "$payload"
destination=$output/$label
if exists "$destination"; then
    [[ ! -L $destination ]] || die 'Release destination is a symlink'
    verify_release "$destination"
    cmp -s "$payload/SHA256SUMS" "$destination/SHA256SUMS" || die "Existing release differs: $destination"
else
    mv -T -- "$payload" "$destination"
fi
tar -czf "$temporary/release.tar.gz" -C "$output" "$label"
mv -Tf -- "$temporary/release.tar.gz" "$destination.tar.gz"
atomic_link "$output/latest" "$label"
printf 'Release: %s\nArchive: %s.tar.gz\n' "$destination" "$destination"
