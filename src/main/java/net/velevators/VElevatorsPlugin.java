package net.velevators;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.milkbowl.vault.economy.Economy;
import net.velevators.command.ElevatorCommand;
import net.velevators.command.InternalActionCommand;
import net.velevators.command.CommandAliasService;
import net.velevators.command.VElevatorsBasicCommand;
import net.velevators.config.MessageService;
import net.velevators.config.PluginSettings;
import net.velevators.data.ElevatorDatabase;
import net.velevators.listener.ElevatorInteractionListener;
import net.velevators.listener.ProtectionListener;
import net.velevators.listener.RenameListener;
import net.velevators.menu.MenuInstaller;
import net.velevators.menu.MenuService;
import net.velevators.placeholder.ElevatorExpansion;
import net.velevators.service.ElevatorService;
import net.velevators.service.PaperPlayerTextureResolver;
import net.velevators.service.TransactionLogger;
import net.velevators.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class VElevatorsPlugin extends JavaPlugin {
    private volatile PluginSettings settings;
    private MessageService messages;
    private ElevatorDatabase database;
    private SessionManager sessions;
    private MenuInstaller menuInstaller;
    private PlaceholderExpansion expansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new MessageService(this);
        settings = PluginSettings.load(this);

        if (!verifyDependencies()) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Economy economy = resolveEconomy();
        if (economy == null) {
            getLogger().severe("Vault está instalado, pero no hay ningún proveedor de economía registrado.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        try {
            Files.createDirectories(getDataFolder().toPath());
            database = new ElevatorDatabase(getDataFolder().toPath().resolve("elevators.db"));
        } catch (SQLException | ClassNotFoundException | IOException exception) {
            getLogger().severe("No se pudo iniciar SQLite: " + exception.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        sessions = new SessionManager();
        TransactionLogger transactionLogger = new TransactionLogger(
                getDataFolder().toPath().resolve("transactions.log"),
                getDataFolder().toPath().resolve("transactions_logs.log"));
        ElevatorService elevators = new ElevatorService(this, database, economy, transactionLogger, () -> settings);
        MenuService menus = new MenuService(this, database, sessions, messages,
                new PaperPlayerTextureResolver(this), transactionLogger);
        menuInstaller = new MenuInstaller(this);

        if (settings.autoInstallMenus()) {
            try {
                if (menuInstaller.installForLocale(messages.locale())) {
                    menuInstaller.reloadDeluxeMenus();
                }
            } catch (IOException exception) {
                getLogger().severe("No se pudieron instalar los menús de DeluxeMenus: " + exception.getMessage());
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
        }

        ElevatorCommand elevatorCommand = new ElevatorCommand(this, menus, messages, transactionLogger, economy);
        CommandAliasService commandAliases = new CommandAliasService(this);
        VElevatorsBasicCommand basicCommand = new VElevatorsBasicCommand(elevatorCommand);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            CommandAliasService.AliasResult aliases = commandAliases.configuredAliases();
            event.registrar().register("velevators", "Opens the vElevators menu.", aliases.aliases(), basicCommand);
        });
        Objects.requireNonNull(getCommand("velevatorsaction"), "Falta el comando interno en plugin.yml")
                .setExecutor(new InternalActionCommand(this, elevators, menus, sessions, messages, () -> settings));

        Bukkit.getPluginManager().registerEvents(
                new ElevatorInteractionListener(this, elevators, menus, sessions, messages, () -> settings), this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this, elevators, messages), this);
        Bukkit.getPluginManager().registerEvents(
                new RenameListener(this, elevators, menus, sessions, messages, () -> settings), this);

        expansion = new ElevatorExpansion(elevators, sessions, () -> settings);
        if (!expansion.register()) {
            getLogger().severe("PlaceholderAPI rechazó el registro de la expansión velevators.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("vElevators v" + getPluginMeta().getVersion()
                + " activado con " + economy.getName() + " como proveedor económico.");
    }

    private boolean verifyDependencies() {
        List<String> required = List.of("DeluxeMenus", "PlaceholderAPI", "LuckPerms", "Vault");
        for (String name : required) {
            if (!Bukkit.getPluginManager().isPluginEnabled(name)) {
                getLogger().severe("Dependencia obligatoria ausente o desactivada: " + name);
                return false;
            }
        }
        return true;
    }

    private Economy resolveEconomy() {
        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    public boolean reloadPlugin() {
        try {
            reloadConfig();
            messages.reload();
            settings = PluginSettings.load(this);
            if (settings.autoInstallMenus()) {
                menuInstaller.installForLocale(messages.locale());
            }
            menuInstaller.reloadDeluxeMenus();
            return true;
        } catch (RuntimeException | IOException exception) {
            getLogger().severe("Fallo durante la recarga: " + exception.getMessage());
            return false;
        }
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
        }
        if (sessions != null) {
            sessions.clear();
        }
        if (database != null) {
            try {
                database.close();
            } catch (SQLException exception) {
                getLogger().severe("No se pudo cerrar SQLite correctamente: " + exception.getMessage());
            }
        }
    }
}
