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
    /** Conversation libre avec l'IA (si elle est activée dans config.yml). */
    public boolean aiEnabled = false;
    /** Personnalité, histoire, façon de parler du marchand (envoyée à l'IA). */
    public String aiPersonality = "";
    /** Surnoms pour l'appeler dans le chat, séparés par des virgules (en plus de son nom affiché). */
    public String aiNames = "";
    /** Nombre de visites à partir duquel un joueur est un « habitué ». */
    public int regularVisits = 10;

    /** Mouvements (achat, vente, salut) : le PNJ montre l'objet, s'incline... */
    public boolean animations = false;

    /** Promotion (optionnelle). */
    public boolean promoEnabled = false;
    public String promoLabel = "Promo";
    /** Réduction sur les achats, en %. */
    public int promoBuyDiscount = 10;
    /** Bonus sur les ventes, en %. */
    public int promoSellBonus = 0;
    /** Jours de la promo, ex. "SATURDAY,SUNDAY". Vide = tous les jours. */
    public String promoDays = "";

    /** Horaires d'ouverture (optionnels), en heures du jeu. */
    public boolean hoursEnabled = false;
    public String openHours = "6-19";
    /** Fermé : REFUSE (reste là mais refuse), SLEEP (dort), HIDE (disparaît). */
    public String closedMode = "SLEEP";

    /** Marchand ambulant (optionnel) : il change d'emplacement régulièrement. */
    public boolean wanderEnabled = false;
    public int wanderMinutes = 60;
    public boolean wanderAnnounce = true;
    public List<Spot> spots = new ArrayList<>();
    public int spotIndex = 0;
    public long wanderLastMove = 0;

    public StoredLocation location;
    public MerchantStats stats = new MerchantStats();
    public Map<Trigger, List<DialogueLine>> lines = new LinkedHashMap<>();

    public List<Spot> spots() {
        if (spots == null) spots = new ArrayList<>();
        return spots;
    }

    /** L'emplacement actuel : celui de la tournée si le marchand est ambulant, sinon sa place fixe. */
    public StoredLocation currentLocation() {
        if (wanderEnabled && !spots().isEmpty()) {
            Spot s = spots().get(Math.floorMod(spotIndex, spots().size()));
            if (s != null && s.location != null) return s.location;
        }
        return location;
    }

    public Spot currentSpot() {
        if (!wanderEnabled || spots().isEmpty()) return null;
        return spots().get(Math.floorMod(spotIndex, spots().size()));
    }

    public MerchantStats stats() {
        if (stats == null) stats = new MerchantStats();
        return stats;
    }

    public List<DialogueLine> lines(Trigger trigger) {
        if (lines == null) lines = new LinkedHashMap<>();
        return lines.computeIfAbsent(trigger, t -> new ArrayList<>());
    }
}
