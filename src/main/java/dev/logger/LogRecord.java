package dev.logger;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class LogRecord {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final String timestamp;
    private final String source;
    private final String action;
    private final String subject;
    private final String world;
    private final String playerName;
    private final String playerUuid;
    private final Map<String, Object> details;

    private LogRecord(String source, String action, String subject, String world, String playerName, UUID playerUuid, Map<String, Object> details) {
        this.timestamp = TIMESTAMP_FORMATTER.format(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        this.source = source;
        this.action = action;
        this.subject = subject;
        this.world = world;
        this.playerName = playerName;
        this.playerUuid = playerUuid != null ? playerUuid.toString() : null;
        this.details = details != null ? details : new LinkedHashMap<>();
    }

    public static LogRecord system(String source, String action, String subject) {
        return new LogRecord(source, action, subject, null, null, null, null);
    }

    public static LogRecord system(String source, String action, String subject, String world, Map<String, Object> details) {
        return new LogRecord(source, action, subject, world, null, null, details);
    }

    public static LogRecord player(String source, String action, String subject, String world, String playerName, UUID playerUuid, Map<String, Object> details) {
        return new LogRecord(source, action, subject, world, playerName, playerUuid, details);
    }

    public String toJson(boolean pretty) {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        if (pretty) {
            builder.append("\n  ");
        }
        appendEntry(builder, "timestamp", timestamp, pretty, 2);
        appendEntry(builder, "source", source, pretty, 2);
        appendEntry(builder, "action", action, pretty, 2);
        appendEntry(builder, "subject", subject, pretty, 2);
        appendEntry(builder, "world", world, pretty, 2);
        appendEntry(builder, "player_name", playerName, pretty, 2);
        appendEntry(builder, "player_uuid", playerUuid, pretty, 2);
        appendObject(builder, "details", details, pretty, 2);
        if (pretty) {
            builder.append("\n");
        }
        builder.append("}");
        return builder.toString();
    }

    private void appendEntry(StringBuilder builder, String key, String value, boolean pretty, int indent) {
        if (value != null) {
            if (pretty) builder.append("  ".repeat(Math.max(0, indent)));
            builder.append('"').append(escape(key)).append('"').append(": ")
                    .append('"').append(escape(value)).append('"').append(",");
            if (pretty) builder.append("\n");
        }
    }

    private void appendObject(StringBuilder builder, String key, Map<String, Object> object, boolean pretty, int indent) {
        if (pretty) builder.append("  ".repeat(Math.max(0, indent)));
        builder.append('"').append(escape(key)).append('"').append(": ");
        if (object == null || object.isEmpty()) {
            builder.append("{}");
            if (pretty) builder.append("\n");
            return;
        }

        builder.append("{");
        if (pretty) builder.append("\n");
        int count = 0;
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            count++;
            if (pretty) builder.append("  ".repeat(Math.max(0, indent + 1)));
            builder.append('"').append(escape(entry.getKey())).append('"').append(": ");
            builder.append(formatValue(entry.getValue()));
            if (count < object.size()) builder.append(',');
            if (pretty) builder.append("\n");
        }
        if (pretty) builder.append("  ".repeat(Math.max(0, indent)));
        builder.append('}');
        if (pretty) builder.append("\n");
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return '"' + escape(value.toString()) + '"';
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getSource() {
        return source;
    }

    public String getAction() {
        return action;
    }

    public String getSubject() {
        return subject;
    }

    public String getWorld() {
        return world;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
