package net.velevators.util;

import java.util.regex.Pattern;

public final class NameValidator {
    private static final Pattern COLOR_CODE = Pattern.compile(
            "(?i)(?:(?:&|§)(?:#[0-9a-f]{6}|[0-9a-fk-orx])|"
                    + "</?(?:black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|"
                    + "dark_gray|blue|green|aqua|red|light_purple|yellow|white|#[0-9a-f]{6}|color(?::[^>]+)?)>)");

    private NameValidator() {
    }

    public static String validate(String input, int maximumCodePoints) {
        String normalized = input == null ? "" : input.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("empty");
        }
        if (COLOR_CODE.matcher(normalized).find()) {
            throw new IllegalArgumentException("color");
        }
        if (normalized.codePoints().anyMatch(NameValidator::isInvisibleOrControl)) {
            throw new IllegalArgumentException("control");
        }
        if (normalized.codePointCount(0, normalized.length()) > maximumCodePoints) {
            throw new IllegalArgumentException("length");
        }
        return normalized;
    }

    private static boolean isInvisibleOrControl(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint) || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }
}
