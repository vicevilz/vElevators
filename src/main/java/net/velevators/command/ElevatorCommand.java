package net.velevators.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.milkbowl.vault.economy.Economy;
import net.velevators.VElevatorsPlugin;
import net.velevators.config.MessageService;
import net.velevators.menu.MenuService;
import net.velevators.service.TransactionLogger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ElevatorCommand implements CommandExecutor, TabCompleter {
    private static final int TRANSACTIONS_PER_PAGE = 8;
    private static final UUID CONSOLE_UUID = new UUID(0L, 0L);
    private final VElevatorsPlugin plugin;
    private final MenuService menus;
    private final MessageService messages;
    private final TransactionLogger transactions;
    private final Economy economy;

    public ElevatorCommand(VElevatorsPlugin plugin, MenuService menus, MessageService messages,
                           TransactionLogger transactions, Economy economy) {
        this.plugin = plugin;
        this.menus = menus;
        this.messages = messages;
        this.transactions = transactions;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        return execute(sender, args);
    }

    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("transactions")) {
            return handleTransactions(sender, args);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("velevators.reload")) {
                messages.send(sender, "errors.no-permission");
                return true;
            }
            if (plugin.reloadPlugin()) {
                messages.send(sender, "admin.reloaded");
            } else {
                messages.send(sender, "admin.reload-failed");
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("adminlog")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "errors.players-only");
                return true;
            }
            if (!player.hasPermission("velevators.adminlog")) {
                messages.send(player, "errors.no-permission");
                return true;
            }
            menus.openAdminMain(player, 0);
            return true;
        }
        if (args.length != 0) {
            messages.send(sender, "errors.usage");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "errors.players-only");
            return true;
        }
        if (!player.hasPermission("velevators.use")) {
            messages.send(player, "errors.no-permission");
            return true;
        }
        menus.openMain(player);
        return true;
    }

    private boolean handleTransactions(CommandSender sender, String[] args) {
        if (!hasAnyTransactionPermission(sender)) {
            messages.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "transactions.usage");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> listTransactions(sender, args);
            case "total" -> showTransactionTotal(sender, args);
            case "collect" -> collectTransactions(sender, args);
            case "clear" -> clearTransactions(sender, args);
            default -> {
                messages.send(sender, "transactions.usage");
                yield true;
            }
        };
    }

    private boolean listTransactions(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "velevators.admin.transactions.list")) {
            return true;
        }
        if (args.length > 3) {
            messages.send(sender, "transactions.usage");
            return true;
        }
        int requestedPage = 1;
        if (args.length == 3) {
            try {
                requestedPage = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                messages.send(sender, "transactions.invalid-page");
                return true;
            }
        }
        try {
            sendTransactionPage(sender, transactions.snapshot(), requestedPage);
        } catch (IOException exception) {
            storageFailure(sender, "Could not list transactions.log", exception);
        }
        return true;
    }

    private boolean showTransactionTotal(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "velevators.admin.transactions.total")) {
            return true;
        }
        if (args.length != 2) {
            messages.send(sender, "transactions.usage");
            return true;
        }
        try {
            TransactionLogger.Snapshot snapshot = transactions.snapshot();
            messages.send(sender, "transactions.total", Map.of(
                    "amount", format(snapshot.total()),
                    "count", Integer.toString(snapshot.records().size())));
            warnInvalidLines(sender, snapshot.invalidLines().size());
        } catch (IOException exception) {
            storageFailure(sender, "Could not calculate the transactions.log total", exception);
        }
        return true;
    }

    private boolean collectTransactions(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "velevators.admin.transactions.collect")) {
            return true;
        }
        if (args.length != 2) {
            messages.send(sender, "transactions.usage");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "transactions.collect-players-only");
            return true;
        }
        TransactionLogger.CollectResult result = transactions.collect(economy, player, Instant.now());
        switch (result.status()) {
            case SUCCESS -> messages.send(player, "transactions.collected", Map.of(
                    "amount", format(result.amount()), "count", Integer.toString(result.count())));
            case EMPTY -> messages.send(player, "transactions.empty");
            case INVALID_LOG -> messages.send(player, "transactions.invalid-log");
            case ECONOMY_FAILURE -> {
                plugin.getLogger().warning("Vault rejected collect for " + player.getName() + ": " + result.error());
                messages.send(player, "transactions.economy-failure");
            }
            case STORAGE_FAILURE -> {
                plugin.getLogger().severe("Collect was rolled back after a file failure for " + player.getName()
                        + ": " + result.error());
                messages.send(player, "transactions.storage-failure");
            }
            case ROLLBACK_FAILURE -> {
                plugin.getLogger().severe("CRITICAL: collect could not be archived or rolled back for "
                        + player.getName() + ": " + result.error());
                messages.send(player, "transactions.rollback-failure");
            }
        }
        return true;
    }

    private boolean clearTransactions(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "velevators.admin.transactions.clear")) {
            return true;
        }
        if (args.length != 2) {
            messages.send(sender, "transactions.usage");
            return true;
        }
        UUID actorId = sender instanceof Player player ? player.getUniqueId() : CONSOLE_UUID;
        try {
            TransactionLogger.ClearResult result = transactions.clear(sender.getName(), actorId, Instant.now());
            messages.send(sender, "transactions.cleared", Map.of(
                    "count", Integer.toString(result.count()),
                    "amount", format(result.total())));
            warnInvalidLines(sender, result.invalidCount());
        } catch (IOException exception) {
            storageFailure(sender, "Could not archive and clear transactions.log", exception);
        }
        return true;
    }

    private void sendTransactionPage(CommandSender sender, TransactionLogger.Snapshot snapshot, int requestedPage) {
        List<TransactionLogger.TransactionRecord> records = new ArrayList<>(snapshot.records());
        Collections.reverse(records);
        int pages = Math.max(1, (records.size() + TRANSACTIONS_PER_PAGE - 1) / TRANSACTIONS_PER_PAGE);
        if (requestedPage < 1 || requestedPage > pages) {
            messages.send(sender, "transactions.invalid-page");
            return;
        }
        sender.sendMessage(messages.component("transactions.list-title"));
        sender.sendMessage(messages.component("transactions.list-summary", Map.of(
                "page", Integer.toString(requestedPage),
                "pages", Integer.toString(pages),
                "amount", format(snapshot.total()))));
        if (records.isEmpty()) {
            sender.sendMessage(messages.component("transactions.list-empty"));
        } else {
            int from = (requestedPage - 1) * TRANSACTIONS_PER_PAGE;
            int to = Math.min(from + TRANSACTIONS_PER_PAGE, records.size());
            for (int index = from; index < to; index++) {
                TransactionLogger.TransactionRecord record = records.get(index);
                int number = records.size() - index;
                Map<String, String> replacements = Map.of(
                        "number", Integer.toString(number),
                        "player", record.playerName(),
                        "amount", format(record.amount()),
                        "date", record.timestamp(),
                        "uuid", record.playerId().toString(),
                        "location", record.worldName() + " | " + record.x() + " "
                                + record.y() + " " + record.z());
                Component hover = messages.component("transactions.list-hover", replacements);
                sender.sendMessage(messages.component("transactions.list-entry", replacements)
                        .hoverEvent(HoverEvent.showText(hover)));
            }
        }
        warnInvalidLines(sender, snapshot.invalidLines().size());
        sender.sendMessage(navigation(requestedPage, pages));
    }

    private Component navigation(int page, int pages) {
        TextComponent.Builder builder = Component.text();
        if (page > 1) {
            builder.append(messages.component("transactions.previous")
                    .clickEvent(ClickEvent.runCommand("/velevators transactions list " + (page - 1)))
                    .hoverEvent(HoverEvent.showText(messages.component("transactions.page-hover",
                            Map.of("page", Integer.toString(page - 1))))));
        } else {
            builder.append(messages.component("transactions.previous-disabled"));
        }
        builder.append(messages.component("transactions.navigation-page", Map.of(
                "page", Integer.toString(page), "pages", Integer.toString(pages))));
        if (page < pages) {
            builder.append(messages.component("transactions.next")
                    .clickEvent(ClickEvent.runCommand("/velevators transactions list " + (page + 1)))
                    .hoverEvent(HoverEvent.showText(messages.component("transactions.page-hover",
                            Map.of("page", Integer.toString(page + 1))))));
        } else {
            builder.append(messages.component("transactions.next-disabled"));
        }
        return builder.build();
    }

    private void warnInvalidLines(CommandSender sender, int count) {
        if (count > 0) {
            messages.send(sender, "transactions.invalid-lines", Map.of("count", Integer.toString(count)));
        }
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        messages.send(sender, "errors.no-permission");
        return false;
    }

    private boolean hasAnyTransactionPermission(CommandSender sender) {
        return sender.hasPermission("velevators.admin.transactions.list")
                || sender.hasPermission("velevators.admin.transactions.collect")
                || sender.hasPermission("velevators.admin.transactions.total")
                || sender.hasPermission("velevators.admin.transactions.clear")
                || sender.hasPermission("velevators.admin.transactions.*");
    }

    private void storageFailure(CommandSender sender, String context, IOException exception) {
        plugin.getLogger().severe(context + ": " + exception.getMessage());
        messages.send(sender, "transactions.storage-failure");
    }

    private String format(BigDecimal amount) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(messages.javaLocale());
        return new DecimalFormat("#,##0.##", symbols).format(amount);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        return complete(sender, args);
    }

    public @NotNull List<String> complete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            addIfVisible(options, args[0], "reload", sender.hasPermission("velevators.reload"));
            addIfVisible(options, args[0], "adminlog", sender.hasPermission("velevators.adminlog"));
            addIfVisible(options, args[0], "transactions", hasAnyTransactionPermission(sender));
            return List.copyOf(options);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("transactions")) {
            List<String> options = new ArrayList<>();
            addIfVisible(options, args[1], "list",
                    sender.hasPermission("velevators.admin.transactions.list"));
            addIfVisible(options, args[1], "collect",
                    sender.hasPermission("velevators.admin.transactions.collect"));
            addIfVisible(options, args[1], "total",
                    sender.hasPermission("velevators.admin.transactions.total"));
            addIfVisible(options, args[1], "clear",
                    sender.hasPermission("velevators.admin.transactions.clear"));
            return List.copyOf(options);
        }
        return List.of();
    }

    private void addIfVisible(List<String> options, String input, String option, boolean visible) {
        if (visible && option.startsWith(input.toLowerCase(Locale.ROOT))) {
            options.add(option);
        }
    }
}
