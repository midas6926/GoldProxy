package com.midas.goldproxy.proxy.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

public class ProxyConfig {
    private boolean redisEnabled = false;
    private String redisHost = "localhost";
    private int redisPort = 6379;
    private String redisPassword = "";

    private boolean pluginMessageEnabled = true;
    private String pluginMessageChannel = "goldproxy:main";
    private String sharedSecret = "";

    public static ProxyConfig loadDefault() {
        try (InputStream in = ProxyConfig.class.getResourceAsStream("/config.yml")) {
            if (in == null) return new ProxyConfig();
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.load(in);
            ProxyConfig c = new ProxyConfig();
            if (map == null) return c;
            Object transportsObj = map.get("transports");
            if (transportsObj instanceof Map) {
                Map<String, Object> transports = (Map<String, Object>) transportsObj;
                Object redisObj = transports.get("redis");
                if (redisObj instanceof Map) {
                    Map<String, Object> redis = (Map<String, Object>) redisObj;
                    c.redisEnabled = Boolean.TRUE.equals(redis.get("enabled"));
                    Object host = redis.get("host");
                    if (host != null) c.redisHost = host.toString();
                    Object port = redis.get("port");
                    if (port instanceof Number) c.redisPort = ((Number) port).intValue();
                    Object pass = redis.get("password");
                    if (pass != null) c.redisPassword = pass.toString();
                }
                Object pmObj = transports.get("plugin-message");
                if (pmObj instanceof Map) {
                    Map<String, Object> pm = (Map<String, Object>) pmObj;
                    c.pluginMessageEnabled = Boolean.TRUE.equals(pm.get("enabled"));
                    Object channel = pm.get("channel");
                    if (channel != null) c.pluginMessageChannel = channel.toString();
                    Object secret = pm.get("shared-secret");
                    if (secret != null) c.sharedSecret = secret.toString();
                }
            }
            return c;
        } catch (Exception e) {
            e.printStackTrace();
            return new ProxyConfig();
        }
    }

    public boolean isRedisEnabled() { return redisEnabled; }
    public String getRedisHost() { return redisHost; }
    public int getRedisPort() { return redisPort; }
    public String getRedisPassword() { return redisPassword; }
    public boolean isPluginMessageEnabled() { return pluginMessageEnabled; }
    public String getPluginMessageChannel() { return pluginMessageChannel; }
    public String getSharedSecret() { return sharedSecret; }
}
