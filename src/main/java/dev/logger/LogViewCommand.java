package dev.logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class LogViewCommand implements CommandExecutor {
    private final LoggerPlugin plugin;

    public LogViewCommand(LoggerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used in-game.");
            return true;
        }
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "You must be an operator to use this command.");
            return true;
        }
        if (args.length >= 1) {
            if ("inspect".equalsIgnoreCase(args[0]) && args.length >= 2) {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                    return true;
                }
                PlayerLogGui.openDetails(player, target.getUniqueId(), plugin.getTracker());
                return true;
            }
            if ("queue".equalsIgnoreCase(args[0])) {
                int size = plugin.getLoggerService().getDiscordQueueSize();
                player.sendMessage(ChatColor.GREEN + "Discord webhook queue size: " + size);
                return true;
            }
            if ("reload".equalsIgnoreCase(args[0])) {
                plugin.reloadLogger();
                player.sendMessage(ChatColor.GREEN + "Logger configuration reloaded.");
                return true;
            }
        }
        PlayerLogGui.openPlayerSelect(player);
        return true;
    }
}
