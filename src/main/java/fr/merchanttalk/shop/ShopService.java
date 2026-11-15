package fr.merchanttalk.shop;

import fr.merchanttalk.MerchantTalkPlugin;
import fr.merchanttalk.model.Merchant;
import fr.merchanttalk.model.Trigger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ouvre EconomyShopGUI pour un joueur et garde la trace des « sessions » :
 * un joueur est en session tant qu'il navigue dans le shop ouvert par un marchand.
 */
public final class ShopService {
    private final MerchantTalkPlugin plugin;
    private final Map<UUID, String> sessions = new HashMap<>();

    public ShopService(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Merchant merchant) {
        String permission = merchant.permission == null ? "" : merchant.permission.trim();
        if (!permission.isEmpty() && !player.hasPermission(permission) && !player.hasPermission("merchanttalk.bypass")) {
            if (merchant.lines(Trigger.DENIED).isEmpty()) {
                player.sendMessage(plugin.message("no-permission"));
            } else {
                plugin.dialogue().speak(merchant, player, Trigger.DENIED);
            }
            return;
        }
        if (!plugin.schedule().isOpen(merchant)) {
            if (merchant.lines(Trigger.CLOSED).isEmpty()) {
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        plugin.getConfig().getString("messages.prefix", "") + plugin.getConfig().getString("messages.closed", "<red>Fermé."),
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("hours",
                                fr.merchanttalk.features.CustomerService.hoursLabel(merchant.openHours))));
            } else {
                plugin.dialogue().speak(merchant, player, Trigger.CLOSED);
            }
            return;
        }
        sessions.put(player.getUniqueId(), merchant.id);
        plugin.customers().visit(merchant, player);
        merchant.stats().opens++;
        merchant.stats().lastActivity = System.currentTimeMillis();
        plugin.store().markDirty();
        String section = merchant.section == null ? "" : merchant.section.trim();
        String template = merchant.isSellMenu()
                ? plugin.getConfig().getString("shop.open-command-sellgui", "sellgui {player}")
                : section.isEmpty()
                ? plugin.getConfig().getString("shop.open-command-main", "shop {player}")
                : plugin.getConfig().getString("shop.open-command-section", "shop {section} {player}");
        String command = template.replace("{player}", player.getName()).replace("{section}", section);
        if (command.startsWith("/")) command = command.substring(1);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        plugin.dialogue().speak(merchant, player, Trigger.OPEN);
        if (plugin.promo().active(merchant)) {
            String promoMsg = plugin.getConfig().getString("messages.promo-active", "");
            if (promoMsg != null && !promoMsg.isBlank()) {
                player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(promoMsg,
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("promo", plugin.promo().describe(merchant))));
            }
        }
    }

    /** @return le marchand avec lequel le joueur est en train de commercer, ou null. */
    public Merchant session(Player player) {
        String id = sessions.get(player.getUniqueId());
        return id == null ? null : plugin.store().merchant(id);
    }

    public boolean inSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /**
     * Appelé quelques ticks après la fermeture d'un inventaire. EconomyShopGUI ferme et rouvre
     * des menus en naviguant : la session ne se termine que si plus aucun menu n'est ouvert.
     */
    public void checkClosed(Player player) {
        if (!player.isOnline() || !inSession(player)) return;
        if (player.getOpenInventory().getType() != InventoryType.CRAFTING) return;
        Merchant merchant = session(player);
        sessions.remove(player.getUniqueId());
        if (merchant != null) plugin.dialogue().speak(merchant, player, Trigger.CLOSE);
    }

    public void end(UUID player) {
        sessions.remove(player);
    }

    /** Ferme le shop de tous les joueurs en session avec ce marchand. */
    public void closeFor(String merchantId) {
        for (Map.Entry<UUID, String> e : List.copyOf(sessions.entrySet())) {
            if (e.getValue().equals(merchantId)) {
                Player p = Bukkit.getPlayer(e.getKey());
                sessions.remove(e.getKey());
                if (p != null) p.closeInventory();
            }
        }
    }

    public Map<UUID, String> sessions() {
        return Collections.unmodifiableMap(sessions);
    }
}
