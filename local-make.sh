#!/bin/bash
# Build this checkout, deploy its release system-wide, and restart the user service.
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: ./local-make.sh

Run from your desktop session, without sudo. Builds both the native driver and
Java GUI from this checkout, installs the generated release system-wide, then
restarts the G13 user service. Existing configuration is preserved.

Close and reopen the G13 configuration window after the script completes.
EOF
}

if [[ ${1:-} == --help || ${1:-} == -h ]]; then usage; exit 0; fi
(($# == 0)) || { printf 'Unknown argument. Use --help.\n' >&2; exit 1; }
((EUID != 0)) || {
    printf 'Run this as your desktop user, without sudo; it requests sudo when needed.\n' >&2
    exit 1
}

repo=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
for tool in make sudo systemctl udevadm modprobe; do
    command -v "$tool" >/dev/null || { printf 'Missing required command: %s\n' "$tool" >&2; exit 1; }
done
systemctl --user show-environment >/dev/null || {
    printf 'Run this from your logged-in desktop session.\n' >&2
    exit 1
}

printf '\nBuilding the driver, Java GUI, and local release...\n'
make -C "$repo" all

release=$(readlink -f -- "$repo/dist/latest")
[[ -x $release/install.sh ]] || {
    printf 'Build did not create a deployable release at %s\n' "$release" >&2
    exit 1
}

printf '\nInstalling local release: %s\n' "$release"
sudo -v
sudo make -C "$repo" install

printf '\nRefreshing device access and restarting the user service...\n'
sudo modprobe uinput
sudo udevadm control --reload-rules
sudo udevadm trigger --subsystem-match=usb --attr-match=idVendor=046d --attr-match=idProduct=c21c
sudo udevadm trigger --subsystem-match=misc --sysname-match=uinput
sudo udevadm settle

display_vars=()
for variable in DISPLAY WAYLAND_DISPLAY XAUTHORITY XDG_CURRENT_DESKTOP; do
    [[ -z ${!variable:-} ]] || display_vars+=("$variable")
done
if ((${#display_vars[@]})); then systemctl --user import-environment "${display_vars[@]}"; fi
systemctl --user daemon-reload
systemctl --user enable g13.service
systemctl --user restart g13.service
systemctl --user is-active --quiet g13.service || {
    printf 'The service did not start. Check: journalctl --user -u g13.service -n 50 --no-pager\n' >&2
    exit 1
}

printf '\nLocal release installed and g13.service is active.\n'
printf 'Close and reopen the configuration window, or launch: g13-gui\n'
