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
        if (args.length >= 1 && "help".equalsIgnoreCase(args[0])) {
            sendHelp(sender);
            return true;
        }

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
                if (args.length >= 2 && "clear".equalsIgnoreCase(args[1])) {
                    plugin.getLoggerService().clearDiscordQueue();
                    player.sendMessage(ChatColor.GREEN + "Discord webhook queue cleared.");
                    return true;
                }
                int size = plugin.getLoggerService().getDiscordQueueSize();
                player.sendMessage(ChatColor.GREEN + "Discord webhook queue size: " + size);
                return true;
            }
            if ("clearqueue".equalsIgnoreCase(args[0])) {
                plugin.getLoggerService().clearDiscordQueue();
                player.sendMessage(ChatColor.GREEN + "Discord webhook queue cleared.");
                return true;
            }
            if ("web".equalsIgnoreCase(args[0]) || "panel".equalsIgnoreCase(args[0])) {
                String url = plugin.getWebPanelUrl();
                if ("disabled".equals(url)) {
                    player.sendMessage(ChatColor.YELLOW + "The web panel is currently disabled.");
                } else {
                    player.sendMessage(ChatColor.GREEN + "Web panel: " + url);
                }
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

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Logger Help");
        sender.sendMessage(ChatColor.YELLOW + "/logger" + ChatColor.GRAY + " - open the player selector GUI");
        sender.sendMessage(ChatColor.YELLOW + "/logger inspect <player>" + ChatColor.GRAY + " - inspect a player");
        sender.sendMessage(ChatColor.YELLOW + "/logger queue" + ChatColor.GRAY + " - show Discord queue size");
        sender.sendMessage(ChatColor.YELLOW + "/logger queue clear" + ChatColor.GRAY + " - clear the Discord queue");
        sender.sendMessage(ChatColor.YELLOW + "/logger clearqueue" + ChatColor.GRAY + " - clear the Discord queue");
        sender.sendMessage(ChatColor.YELLOW + "/logger web" + ChatColor.GRAY + " - show the web panel URL");
        sender.sendMessage(ChatColor.YELLOW + "/logger reload" + ChatColor.GRAY + " - reload the plugin config");
        sender.sendMessage(ChatColor.YELLOW + "/logger help" + ChatColor.GRAY + " - show this help menu");
    }
}
