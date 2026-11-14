package fr.merchanttalk.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.merchanttalk.MerchantTalkPlugin;
import fr.merchanttalk.model.VoiceClip;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Génère le resource pack contenant les voix (assets/merchanttalk/sounds/voice/*.ogg),
 * le sert via le serveur web et l'envoie aux joueurs.
 */
public final class ResourcePackService implements Listener {
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("MerchantTalk-voices".getBytes(StandardCharsets.UTF_8));

    private final MerchantTalkPlugin plugin;
    private volatile byte[] packBytes;
    private volatile String packHash;
    private volatile Set<String> packedSounds = Set.of();

    public ResourcePackService(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("resource-pack.enabled", true);
    }

    public boolean hasSound(String id) {
        return packedSounds.contains(id);
    }

    public String hash() {
        return packHash;
    }

    public byte[] bytes() {
        return packBytes;
    }

    public String url() {
        String base = plugin.getConfig().getString("web.public-url", "").replaceAll("/+$", "");
        return packHash == null ? null : base + "/pack/" + packHash + ".zip";
    }

    /** Reconstruit le pack (hors du thread principal) puis le renvoie si demandé. */
    public void rebuild(boolean resend) {
        List<VoiceClip> clips = new ArrayList<>(plugin.store().data().sounds.values());
        Path soundsDir = plugin.store().soundsDir();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                build(clips, soundsDir);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Impossible de générer le resource pack", e);
                return;
            }
            if (resend && plugin.getConfig().getBoolean("resource-pack.resend-on-change", true)) {
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::send));
            }
        });
    }

    /** Construction synchrone (utilisée au démarrage). */
    public void buildNow() {
        try {
            build(new ArrayList<>(plugin.store().data().sounds.values()), plugin.store().soundsDir());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de générer le resource pack", e);
        }
    }

    private synchronized void build(List<VoiceClip> clips, Path soundsDir) throws Exception {
        Set<String> included = new HashSet<>();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int hearing = plugin.getConfig().getInt("dialogue.hearing-distance", 16);
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            JsonObject sounds = new JsonObject();
            for (VoiceClip clip : clips) {
                Path file = soundsDir.resolve(clip.fileName());
                if (!Files.exists(file)) continue;
                put(zip, "assets/merchanttalk/sounds/voice/" + clip.fileName(), Files.readAllBytes(file));
                JsonObject entry = new JsonObject();
                entry.addProperty("name", "merchanttalk:voice/" + clip.id);
                entry.addProperty("stream", true);
                entry.addProperty("attenuation_distance", hearing);
                JsonArray list = new JsonArray();
                list.add(entry);
                JsonObject event = new JsonObject();
                event.add("sounds", list);
                sounds.add("voice." + clip.id, event);
                included.add(clip.id);
            }
            if (included.isEmpty()) {
                packBytes = null;
                packHash = null;
                packedSounds = Set.of();
                return;
            }
            put(zip, "assets/merchanttalk/sounds.json", sounds.toString().getBytes(StandardCharsets.UTF_8));

            JsonObject pack = new JsonObject();
            int min = plugin.getConfig().getInt("resource-pack.min-format", 69);
            int max = plugin.getConfig().getInt("resource-pack.max-format", 999);
            pack.addProperty("pack_format", min);
            pack.addProperty("min_format", min);
            pack.addProperty("max_format", max);
            pack.addProperty("description", plugin.getConfig().getString("resource-pack.description", "MerchantTalk"));
            JsonObject meta = new JsonObject();
            meta.add("pack", pack);
            put(zip, "pack.mcmeta", meta.toString().getBytes(StandardCharsets.UTF_8));
        }
        byte[] bytes = out.toByteArray();
        packBytes = bytes;
        packHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        packedSounds = Set.copyOf(included);
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(0L); // zip déterministe : même contenu = même hash
        zip.putNextEntry(e);
        zip.write(data);
        zip.closeEntry();
    }

    public void send(Player player) {
        String url = url();
        if (!enabled() || url == null) return;
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url), packHash);
        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .replace(false)
                .required(plugin.getConfig().getBoolean("resource-pack.required", true))
                .prompt(MiniMessage.miniMessage().deserialize(plugin.getConfig().getString("resource-pack.prompt", "")))
                .build();
        player.sendResourcePacks(request);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) return;
        if (!plugin.getConfig().getBoolean("resource-pack.required", true)) return;
        Player player = event.getPlayer();
        if (player.hasPermission("merchanttalk.bypass")) return;
        switch (event.getStatus()) {
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                if (event.getStatus() != PlayerResourcePackStatusEvent.Status.DECLINED) {
                    plugin.getLogger().warning("Le joueur " + player.getName() + " n'a pas pu charger le pack ("
                            + event.getStatus() + "). Vérifie web.public-url dans config.yml : " + url());
                }
                player.kick(MiniMessage.miniMessage().deserialize(
                        plugin.getConfig().getString("resource-pack.kick-message", "Resource pack requis.")));
            }
            default -> {
            }
        }
    }
}
