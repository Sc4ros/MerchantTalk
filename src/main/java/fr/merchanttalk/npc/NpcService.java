package fr.merchanttalk.npc;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.NpcManager;
import de.oliver.fancynpcs.api.skins.SkinData;
import fr.merchanttalk.MerchantTalkPlugin;
import fr.merchanttalk.model.Merchant;
import org.bukkit.Location;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Crée les PNJ via FancyNpcs. Les PNJ ne sont pas enregistrés par FancyNpcs
 * (setSaveToFile(false)) : MerchantTalk les recrée à chaque démarrage depuis data.json.
 */
public final class NpcService {
    public static final String PREFIX = "merchanttalk_";
    private static final UUID CREATOR = UUID.nameUUIDFromBytes("MerchantTalk".getBytes());

    private final MerchantTalkPlugin plugin;
    private boolean ready;

    public NpcService(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
    }

    public static String npcName(Merchant m) {
        return PREFIX + m.id;
    }

    /** @return l'id du marchand si ce PNJ appartient à MerchantTalk, sinon null. */
    public static String merchantId(Npc npc) {
        String name = npc.getData().getName();
        return name != null && name.startsWith(PREFIX) ? name.substring(PREFIX.length()) : null;
    }

    public boolean isReady() {
        return ready;
    }

    /** Appelé quand FancyNpcs a fini de charger ses PNJ. */
    public void onFancyNpcsLoaded() {
        ready = true;
        spawnAll();
    }

    public void tryInitialSpawn() {
        try {
            if (FancyNpcsPlugin.get().getNpcManager().isLoaded()) {
                onFancyNpcsLoaded();
            }
        } catch (Exception ignored) {
            // FancyNpcs pas encore prêt : on attend NpcsLoadedEvent.
        }
    }

    public void spawnAll() {
        if (!ready) return;
        for (Merchant m : plugin.store().data().merchants.values()) {
            spawnOrUpdate(m);
        }
    }

    public void despawnAll() {
        if (!ready) return;
        for (Merchant m : plugin.store().data().merchants.values()) {
            remove(m);
        }
    }

    /** (Re)crée le PNJ d'un marchand avec ses réglages actuels. */
    public void spawnOrUpdate(Merchant m) {
        if (!ready) return;
        remove(m);
        var stored = m.currentLocation();
        if (stored == null) return;
        Location loc = stored.toBukkit();
        if (loc == null) {
            plugin.getLogger().warning("Le monde '" + stored.world + "' du marchand " + m.id + " n'est pas chargé.");
            return;
        }
        // Horaires : fermé en mode « il disparaît » -> pas de PNJ.
        if (plugin.schedule() != null && plugin.schedule().isHidden(m)) return;
        boolean asleep = plugin.schedule() != null && plugin.schedule().isAsleep(m);
        try {
            NpcData data = new NpcData(npcName(m), CREATOR, loc);
            data.setDisplayName(m.displayName == null || m.displayName.isBlank() ? "<empty>" : m.displayName);
            data.setTurnToPlayer(m.lookAtPlayer);
            data.setShowInTab(false);
            data.setCollidable(false);
            String skinId = installSkin(m);
            if (skinId != null) {
                try {
                    data.setSkin(skinId, m.slimSkin ? SkinData.SkinVariant.SLIM : SkinData.SkinVariant.AUTO);
                } catch (Exception e) {
                    plugin.getLogger().warning("Skin du marchand " + m.id + " non appliqué : " + e.getMessage());
                }
            }
            Npc npc = FancyNpcsPlugin.get().getNpcAdapter().apply(data);
            npc.setSaveToFile(false);
            FancyNpcsPlugin.get().getNpcManager().registerNpc(npc);
            npc.create();
            npc.spawnForAll();
            if (asleep) fr.merchanttalk.features.AnimationService.setPose(npc, "sleeping");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de créer le PNJ du marchand " + m.id, e);
        }
    }

    public void remove(Merchant m) {
        if (!ready) return;
        NpcManager manager = FancyNpcsPlugin.get().getNpcManager();
        Npc npc = manager.getNpc(npcName(m));
        if (npc != null) {
            npc.removeForAll();
            manager.removeNpc(npc);
        }
    }

    public Location npcLocation(Merchant m) {
        if (ready) {
            Npc npc = FancyNpcsPlugin.get().getNpcManager().getNpc(npcName(m));
            if (npc != null && npc.getData().getLocation() != null) return npc.getData().getLocation();
        }
        var stored = m.currentLocation();
        return stored == null ? null : stored.toBukkit();
    }

    /**
     * FancyNpcs génère les skins (via MineSkin) à partir des fichiers de plugins/FancyNpcs/skins.
     * On y copie le PNG du marchand et on renvoie son nom de fichier.
     */
    private String installSkin(Merchant m) {
        if (m.skinFile == null || m.skinFile.isBlank()) return null;
        Path source = plugin.store().skinsDir().resolve(m.skinFile);
        if (!Files.exists(source)) return null;
        Path fancySkins = plugin.getServer().getPluginsFolder().toPath().resolve("FancyNpcs").resolve("skins");
        String target = m.skinFile.toLowerCase(Locale.ROOT);
        try {
            Files.createDirectories(fancySkins);
            Path dest = fancySkins.resolve(target);
            if (!Files.exists(dest)) {
                Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Impossible de copier le skin vers FancyNpcs", e);
            return null;
        }
    }
}
