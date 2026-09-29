# Keycodes and printed button labels

The left side of a binding identifies a G13 control. The numeric value on the
right is a Linux input-event code. These are separate numbering systems:

```properties
# Printed G20 sends the Linux T key position.
G19=p,k.20
```

Printed G1-G22 use internal indices G0-G21. Physical M1, M2, M3, and MR use
G29-G32. Thumb buttons/press use G33-G35, and joystick up, left, right, and down
use G36-G39. See `Key.java` and `Constants.h` for the full control map.

Use **Choose…** in the GUI for supported keyboard, mouse-button, media, and system
inputs. When editing files, use decimal codes from your installed
`/usr/include/linux/input-event-codes.h`; do not use Java key codes or X11 keycodes.

| Linux symbol | Decimal code | Example use |
| --- | --- | --- |
| `KEY_ESC` | 1 | Escape |
| `KEY_TAB` | 15 | Tab |
| `KEY_W` | 17 | W key position |
| `KEY_T` | 20 | T key position |
| `KEY_Y` | 21 | US Y / German Z position |
| `KEY_ENTER` | 28 | Enter |
| `KEY_LEFTCTRL` | 29 | Left Ctrl |
| `KEY_LEFTSHIFT` | 42 | Left Shift |
| `KEY_Z` | 44 | US Z / German Y position |
| `KEY_SPACE` | 57 | Space |
| `KEY_UP`, `KEY_LEFT`, `KEY_RIGHT`, `KEY_DOWN` | 103, 105, 106, 108 | Arrow keys |
| `KEY_VOLUMEDOWN`, `KEY_VOLUMEUP` | 114, 115 | Volume |
| `KEY_PLAYPAUSE` | 164 | Media playback |
| `BTN_LEFT`, `BTN_RIGHT`, `BTN_MIDDLE` | 272, 273, 274 | Mouse buttons |
| `BTN_THUMB` | 289 | Imported joystick press |

Linux symbol names do not change with the desktop keyboard layout: `KEY_Y` is
always 21 and `KEY_Z` is always 44. The desktop determines the character produced
by those positions. Text macros currently generate US-keyboard positions, not
layout-independent Unicode text.

The older [German-layout PDF](Eventcodes_for_Mapping.pdf) is retained as a legacy
reference. Its Y/Z annotations describe German key legends, not different kernel
symbol definitions, and it omits the current mouse/media/gamepad choices. Prefer
this guide, the GUI chooser, and the installed kernel header for current mappings.
