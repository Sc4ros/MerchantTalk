package fr.merchanttalk.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import fr.merchanttalk.MerchantTalkPlugin;
import fr.merchanttalk.dialogue.WorldConditions;
import fr.merchanttalk.model.DialogueLine;
import fr.merchanttalk.model.Merchant;
import fr.merchanttalk.model.Trigger;
import fr.merchanttalk.model.VoiceClip;
import fr.merchanttalk.web.WebServer.ApiException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Routes /api/* de l'interface web. Les modifications sont appliquées sur le thread principal. */
final class ApiHandler {
    private static final Pattern MERCHANT_ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final int MAX_AUDIO = 8 * 1024 * 1024;
    private static final int MAX_SKIN = 512 * 1024;
    private static final int MAX_LINES = 50;
    private static final int MAX_TEXT = 500;

    private final MerchantTalkPlugin plugin;
    private final SecureRandom random = new SecureRandom();

    ApiHandler(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
    }

    void handle(HttpExchange ex, String route) throws Exception {
        String method = ex.getRequestMethod();
        String[] parts = route.split("/");

        if (route.equals("state") && method.equals("GET")) {
            WebServer.sendJson(ex, 200, plugin.sync(this::state));
            return;
        }
        if (route.equals("preview") && method.equals("POST")) {
            preview(ex);
            return;
        }
        if (parts[0].equals("merchants") && parts.length == 3 && method.equals("POST")) {
            String id = decode(parts[1]);
            switch (parts[2]) {
                case "test" -> {
                    testSpeak(ex, id);
                    return;
                }
                case "duplicate" -> {
                    duplicate(ex, id);
                    return;
                }
                case "reset-stats" -> {
                    plugin.sync(() -> {
                        requireMerchant(id).stats = new fr.merchanttalk.model.MerchantStats();
                        plugin.store().save();
                        return null;
                    });
                    WebServer.sendJson(ex, 200, ok());
                    return;
                }
                default -> {
                    // route suivante
                }
            }
        }
        if (parts[0].equals("merchants")) {
            if (parts.length == 1 && method.equals("POST")) {
                createMerchant(ex);
                return;
            }
            if (parts.length >= 2) {
                String id = decode(parts[1]);
                if (parts.length == 2 && method.equals("PUT")) {
                    updateMerchant(ex, id);
                    return;
                }
                if (parts.length == 2 && method.equals("DELETE")) {
                    deleteMerchant(ex, id);
                    return;
                }
                if (parts.length == 3 && parts[2].equals("skin")) {
                    switch (method) {
                        case "GET" -> getSkin(ex, id);
                        case "POST" -> uploadSkin(ex, id);
                        case "DELETE" -> deleteSkin(ex, id);
                        default -> throw new ApiException(405, "Méthode non gérée");
                    }
                    return;
                }
            }
        }
        if (parts[0].equals("sounds")) {
            if (parts.length == 1 && method.equals("POST")) {
                uploadSound(ex);
                return;
            }
            if (parts.length == 2) {
                String file = decode(parts[1]);
                if (method.equals("GET") && file.endsWith(".ogg")) {
                    getSound(ex, file.substring(0, file.length() - 4));
                    return;
                }
                if (method.equals("PATCH")) {
                    renameSound(ex, file);
                    return;
                }
                if (method.equals("DELETE")) {
                    deleteSound(ex, file);
                    return;
                }
            }
        }
        if (route.equals("pack/resend") && method.equals("POST")) {
            plugin.sync(() -> {
                plugin.getServer().getOnlinePlayers().forEach(plugin.pack()::send);
                return null;
            });
            WebServer.sendJson(ex, 200, ok());
            return;
        }
        throw new ApiException(404, "Route inconnue : " + route);
    }

    // ---------------------------------------------------------------- état

    private JsonObject state() {
        JsonObject o = new JsonObject();
        JsonArray merchants = new JsonArray();
        for (Merchant m : plugin.store().data().merchants.values()) {
            JsonObject j = plugin.gson().toJsonTree(m).getAsJsonObject();
            j.addProperty("placed", m.location != null);
            // Heure et météo actuelles là où se trouve le marchand.
            org.bukkit.Location loc = plugin.npcs().npcLocation(m);
            if (loc != null && loc.getWorld() != null) {
                JsonObject now = new JsonObject();
                now.addProperty("clock", WorldConditions.clock(loc.getWorld()));
                now.addProperty("weather", WorldConditions.weather(loc.getWorld()));
                JsonArray active = new JsonArray();
                plugin.dialogue().conditions().activeTimes(loc.getWorld()).forEach(active::add);
                now.add("times", active);
                j.add("now", now);
            }
            merchants.add(j);
        }

        JsonObject conditions = new JsonObject();
        JsonArray times = new JsonArray();
        WorldConditions.TIMES.forEach((id, label) -> {
            JsonObject t = new JsonObject();
            t.addProperty("id", id);
            t.addProperty("label", label);
            if (!id.equals("ANY")) t.addProperty("range", plugin.dialogue().conditions().range(id));
            times.add(t);
        });
        JsonArray weathers = new JsonArray();
        WorldConditions.WEATHERS.forEach((id, label) -> {
            JsonObject w = new JsonObject();
            w.addProperty("id", id);
            w.addProperty("label", label);
            weathers.add(w);
        });
        conditions.add("times", times);
        conditions.add("weathers", weathers);
        o.add("conditions", conditions);
        o.add("merchants", merchants);
        o.add("sounds", plugin.gson().toJsonTree(new ArrayList<>(plugin.store().data().sounds.values())));

        JsonArray triggers = new JsonArray();
        for (Trigger t : Trigger.values()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", t.name());
            j.addProperty("label", t.label());
            triggers.add(j);
        }
        o.add("triggers", triggers);

        JsonArray sections = new JsonArray();
        plugin.shopSections().forEach(sections::add);
        o.add("sections", sections);

        JsonObject pack = new JsonObject();
        pack.addProperty("enabled", plugin.pack().enabled());
        pack.addProperty("url", plugin.pack().url());
        pack.addProperty("hash", plugin.pack().hash());
        o.add("pack", pack);
        o.addProperty("npcReady", plugin.npcs().isReady());

        JsonArray players = new JsonArray();
        for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) players.add(p.getName());
        o.add("players", players);
        return o;
    }

    // ---------------------------------------------------------------- aperçu, test, duplication

    /** Rendu HTML de textes MiniMessage : {items:[{type:"name"|"line", text, npc}]} → {html:[...]}. */
    private void preview(HttpExchange ex) throws IOException {
        JsonObject body = WebServer.readJson(ex);
        JsonArray out = new JsonArray();
        var mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
        if (body.has("items")) {
            int n = 0;
            for (JsonElement el : body.getAsJsonArray("items")) {
                if (n++ >= 200) break;
                JsonObject item = el.getAsJsonObject();
                String text = limit(str(item, "text"), MAX_TEXT);
                String html;
                try {
                    if ("line".equals(str(item, "type"))) {
                        String npc = limit(str(item, "npc"), 200);
                        html = HtmlRenderer.render(plugin.dialogue().format(npc.isBlank() ? "Marchand" : npc, "Steve", text));
                    } else {
                        html = HtmlRenderer.render(mm.deserialize(text));
                    }
                } catch (Exception e) {
                    html = "<span style=\"color:#e5534b\">Format invalide</span>";
                }
                out.add(html);
            }
        }
        JsonObject res = new JsonObject();
        res.add("html", out);
        WebServer.sendJson(ex, 200, res);
    }

    /** Fait dire au marchand une réplique d'un déclencheur, à un joueur connecté (le plus proche par défaut). */
    private void testSpeak(HttpExchange ex, String id) throws Exception {
        JsonObject body = WebServer.readJson(ex);
        String triggerName = str(body, "trigger");
        String playerName = str(body, "player");
        JsonObject res = plugin.sync(() -> {
            Merchant m = requireMerchant(id);
            Trigger trigger;
            try {
                trigger = Trigger.valueOf(triggerName);
            } catch (IllegalArgumentException e) {
                throw new ApiException(400, "Moment inconnu");
            }
            org.bukkit.entity.Player target = playerName.isBlank() ? nearestPlayer(m) : plugin.getServer().getPlayerExact(playerName);
            if (target == null) throw new ApiException(400, "Aucun joueur connecté pour entendre le test.");
            DialogueLine said = plugin.dialogue().speak(m, target, trigger, true);
            if (said == null) throw new ApiException(400, "Aucune réplique ne peut être dite en ce moment : aucune n'est enregistrée, ou leurs conditions d'heure/météo ne correspondent pas au jeu.");
            JsonObject o = new JsonObject();
            o.addProperty("player", target.getName());
            o.addProperty("text", said.text);
            return o;
        });
        WebServer.sendJson(ex, 200, res);
    }

    private org.bukkit.entity.Player nearestPlayer(Merchant m) {
        org.bukkit.Location loc = plugin.npcs().npcLocation(m);
        org.bukkit.entity.Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
            double d = loc != null && p.getWorld().equals(loc.getWorld()) ? p.getLocation().distanceSquared(loc) : Double.MAX_VALUE / 2;
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** Copie un marchand (réglages, skin, répliques) sous un nouvel identifiant, sans position. */
    private void duplicate(HttpExchange ex, String id) throws Exception {
        JsonObject body = WebServer.readJson(ex);
        String newId = str(body, "id").toLowerCase(Locale.ROOT).trim();
        if (!MERCHANT_ID.matcher(newId).matches()) {
            throw new ApiException(400, "Identifiant invalide : lettres minuscules, chiffres, - et _ (32 max).");
        }
        String sourceSkin = plugin.sync(() -> {
            if (plugin.store().merchant(newId) != null) throw new ApiException(409, "Ce marchand existe déjà");
            return requireMerchant(id).skinFile;
        });
        String newSkin = null;
        if (sourceSkin != null && Files.exists(plugin.store().skinsDir().resolve(sourceSkin))) {
            newSkin = "merchanttalk_" + newId + "_" + randomHex(4) + ".png";
            Files.copy(plugin.store().skinsDir().resolve(sourceSkin), plugin.store().skinsDir().resolve(newSkin));
        }
        String skin = newSkin;
        plugin.sync(() -> {
            Merchant source = requireMerchant(id);
            Merchant copy = plugin.gson().fromJson(plugin.gson().toJson(source), Merchant.class);
            copy.id = newId;
            copy.location = null;
            copy.skinFile = skin;
            copy.stats = new fr.merchanttalk.model.MerchantStats();
            plugin.store().data().merchants.put(newId, copy);
            plugin.store().save();
            return null;
        });
        WebServer.sendJson(ex, 200, ok());
    }

    // ---------------------------------------------------------------- marchands

    private void createMerchant(HttpExchange ex) throws Exception {
        JsonObject body = WebServer.readJson(ex);
        String id = str(body, "id").toLowerCase(Locale.ROOT).trim();
        if (!MERCHANT_ID.matcher(id).matches()) {
            throw new ApiException(400, "Identifiant invalide : lettres minuscules, chiffres, - et _ (32 max).");
        }
        plugin.sync(() -> {
            if (plugin.store().merchant(id) != null) throw new ApiException(409, "Ce marchand existe déjà");
            Merchant m = new Merchant();
            m.id = id;
            String name = str(body, "displayName");
            if (!name.isBlank()) m.displayName = limit(name, 200);
            plugin.store().data().merchants.put(id, m);
            plugin.store().save();
            return null;
        });
        WebServer.sendJson(ex, 200, ok());
    }

    private void updateMerchant(HttpExchange ex, String id) throws Exception {
        JsonObject body = WebServer.readJson(ex);
        plugin.sync(() -> {
            Merchant m = requireMerchant(id);
            if (body.has("displayName")) m.displayName = limit(str(body, "displayName"), 200);
            if (body.has("section")) m.section = limit(str(body, "section").trim(), 100);
            if (body.has("mode")) m.mode = "SELLGUI".equalsIgnoreCase(str(body, "mode")) ? "SELLGUI" : "SHOP";
            if (body.has("slimSkin")) m.slimSkin = body.get("slimSkin").getAsBoolean();
            if (body.has("lookAtPlayer")) m.lookAtPlayer = body.get("lookAtPlayer").getAsBoolean();
            if (body.has("talkParticles")) m.talkParticles = body.get("talkParticles").getAsBoolean();
            if (body.has("permission")) m.permission = limit(str(body, "permission").trim().replace(" ", ""), 100);
            if (body.has("greetRadius")) m.greetRadius = clamp(body.get("greetRadius").getAsDouble(), 0, 32);
            if (body.has("greetCooldownSeconds")) {
                m.greetCooldownSeconds = (int) clamp(body.get("greetCooldownSeconds").getAsDouble(), 0, 86400);
            }
            if (body.has("lines") && body.get("lines").isJsonObject()) {
                Map<Trigger, List<DialogueLine>> lines = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> e : body.getAsJsonObject("lines").entrySet()) {
                    Trigger trigger;
                    try {
                        trigger = Trigger.valueOf(e.getKey());
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    List<DialogueLine> list = new ArrayList<>();
                    for (JsonElement el : e.getValue().getAsJsonArray()) {
                        if (list.size() >= MAX_LINES) break;
                        JsonObject lo = el.getAsJsonObject();
                        DialogueLine line = new DialogueLine();
                        line.text = limit(str(lo, "text"), MAX_TEXT);
                        String sound = str(lo, "sound");
                        line.sound = plugin.store().sound(sound) != null ? sound : null;
                        String time = str(lo, "time");
                        line.time = WorldConditions.TIMES.containsKey(time) ? time : "ANY";
                        String weather = str(lo, "weather");
                        line.weather = WorldConditions.WEATHERS.containsKey(weather) ? weather : "ANY";
                        if (!line.isEmpty()) list.add(line);
                    }
                    lines.put(trigger, list);
                }
                m.lines = lines;
            }
            plugin.store().save();
            plugin.npcs().spawnOrUpdate(m);
            return null;
        });
        WebServer.sendJson(ex, 200, ok());
    }

    private void deleteMerchant(HttpExchange ex, String id) throws Exception {
        String skin = plugin.sync(() -> {
            Merchant m = requireMerchant(id);
            plugin.shop().closeFor(id);
            plugin.npcs().remove(m);
            plugin.store().data().merchants.remove(id);
            plugin.store().save();
            return m.skinFile;
        });
        deleteSkinFiles(skin);
        WebServer.sendJson(ex, 200, ok());
    }

    // ---------------------------------------------------------------- skins

    private void uploadSkin(HttpExchange ex, String id) throws Exception {
        byte[] png = WebServer.readBody(ex, MAX_SKIN);
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            img = null;
        }
        if (img == null || png.length < 8 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') {
            throw new ApiException(400, "Le fichier doit être une image PNG.");
        }
        if (img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32)) {
            throw new ApiException(400, "Un skin Minecraft doit faire 64x64 (ou 64x32) pixels. Le tien fait "
                    + img.getWidth() + "x" + img.getHeight() + ".");
        }
        plugin.sync(() -> requireMerchant(id));
        String file = "merchanttalk_" + id + "_" + randomHex(4) + ".png";
        Files.write(plugin.store().skinsDir().resolve(file), png);
        String old = plugin.sync(() -> {
            Merchant m = requireMerchant(id);
            String previous = m.skinFile;
            m.skinFile = file;
            String slim = WebServer.queryParam(ex, "slim");
            if (slim != null) m.slimSkin = Boolean.parseBoolean(slim);
            plugin.store().save();
            plugin.npcs().spawnOrUpdate(m);
            return previous;
        });
        deleteSkinFiles(old);
        JsonObject res = ok();
        res.addProperty("skinFile", file);
        WebServer.sendJson(ex, 200, res);
    }

    private void deleteSkin(HttpExchange ex, String id) throws Exception {
        String old = plugin.sync(() -> {
            Merchant m = requireMerchant(id);
            String previous = m.skinFile;
            m.skinFile = null;
            plugin.store().save();
            plugin.npcs().spawnOrUpdate(m);
            return previous;
        });
        deleteSkinFiles(old);
        WebServer.sendJson(ex, 200, ok());
    }

    private void getSkin(HttpExchange ex, String id) throws Exception {
        String file = plugin.sync(() -> requireMerchant(id).skinFile);
        Path path = file == null ? null : plugin.store().skinsDir().resolve(file);
        if (path == null || !Files.exists(path)) throw new ApiException(404, "Pas de skin");
        WebServer.send(ex, 200, "image/png", Files.readAllBytes(path));
    }

    private void deleteSkinFiles(String file) {
        if (file == null || file.isBlank() || file.contains("/") || file.contains("\\")) return;
        try {
            Files.deleteIfExists(plugin.store().skinsDir().resolve(file));
            Files.deleteIfExists(plugin.getServer().getPluginsFolder().toPath()
                    .resolve("FancyNpcs").resolve("skins").resolve(file.toLowerCase(Locale.ROOT)));
        } catch (IOException ignored) {
        }
    }

    // ---------------------------------------------------------------- voix

    private void uploadSound(HttpExchange ex) throws Exception {
        String rawName = ex.getRequestHeaders().getFirst("X-Name");
        String name = rawName == null ? "voix" : URLDecoder.decode(rawName, StandardCharsets.UTF_8).trim();
        if (name.isEmpty()) name = "voix";
        byte[] data = WebServer.readBody(ex, MAX_AUDIO);
        OggInfo info = OggInfo.read(data);
        if (info == null) throw new ApiException(400, "Le fichier n'est pas un OGG Vorbis valide.");
        if (info.channels() != 1) {
            throw new ApiException(400, "Le son doit être en MONO pour être spatialisé (il a " + info.channels() + " canaux).");
        }
        String finalName = limit(name, 80);
        VoiceClip clip = plugin.sync(() -> {
            VoiceClip c = new VoiceClip();
            String base = slug(finalName);
            String id;
            do {
                id = base + "_" + randomHex(2);
            } while (plugin.store().sound(id) != null);
            c.id = id;
            c.name = finalName;
            c.size = data.length;
            c.duration = Math.round(info.duration() * 100) / 100.0;
            return c;
        });
        Files.write(plugin.store().soundFile(clip), data);
        plugin.sync(() -> {
            plugin.store().data().sounds.put(clip.id, clip);
            plugin.store().save();
            plugin.pack().rebuild(true);
            return null;
        });
        WebServer.sendJson(ex, 200, plugin.gson().toJsonTree(clip));
    }

    private void renameSound(HttpExchange ex, String id) throws Exception {
        JsonObject body = WebServer.readJson(ex);
        String name = limit(str(body, "name").trim(), 80);
        if (name.isEmpty()) throw new ApiException(400, "Nom vide");
        plugin.sync(() -> {
            VoiceClip c = plugin.store().sound(id);
            if (c == null) throw new ApiException(404, "Voix introuvable");
            c.name = name;
            plugin.store().save();
            return null;
        });
        WebServer.sendJson(ex, 200, ok());
    }

    private void deleteSound(HttpExchange ex, String id) throws Exception {
        VoiceClip clip = plugin.sync(() -> {
            VoiceClip c = plugin.store().data().sounds.remove(id);
            if (c == null) throw new ApiException(404, "Voix introuvable");
            for (Merchant m : plugin.store().data().merchants.values()) {
                for (List<DialogueLine> list : m.lines.values()) {
                    for (DialogueLine l : list) {
                        if (id.equals(l.sound)) l.sound = null;
                    }
                    list.removeIf(DialogueLine::isEmpty);
                }
            }
            plugin.store().save();
            plugin.pack().rebuild(true);
            return c;
        });
        Files.deleteIfExists(plugin.store().soundFile(clip));
        WebServer.sendJson(ex, 200, ok());
    }

    private void getSound(HttpExchange ex, String id) throws Exception {
        VoiceClip clip = plugin.sync(() -> plugin.store().sound(id));
        if (clip == null) throw new ApiException(404, "Voix introuvable");
        Path path = plugin.store().soundFile(clip);
        if (!Files.exists(path)) throw new ApiException(404, "Fichier manquant");
        WebServer.send(ex, 200, "audio/ogg", Files.readAllBytes(path));
    }

    // ---------------------------------------------------------------- utilitaires

    private Merchant requireMerchant(String id) {
        Merchant m = plugin.store().merchant(id);
        if (m == null) throw new ApiException(404, "Marchand introuvable : " + id);
        return m;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static String limit(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static double clamp(double v, double min, double max) {
        return Double.isNaN(v) ? min : Math.max(min, Math.min(max, v));
    }

    private String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    /** Nom → identifiant valide pour une ressource Minecraft (a-z, 0-9, _). */
    private static String slug(String name) {
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.isEmpty()) s = "voix";
        return s.length() > 24 ? s.substring(0, 24) : s;
    }

    private static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o;
    }
}
