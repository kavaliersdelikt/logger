package dev.logger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LogEventListener implements Listener {
    private final LoggerPlugin plugin;
    private final LoggerService loggerService;
    private final PlayerLogTracker tracker;
    private final LoggerConfig config;

    public LogEventListener(LoggerPlugin plugin, LoggerService loggerService, PlayerLogTracker tracker, LoggerConfig config) {
        this.plugin = plugin;
        this.loggerService = loggerService;
        this.tracker = tracker;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!config.isWorldAllowed(player.getWorld().getName())) return;
        LogRecord record = LogRecord.player("player", "join", event.getJoinMessage(), player.getWorld().getName(), player.getName(), player.getUniqueId(), Map.of("address", player.getAddress().getAddress().getHostAddress()));
        dispatch(record);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!config.isWorldAllowed(player.getWorld().getName())) return;
        LogRecord record = LogRecord.player("player", "quit", event.getQuitMessage(), player.getWorld().getName(), player.getName(), player.getUniqueId(), null);
        dispatch(record);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!config.isWorldAllowed(player.getWorld().getName())) return;
        LogRecord record = LogRecord.player("chat", "message", event.getMessage(), player.getWorld().getName(), player.getName(), player.getUniqueId(), Map.of("format", event.getFormat()));
        dispatch(record);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!config.isWorldAllowed(player.getWorld().getName())) return;
        LogRecord record = LogRecord.player("command", "execute", event.getMessage(), player.getWorld().getName(), player.getName(), player.getUniqueId(), null);
        dispatch(record);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!config.isWorldAllowed(event.getBlock().getWorld().getName())) return;
        Player player = event.getPlayer();
        Map<String, Object> details = new HashMap<>();
        details.put("block", event.getBlock().getType().name());
        details.put("location", formatLocation(event.getBlock().getLocation().getBlockX(), event.getBlock().getLocation().getBlockY(), event.getBlock().getLocation().getBlockZ()));
        LogRecord record = LogRecord.player("block", "break", "block broken", event.getBlock().getWorld().getName(), player.getName(), player.getUniqueId(), details);
        dispatch(record);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!config.isWorldAllowed(event.getBlock().getWorld().getName())) return;
        Player player = event.getPlayer();
        Map<String, Object> details = new HashMap<>();
        details.put("block", event.getBlock().getType().name());
        details.put("location", formatLocation(event.getBlock().getLocation().getBlockX(), event.getBlock().getLocation().getBlockY(), event.getBlock().getLocation().getBlockZ()));
        LogRecord record = LogRecord.player("block", "place", "block placed", event.getBlock().getWorld().getName(), player.getName(), player.getUniqueId(), details);
        dispatch(record);
    }

    // Entity death logging removed per user request (was generating noisy logs)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!config.isWorldAllowed(event.getFrom().getWorld().getName())) return;
        Map<String, Object> details = new HashMap<>();
        details.put("from", formatLocation(event.getFrom()));
        details.put("to", formatLocation(event.getTo()));
        Player player = event.getPlayer();
        LogRecord record = LogRecord.player("movement", "teleport", "player teleported", event.getTo().getWorld().getName(), player.getName(), player.getUniqueId(), details);
        dispatch(record);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!config.isWorldAllowed(event.getRespawnLocation().getWorld().getName())) return;
        LogRecord record = LogRecord.player("player", "respawn", "player respawned", event.getRespawnLocation().getWorld().getName(), player.getName(), player.getUniqueId(), null);
        dispatch(record);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() == null || event.getView() == null) return;
        Component title = event.getView().title();
        if (title == null) return;
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(title);
        if (plainTitle.contains("Logger Player View")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player viewer)) return;
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null || meta.getLore() == null) return;
            for (String line : meta.getLore()) {
                String stripped = ChatColor.stripColor(line);
                if (stripped.startsWith("UUID:")) {
                    String uuidString = stripped.substring(5).trim();
                    try {
                        PlayerLogGui.openDetails(viewer, UUID.fromString(uuidString), tracker);
                    } catch (IllegalArgumentException ignored) {
                    }
                    return;
                }
            }
        }
        if (plainTitle.contains("Logger Details")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            PlayerLogGui.closeDetails(player);
        }
    }

    private String formatLocation(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private String formatLocation(org.bukkit.Location location) {
        if (location == null) return "unknown";
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private void dispatch(LogRecord record) {
        loggerService.logAsync(record);
        tracker.track(record);
    }
}
