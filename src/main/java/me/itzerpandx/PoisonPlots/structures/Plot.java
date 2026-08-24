package me.itzerpandx.PoisonPlots.structures;

import me.itzerpandx.PoisonPlots.PlotGui;
import me.itzerpandx.PoisonPlots.PlotsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class Plot extends BukkitRunnable implements ConfigurationSerializable, Listener {
    private long expires;
    private int maxMembers;
    private int rent;
    private Location sign;
    private Region region;
    private UUID owner;
    private final ArrayList<UUID> members;
    private String plotId;
    private boolean destroyed;

    public Plot(Region region) {
        this.region = region;
        this.expires = 0L;
        this.maxMembers = PlotsPlugin.INSTANCE.getPluginConfig().getDefaultMaxMembers();
        this.rent = PlotsPlugin.INSTANCE.getPluginConfig().getDefaultPlotRent();
        this.members = new ArrayList<>();

        Bukkit.getPluginManager().registerEvents(this, PlotsPlugin.INSTANCE);
        runTaskTimer(PlotsPlugin.INSTANCE, 0L, 20L);
    }

    @Override
    public @NonNull Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("expires", expires);
        map.put("max-members", maxMembers);
        map.put("rent", rent);
        if (sign != null) map.put("sign", sign);
        map.put("region", region);
        map.put("members", members.stream().map(UUID::toString).collect(Collectors.toList()));
        if (owner != null) map.put("owner", owner.toString());
        return map;
    }

    public void destroy() {
        if (destroyed) return;
        destroyed = true;
        HandlerList.unregisterAll(this);
        cancel();
    }

    public List<UUID> getMembers() { return new ArrayList<>(members); }
    public int getMaxMembers() { return maxMembers; }
    public int getRent() { return rent; }
    public UUID getOwner() { return owner; }
    public long getRemaining() { return expires - System.currentTimeMillis(); }
    public long getExpires() { return expires; }

    public void setExpires(long expires) {
        this.expires = expires;
        saveToDatabase();
    }

    public void setRemaining(long duration) {
        this.expires = System.currentTimeMillis() + duration;
        saveToDatabase();
    }

    public void addMember(Player player) { addMember(player.getUniqueId()); }

    public void addMember(UUID player) {
        if (player == null || members.contains(player) || members.size() >= maxMembers) return;
        members.add(player);
        saveToDatabase();
    }

    private void saveToDatabase() {
        if (plotId != null && !destroyed && PlotsPlugin.INSTANCE.getDatabase() != null) {
            PlotsPlugin.INSTANCE.getDatabase().savePlotAsync(plotId, this);
        }
    }

    public void removeMember(UUID player) {
        if (player == null || player.equals(owner)) return;
        if (members.remove(player)) saveToDatabase();
    }

    public void setPlotId(String id) { this.plotId = id; }

    public void setOwner(UUID player) {
        owner = player;
        saveToDatabase();
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = Math.max(0, maxMembers);
        if (members.size() > this.maxMembers) {
            members.subList(this.maxMembers, members.size()).clear();
        }
        saveToDatabase();
    }

    public void setRent(int rent) {
        this.rent = Math.max(0, rent);
        saveToDatabase();
    }

    public void evict() {
        members.clear();
        owner = null;
        expires = 0L;
        saveToDatabase();
    }

    public void rent(Player player) {
        members.clear();
        owner = player.getUniqueId();
        members.add(owner);
        expires = System.currentTimeMillis()
                + PlotsPlugin.INSTANCE.getPluginConfig().getStartRent()
                * 24L * 60L * 60L * 1000L;
        saveToDatabase();
    }

    public boolean isRented() { return owner != null; }
    public Location getSignLocation() { return sign; }

    public void setSignLocation(Location sign) {
        this.sign = sign == null ? null : sign.clone();
        saveToDatabase();
    }

    public Region getRegion() { return region; }

    public void setRegion(Region region) {
        this.region = region;
        saveToDatabase();
    }

    public boolean hasAccess(Player player) { return hasAccess(player.getUniqueId()); }
    public boolean hasAccess(UUID player) { return isOwner(player) || members.contains(player); }
    public boolean isOwner(UUID player) { return owner != null && owner.equals(player); }

    public String getPrettyRemainingTime() {
        long remaining = getRemaining();
        return remaining <= 0L ? "00:00:00" : DurationFormatUtils.formatDuration(remaining, "HH:mm:ss", true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        handleProtection(event.getPlayer(), event.getClickedBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        handleProtection(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        handleProtection(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLiquidPlace(PlayerBucketEmptyEvent event) {
        handleProtection(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLiquidBreak(PlayerBucketFillEvent event) {
        handleProtection(event.getPlayer(), event.getBlock(), event);
    }

    private void handleProtection(Player player, Block block, org.bukkit.event.Cancellable event) {
        if (block == null || region == null) return;
        if (!region.contains(Point.fromLocation(block.getLocation()))) return;

        if (PlotsPlugin.INSTANCE.isBypassing(player) || hasAccess(player)) {
            // Uncancel event previously cancelled by WorldGuard
            event.setCancelled(false);
        } else {
            // Block unauthorized players even if WorldGuard permitted it
            event.setCancelled(true);
            player.sendMessage(PlotsPlugin.INSTANCE.getPluginConfig().getMessageComponent("no-access"));
        }
    }

    @EventHandler
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || sign == null) return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign) || !block.getLocation().equals(sign)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (!isRented()) {
            int maxPlots = PlotsPlugin.INSTANCE.getMaxPlotsForPlayer(player);
            if (PlotsPlugin.INSTANCE.getPlayerOwnedPlotsCount(player.getUniqueId()) >= maxPlots) {
                player.sendMessage(PlotsPlugin.INSTANCE.getPluginConfig().getMessageComponent("plot-limit-exceeded"));
                return;
            }

            long cost = (long) rent * PlotsPlugin.INSTANCE.getPluginConfig().getStartRent();
            if (cost > Integer.MAX_VALUE || !player.getInventory().containsAtLeast(PlotsPlugin.INSTANCE.getCurrency(), (int) cost)) {
                player.sendMessage(PlotsPlugin.INSTANCE.getPluginConfig().getMessageComponent("not-enough-currency-rent"));
                return;
            }

            new me.itzerpandx.PoisonPlots.RentConfirmationGui(player, this);
            return;
        }

        if (!hasAccess(player)) {
            player.sendMessage(PlotsPlugin.INSTANCE.getPluginConfig().getMessageComponent("no-access"));
            return;
        }

        new PlotGui(player, this);
    }

    @Override
    public void run() {
        if (destroyed) return;
        if (isRented()) checkExpired();
        if (sign == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(sign.getWorld()) && player.getLocation().distanceSquared(sign) <= 225.0) {
                updateSign();
                return;
            }
        }
    }

    private void checkExpired() {
        if (getRemaining() <= 0L) {
            Player player = owner == null ? null : Bukkit.getPlayer(owner);
            if (player != null) player.sendMessage(PlotsPlugin.INSTANCE.getPluginConfig().getMessageComponent("plot-expired"));
            evict();
        }
    }

    @SuppressWarnings("deprecation")
    public void updateSign() {
        if (sign == null) return;
        Block block = sign.getBlock();
        BlockState state = block.getState();

        Sign blockSign;

        if (state instanceof Sign signState) {
            blockSign = signState;
        } else {
            block.setType(Material.OAK_SIGN, false);

            BlockState newState = block.getState();
            if (!(newState instanceof Sign newSign)) {
                return;
            }

            blockSign = newSign;
        }

        if (isRented()) {
            String ownerName = Bukkit.getOfflinePlayer(owner).getName();
            blockSign.line(0, line("rented", "line0"));
            blockSign.line(1, line("rented", "line1", "time", getPrettyRemainingTime()));
            blockSign.line(2, line("rented", "line2", "owner", ownerName == null ? "Unknown" : ownerName));
            blockSign.line(3, line("rented", "line3", "members", String.valueOf(members.size()), "maxMembers", String.valueOf(maxMembers)));
        } else {
            long startRent = PlotsPlugin.INSTANCE.getPluginConfig().getStartRent();
            blockSign.line(0, line("unrented", "line0"));
            blockSign.line(1, line("unrented", "line1", "rent", String.valueOf(rent * startRent)));
            blockSign.line(2, line("unrented", "line2", "maxMembers", String.valueOf(maxMembers)));
            blockSign.line(3, line("unrented", "line3"));
        }
        blockSign.update(false, false);
    }

    private @NotNull Component line(
            String type,
            String key,
            String... replacements
    ) {
        return MiniMessage.miniMessage().deserialize(
                PlotsPlugin.INSTANCE.getPluginConfig()
                        .getSignLinePlain(type, key, replacements)
        );
    }
}