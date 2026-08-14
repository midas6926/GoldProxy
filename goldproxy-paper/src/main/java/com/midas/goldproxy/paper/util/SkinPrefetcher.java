package com.midas.goldproxy.paper.util;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class SkinPrefetcher {
    private final Plugin plugin;
    private final HttpClient client;
    private final ExecutorService exec;
    private final Path cacheDir;
    private final int maxSizeBytes;

    public SkinPrefetcher(Plugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.exec = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "goldproxy-skin-prefetch");
            t.setDaemon(true);
            return t;
        });
        this.cacheDir = plugin.getDataFolder().toPath().resolve("skins");
        this.maxSizeBytes = 64 * 1024; // 64 KB default limit
        try { Files.createDirectories(cacheDir); } catch (IOException ignored) {}
    }

    public void prefetchAllFromResource(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) return;
            List<String> names = new ArrayList<>();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    names.add(line);
                }
            }
            prefetchNames(names);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read skin list resource", e);
        }
    }

    public void prefetchNames(List<String> names) {
        for (String n : names) {
            exec.submit(() -> prefetchName(n));
        }
    }

    private void prefetchName(String name) {
        try {
            String url = "https://minotar.net/avatar/" + java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8) + "/100.png";
            Path out = cacheDir.resolve(name + ".png");
            if (Files.exists(out) && Files.size(out) > 0) return; // already cached
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().length > 0 && resp.body().length <= maxSizeBytes) {
                Files.write(out, resp.body());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Failed to prefetch skin for " + name + ": " + e.getMessage());
        }
    }

    public void shutdown() {
        try { exec.shutdownNow(); } catch (Exception ignored) {}
    }
}
