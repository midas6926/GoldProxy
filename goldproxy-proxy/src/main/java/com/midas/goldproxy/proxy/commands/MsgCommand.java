package com.midas.goldproxy.proxy.commands;

import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;

public class MsgCommand implements Command {
    private final com.midas.goldproxy.proxy.GoldProxyVelocity plugin;

    public MsgCommand(com.midas.goldproxy.proxy.GoldProxyVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Usage: /msg <player> <message>"));
            return;
        }
        String target = args[0];
        StringBuilder b = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) b.append(' ');
            b.append(args[i]);
        }
        String message = b.toString();

        // TODO: publish to Redis or forward to target server
        source.sendMessage(Component.text("[-> " + target + "] " + message));
    }
}
