package fr.merchanttalk.dialogue;

import fr.merchanttalk.MerchantTalkPlugin;
import fr.merchanttalk.model.Merchant;
import fr.merchanttalk.model.Trigger;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Le marchand salue les joueurs qui s'approchent (une fois par entrée dans la zone, avec un délai). */
public final class ProximityTask extends BukkitRunnable {
    private final MerchantTalkPlugin plugin;
    /** marchand -> joueurs actuellement dans la zone. */
    private final Map<String, Set<UUID>> inside = new HashMap<>();
    /** marchand:joueur -> dernier salut. */
    private final Map<String, Long> lastGreet = new HashMap<>();

    public ProximityTask(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (Merchant m : plugin.store().data().merchants.values()) {
            if (m.lines(Trigger.GREET).isEmpty() || m.greetRadius <= 0) {
                inside.remove(m.id);
                continue;
            }
            Location origin = plugin.npcs().npcLocation(m);
            if (origin == null || origin.getWorld() == null) continue;
            double r2 = m.greetRadius * m.greetRadius;
            Set<UUID> previous = inside.getOrDefault(m.id, Set.of());
            Set<UUID> current = new HashSet<>();
            for (Player p : origin.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(origin) > r2) continue;
                current.add(p.getUniqueId());
                if (previous.contains(p.getUniqueId())) continue;
                if (plugin.shop().inSession(p)) continue;
                String key = m.id + ":" + p.getUniqueId();
                Long last = lastGreet.get(key);
                if (last != null && now - last < m.greetCooldownSeconds * 1000L) continue;
                lastGreet.put(key, now);
                plugin.dialogue().speak(m, p, Trigger.GREET);
            }
            inside.put(m.id, current);
        }
    }

    public void forget(UUID player) {
        inside.values().forEach(s -> s.remove(player));
    }
}
