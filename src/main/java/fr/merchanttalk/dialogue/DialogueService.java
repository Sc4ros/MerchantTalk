package fr.merchanttalk.dialogue;

import fr.merchanttalk.MerchantTalkPlugin;
import fr.merchanttalk.model.DialogueLine;
import fr.merchanttalk.model.Merchant;
import fr.merchanttalk.model.Trigger;
import fr.merchanttalk.model.VoiceClip;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Fait parler les marchands : texte dans le chat + voix spatialisée jouée depuis le PNJ. */
public final class DialogueService {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final MerchantTalkPlugin plugin;
    /** Dernière phrase dite par (marchand + joueur), pour éviter le spam. */
    private final Map<String, Speech> lastSpeech = new HashMap<>();
    /** Dernière réplique choisie par (marchand + déclencheur), pour éviter de répéter la même. */
    private final Map<String, DialogueLine> lastLine = new HashMap<>();
    private final WorldConditions conditions;

    private record Speech(long at, long busyUntil, String soundKey) {
    }

    public DialogueService(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
        this.conditions = new WorldConditions(plugin);
    }

    /** Fait dire au marchand une réplique au hasard pour ce déclencheur. Doit être appelé sur le thread principal. */
    public void speak(Merchant merchant, Player player, Trigger trigger) {
        speak(merchant, player, trigger, false);
    }

    /**
     * @param force ignore l'anti-spam (utilisé par le bouton « Tester en jeu » du site)
     * @return la réplique dite, ou null si le marchand n'a rien dit
     */
    public DialogueLine speak(Merchant merchant, Player player, Trigger trigger, boolean force) {
        Location npcLoc = plugin.npcs().npcLocation(merchant);
        org.bukkit.World world = npcLoc != null && npcLoc.getWorld() != null ? npcLoc.getWorld() : player.getWorld();
        // Répliques dont les conditions (heure, météo) sont remplies en ce moment.
        List<DialogueLine> candidates = new ArrayList<>();
        List<DialogueLine> specific = new ArrayList<>();
        for (DialogueLine l : merchant.lines(trigger)) {
            if (l == null || l.isEmpty() || !conditions.matches(l, world)) continue;
            candidates.add(l);
            if (l.isConditional()) specific.add(l);
        }
        // Par défaut, une réplique spéciale (ex. « Nuit ») passe avant les répliques « Toujours ».
        if (!specific.isEmpty() && plugin.getConfig().getBoolean("dialogue.prefer-specific", true)) {
            candidates = specific;
        }
        if (candidates.isEmpty()) return null;

        String key = merchant.id + ":" + player.getUniqueId();
        long now = System.currentTimeMillis();
        Speech previous = lastSpeech.get(key);
        boolean repeatable = trigger == Trigger.BUY || trigger == Trigger.SELL;
        if (repeatable && previous != null && !force) {
            long minInterval = plugin.getConfig().getLong("dialogue.min-interval-ms", 1200);
            // Achats/ventes à la chaîne : on ne coupe pas la voix en cours.
            if (now < previous.busyUntil() || now - previous.at() < minInterval) return null;
        }

        DialogueLine line = pick(merchant.id + ":" + trigger, candidates);
        Location origin = npcLoc;

        // Coupe la voix précédente du marchand si elle est encore en train d'être jouée.
        if (previous != null && previous.soundKey() != null && now < previous.busyUntil() && origin != null) {
            for (Player p : nearby(origin)) p.stopSound(previous.soundKey(), SoundCategory.VOICE);
        }

        if (line.text != null && !line.text.isBlank()) {
            player.sendMessage(format(merchant, player, line.text));
        }

        String soundKey = null;
        long busyUntil = now;
        VoiceClip clip = plugin.store().sound(line.sound);
        if (clip != null && origin != null && plugin.pack().hasSound(clip.id)) {
            soundKey = clip.soundKey();
            float volume = (float) plugin.getConfig().getDouble("dialogue.volume", 1.0);
            // Le serveur n'envoie le son qu'aux joueurs à moins de 16 x volume blocs.
            float hearing = (float) plugin.getConfig().getDouble("dialogue.hearing-distance", 16);
            if (hearing > 16 && volume >= 1.0f) volume = Math.max(volume, hearing / 16f);
            Location at = origin.clone().add(0, 1.6, 0);
            at.getWorld().playSound(at, soundKey, SoundCategory.VOICE, volume, 1.0f);
            busyUntil = now + (long) (clip.duration * 1000);
        }
        lastSpeech.put(key, new Speech(now, busyUntil, soundKey));
        if (origin != null && merchant.talkParticles) {
            talkParticles(origin, Math.max(0.6, (busyUntil - now) / 1000.0));
        }
        return line;
    }

    /** Petites notes au-dessus de la tête du PNJ pendant qu'il parle. */
    private void talkParticles(Location origin, double seconds) {
        if (!plugin.getConfig().getBoolean("dialogue.particles.enabled", true)) return;
        Particle particle;
        try {
            particle = Particle.valueOf(plugin.getConfig().getString("dialogue.particles.type", "NOTE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            particle = Particle.NOTE;
        }
        if (particle.getDataType() != Void.class) particle = Particle.NOTE;
        Particle type = particle;
        Location at = origin.clone().add(0, 2.25, 0);
        int bursts = (int) Math.min(30, Math.ceil(seconds * 2)); // une bouffée toutes les 10 ticks
        new BukkitRunnable() {
            int done = 0;

            @Override
            public void run() {
                if (done++ >= bursts || at.getWorld() == null) {
                    cancel();
                    return;
                }
                at.getWorld().spawnParticle(type, at, 1, 0.25, 0.1, 0.25, ThreadLocalRandom.current().nextDouble());
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    public Component format(Merchant merchant, Player player, String text) {
        return format(merchant.displayName == null ? merchant.id : merchant.displayName, player.getName(), text);
    }

    /** Message de chat d'une réplique (utilisé aussi pour l'aperçu du site). */
    public Component format(String npcName, String playerName, String text) {
        String withPlayer = text.replace("{player}", playerName);
        TagResolver resolver = TagResolver.resolver(
                Placeholder.component("npc", MM.deserialize(npcName)),
                Placeholder.component("text", MM.deserialize(withPlayer, Placeholder.unparsed("player", playerName))),
                Placeholder.unparsed("player", playerName));
        return MM.deserialize(plugin.getConfig().getString("dialogue.chat-format", "<npc> » <text>"), resolver);
    }

    public void forget(UUID player) {
        String suffix = ":" + player;
        lastSpeech.keySet().removeIf(k -> k.endsWith(suffix));
    }

    private DialogueLine pick(String key, List<DialogueLine> candidates) {
        DialogueLine last = lastLine.get(key);
        DialogueLine chosen;
        if (candidates.size() == 1) {
            chosen = candidates.getFirst();
        } else {
            // Jamais deux fois la même réplique d'affilée.
            do {
                chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            } while (chosen == last);
        }
        lastLine.put(key, chosen);
        return chosen;
    }

    public WorldConditions conditions() {
        return conditions;
    }

    private List<Player> nearby(Location origin) {
        double distance = plugin.getConfig().getDouble("dialogue.hearing-distance", 16) + 4;
        List<Player> list = new ArrayList<>();
        for (Player p : origin.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(origin) <= distance * distance) list.add(p);
        }
        return list;
    }
}
