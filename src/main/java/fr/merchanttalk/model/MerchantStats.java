package fr.merchanttalk.model;

/** Statistiques d'un marchand (depuis sa création ou la dernière remise à zéro). */
public final class MerchantStats {
    /** Nombre d'ouvertures du shop via ce marchand. */
    public long opens;
    /** Nombre d'achats réussis. */
    public long buys;
    /** Nombre de ventes réussies. */
    public long sells;
    /** Argent dépensé par les joueurs (achats). */
    public double spent;
    /** Argent gagné par les joueurs (ventes). */
    public double earned;
    /** Joueurs refusés faute d'argent. */
    public long noMoney;
    /** Date (ms) de la dernière activité. */
    public long lastActivity;
}
