package dev.logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PlayerLogTracker {
    private final ConcurrentMap<UUID, Deque<LogRecord>> playerRecords;
    private final int maxPlayerCache;

    public PlayerLogTracker(int maxPlayerCache) {
        this.maxPlayerCache = Math.max(20, maxPlayerCache);
        this.playerRecords = new ConcurrentHashMap<>();
    }

    public void track(LogRecord record) {
        if (record == null || record.getPlayerUuid() == null) {
            return;
        }
        try {
            UUID playerUuid = UUID.fromString(record.getPlayerUuid());
            Deque<LogRecord> events = playerRecords.computeIfAbsent(playerUuid, id -> new ArrayDeque<>());
            synchronized (events) {
                events.addFirst(record);
                if (events.size() > maxPlayerCache) {
                    events.removeLast();
                }
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    public List<LogRecord> getLogs(UUID playerUuid) {
        if (playerUuid == null) {
            return Collections.emptyList();
        }
        Deque<LogRecord> events = playerRecords.get(playerUuid);
        if (events == null) {
            return Collections.emptyList();
        }
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }
}
