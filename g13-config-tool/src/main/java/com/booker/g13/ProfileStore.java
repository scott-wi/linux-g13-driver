package com.booker.g13;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Named profile storage and selection policy shared with the native driver. */
public final class ProfileStore {
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final String APPLICATION = "[A-Za-z0-9._+ -]{1,128}";
    public record Profile(String id, String name, Path icon, List<String> applications) {
        public Profile { applications = List.copyOf(applications); }
        @Override public String toString() { return name; }
    }
    public static final Profile DEFAULT = new Profile("default", "Default (existing bindings)", null, List.of());
    private final Path root;

    public ProfileStore(Path root) { this.root = root; }

    public Path directory(Profile profile) {
        if (profile.id().equals("default")) return root;
        if (!profile.id().matches(UUID_PATTERN)) throw new IllegalArgumentException("Invalid profile identifier");
        return root.resolve("profiles").resolve(profile.id());
    }

    public List<Profile> list() throws IOException {
        List<Profile> result = new ArrayList<>();
        result.add(readProfile("default", root, DEFAULT.name()));
        Path profiles = root.resolve("profiles");
        if (Files.isDirectory(profiles)) try (var entries = Files.list(profiles)) {
            for (Path entry : entries.toList()) {
                String id = entry.getFileName().toString();
                if (id.matches(UUID_PATTERN) && complete(entry) && Files.isRegularFile(entry.resolve("profile.properties")))
                    result.add(readProfile(id, entry, id));
            }
        }
        result.subList(1, result.size()).sort(Comparator.comparing(Profile::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private Profile readProfile(String id, Path directory, String fallbackName) throws IOException {
        Properties metadata = readMetadata(directory);
        List<String> applications = new ArrayList<>();
        for (int i = 0; ; i++) {
            String value = metadata.getProperty("application." + i);
            if (value == null) break;
            if (value.matches(APPLICATION)) applications.add(value);
        }
        Path icon = directory.resolve("profile-icon.png");
        return new Profile(id, metadata.getProperty("name", fallbackName),
                Files.isRegularFile(icon) ? icon : null, applications);
    }

    private static Properties readMetadata(Path directory) throws IOException {
        Properties result = new Properties();
        Path metadata = directory.resolve("profile.properties");
        if (Files.isRegularFile(metadata)) try (var in = Files.newInputStream(metadata)) { result.load(in); }
        return result;
    }

    private static boolean complete(Path directory) {
        for (int i = 0; i < 4; i++)
            if (!Files.isRegularFile(directory.resolve("bindings-" + i + ".properties"))) return false;
        return true;
    }

    public Profile find(String id) throws IOException {
        return list().stream().filter(profile -> profile.id().equals(id)).findFirst().orElse(DEFAULT);
    }

    private Optional<Profile> exact(String id) throws IOException {
        return list().stream().filter(profile -> profile.id().equals(id)).findFirst();
    }

    public Profile defaultProfile() throws IOException {
        return exact(readMarker("default-profile")).orElse(DEFAULT);
    }
    public Optional<Profile> persistentProfile() throws IOException {
        String id = readMarker("persistent-profile");
        return id.isBlank() ? Optional.empty() : exact(id);
    }

    private String readMarker(String name) throws IOException {
        Path marker = root.resolve(name);
        if (!Files.isRegularFile(marker)) return name.equals("default-profile") ? "default" : "";
        String id = Files.readString(marker).strip();
        return id.equals("default") || id.matches(UUID_PATTERN) ? id : name.equals("default-profile") ? "default" : "";
    }

    public void setDefault(Profile profile) throws IOException { writeMarker("default-profile", profile.id()); }
    public void setPersistent(Profile profile) throws IOException { writeMarker("persistent-profile", profile.id()); }
    public void clearPersistent() throws IOException { Files.deleteIfExists(root.resolve("persistent-profile")); }

    public Profile create(String requestedName) throws IOException {
        String name = uniqueName(normalizeName(requestedName));
        Profile profile = new Profile(UUID.randomUUID().toString(), name, null, List.of());
        Files.createDirectories(root.resolve("profiles"));
        Path pending = Files.createTempDirectory(root.resolve("profiles"), ".new-");
        try {
            for (int i = 0; i < 4; i++) {
                Properties bindings = new Properties();
                bindings.setProperty("color", "255,255,255");
                bindings.setProperty("format", "2");
                for (String[] binding : Configs.defaultBindings)
                    bindings.setProperty(binding[0], "p,k." + binding[1]);
                bindings.setProperty("G29", "b,0");
                bindings.setProperty("G30", "b,1");
                bindings.setProperty("G31", "b,2");
                write(pending.resolve("bindings-" + i + ".properties"), bindings);
            }
            for (int i = 0; i < 200; i++) {
                Properties macro = new Properties();
                macro.setProperty("id", Integer.toString(i));
                macro.setProperty("name", i < Configs.DEFAULT_MACROS_COUNT ? Configs.defaultMacros[i][0] : "");
                macro.setProperty("sequence", i < Configs.DEFAULT_MACROS_COUNT ? Configs.defaultMacros[i][1] : "");
                write(pending.resolve("macro-" + i + ".properties"), macro);
            }
            Properties metadata = new Properties();
            metadata.setProperty("name", name);
            write(pending.resolve("profile.properties"), metadata);
            moveAtomically(pending, directory(profile));
            return find(profile.id());
        } finally { deleteTree(pending); }
    }

    public void delete(Profile profile) throws IOException {
        if (profile == null || profile.id().equals("default"))
            throw new IllegalArgumentException("The existing-bindings profile cannot be deleted.");
        Path directory = directory(profile);
        if (!Files.isDirectory(directory)) throw new IOException("Profile is missing.");
        Path discarded = directory.resolveSibling(".delete-" + profile.id());
        moveAtomically(directory, discarded);
        if (readMarker("default-profile").equals(profile.id())) setDefault(DEFAULT);
        if (readMarker("persistent-profile").equals(profile.id())) clearPersistent();
        deleteTree(discarded);
    }

    private void writeMarker(String name, String value) throws IOException {
        if (exact(value).isEmpty()) throw new IOException("Profile is incomplete or missing.");
        Files.createDirectories(root);
        Path temp = Files.createTempFile(root, ".selection-", ".tmp");
        try {
            Files.writeString(temp, value + "\n");
            moveAtomically(temp, root.resolve(name));
        } finally { Files.deleteIfExists(temp); }
    }

    public Profile update(Profile profile, String name, String application, Path iconSource) throws IOException {
        name = normalizeName(name);
        for (Profile candidate : list())
            if (!candidate.id().equals(profile.id()) && candidate.name().equalsIgnoreCase(name))
                throw new IllegalArgumentException("Another profile already uses that name.");
        application = normalizeApplication(application);
        Path directory = directory(profile);
        Properties metadata = readMetadata(directory);
        metadata.setProperty("name", name);
        metadata.stringPropertyNames().stream().filter(key -> key.startsWith("application."))
                .toList().forEach(metadata::remove);
        if (!application.isBlank()) metadata.setProperty("application.0", application);
        writeMetadata(directory, metadata);
        if (iconSource != null) writeIcon(directory.resolve("profile-icon.png"), iconSource);
        return find(profile.id());
    }

    public Profile update(Profile profile, String application, Path iconSource) throws IOException {
        return update(profile, profile.name(), application, iconSource);
    }

    static String normalizeName(String value) {
        value = value == null ? "" : value.strip().replaceAll("\\s+", " ");
        if (value.isEmpty() || value.length() > 80 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Profile name must contain 1–80 printable characters.");
        return value;
    }

    private String uniqueName(String requested) throws IOException {
        Set<String> names = new HashSet<>();
        for (Profile profile : list()) names.add(profile.name().toLowerCase(Locale.ROOT));
        if (!names.contains(requested.toLowerCase(Locale.ROOT))) return requested;
        for (int suffix = 2; ; suffix++) {
            String candidate = requested + " (" + suffix + ")";
            if (!names.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
        }
    }

    static String normalizeApplication(String value) {
        value = value == null ? "" : value.strip().replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        if (!value.isEmpty() && !value.matches(APPLICATION))
            throw new IllegalArgumentException("Application must be an executable name using letters, numbers, spaces, dot, underscore, plus, or hyphen.");
        return value;
    }

    private static void writeIcon(Path destination, Path source) throws IOException {
        if (!Files.isRegularFile(source) || Files.size(source) > 10 * 1024 * 1024)
            throw new IOException("Choose an image smaller than 10 MiB.");
        BufferedImage input = ImageIO.read(source.toFile());
        if (input == null || input.getWidth() < 1 || input.getHeight() < 1
                || (long) input.getWidth() * input.getHeight() > 16_000_000L)
            throw new IOException("The selected file is not a supported image or is too large.");
        int side = 128;
        double scale = Math.min((double) side / input.getWidth(), (double) side / input.getHeight());
        int width = Math.max(1, (int) Math.round(input.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(input.getHeight() * scale));
        BufferedImage output = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(input, (side - width) / 2, (side - height) / 2, width, height, null);
        graphics.dispose();
        Files.createDirectories(destination.getParent());
        Path temp = Files.createTempFile(destination.getParent(), ".icon-", ".png");
        try {
            if (!ImageIO.write(output, "png", temp.toFile())) throw new IOException("PNG support is unavailable.");
            moveAtomically(temp, destination);
        } finally { Files.deleteIfExists(temp); }
    }

    public Profile save(LogitechProfileImporter.Result result) throws IOException {
        String name = uniqueName(normalizeName(result.name()));
        Profile profile = new Profile(UUID.randomUUID().toString(), name, null, List.of());
        Files.createDirectories(root.resolve("profiles"));
        Path pending = Files.createTempDirectory(root.resolve("profiles"), ".import-");
        try {
            for (int i = 0; i < 4; i++) write(pending.resolve("bindings-" + i + ".properties"), result.banks()[i]);
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
            return find(profile.id());
        } finally { deleteTree(pending); }
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }

    private static void writeMetadata(Path directory, Properties metadata) throws IOException {
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, ".profile-", ".tmp");
        try {
            write(temp, metadata);
            moveAtomically(temp, directory.resolve("profile.properties"));
        } finally { Files.deleteIfExists(temp); }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try { Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING); }
    }

    static void write(Path path, Properties properties) throws IOException {
        try (var out = Files.newOutputStream(path)) { properties.store(out, "G13 profile"); }
    }
}
