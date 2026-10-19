package net.velevators.menu;

import net.velevators.config.MessageService;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizedDeluxeMenusTest {
    private static final List<String> MENU_FILES = List.of(
            "main.yml", "manage.yml", "confirm-create.yml", "confirm-delete.yml",
            "add-member.yml", "remove-member.yml", "network-members.yml", "transfer-owner.yml",
            "confirm-transfer.yml", "confirm-delete-network.yml", "network-icon.yml", "floor-icon.yml",
            "admin-main.yml", "admin-networks.yml", "admin-history.yml");
    private static final Pattern TECHNICAL_TOKEN = Pattern.compile(
            "%[^%\\r\\n]+%|/(?:cancelar)\\b|Europe/Madrid|\\bvElevators\\b|\\bREDSTONE_BLOCK\\b|"
                    + "\\b(?:collect|clear)\\b");
    private static final Pattern COORDINATE = Pattern.compile("(?<![A-Za-z0-9_])[XYZ](?![A-Za-z0-9_])");
    private static final Pattern COLOR_TOKEN = Pattern.compile("&#[0-9A-Fa-f]{6}|&[0-9A-FK-ORa-fk-or]");

    @Test
    void everySupportedLocaleContainsFifteenValidMenus() {
        for (String locale : MessageService.SUPPORTED_LOCALES) {
            for (String menu : MENU_FILES) {
                Map<String, Object> parsed = load(locale, menu);
                assertThat(parsed).as(locale + "/" + menu).containsKeys("menu_title", "size", "items");
            }
        }
    }

    @Test
    void translationsChangeOnlyVisibleFieldsAndPreserveTechnicalTokens() {
        for (String menu : MENU_FILES) {
            Map<String, Object> spanish = load("es_ES", menu);
            for (String locale : MessageService.SUPPORTED_LOCALES) {
                if (locale.equals("es_ES")) {
                    continue;
                }
                compare(spanish, load(locale, menu), List.of(), locale + "/" + menu);
            }
        }
    }

    @Test
    void englishIsActuallyTheDefaultTranslatedMenuSet() {
        Map<String, Object> english = load("en_US", "main.yml");
        Map<String, Object> spanish = load("es_ES", "main.yml");
        assertThat(english.get("menu_title")).isNotEqualTo(spanish.get("menu_title")).asString().contains("ELEVATORS");
    }

    @SuppressWarnings("unchecked")
    private void compare(Object spanish, Object localized, List<String> path, String resource) {
        assertThat(localized).as(resource + " at " + path).isNotNull();
        if (spanish instanceof Map<?, ?> spanishMap) {
            assertThat(localized).as(resource + " at " + path).isInstanceOf(Map.class);
            Map<Object, Object> localizedMap = (Map<Object, Object>) localized;
            assertThat(localizedMap.keySet()).as(resource + " keys at " + path).containsExactlyElementsOf(spanishMap.keySet());
            for (Map.Entry<?, ?> entry : spanishMap.entrySet()) {
                List<String> child = new ArrayList<>(path);
                child.add(String.valueOf(entry.getKey()));
                compare(entry.getValue(), localizedMap.get(entry.getKey()), child, resource);
            }
            return;
        }
        if (spanish instanceof List<?> spanishList) {
            assertThat(localized).as(resource + " at " + path).isInstanceOf(List.class);
            List<?> localizedList = (List<?>) localized;
            assertThat(localizedList).as(resource + " list size at " + path).hasSameSizeAs(spanishList);
            for (int index = 0; index < spanishList.size(); index++) {
                List<String> child = new ArrayList<>(path);
                child.add(Integer.toString(index));
                compare(spanishList.get(index), localizedList.get(index), child, resource);
            }
            return;
        }
        if (isVisible(path)) {
            String originalText = Objects.toString(spanish, "");
            String localizedText = Objects.toString(localized, "");
            assertThat(tokens(TECHNICAL_TOKEN, localizedText))
                    .as(resource + " technical tokens at " + path)
                    .containsExactlyElementsOf(tokens(TECHNICAL_TOKEN, originalText));
            for (String coordinate : tokens(COORDINATE, originalText)) {
                assertThat(localizedText).as(resource + " coordinate at " + path).contains(coordinate);
            }
            if (!isGradientName(path, originalText)) {
                assertThat(tokens(COLOR_TOKEN, localizedText))
                        .as(resource + " colors at " + path)
                        .containsExactlyElementsOf(tokens(COLOR_TOKEN, originalText));
            }
            return;
        }
        assertThat(localized).as(resource + " immutable value at " + path).isEqualTo(spanish);
    }

    private boolean isVisible(List<String> path) {
        if (path.equals(List.of("menu_title"))) {
            return true;
        }
        return path.size() >= 3 && path.get(0).equals("items")
                && (path.get(2).equals("display_name") || path.get(2).equals("lore"));
    }

    private boolean isGradientName(List<String> path, String value) {
        return path.size() == 3 && path.get(0).equals("items") && path.get(1).equals("statistics")
                && path.get(2).equals("display_name") && tokens(COLOR_TOKEN, value).size() >= 4;
    }

    private List<String> tokens(Pattern pattern, String value) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> load(String locale, String menu) {
        String resource = "/deluxemenus/" + locale + "/" + menu;
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            Object loaded = new Yaml().load(stream);
            assertThat(loaded).as(resource).isInstanceOf(Map.class);
            return (Map<String, Object>) loaded;
        } catch (Exception exception) {
            throw new AssertionError("Invalid localized DeluxeMenus resource: " + resource, exception);
        }
    }
}
