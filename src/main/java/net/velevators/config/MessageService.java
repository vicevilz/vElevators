package net.velevators.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.velevators.util.ColorCodes;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MessageService {
    public static final String DEFAULT_LOCALE = "en_US";
    public static final List<String> SUPPORTED_LOCALES = List.of(
            "en_US", "es_ES", "de_DE", "fr_FR", "pt_BR", "it_IT", "pl_PL", "ru_RU",
            "tr_TR", "zh_CN", "ja_JP", "nl_NL", "cs_CZ", "id_ID", "ko_KR");
    private static final Set<String> SUPPORTED = Set.copyOf(SUPPORTED_LOCALES);
    private final JavaPlugin plugin;
    private volatile YamlConfiguration messages;
    private volatile String locale = DEFAULT_LOCALE;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public synchronized void reload() {
        installMissingLocales();
        String configured = plugin.getConfig().getString("locale", DEFAULT_LOCALE).trim();
        if (!SUPPORTED.contains(configured)) {
            plugin.getLogger().warning("Unsupported locale '" + configured + "'. Falling back to "
                    + DEFAULT_LOCALE + ". Supported locales: " + String.join(", ", SUPPORTED_LOCALES));
            configured = DEFAULT_LOCALE;
        }
        locale = configured;
        File selectedFile = new File(plugin.getDataFolder(), "lang/" + locale + ".yml");
        messages = YamlConfiguration.loadConfiguration(selectedFile);
        messages.setDefaults(loadBundledLocale(locale));
    }

    private void installMissingLocales() {
        for (String supported : SUPPORTED_LOCALES) {
            String path = "lang/" + supported + ".yml";
            File file = new File(plugin.getDataFolder(), path);
            if (!file.exists()) {
                plugin.saveResource(path, false);
            }
        }
    }

    private YamlConfiguration loadBundledLocale(String selectedLocale) {
        YamlConfiguration english = readResource(DEFAULT_LOCALE);
        if (selectedLocale.equals(DEFAULT_LOCALE)) {
            return english;
        }
        YamlConfiguration selected = readResource(selectedLocale);
        for (String key : english.getKeys(true)) {
            if (!english.isConfigurationSection(key) && !selected.contains(key)) {
                selected.set(key, english.get(key));
            }
        }
        return selected;
    }

    private YamlConfiguration readResource(String selectedLocale) {
        String path = "lang/" + selectedLocale + ".yml";
        try (InputStream resource = plugin.getResource(path)) {
            if (resource == null) {
                throw new IllegalStateException("Missing bundled language file: " + path);
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load bundled language file: " + path, exception);
        }
    }

    public String locale() {
        return locale;
    }

    public Locale javaLocale() {
        return Locale.forLanguageTag(locale.replace('_', '-'));
    }

    public String text(String key, Map<String, String> replacements) {
        String prefix = messages.getString("prefix", "");
        String value = messages.getString(key, key);
        String result = value.replace("{prefix}", prefix);
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            result = result.replace("{" + replacement.getKey() + "}", replacement.getValue());
        }
        return ColorCodes.colorize(result);
    }

    public String text(String key) {
        return text(key, Map.of());
    }

    public Component component(String key, Map<String, String> replacements) {
        return LegacyComponentSerializer.legacySection().deserialize(text(key, replacements));
    }

    public Component component(String key) {
        return component(key, Map.of());
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(text(key));
    }

    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        sender.sendMessage(text(key, replacements));
    }
}
