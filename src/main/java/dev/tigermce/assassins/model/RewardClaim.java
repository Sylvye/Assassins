package dev.tigermce.assassins.model;

import org.bukkit.inventory.ItemStack;
import java.util.*;

public record RewardClaim(UUID id, String label, List<ItemStack> items) {
    public RewardClaim {
        items = items.stream().map(ItemStack::clone).toList();
    }
    @Override public List<ItemStack> items() { return items.stream().map(ItemStack::clone).toList(); }
}
