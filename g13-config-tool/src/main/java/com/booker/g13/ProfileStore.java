package com.booker.g13;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Named profiles do not overwrite the legacy configuration or one another. */
public final class ProfileStore {
    public record Profile(String id, String name) {
        @Override public String toString() { return name; }
    }
    public static final Profile DEFAULT = new Profile("default", "Default (existing bindings)");
    private final Path root;
    public ProfileStore(Path root) { this.root = root; }
    public Path directory(Profile profile) {
        if (profile.id().equals("default")) return root;
        if (!profile.id().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("Invalid profile identifier");
        return root.resolve("profiles").resolve(profile.id());
    }
    public List<Profile> list() throws IOException {
        List<Profile> result = new ArrayList<>();
        result.add(DEFAULT);
        Path profiles = root.resolve("profiles");
        if (Files.isDirectory(profiles)) try (var entries = Files.list(profiles)) {
            for (Path entry : entries.sorted().toList()) {
                String id = entry.getFileName().toString();
                if (!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) continue;
                Path metadata = entry.resolve("profile.properties");
                if (Files.isRegularFile(metadata) && complete(entry)) {
                    Properties p = new Properties();
                    try (var in = Files.newInputStream(metadata)) { p.load(in); }
                    result.add(new Profile(id, p.getProperty("name", id)));
                }
            }
        }
        return result;
    }
    private static boolean complete(Path directory) {
        for (int i = 0; i < 4; i++)
            if (!Files.isRegularFile(directory.resolve("bindings-" + i + ".properties"))) return false;
        return true;
    }
    public Profile active() throws IOException {
        Path marker = root.resolve("active-profile");
        if (!Files.exists(marker)) return DEFAULT;
        String id = Files.readString(marker).strip();
        return list().stream().filter(p -> p.id().equals(id)).findFirst().orElse(DEFAULT);
    }
    public void activate(Profile profile) throws IOException {
        Path dir = directory(profile);
        for (int i = 0; i < 4; i++) if (!Files.isRegularFile(dir.resolve("bindings-" + i + ".properties")))
            throw new IOException("Profile is incomplete: missing bank " + (i + 1));
        Files.createDirectories(root);
        Path temp = Files.createTempFile(root, ".active-", ".tmp");
        try {
            Files.writeString(temp, profile.id() + "\n");
            Files.move(temp, root.resolve("active-profile"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    public Profile save(LogitechProfileImporter.Result result) throws IOException {
        String name = result.name();
        Set<String> names = new HashSet<>();
        for (Profile p : list()) names.add(p.name());
        for (int i = 2; names.contains(name); i++) name = result.name() + " (" + i + ")";
        Profile profile = new Profile(UUID.randomUUID().toString(), name);
        Files.createDirectories(root.resolve("profiles"));
        Path pending = Files.createTempDirectory(root.resolve("profiles"), ".import-");
        try {
            for (int i = 0; i < 4; i++) write(pending.resolve("bindings-" + i + ".properties"), result.banks()[i]);
            // Explicit empty slots prevent legacy default macros being injected into imports.
            for (int i = 0; i < 200; i++) {
                Properties macro = i < result.macros().size() ? result.macros().get(i) : new Properties();
                write(pending.resolve("macro-" + i + ".properties"), macro);
            }
            Properties metadata = new Properties();
            metadata.setProperty("name", name);
            metadata.setProperty("source.guid", result.sourceGuid());
            for (int i = 0; i < result.windowsTargets().size(); i++) metadata.setProperty("windows.target." + i, result.windowsTargets().get(i));
            for (int i = 0; i < result.warnings().size(); i++) metadata.setProperty("import.warning." + i, result.warnings().get(i));
            write(pending.resolve("profile.properties"), metadata);
            Files.move(pending, directory(profile), StandardCopyOption.ATOMIC_MOVE);
            return profile;
        } finally {
            if (Files.exists(pending)) try (var files = Files.walk(pending)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }
    static void write(Path path, Properties props) throws IOException {
        try (var out = Files.newOutputStream(path)) { props.store(out, "G13 profile"); }
    }
}
