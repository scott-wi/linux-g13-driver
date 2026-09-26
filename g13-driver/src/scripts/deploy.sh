#!/bin/bash
# Install and switch verified G13 releases without a source checkout.
set -euo pipefail
source_dir=$(CDPATH= cd -- "$(dirname -- "$(readlink -f -- "${BASH_SOURCE[0]}")")" && pwd)
. "$source_dir/release-common.sh"
usage() {
    cat <<'EOF'
Usage: bash install.sh {install|rollback|status|hardware} [options]
  --scope user|system         Installation scope (default: user)
  --destdir /staging/root     Stage files only; never activate services/devices
  --activate                 Enable/restart the user service after deployment
  --debug-input              Enable per-event logs in a debug-compiled release
  --replace-legacy           Back up conflicting files before migrating them
  --allow-platform-mismatch  Override distro/version/architecture check
EOF
}
command=${1:---help}
case "$command" in --help|-h) usage; exit 0 ;; install|rollback|status|hardware) shift ;; *) usage >&2; exit 1 ;; esac
scope=user destdir= activate=false replace_legacy=false allow_mismatch=false debug_input=false
while (($#)); do
    case "$1" in
        --scope|--destdir)
            (($# >= 2)) || die "Missing value for $1"
            if [[ $1 == --scope ]]; then scope=$2; else destdir=$2; fi
            shift 2 ;;
        --activate) activate=true; shift ;;
        --debug-input) debug_input=true; shift ;;
        --replace-legacy) replace_legacy=true; shift ;;
        --allow-platform-mismatch) allow_mismatch=true; shift ;;
        --help|-h) usage; exit 0 ;;
        *) die "Unknown argument: $1" ;;
    esac
done
[[ $scope == user || $scope == system ]] || die 'Scope must be user or system'
if $activate; then
    [[ $scope == user && ( $command == install || $command == rollback ) ]] || die '--activate is only supported for user installation or rollback'
fi
if $debug_input; then
    [[ $command == install || $command == rollback ]] || die '--debug-input is only supported for installation or rollback'
fi
if [[ -n $destdir ]]; then
    absolute_path "$destdir"
    destdir=$(realpath -m -- "$destdir")
    [[ $destdir != / ]] || die 'DESTDIR must be a separate staging directory, not /'
elif [[ $command != status ]]; then
    if [[ $scope == system || $command == hardware ]]; then
        ((EUID == 0)) || die 'System installation/device setup requires root; rerun with sudo'
    else
        ((EUID != 0)) || die 'Run user installation as your desktop user, without sudo'
    fi
fi
if [[ $scope == system ]]; then
    install_root=/usr/local/lib/linux-g13-driver
    bin=/usr/local/bin
    unit=/etc/systemd/user/g13.service
else
    install_root=${XDG_DATA_HOME:-$HOME/.local/share}/linux-g13-driver
    bin=$HOME/.local/bin
    unit=${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user/g13.service
fi
for path in "$install_root" "$bin" "$unit"; do absolute_path "$path"; done
root=$destdir$install_root
rules=$destdir/etc/udev/rules.d/99-g13.rules
temporaries=() lock_owned=false
cleanup() {
    rm -rf -- "${temporaries[@]}"
    if $lock_owned; then rmdir -- "$root/.deploy.lockdir"; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

selected() {
    local link=$root/$1 target
    if ! exists "$link"; then return; fi
    [[ -L $link ]] || die "Refusing unmanaged release pointer: $link"
    target=$(readlink -- "$link")
    [[ $target =~ ^releases/g13-[A-Za-z0-9._-]+$ ]] || die "Invalid release pointer: $link"
    verify_release "$root/$target"
    [[ ${metadata[release]} == "${target#releases/}" ]] || die 'Installed release name does not match metadata'
    printf '%s' "$target"
}

if [[ $command == status ]]; then
    for name in current previous; do
        selection=$(selected "$name")
        printf '%s: %s\n' "$name" "${selection:-none}"
    done
    exit 0
fi
if [[ $command == hardware ]]; then
    verify_release "$source_dir"
    if exists "$rules"; then
        [[ ! -L $rules ]] && cmp -s "$rules" "$source_dir/share/udev/99-g13.rules" || die "Existing custom rule must be reviewed before replacing: $rules"
    fi
    atomic_copy "$source_dir/share/udev/99-g13.rules" "$rules"
    [[ -n $destdir ]] || udevadm control --reload-rules
    printf 'Device rule installed. Reconnect the G13 if permissions need refreshing.\n'
    exit 0
fi

[[ $command != install ]] || verify_release "$source_dir"
mkdir -p -- "$root"
mkdir -- "$root/.deploy.lockdir" 2>/dev/null || die "Another deployment holds $root/.deploy.lockdir; retry when it finishes. Remove a stale lock only after confirming no installer is running."
lock_owned=true
current=$(selected current)
previous=$(selected previous)
if [[ $command == rollback ]]; then
    [[ -n $previous ]] || die 'No previous release is available'
    source_dir=$root/$previous
fi
verify_release "$source_dir"
release=${metadata[release]}
if $debug_input && [[ ${metadata[input_debug]} != true ]]; then
    die 'This release was not compiled with input tracing; rebuild with INPUT_DEBUG=1'
fi
read_host
if ! $allow_mismatch; then
    [[ ${metadata[os_id]} == "$host_os" && ${metadata[os_version]} == "$host_version" &&
       ${metadata[architecture]} == "$host_arch" ]] || die 'Release targets a different distro/version or architecture. Use a matching archive, or explicitly pass --allow-platform-mismatch.'
fi
if [[ -z $destdir ]]; then
    command -v java >/dev/null || die 'Java 17 or newer is required'
    java_version=$(java -version 2>&1)
    [[ $java_version =~ version\ \"([0-9]+) ]] && ((BASH_REMATCH[1] >= 17)) || die 'Java 17 or newer is required'
    libraries=$(ldd "$source_dir/libexec/linux-g13-driver" 2>&1) || die "Native runtime dependencies unavailable: $libraries"
    [[ $libraries != *'not found'* ]] || die "Native runtime dependencies unavailable: $libraries"
fi

commands=(linux-g13-driver g13-gui g13-release)
conflicts=()
for name in "${commands[@]}"; do
    path=$destdir$bin/$name
    if exists "$path" && ! { [[ -L $path ]] && [[ $(readlink -- "$path") == "$install_root/current/bin/$name" ]]; }; then
        conflicts+=("$path")
    fi
done
if exists "$destdir$unit" && ! { [[ -f $destdir$unit && ! -L $destdir$unit ]] &&
    [[ $(head -n 1 -- "$destdir$unit") == '# Managed by the G13 release installer.' ]]; }; then
    conflicts+=("$destdir$unit")
fi
if [[ $scope == system ]] && exists "$rules" && ! { [[ -f $rules && ! -L $rules ]] &&
    cmp -s "$rules" "$source_dir/share/udev/99-g13.rules"; }; then
    conflicts+=("$rules")
fi
if ((${#conflicts[@]})) && ! $replace_legacy; then
    printf 'Conflicting file: %s\n' "${conflicts[@]}" >&2
    die 'Use --replace-legacy to back up and migrate these files'
fi
for path in "${conflicts[@]}"; do
    [[ ! -d $path ]] && ! exists "$path.g13-before-release" || die "Cannot safely back up $path; resolve the directory or existing backup first"
done
relative=releases/$release
target=$root/$relative
mkdir -p -- "$root/releases"
if exists "$target"; then
    [[ ! -L $target ]] || die 'Installed release directory is a symlink'
    verify_release "$target"
    cmp -s "$source_dir/SHA256SUMS" "$target/SHA256SUMS" || die 'Installed release with this ID has different contents'
else
    incoming=$(mktemp -d "$root/releases/.incoming-XXXXXX")
    temporaries+=("$incoming")
    cp -a -- "$source_dir" "$incoming/payload"
    verify_release "$incoming/payload"
    mv -T -- "$incoming/payload" "$target"
fi
for path in "${conflicts[@]}"; do mv -T -- "$path" "$path.g13-before-release"; done
for name in "${commands[@]}"; do atomic_link "$destdir$bin/$name" "$install_root/current/bin/$name"; done

# Escape systemd quoting/specifiers without evaluating the path as shell code.
driver=$install_root/current/bin/linux-g13-driver
driver=${driver//\\/\\\\}
driver=${driver//\"/\\\"}
driver=${driver//%/%%}
driver=${driver//\$/\$\$}
generated=$(mktemp "$root/.g13-unit-XXXXXX")
temporaries+=("$generated")
while IFS= read -r line || [[ -n $line ]]; do
    if [[ $line == 'ExecStart=@DRIVER@' ]]; then
        printf 'ExecStart="%s"\n' "$driver"
        $debug_input && printf 'Environment=G13_INPUT_DEBUG=1\n'
    else
        printf '%s\n' "$line"
    fi
done < "$target/share/systemd/g13.service.in" > "$generated"
atomic_copy "$generated" "$destdir$unit"
if [[ $scope == system ]]; then atomic_copy "$target/share/udev/99-g13.rules" "$rules"; fi
if [[ -n $current && $current != "$relative" ]]; then atomic_link "$root/previous" "$current"; fi
atomic_link "$root/current" "$relative"
printf 'Installed release: %s\nLocation: %s/current\n' "$release" "$install_root"
if [[ -n $destdir ]]; then
    printf 'Staged under %s; no services or device rules were reloaded.\n' "$destdir"
elif $activate; then
    systemctl --user daemon-reload
    systemctl --user enable g13.service
    systemctl --user restart g13.service
else
    printf 'Activate as the desktop user: systemctl --user daemon-reload && systemctl --user enable g13.service && systemctl --user restart g13.service\n'
fi
if [[ -z $destdir ]]; then
    if [[ $scope == system ]]; then
        printf 'A user-local g13.service overrides the system-wide unit; review that override first.\nReload rules with sudo udevadm control --reload-rules; reconnect the G13 if needed.\n'
    else
        printf 'For one-time device permissions, run from the extracted release: sudo bash install.sh hardware\n'
    fi
fi
