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
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                UiTheme.apply(false);
                // Initialize the legacy files as a real first launch would.
                new G13();
                byte[] legacy = Files.readAllBytes(Configs.getRootDir().resolve("bindings-0.properties"));
                Properties[] banks = new Properties[4];
                for (int i = 0; i < 4; i++) { banks[i] = new Properties(); banks[i].setProperty("color", "255,255,255"); }
                banks[0].setProperty("G0", "c,42,17");
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
                JPopupMenu profileMenu = sidebar.profileMenu(saved);
                menuItem(profileMenu, "Set Default").doClick();
                ProfileImportTest.check(store.defaultProfile().id().equals(saved.id()), "default button failed");
                JCheckBoxMenuItem persistence = (JCheckBoxMenuItem) menuItem(sidebar.profileMenu(saved), "Set Persistent");
                persistence.doClick();
                ProfileImportTest.check(store.persistentProfile().orElseThrow().id().equals(saved.id()), "persistent toggle failed");
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
