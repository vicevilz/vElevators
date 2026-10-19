package net.velevators.listener;

import net.velevators.config.MessageService;
import net.velevators.service.ElevatorService;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class ProtectionListener implements Listener {
    private final JavaPlugin plugin;
    private final ElevatorService elevators;
    private final MessageService messages;

    public ProtectionListener(JavaPlugin plugin, ElevatorService elevators, MessageService messages) {
        this.plugin = plugin;
        this.elevators = elevators;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getPlayer().hasPermission("velevators.manage.others")) {
            return;
        }
        if (isProtected(event.getBlock())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "protection.registered");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isProtected)
                || event.getBlocks().stream().map(block -> block.getRelative(event.getDirection())).anyMatch(this::isProtected)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isProtected)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isProtected);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isProtected);
    }

    private boolean isProtected(Block block) {
        try {
            return elevators.findProtectedFloor(block).isPresent();
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo comprobar la protección del bloque: " + exception.getMessage());
            return true;
        }
    }
}
