package me.zivex.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZivexScoreboardPlugin extends JavaPlugin {
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
