package fr.merchanttalk.features;

import fr.merchanttalk.MerchantTalkPlugin;
import fr.merchanttalk.model.CustomerRecord;
import fr.merchanttalk.model.Merchant;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Mémoire des clients : visites, achats, ventes de chaque joueur chez chaque marchand. */
public final class CustomerService {
    private static final long VISIT_GAP_MS = 5 * 60_000L;

    private final MerchantTalkPlugin plugin;

    public CustomerService(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
    }

    private Map<String, CustomerRecord> of(Merchant m) {
        var all = plugin.store().data().customers;
        if (all == null) {
            all = new LinkedHashMap<>();
            plugin.store().data().customers = all;
        }
        return all.computeIfAbsent(m.id, k -> new LinkedHashMap<>());
    }

    /** @return la fiche du joueur, ou null s'il n'est jamais venu. */
    public CustomerRecord get(Merchant m, Player p) {
        return of(m).get(p.getUniqueId().toString());
    }

    private CustomerRecord getOrCreate(Merchant m, Player p) {
        CustomerRecord r = of(m).computeIfAbsent(p.getUniqueId().toString(), k -> {
            CustomerRecord n = new CustomerRecord();
            n.firstVisit = System.currentTimeMillis();
            return n;
        });
        r.name = p.getName();
        return r;
    }

    /** Le joueur ouvre la boutique : compte une visite (au plus une toutes les 5 minutes). */
    public void visit(Merchant m, Player p) {
        CustomerRecord r = getOrCreate(m, p);
        long now = System.currentTimeMillis();
        if (r.visits == 0 || now - r.lastVisit > VISIT_GAP_MS) r.visits++;
        r.lastVisit = now;
        plugin.store().markDirty();
    }

    public void transaction(Merchant m, Player p, boolean sell, double amount) {
        CustomerRecord r = getOrCreate(m, p);
        if (sell) {
            r.sells++;
            r.earned += amount;
        } else {
            r.buys++;
            r.spent += amount;
        }
        r.lastVisit = System.currentTimeMillis();
        plugin.store().markDirty();
    }

    public boolean isRegular(Merchant m, Player p) {
        CustomerRecord r = get(m, p);
        return r != null && m.regularVisits > 0 && r.visits >= m.regularVisits;
    }

    /** Meilleurs clients (argent dépensé + gagné, puis visites). */
    public List<CustomerRecord> top(Merchant m, int limit) {
        List<CustomerRecord> list = new ArrayList<>(of(m).values());
        list.sort(Comparator.comparingDouble((CustomerRecord r) -> -(r.spent + r.earned))
                .thenComparingLong(r -> -r.visits));
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }

    public int count(Merchant m) {
        return of(m).size();
    }

    public void forgetMerchant(String id) {
        if (plugin.store().data().customers != null) plugin.store().data().customers.remove(id);
    }

    /** Remplace {visits}, {spent}, {earned}, {promo}, {discount}, {bonus}, {hours} dans une réplique. */
    public String placeholders(Merchant m, Player p, String text) {
        if (text == null || text.indexOf('{') < 0) return text;
        CustomerRecord r = get(m, p);
        return text.replace("{visits}", String.valueOf(r == null ? 0 : r.visits))
                .replace("{spent}", money(r == null ? 0 : r.spent))
                .replace("{earned}", money(r == null ? 0 : r.earned))
                .replace("{promo}", m.promoLabel == null ? "" : m.promoLabel)
                .replace("{discount}", String.valueOf(m.promoBuyDiscount))
                .replace("{bonus}", String.valueOf(m.promoSellBonus))
                .replace("{hours}", hoursLabel(m.openHours));
    }

    public static String money(double v) {
        return v == Math.floor(v) && Math.abs(v) < 1e12
                ? String.format(Locale.FRANCE, "%,.0f", v)
                : String.format(Locale.FRANCE, "%,.2f", v);
    }

    /** "6-19" -> "6h à 19h". */
    public static String hoursLabel(String range) {
        if (range == null) return "";
        String[] parts = range.replace(" ", "").replace("h", "").split("-");
        return parts.length == 2 ? parts[0] + "h à " + parts[1] + "h" : range;
    }
}
