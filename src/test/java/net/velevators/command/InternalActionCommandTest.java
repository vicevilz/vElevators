package net.velevators.command;

import net.velevators.config.MessageService;
import net.velevators.config.PluginSettings;
import net.velevators.data.ElevatorDatabase;
import net.velevators.menu.MenuService;
import net.velevators.model.MemberCandidateView;
import net.velevators.model.NetworkRecord;
import net.velevators.service.ElevatorService;
import net.velevators.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalActionCommandTest {
    @Test
    void addMemberActionOpensOnlinePlayerSelectorForTheOwner() throws Exception {
        TestContext context = context();
        context.sessions.getOrCreate(context.ownerId).managing(12L, 7L, List.of(), 0);

        context.command.onCommand(context.owner, mock(Command.class), "velevatorsaction",
                new String[]{"addmember"});

        verify(context.menus).openMemberSelection(context.owner, true, 0);
    }

    @Test
    void sessionBoundHeadSelectionAddsTheSnapshottedOnlinePlayerAndReturnsToManagement() throws Exception {
        TestContext context = context();
        UUID targetId = UUID.randomUUID();
        Player target = mock(Player.class);
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Pepito");
        when(target.isOnline()).thenReturn(true);
        context.sessions.getOrCreate(context.ownerId).managing(12L, 7L, List.of(), 0);
        context.sessions.getOrCreate(context.ownerId).selectingMembers(true,
                List.of(new MemberCandidateView(targetId, "Pepito", false, false, false, true)), 0);
        when(context.elevators.addMember(12L, targetId, "Pepito", context.ownerId, "Vicevil"))
                .thenReturn(new ElevatorService.MemberResult(ElevatorService.MemberStatus.SUCCESS, "Pepito"));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(targetId)).thenReturn(target);
            context.command.onCommand(context.owner, mock(Command.class), "velevatorsaction",
                    new String[]{"memberselect", "0"});
        }

        verify(context.elevators).addMember(12L, targetId, "Pepito", context.ownerId, "Vicevil");
        verify(context.menus).openManagement(context.owner, 12L, 7L, 0);
    }

    @Test
    void sessionBoundHeadSelectionRemovesAnOfflinePersistedMember() throws Exception {
        TestContext context = context();
        UUID targetId = UUID.randomUUID();
        context.sessions.getOrCreate(context.ownerId).managing(12L, 7L, List.of(), 0);
        context.sessions.getOrCreate(context.ownerId).selectingMembers(false,
                List.of(new MemberCandidateView(targetId, "Pepito", false, true, false, false)), 0);
        when(context.elevators.removeMember(12L, targetId, "Pepito", context.ownerId, "Vicevil"))
                .thenReturn(new ElevatorService.MemberResult(ElevatorService.MemberStatus.SUCCESS, "Pepito"));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(targetId)).thenReturn(null);
            context.command.onCommand(context.owner, mock(Command.class), "velevatorsaction",
                    new String[]{"memberselect", "0"});
        }

        verify(context.elevators).removeMember(12L, targetId, "Pepito", context.ownerId, "Vicevil");
        verify(context.menus).openManagement(context.owner, 12L, 7L, 0);
    }

    @Test
    void memberSelectionRejectsSlotsThatWereNotInTheSessionSnapshot() throws Exception {
        TestContext context = context();
        context.sessions.getOrCreate(context.ownerId).managing(12L, 7L, List.of(), 0);
        context.sessions.getOrCreate(context.ownerId).selectingMembers(false, List.of(), 0);

        context.command.onCommand(context.owner, mock(Command.class), "velevatorsaction",
                new String[]{"memberselect", "0"});

        verify(context.elevators, never()).removeMember(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(UUID.class),
                org.mockito.ArgumentMatchers.anyString());
    }

    private TestContext context() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        JavaPlugin plugin = mock(JavaPlugin.class);
        ElevatorService elevators = mock(ElevatorService.class);
        ElevatorDatabase database = mock(ElevatorDatabase.class);
        MenuService menus = mock(MenuService.class);
        SessionManager sessions = new SessionManager();
        MessageService messages = mock(MessageService.class);
        Player owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(ownerId);
        when(owner.getName()).thenReturn("Vicevil");
        when(owner.hasPermission("velevators.manage")).thenReturn(true);
        when(elevators.database()).thenReturn(database);
        when(database.getNetwork(12L)).thenReturn(Optional.of(new NetworkRecord(
                12L, ownerId, "Vicevil", worldId, "world", 4, 8, 7L, false, "2026-08-09T12:00:00Z")));
        PluginSettings settings = new PluginSettings(Material.QUARTZ_BLOCK, BigDecimal.TEN,
                20, 60, 60, 60, 1_000L, true, true, Sound.ENTITY_PLAYER_LEVELUP,
                Sound.BLOCK_NOTE_BLOCK_BASS, Sound.ENTITY_ENDERMAN_TELEPORT);
        InternalActionCommand command = new InternalActionCommand(
                plugin, elevators, menus, sessions, messages, () -> settings);
        return new TestContext(ownerId, owner, elevators, menus, sessions, command);
    }

    private record TestContext(UUID ownerId, Player owner, ElevatorService elevators,
                               MenuService menus, SessionManager sessions,
                               InternalActionCommand command) {
    }
}
