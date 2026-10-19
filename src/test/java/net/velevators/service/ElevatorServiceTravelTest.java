package net.velevators.service;

import net.milkbowl.vault.economy.Economy;
import net.velevators.config.PluginSettings;
import net.velevators.data.ElevatorDatabase;
import net.velevators.model.FloorLocation;
import net.velevators.model.AccessMode;
import net.velevators.model.FloorRecord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElevatorServiceTravelTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void teleportsToTheNextHeightCenteredAndPreservesOrientation() throws Exception {
        try (TravelContext context = context(true); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(context.worldId)).thenReturn(context.world);
            when(context.player.teleport(org.mockito.ArgumentMatchers.any(Location.class),
                    org.mockito.ArgumentMatchers.eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenReturn(true);

            ElevatorService.TravelStatus result = context.service.travel(context.player, 1);

            assertThat(result).isEqualTo(ElevatorService.TravelStatus.SUCCESS);
            ArgumentCaptor<Location> destination = ArgumentCaptor.forClass(Location.class);
            verify(context.player).teleport(destination.capture(),
                    org.mockito.ArgumentMatchers.eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
            assertThat(destination.getValue().getX()).isEqualTo(4419.5D);
            assertThat(destination.getValue().getY()).isEqualTo(71.0D);
            assertThat(destination.getValue().getZ()).isEqualTo(1002.5D);
            assertThat(destination.getValue().getYaw()).isEqualTo(35.0F);
            assertThat(destination.getValue().getPitch()).isEqualTo(-10.0F);
        }
    }

    @Test
    void obstructedDestinationDoesNotTeleport() throws Exception {
        try (TravelContext context = context(false); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(context.worldId)).thenReturn(context.world);

            ElevatorService.TravelStatus result = context.service.travel(context.player, 1);

            assertThat(result).isEqualTo(ElevatorService.TravelStatus.UNSAFE_DESTINATION);
            verify(context.player, never()).teleport(org.mockito.ArgumentMatchers.any(Location.class),
                    org.mockito.ArgumentMatchers.any(PlayerTeleportEvent.TeleportCause.class));
        }
    }

    @Test
    void reportsMissingLowerFloorWithoutTeleporting() throws Exception {
        try (TravelContext context = context(true); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(context.worldId)).thenReturn(context.world);

            ElevatorService.TravelStatus result = context.service.travel(context.player, -1);

            assertThat(result).isEqualTo(ElevatorService.TravelStatus.NO_DESTINATION);
            verify(context.player, never()).teleport(org.mockito.ArgumentMatchers.any(Location.class),
                    org.mockito.ArgumentMatchers.any(PlayerTeleportEvent.TeleportCause.class));
        }
    }

    @Test
    void lockedNetworkRejectsPlayersWhoAreNeitherOwnerNorMember() throws Exception {
        try (TravelContext context = context(true, false); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(context.worldId)).thenReturn(context.world);
            context.database.setNetworkLocked(context.networkId, true);

            ElevatorService.TravelStatus result = context.service.travel(context.player, 1);

            assertThat(result).isEqualTo(ElevatorService.TravelStatus.LOCKED);
            verify(context.player, never()).teleport(org.mockito.ArgumentMatchers.any(Location.class),
                    org.mockito.ArgumentMatchers.any(PlayerTeleportEvent.TeleportCause.class));
        }
    }

    @Test
    void lockedNetworkAllowsAnAddedMemberToTravel() throws Exception {
        try (TravelContext context = context(true, false); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(context.worldId)).thenReturn(context.world);
            context.database.addMember(context.networkId, context.playerId, "Pepito", Instant.now());
            context.database.setNetworkLocked(context.networkId, true);
            when(context.player.teleport(org.mockito.ArgumentMatchers.any(Location.class),
                    org.mockito.ArgumentMatchers.eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenReturn(true);

            ElevatorService.TravelStatus result = context.service.travel(context.player, 1);

            assertThat(result).isEqualTo(ElevatorService.TravelStatus.SUCCESS);
        }
    }

    @Test
    void skipsARestrictedIntermediateFloorAndUsesTheNextAccessibleHeight() throws Exception {
        try (TravelContext context = context(true, false); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(context.worldId)).thenReturn(context.world);
            FloorRecord restricted = context.database.createFloor(context.ownerId, "Vicevil",
                    location(context.worldId, 62), "QUARTZ_BLOCK", BigDecimal.TEN, Instant.now());
            context.database.setFloorAccessMode(restricted.id(), AccessMode.PRIVATE,
                    context.ownerId, "Vicevil", Instant.now());

            Block restrictedTop = activeTop(context.world, 62);
            when(context.world.getBlockAt(4419, 62, 1002)).thenReturn(restrictedTop);
            when(context.player.teleport(org.mockito.ArgumentMatchers.any(Location.class),
                    org.mockito.ArgumentMatchers.eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenReturn(true);

            ElevatorService.TravelResult result = context.service.travelDetailed(context.player, 1);

            assertThat(result.status()).isEqualTo(ElevatorService.TravelStatus.SUCCESS);
            assertThat(result.destination()).isNotNull();
            assertThat(result.destination().y()).isEqualTo(70);
            ArgumentCaptor<Location> destination = ArgumentCaptor.forClass(Location.class);
            verify(context.player).teleport(destination.capture(),
                    org.mockito.ArgumentMatchers.eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
            assertThat(destination.getValue().getY()).isEqualTo(71.0D);
        }
    }

    @Test
    void memberCanUsePrivateFloor() throws Exception {
        try (TravelContext context = context(true, false); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(context.worldId)).thenReturn(context.world);
            context.database.addMember(context.networkId, context.playerId, "Pepito", Instant.now());
            FloorRecord target = context.database.getFloors(context.networkId).stream()
                    .filter(floor -> floor.y() == 70).findFirst().orElseThrow();
            context.database.setFloorAccessMode(target.id(), AccessMode.PRIVATE,
                    context.ownerId, "Vicevil", Instant.now());
            when(context.player.teleport(org.mockito.ArgumentMatchers.any(Location.class),
                    org.mockito.ArgumentMatchers.eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenReturn(true);

            assertThat(context.service.travel(context.player, 1)).isEqualTo(ElevatorService.TravelStatus.SUCCESS);
        }
    }

    @Test
    void ownerOnlyFloorRejectsAMember() throws Exception {
        try (TravelContext context = context(true, false); MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(context.worldId)).thenReturn(context.world);
            context.database.addMember(context.networkId, context.playerId, "Pepito", Instant.now());
            FloorRecord target = context.database.getFloors(context.networkId).stream()
                    .filter(floor -> floor.y() == 70).findFirst().orElseThrow();
            context.database.setFloorAccessMode(target.id(), AccessMode.OWNER_ONLY,
                    context.ownerId, "Vicevil", Instant.now());

            assertThat(context.service.travel(context.player, 1)).isEqualTo(ElevatorService.TravelStatus.LOCKED);
            verify(context.player, never()).teleport(org.mockito.ArgumentMatchers.any(Location.class),
                    org.mockito.ArgumentMatchers.any(PlayerTeleportEvent.TeleportCause.class));
        }
    }

    private TravelContext context(boolean destinationPassable) throws Exception {
        return context(destinationPassable, true);
    }

    private TravelContext context(boolean destinationPassable, boolean ownerAsPlayer) throws Exception {
        UUID worldId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID playerId = ownerAsPlayer ? ownerId : UUID.randomUUID();
        World world = mock(World.class);
        Player player = mock(Player.class);
        JavaPlugin plugin = mock(JavaPlugin.class);
        Economy economy = mock(Economy.class);
        ElevatorDatabase database = new ElevatorDatabase(temporaryDirectory.resolve(UUID.randomUUID() + ".db"));
        when(world.getUID()).thenReturn(worldId);
        long networkId = database.createFloor(ownerId, "Vicevil", location(worldId, 54), "QUARTZ_BLOCK",
                BigDecimal.TEN, Instant.now()).networkId();
        database.createFloor(ownerId, "Vicevil", location(worldId, 70), "QUARTZ_BLOCK",
                BigDecimal.TEN, Instant.now());

        Block currentTop = activeTop(world, 54);
        Block targetTop = activeTop(world, 70);
        Block playerFeet = mock(Block.class);
        Block targetFeet = mock(Block.class);
        Block targetHead = mock(Block.class);
        when(playerFeet.getRelative(0, -1, 0)).thenReturn(currentTop);
        when(targetFeet.isPassable()).thenReturn(destinationPassable);
        when(targetHead.isPassable()).thenReturn(true);
        when(world.getBlockAt(4419, 54, 1002)).thenReturn(currentTop);
        when(world.getBlockAt(4419, 55, 1002)).thenReturn(playerFeet);
        when(world.getBlockAt(4419, 70, 1002)).thenReturn(targetTop);
        when(world.getBlockAt(4419, 71, 1002)).thenReturn(targetFeet);
        when(world.getBlockAt(4419, 72, 1002)).thenReturn(targetHead);
        Location playerLocation = new Location(world, 4419.5D, 55.0D, 1002.5D, 35.0F, -10.0F);
        when(world.getBlockAt(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(playerFeet);
        when(player.getLocation()).thenReturn(playerLocation);
        when(player.getUniqueId()).thenReturn(playerId);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ElevatorServiceTravelTest"));

        PluginSettings settings = new PluginSettings(Material.QUARTZ_BLOCK, BigDecimal.TEN, 20,
                60, 60, 60, 1_000L, true, true, Sound.ENTITY_PLAYER_LEVELUP,
                Sound.BLOCK_NOTE_BLOCK_BASS, Sound.ENTITY_ENDERMAN_TELEPORT);
        ElevatorService service = new ElevatorService(plugin, database, economy,
                new TransactionLogger(temporaryDirectory.resolve("transactions.log")), () -> settings);
        return new TravelContext(worldId, world, ownerId, playerId, networkId, player, database, service);
    }

    private Block activeTop(World world, int y) {
        Block top = mock(Block.class);
        Block redstone = mock(Block.class);
        when(top.getType()).thenReturn(Material.QUARTZ_BLOCK);
        when(top.getWorld()).thenReturn(world);
        when(top.getX()).thenReturn(4419);
        when(top.getY()).thenReturn(y);
        when(top.getZ()).thenReturn(1002);
        when(top.getRelative(0, -1, 0)).thenReturn(redstone);
        when(redstone.getType()).thenReturn(Material.REDSTONE_BLOCK);
        return top;
    }

    private FloorLocation location(UUID worldId, int y) {
        return new FloorLocation(worldId, "world", 4419, y, 1002);
    }

    private record TravelContext(UUID worldId, World world, UUID ownerId, UUID playerId, long networkId,
                                 Player player, ElevatorDatabase database,
                                 ElevatorService service) implements AutoCloseable {
        @Override
        public void close() throws java.sql.SQLException {
            database.close();
        }
    }
}
