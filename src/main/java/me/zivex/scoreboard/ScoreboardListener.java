package me.zivex.scoreboard;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ScoreboardListener implements Listener {
    private final ZivexScoreboardPlugin plugin;
    private final ScoreboardManagerService service;

    public ScoreboardListener(ZivexScoreboardPlugin plugin, ScoreboardManagerService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        service.update(event.getPlayer());
    }

    @EventHandler
    public void world(PlayerChangedWorldEvent event) {
        service.update(event.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        service.clear(event.getPlayer());
    }
}
