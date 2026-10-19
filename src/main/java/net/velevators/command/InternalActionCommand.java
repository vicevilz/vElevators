package net.velevators.command;

import net.velevators.config.MessageService;
import net.velevators.config.PluginSettings;
import net.velevators.menu.MenuService;
import net.velevators.model.FloorLocation;
import net.velevators.model.FloorView;
import net.velevators.model.MemberCandidateView;
import net.velevators.model.NetworkRecord;
import net.velevators.model.AccessMode;
import net.velevators.model.NetworkSummaryView;
import net.velevators.service.ElevatorService;
import net.velevators.session.PlayerSession;
import net.velevators.session.SessionManager;
import net.velevators.session.SessionMode;
import net.velevators.util.ManagementAccess;
import net.velevators.util.IconCatalog;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class InternalActionCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final ElevatorService elevators;
    private final MenuService menus;
    private final SessionManager sessions;
    private final MessageService messages;
    private final Supplier<PluginSettings> settings;

    public InternalActionCommand(JavaPlugin plugin, ElevatorService elevators, MenuService menus,
                                 SessionManager sessions, MessageService messages,
                                 Supplier<PluginSettings> settings) {
        this.plugin = plugin;
        this.elevators = elevators;
        this.menus = menus;
        this.sessions = sessions;
        this.messages = messages;
        this.settings = settings;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player) || args.length == 0) {
            return true;
        }
        PlayerSession session = sessions.get(player.getUniqueId()).orElse(null);
        if (session == null) {
            messages.send(player, "errors.session-expired");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "select" -> startSelection(player, session);
            case "confirmcreate" -> confirmCreation(player, session);
            case "cancelcreate" -> cancelCreation(player, session);
            case "page" -> changePage(player, session, args);
            case "rename" -> startRename(player, session, args);
            case "delete" -> startDelete(player, session, args);
            case "confirmdelete" -> confirmDelete(player, session);
            case "canceldelete" -> cancelDelete(player, session);
            case "addmember" -> startMemberSelection(player, session, true);
            case "removemember" -> startMemberSelection(player, session, false);
            case "memberpage" -> changeMemberPage(player, session, args);
            case "memberselect" -> selectMember(player, session, args);
            case "memberchat" -> startMemberChatInput(player, session);
            case "memberback" -> returnFromMemberSelection(player, session);
            case "togglelock" -> toggleLock(player, session);
            case "renamenetwork" -> startNetworkRename(player, session);
            case "networkicons" -> openNetworkIcons(player, session);
            case "flooricons" -> openFloorIcons(player, session, args);
            case "setnetworkicon" -> setNetworkIcon(player, session, args);
            case "setflooricon" -> setFloorIcon(player, session, args);
            case "networkaccess" -> cycleNetworkAccess(player, session);
            case "flooraccess" -> cycleFloorAccess(player, session, args);
            case "viewmembers" -> openMemberDirectory(player, session, false);
            case "transferowner" -> openMemberDirectory(player, session, true);
            case "directorypage" -> changeDirectoryPage(player, session, args);
            case "transferselect" -> selectTransferTarget(player, session, args);
            case "confirmtransfer" -> confirmTransfer(player, session);
            case "canceltransfer" -> cancelTransfer(player, session);
            case "deletenetwork" -> startDeleteNetwork(player, session);
            case "confirmdeletenetwork" -> confirmDeleteNetwork(player, session);
            case "canceldeletenetwork" -> cancelDeleteNetwork(player, session);
            case "adminplayerpage" -> changeAdminPlayerPage(player, session, args);
            case "adminplayerselect" -> selectAdminPlayer(player, session, args);
            case "adminsearch" -> startAdminSearch(player, args);
            case "adminnetworkpage" -> changeAdminNetworkPage(player, session, args);
            case "adminnetworkmanage" -> adminManageNetwork(player, session, args);
            case "adminnetworkteleport" -> adminTeleportNetwork(player, session, args);
            case "adminhistory" -> openAdminHistory(player, 0);
            case "adminhistorypage" -> changeAdminHistoryPage(player, session, args);
            case "adminback" -> openAdminMain(player, 0);
            case "manageback" -> returnToManagement(player, session);
            case "close" -> {
                sessions.remove(player.getUniqueId());
                player.closeInventory();
            }
            default -> messages.send(player, "errors.session-expired");
        }
        return true;
    }

    private void startSelection(Player player, PlayerSession session) {
        if (session.mode() != SessionMode.MAIN || !player.hasPermission("velevators.create")) {
            messages.send(player, "errors.no-permission");
            return;
        }
        int timeout = settings.get().selectionTimeoutSeconds();
        session.selecting(timeout);
        player.closeInventory();
        messages.send(player, "creation.select", Map.of("seconds", Integer.toString(timeout)));
        scheduleTimeout(player.getUniqueId(), SessionMode.SELECTING, null, timeout, "creation.selection-timeout");
    }

    private void confirmCreation(Player player, PlayerSession session) {
        if (session.mode() != SessionMode.CONFIRM_CREATE || session.pendingLocation() == null
                || !player.hasPermission("velevators.create")) {
            messages.send(player, "errors.session-expired");
            return;
        }
        FloorLocation location = session.pendingLocation();
        session.main();
        player.closeInventory();
        ElevatorService.CreationResult result = elevators.createFloor(player, location);
        switch (result.status()) {
            case SUCCESS -> {
                messages.send(player, "creation.success", Map.of("price", elevators.formatPrice(result.paidAmount())));
                player.playSound(player.getLocation(), settings.get().successSound(), 1.0F, 1.0F);
            }
            case INVALID_STRUCTURE -> messages.send(player, "creation.invalid-structure");
            case ALREADY_REGISTERED -> messages.send(player, "creation.already-registered");
            case INSUFFICIENT_FUNDS -> messages.send(player, "creation.insufficient-funds",
                    Map.of("price", elevators.formatPrice(result.paidAmount())));
            case ECONOMY_FAILURE -> messages.send(player, "creation.economy-failure");
            case PERSISTENCE_FAILURE -> messages.send(player, "errors.database");
        }
        if (result.status() != ElevatorService.CreationStatus.SUCCESS) {
            player.playSound(player.getLocation(), settings.get().errorSound(), 1.0F, 0.8F);
        }
    }

    private void cancelCreation(Player player, PlayerSession session) {
        if (session.mode() != SessionMode.CONFIRM_CREATE) {
            messages.send(player, "errors.session-expired");
            return;
        }
        messages.send(player, "creation.cancelled");
        menus.openMain(player);
    }

    private void changePage(Player player, PlayerSession session, String[] args) {
        if (session.mode() != SessionMode.MANAGING || args.length != 2 || !canManage(player, session)) {
            messages.send(player, "errors.session-expired");
            return;
        }
        int delta;
        try {
            delta = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            return;
        }
        menus.openManagement(player, session.networkId(), session.currentFloorId(), session.page() + delta);
    }

    private void startRename(Player player, PlayerSession session, String[] args) {
        FloorView floor = resolveFloor(player, session, args);
        if (floor == null) {
            return;
        }
        int timeout = settings.get().renameTimeoutSeconds();
        session.renaming(floor.floor().id(), timeout);
        player.closeInventory();
        menus.sendFloorPrompt(player, settings.get().maximumNameLength());
        scheduleTimeout(player.getUniqueId(), SessionMode.RENAMING, floor.floor().id(), timeout, "rename.timeout");
    }

    private void startDelete(Player player, PlayerSession session, String[] args) {
        FloorView floor = resolveFloor(player, session, args);
        if (floor != null) {
            menus.openDeleteConfirmation(player, floor);
        }
    }

    private FloorView resolveFloor(Player player, PlayerSession session, String[] args) {
        if (session.mode() != SessionMode.MANAGING || args.length != 2 || !canManage(player, session)) {
            messages.send(player, "errors.session-expired");
            return null;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            messages.send(player, "errors.session-expired");
            return null;
        }
        FloorView floor = session.floorAtSlot(slot);
        if (floor == null) {
            messages.send(player, "errors.session-expired");
        }
        return floor;
    }

    private void confirmDelete(Player player, PlayerSession session) {
        if (session.mode() != SessionMode.CONFIRM_DELETE || session.selectedFloorId() == null
                || !canManage(player, session)) {
            messages.send(player, "errors.session-expired");
            return;
        }
        long floorId = session.selectedFloorId();
        long networkId = session.networkId();
        Long currentFloorId = session.currentFloorId();
        int page = session.page();
        try {
            if (!elevators.deleteFloor(floorId, player)) {
                messages.send(player, "errors.floor-not-found");
                menus.openMain(player);
                return;
            }
            messages.send(player, "deletion.success");
            player.playSound(player.getLocation(), settings.get().successSound(), 1.0F, 1.0F);
            if (elevators.database().getNetwork(networkId).isPresent()) {
                menus.openManagement(player, networkId, currentFloorId, page);
            } else {
                menus.openMain(player);
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo eliminar el piso: " + exception.getMessage());
            messages.send(player, "errors.database");
        }
    }

    private void cancelDelete(Player player, PlayerSession session) {
        if (session.mode() != SessionMode.CONFIRM_DELETE || session.networkId() == null) {
            messages.send(player, "errors.session-expired");
            return;
        }
        long network = session.networkId();
        Long current = session.currentFloorId();
        int page = session.page();
        messages.send(player, "deletion.cancelled");
        menus.openManagement(player, network, current, page);
    }

    private void startMemberSelection(Player player, PlayerSession session, boolean adding) {
        if (session.mode() != SessionMode.MANAGING || !canAdminister(player, session)) {
            messages.send(player, "errors.owner-only");
            return;
        }
        menus.openMemberSelection(player, adding, 0);
    }

    private void changeMemberPage(Player player, PlayerSession session, String[] args) {
        if (!isMemberSelection(session) || args.length != 2 || !canAdminister(player, session)) {
            messages.send(player, "errors.session-expired");
            return;
        }
        int delta;
        try {
            delta = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            messages.send(player, "errors.session-expired");
            return;
        }
        boolean adding = session.mode() == SessionMode.SELECTING_MEMBER_ADD;
        menus.openMemberSelection(player, adding, session.memberPage() + delta);
    }

    private void selectMember(Player player, PlayerSession session, String[] args) {
        if (!isMemberSelection(session) || args.length != 2 || !canAdminister(player, session)) {
            messages.send(player, "errors.session-expired");
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            messages.send(player, "errors.session-expired");
            return;
        }
        MemberCandidateView candidate = session.memberAtSlot(slot);
        if (candidate == null || session.networkId() == null) {
            messages.send(player, "errors.session-expired");
            return;
        }
        Player target = Bukkit.getPlayer(candidate.playerId());
        boolean adding = session.mode() == SessionMode.SELECTING_MEMBER_ADD;
        int memberPage = session.memberPage();
        if (adding && (target == null || !target.isOnline())) {
            messages.send(player, "members.player-went-offline", Map.of("player", candidate.playerName()));
            menus.openMemberSelection(player, adding, memberPage);
            return;
        }
        long networkId = session.networkId();
        Long currentFloorId = session.currentFloorId();
        int managementPage = session.page();
        try {
            ElevatorService.MemberResult result = adding
                    ? elevators.addMember(networkId, target.getUniqueId(), target.getName(),
                    player.getUniqueId(), player.getName())
                    : elevators.removeMember(networkId, candidate.playerId(), candidate.playerName(),
                    player.getUniqueId(), player.getName());
            switch (result.status()) {
                case SUCCESS -> {
                    messages.send(player, adding ? "members.added" : "members.removed",
                            Map.of("player", result.playerName()));
                    player.playSound(player.getLocation(), settings.get().successSound(), 0.8F,
                            adding ? 1.15F : 0.95F);
                    menus.openManagement(player, networkId, currentFloorId, managementPage);
                }
                case OWNER -> {
                    messages.send(player, "members.owner-not-member");
                    menus.openMemberSelection(player, adding, memberPage);
                }
                case ALREADY_MEMBER -> {
                    messages.send(player, "members.already-member", Map.of("player", result.playerName()));
                    menus.openMemberSelection(player, true, memberPage);
                }
                case NOT_MEMBER -> {
                    messages.send(player, "members.not-member", Map.of("player", result.playerName()));
                    menus.openMemberSelection(player, false, memberPage);
                }
                case NETWORK_NOT_FOUND -> {
                    messages.send(player, "errors.network-not-found");
                    menus.openMain(player);
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo modificar un miembro seleccionado: " + exception.getMessage());
            messages.send(player, "errors.database");
        }
    }

    private void startMemberChatInput(Player player, PlayerSession session) {
        if (!isMemberSelection(session) || !canAdminister(player, session)) {
            messages.send(player, "errors.session-expired");
            return;
        }
        boolean adding = session.mode() == SessionMode.SELECTING_MEMBER_ADD;
        int timeout = settings.get().memberTimeoutSeconds();
        if (adding) {
            session.addingMember(timeout);
        } else {
            session.removingMember(timeout);
        }
        player.closeInventory();
        menus.sendMemberPrompt(player, adding, timeout);
        scheduleTimeout(player.getUniqueId(), adding ? SessionMode.ADDING_MEMBER : SessionMode.REMOVING_MEMBER,
                null, timeout, "members.timeout");
    }

    private void returnFromMemberSelection(Player player, PlayerSession session) {
        if ((!isMemberSelection(session) && session.mode() != SessionMode.VIEWING_MEMBERS
                && session.mode() != SessionMode.SELECTING_TRANSFER) || session.networkId() == null) {
            messages.send(player, "errors.session-expired");
            return;
        }
        menus.openManagement(player, session.networkId(), session.currentFloorId(), session.page());
    }

    private boolean isMemberSelection(PlayerSession session) {
        return session.mode() == SessionMode.SELECTING_MEMBER_ADD
                || session.mode() == SessionMode.SELECTING_MEMBER_REMOVE;
    }

    private void toggleLock(Player player, PlayerSession session) {
        cycleNetworkAccess(player, session);
    }

    private void startNetworkRename(Player player, PlayerSession session) {
        if (session.mode() != SessionMode.MANAGING || !canAdminister(player, session)) {
            messages.send(player, "errors.owner-only");
            return;
        }
        int timeout = settings.get().renameTimeoutSeconds();
        session.renamingNetwork(timeout);
        player.closeInventory();
        messages.send(player, "network.rename-prompt",
                Map.of("limit", Integer.toString(settings.get().maximumNetworkNameLength())));
        scheduleTimeout(player.getUniqueId(), SessionMode.RENAMING_NETWORK, null, timeout, "network.rename-timeout");
    }

    private void openNetworkIcons(Player player, PlayerSession session) {
        if (session.mode() != SessionMode.MANAGING || !canAdminister(player, session)) {
            messages.send(player, "errors.owner-only");
            return;
        }
        menus.openNetworkIconPicker(player);
    }

    private void openFloorIcons(Player player, PlayerSession session, String[] args) {
        FloorView floor = resolveFloor(player, session, args);
        if (floor != null) {
            menus.openFloorIconPicker(player, floor.floor().id());
        }
    }

    private void setNetworkIcon(Player player, PlayerSession session, String[] args) {
        if (session.mode() != SessionMode.SELECTING_NETWORK_ICON || session.networkId() == null
                || args.length != 2 || !canAdminister(player, session)) {
            messages.send(player, "errors.session-expired");
            return;
        }
        Material material = IconCatalog.parseAllowed(args[1]);
        if (material == null) {
            messages.send(player, "icons.invalid");
            return;
        }
        try {
            if (!elevators.setNetworkIcon(session.networkId(), material, player)) {
                messages.send(player, "errors.network-not-found");
                return;
            }
            messages.send(player, "icons.network-updated", Map.of("icon", material.name()));
            menus.openManagement(player, session.networkId(), session.currentFloorId(), session.page());
        } catch (SQLException exception) {
            databaseError("No se pudo cambiar el icono de la red", player, exception);
        }
    }

    private void setFloorIcon(Player player, PlayerSession session, String[] args) {
        if (session.mode() != SessionMode.SELECTING_FLOOR_ICON || session.selectedFloorId() == null
                || args.length != 2 || !canManage(player, session)) {
            messages.send(player, "errors.session-expired");
            return;
        }
        Material material = IconCatalog.parseAllowed(args[1]);
        if (material == null) {
            messages.send(player, "icons.invalid");
            return;
        }
        try {
            if (!elevators.setFloorIcon(session.selectedFloorId(), material, player)) {
                messages.send(player, "errors.floor-not-found");
                return;
            }
            messages.send(player, "icons.floor-updated", Map.of("icon", material.name()));
            menus.openManagement(player, session.networkId(), session.currentFloorId(), session.page());
        } catch (SQLException exception) {
            databaseError("No se pudo cambiar el icono del piso", player, exception);
        }
    }

    private void cycleNetworkAccess(Player player, PlayerSession session) {
        if (session.mode() != SessionMode.MANAGING || session.networkId() == null
                || !canAdminister(player, session)) {
            messages.send(player, "errors.owner-only");
            return;
        }
        try {
            NetworkRecord network = elevators.database().getNetwork(session.networkId()).orElse(null);
            if (network == null) {
                messages.send(player, "errors.network-not-found");
                return;
            }
            AccessMode mode = network.accessMode().next();
            if (!elevators.setNetworkAccessMode(network.id(), mode, player)) {
                messages.send(player, "errors.network-not-found");
                return;
            }
            messages.send(player, "network.access-updated", Map.of("mode", accessModeName(mode)));
            player.playSound(player.getLocation(), settings.get().successSound(), 0.8F, 1.1F);
            menus.openManagement(player, network.id(), session.currentFloorId(), session.page());
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo cambiar el bloqueo de la red: " + exception.getMessage());
            messages.send(player, "errors.database");
        }
    }

    private void cycleFloorAccess(Player player, PlayerSession session, String[] args) {
        FloorView floor = resolveFloor(player, session, args);
        if (floor == null || !canAdminister(player, session)) {
            messages.send(player, "errors.owner-only");
            return;
        }
        AccessMode mode = floor.floor().accessMode().next();
        try {
            if (!elevators.setFloorAccessMode(floor.floor().id(), mode, player)) {
                messages.send(player, "errors.floor-not-found");
                return;
            }
            messages.send(player, "network.floor-access-updated",
                    Map.of("floor", floor.displayName(), "mode", accessModeName(mode)));
            menus.openManagement(player, session.networkId(), session.currentFloorId(), session.page());
        } catch (SQLException exception) {
            databaseError("No se pudo cambiar el acceso del piso", player, exception);
        }
    }

    private void openMemberDirectory(Player player, PlayerSession session, boolean transferring) {
        if (session.mode() != SessionMode.MANAGING || (transferring && !canAdminister(player, session))) {
            messages.send(player, transferring ? "errors.owner-only" : "errors.session-expired");
            return;
        }
        menus.openMemberDirectory(player, transferring, 0);
    }

    private void changeDirectoryPage(Player player, PlayerSession session, String[] args) {
        if ((session.mode() != SessionMode.VIEWING_MEMBERS && session.mode() != SessionMode.SELECTING_TRANSFER)
                || args.length != 2) {
            messages.send(player, "errors.session-expired");
            return;
        }
        Integer delta = integer(args[1]);
        if (delta == null) {
            return;
        }
        menus.openMemberDirectory(player, session.mode() == SessionMode.SELECTING_TRANSFER,
                session.memberPage() + delta);
    }

    private void selectTransferTarget(Player player, PlayerSession session, String[] args) {
        if (session.mode() != SessionMode.SELECTING_TRANSFER || args.length != 2 || !canAdminister(player, session)) {
            messages.send(player, "errors.session-expired");
            return;
        }
        Integer slot = integer(args[1]);
        MemberCandidateView candidate = slot == null ? null : session.memberAtSlot(slot);
        if (candidate == null || !candidate.member()) {
            messages.send(player, "errors.session-expired");
            return;
        }
        menus.openTransferConfirmation(player, candidate);
    }

    private void confirmTransfer(Player player, PlayerSession session) {
        if (session.mode() != SessionMode.CONFIRM_TRANSFER || session.networkId() == null
                || session.selectedMemberId() == null || !canAdminister(player, session)) {
            messages.send(player, "errors.session-expired");
            return;
        }
        try {
            ElevatorService.ChangeStatus status = elevators.transferOwnership(session.networkId(),
                    session.selectedMemberId(), session.selectedMemberName(), player);
            switch (status) {
                case SUCCESS -> {
                    messages.send(player, "transfer.success", Map.of("player", session.selectedMemberName()));
                    menus.openManagement(player, session.networkId(), session.currentFloorId(), session.page());
                }
                case CONFLICT -> messages.send(player, "transfer.conflict");
                case INVALID_TARGET -> messages.send(player, "transfer.invalid-target");
                case NOT_FOUND -> messages.send(player, "errors.network-not-found");
            }
        } catch (SQLException exception) {
            databaseError("No se pudo transferir la propiedad", player, exception);
        }
    }

    private void cancelTransfer(Player player, PlayerSession session) {
        if (session.mode() != SessionMode.CONFIRM_TRANSFER || session.networkId() == null) {
            messages.send(player, "errors.session-expired");
            return;
        }
        messages.send(player, "transfer.cancelled");
        menus.openManagement(player, session.networkId(), session.currentFloorId(), session.page());
    }

    private void startDeleteNetwork(Player player, PlayerSession session) {
        if (session.mode() != SessionMode.MANAGING || !canAdminister(player, session)) {
            messages.send(player, "errors.owner-only");
            return;
        }
        menus.openDeleteNetworkConfirmation(player);
    }

    private void confirmDeleteNetwork(Player player, PlayerSession session) {
        if (session.mode() != SessionMode.CONFIRM_DELETE_NETWORK || session.networkId() == null
                || !canAdminister(player, session)) {
            messages.send(player, "errors.session-expired");
            return;
        }
        try {
            if (!elevators.deleteNetwork(session.networkId(), player)) {
                messages.send(player, "errors.network-not-found");
                return;
            }
            messages.send(player, "network.delete-success");
            menus.openMain(player);
        } catch (SQLException exception) {
            databaseError("No se pudo eliminar la red", player, exception);
        }
    }

    private void cancelDeleteNetwork(Player player, PlayerSession session) {
        if (session.mode() != SessionMode.CONFIRM_DELETE_NETWORK || session.networkId() == null) {
            messages.send(player, "errors.session-expired");
            return;
        }
        messages.send(player, "network.delete-cancelled");
        menus.openManagement(player, session.networkId(), session.currentFloorId(), session.page());
    }

    private void changeAdminPlayerPage(Player player, PlayerSession session, String[] args) {
        if (session.mode() != SessionMode.ADMIN_SELECTING_PLAYER || args.length != 2 || !isAdmin(player)) {
            messages.send(player, "errors.session-expired");
            return;
        }
        Integer delta = integer(args[1]);
        if (delta != null) {
            menus.openAdminMain(player, session.memberPage() + delta);
        }
    }

    private void selectAdminPlayer(Player player, PlayerSession session, String[] args) {
        if (session.mode() != SessionMode.ADMIN_SELECTING_PLAYER || args.length != 2 || !isAdmin(player)) {
            messages.send(player, "errors.session-expired");
            return;
        }
        Integer slot = integer(args[1]);
        MemberCandidateView candidate = slot == null ? null : session.memberAtSlot(slot);
        if (candidate == null) {
            messages.send(player, "errors.session-expired");
            return;
        }
        try {
            menus.openAdminNetworks(player, elevators.database().findNetworksByOwner(candidate.playerId()), 0);
        } catch (SQLException exception) {
            databaseError("No se pudieron buscar las redes del propietario", player, exception);
        }
    }

    private void startAdminSearch(Player player, String[] args) {
        if (!isAdmin(player) || args.length != 2) {
            messages.send(player, "errors.no-permission");
            return;
        }
        SessionMode mode = switch (args[1].toLowerCase()) {
            case "owner" -> SessionMode.ADMIN_SEARCH_OWNER;
            case "world" -> SessionMode.ADMIN_SEARCH_WORLD;
            case "coords" -> SessionMode.ADMIN_SEARCH_COORDINATES;
            default -> null;
        };
        if (mode == null) {
            messages.send(player, "errors.session-expired");
            return;
        }
        int timeout = settings.get().memberTimeoutSeconds();
        sessions.getOrCreate(player.getUniqueId()).adminInput(mode, timeout);
        player.closeInventory();
        String key = switch (mode) {
            case ADMIN_SEARCH_OWNER -> "admin-search.owner-prompt";
            case ADMIN_SEARCH_WORLD -> "admin-search.world-prompt";
            case ADMIN_SEARCH_COORDINATES -> "admin-search.coords-prompt";
            default -> throw new IllegalStateException();
        };
        messages.send(player, key, Map.of("seconds", Integer.toString(timeout)));
        scheduleTimeout(player.getUniqueId(), mode, null, timeout, "admin-search.timeout");
    }

    private void changeAdminNetworkPage(Player player, PlayerSession session, String[] args) {
        if (session.mode() != SessionMode.ADMIN_NETWORKS || args.length != 2 || !isAdmin(player)) {
            messages.send(player, "errors.session-expired");
            return;
        }
        Integer delta = integer(args[1]);
        if (delta != null) {
            menus.openAdminNetworks(player,
                    session.networkCandidates().stream().map(NetworkSummaryView::network).toList(),
                    session.adminPage() + delta);
        }
    }

    private void adminManageNetwork(Player player, PlayerSession session, String[] args) {
        NetworkSummaryView summary = adminNetworkAt(player, session, args);
        if (summary != null) {
            menus.openManagement(player, summary.network().id(), null, 0);
        }
    }

    private void adminTeleportNetwork(Player player, PlayerSession session, String[] args) {
        NetworkSummaryView summary = adminNetworkAt(player, session, args);
        if (summary == null) {
            return;
        }
        ElevatorService.TravelStatus status = elevators.teleportToNetworkAnchor(player, summary.network().id());
        if (status == ElevatorService.TravelStatus.SUCCESS) {
            messages.send(player, "admin-search.teleported", Map.of("network", summary.network().effectiveName()));
        } else {
            messages.send(player, status == ElevatorService.TravelStatus.UNSAFE_DESTINATION
                    ? "travel.unsafe" : "admin-search.teleport-failed");
        }
    }

    private NetworkSummaryView adminNetworkAt(Player player, PlayerSession session, String[] args) {
        if (session.mode() != SessionMode.ADMIN_NETWORKS || args.length != 2 || !isAdmin(player)) {
            messages.send(player, "errors.session-expired");
            return null;
        }
        Integer slot = integer(args[1]);
        NetworkSummaryView summary = slot == null ? null : session.networkAtSlot(slot);
        if (summary == null) {
            messages.send(player, "errors.session-expired");
        }
        return summary;
    }

    private void openAdminHistory(Player player, int page) {
        if (!isAdmin(player)) {
            messages.send(player, "errors.no-permission");
            return;
        }
        menus.openAdminHistory(player, page);
    }

    private void changeAdminHistoryPage(Player player, PlayerSession session, String[] args) {
        if (session.mode() != SessionMode.ADMIN_HISTORY || args.length != 2 || !isAdmin(player)) {
            messages.send(player, "errors.session-expired");
            return;
        }
        Integer delta = integer(args[1]);
        if (delta != null) {
            menus.openAdminHistory(player, session.adminPage() + delta);
        }
    }

    private void openAdminMain(Player player, int page) {
        if (isAdmin(player)) {
            menus.openAdminMain(player, page);
        } else {
            messages.send(player, "errors.no-permission");
        }
    }

    private void returnToManagement(Player player, PlayerSession session) {
        if (session.networkId() == null) {
            messages.send(player, "errors.session-expired");
            return;
        }
        menus.openManagement(player, session.networkId(), session.currentFloorId(), session.page());
    }

    private boolean isAdmin(Player player) {
        return player.hasPermission("velevators.adminlog");
    }

    private Integer integer(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void databaseError(String context, Player player, SQLException exception) {
        plugin.getLogger().severe(context + ": " + exception.getMessage());
        messages.send(player, "errors.database");
    }

    private void scheduleTimeout(UUID playerId, SessionMode expectedMode, Long floorId,
                                 int seconds, String messageKey) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PlayerSession current = sessions.peek(playerId).orElse(null);
            if (current == null || current.mode() != expectedMode) {
                return;
            }
            if (floorId != null && !floorId.equals(current.selectedFloorId())) {
                return;
            }
            sessions.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                messages.send(player, messageKey);
            }
        }, seconds * 20L);
    }

    private boolean canManage(Player player, PlayerSession session) {
        if (session.networkId() == null) {
            return false;
        }
        try {
            NetworkRecord network = elevators.database().getNetwork(session.networkId()).orElse(null);
            boolean member = network != null
                    && elevators.database().isMember(session.networkId(), player.getUniqueId());
            return network != null && ManagementAccess.canManage(player.getUniqueId(), network.ownerId(), member,
                    player.hasPermission("velevators.manage"),
                    player.hasPermission("velevators.manage.others")
                            || player.hasPermission("velevators.adminlog"));
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo validar el acceso a la red: " + exception.getMessage());
            return false;
        }
    }

    private boolean canAdminister(Player player, PlayerSession session) {
        if (session.networkId() == null) {
            return false;
        }
        try {
            NetworkRecord network = elevators.database().getNetwork(session.networkId()).orElse(null);
            return network != null && ManagementAccess.canAdminister(player.getUniqueId(), network.ownerId(),
                    player.hasPermission("velevators.manage"),
                    player.hasPermission("velevators.manage.others")
                            || player.hasPermission("velevators.adminlog"));
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo validar la administración de la red: " + exception.getMessage());
            return false;
        }
    }

    private String accessModeName(AccessMode mode) {
        return messages.text("access-modes." + mode.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
