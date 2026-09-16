#!/bin/bash
# Download a Fedora preview and deploy it using the release's existing installer.
set -euo pipefail

fail() { printf 'G13: %s\n' "$*" >&2; exit 1; }
usage() {
    cat <<'HELP'
Usage: bash install.sh [--tag preview-TAG] [--scope system|user] [--yes] [--check]

Install/upgrade a Fedora 44 x86_64 preview without building source.
Default: newest main-branch preview; detect an existing managed user installation,
otherwise install system-wide. Run as your desktop user, without sudo.
  --tag     Select a particular main or manually published branch preview.
  --yes     Allow DNF to install missing prerequisites without its confirmation.
  --check   Check platform/runtime dependencies only; make no changes.

Private repository: export GH_TOKEN (fine-grained Contents: read access), or use
an existing authenticated gh session. Tokens are never persisted in installation.
Close the G13 GUI first. Existing configuration is preserved.
HELP
}
read_platform() {
    local ID VERSION_ID
    . /etc/os-release
    platform=${ID:-unknown}
    version=${VERSION_ID:-unknown}
    architecture=$(uname -m)
}
java_works() {
    local info java_home libraries
    command -v java >/dev/null || return 1
    info=$(java -XshowSettings:properties -version 2>&1) || return 1
    [[ $info =~ version\ \"([0-9]+) ]] && (( BASH_REMATCH[1] >= 17 )) || return 1
    java_home=$(sed -n 's/^[[:space:]]*java.home = //p' <<< "$info")
    [[ -n $java_home && -f $java_home/lib/libawt_xawt.so ]] || return 1
    libraries=$(LD_LIBRARY_PATH="$java_home/lib/server:$java_home/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" ldd "$java_home/lib/libawt_xawt.so" 2>&1) || return 1
    [[ $libraries != *'not found'* ]]
}
ensure_dependencies() {
    local package tool
    local -a missing=() dnf_flags=()
    for tool in curl jq tar gzip; do
        command -v "$tool" >/dev/null || missing+=("$tool")
    done
    for package in libusb1 gtk3 libappindicator-gtk3; do
        rpm -q "$package" >/dev/null 2>&1 || missing+=("$package")
    done
    java_works || missing+=('java >= 17')
    if ((${#missing[@]})); then
        printf 'Required Fedora packages: %s\n' "${missing[*]}"
        $check && fail 'Dependency check failed; rerun without --check to install missing packages.'
        $yes && dnf_flags+=(-y)
        sudo dnf install "${dnf_flags[@]}" "${missing[@]}"
        hash -r
    fi
    for tool in curl jq tar gzip; do command -v "$tool" >/dev/null || fail "Missing command after dependency installation: $tool"; done
    java_works || fail 'The selected java must be 17+ with working desktop/AWT libraries. Check PATH/JAVA_HOME and sudo alternatives --config java; installing a package does not necessarily change a manually selected Java.'
    for package in libusb1 gtk3 libappindicator-gtk3; do
        rpm -q "$package" >/dev/null 2>&1 || fail "Missing runtime package: $package"
    done
}
get() {
    local endpoint=$1 output=$2 accept=${3:-application/vnd.github+json}
    curl -q --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
        --retry 3 --connect-timeout 20 --max-time 300 --max-filesize 134217728 \
        --header "@$scratch/headers" --header "Accept: $accept" \
        --output "$output" "https://api.github.com/repos/scott-wi/linux-g13-driver/$endpoint"
}
find_release() {
    local page selected
    if [[ -n $tag ]]; then
        get "releases/tags/$tag" "$scratch/release.json" || fail 'Cannot download that preview. Check its tag and repository access.'
    else
        for page in {1..10}; do
            get "releases?per_page=100&page=$page" "$scratch/page.json" || fail 'Cannot list previews. Private repositories require GH_TOKEN or an authenticated gh session with repository access.'
            selected=$(jq -c --arg asset "$asset" '[.[] | select(.draft == false and .prerelease == true and (.tag_name | startswith("preview-main-"))) | select(any(.assets[]; .name == $asset and .state == "uploaded"))] | sort_by(.published_at) | reverse | .[0] // empty' "$scratch/page.json")
            if [[ -n $selected ]]; then printf '%s\n' "$selected" > "$scratch/release.json"; break; fi
            [[ $(jq 'length' "$scratch/page.json") == 100 ]] || break
        done
    fi
    [[ -s $scratch/release.json ]] || fail 'No matching Fedora preview is published yet.'
    jq -e '.draft == false and .prerelease == true and (.tag_name | startswith("preview-"))' "$scratch/release.json" >/dev/null || fail 'The selected release is not a published preview.'
    tag=$(jq -r '.tag_name' "$scratch/release.json")
}
download_asset() {
    local name=$1 output=$2 asset_id
    asset_id=$(jq -er --arg name "$name" '[.assets[] | select(.name == $name and .state == "uploaded")] | if length == 1 then .[0].id else error("Missing or ambiguous asset") end' "$scratch/release.json") || fail "Preview is missing asset: $name"
    [[ $asset_id =~ ^[0-9]+$ ]] || fail 'Invalid release asset ID'
    get "releases/assets/$asset_id" "$output" application/octet-stream || fail "Download failed: $name"
}
extract_release() {
    local entry kind root= current
    tar -tzf "$scratch/$asset" > "$scratch/members"
    while IFS= read -r entry; do
        [[ $entry =~ ^g13-[A-Za-z0-9._-]+(/[A-Za-z0-9._/-]*)?$ && /$entry/ != */../* && /$entry/ != */./* ]] || fail 'Invalid archive member path'
        current=${entry%%/*}
        [[ -z $root || $root == "$current" ]] || fail 'Archive contains multiple releases'
        root=$current
    done < "$scratch/members"
    [[ -n $root ]] || fail 'Empty release archive'
    tar -tvzf "$scratch/$asset" > "$scratch/types"
    while IFS= read -r entry; do
        kind=${entry:0:1}
        [[ $kind == - || $kind == d ]] || fail 'Archive contains a link or special file'
    done < "$scratch/types"
    mkdir "$scratch/extracted"
    tar -xzf "$scratch/$asset" --no-same-owner --same-permissions -C "$scratch/extracted"
    release=$scratch/extracted/$root
    # The outer checksum has been verified before sourcing any downloaded code.
    . "$release/release-common.sh"
    verify_release "$release"
    [[ ${metadata[release]} == "$root" && ${metadata[os_id]} == "$platform" && ${metadata[os_version]} == "$version" && ${metadata[architecture]} == "$architecture" ]] || fail 'Preview metadata does not match this Fedora installation'
}
check_service_layout() {
    local user_unit=${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user/g13.service dropins
    if [[ -z $scope ]]; then
        scope=system
        [[ ! -e $user_unit && ! -L $user_unit ]] || scope=user
    fi
    if [[ -e $user_unit || -L $user_unit ]]; then
        [[ $scope == user && ! -L $user_unit && $(head -n 1 "$user_unit") == '# Managed by the G13 release installer.' ]] || fail 'An existing user service override needs review. This downloader upgrades managed releases; use the documented legacy migration path first.'
    fi
    for legacy in /usr/bin/linux-g13-driver /usr/bin/g13-gui /usr/lib/systemd/user/g13.service; do
        [[ ! -e $legacy && ! -L $legacy ]] || fail "Legacy installation detected at $legacy; migrate with reinstall.sh first."
    done
    systemctl --user show-environment >/dev/null || fail 'Run this from your logged-in desktop user session.'
    dropins=$(systemctl --user show g13.service -p DropInPaths --value)
    [[ $dropins != *'/g13.service.d/'* ]] || fail 'G13-specific service drop-ins need review before updating.'
}
activate_release() {
    local expected actual pid libraries variable
    local -a display_vars=()
    libraries=$(ldd "$release/libexec/linux-g13-driver" 2>&1) || fail "Cannot resolve native dependencies: $libraries"
    [[ $libraries != *'not found'* ]] || fail "Missing native dependencies: $libraries"
    if [[ $scope == system ]]; then
        sudo bash "$release/install.sh" install --scope system
        expected=/etc/systemd/user/g13.service
    else
        bash "$release/install.sh" install --scope user
        sudo bash "$release/install.sh" hardware
        expected=${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user/g13.service
    fi
    deployed=true
    sudo modprobe uinput
    sudo udevadm control --reload-rules
    sudo udevadm trigger --subsystem-match=usb --attr-match=idVendor=046d --attr-match=idProduct=c21c
    sudo udevadm trigger --subsystem-match=misc --sysname-match=uinput
    sudo udevadm settle
    for variable in DISPLAY WAYLAND_DISPLAY XAUTHORITY XDG_CURRENT_DESKTOP; do
        [[ -z ${!variable:-} ]] || display_vars+=("$variable")
    done
    if ((${#display_vars[@]})); then systemctl --user import-environment "${display_vars[@]}"; fi
    systemctl --user daemon-reload
    actual=$(systemctl --user show g13.service -p FragmentPath --value)
    [[ $actual == "$expected" ]] || fail "Unexpected service unit: $actual"
    systemctl --user enable g13.service
    systemctl --user restart g13.service
    pid=$(systemctl --user show g13.service -p MainPID --value)
    [[ $pid =~ ^[1-9][0-9]*$ ]] || fail 'Driver did not start'
    for attempt in {1..6}; do
        sleep 1
        systemctl --user is-active --quiet g13.service || fail 'Driver failed its startup check; see journalctl --user -u g13.service'
        [[ $(systemctl --user show g13.service -p MainPID --value) == "$pid" ]] || fail 'Driver restarted during its startup check'
    done
    printf 'Installed %s (%s scope). Open g13-gui and test your device.\n' "$tag" "$scope"
}
main() {
    tag= scope= yes=false check=false deployed=false
    while (($#)); do
        case "$1" in
            --tag|--scope)
                (($# >= 2)) || fail "Missing value for $1"
                if [[ $1 == --tag ]]; then tag=$2; else scope=$2; fi
                shift 2 ;;
            --yes) yes=true; shift ;;
            --check) check=true; shift ;;
            --help|-h) usage; return ;;
            *) fail "Unknown option: $1" ;;
        esac
    done
    [[ -z $tag || $tag =~ ^preview-[A-Za-z0-9._-]+$ ]] || fail 'Invalid preview tag'
    [[ -z $scope || $scope == system || $scope == user ]] || fail 'Scope must be system or user'
    read_platform
    [[ $platform == fedora && $version == 44 && $architecture == x86_64 ]] || fail "Supported target: Fedora 44 x86_64 (detected $platform $version $architecture)."
    [[ ! -e /run/ostree-booted ]] || fail 'Atomic/OSTree Fedora editions need a separate installation path; this installer supports DNF-managed Fedora.'
    [[ $(id -u) != 0 ]] || fail 'Run as your desktop user without sudo; elevation is requested when needed.'
    for tool in rpm dnf sudo ldd sha256sum systemctl udevadm modprobe; do
        command -v "$tool" >/dev/null || fail "Required Fedora system tool is missing: $tool"
    done
    if ! $check; then check_service_layout; fi
    ensure_dependencies
    if $check; then printf 'Fedora runtime dependencies are ready.\n'; return; fi
    scratch=$(mktemp -d)
    trap 'status=$?; rm -rf -- "$scratch"; if ((status != 0)) && $deployed; then printf "Deployment finished but activation failed. Configuration was preserved. Check journalctl --user -u g13.service; rollback instructions: docs/previews.md\n" >&2; fi' EXIT
    trap 'exit 130' INT
    trap 'exit 143' TERM
    local token=${GH_TOKEN:-${GITHUB_TOKEN:-}}
    if [[ -z $token ]] && command -v gh >/dev/null; then token=$(gh auth token 2>/dev/null || true); fi
    [[ -z $token || $token =~ ^[A-Za-z0-9_]+$ ]] || fail 'Invalid GitHub token format'
    (umask 077; printf 'X-GitHub-Api-Version: 2022-11-28\n' > "$scratch/headers")
    if [[ -n $token ]]; then printf 'Authorization: Bearer %s\n' "$token" >> "$scratch/headers"; fi
    unset token GH_TOKEN GITHUB_TOKEN
    asset=g13-preview-fedora-$version-$architecture.tar.gz
    find_release
    printf 'Downloading %s for Fedora %s %s.\n' "$tag" "$version" "$architecture"
    download_asset "$asset" "$scratch/$asset"
    download_asset "$asset.sha256" "$scratch/expected"
    (cd "$scratch" && sha256sum "$asset") > "$scratch/actual"
    cmp -s "$scratch/actual" "$scratch/expected" || fail 'Downloaded archive checksum does not match the published preview'
    extract_release
    activate_release
}
if [[ ${BASH_SOURCE[0]} == "$0" ]]; then main "$@"; fi
