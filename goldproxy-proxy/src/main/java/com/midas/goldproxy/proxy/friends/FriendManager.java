package com.midas.goldproxy.proxy.friends;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.midas.goldproxy.proxy.GoldProxyVelocity;

import java.io.FileWriter;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class FriendManager {
    private final GoldProxyVelocity plugin;
    private final Path dataDir;
    private final Path file;
    private final Gson gson = new Gson();

    // uuid string -> set of uuid strings
    private final Map<String, Set<String>> friends = new ConcurrentHashMap<>();
    // uuid string -> last seen epoch millis
    private final Map<String, Long> lastSeen = new ConcurrentHashMap<>();

    // recent username -> uuid cache
    private final Map<String, String> nameToUuidCache = new ConcurrentHashMap<>();

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "goldproxy-friend-save");
        t.setDaemon(true);
        return t;
    });
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private volatile boolean dirty = false;
    private volatile ScheduledFuture<?> pendingSave;

    public FriendManager(GoldProxyVelocity plugin) {
        this.plugin = plugin;
        this.dataDir = Path.of(System.getProperty("user.dir"), "plugins", "goldproxy");
        this.file = dataDir.resolve("friends.json");
        load();
    }

    private void load() {
        try {
            if (!Files.exists(dataDir)) Files.createDirectories(dataDir);
            if (!Files.exists(file)) return;
            String json = Files.readString(file);
            Type t = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> root = gson.fromJson(json, t);
            if (root == null) return;
            Object f = root.get("friends");
            if (f instanceof Map) {
                Map<String, List<String>> fm = (Map<String, List<String>>) f;
                for (Map.Entry<String, List<String>> e : fm.entrySet()) {
                    String k = e.getKey();
                    Set<String> set = ConcurrentHashMap.newKeySet();
                    for (String v : e.getValue()) set.add(v);
                    friends.put(k, set);
                }
            }
            Object ls = root.get("lastSeen");
            if (ls instanceof Map) {
                Map<String, Number> lm = (Map<String, Number>) ls;
                for (Map.Entry<String, Number> e : lm.entrySet()) {
                    lastSeen.put(e.getKey(), e.getValue().longValue());
                }
            }
            Object cache = root.get("nameToUuid");
            if (cache instanceof Map) {
                Map<String, String> cm = (Map<String, String>) cache;
                nameToUuidCache.putAll(cm);
            }
        } catch (Exception e) {
            plugin.getLogger().warn("Failed to load friends data", e);
        }
    }

    private synchronized void scheduleSave() {
        dirty = true;
        if (pendingSave != null && !pendingSave.isDone()) return;
        pendingSave = executor.schedule(this::save, 5, TimeUnit.SECONDS);
    }

    private synchronized void save() {
        if (!dirty) return;
        try (FileWriter w = new FileWriter(file.toFile())) {
            Map<String, Object> root = new HashMap<>();
            Map<String, List<String>> fm = new HashMap<>();
            for (Map.Entry<String, Set<String>> e : friends.entrySet()) {
                fm.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
            Map<String, Long> lm = new HashMap<>(lastSeen);
            root.put("friends", fm);
            root.put("lastSeen", lm);
            root.put("nameToUuid", new HashMap<>(nameToUuidCache));
            gson.toJson(root, w);
            dirty = false;
        } catch (Exception e) {
            plugin.getLogger().warn("Failed to save friends data", e);
        }
    }

    public CompletableFuture<Boolean> addFriendAsync(UUID ownerUuid, String targetName) {
        // resolve target uuid asynchronously (cache first)
        CompletableFuture<Optional<UUID>> resolved = resolveUuidByNameAsync(targetName);
        return resolved.thenApply(opt -> {
            if (opt.isEmpty()) return false;
            UUID targetUuid = opt.get();
            String owner = ownerUuid.toString();
            String target = targetUuid.toString();
            if (owner.equals(target)) return false;
            friends.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(target);
            scheduleSave();
            return true;
        });
    }

    public CompletableFuture<Boolean> removeFriendAsync(UUID ownerUuid, String targetName) {
        CompletableFuture<Optional<UUID>> resolved = resolveUuidByNameAsync(targetName);
        return resolved.thenApply(opt -> {
            if (opt.isEmpty()) return false;
            UUID targetUuid = opt.get();
            String owner = ownerUuid.toString();
            String target = targetUuid.toString();
            Set<String> set = friends.get(owner);
            if (set == null) return false;
            boolean removed = set.remove(target);
            if (set.isEmpty()) friends.remove(owner);
            scheduleSave();
            return removed;
        });
    }

    public Set<UUID> getFriends(UUID ownerUuid) {
        Set<String> set = friends.getOrDefault(ownerUuid.toString(), Collections.emptySet());
        Set<UUID> out = new HashSet<>();
        for (String s : set) {
            try { out.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        return out;
    }

    public void setLastSeen(UUID uuid, long epochMilli) {
        lastSeen.put(uuid.toString(), epochMilli);
        scheduleSave();
    }

    public OptionalLong getLastSeen(UUID uuid) {
        Long v = lastSeen.get(uuid.toString());
        return v == null ? OptionalLong.empty() : OptionalLong.of(v);
    }

    public boolean isFriend(UUID owner, UUID target) {
        Set<String> s = friends.getOrDefault(owner.toString(), Collections.emptySet());
        return s.contains(target.toString());
    }

    public CompletableFuture<Optional<UUID>> resolveUuidByNameAsync(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (nameToUuidCache.containsKey(lower)) {
            try { return CompletableFuture.completedFuture(Optional.of(UUID.fromString(nameToUuidCache.get(lower)))); }
            catch (Exception e) { /* fallthrough */ }
        }
        // check online players
        Optional<com.velocitypowered.api.proxy.Player> online = plugin.getProxy().getPlayer(name);
        if (online.isPresent()) {
            UUID id = online.get().getUniqueId();
            nameToUuidCache.put(lower, id.toString());
            scheduleSave();
            return CompletableFuture.completedFuture(Optional.of(id));
        }
        // query Mojang API asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    // response: { id: "<uuid-without-hyphens>", name: "..." }
                    Map<String, Object> map = gson.fromJson(resp.body(), Map.class);
                    Object idObj = map.get("id");
                    if (idObj instanceof String) {
                        String raw = (String) idObj;
                        UUID uuid = uuidFromMojangRaw(raw);
                        nameToUuidCache.put(lower, uuid.toString());
                        scheduleSave();
                        return Optional.of(uuid);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().debug("Failed to resolve UUID for " + name + ": " + e.getMessage());
            }
            return Optional.empty();
        }, executor);
    }

    private static UUID uuidFromMojangRaw(String raw) {
        // raw like "f84c6a790a7e4d9e8ee3b3d1f4a0c123"
        if (raw.length() != 32) throw new IllegalArgumentException("Invalid raw UUID");
        StringBuilder sb = new StringBuilder(raw);
        sb.insert(8, '-');
        sb.insert(13, '-');
        sb.insert(18, '-');
        sb.insert(23, '-');
        return UUID.fromString(sb.toString());
    }

    public void shutdown() {
        try { if (pendingSave != null) pendingSave.get(2, TimeUnit.SECONDS); } catch (Exception ignored) {}
        save();
        executor.shutdownNow();
    }
}
