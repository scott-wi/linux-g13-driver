package com.booker.g13;

import java.util.ArrayList;
import java.util.List;

/** Converts editable US-keyboard text into the driver's key-event macro format. */
public final class TextMacroCodec {
    private static final int LEFT_SHIFT = 42;

    private TextMacroCodec() {}

    public static String sequence(String text, int characterDelay) {
        if (text == null) text = "";
        if (text.length() > 4096) throw new IllegalArgumentException("Text macros are limited to 4096 characters.");
        if (characterDelay < 0 || characterDelay > 60000)
            throw new IllegalArgumentException("Character delay must be between 0 and 60000 ms.");
        List<String> events = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            Stroke stroke = stroke(text.charAt(i));
            if (stroke.shifted()) events.add("kd." + LEFT_SHIFT);
            events.add("kd." + stroke.code());
            events.add("ku." + stroke.code());
            if (stroke.shifted()) events.add("ku." + LEFT_SHIFT);
            if (characterDelay > 0 && i + 1 < text.length()) events.add("d." + characterDelay);
        }
        return String.join(",", events);
    }

    private static Stroke stroke(char value) {
        if (value >= 'a' && value <= 'z') return new Stroke(letterCode(value), false);
        if (value >= 'A' && value <= 'Z') return new Stroke(letterCode(Character.toLowerCase(value)), true);
        if (value >= '1' && value <= '9') return new Stroke(value - '1' + 2, false);
        if (value == '0') return new Stroke(11, false);
        return switch (value) {
            case ' ' -> new Stroke(57, false);
            case '\t' -> new Stroke(15, false);
            case '\n', '\r' -> new Stroke(28, false);
            case '-' -> new Stroke(12, false); case '_' -> new Stroke(12, true);
            case '=' -> new Stroke(13, false); case '+' -> new Stroke(13, true);
            case '[' -> new Stroke(26, false); case '{' -> new Stroke(26, true);
            case ']' -> new Stroke(27, false); case '}' -> new Stroke(27, true);
            case ';' -> new Stroke(39, false); case ':' -> new Stroke(39, true);
            case '\'' -> new Stroke(40, false); case '"' -> new Stroke(40, true);
            case '`' -> new Stroke(41, false); case '~' -> new Stroke(41, true);
            case '\\' -> new Stroke(43, false); case '|' -> new Stroke(43, true);
            case ',' -> new Stroke(51, false); case '<' -> new Stroke(51, true);
            case '.' -> new Stroke(52, false); case '>' -> new Stroke(52, true);
            case '/' -> new Stroke(53, false); case '?' -> new Stroke(53, true);
            case '!' -> new Stroke(2, true); case '@' -> new Stroke(3, true);
            case '#' -> new Stroke(4, true); case '$' -> new Stroke(5, true);
            case '%' -> new Stroke(6, true); case '^' -> new Stroke(7, true);
            case '&' -> new Stroke(8, true); case '*' -> new Stroke(9, true);
            case '(' -> new Stroke(10, true); case ')' -> new Stroke(11, true);
            default -> throw new IllegalArgumentException(String.format(
                    "Text contains unsupported character U+%04X; use US-keyboard characters.", (int) value));
        };
    }

    private static int letterCode(char value) {
        String rows = "qwertyuiopasdfghjklzxcvbnm";
        int[] codes = {16,17,18,19,20,21,22,23,24,25,30,31,32,33,34,35,36,37,38,44,45,46,47,48,49,50};
        return codes[rows.indexOf(value)];
    }

    private record Stroke(int code, boolean shifted) {}
}
