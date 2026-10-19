package net.velevators.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinecraftPlayerNameTest {
    @Test
    void acceptsMinecraftUsernamesFromThreeToSixteenCharacters() {
        assertThat(MinecraftPlayerName.isValid("Pepito_123")).isTrue();
        assertThat(MinecraftPlayerName.isValid("Abc")).isTrue();
        assertThat(MinecraftPlayerName.isValid("SixteenChars_123")).isTrue();
    }

    @Test
    void rejectsWhitespaceColorsUnicodeAndInvalidLengths() {
        assertThat(MinecraftPlayerName.isValid("ab")).isFalse();
        assertThat(MinecraftPlayerName.isValid("seventeen_chars_1")).isFalse();
        assertThat(MinecraftPlayerName.isValid("Pepito Dos")).isFalse();
        assertThat(MinecraftPlayerName.isValid("&aPepito")).isFalse();
        assertThat(MinecraftPlayerName.isValid("Peñito")).isFalse();
        assertThat(MinecraftPlayerName.isValid(null)).isFalse();
    }
}
