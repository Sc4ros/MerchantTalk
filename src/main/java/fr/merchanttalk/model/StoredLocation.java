package fr.merchanttalk.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class StoredLocation {
    public String world;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;

    public static StoredLocation of(Location loc) {
        StoredLocation s = new StoredLocation();
        s.world = loc.getWorld().getName();
        s.x = loc.getX();
        s.y = loc.getY();
        s.z = loc.getZ();
        s.yaw = loc.getYaw();
        s.pitch = loc.getPitch();
        return s;
    }

    /** @return la position, ou null si le monde n'est pas chargé. */
    public Location toBukkit() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }
}
