package net.velevators.model;

import java.util.Locale;

public enum AccessMode {
    PUBLIC("Pública"),
    PRIVATE("Privada"),
    OWNER_ONLY("Solo propietario");

    private final String displayName;

    AccessMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public AccessMode next() {
        AccessMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static AccessMode parse(String value, AccessMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
