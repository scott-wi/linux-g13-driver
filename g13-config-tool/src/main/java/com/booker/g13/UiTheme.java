package com.booker.g13;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;

/** Shared colors, type, spacing, and persistent light/dark appearance. */
public final class UiTheme {
    private static final Path SETTINGS = Configs.getRootDir().resolve("ui.properties");
    private static boolean dark;

    private UiTheme() { }

    public static void initialize() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception error) {
            System.err.println("Could not initialize the cross-platform look and feel: " + error.getMessage());
        }
        apply(loadPreference());
    }

    public static boolean isDark() { return dark; }
    public static Color accent() { return color(dark ? 0x70A5FF : 0x2563A6); }
    public static Color muted() { return color(dark ? 0xAAB4C3 : 0x5F6B7A); }
    public static Color warning() { return color(dark ? 0xFFB86B : 0xB54708); }
    public static Color outline() { return color(dark ? 0x79B8FF : 0x1F6FB2); }
    public static Color selectedFill() { return new Color(59, 130, 246, dark ? 105 : 78); }
    public static Color hoverFill() { return new Color(45, 212, 191, dark ? 90 : 68); }
    public static String mutedHex() { return dark ? "#AAB4C3" : "#5F6B7A"; }

    public static Border sectionBorder(String title) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(8, 10, 10, 10));
    }

    public static void setDark(boolean value, Window window) {
        apply(value);
        savePreference(value);
        if (window != null) {
            SwingUtilities.updateComponentTreeUI(window);
            window.invalidate();
            window.validate();
            window.repaint();
        }
    }

    static void apply(boolean value) {
        dark = value;
        Color background = color(value ? 0x151922 : 0xF3F5F7);
        Color surface = color(value ? 0x202631 : 0xFFFFFF);
        Color input = color(value ? 0x171C25 : 0xFFFFFF);
        Color text = color(value ? 0xE8EDF5 : 0x17202A);
        Color muted = muted();
        Color border = color(value ? 0x3A4658 : 0xC8D0DA);
        Color selection = color(value ? 0x315F9E : 0xC9DDF5);
        Color selectionText = color(value ? 0xFFFFFF : 0x10243D);
        Color disabled = color(value ? 0x778397 : 0x87919E);

        UIManager.put("control", new ColorUIResource(surface));
        UIManager.put("info", new ColorUIResource(surface));
        UIManager.put("nimbusBase", new ColorUIResource(accent()));
        UIManager.put("text", new ColorUIResource(text));
        UIManager.put("textText", new ColorUIResource(text));
        UIManager.put("textHighlight", new ColorUIResource(selection));
        UIManager.put("textHighlightText", new ColorUIResource(selectionText));
        UIManager.put("window", new ColorUIResource(background));
        UIManager.put("windowText", new ColorUIResource(text));

        putColor(background, "Panel.background", "OptionPane.background", "ScrollPane.background", "Viewport.background");
        putColor(surface, "Button.background", "ToggleButton.background", "ComboBox.background", "List.background",
                "Table.background", "Menu.background", "MenuItem.background",
                "PopupMenu.background", "ToolTip.background");
        putColor(background, "CheckBox.background", "RadioButton.background");
        putColor(input, "TextField.background", "FormattedTextField.background", "TextArea.background",
                "PasswordField.background", "EditorPane.background", "TextField.inactiveBackground",
                "FormattedTextField.inactiveBackground", "ComboBox.disabledBackground");
        putColor(text, "Label.foreground", "Button.foreground", "ToggleButton.foreground", "CheckBox.foreground",
                "RadioButton.foreground", "ComboBox.foreground", "List.foreground", "Table.foreground",
                "TextField.foreground", "FormattedTextField.foreground", "TextArea.foreground", "EditorPane.foreground",
                "TitledBorder.titleColor", "ToolTip.foreground", "Menu.foreground", "MenuItem.foreground", "controlText");
        putColor(muted, "Label.disabledForeground", "Button.disabledText", "CheckBox.disabledText",
                "RadioButton.disabledText", "ComboBox.disabledForeground", "TextField.inactiveForeground");
        putColor(border, "Separator.foreground", "Separator.background", "Button.shadow", "controlShadow",
                "TextField.shadow", "ComboBox.buttonShadow");
        putColor(selection, "List.selectionBackground", "Table.selectionBackground", "ComboBox.selectionBackground");
        putColor(selectionText, "List.selectionForeground", "Table.selectionForeground", "ComboBox.selectionForeground");
        Color gradientTop = color(value ? 0x313B4B : 0xFFFFFF);
        Color gradientBottom = color(value ? 0x222A36 : 0xE5EAF0);
        List<Object> gradient = List.of(0.25f, 0.0f, new ColorUIResource(gradientTop),
                new ColorUIResource(gradientTop), new ColorUIResource(gradientBottom));
        UIManager.put("Button.gradient", gradient);
        UIManager.put("ToggleButton.gradient", gradient);

        Font base = new Font("SansSerif", Font.PLAIN, 14);
        HashMap<TextAttribute, Object> attributes = new HashMap<>();
        attributes.putAll(base.getAttributes());
        attributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        FontUIResource font = new FontUIResource(base.deriveFont(attributes));
        for (Object key : UIManager.getDefaults().keySet())
            if (key.toString().endsWith(".font")) UIManager.put(key, font);
        UIManager.put("TitledBorder.font", new FontUIResource(font.deriveFont(Font.BOLD)));

        UIManager.put("Button.margin", new InsetsUIResource(7, 14, 7, 14));
        UIManager.put("ToggleButton.margin", new InsetsUIResource(7, 12, 7, 12));
        UIManager.put("TextField.margin", new InsetsUIResource(6, 8, 6, 8));
        UIManager.put("ComboBox.padding", new InsetsUIResource(5, 8, 5, 8));
        UIManager.put("List.cellNoFocusBorder", BorderFactory.createEmptyBorder(5, 8, 5, 8));
    }

    private static void putColor(Color color, String... keys) {
        ColorUIResource resource = new ColorUIResource(color);
        for (String key : keys) UIManager.put(key, resource);
    }

    private static Color color(int rgb) { return new Color(rgb); }

    private static boolean loadPreference() {
        String override = System.getProperty("g13.theme", System.getenv("G13_THEME"));
        if (override != null && !override.isBlank()) return override.toLowerCase(Locale.ROOT).startsWith("dark");
        Properties settings = new Properties();
        if (Files.isRegularFile(SETTINGS)) try (var input = Files.newInputStream(SETTINGS)) {
            settings.load(input);
            return "dark".equalsIgnoreCase(settings.getProperty("theme"));
        } catch (IOException error) {
            System.err.println("Could not read appearance preference: " + error.getMessage());
        }
        String gtkTheme = System.getenv("GTK_THEME");
        return gtkTheme != null && gtkTheme.toLowerCase(Locale.ROOT).contains("dark");
    }

    private static void savePreference(boolean value) {
        try {
            Files.createDirectories(SETTINGS.getParent());
            Path temporary = Files.createTempFile(SETTINGS.getParent(), ".ui-", ".tmp");
            try {
                Files.writeString(temporary, "theme=" + (value ? "dark" : "light") + "\n");
                try {
                    Files.move(temporary, SETTINGS, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException error) {
                    Files.move(temporary, SETTINGS, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException error) {
            System.err.println("Could not save appearance preference: " + error.getMessage());
        }
    }
}
