package net.velevators.config;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginSettingsTest {
    @Test
    void readsNetworkNamingActionBarParticlesAndIndependentTravelSounds() {
        YamlConfiguration config = new YamlConfiguration();
        // Avoid Paper's runtime registry-only Material#isBlock path in this unit test.
        config.set("elevator.block", "REDSTONE_BLOCK");
        config.set("naming.network-max-length", 48);
        config.set("effects.action-bar.enabled", false);
        config.set("effects.action-bar.text", "Destino: {floor}");
        config.set("effects.particles.enabled", true);
        config.set("effects.particles.up", "HAPPY_VILLAGER");
        config.set("effects.particles.down", "SMOKE");
        config.set("effects.particles.arrival", "END_ROD");
        config.set("effects.particles.count", 7);
        config.set("effects.particles.offset", 0.2D);
        config.set("effects.particles.speed", 0.1D);
        config.set("sounds.travel-up.sound", "BLOCK_NOTE_BLOCK_CHIME");
        config.set("sounds.travel-up.volume", 0.7D);
        config.set("sounds.travel-up.pitch", 1.4D);
        config.set("sounds.travel-down.sound", "BLOCK_NOTE_BLOCK_BASS");
        config.set("sounds.arrival.sound", "ENTITY_ENDERMAN_TELEPORT");
        config.set("sounds.access-denied.sound", "BLOCK_IRON_DOOR_CLOSE");
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("PluginSettingsTest"));

        PluginSettings settings = PluginSettings.load(plugin);

        assertThat(settings.maximumNetworkNameLength()).isEqualTo(48);
        assertThat(settings.actionBarEnabled()).isFalse();
        assertThat(settings.actionBarText()).isEqualTo("Destino: {floor}");
        assertThat(settings.upParticle()).isEqualTo(Particle.HAPPY_VILLAGER);
        assertThat(settings.downParticle()).isEqualTo(Particle.SMOKE);
        assertThat(settings.particleCount()).isEqualTo(7);
        assertThat(settings.particleOffset()).isEqualTo(0.2D);
        assertThat(settings.travelUpSound().sound()).isEqualTo(Sound.BLOCK_NOTE_BLOCK_CHIME);
        assertThat(settings.travelUpSound().volume()).isEqualTo(0.7F);
        assertThat(settings.travelUpSound().pitch()).isEqualTo(1.4F);
    }

    @Test
    void invalidValuesUseSafeDocumentedFallbacks() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("elevator.block", "REDSTONE_BLOCK");
        config.set("economy.creation-price", -5.0D);
        config.set("naming.max-length", 0);
        config.set("interaction.selection-timeout-seconds", 1);
        config.set("interaction.rename-timeout-seconds", 1);
        config.set("interaction.member-timeout-seconds", 1);
        config.set("interaction.teleport-cooldown-millis", -1L);
        config.set("sounds.success", "NOT_A_SOUND");
        config.set("sounds.error", "NOT_A_SOUND");
        config.set("sounds.teleport", "NOT_A_SOUND");
        config.set("effects.particles.up", "NOT_A_PARTICLE");
        config.set("effects.particles.down", "NOT_A_PARTICLE");
        config.set("effects.particles.arrival", "NOT_A_PARTICLE");
        config.set("effects.particles.count", -5);
        config.set("effects.particles.offset", -1.0D);
        config.set("effects.particles.speed", Double.NaN);
        config.set("sounds.travel-up.volume", -1.0D);
        config.set("sounds.travel-up.pitch", Double.POSITIVE_INFINITY);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("PluginSettingsTest"));

        PluginSettings settings = PluginSettings.load(plugin);

        assertThat(settings.elevatorMaterial()).isEqualTo(Material.QUARTZ_BLOCK);
        assertThat(settings.creationPrice()).isEqualByComparingTo(new BigDecimal("10000.0"));
        assertThat(settings.maximumNameLength()).isEqualTo(1);
        assertThat(settings.selectionTimeoutSeconds()).isEqualTo(5);
        assertThat(settings.renameTimeoutSeconds()).isEqualTo(5);
        assertThat(settings.memberTimeoutSeconds()).isEqualTo(5);
        assertThat(settings.teleportCooldownMillis()).isZero();
        assertThat(settings.successSound()).isEqualTo(Sound.ENTITY_PLAYER_LEVELUP);
        assertThat(settings.errorSound()).isEqualTo(Sound.BLOCK_NOTE_BLOCK_BASS);
        assertThat(settings.teleportSound()).isEqualTo(Sound.ENTITY_ENDERMAN_TELEPORT);
        assertThat(settings.upParticle()).isEqualTo(Particle.CLOUD);
        assertThat(settings.downParticle()).isEqualTo(Particle.REVERSE_PORTAL);
        assertThat(settings.arrivalParticle()).isEqualTo(Particle.END_ROD);
        assertThat(settings.particleCount()).isZero();
        assertThat(settings.particleOffset()).isEqualTo(0.35D);
        assertThat(settings.particleSpeed()).isEqualTo(0.05D);
        assertThat(settings.travelUpSound().volume()).isEqualTo(1.0F);
        assertThat(settings.travelUpSound().pitch()).isEqualTo(1.15F);
    }
}
