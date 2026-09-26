package com.booker.g13;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Swing interactions run headless with an isolated XDG_CONFIG_HOME. */
public class ProfileGuiTest {
    static <T> T find(Container parent, Class<T> type) {
        for (Component c : parent.getComponents()) {
            if (type.isInstance(c)) return type.cast(c);
            if (c instanceof Container container) { T found = find(container, type); if (found != null) return found; }
        }
        return null;
    }
    static JButton button(Container parent, String label) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JButton b && b.getText().equals(label)) return b;
            if (c instanceof Container container) { JButton b = button(container, label); if (b != null) return b; }
        }
        return null;
    }
    static JToggleButton toggle(Container parent, String label) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JToggleButton b && (label.equals(b.getText()) || label.equals(b.getName()))) return b;
            if (c instanceof Container container) { JToggleButton b = toggle(container, label); if (b != null) return b; }
        }
        return null;
    }
    static JCheckBox checkbox(Container parent, String label) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JCheckBox box && box.getText().equals(label)) return box;
            if (c instanceof Container container) { JCheckBox box = checkbox(container, label); if (box != null) return box; }
        }
        return null;
    }
    static JMenuItem menuItem(JPopupMenu menu, String label) {
        for (Component component : menu.getComponents())
            if (component instanceof JMenuItem item && label.equals(item.getText())) return item;
        return null;
    }
    static JTextField textField(Container parent, String value) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JTextField field && field.getText().equals(value)) return field;
            if (c instanceof Container container) { JTextField field = textField(container, value); if (field != null) return field; }
        }
        return null;
    }
    static JComboBox<?> layoutBox(Container parent) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JComboBox<?> box && box.getItemCount() == 3 && "M1".equals(box.getItemAt(0))) return box;
            if (c instanceof Container container) { JComboBox<?> box = layoutBox(container); if (box != null) return box; }
        }
        return null;
    }
    static JComboBox<?> combo(Container parent, String firstItem) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JComboBox<?> box && box.getItemCount() > 0 && firstItem.equals(box.getItemAt(0))) return box;
            if (c instanceof Container container) { JComboBox<?> box = combo(container, firstItem); if (box != null) return box; }
        }
        return null;
    }
    static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container next) layout(next); }
    static BufferedImage render(Container component, Path output) throws Exception {
        layout(component);
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        component.printAll(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", output.toFile());
        return image;
    }
    static void checkProfileMenuClicks(ProfileSidebar sidebar) {
        JList<?> profiles = find(sidebar, JList.class);
        profiles.setSize(320, 500);
        for (int row = 0; row < profiles.getModel().getSize(); row++) {
            Rectangle cell = profiles.getCellBounds(row, row);
            int x = cell.x + cell.width - 20, y = cell.y + cell.height / 2;
            for (int id : new int[] {java.awt.event.MouseEvent.MOUSE_PRESSED,
                    java.awt.event.MouseEvent.MOUSE_RELEASED, java.awt.event.MouseEvent.MOUSE_CLICKED}) {
                var event = new java.awt.event.MouseEvent(profiles, id, 0, 0, x, y, 1, false,
                        java.awt.event.MouseEvent.BUTTON1);
                int target = sidebar.profileMenuIndex(event);
                ProfileImportTest.check(target == (id == java.awt.event.MouseEvent.MOUSE_CLICKED ? row : -1),
                        "three-dot menu must open after the completed click, not on press or release");
            }
            for (int id : new int[] {java.awt.event.MouseEvent.MOUSE_PRESSED, java.awt.event.MouseEvent.MOUSE_RELEASED}) {
                var event = new java.awt.event.MouseEvent(profiles, id, 0, 0, 60, y, 1, true,
                        java.awt.event.MouseEvent.BUTTON3);
                ProfileImportTest.check(sidebar.profileMenuIndex(event) == row, "right-click menu trigger failed");
            }
            sidebar.profileMenuIndex(new java.awt.event.MouseEvent(profiles, java.awt.event.MouseEvent.MOUSE_PRESSED,
                    0, 0, 60, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
            ProfileImportTest.check(sidebar.profileMenuIndex(new java.awt.event.MouseEvent(profiles,
                    java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, x, y, 1, false,
                    java.awt.event.MouseEvent.BUTTON1)) == -1, "dragging onto menu activated it");
        }
    }

    static void checkControlsFit(Container container) {
        for (Component child : container.getComponents()) {
            if (!child.isVisible()) continue;
            if (child instanceof AbstractButton || child instanceof JTextField || child instanceof JComboBox<?>) {
                ProfileImportTest.check(child.getX() >= 0 && child.getWidth() > 0
                        && child.getX() + child.getWidth() <= container.getWidth(),
                        "sidebar control exceeds its container: " + child.getClass().getSimpleName());
            }
            if (child instanceof Container next && !(child instanceof JViewport)) checkControlsFit(next);
        }
    }

    static void checkSidebarResize(G13 gui) {
        gui.setSize(1920, 1080);
        layout(gui);
        ProfileSidebar profiles = find(gui, ProfileSidebar.class);
        Container editor = find(gui, KeybindPanel.class).getParent();
        JSplitPane leftSplit = (JSplitPane) profiles.getParent();
        JSplitPane rightSplit = (JSplitPane) editor.getParent();
        ImageMap map = find(gui, ImageMap.class);
        leftSplit.setDividerLocation(430);
        layout(gui);
        rightSplit.setDividerLocation(rightSplit.getWidth() - 470 - rightSplit.getDividerSize());
        layout(gui);
        int leftWidth = profiles.getWidth(), rightWidth = editor.getWidth(), mapWidth = map.getWidth();
        ProfileImportTest.check(leftWidth == 430 && rightWidth == 470, "sidebars did not follow divider locations");
        toggle(gui, "Theatre").doClick();
        layout(gui);
        ProfileImportTest.check(leftSplit.getDividerSize() == 0 && rightSplit.getDividerSize() == 0,
                "hidden sidebars left visible drag dividers");
        toggle(gui, "Profiles").doClick();
        layout(gui);
        toggle(gui, "Editor").doClick();
        layout(gui);
        ProfileImportTest.check(profiles.getWidth() == profiles.getMinimumSize().width
                && editor.getWidth() == editor.getMinimumSize().width,
                "chevrons did not reopen sidebars at their minimum usable widths");
        leftWidth = profiles.getWidth();
        rightWidth = editor.getWidth();
        mapWidth = map.getWidth();
        gui.setSize(1600, 900);
        layout(gui);
        ProfileImportTest.check(map.getWidth() < mapWidth && profiles.getWidth() == leftWidth
                && editor.getWidth() == rightWidth, "window resizing did not allocate space to the preview");
        ProfileImportTest.check(leftSplit.getMinimumDividerLocation() >= profiles.getMinimumSize().width,
                "profile divider permits an unusably narrow sidebar");
        ProfileImportTest.check(rightSplit.getWidth() - rightSplit.getMaximumDividerLocation()
                - rightSplit.getDividerSize() >= editor.getMinimumSize().width,
                "editor divider permits an unusably narrow sidebar");
        gui.setSize(gui.getMinimumSize().width, 720);
        layout(gui);
        ProfileImportTest.check(profiles.getWidth() >= profiles.getMinimumSize().width
                && editor.getWidth() >= editor.getMinimumSize().width, "small window squeezed sidebar controls");
        for (Container sidebar : java.util.List.of(profiles, editor)) checkControlsFit(sidebar);
    }

    static void checkHardwareHighlights(ImageMap map, Path output) throws Exception {
        map.setSize(1100, 900);
        map.clearHardwareState();
        map.setHardwareState(Set.of(), Map.of(), 0);
        BufferedImage before = render(map, output);
        map.setHardwareState(Set.of(0, 3, 30, 35, 36), Map.of(0, 1L, 3, 1L), 10_000_000L);
        BufferedImage pressed = render(map, output);
        Point pixel = map.imagePointToComponent(82, 191);
        ProfileImportTest.check(before.getRGB(pixel.x, pixel.y) != pressed.getRGB(pixel.x, pixel.y),
                "physical press did not change the painted key");
        ProfileImportTest.check(map.hardwareHighlighted(0) && map.hardwareHighlighted(30)
                && map.hardwareHighlighted(35) && map.hardwareHighlighted(36) && !map.hardwareHighlighted(1),
                "hardware highlights did not match physical controls");
        map.setHardwareState(Set.of(), Map.of(0, 1L, 3, 1L), 20_000_000L);
        ProfileImportTest.check(map.hardwareHighlighted(0), "short tap disappeared before it could be seen");
        map.setHardwareState(Set.of(), Map.of(0, 1L, 3, 1L), 200_000_000L);
        ProfileImportTest.check(!map.hardwareHighlighted(0), "released key highlight did not expire");
        map.setHardwareState(Set.of(0), Map.of(0, 1L, 3, 1L), 500_000_000L);
        ProfileImportTest.check(map.hardwareHighlighted(0), "held key stopped highlighting");
        map.setHardwareState(Set.of(), Map.of(0, 1L, 3, 1L), 501_000_000L);
        ProfileImportTest.check(!map.hardwareHighlighted(0), "long hold did not clear on release");
        map.setHardwareState(Set.of(), Map.of(0, 2L, 3, 1L), 600_000_000L);
        ProfileImportTest.check(map.hardwareHighlighted(0), "tap entirely between polls was missed");
        map.clearHardwareState();
        map.setHardwareState(Set.of(), Map.of(0, 2L), 700_000_000L);
        ProfileImportTest.check(!map.hardwareHighlighted(0), "old press history flashed after reconnect");
        map.clearHardwareState();
    }

    static void checkHardwareLayouts(G13 gui) throws Exception {
        ImageMap map = find(gui, ImageMap.class);
        KeybindPanel editor = find(gui, KeybindPanel.class);
        ProfileSidebar profiles = find(gui, ProfileSidebar.class);
        String profileId = profiles.selected().id();
        JComboBox<?> layouts = layoutBox(gui);
        gui.setSize(1920, 1080);
        layout(gui);
        byte[][] before = new byte[3][];
        for (int i = 0; i < 3; i++) before[i] = Files.readAllBytes(Configs.getConfigDir().resolve("bindings-" + i + ".properties"));
        previewClick(map, map.imagePointToComponent(80, 203), 1, java.awt.event.MouseEvent.BUTTON1);
        ProfileImportTest.check(!gui.isFocusOwner(), "test unexpectedly has focus");
        gui.applyDriverState(new DriverState.Snapshot(profileId, 1, "100"));
        ProfileImportTest.check(layouts.getSelectedIndex() == 1
                && Key.getKeyFor(0).getMappedValue().equals(JavaToLinuxKeymapping.cKeyCodeToString(32))
                && textField(editor, JavaToLinuxKeymapping.cKeyCodeToString(32)) != null,
                "background M2 change did not update preview and selected-key editor");
        layouts.setSelectedIndex(0);
        gui.applyDriverState(new DriverState.Snapshot(profileId, 1, "100"));
        ProfileImportTest.check(layouts.getSelectedIndex() == 0, "unchanged poll overwrote manual editing layout");
        gui.applyDriverState(new DriverState.Snapshot(profileId, 1, "101"));
        ProfileImportTest.check(layouts.getSelectedIndex() == 1, "reselecting the hardware layout did not synchronize");
        toggle(gui, "Theatre").doClick();
        gui.applyDriverState(new DriverState.Snapshot("default", 2, "102"));
        ProfileImportTest.check(layouts.getSelectedIndex() == 2 && toggle(gui, "Theatre").isSelected()
                && profiles.selected().id().equals(profileId)
                && Key.getKeyFor(0).getMappedValue().equals(JavaToLinuxKeymapping.cKeyCodeToString(18)),
                "hardware change did not update theatre or unexpectedly changed the editing profile");
        gui.applyDriverState(new DriverState.Snapshot(profileId, 0, ""));
        ProfileImportTest.check(layouts.getSelectedIndex() == 0, "legacy driver layout changes stopped working");
        for (int i = 0; i < 3; i++) ProfileImportTest.check(Arrays.equals(before[i],
                Files.readAllBytes(Configs.getConfigDir().resolve("bindings-" + i + ".properties"))),
                "hardware layout synchronization rewrote bindings");
        toggle(gui, "Profiles").doClick();
        toggle(gui, "Editor").doClick();
    }

    static void previewClick(Component target, Point point, int count, int button) {
        target.dispatchEvent(new java.awt.event.MouseEvent(target, java.awt.event.MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, point.x, point.y, count, false, button));
    }

    static void checkPreviewDoubleClick(G13 gui) {
        gui.setSize(1920, 1080);
        layout(gui);
        ImageMap map = find(gui, ImageMap.class);
        ProfileSidebar profiles = find(gui, ProfileSidebar.class);
        KeybindPanel bindings = find(gui, KeybindPanel.class);
        Container editor = bindings.getParent();
        toggle(gui, "Theatre").doClick();
        layout(gui);
        Point blank = new Point(2, map.getHeight() / 2);
        previewClick(map, blank, 1, java.awt.event.MouseEvent.BUTTON1);
        previewClick(map, blank, 2, java.awt.event.MouseEvent.BUTTON3);
        ProfileImportTest.check(!editor.isVisible(), "single/right click opened the editor");
        previewClick(map, blank, 2, java.awt.event.MouseEvent.BUTTON1);
        layout(gui);
        ProfileImportTest.check(editor.isVisible() && !profiles.isVisible()
                && editor.getWidth() == editor.getMinimumSize().width, "blank preview double-click did not open only the editor");
        toggle(gui, "Editor").doClick();
        layout(gui);
        previewClick(map, map.imagePointToComponent(80, 203), 2, java.awt.event.MouseEvent.BUTTON1);
        layout(gui);
        ProfileImportTest.check(editor.isVisible() && checkbox(bindings, "Switch layout").isEnabled(),
                "key double-click did not select a key and open its editor");
        JSplitPane split = (JSplitPane) editor.getParent();
        split.setDividerLocation(split.getWidth() - 470 - split.getDividerSize());
        layout(gui);
        previewClick(map, blank, 2, java.awt.event.MouseEvent.BUTTON1);
        layout(gui);
        ProfileImportTest.check(editor.getWidth() == 470, "double-click resized an already open editor");
        toggle(gui, "Editor").doClick();
        layout(gui);
        previewClick(map.getParent(), new Point(2, 2), 2, java.awt.event.MouseEvent.BUTTON1);
        layout(gui);
        ProfileImportTest.check(editor.isVisible(), "preview border double-click did not open the editor");
        toggle(gui, "Profiles").doClick();
    }

    static void checkTheatre(G13 gui, Path output) throws Exception {
        ImageMap map = find(gui, ImageMap.class);
        ProfileSidebar profiles = find(gui, ProfileSidebar.class);
        Container editor = find(gui, KeybindPanel.class).getParent();
        JToggleButton theatre = toggle(gui, "Theatre");
        JToggleButton left = toggle(gui, "Profiles");
        JToggleButton right = toggle(gui, "Editor");
        gui.setSize(1920, 1080);
        layout(gui);
        int normalWidth = map.getWidth();
        double normalKeyWidth = map.imagePointToComponent(100, 203).distance(map.imagePointToComponent(60, 203));
        theatre.doClick();
        layout(gui);
        ProfileImportTest.check(!profiles.isVisible() && !editor.isVisible() && theatre.isSelected(),
                "theatre did not hide both sidebars");
        ProfileImportTest.check(map.getWidth() > normalWidth
                && map.imagePointToComponent(100, 203).distance(map.imagePointToComponent(60, 203)) > normalKeyWidth,
                "theatre did not enlarge the keypad");
        for (Dimension size : java.util.List.of(new Dimension(1920, 1080), new Dimension(1100, 720), new Dimension(1200, 1600))) {
            gui.setSize(size);
            layout(gui);
            ProfileImportTest.check(!theatre.isVisible() && left.isVisible() && right.isVisible(),
                    "theatre button must hide while sidebar handles remain visible");
            Point leftHandle = SwingUtilities.convertPoint(left, 0, 0, gui);
            Point rightHandle = SwingUtilities.convertPoint(right, 0, 0, gui);
            Point imageOrigin = SwingUtilities.convertPoint(map, 0, 0, gui);
            ProfileImportTest.check(leftHandle.x < imageOrigin.x
                    && rightHandle.x >= imageOrigin.x + map.getWidth(), "sidebar handles are not docked to preview edges");
            ProfileImportTest.check(map.imagePointToComponent(0, 203).x >= 0
                    && map.imagePointToComponent(ImageMap.G13_KEYPAD.getIconWidth(), 203).x <= map.getWidth(),
                    "focused view clips the chassis width");
            for (Key key : Key.getAllMasks()) {
                Rectangle bounds = key.getShape().getBounds();
                Point topLeft = map.imagePointToComponent(bounds.x, bounds.y);
                Point bottomRight = map.imagePointToComponent(bounds.x + bounds.width, bounds.y + bounds.height);
                ProfileImportTest.check(topLeft.x >= 0 && topLeft.y >= 0
                        && bottomRight.x <= map.getWidth() && bottomRight.y <= map.getHeight(),
                        "focused view cropped an assignable control");
                Point center = map.imagePointToComponent(bounds.getCenterX(), bounds.getCenterY());
                ProfileImportTest.check(map.keyAtComponent(center) == key, "focused hit regions no longer track keys");
            }
        }
        gui.setSize(1920, 1080);
        render(gui, output);
        left.doClick();
        ProfileImportTest.check(profiles.isVisible() && !editor.isVisible() && theatre.isVisible(),
                "left chevron did not reopen profiles and restore the theatre button");
        ProfileImportTest.check("Hide profiles sidebar".equals(left.getToolTipText())
                && "Show editor sidebar".equals(right.getToolTipText()), "sidebar actions do not match visibility");
        right.doClick();
        ProfileImportTest.check(profiles.isVisible() && editor.isVisible(), "right chevron did not reopen editor");
        left.doClick();
        right.doClick();
        ProfileImportTest.check(theatre.isSelected() && !theatre.isVisible(), "hiding both sidebars did not enter theatre");
        right.doClick();
        ProfileImportTest.check(!profiles.isVisible() && editor.isVisible() && !theatre.isSelected(),
                "editor cannot be restored independently from theatre");
        left.doClick();

    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                UiTheme.apply(false);
                Properties validState = new Properties();
                validState.setProperty("profile", "default");
                validState.setProperty("layout", "2");
                ProfileImportTest.check(DriverState.parse(validState).orElseThrow().layout() == 2,
                        "valid driver layout state was not accepted");
                validState.setProperty("layout-event", "12345");
                ProfileImportTest.check("12345".equals(DriverState.parse(validState).orElseThrow().layoutEvent()),
                        "driver layout event was not parsed");
                validState.setProperty("pressed", "0,29,39");
                validState.setProperty("press-events", "123," + "0,".repeat(38) + "456");
                var inputState = DriverState.parse(validState).orElseThrow();
                ProfileImportTest.check(inputState.pressedKeys().equals(Set.of(0, 29, 39))
                        && inputState.pressEvents().equals(Map.of(0, 123L, 39, 456L)), "hardware input state parsing failed");
                validState.setProperty("pressed", "40");
                ProfileImportTest.check(DriverState.parse(validState).isEmpty(), "invalid hardware key accepted");
                validState.remove("pressed");
                validState.remove("press-events");
                validState.setProperty("layout", "7");
                ProfileImportTest.check(DriverState.parse(validState).isEmpty(), "invalid driver layout state was accepted");
                // Initialize the legacy files as a real first launch would.
                new G13();
                byte[] legacy = Files.readAllBytes(Configs.getRootDir().resolve("bindings-0.properties"));
                Properties[] banks = new Properties[4];
                for (int i = 0; i < 4; i++) { banks[i] = new Properties(); banks[i].setProperty("color", "255,255,255"); }
                banks[0].setProperty("G0", "c,42,17");
                banks[0].setProperty("G1", "m,0,0");
                banks[1].setProperty("G0", "p,k.32");
                banks[2].setProperty("G0", "p,k.18");
                Properties textMacro = new Properties();
                textMacro.setProperty("id", "0");
                textMacro.setProperty("name", "Imported greeting");
                textMacro.setProperty("type", "text");
                textMacro.setProperty("text", "hello\n");
                textMacro.setProperty("characterDelay", "0");
                textMacro.setProperty("sequence", TextMacroCodec.sequence("hello\n", 0));
                var store = new ProfileStore(Configs.getRootDir());
                var saved = store.save(new LogitechProfileImporter.Result("Example game", "synthetic", java.util.List.of(), banks,
                    java.util.List.of(textMacro), java.util.List.of(), 1));
                BufferedImage sourceIcon = new BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB);
                Graphics2D sourceGraphics = sourceIcon.createGraphics();
                sourceGraphics.setColor(Color.MAGENTA);
                sourceGraphics.fillRect(0, 0, 300, 100);
                sourceGraphics.dispose();
                Path iconSource = Path.of(args[0]).resolveSibling("source-icon.png");
                ImageIO.write(sourceIcon, "png", iconSource.toFile());
                saved = store.update(saved, "/games/example-game", iconSource);
                ProfileImportTest.check(saved.icon() != null && ImageIO.read(saved.icon().toFile()).getWidth() == 128,
                        "icon was not normalized into profile storage");
                G13 gui = new G13();
                ProfileSidebar sidebar = find(gui, ProfileSidebar.class);
                JList<?> selector = find(sidebar, JList.class);
                selector.setSelectedValue(saved, true);
                JTextField profileName = textField(sidebar, "Example game");
                profileName.setText("Renamed example");
                button(sidebar, "Save details").doClick();
                saved = store.find(saved.id());
                ProfileImportTest.check(saved.name().equals("Renamed example"), "profile name edit was not saved");
                ProfileImportTest.check(Configs.getConfigDir().equals(store.directory(saved)), "selection did not change editor storage");
                ProfileImportTest.check(store.defaultProfile().id().equals("default"), "editing selection changed default");
                ProfileImportTest.check(store.persistentProfile().isEmpty(), "editing selection enabled persistence");
                ProfileImportTest.check(saved.applications().equals(java.util.List.of("example-game")), "application basename was not saved");
                MacroEditorPanel editor = find(gui, MacroEditorPanel.class);
                JComboBox<?> macroSelector = find(editor, JComboBox.class);
                ProfileImportTest.check(macroSelector.isEnabled(), "imported macros cannot be selected");
                JTextField macroName = find(editor, JTextField.class);
                ProfileImportTest.check(macroName.isEnabled() && macroName.isEditable(), "imported macro slot zero is incorrectly protected");
                macroName.setText("Edited imported macro");
                macroName.postActionEvent();
                ProfileImportTest.check("Edited imported macro".equals(Configs.loadMacro(0).getProperty("name")), "imported macro edit not saved");
                ProfileImportTest.check("Edited imported macro".equals(ImageMap.bindingLabel(Key.getKeyFor(1))),
                        "macro rename did not refresh its keypad label");
                JComboBox<?> macroType = combo(editor, "Keystrokes");
                ProfileImportTest.check(macroType.getSelectedIndex() == 1,
                        "imported text macro did not open in the text editor");
                JTextArea textEditor = find(editor, JTextArea.class);
                textEditor.setText("Updated!\n");
                button(editor, "Save text").doClick();
                Properties editedText = Configs.loadMacro(0);
                ProfileImportTest.check("Updated!\n".equals(editedText.getProperty("text"))
                        && editedText.getProperty("sequence").endsWith("kd.28,ku.28"), "text macro editor did not save runnable output");
                macroSelector.setSelectedIndex(1);
                macroType.setSelectedIndex(1);
                textEditor.setText("Created in Linux\n");
                button(editor, "Save text").doClick();
                ProfileImportTest.check("text".equals(Configs.loadMacro(1).getProperty("type"))
                        && "Created in Linux\n".equals(Configs.loadMacro(1).getProperty("text")),
                        "GUI could not create a new text macro");
                KeybindPanel bindings = find(gui, KeybindPanel.class);
                combo(bindings, "Mapped keys").setSelectedIndex(1);
                ProfileImportTest.check("absolute".equals(Configs.loadBindings(0).getProperty("stick")),
                        "joystick mode editor did not save analog mode");
                bindings.setSelectedKey(Key.getKeyFor(0));
                ProfileImportTest.check("c,42,17".equals(Configs.loadBindings(0).getProperty("G0")), "viewing chord changed binding");
                checkbox(bindings, "Switch layout").doClick();
                layoutBox(bindings).setSelectedIndex(2);
                ProfileImportTest.check("b,2".equals(Configs.loadBindings(0).getProperty("G0")), "arbitrary button layout switch not saved");
                ProfileImportTest.check(button(sidebar, "New…") != null && button(sidebar, "Import…") != null,
                        "new and import actions are not below the profile list");
                ProfileImportTest.check(checkbox(sidebar, "Persistent profile") == null,
                        "persistent control still appears in profile details");
                JComboBox<?> editorLayout = layoutBox(gui);
                editorLayout.setSelectedIndex(2);
                checkProfileMenuClicks(sidebar);
                JPopupMenu profileMenu = sidebar.profileMenu(saved);
                menuItem(profileMenu, "Set Default").doClick();
                ProfileImportTest.check(store.defaultProfile().id().equals(saved.id()), "default button failed");
                JCheckBoxMenuItem persistence = (JCheckBoxMenuItem) menuItem(sidebar.profileMenu(saved), "Set Persistent");
                persistence.doClick();
                ProfileImportTest.check(store.persistentProfile().orElseThrow().id().equals(saved.id()), "persistent toggle failed");
                ProfileImportTest.check(editorLayout.getSelectedIndex() == 2,
                        "persistent profile selection unexpectedly reset the editing layout");
                ProfileImportTest.check(menuItem(sidebar.profileMenu(store.find("default")), "Delete").isEnabled() == false,
                        "existing-bindings profile can be deleted");
                ProfileImportTest.check(menuItem(sidebar.profileMenu(saved), "Delete").isEnabled(),
                        "named profile delete action is disabled");
                selector.setSelectedValue(store.find("default"), true);
                ProfileImportTest.check(Arrays.equals(legacy, Files.readAllBytes(Configs.getRootDir().resolve("bindings-0.properties"))), "switching damaged legacy bindings");
                selector.setSelectedValue(saved, true);
                bindings.setSelectedKey(Key.getKeyFor(0));
                ImageMap imageMap = find(gui, ImageMap.class);
                for (Dimension size : java.util.List.of(new Dimension(491, 710), new Dimension(820, 500), new Dimension(360, 760))) {
                    imageMap.setSize(size);
                    Point keyCenter = imageMap.imagePointToComponent(80, 203);
                    ProfileImportTest.check(imageMap.keyAtComponent(keyCenter) == Key.getKeyFor(0),
                            "resized keypad hit region no longer tracks its image");
                }
                BufferedImage measure = new BufferedImage(100, 30, BufferedImage.TYPE_INT_RGB);
                Graphics2D mg = measure.createGraphics();
                mg.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                FontMetrics metrics = mg.getFontMetrics();
                for (String value : java.util.List.of("A very long macro name", "Long text block ".repeat(200), "😀".repeat(30))) {
                    for (int width : new int[] {0, 5, 20, 45, 90}) {
                        String fitted = ImageMap.elide(value, metrics, width);
                        ProfileImportTest.check(metrics.stringWidth(fitted) <= width, "label exceeds available width");
                        ProfileImportTest.check(fitted.isEmpty() || fitted.endsWith("…"), "long label lacks ellipsis");
                    }
                }
                mg.dispose();
                ProfileImportTest.check("G1".equals(ImageMap.keyName(Key.getKeyFor(0))), "physical label is off by one");
                ProfileImportTest.check("M1".equals(ImageMap.keyName(Key.getKeyFor(29))), "layout key label is incorrect");
                for (Key key : Key.getAllMasks()) {
                    Rectangle area = ImageMap.labelBounds(key.getShape(), 12);
                    ProfileImportTest.check(area.isEmpty() || key.getShape().contains(area), "label escapes a slanted key");
                }
                Key.getKeyFor(0).setMappedValue("Macro: A very long macro name with a text block\nsecond line");
                ProfileImportTest.check(!ImageMap.bindingLabel(Key.getKeyFor(0)).contains("\n"), "multiline label was not normalized");
                Key.getKeyFor(1).setMappedValue("W");
                Key.getKeyFor(2).setMappedValue("Chord: Shift + W");
                Key.getKeyFor(3).setMappedValue("Macro: /dancenord");
                Key.getKeyFor(4).setMappedValue("Space");
                Key.getKeyFor(5).setMappedValue("Macro: Heal");
                Key.getKeyFor(6).setMappedValue("Macro: Inventory");
                imageMap.setSize(1100, 1200);
                Path referencePath = Path.of(args[0]).resolveSibling("g13-keypad-reference.png");
                render(imageMap, referencePath);
                Point hover = imageMap.imagePointToComponent(80, 203);
                String tooltip = imageMap.getToolTipText(new java.awt.event.MouseEvent(imageMap,
                        java.awt.event.MouseEvent.MOUSE_MOVED, 0, 0, hover.x, hover.y, 0, false));
                ProfileImportTest.check(tooltip.contains("A very long macro name") && tooltip.contains("G1</b>"),
                        "hover description lost the full binding or physical key name");
                gui.setSize(gui.getPreferredSize());
                BufferedImage light = render(gui, Path.of(args[0]));
                checkbox(sidebar, "Dark mode").doClick();
                ProfileImportTest.check(UiTheme.isDark(), "dark-mode control did not apply the theme");
                ProfileImportTest.check(Files.readString(Configs.getRootDir().resolve("ui.properties")).contains("theme=dark"),
                        "dark-mode preference was not persisted");
                SwingUtilities.updateComponentTreeUI(gui);
                Path darkPath = Path.of(args[0]).resolveSibling("g13-ui-dark.png");
                BufferedImage dark = render(gui, darkPath);
                ProfileImportTest.check(light.getRGB(1, 1) != dark.getRGB(1, 1), "dark mode did not change the application surface");
                checkHardwareHighlights(imageMap, Path.of(args[0]).resolveSibling("g13-hardware-pressed.png"));
                checkHardwareLayouts(gui);
                checkPreviewDoubleClick(gui);
                checkSidebarResize(gui);
                checkTheatre(gui, Path.of(args[0]).resolveSibling("g13-theatre-dark.png"));
                UiTheme.apply(false);
                SwingUtilities.updateComponentTreeUI(gui);
                checkTheatre(gui, Path.of(args[0]).resolveSibling("g13-theatre-light.png"));
                System.out.println("Headless GUI profiles, themes, menus, theatre, spacing, and resize alignment tests passed.");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
