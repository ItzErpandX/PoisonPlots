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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Optional;
import java.util.UUID;

public class PlotGui implements Listener {
    private static final int MAX_VISIBLE_MEMBERS = 17;

    private final Player player;
    private final Plot plot;
    private final Inventory inventory;
    private Page page = Page.MAIN;
    private boolean closed;

    @SuppressWarnings("deprecation")
    public PlotGui(Player player, Plot plot) {
        this.player = player;
        this.plot = plot;
        this.inventory = Bukkit.createInventory(null, 27, "Plot Management");
        Bukkit.getPluginManager().registerEvents(this, PlotsPlugin.INSTANCE);
        refresh();
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() != player || event.getInventory() != inventory) {
            return;
        }
        closeGui();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (closed || event.getWhoClicked() != player || event.getInventory() != inventory) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        if (page == Page.MAIN) {
            handleMainClick(slot);
        } else if (page == Page.CONFIRM_DELETE) {
            handleDeleteClick(slot);
        } else if (page == Page.MEMBER_MANAGEMENT) {
            handleMemberClick(slot);
        }
    }

    private void handleMainClick(int slot) {
        if (slot == 11) {
            page = Page.MEMBER_MANAGEMENT;
            refresh();
            return;
        }

        if (slot == 15) {
            long maxRent = PlotsPlugin.INSTANCE.getPluginConfig().getMaxRent();
            long maxDuration = Math.max(0L, maxRent - 1L) * 24L * 60L * 60L * 1000L;

            if (plot.getRemaining() >= maxDuration) {
                player.sendMessage(PlotsPlugin.INSTANCE.getPluginConfig()
                        .getMessageComponent("plot-already-rented"));
                closeGui();
                return;
            }

            if (!player.getInventory().containsAtLeast(
                    PlotsPlugin.INSTANCE.getCurrency(), plot.getRent())) {
                player.sendMessage(PlotsPlugin.INSTANCE.getPluginConfig()
                        .getMessageComponent("not-enough-currency"));
                closeGui();
                return;
            }

            ItemUtil.takeItem(player, PlotsPlugin.INSTANCE.getCurrency(), plot.getRent());
            plot.setRemaining(plot.getRemaining() + 86_400_000L);
            refresh();
            return;
        }

        if (slot == 26) {
            if (!plot.isOwner(player.getUniqueId())) {
                player.sendMessage(PlotsPlugin.INSTANCE.getPluginConfig()
                        .getMessageComponent("must-be-owner"));
                closeGui();
                return;
            }

            page = Page.CONFIRM_DELETE;
            refresh();
        }
    }

    private void handleDeleteClick(int slot) {
        if (slot == 13) {
            plot.evict();
            player.sendMessage(PlotsPlugin.INSTANCE.getPluginConfig()
                    .getMessageComponent("plot-abandoned"));
        }
        closeGui();
    }

    private void handleMemberClick(int slot) {
        if (slot < 10 || slot >= 10 + MAX_VISIBLE_MEMBERS) {
            return;
        }

        int memberIndex = slot - 10;
        if (memberIndex >= plot.getMaxMembers()) {
            return;
        }

        var members = plot.getMembers();
        if (memberIndex >= members.size()) {
            if (!plot.isOwner(player.getUniqueId())) {
                player.sendMessage(PlotsPlugin.INSTANCE.getPluginConfig()
                        .getMessageComponent("must-be-owner"));
                closeGui();
                return;
            }

            if (members.size() >= plot.getMaxMembers()) {
                player.sendMessage(PlotsPlugin.INSTANCE.getPluginConfig()
                        .getMessageComponent("max-members-reached"));
                closeGui();
                return;
            }

            closeGui();
            player.sendMessage(PlotsPlugin.INSTANCE.getPluginConfig()
                    .getMessageComponent("prefix")
                    .append(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                            .deserialize("<yellow>Please type the name of the person you wish to add in chat</yellow>")));
            new MemberAddProcess(player, plot);
            return;
        }

        if (!plot.isOwner(player.getUniqueId())) {
            player.sendMessage(PlotsPlugin.INSTANCE.getPluginConfig()
                    .getMessageComponent("must-be-owner"));
            closeGui();
            return;
        }

        UUID memberUuid = members.get(memberIndex);
        if (plot.isOwner(memberUuid)) {
            player.sendMessage(PlotsPlugin.INSTANCE.getPluginConfig()
                    .getMessageComponent("cannot-kick-owner"));
            closeGui();
            return;
        }

        plot.removeMember(memberUuid);
        refresh();
    }

    private void refresh() {
        if (closed) {
            return;
        }

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setName(" ").create();

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler.clone());
        }

        if (page == Page.MAIN) {
            inventory.setItem(11, new ItemBuilder(Material.PLAYER_HEAD)
                    .setName("<yellow>Member Management</yellow>")
                    .addLore("<gray>Click this to manage who</gray>")
                    .addLore("<gray>can access your plot</gray>")
                    .create());

            inventory.setItem(15, new ItemBuilder(Material.SUNFLOWER)
                    .setName("<yellow>Pay Rent</yellow>")
                    .addLore("<gray>Click this to extend the</gray>")
                    .addLore("<gray>length of time that this</gray>")
                    .addLore("<gray>plot will remain claimed for</gray>")
                    .addLore("")
                    .addLore("<dark_gray>Currently: <yellow>" + plot.getPrettyRemainingTime() + "</yellow></dark_gray>")
                    .create());

            inventory.setItem(26, new ItemBuilder(Material.BARRIER)
                    .setName("<red>Abandon Plot</red>")
                    .addLore("<dark_red>Clicking this will permanently delete</dark_red>")
                    .addLore("<dark_red>your ownership of this plot</dark_red>")
                    .addLore("<dark_red><!> NO REFUND <!></dark_red>")
                    .create());
            return;
        }

        if (page == Page.CONFIRM_DELETE) {
            inventory.setItem(13, new ItemBuilder(Material.BARRIER)
                    .setName("<red>Confirm Abandon Plot</red>")
                    .addLore("<dark_red>Clicking this will permanently delete</dark_red>")
                    .addLore("<dark_red>your ownership of this plot</dark_red>")
                    .addLore("<dark_red><!> NO REFUND <!></dark_red>")
                    .create());
            return;
        }

        ItemStack emptyMember = new ItemBuilder(Material.PLAYER_HEAD)
                .setName("<yellow>Empty Member Slot</yellow>")
                .addLore("<gray>Click this to add someone to your plot</gray>")
                .create();

        int visibleMembers = Math.min(plot.getMaxMembers(), MAX_VISIBLE_MEMBERS);
        for (int i = 0; i < visibleMembers; i++) {
            inventory.setItem(10 + i, emptyMember.clone());
        }

        int index = 0;
        for (UUID member : plot.getMembers()) {
            if (index >= visibleMembers) {
                break;
            }

            OfflinePlayerData data = new OfflinePlayerData(member);
            ItemStack head = new ItemBuilder(Material.PLAYER_HEAD)
                    .setName("<red>" + data.name() + "</red>")
                    .addLore(plot.isOwner(member)
                            ? "<white>Plot owner</white>"
                            : "<white>Click to kick</white>")
                    .create();

            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(member));
                head.setItemMeta(meta);
            }
            inventory.setItem(10 + index, head);
            index++;
        }
    }

    private void closeGui() {
        if (closed) {
            return;
        }
        closed = true;
        HandlerList.unregisterAll(this);
        if (player.getOpenInventory().getTopInventory().equals(inventory)) {
            player.closeInventory();
        }
    }

    private record OfflinePlayerData(String name) {
        private OfflinePlayerData(UUID uuid) {
            this(Optional.ofNullable(Bukkit.getOfflinePlayer(uuid).getName()).orElse("Unknown"));
        }
    }

    private enum Page {
        MAIN,
        CONFIRM_DELETE,
        MEMBER_MANAGEMENT
    }
}