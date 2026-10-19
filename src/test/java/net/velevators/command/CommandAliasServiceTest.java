package net.velevators.command;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandAliasServiceTest {
    @Test
    void normalizesDeduplicatesAndRejectsInvalidOrReservedAliases() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("command.aliases", List.of(
                "Elevator", "elevator", "elevators", "bad alias", "velevators", "velevatorsaction"));
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CommandAliasServiceTest"));

        CommandAliasService.AliasResult result = new CommandAliasService(plugin).configuredAliases();

        assertThat(result.aliases()).containsExactly("elevator", "elevators");
        assertThat(result.rejected()).containsExactly("bad alias", "velevators", "velevatorsaction");
    }
}
