package net.velevators.util;

import java.util.regex.Pattern;

public final class MinecraftPlayerName {
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private MinecraftPlayerName() {
    }

    public static boolean isValid(String input) {
        return input != null && VALID_NAME.matcher(input).matches();
    }
}
