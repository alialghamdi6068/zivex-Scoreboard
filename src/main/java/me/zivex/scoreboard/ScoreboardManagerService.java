package me.zivex.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
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
                .replace("%money%", money(player));

        return ZivexScoreboardPlugin.color(result);
    }

    private String rank(Player player) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            String prefix = luckPerms.getPlayerAdapter(Player.class)
                    .getMetaData(player)
                    .getPrefix();
            if (prefix != null && !prefix.isBlank()) {
                return prefix;
            }
        } catch (IllegalStateException ignored) {
        }
        return plugin.getConfig().getString("placeholders.rank-default", "");
    }

    private String shards(Player player) {
        try {
            var shardsPlugin = Bukkit.getPluginManager().getPlugin("ZivexShards");
            if (shardsPlugin == null || !shardsPlugin.isEnabled()) {
                return plugin.getConfig().getString("placeholders.shards-default", "0");
            }

            Class<?> serviceClass = Class.forName(
                    "me.voidflame.zivexshards.ShardService",
                    true,
                    shardsPlugin.getClass().getClassLoader()
            );

            var registrationsMethod = Bukkit.getServicesManager().getClass()
                    .getMethod("getRegistrations", Class.class);
            var registrations = (Iterable<?>) registrationsMethod.invoke(
                    Bukkit.getServicesManager(), serviceClass
            );

            for (Object registration : registrations) {
                Object provider = registration.getClass().getMethod("getProvider").invoke(registration);
                Object value = serviceClass
                        .getMethod("getBalance", UUID.class)
                        .invoke(provider, player.getUniqueId());

                if (value instanceof Number number && number.longValue() >= 0) {
                    return String.valueOf(number.longValue());
                }
            }
        } catch (Exception ignored) {
        }

        return plugin.getConfig().getString("placeholders.shards-default", "0");
    }

    private String money(Player player) {
        File file = new File(plugin.getDataFolder(),
                plugin.getConfig().getString("placeholders.database-file", "../Zivex/database.db"));
        if (!file.isFile()) return plugin.getConfig().getString("placeholders.money-default", "0");

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT balance FROM economy WHERE uuid = ?")) {
            statement.setString(1, player.getUniqueId().toString());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    double value = Math.max(0D, result.getDouble(1));
                    return value == Math.rint(value)
                            ? String.format(java.util.Locale.ROOT, "%.0f", value)
                            : String.format(java.util.Locale.ROOT, "%.2f", value);
                }
            }
        } catch (SQLException ignored) {
        }
        return plugin.getConfig().getString("placeholders.money-default", "0");
    }

    private String uniqueEntry(int index) {
        return ChatColor.values()[index % ChatColor.values().length].toString() + ChatColor.RESET;
    }
}
