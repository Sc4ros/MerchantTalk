package fr.merchanttalk.features;

import fr.merchanttalk.MerchantTalkPlugin;
import fr.merchanttalk.dialogue.WorldConditions;
import fr.merchanttalk.model.Merchant;
import fr.merchanttalk.model.Spot;
import fr.merchanttalk.model.StoredLocation;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Horaires d'ouverture et marchands ambulants. Vérifie toutes les 5 secondes si un marchand
 * doit ouvrir, fermer (dormir / disparaître) ou partir vers son prochain emplacement.
 */
public final class ScheduleService extends BukkitRunnable {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final MerchantTalkPlugin plugin;
    /** Dernier état connu (ouvert ?) de chaque marchand, pour ne réagir qu'aux changements. */
    private final Map<String, Boolean> lastOpen = new HashMap<>();

    public ScheduleService(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
    }

    /** La boutique est-elle ouverte en ce moment ? (toujours vrai sans horaires) */
    public boolean isOpen(Merchant m) {
        if (!m.hoursEnabled) return true;
        StoredLocation sl = m.currentLocation();
        Location loc = sl == null ? null : sl.toBukkit();
        if (loc == null || loc.getWorld() == null) return true;
        Boolean in = WorldConditions.inRange(m.openHours, loc.getWorld());
        return in == null || in;
    }

    public String closedMode(Merchant m) {
        String mode = m.closedMode == null ? "SLEEP" : m.closedMode.toUpperCase(Locale.ROOT);
        return switch (mode) {
            case "HIDE", "REFUSE" -> mode;
            default -> "SLEEP";
        };
    }

    /** Le marchand dort (fermé en mode « il dort »). */
    public boolean isAsleep(Merchant m) {
        return !isOpen(m) && closedMode(m).equals("SLEEP");
    }

    /** Le marchand a disparu (fermé en mode « il disparaît »). */
    public boolean isHidden(Merchant m) {
        return !isOpen(m) && closedMode(m).equals("HIDE");
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (Merchant m : plugin.store().data().merchants.values()) {
            // Marchand ambulant : il part vers le prochain emplacement.
            if (m.wanderEnabled && m.spots().size() >= 2) {
                if (m.wanderLastMove == 0) {
                    m.wanderLastMove = now;
                    plugin.store().markDirty();
                } else if (now - m.wanderLastMove >= Math.max(1, m.wanderMinutes) * 60_000L) {
                    moveNext(m);
                    continue;
                }
            }
            // Horaires : on ne recrée le PNJ que quand l'état change.
            boolean open = isOpen(m);
            Boolean previous = lastOpen.put(m.id, open);
            if (previous != null && previous != open && m.hoursEnabled && !closedMode(m).equals("REFUSE")) {
                plugin.npcs().spawnOrUpdate(m);
            }
        }
    }

    /** Envoie le marchand ambulant à son emplacement suivant. */
    public void moveNext(Merchant m) {
        if (m.spots().isEmpty()) return;
        m.spotIndex = Math.floorMod(m.spotIndex + 1, m.spots().size());
        m.wanderLastMove = System.currentTimeMillis();
        plugin.store().markDirty();
        plugin.shop().closeFor(m.id);
        plugin.npcs().spawnOrUpdate(m);
        lastOpen.put(m.id, isOpen(m));
        Spot spot = m.currentSpot();
        if (m.wanderAnnounce && spot != null) {
            String format = plugin.getConfig().getString("messages.wander-arrived", "");
            if (format != null && !format.isBlank() && spot.location != null) {
                var msg = MM.deserialize(format,
                        Placeholder.component("npc", MM.deserialize(m.displayName == null ? m.id : m.displayName)),
                        Placeholder.unparsed("spot", spot.name == null ? "" : spot.name),
                        Placeholder.unparsed("x", String.valueOf((int) spot.location.x)),
                        Placeholder.unparsed("y", String.valueOf((int) spot.location.y)),
                        Placeholder.unparsed("z", String.valueOf((int) spot.location.z)),
                        Placeholder.unparsed("world", spot.location.world == null ? "" : spot.location.world));
                Bukkit.broadcast(msg);
            }
        }
    }

    public void forget(String merchantId) {
        lastOpen.remove(merchantId);
    }
}
