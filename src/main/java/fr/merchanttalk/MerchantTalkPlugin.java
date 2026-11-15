package fr.merchanttalk;

import com.google.gson.Gson;
import fr.merchanttalk.ai.AiChatListener;
import fr.merchanttalk.ai.AiService;
import fr.merchanttalk.ai.CatalogService;
import fr.merchanttalk.features.AnimationService;
import fr.merchanttalk.features.CustomerService;
import fr.merchanttalk.features.PromoService;
import fr.merchanttalk.features.ScheduleService;
import fr.merchanttalk.command.MerchantTalkCommand;
import fr.merchanttalk.dialogue.DialogueService;
import fr.merchanttalk.dialogue.ProximityTask;
import fr.merchanttalk.npc.NpcService;
import fr.merchanttalk.pack.ResourcePackService;
import fr.merchanttalk.shop.CommandBlocker;
import fr.merchanttalk.shop.ShopListener;
import fr.merchanttalk.shop.ShopService;
import fr.merchanttalk.storage.DataStore;
import fr.merchanttalk.web.WebServer;
import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class MerchantTalkPlugin extends JavaPlugin {
    private DataStore store;
    private NpcService npcs;
    private DialogueService dialogue;
    private ShopService shop;
    private CommandBlocker commandBlocker;
    private ProximityTask proximity;
    private ResourcePackService pack;
    private WebServer web;
    private AiService ai;
    private CatalogService catalog;
    private CustomerService customers;
    private PromoService promo;
    private AnimationService animations;
    private ScheduleService schedule;
    /** Sessions du site (jeton -> expiration), conservées lors d'un /mt reload. */
    private final java.util.Map<String, Long> webSessions = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        if (!isEnabled("EconomyShopGUI") && !isEnabled("EconomyShopGUI-Premium")) {
            getLogger().severe("EconomyShopGUI est absent ou désactivé (il a besoin de Vault et d'un plugin d'économie). MerchantTalk se désactive.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        // Ajoute les nouvelles options aux anciens config.yml après une mise à jour.
        getConfig().options().copyDefaults(true);
        saveConfig();
        ensurePassword();

        store = new DataStore(getDataFolder().toPath(), getLogger());
        store.load();

        npcs = new NpcService(this);
        dialogue = new DialogueService(this);
        shop = new ShopService(this);
        commandBlocker = new CommandBlocker(this);
        pack = new ResourcePackService(this);
        ai = new AiService(this);
        catalog = new CatalogService(this);
        customers = new CustomerService(this);
        promo = new PromoService(this);
        animations = new AnimationService(this);
        pack.buildNow();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new ShopListener(this), this);
        pm.registerEvents(commandBlocker, this);
        pm.registerEvents(pack, this);
        pm.registerEvents(new AiChatListener(this), this);
        pm.registerEvents(promo, this);

        MerchantTalkCommand command = new MerchantTalkCommand(this);
        PluginCommand pc = getCommand("merchanttalk");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }

        startProximity();
        startSchedule();
        startWeb();
        logAiStatus();
        // Sauvegarde automatique des statistiques (toutes les minutes).
        Bukkit.getScheduler().runTaskTimer(this, () -> store.saveIfDirty(), 1200L, 1200L);

        // Une fois tous les plugins chargés : liste des commandes du shop + PNJ.
        Bukkit.getScheduler().runTask(this, () -> {
            commandBlocker.refresh();
            npcs.tryInitialSpawn();
        });
    }

    @Override
    public void onDisable() {
        if (web != null) web.stop();
        if (proximity != null) proximity.cancel();
        if (schedule != null) schedule.cancel();
        if (animations != null) animations.cancelAll();
        if (npcs != null) npcs.despawnAll();
        if (store != null) store.saveIfDirty();
    }

    /** Recharge config.yml et data.json, puis recrée PNJ, pack et site. */
    public void reloadAll() {
        reloadConfig();
        ensurePassword();
        npcs.despawnAll();
        store.saveIfDirty();
        store.load();
        commandBlocker.refresh();
        npcs.spawnAll();
        pack.rebuild(true);
        catalog.clear();
        startProximity();
        startSchedule();
        startWeb();
        logAiStatus();
    }

    private void startSchedule() {
        if (schedule != null) schedule.cancel();
        schedule = new ScheduleService(this);
        schedule.runTaskTimer(this, 60L, 100L);
    }

    private void logAiStatus() {
        if (!ai.enabled()) return;
        if (ai.hasKey()) getLogger().info("IA activée : " + ai.provider() + " (modèle " + ai.model() + ").");
        else getLogger().warning("IA activée mais aucune clé API (ai.api-key) dans config.yml : les marchands ne répondront pas.");
    }

    private void startProximity() {
        if (proximity != null) proximity.cancel();
        proximity = new ProximityTask(this);
        long period = Math.max(2, getConfig().getLong("dialogue.greet-check-ticks", 10));
        proximity.runTaskTimer(this, 40L, period);
    }

    private void startWeb() {
        if (web != null) web.stop();
        web = null;
        if (!getConfig().getBoolean("web.enabled", true)) return;
        web = new WebServer(this);
        try {
            web.start();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Impossible de démarrer l'interface web sur le port "
                    + getConfig().getInt("web.port") + " : " + e.getMessage());
            web = null;
        }
    }

    private void ensurePassword() {
        String pw = getConfig().getString("web.password", "");
        if (pw == null || pw.isBlank()) {
            String chars = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
            SecureRandom r = new SecureRandom();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
            getConfig().set("web.password", sb.toString());
            saveConfig();
            getLogger().warning("============================================================");
            getLogger().warning(" Mot de passe de l'interface web généré : " + sb);
            getLogger().warning(" (modifiable dans plugins/MerchantTalk/config.yml)");
            getLogger().warning("============================================================");
        }
    }

    private boolean isEnabled(String name) {
        return getServer().getPluginManager().isPluginEnabled(name);
    }

    /** Exécute une tâche sur le thread principal et attend son résultat (utilisé par le site). */
    public <T> T sync(Callable<T> task) throws Exception {
        if (Bukkit.isPrimaryThread()) return task.call();
        try {
            return Bukkit.getScheduler().callSyncMethod(this, task).get(15, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            throw e;
        }
    }

    /** Sections du shop EconomyShopGUI (pour la liste déroulante du site). */
    public List<String> shopSections() {
        try {
            List<String> sections = EconomyShopGUIHook.getShopSections();
            return sections == null ? List.of() : sections;
        } catch (Throwable t) {
            return List.of();
        }
    }

    public Component message(String key) {
        String prefix = getConfig().getString("messages.prefix", "");
        return MiniMessage.miniMessage().deserialize(prefix + getConfig().getString("messages." + key, key));
    }

    public java.util.Map<String, Long> webSessions() {
        return webSessions;
    }

    public Gson gson() {
        return DataStore.GSON;
    }

    public DataStore store() {
        return store;
    }

    public NpcService npcs() {
        return npcs;
    }

    public DialogueService dialogue() {
        return dialogue;
    }

    public CatalogService catalog() {
        return catalog;
    }

    public CustomerService customers() {
        return customers;
    }

    public PromoService promo() {
        return promo;
    }

    public AnimationService animations() {
        return animations;
    }

    public ScheduleService schedule() {
        return schedule;
    }

    public AiService ai() {
        return ai;
    }

    public ShopService shop() {
        return shop;
    }

    public ProximityTask proximity() {
        return proximity;
    }

    public ResourcePackService pack() {
        return pack;
    }

    public CommandBlocker commandBlocker() {
        return commandBlocker;
    }
}
