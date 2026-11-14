package fr.merchanttalk.model;

/** Une réplique : un texte (chat), une voix optionnelle et des conditions (heure, météo). */
public final class DialogueLine {
    /** Texte MiniMessage affiché dans le chat. Peut être vide pour une voix seule. */
    public String text = "";
    /** Identifiant d'une {@link VoiceClip}, ou null. */
    public String sound;
    /** Moment de la journée en jeu : ANY, MORNING, DAY, EVENING, NIGHT. */
    public String time = "ANY";
    /** Météo : ANY, CLEAR, RAIN, THUNDER. */
    public String weather = "ANY";

    public boolean isEmpty() {
        return (text == null || text.isBlank()) && (sound == null || sound.isBlank());
    }

    /** @return true si la réplique a au moins une condition (heure ou météo). */
    public boolean isConditional() {
        return !"ANY".equals(timeOrAny()) || !"ANY".equals(weatherOrAny());
    }

    public String timeOrAny() {
        return time == null || time.isBlank() ? "ANY" : time;
    }

    public String weatherOrAny() {
        return weather == null || weather.isBlank() ? "ANY" : weather;
    }
}
