# Building and deploying releases

The build produces a self-contained release directory and `.tar.gz` archive in
`dist/`. Each release contains the native driver, Java GUI, relocatable launchers,
service template, device rules, installer, and a manifest with file checksums.
The directory name includes the app version, build distro/version, architecture,
and a content digest. `dist/latest` points to the most recently built release.

## Build once

From the repository root (or `g13-driver/src`):

```sh
make dependencies    # Optional: explicitly install build dependencies with sudo
make all             # Build driver + GUI, assemble release, create archive
make test            # Temporary-directory deployment tests; no device access
```

Building never installs system packages or starts services. `make -j all` also
works: release assembly waits for both builds. Python 3.10+ is required for the
release tooling; runtime deployment needs Python 3.10+, Java 17+, and the native
libraries linked by the driver (libusb, GTK3, AppIndicator and C++ runtime).
Use your distro's packages; native archives are specific to their build platform,
not universal Linux binaries. The installer checks distro/version and architecture
and, for live installs, checks Java and native shared libraries before deployment.
`--allow-platform-mismatch` is an explicit override, not a compatibility guarantee.

## Install a release

After building, choose one scope:

```sh
make install-user                         # Deploy for the current desktop user
make install-user DEPLOY_FLAGS=--activate # Also enable/restart their service
sudo make install                         # Deploy for all users; no root user service
```

Install targets only consume `dist/latest`; they never rebuild or copy directly
from compiler output. Build first if it does not exist. To select another payload,
pass `RELEASE=/absolute/path/to/extracted-release`.

An end user can instead extract a matching release archive, enter its directory,
and run these commands **without the repository, make, CMake, or Maven**:

```sh
python3 install.py install --scope user --activate
# Or, for a system-wide installation:
sudo python3 install.py install --scope system
```

For system-wide installation, each desktop user activates the service separately:

```sh
systemctl --user daemon-reload
systemctl --user enable g13.service
systemctl --user restart g13.service
```

Add `~/.local/bin` to PATH for user commands if your desktop has not done so.
The service uses an absolute installed path, and the driver launcher puts its own
release's GUI on PATH, so tray launches use the matching GUI.

## Installed layout

| Item | System-wide | User-local |
| --- | --- | --- |
| Releases and current/previous links | `/usr/local/lib/linux-g13-driver/` | `$XDG_DATA_HOME/linux-g13-driver/` (default `~/.local/share/linux-g13-driver/`) |
| Command links | `/usr/local/bin/` | `~/.local/bin/` |
| User service | `/etc/systemd/user/g13.service` | `$XDG_CONFIG_HOME/systemd/user/g13.service` (default `~/.config/systemd/user/g13.service`) |
| Device rules | `/etc/udev/rules.d/99-g13.rules` | Same system rule, installed separately |
| Bindings/macros | Existing per-user configuration | Existing per-user configuration |

The same local-administrator/XDG layout works across the supported distro
families (Fedora, Debian/Ubuntu, Arch, openSUSE); distro differences affect package
dependencies and binary compatibility, not arbitrary application folder names.
These are standalone local releases, not distro-owned RPM/DEB packages. See the
[Filesystem Hierarchy Standard](https://www.debian.org/doc/packaging-manuals/fhs/fhs-3.0.html)
and [systemd unit search paths](https://github.com/systemd/systemd/blob/main/man/systemd.unit.xml).

## Device permissions and migration

A user installation does not invoke sudo. If device permissions are not already
configured, run this once from the extracted release:

```sh
sudo python3 install.py hardware
```

This installs the udev rule and reloads rules; reconnect the G13 if needed.
System installation writes the same rule; run `sudo udevadm control --reload-rules`
and reconnect the device when first setting it up. Existing permissions may
already be sufficient. No install command triggers or takes over the device itself.

For a legacy installation with conflicting command files or a custom `g13.service`,
the installer stops before switching releases. Review the conflict, then use
`--replace-legacy` (or `DEPLOY_FLAGS="--replace-legacy --activate"` for a user make
install). Conflicting files are retained with a `.g13-before-release` suffix.
Existing backups are never overwritten. A user-local service overrides a system
unit: when switching scopes, review/remove that old override and reload the user
service manager. Older files in `/usr/bin` or elsewhere are not automatically
removed. Bindings and macros are never copied, overwritten, or deleted by deployment.

## Updates and rollback

Install the next extracted release with the same command. Deployment verifies the
payload, copies it to a versioned directory, registers paths to `current`, and
atomically switches that link only after the payload is ready. The old release
remains under `releases/` and is recorded as `previous`. Reinstalling the same
release preserves rollback history. Concurrent deployments share an install lock.

```sh
g13-release status --scope user
g13-release rollback --scope user --activate
# System-wide equivalents:
sudo g13-release status --scope system
sudo g13-release rollback --scope system
```

Restart the user service after system-wide updates/rollback. Without `--activate`,
an existing driver process continues running its previous executable until it is
restarted. If activation fails, the newly selected release remains installed;
inspect `journalctl --user -u g13` or roll back. Registration and activation are not
a full filesystem transaction, and deployment does not perform hardware health checks.
Checksums detect accidental payload changes; they are not publisher signatures.
Old releases are retained; no automatic pruning or network update downloader is included.

## Staging and cleanup

```sh
make install DESTDIR=/tmp/g13-system-image
make install-user DESTDIR=/tmp/g13-user-image DEPLOY_FLAGS=--activate
```

`DESTDIR` prepends a filesystem staging root. Installed links and service paths
still refer to their final locations, and all service/device commands are suppressed
(including `--activate`). It is useful for inspection and deployment tests, not a
claim that native package generation is implemented. An empty DESTDIR means a live
install; `/` is rejected as a staging root.

`make clean` removes compiler output but keeps release archives. `make stage` is a
compatibility alias for `make release`; the old `.stage` folder is no longer used.
