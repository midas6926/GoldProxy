package com.midas.goldproxy.proxy.transport;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.midas.goldproxy.common.security.HmacUtil;
import com.midas.goldproxy.proxy.GoldProxyVelocity;
import com.midas.goldproxy.proxy.friends.FriendManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PluginMessageEvent;
import com.velocitypowered.api.network.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ProxyPluginMessageListener {
    private final GoldProxyVelocity plugin;
    private final FriendManager friendManager;
    private final Gson gson = new Gson();
    private final MinecraftChannelIdentifier channelId;
    private final String sharedSecret;
    private static final long ALLOWED_SKEW_MS = 10_000; // 10s
    private static final long MIN_REQUEST_INTERVAL_MS = 2_000; // per-player rate limit

    private final ConcurrentMap<UUID, Long> lastRequest = new ConcurrentHashMap<>();

    public ProxyPluginMessageListener(GoldProxyVelocity plugin, String channel, String sharedSecret) {
        this.plugin = plugin;
        this.friendManager = plugin.getFriendManager();
        String[] parts = channel.split(":", 2);
        String namespace = parts.length > 0 ? parts[0] : "goldproxy";
        String ch = parts.length > 1 ? parts[1] : "main";
        this.channelId = MinecraftChannelIdentifier.create(namespace, ch);
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(channelId)) return;

        // Only handle messages that come from servers (i.e., backend -> proxy carrier)
        if (!(event.getSource() instanceof RegisteredServer)) return;

        Player carrier = event.getPlayer(); // the player used to carry the plugin message
        byte[] data = event.getData();
        try {
            String txt = new String(data, StandardCharsets.UTF_8);
            JsonObject envelope = gson.fromJson(txt, JsonObject.class);
            if (envelope == null || !envelope.has("body") || !envelope.has("signature")) return;

            JsonObject body = envelope.getAsJsonObject("body");
            String signature = envelope.getAsJsonPrimitive("signature").getAsString();

            byte[] bodyBytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
            if (!HmacUtil.verifyHmacBase64(sharedSecret, bodyBytes, signature)) {
                plugin.getLogger().warn("Invalid HMAC signature on plugin message from server {} (carrier {})",
                        ((RegisteredServer) event.getSource()).getServerInfo().getName(), carrier.getUsername());
                return;
            }

            // replay protection / timestamp freshness
            long ts = body.has("timestamp") ? body.getAsJsonPrimitive("timestamp").getAsLong() : 0L;
            long now = Instant.now().toEpochMilli();
            if (Math.abs(now - ts) > ALLOWED_SKEW_MS) {
                plugin.getLogger().warn("Stale or future plugin message (timestamp check) from carrier {}", carrier.getUsername());
                return;
            }

            String type = body.has("type") ? body.getAsJsonPrimitive("type").getAsString() : "";
            if ("request_friends".equals(type)) {
                handleRequestFriends(body, carrier, (RegisteredServer) event.getSource());
            }
            // Add more request types (connect request, etc.) as needed
        } catch (Exception e) {
            plugin.getLogger().error("Failed to handle incoming plugin message", e);
        }
    }

    private void handleRequestFriends(JsonObject body, Player carrier, RegisteredServer sourceServer) {
        if (!body.has("playerUuid") || !body.has("requestId")) return;
        String playerUuidStr = body.getAsJsonPrimitive("playerUuid").getAsString();
        String requestId = body.getAsJsonPrimitive("requestId").getAsString();

        // Authorization: ensure the carrier player's UUID matches the requested playerUuid
        if (!carrier.getUniqueId().toString().equalsIgnoreCase(playerUuidStr)) {
            plugin.getLogger().warn("Unauthorized friends request for {} by carrier {}", playerUuidStr, carrier.getUsername());
            return;
        }

        UUID playerUuid;
        try { playerUuid = UUID.fromString(playerUuidStr); } catch (Exception e) { return; }

        // rate-limit per-player
        long now = Instant.now().toEpochMilli();
        Long last = lastRequest.get(playerUuid);
        if (last != null && (now - last) < MIN_REQUEST_INTERVAL_MS) {
            plugin.getLogger().debug("Rate-limited friends request for {}", playerUuid);
            return;
        }
        lastRequest.put(playerUuid, now);

        // Build friends array
        JsonArray friendsArr = new JsonArray();
        Set<UUID> friends = friendManager.getFriends(playerUuid);
        for (UUID fu : friends) {
            JsonObject fobj = new JsonObject();
            fobj.addProperty("uuid", fu.toString());

            // Name: try last-known name (FriendManager should supply), fallback to UUID string
            String name = friendManager.getNameForUuid(fu).orElse(fu.toString());
            fobj.addProperty("name", name);

            Optional<com.velocitypowered.api.proxy.Player> online = plugin.getProxy().getPlayer(fu);
            fobj.addProperty("online", online.isPresent());
            fobj.addProperty("server", online.flatMap(p -> p.getCurrentServer().map(s -> s.getServerInfo().getName())).orElse(""));

            friendManager.getLastSeen(fu).ifPresent(ls -> fobj.addProperty("lastSeen", ls));

            friendsArr.add(fobj);
        }

        // Create response body
        JsonObject respBody = new JsonObject();
        respBody.addProperty("type", "response_friends");
        respBody.addProperty("requestId", requestId);
        respBody.addProperty("timestamp", Instant.now().toEpochMilli());
        respBody.add("friends", friendsArr);

        // Sign and envelope
        byte[] respBytes = gson.toJson(respBody).getBytes(StandardCharsets.UTF_8);
        String respSig = HmacUtil.computeHmacBase64(sharedSecret, respBytes);
        JsonObject respEnvelope = new JsonObject();
        respEnvelope.add("body", respBody);
        respEnvelope.addProperty("signature", respSig);
        String envelopeTxt = gson.toJson(respEnvelope);

        // Send back via the carrier's current ServerConnection (send plugin message back to backend)
        carrier.getCurrentServer().ifPresent(serverConnection -> {
            try {
                serverConnection.sendPluginMessage(channelId, envelopeTxt.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                plugin.getLogger().error("Failed to send friends response for {} back to server {}", playerUuid, serverConnection.getServerInfo().getName(), e);
            }
        });
    }
}
