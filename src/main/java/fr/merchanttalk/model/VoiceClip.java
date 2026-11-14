package fr.merchanttalk.model;

/** Un fichier audio OGG mono stocké dans plugins/MerchantTalk/sounds. */
public final class VoiceClip {
    public String id;
    public String name;
    public long size;
    public double duration;

    public String fileName() {
        return id + ".ogg";
    }

    /** Clé du son dans le resource pack (merchanttalk:voice.&lt;id&gt;). */
    public String soundKey() {
        return "merchanttalk:voice." + id;
    }
}
