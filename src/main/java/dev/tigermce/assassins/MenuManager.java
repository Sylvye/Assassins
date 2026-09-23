package dev.tigermce.assassins;

import dev.tigermce.assassins.model.*;
import dev.tigermce.assassins.util.Items;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import java.util.*;

public final class MenuManager implements Listener {
    private static final int PAGE_SIZE = 45;
    private final AssassinsPlugin plugin;
    private final EventManager events;
    private final Map<UUID, InputRequest> requests = new HashMap<>();

    public MenuManager(AssassinsPlugin plugin, EventManager events) { this.plugin = plugin; this.events = events; }
    private sealed interface Menu extends InventoryHolder permits Dashboard, Settings, Rewards, Preview, PlayerView, AdminProgress, Confirm { @Override default Inventory getInventory() { return null; } }
    private record Dashboard() implements Menu {}
    private record Settings() implements Menu {}
    private record Rewards(PrizeTier tier, int page) implements Menu {}
    private record Preview(PrizeTier tier, int page) implements Menu {}
    private record PlayerView(List<UUID> claimIds) implements Menu {}
    private record AdminProgress(int page) implements Menu {}
    private record Confirm(boolean start) implements Menu {}
    private record InputRequest(Field field) {}
    private enum Field { ROUNDS, DURATION, CREDIT, GRACE }

    public void openDashboard(Player player) {
        Inventory inv = Bukkit.createInventory(new Dashboard(), 27, title("Assassins • Admin")); fill(inv);
        inv.setItem(10, Items.button(Material.COMPARATOR, "Event Settings", NamedTextColor.LIGHT_PURPLE,
                events.draft.rounds + " rounds", "Round timer: " + EventManager.formatDuration(events.draft.roundDurationMillis), "Bossbar: " + onOff(events.draft.bossBar)));
        int count = events.draft.prizes.values().stream().mapToInt(List::size).sum();
        inv.setItem(11, Items.button(Material.CHEST, "Edit Prizes", NamedTextColor.GOLD, count + " prize stacks", "Task, placement, and completion rewards"));
        inv.setItem(14, Items.button(Material.PLAYER_HEAD, "Participant Progress", NamedTextColor.GREEN,
                events.state.active ? events.state.participants.size() + " participants" : "No active event"));
        inv.setItem(16, events.state.active
                ? Items.button(Material.BARRIER, "Stop Event", NamedTextColor.RED, "Requires confirmation")
                : Items.button(Material.LIME_DYE, "Start Event", NamedTextColor.GREEN, "Snapshots eligible online players", "Requires at least 2 players", "Requires confirmation"));
        player.openInventory(inv);
    }

    public void openSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new Settings(), 45, title("Assassins • Settings")); fill(inv);
        inv.setItem(10, Items.button(Material.REPEATER, "Rounds: " + events.draft.rounds, NamedTextColor.AQUA, "Click and enter a whole number"));
        inv.setItem(12, Items.button(Material.CLOCK, "Round Duration", NamedTextColor.YELLOW, EventManager.formatDuration(events.draft.roundDurationMillis), "Click and enter seconds"));
        inv.setItem(14, Items.button(Material.REDSTONE_TORCH, "Kill Credit Window", NamedTextColor.RED, EventManager.formatDuration(events.draft.killCreditMillis), "Click and enter seconds"));
        inv.setItem(16, Items.button(Material.OAK_DOOR, "Disconnect Grace", NamedTextColor.GREEN, EventManager.formatDuration(events.draft.disconnectGraceMillis), "Click and enter seconds"));
        inv.setItem(22, Items.button(events.draft.bossBar ? Material.LIME_DYE : Material.GRAY_DYE, "Bossbar: " + onOff(events.draft.bossBar), NamedTextColor.LIGHT_PURPLE, "Click to toggle"));
        inv.setItem(40, Items.button(Material.ARROW, "Back", NamedTextColor.YELLOW)); player.openInventory(inv);
    }

    public void openRewards(Player player, PrizeTier tier, int requestedPage) {
        List<ItemStack> prizes = events.draft.prizes.get(tier); int page = boundedPage(requestedPage, prizes.size());
        Inventory inv = Bukkit.createInventory(new Rewards(tier, page), 54, title(tier.label() + " Prizes • " + (page + 1)));
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = page * PAGE_SIZE + slot; if (index >= prizes.size()) break;
            ItemStack stack = prizes.get(index).clone(); ItemMeta meta = stack.getItemMeta();
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore()); lore.add(Items.text("Click to remove", NamedTextColor.RED)); meta.lore(lore); stack.setItemMeta(meta); inv.setItem(slot, stack);
        }
        rewardControls(inv, tier, page, prizes.size(), true); player.openInventory(inv);
    }

    public void openPreview(Player player, PrizeTier tier, int requestedPage) {
        List<ItemStack> prizes = events.visiblePrizes().get(tier); int page = boundedPage(requestedPage, prizes.size());
        Inventory inv = Bukkit.createInventory(new Preview(tier, page), 54, title(tier.label() + " Prizes • " + (page + 1)));
        for (int slot = 0; slot < PAGE_SIZE; slot++) { int index = page * PAGE_SIZE + slot; if (index < prizes.size()) inv.setItem(slot, prizes.get(index).clone()); }
        rewardControls(inv, tier, page, prizes.size(), false); player.openInventory(inv);
    }

    private void rewardControls(Inventory inv, PrizeTier selected, int page, int count, boolean editing) {
        for (int i = 45; i < 54; i++) inv.setItem(i, Items.button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY));
        PrizeTier[] tiers = PrizeTier.values(); Material[] icons = {Material.DIAMOND_SWORD, Material.GOLD_INGOT, Material.IRON_INGOT, Material.COPPER_INGOT, Material.EMERALD};
        for (int i = 0; i < tiers.length; i++) inv.setItem(45 + i, Items.button(icons[i], tiers[i].label(), tiers[i] == selected ? NamedTextColor.AQUA : NamedTextColor.GRAY));
        if (editing) inv.setItem(50, Items.button(Material.HOPPER, "Add Reward Items", NamedTextColor.GREEN, "Click an item in your inventory"));
        if (page > 0) inv.setItem(51, Items.button(Material.ARROW, "Previous Page", NamedTextColor.YELLOW));
        if ((page + 1) * PAGE_SIZE < count) inv.setItem(52, Items.button(Material.ARROW, "Next Page", NamedTextColor.YELLOW));
        if (editing) inv.setItem(53, Items.button(Material.BARRIER, "Back", NamedTextColor.YELLOW));
    }

    public void openPlayer(Player player) {
        Inventory inv = Bukkit.createInventory(new PlayerView(claimIds(player)), 54, title("Assassins")); fill(inv);
        PlayerProgress p = events.state.progress(player.getUniqueId());
        if (!events.state.active && p == null) inv.setItem(13, Items.button(Material.GRAY_DYE, "No Active Event", NamedTextColor.GRAY, "Use /assassins rewards to view prizes"));
        else if (p == null) inv.setItem(13, Items.button(Material.SPYGLASS, "Spectating", NamedTextColor.GRAY, "This event started before you joined"));
        else if (p.finished) inv.setItem(13, Items.button(Material.NETHER_STAR, "Event Complete", NamedTextColor.GOLD, "Place: " + place(player.getUniqueId())));
        else {
            UUID targetId = events.state.target(player.getUniqueId()); OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
            inv.setItem(13, playerHead(target, "Target: " + Optional.ofNullable(target.getName()).orElse("Unknown"), NamedTextColor.RED,
                    "Round: " + (p.round + 1) + "/" + events.state.rounds, "Status: " + (target.isOnline() ? "Online" : "Offline"), "Remaining: " + EventManager.formatDuration(p.deadline - System.currentTimeMillis())));
            for (int i = 0; i < Math.min(events.state.rounds, 9); i++) inv.setItem(27 + i, Items.button(i < p.round ? Material.LIME_STAINED_GLASS_PANE : i == p.round ? Material.YELLOW_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE,
                    "Round " + (i + 1), i < p.round ? NamedTextColor.GREEN : i == p.round ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY, i < p.round ? "Completed" : i == p.round ? "Current target" : "Locked"));
        }
        List<RewardClaim> pending = events.claims.getOrDefault(player.getUniqueId(), List.of());
        for (int i = 0; i < Math.min(9, pending.size()); i++) inv.setItem(45 + i, Items.button(Material.CHEST_MINECART, pending.get(i).label(), NamedTextColor.GOLD, "Click to claim", pending.get(i).items().size() + " item stacks"));
        player.openInventory(inv);
    }

    public void openAdminProgress(Player player, int requestedPage) {
        int page = boundedPage(requestedPage, events.state.participants.size()); Inventory inv = Bukkit.createInventory(new AdminProgress(page), 54, title("Participant Progress • " + (page + 1)));
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = page * PAGE_SIZE + slot; if (index >= events.state.participants.size()) break;
            UUID id = events.state.participants.get(index); OfflinePlayer subject = Bukkit.getOfflinePlayer(id); PlayerProgress p = events.state.progress(id);
            UUID targetId = events.state.target(id); String target = targetId == null ? "—" : Optional.ofNullable(Bukkit.getOfflinePlayer(targetId).getName()).orElse("Unknown");
            inv.setItem(slot, playerHead(subject, Optional.ofNullable(subject.getName()).orElse(id.toString()), p != null && p.finished ? NamedTextColor.GREEN : NamedTextColor.YELLOW,
                    p == null ? "No progress" : "Round: " + Math.min(p.round + 1, events.state.rounds) + "/" + events.state.rounds, "Target: " + target, "Status: " + (p != null && p.finished ? "Finished" : subject.isOnline() ? "Online" : "Offline")));
        }
        controls(inv, page, events.state.participants.size()); inv.setItem(53, Items.button(Material.BARRIER, "Back", NamedTextColor.YELLOW)); player.openInventory(inv);
    }

    private void openConfirm(Player player, boolean start) {
        int size = start ? 54 : 27;
        Inventory inv = Bukkit.createInventory(new Confirm(start), size, title("Confirm • " + (start ? "START" : "STOP"))); fill(inv);
        int confirmSlot = start ? 47 : 11, cancelSlot = start ? 51 : 15;
        inv.setItem(confirmSlot, Items.button(Material.LIME_CONCRETE, "Confirm", NamedTextColor.GREEN)); inv.setItem(cancelSlot, Items.button(Material.RED_CONCRETE, "Cancel", NamedTextColor.RED));
        if (start) {
            List<? extends Player> eligible = Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("assassins.use")).toList();
            for (int i = 0; i < Math.min(27, eligible.size()); i++) inv.setItem(9 + i, playerHead(eligible.get(i), eligible.get(i).getName(), NamedTextColor.AQUA, "Eligible participant"));
            inv.setItem(4, Items.button(Material.MAP, "Event Preview", NamedTextColor.GOLD, eligible.size() + " eligible players", events.draft.rounds + " rounds", "Round timer: " + EventManager.formatDuration(events.draft.roundDurationMillis), eligible.size() > 27 ? "Showing the first 27" : "Review before starting"));
        }
        player.openInventory(inv);
    }

    @EventHandler public void onChat(AsyncChatEvent event) {
        InputRequest request = requests.remove(event.getPlayer().getUniqueId()); if (request == null) return;
        event.setCancelled(true); String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> applyInput(event.getPlayer(), request.field, input));
    }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        event.setCancelled(true); int raw = event.getRawSlot();
        if (menu instanceof Dashboard) dashboardClick(player, raw);
        else if (menu instanceof Settings) settingsClick(player, raw);
        else if (menu instanceof Rewards rewards) rewardsClick(player, rewards, raw, event);
        else if (menu instanceof Preview preview) previewClick(player, preview, raw);
        else if (menu instanceof PlayerView view) playerClick(player, view, raw);
        else if (menu instanceof AdminProgress progress) progressClick(player, progress, raw);
        else if (menu instanceof Confirm confirm) confirmClick(player, confirm, raw);
    }
    @EventHandler public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder(false) instanceof Menu) event.setCancelled(true); }

    private void dashboardClick(Player player, int slot) {
        if (slot == 10) openSettings(player); else if (slot == 11) openRewards(player, PrizeTier.TASK, 0); else if (slot == 14) openAdminProgress(player, 0); else if (slot == 16) openConfirm(player, !events.state.active);
    }
    private void settingsClick(Player player, int slot) {
        if (slot == 22) { events.draft.bossBar = !events.draft.bossBar; events.save(); openSettings(player); tick(player); return; }
        if (slot == 40) { openDashboard(player); return; }
        Field field = switch (slot) { case 10 -> Field.ROUNDS; case 12 -> Field.DURATION; case 14 -> Field.CREDIT; case 16 -> Field.GRACE; default -> null; };
        if (field != null) { requests.put(player.getUniqueId(), new InputRequest(field)); player.closeInventory(); player.sendMessage(Items.text("Enter a positive whole number " + (field == Field.ROUNDS ? "of rounds" : "of seconds") + ", or 'cancel'.", NamedTextColor.YELLOW)); }
    }
    private void rewardsClick(Player player, Rewards menu, int raw, InventoryClickEvent event) {
        List<ItemStack> prizes = events.draft.prizes.get(menu.tier);
        if (raw >= 0 && raw < PAGE_SIZE) { int index = menu.page * PAGE_SIZE + raw; if (index < prizes.size()) { prizes.remove(index); events.save(); openRewards(player, menu.tier, menu.page); tick(player); } return; }
        if (raw >= event.getView().getTopInventory().getSize()) { ItemStack clicked = event.getCurrentItem(); if (clicked != null && !clicked.getType().isAir()) { prizes.add(clicked.clone()); events.save(); openRewards(player, menu.tier, (prizes.size() - 1) / PAGE_SIZE); tick(player); } return; }
        if (raw >= 45 && raw <= 49) openRewards(player, PrizeTier.values()[raw - 45], 0); else if (raw == 51) openRewards(player, menu.tier, menu.page - 1); else if (raw == 52) openRewards(player, menu.tier, menu.page + 1); else if (raw == 53) openDashboard(player);
    }
    private void previewClick(Player player, Preview menu, int raw) { if (raw >= 45 && raw <= 49) openPreview(player, PrizeTier.values()[raw - 45], 0); else if (raw == 51) openPreview(player, menu.tier, menu.page - 1); else if (raw == 52) openPreview(player, menu.tier, menu.page + 1); }
    private void playerClick(Player player, PlayerView menu, int raw) { if (raw >= 45 && raw < 45 + menu.claimIds.size() && events.claim(player, menu.claimIds.get(raw - 45))) openPlayer(player); }
    private void progressClick(Player player, AdminProgress menu, int raw) { if (raw == 45) openAdminProgress(player, menu.page - 1); else if (raw == 52) openAdminProgress(player, menu.page + 1); else if (raw == 53) openDashboard(player); }
    private void confirmClick(Player player, Confirm menu, int slot) {
        int confirmSlot = menu.start ? 47 : 11, cancelSlot = menu.start ? 51 : 15;
        if (slot == cancelSlot) { openDashboard(player); return; } if (slot != confirmSlot) return;
        boolean success = menu.start ? events.start() : events.stop(true);
        if (!success) player.sendMessage(Items.text(menu.start ? "An event is active or fewer than two eligible players are online." : "No event is active.", NamedTextColor.RED)); openDashboard(player);
    }
    private void applyInput(Player player, Field field, String input) {
        if (input.equalsIgnoreCase("cancel")) { openSettings(player); return; }
        try {
            long value = Long.parseLong(input); if (value < 1 || value > 1_000_000) throw new NumberFormatException();
            if (field == Field.ROUNDS) events.draft.rounds = Math.toIntExact(value); else if (field == Field.DURATION) events.draft.roundDurationMillis = Math.multiplyExact(value, 1000); else if (field == Field.CREDIT) events.draft.killCreditMillis = Math.multiplyExact(value, 1000); else events.draft.disconnectGraceMillis = Math.multiplyExact(value, 1000);
            events.save(); openSettings(player); tick(player);
        } catch (ArithmeticException | NumberFormatException e) { player.sendMessage(Items.text("Enter a whole number from 1 to 1,000,000.", NamedTextColor.RED)); requests.put(player.getUniqueId(), new InputRequest(field)); }
    }

    private List<UUID> claimIds(Player player) { return events.claims.getOrDefault(player.getUniqueId(), List.of()).stream().limit(9).map(RewardClaim::id).toList(); }
    private ItemStack playerHead(OfflinePlayer owner, String name, NamedTextColor color, String... lore) { ItemStack stack = Items.button(Material.PLAYER_HEAD, name, color, lore); SkullMeta meta = (SkullMeta) stack.getItemMeta(); meta.setOwningPlayer(owner); stack.setItemMeta(meta); return stack; }
    private int place(UUID id) { int index = events.state.finishers.indexOf(id); return index < 0 ? 0 : index + 1; }
    private void controls(Inventory inv, int page, int count) { for (int i = 45; i < 54; i++) inv.setItem(i, Items.button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY)); if (page > 0) inv.setItem(45, Items.button(Material.ARROW, "Previous Page", NamedTextColor.YELLOW)); if ((page + 1) * PAGE_SIZE < count) inv.setItem(52, Items.button(Material.ARROW, "Next Page", NamedTextColor.YELLOW)); }
    private void fill(Inventory inv) { for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, Items.button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY)); }
    private Component title(String value) { return Items.text(value, NamedTextColor.DARK_AQUA); }
    private int boundedPage(int page, int count) { return Math.max(0, Math.min(page, Math.max(0, (count - 1) / PAGE_SIZE))); }
    private String onOff(boolean value) { return value ? "ON" : "OFF"; }
    private void tick(Player player) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f); }
}
