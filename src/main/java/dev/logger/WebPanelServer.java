package dev.logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class WebPanelServer {
    private final LoggerPlugin plugin;
    private final LoggerConfig config;
    private final Gson gson = new Gson();
    private HttpServer server;

    public WebPanelServer(LoggerPlugin plugin, LoggerConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        if (!config.isWebPanelEnabled()) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(config.getWebPanelHost(), config.getWebPanelPort()), 0);
            server.createContext("/", new RootHandler());
            server.createContext("/api/health", new HealthHandler());
            server.createContext("/api/events", new EventsHandler());
            server.createContext("/api/stats", new StatsHandler());
            server.createContext("/api/players", new PlayersHandler());
            server.createContext("/api/worlds", new WorldsHandler());
            server.createContext("/api/player-summary", new PlayerSummaryHandler());
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            plugin.getLogger().info("Web panel started at http://" + config.getWebPanelHost() + ":" + config.getWebPanelPort());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to start web panel: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private boolean isAuthorized(HttpExchange exchange) {
        if (!config.isWebPanelAuthEnabled()) {
            return true;
        }
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Basic ")) {
            return false;
        }
        String decoded = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
        String[] parts = decoded.split(":", 2);
        return parts.length == 2 && config.getWebPanelUsername().equals(parts[0]) && config.getWebPanelPassword().equals(parts[1]);
    }

    private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private void sendHtml(HttpExchange exchange, String html) throws IOException {
        sendHtml(exchange, 200, html);
    }

    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] payload = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private String loadResource(String path) throws IOException {
        String resourcePath = path.startsWith("/") ? path.substring(1) : path;
        Path file = plugin.getDataFolder().toPath().resolve("web").resolve(resourcePath);
        if (Files.exists(file)) {
            return Files.readString(file, StandardCharsets.UTF_8);
        }
        InputStream inputStream = plugin.getResource(resourcePath);
        if (inputStream == null) {
            return null;
        }
        try (InputStream stream = inputStream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=Logger");
                sendHtml(exchange, 401, "<html><body><h1>Authentication required</h1><p>Use the username and password from the config.</p></body></html>");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                String html = loadResource("web/index.html");
                if (html == null) {
                    html = buildFallbackHtml();
                }
                html = html.replace("__USERNAME__", config.getWebPanelUsername())
                        .replace("__PASSWORD__", config.getWebPanelPassword());
                sendHtml(exchange, html);
                return;
            }
            if (path.startsWith("/api/")) {
                sendJson(exchange, 404, "{\"error\":\"not found\"}");
                return;
            }
            sendHtml(exchange, "<html><body><h1>404</h1></body></html>");
        }
    }

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=Logger");
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "ok");
            response.put("server", plugin.getName());
            response.put("timestamp", Instant.now().toString());
            sendJson(exchange, 200, gson.toJson(response));
        }
    }

    private class EventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=Logger");
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            int page = Math.max(1, Integer.parseInt(query.getOrDefault("page", "1")));
            int limit = Math.max(1, Math.min(250, Integer.parseInt(query.getOrDefault("limit", "100"))));
            String playerFilter = query.getOrDefault("player", "").trim();
            String worldFilter = query.getOrDefault("world", "").trim();
            String actionFilter = query.getOrDefault("action", "").trim();
            Map<String, Object> payload = loadEventsPage(page, limit, playerFilter, worldFilter, actionFilter);
            sendJson(exchange, 200, gson.toJson(payload));
        }
    }

    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=Logger");
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            Path logFolder = plugin.getDataFolder().toPath().resolve(config.getLogFolder());
            if (!Files.exists(logFolder)) {
                sendJson(exchange, 200, "{\"events\":0,\"players\":0,\"worlds\":0}");
                return;
            }
            int eventCount = 0;
            Set<String> players = new LinkedHashSet<>();
            Set<String> worlds = new LinkedHashSet<>();
            try (java.util.stream.Stream<Path> files = Files.list(logFolder)) {
                for (Path file : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                        if (line == null || line.isBlank()) {
                            continue;
                        }
                        eventCount++;
                        try {
                            JsonObject object = gson.fromJson(line, JsonObject.class);
                            String player = object.has("player_name") ? object.get("player_name").getAsString() : null;
                            String world = object.has("world") ? object.get("world").getAsString() : null;
                            if (player != null && !player.isEmpty()) {
                                players.add(player);
                            }
                            if (world != null && !world.isEmpty()) {
                                worlds.add(world);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("events", eventCount);
            response.put("players", players.size());
            response.put("worlds", worlds.size());
            response.put("recentPlayers", new ArrayList<>(players));
            response.put("recentDeaths", loadRecentDeaths(5));
            response.put("actionBreakdown", loadActionBreakdown(8));
            sendJson(exchange, 200, gson.toJson(response));
        }
    }

    private class PlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=Logger");
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            Path logFolder = plugin.getDataFolder().toPath().resolve(config.getLogFolder());
            if (!Files.exists(logFolder)) {
                sendJson(exchange, 200, "[]");
                return;
            }
            Set<String> players = new LinkedHashSet<>();
            try (java.util.stream.Stream<Path> files = Files.list(logFolder)) {
                for (Path file : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                        if (line == null || line.isBlank()) {
                            continue;
                        }
                        try {
                            JsonObject object = gson.fromJson(line, JsonObject.class);
                            String player = object.has("player_name") ? object.get("player_name").getAsString() : null;
                            if (player != null && !player.isEmpty()) {
                                players.add(player);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            sendJson(exchange, 200, gson.toJson(new ArrayList<>(players)));
        }
    }

    private class WorldsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=Logger");
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            Path logFolder = plugin.getDataFolder().toPath().resolve(config.getLogFolder());
            if (!Files.exists(logFolder)) {
                sendJson(exchange, 200, "[]");
                return;
            }
            Set<String> worlds = new LinkedHashSet<>();
            try (java.util.stream.Stream<Path> files = Files.list(logFolder)) {
                for (Path file : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                        if (line == null || line.isBlank()) {
                            continue;
                        }
                        try {
                            JsonObject object = gson.fromJson(line, JsonObject.class);
                            String world = object.has("world") ? object.get("world").getAsString() : null;
                            if (world != null && !world.isEmpty()) {
                                worlds.add(world);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            sendJson(exchange, 200, gson.toJson(new ArrayList<>(worlds)));
        }
    }

    private class PlayerSummaryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=Logger");
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            String player = query.getOrDefault("player", "").trim();
            if (player.isBlank()) {
                sendJson(exchange, 200, "{}");
                return;
            }
            Path logFolder = plugin.getDataFolder().toPath().resolve(config.getLogFolder());
            if (!Files.exists(logFolder)) {
                sendJson(exchange, 200, "{}");
                return;
            }
            int eventCount = 0;
            int deathCount = 0;
            List<Map<String, Object>> recentEvents = new ArrayList<>();
            try (java.util.stream.Stream<Path> files = Files.list(logFolder)) {
                for (Path file : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                        if (line == null || line.isBlank()) {
                            continue;
                        }
                        try {
                            JsonObject object = gson.fromJson(line, JsonObject.class);
                            String eventPlayer = object.has("player_name") ? object.get("player_name").getAsString() : "";
                            if (!player.equalsIgnoreCase(eventPlayer)) {
                                continue;
                            }
                            eventCount++;
                            String action = object.has("action") ? object.get("action").getAsString() : "";
                            if ("death".equalsIgnoreCase(action)) {
                                deathCount++;
                            }
                            if (recentEvents.size() < 10) {
                                Map<String, Object> event = new LinkedHashMap<>();
                                event.put("timestamp", object.has("timestamp") ? object.get("timestamp").getAsString() : "");
                                event.put("action", action);
                                event.put("subject", object.has("subject") ? object.get("subject").getAsString() : "");
                                event.put("world", object.has("world") ? object.get("world").getAsString() : "");
                                event.put("details", object.has("details") ? object.get("details") : new JsonObject());
                                recentEvents.add(event);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("player", player);
            response.put("eventCount", eventCount);
            response.put("deathCount", deathCount);
            response.put("recentEvents", recentEvents);
            sendJson(exchange, 200, gson.toJson(response));
        }
    }

    private Map<String, Object> loadEventsPage(int page, int limit, String playerFilter, String worldFilter, String actionFilter) throws IOException {
        Path logFolder = plugin.getDataFolder().toPath().resolve(config.getLogFolder());
        if (!Files.exists(logFolder)) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("page", page);
            empty.put("limit", limit);
            empty.put("total", 0);
            empty.put("pages", 1);
            empty.put("items", Collections.emptyList());
            return empty;
        }
        List<Map<String, Object>> events = new ArrayList<>();
        List<Map<String, Object>> allEvents = new ArrayList<>();
        try (java.util.stream.Stream<Path> files = Files.list(logFolder)) {
            List<Path> logFiles = files.filter(Files::isRegularFile).sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString())).collect(Collectors.toList());
            for (Path file : logFiles) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    try {
                        JsonObject object = gson.fromJson(line, JsonObject.class);
                        String player = object.has("player_name") ? object.get("player_name").getAsString() : "";
                        String world = object.has("world") ? object.get("world").getAsString() : "";
                        String action = object.has("action") ? object.get("action").getAsString() : "";
                        if (!playerFilter.isEmpty() && !player.toLowerCase().contains(playerFilter.toLowerCase())) {
                            continue;
                        }
                        if (!worldFilter.isEmpty() && !world.toLowerCase().contains(worldFilter.toLowerCase())) {
                            continue;
                        }
                        if (!actionFilter.isEmpty() && !action.toLowerCase().contains(actionFilter.toLowerCase())) {
                            continue;
                        }
                        Map<String, Object> event = new LinkedHashMap<>();
                        event.put("timestamp", object.has("timestamp") ? object.get("timestamp").getAsString() : "");
                        event.put("source", object.has("source") ? object.get("source").getAsString() : "");
                        event.put("action", action);
                        event.put("subject", object.has("subject") ? object.get("subject").getAsString() : "");
                        event.put("player", player);
                        event.put("world", world);
                        event.put("details", object.has("details") ? object.get("details") : new JsonObject());
                        allEvents.add(event);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        int total = allEvents.size();
        int startIndex = (page - 1) * limit;
        int endIndex = Math.min(startIndex + limit, total);
        if (startIndex < total) {
            events.addAll(allEvents.subList(startIndex, endIndex));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("page", page);
        payload.put("limit", limit);
        payload.put("total", total);
        payload.put("pages", total == 0 ? 1 : (int) Math.ceil(total / (double) limit));
        payload.put("items", events);
        return payload;
    }

    private List<Map<String, Object>> loadRecentDeaths(int limit) throws IOException {
        Path logFolder = plugin.getDataFolder().toPath().resolve(config.getLogFolder());
        if (!Files.exists(logFolder)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> deaths = new ArrayList<>();
        try (java.util.stream.Stream<Path> files = Files.list(logFolder)) {
            for (Path file : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    try {
                        JsonObject object = gson.fromJson(line, JsonObject.class);
                        String action = object.has("action") ? object.get("action").getAsString() : "";
                        if (!"death".equalsIgnoreCase(action)) {
                            continue;
                        }
                        Map<String, Object> death = new LinkedHashMap<>();
                        death.put("timestamp", object.has("timestamp") ? object.get("timestamp").getAsString() : "");
                        death.put("player", object.has("player_name") ? object.get("player_name").getAsString() : "");
                        death.put("world", object.has("world") ? object.get("world").getAsString() : "");
                        death.put("details", object.has("details") ? object.get("details") : new JsonObject());
                        deaths.add(death);
                        if (deaths.size() >= limit) {
                            return deaths;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return deaths;
    }

    private Map<String, Integer> loadActionBreakdown(int limit) throws IOException {
        Path logFolder = plugin.getDataFolder().toPath().resolve(config.getLogFolder());
        if (!Files.exists(logFolder)) {
            return Collections.emptyMap();
        }
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> files = Files.list(logFolder)) {
            for (Path file : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    try {
                        JsonObject object = gson.fromJson(line, JsonObject.class);
                        String action = object.has("action") ? object.get("action").getAsString() : "";
                        if (action.isBlank()) {
                            continue;
                        }
                        breakdown.put(action, breakdown.getOrDefault(action, 0) + 1);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return breakdown.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> values = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return values;
        }
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2) {
                values.put(pair[0], java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    private String buildFallbackHtml() {
        return """
                <!doctype html>
                <html>
                <head><meta charset=\"utf-8\"><title>Logger Web Panel</title></head>
                <body style=\"font-family:Arial,sans-serif;padding:24px;\">
                  <h1>Logger Web Panel</h1>
                  <p>The panel is running. Use the /api/health and /api/events endpoints.</p>
                </body>
                </html>
                """;
    }
}
