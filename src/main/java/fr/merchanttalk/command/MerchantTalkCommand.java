package fr.merchanttalk.command;

import fr.merchanttalk.MerchantTalkPlugin;
import fr.merchanttalk.model.Merchant;
import fr.merchanttalk.model.StoredLocation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** /merchanttalk (alias /mt) */
public final class MerchantTalkCommand implements CommandExecutor, TabCompleter {
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> SUBS = List.of("help", "list", "create", "place", "tp", "delete", "open", "web", "sendpack", "stats", "blocked", "reload");

    private final MerchantTalkPlugin plugin;

    public MerchantTalkCommand(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(sender);
            case "create", "place" -> place(sender, args, sub.equals("create"));
            case "tp" -> teleport(sender, args);
            case "delete" -> delete(sender, args);
            case "open" -> open(sender, args);
            case "web" -> web(sender);
            case "sendpack" -> sendPack(sender, args);
            case "stats" -> stats(sender);
            case "blocked" -> msg(sender, "<gray>Commandes bloquées pour les joueurs : <yellow>"
                    + escape(String.join(", ", new java.util.TreeSet<>(plugin.commandBlocker().blocked()))));
            case "reload" -> {
                plugin.reloadAll();
                msg(sender, "<green>MerchantTalk rechargé.");
            }
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender s, String label) {
        msg(s, "<gold><bold>MerchantTalk</bold></gold> <gray>- commandes :");
        msg(s, "<yellow>/" + label + " list</yellow> <gray>liste des marchands");
        msg(s, "<yellow>/" + label + " create <id></yellow> <gray>crée un marchand à ta position");
        msg(s, "<yellow>/" + label + " place <id></yellow> <gray>déplace un marchand à ta position");
        msg(s, "<yellow>/" + label + " tp <id></yellow> <gray>te téléporte au marchand");
        msg(s, "<yellow>/" + label + " delete <id></yellow> <gray>supprime un marchand");
        msg(s, "<yellow>/" + label + " open <id> [joueur]</yellow> <gray>ouvre le shop d'un marchand (test)");
        msg(s, "<yellow>/" + label + " web</yellow> <gray>adresse de l'interface web");
        msg(s, "<yellow>/" + label + " sendpack [joueur]</yellow> <gray>renvoie le resource pack");
        msg(s, "<yellow>/" + label + " stats</yellow> <gray>statistiques des marchands");
        msg(s, "<yellow>/" + label + " blocked</yellow> <gray>liste des commandes du shop bloquées");
        msg(s, "<yellow>/" + label + " reload</yellow> <gray>recharge la configuration");
    }

    private void list(CommandSender s) {
        var merchants = plugin.store().data().merchants.values();
        if (merchants.isEmpty()) {
            msg(s, "<gray>Aucun marchand. Crée-en un avec <yellow>/mt create <id></yellow> ou depuis le site.");
            return;
        }
        msg(s, "<gold>Marchands (" + merchants.size() + ") :");
        for (Merchant m : merchants) {
            String where = m.location == null ? "<red>pas placé" :
                    "<gray>" + m.location.world + " " + (int) m.location.x + " " + (int) m.location.y + " " + (int) m.location.z;
            String section = m.section == null || m.section.isBlank() ? "menu principal" : m.section;
            s.sendMessage(MM.deserialize("<dark_gray>- <yellow>" + m.id + "</yellow> ")
                    .append(MM.deserialize(m.displayName == null ? "" : m.displayName))
                    .append(MM.deserialize(" <dark_gray>| <gray>" + escape(section) + " <dark_gray>| " + where)));
        }
    }

    private void stats(CommandSender s) {
        var merchants = plugin.store().data().merchants.values();
        if (merchants.isEmpty()) {
            msg(s, "<gray>Aucun marchand.");
            return;
        }
        msg(s, "<gold>Statistiques des marchands :");
        for (Merchant m : merchants) {
            var st = m.stats();
            s.sendMessage(MM.deserialize("<dark_gray>- <yellow>" + m.id + "</yellow> <gray>ouvertures <white>" + st.opens
                    + "</white> · achats <white>" + st.buys + "</white> (<green>" + String.format(Locale.FRANCE, "%.2f", st.spent)
                    + "</green>) · ventes <white>" + st.sells + "</white> (<green>" + String.format(Locale.FRANCE, "%.2f", st.earned)
                    + "</green>) · sans argent <white>" + st.noMoney));
        }
    }

    private void place(CommandSender s, String[] args, boolean create) {
        if (!(s instanceof Player p)) {
            msg(s, "<red>Commande à utiliser en jeu.");
            return;
        }
        if (args.length < 2) {
            msg(s, "<red>Usage : /mt " + (create ? "create" : "place") + " <id>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        Merchant m = plugin.store().merchant(id);
        if (m == null) {
            if (!create) {
                msg(s, "<red>Marchand inconnu : " + escape(id) + ". Utilise /mt create " + escape(id));
                return;
            }
            if (!ID.matcher(id).matches()) {
                msg(s, "<red>Identifiant invalide : lettres minuscules, chiffres, - et _ (32 max).");
                return;
            }
            m = new Merchant();
            m.id = id;
            plugin.store().data().merchants.put(id, m);
        }
        Location loc = p.getLocation();
        loc.setPitch(0);
        m.location = StoredLocation.of(loc);
        plugin.store().save();
        if (!plugin.npcs().isReady()) {
            msg(s, "<yellow>FancyNpcs n'a pas encore fini de charger, le PNJ apparaîtra dans quelques secondes.");
        }
        plugin.npcs().spawnOrUpdate(m);
        msg(s, "<green>Marchand <yellow>" + id + "</yellow> placé ici. Configure-le sur le site : <yellow>/mt web");
    }

    private void teleport(CommandSender s, String[] args) {
        if (!(s instanceof Player p) || args.length < 2) {
            msg(s, "<red>Usage (en jeu) : /mt tp <id>");
            return;
        }
        Merchant m = plugin.store().merchant(args[1].toLowerCase(Locale.ROOT));
        Location loc = m == null ? null : plugin.npcs().npcLocation(m);
        if (loc == null) {
            msg(s, "<red>Marchand introuvable ou pas encore placé.");
            return;
        }
        p.teleportAsync(loc);
    }

    private void delete(CommandSender s, String[] args) {
        if (args.length < 2) {
            msg(s, "<red>Usage : /mt delete <id>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        Merchant m = plugin.store().merchant(id);
        if (m == null) {
            msg(s, "<red>Marchand inconnu.");
            return;
        }
        plugin.shop().closeFor(id);
        plugin.npcs().remove(m);
        plugin.store().data().merchants.remove(id);
        plugin.store().save();
        msg(s, "<green>Marchand <yellow>" + escape(id) + "</yellow> supprimé.");
    }

    private void open(CommandSender s, String[] args) {
        if (args.length < 2) {
            msg(s, "<red>Usage : /mt open <id> [joueur]");
            return;
        }
        Merchant m = plugin.store().merchant(args[1].toLowerCase(Locale.ROOT));
        if (m == null) {
            msg(s, "<red>Marchand inconnu.");
            return;
        }
        Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : (s instanceof Player p ? p : null);
        if (target == null) {
            msg(s, "<red>Joueur introuvable.");
            return;
        }
        plugin.shop().open(target, m);
    }

    private void web(CommandSender s) {
        if (!plugin.getConfig().getBoolean("web.enabled", true)) {
            msg(s, "<red>L'interface web est désactivée dans config.yml.");
            return;
        }
        String url = plugin.getConfig().getString("web.public-url", "");
        s.sendMessage(MM.deserialize("<gold>[MerchantTalk]</gold> <gray>Interface web : <click:open_url:'"
                + url.replace("'", "") + "'><aqua><underlined>" + escape(url) + "</underlined></aqua></click>"));
        msg(s, "<gray>Le mot de passe est dans <yellow>plugins/MerchantTalk/config.yml</yellow> (web.password).");
    }

    private void sendPack(CommandSender s, String[] args) {
        if (plugin.pack().url() == null) {
            msg(s, "<red>Aucune voix n'a été ajoutée, il n'y a pas encore de pack.");
            return;
        }
        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                msg(s, "<red>Joueur introuvable.");
                return;
            }
            plugin.pack().send(target);
        } else {
            Bukkit.getOnlinePlayers().forEach(plugin.pack()::send);
        }
        msg(s, "<green>Resource pack envoyé.");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBS) if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(sub);
        } else if (args.length == 2 && List.of("place", "tp", "delete", "open").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (String id : plugin.store().data().merchants.keySet()) if (id.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(id);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("open") || args.length == 2 && args[0].equalsIgnoreCase("sendpack")) {
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
        }
        return out;
    }

    private static void msg(CommandSender s, String mini) {
        Component c = MM.deserialize("<gold>[MerchantTalk]</gold> " + mini);
        s.sendMessage(c);
    }

    private static String escape(String s) {
        return MM.escapeTags(s);
    }
}
