package fr.merchanttalk.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tout ce qui est enregistré dans data.json. */
public final class PluginData {
    public Map<String, Merchant> merchants = new LinkedHashMap<>();
    public Map<String, VoiceClip> sounds = new LinkedHashMap<>();
    /** Mémoire des clients : marchand -> (UUID du joueur -> fiche). */
    public Map<String, Map<String, CustomerRecord>> customers = new LinkedHashMap<>();
}
