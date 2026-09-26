# Importing Windows G13 profiles

Build and deploy both the driver and GUI from this version. Older installed drivers do not understand the profile selector or chord bindings. Building with `make all` does not change the installed driver.

1. Open the new GUI and choose **Import…** below the profile list.
2. Select one Logitech Gaming Software `.xml` export.
3. Review the assignment count and warnings. Cancel makes no configuration changes.
4. Import saves a separate named profile and opens it for editing. Duplicate names receive a suffix; previous profiles are preserved.
5. Optionally edit its name, choose an icon, then enter the Linux executable name used to match the profile. A full path is reduced to its executable basename.
6. Open a profile's three-dot menu and choose **Set Default** for the fallback when no configured application is running. Enable or disable **Persistent profile** in the selected profile's details, or toggle **Set Persistent** in its three-dot menu, to keep that profile selected regardless of running applications. The menu can also delete named profiles; the existing-bindings profile is protected.

Imported M1–M3 layouts receive explicit switch assignments on the physical M keys. Choose a layout above the keypad to edit it. Any control can switch to M1, M2, or M3, and the M keys can be reassigned like other controls. **Choose…** under Pass through includes keyboard keys, mouse buttons, media controls, and common system keys.

Selecting a profile in the left list changes which profile is being edited; it does not change the driver's selection rule. Stop macro recording before changing the editing profile. The driver re-evaluates rules once per second and resets to bank 0 when the selected profile changes.

## Conversion

The importer reads active G13 assignments only, ignoring backup assignments and mappings belonging to mice, keyboards, or headsets. Windows M1–M3 become layouts 0–2; layout 3 remains empty for storage compatibility. Imported profiles explicitly map physical M1–M3 to the corresponding layouts, while all M buttons and MR remain available for reassignment. Unspecified assignments remain unassigned rather than guessing Logitech's implicit defaults.

Supported actions:

- Standard keyboard keys, including modifiers, arrows, function keys F1–F12, and numeric keypad keys.
- Key combinations such as Shift+W, held until the physical G13 button is released. The GUI displays imported chords; selecting a passthrough key or macro replaces them. A chord editor is not included yet.
- Balanced key-down/key-up macros with delays up to 60 seconds per step. Single playback and repeat while held are supported, including repeat delays.
- Text blocks using printable US-keyboard characters, tab, and newline. They remain editable as Text macros in the macro editor; newline sends Enter, and an optional per-character delay can be set.
- M1, M2, and M3 functions, imported as editable layout-switch bindings.
- Joystick actions on G25–G29, imported as per-layout analog joystick mode with the stick press exposed as a gamepad button. The GUI's Joystick mode selector can switch a layout between analog axes and mapped direction keys.
- Per-bank backlight colors.

Printed G1–G22 map to internal indices 0–21. Logitech G23–G25 map to thumb buttons/joystick press (33–35). G26–G29 map clockwise from up to the driver's directional keys (36, 38, 39, 37). Validate these controls on your device as part of testing.

Unsupported assigned actions are left unassigned and listed in the preview: unsupported mouse/DPI commands, toggle repeat, repeating single-keystroke actions, unknown keys, non-US text characters, and unsupported macro events. Lua scripts are never executed. Windows executable paths are saved as reference metadata and do not create Linux application matching rules.

Only one profile per XML is accepted. Malformed XML, non-Logitech documents, ambiguous duplicate assignments, and exports without G13 assignments are rejected. XML DTDs/external entities are disabled; the source size limit is 4 MiB. Import leaves the source export unchanged.

## Storage and testing

Profiles live in `$XDG_CONFIG_HOME/g13/profiles/<UUID>/` (normally `~/.config/g13/profiles/`). Each has four binding files, its own macro collection, `profile.properties`, and an optional normalized 128-pixel `profile-icon.png`. The root-level legacy files remain intact. `default-profile` and `persistent-profile` contain stable IDs and are replaced atomically.

The driver resolves selection in this order: persistent profile; first alphabetically named profile whose configured executable is running; designated default; legacy Default. It examines `/proc/<pid>/exe` and command-line basenames, which covers native programs and many Wine/Proton launches. Matching is exact and case-sensitive. This is running-process detection, not foreground-window detection. If several configured applications run together, alphabetical profile order is deterministic.

`make test` uses temporary configuration, a synthetic `/proc`, mocked USB/uinput, and headless Swing. It covers XML security, mapping, backups, warnings, duplicate imports, icon normalization, default/persistent rules, running-app selection, legacy preservation, held chords, macro cancellation, clearing stale bindings, and live profile changes. No new runtime dependency is introduced.

To additionally validate local exports without importing into your actual configuration:

```sh
bash g13-driver/src/tests/test_profiles.sh "$PWD/example-profiles"
```

The supplied folder currently yields 44 convertible G13 profiles, five malformed files, and two profiles for other devices. These are parser/conversion checks, not hardware validation. Personal example exports are not added to this change. Folder import and true foreground-window selection remain future work; the converter and storage APIs are independent of the file chooser for that purpose.
