#!/bin/bash
# Trial helper: run as the desktop user, not with sudo. No packages are installed.
# Rebuild -> deploy -> restart/check -> retire the manual system-wide installation.
set -euo pipefail

if [[ ${1:-} == --help || ${1:-} == -h ]]; then
    cat <<'EOF'
Usage: bash reinstall.sh

Run from your desktop session, without sudo (the script prompts when needed).
Builds this checkout, installs system-wide, and enables your G13 user service.
Works for a first installation as well as an existing manual system installation.
After the new service starts, old /usr binaries, GUI JAR, and unit are moved to
/var/backups/linux-g13-driver/. Existing bindings, macros, and device rules stay.
Close the configuration GUI first. No dependencies are installed or added.
EOF
    exit 0
fi
(($# == 0)) || { printf 'Unknown argument. Use --help.\n' >&2; exit 1; }
((EUID != 0)) || { printf 'Run this as your desktop user, without sudo; it requests sudo when needed.\n' >&2; exit 1; }
repo=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
. "$repo/g13-driver/src/scripts/release-common.sh"
# Fixed system paths; the isolated test harness rewrites this only in its copy.
system_root=''
step='initial checks'
backup=''
lock_owned=false
cleanup() {
    status=$?
    if $lock_owned; then rmdir -- "$repo/.reinstall.lock"; fi
    if ((status != 0)); then
        printf '\nStopped during %s. User configuration was preserved.\n' "$step" >&2
        [[ -z $backup ]] || printf 'Retired files are in %s.\n' "$backup" >&2
        printf 'Legacy files are not retired unless the new service passes its startup check.\nCheck: journalctl --user -u g13.service -n 50 --no-pager\n' >&2
    fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mkdir "$repo/.reinstall.lock" 2>/dev/null || die 'Another reinstall is running (or .reinstall.lock is stale).'
lock_owned=true
for tool in make sudo systemctl udevadm; do command -v "$tool" >/dev/null || die "Required existing tool is unavailable: $tool"; done
systemctl --user show-environment >/dev/null # Fail early if this is not a user session.

# A user unit or G13-specific drop-in can override the unit we install.
# Inherited service.d defaults (such as Fedora shutdown timeouts) are retained.
user_unit=${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user/g13.service
if exists "$user_unit"; then
    die "A user-local unit exists at $user_unit. This script migrates system-wide installs; review that override before proceeding."
fi
dropins=$(systemctl --user show g13.service -p DropInPaths --value)
if [[ $dropins == *"/g13.service.d/"* ]]; then
    die "Existing G13-specific drop-ins need review before migration: $dropins"
fi
legacy=(/usr/bin/linux-g13-driver /usr/bin/g13-gui
    /usr/share/java/linux-g13-driver/Linux-G13-GUI.jar /usr/lib/systemd/user/g13.service)
old_files=()
for file in "${legacy[@]}"; do
    path=$system_root$file
    if ! exists "$path"; then continue; fi
    [[ -f $path && ! -L $path ]] || die "Unexpected legacy file type: $path; review it manually"
    # Never remove files owned by the distro's package manager.
    if command -v rpm >/dev/null; then
        if rpm -qf -- "$path" >/dev/null 2>&1; then die "Package-managed file: $path. Remove its owning package using your package manager first."; fi
    elif command -v dpkg-query >/dev/null; then
        if dpkg-query -S "$path" >/dev/null 2>&1; then die "Package-managed file: $path. Remove its owning package using your package manager first."; fi
    elif command -v pacman >/dev/null; then
        if pacman -Qo -- "$path" >/dev/null 2>&1; then die "Package-managed file: $path. Remove its owning package using your package manager first."; fi
    else
        die "Cannot check package ownership of $path on this system; review the legacy files manually"
    fi
    old_files+=("$file")
done

step='building the release'
printf '\nBuilding locally; your existing driver keeps running during the build.\n'
make -C "$repo" all
release=$(readlink -f -- "$repo/dist/latest")
verify_release "$release"

step='installing the release'
sudo -v
# Install the complete new payload before stopping the old service. Explicitly
# back up conflicting local-admin files using the release installer's migration.
sudo bash "$release/install.sh" install --scope system --replace-legacy

step='refreshing device permissions'
sudo udevadm control --reload-rules
sudo udevadm trigger --subsystem-match=usb --attr-match=idVendor=046d --attr-match=idProduct=c21c
sudo udevadm trigger --subsystem-match=misc --sysname-match=uinput
sudo udevadm settle

step='switching the user service'
load_state=$(systemctl --user show g13.service -p LoadState --value)
if [[ $load_state != not-found ]]; then
    systemctl --user disable --now g13.service
fi
systemctl --user daemon-reload
# New desktop-session installs may not yet have display variables in systemd.
display_vars=()
for variable in DISPLAY WAYLAND_DISPLAY XAUTHORITY XDG_CURRENT_DESKTOP; do
    [[ -z ${!variable:-} ]] || display_vars+=("$variable")
done
if ((${#display_vars[@]})); then systemctl --user import-environment "${display_vars[@]}"; fi
expected_unit=$system_root/etc/systemd/user/g13.service
actual_unit=$(systemctl --user show g13.service -p FragmentPath --value)
[[ $actual_unit == "$expected_unit" ]] || die "Unexpected service unit: $actual_unit; expected $expected_unit"
systemctl --user enable --now g13.service

step='checking service startup'
initial_pid=$(systemctl --user show g13.service -p MainPID --value)
[[ $initial_pid =~ ^[0-9]+$ && $initial_pid != 0 ]] || die 'Driver did not start'
for attempt in 1 2 3 4 5 6; do
    sleep 1
    systemctl --user is-active --quiet g13.service || die 'New driver is not active; legacy files have been kept'
    [[ $(systemctl --user show g13.service -p MainPID --value) == "$initial_pid" ]] || die 'Driver restarted during its startup check; legacy files have been kept'
done

step='retiring obsolete files'
if ((${#old_files[@]})); then
    sudo mkdir -p "$system_root/var/backups/linux-g13-driver"
    backup=$(sudo mktemp -d "$system_root/var/backups/linux-g13-driver/legacy-XXXXXX")
    for file in "${old_files[@]}"; do
        sudo mkdir -p -- "$(dirname -- "$backup$file")"
        sudo mv -T -- "$system_root$file" "$backup$file"
    done
    systemctl --user daemon-reload
    printf 'Old installation retired to: %s\n' "$backup"
else
    printf 'No legacy installation files needed removal.\n'
fi
printf '\nInstalled release: %s\n' "$release"
printf 'The service stayed active during its startup check. Please test the G13 and GUI.\n'
printf 'Open the GUI: /usr/local/bin/g13-gui\nStatus: systemctl --user status g13.service\n'
