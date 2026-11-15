package fr.merchanttalk.model;

/** Un emplacement d'un marchand ambulant. */
public final class Spot {
    public String name;
    public StoredLocation location;

    public Spot() {
    }

    public Spot(String name, StoredLocation location) {
        this.name = name;
        this.location = location;
    }
}
