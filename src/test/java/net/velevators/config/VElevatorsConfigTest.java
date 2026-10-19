package net.velevators.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VElevatorsConfigTest {
    @SuppressWarnings("unchecked")
    @Test
    void defaultsToEnglishAndTheThreeRequestedAliases() {
        try (InputStream stream = getClass().getResourceAsStream("/config.yml")) {
            assertThat(stream).isNotNull();
            Map<String, Object> root = new Yaml().load(stream);
            assertThat(root.get("locale")).isEqualTo("en_US");
            Map<String, Object> command = (Map<String, Object>) root.get("command");
            assertThat((List<String>) command.get("aliases"))
                    .containsExactly("elevator", "elevators", "elev");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
