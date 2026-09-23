package dev.tigermce.assassins.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public final class Items {
    private Items() {}
    public static Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }
    public static ItemStack button(Material material, String name, NamedTextColor color, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(text(name, color));
        meta.lore(Arrays.stream(lore).map(line -> text(line, NamedTextColor.GRAY)).toList());
        stack.setItemMeta(meta);
        return stack;
    }
    public static List<ItemStack> clones(Collection<ItemStack> stacks) { return stacks.stream().map(ItemStack::clone).toList(); }
}
