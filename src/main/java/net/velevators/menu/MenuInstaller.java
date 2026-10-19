package net.velevators.menu;

import net.velevators.config.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MenuInstaller {
    private static final List<MenuFile> MENUS = createMenus();
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private final JavaPlugin plugin;

    public MenuInstaller(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean installForLocale(String activeLocale) throws IOException {
        if (!MessageService.SUPPORTED_LOCALES.contains(activeLocale)) {
            throw new IllegalArgumentException("Unsupported menu locale: " + activeLocale);
        }
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        Path deluxeFolder = pluginsFolder.toPath().resolve("DeluxeMenus");
        Path targetFolder = deluxeFolder.resolve("gui_menus").resolve("vElevators");
        Files.createDirectories(targetFolder);

        boolean changed = false;
        for (String locale : MessageService.SUPPORTED_LOCALES) {
            Path localeFolder = targetFolder.resolve(locale);
            Files.createDirectories(localeFolder);
            for (MenuFile menu : MENUS) {
                Path target = localeFolder.resolve(menu.fileName());
                if (Files.notExists(target)) {
                    String resourcePath = "deluxemenus/" + locale + "/" + menu.fileName();
                    try (InputStream resource = plugin.getResource(resourcePath)) {
                        if (resource == null) {
                            throw new IOException("Missing bundled DeluxeMenus resource: " + resourcePath);
                        }
                        Files.copy(resource, target);
                        changed = true;
                    }
                }
            }
        }

        Path config = deluxeFolder.resolve("config.yml");
        String original = Files.exists(config) ? Files.readString(config, StandardCharsets.UTF_8) : "";
        String updated = DeluxeMenusConfigEditor.upsertMenus(original, activeMenus(activeLocale));
        if (!updated.equals(original.replace("\r\n", "\n"))) {
            Files.createDirectories(deluxeFolder);
            if (Files.exists(config)) {
                String stamp = BACKUP_FORMAT.format(LocalDateTime.now(ZoneId.of("Europe/Madrid")));
                Files.copy(config, deluxeFolder.resolve("config.yml.velevators-backup-" + stamp),
                        StandardCopyOption.COPY_ATTRIBUTES);
            }
            Path temporary = deluxeFolder.resolve("config.yml.velevators.tmp");
            Files.writeString(temporary, updated, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, config, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temporary, config, StandardCopyOption.REPLACE_EXISTING);
            }
            changed = true;
        }
        return changed;
    }

    public void reloadDeluxeMenus() {
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dm reload"), 2L);
    }

    static Map<String, String> activeMenus(String locale) {
        Map<String, String> active = new LinkedHashMap<>();
        for (MenuFile menu : MENUS) {
            active.put(menu.id(), "vElevators/" + locale + "/" + menu.fileName());
        }
        return Collections.unmodifiableMap(active);
    }

    private static List<MenuFile> createMenus() {
        List<MenuFile> menus = new ArrayList<>();
        menus.add(new MenuFile(MenuService.MAIN_MENU, "main.yml"));
        menus.add(new MenuFile(MenuService.MANAGE_MENU, "manage.yml"));
        menus.add(new MenuFile(MenuService.CONFIRM_CREATE_MENU, "confirm-create.yml"));
        menus.add(new MenuFile(MenuService.CONFIRM_DELETE_MENU, "confirm-delete.yml"));
        menus.add(new MenuFile(MenuService.ADD_MEMBER_MENU, "add-member.yml"));
        menus.add(new MenuFile(MenuService.REMOVE_MEMBER_MENU, "remove-member.yml"));
        menus.add(new MenuFile(MenuService.NETWORK_MEMBERS_MENU, "network-members.yml"));
        menus.add(new MenuFile(MenuService.TRANSFER_OWNER_MENU, "transfer-owner.yml"));
        menus.add(new MenuFile(MenuService.CONFIRM_TRANSFER_MENU, "confirm-transfer.yml"));
        menus.add(new MenuFile(MenuService.CONFIRM_DELETE_NETWORK_MENU, "confirm-delete-network.yml"));
        menus.add(new MenuFile(MenuService.NETWORK_ICON_MENU, "network-icon.yml"));
        menus.add(new MenuFile(MenuService.FLOOR_ICON_MENU, "floor-icon.yml"));
        menus.add(new MenuFile(MenuService.ADMIN_MAIN_MENU, "admin-main.yml"));
        menus.add(new MenuFile(MenuService.ADMIN_NETWORKS_MENU, "admin-networks.yml"));
        menus.add(new MenuFile(MenuService.ADMIN_HISTORY_MENU, "admin-history.yml"));
        return List.copyOf(menus);
    }

    private record MenuFile(String id, String fileName) {
    }
}
