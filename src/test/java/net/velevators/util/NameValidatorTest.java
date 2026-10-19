package net.velevators.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NameValidatorTest {
    @Test
    void acceptsVisibleUnicodeAndTrimsOuterWhitespace() {
        assertThat(NameValidator.validate("  Piso José 🏰  ", 20)).isEqualTo("Piso José 🏰");
    }

    @Test
    void countsUnicodeCodePointsInsteadOfUtf16Units() {
        assertThat(NameValidator.validate("1234567890123456789🏰", 20)).hasSize(21);
        assertThatThrownBy(() -> NameValidator.validate("12345678901234567890🏰", 20))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("length");
    }

    @Test
    void rejectsColorsControlsAndEmptyText() {
        assertThatThrownBy(() -> NameValidator.validate("&#39A857VIP", 20)).hasMessage("color");
        assertThatThrownBy(() -> NameValidator.validate("&aVIP", 20)).hasMessage("color");
        assertThatThrownBy(() -> NameValidator.validate("<red>VIP</red>", 20)).hasMessage("color");
        assertThatThrownBy(() -> NameValidator.validate("Piso\nVIP", 20)).hasMessage("control");
        assertThatThrownBy(() -> NameValidator.validate("Piso\u200BVIP", 20)).hasMessage("control");
        assertThatThrownBy(() -> NameValidator.validate("   ", 20)).hasMessage("empty");
    }
}
