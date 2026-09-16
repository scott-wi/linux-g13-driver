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
    static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container next) layout(next); }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                // Initialize the legacy files as a real first launch would.
                new G13();
                byte[] legacy = Files.readAllBytes(Configs.getRootDir().resolve("bindings-0.properties"));
                Properties[] banks = new Properties[4];
                for (int i = 0; i < 4; i++) { banks[i] = new Properties(); banks[i].setProperty("color", "255,255,255"); }
                banks[0].setProperty("G0", "c,42,17");
                var store = new ProfileStore(Configs.getRootDir());
                var saved = store.save(new LogitechProfileImporter.Result("Example game", "synthetic", java.util.List.of(), banks,
                    java.util.List.of(), java.util.List.of(), 1));
                G13 gui = new G13();
                JComboBox<?> selector = find(gui, JComboBox.class);
                selector.setSelectedItem(saved);
                ProfileImportTest.check(Configs.getConfigDir().equals(store.directory(saved)), "selection did not change editor storage");
                ProfileImportTest.check(store.active().equals(ProfileStore.DEFAULT), "editing selection activated driver");
                MacroEditorPanel editor = find(gui, MacroEditorPanel.class);
                JComboBox<?> macroSelector = find(editor, JComboBox.class);
                ProfileImportTest.check(macroSelector.isEnabled(), "imported macros cannot be selected");
                JTextField macroName = find(editor, JTextField.class);
                ProfileImportTest.check(macroName.isEnabled() && macroName.isEditable(), "imported macro slot zero is incorrectly protected");
                macroName.setText("Edited imported macro");
                macroName.postActionEvent();
                ProfileImportTest.check("Edited imported macro".equals(Configs.loadMacro(0).getProperty("name")), "imported macro edit not saved");
                KeybindPanel bindings = find(gui, KeybindPanel.class);
                bindings.setSelectedKey(Key.getKeyFor(0));
                ProfileImportTest.check("c,42,17".equals(Configs.loadBindings(0).getProperty("G0")), "viewing chord changed binding");
                button(gui, "Use profile").doClick();
                ProfileImportTest.check(store.active().equals(saved), "Use profile failed");
                selector.setSelectedItem(ProfileStore.DEFAULT);
                ProfileImportTest.check(Arrays.equals(legacy, Files.readAllBytes(Configs.getRootDir().resolve("bindings-0.properties"))), "switching damaged legacy bindings");
                selector.setSelectedItem(saved);
                bindings.setSelectedKey(Key.getKeyFor(0));
                gui.setSize(gui.getPreferredSize());
                layout(gui);
                BufferedImage image = new BufferedImage(gui.getWidth(), gui.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                gui.printAll(graphics);
                graphics.dispose();
                ImageIO.write(image, "png", Path.of(args[0]).toFile());
                System.out.println("Headless GUI profile selection, activation and preservation tests passed.");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
