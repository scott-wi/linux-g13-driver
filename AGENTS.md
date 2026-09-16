# Development notes

## Workspace and scope

- Develop in `/home/scott/src/linux-g13-driver-worktree` (currently `codex/worktree-build`). The main checkout at `/home/scott/src/linux-g13-driver` supports the user's installed driver; preserve it and its pending changes.
- The user owns a G13. Building is authorized; installing, restarting the live service, or taking control of the device requires an explicit request for that action.
- Current priorities: named profiles for multiple games, automatic selection from the foreground application, then a more interactive graphical UI. These are planned features, not implemented behavior. UI details remain to be discussed.
- Read [docs/architecture.md](docs/architecture.md) for the current behavior and extension points.

## Build and verification

Run from the worktree root:

```sh
make all
make test
```

This builds the C++ driver and Java GUI and assembles a versioned release plus archive in `dist/`. Building does not install packages; `make dependencies` is explicit. Root targets delegate to `g13-driver/src/Makefile`. See [docs/releases.md](docs/releases.md) for deployment, migration, rollback, and DESTDIR staging.

C++ uses C++17, CMake, libusb, GTK3, and AppIndicator. The Swing GUI targets Java 17 and builds with Maven. Release tooling uses Python 3.10+. The all target skips Java tests. `make test` runs deployment integration tests in temporary directories with no service/device access; distinguish those checks from actual hardware validation.

Do not commit generated `build/`, `target/`, `.stage/`, or `dist/` artifacts. Release launchers resolve their own location. Launching the GUI still writes real user configuration unless `XDG_CONFIG_HOME` is isolated. Use DESTDIR for deployment checks; even `--activate` is suppressed in staged installs.

## Code conventions and contracts

- Keep USB/input handling in `g13-driver/src/cpp`; keep Swing editing and persistence in `g13-config-tool/src/main/java/com/booker/g13`.
- Configuration is a shared C++/Java contract. Update both readers/writers when changing it and preserve existing users' bindings and macros.
- Internal G-key indices are zero-based, unlike the printed G1–G22 labels. Check `Constants.h` and `Key.java` before changing mappings.
- Profile transitions need explicit handling of held keys and running macros. Do not assume the current reload code safely resets them.
- Keep documentation clear about implemented behavior versus proposals. Do not choose a foreground-detection backend before establishing the user's desktop/session requirements.
