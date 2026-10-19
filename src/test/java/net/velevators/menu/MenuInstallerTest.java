package net.velevators.menu;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MenuInstallerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void installsEveryTranslationButRegistersOnlyTheSelectedLocaleWithoutOverwritingFiles() throws Exception {
        Path plugins = temporaryDirectory.resolve("plugins");
        Path dataFolder = plugins.resolve("vElevators");
        Path deluxe = plugins.resolve("DeluxeMenus");
        Files.createDirectories(deluxe);
        Files.writeString(deluxe.resolve("config.yml"), "gui_menus:\n  existing:\n    file: existing.yml\n");
        Path menuFolder = deluxe.resolve("gui_menus/vElevators");
        Path spanishFolder = menuFolder.resolve("es_ES");
        Files.createDirectories(spanishFolder);
        Files.writeString(spanishFolder.resolve("manage.yml"), "custom: true\n");

        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getResource(anyString())).thenAnswer(invocation -> {
            String resource = invocation.getArgument(0, String.class);
            return getClass().getResourceAsStream("/" + resource);
        });

        MenuInstaller installer = new MenuInstaller(plugin);
        assertThat(installer.installForLocale("en_US")).isTrue();
        assertThat(Files.readString(spanishFolder.resolve("manage.yml"))).isEqualTo("custom: true\n");
        for (String locale : net.velevators.config.MessageService.SUPPORTED_LOCALES) {
            Path localizedFolder = menuFolder.resolve(locale);
            assertThat(localizedFolder).isDirectory();
            try (var files = Files.list(localizedFolder)) {
                assertThat(files.filter(path -> path.getFileName().toString().endsWith(".yml")).count()).isEqualTo(15);
            }
        }

        String config = Files.readString(deluxe.resolve("config.yml"));
        assertThat(config).contains("velevators_main:", "velevators_manage:", "velevators_confirm_create:",
                "velevators_confirm_delete:", "velevators_add_member:", "velevators_remove_member:",
                "velevators_network_members:", "velevators_transfer_owner:", "velevators_confirm_transfer:",
                "velevators_confirm_delete_network:", "velevators_network_icon:", "velevators_floor_icon:",
                "velevators_admin_main:", "velevators_admin_networks:", "velevators_admin_history:");
        assertThat(config).contains("file: vElevators/en_US/main.yml", "file: vElevators/en_US/manage.yml");
        assertThat(config).doesNotContain("file: vElevators/es_ES/main.yml");
        assertThat(installer.installForLocale("en_US")).isFalse();

        assertThat(installer.installForLocale("es_ES")).isTrue();
        String spanishConfig = Files.readString(deluxe.resolve("config.yml"));
        assertThat(spanishConfig).contains("file: vElevators/es_ES/main.yml", "file: vElevators/es_ES/manage.yml");
        assertThat(spanishConfig).doesNotContain("file: vElevators/en_US/main.yml");
        assertThat(installer.installForLocale("es_ES")).isFalse();
    }

    @Test
    void rejectsAnUnsupportedMenuLocale() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(temporaryDirectory.resolve("plugins/vElevators").toFile());

        MenuInstaller installer = new MenuInstaller(plugin);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> installer.installForLocale("xx_XX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xx_XX");
    }
}
