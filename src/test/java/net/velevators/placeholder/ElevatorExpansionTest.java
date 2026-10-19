package net.velevators.placeholder;

import net.milkbowl.vault.economy.Economy;
import net.velevators.config.PluginSettings;
import net.velevators.data.ElevatorDatabase;
import net.velevators.model.FloorLocation;
import net.velevators.model.AccessMode;
import net.velevators.model.AuditRecord;
import net.velevators.model.MemberCandidateView;
import net.velevators.service.ElevatorService;
import net.velevators.service.TransactionLogger;
import net.velevators.session.SessionManager;
import net.velevators.util.SkinTextureIds;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElevatorExpansionTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesOwnerMembersPrivacyAndOwnerAdministrationToDeluxeMenus() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        try (ElevatorDatabase database = new ElevatorDatabase(temporaryDirectory.resolve("placeholders.db"))) {
            long networkId = database.createFloor(ownerId, "Vicevil",
                    new FloorLocation(worldId, "world", 4, 64, 8), "QUARTZ_BLOCK",
                    BigDecimal.TEN, Instant.now()).networkId();
            database.addMember(networkId, memberId, "Pepito", Instant.now());

            JavaPlugin plugin = mock(JavaPlugin.class);
            when(plugin.getLogger()).thenReturn(Logger.getLogger("ElevatorExpansionTest"));
            PluginSettings settings = new PluginSettings(Material.QUARTZ_BLOCK, BigDecimal.TEN,
                    20, 60, 60, 60, 1_000L, true, true, Sound.ENTITY_PLAYER_LEVELUP,
                    Sound.BLOCK_NOTE_BLOCK_BASS, Sound.ENTITY_ENDERMAN_TELEPORT);
            ElevatorService service = new ElevatorService(plugin, database, mock(Economy.class),
                    new TransactionLogger(temporaryDirectory.resolve("transactions.log")), () -> settings);
            SessionManager sessions = new SessionManager();
            sessions.getOrCreate(ownerId).managing(networkId, null, List.of(), 0);
            ElevatorExpansion expansion = new ElevatorExpansion(service, sessions, () -> settings);

            OfflinePlayer requester = mock(OfflinePlayer.class);
            Player online = mock(Player.class);
            when(requester.getUniqueId()).thenReturn(ownerId);
            when(requester.getPlayer()).thenReturn(online);
            when(online.hasPermission("velevators.manage")).thenReturn(true);

            assertThat(expansion.onRequest(requester, "network_owner")).isEqualTo("Vicevil");
            assertThat(expansion.onRequest(requester, "network_members")).isEqualTo("Pepito");
            assertThat(expansion.onRequest(requester, "network_member_count")).isEqualTo("1");
            assertThat(expansion.onRequest(requester, "network_locked")).isEqualTo("no");
            assertThat(expansion.onRequest(requester, "network_lock_status")).isEqualTo("Pública");
            assertThat(expansion.onRequest(requester, "network_access_texture"))
                    .isEqualTo("948bb4e4435c2c21d7f6183b38a2b7372653f3540be02259dd47bf52492de669");
            assertThat(expansion.onRequest(requester, "network_name")).isEqualTo("Red de Vicevil");
            assertThat(expansion.onRequest(requester, "network_icon")).isEqualTo("ENDER_CHEST");
            assertThat(expansion.onRequest(requester, "can_administer_network")).isEqualTo("yes");

            database.setNetworkLocked(networkId, true);
            assertThat(expansion.onRequest(requester, "network_locked")).isEqualTo("yes");
            assertThat(expansion.onRequest(requester, "network_lock_status")).isEqualTo("Privada");
            assertThat(expansion.onRequest(requester, "network_access_texture"))
                    .isEqualTo("fbf46bf39dfc718e7ea31db43477fc6b7dca588fe0a7891d481c5dda9e16f520");

            database.setNetworkAccessMode(networkId, AccessMode.OWNER_ONLY, ownerId, "Vicevil", Instant.now());
            assertThat(expansion.onRequest(requester, "network_access_mode")).isEqualTo("Solo propietario");
            assertThat(expansion.onRequest(requester, "network_access_texture"))
                    .isEqualTo("afd2400002ad9fbbbd0066941eb5b1a384ab9b0e48a178ee96e4d129a5208654");

            sessions.getOrCreate(ownerId).selectingMembers(true, List.of(
                    new MemberCandidateView(memberId, "Pepito", false, true, false, false)), 0);
            assertThat(expansion.onRequest(requester, "member_page")).isEqualTo("1");
            assertThat(expansion.onRequest(requester, "member_pages")).isEqualTo("1");
            assertThat(expansion.onRequest(requester, "online_player_count")).isEqualTo("1");
            assertThat(expansion.onRequest(requester, "member_player_0_exists")).isEqualTo("yes");
            assertThat(expansion.onRequest(requester, "member_player_0_name")).isEqualTo("Pepito");
            assertThat(expansion.onRequest(requester, "member_player_0_texture")).isEqualTo(SkinTextureIds.STEVE);
            assertThat(expansion.onRequest(requester, "member_player_0_relation")).isEqualTo("Miembro");
            assertThat(expansion.onRequest(requester, "member_player_0_relation_colored"))
                    .isEqualTo("&#68D878Miembro");
            assertThat(expansion.onRequest(requester, "member_player_0_member")).isEqualTo("yes");
            assertThat(expansion.onRequest(requester, "member_player_0_online")).isEqualTo("no");
            assertThat(expansion.onRequest(requester, "member_player_0_connection")).isEqualTo("Desconectado");
            assertThat(expansion.onRequest(requester, "member_player_1_exists")).isEqualTo("no");

            sessions.getOrCreate(ownerId).adminHistory(List.of(
                    new AuditRecord(3L, null, ownerId, "Admin", "TRANSACTIONS_COLLECTED",
                            "Retiró 10000 de 1 pagos pendientes.", "2026-08-24T12:00:00Z"),
                    new AuditRecord(2L, null, ownerId, "Admin", "TRANSACTIONS_CLEARED",
                            "Archivó 2 pagos sin entregar dinero.", "2026-08-24T11:00:00Z"),
                    new AuditRecord(1L, null, memberId, "Pepito", "TRANSACTION_PURCHASE",
                            "Importe: 500", "2026-08-24T10:00:00Z")), 0);
            assertThat(expansion.onRequest(requester, "audit_0_action")).isEqualTo("Dinero retirado");
            assertThat(expansion.onRequest(requester, "audit_1_action")).isEqualTo("Pagos archivados");
            assertThat(expansion.onRequest(requester, "audit_2_action")).isEqualTo("Pago de piso archivado");
        }
    }
}
