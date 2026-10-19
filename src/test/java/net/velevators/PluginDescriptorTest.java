package net.velevators;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PluginDescriptorTest {
    @SuppressWarnings("unchecked")
    @Test
    void declaresRequiredDependenciesAliasesAndDisabledPermissions() {
        try (InputStream stream = getClass().getResourceAsStream("/plugin.yml")) {
            assertThat(stream).isNotNull();
            Map<String, Object> root = new Yaml().load(stream);
            assertThat(root.get("version")).isEqualTo("1.1.0");
            assertThat(root.get("api-version")).isEqualTo("1.21");
            assertThat((List<String>) root.get("depend"))
                    .containsExactly("DeluxeMenus", "PlaceholderAPI", "LuckPerms", "Vault");

            Map<String, Object> commands = (Map<String, Object>) root.get("commands");
            assertThat(commands).containsOnlyKeys("velevatorsaction");

            Map<String, Object> permissions = (Map<String, Object>) root.get("permissions");
            assertThat(permissions).containsKeys("velevators.use", "velevators.create",
                    "velevators.manage", "velevators.manage.others",
                    "velevators.bypass.cost", "velevators.reload",
                    "velevators.adminlog", "velevators.bypass.access",
                    "velevators.admin.transactions.list",
                    "velevators.admin.transactions.collect",
                    "velevators.admin.transactions.total",
                    "velevators.admin.transactions.clear",
                    "velevators.admin.transactions.*",
                    "velevators.*");
            for (Object definition : permissions.values()) {
                assertThat(((Map<String, Object>) definition).get("default")).isEqualTo(false);
            }
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
