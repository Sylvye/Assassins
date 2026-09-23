package dev.tigermce.assassins;

import dev.tigermce.assassins.model.*;
import dev.tigermce.assassins.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
import java.time.Duration;
import java.util.*;

public final class EventManager {
    private final AssassinsPlugin plugin;
    private final Persistence persistence;
    private final NamespacedKey compassKey;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    public final DraftConfig draft;
    public final EventState state;
    public final Map<UUID, List<RewardClaim>> claims;

    public EventManager(AssassinsPlugin plugin, Persistence persistence, Persistence.Loaded loaded) {
        this.plugin = plugin; this.persistence = persistence; this.draft = loaded.draft();
        this.state = loaded.state(); this.claims = loaded.claims();
        this.compassKey = new NamespacedKey(plugin, "tracker");
    }

    public boolean start() {
        if (state.active) return false;
        List<UUID> participants = Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("assassins.use")).map(Player::getUniqueId).toList();
        if (participants.size() < 2) return false;
        clearState();
        state.active = true; state.startedAt = System.currentTimeMillis();
        state.rounds = draft.rounds; state.roundDurationMillis = draft.roundDurationMillis; state.bossBar = draft.bossBar;
        state.killCreditMillis = draft.killCreditMillis; state.disconnectGraceMillis = draft.disconnectGraceMillis;
        state.participants.addAll(participants);
        state.assignments.addAll(AssignmentGenerator.generate(participants, state.rounds, new Random()));
        for (PrizeTier tier : PrizeTier.values()) state.prizes.get(tier).addAll(Items.clones(draft.prizes.get(tier)));
        long now = System.currentTimeMillis();
        for (UUID id : participants) state.progress.put(id, new PlayerProgress(0, now + state.roundDurationMillis));
        save();
        Bukkit.broadcast(Items.text("An Assassins event has begun!", NamedTextColor.GOLD));
        for (UUID id : participants) { Player player = Bukkit.getPlayer(id); if (player != null) { announceTarget(player); updateTracker(player); } }
        return true;
    }

    public boolean stop(boolean announce) {
        if (!state.active) return false;
        state.active = false; state.attacks.clear(); removeAllTrackers(); hideAllBars(); save();
        if (announce) Bukkit.broadcast(Items.text("The Assassins event was stopped by an administrator.", NamedTextColor.RED));
        return true;
    }

    public void tick() {
        if (!state.active) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (UUID hunter : List.copyOf(state.participants)) {
            PlayerProgress p = state.progress(hunter);
            if (p == null || p.finished) continue;
            UUID targetId = state.target(hunter);
            Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
            if (target == null || !target.isOnline()) {
                if (p.targetOfflineSince == 0) { p.targetOfflineSince = now; changed = true; }
                long offlineCreditAt = Math.min(p.deadline, p.targetOfflineSince + state.disconnectGraceMillis);
                if (now >= offlineCreditAt) { completeRound(hunter, true, "Your target remained offline; the round was completed."); changed = true; continue; }
            } else {
                if (p.targetOfflineSince != 0) { p.targetOfflineSince = 0; changed = true; }
                if (now >= p.deadline) { completeRound(hunter, false, "Round timed out. Moving to your next target."); changed = true; continue; }
            }
            Player player = Bukkit.getPlayer(hunter);
            if (player != null) { updateTracker(player); updateBossBar(player, now); }
        }
        if (state.attacks.entrySet().removeIf(e -> now - e.getValue().at() > state.killCreditMillis)) changed = true;
        if (changed) save();
    }

    public void recordAttack(UUID victim, UUID attacker) {
        if (!state.active || victim.equals(attacker)) return;
        state.attacks.put(victim, new AttackCredit(attacker, System.currentTimeMillis()));
        save();
    }

    public boolean recordDeath(UUID victim, UUID directKiller) {
        if (!state.active) return false;
        long now = System.currentTimeMillis();
        AttackCredit credit = state.attacks.remove(victim);
        UUID attacker = creditedAttacker(directKiller, credit, now, state.killCreditMillis);
        if (attacker == null || !victim.equals(state.target(attacker))) { save(); return false; }
        completeRound(attacker, true, "Target eliminated! Your task reward is ready.");
        save(); return true;
    }

    private void completeRound(UUID hunter, boolean rewarded, String message) {
        if (!state.active) return;
        PlayerProgress p = state.progress(hunter); if (p == null || p.finished) return;
        int completedRound = p.round + 1;
        if (rewarded) addClaim(hunter, "Round " + completedRound + " Task Reward", state.prizes.get(PrizeTier.TASK));
        p.round++; p.targetOfflineSince = 0; p.deadline = System.currentTimeMillis() + state.roundDurationMillis;
        Player player = Bukkit.getPlayer(hunter);
        if (player != null) {
            player.sendMessage(Items.text(message + " ", rewarded ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                    .append(Items.text("[Open Assassins]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/assassins"))));
            player.playSound(player.getLocation(), rewarded ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 1.1f);
        }
        if (p.round >= state.rounds) finish(hunter); else if (player != null) announceTarget(player);
    }

    private void finish(UUID id) {
        PlayerProgress p = state.progress(id); if (p == null || p.finished) return;
        p.finished = true; state.finishers.add(id); int place = state.finishers.size();
        addClaim(id, "Completion Prize", state.prizes.get(PrizeTier.COMPLETION));
        PrizeTier tier = PrizeTier.forPlace(place);
        if (tier != PrizeTier.COMPLETION) addClaim(id, tier.label() + " Prize", state.prizes.get(tier));
        Player player = Bukkit.getPlayer(id);
        if (player != null) { removeTrackers(player); hideBar(id); }
        OfflinePlayer named = Bukkit.getOfflinePlayer(id);
        Bukkit.broadcast(Items.text("★ " + (named.getName() == null ? id : named.getName()) + (place <= 3 ? " finished in " + ordinal(place) + " place!" : " completed Assassins!"), NamedTextColor.GOLD));
        if (state.finishers.size() == state.participants.size()) {
            state.active = false; removeAllTrackers(); hideAllBars(); Bukkit.broadcast(Items.text("The Assassins event is complete!", NamedTextColor.AQUA));
        }
    }

    public void join(Player player) {
        removeTrackers(player);
        if (!state.active || !state.participant(player.getUniqueId())) return;
        PlayerProgress p = state.progress(player.getUniqueId()); if (p == null || p.finished) return;
        announceTarget(player); updateTracker(player);
    }

    public boolean claim(Player player, UUID claimId) {
        List<RewardClaim> list = claims.get(player.getUniqueId()); if (list == null) return false;
        RewardClaim claim = list.stream().filter(c -> c.id().equals(claimId)).findFirst().orElse(null); if (claim == null) return false;
        for (ItemStack reward : claim.items()) player.getInventory().addItem(reward).values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        list.remove(claim); if (list.isEmpty()) claims.remove(player.getUniqueId()); save();
        player.sendMessage(Items.text(claim.label() + " claimed!", NamedTextColor.GREEN)); player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f); return true;
    }

    private void addClaim(UUID player, String label, List<ItemStack> items) {
        if (items.isEmpty()) return;
        claims.computeIfAbsent(player, ignored -> new ArrayList<>()).add(new RewardClaim(UUID.randomUUID(), label, items));
    }

    public void announceTarget(Player player) {
        UUID target = state.target(player.getUniqueId()); PlayerProgress p = state.progress(player.getUniqueId()); if (target == null || p == null) return;
        String name = Optional.ofNullable(Bukkit.getOfflinePlayer(target).getName()).orElse(target.toString());
        player.sendMessage(Items.text("Round " + (p.round + 1) + "/" + state.rounds + " target: ", NamedTextColor.YELLOW)
                .append(Items.text(name + " ", NamedTextColor.RED)).append(Items.text("[View]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/assassins"))));
    }

    private void updateTracker(Player player) {
        UUID targetId = state.target(player.getUniqueId()); if (targetId == null) { removeTrackers(player); return; }
        Player target = Bukkit.getPlayer(targetId); ItemStack tracker = findTracker(player);
        if (tracker == null) {
            if (player.getInventory().firstEmpty() < 0) return;
            tracker = new ItemStack(Material.COMPASS); player.getInventory().addItem(tracker);
        }
        CompassMeta meta = (CompassMeta) tracker.getItemMeta();
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
        String targetName = Optional.ofNullable(Bukkit.getOfflinePlayer(targetId).getName()).orElse("Unknown");
        meta.displayName(Items.text("Assassin Tracker: " + targetName, NamedTextColor.RED));
        meta.lore(List.of(Items.text(target != null && target.isOnline() ? "Tracking current location" : "Target is offline", target != null && target.isOnline() ? NamedTextColor.AQUA : NamedTextColor.GRAY), Items.text("Round " + (state.progress(player.getUniqueId()).round + 1) + "/" + state.rounds, NamedTextColor.YELLOW)));
        if (target != null && target.isOnline()) { meta.setLodestone(target.getLocation()); meta.setLodestoneTracked(false); }
        else meta.setLodestone(null);
        tracker.setItemMeta(meta);
    }

    private ItemStack findTracker(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) if (isTracker(stack)) return stack;
        return null;
    }
    public boolean isTracker(ItemStack stack) { return stack != null && stack.getType() == Material.COMPASS && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.BYTE); }
    public void removeTrackers(Player player) { for (ItemStack stack : player.getInventory().getContents()) if (isTracker(stack)) stack.setAmount(0); }
    public void removeAllTrackers() {
        Bukkit.getOnlinePlayers().forEach(this::removeTrackers);
        for (World world : Bukkit.getWorlds()) for (Item item : world.getEntitiesByClass(Item.class)) if (isTracker(item.getItemStack())) item.remove();
    }

    private void updateBossBar(Player player, long now) {
        if (!state.bossBar) { hideBar(player.getUniqueId()); return; }
        PlayerProgress p = state.progress(player.getUniqueId()); if (p == null || p.finished) return;
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), ignored -> Bukkit.createBossBar("Assassins", BarColor.RED, BarStyle.SOLID));
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        long remaining = Math.max(0, p.deadline - now); double progress = state.roundDurationMillis == 0 ? 0 : (double) remaining / state.roundDurationMillis;
        bar.setProgress(Math.max(0, Math.min(1, progress)));
        bar.setTitle("Round " + (p.round + 1) + "/" + state.rounds + " • " + formatDuration(remaining));
    }
    private void hideBar(UUID id) { BossBar bar = bars.remove(id); if (bar != null) bar.removeAll(); }
    private void hideAllBars() { bars.values().forEach(BossBar::removeAll); bars.clear(); }
    public void shutdown() { hideAllBars(); save(); }
    public void save() { persistence.save(draft, state, claims); }
    public Map<PrizeTier, List<ItemStack>> visiblePrizes() { return state.active ? state.prizes : draft.prizes; }

    static UUID creditedAttacker(UUID directKiller, AttackCredit credit, long now, long window) {
        if (directKiller != null) return directKiller;
        return credit != null && now - credit.at() <= window ? credit.attacker() : null;
    }
    static long offlineCreditAt(PlayerProgress progress, long grace) {
        return Math.min(progress.deadline, progress.targetOfflineSince + grace);
    }

    private void clearState() {
        state.active = false; state.startedAt = 0; state.participants.clear(); state.assignments.clear(); state.progress.clear(); state.finishers.clear(); state.attacks.clear();
        for (List<ItemStack> prizes : state.prizes.values()) prizes.clear();
    }
    public static String formatDuration(long millis) { Duration d = Duration.ofMillis(Math.max(0, millis)); return "%02d:%02d".formatted(d.toMinutes(), d.toSecondsPart()); }
    private static String ordinal(int place) { return switch (place) { case 1 -> "1st"; case 2 -> "2nd"; case 3 -> "3rd"; default -> place + "th"; }; }
}
