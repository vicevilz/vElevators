package net.velevators.menu;

import net.velevators.config.MessageService;
import net.velevators.data.ElevatorDatabase;
import net.velevators.model.FloorLocation;
import net.velevators.model.FloorRecord;
import net.velevators.model.FloorView;
import net.velevators.model.MemberCandidateView;
import net.velevators.model.NetworkMemberRecord;
import net.velevators.model.NetworkRecord;
import net.velevators.model.NetworkSummaryView;
import net.velevators.model.AuditRecord;
import net.velevators.service.MemberTextureResolver;
import net.velevators.service.TransactionLogger;
import net.velevators.session.PlayerSession;
import net.velevators.session.SessionManager;
import net.velevators.session.SessionMode;
import net.velevators.util.FloorNames;
import net.velevators.util.ManagementAccess;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class MenuService {
    public static final String MAIN_MENU = "velevators_main";
    public static final String MANAGE_MENU = "velevators_manage";
    public static final String CONFIRM_CREATE_MENU = "velevators_confirm_create";
    public static final String CONFIRM_DELETE_MENU = "velevators_confirm_delete";
    public static final String ADD_MEMBER_MENU = "velevators_add_member";
    public static final String REMOVE_MEMBER_MENU = "velevators_remove_member";
    public static final String NETWORK_MEMBERS_MENU = "velevators_network_members";
    public static final String TRANSFER_OWNER_MENU = "velevators_transfer_owner";
    public static final String CONFIRM_TRANSFER_MENU = "velevators_confirm_transfer";
    public static final String CONFIRM_DELETE_NETWORK_MENU = "velevators_confirm_delete_network";
    public static final String NETWORK_ICON_MENU = "velevators_network_icon";
    public static final String FLOOR_ICON_MENU = "velevators_floor_icon";
    public static final String ADMIN_MAIN_MENU = "velevators_admin_main";
    public static final String ADMIN_NETWORKS_MENU = "velevators_admin_networks";
    public static final String ADMIN_HISTORY_MENU = "velevators_admin_history";

    private final JavaPlugin plugin;
    private final ElevatorDatabase database;
    private final SessionManager sessions;
    private final MessageService messages;
    private final MemberTextureResolver textureResolver;
    private final TransactionLogger transactions;

    public MenuService(JavaPlugin plugin, ElevatorDatabase database, SessionManager sessions, MessageService messages,
                       MemberTextureResolver textureResolver, TransactionLogger transactions) {
        this.plugin = plugin;
        this.database = database;
        this.sessions = sessions;
        this.messages = messages;
        this.textureResolver = textureResolver;
        this.transactions = transactions;
    }

    public void openMain(Player player) {
        sessions.getOrCreate(player.getUniqueId()).main();
        open(player, MAIN_MENU);
    }

    public void openConfirmCreate(Player player, FloorLocation location, int timeoutSeconds) {
        sessions.getOrCreate(player.getUniqueId()).confirmCreate(location, timeoutSeconds);
        open(player, CONFIRM_CREATE_MENU);
    }

    public boolean openManagement(Player player, long networkId, Long currentFloorId, int page) {
        try {
            NetworkRecord network = database.getNetwork(networkId).orElse(null);
            if (network == null) {
                messages.send(player, "errors.network-not-found");
                return false;
            }
            boolean member = database.isMember(networkId, player.getUniqueId());
            if (!ManagementAccess.canManage(player.getUniqueId(), network.ownerId(), member,
                    player.hasPermission("velevators.manage"),
                    player.hasPermission("velevators.manage.others")
                            || player.hasPermission("velevators.adminlog"))) {
                messages.send(player, "errors.not-owner");
                return false;
            }
            List<FloorRecord> floors = database.getFloors(networkId);
            List<FloorView> views = FloorNames.buildViews(network, floors, currentFloorId);
            sessions.getOrCreate(player.getUniqueId()).managing(networkId, currentFloorId, views, page);
            open(player, MANAGE_MENU);
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo abrir el menú de gestión: " + exception.getMessage());
            messages.send(player, "errors.database");
            return false;
        }
    }

    public boolean refreshManagement(Player player) {
        PlayerSession session = sessions.get(player.getUniqueId()).orElse(null);
        if (session == null || session.networkId() == null) {
            messages.send(player, "errors.session-expired");
            return false;
        }
        return openManagement(player, session.networkId(), session.currentFloorId(), session.page());
    }

    public boolean openMemberSelection(Player player, boolean adding, int requestedPage) {
        PlayerSession session = sessions.get(player.getUniqueId()).orElse(null);
        if (session == null || session.networkId() == null) {
            messages.send(player, "errors.session-expired");
            return false;
        }
        try {
            NetworkRecord network = database.getNetwork(session.networkId()).orElse(null);
            if (network == null) {
                messages.send(player, "errors.network-not-found");
                return false;
            }
            if (!ManagementAccess.canAdminister(player.getUniqueId(), network.ownerId(),
                    player.hasPermission("velevators.manage"),
                    player.hasPermission("velevators.manage.others")
                            || player.hasPermission("velevators.adminlog"))) {
                messages.send(player, "errors.owner-only");
                return false;
            }
            List<NetworkMemberRecord> members = database.getMembers(network.id());
            Set<UUID> memberIds = new HashSet<>(members.stream()
                    .map(NetworkMemberRecord::memberId)
                    .toList());
            List<MemberCandidateView> candidates = adding
                    ? Bukkit.getOnlinePlayers().stream()
                    .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                    .map(online -> new MemberCandidateView(
                            online.getUniqueId(),
                            online.getName(),
                            network.ownerId().equals(online.getUniqueId()),
                            memberIds.contains(online.getUniqueId()),
                            player.getUniqueId().equals(online.getUniqueId()),
                            true))
                    .toList()
                    : members.stream()
                    .map(member -> new MemberCandidateView(
                            member.memberId(),
                            member.memberName(),
                            false,
                            true,
                            player.getUniqueId().equals(member.memberId()),
                            isOnline(member.memberId())))
                    .toList();
            session.selectingMembers(adding, candidates, requestedPage);
            if (adding) {
                open(player, ADD_MEMBER_MENU);
                return true;
            }

            int selectedPage = session.memberPage();
            int fromIndex = selectedPage * PlayerSession.MEMBERS_PER_PAGE;
            int toIndex = Math.min(fromIndex + PlayerSession.MEMBERS_PER_PAGE, candidates.size());
            if (fromIndex >= toIndex) {
                open(player, REMOVE_MEMBER_MENU);
                return true;
            }
            List<MemberCandidateView> visibleCandidates = List.copyOf(candidates.subList(fromIndex, toIndex));
            textureResolver.resolveCandidates(visibleCandidates)
                    .completeOnTimeout(visibleCandidates, 3, TimeUnit.SECONDS)
                    .exceptionally(throwable -> visibleCandidates)
                    .thenAccept(resolved -> Bukkit.getScheduler().runTask(plugin, () -> {
                        PlayerSession activeSession = sessions.get(player.getUniqueId()).orElse(null);
                        if (activeSession != session || !player.isOnline()
                                || activeSession.mode() != SessionMode.SELECTING_MEMBER_REMOVE
                                || activeSession.memberPage() != selectedPage) {
                            return;
                        }
                        activeSession.updateMemberPageTextures(selectedPage, resolved);
                        open(player, REMOVE_MEMBER_MENU);
                    }));
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo preparar el selector de miembros: " + exception.getMessage());
            messages.send(player, "errors.database");
            return false;
        }
    }

    public boolean openMemberDirectory(Player player, boolean transferring, int requestedPage) {
        PlayerSession session = sessions.get(player.getUniqueId()).orElse(null);
        if (session == null || session.networkId() == null) {
            messages.send(player, "errors.session-expired");
            return false;
        }
        try {
            NetworkRecord network = database.getNetwork(session.networkId()).orElse(null);
            if (network == null) {
                messages.send(player, "errors.network-not-found");
                return false;
            }
            boolean member = database.isMember(network.id(), player.getUniqueId());
            if (!ManagementAccess.canManage(player.getUniqueId(), network.ownerId(), member,
                    player.hasPermission("velevators.manage"),
                    player.hasPermission("velevators.manage.others")
                            || player.hasPermission("velevators.adminlog"))) {
                messages.send(player, "errors.not-owner");
                return false;
            }
            if (transferring && !ManagementAccess.canAdminister(player.getUniqueId(), network.ownerId(),
                    player.hasPermission("velevators.manage"),
                    player.hasPermission("velevators.manage.others")
                            || player.hasPermission("velevators.adminlog"))) {
                messages.send(player, "errors.owner-only");
                return false;
            }
            List<MemberCandidateView> candidates = new java.util.ArrayList<>();
            if (!transferring) {
                candidates.add(new MemberCandidateView(network.ownerId(), network.ownerName(), true,
                        false, player.getUniqueId().equals(network.ownerId()), isOnline(network.ownerId())));
            }
            for (NetworkMemberRecord listed : database.getMembers(network.id())) {
                candidates.add(new MemberCandidateView(listed.memberId(), listed.memberName(), false,
                        true, player.getUniqueId().equals(listed.memberId()), isOnline(listed.memberId())));
            }
            SessionMode mode = transferring ? SessionMode.SELECTING_TRANSFER : SessionMode.VIEWING_MEMBERS;
            session.memberDirectory(mode, candidates, requestedPage);
            resolveDirectoryAndOpen(player, session, mode, requestedPage,
                    transferring ? TRANSFER_OWNER_MENU : NETWORK_MEMBERS_MENU);
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo abrir el directorio de la red: " + exception.getMessage());
            messages.send(player, "errors.database");
            return false;
        }
    }

    public void openNetworkIconPicker(Player player) {
        PlayerSession session = sessions.get(player.getUniqueId()).orElse(null);
        if (session == null || session.networkId() == null) {
            messages.send(player, "errors.session-expired");
            return;
        }
        session.selectingNetworkIcon();
        open(player, NETWORK_ICON_MENU);
    }

    public void openFloorIconPicker(Player player, long floorId) {
        PlayerSession session = sessions.get(player.getUniqueId()).orElse(null);
        if (session == null || session.networkId() == null) {
            messages.send(player, "errors.session-expired");
            return;
        }
        session.selectingFloorIcon(floorId);
        open(player, FLOOR_ICON_MENU);
    }

    public void openTransferConfirmation(Player player, MemberCandidateView candidate) {
        PlayerSession session = sessions.get(player.getUniqueId()).orElse(null);
        if (session == null || session.networkId() == null) {
            messages.send(player, "errors.session-expired");
            return;
        }
        session.confirmTransfer(candidate);
        open(player, CONFIRM_TRANSFER_MENU);
    }

    public void openDeleteNetworkConfirmation(Player player) {
        PlayerSession session = sessions.get(player.getUniqueId()).orElse(null);
        if (session == null || session.networkId() == null) {
            messages.send(player, "errors.session-expired");
            return;
        }
        session.confirmDeleteNetwork();
        open(player, CONFIRM_DELETE_NETWORK_MENU);
    }

    public void openAdminMain(Player player, int requestedPage) {
        List<MemberCandidateView> players = Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .map(online -> new MemberCandidateView(online.getUniqueId(), online.getName(), false,
                        false, online.getUniqueId().equals(player.getUniqueId()), true))
                .toList();
        PlayerSession session = sessions.getOrCreate(player.getUniqueId());
        session.adminPlayers(players, requestedPage);
        resolveDirectoryAndOpen(player, session, SessionMode.ADMIN_SELECTING_PLAYER,
                session.memberPage(), ADMIN_MAIN_MENU);
    }

    public boolean openAdminNetworks(Player player, List<NetworkRecord> networks, int requestedPage) {
        try {
            List<NetworkSummaryView> summaries = database.summarizeNetworks(networks);
            sessions.getOrCreate(player.getUniqueId()).adminNetworks(summaries, requestedPage);
            open(player, ADMIN_NETWORKS_MENU);
            return true;
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudieron cargar las redes administrativas: " + exception.getMessage());
            messages.send(player, "errors.database");
            return false;
        }
    }

    public boolean openAdminHistory(Player player, int requestedPage) {
        try {
            List<AuditRecord> records = new ArrayList<>(database.getAuditRecords());
            records.addAll(transactions.permanentAuditRecords());
            records.sort(Comparator.comparing((AuditRecord record) -> Instant.parse(record.createdAt())).reversed());
            sessions.getOrCreate(player.getUniqueId()).adminHistory(records, requestedPage);
            open(player, ADMIN_HISTORY_MENU);
            return true;
        } catch (SQLException | IOException exception) {
            plugin.getLogger().severe("No se pudo cargar el historial administrativo: " + exception.getMessage());
            messages.send(player, "errors.database");
            return false;
        }
    }

    private void resolveDirectoryAndOpen(Player player, PlayerSession session, SessionMode expectedMode,
                                         int requestedPage, String menuId) {
        int selectedPage = session.memberPage();
        List<MemberCandidateView> candidates = session.memberCandidates();
        int fromIndex = selectedPage * PlayerSession.MEMBERS_PER_PAGE;
        int toIndex = Math.min(fromIndex + PlayerSession.MEMBERS_PER_PAGE, candidates.size());
        if (fromIndex >= toIndex) {
            open(player, menuId);
            return;
        }
        List<MemberCandidateView> visible = List.copyOf(candidates.subList(fromIndex, toIndex));
        textureResolver.resolveCandidates(visible)
                .completeOnTimeout(visible, 3, TimeUnit.SECONDS)
                .exceptionally(throwable -> visible)
                .thenAccept(resolved -> Bukkit.getScheduler().runTask(plugin, () -> {
                    PlayerSession active = sessions.get(player.getUniqueId()).orElse(null);
                    if (active != session || !player.isOnline() || active.mode() != expectedMode
                            || active.memberPage() != selectedPage) {
                        return;
                    }
                    active.updateMemberPageTextures(selectedPage, resolved);
                    open(player, menuId);
                }));
    }

    private boolean isOnline(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player != null && player.isOnline();
    }

    public void openDeleteConfirmation(Player player, FloorView floor) {
        PlayerSession session = sessions.get(player.getUniqueId()).orElse(null);
        if (session == null || session.networkId() == null) {
            messages.send(player, "errors.session-expired");
            return;
        }
        session.confirmDelete(floor.floor().id());
        open(player, CONFIRM_DELETE_MENU);
    }

    private void open(Player player, String menuId) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "dm open " + menuId + " " + player.getName());
            if (!dispatched) {
                messages.send(player, "errors.menu-open");
                plugin.getLogger().severe("DeluxeMenus rechazó la apertura de " + menuId + " para " + player.getName());
            }
        });
    }

    public void sendFloorPrompt(Player player, int maximumLength) {
        messages.send(player, "rename.prompt", Map.of("limit", Integer.toString(maximumLength)));
    }

    public void sendMemberPrompt(Player player, boolean adding, int timeoutSeconds) {
        messages.send(player, adding ? "members.add-prompt" : "members.remove-prompt",
                Map.of("seconds", Integer.toString(timeoutSeconds)));
    }
}
