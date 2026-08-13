package com.midas.goldproxy.proxy.friends;

import com.midas.goldproxy.proxy.GoldProxyVelocity;
import com.midas.goldproxy.proxy.messages.MessagesConfig;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

public class FriendListener {
    private final GoldProxyVelocity plugin;
    private final FriendManager manager;
    private final MessagesConfig messages;

    public FriendListener(GoldProxyVelocity plugin, FriendManager manager, MessagesConfig messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (!event.getResult().isAllowed()) return;
        Player p = (Player) event.getPlayer();
        String name = p.getUsername();
        java.util.UUID myUuid = p.getUniqueId();
        // Notify this player's friends who are online that I joined
        Set<java.util.UUID> friends = manager.getFriends(myUuid);
        for (java.util.UUID f : friends) {
            plugin.getProxy().getPlayer(f).ifPresent(friendPlayer -> {
                friendPlayer.sendMessage(Component.text(messages.get("friends.notify_online", "Your friend {name} has joined on {server}").replace("{name}", name).replace("{server}", p.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("unknown"))));
            });
        }

        // For the joining player, tell them about friends online or recently left
        for (java.util.UUID f : friends) {
            plugin.getProxy().getPlayer(f).ifPresent(friendPlayer -> {
                p.sendMessage(Component.text(messages.get("friends.notify_you_friend_online", "Your friend {name} is online on {server}").replace("{name}", friendPlayer.getUsername()).replace("{server}", friendPlayer.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("unknown"))));
            });

            if (!plugin.getProxy().getPlayer(f).isPresent()) {
                manager.getLastSeen(f).ifPresent(ls -> {
                    long mins = Duration.between(Instant.ofEpochMilli(ls), Instant.now()).toMinutes();
                    if (mins <= 5) {
                        // We don't have the recent username stored reliably, print UUID as fallback
                        p.sendMessage(Component.text(messages.get("friends.notify_you_recently_left", "Your friend {name} left {mins} minutes ago").replace("{name}", f.toString()).replace("{mins}", String.valueOf(mins))));
                    }
                });
            }
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player p = event.getPlayer();
        if (p == null) return;
        java.util.UUID id = p.getUniqueId();
        String name = p.getUsername();
        manager.setLastSeen(id, Instant.now().toEpochMilli());
        // cache name->uuid for future
        manager.nameToUuidCache.put(name.toLowerCase(), id.toString());
        for (java.util.UUID f : manager.getFriends(id)) {
            plugin.getProxy().getPlayer(f).ifPresent(friendPlayer -> {
                friendPlayer.sendMessage(Component.text(messages.get("friends.notify_left", "Your friend {name} has left the network.").replace("{name}", name)));
            });
        }
    }
}
