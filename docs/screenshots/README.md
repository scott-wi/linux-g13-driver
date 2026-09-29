# Documentation screenshots

The documentation images show the actual Swing panels from main at `818de12`
(2026-09-29), with a synthetic **Example game** profile and **Greeting** text macro.
They are rendered directly by Swing, without window-manager decorations or image
retouching. Device status is **unavailable** because no driver is connected.

From the repository root, regenerate all three PNGs with the installed JDK 17+:

```sh
bash docs/screenshots/capture.sh
```

The script compiles current application sources and reuses the headless GUI test
helpers. It isolates both configuration and runtime state in a temporary directory,
does not start a driver or access the device, and adds no dependencies. Review all
three images after regeneration; Swing rendering and fonts may differ across hosts.

- `../ConfigTool.png`: light appearance, profiles, keypad, bindings, and text editor.
- `../ConfigTool-dark.png`: the same app state in dark appearance.
- `../ConfigTool-theatre.png`: dark theatre view with both sidebars hidden.

The obsolete file-manager images `image.png` and `image2.png` were removed; launching
the installed app is documented with `g13-gui` instead.
