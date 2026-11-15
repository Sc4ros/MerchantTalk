package fr.merchanttalk.shop;

import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import de.oliver.fancynpcs.api.events.NpcsLoadedEvent;
import fr.merchanttalk.MerchantTalkPlugin;
import fr.merchanttalk.model.Merchant;
import fr.merchanttalk.model.Trigger;
import fr.merchanttalk.npc.NpcService;
import me.gypopo.economyshopgui.api.events.PostTransactionEvent;
import me.gypopo.economyshopgui.api.events.PreTransactionEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Clic sur les PNJ, transactions EconomyShopGUI et fermeture du shop. */
public final class ShopListener implements Listener {
    /** Types de transaction faits depuis le shop ou les commandes (pas les shopstands / API / coffres). */
    private static final Set<String> PLAYER_TRANSACTIONS = Set.of(
            "SELL_GUI_SCREEN", "SELL_ALL_COMMAND", "SELL_ALL_SCREEN", "SELL_SCREEN",
            "BUY_SCREEN", "BUY_STACKS_SCREEN", "QUICK_SELL", "QUICK_BUY");
    private static final Set<String> FAIL_RESULTS = Set.of(
            "NO_INVENTORY_SPACE", "NOT_ENOUGH_ITEMS", "NO_ITEMS_FOUND", "TO_MANY_ITEMS",
            "NO_ITEM_STOCK_LEFT", "REACHED_SELL_LIMIT", "CANT_STORE_PAYMENT", "REQUIREMENTS_FAILED", "NO_PERMISSIONS");

    private final MerchantTalkPlugin plugin;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public ShopListener(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNpcsLoaded(NpcsLoadedEvent event) {
        runSync(() -> plugin.npcs().onFancyNpcsLoaded());
    }

    @EventHandler
    public void onNpcInteract(NpcInteractEvent event) {
        String merchantId = NpcService.merchantId(event.getNpc());
        if (merchantId == null) return;
        Player player = event.getPlayer();
        runSync(() -> {
            long now = System.currentTimeMillis();
            Long last = lastClick.put(player.getUniqueId(), now);
            if (last != null && now - last < 600) return; // clic gauche + droit = un seul clic
            Merchant merchant = plugin.store().merchant(merchantId);
            if (merchant == null || !player.isOnline()) return;
            plugin.shop().open(player, merchant);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreTransaction(PreTransactionEvent event) {
        if (!plugin.getConfig().getBoolean("shop.block-transactions-outside-npc", true)) return;
        Player player = event.getPlayer();
        if (player == null || player.hasPermission("merchanttalk.bypass")) return;
        if (!PLAYER_TRANSACTIONS.contains(event.getTransactionType().name())) return;
        if (!plugin.shop().inSession(player)) {
            event.setCancelled(true);
            player.sendMessage(plugin.message("transaction-blocked"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPostTransaction(PostTransactionEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        String type = event.getTransactionType().name();
        String result = event.getTransactionResult().name();
        double amount = totalPrice(event);
        runSync(() -> {
            Merchant merchant = plugin.shop().session(player);
            if (merchant == null) return;
            var stats = merchant.stats();
            Trigger trigger;
            if (result.startsWith("SUCCESS")) {
                boolean sell = type.contains("SELL");
                trigger = sell ? Trigger.SELL : Trigger.BUY;
                if (sell) {
                    stats.sells++;
                    stats.earned += amount;
                } else {
                    stats.buys++;
                    stats.spent += amount;
                }
                stats.lastActivity = System.currentTimeMillis();
                plugin.store().markDirty();
            } else if (result.equals("INSUFFICIENT_FUNDS")) {
                trigger = Trigger.NO_MONEY;
                stats.noMoney++;
                plugin.store().markDirty();
            } else if (FAIL_RESULTS.contains(result)) {
                trigger = Trigger.FAIL;
            } else {
                return;
            }
            plugin.dialogue().speak(merchant, player, trigger);
        });
    }

    /** Montant total de la transaction (une ou plusieurs monnaies additionnées). */
    private static double totalPrice(PostTransactionEvent event) {
        try {
            var prices = event.getPrices();
            if (prices != null && !prices.isEmpty()) {
                double sum = 0;
                for (Double d : prices.values()) if (d != null) sum += d;
                return sum;
            }
        } catch (Throwable ignored) {
            // version d'EconomyShopGUI sans getPrices()
        }
        try {
            return Math.max(0, event.getPrice());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!plugin.shop().inSession(player)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.shop().checkClosed(player), 3L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        plugin.shop().end(id);
        plugin.dialogue().forget(id);
        plugin.proximity().forget(id);
        plugin.ai().forget(id);
        lastClick.remove(id);
    }

    private void runSync(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }
}
