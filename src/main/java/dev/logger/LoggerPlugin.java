package dev.logger;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class LoggerPlugin extends JavaPlugin {
    private static LoggerPlugin instance;
    private LoggerService loggerService;
    private PlayerLogTracker tracker;
    private LoggerConfig loggerConfig;
    private LogEventListener logEventListener;

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loggerConfig = new LoggerConfig(getConfig());
        loggerService = new LoggerService(this, loggerConfig);
        tracker = new PlayerLogTracker(loggerConfig.getMaxPlayerCache());
        logEventListener = new LogEventListener(this, loggerService, tracker, loggerConfig);

        getServer().getPluginManager().registerEvents(logEventListener, this);
        getCommand("logger").setExecutor(new LogViewCommand(this));

        loggerService.logAsync(LogRecord.system("plugin", "enabled", "logger plugin enabled"));
    }

    @Override
    public void onDisable() {
        if (loggerService != null) {
            loggerService.logSync(LogRecord.system("plugin", "disabled", "logger plugin disabled"));
            loggerService.shutdown();
        }
        if (logEventListener != null) {
            HandlerList.unregisterAll(logEventListener);
        }
    }

    public static LoggerPlugin getInstance() {
        return instance;
    }

    public LoggerService getLoggerService() {
        return loggerService;
    }

    public PlayerLogTracker getTracker() {
        return tracker;
    }

    public LoggerConfig getLoggerConfig() {
        return loggerConfig;
    }

    public void reloadLogger() {
        reloadConfig();
        LoggerService oldService = this.loggerService;
        if (logEventListener != null) {
            HandlerList.unregisterAll(logEventListener);
        }
        if (oldService != null) {
            oldService.shutdown();
        }

        this.loggerConfig = new LoggerConfig(getConfig());
        this.loggerService = new LoggerService(this, loggerConfig);
        this.tracker = new PlayerLogTracker(loggerConfig.getMaxPlayerCache());
        this.logEventListener = new LogEventListener(this, loggerService, tracker, loggerConfig);
        getServer().getPluginManager().registerEvents(logEventListener, this);
        loggerService.logAsync(LogRecord.system("plugin", "reload", "logger plugin reloaded"));
    }
}
