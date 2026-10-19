package net.velevators.data;

import net.velevators.model.FloorLocation;
import net.velevators.model.FloorRecord;
import net.velevators.model.NetworkRecord;
import net.velevators.model.AccessMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElevatorDatabaseTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsNetworksFloorsNamesAndPaymentsAcrossRestart() throws Exception {
        Path file = temporaryDirectory.resolve("elevators.db");
        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        long networkId;

        try (ElevatorDatabase database = new ElevatorDatabase(file)) {
            FloorRecord anchor = database.createFloor(owner, "Vicevil", location(world, 54),
                    "QUARTZ_BLOCK", new BigDecimal("10000.00"), Instant.parse("2026-08-09T12:00:00Z"));
            FloorRecord upper = database.createFloor(owner, "Vicevil", location(world, 70),
                    "QUARTZ_BLOCK", new BigDecimal("10000.00"), Instant.parse("2026-08-09T12:01:00Z"));
            database.createFloor(owner, "Vicevil", location(world, 28),
                    "QUARTZ_BLOCK", new BigDecimal("10000.00"), Instant.parse("2026-08-09T12:02:00Z"));
            networkId = anchor.networkId();

            assertThat(upper.networkId()).isEqualTo(networkId);
            assertThat(database.getNetwork(networkId).map(NetworkRecord::anchorFloorId)).contains(anchor.id());
            assertThat(database.getFloors(networkId)).hasSize(3);
            assertThat(database.renameFloor(anchor.id(), "piso de pepito")).isTrue();
            assertThat(database.getFloor(anchor.id()).map(FloorRecord::customName)).contains("piso de pepito");
        }

        try (ElevatorDatabase reopened = new ElevatorDatabase(file)) {
            assertThat(reopened.getFloors(networkId)).hasSize(3);
            assertThat(reopened.getFloors(networkId)).extracting(FloorRecord::paidAmount)
                    .containsOnly(new BigDecimal("10000.00"));
        }
    }

    @Test
    void selectsLowerFloorWhenAnchorDeletionHasEqualDistance() throws Exception {
        Path file = temporaryDirectory.resolve("anchor.db");
        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        try (ElevatorDatabase database = new ElevatorDatabase(file)) {
            FloorRecord anchor = database.createFloor(owner, "Vicevil", location(world, 54),
                    "QUARTZ_BLOCK", BigDecimal.TEN, Instant.now());
            FloorRecord lower = database.createFloor(owner, "Vicevil", location(world, 44),
                    "QUARTZ_BLOCK", BigDecimal.TEN, Instant.now());
            database.createFloor(owner, "Vicevil", location(world, 64),
                    "QUARTZ_BLOCK", BigDecimal.TEN, Instant.now());

            assertThat(database.deleteFloor(anchor.id())).isTrue();
            assertThat(database.getNetwork(anchor.networkId()).map(NetworkRecord::anchorFloorId)).contains(lower.id());
            assertThat(database.deleteFloor(lower.id())).isTrue();
            long lastId = database.getFloors(anchor.networkId()).getFirst().id();
            assertThat(database.deleteFloor(lastId)).isTrue();
            assertThat(database.getNetwork(anchor.networkId())).isEmpty();
        }
    }

    @Test
    void preventsTwoOwnersFromRegisteringTheSamePhysicalLocation() throws Exception {
        Path file = temporaryDirectory.resolve("unique.db");
        UUID world = UUID.randomUUID();
        try (ElevatorDatabase database = new ElevatorDatabase(file)) {
            database.createFloor(UUID.randomUUID(), "One", location(world, 54),
                    "QUARTZ_BLOCK", BigDecimal.TEN, Instant.now());
            assertThatThrownBy(() -> database.createFloor(UUID.randomUUID(), "Two", location(world, 54),
                    "QUARTZ_BLOCK", BigDecimal.TEN, Instant.now()))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void protectsBothTheRegisteredTopBlockAndItsRedstoneBase() throws Exception {
        Path file = temporaryDirectory.resolve("protection.db");
        UUID world = UUID.randomUUID();
        try (ElevatorDatabase database = new ElevatorDatabase(file)) {
            FloorRecord floor = database.createFloor(UUID.randomUUID(), "Vicevil", location(world, 54),
                    "QUARTZ_BLOCK", BigDecimal.TEN, Instant.now());

            assertThat(database.findProtectedFloor(world, 4419, 54, 1002)).contains(floor);
            assertThat(database.findProtectedFloor(world, 4419, 53, 1002)).contains(floor);
            assertThat(database.findProtectedFloor(world, 4419, 52, 1002)).isEmpty();
        }
    }

    @Test
    void persistsMembersAndLockStateAndEnforcesTravelAccess() throws Exception {
        Path file = temporaryDirectory.resolve("members.db");
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        long networkId;

        try (ElevatorDatabase database = new ElevatorDatabase(file)) {
            FloorRecord floor = database.createFloor(owner, "Vicevil", location(world, 54),
                    "QUARTZ_BLOCK", BigDecimal.TEN, Instant.now());
            networkId = floor.networkId();

            assertThat(database.getNetwork(networkId).orElseThrow().locked()).isFalse();
            assertThat(database.canTravel(networkId, outsider)).isTrue();
            assertThat(database.addMember(networkId, member, "Pepito", Instant.now())).isTrue();
            assertThat(database.addMember(networkId, member, "Pepito", Instant.now())).isFalse();
            assertThat(database.getMembers(networkId)).extracting("memberName").containsExactly("Pepito");
            assertThat(database.findMemberByName(networkId, "pEpItO")).isPresent();

            assertThat(database.setNetworkLocked(networkId, true)).isTrue();
            assertThat(database.canTravel(networkId, owner)).isTrue();
            assertThat(database.canTravel(networkId, member)).isTrue();
            assertThat(database.canTravel(networkId, outsider)).isFalse();
        }

        try (ElevatorDatabase reopened = new ElevatorDatabase(file)) {
            assertThat(reopened.getNetwork(networkId).orElseThrow().locked()).isTrue();
            assertThat(reopened.isMember(networkId, member)).isTrue();
            assertThat(reopened.removeMember(networkId, member)).isTrue();
            assertThat(reopened.removeMember(networkId, member)).isFalse();
            assertThat(reopened.canTravel(networkId, member)).isFalse();
        }
    }

    @Test
    void migratesVersionOneNetworksAsUnlockedWithoutLosingTheirOwner() throws Exception {
        Path file = temporaryDirectory.resolve("legacy.db");
        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE elevator_networks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        owner_uuid TEXT NOT NULL,
                        owner_name TEXT NOT NULL,
                        world_uuid TEXT NOT NULL,
                        world_name TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        anchor_floor_id INTEGER,
                        created_at TEXT NOT NULL,
                        UNIQUE(owner_uuid, world_uuid, x, z)
                    )
                    """);
            statement.executeUpdate(("""
                    INSERT INTO elevator_networks
                    (owner_uuid, owner_name, world_uuid, world_name, x, z, created_at)
                    VALUES ('%s', 'Vicevil', '%s', 'world', 4419, 1002, '2026-08-09T12:00:00Z')
                    """).formatted(owner, world));
        }

        try (ElevatorDatabase migrated = new ElevatorDatabase(file)) {
            NetworkRecord network = migrated.getNetwork(1L).orElseThrow();
            assertThat(network.ownerId()).isEqualTo(owner);
            assertThat(network.ownerName()).isEqualTo("Vicevil");
            assertThat(network.locked()).isFalse();
            assertThat(migrated.getMembers(network.id())).isEmpty();
        }
    }

    @Test
    void persistsCustomizationAccessTransferSearchDeletionAndAdministrativeHistory() throws Exception {
        Path file = temporaryDirectory.resolve("advanced.db");
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        UUID world = UUID.randomUUID();

        try (ElevatorDatabase database = new ElevatorDatabase(file)) {
            FloorRecord anchor = database.createFloor(owner, "Vicevil", location(world, 54),
                    "QUARTZ_BLOCK", BigDecimal.TEN, Instant.now());
            FloorRecord restricted = database.createFloor(owner, "Vicevil", location(world, 70),
                    "QUARTZ_BLOCK", BigDecimal.TEN, Instant.now());
            long networkId = anchor.networkId();
            assertThat(database.renameNetwork(networkId, "Clan de Pepito", owner, "Vicevil", Instant.now())).isTrue();
            assertThat(database.setNetworkIcon(networkId, "BEACON", owner, "Vicevil", Instant.now())).isTrue();
            assertThat(database.setFloorIcon(restricted.id(), "SPAWNER", owner, "Vicevil", Instant.now())).isTrue();
            assertThat(database.setFloorAccessMode(restricted.id(), AccessMode.PRIVATE,
                    owner, "Vicevil", Instant.now())).isTrue();
            assertThat(database.addMember(networkId, member, "Pepito", Instant.now(), owner, "Vicevil")).isTrue();

            assertThat(database.canAccessFloor(database.getFloor(restricted.id()).orElseThrow(), outsider)).isFalse();
            assertThat(database.canAccessFloor(database.getFloor(restricted.id()).orElseThrow(), member)).isTrue();
            assertThat(database.transferOwnership(networkId, member, "Pepito", owner, "Vicevil", Instant.now())).isTrue();

            NetworkRecord transferred = database.getNetwork(networkId).orElseThrow();
            assertThat(transferred.ownerId()).isEqualTo(member);
            assertThat(transferred.effectiveName()).isEqualTo("Clan de Pepito");
            assertThat(transferred.iconMaterial()).isEqualTo("BEACON");
            assertThat(database.isMember(networkId, owner)).isTrue();
            assertThat(database.isMember(networkId, member)).isFalse();
            assertThat(database.getFloor(restricted.id()).orElseThrow().iconMaterial()).isEqualTo("SPAWNER");
            assertThat(database.findNetworksByOwnerName("pEpItO")).singleElement()
                    .extracting(NetworkRecord::id).isEqualTo(networkId);
            assertThat(database.findNetworksByWorld("WORLD")).hasSize(1);
            assertThat(database.findNetworksByCoordinates("world", 4419, 1002)).hasSize(1);
            assertThat(database.getAuditRecords()).extracting("action")
                    .contains("NETWORK_CREATED", "FLOOR_CREATED", "NETWORK_RENAMED",
                            "NETWORK_ICON_CHANGED", "FLOOR_ICON_CHANGED", "FLOOR_ACCESS_CHANGED",
                            "MEMBER_ADDED", "OWNER_TRANSFERRED");

            assertThat(database.deleteNetwork(networkId, member, "Pepito", Instant.now())).isTrue();
            assertThat(database.getNetwork(networkId)).isEmpty();
            assertThat(database.getFloors(networkId)).isEmpty();
            assertThat(database.getMembers(networkId)).isEmpty();
            assertThat(database.getAuditRecords()).extracting("action").contains("NETWORK_DELETED");
        }
    }

    private FloorLocation location(UUID world, int y) {
        return new FloorLocation(world, "world", 4419, y, 1002);
    }
}
