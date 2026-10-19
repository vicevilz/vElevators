package net.velevators.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.velevators.config.PluginSettings;
import net.velevators.model.FloorView;
import net.velevators.model.MemberCandidateView;
import net.velevators.model.NetworkMemberRecord;
import net.velevators.model.NetworkRecord;
import net.velevators.model.NetworkSummaryView;
import net.velevators.model.AuditRecord;
import net.velevators.service.ElevatorService;
import net.velevators.session.PlayerSession;
import net.velevators.session.SessionManager;
import net.velevators.util.ManagementAccess;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ElevatorExpansion extends PlaceholderExpansion {
    private static final Pattern FLOOR_PLACEHOLDER = Pattern.compile(
            "floor_(\\d+)_(exists|name|y|number|current|world|icon|access_mode)");
    private static final Pattern MEMBER_PLAYER_PLACEHOLDER = Pattern.compile(
            "member_player_(\\d+)_(exists|name|texture|relation|relation_colored|member|owner|self|online|connection)");
    private static final Pattern ADMIN_NETWORK_PLACEHOLDER = Pattern.compile(
            "admin_network_(\\d+)_(exists|name|icon|owner|members|floors|world|x|y|z|access_mode|id)");
    private static final Pattern AUDIT_PLACEHOLDER = Pattern.compile(
            "audit_(\\d+)_(exists|action|actor|details|date|network)");
    private static final DateTimeFormatter AUDIT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Europe/Madrid"));
    private final ElevatorService elevators;
    private final SessionManager sessions;
    private final Supplier<PluginSettings> settings;

    public ElevatorExpansion(ElevatorService elevators, SessionManager sessions, Supplier<PluginSettings> settings) {
        this.elevators = elevators;
        this.sessions = sessions;
        this.settings = settings;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "velevators";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Vicevil";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String rawIdentifier) {
        String identifier = rawIdentifier.toLowerCase(Locale.ROOT);
        if (identifier.equals("price")) {
            return elevators.formatPrice();
        }
        if (identifier.equals("elevator_block")) {
            return settings.get().elevatorMaterial().name();
        }
        if (player == null) {
            return "";
        }
        if (identifier.equals("player_networks")) {
            try {
                return Integer.toString(elevators.database().countNetworks(player.getUniqueId()));
            } catch (SQLException exception) {
                return "0";
            }
        }

        PlayerSession session = sessions.get(player.getUniqueId()).orElse(null);
        if (session == null) {
            return identifier.endsWith("_exists") ? "no" : "";
        }
        if (identifier.equals("network_owner") || identifier.equals("network_members")
                || identifier.equals("network_member_count") || identifier.equals("network_locked")
                || identifier.equals("network_lock_status") || identifier.equals("can_administer_network")
                || identifier.equals("network_name") || identifier.equals("network_icon")
                || identifier.equals("network_access_mode") || identifier.equals("network_access_texture")) {
            return networkPlaceholder(player, session, identifier);
        }
        return switch (identifier) {
            case "page" -> Integer.toString(session.page() + 1);
            case "pages" -> Integer.toString(session.pageCount());
            case "member_page" -> Integer.toString(session.memberPage() + 1);
            case "member_pages" -> Integer.toString(session.memberPageCount());
            case "member_has_previous_page" -> session.memberPage() > 0 ? "yes" : "no";
            case "member_has_next_page" -> session.memberPage() + 1 < session.memberPageCount() ? "yes" : "no";
            case "online_player_count" -> Integer.toString(session.memberCandidates().size());
            case "selected_member_name" -> session.selectedMemberName() == null ? "" : session.selectedMemberName();
            case "selected_member_texture" -> session.selectedMemberTexture() == null ? "" : session.selectedMemberTexture();
            case "admin_page" -> Integer.toString(session.adminPage() + 1);
            case "admin_pages" -> Integer.toString(session.adminPageCount());
            case "admin_has_previous_page" -> session.adminPage() > 0 ? "yes" : "no";
            case "admin_has_next_page" -> session.adminPage() + 1 < session.adminPageCount() ? "yes" : "no";
            case "network_floors" -> Integer.toString(session.floors().size());
            case "has_previous_page" -> session.page() > 0 ? "yes" : "no";
            case "has_next_page" -> session.page() + 1 < session.pageCount() ? "yes" : "no";
            case "selected_floor_name" -> selectedFloor(session) == null ? "" : selectedFloor(session).displayName();
            case "pending_world" -> session.pendingLocation() == null ? "" : session.pendingLocation().worldName();
            case "pending_x" -> session.pendingLocation() == null ? "" : Integer.toString(session.pendingLocation().x());
            case "pending_y" -> session.pendingLocation() == null ? "" : Integer.toString(session.pendingLocation().y());
            case "pending_z" -> session.pendingLocation() == null ? "" : Integer.toString(session.pendingLocation().z());
            default -> {
                String member = memberPlayerPlaceholder(session, identifier);
                if (member != null) {
                    yield member;
                }
                String floor = floorPlaceholder(session, identifier);
                if (floor != null) {
                    yield floor;
                }
                String network = adminNetworkPlaceholder(session, identifier);
                yield network == null ? auditPlaceholder(session, identifier) : network;
            }
        };
    }

    private String memberPlayerPlaceholder(PlayerSession session, String identifier) {
        Matcher matcher = MEMBER_PLAYER_PLACEHOLDER.matcher(identifier);
        if (!matcher.matches()) {
            return null;
        }
        int slot;
        try {
            slot = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return "";
        }
        MemberCandidateView candidate = session.memberAtSlot(slot);
        String field = matcher.group(2);
        if (field.equals("exists")) {
            return candidate == null ? "no" : "yes";
        }
        if (candidate == null) {
            return "";
        }
        return switch (field) {
            case "name" -> candidate.playerName();
            case "texture" -> candidate.textureId();
            case "relation" -> candidate.relation();
            case "relation_colored" -> candidate.owner()
                    ? "&#C93A3FPropietario" : "&#68D878Miembro";
            case "member" -> candidate.member() ? "yes" : "no";
            case "owner" -> candidate.owner() ? "yes" : "no";
            case "self" -> candidate.self() ? "yes" : "no";
            case "online" -> candidate.online() ? "yes" : "no";
            case "connection" -> candidate.connectionStatus();
            default -> "";
        };
    }

    private String networkPlaceholder(OfflinePlayer requester, PlayerSession session, String identifier) {
        if (session.networkId() == null) {
            return identifier.equals("network_locked") || identifier.equals("can_administer_network") ? "no" : "";
        }
        try {
            NetworkRecord network = elevators.database().getNetwork(session.networkId()).orElse(null);
            if (network == null) {
                return identifier.equals("network_locked") || identifier.equals("can_administer_network") ? "no" : "";
            }
            return switch (identifier) {
                case "network_owner" -> network.ownerName();
                case "network_members" -> {
                    String members = elevators.database().getMembers(network.id()).stream()
                            .map(NetworkMemberRecord::memberName)
                            .collect(Collectors.joining(", "));
                    yield members.isEmpty() ? "Ninguno" : members;
                }
                case "network_member_count" -> Integer.toString(elevators.database().getMembers(network.id()).size());
                case "network_locked" -> network.locked() ? "yes" : "no";
                case "network_lock_status", "network_access_mode" -> network.accessMode().displayName();
                case "network_access_texture" -> switch (network.accessMode()) {
                    case PUBLIC -> "948bb4e4435c2c21d7f6183b38a2b7372653f3540be02259dd47bf52492de669";
                    case PRIVATE -> "fbf46bf39dfc718e7ea31db43477fc6b7dca588fe0a7891d481c5dda9e16f520";
                    case OWNER_ONLY -> "afd2400002ad9fbbbd0066941eb5b1a384ab9b0e48a178ee96e4d129a5208654";
                };
                case "network_name" -> network.effectiveName();
                case "network_icon" -> network.iconMaterial();
                case "can_administer_network" -> {
                    Player online = requester.getPlayer();
                    yield online != null && ManagementAccess.canAdminister(requester.getUniqueId(), network.ownerId(),
                            online.hasPermission("velevators.manage"),
                            online.hasPermission("velevators.manage.others")
                                    || online.hasPermission("velevators.adminlog")) ? "yes" : "no";
                }
                default -> "";
            };
        } catch (SQLException exception) {
            return identifier.equals("network_locked") || identifier.equals("can_administer_network") ? "no" : "";
        }
    }

    private String floorPlaceholder(PlayerSession session, String identifier) {
        Matcher matcher = FLOOR_PLACEHOLDER.matcher(identifier);
        if (!matcher.matches()) {
            return null;
        }
        int slot;
        try {
            slot = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return "";
        }
        FloorView view = session.floorAtSlot(slot);
        String field = matcher.group(2);
        if (field.equals("exists")) {
            return view == null ? "no" : "yes";
        }
        if (view == null) {
            return "";
        }
        return switch (field) {
            case "name" -> view.displayName();
            case "y" -> Integer.toString(view.floor().y());
            case "number" -> Integer.toString(view.number());
            case "current" -> view.current() ? "yes" : "no";
            case "world" -> view.floor().worldName();
            case "icon" -> view.floor().iconMaterial();
            case "access_mode" -> view.floor().accessMode().displayName();
            default -> "";
        };
    }

    private String adminNetworkPlaceholder(PlayerSession session, String identifier) {
        Matcher matcher = ADMIN_NETWORK_PLACEHOLDER.matcher(identifier);
        if (!matcher.matches()) {
            return null;
        }
        int slot;
        try {
            slot = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return "";
        }
        NetworkSummaryView summary = session.networkAtSlot(slot);
        String field = matcher.group(2);
        if (field.equals("exists")) {
            return summary == null ? "no" : "yes";
        }
        if (summary == null) {
            return "";
        }
        NetworkRecord network = summary.network();
        return switch (field) {
            case "name" -> network.effectiveName();
            case "icon" -> network.iconMaterial();
            case "owner" -> network.ownerName();
            case "members" -> Integer.toString(summary.memberCount());
            case "floors" -> Integer.toString(summary.floorCount());
            case "world" -> network.worldName();
            case "x" -> Integer.toString(network.x());
            case "y" -> summary.anchorFloor() == null ? "?" : Integer.toString(summary.anchorFloor().y());
            case "z" -> Integer.toString(network.z());
            case "access_mode" -> network.accessMode().displayName();
            case "id" -> Long.toString(network.id());
            default -> "";
        };
    }

    private String auditPlaceholder(PlayerSession session, String identifier) {
        Matcher matcher = AUDIT_PLACEHOLDER.matcher(identifier);
        if (!matcher.matches()) {
            return null;
        }
        int slot;
        try {
            slot = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return "";
        }
        AuditRecord record = session.auditAtSlot(slot);
        String field = matcher.group(2);
        if (field.equals("exists")) {
            return record == null ? "no" : "yes";
        }
        if (record == null) {
            return "";
        }
        return switch (field) {
            case "action" -> auditActionName(record.action());
            case "actor" -> record.actorName();
            case "details" -> record.details();
            case "date" -> AUDIT_DATE.format(Instant.parse(record.createdAt()));
            case "network" -> record.networkId() == null ? "-" : Long.toString(record.networkId());
            default -> "";
        };
    }

    private String auditActionName(String action) {
        return switch (action) {
            case "NETWORK_CREATED" -> "Red creada";
            case "FLOOR_CREATED" -> "Piso creado";
            case "FLOOR_RENAMED" -> "Piso renombrado";
            case "NETWORK_RENAMED" -> "Red renombrada";
            case "NETWORK_ICON_CHANGED" -> "Icono de red cambiado";
            case "FLOOR_ICON_CHANGED" -> "Icono de piso cambiado";
            case "NETWORK_ACCESS_CHANGED" -> "Acceso de red cambiado";
            case "FLOOR_ACCESS_CHANGED" -> "Acceso de piso cambiado";
            case "MEMBER_ADDED" -> "Miembro añadido";
            case "MEMBER_REMOVED" -> "Miembro eliminado";
            case "OWNER_TRANSFERRED" -> "Propiedad transferida";
            case "FLOOR_DELETED" -> "Piso eliminado";
            case "NETWORK_DELETED" -> "Red eliminada";
            case "TRANSACTION_PURCHASE" -> "Pago de piso archivado";
            case "TRANSACTIONS_COLLECTED" -> "Dinero retirado";
            case "TRANSACTIONS_CLEARED" -> "Pagos archivados";
            default -> action;
        };
    }

    private FloorView selectedFloor(PlayerSession session) {
        if (session.selectedFloorId() == null) {
            return null;
        }
        return session.floors().stream()
                .filter(view -> view.floor().id() == session.selectedFloorId())
                .findFirst().orElse(null);
    }
}
