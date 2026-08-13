package com.midas.goldproxy.proxy.transport;

import com.midas.goldproxy.proxy.GoldProxyVelocity;
import com.midas.goldproxy.proxy.config.ProxyConfig;
import org.slf4j.Logger;

public class TransportManager {
    private final GoldProxyVelocity plugin;
    private final ProxyConfig config;
    private final Logger logger;

    private RedisTransport redisTransport;
    private PluginMessageTransport pluginMessageTransport;

    public TransportManager(GoldProxyVelocity plugin, ProxyConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
    }

    public void start() {
        logger.info("TransportManager starting - Redis enabled={}, PluginMessage enabled={}", config.isRedisEnabled(), config.isPluginMessageEnabled());
        if (config.isRedisEnabled()) {
            redisTransport = new RedisTransport(config);
            try { redisTransport.start(); logger.info("RedisTransport started"); }
            catch (Exception e) { logger.error("Failed to start RedisTransport", e); }
        }
        if (config.isPluginMessageEnabled()) {
            pluginMessageTransport = new PluginMessageTransport(plugin, config.getPluginMessageChannel(), config.getSharedSecret());
            try { pluginMessageTransport.start(); logger.info("PluginMessageTransport started"); }
            catch (Exception e) { logger.error("Failed to start PluginMessageTransport", e); }
        }
    }

    public void stop() {
        if (redisTransport != null) redisTransport.stop();
        if (pluginMessageTransport != null) pluginMessageTransport.stop();
    }
}
