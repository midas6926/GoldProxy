package com.midas.goldproxy.proxy;

import com.google.inject.Inject;
import com.midas.goldproxy.proxy.config.ProxyConfig;
import com.midas.goldproxy.proxy.friends.FriendListener;
import com.midas.goldproxy.proxy.friends.FriendManager;
import com.midas.goldproxy.proxy.messages.MessagesConfig;
import com.midas.goldproxy.proxy.transport.ProxyPluginMessageListener;
import com.midas.goldproxy.proxy.transport.TransportManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.server.ProxyPingEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

@Plugin(id = "goldproxy", name = "GoldProxy", version = "0.1.0", description = "GoldProxy for Velocity")
public class GoldProxyVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private TransportManager transportManager;
    private FriendManager friendManager;
    private MessagesConfig messages;
    private ProxyConfig proxyConfig;

    @Inject
    public GoldProxyVelocity(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        init();
    }

    private void init() {
        // Load config and messages
        this.proxyConfig = ProxyConfig.loadDefault();
        this.messages = MessagesConfig.loadDefault();

        // Transport manager
        this.transportManager = new TransportManager(this, proxyConfig);
        transportManager.start();

        // Friends
        this.friendManager = new FriendManager(this);
        // register commands and listeners
        server.getCommandManager().register(new com.midas.goldproxy.proxy.commands.PingCommand(this), "ping");
        server.getCommandManager().register(new com.midas.goldproxy.proxy.commands.MsgCommand(this), "msg", "tell");
        server.getCommandManager().register(new com.midas.goldproxy.proxy.commands.FriendCommand(this, friendManager, messages), "friend", "friends");

        server.getEventManager().register(this, new FriendListener(this, friendManager, messages));

        // Register plugin-message listener for Paper GUI requests
        if (proxyConfig.isPluginMessageEnabled()) {
            String channel = proxyConfig.getPluginMessageChannel();
            String secret = proxyConfig.getSharedSecret();
            server.getEventManager().register(this, new ProxyPluginMessageListener(this, channel, secret));
        }
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        // TODO: load config and apply MOTD, FakePlayers, OneMorePlayer
        event.setPing(event.getPing().withSamplePlayers(event.getPing().getSamplePlayers()));
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        // TODO: ProtocolBlocker, Maintenance checks
    }

    // helper: get proxy server
    public ProxyServer getProxy() {
        return server;
    }

    public Logger getLogger() { return logger; }

    public FriendManager getFriendManager() { return friendManager; }
}
