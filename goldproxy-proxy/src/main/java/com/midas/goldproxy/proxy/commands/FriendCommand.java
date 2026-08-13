package com.midas.goldproxy.proxy.commands;

import com.midas.goldproxy.proxy.GoldProxyVelocity;
import com.midas.goldproxy.proxy.friends.FriendManager;
import com.midas.goldproxy.proxy.messages.MessagesConfig;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class FriendCommand implements Command {
    private final GoldProxyVelocity plugin;
    private final FriendManager manager;
    private final MessagesConfig messages;

    public FriendCommand(GoldProxyVelocity plugin, FriendManager manager, MessagesConfig messages) {
        this.plugin = plugin;
        this.manager = manager;
        this.messages = messages;
    }

    @Override
    public void execute(CommandSource source, String[] args) {
        if (!(source instanceof Player)) {
            source.sendMessage(Component.text(messages.get("permission.no-permission", "This command is only for players.")));
            return;
        }
        Player p = (Player) source;
        UUID meUuid = p.getUniqueId();
        if (args.length == 0) {
            showList(p, meUuid);
            return;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "add":
                if (args.length < 2) { p.sendMessage(Component.text("Usage: /friend add <player>")); return; }
                String toAdd = args[1];
                manager.addFriendAsync(meUuid, toAdd).thenAccept(success -> {
                    if (success) p.sendMessage(Component.text(messages.get("friends.added", "Added {target} to your friends.").replace("{target}", toAdd)));
                    else p.sendMessage(Component.text(messages.get("friends.already", "{target} is already your friend.").replace("{target}", toAdd)));
                });
                break;
            case "remove":
            case "del":
                if (args.length < 2) { p.sendMessage(Component.text("Usage: /friend remove <player>")); return; }
                String toRem = args[1];
                manager.removeFriendAsync(meUuid, toRem).thenAccept(removed -> {
                    if (removed) p.sendMessage(Component.text(messages.get("friends.removed", "Removed {target} from your friends.").replace("{target}", toRem)));
                    else p.sendMessage(Component.text(messages.get("friends.notfound", "{target} is not in your friends list.").replace("{target}", toRem)));
                });
                break;
            case "list":
            case "ls":
                showList(p, meUuid);
                break;
            default:
                p.sendMessage(Component.text("Unknown subcommand. Use add/remove/list."));
        }
    }

    private void showList(Player p, UUID meUuid) {
        Set<UUID> friends = manager.getFriends(meUuid);
        if (friends.isEmpty()) {
            p.sendMessage(Component.text(messages.get("friends.list.empty", "You have no friends.")));
            return;
        }
        p.sendMessage(Component.text(messages.get("friends.list.header", "Your friends:")));
        for (UUID f : friends) {
            Optional<com.velocitypowered.api.proxy.Player> online = plugin.getProxy().getPlayer(f);
            if (online.isPresent()) {
                String server = online.get().getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("unknown");
                String name = online.get().getUsername();
                p.sendMessage(Component.text(messages.get("friends.list.entry_online", "{name} - online on {server}").replace("{name}", name).replace("{server}", server)));
            } else {
                manager.getLastSeen(f).ifPresentOrElse(ls -> {
                    long mins = Duration.between(Instant.ofEpochMilli(ls), Instant.now()).toMinutes();
                    if (mins <= 5) {
                        p.sendMessage(Component.text(messages.get("friends.list.entry_recent", "{name} - left {mins} minutes ago").replace("{name}", f.toString()).replace("{mins}", String.valueOf(mins))));
                    } else {
                        p.sendMessage(Component.text(messages.get("friends.list.entry_offline", "{name} - offline").replace("{name}", f.toString())));
                    }
                }, () -> p.sendMessage(Component.text(messages.get("friends.list.entry_offline", "{name} - offline").replace("{name}", f.toString()))));
            }
        }
    }
}
