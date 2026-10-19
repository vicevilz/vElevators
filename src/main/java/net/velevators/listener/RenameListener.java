package net.velevators.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.velevators.config.MessageService;
import net.velevators.config.PluginSettings;
import net.velevators.menu.MenuService;
import net.velevators.model.NetworkMemberRecord;
import net.velevators.model.NetworkRecord;
import net.velevators.service.ElevatorService;
import net.velevators.session.PlayerSession;
import net.velevators.session.SessionManager;
import net.velevators.session.SessionMode;
import net.velevators.util.ManagementAccess;
import net.velevators.util.MinecraftPlayerName;
import net.velevators.util.NameValidator;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.function.Supplier;

public final class RenameListener implements Listener {
    private final JavaPlugin plugin;
    private final ElevatorService elevators;
    private final MenuService menus;
    private final SessionManager sessions;
    private final MessageService messages;
    private final Supplier<PluginSettings> settings;

    public RenameListener(JavaPlugin plugin, ElevatorService elevators, MenuService menus,
                          SessionManager sessions, MessageService messages,
                          Supplier<PluginSettings> settings) {
        this.plugin = plugin;
        this.elevators = elevators;
        this.menus = menus;
        this.sessions = sessions;
        this.messages = messages;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        PlayerSession session = sessions.get(event.getPlayer().getUniqueId()).orElse(null);
        if (session == null || (session.mode() != SessionMode.RENAMING
                && session.mode() != SessionMode.RENAMING_NETWORK
                && session.mode() != SessionMode.ADDING_MEMBER
                && session.mode() != SessionMode.REMOVING_MEMBER
                && session.mode() != SessionMode.ADMIN_SEARCH_OWNER
                && session.mode() != SessionMode.ADMIN_SEARCH_WORLD
                && session.mode() != SessionMode.ADMIN_SEARCH_COORDINATES)) {
            return;
        }
        if (session.mode() == SessionMode.RENAMING && session.selectedFloorId() == null) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());
        Long floorId = session.selectedFloorId();
        long networkId = session.networkId() == null ? -1L : session.networkId();
        Long currentFloorId = session.currentFloorId();
        int page = session.page();
        SessionMode mode = session.mode();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (mode == SessionMode.RENAMING && floorId != null) {
                processRename(event.getPlayer(), input, floorId, networkId, currentFloorId, page);
            } else if (mode == SessionMode.RENAMING_NETWORK) {
                processNetworkRename(event.getPlayer(), input, networkId, currentFloorId, page);
            } else if (mode == SessionMode.ADMIN_SEARCH_OWNER || mode == SessionMode.ADMIN_SEARCH_WORLD
                    || mode == SessionMode.ADMIN_SEARCH_COORDINATES) {
                processAdminSearch(event.getPlayer(), input, mode);
            } else {
                processMember(event.getPlayer(), input, mode, networkId, currentFloorId, page);
            }
        });
    }

    private void processRename(Player player, String input, long floorId, long networkId,
                               Long currentFloorId, int page) {
        PlayerSession active = sessions.peek(player.getUniqueId()).orElse(null);
        if (active == null || active.mode() != SessionMode.RENAMING
                || active.selectedFloorId() == null || active.selectedFloorId() != floorId) {
            messages.send(player, "errors.session-expired");
            return;
        }
        String validated;
        try {
            validated = NameValidator.validate(input, settings.get().maximumNameLength());
        } catch (IllegalArgumentException exception) {
            String key = switch (exception.getMessage()) {
                case "color" -> "rename.invalid-color";
                case "length" -> "rename.too-long";
                default -> "rename.invalid";
            };
            messages.send(player, key, Map.of("limit", Integer.toString(settings.get().maximumNameLength())));
            menus.sendFloorPrompt(player, settings.get().maximumNameLength());
            return;
        }
        try {
            NetworkRecord network = elevators.database().getNetwork(networkId).orElse(null);
            boolean member = network != null && elevators.database().isMember(networkId, player.getUniqueId());
            if (network == null || !ManagementAccess.canManage(player.getUniqueId(), network.ownerId(), member,
                    player.hasPermission("velevators.manage"),
                    player.hasPermission("velevators.manage.others")
                            || player.hasPermission("velevators.adminlog"))) {
                messages.send(player, "errors.not-owner");
                sessions.remove(player.getUniqueId());
                return;
            }
            if (!elevators.renameFloor(floorId, validated, player)) {
                messages.send(player, "errors.floor-not-found");
                sessions.remove(player.getUniqueId());
                return;
            }
            messages.send(player, "rename.success", Map.of("name", validated));
            player.playSound(player.getLocation(), settings.get().successSound(), 1.0F, 1.0F);
            menus.openManagement(player, networkId, currentFloorId, page);
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo renombrar el piso: " + exception.getMessage());
            messages.send(player, "errors.database");
        }
    }

    private void processNetworkRename(Player player, String input, long networkId,
                                      Long currentFloorId, int page) {
        PlayerSession active = sessions.peek(player.getUniqueId()).orElse(null);
        if (active == null || active.mode() != SessionMode.RENAMING_NETWORK || active.networkId() == null
                || active.networkId() != networkId) {
            messages.send(player, "errors.session-expired");
            return;
        }
        String validated;
        try {
            validated = NameValidator.validate(input, settings.get().maximumNetworkNameLength());
        } catch (IllegalArgumentException exception) {
            messages.send(player, "network.rename-invalid",
                    Map.of("limit", Integer.toString(settings.get().maximumNetworkNameLength())));
            messages.send(player, "network.rename-prompt",
                    Map.of("limit", Integer.toString(settings.get().maximumNetworkNameLength())));
            return;
        }
        try {
            NetworkRecord network = elevators.database().getNetwork(networkId).orElse(null);
            if (network == null || !ManagementAccess.canAdminister(player.getUniqueId(), network.ownerId(),
                    player.hasPermission("velevators.manage"),
                    player.hasPermission("velevators.manage.others")
                            || player.hasPermission("velevators.adminlog"))) {
                messages.send(player, "errors.owner-only");
                sessions.remove(player.getUniqueId());
                return;
            }
            if (!elevators.renameNetwork(networkId, validated, player)) {
                messages.send(player, "errors.network-not-found");
                return;
            }
            messages.send(player, "network.rename-success", Map.of("name", validated));
            menus.openManagement(player, networkId, currentFloorId, page);
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo renombrar la red: " + exception.getMessage());
            messages.send(player, "errors.database");
        }
    }

    private void processAdminSearch(Player player, String rawInput, SessionMode mode) {
        PlayerSession active = sessions.peek(player.getUniqueId()).orElse(null);
        if (active == null || active.mode() != mode || !player.hasPermission("velevators.adminlog")) {
            messages.send(player, "errors.session-expired");
            return;
        }
        String input = rawInput.trim();
        try {
            java.util.List<NetworkRecord> networks;
            if (mode == SessionMode.ADMIN_SEARCH_OWNER) {
                if (!MinecraftPlayerName.isValid(input)) {
                    messages.send(player, "members.invalid-name");
                    return;
                }
                networks = elevators.database().findNetworksByOwnerName(input);
            } else if (mode == SessionMode.ADMIN_SEARCH_WORLD) {
                if (input.isBlank()) {
                    messages.send(player, "admin-search.invalid");
                    return;
                }
                networks = elevators.database().findNetworksByWorld(input);
            } else {
                String[] parts = input.split("\\s+");
                String world;
                int x;
                int z;
                try {
                    if (parts.length == 2) {
                        world = player.getWorld().getName();
                        x = Integer.parseInt(parts[0]);
                        z = Integer.parseInt(parts[1]);
                    } else if (parts.length == 3) {
                        world = parts[0];
                        x = Integer.parseInt(parts[1]);
                        z = Integer.parseInt(parts[2]);
                    } else {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException exception) {
                    messages.send(player, "admin-search.invalid-coordinates");
                    return;
                }
                networks = elevators.database().findNetworksByCoordinates(world, x, z);
            }
            if (networks.isEmpty()) {
                messages.send(player, "admin-search.no-results");
            }
            menus.openAdminNetworks(player, networks, 0);
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo completar la búsqueda administrativa: " + exception.getMessage());
            messages.send(player, "errors.database");
        }
    }

    private void processMember(Player player, String rawInput, SessionMode expectedMode, long networkId,
                               Long currentFloorId, int page) {
        PlayerSession active = sessions.peek(player.getUniqueId()).orElse(null);
        if (active == null || active.mode() != expectedMode || active.networkId() == null
                || active.networkId() != networkId || (expectedMode != SessionMode.ADDING_MEMBER
                && expectedMode != SessionMode.REMOVING_MEMBER)) {
            messages.send(player, "errors.session-expired");
            return;
        }
        String input = rawInput.trim();
        if (!MinecraftPlayerName.isValid(input)) {
            messages.send(player, "members.invalid-name");
            menus.sendMemberPrompt(player, expectedMode == SessionMode.ADDING_MEMBER,
                    active.remainingSeconds());
            return;
        }

        try {
            NetworkRecord network = elevators.database().getNetwork(networkId).orElse(null);
            if (network == null) {
                messages.send(player, "errors.network-not-found");
                sessions.remove(player.getUniqueId());
                return;
            }
            if (!ManagementAccess.canAdminister(player.getUniqueId(), network.ownerId(),
                    player.hasPermission("velevators.manage"),
                    player.hasPermission("velevators.manage.others")
                            || player.hasPermission("velevators.adminlog"))) {
                messages.send(player, "errors.owner-only");
                sessions.remove(player.getUniqueId());
                return;
            }

            if (expectedMode == SessionMode.ADDING_MEMBER) {
                addMember(player, input, network, currentFloorId, page);
            } else {
                removeMember(player, input, network, currentFloorId, page);
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo modificar un miembro de la red: " + exception.getMessage());
            messages.send(player, "errors.database");
            sessions.remove(player.getUniqueId());
        }
    }

    private void addMember(Player player, String input, NetworkRecord network,
                           Long currentFloorId, int page) throws SQLException {
        Player online = Bukkit.getPlayerExact(input);
        OfflinePlayer target = online == null ? Bukkit.getOfflinePlayerIfCached(input) : online;
        if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
            messages.send(player, "members.player-not-found", Map.of("player", input));
            menus.sendMemberPrompt(player, true, remainingSeconds(player));
            return;
        }
        String targetName = target.getName() == null ? input : target.getName();
        ElevatorService.MemberResult result = elevators.addMember(
                network.id(), target.getUniqueId(), targetName, player.getUniqueId(), player.getName());
        switch (result.status()) {
            case SUCCESS -> {
                messages.send(player, "members.added", Map.of("player", targetName));
                player.playSound(player.getLocation(), settings.get().successSound(), 0.8F, 1.15F);
                menus.openManagement(player, network.id(), currentFloorId, page);
            }
            case OWNER -> {
                messages.send(player, "members.owner-not-member");
                menus.sendMemberPrompt(player, true, remainingSeconds(player));
            }
            case ALREADY_MEMBER -> {
                messages.send(player, "members.already-member", Map.of("player", targetName));
                menus.sendMemberPrompt(player, true, remainingSeconds(player));
            }
            case NETWORK_NOT_FOUND -> {
                messages.send(player, "errors.network-not-found");
                sessions.remove(player.getUniqueId());
            }
            case NOT_MEMBER -> throw new IllegalStateException("Estado imposible al añadir un miembro");
        }
    }

    private void removeMember(Player player, String input, NetworkRecord network,
                              Long currentFloorId, int page) throws SQLException {
        NetworkMemberRecord member = elevators.database().findMemberByName(network.id(), input).orElse(null);
        if (member == null) {
            messages.send(player, "members.not-member", Map.of("player", input));
            menus.sendMemberPrompt(player, false, remainingSeconds(player));
            return;
        }
        ElevatorService.MemberResult result = elevators.removeMember(
                network.id(), member.memberId(), member.memberName(), player.getUniqueId(), player.getName());
        switch (result.status()) {
            case SUCCESS -> {
                messages.send(player, "members.removed", Map.of("player", member.memberName()));
                player.playSound(player.getLocation(), settings.get().successSound(), 0.8F, 0.95F);
                menus.openManagement(player, network.id(), currentFloorId, page);
            }
            case NOT_MEMBER -> {
                messages.send(player, "members.not-member", Map.of("player", input));
                menus.sendMemberPrompt(player, false, remainingSeconds(player));
            }
            case OWNER -> {
                messages.send(player, "members.owner-not-member");
                menus.sendMemberPrompt(player, false, remainingSeconds(player));
            }
            case NETWORK_NOT_FOUND -> {
                messages.send(player, "errors.network-not-found");
                sessions.remove(player.getUniqueId());
            }
            case ALREADY_MEMBER -> throw new IllegalStateException("Estado imposible al eliminar un miembro");
        }
    }

    private int remainingSeconds(Player player) {
        return sessions.peek(player.getUniqueId()).map(PlayerSession::remainingSeconds).orElse(0);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!event.getMessage().equalsIgnoreCase("/cancelar")
                && !event.getMessage().equalsIgnoreCase("/cancel")) {
            return;
        }
        PlayerSession session = sessions.get(event.getPlayer().getUniqueId()).orElse(null);
        if (session == null || session.networkId() == null || (session.mode() != SessionMode.RENAMING
                && session.mode() != SessionMode.RENAMING_NETWORK
                && session.mode() != SessionMode.ADDING_MEMBER
                && session.mode() != SessionMode.REMOVING_MEMBER)) {
            if (session == null || (session.mode() != SessionMode.ADMIN_SEARCH_OWNER
                    && session.mode() != SessionMode.ADMIN_SEARCH_WORLD
                    && session.mode() != SessionMode.ADMIN_SEARCH_COORDINATES)) {
                return;
            }
        }
        event.setCancelled(true);
        if (session.mode() == SessionMode.ADMIN_SEARCH_OWNER || session.mode() == SessionMode.ADMIN_SEARCH_WORLD
                || session.mode() == SessionMode.ADMIN_SEARCH_COORDINATES) {
            messages.send(event.getPlayer(), "admin-search.cancelled");
            menus.openAdminMain(event.getPlayer(), 0);
            return;
        }
        long networkId = session.networkId();
        Long currentFloor = session.currentFloorId();
        int page = session.page();
        messages.send(event.getPlayer(), session.mode() == SessionMode.RENAMING
                || session.mode() == SessionMode.RENAMING_NETWORK
                ? "rename.cancelled" : "members.cancelled");
        menus.openManagement(event.getPlayer(), networkId, currentFloor, page);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }
}
