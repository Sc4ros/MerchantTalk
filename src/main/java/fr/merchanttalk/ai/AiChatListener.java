package fr.merchanttalk.ai;

import fr.merchanttalk.MerchantTalkPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Transmet les messages de chat à l'IA des marchands (les messages restent visibles normalement). */
public final class AiChatListener implements Listener {
    private final MerchantTalkPlugin plugin;

    public AiChatListener(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.ai().ready()) return;
        Player player = event.getPlayer();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) plugin.ai().onChat(player, text);
        });
    }
}
