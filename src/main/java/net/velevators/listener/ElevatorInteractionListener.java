package net.velevators.listener;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import net.velevators.config.MessageService;
import net.velevators.config.PluginSettings;
import net.velevators.menu.MenuService;
import net.velevators.model.FloorLocation;
import net.velevators.model.FloorRecord;
import net.velevators.service.ElevatorService;
import net.velevators.session.PlayerSession;
import net.velevators.session.SessionManager;
import net.velevators.session.SessionMode;
import net.velevators.util.ColorCodes;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.function.Supplier;

public final class ElevatorInteractionListener implements Listener {
    private final JavaPlugin plugin;
    private final ElevatorService elevators;
    private final MenuService menus;
    private final SessionManager sessions;
    private final MessageService messages;
    private final Supplier<PluginSettings> settings;

    public ElevatorInteractionListener(JavaPlugin plugin, ElevatorService elevators, MenuService menus,
                                       SessionManager sessions, MessageService messages,
                                       Supplier<PluginSettings> settings) {
        this.plugin = plugin;
        this.elevators = elevators;
        this.menus = menus;
        this.sessions = sessions;
        this.messages = messages;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        PlayerSession session = sessions.get(player.getUniqueId()).orElse(null);
        if (session != null && session.mode() == SessionMode.SELECTING) {
            event.setCancelled(true);
            if (!player.hasPermission("velevators.create")) {
                messages.send(player, "errors.no-permission");
                sessions.remove(player.getUniqueId());
                return;
            }
            if (!elevators.isValidNewStructure(block)) {
                messages.send(player, "creation.invalid-structure");
                player.playSound(player.getLocation(), settings.get().errorSound(), 1.0F, 0.8F);
                return;
            }
            FloorLocation location = new FloorLocation(block.getWorld().getUID(), block.getWorld().getName(),
                    block.getX(), block.getY(), block.getZ());
            try {
                if (elevators.findFloor(block).isPresent()) {
                    messages.send(player, "creation.already-registered");
                    return;
                }
            } catch (SQLException exception) {
                plugin.getLogger().severe("No se pudo consultar el piso: " + exception.getMessage());
                messages.send(player, "errors.database");
                return;
            }
            menus.openConfirmCreate(player, location, settings.get().selectionTimeoutSeconds());
            return;
        }

        try {
            FloorRecord floor = elevators.findFloor(block).orElse(null);
            if (floor == null) {
                return;
            }
            event.setCancelled(true);
            if (!player.hasPermission("velevators.manage")
                    && !player.hasPermission("velevators.manage.others")
                    && !player.hasPermission("velevators.adminlog")) {
                messages.send(player, "errors.no-permission");
                return;
            }
            menus.openManagement(player, floor.networkId(), floor.id(), 0);
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo abrir la gestión del ascensor: " + exception.getMessage());
            messages.send(player, "errors.database");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("velevators.use")) {
            return;
        }
        Block below = player.getLocation().getBlock().getRelative(0, -1, 0);
        try {
            if (elevators.findFloor(below).isEmpty()) {
                return;
            }
        } catch (SQLException exception) {
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> handleTravel(player, 1));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking() || !event.getPlayer().hasPermission("velevators.use")) {
            return;
        }
        Block below = event.getPlayer().getLocation().getBlock().getRelative(0, -1, 0);
        try {
            if (elevators.findFloor(below).isPresent()) {
                handleTravel(event.getPlayer(), -1);
            }
        } catch (SQLException ignored) {
            messages.send(event.getPlayer(), "errors.database");
        }
    }

    private void handleTravel(Player player, int direction) {
        ElevatorService.TravelResult result = elevators.travelDetailed(player, direction);
        switch (result.status()) {
            case SUCCESS -> showTravelEffects(player, result);
            case NO_DESTINATION -> messages.send(player,
                    direction > 0 ? "travel.no-floor-above" : "travel.no-floor-below");
            case UNSAFE_DESTINATION -> messages.send(player, "travel.unsafe");
            case BROKEN_STRUCTURE -> messages.send(player, "travel.broken");
            case LOCKED -> {
                messages.send(player, "travel.inaccessible");
                play(player, settings.get().accessDeniedSound());
            }
            case ERROR -> messages.send(player, "errors.database");
            case NOT_ON_ELEVATOR, COOLDOWN -> {
            }
        }
    }

    private void showTravelEffects(Player player, ElevatorService.TravelResult result) {
        PluginSettings configured = settings.get();
        PluginSettings.SoundSetting movement = result.direction() > 0
                ? configured.travelUpSound() : configured.travelDownSound();
        play(player, movement);
        if (result.source() != null) {
            World sourceWorld = Bukkit.getWorld(result.source().worldId());
            if (sourceWorld != null) {
                Location source = new Location(sourceWorld, result.source().x() + 0.5D,
                        result.source().y() + 1.0D, result.source().z() + 0.5D);
                if (configured.particlesEnabled()) {
                    sourceWorld.spawnParticle(result.direction() > 0 ? configured.upParticle() : configured.downParticle(),
                            source, configured.particleCount(), configured.particleOffset(),
                            configured.particleOffset(), configured.particleOffset(), configured.particleSpeed());
                }
            }
        }
        play(player, configured.arrivalSound());
        if (configured.particlesEnabled()) {
            player.getWorld().spawnParticle(configured.arrivalParticle(), player.getLocation(),
                    configured.particleCount(), configured.particleOffset(), configured.particleOffset(),
                    configured.particleOffset(), configured.particleSpeed());
        }
        if (configured.actionBarEnabled()) {
            String text = configured.actionBarText().replace("{floor}", result.destinationName());
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(ColorCodes.colorize(text)));
        }
    }

    private void play(Player player, PluginSettings.SoundSetting sound) {
        player.playSound(player.getLocation(), sound.sound(), sound.volume(), sound.pitch());
    }
}
