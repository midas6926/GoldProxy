package com.midas.goldproxy.paper.transport;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.midas.goldproxy.common.security.HmacUtil;
import com.midas.goldproxy.paper.GoldProxyPaper;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class PaperPluginMessageTransport {
    private final GoldProxyPaper plugin;
    private final String channel;
    private final String sharedSecret;
    private final Logger logger;
    private final Gson gson = new Gson();

    private final Map<String, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutExec = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "goldproxy-pm-timeouts");
        t.setDaemon(true);
        return t;
    });

    public PaperPluginMessageTransport(GoldProxyPaper plugin, String channel, String sharedSecret) {
        this.plugin = plugin;
        this.channel = channel;
        this.sharedSecret = sharedSecret;
        this.logger = plugin.getLoggerSLF4J();
    }

    public void start() {
        // Register outgoing and incoming channels
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel, new PluginMessageListener() {
            @Override
            public void onPluginMessageReceived(String incomingChannel, Player player, byte[] message) {
                try {
                    String txt = new String(message, StandardCharsets.UTF_8);
                    JsonObject envelope = gson.fromJson(txt, JsonObject.class);
                    if (envelope == null || !envelope.has("body") || !envelope.has("signature")) return;
                    JsonObject body = envelope.getAsJsonObject("body");
                    String sig = envelope.getAsJsonPrimitive("signature").getAsString();
                    byte[] bodyBytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
                    if (!HmacUtil.verifyHmacBase64(sharedSecret, bodyBytes, sig)) {
                        logger.warn("Received plugin-message with invalid signature from {}", player.getName());
                        return;
                    }
                    String requestId = body.has("requestId") ? body.getAsJsonPrimitive("requestId").getAsString() : null;
                    if (requestId != null && pending.containsKey(requestId)) {
                        pending.remove(requestId).complete(body);
                    } else {
                        // handle unsolicited messages if needed
                    }
                } catch (Exception e) {
                    logger.error("Failed to handle incoming plugin message", e);
                }
            }
        });
        logger.info("PaperPluginMessageTransport listening on channel {}", channel);
    }

    public CompletableFuture<JsonObject> requestFriends(Player carrier, UUID playerUuid) {
        JsonObject body = new JsonObject();
        String requestId = UUID.randomUUID().toString();
        body.addProperty("type", "request_friends");
        body.addProperty("playerUuid", playerUuid.toString());
        body.addProperty("requestId", requestId);
        body.addProperty("timestamp", Instant.now().toEpochMilli());
        byte[] bodyBytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        String sig = HmacUtil.computeHmacBase64(sharedSecret, bodyBytes);
        JsonObject envelope = new JsonObject();
        envelope.add("body", body);
        envelope.addProperty("signature", sig);
        String txt = gson.toJson(envelope);
        CompletableFuture<JsonObject> fut = new CompletableFuture<>();
        pending.put(requestId, fut);
        // Timeout
        timeoutExec.schedule(() -> {
            CompletableFuture<JsonObject> f = pending.remove(requestId);
            if (f != null && !f.isDone()) f.completeExceptionally(new TimeoutException("Request timed out"));
        }, 5, TimeUnit.SECONDS);

        try {
            carrier.sendPluginMessage(plugin, channel, txt.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            pending.remove(requestId);
            fut.completeExceptionally(e);
        }
        return fut;
    }

    public void stop() {
        try { timeoutExec.shutdownNow(); } catch (Exception ignored) {}
    }
}
