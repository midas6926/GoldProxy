package com.midas.goldproxy.proxy.transport;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.midas.goldproxy.proxy.GoldProxyVelocity;
import com.midas.goldproxy.proxy.friends.FriendManager;
import com.midas.goldproxy.proxy.config.ProxyConfig;
import com.midas.goldproxy.common.security.HmacUtil;
import org.slf4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RedisTransport {
    private final GoldProxyVelocity plugin;
    private final String host;
    private final int port;
    private final String password;
    private Jedis jedisPub;
    private Jedis jedisSub;
    private Thread subscriberThread;
    private final Gson gson = new Gson();
    private final Logger logger;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "goldproxy-redis-exec");
        t.setDaemon(true);
        return t;
    });

    public RedisTransport(GoldProxyVelocity plugin, ProxyConfig cfg) {
        this.plugin = plugin;
        this.host = cfg.getRedisHost();
        this.port = cfg.getRedisPort();
        this.password = cfg.getRedisPassword();
        this.logger = plugin.getLogger();
    }

    public void start() {
        try {
            jedisPub = new Jedis(host, port);
            jedisSub = new Jedis(host, port);
            if (password != null && !password.isEmpty()) {
                jedisPub.auth(password);
                jedisSub.auth(password);
            }

            // Subscriber thread
            subscriberThread = new Thread(() -> {
                JedisPubSub sub = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        try {
                            // message is an envelope { body: {...}, signature: "..." }
                            JsonObject envelope = gson.fromJson(message, JsonObject.class);
                            if (envelope == null || !envelope.has("body") || !envelope.has("signature")) return;
                            JsonObject body = envelope.getAsJsonObject("body");
                            String sig = envelope.getAsJsonPrimitive("signature").getAsString();
                            byte[] bodyBytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
                            String shared = plugin.getProxyConfig().getSharedSecret();
                            if (!HmacUtil.verifyHmacBase64(shared, bodyBytes, sig)) {
                                logger.warn("Received redis friend event with invalid signature");
                                return;
                            }
                            String type = body.has("type") ? body.getAsJsonPrimitive("type").getAsString() : "";
                            if ("friend_join".equals(type) || "friend_leave".equals(type)) {
                                // delegate to FriendManager to handle remote event
                                exec.submit(() -> plugin.getFriendManager().handleRemoteEvent(body));
                            }
                        } catch (Exception e) {
                            logger.debug("Failed to process redis message", e);
                        }
                    }
                };
                try {
                    jedisSub.subscribe(sub, "goldproxy:friend_events");
                } catch (Exception e) {
                    logger.error("Redis subscribe failed", e);
                }
            }, "goldproxy-redis-subscriber");
            subscriberThread.setDaemon(true);
            subscriberThread.start();
            logger.info("Connected to Redis at {}:{}", host, port);
        } catch (Exception e) {
            logger.error("Failed to start RedisTransport", e);
        }
    }

    public void publishFriendEvent(JsonObject body) {
        try {
            String shared = plugin.getProxyConfig().getSharedSecret();
            byte[] bodyBytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
            String sig = HmacUtil.computeHmacBase64(shared, bodyBytes);
            JsonObject env = new JsonObject();
            env.add("body", body);
            env.addProperty("signature", sig);
            jedisPub.publish("goldproxy:friend_events", gson.toJson(env));
        } catch (Exception e) {
            logger.debug("Failed to publish friend event", e);
        }
    }

    public void stop() {
        try { if (jedisPub != null) jedisPub.close(); } catch (Exception ignored) {}
        try { if (jedisSub != null) jedisSub.close(); } catch (Exception ignored) {}
        try { if (subscriberThread != null) subscriberThread.interrupt(); } catch (Exception ignored) {}
        exec.shutdownNow();
    }
}
