#!/bin/bash
# Integration tests use only Bash/standard utilities and temporary staging roots.
set -euo pipefail
test_script=$(readlink -f -- "${BASH_SOURCE[0]}")
source_dir=$(CDPATH= cd -- "$(dirname -- "$test_script")/.." && pwd)
. "$source_dir/scripts/release-common.sh"

assert_eq() { [[ $1 == "$2" ]] || die "Expected '$2', got '$1'"; }
assert_contains() { grep -Fq -- "$2" "$1" || die "Missing '$2' in $1"; }
run_deploy() { bash "$release/install.sh" "$@" --destdir "$stage" > "$base/output" 2>&1; }
expect_failure() {
    if run_deploy "$@"; then die "Unexpected success: $*"; fi
}

make_release() {
    release=$base/$1
    mkdir -p "$release"/{bin,libexec,share/java,share/systemd,share/udev}
    install -m 755 "$source_dir/scripts/deploy.sh" "$release/install.sh"
    install -m 644 "$source_dir/scripts/release-common.sh" "$release/release-common.sh"
    install -m 755 "$source_dir/scripts/launch-driver.sh" "$release/bin/linux-g13-driver"
    install -m 755 "$source_dir/scripts/launch-gui.sh" "$release/bin/g13-gui"
    install -m 755 "$source_dir/scripts/launch-release.sh" "$release/bin/g13-release"
    install -m 644 "$source_dir/systemd/g13.service" "$release/share/systemd/g13.service.in"
    install -m 644 "$source_dir/udev/99-g13.rules" "$release/share/udev/99-g13.rules"
    printf '#!/bin/sh\nprintf "%%s\\n" "$0" "$@"\ncommand -v g13-gui\n' > "$release/libexec/linux-g13-driver"
    chmod 755 "$release/libexec/linux-g13-driver"
    printf '%s\n' "$1" > "$release/share/java/Linux-G13-GUI.jar"
    printf 'test payload\n' > "$release/README.md"
    read_host
    printf 'format=2\nrelease=%s\nversion=test\nrevision=test\ndirty=true\nos_id=%s\nos_version=%s\narchitecture=%s\n' \
        "$1" "$host_os" "$host_version" "$host_arch" > "$release/release.meta"
    chmod 644 "$release/release.meta" "$release/README.md" "$release/share/java/Linux-G13-GUI.jar"
    write_checksums "$release"
}

test_update_rollback() {
    run_deploy install
    run_deploy install
    [[ ! -e $root/previous ]]
    first=$release
    make_release g13-test-two
    run_deploy install
    run_deploy install
    assert_eq "$(readlink "$root/previous")" releases/g13-test-one
    rm -rf -- "$first" "$release"
    release=$root/current
    run_deploy rollback
    assert_eq "$(readlink "$root/current")" releases/g13-test-one
    assert_eq "$(readlink "$root/previous")" releases/g13-test-two
    run_deploy status
    assert_contains "$base/output" g13-test-one
    "$root/current/bin/g13-release" status --destdir "$stage" > "$base/output"
    assert_contains "$base/output" g13-test-one
}

test_corruption_and_metadata() {
    run_deploy install
    make_release g13-test-two
    printf 'corrupt\n' > "$release/share/java/Linux-G13-GUI.jar"
    expect_failure install
    assert_contains "$base/output" 'checksum mismatch'
    assert_eq "$(readlink "$root/current")" releases/g13-test-one
    write_checksums "$release"
    # Valid checksums must not turn metadata into executable shell code.
    printf 'release=$(touch %s)\n' "$base/executed" >> "$release/release.meta"
    write_checksums "$release"
    expect_failure install
    [[ ! -e $base/executed ]]
    assert_eq "$(readlink "$root/current")" releases/g13-test-one
}

test_system_layout() {
    run_deploy install --scope system
    [[ -f $stage/usr/local/lib/linux-g13-driver/current/release.meta ]]
    assert_contains "$stage/etc/systemd/user/g13.service" 'ExecStart="/usr/local/lib/linux-g13-driver/current/bin/linux-g13-driver"'
    assert_eq "$(readlink "$stage/usr/local/bin/g13-gui")" /usr/local/lib/linux-g13-driver/current/bin/g13-gui
    [[ -f $stage/etc/udev/rules.d/99-g13.rules ]]
    [[ ! -e $base/runtime-command ]]
}

test_user_layout_and_launchers() {
    config=$stage$XDG_CONFIG_HOME/g13/bindings-0.properties
    mkdir -p "$(dirname "$config")"
    printf 'G0=p,k.17\n' > "$config"
    run_deploy install --activate
    assert_eq "$(cat "$config")" 'G0=p,k.17'
    [[ ! -e $stage/etc/udev && ! -e $base/runtime-command ]]
    assert_contains "$stage$XDG_CONFIG_HOME/systemd/user/g13.service" "$XDG_DATA_HOME/linux-g13-driver/current/bin"
    "$root/current/bin/linux-g13-driver" 'two words' > "$base/output"
    assert_contains "$base/output" 'two words'
    assert_contains "$base/output" "$root/releases/g13-test-one/bin/g13-gui"
    "$root/current/bin/g13-gui" 'two words' > "$base/output"
    assert_eq "$(cat "$base/output")" "$(printf '%s\n' -jar "$root/releases/g13-test-one/share/java/Linux-G13-GUI.jar" 'two words')"
}

test_legacy_migration() {
    binary=$stage$HOME/.local/bin/linux-g13-driver
    mkdir -p "$(dirname "$binary")"
    printf 'old driver\n' > "$binary"
    expect_failure install
    assert_eq "$(cat "$binary")" 'old driver'
    run_deploy install --replace-legacy
    [[ -L $binary ]]
    assert_eq "$(cat "$binary.g13-before-release")" 'old driver'
    # Do not overwrite the saved original during another conflict.
    rm "$binary"
    printf 'another driver\n' > "$binary"
    expect_failure install --replace-legacy
    assert_eq "$(cat "$binary.g13-before-release")" 'old driver'
}

test_platform_and_inventory() {
    sed -i 's/^architecture=.*/architecture=wrong-architecture/' "$release/release.meta"
    write_checksums "$release"
    expect_failure install
    [[ ! -e $root/current ]]
    run_deploy install --allow-platform-mismatch
    printf 'extra\n' > "$release/unexpected"
    expect_failure install --allow-platform-mismatch
    assert_contains "$base/output" 'extra files'
}

test_history_symlinks_and_permissions() {
    expect_failure rollback
    chmod 644 "$release/libexec/linux-g13-driver"
    expect_failure install
    assert_contains "$base/output" 'permission mismatch'
    rm "$release/libexec/linux-g13-driver"
    ln -s /bin/true "$release/libexec/linux-g13-driver"
    expect_failure install
    assert_contains "$base/output" symlink
}

test_hardware_and_staging_guards() {
    run_deploy hardware
    [[ -f $stage/etc/udev/rules.d/99-g13.rules && ! -e $root/current && ! -e $base/runtime-command ]]
    if bash "$release/install.sh" install --destdir / > "$base/output" 2>&1; then die 'Accepted / as DESTDIR'; fi
    if bash "$release/install.sh" install --destdir '/tmp/../' > "$base/output" 2>&1; then die 'Accepted traversal'; fi
    mkdir -p "$root/.deploy.lockdir"
    expect_failure install
    assert_contains "$base/output" 'Another deployment'
    [[ -d $root/.deploy.lockdir ]] # Never remove someone else's lock.
}

test_runtime_preflight() {
    # Live-layout writes are confined to the temporary HOME; all runtime commands
    # are stubs, and neither the real driver nor a real service manager is run.
    if ((EUID == 0)); then
        if bash "$release/install.sh" install > "$base/output" 2>&1; then die 'Accepted root user install'; fi
        assert_contains "$base/output" 'without sudo'
        return
    fi
    if bash "$release/install.sh" install > "$base/output" 2>&1; then die 'Accepted invalid Java'; fi
    assert_contains "$base/output" 'Java 17'
    printf '#!/bin/sh\necho '\''openjdk version "17.0.1"'\'' >&2\n' > "$base/tools/java"
    printf '#!/bin/sh\necho "libmissing.so => not found"\n' > "$base/tools/ldd"
    chmod 755 "$base/tools/ldd"
    if bash "$release/install.sh" install > "$base/output" 2>&1; then die 'Accepted missing native library'; fi
    assert_contains "$base/output" 'Native runtime dependencies unavailable'
    [[ ! -e $XDG_DATA_HOME/linux-g13-driver/current ]]
    printf '#!/bin/sh\necho "All libraries resolved"\n' > "$base/tools/ldd"
    bash "$release/install.sh" install > "$base/output" 2>&1
    [[ -f $XDG_DATA_HOME/linux-g13-driver/current/release.meta && ! -e $base/runtime-command ]]
}

tests=(test_update_rollback test_corruption_and_metadata test_system_layout
    test_user_layout_and_launchers test_legacy_migration test_platform_and_inventory
    test_history_symlinks_and_permissions test_hardware_and_staging_guards test_runtime_preflight)
if (($# == 0)); then
    for test in "${tests[@]}"; do
        bash "$test_script" "$test"
        printf 'PASS %s\n' "$test"
    done
    printf '%s deployment tests passed (Python is absent from the test PATH).\n' "${#tests[@]}"
    exit 0
fi
[[ " ${tests[*]} " == *" $1 "* ]] || die 'Unknown test'
base=$(mktemp -d /tmp/g13-shell-test-XXXXXX)
trap 'rm -rf -- "$base"' EXIT
trap '[[ ! -f $base/output ]] || cat "$base/output" >&2' ERR
export HOME="$base/desktop user"
export XDG_DATA_HOME="$HOME/custom data" XDG_CONFIG_HOME="$HOME/custom config"
stage=$base/staged
root=$stage$XDG_DATA_HOME/linux-g13-driver
# Use a closed list of existing Linux utilities so accidental Python/runtime
# dependencies fail even on a developer machine where Python is installed.
mkdir -p "$base/tools"
for utility in bash cat chmod cmp cp dirname find grep head install ln mkdir mktemp mv readlink realpath rm rmdir sed sha256sum sort stat uname; do
    ln -s "$(command -v "$utility")" "$base/tools/$utility"
done
for utility in systemctl udevadm; do
    printf '#!/bin/sh\nprintf "called\\n" > "%s"\nexit 99\n' "$base/runtime-command" > "$base/tools/$utility"
    chmod 755 "$base/tools/$utility"
done
printf '#!/bin/sh\nprintf "%%s\\n" "$@"\n' > "$base/tools/java"
chmod 755 "$base/tools/java"
export PATH=$base/tools
make_release g13-test-one
"$1"
