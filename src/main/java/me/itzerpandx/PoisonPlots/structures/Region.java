package me.itzerpandx.PoisonPlots.structures;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.World;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class Region
        implements ConfigurationSerializable {
    private final Point min;
    private final Point max;
    private final World world;

    public Region(World world, Point p1, Point p2) {
        this.min = Point.min(p1, p2);
        this.max = Point.max(p1, p2);
        this.world = world;
    }

    public Point getMin() {
        return this.min;
    }

    public Point getMax() {
        return this.max;
    }

    public World getWorld() {
        return this.world;
    }

    public boolean contains(Point point) {
        return point.x() >= this.min.x() && point.x() <= this.max.x() && point.y() >= this.min.y() && point.y() <= this.max.y() && point.z() >= this.min.z() && point.z() <= this.max.z();
    }


    public @NotNull Map<String, Object> serialize() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("min", this.min);
        map.put("max", this.max);
        map.put("world", this.world.getUID().toString());
        return map;
    }

    public static Region fromWorldEditSelection(Player player) throws IncompleteRegionException {
        com.sk89q.worldedit.regions.Region selection = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player)).getSelection();
        BlockVector3 vmin = selection.getMinimumPoint();
        BlockVector3 vmax = selection.getMaximumPoint();
        Point min = new Point(vmin.x(), vmin.y(), vmin.z());
        Point max = new Point(vmax.x(), vmax.y(), vmax.z());
        World world = BukkitAdapter.adapt(Objects.requireNonNull(selection.getWorld()));
        return new Region(world, min, max);
    }
}
