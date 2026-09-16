package com.booker.g13;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Dependency-free integration tests; run through make test. */
public class ProfileImportTest {
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static Path fixture;
    static String export(String macros, String assignments) {
        return "<profiles xmlns='http://www.logitech.com/Cassandra/2010.7/Profile'><profile name='Test game' guid='source-id'>"
            + "<target path='C:\\Games\\game.exe'/><macros>" + macros + "</macros>"
            + "<assignments devicecategory='Logitech.Gaming.LeftHandedController'>" + assignments + "</assignments>"
            + "<backlight devicemodel='Logitech.Gaming.LeftHandedController.G13'><mode shiftstate='2' color='#1234ab'/></backlight>"
            + "</profile></profiles>";
    }
    static String assignment(String button, int bank, String guid, boolean backup) {
        return "<assignment contextid='" + button + "' shiftstate='" + bank + "' macroguid='" + guid + "' backup='" + backup + "'/>";
    }
    static LogitechProfileImporter.Result read(String xml) throws IOException {
        Files.writeString(fixture, xml);
        return LogitechProfileImporter.read(fixture);
    }
    static void fails(String xml, String reason) throws IOException {
        try { read(xml); throw new AssertionError(reason); } catch (IOException expected) { }
    }
    public static void main(String[] args) throws Exception {
        Path temp = Path.of(args[0]);
        fixture = temp.resolve("fixture.xml");
        String key = "<macro guid='key' name='Forward'><keystroke><key value='W'/></keystroke></macro>";
        String chord = "<macro guid='chord' name='Sprint'><keystroke><modifier value='LSHIFT'/><key value='W'/></keystroke></macro>";
        String macro = "<macro guid='macro' name='Repeat' repeatmode='pressed' repeatdelay='50'><multikey><key value='E' direction='down'/><delay milliseconds='10'/><key value='E' direction='up'/></multikey></macro>";
        String unsupported = "<macro guid='unsupported'><textblock><text>hello</text></textblock></macro>";
        var result = read(export(key + chord + macro + unsupported,
            assignment("G1", 1, "key", false) + assignment("G1", 1, "unsupported", true)
            + assignment("G23", 2, "chord", false) + assignment("G26", 3, "macro", false)
            + assignment("G2", 1, "unsupported", false) + assignment("G3", 1, "missing", false)));
        check(result.importedAssignments() == 3, "assignment count / backup filtering");
        check("p,k.17".equals(result.banks()[0].getProperty("G0")), "key mapping");
        check("c,42,17".equals(result.banks()[1].getProperty("G33")), "held chord and thumb mapping");
        check("m,0,1".equals(result.banks()[2].getProperty("G36")), "bank/macro mapping");
        check("kd.18,d.10,ku.18,d.50".equals(result.macros().get(0).getProperty("sequence")), "macro timing");
        check("18,52,171".equals(result.banks()[1].getProperty("color")), "bank color");
        check(result.banks()[0].getProperty("G1") == null && result.banks()[3].size() == 1, "unsupported stays unassigned");
        check(result.warnings().stream().anyMatch(w -> w.contains("textblock")), "unsupported warning");
        check(LogitechProfileImporter.contextKey("G27") == 38 && LogitechProfileImporter.contextKey("G28") == 39
            && LogitechProfileImporter.contextKey("G29") == 37, "stick clockwise mapping");
        fails("not xml", "malformed XML accepted");
        fails("<!DOCTYPE profiles [<!ENTITY attack SYSTEM 'file:///etc/passwd'>]>" + export(key, ""), "XXE accepted");
        fails("<profiles/>", "wrong namespace accepted");
        fails(export(key, "").replace("Logitech.Gaming.LeftHandedController'", "Logitech.Gaming.Mouse'"), "other device accepted");
        fails(export(key, assignment("G1", 1, "key", false).repeat(2)), "duplicate active assignment accepted");
        var bad = read(export("<macro guid='key'><multikey><key value='W' direction='down'/></multikey></macro>", assignment("G1", 1, "key", false)));
        check(bad.importedAssignments() == 0 && bad.warnings().get(0).contains("unbalanced"), "unbalanced macro accepted");
        var toggle = read(export(key.replace("guid='key'", "guid='key' repeatmode='toggle'"), assignment("G1", 1, "key", false)));
        check(toggle.importedAssignments() == 0 && toggle.warnings().get(0).contains("toggle"), "toggle silently changed");
        ProfileStore store = new ProfileStore(temp.resolve("config"));
        Files.createDirectories(temp.resolve("config"));
        Path legacy = temp.resolve("config/bindings-0.properties");
        Files.writeString(legacy, "G0=p,k.1\n");
        byte[] previous = Files.readAllBytes(legacy);
        var saved = store.save(result);
        var duplicate = store.save(result);
        check(!saved.id().equals(duplicate.id()) && duplicate.name().equals("Test game (2)"), "duplicate overwrote profile");
        check(Arrays.equals(previous, Files.readAllBytes(legacy)), "legacy configuration overwritten");
        check(store.active().equals(ProfileStore.DEFAULT), "import activated implicitly");
        store.activate(saved);
        check(store.active().equals(saved), "activation round trip");
        Properties metadata = new Properties();
        try (var in = Files.newInputStream(store.directory(saved).resolve("profile.properties"))) { metadata.load(in); }
        check("C:\\Games\\game.exe".equals(metadata.getProperty("windows.target.0")), "lost Windows reference");
        try { store.directory(new ProfileStore.Profile("../escape", "bad")); throw new AssertionError("path traversal"); }
        catch (IllegalArgumentException expected) { }
        Files.delete(store.directory(duplicate).resolve("bindings-2.properties"));
        try { store.activate(duplicate); throw new AssertionError("incomplete profile activated"); } catch (IOException expected) { }
        check(store.active().equals(saved), "failed activation changed marker");
        Files.delete(store.directory(saved).resolve("bindings-3.properties"));
        check(store.active().equals(ProfileStore.DEFAULT), "incomplete selection must match driver fallback");
        System.out.println("Profile import, security, mapping and storage tests passed.");
        if (args.length > 1) {
            int imported = 0, rejected = 0;
            Map<String, Integer> reasons = new TreeMap<>();
            try (var files = Files.list(Path.of(args[1]))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".xml")).toList()) {
                    try { var r = LogitechProfileImporter.read(file); imported++;
                        for (String w : r.warnings()) if (w.contains("left unassigned")) reasons.merge(w.substring(w.indexOf(":") + 1), 1, Integer::sum);
                    } catch (IOException e) { rejected++; System.out.println("Rejected " + file.getFileName() + ": " + e.getMessage()); }
                }
            }
            System.out.println("Example corpus: " + imported + " imported, " + rejected + " rejected. Warning counts: " + reasons);
        }
    }
}
