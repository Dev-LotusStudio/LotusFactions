package org.degree.factions.commands;

import org.bukkit.command.*;
import org.degree.factions.Factions;
import org.degree.factions.database.FactionDatabase;
import org.degree.factions.http.FactionApiClient;
import org.degree.factions.utils.ConfigManager;
import org.degree.factions.utils.FactionUtils;
import org.degree.factions.utils.LocalizationManager;

import java.util.List;

public abstract class AbstractCommand implements CommandExecutor, TabCompleter {
    protected final LocalizationManager localization;
    protected final ConfigManager config;
    protected final FactionApiClient apiClient;
    protected final FactionUtils factionUtils;
    protected final FactionDatabase factionDatabase;

    public AbstractCommand() {
        Factions plugin = Factions.getInstance();
        this.localization = plugin.getLocalizationManager();
        this.config = plugin.getConfigManager();
        this.apiClient = plugin.getApiClient();
        this.factionUtils = plugin.getFactionUtils();
        this.factionDatabase = plugin.getFactionDatabase();
    }

    public abstract void execute(CommandSender sender, String label, String[] args);

    public abstract List<String> complete(CommandSender sender, String[] args);

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        execute(commandSender, s, strings);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        return complete(commandSender, strings);
    }
}
