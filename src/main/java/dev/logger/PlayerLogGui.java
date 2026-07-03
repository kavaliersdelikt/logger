package dev.logger;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PlayerLogGui {
    private static final ConcurrentMap<UUID, UUID> detailViewers = new ConcurrentHashMap<>();

    public static void openPlayerSelect(Player viewer) {
        Inventory inventory = Bukkit.createInventory(null, 27, "Logger Player View");
        int slot = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (slot >= inventory.getSize()) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + player.getName());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "UUID: " + player.getUniqueId());
                lore.add(ChatColor.GRAY + "World: " + player.getWorld().getName());
                lore.add(ChatColor.GRAY + "Click to inspect logs");
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inventory.setItem(slot++, head);
        }
        viewer.openInventory(inventory);
    }

    public static void openDetails(Player viewer, UUID targetUuid, PlayerLogTracker tracker) {
        detailViewers.put(viewer.getUniqueId(), targetUuid);
        Inventory inventory = Bukkit.createInventory(null, 54, "Logger Details");
        populateDetailsInventory(inventory, tracker.getLogs(targetUuid));
        viewer.openInventory(inventory);
    }

    private static void populateDetailsInventory(Inventory inventory, List<LogRecord> logs) {
        inventory.clear();
        int slot = 0;
        if (logs.isEmpty()) {
            ItemStack empty = new ItemStack(Material.PAPER);
            ItemMeta meta = empty.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.RED + "No recent logs available");
                meta.setLore(List.of(ChatColor.GRAY + "This player has no cached records."));
                empty.setItemMeta(meta);
            }
            inventory.setItem(slot, empty);
            return;
        }

        for (LogRecord record : logs) {
            if (slot >= inventory.getSize()) break;
            ItemStack logItem = new ItemStack(Material.PAPER);
            ItemMeta meta = logItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + record.getAction() + " - " + record.getSubject());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Time: " + record.getTimestamp());
                lore.add(ChatColor.GRAY + "Source: " + record.getSource());
                lore.add(ChatColor.GRAY + "World: " + (record.getWorld() != null ? record.getWorld() : "server"));
                if (record.getPlayerName() != null) lore.add(ChatColor.GRAY + "Player: " + record.getPlayerName());
                record.getDetails().forEach((key, value) -> lore.add(ChatColor.GRAY + key + ": " + value));
                meta.setLore(lore);
                logItem.setItemMeta(meta);
            }
            inventory.setItem(slot++, logItem);
        }
    }

    public static void refreshDetails(UUID targetUuid, PlayerLogTracker tracker) {
        // Live refresh disabled by user preference — GUI updates only when reopened.
    }

    public static void closeDetails(Player viewer) {
        detailViewers.remove(viewer.getUniqueId());
    }
}
