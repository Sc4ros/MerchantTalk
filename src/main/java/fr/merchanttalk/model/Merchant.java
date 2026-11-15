package fr.merchanttalk.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Un PNJ marchand. */
public final class Merchant {
    public String id;
    /** Nom affiché au-dessus du PNJ et dans le chat (MiniMessage). */
    public String displayName = "<gold>Marchand";
    /** Section EconomyShopGUI ouverte. Vide = menu principal. */
    public String section = "";
    /** Ce que le marchand ouvre : "SHOP" (le shop / une section) ou "SELLGUI" (le menu de vente /sellgui). */
    public String mode = "SHOP";

    public boolean isSellMenu() {
        return "SELLGUI".equalsIgnoreCase(mode);
    }
    /** Nom du fichier de skin dans plugins/MerchantTalk/skins, ou null pour le skin par défaut. */
    public String skinFile;
    public boolean slimSkin;
    public boolean lookAtPlayer = true;
    public double greetRadius = 5.0;
    public int greetCooldownSeconds = 120;
    /** Permission requise pour commercer avec ce marchand. Vide = tout le monde. */
    public String permission = "";
    /** Particules au-dessus du PNJ quand il parle. */
    public boolean talkParticles = true;
    public StoredLocation location;
    public MerchantStats stats = new MerchantStats();
    public Map<Trigger, List<DialogueLine>> lines = new LinkedHashMap<>();

    public MerchantStats stats() {
        if (stats == null) stats = new MerchantStats();
        return stats;
    }

    public List<DialogueLine> lines(Trigger trigger) {
        if (lines == null) lines = new LinkedHashMap<>();
        return lines.computeIfAbsent(trigger, t -> new ArrayList<>());
    }
}
