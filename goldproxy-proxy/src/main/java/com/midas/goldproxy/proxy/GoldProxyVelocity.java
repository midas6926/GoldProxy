package com.midas.goldproxy.proxy;

import com.google.inject.Inject;
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

    @Inject
    public GoldProxyVelocity(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        // TODO: load config and apply MOTD, FakePlayers, OneMorePlayer
        event.setPing(event.getPing().withSamplePlayers(event.getPing().getSamplePlayers()));
        // Example modification: set MOTD if maintenance
        // Component motd = Component.text("&6GoldProxy &7- &cMaintenance");
        // event.getPing().setDescription(motd);
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        // TODO: ProtocolBlocker, Maintenance checks
        // int version = event.getConnection().getProtocolVersion();
        // if blocked -> event.setResult(ResultedEvent.ComponentResult.denied(Component.text("Your version is not allowed")));
    }

    // helper: get proxy server
    public ProxyServer getProxy() {
        return server;
    }

    public Logger getLogger() { return logger; }
}
