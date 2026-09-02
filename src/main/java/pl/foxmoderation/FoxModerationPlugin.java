package pl.foxmoderation;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import pl.foxmoderation.command.FoxCommandExecutor;
import pl.foxmoderation.data.DatabaseManager;
import pl.foxmoderation.gui.GuiService;
import pl.foxmoderation.listener.ChatListener;
import pl.foxmoderation.listener.CheckListener;
import pl.foxmoderation.listener.InventoryListener;
import pl.foxmoderation.service.*;

public final class FoxModerationPlugin extends JavaPlugin {
    private DatabaseManager databaseManager;
    private PresetService presetService;
    private HierarchyService hierarchyService;
    private PunishmentService punishmentService;
    private NoteService noteService;
    private CheckService checkService;
    private GuiService guiService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();
        databaseManager = new DatabaseManager(getDataFolder());
        try {
            databaseManager.connect();
        } catch (Exception exception) {
            throw new IllegalStateException("Nie udało się uruchomić SQLite", exception);
        }
        presetService = new PresetService(getConfig());
        hierarchyService = new HierarchyService();
        punishmentService = new PunishmentService(this, databaseManager);
        noteService = new NoteService(databaseManager, punishmentService);
        checkService = new CheckService(this, databaseManager, punishmentService);
        guiService = new GuiService(this, punishmentService, noteService);

        FoxCommandExecutor executor = new FoxCommandExecutor(this, hierarchyService, presetService, punishmentService, noteService, checkService, guiService);
        for (String name : new String[]{"warn", "mute", "kick", "ban", "unmute", "unban", "check", "checkstart", "checkclear", "checkcheat", "note", "importantnote", "notes", "punishment", "history", "deletepunishment", "checkinfo", "stafflog", "discord"}) {
            PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(executor);
                command.setTabCompleter(executor);
            }
        }

        getServer().getPluginManager().registerEvents(new ChatListener(noteService, punishmentService), this);
        getServer().getPluginManager().registerEvents(new CheckListener(checkService), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(this, guiService, punishmentService), this);
        checkService.startTicker();
    }

    @Override
    public void onDisable() {
        if (checkService != null) checkService.stopTicker();
        if (databaseManager != null) databaseManager.close();
    }

    public DatabaseManager database() {
        return databaseManager;
    }
}
