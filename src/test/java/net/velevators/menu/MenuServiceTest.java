package net.velevators.menu;

import net.velevators.config.MessageService;
import net.velevators.data.ElevatorDatabase;
import net.velevators.model.FloorLocation;
import net.velevators.model.MemberCandidateView;
import net.velevators.service.TransactionLogger;
import net.velevators.session.PlayerSession;
import net.velevators.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class MenuServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void removalSelectorLoadsEveryPersistedMemberAndMarksConnectionState() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID onlineMemberId = UUID.randomUUID();
        UUID offlineMemberId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        try (ElevatorDatabase database = new ElevatorDatabase(temporaryDirectory.resolve("menu.db"))) {
            long networkId = database.createFloor(ownerId, "Vicevil",
                    new FloorLocation(worldId, "world", 4, 64, 8), "QUARTZ_BLOCK",
                    BigDecimal.TEN, Instant.now()).networkId();
            database.addMember(networkId, onlineMemberId, "Zeta", Instant.now());
            database.addMember(networkId, offlineMemberId, "Alpha", Instant.now());

            JavaPlugin plugin = mock(JavaPlugin.class);
            when(plugin.getLogger()).thenReturn(Logger.getLogger("MenuServiceTest"));
            Player owner = mock(Player.class);
            when(owner.getUniqueId()).thenReturn(ownerId);
            when(owner.hasPermission("velevators.manage")).thenReturn(true);
            Player onlineMember = mock(Player.class);
            when(onlineMember.isOnline()).thenReturn(true);
            SessionManager sessions = new SessionManager();
            sessions.getOrCreate(ownerId).managing(networkId, null, List.of(), 0);
            MenuService menus = new MenuService(plugin, database, sessions, mock(MessageService.class),
                    candidates -> CompletableFuture.completedFuture(candidates),
                    new TransactionLogger(temporaryDirectory.resolve("transactions.log")));
            BukkitScheduler scheduler = mock(BukkitScheduler.class);

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
                bukkit.when(() -> Bukkit.getPlayer(onlineMemberId)).thenReturn(onlineMember);
                bukkit.when(() -> Bukkit.getPlayer(offlineMemberId)).thenReturn(null);

                assertThat(menus.openMemberSelection(owner, false, 0)).isTrue();
            }

            PlayerSession session = sessions.get(ownerId).orElseThrow();
            assertThat(session.memberCandidates()).extracting(MemberCandidateView::playerName)
                    .containsExactly("Alpha", "Zeta");
            assertThat(session.memberCandidates()).extracting(MemberCandidateView::connectionStatus)
                    .containsExactly("Desconectado", "Conectado");
            assertThat(session.memberCandidates()).allMatch(MemberCandidateView::member);
        }
    }

    @Test
    void administrativeHistoryCombinesDatabaseAuditWithPermanentTransactionLog() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        try (ElevatorDatabase database = new ElevatorDatabase(temporaryDirectory.resolve("admin-history.db"))) {
            database.createFloor(ownerId, "Vicevil", new FloorLocation(worldId, "world", 4, 64, 8),
                    "QUARTZ_BLOCK", BigDecimal.TEN, Instant.parse("2026-08-24T10:00:00Z"));
            TransactionLogger transactions = new TransactionLogger(
                    temporaryDirectory.resolve("transactions.log"),
                    temporaryDirectory.resolve("transactions_logs.log"));
            transactions.append(Instant.parse("2026-08-24T11:00:00Z"), "Comprador", UUID.randomUUID(),
                    new BigDecimal("500.00"), new FloorLocation(worldId, "world", 4, 70, 8));
            transactions.clear("Admin", UUID.randomUUID(), Instant.parse("2026-08-24T12:00:00Z"));

            JavaPlugin plugin = mock(JavaPlugin.class);
            when(plugin.getLogger()).thenReturn(Logger.getLogger("MenuServiceAdminHistoryTest"));
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(ownerId);
            SessionManager sessions = new SessionManager();
            MenuService menus = new MenuService(plugin, database, sessions, mock(MessageService.class),
                    candidates -> CompletableFuture.completedFuture(candidates), transactions);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
                assertThat(menus.openAdminHistory(player, 0)).isTrue();
            }

            assertThat(sessions.get(ownerId).orElseThrow().auditRecords())
                    .extracting(record -> record.action())
                    .contains("NETWORK_CREATED", "TRANSACTION_PURCHASE", "TRANSACTIONS_CLEARED");
        }
    }
}
