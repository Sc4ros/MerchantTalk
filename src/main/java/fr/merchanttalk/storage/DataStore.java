package fr.merchanttalk.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.merchanttalk.model.Merchant;
import fr.merchanttalk.model.PluginData;
import fr.merchanttalk.model.VoiceClip;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stockage des marchands et des voix dans plugins/MerchantTalk/data.json.
 * Toutes les modifications se font sur le thread principal du serveur.
 */
public final class DataStore {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path folder;
    private final Path dataFile;
    private final Path soundsDir;
    private final Path skinsDir;
    private final Logger logger;
    private PluginData data = new PluginData();

    public DataStore(Path folder, Logger logger) {
        this.folder = folder;
        this.dataFile = folder.resolve("data.json");
        this.soundsDir = folder.resolve("sounds");
        this.skinsDir = folder.resolve("skins");
        this.logger = logger;
    }

    public void load() {
        try {
            Files.createDirectories(soundsDir);
            Files.createDirectories(skinsDir);
            if (Files.exists(dataFile)) {
                try (Reader r = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
                    PluginData loaded = GSON.fromJson(r, PluginData.class);
                    data = loaded == null ? new PluginData() : loaded;
                }
            } else {
                data = new PluginData();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Impossible de lire data.json, il ne sera pas écrasé tant que l'erreur existe.", e);
            throw new IllegalStateException(e);
        }
        if (data.merchants == null) data.merchants = new LinkedHashMap<>();
        if (data.sounds == null) data.sounds = new LinkedHashMap<>();
        data.merchants.forEach((id, m) -> m.id = id);
        data.sounds.forEach((id, s) -> s.id = id);
    }

    private volatile boolean dirty;

    /** Signale une modification mineure (stats) : enregistrée par la sauvegarde automatique. */
    public void markDirty() {
        dirty = true;
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public synchronized void save() {
        dirty = false;
        try {
            Files.createDirectories(folder);
            Path tmp = folder.resolve("data.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
            Files.move(tmp, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Impossible d'enregistrer data.json", e);
        }
    }

    public PluginData data() {
        return data;
    }

    public Merchant merchant(String id) {
        return id == null ? null : data.merchants.get(id);
    }

    public VoiceClip sound(String id) {
        return id == null ? null : data.sounds.get(id);
    }

    public Path soundsDir() {
        return soundsDir;
    }

    public Path skinsDir() {
        return skinsDir;
    }

    public Path soundFile(VoiceClip clip) {
        return soundsDir.resolve(clip.fileName());
    }
}
