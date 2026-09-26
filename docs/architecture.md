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

## Named profiles and configuration

Both apps use `$XDG_CONFIG_HOME/g13`, falling back to `~/.config/g13`. Existing root-level configuration remains the legacy Default profile. Each imported game has an independent `profiles/<UUID>/` directory with four binding banks, 200 macro slots, `profile.properties` metadata, and an optional normalized PNG icon.

- `bindings-0.properties` through `bindings-2.properties` hold the editable M1–M3 layouts. `bindings-3.properties` remains for storage compatibility. A binding such as `G0=b,1` switches any control to M2; physical M1–M3 receive these actions by default, and M1–MR can all be remapped. Selecting a layout in the GUI only changes the layout being edited.
- `macro-0.properties` through `macro-199.properties` belong to that profile. Driver and GUI legacy defaults still differ; imports explicitly write all banks and macro slots.
- `G0=p,k.17` maps printed G1 to Linux keycode 17. `G0=c,42,17` holds Shift and W until release. `G0=m,2,1` assigns macro 2 with repeat while held. `color=0,0,255` sets blue backlighting.
- Macro sequences use `kd.<code>`, `ku.<code>`, and `d.<milliseconds>`. Repeat 0 runs once, 1 repeats while held; the driver also accepts fixed repeat counts greater than 1.

`LogitechProfileImporter` uses the JDK XML parser to produce a conversion result without writing files. `ProfileStore` stages a complete named profile before atomically exposing it; stable IDs and separation from Swing allow later folder import to reuse these operations. See [Importing Windows profiles](profile-import.md) for supported actions and limits.

The GUI presents profiles in a vertical icon list. List selection chooses the profile being edited. Each profile can store a Linux executable basename; one profile can be the default fallback and one can be persistent. The driver resolves persistent, running-application, default, then legacy fallback rules once per second using `/proc`, resets to bank 0 on selection changes, and snapshots one directory for loading its bank and macros. Reloading clears old assignments, releases passthrough/chord keys, and cancels running macros with balanced key releases. Delay cancellation is checked every 5 ms. Binding reload checks include nanosecond timestamps.

Profiles expose three editable button layouts. Layout changes use the saved `b,0` through `b,2` action type, so M1–M3 and every other G13 control follow the same binding path. The GUI migrates the former implicit M-key defaults to explicit actions. Binding files marked `format=2` can freely remap those physical buttons. The virtual uinput device advertises Linux key codes through `KEY_MAX`, allowing keyboard, mouse-button, media, and system-key passthrough mappings.

Macro-file edits alone still do not trigger reload; switch banks or let the selection rule change profiles and back to reload them. LCD text uses `$XDG_RUNTIME_DIR/g13-lcd`, falling back to `/tmp/g13-lcd`; that pipe accepts text, not profile commands. `g13-driver/src/scripts/g13_monitor.py` is an example LCD producer.

## Planned direction

Folder import, true foreground-window detection, and driver activation acknowledgements are not implemented. Windows executable paths remain reference metadata. Running-process matching is desktop/session independent; foreground detection would need a separately designed desktop backend. Further graphical UI changes remain to be specified.

## Build and development

See [AGENTS.md](../AGENTS.md) for the isolated worktree and build command, and [Release deployment](releases.md) for the versioned payload, installer, and rollback flow. The initial worktree build successfully compiled the C++ driver, packaged the Java GUI, and staged the payload. It did not install or launch the app, and does not establish hardware/runtime correctness.
