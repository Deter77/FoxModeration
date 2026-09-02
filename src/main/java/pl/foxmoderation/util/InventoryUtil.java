package pl.foxmoderation.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class InventoryUtil {
    private InventoryUtil() {
    }

    public static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(ColorUtil.guiColor(name));
        if (material == Material.EMERALD) {
            meta.setCustomModelData(1010);
        }
        if (lore != null) {
            meta.lore(ColorUtil.guiLines(lore));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (material == Material.EMERALD) {
            meta.setCustomModelData(1010);
        }
        meta.lore(lore == null ? null : lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        stack.setItemMeta(meta);
        return stack;
    }
}
