# Issue audit, 2026-09-29

Reviewed all 15 open issues in `scott-wi/linux-g13-driver` against main at
[`818de12`](https://github.com/scott-wi/linux-g13-driver/commit/818de121bfc328f2791dc5b0fd9d20a3107d2905).
`make all` and `make test` passed on Fedora 44 x86_64. Tests cover 10 deployment
scenarios, 20 preview-installer scenarios, Java import/storage/headless Swing,
and native actions with mocked USB/uinput. No installation, live service restart,
or physical-device validation was performed.

No open issue is fully verified against all of its acceptance criteria. In
particular, Scott confirmed that #7 should remain open for hardware verification.
Implemented portions should not be mistaken for completed end-to-end validation.

| Issue | Current evidence | Remaining work / disposition |
| --- | --- | --- |
| [#2: legacy user-local upgrades](https://github.com/scott-wi/linux-g13-driver/issues/2) | `test_deploy.sh` covers isolated legacy conflict/backup behavior. | Real legacy user-local migration, scope switching, activation, and recovery matrix remain unverified. Keep open. |
| [#3: apt distributions](https://github.com/scott-wi/linux-g13-driver/issues/3) | Dependency helper includes apt package mappings. | Debian/Ubuntu clean-install and upgrade matrix has no recorded validation. Keep open. |
| [#4: pacman distributions](https://github.com/scott-wi/linux-g13-driver/issues/4) | Dependency helper includes pacman mappings. | Arch and derivative clean-install and upgrade matrix remains unverified. Keep open. |
| [#5: zypper distributions](https://github.com/scott-wi/linux-g13-driver/issues/5) | Dependency helper includes zypper mappings. | Leap/Tumbleweed clean-install and upgrade matrix remains unverified. Keep open. |
| [#6: folder import](https://github.com/scott-wi/linux-g13-driver/issues/6) | Converter/storage can be reused, but GUI accepts one XML and parser requires one profile. | Folder preview, per-file results, cancellation, and duplicate policy are not implemented. Keep open. |
| [#7: text blocks](https://github.com/scott-wi/linux-g13-driver/issues/7) | PR #23 added `convertText`, `TextMacroCodec`, editable text macros, timing, and warnings. Java/native tests cover conversion, GUI persistence, and macro cancellation. | Physical playback and switching/cancellation need verification; broaden edge-case coverage as needed. Keep open per Scott. |
| [#8: analog joystick](https://github.com/scott-wi/linux-g13-driver/issues/8) | PR #23 added import, per-layout selector, `stick=absolute`, and gamepad press. Native tests check mode and button output. | Axis reset on mode/profile transitions, deadzone semantics, unsupported settings, and physical-axis verification remain. Keep open. |
| [#9: mouse import](https://github.com/scott-wi/linux-g13-driver/issues/9) | Manual mouse-button bindings route through the pointer device. | Importer still rejects `mousefunction`, including middle click. Wheel/click variants and hardware verification remain. Keep open. |
| [#10: bank/system commands](https://github.com/scott-wi/linux-g13-driver/issues/10) | M1–M3 functions convert to `b,0`–`b,2`; arbitrary controls use the same transition path and tests cover held-key cleanup. | Other Logitech function commands remain unsupported despite manual media/system-key choices. Keep open. |
| [#11: toggle/repeating keystrokes](https://github.com/scott-wi/linux-g13-driver/issues/11) | Balanced multikey macros support one-shot/while-held repeat. | Toggle repeat and repeating single keystrokes are still rejected. Keep open. |
| [#12: conversion coverage](https://github.com/scott-wi/linux-g13-driver/issues/12) | Text, M1–M3, and joystick conversion were added. | Import key table is still bounded; unknown/unbalanced macro events remain unsupported. Broader inventory/fixtures are outstanding. Keep open. |
| [#13: chord editor](https://github.com/scott-wi/linux-g13-driver/issues/13) | Held imported chords load and display correctly. | No modifier/primary-key editor exists; choosing another binding replaces the chord. Keep open. |
| [#14: application selection](https://github.com/scott-wi/linux-g13-driver/issues/14) | Executable association, `/proc` matching, deterministic priority, Default and Persistent controls, and synthetic-process tests are implemented. | Foreground-window selection, explicit activation acknowledgement, and desktop/session validation in the original criteria remain. Keep open. |
| [#15: Lua](https://github.com/scott-wi/linux-g13-driver/issues/15) | Importer reports scripts without executing them. | Supported API/runtime model, execution limits, and fixtures remain unimplemented. Keep open. |
| [#16: implicit defaults](https://github.com/scott-wi/linux-g13-driver/issues/16) | Imports install explicit physical M1–M3 switches and keep a fourth compatibility file. | Logitech missing/backup/inherited-assignment semantics are unverified; other omitted bindings stay unassigned. Keep open. |

Primary evidence is in `LogitechProfileImporter.java`, `TextMacroCodec.java`,
`ProfileStore.java`, `ProfileSidebar.java`, `KeybindPanel.java`, `DriverState.java`,
and the Java tests under `g13-config-tool/src/`; native behavior is in
`ConfigPath.cpp`, `G13.cpp`, `Output.cpp`, and `g13-driver/src/tests/`.
The text/joystick/layout import implementation was merged in
[PR #23](https://github.com/scott-wi/linux-g13-driver/pull/23); persistent controls
were restored in [PR #26](https://github.com/scott-wi/linux-g13-driver/pull/26).
