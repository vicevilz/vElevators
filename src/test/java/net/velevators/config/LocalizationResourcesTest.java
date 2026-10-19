package net.velevators.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizationResourcesTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Za-z0-9_-]+}");

    @Test
    void allFifteenLocalesContainTheSameKeysAndPlaceholders() {
        Map<String, String> english = flatten(load("en_US"));
        assertThat(english).hasSize(102);
        for (String locale : MessageService.SUPPORTED_LOCALES) {
            Map<String, String> translated = flatten(load(locale));
            assertThat(translated.keySet()).as(locale).containsExactlyElementsOf(english.keySet());
            assertThat(translated.get("prefix")).as(locale).isEqualTo("&#39A857&lvElevators &8» &r");
            assertThat(translated.get("errors.usage")).as(locale).contains("/velevators");
            assertThat(translated.get("transactions.usage")).as(locale).contains("/velevators transactions");
            for (Map.Entry<String, String> entry : english.entrySet()) {
                assertThat(placeholders(translated.get(entry.getKey())))
                        .as(locale + ":" + entry.getKey())
                        .containsExactlyElementsOf(placeholders(entry.getValue()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> load(String locale) {
        try (InputStream stream = getClass().getResourceAsStream("/lang/" + locale + ".yml")) {
            assertThat(stream).as(locale).isNotNull();
            return new Yaml().load(stream);
        } catch (Exception exception) {
            throw new AssertionError(locale, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> flatten(Map<String, Object> source) {
        Map<String, String> flattened = new LinkedHashMap<>();
        flattenInto("", source, flattened);
        return flattened;
    }

    @SuppressWarnings("unchecked")
    private void flattenInto(String prefix, Map<String, Object> source, Map<String, String> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> nested) {
                flattenInto(key, (Map<String, Object>) nested, target);
            } else {
                target.put(key, String.valueOf(entry.getValue()));
            }
        }
    }

    private List<String> placeholders(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        java.util.ArrayList<String> found = new java.util.ArrayList<>();
        while (matcher.find()) {
            found.add(matcher.group());
        }
        found.sort(String::compareTo);
        return List.copyOf(found);
    }
}
