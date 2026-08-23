package me.itzerpandx.PoisonPlots.structures;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

public record Point(int x, int y, int z) implements ConfigurationSerializable
{

    public static Point min(Point p1, Point p2) {
        return new Point(Math.min(p1.x, p2.x), Math.min(p1.y, p2.y), Math.min(p1.z, p2.z));
    }

    public static Point max(Point p1, Point p2) {
        return new Point(Math.max(p1.x, p2.x), Math.max(p1.y, p2.y), Math.max(p1.z, p2.z));
    }


    public static Point fromLocation(Location location) {
        return new Point(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }


    public @NotNull Map<String, Object> serialize() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("x", this.x);
        map.put("y", this.y);
        map.put("z", this.z);
        return map;
    }
}
