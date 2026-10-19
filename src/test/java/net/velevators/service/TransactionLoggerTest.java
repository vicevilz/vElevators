package net.velevators.service;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.velevators.model.FloorLocation;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransactionLoggerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void appendsMadridTimestampAndRequiredAuditFields() throws Exception {
        Path file = temporaryDirectory.resolve("transactions.log");
        TransactionLogger logger = new TransactionLogger(file);
        UUID playerId = UUID.randomUUID();
        FloorLocation location = new FloorLocation(UUID.randomUUID(), "world", 4419, 54, 1002);

        logger.append(Instant.parse("2026-01-15T12:30:00Z"), "Vicevil", playerId,
                new BigDecimal("10000.00"), location);
        logger.append(Instant.parse("2026-07-15T12:30:00Z"), "Vice|vil", playerId,
                new BigDecimal("500.00"), location);

        assertThat(Files.readAllLines(file))
                .containsExactly(
                        "15/01/2026 13:30 Europe/Madrid | Vicevil | " + playerId + " | 10000.00 | world | 4419 54 1002",
                        "15/07/2026 14:30 Europe/Madrid | Vice_vil | " + playerId + " | 500.00 | world | 4419 54 1002"
                );
        TransactionLogger.Snapshot snapshot = logger.snapshot();
        assertThat(snapshot.records()).hasSize(2);
        assertThat(snapshot.total()).isEqualByComparingTo("10500.00");
        assertThat(snapshot.invalidLines()).isEmpty();
    }

    @Test
    void collectDepositsTotalArchivesEveryPurchaseAndClearsPendingLog() throws Exception {
        Path pending = temporaryDirectory.resolve("transactions.log");
        Path permanent = temporaryDirectory.resolve("transactions_logs.log");
        TransactionLogger logger = new TransactionLogger(pending, permanent);
        UUID playerId = UUID.randomUUID();
        FloorLocation location = new FloorLocation(UUID.randomUUID(), "world", 4, 64, 8);
        logger.append(Instant.parse("2026-08-15T10:00:00Z"), "Comprador", UUID.randomUUID(),
                new BigDecimal("10000.00"), location);
        logger.append(Instant.parse("2026-08-15T11:00:00Z"), "Otro", UUID.randomUUID(),
                new BigDecimal("500.00"), location);
        Economy economy = mock(Economy.class);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Vicevil");
        when(player.getUniqueId()).thenReturn(playerId);
        when(economy.depositPlayer(player, 10_500.0D)).thenReturn(success(10_500.0D));

        TransactionLogger.CollectResult result = logger.collect(economy, player,
                Instant.parse("2026-08-15T12:00:00Z"));

        assertThat(result.status()).isEqualTo(TransactionLogger.CollectStatus.SUCCESS);
        assertThat(result.amount()).isEqualByComparingTo("10500.00");
        assertThat(result.count()).isEqualTo(2);
        assertThat(Files.readString(pending)).isEmpty();
        assertThat(Files.readAllLines(permanent))
                .hasSize(3)
                .allSatisfy(line -> assertThat(line).doesNotContain("UNPARSED"));
        assertThat(Files.readString(permanent)).contains("PURCHASE |", "COLLECT |", "Vicevil", "10500.00 | 2");
        assertThat(logger.permanentAuditRecords())
                .extracting(record -> record.action())
                .containsExactly("TRANSACTIONS_COLLECTED", "TRANSACTION_PURCHASE", "TRANSACTION_PURCHASE");
        verify(economy).depositPlayer(player, 10_500.0D);
        verify(economy, never()).withdrawPlayer(player, 10_500.0D);
    }

    @Test
    void clearArchivesWithoutPayingAndRecordsAdministratorEvenWhenEmpty() throws Exception {
        Path pending = temporaryDirectory.resolve("clear-transactions.log");
        Path permanent = temporaryDirectory.resolve("clear-transactions_logs.log");
        TransactionLogger logger = new TransactionLogger(pending, permanent);
        UUID adminId = UUID.randomUUID();
        FloorLocation location = new FloorLocation(UUID.randomUUID(), "world", 1, 2, 3);
        logger.append(Instant.parse("2026-08-15T10:00:00Z"), "Comprador", UUID.randomUUID(),
                new BigDecimal("250.00"), location);

        TransactionLogger.ClearResult first = logger.clear("Admin", adminId,
                Instant.parse("2026-08-15T12:00:00Z"));
        TransactionLogger.ClearResult second = logger.clear("Admin", adminId,
                Instant.parse("2026-08-15T13:00:00Z"));

        assertThat(first.count()).isEqualTo(1);
        assertThat(first.total()).isEqualByComparingTo("250.00");
        assertThat(second.count()).isZero();
        assertThat(Files.readString(pending)).isEmpty();
        assertThat(logger.permanentAuditRecords())
                .extracting(record -> record.action())
                .containsExactly("TRANSACTIONS_CLEARED", "TRANSACTIONS_CLEARED", "TRANSACTION_PURCHASE");
    }

    @Test
    void rejectedDepositLeavesPendingTransactionsUntouched() throws Exception {
        Path pending = temporaryDirectory.resolve("rejected.log");
        TransactionLogger logger = new TransactionLogger(pending);
        FloorLocation location = new FloorLocation(UUID.randomUUID(), "world", 1, 2, 3);
        logger.append(Instant.parse("2026-08-15T10:00:00Z"), "Comprador", UUID.randomUUID(),
                new BigDecimal("100.00"), location);
        Economy economy = mock(Economy.class);
        Player player = mock(Player.class);
        when(economy.depositPlayer(player, 100.0D)).thenReturn(failure(100.0D, "rechazado"));

        TransactionLogger.CollectResult result = logger.collect(economy, player, Instant.now());

        assertThat(result.status()).isEqualTo(TransactionLogger.CollectStatus.ECONOMY_FAILURE);
        assertThat(Files.readString(pending)).contains("100.00");
        assertThat(logger.permanentFile()).doesNotExist();
    }

    @Test
    void malformedPendingLineBlocksCollectionButClearPreservesItPermanently() throws Exception {
        Path pending = temporaryDirectory.resolve("malformed.log");
        Path permanent = temporaryDirectory.resolve("malformed-archive.log");
        Files.writeString(pending, "línea dañada" + System.lineSeparator());
        TransactionLogger logger = new TransactionLogger(pending, permanent);
        Economy economy = mock(Economy.class);
        Player player = mock(Player.class);

        TransactionLogger.CollectResult collect = logger.collect(economy, player, Instant.now());
        TransactionLogger.ClearResult clear = logger.clear("Admin", UUID.randomUUID(), Instant.now());

        assertThat(collect.status()).isEqualTo(TransactionLogger.CollectStatus.INVALID_LOG);
        verifyNoInteractions(economy);
        assertThat(clear.invalidCount()).isEqualTo(1);
        assertThat(Files.readString(permanent)).contains("UNPARSED | línea dañada", "CLEAR |");
        assertThat(Files.readString(pending)).isEmpty();
    }

    @Test
    void archiveFailureWithdrawsTheDepositAndKeepsPendingLog() throws Exception {
        Path pending = temporaryDirectory.resolve("rollback.log");
        Path blockedParent = temporaryDirectory.resolve("blocked");
        Files.writeString(blockedParent, "not a directory");
        TransactionLogger logger = new TransactionLogger(pending, blockedParent.resolve("transactions_logs.log"));
        FloorLocation location = new FloorLocation(UUID.randomUUID(), "world", 1, 2, 3);
        logger.append(Instant.parse("2026-08-15T10:00:00Z"), "Comprador", UUID.randomUUID(),
                new BigDecimal("100.00"), location);
        Economy economy = mock(Economy.class);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Admin");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(economy.depositPlayer(player, 100.0D)).thenReturn(success(100.0D));
        when(economy.withdrawPlayer(player, 100.0D)).thenReturn(success(100.0D));

        TransactionLogger.CollectResult result = logger.collect(economy, player, Instant.now());

        assertThat(result.status()).isEqualTo(TransactionLogger.CollectStatus.STORAGE_FAILURE);
        assertThat(Files.readString(pending)).contains("100.00");
        verify(economy).depositPlayer(player, 100.0D);
        verify(economy).withdrawPlayer(player, 100.0D);
    }

    private EconomyResponse success(double amount) {
        return new EconomyResponse(amount, 0.0D, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse failure(double amount, String error) {
        return new EconomyResponse(amount, 0.0D, EconomyResponse.ResponseType.FAILURE, error);
    }
}
