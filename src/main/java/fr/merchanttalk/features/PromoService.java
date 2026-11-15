package fr.merchanttalk.features;

import fr.merchanttalk.MerchantTalkPlugin;
import fr.merchanttalk.model.Merchant;
import me.gypopo.economyshopgui.api.events.PreTransactionEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/** Promotions : réduction sur les achats et/ou bonus sur les ventes, certains jours. */
public final class PromoService implements Listener {
    private final MerchantTalkPlugin plugin;

    public PromoService(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean active(Merchant m) {
        if (m == null || !m.promoEnabled) return false;
        if (m.promoBuyDiscount <= 0 && m.promoSellBonus <= 0) return false;
        String days = m.promoDays == null ? "" : m.promoDays.trim();
        if (days.isEmpty()) return true;
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        for (String d : days.split(",")) {
            if (d.trim().equalsIgnoreCase(today.name())) return true;
        }
        return false;
    }

    /** Multiplicateur du prix d'achat (ex. 0.8 pour -20 %). */
    public double buyFactor(Merchant m) {
        return active(m) ? Math.max(0.1, 1 - Math.min(90, Math.max(0, m.promoBuyDiscount)) / 100.0) : 1;
    }

    /** Multiplicateur du prix de vente (ex. 1.1 pour +10 %). */
    public double sellFactor(Merchant m) {
        return active(m) ? 1 + Math.min(200, Math.max(0, m.promoSellBonus)) / 100.0 : 1;
    }

    /** Ex. « Soldes : -20 % sur les achats, +10 % sur les ventes ». */
    public String describe(Merchant m) {
        StringBuilder sb = new StringBuilder(m.promoLabel == null || m.promoLabel.isBlank() ? "Promo" : m.promoLabel).append(" : ");
        boolean first = true;
        if (m.promoBuyDiscount > 0) {
            sb.append("-").append(m.promoBuyDiscount).append(" % sur les achats");
            first = false;
        }
        if (m.promoSellBonus > 0) sb.append(first ? "" : ", ").append("+").append(m.promoSellBonus).append(" % sur les ventes");
        return sb.toString();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreTransaction(PreTransactionEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        Merchant m = plugin.shop().session(player);
        if (!active(m)) return;
        boolean sell = event.getTransactionType().name().contains("SELL");
        double factor = sell ? sellFactor(m) : buyFactor(m);
        if (factor == 1) return;
        try {
            event.setPrice(event.getPrice() * factor);
        } catch (Throwable ignored) {
            // transaction à plusieurs monnaies
        }
        try {
            Map<?, Double> prices = event.getPrices();
            if (prices != null && !prices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<Object, Double> editable = (Map<Object, Double>) prices;
                editable.replaceAll((k, v) -> v == null ? null : v * factor);
            }
        } catch (Throwable ignored) {
            // liste de prix non modifiable : seul setPrice s'applique
        }
    }
}
