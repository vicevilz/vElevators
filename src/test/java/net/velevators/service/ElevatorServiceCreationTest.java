package net.velevators.service;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.velevators.config.PluginSettings;
import net.velevators.data.ElevatorDatabase;
import net.velevators.model.FloorLocation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElevatorServiceCreationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void chargesExactlyOncePersistsFloorAndAppendsTransaction() throws Exception {
        TestContext context = context(temporaryDirectory.resolve("transactions.log"));
        when(context.economy.has(context.player, 10_000.0D)).thenReturn(true);
        when(context.economy.withdrawPlayer(context.player, 10_000.0D)).thenReturn(success(10_000.0D));

        try (context; MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(context.worldId)).thenReturn(context.world);

            ElevatorService.CreationResult result = context.service.createFloor(context.player, context.location);

            assertThat(result.status()).isEqualTo(ElevatorService.CreationStatus.SUCCESS);
            assertThat(result.paidAmount()).isEqualByComparingTo("10000.00");
            assertThat(context.database.findFloorAt(context.worldId, 4419, 54, 1002)).isPresent();
            assertThat(Files.readString(temporaryDirectory.resolve("transactions.log")))
                    .contains("Vicevil", "10000.00", "4419 54 1002", "Europe/Madrid");
            verify(context.economy).withdrawPlayer(context.player, 10_000.0D);
            verify(context.economy, never()).depositPlayer(context.player, 10_000.0D);
        }
    }

    @Test
    void insufficientFundsDoesNotWithdrawPersistOrLog() throws Exception {
        Path log = temporaryDirectory.resolve("insufficient.log");
        TestContext context = context(log);
        when(context.economy.has(context.player, 10_000.0D)).thenReturn(false);

        try (context; MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(context.worldId)).thenReturn(context.world);

            ElevatorService.CreationResult result = context.service.createFloor(context.player, context.location);

            assertThat(result.status()).isEqualTo(ElevatorService.CreationStatus.INSUFFICIENT_FUNDS);
            assertThat(context.database.findFloorAt(context.worldId, 4419, 54, 1002)).isEmpty();
            assertThat(log).doesNotExist();
            verify(context.economy, never()).withdrawPlayer(context.player, 10_000.0D);
            verify(context.economy, never()).depositPlayer(context.player, 10_000.0D);
        }
    }

    @Test
    void logFailureDeletesPersistedFloorAndRefundsCharge() throws Exception {
        Path blockedParent = temporaryDirectory.resolve("blocked-parent");
        Files.writeString(blockedParent, "not a directory");
        TestContext context = context(blockedParent.resolve("transactions.log"));
        when(context.economy.has(context.player, 10_000.0D)).thenReturn(true);
        when(context.economy.withdrawPlayer(context.player, 10_000.0D)).thenReturn(success(10_000.0D));
        when(context.economy.depositPlayer(context.player, 10_000.0D)).thenReturn(success(10_000.0D));

        try (context; MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(context.worldId)).thenReturn(context.world);

            ElevatorService.CreationResult result = context.service.createFloor(context.player, context.location);

            assertThat(result.status()).isEqualTo(ElevatorService.CreationStatus.PERSISTENCE_FAILURE);
            assertThat(context.database.findFloorAt(context.worldId, 4419, 54, 1002)).isEmpty();
            assertThat(context.database.getAuditRecords()).isEmpty();
            verify(context.economy).withdrawPlayer(context.player, 10_000.0D);
            verify(context.economy).depositPlayer(context.player, 10_000.0D);
        }
    }

    @Test
    void duplicateConfirmationCannotChargeTwice() throws Exception {
        TestContext context = context(temporaryDirectory.resolve("duplicate.log"));
        when(context.economy.has(context.player, 10_000.0D)).thenReturn(true);
        when(context.economy.withdrawPlayer(context.player, 10_000.0D)).thenReturn(success(10_000.0D));

        try (context; MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(context.worldId)).thenReturn(context.world);

            ElevatorService.CreationResult first = context.service.createFloor(context.player, context.location);
            ElevatorService.CreationResult second = context.service.createFloor(context.player, context.location);

            assertThat(first.status()).isEqualTo(ElevatorService.CreationStatus.SUCCESS);
            assertThat(second.status()).isEqualTo(ElevatorService.CreationStatus.ALREADY_REGISTERED);
            verify(context.economy).withdrawPlayer(context.player, 10_000.0D);
        }
    }

    private TestContext context(Path transactionLog) throws Exception {
        UUID worldId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        JavaPlugin plugin = mock(JavaPlugin.class);
        Economy economy = mock(Economy.class);
        Player player = mock(Player.class);
        World world = mock(World.class);
        Block top = mock(Block.class);
        Block redstone = mock(Block.class);
        Block airOne = mock(Block.class);
        Block airTwo = mock(Block.class);
        ElevatorDatabase database = new ElevatorDatabase(temporaryDirectory.resolve(UUID.randomUUID() + ".db"));
        PluginSettings settings = new PluginSettings(Material.QUARTZ_BLOCK, new BigDecimal("10000.00"),
                20, 60, 60, 60, 1_000L, true, true, Sound.ENTITY_PLAYER_LEVELUP,
                Sound.BLOCK_NOTE_BLOCK_BASS, Sound.ENTITY_ENDERMAN_TELEPORT);

        when(plugin.getLogger()).thenReturn(Logger.getLogger("ElevatorServiceCreationTest"));
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Vicevil");
        when(player.hasPermission("velevators.bypass.cost")).thenReturn(false);
        when(world.getBlockAt(4419, 54, 1002)).thenReturn(top);
        when(top.getType()).thenReturn(Material.QUARTZ_BLOCK);
        when(top.getRelative(0, -1, 0)).thenReturn(redstone);
        when(top.getRelative(0, 1, 0)).thenReturn(airOne);
        when(top.getRelative(0, 2, 0)).thenReturn(airTwo);
        when(redstone.getType()).thenReturn(Material.REDSTONE_BLOCK);
        when(airOne.isPassable()).thenReturn(true);
        when(airTwo.isPassable()).thenReturn(true);

        FloorLocation location = new FloorLocation(worldId, "world", 4419, 54, 1002);
        ElevatorService service = new ElevatorService(plugin, database, economy,
                new TransactionLogger(transactionLog), () -> settings);
        return new TestContext(worldId, world, player, economy, database, location, service);
    }

    private EconomyResponse success(double amount) {
        return new EconomyResponse(amount, 0.0D, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private record TestContext(UUID worldId, World world, Player player, Economy economy,
                               ElevatorDatabase database, FloorLocation location,
                               ElevatorService service) implements AutoCloseable {
        @Override
        public void close() throws java.sql.SQLException {
            database.close();
        }
    }
}
