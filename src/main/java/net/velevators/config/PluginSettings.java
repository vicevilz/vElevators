package net.velevators.config;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.Locale;

public record PluginSettings(
        Material elevatorMaterial, BigDecimal creationPrice, int maximumNameLength,
        int maximumNetworkNameLength, int selectionTimeoutSeconds, int renameTimeoutSeconds,
        int memberTimeoutSeconds, long teleportCooldownMillis, boolean destinationSafety,
        boolean autoInstallMenus, Sound successSound, Sound errorSound,
        boolean actionBarEnabled, String actionBarText, boolean particlesEnabled,
        Particle upParticle, Particle downParticle, Particle arrivalParticle,
        int particleCount, double particleOffset, double particleSpeed,
        SoundSetting travelUpSound, SoundSetting travelDownSound,
        SoundSetting arrivalSound, SoundSetting accessDeniedSound
) {
    public record SoundSetting(Sound sound, float volume, float pitch) {
    }

    public PluginSettings(Material elevatorMaterial, BigDecimal creationPrice, int maximumNameLength,
                          int selectionTimeoutSeconds, int renameTimeoutSeconds, int memberTimeoutSeconds,
                          long teleportCooldownMillis, boolean destinationSafety, boolean autoInstallMenus,
                          Sound successSound, Sound errorSound, Sound teleportSound) {
        this(elevatorMaterial, creationPrice, maximumNameLength, 32, selectionTimeoutSeconds,
                renameTimeoutSeconds, memberTimeoutSeconds, teleportCooldownMillis, destinationSafety,
                autoInstallMenus, successSound, errorSound, true,
                "&#68D878Has llegado a &#F1E7CC{floor}", true,
                Particle.CLOUD, Particle.REVERSE_PORTAL, Particle.END_ROD, 18, 0.35D, 0.05D,
                new SoundSetting(teleportSound, 1.0F, 1.15F),
                new SoundSetting(teleportSound, 1.0F, 0.85F),
                new SoundSetting(teleportSound, 1.0F, 1.0F),
                new SoundSetting(Sound.BLOCK_IRON_DOOR_CLOSE, 1.0F, 0.8F));
    }

    public static PluginSettings load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        Material material = parseMaterial(plugin, config.getString("elevator.block", "QUARTZ_BLOCK"));
        BigDecimal price = parsePrice(plugin, config.getDouble("economy.creation-price", 10_000.0D));
        int maximumNameLength = Math.max(1, config.getInt("naming.max-length", 20));
        int maximumNetworkNameLength = Math.max(1, config.getInt("naming.network-max-length", 32));
        int selectionTimeout = Math.max(5, config.getInt("interaction.selection-timeout-seconds", 60));
        int renameTimeout = Math.max(5, config.getInt("interaction.rename-timeout-seconds", 60));
        int memberTimeout = Math.max(5, config.getInt("interaction.member-timeout-seconds", 60));
        long cooldown = Math.max(0L, config.getLong("interaction.teleport-cooldown-millis", 1_000L));
        boolean safety = config.getBoolean("interaction.destination-safety", true);
        boolean installMenus = config.getBoolean("menus.auto-install", true);
        Sound success = parseSound(plugin, config.getString("sounds.success", "ENTITY_PLAYER_LEVELUP"),
                Sound.ENTITY_PLAYER_LEVELUP);
        Sound error = parseSound(plugin, config.getString("sounds.error", "BLOCK_NOTE_BLOCK_BASS"),
                Sound.BLOCK_NOTE_BLOCK_BASS);
        Sound legacyTeleport = parseSound(plugin, config.getString("sounds.teleport", "ENTITY_ENDERMAN_TELEPORT"),
                Sound.ENTITY_ENDERMAN_TELEPORT);
        boolean actionBarEnabled = config.getBoolean("effects.action-bar.enabled", true);
        String actionBarText = config.getString("effects.action-bar.text", "&#68D878Has llegado a &#F1E7CC{floor}");
        boolean particlesEnabled = config.getBoolean("effects.particles.enabled", true);
        Particle up = parseParticle(plugin, config.getString("effects.particles.up", "CLOUD"), Particle.CLOUD);
        Particle down = parseParticle(plugin, config.getString("effects.particles.down", "REVERSE_PORTAL"),
                Particle.REVERSE_PORTAL);
        Particle arrival = parseParticle(plugin, config.getString("effects.particles.arrival", "END_ROD"),
                Particle.END_ROD);
        int particleCount = Math.max(0, config.getInt("effects.particles.count", 18));
        double particleOffset = finiteNonNegative(config.getDouble("effects.particles.offset", 0.35D), 0.35D);
        double particleSpeed = finiteNonNegative(config.getDouble("effects.particles.speed", 0.05D), 0.05D);
        SoundSetting travelUp = parseSoundSetting(plugin, config, "sounds.travel-up", legacyTeleport, 1.0F, 1.15F);
        SoundSetting travelDown = parseSoundSetting(plugin, config, "sounds.travel-down", legacyTeleport, 1.0F, 0.85F);
        SoundSetting arrivalSound = parseSoundSetting(plugin, config, "sounds.arrival", legacyTeleport, 1.0F, 1.0F);
        SoundSetting deniedSound = parseSoundSetting(plugin, config, "sounds.access-denied",
                Sound.BLOCK_IRON_DOOR_CLOSE, 1.0F, 0.8F);
        return new PluginSettings(material, price, maximumNameLength, maximumNetworkNameLength,
                selectionTimeout, renameTimeout, memberTimeout, cooldown, safety, installMenus,
                success, error, actionBarEnabled, actionBarText == null ? "" : actionBarText,
                particlesEnabled, up, down, arrival, particleCount, particleOffset, particleSpeed,
                travelUp, travelDown, arrivalSound, deniedSound);
    }

    public Sound teleportSound() {
        return arrivalSound.sound();
    }

    private static SoundSetting parseSoundSetting(JavaPlugin plugin, FileConfiguration config, String path,
                                                  Sound fallback, float volume, float pitch) {
        Sound sound = parseSound(plugin, config.getString(path + ".sound", fallback.name()), fallback);
        float configuredVolume = (float) finiteNonNegative(config.getDouble(path + ".volume", volume), volume);
        float configuredPitch = (float) finiteNonNegative(config.getDouble(path + ".pitch", pitch), pitch);
        return new SoundSetting(sound, configuredVolume, configuredPitch);
    }

    private static Material parseMaterial(JavaPlugin plugin, String configured) {
        Material material = Material.matchMaterial(configured == null ? "" : configured.trim());
        if (material == null || material == Material.REDSTONE_BLOCK || !material.isBlock() || !material.isSolid()) {
            plugin.getLogger().warning("elevator.block debe ser un bloque sólido distinto de REDSTONE_BLOCK. Se usará QUARTZ_BLOCK.");
            return Material.QUARTZ_BLOCK;
        }
        return material;
    }

    private static BigDecimal parsePrice(JavaPlugin plugin, double configured) {
        if (!Double.isFinite(configured) || configured < 0.0D) {
            plugin.getLogger().warning("economy.creation-price debe ser un número finito no negativo. Se usará 10000.0.");
            return BigDecimal.valueOf(10_000.0D);
        }
        return BigDecimal.valueOf(configured);
    }

    private static Sound parseSound(JavaPlugin plugin, String configured, Sound fallback) {
        try {
            return Sound.valueOf((configured == null ? "" : configured).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Sonido inválido '" + configured + "'. Se usará " + fallback.name() + ".");
            return fallback;
        }
    }

    private static Particle parseParticle(JavaPlugin plugin, String configured, Particle fallback) {
        try {
            return Particle.valueOf((configured == null ? "" : configured).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Partícula inválida '" + configured + "'. Se usará " + fallback.name() + ".");
            return fallback;
        }
    }

    private static double finiteNonNegative(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0D ? value : fallback;
    }
}
