package com.midas.goldproxy.proxy.transport;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public class RedisTransport {
    private final String host;
    private final int port;
    private final String password;
    private Jedis jedis;
    private Thread subscriberThread;

    public RedisTransport(com.midas.goldproxy.proxy.config.ProxyConfig cfg) {
        this.host = cfg.getRedisHost();
        this.port = cfg.getRedisPort();
        this.password = cfg.getRedisPassword();
    }

    public void start() {
        jedis = new Jedis(host, port);
        if (password != null && !password.isEmpty()) jedis.auth(password);
        // simple subscriber example (runs on background thread)
        subscriberThread = new Thread(() -> {
            JedisPubSub sub = new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    // TODO: handle incoming messages
                }
            };
            try {
                jedis.subscribe(sub, "goldproxy:pm");
            } catch (Exception e) {
                // ignore
            }
        }, "goldproxy-redis-subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    public void stop() {
        try { if (jedis != null) jedis.close(); } catch (Exception ignored) {}
    }
}
