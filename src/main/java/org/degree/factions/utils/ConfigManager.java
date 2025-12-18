package org.degree.factions.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.degree.factions.Factions;

public class ConfigManager {
    private final Factions plugin;
    private FileConfiguration config;

    public ConfigManager(Factions plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        loadConfig();
    }

    private void loadConfig() {
        config = plugin.getConfig();
    }

    public String getString(String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }

    public int getInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }

    public long getLong(String path, long defaultValue) {
        return config.getLong(path, defaultValue);
    }

    public int getInviteCooldownSeconds() {
        return getInt("faction-settings.invite-cooldown-seconds", 60);
    }

    public boolean isIngestEnabled() {
        return getBoolean("ingest.enabled", false);
    }

    public String getIngestBaseUrl() {
        return getString("ingest.base-url", "http://127.0.0.1:8000");
    }

    public String getIngestEndpointPath() {
        return getString("ingest.endpoint-path", "/api/v1/ingest/factions/");
    }

    public String getIngestServerHeader() {
        return getString("ingest.server-header", "lotuscraft");
    }

    public String getIngestApiKey() {
        return getString("ingest.api-key", "");
    }

    public int getIngestConnectTimeoutMs() {
        return getInt("ingest.connect-timeout-ms", 5000);
    }

    public int getIngestReadTimeoutMs() {
        return getInt("ingest.read-timeout-ms", 10000);
    }

    public int getIngestIntervalSeconds() {
        return getInt("ingest.interval-seconds", 300);
    }

    public int getIngestOnlineSampleSeconds() {
        return getInt("ingest.online-sample-seconds", 60);
    }

    public int getIngestChartsDays() {
        return getInt("ingest.charts-days", 14);
    }

    public String getIngestPayloadPluginName() {
        return getString("ingest.payload.plugin-name", plugin.getName());
    }

    public String getIngestPayloadServerName() {
        return getString("ingest.payload.server-name", plugin.getName());
    }

    public String getIngestPayloadPlatform() {
        return getString("ingest.payload.platform", "paper");
    }
}
