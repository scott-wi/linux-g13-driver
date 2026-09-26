package com.booker.g13;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;

/** Read the active profile and M-layout published by the running driver. */
final class DriverState {
    record Snapshot(String profileId, int layout, String layoutEvent, Set<Integer> pressedKeys,
                    Map<Integer, Long> pressEvents) {
        Snapshot(String profileId, int layout, String layoutEvent) {
            this(profileId, layout, layoutEvent, Set.of(), Map.of());
        }
        Snapshot {
            pressedKeys = Set.copyOf(pressedKeys);
            pressEvents = Map.copyOf(pressEvents);
        }
    }

    static Path path() {
        String runtime = System.getenv("XDG_RUNTIME_DIR");
        return runtime == null || runtime.isBlank()
                ? Configs.getRootDir().resolve("driver-state.properties")
                : Path.of(runtime).resolve("g13-state.properties");
    }

    static Optional<Snapshot> read() {
        Properties state = new Properties();
        try (InputStream input = Files.newInputStream(path())) { state.load(input); }
        catch (IOException error) { return Optional.empty(); }
        return parse(state);
    }

    static Optional<Snapshot> parse(Properties state) {
        String profile = state.getProperty("profile", "").strip();
        try {
            int layout = Integer.parseInt(state.getProperty("layout", ""));
            Set<Integer> pressed = new HashSet<>();
            String rawPressed = state.getProperty("pressed", "").strip();
            if (!rawPressed.isEmpty()) for (String value : rawPressed.split(",")) {
                int key = Integer.parseInt(value);
                if (key < 0 || key >= 40) return Optional.empty();
                pressed.add(key);
            }
            Map<Integer, Long> events = new HashMap<>();
            String rawEvents = state.getProperty("press-events", "").strip();
            if (!rawEvents.isEmpty()) {
                String[] values = rawEvents.split(",");
                if (values.length != 40) return Optional.empty();
                for (int key = 0; key < values.length; key++) {
                    long stamp = Long.parseLong(values[key]);
                    if (stamp < 0) return Optional.empty();
                    if (stamp != 0) events.put(key, stamp);
                }
            }
            if ((profile.equals("default") || profile.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
                    && layout >= 0 && layout <= 2) return Optional.of(new Snapshot(profile, layout, state.getProperty("layout-event", ""), pressed, events));
        } catch (NumberFormatException ignored) {}
        return Optional.empty();
    }
}
