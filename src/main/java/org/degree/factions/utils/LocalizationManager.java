package org.degree.factions.utils;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.degree.factions.Factions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalizationManager {
    private final Factions plugin;
    private final Map<String, String> messages = new HashMap<>();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final String DEFAULT_LANGUAGE = "en";

    public LocalizationManager(Factions plugin, String language) {
        this.plugin = plugin;
        loadLocale(language);
    }

    private void loadLocale(String lang) {
        // Layer the sources so that keys added in newer plugin versions are never missing,
        // even when the server owner still has a lang file saved by an older version:
        // bundled English -> bundled <lang> -> <plugin folder>/lang/<lang>.yml (user overrides).
        loadBundled(DEFAULT_LANGUAGE);
        if (!DEFAULT_LANGUAGE.equals(lang)) {
            loadBundled(lang);
        }

        String resourcePath = "lang/" + lang + ".yml";
        File localeFile = new File(plugin.getDataFolder(), resourcePath);
        boolean bundled = hasBundled(lang);
        if (!localeFile.exists() && bundled) {
            plugin.saveResource(resourcePath, false);
        }
        if (localeFile.exists()) {
            putStrings(YamlConfiguration.loadConfiguration(localeFile));
        } else if (!bundled) {
            plugin.getLogger().warning("Language '" + lang + "' not found, using " + DEFAULT_LANGUAGE);
        }
    }

    private boolean hasBundled(String lang) {
        try (InputStream in = plugin.getResource("lang/" + lang + ".yml")) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    private void loadBundled(String lang) {
        try (InputStream in = plugin.getResource("lang/" + lang + ".yml")) {
            if (in == null) {
                return;
            }
            putStrings(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read bundled language '" + lang + "': " + e.getMessage());
        }
    }

    private void putStrings(FileConfiguration config) {
        for (String key : config.getKeys(true)) {
            if (config.isString(key)) {
                messages.put(key, config.getString(key));
            }
        }
    }

    public void reload(String lang) {
        messages.clear();
        loadLocale(lang);
    }

    public String getMessage(String key) {
        return formatColors(messages.getOrDefault(key, "Message not found: " + key));
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String message = messages.getOrDefault(key, "Message not found: " + key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return formatColors(message);
    }

    public void sendMessageToPlayer(Player player, String messageKey, Map<String, String> placeholders) {
        String message = getMessage(messageKey, placeholders);
        player.sendMessage(message);
    }

    public void sendMessageToPlayer(Player player, String messageKey) {
        sendMessageToPlayer(player, messageKey, Collections.emptyMap());
    }

    private String formatColors(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hexColor = matcher.group(1);
            matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + hexColor).toString());
        }
        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
