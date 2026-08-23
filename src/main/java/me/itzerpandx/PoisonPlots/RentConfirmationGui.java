package me.itzerpandx.PoisonPlots;

import me.itzerpandx.PoisonPlots.structures.Plot;
import me.itzerpandx.PoisonPlots.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class RentConfirmationGui implements Listener {
    private final Player player;
    private final Plot plot;
    private final Inventory inventory;
    private boolean completed;

    @SuppressWarnings("deprecation")
    public RentConfirmationGui(Player player, Plot plot) {
        this.player = player;
        this.plot = plot;
        this.inventory = Bukkit.createInventory(null, 27, "Confirm Plot Rental");

        Bukkit.getPluginManager().registerEvents(this, PlotsPlugin.INSTANCE);
        refresh();
        player.openInventory(inventory);
    }

    private void refresh() {
        inventory.clear();

        long startRent = PlotsPlugin.INSTANCE.getPluginConfig().getStartRent();
        long cost = (long) plot.getRent() * startRent;

        ItemStack infoItem = new ItemBuilder(Material.PAPER)
                .setName("<gold>Plot Rental Details</gold>")
                .addLore("<white>Rent Cost: <yellow>" + cost + "</yellow></white>")
                .addLore("<white>Duration: <yellow>" + startRent + " days</yellow></white>")
                .addLore("")
                .addLore("<gray>Click CONFIRM to proceed</gray>")
                .create();
        inventory.setItem(13, infoItem);

        ItemStack confirmItem = new ItemBuilder(Material.LIME_CONCRETE)
                .setName("<green>✓ CONFIRM RENTAL</green>")
                .addLore("<gray>Click to rent this plot</gray>")
                .create();
        inventory.setItem(12, confirmItem);

        ItemStack cancelItem = new ItemBuilder(Material.RED_CONCRETE)
                .setName("<red>✗ CANCEL</red>")
                .addLore("<gray>Click to cancel rental</gray>")
                .create();
        inventory.setItem(14, cancelItem);

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setName(" ")
                .create();

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null
                    || inventory.getItem(i).getType().isAir()) {
                inventory.setItem(i, filler.clone());
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() != player
                || event.getInventory() != inventory) {
            return;
        }

        finish();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (completed
                || event.getWhoClicked() != player
                || event.getView().getTopInventory() != inventory) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 12) {
            confirmRental();
        } else if (slot == 14) {
            finish();
            player.closeInventory();
        }
    }

    private void confirmRental() {
        if (completed) {
            return;
        }

        if (plot.isRented()) {
            player.sendMessage(
                    PlotsPlugin.INSTANCE.getPluginConfig()
                            .getMessageComponent("plot-already-rented")
            );
            finish();
            player.closeInventory();
            return;
        }

        long startRent = PlotsPlugin.INSTANCE.getPluginConfig().getStartRent();
        long longCost = (long) plot.getRent() * startRent;

        if (longCost > Integer.MAX_VALUE) {
            player.sendMessage(
                    PlotsPlugin.INSTANCE.getPluginConfig()
                            .getMessageComponent("not-enough-currency-rent")
            );
            finish();
            player.closeInventory();
            return;
        }

        int cost = (int) longCost;
        ItemStack currency = PlotsPlugin.INSTANCE.getCurrency();

        if (!player.getInventory().containsAtLeast(currency, cost)) {
            player.sendMessage(
                    PlotsPlugin.INSTANCE.getPluginConfig()
                            .getMessageComponent("not-enough-currency-rent")
            );
            finish();
            player.closeInventory();
            return;
        }

        completed = true;
        HandlerList.unregisterAll(this);

        ItemUtil.takeItem(player, currency, cost);
        plot.rent(player);

        player.closeInventory();
        player.sendMessage(
                PlotsPlugin.INSTANCE.getPluginConfig()
                        .getMessageComponent("plot-rented")
        );
    }

    private void finish() {
        if (completed) {
            return;
        }

        completed = true;
        HandlerList.unregisterAll(this);
    }
}