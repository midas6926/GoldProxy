package com.midas.goldproxy.proxy.commands;

import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

public class PingCommand implements Command {
    private final com.midas.goldproxy.proxy.GoldProxyVelocity plugin;

    public PingCommand(com.midas.goldproxy.proxy.GoldProxyVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSource source, String[] args) {
        if (args.length == 0) {
            if (source instanceof Player) {
                Player p = (Player) source;
                p.sendMessage(Component.text("Your ping: " + p.getPing() + "ms"));
            } else {
                source.sendMessage(Component.text("Console ping: N/A"));
            }
            return;
        }
        String serverName = args[0];
        plugin.getProxy().getServer(serverName).ifPresentOrElse(serverInfo ->
                source.sendMessage(Component.text("Server found: " + serverName)),
            () -> source.sendMessage(Component.text("Server not found: " + serverName)));
    }
}
