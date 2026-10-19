package net.velevators.data;

import net.velevators.model.FloorLocation;
import net.velevators.model.FloorRecord;
import net.velevators.model.AccessMode;
import net.velevators.model.AuditRecord;
import net.velevators.model.NetworkSummaryView;
import net.velevators.model.NetworkMemberRecord;
import net.velevators.model.NetworkRecord;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ElevatorDatabase implements AutoCloseable {
    private final Connection connection;

    public ElevatorDatabase(Path databaseFile) throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        migrate();
    }

    private void migrate() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS elevator_networks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner_uuid TEXT NOT NULL,
                        owner_name TEXT NOT NULL,
                        world_uuid TEXT NOT NULL,
                        world_name TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        anchor_floor_id INTEGER,
                        locked INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL,
                        UNIQUE(owner_uuid, world_uuid, x, z)
                    )
                    """);
            if (!hasColumn("elevator_networks", "locked")) {
                statement.executeUpdate("ALTER TABLE elevator_networks ADD COLUMN locked INTEGER NOT NULL DEFAULT 0");
            }
            if (!hasColumn("elevator_networks", "display_name")) {
                statement.executeUpdate("ALTER TABLE elevator_networks ADD COLUMN display_name TEXT");
            }
            if (!hasColumn("elevator_networks", "icon_material")) {
                statement.executeUpdate("ALTER TABLE elevator_networks ADD COLUMN icon_material TEXT NOT NULL DEFAULT 'ENDER_CHEST'");
            }
            if (!hasColumn("elevator_networks", "access_mode")) {
                statement.executeUpdate("ALTER TABLE elevator_networks ADD COLUMN access_mode TEXT NOT NULL DEFAULT 'PUBLIC'");
                statement.executeUpdate("UPDATE elevator_networks SET access_mode = CASE WHEN locked = 1 THEN 'PRIVATE' ELSE 'PUBLIC' END");
            }
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS elevator_floors (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        network_id INTEGER NOT NULL,
                        world_uuid TEXT NOT NULL,
                        world_name TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        material TEXT NOT NULL,
                        custom_name TEXT,
                        paid_amount TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        FOREIGN KEY(network_id) REFERENCES elevator_networks(id) ON DELETE CASCADE,
                        UNIQUE(network_id, y),
                        UNIQUE(world_uuid, x, y, z)
                    )
                    """);
            if (!hasColumn("elevator_floors", "icon_material")) {
                statement.executeUpdate("ALTER TABLE elevator_floors ADD COLUMN icon_material TEXT NOT NULL DEFAULT 'QUARTZ_BLOCK'");
            }
            if (!hasColumn("elevator_floors", "access_mode")) {
                statement.executeUpdate("ALTER TABLE elevator_floors ADD COLUMN access_mode TEXT NOT NULL DEFAULT 'PUBLIC'");
            }
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS elevator_network_members (
                        network_id INTEGER NOT NULL,
                        member_uuid TEXT NOT NULL,
                        member_name TEXT NOT NULL,
                        added_at TEXT NOT NULL,
                        PRIMARY KEY(network_id, member_uuid),
                        FOREIGN KEY(network_id) REFERENCES elevator_networks(id) ON DELETE CASCADE
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS elevator_admin_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        network_id INTEGER,
                        actor_uuid TEXT NOT NULL,
                        actor_name TEXT NOT NULL,
                        action TEXT NOT NULL,
                        details TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_floors_network_y ON elevator_floors(network_id, y)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_floors_location ON elevator_floors(world_uuid, x, y, z)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_network_members_uuid ON elevator_network_members(member_uuid)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_network_owner ON elevator_networks(owner_uuid)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_network_world_xz ON elevator_networks(world_uuid, x, z)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_admin_log_created ON elevator_admin_log(id DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_admin_log_network ON elevator_admin_log(network_id, id DESC)");
        }
    }

    private boolean hasColumn(String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                if (column.equalsIgnoreCase(result.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    public synchronized Optional<FloorRecord> findFloorAt(UUID worldId, int x, int y, int z) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM elevator_floors WHERE world_uuid = ? AND x = ? AND y = ? AND z = ?
                """)) {
            statement.setString(1, worldId.toString());
            statement.setInt(2, x);
            statement.setInt(3, y);
            statement.setInt(4, z);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readFloor(result)) : Optional.empty();
            }
        }
    }

    public synchronized Optional<FloorRecord> findProtectedFloor(UUID worldId, int x, int blockY, int z) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM elevator_floors
                WHERE world_uuid = ? AND x = ? AND z = ? AND (y = ? OR y = ?)
                LIMIT 1
                """)) {
            statement.setString(1, worldId.toString());
            statement.setInt(2, x);
            statement.setInt(3, z);
            statement.setInt(4, blockY);
            statement.setInt(5, blockY + 1);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readFloor(result)) : Optional.empty();
            }
        }
    }

    public synchronized Optional<FloorRecord> getFloor(long floorId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM elevator_floors WHERE id = ?")) {
            statement.setLong(1, floorId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readFloor(result)) : Optional.empty();
            }
        }
    }

    public synchronized Optional<NetworkRecord> getNetwork(long networkId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM elevator_networks WHERE id = ?")) {
            statement.setLong(1, networkId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readNetwork(result)) : Optional.empty();
            }
        }
    }

    public synchronized List<NetworkRecord> findNetworksByOwner(UUID ownerId) throws SQLException {
        return readNetworks("SELECT * FROM elevator_networks WHERE owner_uuid = ? ORDER BY id DESC",
                statement -> statement.setString(1, ownerId.toString()));
    }

    public synchronized List<NetworkRecord> findNetworksByOwnerName(String ownerName) throws SQLException {
        return readNetworks("SELECT * FROM elevator_networks WHERE owner_name = ? COLLATE NOCASE ORDER BY id DESC",
                statement -> statement.setString(1, ownerName));
    }

    public synchronized List<NetworkRecord> findNetworksByWorld(String worldName) throws SQLException {
        return readNetworks("SELECT * FROM elevator_networks WHERE world_name = ? COLLATE NOCASE ORDER BY id DESC",
                statement -> statement.setString(1, worldName));
    }

    public synchronized List<NetworkRecord> findNetworksByCoordinates(String worldName, int x, int z)
            throws SQLException {
        return readNetworks("""
                SELECT * FROM elevator_networks
                WHERE world_name = ? COLLATE NOCASE AND x = ? AND z = ? ORDER BY id DESC
                """, statement -> {
            statement.setString(1, worldName);
            statement.setInt(2, x);
            statement.setInt(3, z);
        });
    }

    public synchronized NetworkSummaryView getNetworkSummary(NetworkRecord network) throws SQLException {
        List<FloorRecord> floors = getFloors(network.id());
        FloorRecord anchor = network.anchorFloorId() == null ? null : floors.stream()
                .filter(floor -> floor.id() == network.anchorFloorId())
                .findFirst().orElse(null);
        return new NetworkSummaryView(network, floors.size(), getMembers(network.id()).size(), anchor);
    }

    public synchronized List<NetworkSummaryView> summarizeNetworks(List<NetworkRecord> networks) throws SQLException {
        List<NetworkSummaryView> summaries = new ArrayList<>(networks.size());
        for (NetworkRecord network : networks) {
            summaries.add(getNetworkSummary(network));
        }
        return List.copyOf(summaries);
    }

    private List<NetworkRecord> readNetworks(String sql, StatementBinder binder) throws SQLException {
        List<NetworkRecord> networks = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    networks.add(readNetwork(result));
                }
            }
        }
        return List.copyOf(networks);
    }

    public synchronized List<FloorRecord> getFloors(long networkId) throws SQLException {
        List<FloorRecord> floors = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM elevator_floors WHERE network_id = ? ORDER BY y ASC, id ASC")) {
            statement.setLong(1, networkId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    floors.add(readFloor(result));
                }
            }
        }
        return List.copyOf(floors);
    }

    public synchronized List<NetworkMemberRecord> getMembers(long networkId) throws SQLException {
        List<NetworkMemberRecord> members = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT network_id, member_uuid, member_name, added_at
                FROM elevator_network_members
                WHERE network_id = ?
                ORDER BY member_name COLLATE NOCASE ASC, member_uuid ASC
                """)) {
            statement.setLong(1, networkId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    members.add(readMember(result));
                }
            }
        }
        return List.copyOf(members);
    }

    public synchronized boolean isMember(long networkId, UUID memberId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM elevator_network_members WHERE network_id = ? AND member_uuid = ?
                """)) {
            statement.setLong(1, networkId);
            statement.setString(2, memberId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public synchronized Optional<NetworkMemberRecord> findMemberByName(long networkId, String memberName)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT network_id, member_uuid, member_name, added_at
                FROM elevator_network_members
                WHERE network_id = ? AND member_name = ? COLLATE NOCASE
                LIMIT 1
                """)) {
            statement.setLong(1, networkId);
            statement.setString(2, memberName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readMember(result)) : Optional.empty();
            }
        }
    }

    public synchronized boolean addMember(long networkId, UUID memberId, String memberName, Instant addedAt)
            throws SQLException {
        return addMember(networkId, memberId, memberName, addedAt, memberId, memberName);
    }

    public synchronized boolean addMember(long networkId, UUID memberId, String memberName, Instant addedAt,
                                          UUID actorId, String actorName) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT OR IGNORE INTO elevator_network_members
                    (network_id, member_uuid, member_name, added_at)
                    VALUES (?, ?, ?, ?)
                    """)) {
                statement.setLong(1, networkId);
                statement.setString(2, memberId.toString());
                statement.setString(3, memberName);
                statement.setString(4, addedAt.toString());
                if (statement.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
            }
            appendAudit(networkId, actorId, actorName, "MEMBER_ADDED", memberName, addedAt);
            connection.commit();
            return true;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public synchronized boolean removeMember(long networkId, UUID memberId) throws SQLException {
        NetworkMemberRecord member = getMembers(networkId).stream()
                .filter(candidate -> candidate.memberId().equals(memberId)).findFirst().orElse(null);
        return removeMember(networkId, memberId, memberId,
                member == null ? memberId.toString() : member.memberName(), Instant.now());
    }

    public synchronized boolean removeMember(long networkId, UUID memberId, UUID actorId,
                                             String actorName, Instant at) throws SQLException {
        NetworkMemberRecord member = getMembers(networkId).stream()
                .filter(candidate -> candidate.memberId().equals(memberId)).findFirst().orElse(null);
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM elevator_network_members WHERE network_id = ? AND member_uuid = ?
                    """)) {
                statement.setLong(1, networkId);
                statement.setString(2, memberId.toString());
                if (statement.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
            }
            appendAudit(networkId, actorId, actorName, "MEMBER_REMOVED",
                    member == null ? memberId.toString() : member.memberName(), at);
            connection.commit();
            return true;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public synchronized boolean setNetworkLocked(long networkId, boolean locked) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE elevator_networks SET locked = ?, access_mode = ? WHERE id = ?")) {
            statement.setInt(1, locked ? 1 : 0);
            statement.setString(2, locked ? AccessMode.PRIVATE.name() : AccessMode.PUBLIC.name());
            statement.setLong(3, networkId);
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized boolean canTravel(long networkId, UUID playerId) throws SQLException {
        NetworkRecord network = getNetwork(networkId).orElse(null);
        return network != null && canUse(network.accessMode(), network.ownerId().equals(playerId),
                isMember(networkId, playerId));
    }

    public synchronized boolean canAccessFloor(FloorRecord floor, UUID playerId) throws SQLException {
        NetworkRecord network = getNetwork(floor.networkId()).orElse(null);
        if (network == null) {
            return false;
        }
        boolean owner = network.ownerId().equals(playerId);
        boolean member = isMember(network.id(), playerId);
        return canUse(network.accessMode(), owner, member) && canUse(floor.accessMode(), owner, member);
    }

    public synchronized int countNetworks(UUID ownerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM elevator_networks WHERE owner_uuid = ?")) {
            statement.setString(1, ownerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    public synchronized FloorRecord createFloor(UUID ownerId, String ownerName, FloorLocation location,
                                                String material, BigDecimal paidAmount, Instant createdAt) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            NetworkRecord network = findNetwork(ownerId, location).orElse(null);
            boolean newNetwork = network == null;
            long networkId = newNetwork ? insertNetwork(ownerId, ownerName, location, createdAt) : network.id();
            if (!newNetwork) {
                updateOwnerName(networkId, ownerName);
            }
            long floorId = insertFloor(networkId, location, material, paidAmount, createdAt);
            if (newNetwork) {
                updateAnchor(networkId, floorId);
            }
            FloorRecord created = getFloor(floorId).orElseThrow(() -> new SQLException("No se pudo releer el piso creado"));
            appendAudit(networkId, ownerId, ownerName, newNetwork ? "NETWORK_CREATED" : "FLOOR_CREATED",
                    location.worldName() + " " + location.x() + " " + location.y() + " " + location.z(), createdAt);
            connection.commit();
            return created;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public synchronized boolean renameFloor(long floorId, String customName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE elevator_floors SET custom_name = ? WHERE id = ?")) {
            statement.setString(1, customName);
            statement.setLong(2, floorId);
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized boolean renameFloor(long floorId, String customName, UUID actorId,
                                            String actorName, Instant at) throws SQLException {
        FloorRecord floor = getFloor(floorId).orElse(null);
        if (floor == null) {
            return false;
        }
        return updateWithAudit("UPDATE elevator_floors SET custom_name = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, customName);
                    statement.setLong(2, floorId);
                }, floor.networkId(), actorId, actorName, "FLOOR_RENAMED", customName, at);
    }

    public synchronized boolean renameNetwork(long networkId, String displayName, UUID actorId,
                                              String actorName, Instant at) throws SQLException {
        return updateWithAudit("UPDATE elevator_networks SET display_name = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, displayName);
                    statement.setLong(2, networkId);
                }, networkId, actorId, actorName, "NETWORK_RENAMED", displayName, at);
    }

    public synchronized boolean setNetworkIcon(long networkId, String material, UUID actorId,
                                               String actorName, Instant at) throws SQLException {
        return updateWithAudit("UPDATE elevator_networks SET icon_material = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, material);
                    statement.setLong(2, networkId);
                }, networkId, actorId, actorName, "NETWORK_ICON_CHANGED", material, at);
    }

    public synchronized boolean setNetworkAccessMode(long networkId, AccessMode mode, UUID actorId,
                                                     String actorName, Instant at) throws SQLException {
        return updateWithAudit("UPDATE elevator_networks SET access_mode = ?, locked = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, mode.name());
                    statement.setInt(2, mode == AccessMode.PUBLIC ? 0 : 1);
                    statement.setLong(3, networkId);
                }, networkId, actorId, actorName, "NETWORK_ACCESS_CHANGED", mode.name(), at);
    }

    public synchronized boolean setFloorIcon(long floorId, String material, UUID actorId,
                                             String actorName, Instant at) throws SQLException {
        FloorRecord floor = getFloor(floorId).orElse(null);
        if (floor == null) {
            return false;
        }
        return updateWithAudit("UPDATE elevator_floors SET icon_material = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, material);
                    statement.setLong(2, floorId);
                }, floor.networkId(), actorId, actorName, "FLOOR_ICON_CHANGED",
                floorId + ":" + material, at);
    }

    public synchronized boolean setFloorAccessMode(long floorId, AccessMode mode, UUID actorId,
                                                   String actorName, Instant at) throws SQLException {
        FloorRecord floor = getFloor(floorId).orElse(null);
        if (floor == null) {
            return false;
        }
        return updateWithAudit("UPDATE elevator_floors SET access_mode = ? WHERE id = ?",
                statement -> {
                    statement.setString(1, mode.name());
                    statement.setLong(2, floorId);
                }, floor.networkId(), actorId, actorName, "FLOOR_ACCESS_CHANGED",
                floorId + ":" + mode.name(), at);
    }

    public synchronized boolean transferOwnership(long networkId, UUID newOwnerId, String newOwnerName,
                                                  UUID actorId, String actorName, Instant at) throws SQLException {
        NetworkRecord network = getNetwork(networkId).orElse(null);
        if (network == null || network.ownerId().equals(newOwnerId) || !isMember(networkId, newOwnerId)) {
            return false;
        }
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement remove = connection.prepareStatement(
                    "DELETE FROM elevator_network_members WHERE network_id = ? AND member_uuid = ?")) {
                remove.setLong(1, networkId);
                remove.setString(2, newOwnerId.toString());
                if (remove.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
            }
            try (PreparedStatement addOld = connection.prepareStatement("""
                    INSERT OR REPLACE INTO elevator_network_members
                    (network_id, member_uuid, member_name, added_at) VALUES (?, ?, ?, ?)
                    """)) {
                addOld.setLong(1, networkId);
                addOld.setString(2, network.ownerId().toString());
                addOld.setString(3, network.ownerName());
                addOld.setString(4, at.toString());
                addOld.executeUpdate();
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE elevator_networks SET owner_uuid = ?, owner_name = ? WHERE id = ?
                    """)) {
                update.setString(1, newOwnerId.toString());
                update.setString(2, newOwnerName);
                update.setLong(3, networkId);
                if (update.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
            }
            appendAudit(networkId, actorId, actorName, "OWNER_TRANSFERRED",
                    network.ownerName() + " -> " + newOwnerName, at);
            connection.commit();
            return true;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public synchronized boolean deleteNetwork(long networkId, UUID actorId, String actorName, Instant at)
            throws SQLException {
        NetworkRecord network = getNetwork(networkId).orElse(null);
        if (network == null) {
            return false;
        }
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM elevator_networks WHERE id = ?")) {
                statement.setLong(1, networkId);
                if (statement.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
            }
            appendAudit(networkId, actorId, actorName, "NETWORK_DELETED",
                    network.effectiveName() + " @ " + network.worldName() + " " + network.x() + " " + network.z(), at);
            connection.commit();
            return true;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public synchronized List<AuditRecord> getAuditRecords() throws SQLException {
        List<AuditRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM elevator_admin_log ORDER BY id DESC");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                records.add(readAudit(result));
            }
        }
        return List.copyOf(records);
    }

    public synchronized boolean deleteFloor(long floorId) throws SQLException {
        FloorRecord floor = getFloor(floorId).orElse(null);
        if (floor == null) {
            return false;
        }
        NetworkRecord network = getNetwork(floor.networkId()).orElse(null);
        if (network == null) {
            return false;
        }

        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            if (!deleteFloorInternal(floor, network)) {
                connection.rollback();
                return false;
            }
            connection.commit();
            return true;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    public synchronized boolean deleteFloor(long floorId, UUID actorId, String actorName, Instant at)
            throws SQLException {
        FloorRecord floor = getFloor(floorId).orElse(null);
        if (floor == null) {
            return false;
        }
        NetworkRecord network = getNetwork(floor.networkId()).orElse(null);
        if (network == null) {
            return false;
        }
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            if (!deleteFloorInternal(floor, network)) {
                connection.rollback();
                return false;
            }
            appendAudit(floor.networkId(), actorId, actorName, "FLOOR_DELETED",
                    floor.worldName() + " " + floor.x() + " " + floor.y() + " " + floor.z(), at);
            connection.commit();
            return true;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    /** Reverts a creation that could not finish its external transaction log. */
    public synchronized boolean rollbackCreatedFloor(long floorId) throws SQLException {
        FloorRecord floor = getFloor(floorId).orElse(null);
        if (floor == null) {
            return false;
        }
        NetworkRecord network = getNetwork(floor.networkId()).orElse(null);
        if (network == null) {
            return false;
        }
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM elevator_admin_log
                    WHERE network_id = ? AND created_at = ?
                      AND action IN ('NETWORK_CREATED', 'FLOOR_CREATED')
                    """)) {
                statement.setLong(1, floor.networkId());
                statement.setString(2, floor.createdAt());
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("No se encontró la auditoría de la creación que debía revertirse");
                }
            }
            if (!deleteFloorInternal(floor, network)) {
                connection.rollback();
                return false;
            }
            connection.commit();
            return true;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private boolean deleteFloorInternal(FloorRecord floor, NetworkRecord network) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM elevator_floors WHERE id = ?")) {
            statement.setLong(1, floor.id());
            if (statement.executeUpdate() != 1) {
                return false;
            }
        }

        List<FloorRecord> remaining = getFloors(network.id());
        if (remaining.isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM elevator_networks WHERE id = ?")) {
                statement.setLong(1, network.id());
                statement.executeUpdate();
            }
        } else if (network.anchorFloorId() != null && network.anchorFloorId() == floor.id()) {
            FloorRecord newAnchor = remaining.stream()
                    .min(Comparator.comparingInt((FloorRecord candidate) -> Math.abs(candidate.y() - floor.y()))
                            .thenComparingInt(FloorRecord::y)
                            .thenComparingLong(FloorRecord::id))
                    .orElseThrow();
            updateAnchor(network.id(), newAnchor.id());
        }
        return true;
    }

    private Optional<NetworkRecord> findNetwork(UUID ownerId, FloorLocation location) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM elevator_networks
                WHERE owner_uuid = ? AND world_uuid = ? AND x = ? AND z = ?
                """)) {
            statement.setString(1, ownerId.toString());
            statement.setString(2, location.worldId().toString());
            statement.setInt(3, location.x());
            statement.setInt(4, location.z());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readNetwork(result)) : Optional.empty();
            }
        }
    }

    private long insertNetwork(UUID ownerId, String ownerName, FloorLocation location, Instant createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO elevator_networks
                (owner_uuid, owner_name, world_uuid, world_name, x, z, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, ownerId.toString());
            statement.setString(2, ownerName);
            statement.setString(3, location.worldId().toString());
            statement.setString(4, location.worldName());
            statement.setInt(5, location.x());
            statement.setInt(6, location.z());
            statement.setString(7, createdAt.toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite no devolvió el ID de la red");
                }
                return keys.getLong(1);
            }
        }
    }

    private long insertFloor(long networkId, FloorLocation location, String material,
                             BigDecimal paidAmount, Instant createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO elevator_floors
                (network_id, world_uuid, world_name, x, y, z, material, paid_amount, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, networkId);
            statement.setString(2, location.worldId().toString());
            statement.setString(3, location.worldName());
            statement.setInt(4, location.x());
            statement.setInt(5, location.y());
            statement.setInt(6, location.z());
            statement.setString(7, material);
            statement.setString(8, paidAmount.toPlainString());
            statement.setString(9, createdAt.toString());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite no devolvió el ID del piso");
                }
                return keys.getLong(1);
            }
        }
    }

    private void updateOwnerName(long networkId, String ownerName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE elevator_networks SET owner_name = ? WHERE id = ?")) {
            statement.setString(1, ownerName);
            statement.setLong(2, networkId);
            statement.executeUpdate();
        }
    }

    private void updateAnchor(long networkId, long floorId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE elevator_networks SET anchor_floor_id = ? WHERE id = ?")) {
            statement.setLong(1, floorId);
            statement.setLong(2, networkId);
            statement.executeUpdate();
        }
    }

    private boolean updateWithAudit(String sql, StatementBinder binder, long networkId, UUID actorId,
                                    String actorName, String action, String details, Instant at) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            if (statement.executeUpdate() != 1) {
                connection.rollback();
                return false;
            }
            appendAudit(networkId, actorId, actorName, action, details, at);
            connection.commit();
            return true;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void appendAudit(Long networkId, UUID actorId, String actorName, String action,
                             String details, Instant at) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO elevator_admin_log
                (network_id, actor_uuid, actor_name, action, details, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            if (networkId == null) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setLong(1, networkId);
            }
            statement.setString(2, actorId.toString());
            statement.setString(3, actorName);
            statement.setString(4, action);
            statement.setString(5, details);
            statement.setString(6, at.toString());
            statement.executeUpdate();
        }
    }

    private boolean canUse(AccessMode mode, boolean owner, boolean member) {
        return switch (mode) {
            case PUBLIC -> true;
            case PRIVATE -> owner || member;
            case OWNER_ONLY -> owner;
        };
    }

    private NetworkRecord readNetwork(ResultSet result) throws SQLException {
        long anchor = result.getLong("anchor_floor_id");
        Long anchorId = result.wasNull() ? null : anchor;
        return new NetworkRecord(
                result.getLong("id"),
                UUID.fromString(result.getString("owner_uuid")),
                result.getString("owner_name"),
                UUID.fromString(result.getString("world_uuid")),
                result.getString("world_name"),
                result.getInt("x"),
                result.getInt("z"),
                anchorId,
                result.getString("display_name"),
                result.getString("icon_material"),
                AccessMode.parse(result.getString("access_mode"),
                        result.getInt("locked") != 0 ? AccessMode.PRIVATE : AccessMode.PUBLIC),
                result.getString("created_at")
        );
    }

    private NetworkMemberRecord readMember(ResultSet result) throws SQLException {
        return new NetworkMemberRecord(
                result.getLong("network_id"),
                UUID.fromString(result.getString("member_uuid")),
                result.getString("member_name"),
                result.getString("added_at")
        );
    }

    private FloorRecord readFloor(ResultSet result) throws SQLException {
        return new FloorRecord(
                result.getLong("id"),
                result.getLong("network_id"),
                UUID.fromString(result.getString("world_uuid")),
                result.getString("world_name"),
                result.getInt("x"),
                result.getInt("y"),
                result.getInt("z"),
                result.getString("material"),
                result.getString("custom_name"),
                result.getString("icon_material"),
                AccessMode.parse(result.getString("access_mode"), AccessMode.PUBLIC),
                new BigDecimal(result.getString("paid_amount")),
                result.getString("created_at")
        );
    }

    private AuditRecord readAudit(ResultSet result) throws SQLException {
        long network = result.getLong("network_id");
        Long networkId = result.wasNull() ? null : network;
        return new AuditRecord(
                result.getLong("id"),
                networkId,
                UUID.fromString(result.getString("actor_uuid")),
                result.getString("actor_name"),
                result.getString("action"),
                result.getString("details"),
                result.getString("created_at")
        );
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    @Override
    public synchronized void close() throws SQLException {
        connection.close();
    }
}
