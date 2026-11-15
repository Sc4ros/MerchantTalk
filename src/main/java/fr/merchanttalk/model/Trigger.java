package fr.merchanttalk.model;

/** Moments où un marchand peut parler. */
public enum Trigger {
    OPEN("Ouverture du shop"),
    CLOSE("Fermeture du shop"),
    BUY("Achat réussi"),
    SELL("Vente réussie"),
    NO_MONEY("Pas assez d'argent"),
    FAIL("Transaction impossible (inventaire plein, pas assez d'objets...)"),
    GREET("Un joueur s'approche"),
    DENIED("Joueur sans la permission requise"),
    REGULAR("Un habitué s'approche (remplace le salut)"),
    PROMO("Annonce de la promo quand un joueur s'approche"),
    CLOSED("Clic quand la boutique est fermée");

    private final String label;

    Trigger(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
