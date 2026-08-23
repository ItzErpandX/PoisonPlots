package me.itzerpandx.PoisonPlots;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ItemUtil {
    public static void takeItem(Player player, ItemStack item, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (remaining <= 0) break;
            if (stack == null || !stack.isSimilar(item)) continue;
            int amt = stack.getAmount();
            if (amt <= remaining) {
                remaining -= amt;
                stack.setAmount(0);
                continue;
            }
            stack.setAmount(stack.getAmount() - remaining);
            remaining = 0;
        }
    }
}
