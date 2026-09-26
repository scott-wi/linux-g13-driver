package com.booker.g13;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

/** Read-only conversion, independent of Swing and storage for future batch import. */
public final class LogitechProfileImporter {
    private static final String NS = "http://www.logitech.com/Cassandra/2010.7/Profile";
    private static final String DEVICE = "Logitech.Gaming.LeftHandedController";
    public record Result(String name, String sourceGuid, List<String> windowsTargets,
                         Properties[] banks, List<Properties> macros, List<String> warnings,
                         int importedAssignments) {}

    public static Result read(Path source) throws IOException {
        if (Files.size(source) > 4 * 1024 * 1024) throw new IOException("Profile exceeds the 4 MiB limit.");
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
                @Override public void error(org.xml.sax.SAXParseException e) throws SAXException { throw e; }
            });
            Document doc;
            try (var input = Files.newInputStream(source)) { doc = builder.parse(input); }
            Element root = doc.getDocumentElement();
            if (!NS.equals(root.getNamespaceURI()) || !"profiles".equals(root.getLocalName()))
                throw new IOException("This is not a Logitech Gaming Software profile export.");
            var profiles = children(root, "profile");
            if (profiles.size() != 1) throw new IOException("Select an export containing exactly one profile.");
            return convert(profiles.get(0));
        } catch (javax.xml.parsers.ParserConfigurationException | SAXException | IllegalArgumentException e) {
            throw new IOException("Cannot read Logitech XML: " + e.getMessage(), e);
        }
    }

    private static Result convert(Element profile) throws IOException {
        Properties[] banks = new Properties[4];
        for (int i = 0; i < banks.length; i++) {
            banks[i] = new Properties();
            banks[i].setProperty("color", "255,255,255");
            banks[i].setProperty("format", "2");
            banks[i].setProperty("G29", "b,0");
            banks[i].setProperty("G30", "b,1");
            banks[i].setProperty("G31", "b,2");
        }
        List<String> warnings = new ArrayList<>();
        List<String> targets = new ArrayList<>();
        for (Element target : children(profile, "target")) targets.add(target.getAttribute("path"));
        Map<String, Element> definitions = new HashMap<>();
        for (Element group : children(profile, "macros")) for (Element macro : children(group, "macro")) {
            if (definitions.put(macro.getAttribute("guid"), macro) != null)
                throw new IOException("Duplicate macro GUID in export.");
        }
        List<Properties> macros = new ArrayList<>();
        Map<String, String> converted = new HashMap<>();
        Set<String> seen = new HashSet<>();
        int count = 0;
        boolean hasG13 = false;
        for (Element group : children(profile, "assignments")) {
            if (!DEVICE.equals(group.getAttribute("devicecategory"))) continue;
            hasG13 = true;
            for (Element assignment : children(group, "assignment")) {
                if ("true".equals(assignment.getAttribute("backup"))) continue;
                String context = assignment.getAttribute("contextid");
                String state = assignment.getAttribute("shiftstate");
                String label = "M" + state + " " + context;
                try {
                    int bank = Integer.parseInt(state) - 1;
                    int key = contextKey(context);
                    if (bank < 0 || bank > 2 || key < 0) throw new IllegalArgumentException("unsupported button or bank");
                    if (!seen.add(bank + ":" + key)) throw new IOException("Duplicate active assignment: " + label);
                    String guid = assignment.getAttribute("macroguid");
                    Element macro = definitions.get(guid);
                    if (macro == null) throw new IllegalArgumentException("missing action " + guid);
                    String binding = converted.get(guid);
                    if (binding == null) {
                        binding = convertAction(macro, macros);
                        converted.put(guid, binding);
                    }
                    if (binding.equals("joystick")) {
                        if (key == 35) banks[bank].setProperty("G35", "p,k.289");
                        else if (key >= 36 && key <= 39) banks[bank].setProperty("stick", "absolute");
                        else throw new IllegalArgumentException("joystick action requires the stick or its press button");
                    } else banks[bank].setProperty("G" + key, binding);
                    count++;
                } catch (IllegalArgumentException e) { warnings.add(label + ": " + e.getMessage() + "; left unassigned."); }
            }
        }
        if (!hasG13) throw new IOException("This export contains no G13 assignments.");
        for (Element light : children(profile, "backlight")) {
            if (!(DEVICE + ".G13").equals(light.getAttribute("devicemodel"))) continue;
            for (Element mode : children(light, "mode")) {
                try {
                    int bank = Integer.parseInt(mode.getAttribute("shiftstate")) - 1;
                    String color = mode.getAttribute("color");
                    if (bank < 0 || bank > 2 || !color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException();
                    int rgb = Integer.parseInt(color.substring(1), 16);
                    banks[bank].setProperty("color", (rgb >> 16) + "," + ((rgb >> 8) & 255) + "," + (rgb & 255));
                } catch (IllegalArgumentException e) { warnings.add("Invalid bank backlight; using white."); }
            }
        }
        for (Element script : children(profile, "script")) if (!script.getTextContent().isBlank()) {
            warnings.add("Lua scripts are not imported or executed."); break;
        }
        warnings.add("Only explicit active G13 assignments are imported. Missing assignments remain unassigned; M1–M3 switch layouts by default.");
        if (!targets.isEmpty()) warnings.add("Windows executable paths are saved for reference; add the Linux executable in the profile sidebar.");
        String name = profile.getAttribute("name").strip();
        return new Result(name.isEmpty() ? "Imported profile" : name, profile.getAttribute("guid"),
                List.copyOf(targets), banks, List.copyOf(macros), List.copyOf(warnings), count);
    }

    // LGS G23/G24/G25 are thumb buttons/press; G26..29 run clockwise from up.
    static int contextKey(String context) {
        if (!context.matches("G(?:[1-9]|[12][0-9])")) return -1;
        int n = Integer.parseInt(context.substring(1));
        if (n <= 22) return n - 1;
        return switch (n) { case 23 -> 33; case 24 -> 34; case 25 -> 35;
            case 26 -> 36; case 27 -> 38; case 28 -> 39; case 29 -> 37; default -> -1; };
    }

    private static String convertAction(Element macro, List<Properties> macros) {
        List<Element> actions = children(macro, null);
        if (actions.size() != 1) throw new IllegalArgumentException("unsupported action structure");
        Element action = actions.get(0);
        String type = action.getLocalName();
        String repeat = macro.getAttribute("repeatmode");
        if (!repeat.isEmpty() && !repeat.equals("none") && !repeat.equals("pressed"))
            throw new IllegalArgumentException("unsupported repeat mode: " + repeat);
        if (type.equals("keystroke")) {
            if (!repeat.isEmpty() && !repeat.equals("none")) throw new IllegalArgumentException("repeating keystroke not supported");
            List<Element> keys = children(action, "key");
            if (keys.size() != 1) throw new IllegalArgumentException("invalid keystroke");
            List<Integer> codes = new ArrayList<>();
            for (Element modifier : children(action, "modifier")) codes.add(keyCode(modifier.getAttribute("value")));
            codes.add(keyCode(keys.get(0).getAttribute("value")));
            if (codes.size() == 1) return "p,k." + codes.get(0);
            return "c," + String.join(",", codes.stream().map(String::valueOf).toList());
        }
        if (type.equals("function")) {
            List<Element> commands = children(action, "do");
            if (commands.size() != 1) throw new IllegalArgumentException("invalid function action");
            return switch (commands.get(0).getAttribute("task").toUpperCase(Locale.ROOT)) {
                case "M1" -> "b,0";
                case "M2" -> "b,1";
                case "M3" -> "b,2";
                default -> throw new IllegalArgumentException("unsupported function: " + commands.get(0).getAttribute("task"));
            };
        }
        if (type.equals("joystick")) return "joystick";
        if (type.equals("textblock")) return convertText(action, macro, macros);
        if (!type.equals("multikey")) throw new IllegalArgumentException("unsupported action: " + type);
        List<String> sequence = new ArrayList<>();
        Set<Integer> held = new HashSet<>();
        for (Element event : children(action, null)) {
            switch (event.getLocalName()) {
                case "key" -> {
                    int code = keyCode(event.getAttribute("value"));
                    String direction = event.getAttribute("direction");
                    if (direction.equals("down")) {
                        if (!held.add(code)) throw new IllegalArgumentException("duplicate macro key-down");
                        sequence.add("kd." + code);
                    } else if (direction.equals("up")) {
                        if (!held.remove(code)) throw new IllegalArgumentException("macro key-up without key-down");
                        sequence.add("ku." + code);
                    } else throw new IllegalArgumentException("unknown macro key direction");
                }
                case "delay" -> sequence.add("d." + delay(event.getAttribute("milliseconds")));
                default -> throw new IllegalArgumentException("unsupported macro event: " + event.getLocalName());
            }
        }
        if (!held.isEmpty() || sequence.isEmpty()) throw new IllegalArgumentException("empty or unbalanced key macro");
        int repeats = repeat.equals("pressed") ? 1 : 0;
        if (repeats == 1) sequence.add("d." + Math.max(1, delay(macro.getAttribute("repeatdelay").isEmpty() ? "1" : macro.getAttribute("repeatdelay"))));
        if (macros.size() >= 200) throw new IllegalArgumentException("200-macro limit reached");
        int id = macros.size();
        Properties props = new Properties();
        props.setProperty("id", "" + id);
        props.setProperty("name", macro.getAttribute("name"));
        props.setProperty("sequence", String.join(",", sequence));
        macros.add(props);
        return "m," + id + "," + repeats;
    }

    private static String convertText(Element action, Element macro, List<Properties> macros) {
        List<Element> entries = children(action, "text");
        if (entries.size() != 1 || !children(entries.get(0), null).isEmpty())
            throw new IllegalArgumentException("invalid text block");
        Element entry = entries.get(0);
        if (!entry.getAttribute("playback").isEmpty() && !entry.getAttribute("playback").equals("normal"))
            throw new IllegalArgumentException("unsupported text playback mode");
        int delay = "true".equals(entry.getAttribute("hasdelay")) ? delay(entry.getAttribute("delay")) : 0;
        String text = entry.getTextContent();
        String sequence = TextMacroCodec.sequence(text, delay);
        if (sequence.isEmpty()) throw new IllegalArgumentException("empty text block");
        if (macros.size() >= 200) throw new IllegalArgumentException("200-macro limit reached");
        int id = macros.size();
        Properties props = new Properties();
        props.setProperty("id", Integer.toString(id));
        props.setProperty("name", macro.getAttribute("name"));
        props.setProperty("type", "text");
        props.setProperty("text", text);
        props.setProperty("characterDelay", Integer.toString(delay));
        props.setProperty("sequence", sequence);
        macros.add(props);
        return "m," + id + ",0";
    }

    private static int delay(String value) {
        int delay = Integer.parseInt(value);
        if (delay < 0 || delay > 60000) throw new IllegalArgumentException("delay outside 0–60000 ms");
        return delay;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element e && (name == null || name.equals(e.getLocalName()))) result.add(e);
        return result;
    }

    private static final Map<String, Integer> KEYS = new HashMap<>();
    static {
        String[] rows = {"Q W E R T Y U I O P", "A S D F G H J K L", "Z X C V B N M"};
        int[] starts = {16, 30, 44};
        for (int r = 0; r < rows.length; r++) {
            String[] row = rows[r].split(" ");
            for (int i = 0; i < row.length; i++) KEYS.put(row[i], starts[r] + i);
        }
        for (int i = 1; i <= 9; i++) KEYS.put("" + i, i + 1);
        KEYS.put("0", 11);
        for (int i = 1; i <= 10; i++) KEYS.put("F" + i, 58 + i);
        String[] names = {"ESCAPE","MINUS","EQUAL","BACKSPACE","TAB","LBRACKET","RBRACKET","ENTER","LCTRL","SEMICOLON","QUOTE","TILDE","LSHIFT","BACKSLASH","COMMA","PERIOD","SLASH","RSHIFT","NUMMULTIPLY","LALT","SPACEBAR","CAPSLOCK","NUMLOCK","SCROLLLOCK","NUM7","NUM8","NUM9","NUMMINUS","NUM4","NUM5","NUM6","NUMPLUS","NUM1","NUM2","NUM3","NUM0","NUMPERIOD","F11","F12","NUMENTER","RCTRL","NUMDIVIDE","PRINTSCREEN","RALT","HOME","UP","PAGEUP","LEFT","RIGHT","END","DOWN","PAGEDOWN","INSERT","DELETE","PAUSE","LGUI","RGUI","APPLICATION"};
        int[] values = {1,12,13,14,15,26,27,28,29,39,40,41,42,43,51,52,53,54,55,56,57,58,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,87,88,96,97,98,99,100,102,103,104,105,106,107,108,109,110,111,119,125,126,127};
        for (int i = 0; i < names.length; i++) KEYS.put(names[i], values[i]);
    }
    private static int keyCode(String token) {
        Integer code = KEYS.get(token);
        if (code == null) throw new IllegalArgumentException("unknown Windows key: " + token);
        return code;
    }
}
