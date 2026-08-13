package com.midas.goldproxy.proxy.messages;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

public class MessagesConfig {
    private final Map<String, Object> root;

    private MessagesConfig(Map<String, Object> root) {
        this.root = root;
    }

    public static MessagesConfig loadDefault() {
        try (InputStream in = MessagesConfig.class.getResourceAsStream("/messages.yml")) {
            if (in == null) return new MessagesConfig(Collections.emptyMap());
            Yaml yaml = new Yaml();
            Object o = yaml.load(in);
            if (!(o instanceof Map)) return new MessagesConfig(Collections.emptyMap());
            return new MessagesConfig((Map<String, Object>) o);
        } catch (Exception e) {
            e.printStackTrace();
            return new MessagesConfig(Collections.emptyMap());
        }
    }

    @SuppressWarnings("unchecked")
    public String get(String path, String def) {
        try {
            String[] parts = path.split("\\.");
            Map<String, Object> cur = root;
            for (int i = 0; i < parts.length - 1; i++) {
                Object o = cur.get(parts[i]);
                if (!(o instanceof Map)) return def;
                cur = (Map<String, Object>) o;
            }
            Object last = cur.get(parts[parts.length - 1]);
            return last == null ? def : last.toString();
        } catch (Exception e) {
            return def;
        }
    }
}
