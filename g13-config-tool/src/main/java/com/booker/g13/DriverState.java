package com.booker.g13;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/** Read the active profile and M-layout published by the running driver. */
final class DriverState {
    record Snapshot(String profileId, int layout, String layoutEvent) {}

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
            if ((profile.equals("default") || profile.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
                    && layout >= 0 && layout <= 2) return Optional.of(new Snapshot(profile, layout, state.getProperty("layout-event", "")));
        } catch (NumberFormatException ignored) {}
        return Optional.empty();
    }
}
