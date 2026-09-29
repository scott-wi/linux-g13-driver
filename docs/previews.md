# Fedora previews

The `Fedora preview` GitHub Action builds the driver and GUI in Fedora 44, runs the tests, and verifies the packaged runtime in a second clean Fedora image without compilers or Maven. Pull requests produce downloadable workflow artifacts. Successful pushes to `main` publish a GitHub prerelease; manual runs can publish previews of other branches.

Targets initially supported: **DNF-managed Fedora 44, x86_64**. Other Fedora versions, architectures, derivatives and Atomic/OSTree editions stop with an explanatory error. A build targeting the exact distro/version is required; this is not a universal Linux archive.

## Install or upgrade

The repository is public. Run from your normal desktop terminal and close the G13 GUI. This downloads the root installer fully before running it; no GitHub token is required. It requires curl; if missing, install it with `sudo dnf install curl`.

```bash
(
  set -e
  temp=$(mktemp)
  trap 'rm -f -- "$temp"' EXIT
  curl -q --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
    'https://raw.githubusercontent.com/scott-wi/linux-g13-driver/main/install.sh' -o "$temp"
  bash "$temp"
)
```

At least one matching main preview must have finished publishing. Repeating the command upgrades to the newest published main preview; it does not rebuild source. Check [published previews](https://github.com/scott-wi/linux-g13-driver/releases) when diagnosing a missing release.

### Optional authentication

Authentication can help with GitHub API rate limits and is required for private forks. A token needs **Contents: read** permission. For the origin repository:

```bash
(
  set -e
  read -rsp 'GitHub token (Contents: read): ' GH_TOKEN; printf '\n'
  export GH_TOKEN
  [[ $GH_TOKEN =~ ^[A-Za-z0-9_]+$ ]] || exit 1
  temp=$(mktemp -d)
  trap 'rm -rf -- "$temp"' EXIT
  (umask 077; printf 'Authorization: Bearer %s\n' "$GH_TOKEN" > "$temp/auth")
  curl -q --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
    --header "@$temp/auth" --header 'Accept: application/vnd.github.raw+json' \
    --output "$temp/install.sh" \
    'https://api.github.com/repos/scott-wi/linux-g13-driver/contents/install.sh?ref=main'
  bash "$temp/install.sh"
)
```

If GitHub CLI is already installed and authenticated, this alternative uses that existing session; installing `gh` is not required:

```bash
(
  set -e
  temp=$(mktemp)
  trap 'rm -f -- "$temp"' EXIT
  gh api 'repos/scott-wi/linux-g13-driver/contents/install.sh?ref=main' \
    -H 'Accept: application/vnd.github.raw+json' > "$temp"
  bash "$temp"
)
```

## Options and dependency behavior

For a locally downloaded/root installer:

```bash
bash install.sh --check                       # Read-only platform/runtime checks
bash install.sh                               # Install/upgrade newest main preview
bash install.sh --scope user                  # Explicit user-local deployment
bash install.sh --tag preview-branch-RUN-TRY  # Use an exact published preview tag
bash install.sh --yes                         # Allow DNF's missing-package transaction
```

Run without sudo; the script requests elevation only for packages/system deployment/device rules. DNF normally asks you to confirm its transaction; `--yes` supplies `-y`. It checks curl, jq, tar/gzip, libusb1, GTK3 and AppIndicator. It checks the actual `java` on PATH, requires Java 17+, locates its desktop/AWT native library and checks that library's linkage. JVM-internal library paths are supplied only for this probe.

When Java needs installation, Fedora resolves **`java >= 17`**, including desktop support. Builds request **`java-devel >= 17`** for the JDK. `java-latest-openjdk` is a rolling package that can contain early-access builds, and a fixed `java-17-openjdk` name is not available on every Fedora release. Maven compiles against Java 17's API/bytecode with `--release 17` even when the build JDK is newer. Other distros require their own verified package mapping before support is enabled. Fedora capability guidance: [Java packaging](https://docs.fedoraproject.org/en-US/packaging-guidelines/Java/), [current java-latest package](https://packages.fedoraproject.org/pkgs/java-latest-openjdk/java-latest-openjdk/index.html).

The script rechecks dependencies after installation. If PATH or alternatives still select an old/headless/broken Java, it stops and explains how to select a working Java; it does not globally rewrite Java alternatives. Before deployment it also checks the downloaded driver's actual native dependencies with `ldd`.

## Deployment and recovery

- Default scope is system-wide unless an existing managed user-local service is detected. Explicitly selecting system scope while a user override exists stops rather than installing an ineffective service.
- Archive and payload checksums, archive paths/types, and platform metadata are checked before deployment. Private downloads use GitHub's authenticated release asset API; tokens are not placed in download URLs, command arguments or installed files.
- The release installer keeps immutable payload directories plus `current` and `previous` links. Bindings, macros and named profiles remain in the existing user configuration directory.
- The script sets device rules, loads `uinput`, refreshes udev, imports desktop display variables, enables/restarts the user service, and verifies the process stays active for six seconds. A successful process check is not proof that a physical device works; test the G13 and GUI afterward.
- Legacy manual installations and G13-specific service overrides require review/migration first. Use the existing `reinstall.sh` migration flow for supported legacy system installs; legacy user migration remains tracked separately. Reinstalling a current managed preview is supported.
- A failed startup returns an error. It does not automatically roll back or delete configuration. Inspect `journalctl --user -u g13.service -n 50 --no-pager`, then roll back a system installation with `sudo /usr/local/bin/g13-release rollback --scope system` followed by `systemctl --user daemon-reload` and `systemctl --user restart g13.service`. For user scope use `~/.local/bin/g13-release rollback --scope user --activate`. A first install has no previous release to roll back to.

## Publication details

Prerelease tags are `preview-main-<run-id>-<attempt>` or `preview-branch-<run-id>-<attempt>`. Each is created as a draft, receives the archive/checksum and installer/checksum assets, then is published. A failed upload leaves a draft that the installer ignores. Branch previews require explicit `--tag`; they cannot unexpectedly replace the normal main channel. Releases are not marked as the latest stable release.

Build jobs have read-only repository access; only the separate publication job has `contents: write`. Pull-request runs never publish releases. Action versions are pinned to commits. GitHub references: [release assets API](https://docs.github.com/en/rest/releases/assets) and [workflow permissions](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#permissions).
