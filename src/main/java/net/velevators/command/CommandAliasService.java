package net.velevators.command;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class CommandAliasService {
    private static final Pattern VALID_ALIAS = Pattern.compile("[a-z0-9][a-z0-9_-]*");
    private final JavaPlugin plugin;

    public CommandAliasService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public AliasResult configuredAliases() {
        List<String> configured = plugin.getConfig().getStringList("command.aliases");
        Set<String> accepted = new LinkedHashSet<>();
        List<String> rejected = new ArrayList<>();
        for (String rawAlias : configured) {
            String alias = rawAlias == null ? "" : rawAlias.trim().toLowerCase(Locale.ROOT);
            if (!VALID_ALIAS.matcher(alias).matches()
                    || alias.equals("velevators")
                    || alias.equals("velevatorsaction")) {
                rejected.add(rawAlias == null ? "null" : rawAlias);
            } else {
                accepted.add(alias);
            }
        }
        if (!rejected.isEmpty()) {
            plugin.getLogger().warning("Ignored invalid or reserved command aliases: "
                    + String.join(", ", rejected));
        }
        return new AliasResult(List.copyOf(accepted), rejected);
    }

    public record AliasResult(List<String> aliases, List<String> rejected) {
        public AliasResult {
            aliases = List.copyOf(aliases);
            rejected = List.copyOf(rejected);
        }
    }
}
