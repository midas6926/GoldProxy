package com.midas.goldproxy.paper;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerReportListener implements Listener {
    private final GoldProxyPaper plugin;

    public PlayerReportListener(GoldProxyPaper plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // TODO: detect client brand / modded client and report to Redis
        // String brand = event.getPlayer().getClientBrandName(); // may require a supported API
    }
}
