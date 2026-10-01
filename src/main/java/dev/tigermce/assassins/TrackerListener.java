package dev.tigermce.assassins;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

final class TrackerListener implements Listener {
    private final AssassinsPlugin plugin;
    private final EventManager events;

    TrackerListener(AssassinsPlugin plugin, EventManager events) {
        this.plugin = plugin;
        this.events = events;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (events.state.active && events.isTracker(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        ItemStack retained = null;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (events.isTracker(stack)) { retained = stack.clone(); break; }
        }
        if (retained == null) {
            for (ItemStack stack : event.getDrops()) {
                if (events.isTracker(stack)) { retained = stack.clone(); break; }
            }
        }
        event.getDrops().removeIf(events::isTracker);
        event.getItemsToKeep().removeIf(events::isTracker);
        if (!events.state.active || events.state.target(player.getUniqueId()) == null) {
            events.removeTrackers(player);
        } else if (!event.getKeepInventory() && retained != null) {
            retained.setAmount(1);
            event.getItemsToKeep().add(retained);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> events.updateTracker(event.getPlayer()));
    }
}
