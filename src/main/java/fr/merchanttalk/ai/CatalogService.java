package fr.merchanttalk.ai;

import fr.merchanttalk.MerchantTalkPlugin;
import fr.merchanttalk.features.CustomerService;
import fr.merchanttalk.model.Merchant;
import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.objects.ShopItem;
import me.gypopo.economyshopgui.objects.shops.ShopSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Liste des objets et des prix d'un marchand, donnée à l'IA pour qu'elle réponde avec les vrais prix. */
public final class CatalogService {
    private record Cached(long at, String text) {
    }

    private final MerchantTalkPlugin plugin;
    private final Map<String, Cached> cache = new HashMap<>();

    public CatalogService(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
    }

    /** Doit être appelé sur le thread principal. Renvoie "" si rien n'est disponible. */
    public String describe(Merchant m) {
        String key = m.id + ":" + m.mode + ":" + m.section + ":" + plugin.promo().active(m);
        Cached c = cache.get(key);
        long now = System.currentTimeMillis();
        if (c != null && now - c.at() < 60_000) return c.text();
        String text;
        try {
            text = build(m);
        } catch (Throwable t) {
            text = "";
        }
        cache.put(key, new Cached(now, text));
        return text;
    }

    private String build(Merchant m) {
        int max = Math.max(10, plugin.getConfig().getInt("ai.max-catalog-items", 150));
        boolean sellOnly = m.isSellMenu();
        List<String> sections = new ArrayList<>();
        String section = m.section == null ? "" : m.section.trim();
        if (sellOnly || section.isEmpty()) sections.addAll(EconomyShopGUIHook.getShopSections());
        else sections.add(section);

        double buyFactor = plugin.promo().buyFactor(m);
        double sellFactor = plugin.promo().sellFactor(m);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String name : sections) {
            ShopSection s = EconomyShopGUIHook.getShopSection(name);
            if (s == null) continue;
            for (ShopItem item : s.getShopItems()) {
                if (count >= max) break;
                try {
                    if (item.isDisplayItem() || item.hasItemError()) continue;
                    ItemStack stack = item.getItemToGive();
                    if (stack == null) continue;
                    String buy = null, sell = null;
                    if (!sellOnly && EconomyShopGUIHook.isBuyAble(item)) {
                        double p = EconomyShopGUIHook.getItemBuyPrice(item, 1);
                        if (p > 0) buy = CustomerService.money(p * buyFactor);
                    }
                    if (EconomyShopGUIHook.isSellAble(item)) {
                        Double p = EconomyShopGUIHook.getItemSellPrice(item, stack);
                        if (p != null && p > 0) sell = CustomerService.money(p * sellFactor);
                    }
                    if (buy == null && sell == null) continue;
                    sb.append("- ").append(itemName(stack));
                    if (stack.getAmount() > 1) sb.append(" (x").append(stack.getAmount()).append(")");
                    sb.append(" :");
                    if (buy != null) sb.append(" achat ").append(buy).append(" $");
                    if (buy != null && sell != null) sb.append(",");
                    if (sell != null) sb.append(" rachat ").append(sell).append(" $");
                    sb.append('\n');
                    count++;
                } catch (Throwable ignored) {
                    // objet illisible : on le saute
                }
            }
        }
        return sb.toString();
    }

    private static String itemName(ItemStack stack) {
        return stack.getType().getKey().getKey().replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    public void clear() {
        cache.clear();
    }
}
