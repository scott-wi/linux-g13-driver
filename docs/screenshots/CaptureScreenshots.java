package com.booker.g13;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.Properties;
import javax.swing.SwingUtilities;

/** Renders the real Swing panels using isolated, synthetic profile data. */
public final class CaptureScreenshots {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        SwingUtilities.invokeAndWait(() -> {
            try {
                UiTheme.initialize();
                ProfileStore store = new ProfileStore(Configs.getRootDir());
                var profile = store.create("Example game");
                profile = store.update(profile, "example-game", null);
                store.setDefault(profile);
                Configs.selectProfile(store.directory(profile));
                Properties binding = Configs.loadBindings(0);
                binding.setProperty("G0", "m,0,0");
                binding.setProperty("G7", "p,k.17");
                binding.setProperty("G14", "p,k.30");
                binding.setProperty("G15", "p,k.31");
                binding.setProperty("G16", "p,k.32");
                Configs.saveBindings(0, binding);
                Properties macro = new Properties();
                macro.setProperty("id", "0");
                macro.setProperty("name", "Greeting");
                macro.setProperty("type", "text");
                macro.setProperty("text", "Hello, team!\n");
                macro.setProperty("characterDelay", "25");
                macro.setProperty("sequence", TextMacroCodec.sequence("Hello, team!\n", 25));
                Configs.saveMacro(0, macro);

                G13 gui = new G13();
                gui.setSize(1600, 1000);
                gui.addNotify();
                gui.removeNotify();
                ProfileGuiTest.layout(gui);
                ImageMap map = ProfileGuiTest.find(gui, ImageMap.class);
                Point point = map.imagePointToComponent(80, 203);
                map.dispatchEvent(new MouseEvent(map, MouseEvent.MOUSE_CLICKED, 0, 0,
                        point.x, point.y, 1, false, MouseEvent.BUTTON1));
                ProfileGuiTest.render(gui, output.resolve("ConfigTool.png"));

                UiTheme.apply(true);
                SwingUtilities.updateComponentTreeUI(gui);
                ProfileGuiTest.checkbox(gui, "Dark mode").setSelected(true);
                ProfileGuiTest.render(gui, output.resolve("ConfigTool-dark.png"));
                ProfileGuiTest.toggle(gui, "Theatre").doClick();
                ProfileGuiTest.render(gui, output.resolve("ConfigTool-theatre.png"));
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
    }
}
