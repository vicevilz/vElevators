package net.velevators.util;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

public final class IconCatalog {
    public static final List<Material> ICONS = List.of(
            Material.ENDER_CHEST, Material.BEACON, Material.QUARTZ_BLOCK, Material.EMERALD_BLOCK,
            Material.REDSTONE_BLOCK, Material.DIAMOND_BLOCK, Material.GOLD_BLOCK, Material.AMETHYST_BLOCK,
            Material.CHEST, Material.BARREL, Material.CRAFTING_TABLE, Material.FURNACE,
            Material.HOPPER, Material.SPAWNER, Material.OAK_DOOR, Material.IRON_DOOR,
            Material.COMPASS, Material.RECOVERY_COMPASS, Material.CLOCK, Material.FILLED_MAP,
            Material.NETHER_STAR, Material.DIAMOND, Material.EMERALD, Material.AMETHYST_SHARD,
            Material.BOOK, Material.BELL, Material.SEA_LANTERN, Material.GLOWSTONE,
            Material.OBSERVER, Material.TARGET, Material.RAIL, Material.MINECART,
            Material.END_CRYSTAL, Material.LODESTONE, Material.HEART_OF_THE_SEA, Material.DRAGON_EGG
    );

    private IconCatalog() {
    }

    public static Material parseAllowed(String value) {
        Material material = Material.matchMaterial(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        return material != null && ICONS.contains(material) ? material : null;
    }
}
