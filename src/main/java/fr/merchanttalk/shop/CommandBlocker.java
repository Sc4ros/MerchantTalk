package fr.merchanttalk.shop;

import fr.merchanttalk.MerchantTalkPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Empêche les joueurs d'ouvrir le shop par commande : seul un marchand y donne accès. */
public final class CommandBlocker implements Listener {
    private final MerchantTalkPlugin plugin;
    private Set<String> blocked = Set.of();

    public CommandBlocker(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
    }

    /** Recalcule la liste des commandes bloquées (à appeler une fois tous les plugins chargés). */
    public void refresh() {
        Set<String> set = new HashSet<>();
        for (String s : plugin.getConfig().getStringList("shop.blocked-commands")) {
            set.add(s.toLowerCase(Locale.ROOT).replace("/", "").trim());
        }
        if (plugin.getConfig().getBoolean("shop.block-all-economyshopgui-commands", true)) {
            for (Map.Entry<String, Command> e : Bukkit.getCommandMap().getKnownCommands().entrySet()) {
                Command cmd = e.getValue();
                boolean fromShop = cmd.getClass().getName().startsWith("me.gypopo.")
                        || e.getKey().toLowerCase(Locale.ROOT).startsWith("economyshopgui")
                        || cmd instanceof PluginIdentifiableCommand pic && pic.getPlugin().getName().startsWith("EconomyShopGUI");
                if (fromShop) set.add(strip(e.getKey()));
            }
        }
        blocked = set;
    }

    public Set<String> blocked() {
        return blocked;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("shop.block-commands", true)) return;
        if (event.getPlayer().hasPermission("merchanttalk.bypass")) return;
        String msg = event.getMessage();
        if (!msg.startsWith("/")) return;
        int space = msg.indexOf(' ');
        String label = strip(space < 0 ? msg.substring(1) : msg.substring(1, space));
        if (blocked.contains(label)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.message("command-blocked"));
        }
    }

    @EventHandler
    public void onTabList(PlayerCommandSendEvent event) {
        if (!plugin.getConfig().getBoolean("shop.block-commands", true)) return;
        if (event.getPlayer().hasPermission("merchanttalk.bypass")) return;
        event.getCommands().removeIf(c -> blocked.contains(strip(c)));
    }

    private static String strip(String label) {
        String l = label.toLowerCase(Locale.ROOT);
        int colon = l.indexOf(':');
        return colon >= 0 ? l.substring(colon + 1) : l;
    }
}
