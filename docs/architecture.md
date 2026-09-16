# How the G13 app works

## What it does today

This is a Linux userspace driver for the Logitech G13, plus a separate Java Swing configuration app. It maps keypad and thumbstick input to Linux keyboard events, plays keyboard macros, sets RGB backlighting, and renders text on the LCD. A GTK tray menu opens the configuration app or quits the driver; a user systemd unit can run it in the background.

The GUI already provides a clickable keypad image with polygon hit areas, hover information, and selection highlighting. Side panels edit key assignments, color, and macros, including recording key presses/releases and delays.

## Runtime flow and source map

```text
G13 USB reports -> libusb -> G13 actions -> uinput virtual device -> game
Swing editor -> configuration files -> driver binding reload
External LCD script -> named pipe -> driver LCD renderer -> G13 USB
```

| Area | Main source files | Responsibility |
| --- | --- | --- |
| Process and tray | `g13-driver/src/cpp/Main.cpp` | GTK loop, uinput/libusb setup, periodic device discovery, per-device threads, GUI launch via `g13-gui` |
| Device behavior | `g13-driver/src/cpp/G13.cpp` | USB reports, bank selection, binding parsing/reload, thumbstick, backlight, LCD and FIFO |
| Input actions | `G13Action.cpp`, `PassThroughAction.cpp`, `MacroAction.cpp`, `Output.cpp` in the same directory | State transitions, mapped key events, threaded macro playback, shared uinput output |
| Configuration paths | `g13-driver/src/cpp/ConfigPath.cpp` | XDG configuration and LCD pipe paths |
| GUI and storage | `g13-config-tool/src/main/java/com/booker/g13/` | `G13.java` assembles panels; `Configs.java` reads/writes files; `ImageMap.java` and `Key.java` define keypad interactions; `KeybindPanel.java` and `MacroEditorPanel.java` edit settings |

The thumbstick currently defaults to four directional key actions (internal indices 36–39). An absolute-axis branch exists in the driver, but the current configuration parser does not expose a mode setting.

## Configuration and current “profiles”

Both apps use `$XDG_CONFIG_HOME/g13`, falling back to `~/.config/g13`. Configuration is shared by all games and devices:

- `bindings-0.properties` through `bindings-3.properties`: four banks selected by physical M1, M2, M3, and MR. The driver starts with bank 0. Selecting a bank in the GUI changes which bank is edited; it does not command the driver to activate it.
- `macro-0.properties` through `macro-199.properties`: one shared macro collection. The GUI creates missing bank/macro files when loading them. Driver and GUI defaults currently differ.
- A binding such as `G0=p,k.17` maps printed G1 (internal index 0) to Linux keycode 17. `G0=m,2,1` assigns macro 2 with repeat-while-held enabled. `color=0,0,255` sets blue backlighting.
- Macros contain `name` and `sequence`; sequence tokens are `kd.<code>` (press), `ku.<code>` (release), and `d.<milliseconds>` (delay). Repeat 0 runs once, 1 repeats while held; the driver also accepts counts greater than 1, though the GUI exposes only an Auto Repeat checkbox.

The driver polls the active binding file's modification time in its input loop. This is not a general settings channel: it does not watch macro files or receive GUI selection changes. LCD text uses `$XDG_RUNTIME_DIR/g13-lcd`, falling back to `/tmp/g13-lcd`; that pipe accepts text, not profile commands. `g13-driver/src/scripts/g13_monitor.py` is an example LCD producer.

## Limitations relevant to future profiles

There are no named game profiles, application-matching rules, foreground-app detection, or shared active-profile status between the GUI and driver.

Reloading replaces only entries found in the file, so missing assignments can retain an earlier bank's actions. Replacement does not explicitly release held output keys, and stopping a macro does not balance pending key-down events. Reload detection uses second-resolution timestamps and requires a previously recorded nonzero timestamp; edits in the same second or edits to a newly driver-created file may be missed. These are source-review findings, not device-tested results.

## Planned direction (not implemented)

Introduce named game profiles with stable IDs, retaining the four hardware banks within each profile as a possible compatibility model. Define migration for the existing files and decide whether macros stay shared or become profile-specific.

Provide one driver-side activation path for manual selection and future foreground-app selection. It should safely release old inputs, stop macros, load a complete mapping, and report the active profile back to the UI. Establish the target desktop/session before choosing a foreground-detection backend; define fallback and manual override behavior as part of that design.

The graphical UI improvements will be specified later. The existing image map can serve as a starting point, but there is no UI redesign decision yet.

## Build and development

See [AGENTS.md](../AGENTS.md) for the isolated worktree and build command, and [Release deployment](releases.md) for the versioned payload, installer, and rollback flow. The initial worktree build successfully compiled the C++ driver, packaged the Java GUI, and staged the payload. It did not install or launch the app, and does not establish hardware/runtime correctness.
