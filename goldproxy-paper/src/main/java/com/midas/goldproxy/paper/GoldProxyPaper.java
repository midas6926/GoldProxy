package com.midas.goldproxy.paper;

import com.midas.goldproxy.paper.messages.MessagesConfig;
import com.midas.goldproxy.paper.transport.PaperPluginMessageTransport;
import com.midas.goldproxy.paper.config.PaperConfig;
import com.midas.goldproxy.paper.commands.FriendsGuiCommand;
import com.midas.goldproxy.paper.listeners.FriendsInventoryListener;
import com.midas.goldproxy.paper.util.SkinPrefetcher;
import org.bukkit.plugin.java.JavaPlugin;

public class GoldProxyPaper extends JavaPlugin {

    private PaperPluginMessageTransport transport;
    private MessagesConfig messages;
    private PaperConfig config;
    private SkinPrefetcher prefetcher;

    @Override
    public void onEnable() {
        this.messages = MessagesConfig.loadDefault();
        this.config = PaperConfig.loadDefault();
        this.transport = new PaperPluginMessageTransport(this, config.getPluginMessageChannel(), config.getSharedSecret());
        transport.start();
        getCommand("friends").setExecutor(new FriendsGuiCommand(this, transport, messages));
        getServer().getPluginManager().registerEvents(new FriendsInventoryListener(this, transport, messages), this);

        // start skin prefetcher
        this.prefetcher = new SkinPrefetcher(this);
        prefetcher.prefetchAllFromResource("/skin-random-list.txt");

        getLogger().info("GoldProxyPaper enabled");
    }

    @Override
    public void onDisable() {
        if (transport != null) transport.stop();
        if (prefetcher != null) prefetcher.shutdown();
        getLogger().info("GoldProxyPaper disabled");
    }

    public org.slf4j.Logger getLoggerSLF4J() { return org.slf4j.LoggerFactory.getLogger(getName()); }
}
