package com.midas.goldproxy.paper.transport;

import com.midas.goldproxy.paper.GoldProxyPaper;
import org.slf4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public class PaperRedisTransport {
    private final String host;
    private final int port;
    private final String password;
    private final GoldProxyPaper plugin;
    private final Logger logger;
    private Jedis jedis;
    private Thread subscriberThread;

    public PaperRedisTransport(GoldProxyPaper plugin, String host, int port, String password) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;
        this.password = password;
        this.logger = plugin.getLoggerSLF4J();
    }

    public void start() {
        jedis = new Jedis(host, port);
        if (password != null && !password.isEmpty()) jedis.auth(password);
        subscriberThread = new Thread(() -> {
            JedisPubSub sub = new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    // TODO: handle incoming messages
                }
            };
            try { jedis.subscribe(sub, "goldproxy:pm"); }
            catch (Exception e) { logger.error("Redis subscribe failed", e); }
        }, "goldproxy-paper-redis-sub");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    public void stop() {
        try { if (jedis != null) jedis.close(); } catch (Exception ignored) {}
    }
}
