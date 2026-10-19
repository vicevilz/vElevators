package net.velevators.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.velevators.config.MessageService;
import net.velevators.config.PluginSettings;
import net.velevators.menu.MenuService;
import net.velevators.service.ElevatorService;
import net.velevators.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RenameListenerTest {
    @Test
    void memberInputCancelsPublicChatAndSchedulesPrivateProcessing() {
        Context context = context();
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(event.getPlayer()).thenReturn(context.player);
        when(event.message()).thenReturn(Component.text("Pepito"));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            context.listener.onChat(event);

            verify(event).setCancelled(true);
            verify(scheduler).runTask(eq(context.plugin), any(Runnable.class));
        }
    }

    @Test
    void cancelarStopsMemberInputAndReopensTheSameManagementPage() {
        Context context = context();
        PlayerCommandPreprocessEvent event = mock(PlayerCommandPreprocessEvent.class);
        when(event.getMessage()).thenReturn("/cancelar");
        when(event.getPlayer()).thenReturn(context.player);

        context.listener.onCommand(event);

        verify(event).setCancelled(true);
        verify(context.messages).send(context.player, "members.cancelled");
        verify(context.menus).openManagement(context.player, 12L, 7L, 0);
    }

    private Context context() {
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        JavaPlugin plugin = mock(JavaPlugin.class);
        ElevatorService elevators = mock(ElevatorService.class);
        MenuService menus = mock(MenuService.class);
        MessageService messages = mock(MessageService.class);
        PluginSettings settings = mock(PluginSettings.class);
        SessionManager sessions = new SessionManager();
        sessions.getOrCreate(playerId).managing(12L, 7L, List.of(), 0);
        sessions.getOrCreate(playerId).addingMember(60);
        RenameListener listener = new RenameListener(plugin, elevators, menus, sessions, messages, () -> settings);
        return new Context(plugin, player, menus, messages, listener);
    }

    private record Context(JavaPlugin plugin, Player player, MenuService menus,
                           MessageService messages, RenameListener listener) {
    }
}
