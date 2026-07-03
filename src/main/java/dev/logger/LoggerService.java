package dev.logger;

import org.bukkit.plugin.Plugin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoggerService {
    private final Plugin plugin;
    private final LoggerConfig config;
    private final ExecutorService executorService;
    private final DiscordWebhookQueue discordQueue;

    public LoggerService(Plugin plugin, LoggerConfig config) {
        this.plugin = plugin;
        this.config = config;
        AtomicInteger threadCount = new AtomicInteger(0);
        this.executorService = Executors.newFixedThreadPool(config.getThreadPoolSize(), runnable -> {
            Thread thread = new Thread(runnable, "LoggerWorker-" + threadCount.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.discordQueue = new DiscordWebhookQueue(plugin, config);
    }

    public void logAsync(LogRecord record) {
        if (record == null) return;
        executorService.submit(() -> {
            try {
                if (config.isEnableFileLogging()) {
                    writeFile(record);
                }
                if (config.isEnableDiscord() && shouldSendDiscord(record)) {
                    discordQueue.offer(record);
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("LoggerService failed to write record: " + exception.getMessage());
            }
        });
    }

    private boolean shouldSendDiscord(LogRecord record) {
        if (record == null) return false;
        if ("block".equals(record.getSource()) && ("break".equals(record.getAction()) || "place".equals(record.getAction()))) {
            return false;
        }
        return !"container".equals(record.getSource());
    }

    private void writeFile(LogRecord record) throws IOException {
        Path folder = plugin.getDataFolder().toPath().resolve(config.getLogFolder());
        Files.createDirectories(folder);
        String fileName = config.getLogFilePattern().replace("%DATE%", java.time.LocalDate.now().toString());
        Path file = resolveLogFilePath(folder, fileName, config.isLogRotationEnabled(), config.getMaxLogFileSizeBytes(),
                Files.exists(folder.resolve(fileName)) ? Files.size(folder.resolve(fileName)) : 0L);
        String line = record.toJson(config.isLogJsonPretty());
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            writer.write(line);
            writer.write(System.lineSeparator());
            writer.flush();
        }
    }

    static Path resolveLogFilePath(Path folder, String fileName, boolean rotationEnabled, long maxFileSizeBytes, long currentFileSize) throws IOException {
        Path file = folder.resolve(fileName);
        if (!rotationEnabled || !Files.exists(file) || currentFileSize < maxFileSizeBytes) {
            return file;
        }

        String name = file.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        String baseName = dotIndex >= 0 ? name.substring(0, dotIndex) : name;
        String extension = dotIndex >= 0 ? name.substring(dotIndex) : "";
        int index = 1;
        while (true) {
            Path rotatedFile = folder.resolve(baseName + "-" + index + extension);
            if (!Files.exists(rotatedFile)) {
                return rotatedFile;
            }
            index++;
        }
    }

    private void sendDiscord(LogRecord record) {
        String webhookUrl = config.getDiscordWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(config.getDiscordTimeoutMs());
            connection.setReadTimeout(config.getDiscordTimeoutMs());
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            String payload = buildDiscordPayload(record);
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            connection.getOutputStream().write(bytes);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                plugin.getLogger().warning("Discord webhook returned status " + status);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Discord webhook send failed: " + exception.getMessage());
        }
    }

    private String buildDiscordPayload(LogRecord record) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        builder.append("\"content\":\"");
        builder.append(escape(record.getSource() + " > " + record.getAction() + " - " + record.getSubject()));
        builder.append("\",");
        builder.append("\"embeds\":[{\"title\":\"");
        builder.append(escape(record.getPlayerName() != null ? record.getPlayerName() : "server"));
        builder.append("\",\"description\":\"");
        builder.append(escape(buildDiscordDescription(record)));
        builder.append("\",\"fields\":[");
        builder.append(buildField("World", record.getWorld()));
        builder.append(',');
        builder.append(buildField("Player UUID", record.getPlayerUuid()));
        builder.append(']');
        builder.append("}]}");
        return builder.toString();
    }

    private String buildDiscordDescription(LogRecord record) {
        StringBuilder builder = new StringBuilder();
        builder.append("Source: ").append(record.getSource()).append("\n");
        builder.append("Action: ").append(record.getAction()).append("\n");
        builder.append("Subject: ").append(record.getSubject()).append("\n");
        if (!record.getDetails().isEmpty()) {
            builder.append("Details:\n");
            record.getDetails().forEach((key, value) -> builder.append(" - ").append(key).append(": ").append(value).append("\n"));
        }
        return builder.toString().trim();
    }

    private String buildField(String name, String value) {
        return "{\"name\":\"" + escape(name) + "\",\"value\":\"" + escape(value != null ? value : "-") + "\",\"inline\":true}";
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public int getDiscordQueueSize() {
        return discordQueue.queueSize();
    }

    public void clearDiscordQueue() {
        discordQueue.clear();
    }

    public void logSync(LogRecord record) {
        if (record == null) return;
        try {
            if (config.isEnableFileLogging()) {
                writeFile(record);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("LoggerService failed to synchronously write record: " + exception.getMessage());
        }
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
        discordQueue.shutdown();
    }
}
