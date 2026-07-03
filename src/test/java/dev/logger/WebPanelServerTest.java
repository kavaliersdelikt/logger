package dev.logger;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebPanelServerTest {

    @Test
    void paginatesNewestEventsFirst() {
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(event("2024-01-01T00:00:00Z", "old"));
        events.add(event("2024-01-02T00:00:00Z", "newer"));
        events.add(event("2024-01-03T00:00:00Z", "newest"));

        List<Map<String, Object>> page = WebPanelServer.paginateEventsForDisplay(events, 1, 2);

        assertEquals(2, page.size());
        assertEquals("newest", page.get(0).get("subject"));
        assertEquals("newer", page.get(1).get("subject"));
    }

    private Map<String, Object> event(String timestamp, String subject) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("timestamp", timestamp);
        event.put("subject", subject);
        return event;
    }
}
