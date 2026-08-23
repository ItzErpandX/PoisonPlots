package me.itzerpandx.PoisonPlots.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.item.setAmount(1);
        this.meta = this.item.getItemMeta();
    }

    public ItemBuilder setName(String name) {
        if (this.meta != null) {
            Component displayName = miniMessage.deserialize(name);
            // Prevent italic from being inherited
            displayName = displayName.decorationIfAbsent(net.kyori.adventure.text.format.TextDecoration.ITALIC, net.kyori.adventure.text.format.TextDecoration.State.FALSE);
            this.meta.displayName(displayName);
        }
        return this;
    }

    public ItemBuilder addLore(String string) {
        if (this.meta == null) return this;
        List<Component> lore = this.meta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(lore);
        }
        Component component = miniMessage.deserialize(string);
        // Prevent italic from being inherited from lore parent style
        component = component.decorationIfAbsent(net.kyori.adventure.text.format.TextDecoration.ITALIC, net.kyori.adventure.text.format.TextDecoration.State.FALSE);
        lore.add(component);
        this.meta.lore(lore);
        return this;
    }

    public void addEnchantment(Enchantment enchant, int level) {
        if (this.meta != null) {
            this.meta.addEnchant(enchant, level, true);
        }
    }

    public void addFlags(ItemFlag... flags) {
        if (this.meta != null) {
            this.meta.addItemFlags(flags);
        }
    }

    public ItemStack create() {
        this.item.setItemMeta(this.meta);
        return this.item;
    }
}
