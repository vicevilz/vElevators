package net.velevators.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorCodes {
    private static final Pattern HEX = Pattern.compile("(?i)&#([0-9a-f]{6})");

    private ColorCodes() {
    }

    public static String colorize(String input) {
        if (input == null) {
            return "";
        }
        Matcher matcher = HEX.matcher(input);
        StringBuilder converted = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char digit : hex.toCharArray()) {
                replacement.append('§').append(digit);
            }
            matcher.appendReplacement(converted, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(converted);
        StringBuilder legacy = new StringBuilder(converted.length());
        String value = converted.toString();
        String validCodes = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '&' && index + 1 < value.length() && validCodes.indexOf(value.charAt(index + 1)) >= 0) {
                legacy.append('§').append(Character.toLowerCase(value.charAt(++index)));
            } else {
                legacy.append(current);
            }
        }
        return legacy.toString();
    }
}
