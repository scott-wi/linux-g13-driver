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

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                UiTheme.apply(false);
                Properties validState = new Properties();
                validState.setProperty("profile", "default");
                validState.setProperty("layout", "2");
                ProfileImportTest.check(DriverState.parse(validState).orElseThrow().layout() == 2,
                        "valid driver layout state was not accepted");
                validState.setProperty("layout", "7");
                ProfileImportTest.check(DriverState.parse(validState).isEmpty(), "invalid driver layout state was accepted");
                // Initialize the legacy files as a real first launch would.
                new G13();
                byte[] legacy = Files.readAllBytes(Configs.getRootDir().resolve("bindings-0.properties"));
                Properties[] banks = new Properties[4];
                for (int i = 0; i < 4; i++) { banks[i] = new Properties(); banks[i].setProperty("color", "255,255,255"); }
                banks[0].setProperty("G0", "c,42,17");
                banks[0].setProperty("G1", "m,0,0");
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
                System.out.println("Headless GUI profiles, themes, spacing, and resize alignment tests passed.");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
