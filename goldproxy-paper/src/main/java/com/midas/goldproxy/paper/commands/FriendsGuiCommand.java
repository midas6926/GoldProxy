package com.midas.goldproxy.paper.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.midas.goldproxy.paper.GoldProxyPaper;
import com.midas.goldproxy.paper.messages.MessagesConfig;
import com.midas.goldproxy.paper.transport.PaperPluginMessageTransport;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

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
            // resp is a JsonObject containing friends array
            JsonArray arr = resp.has("friends") ? resp.getAsJsonArray("friends") : new JsonArray();
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject f = arr.get(i).getAsJsonObject();
                UUID fu = UUID.fromString(f.getAsJsonPrimitive("uuid").getAsString());
                String name = f.has("name") ? f.getAsJsonPrimitive("name").getAsString() : fu.toString();
                boolean online = f.has("online") && f.getAsJsonPrimitive("online").getAsBoolean();
                String server = f.has("server") ? f.getAsJsonPrimitive("server").getAsString() : "";
                long lastSeen = f.has("lastSeen") ? f.getAsJsonPrimitive("lastSeen").getAsLong() : 0L;

                ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
                SkullMeta meta = (SkullMeta) skull.getItemMeta();
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(fu));
                meta.setDisplayName(name);
                List<String> lore = new ArrayList<>();
                if (online) lore.add("Online on: " + server);
                else {
                    if (lastSeen > 0) {
                        long mins = Duration.between(Instant.ofEpochMilli(lastSeen), Instant.now()).toMinutes();
                        lore.add("Last seen: " + mins + " minutes ago");
                    } else lore.add("Offline");
                }
                meta.setLore(lore);
                skull.setItemMeta(meta);
                items.add(skull);
            }
            // open inventory on main server thread
            Bukkit.getScheduler().runTask(plugin, () -> openGui(p, items));
        });
        return true;
    }

    private void openGui(Player p, List<ItemStack> items) {
        int size = 9 * ((items.size() + 8) / 9);
        if (size == 0) size = 9;
        Inventory inv = Bukkit.createInventory(null, size, "Friends");
        for (int i = 0; i < items.size(); i++) inv.setItem(i, items.get(i));
        p.openInventory(inv);
    }
}
