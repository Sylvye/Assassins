package dev.tigermce.assassins;

import dev.tigermce.assassins.model.PrizeTier;
import dev.tigermce.assassins.util.Items;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class AssassinsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private EventManager events;
    private MenuManager menus;

    @Override public void onEnable() {
        Persistence persistence = new Persistence(getDataFolder());
        events = new EventManager(this, persistence, persistence.load()); menus = new MenuManager(this, events);
        Bukkit.getPluginManager().registerEvents(this, this); Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(new TrackerListener(this, events), this);
        PluginCommand command = getCommand("assassins"); if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
        Bukkit.getScheduler().runTaskTimer(this, events::tick, 20L, 20L);
        if (events.state.active) getLogger().info("Resumed an active Assassins event with " + events.state.participants.size() + " participants.");
    }
    @Override public void onDisable() { if (events != null) events.shutdown(); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Assassins is configured in-game by a player with assassins.admin."); return true; }
        if (!player.hasPermission("assassins.use")) { player.sendMessage(Items.text("You do not have permission to use Assassins.", NamedTextColor.RED)); return true; }
        if (args.length == 0) { menus.openPlayer(player); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("rewards")) { menus.openPreview(player, PrizeTier.TASK, 0); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("settings")) {
            if (!player.hasPermission("assassins.admin")) player.sendMessage(Items.text("You do not have permission to configure Assassins.", NamedTextColor.RED)); else menus.openDashboard(player); return true;
        }
        player.sendMessage(Items.text("Usage: /" + label + " [rewards|settings]", NamedTextColor.RED)); return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of(); String prefix = args[0].toLowerCase(Locale.ROOT); List<String> result = new ArrayList<>();
        if ("rewards".startsWith(prefix)) result.add("rewards"); if (sender.hasPermission("assassins.admin") && "settings".startsWith(prefix)) result.add("settings"); return result;
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { Bukkit.getScheduler().runTask(this, () -> events.join(event.getPlayer())); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return; Player attacker = attacker(event.getDamager());
        if (attacker != null) events.recordAttack(victim.getUniqueId(), attacker.getUniqueId());
    }
    @EventHandler(priority = EventPriority.MONITOR) public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer(); Player killer = victim.getKiller(); events.recordDeath(victim.getUniqueId(), killer == null ? null : killer.getUniqueId());
    }
    @EventHandler(ignoreCancelled = true) public void onPickup(EntityPickupItemEvent event) {
        if (!events.state.active && events.isTracker(event.getItem().getItemStack())) { event.setCancelled(true); event.getItem().remove(); }
    }
    private Player attacker(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile) { ProjectileSource source = projectile.getShooter(); if (source instanceof Player player) return player; }
        return null;
    }
}
