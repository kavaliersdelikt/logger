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
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.Material;
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
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory() == null) return;
        if (event.getView() == null) return;
        if (event.getView().getTopInventory() == null) return;
        if (!event.getView().getTopInventory().equals(event.getInventory())) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!config.isWorldAllowed(player.getWorld().getName())) return;
        String world = player.getWorld().getName();
        Material material = event.getOldCursor() == null ? Material.AIR : event.getOldCursor().getType();
        Map<String, Object> details = new HashMap<>();
        details.put("slots", event.getRawSlots().toString());
        details.put("item", material.name());
        LogRecord record = LogRecord.player("container", "drag", "container modified by drag", world, player.getName(), player.getUniqueId(), details);
        dispatch(record);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!config.isWorldAllowed(player.getWorld().getName())) return;
        ItemStack hand = event.getItem();
        if (hand == null) return;
        // Don't treat a right-click with flint-and-steel as an ignite if the player is interacting with a container (opening a chest, barrel, etc.)
        if (event.getClickedBlock() != null) {
            org.bukkit.block.BlockState state = event.getClickedBlock().getState();
            if (state instanceof org.bukkit.inventory.InventoryHolder) {
                return;
            }
        }
        if (hand.getType() == Material.FLINT_AND_STEEL) {
            Map<String, Object> details = new HashMap<>();
            details.put("target", event.getClickedBlock() == null ? "-" : formatLocation(event.getClickedBlock().getLocation()));
            LogRecord record = LogRecord.player("player", "ignite", "used flint and steel", player.getWorld().getName(), player.getName(), player.getUniqueId(), details);
            dispatch(record);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getBlock() == null || event.getBlock().getWorld() == null) return;
        if (!config.isWorldAllowed(event.getBlock().getWorld().getName())) return;
        String cause = event.getCause() == null ? "UNKNOWN" : event.getCause().name();
        Map<String, Object> details = new HashMap<>();
        details.put("location", formatLocation(event.getBlock().getLocation()));
        details.put("cause", cause);
        if (event.getIgnitingEntity() instanceof Player player) {
            LogRecord record = LogRecord.player("environment", "ignite", "block ignited by player", event.getBlock().getWorld().getName(), player.getName(), player.getUniqueId(), details);
            dispatch(record);
        } else {
            LogRecord record = LogRecord.system("environment", "ignite", "block ignited", event.getBlock().getWorld().getName(), details);
            dispatch(record);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity() == null || event.getEntity().getWorld() == null) return;
        if (!config.isWorldAllowed(event.getEntity().getWorld().getName())) return;
        String type = event.getEntity().getType().name();
        Map<String, Object> details = new HashMap<>();
        details.put("type", type);
        details.put("location", formatLocation(event.getEntity().getLocation()));
        details.put("blocks", event.blockList().size());
        LogRecord record = LogRecord.system("environment", "explode", type + " exploded", event.getEntity().getWorld().getName(), details);
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
        // GUI handling
        if (plainTitle.contains("Logger Player View")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player viewer = (Player) event.getWhoClicked();
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
            return;
        }

        // Non-GUI container click handling: determine add/remove for various click types (including shift-click transfers)
        Inventory top = event.getView().getTopInventory();
        Inventory clickedInv = event.getClickedInventory();
        if (top == null || clickedInv == null) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!config.isWorldAllowed(player.getWorld().getName())) return;

        InventoryAction action = event.getAction();
        Map<String, Object> details = new HashMap<>();
        details.put("action", action.name());
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        details.put("current", current == null ? "-" : current.getType().name());
        details.put("cursor", cursor == null ? "-" : cursor.getType().name());
        details.put("slot", event.getSlot());

        boolean topClicked = top.equals(clickedInv);
        boolean playerInvClicked = player.getInventory().equals(clickedInv);

        LogRecord record = null;

        // Direct pickup/place from/to container slots
        if (topClicked) {
            if (action == InventoryAction.PICKUP_ALL || action == InventoryAction.PICKUP_HALF || action == InventoryAction.PICKUP_ONE || action == InventoryAction.PICKUP_SOME) {
                record = LogRecord.player("container", "remove", "item removed from container", player.getWorld().getName(), player.getName(), player.getUniqueId(), details);
            } else if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE || action == InventoryAction.PLACE_SOME || action == InventoryAction.HOTBAR_SWAP) {
                record = LogRecord.player("container", "add", "item added to container", player.getWorld().getName(), player.getName(), player.getUniqueId(), details);
            }
        }

        // Shift-click or move-to-other-inventory transfers
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (topClicked) {
                // moving from top to player
                record = LogRecord.player("container", "remove", "item removed from container", player.getWorld().getName(), player.getName(), player.getUniqueId(), details);
            } else if (playerInvClicked) {
                // moving from player to top
                record = LogRecord.player("container", "add", "item added to container", player.getWorld().getName(), player.getName(), player.getUniqueId(), details);
            }
        }

        if (record != null) {
            dispatch(record);
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
