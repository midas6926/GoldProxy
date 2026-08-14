package com.midas.goldproxy.paper.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.midas.goldproxy.paper.GoldProxyPaper;
import com.midas.goldproxy.paper.messages.MessagesConfig;
import com.midas.goldproxy.paper.transport.PaperPluginMessageTransport;
import com.midas.goldproxy.paper.util.SkinUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FriendsGuiCommand implements CommandExecutor {
    private final GoldProxyPaper plugin;
    private final PaperPluginMessageTransport transport;
    private final MessagesConfig messages;

    public FriendsGuiCommand(GoldProxyPaper plugin, PaperPluginMessageTransport transport, MessagesConfig messages) {
        this.plugin = plugin;
        this.transport = transport;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command is only for players.");
            return true;
        }
        Player p = (Player) sender;
        UUID me = p.getUniqueId();
        transport.requestFriends(p, me).whenComplete((resp, ex) -> {
            if (ex != null) {
                p.sendMessage("Failed to fetch friends: " + ex.getMessage());
                return;
            }
            JsonArray arr = resp.has("friends") ? resp.getAsJsonArray("friends") : new JsonArray();
            List<ItemStack> items = new ArrayList<>();
            NamespacedKey keyUuid = new NamespacedKey(plugin, "friend_uuid");
            NamespacedKey keyServer = new NamespacedKey(plugin, "friend_server");

            for (int i = 0; i < arr.size(); i++) {
                JsonObject f = arr.get(i).getAsJsonObject();
                UUID fu = UUID.fromString(f.getAsJsonPrimitive("uuid").getAsString());
                String name = f.has("name") ? f.getAsJsonPrimitive("name").getAsString() : fu.toString();
                boolean online = f.has("online") && f.getAsJsonPrimitive("online").getAsBoolean();
                String server = f.has("server") ? f.getAsJsonPrimitive("server").getAsString() : "";
                long lastSeen = f.has("lastSeen") ? f.getAsJsonPrimitive("lastSeen").getAsLong() : 0L;
                String displayName = messages.get("friends.gui.item_name", "{name}").replace("{name}", name);

                // Custom skin URL support: server may return customSkin property (optional)
                java.util.Optional<String> customSkin = java.util.Optional.empty();
                if (f.has("customSkin")) customSkin = java.util.Optional.ofNullable(f.getAsJsonPrimitive("customSkin").getAsString());

                SkinUtil.MessagesConfigAccessor cfg = new SkinUtil.MessagesConfigAccessor(
                        plugin.getConfig().getString("skins.mode", "minotar"),
                        plugin.getConfig().getBoolean("skins.allow-custom-skins", true),
                        plugin.getConfig().getString("skins.minotar-url-template", "https://minotar.net/avatar/{name}/{size}.png")
                );

                ItemStack skull = SkinUtil.createHead(displayName, customSkin, name, cfg);

                // set lore
                List<String> lore = new ArrayList<>();
                if (online) lore.add(messages.get("friends.gui.item_lore_online", "Online on: {server}").replace("{server}", server));
                else {
                    if (lastSeen > 0) {
                        long mins = Duration.between(Instant.ofEpochMilli(lastSeen), Instant.now()).toMinutes();
                        lore.add(messages.get("friends.gui.item_lore_offline", "Last seen: {lastSeen} minutes ago").replace("{lastSeen}", String.valueOf(mins)));
                    } else lore.add(messages.get("friends.gui.item_lore_offline", "Last seen: {lastSeen} minutes ago").replace("{lastSeen}", "?") );
                }

                SkullMeta meta = (SkullMeta) skull.getItemMeta();
                if (meta != null) {
                    meta.setLore(lore);
                    // store friend uuid and server for click handling
                    meta.getPersistentDataContainer().set(keyUuid, PersistentDataType.STRING, fu.toString());
                    if (server != null && !server.isEmpty()) meta.getPersistentDataContainer().set(keyServer, PersistentDataType.STRING, server);
                    skull.setItemMeta(meta);
                }

                items.add(skull);
            }
            Bukkit.getScheduler().runTask(plugin, () -> openGui(p, items));
        });
        return true;
    }

    private void openGui(Player p, List<ItemStack> items) {
        int size = 9 * ((items.size() + 8) / 9);
        if (size == 0) size = 9;
        Inventory inv = Bukkit.createInventory(null, size, messages.get("friends.gui.title", "Friends"));
        for (int i = 0; i < items.size(); i++) inv.setItem(i, items.get(i));
        p.openInventory(inv);
    }
}
