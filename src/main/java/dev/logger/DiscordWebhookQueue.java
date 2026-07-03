package dev.logger;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DiscordWebhookQueue {
    private static final long RATE_LIMIT_WARNING_INTERVAL_MS = 30_000;

    private final Plugin plugin;
    private final LoggerConfig config;
    private final BlockingDeque<QueuedRecord> queue;
    private final ScheduledExecutorService scheduler;
    private volatile long rateLimitedUntilMs = 0;
    private volatile long lastRateLimitWarningMs = 0;
    private volatile long lastMissingUrlWarnMs = 0;

    private static class QueuedRecord {
        final LogRecord record;
        final int position;
        int retries;

        QueuedRecord(LogRecord record, int position) {
            this.record = record;
            this.position = position;
            this.retries = 0;
        }
    }

    public DiscordWebhookQueue(Plugin plugin, LoggerConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.queue = new LinkedBlockingDeque<>(config.getDiscordQueueMaxSize());
        AtomicInteger threadCount = new AtomicInteger(0);
        this.scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "DiscordWebhookQueue-" + threadCount.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::flushQueue,
                config.getDiscordFlushIntervalMs(),
                config.getDiscordFlushIntervalMs(),
                TimeUnit.MILLISECONDS);
    }

    public boolean offer(LogRecord record) {
        if (record == null || !config.isEnableDiscord()) return false;
        String webhookUrl = config.getDiscordWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            long now = System.currentTimeMillis();
            if (now - lastMissingUrlWarnMs > RATE_LIMIT_WARNING_INTERVAL_MS) {
                lastMissingUrlWarnMs = now;
                plugin.getLogger().warning("Discord webhook is enabled but `discord-webhook-url` is empty; webhooks will be skipped.");
            }
            return false;
        }
        int position = queue.size() + 1;
        if (!queue.offerLast(new QueuedRecord(record, position))) {
            return false;
        }
        return true;
    }

    public int queueSize() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
    }

    private void flushQueue() {
        if (System.currentTimeMillis() < rateLimitedUntilMs) {
            return;
        }

        while (true) {
            QueuedRecord queued = queue.pollFirst();
            if (queued == null) {
                return;
            }
            try {
                send(queued);
            } catch (RateLimitException e) {
                handleRateLimit(queued, e.getRetryAfterSeconds());
                return;
            } catch (Exception e) {
                plugin.getLogger().warning("Discord webhook send failed: " + e.getMessage());
            }
        }
    }

    private void handleRateLimit(QueuedRecord queued, int retryAfterSeconds) {
        long now = System.currentTimeMillis();
        queued.retries++;
        if (queued.retries > config.getDiscordMaxRetries()) {
            plugin.getLogger().warning("Discord webhook dropped after " + queued.retries + " retries.");
            return;
        }
        rateLimitedUntilMs = now + TimeUnit.SECONDS.toMillis(retryAfterSeconds);
        if (!queue.offerFirst(queued)) {
            plugin.getLogger().warning("Discord webhook retry queue full, dropping rate-limited event");
            return;
        }
        if (now - lastRateLimitWarningMs > RATE_LIMIT_WARNING_INTERVAL_MS) {
            lastRateLimitWarningMs = now;
            plugin.getLogger().warning("Discord webhook rate limited; next retry in " + retryAfterSeconds + "s.");
        }
    }

    private void send(QueuedRecord queued) throws IOException, RateLimitException {
        LogRecord record = queued.record;
        String webhookUrl = config.getDiscordWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        URL url = new URL(webhookUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(config.getDiscordTimeoutMs());
        connection.setReadTimeout(config.getDiscordTimeoutMs());
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        String payload = buildDiscordPayload(queued);
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        connection.getOutputStream().write(bytes);
        int status = connection.getResponseCode();
        if (status == 429) {
            int retryAfterSeconds = parseRetryAfter(connection.getHeaderField("Retry-After"));
            throw new RateLimitException(retryAfterSeconds);
        }
        if (status < 200 || status >= 300) {
            plugin.getLogger().warning("Discord webhook returned status " + status);
        }
    }

    private int parseRetryAfter(String raw) {
        if (raw == null || raw.isBlank()) {
            return config.getDiscordBackoffMs() / 1000;
        }
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ignored) {
            return config.getDiscordBackoffMs() / 1000;
        }
    }

    private String buildDiscordPayload(QueuedRecord queued) {
        LogRecord record = queued.record;
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
        builder.append(buildField("Timestamp", record.getTimestamp()));
        builder.append(',');
        builder.append(buildField("Queue Position", String.valueOf(queued.position)));
        builder.append(',');
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

    public void shutdown() {
        scheduler.shutdownNow();
        // Attempt to flush remaining queued items synchronously (best-effort)
        try {
            java.util.List<QueuedRecord> drained = new java.util.ArrayList<>();
            queue.drainTo(drained);
            for (QueuedRecord q : drained) {
                try {
                    send(q);
                } catch (RateLimitException e) {
                    plugin.getLogger().warning("Discord webhook rate-limited while flushing during shutdown; remaining events dropped.");
                    break;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Discord webhook failed during shutdown flush: " + ex.getMessage());
                }
            }
        } catch (Exception ignore) {
        }
    }

    private static class RateLimitException extends Exception {
        private final int retryAfterSeconds;

        RateLimitException(int retryAfterSeconds) {
            this.retryAfterSeconds = retryAfterSeconds;
        }

        int getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
