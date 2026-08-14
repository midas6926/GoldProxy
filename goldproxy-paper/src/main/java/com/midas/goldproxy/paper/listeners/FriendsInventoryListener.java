package com.midas.goldproxy.paper.listeners;

import com.google.gson.JsonObject;
import com.midas.goldproxy.paper.GoldProxyPaper;
import com.midas.goldproxy.paper.messages.MessagesConfig;
import com.midas.goldproxy.paper.transport.PaperPluginMessageTransport;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class FriendsInventoryListener implements Listener {
    private final GoldProxyPaper plugin;
    private final PaperPluginMessageTransport transport;
    private final MessagesConfig messages;
    private final NamespacedKey keyUuid;
    private final NamespacedKey keyServer;

    public FriendsInventoryListener(GoldProxyPaper plugin, PaperPluginMessageTransport transport, MessagesConfig messages) {
        this.plugin = plugin;
        this.transport = transport;
        this.messages = messages;
        this.keyUuid = new NamespacedKey(plugin, "friend_uuid");
        this.keyServer = new NamespacedKey(plugin, "friend_server");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player clicker = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        if (title == null || !title.equals("Friends")) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        if (!(clicked.getItemMeta() instanceof SkullMeta)) return;
        SkullMeta meta = (SkullMeta) clicked.getItemMeta();
        if (!meta.getPersistentDataContainer().has(keyUuid, PersistentDataType.STRING)) return;
        String uuidStr = meta.getPersistentDataContainer().get(keyUuid, PersistentDataType.STRING);
        String server = meta.getPersistentDataContainer().has(keyServer, PersistentDataType.STRING) ? meta.getPersistentDataContainer().get(keyServer, PersistentDataType.STRING) : "";
        if (uuidStr == null) return;
        UUID friendUuid = UUID.fromString(uuidStr);
        // left click -> attempt to connect to friend's server
        // create and send request_connect
        transport.requestConnect(clicker, clicker.getUniqueId(), server).whenComplete((resp, ex) -> {
            if (ex != null) {
                clicker.sendMessage("Failed to request connect: " + ex.getMessage());
                return;
            }
            JsonObject body = resp.getAsJsonObject("body");
            JsonObject respBody = body; // body contains response fields
            boolean success = respBody.has("success") && respBody.getAsJsonPrimitive("success").getAsBoolean();
            if (success) clicker.sendMessage(messages.get("friends.gui.connect_success", "Connecting to {server}...").replace("{server}", server));
            else clicker.sendMessage(messages.get("friends.gui.connect_fail", "Failed to connect to {server}.").replace("{server}", server));
        });
    }
}
