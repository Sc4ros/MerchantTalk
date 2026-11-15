package fr.merchanttalk.model;

/** Ce qu'un marchand retient d'un joueur. */
public final class CustomerRecord {
    public String name;
    /** Visites (ouvertures du shop, espacées d'au moins 5 minutes). */
    public long visits;
    public long buys;
    public long sells;
    /** Argent dépensé chez ce marchand. */
    public double spent;
    /** Argent gagné en vendant à ce marchand. */
    public double earned;
    public long firstVisit;
    public long lastVisit;
}
