package fr.merchanttalk.dialogue;

import fr.merchanttalk.MerchantTalkPlugin;
import fr.merchanttalk.model.DialogueLine;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Heure et météo du monde, pour choisir les répliques selon le moment.
 * Les plages horaires sont en heures du jeu (le tick 0 correspond à 6h du matin).
 */
public final class WorldConditions {
    public static final Map<String, String> TIMES = new LinkedHashMap<>();
    public static final Map<String, String> WEATHERS = new LinkedHashMap<>();

    static {
        TIMES.put("ANY", "Toujours");
        TIMES.put("MORNING", "Matin");
        TIMES.put("DAY", "Jour");
        TIMES.put("EVENING", "Soir");
        TIMES.put("NIGHT", "Nuit");
        WEATHERS.put("ANY", "Tout temps");
        WEATHERS.put("CLEAR", "Beau temps");
        WEATHERS.put("RAIN", "Pluie");
        WEATHERS.put("THUNDER", "Orage");
    }

    private static final Map<String, String> DEFAULT_RANGES = Map.of(
            "MORNING", "5-9", "DAY", "6-19", "EVENING", "17-20", "NIGHT", "19-6");

    private final MerchantTalkPlugin plugin;

    public WorldConditions(MerchantTalkPlugin plugin) {
        this.plugin = plugin;
    }

    /** Heure du jeu, de 0.0 à 24.0 (6.0 = lever du soleil). */
    public static double hour(World world) {
        return ((world.getTime() / 1000.0) + 6.0) % 24.0;
    }

    public static String clock(World world) {
        double h = hour(world);
        int hh = (int) h;
        int mm = (int) ((h - hh) * 60);
        return String.format(Locale.ROOT, "%02d:%02d", hh, mm);
    }

    /** Plage horaire configurée, ex. "19-6" (peut passer minuit). */
    public String range(String time) {
        return plugin.getConfig().getString("dialogue.time-periods." + time.toLowerCase(Locale.ROOT),
                DEFAULT_RANGES.getOrDefault(time, "0-24"));
    }

    public boolean timeMatches(String time, World world) {
        if (time == null || time.equals("ANY")) return true;
        if (!TIMES.containsKey(time)) return true;
        String[] parts = range(time).replace(" ", "").split("-");
        if (parts.length != 2) return true;
        try {
            double start = Double.parseDouble(parts[0].replace("h", "").replace(",", "."));
            double end = Double.parseDouble(parts[1].replace("h", "").replace(",", "."));
            double h = hour(world);
            return start <= end ? h >= start && h < end : h >= start || h < end;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    public static boolean weatherMatches(String weather, World world) {
        if (weather == null) return true;
        return switch (weather) {
            case "CLEAR" -> !world.hasStorm();
            case "RAIN" -> world.hasStorm();
            case "THUNDER" -> world.isThundering();
            default -> true;
        };
    }

    public boolean matches(DialogueLine line, World world) {
        if (world == null) return !line.isConditional();
        return timeMatches(line.timeOrAny(), world) && weatherMatches(line.weatherOrAny(), world);
    }

    /** Moments de la journée actuellement actifs dans ce monde (ex. [DAY, EVENING]). */
    public List<String> activeTimes(World world) {
        List<String> list = new ArrayList<>();
        for (String t : TIMES.keySet()) {
            if (!t.equals("ANY") && timeMatches(t, world)) list.add(t);
        }
        return list;
    }

    public static String weather(World world) {
        if (world.isThundering()) return "THUNDER";
        return world.hasStorm() ? "RAIN" : "CLEAR";
    }
}
