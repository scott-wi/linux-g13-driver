# How the G13 app works

## What it does today

This is a Linux userspace driver for the Logitech G13, plus a separate Java Swing configuration app. It maps keypad and thumbstick input to keyboard, mouse-button, media/system-key, and analog joystick events, plays key-event and text macros, sets RGB backlighting, and renders text on the LCD. A GTK tray menu opens the configuration app or quits the driver; a user systemd unit can run it in the background.

The GUI already provides a clickable keypad image with polygon hit areas, hover information, and selection highlighting. The enlarged keypad shows binding labels inside the buttons, truncates long names with an ellipsis, and keeps full descriptions available on hover. Labels follow the profile and layout being edited and update when bindings or macro names are saved. Draggable dividers resize both sidebars while the preview responds to the remaining space. Chevron handles on the left and right preview edges hide or show each sidebar independently. Reopening a sidebar uses its minimum usable width, computed from its controls; divider dragging cannot shrink it below that width. Theatre hides both sidebars and its own button; the edge handles remain available to reopen either sidebar. Double-clicking the preview opens the right editor at its minimum usable width without changing the profiles sidebar. Double-clicking a key also selects it for editing. The focused image fits all assignable controls (including the thumbstick and top buttons), preserving the full chassis width while cropping vertically. Hiding both sidebars individually also enters this focused view. Side panels edit key assignments, color, and macros, including recording key presses/releases and delays.

Dropdowns use a shared themed Swing UI that opens mouse-triggered popups only after the click completes. This covers the layout selectors, joystick mode, macro selectors/type, and dialog dropdowns; keyboard navigation and popup-list selection remain standard Swing behavior.

## Runtime flow and source map

```text
G13 USB reports -> libusb -> G13 actions -> uinput keyboard/pointer/joystick -> game
Swing editor -> configuration files -> driver binding reload
External LCD script -> named pipe -> driver LCD renderer -> G13 USB
```

| Area | Main source files | Responsibility |
| --- | --- | --- |
| Process and tray | `g13-driver/src/cpp/Main.cpp` | GTK loop, uinput/libusb setup, periodic device discovery, per-device threads, GUI launch via `g13-gui` |
| Device behavior | `g13-driver/src/cpp/G13.cpp` | USB reports, bank selection, binding parsing/reload, thumbstick, backlight, LCD and FIFO |
| Input actions | `G13Action.cpp`, `PassThroughAction.cpp`, `ChordAction.h`, `MacroAction.cpp`, `Output.cpp` in the same directory | State transitions, held keys/chords, threaded macro playback, routing to separate uinput devices |
| Configuration paths | `g13-driver/src/cpp/ConfigPath.cpp` | XDG configuration and LCD pipe paths |
| GUI and storage | `g13-config-tool/src/main/java/com/booker/g13/` | `G13.java` assembles panels; `Configs.java` reads/writes files; `ImageMap.java` and `Key.java` define keypad interactions; `KeybindPanel.java` and `MacroEditorPanel.java` edit settings |

The thumbstick defaults to four directional key actions (internal indices 36–39). Each layout can select **Mapped keys** (`stick=keys`) or **Analog joystick** (`stick=absolute`) in the GUI. Analog mode emits raw 0–255 X/Y values; it has no configurable deadzone or calibration. Directional-key thresholds are <=96 and >=160. Reloading releases old key actions, but analog-axis recentering on mode/profile changes is not implemented; see [#8](https://github.com/scott-wi/linux-g13-driver/issues/8).

## Named profiles and configuration

Both apps use `$XDG_CONFIG_HOME/g13`, falling back to `~/.config/g13`. Existing root-level configuration remains the legacy Default profile. Each imported game has an independent `profiles/<UUID>/` directory with four binding banks, 200 macro slots, `profile.properties` metadata, and an optional normalized PNG icon.

- `bindings-0.properties` through `bindings-2.properties` hold the editable M1–M3 layouts. `bindings-3.properties` remains for storage compatibility. A binding such as `G0=b,1` switches any control to M2; physical M1–M3 receive these actions by default, and M1–MR can all be remapped. Selecting a layout in the GUI only changes the layout being edited. The GUI polls the driver state every 50 ms and follows hardware layout selections even while a game has focus, updating the preview and selected key’s editor. Each hardware layout action publishes a new `layout-event` token, including reselecting the active layout; repeated polls and configuration reloads do not override manual GUI layout choices. This changes the viewed layout, not the selected editing profile. Older drivers without the token still report layout changes. Driver state also publishes held physical controls and per-key press timestamps. The preview lightly highlights them in yellow while held, with a short 180 ms pulse so taps between polls remain visible. This includes unassigned buttons and joystick directions; generated macro keystrokes do not light unrelated physical buttons. Driver input snapshots are written on transitions, not continuously while idle.
- `macro-0.properties` through `macro-199.properties` belong to that profile. Driver and GUI legacy defaults still differ; imports explicitly write all banks and macro slots. Binding edits save automatically; profile details require **Save details**, macro names require Enter, and text content/delay require **Save text**.
- `G0=p,k.17` maps printed G1 to Linux keycode 17. `G0=c,42,17` holds Shift and W until release. `G0=m,2,1` assigns macro 2 with repeat while held. `color=0,0,255` sets blue backlighting. `stick=absolute` exposes the thumbstick as analog X/Y axes; `stick=keys` uses its four configurable direction bindings.
- Macro sequences use `kd.<code>`, `ku.<code>`, and `d.<milliseconds>`. Repeat 0 runs once, 1 repeats while held; the driver also accepts fixed repeat counts greater than 1. Editable text macros store `type=text`, `text`, and `characterDelay` metadata while keeping a generated key-event `sequence` for the driver.

`LogitechProfileImporter` uses the JDK XML parser to produce a conversion result without writing files. `ProfileStore` stages a complete named profile before atomically exposing it; stable IDs and separation from Swing allow later folder import to reuse these operations. See [Importing Windows profiles](profile-import.md) for supported actions and limits.

The GUI presents profiles in a vertical icon list. List selection chooses the profile being edited. Each profile can store a Linux executable basename; one profile can be the default fallback and one can be persistent. The driver resolves persistent, running-application, default, then legacy fallback rules once per second using `/proc`, retains the current layout on profile changes, and snapshots one directory for loading its layout and macros. Reloading clears old assignments, releases passthrough/chord keys, and cancels running macros with balanced key releases. Delay cancellation is checked every 5 ms. Binding reload checks include nanosecond timestamps.

Profiles expose three editable button layouts. Layout changes use the saved `b,0` through `b,2` action type, so M1–M3 and every other G13 control follow the same binding path. The GUI migrates the former implicit M-key defaults to explicit actions. Binding files marked `format=2` can freely remap those physical buttons. `Output.cpp` creates separate **G13 Keyboard**, **G13 Pointer**, and **G13 Joystick** devices. Keyboard/media/system codes route to the keyboard, mouse buttons to the pointer, and absolute axes/gamepad buttons to the joystick; synchronization events go to all three. Manual mouse-button bindings are supported, but Logitech `mousefunction` import and mouse-wheel output are not.

Macro-file edits alone still do not trigger reload; switch banks or let the selection rule change profiles and back to reload them. LCD text uses `$XDG_RUNTIME_DIR/g13-lcd`, falling back to `/tmp/g13-lcd`; that pipe accepts text, not profile commands. `g13-driver/src/scripts/g13_monitor.py` is an example LCD producer.

## Planned direction

Folder import, true foreground-window detection, and driver activation acknowledgements are not implemented. The GUI reads a driver state file for layout/input feedback, but it has no freshness/health check or acknowledgement of a requested profile change. Windows executable paths remain reference metadata. Running-process matching uses `/proc` without a desktop-specific backend; foreground detection would need a separately designed backend. See [the issue audit](issue-audit.md) for implemented portions and remaining acceptance criteria.

## Build and development

See [AGENTS.md](../AGENTS.md) for development instructions and [Release deployment](releases.md) for the versioned payload, installer, and rollback flow. `make all` builds both apps and packages a release without installing it. `make test` runs isolated deployment/downloader tests, Java importer/storage/headless GUI tests, and native action tests with mocked USB/uinput; these do not establish physical-device correctness.
