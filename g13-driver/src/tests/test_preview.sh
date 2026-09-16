#!/bin/bash
# No network, packages, services or devices are touched by these tests.
set -euo pipefail
root=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)
base=$(mktemp -d)
trap 'rm -rf -- "$base"' EXIT
export preview_test_base=$base preview_test_root=$root
payload=$base/g13-test-fedora-44-x86_64
mkdir -p "$payload"/{bin,libexec,share/java,share/systemd,share/udev} "$base/java/lib/server"
. "$root/g13-driver/src/scripts/release-common.sh"
for file in "${release_files[@]}"; do printf 'fixture\n' > "$payload/$file"; chmod 644 "$payload/$file"; done
cp "$root/g13-driver/src/scripts/release-common.sh" "$payload/release-common.sh"
cat > "$payload/install.sh" <<'PAYLOAD'
#!/bin/bash
printf 'deploy %s\n' "$*" >> "$preview_test_base/calls"
PAYLOAD
chmod 755 "$payload/install.sh" "$payload"/bin/* "$payload"/libexec/*
printf 'format=2\nversion=2.0.0\nrevision=abcdef\ndirty=false\nos_id=fedora\nos_version=44\narchitecture=x86_64\nrelease=g13-test-fedora-44-x86_64\n' > "$payload/release.meta"
write_checksums "$payload"
verify_release "$payload"
asset=g13-preview-fedora-44-x86_64.tar.gz
tar -czf "$base/$asset" -C "$base" "${payload##*/}"
(cd "$base" && sha256sum "$asset") > "$base/checksum"
touch "$base/java/lib/libawt_xawt.so"
cat > "$base/release.json" <<'JSON'
{"tag_name":"preview-main-123-1","prerelease":true,"draft":false,"published_at":"2026-09-16T00:00:00Z","assets":[{"name":"g13-preview-fedora-44-x86_64.tar.gz","id":1,"state":"uploaded"},{"name":"g13-preview-fedora-44-x86_64.tar.gz.sha256","id":2,"state":"uploaded"}]}
JSON
cat > "$base/harness" <<'HARNESS'
set -euo pipefail
source "$preview_test_root/install.sh"
export HOME="$preview_test_base/home" XDG_CONFIG_HOME="$preview_test_base/home/.config"
mkdir -p "$XDG_CONFIG_HOME"
read_platform() { platform=${test_platform:-fedora}; version=${test_version:-44}; architecture=x86_64; }
id() { printf '1000\n'; }
rpm() { [[ ${test_missing:-false} == false || -f $preview_test_base/packages-installed ]]; }
dnf() { :; }
udevadm() { :; }
modprobe() { :; }
sleep() { :; }
java() { printf '    java.home = %s/java\nopenjdk version "%s.0.1"\n' "$preview_test_base" "${test_java:-25}" >&2; }
ldd() { if [[ ${test_ldd_missing:-false} == true ]]; then printf 'libbad.so => not found\n'; else printf 'libraries resolved\n'; fi; }
sudo() {
    printf 'sudo %s\n' "$*" >> "$preview_test_base/calls"
    if [[ $1 == dnf ]]; then touch "$preview_test_base/packages-installed";
    elif [[ $1 == bash ]]; then shift; "$@"; fi
}
systemctl() {
    printf 'systemctl %s\n' "$*" >> "$preview_test_base/calls"
    case "$*" in
        *DropInPaths*) printf '%s\n' "${test_dropins:-/usr/lib/systemd/user/service.d/10-timeout-abort.conf}" ;;
        *FragmentPath*)
            if [[ $scope == user ]]; then printf '%s/systemd/user/g13.service\n' "$XDG_CONFIG_HOME";
            else printf '/etc/systemd/user/g13.service\n'; fi ;;
        *MainPID*) printf '123\n' ;;
        *is-active*) [[ ${test_service_fail:-false} == false ]] ;;
    esac
}
curl() {
    local output= url=${!#}
    printf 'curl %s\n' "$*" >> "$preview_test_base/calls"
    while (($#)); do
        if [[ $1 == --output ]]; then output=$2; shift 2; else shift; fi
    done
    case "$url" in
        *'releases?per_page='*)
            if [[ ${test_no_release:-false} == true ]]; then printf '[]\n' > "$output";
            else jq -s . "$preview_test_base/release.json" > "$output"; fi ;;
        */releases/tags/*) cp "$preview_test_base/release.json" "$output" ;;
        */releases/assets/1) cp "$preview_test_base/g13-preview-fedora-44-x86_64.tar.gz" "$output" ;;
        */releases/assets/2)
            if [[ ${test_bad_checksum:-false} == true ]]; then printf 'wrong\n' > "$output";
            else cp "$preview_test_base/checksum" "$output"; fi ;;
        *) return 22 ;;
    esac
}
main "$@"
HARNESS
passed=0
run_ok() {
    : > "$base/calls"
    bash "$base/harness" "$@" > "$base/output" 2>&1 || { cat "$base/output"; exit 1; }
    passed=$((passed + 1))
}
run_bad() {
    : > "$base/calls"
    if bash "$base/harness" "$@" > "$base/output" 2>&1; then cat "$base/output"; echo 'Unexpected success'; exit 1; fi
    passed=$((passed + 1))
}
run_ok --check
! grep -q 'sudo\|curl\|systemctl' "$base/calls"
run_ok --scope system
grep -q 'deploy install --scope system' "$base/calls"
grep -q 'systemctl --user restart g13.service' "$base/calls"
run_ok --scope user --tag preview-main-123-1
grep -q 'deploy hardware' "$base/calls"
mkdir -p "$base/home/.config/systemd/user"
printf '# Managed by the G13 release installer.\n' > "$base/home/.config/systemd/user/g13.service"
run_ok
grep -q 'deploy install --scope user' "$base/calls"
run_bad --scope system
! grep -q deploy "$base/calls"
printf 'Unmanaged unit\n' > "$base/home/.config/systemd/user/g13.service"
run_bad
! grep -q sudo "$base/calls"
rm "$base/home/.config/systemd/user/g13.service"
test_missing=true run_ok --yes
grep -q 'sudo dnf install -y libusb1 gtk3 libappindicator-gtk3' "$base/calls"
rm "$base/packages-installed"
test_missing=true run_bad --check
! grep -q sudo "$base/calls"
test_java=11 run_bad --check
test_java=11 run_bad --yes
grep -q 'sudo dnf install -y java >= 17' "$base/calls"
! grep -q deploy "$base/calls"
mv "$base/java/lib/libawt_xawt.so" "$base/java/lib/libawt_xawt.so.saved"
run_bad --check
mv "$base/java/lib/libawt_xawt.so.saved" "$base/java/lib/libawt_xawt.so"
test_ldd_missing=true run_bad --check
test_version=43 run_bad
! grep -q sudo "$base/calls"
test_no_release=true run_bad
! grep -q deploy "$base/calls"
test_bad_checksum=true run_bad
! grep -q deploy "$base/calls"
test_dropins=/etc/systemd/user/g13.service.d/custom.conf run_bad
! grep -q deploy "$base/calls"
test_service_fail=true run_bad
grep -q 'Driver failed its startup check' "$base/output"
GH_TOKEN=ghp_synthetic_test_token run_ok
! grep -q 'ghp_synthetic_test_token' "$base/calls"
# Reject authenticated but unsafe archive members before extraction/execution.
ln -s /etc/passwd "$payload/link"
tar -czf "$base/$asset" -C "$base" "${payload##*/}"
(cd "$base" && sha256sum "$asset") > "$base/checksum"
run_bad
grep -q 'link or special file' "$base/output"
! grep -q deploy "$base/calls"
printf '%s preview installer tests passed.\n' "$passed"
