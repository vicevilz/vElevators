package net.velevators.menu;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeluxeMenusConfigEditorTest {
    @Test
    void insertsOnlyMissingEntriesWithoutDestroyingOtherSectionsOrComments() {
        String original = """
                # keep this comment
                check_updates: true
                gui_menus:
                  existing:
                    file: existing.yml
                debug: LOWEST
                """;
        Map<String, String> menus = new LinkedHashMap<>();
        menus.put("velevators_main", "vElevators/main.yml");
        menus.put("velevators_manage", "vElevators/manage.yml");

        String updated = DeluxeMenusConfigEditor.upsertMenus(original, menus);

        assertThat(updated).contains("# keep this comment", "debug: LOWEST", "  existing:",
                "  velevators_main:", "    file: vElevators/main.yml", "  velevators_manage:");
        assertThat(DeluxeMenusConfigEditor.upsertMenus(updated, menus)).isEqualTo(updated);
    }

    @Test
    void createsGuiMenusRootWhenMissing() {
        String updated = DeluxeMenusConfigEditor.upsertMenus("debug: LOWEST\n", Map.of(
                "velevators_main", "vElevators/main.yml"));
        assertThat(updated).contains("gui_menus:\n  velevators_main:\n    file: vElevators/main.yml");
    }

    @Test
    void createsACleanConfigFromAnEmptyFile() {
        String updated = DeluxeMenusConfigEditor.upsertMenus("", Map.of(
                "velevators_main", "vElevators/en_US/main.yml"));

        assertThat(updated).isEqualTo("gui_menus:\n  velevators_main:\n    file: vElevators/en_US/main.yml");
    }

    @Test
    void updatesOnlyManagedFilePathsWhenLocaleChanges() {
        String original = """
                gui_menus:
                  unrelated:
                    file: custom/menu.yml
                  velevators_main:
                    file: vElevators/en_US/main.yml
                    load_requirement:
                      requirements: {}
                debug: LOWEST
                """;

        String updated = DeluxeMenusConfigEditor.upsertMenus(original, Map.of(
                "velevators_main", "vElevators/es_ES/main.yml"));

        assertThat(updated).contains("file: custom/menu.yml", "file: vElevators/es_ES/main.yml",
                "load_requirement:", "debug: LOWEST");
        assertThat(updated).doesNotContain("vElevators/en_US/main.yml");
    }

    @Test
    void insertsMissingFileLineIntoAnExistingManagedEntry() {
        String original = """
                gui_menus:
                  velevators_main:
                    load_requirement:
                      requirements: {}
                """;

        String updated = DeluxeMenusConfigEditor.upsertMenus(original, Map.of(
                "velevators_main", "vElevators/en_US/main.yml"));

        assertThat(updated).contains("  velevators_main:\n    file: vElevators/en_US/main.yml\n    load_requirement:");
    }
}
