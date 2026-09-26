#!/bin/bash
# Shared by the build scripts and the standalone release installer.
# Requires Bash and standard GNU/Linux utilities; no additional language runtime.
export LC_ALL=C
release_files=(README.md bin/g13-gui bin/g13-release bin/linux-g13-driver
    install.sh libexec/linux-g13-driver release-common.sh release.meta
    share/java/Linux-G13-GUI.jar share/systemd/g13.service.in share/udev/99-g13.rules)
declare -A metadata=()

die() { printf 'G13: %s\n' "$*" >&2; exit 1; }
exists() { [[ -e $1 || -L $1 ]]; }

read_host() {
    local os_file=/etc/os-release
    [[ -f $os_file ]] || os_file=/usr/lib/os-release
    host_os=$( . "$os_file"; printf '%s' "${ID:-linux}")
    host_version=$( . "$os_file"; printf '%s' "${VERSION_ID:-}")
    host_arch=$(uname -m)
}

read_metadata() {
    local key value
    metadata=()
    while IFS='=' read -r key value; do
        case "$key" in
            format|release|version|revision|dirty|os_id|os_version|architecture|input_debug) ;;
            *) die 'Unknown release metadata field' ;;
        esac
        [[ ! ${metadata[$key]+present} && $value =~ ^[A-Za-z0-9._-]*$ ]] || die 'Invalid release metadata'
        metadata[$key]=$value
    done < "$1/release.meta"
    if [[ ${#metadata[@]} == 8 && ${metadata[format]:-} == 2 ]]; then
        metadata[input_debug]=false
    elif [[ ${#metadata[@]} == 9 && ${metadata[format]:-} == 3 &&
            ( ${metadata[input_debug]:-} == true || ${metadata[input_debug]:-} == false ) ]]; then
        :
    else
        die 'Unsupported release metadata'
    fi
    [[ ${metadata[release]:-} =~ ^g13-[A-Za-z0-9._-]+$ ]] || die 'Unsupported release metadata'
}

verify_release() {
    local payload=$1 file mode actual expected line hash name
    [[ -d $payload ]] || die "Missing release: $payload"
    # Reject symlinks, special files, and unexpected paths before checksum handling.
    [[ -z $(find "$payload" -mindepth 1 ! -type f ! -type d -print -quit) ]] || die 'Release contains a symlink or special file'
    actual=$(cd "$payload" && find . -type f -printf '%P\n' | sort)
    expected=$(printf '%s\n' "${release_files[@]}" SHA256SUMS | sort)
    [[ $actual == "$expected" ]] || die 'Release contains missing or extra files'
    # Compare a freshly generated list instead of letting an untrusted checksum
    # file choose paths to read. Metadata is data, never sourced as shell code.
    (cd "$payload" && sha256sum -- "${release_files[@]}") | cmp -s - "$payload/SHA256SUMS" || die 'Release checksum mismatch'
    for file in "${release_files[@]}" SHA256SUMS; do
        mode=644
        case "$file" in bin/*|libexec/*|install.sh) mode=755 ;; esac
        [[ $(stat -c '%a' -- "$payload/$file") == "$mode" ]] || die "Release permission mismatch: $file"
    done
    read_metadata "$payload"
}

write_checksums() {
    (cd "$1" && sha256sum -- "${release_files[@]}" > SHA256SUMS)
    chmod 644 "$1/SHA256SUMS"
}

absolute_path() {
    [[ $1 == /* && $1 != *$'\n'* && $1 != *$'\r'* && $1 != *:* &&
       /${1#/}/ != */../* && /${1#/}/ != */./* ]] || die "Expected an absolute path without traversal, newline, or colon: $1"
}

# Callers own the temporaries array and remove it in their EXIT trap.
atomic_link() {
    local path=$1 target=$2 temp
    mkdir -p -- "$(dirname -- "$path")"
    temp=$(mktemp -d "$(dirname -- "$path")/.g13-link-XXXXXX")
    temporaries+=("$temp")
    ln -s -- "$target" "$temp/link"
    mv -Tf -- "$temp/link" "$path"
}

atomic_copy() {
    local source=$1 path=$2 temp
    mkdir -p -- "$(dirname -- "$path")"
    temp=$(mktemp "$(dirname -- "$path")/.g13-file-XXXXXX")
    temporaries+=("$temp")
    install -m 644 -- "$source" "$temp"
    mv -Tf -- "$temp" "$path"
}
