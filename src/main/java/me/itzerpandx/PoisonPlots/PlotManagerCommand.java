package me.itzerpandx.PoisonPlots;

import com.sk89q.worldedit.IncompleteRegionException;
import me.itzerpandx.PoisonPlots.structures.Plot;
import me.itzerpandx.PoisonPlots.structures.Point;
import me.itzerpandx.PoisonPlots.structures.Region;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Stream;

public class PlotManagerCommand implements TabExecutor {

    private static final PlotsPlugin PLUGIN = PlotsPlugin.INSTANCE;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            usage(sender, "/" + label + " help");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(sender, label, args);
            case "delete" -> handleDelete(sender, label, args);
            case "region" -> handleRegion(sender, label, args);
            case "evict" -> handleEvict(sender, label, args);
            case "sign" -> handleSign(sender, label, args);
            case "maxmembers" -> handleMaxMembers(sender, label, args);
            case "rent" -> handleRent(sender, label, args);
            case "setcurrency" -> handleSetCurrency(sender);
            case "getcurrency" -> handleGetCurrency(sender);
            case "info" -> handleInfo(sender, args);
            case "bypass" -> handleBypass(sender);
            case "owner" -> handleOwner(sender, label, args);
            case "myplots" -> handleMyPlots(sender);
            case "time" -> handleTime(sender, label, args);
            case "help" -> handleHelp(sender, label);
            case "save" -> handleSave(sender);
            case "reload" -> handleReload(sender);
            default -> usage(sender, "/" + label + " help");
        }
        return true;
    }

    // ---------- shared helpers ----------

    private boolean noPermission(CommandSender sender, String perm) {
        if (!sender.hasPermission(perm)) {
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("permission-denied"));
            return true;
        }
        return false;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("only-players"));
        return null;
    }

    private boolean plotMissing(CommandSender sender, String id) {
        if (!PLUGIN.plotExists(id)) {
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("plot-not-found"));
            return true;
        }
        return false;
    }

    private void usage(CommandSender sender, String usage) {
        sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("incorrect-usage", "usage", usage));
    }

    // ---------- subcommands ----------

    private void handleCreate(CommandSender sender, String label, String[] args) {
        if (noPermission(sender, "poisonplots.command.create")) return;
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length != 2) { usage(sender, "/" + label + " create <id>"); return; }

        String id = args[1].replace(".", "_");
        if (PLUGIN.plotExists(id)) {
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("plot-already-exists"));
            return;
        }
        try {
            Plot plot = new Plot(Region.fromWorldEditSelection(player));
            PLUGIN.registerPlot(id, plot);
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("plot-created", "id", id));
        } catch (IncompleteRegionException e) {
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("must-select-region"));
        }
    }

    private void handleDelete(CommandSender sender, String label, String[] args) {
        if (noPermission(sender, "poisonplots.command.delete")) return;
        if (args.length != 2) { usage(sender, "/" + label + " delete <id>"); return; }
        String id = args[1];
        if (plotMissing(sender, id)) return;

        PLUGIN.deletePlot(id);
        sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("plot-deleted", "id", id));
    }

    private void handleRegion(CommandSender sender, String label, String[] args) {
        if (noPermission(sender, "poisonplots.command.region")) return;
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length != 2) { usage(sender, "/" + label + " region <id>"); return; }
        String id = args[1];
        if (plotMissing(sender, id)) return;

        try {
            Region region = Region.fromWorldEditSelection(player);
            Plot plot = PLUGIN.getPlot(id);
            plot.setRegion(region);
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("plot-created", "id", id));
        } catch (IncompleteRegionException e) {
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("must-select-region"));
        }
    }

    private void handleEvict(CommandSender sender, String label, String[] args) {
        if (noPermission(sender, "poisonplots.command.evict")) return;
        if (args.length != 2) { usage(sender, "/" + label + " evict <id>"); return; }
        String id = args[1];
        if (plotMissing(sender, id)) return;

        PLUGIN.getPlot(id).evict();
        sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("plot-abandoned"));
    }

    private void handleSign(CommandSender sender, String label, String[] args) {
        if (noPermission(sender, "poisonplots.command.sign")) return;
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length != 2) { usage(sender, "/" + label + " sign <id>"); return; }
        String id = args[1];
        if (plotMissing(sender, id)) return;

        Block block = player.getTargetBlockExact(5, FluidCollisionMode.NEVER);
        if (block == null || !(block.getState() instanceof Sign)) {
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("not-looking-at-sign"));
            return;
        }
        Plot plot = PLUGIN.getPlot(id);
        Location loc = block.getLocation();
        plot.setSignLocation(loc);
        sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("sign-set", "id", id,
                "x", String.valueOf(loc.getBlockX()), "y", String.valueOf(loc.getBlockY()), "z", String.valueOf(loc.getBlockZ())));
    }

    private void handleMaxMembers(CommandSender sender, String label, String[] args) {
        if (noPermission(sender, "poisonplots.command.maxmembers")) return;
        if (args.length != 3) { usage(sender, "/" + label + " maxmembers <id> <amount>"); return; }
        String id = args[1];
        if (plotMissing(sender, id)) return;

        try {
            int amount = Integer.parseInt(args[2]);
            PLUGIN.getPlot(id).setMaxMembers(amount);
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("max-members-set", "id", id, "amount", String.valueOf(amount)));
        } catch (NumberFormatException e) {
            usage(sender, "/" + label + " maxmembers <id> <amount>");
        }
    }

    private void handleRent(CommandSender sender, String label, String[] args) {
        if (noPermission(sender, "poisonplots.command.rent")) return;
        if (args.length != 3) { usage(sender, "/" + label + " rent <id> <amount>"); return; }
        String id = args[1];
        if (plotMissing(sender, id)) return;

        try {
            int amount = Integer.parseInt(args[2]);
            PLUGIN.getPlot(id).setRent(amount);
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("rent-set", "id", id, "amount", String.valueOf(amount)));
        } catch (NumberFormatException e) {
            usage(sender, "/" + label + " rent <id> <amount>");
        }
    }

    private void handleSetCurrency(CommandSender sender) {
        if (noPermission(sender, "poisonplots.command.setcurrency")) return;
        Player player = requirePlayer(sender);
        if (player == null) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("must-hold-item"));
            return;
        }
        PLUGIN.setCurrency(item);
        sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("currency-set"));
    }

    private void handleGetCurrency(CommandSender sender) {
        if (noPermission(sender, "poisonplots.command.getcurrency")) return;
        Player player = requirePlayer(sender);
        if (player == null) return;

        player.getInventory().addItem(PLUGIN.getCurrency().clone());
        sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("currency-received"));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (noPermission(sender, "poisonplots.command.info")) return;

        String id = resolvePlotId(sender, args);
        if (id == null) return;

        Plot plot = PLUGIN.getPlot(id);
        sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("plot-info",
                "id", id,
                "owner", plot.getOwner() == null ? "None" : Bukkit.getOfflinePlayer(plot.getOwner()).getName(),
                "members", String.valueOf(plot.getMembers().size()),
                "maxMembers", String.valueOf(plot.getMaxMembers()),
                "rent", String.valueOf(plot.getRent()),
                "remaining", plot.getPrettyRemainingTime()));
    }

    /** Resolves the plot id for /plotman info, either from an explicit arg,
     *  or by locating the plot the player stands in / looks at. Sends error messages itself. */
    private String resolvePlotId(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            String id = args[1];
            if (plotMissing(sender, id)) return null;
            return id;
        }

        Player player = requirePlayer(sender);
        if (player == null) return null;

        String id = PLUGIN.getPlotAt(new Point(
                (int) player.getLocation().getX(),
                (int) player.getLocation().getY(),
                (int) player.getLocation().getZ()));

        if (id == null) {
            Block block = player.getTargetBlockExact(5, FluidCollisionMode.NEVER);
            if (block != null && block.getState() instanceof Sign) {
                for (Map.Entry<String, Plot> entry : PLUGIN.getPlots().entrySet()) {
                    Location signLoc = entry.getValue().getSignLocation();
                    if (signLoc != null && signLoc.getBlock().equals(block)) {
                        id = entry.getKey();
                        break;
                    }
                }
            }
        }

        if (id == null) {
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("plot-info-not-found"));
            return null;
        }
        return id;
    }

    private void handleBypass(CommandSender sender) {
        if (noPermission(sender, "poisonplots.command.bypass")) return;
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (noPermission(player, "poisonplots.bypass")) return;

        boolean bypass = !PLUGIN.isBypassing(player);
        PLUGIN.setBypassing(player, bypass);
        Component prefix = PLUGIN.getPluginConfig().getMessageComponent("prefix");
        Component message = bypass
                ? MiniMessage.miniMessage().deserialize("<green>Bypass enabled</green>")
                : MiniMessage.miniMessage().deserialize("<red>Bypass disabled</red>");
        sender.sendMessage(prefix.append(message));
    }

    private void handleOwner(CommandSender sender, String label, String[] args) {
        if (noPermission(sender, "poisonplots.command.owner")) return;
        if (args.length != 3) { usage(sender, "/" + label + " owner <id> <player>"); return; }
        String id = args[1];
        if (plotMissing(sender, id)) return;

        @Nullable OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[2]);
        if (!targetPlayer.hasPlayedBefore()) {
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("player-not-found"));
            return;
        }
        PLUGIN.getPlot(id).setOwner(targetPlayer.getUniqueId());
        sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("plot-owner-set", "id", id, "player", targetPlayer.getName()));
    }

    private void handleMyPlots(CommandSender sender) {
        if (noPermission(sender, "poisonplots.command.myplots")) return;
        Player player = requirePlayer(sender);
        if (player == null) return;

        List<String> ownedPlotIds = PLUGIN.getPlayerOwnedPlots(player.getUniqueId());
        if (ownedPlotIds.isEmpty()) {
            player.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("no-plots-owned"));
            return;
        }

        player.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("plots-header"));
        for (String plotId : ownedPlotIds) {
            Plot plot = PLUGIN.getPlot(plotId);
            if (plot == null) continue;

            Location signLoc = plot.getSignLocation();
            String entry = signLoc != null
                    ? PLUGIN.getPluginConfig().getMessageString("plot-entry",
                    "id", plotId,
                    "x", String.valueOf(signLoc.getBlockX()),
                    "y", String.valueOf(signLoc.getBlockY()),
                    "z", String.valueOf(signLoc.getBlockZ()),
                    "time", plot.getRemaining() <= 0 ? "00:00:00" : plot.getPrettyRemainingTime())
                    : PLUGIN.getPluginConfig().getMessageString("plot-entry",
                    "id", plotId, "x", "?", "y", "?", "z", "?", "time", "?");
            player.sendMessage(MiniMessage.miniMessage().deserialize(entry));
        }
    }

    private void handleTime(CommandSender sender, String label, String[] args) {
        if (noPermission(sender, "poisonplots.command.time")) return;
        if (args.length < 4) { usage(sender, "/" + label + " time <set|add|remove> <id|all> <seconds>"); return; }

        String action = args[1].toLowerCase();
        String id = args[2];

        long timeMs;
        try {
            timeMs = Long.parseLong(args[3]) * 1000L;
        } catch (NumberFormatException e) {
            usage(sender, "/" + label + " time <set|add|remove> <id|all> <seconds>");
            return;
        }

        if (!Set.of("set", "add", "remove").contains(action)) {
            usage(sender, "/" + label + " time <set|add|remove> <id|all> <seconds>");
            return;
        }

        if (id.equalsIgnoreCase("all")) {
            int count = 0;
            for (Plot plot : PLUGIN.getPlots().values()) {
                applyTime(plot, action, timeMs);
                count++;
            }
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("plot-time-modified-all",
                    "action", action, "count", String.valueOf(count), "seconds", args[3]));
        } else {
            if (plotMissing(sender, id)) return;
            Plot plot = PLUGIN.getPlot(id);
            applyTime(plot, action, timeMs);
            String key = switch (action) {
                case "set" -> "plot-time-set";
                case "add" -> "plot-time-added";
                default -> "plot-time-removed"; // "remove"
            };
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent(key, "id", id, "seconds", args[3]));
        }
    }

    private void applyTime(Plot plot, String action, long timeMs) {
        long current = plot.getExpires();
        long newTime = switch (action) {
            case "set" -> System.currentTimeMillis() + timeMs;
            case "add" -> current + timeMs;
            default -> Math.max(current - timeMs, System.currentTimeMillis()); // "remove"
        };
        plot.setExpires(newTime);
    }

    private void handleHelp(CommandSender sender, String label) {
        if (noPermission(sender, "poisonplots.command.help")) return;
        for (String line : PLUGIN.getPluginConfig().getHelpMessage(label)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(line));
        }
    }

    private void handleSave(CommandSender sender) {
        if (noPermission(sender, "poisonplots.command.save")) return;

        sender.sendMessage(
                PLUGIN.getPluginConfig()
                        .getMessageComponent("saving-plots")
        );

        PLUGIN.savePlots();

        sender.sendMessage(
                PLUGIN.getPluginConfig()
                        .getMessageComponent("plots-saved")
        );
    }

    private void handleReload(CommandSender sender) {
        if (noPermission(sender, "poisonplots.command.reload")) return;
        sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("reloading-config"));
        try {
            PLUGIN.getPluginConfig().reload();
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("config-reloaded"));
        } catch (Exception e) {
            PLUGIN.getLogger().log(Level.SEVERE, "Failed to reload config", e);
            sender.sendMessage(PLUGIN.getPluginConfig().getMessageComponent("reload-failed"));
        }
    }

    // ---------- tab complete (unchanged logic, cleaned up) ----------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, String[] args) {
        if (args.length == 1) {
            return Stream.of("create", "delete", "region", "evict", "sign", "maxmembers", "rent",
                            "setcurrency", "getcurrency", "info", "myplots", "bypass", "owner",
                            "time", "save", "reload", "help")
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args[0].equalsIgnoreCase("time")) {
            return switch (args.length) {
                case 2 -> Stream.of("set", "add", "remove")
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
                case 3 -> {
                    List<String> completions = new ArrayList<>(PLUGIN.getPlots().keySet());
                    completions.add("all");
                    yield completions.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .toList();
                }
                case 4 -> List.of("<seconds>");
                default -> List.of();
            };
        }

        boolean needsPlotId = args.length == 2 && Set.of(
                "delete", "region", "evict", "sign", "maxmembers", "rent", "info", "owner"
        ).contains(args[0].toLowerCase());

        if (needsPlotId) {
            return PLUGIN.getPlots().keySet().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return List.of();
    }
}