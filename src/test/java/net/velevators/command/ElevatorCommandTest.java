package net.velevators.command;

import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.velevators.VElevatorsPlugin;
import net.velevators.config.MessageService;
import net.velevators.menu.MenuService;
import net.velevators.model.FloorLocation;
import net.velevators.service.TransactionLogger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElevatorCommandTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void collectRequiresItsOwnPermissionPaysPlayerAndArchivesPendingTransactions() throws Exception {
        TestContext context = context("collect");
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.hasPermission("velevators.admin.transactions.collect")).thenReturn(true);
        when(player.getName()).thenReturn("Vicevil");
        when(player.getUniqueId()).thenReturn(playerId);
        when(context.economy.depositPlayer(player, 1000.0D)).thenReturn(success(1000.0D));
        append(context.transactions, "Comprador", new BigDecimal("1000.00"));

        context.command.onCommand(player, mock(Command.class), "velevators",
                new String[]{"transactions", "collect"});

        verify(context.economy).depositPlayer(player, 1000.0D);
        verify(context.messages).send(eq(player), eq("transactions.collected"),
                argThat((Map<String, String> values) -> values.get("amount").equals("1,000")
                        && values.get("count").equals("1")));
        assertThat(Files.readString(context.transactions.pendingFile())).isEmpty();
        assertThat(Files.readString(context.transactions.permanentFile()))
                .contains("PURCHASE |", "COLLECT |", "Vicevil");
    }

    @Test
    void transactionSubcommandsDoNotSharePermissions() throws Exception {
        TestContext context = context("permissions");
        Player player = mock(Player.class);
        when(player.hasPermission("velevators.admin.transactions.list")).thenReturn(true);
        append(context.transactions, "Comprador", new BigDecimal("100.00"));

        context.command.onCommand(player, mock(Command.class), "velevators",
                new String[]{"transactions", "collect"});

        verify(context.messages).send(player, "errors.no-permission");
        verify(context.economy, never()).depositPlayer(any(Player.class), any(Double.class));
        assertThat(Files.readString(context.transactions.pendingFile())).contains("100.00");
    }

    @Test
    void listIsPaginatedAndTotalUsesOnlyItsOwnPermission() throws Exception {
        TestContext context = context("list");
        Player player = mock(Player.class);
        when(player.hasPermission("velevators.admin.transactions.list")).thenReturn(true);
        when(player.hasPermission("velevators.admin.transactions.total")).thenReturn(true);
        for (int index = 0; index < 9; index++) {
            append(context.transactions, "Player" + index, new BigDecimal("100.00"));
        }

        context.command.onCommand(player, mock(Command.class), "elev",
                new String[]{"transactions", "list", "2"});
        context.command.onCommand(player, mock(Command.class), "elev",
                new String[]{"transactions", "total"});

        verify(player, atLeast(4)).sendMessage(any(Component.class));
        verify(context.messages).send(eq(player), eq("transactions.total"),
                argThat((Map<String, String> values) -> values.get("amount").equals("900")
                        && values.get("count").equals("9")));
    }

    @Test
    void clearWorksFromConsoleAndIsRecordedWithoutDepositingMoney() throws Exception {
        TestContext context = context("clear");
        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission("velevators.admin.transactions.clear")).thenReturn(true);
        when(console.getName()).thenReturn("CONSOLE");
        append(context.transactions, "Comprador", new BigDecimal("75.00"));

        context.command.onCommand(console, mock(Command.class), "velevators",
                new String[]{"transactions", "clear"});

        verify(context.messages).send(eq(console), eq("transactions.cleared"),
                argThat((Map<String, String> values) -> values.get("amount").equals("75")
                        && values.get("count").equals("1")));
        verify(context.economy, never()).depositPlayer(any(Player.class), any(Double.class));
        assertThat(Files.readString(context.transactions.permanentFile()))
                .contains("CLEAR |", "CONSOLE", "00000000-0000-0000-0000-000000000000");
    }

    @Test
    void tabCompletionOnlyShowsAuthorizedTransactionActions() {
        TestContext context = context("tab");
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("velevators.admin.transactions.list")).thenReturn(true);
        when(sender.hasPermission("velevators.admin.transactions.total")).thenReturn(true);

        List<String> roots = context.command.onTabComplete(sender, mock(Command.class), "velevators",
                new String[]{"t"});
        List<String> actions = context.command.onTabComplete(sender, mock(Command.class), "velevators",
                new String[]{"transactions", ""});

        assertThat(roots).containsExactly("transactions");
        assertThat(actions).containsExactly("list", "total");
    }

    private TestContext context(String name) {
        VElevatorsPlugin plugin = mock(VElevatorsPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ElevatorCommandTest-" + name));
        MenuService menus = mock(MenuService.class);
        MessageService messages = mock(MessageService.class);
        when(messages.javaLocale()).thenReturn(Locale.US);
        when(messages.component(anyString())).thenReturn(Component.empty());
        when(messages.component(anyString(), any())).thenReturn(Component.empty());
        Economy economy = mock(Economy.class);
        TransactionLogger transactions = new TransactionLogger(
                temporaryDirectory.resolve(name + "-transactions.log"),
                temporaryDirectory.resolve(name + "-transactions_logs.log"));
        ElevatorCommand command = new ElevatorCommand(plugin, menus, messages, transactions, economy);
        return new TestContext(command, messages, economy, transactions);
    }

    private void append(TransactionLogger transactions, String player, BigDecimal amount) throws Exception {
        transactions.append(Instant.parse("2026-08-24T12:00:00Z"), player, UUID.randomUUID(), amount,
                new FloorLocation(UUID.randomUUID(), "world", 4, 64, 8));
    }

    private EconomyResponse success(double amount) {
        return new EconomyResponse(amount, 0.0D, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private record TestContext(ElevatorCommand command, MessageService messages, Economy economy,
                               TransactionLogger transactions) {
    }
}
