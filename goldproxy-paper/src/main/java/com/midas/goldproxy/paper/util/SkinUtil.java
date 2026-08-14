package com.midas.goldproxy.paper.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.ProfileProperty;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class SkinUtil {
    private static final Random RANDOM = new Random();
    // A small list of public Minecraft usernames to use for "random" skins fallback via Minotar
    private static final List<String> RANDOM_NAMES = List.of(
            "Notch", "jeb_", "Dinnerbone", "Grumm", "Searge", "CaptainSparklez", "Technoblade", "AntVenom", "Dream", "GeorgeNotFound"
    );

    private SkinUtil() {}

    public static ItemStack createHead(String displayName, Optional<String> customSkinUrl, String friendName, MessagesConfigAccessor cfg) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        // Determine skin URL/source
        String mode = cfg.getSkinMode(); // e.g., "minotar", "mojang", "disabled"
        boolean allowCustom = cfg.isAllowCustomSkins();
        String finalUrl = null;

        if (allowCustom && customSkinUrl.isPresent()) {
            finalUrl = customSkinUrl.get();
        }

        if (finalUrl == null) {
            if ("minotar".equalsIgnoreCase(mode) || mode == null) {
                // Use Minotar by friendName if available else random
                String name = (friendName == null || friendName.isEmpty()) ? randomName() : friendName;
                finalUrl = cfg.getMinotarTemplate().replace("{name}", name).replace("{size}", "100");
            } else if ("mojang".equalsIgnoreCase(mode)) {
                // Attempt to use friendName via Minotar as a safer default (avoid direct Mojang API here)
                String name = (friendName == null || friendName.isEmpty()) ? randomName() : friendName;
                finalUrl = cfg.getMinotarTemplate().replace("{name}", name).replace("{size}", "100");
            } else {
                // disabled -> use random
                String name = randomName();
                finalUrl = cfg.getMinotarTemplate().replace("{name}", name).replace("{size}", "100");
            }
        }

        try {
            // Build a PlayerProfile with a textures property pointing to the chosen URL
            PlayerProfile profile = Bukkit.createProfile(java.util.UUID.randomUUID());
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + finalUrl + "\"}}}";
            String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            profile.getProperties().add(new ProfileProperty("textures", encoded));
            meta.setPlayerProfile(profile);
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            // Fallback: set owning player by name if API not available
            if (friendName != null && !friendName.isEmpty()) {
                try {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(friendName));
                } catch (Exception ignored) {}
            }
        } catch (Throwable t) {
            // ignore and fallback to default head
        }

        if (displayName != null) meta.setDisplayName(displayName);
        skull.setItemMeta(meta);
        return skull;
    }

    private static String randomName() {
        return RANDOM_NAMES.get(RANDOM.nextInt(RANDOM_NAMES.size()));
    }

    // Simple accessor interface so we don't have to depend on full config class here
    public static class MessagesConfigAccessor {
        private final String mode;
        private final boolean allowCustom;
        private final String minotarTemplate;

        public MessagesConfigAccessor(String mode, boolean allowCustom, String minotarTemplate) {
            this.mode = mode;
            this.allowCustom = allowCustom;
            this.minotarTemplate = minotarTemplate;
        }

        public String getSkinMode() { return mode; }
        public boolean isAllowCustomSkins() { return allowCustom; }
        public String getMinotarTemplate() { return minotarTemplate; }
    }
}
