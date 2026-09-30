package me.zivex.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ScoreboardManagerService {
    private final ZivexScoreboardPlugin plugin;
    private final Set<UUID> managed = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    public ScoreboardManagerService(ZivexScoreboardPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("settings.enabled", true)) return;

        long ticks = Math.max(1L, plugin.getConfig().getLong("settings.update-ticks", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) update(player);
        }, 1L, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) clear(player);
        managed.clear();
    }

    public void reload() {
        stop();
        start();
    }

    public void update(Player player) {
        if (!plugin.getConfig().getBoolean("settings.enabled", true)
                || disabledWorld(player.getWorld().getName())) {
            clear(player);
            return;
        }

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective(
                "zivex", "dummy",
                ZivexScoreboardPlugin.color(plugin.getConfig().getString("settings.title", "&5&lZivexMC"))
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = new ArrayList<>(plugin.getConfig().getStringList("settings.lines"));
        String footer = plugin.getConfig().getString("settings.footer", "");
        if (!footer.isBlank()) lines.add(footer);

        int score = lines.size();
        int index = 0;
        for (String raw : lines) {
            if (score <= 0 || index >= 15) break;

            String line = placeholders(player, raw);
            String entry = uniqueEntry(index);
            objective.getScore(entry).setScore(score);

            var team = board.registerNewTeam("line" + index);
            team.setPrefix(ZivexScoreboardPlugin.color(line));
            team.addEntry(entry);

            score--;
            index++;
        }

        player.setScoreboard(board);
        managed.add(player.getUniqueId());
    }

    public void clear(Player player) {
        if (Bukkit.getScoreboardManager() != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        managed.remove(player.getUniqueId());
    }

    private boolean disabledWorld(String world) {
        for (String configured : plugin.getConfig().getStringList("settings.disabled-worlds")) {
            if (configured.equalsIgnoreCase(world)) return true;
        }
        return false;
    }

    private String placeholders(Player player, String input) {
        String result = input
                .replace("%player%", player.getName())
                .replace("%server%", plugin.getConfig().getString("settings.server-name", "ZivexMC"))
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%ping%", String.valueOf(player.getPing()))
                .replace("%world%", player.getWorld().getName())
                .replace("%rank%", rank(player))
                .replace("%shards%", shards(player))
                .replace("%money%", plugin.getConfig().getString("placeholders.money-default", "0"));

        return ZivexScoreboardPlugin.color(result);
    }

    private String rank(Player player) {
        var ranks = plugin.getConfig().getConfigurationSection("ranks");
        if (ranks == null) return plugin.getConfig().getString("placeholders.rank-default", "Player");

        for (String key : ranks.getKeys(false)) {
            String permission = ranks.getString(key + ".permission", "");
            if (!permission.isBlank() && player.hasPermission(permission)) {
                return ranks.getString(key + ".display", key);
            }
        }
        return plugin.getConfig().getString("placeholders.rank-default", "Player");
    }

    private String shards(Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("ZivexShards")) {
            var registration = Bukkit.getServicesManager().getRegistration(
                    me.voidflame.zivexshards.ShardService.class
            );
            if (registration != null) {
                long balance = registration.getProvider().getBalance(player.getUniqueId());
                if (balance >= 0) return String.valueOf(balance);
            }
        }
        return plugin.getConfig().getString("placeholders.shards-default", "0");
    }

    private String uniqueEntry(int index) {
        return ChatColor.values()[index % ChatColor.values().length].toString() + ChatColor.RESET;
    }
}
