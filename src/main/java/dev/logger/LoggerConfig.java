package dev.logger;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LoggerConfig {
    private final boolean enableFileLogging;
    private final String logFolder;
    private final String logFilePattern;
    private final boolean logRotationEnabled;
    private final long maxLogFileSizeBytes;
    private final boolean logJsonPretty;
    private final boolean enableDiscord;
    private final String discordWebhookUrl;
    private final boolean webPanelEnabled;
    private final String webPanelHost;
    private final int webPanelPort;
    private final boolean webPanelAuthEnabled;
    private final String webPanelUsername;
    private final String webPanelPassword;
    private final int discordTimeoutMs;
    private final int discordQueueMaxSize;
    private final int discordFlushIntervalMs;
    private final int discordBackoffMs;
    private final int discordMaxRetries;
    private final int threadPoolSize;
    private final int maxPlayerCache;
    private final boolean worldFilterEnabled;
    private final Set<String> allowedWorlds;
    private final Set<String> excludedWorlds;

    public LoggerConfig(FileConfiguration config) {
        enableFileLogging = config.getBoolean("enable-file-logging", true);
        logFolder = config.getString("log-folder", "logs");
        logFilePattern = config.getString("log-file-pattern", "logger-%DATE%.json");
        logRotationEnabled = config.getBoolean("log-rotation.enabled", true);
        maxLogFileSizeBytes = Math.max(1024, config.getLong("log-rotation.max-file-size-bytes", 10L * 1024L * 1024L));
        logJsonPretty = config.getBoolean("log-json-pretty", false);
        enableDiscord = config.getBoolean("enable-discord", true);
        discordWebhookUrl = config.getString("discord-webhook-url", "");
        webPanelEnabled = config.getBoolean("web-panel.enabled", false);
        webPanelHost = config.getString("web-panel.host", "0.0.0.0");
        webPanelPort = Math.max(1, Math.min(65535, config.getInt("web-panel.port", 8080)));
        webPanelAuthEnabled = config.getBoolean("web-panel.auth-enabled", true);
        webPanelUsername = config.getString("web-panel.username", "admin");
        webPanelPassword = config.getString("web-panel.password", "change-me");
        discordTimeoutMs = config.getInt("discord-timeout-ms", 5000);
        discordQueueMaxSize = Math.max(100, config.getInt("discord-queue-max-size", 1000));
        discordFlushIntervalMs = Math.max(250, config.getInt("discord-flush-interval-ms", 1500));
        discordBackoffMs = Math.max(1000, config.getInt("discord-backoff-ms", 5000));
        discordMaxRetries = Math.max(1, config.getInt("discord-max-retries", 3));
        threadPoolSize = Math.max(1, config.getInt("thread-pool-size", 4));
        maxPlayerCache = Math.max(20, config.getInt("max-player-cache", 200));

        worldFilterEnabled = config.getBoolean("world-filters.enabled", false);
        allowedWorlds = new HashSet<>(config.getStringList("world-filters.allowed"));
        excludedWorlds = new HashSet<>(config.getStringList("world-filters.excluded"));
    }

    public boolean isEnableFileLogging() {
        return enableFileLogging;
    }

    public String getLogFolder() {
        return logFolder;
    }

    public String getLogFilePattern() {
        return logFilePattern;
    }

    public boolean isLogRotationEnabled() {
        return logRotationEnabled;
    }

    public long getMaxLogFileSizeBytes() {
        return maxLogFileSizeBytes;
    }

    public boolean isLogJsonPretty() {
        return logJsonPretty;
    }

    public boolean isEnableDiscord() {
        return enableDiscord;
    }

    public String getDiscordWebhookUrl() {
        return discordWebhookUrl;
    }

    public boolean isWebPanelEnabled() {
        return webPanelEnabled;
    }

    public String getWebPanelHost() {
        return webPanelHost;
    }

    public int getWebPanelPort() {
        return webPanelPort;
    }

    public boolean isWebPanelAuthEnabled() {
        return webPanelAuthEnabled;
    }

    public String getWebPanelUsername() {
        return webPanelUsername;
    }

    public String getWebPanelPassword() {
        return webPanelPassword;
    }

    public int getDiscordTimeoutMs() {
        return discordTimeoutMs;
    }

    public int getDiscordQueueMaxSize() {
        return discordQueueMaxSize;
    }

    public int getDiscordFlushIntervalMs() {
        return discordFlushIntervalMs;
    }

    public int getDiscordBackoffMs() {
        return discordBackoffMs;
    }

    public int getDiscordMaxRetries() {
        return discordMaxRetries;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public int getMaxPlayerCache() {
        return maxPlayerCache;
    }

    public boolean isWorldFilterEnabled() {
        return worldFilterEnabled;
    }

    public boolean isWorldAllowed(String world) {
        if (!worldFilterEnabled || world == null || world.isEmpty()) {
            return true;
        }
        if (!allowedWorlds.isEmpty()) {
            return allowedWorlds.contains(world);
        }
        if (!excludedWorlds.isEmpty()) {
            return !excludedWorlds.contains(world);
        }
        return true;
    }
}
