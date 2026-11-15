package fr.merchanttalk.features;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import de.oliver.fancynpcs.api.utils.NpcEquipmentSlot;
import fr.merchanttalk.MerchantTalkPlugin;
import fr.merchanttalk.model.Merchant;
import fr.merchanttalk.npc.NpcService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/** Petits mouvements des marchands : salut (il s'incline), objet acheté en main, pièces qui volent. */
public final class AnimationService {
    private final MerchantTalkPlugin plugin;
    /** Retour à la normale programmé, par marchand. */
    private final Map<String, BukkitTask> itemReset = new HashMap<>();
    private final Map<String, BukkitTask> poseReset = new HashMap<>();

    public AnimationService(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
    }

    private Npc npc(Merchant m) {
        if (!plugin.npcs().isReady()) return null;
        try {
            return FancyNpcsPlugin.get().getNpcManager().getNpc(NpcService.npcName(m));
        } catch (Exception e) {
            return null;
        }
    }

    private static NpcAttribute poseAttribute() {
        try {
            return FancyNpcsPlugin.get().getAttributeManager().getAttributeByName(EntityType.PLAYER, "pose");
        } catch (Exception e) {
            return null;
        }
    }

    /** Applique une posture (standing, crouching, sleeping...) au PNJ. */
    public static void setPose(Npc npc, String pose) {
        NpcAttribute attr = poseAttribute();
        if (npc == null || attr == null) return;
        try {
            npc.getData().addAttribute(attr, pose);
            attr.apply(npc, pose);
            npc.updateForAll();
        } catch (Exception ignored) {
            // version de FancyNpcs sans cet attribut
        }
    }

    /** Le marchand s'incline pour saluer. */
    public void bow(Merchant m) {
        if (!m.animations) return;
        Npc npc = npc(m);
        if (npc == null || plugin.schedule().isAsleep(m)) return;
        BukkitTask old = poseReset.remove(m.id);
        if (old != null) old.cancel();
        setPose(npc, "crouching");
        poseReset.put(m.id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            poseReset.remove(m.id);
            Npc current = npc(m);
            if (current != null && !plugin.schedule().isAsleep(m)) setPose(current, "standing");
        }, 12L));
    }

    /** Achat ou vente réussi : il prend l'objet en main, des pièces volent et un petit son joue. */
    public void trade(Merchant m, ItemStack item, boolean sell) {
        if (!m.animations) return;
        Npc npc = npc(m);
        if (npc == null) return;
        Location loc = npc.getData().getLocation();
        if (item != null && item.getType() != Material.AIR) {
            BukkitTask old = itemReset.remove(m.id);
            if (old != null) old.cancel();
            ItemStack shown = item.clone();
            shown.setAmount(1);
            try {
                npc.getData().addEquipment(NpcEquipmentSlot.MAINHAND, shown);
                npc.updateForAll(); // met l'objet en main (et lui fait bouger le bras)
            } catch (Exception ignored) {
                // pas d'équipement possible
            }
            itemReset.put(m.id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
                itemReset.remove(m.id);
                Npc current = npc(m);
                if (current == null) return;
                try {
                    current.getData().getEquipment().remove(NpcEquipmentSlot.MAINHAND);
                    current.updateForAll();
                } catch (Exception ignored) {
                    // PNJ supprimé entre-temps
                }
            }, 50L));
        }
        if (loc != null && loc.getWorld() != null) {
            Location at = loc.clone().add(0, 1.3, 0);
            loc.getWorld().spawnParticle(Particle.ITEM, at, 12, 0.3, 0.3, 0.3, 0.08,
                    new ItemStack(sell ? Material.EMERALD : Material.GOLD_NUGGET));
            loc.getWorld().playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 0.5f, sell ? 1.4f : 1.1f);
        }
    }

    public void cancelAll() {
        itemReset.values().forEach(BukkitTask::cancel);
        poseReset.values().forEach(BukkitTask::cancel);
        itemReset.clear();
        poseReset.clear();
    }
}
