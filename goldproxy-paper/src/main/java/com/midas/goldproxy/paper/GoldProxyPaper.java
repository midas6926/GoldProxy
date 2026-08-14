package com.midas.goldproxy.paper;

import com.midas.goldproxy.paper.messages.MessagesConfig;
import com.midas.goldproxy.paper.transport.PaperPluginMessageTransport;
import com.midas.goldproxy.paper.config.PaperConfig;
import com.midas.goldproxy.paper.commands.FriendsGuiCommand;
import com.midas.goldproxy.paper.listeners.FriendsInventoryListener;
import org.bukkit.plugin.java.JavaPlugin;

public class GoldProxyPaper extends JavaPlugin {

    private PaperPluginMessageTransport transport;
    private MessagesConfig messages;
    private PaperConfig config;

    @Override
    public void onEnable() {
        this.messages = MessagesConfig.loadDefault();
        this.config = PaperConfig.loadDefault();
        this.transport = new PaperPluginMessageTransport(this, config.getPluginMessageChannel(), config.getSharedSecret());
        transport.start();
        getCommand("friends").setExecutor(new FriendsGuiCommand(this, transport, messages));
        getServer().getPluginManager().registerEvents(new FriendsInventoryListener(this, transport, messages), this);
        getLogger().info("GoldProxyPaper enabled");
    }

    @Override
    public void onDisable() {
        if (transport != null) transport.stop();
        getLogger().info("GoldProxyPaper disabled");
    }

    public org.slf4j.Logger getLoggerSLF4J() { return org.slf4j.LoggerFactory.getLogger(getName()); }
}
