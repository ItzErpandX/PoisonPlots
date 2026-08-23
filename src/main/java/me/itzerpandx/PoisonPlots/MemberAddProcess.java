package me.itzerpandx.PoisonPlots;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.itzerpandx.PoisonPlots.structures.Plot;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public record MemberAddProcess(Player owner, Plot plot) implements Listener {
    public MemberAddProcess(Player owner, Plot plot) {
        this.owner = owner;
        this.plot = plot;
        Bukkit.getPluginManager().registerEvents(this, PlotsPlugin.INSTANCE);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (event.getPlayer() != owner) {
            return;
        }

        event.setCancelled(true);
        String playerName = event.signedMessage().message().trim();

        Bukkit.getScheduler().runTask(PlotsPlugin.INSTANCE, () -> processName(playerName));
    }

    private void processName(String playerName) {
        if (!owner.isOnline()) {
            finish();
            return;
        }

        if (!plot.isOwner(owner.getUniqueId())) {
            finish();
            return;
        }

        if (plot.getMembers().size() >= plot.getMaxMembers()) {
            owner.sendMessage(PlotsPlugin.INSTANCE.getPluginConfig()
                    .getMessageComponent("max-members-reached"));
            finish();
            return;
        }

        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            owner.sendMessage(prefixMessage("<red>No player with that name is online</red>"));
            finish();
            return;
        }

        if (player.getUniqueId().equals(owner.getUniqueId())) {
            owner.sendMessage(prefixMessage("<red>You are already the plot owner</red>"));
            finish();
            return;
        }

        if (plot.getMembers().contains(player.getUniqueId())) {
            owner.sendMessage(prefixMessage("<red>That player is already a part of the plot</red>"));
            finish();
            return;
        }

        plot.addMember(player);
        owner.sendMessage(prefixMessage(
                "<green>Added " + player.getName() + " to your plot</green>"));
        finish();
    }

    private net.kyori.adventure.text.Component prefixMessage(String message) {
        return PlotsPlugin.INSTANCE.getPluginConfig()
                .getMessageComponent("prefix")
                .append(MiniMessage.miniMessage().deserialize(message));
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        if (event.getPlayer() == owner) {
            finish();
        }
    }

    private void finish() {
        HandlerList.unregisterAll(this);
    }
}