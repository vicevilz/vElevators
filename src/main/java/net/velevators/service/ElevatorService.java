package net.velevators.service;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.velevators.config.PluginSettings;
import net.velevators.data.ElevatorDatabase;
import net.velevators.model.FloorLocation;
import net.velevators.model.FloorRecord;
import net.velevators.model.AccessMode;
import net.velevators.model.FloorView;
import net.velevators.model.NetworkRecord;
import net.velevators.util.FloorNames;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ElevatorService {
    public enum CreationStatus {
        SUCCESS,
        INVALID_STRUCTURE,
        ALREADY_REGISTERED,
        INSUFFICIENT_FUNDS,
        ECONOMY_FAILURE,
        PERSISTENCE_FAILURE
    }

    public enum TravelStatus {
        SUCCESS,
        NOT_ON_ELEVATOR,
        NO_DESTINATION,
        UNSAFE_DESTINATION,
        BROKEN_STRUCTURE,
        LOCKED,
        COOLDOWN,
        ERROR
    }

    public enum MemberStatus {
        SUCCESS,
        NETWORK_NOT_FOUND,
        OWNER,
        ALREADY_MEMBER,
        NOT_MEMBER
    }

    public enum ChangeStatus {
        SUCCESS,
        NOT_FOUND,
        INVALID_TARGET,
        CONFLICT
    }

    public record CreationResult(CreationStatus status, FloorRecord floor, BigDecimal paidAmount) {
    }

    public record MemberResult(MemberStatus status, String playerName) {
    }

    public record TravelResult(TravelStatus status, FloorRecord source, FloorRecord destination,
                               String destinationName, int direction) {
    }

    private final JavaPlugin plugin;
    private final ElevatorDatabase database;
    private final Economy economy;
    private final TransactionLogger transactionLogger;
    private final Supplier<PluginSettings> settings;
    private final Map<UUID, Long> teleportCooldowns = new ConcurrentHashMap<>();

    public ElevatorService(JavaPlugin plugin, ElevatorDatabase database, Economy economy,
                           TransactionLogger transactionLogger, Supplier<PluginSettings> settings) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.transactionLogger = transactionLogger;
        this.settings = settings;
    }

    public boolean isValidNewStructure(Block topBlock) {
        return topBlock.getType() == settings.get().elevatorMaterial()
                && topBlock.getRelative(0, -1, 0).getType() == Material.REDSTONE_BLOCK;
    }

    public boolean isActiveStructure(FloorRecord floor) {
        World world = Bukkit.getWorld(floor.worldId());
        if (world == null) {
            return false;
        }
        Material expected = Material.matchMaterial(floor.material());
        if (expected == null) {
            return false;
        }
        Block top = world.getBlockAt(floor.x(), floor.y(), floor.z());
        return top.getType() == expected && top.getRelative(0, -1, 0).getType() == Material.REDSTONE_BLOCK;
    }

    public synchronized CreationResult createFloor(Player player, FloorLocation location) {
        World world = Bukkit.getWorld(location.worldId());
        if (world == null) {
            return new CreationResult(CreationStatus.INVALID_STRUCTURE, null, BigDecimal.ZERO);
        }
        Block top = world.getBlockAt(location.x(), location.y(), location.z());
        if (!isValidNewStructure(top)) {
            return new CreationResult(CreationStatus.INVALID_STRUCTURE, null, BigDecimal.ZERO);
        }
        try {
            if (database.findFloorAt(location.worldId(), location.x(), location.y(), location.z()).isPresent()) {
                return new CreationResult(CreationStatus.ALREADY_REGISTERED, null, BigDecimal.ZERO);
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo comprobar la ubicación: " + exception.getMessage());
            return new CreationResult(CreationStatus.PERSISTENCE_FAILURE, null, BigDecimal.ZERO);
        }

        BigDecimal configuredPrice = settings.get().creationPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal charged = player.hasPermission("velevators.bypass.cost")
                ? BigDecimal.ZERO.setScale(2) : configuredPrice;
        boolean withdrew = false;
        if (charged.signum() > 0) {
            if (!economy.has(player, charged.doubleValue())) {
                return new CreationResult(CreationStatus.INSUFFICIENT_FUNDS, null, charged);
            }
            EconomyResponse response = economy.withdrawPlayer(player, charged.doubleValue());
            if (!response.transactionSuccess()) {
                plugin.getLogger().warning("Vault rechazó el cobro a " + player.getName() + ": " + response.errorMessage);
                return new CreationResult(CreationStatus.ECONOMY_FAILURE, null, charged);
            }
            withdrew = true;
        }

        Instant now = Instant.now();
        FloorRecord created;
        try {
            created = database.createFloor(player.getUniqueId(), player.getName(), location,
                    top.getType().name(), charged, now);
        } catch (SQLException exception) {
            if (withdrew) {
                refund(player, charged, "fallo de SQLite durante la creación");
            }
            plugin.getLogger().severe("No se pudo guardar el piso: " + exception.getMessage());
            return new CreationResult(CreationStatus.PERSISTENCE_FAILURE, null, charged);
        }

        if (charged.signum() > 0) {
            try {
                transactionLogger.append(now, player.getName(), player.getUniqueId(), charged, location);
            } catch (IOException logFailure) {
                plugin.getLogger().severe("No se pudo escribir transactions.log: " + logFailure.getMessage());
                try {
                    database.rollbackCreatedFloor(created.id());
                } catch (SQLException rollbackFailure) {
                    plugin.getLogger().severe("También falló la reversión del piso: " + rollbackFailure.getMessage());
                }
                refund(player, charged, "fallo del registro de transacciones");
                return new CreationResult(CreationStatus.PERSISTENCE_FAILURE, null, charged);
            }
        }
        return new CreationResult(CreationStatus.SUCCESS, created, charged);
    }

    private void refund(Player player, BigDecimal amount, String reason) {
        EconomyResponse refund = economy.depositPlayer(player, amount.doubleValue());
        if (!refund.transactionSuccess()) {
            plugin.getLogger().severe("CRÍTICO: no se pudo devolver " + amount + " a " + player.getName()
                    + " tras " + reason + ": " + refund.errorMessage);
        }
    }

    public boolean deleteFloor(long floorId) throws SQLException {
        return database.deleteFloor(floorId);
    }

    public boolean deleteFloor(long floorId, Player actor) throws SQLException {
        return database.deleteFloor(floorId, actor.getUniqueId(), actor.getName(), Instant.now());
    }

    public boolean renameFloor(long floorId, String name) throws SQLException {
        return database.renameFloor(floorId, name);
    }

    public boolean renameFloor(long floorId, String name, Player actor) throws SQLException {
        return database.renameFloor(floorId, name, actor.getUniqueId(), actor.getName(), Instant.now());
    }

    public MemberResult addMember(long networkId, UUID playerId, String playerName) throws SQLException {
        return addMember(networkId, playerId, playerName, playerId, playerName);
    }

    public MemberResult addMember(long networkId, UUID playerId, String playerName,
                                  UUID actorId, String actorName) throws SQLException {
        NetworkRecord network = database.getNetwork(networkId).orElse(null);
        if (network == null) {
            return new MemberResult(MemberStatus.NETWORK_NOT_FOUND, playerName);
        }
        if (network.ownerId().equals(playerId)) {
            return new MemberResult(MemberStatus.OWNER, playerName);
        }
        if (database.isMember(networkId, playerId)) {
            return new MemberResult(MemberStatus.ALREADY_MEMBER, playerName);
        }
        return new MemberResult(database.addMember(networkId, playerId, playerName, Instant.now(), actorId, actorName)
                ? MemberStatus.SUCCESS : MemberStatus.ALREADY_MEMBER, playerName);
    }

    public MemberResult removeMember(long networkId, UUID playerId, String playerName) throws SQLException {
        return removeMember(networkId, playerId, playerName, playerId, playerName);
    }

    public MemberResult removeMember(long networkId, UUID playerId, String playerName,
                                     UUID actorId, String actorName) throws SQLException {
        NetworkRecord network = database.getNetwork(networkId).orElse(null);
        if (network == null) {
            return new MemberResult(MemberStatus.NETWORK_NOT_FOUND, playerName);
        }
        if (network.ownerId().equals(playerId)) {
            return new MemberResult(MemberStatus.OWNER, playerName);
        }
        return new MemberResult(database.removeMember(networkId, playerId, actorId, actorName, Instant.now())
                ? MemberStatus.SUCCESS : MemberStatus.NOT_MEMBER, playerName);
    }

    public boolean renameNetwork(long networkId, String name, Player actor) throws SQLException {
        return database.renameNetwork(networkId, name, actor.getUniqueId(), actor.getName(), Instant.now());
    }

    public boolean setNetworkIcon(long networkId, Material icon, Player actor) throws SQLException {
        return database.setNetworkIcon(networkId, icon.name(), actor.getUniqueId(), actor.getName(), Instant.now());
    }

    public boolean setFloorIcon(long floorId, Material icon, Player actor) throws SQLException {
        return database.setFloorIcon(floorId, icon.name(), actor.getUniqueId(), actor.getName(), Instant.now());
    }

    public boolean setNetworkAccessMode(long networkId, AccessMode mode, Player actor) throws SQLException {
        return database.setNetworkAccessMode(networkId, mode, actor.getUniqueId(), actor.getName(), Instant.now());
    }

    public boolean setFloorAccessMode(long floorId, AccessMode mode, Player actor) throws SQLException {
        return database.setFloorAccessMode(floorId, mode, actor.getUniqueId(), actor.getName(), Instant.now());
    }

    public ChangeStatus transferOwnership(long networkId, UUID newOwnerId, String newOwnerName, Player actor)
            throws SQLException {
        NetworkRecord network = database.getNetwork(networkId).orElse(null);
        if (network == null) {
            return ChangeStatus.NOT_FOUND;
        }
        if (network.ownerId().equals(newOwnerId) || !database.isMember(networkId, newOwnerId)) {
            return ChangeStatus.INVALID_TARGET;
        }
        try {
            return database.transferOwnership(networkId, newOwnerId, newOwnerName,
                    actor.getUniqueId(), actor.getName(), Instant.now())
                    ? ChangeStatus.SUCCESS : ChangeStatus.INVALID_TARGET;
        } catch (SQLException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("UNIQUE constraint failed")) {
                return ChangeStatus.CONFLICT;
            }
            throw exception;
        }
    }

    public boolean deleteNetwork(long networkId, Player actor) throws SQLException {
        return database.deleteNetwork(networkId, actor.getUniqueId(), actor.getName(), Instant.now());
    }

    public Optional<FloorRecord> findFloor(Block block) throws SQLException {
        return database.findFloorAt(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public Optional<FloorRecord> findProtectedFloor(Block block) throws SQLException {
        return database.findProtectedFloor(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public TravelStatus travel(Player player, int direction) {
        return travelDetailed(player, direction).status();
    }

    public TravelResult travelDetailed(Player player, int direction) {
        if (direction == 0) {
            return result(TravelStatus.NO_DESTINATION, null, null, null, direction);
        }
        long now = System.currentTimeMillis();
        long readyAt = teleportCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            return result(TravelStatus.COOLDOWN, null, null, null, direction);
        }
        Block currentBlock = player.getLocation().getBlock().getRelative(0, -1, 0);
        try {
            FloorRecord current = findFloor(currentBlock).orElse(null);
            if (current == null) {
                return result(TravelStatus.NOT_ON_ELEVATOR, null, null, null, direction);
            }
            if (!isActiveStructure(current)) {
                return result(TravelStatus.BROKEN_STRUCTURE, current, null, null, direction);
            }
            boolean bypass = player.hasPermission("velevators.bypass.access");
            if (!bypass && !database.canTravel(current.networkId(), player.getUniqueId())) {
                return result(TravelStatus.LOCKED, current, null, null, direction);
            }
            NetworkRecord network = database.getNetwork(current.networkId()).orElse(null);
            if (network == null) {
                return result(TravelStatus.ERROR, current, null, null, direction);
            }
            List<FloorRecord> floors = database.getFloors(current.networkId()).stream()
                    .sorted(Comparator.comparingInt(FloorRecord::y))
                    .toList();
            int index = -1;
            for (int candidate = 0; candidate < floors.size(); candidate++) {
                if (floors.get(candidate).id() == current.id()) {
                    index = candidate;
                    break;
                }
            }
            if (index < 0) {
                return result(TravelStatus.NO_DESTINATION, current, null, null, direction);
            }
            FloorRecord target = null;
            boolean restrictedDestination = false;
            for (int candidate = index + Integer.signum(direction);
                 candidate >= 0 && candidate < floors.size(); candidate += Integer.signum(direction)) {
                FloorRecord possible = floors.get(candidate);
                if (bypass || database.canAccessFloor(possible, player.getUniqueId())) {
                    target = possible;
                    break;
                }
                restrictedDestination = true;
            }
            if (target == null) {
                return result(restrictedDestination ? TravelStatus.LOCKED : TravelStatus.NO_DESTINATION,
                        current, null, null, direction);
            }
            if (!isActiveStructure(target)) {
                return result(TravelStatus.BROKEN_STRUCTURE, current, target, null, direction);
            }
            World world = Bukkit.getWorld(target.worldId());
            if (world == null) {
                return result(TravelStatus.ERROR, current, target, null, direction);
            }
            if (settings.get().destinationSafety()
                    && (!world.getBlockAt(target.x(), target.y() + 1, target.z()).isPassable()
                    || !world.getBlockAt(target.x(), target.y() + 2, target.z()).isPassable())) {
                return result(TravelStatus.UNSAFE_DESTINATION, current, target, null, direction);
            }

            Location destination = new Location(world, target.x() + 0.5D, target.y() + 1.0D,
                    target.z() + 0.5D, player.getLocation().getYaw(), player.getLocation().getPitch());
            if (!player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                return result(TravelStatus.ERROR, current, target, null, direction);
            }
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0.0F);
            teleportCooldowns.put(player.getUniqueId(), now + settings.get().teleportCooldownMillis());
            FloorRecord resolvedTarget = target;
            String destinationName = FloorNames.buildViews(network, floors, resolvedTarget.id()).stream()
                    .filter(view -> view.floor().id() == resolvedTarget.id())
                    .map(FloorView::displayName).findFirst().orElse("Piso");
            return result(TravelStatus.SUCCESS, current, resolvedTarget, destinationName, direction);
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo resolver el viaje: " + exception.getMessage());
            return result(TravelStatus.ERROR, null, null, null, direction);
        }
    }

    public TravelStatus teleportToNetworkAnchor(Player player, long networkId) {
        try {
            NetworkRecord network = database.getNetwork(networkId).orElse(null);
            if (network == null || network.anchorFloorId() == null) {
                return TravelStatus.NO_DESTINATION;
            }
            FloorRecord target = database.getFloor(network.anchorFloorId()).orElse(null);
            if (target == null || !isActiveStructure(target)) {
                return TravelStatus.BROKEN_STRUCTURE;
            }
            World world = Bukkit.getWorld(target.worldId());
            if (world == null) {
                return TravelStatus.ERROR;
            }
            if (settings.get().destinationSafety()
                    && (!world.getBlockAt(target.x(), target.y() + 1, target.z()).isPassable()
                    || !world.getBlockAt(target.x(), target.y() + 2, target.z()).isPassable())) {
                return TravelStatus.UNSAFE_DESTINATION;
            }
            Location destination = new Location(world, target.x() + 0.5D, target.y() + 1.0D,
                    target.z() + 0.5D, player.getLocation().getYaw(), player.getLocation().getPitch());
            if (!player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                return TravelStatus.ERROR;
            }
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0.0F);
            return TravelStatus.SUCCESS;
        } catch (SQLException exception) {
            plugin.getLogger().severe("No se pudo teletransportar al ancla de la red: " + exception.getMessage());
            return TravelStatus.ERROR;
        }
    }

    private TravelResult result(TravelStatus status, FloorRecord source, FloorRecord destination,
                                String destinationName, int direction) {
        return new TravelResult(status, source, destination, destinationName, Integer.signum(direction));
    }

    public String formatPrice(BigDecimal price) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-ES"));
        DecimalFormat format = new DecimalFormat("#,##0.##", symbols);
        return format.format(price);
    }

    public String formatPrice() {
        return formatPrice(settings.get().creationPrice());
    }

    public ElevatorDatabase database() {
        return database;
    }
}
