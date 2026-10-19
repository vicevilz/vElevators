package net.velevators.service;

import net.milkbowl.vault.economy.Economy;
import net.velevators.config.PluginSettings;
import net.velevators.data.ElevatorDatabase;
import net.velevators.model.FloorLocation;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElevatorServiceMemberTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void addsAndRemovesMemberIdempotentlyWithoutAllowingOwner() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        try (ElevatorDatabase database = new ElevatorDatabase(temporaryDirectory.resolve("members.db"))) {
            long networkId = database.createFloor(ownerId, "Vicevil",
                    new FloorLocation(worldId, "world", 4, 64, 8), "QUARTZ_BLOCK",
                    BigDecimal.TEN, Instant.now()).networkId();
            ElevatorService service = service(database);

            assertThat(service.addMember(networkId, ownerId, "Vicevil").status())
                    .isEqualTo(ElevatorService.MemberStatus.OWNER);
            assertThat(service.addMember(networkId, memberId, "Pepito").status())
                    .isEqualTo(ElevatorService.MemberStatus.SUCCESS);
            assertThat(service.addMember(networkId, memberId, "Pepito").status())
                    .isEqualTo(ElevatorService.MemberStatus.ALREADY_MEMBER);
            assertThat(database.isMember(networkId, memberId)).isTrue();

            assertThat(service.removeMember(networkId, memberId, "Pepito").status())
                    .isEqualTo(ElevatorService.MemberStatus.SUCCESS);
            assertThat(service.removeMember(networkId, memberId, "Pepito").status())
                    .isEqualTo(ElevatorService.MemberStatus.NOT_MEMBER);
            assertThat(service.removeMember(networkId, ownerId, "Vicevil").status())
                    .isEqualTo(ElevatorService.MemberStatus.OWNER);
        }
    }

    @Test
    void reportsMissingNetworkForBothMemberOperations() throws Exception {
        try (ElevatorDatabase database = new ElevatorDatabase(temporaryDirectory.resolve("missing.db"))) {
            ElevatorService service = service(database);
            UUID playerId = UUID.randomUUID();
            assertThat(service.addMember(999L, playerId, "Pepito").status())
                    .isEqualTo(ElevatorService.MemberStatus.NETWORK_NOT_FOUND);
            assertThat(service.removeMember(999L, playerId, "Pepito").status())
                    .isEqualTo(ElevatorService.MemberStatus.NETWORK_NOT_FOUND);
        }
    }

    private ElevatorService service(ElevatorDatabase database) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ElevatorServiceMemberTest"));
        PluginSettings settings = new PluginSettings(Material.QUARTZ_BLOCK, BigDecimal.TEN,
                20, 60, 60, 60, 1_000L, true, true, Sound.ENTITY_PLAYER_LEVELUP,
                Sound.BLOCK_NOTE_BLOCK_BASS, Sound.ENTITY_ENDERMAN_TELEPORT);
        return new ElevatorService(plugin, database, mock(Economy.class),
                new TransactionLogger(temporaryDirectory.resolve("transactions.log")), () -> settings);
    }
}
