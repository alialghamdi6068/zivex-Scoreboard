package me.zivex.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZivexScoreboardPlugin extends JavaPlugin implements CommandExecutor {
    private ScoreboardManagerService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        service = new ScoreboardManagerService(this);
        service.start();

        getServer().getPluginManager().registerEvents(new ScoreboardListener(this, service), this);

        getCommand("zivexscoreboard").setExecutor(this);
        getLogger().info("ZivexScoreboard enabled.");
    }

    @Override
    public void onDisable() {
        if (service != null) service.stop();
    }

    public ScoreboardManagerService service() {
        return service;
    }

    public String message(String key) {
        return color(getConfig().getString("messages." + key, key));
    }

    public static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    public boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("zivexscoreboard.admin");
    }

    public void send(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!isAdmin(sender)) {
            sender.sendMessage(message("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(message("usage"));
            return true;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "reload" -> {
                reloadScoreboard();
                sender.sendMessage(message("reloaded"));
            }
            case "toggle" -> {
                toggle();
                boolean enabled = getConfig().getBoolean("settings.enabled", true);
                sender.sendMessage(message(enabled ? "enabled" : "disabled"));
            }
            default -> sender.sendMessage(message("usage"));
        }
        return true;
    }

    public void reloadScoreboard() {
        reloadConfig();
        service.reload();
    }

    public void toggle() {
        boolean enabled = getConfig().getBoolean("settings.enabled", true);
        getConfig().set("settings.enabled", !enabled);
        saveConfig();
        service.reload();
    }
}
