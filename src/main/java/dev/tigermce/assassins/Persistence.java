package dev.tigermce.assassins;

import dev.tigermce.assassins.model.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class Persistence {
    private final File folder;
    public Persistence(File folder) { this.folder = folder; }
    public record Loaded(DraftConfig draft, EventState state, Map<UUID, List<RewardClaim>> claims) {}

    public Loaded load() {
        DraftConfig draft = new DraftConfig();
        EventState state = new EventState();
        Map<UUID, List<RewardClaim>> claims = new HashMap<>();
        File file = new File(folder, "data.yml");
        if (!file.exists()) return new Loaded(draft, state, claims);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        draft.rounds = y.getInt("draft.rounds", draft.rounds) > 0 ? y.getInt("draft.rounds", draft.rounds) : draft.rounds;
        draft.roundDurationMillis = positive(y.getLong("draft.round-duration-ms", draft.roundDurationMillis), draft.roundDurationMillis);
        draft.bossBar = y.getBoolean("draft.bossbar", draft.bossBar);
        draft.killCreditMillis = positive(y.getLong("draft.kill-credit-ms", draft.killCreditMillis), draft.killCreditMillis);
        draft.disconnectGraceMillis = positive(y.getLong("draft.disconnect-grace-ms", draft.disconnectGraceMillis), draft.disconnectGraceMillis);
        readPrizeMap(y, "draft.prizes", draft.prizes);

        state.active = y.getBoolean("event.active");
        state.startedAt = y.getLong("event.started-at");
        state.rounds = y.getInt("event.rounds");
        state.roundDurationMillis = y.getLong("event.round-duration-ms");
        state.bossBar = y.getBoolean("event.bossbar");
        state.killCreditMillis = y.getLong("event.kill-credit-ms");
        state.disconnectGraceMillis = y.getLong("event.disconnect-grace-ms");
        for (String raw : y.getStringList("event.participants")) addUuid(state.participants, raw);
        for (String raw : y.getStringList("event.finishers")) addUuid(state.finishers, raw);
        readPrizeMap(y, "event.prizes", state.prizes);
        ConfigurationSection rounds = y.getConfigurationSection("event.assignments");
        if (rounds != null) rounds.getKeys(false).stream().sorted(Comparator.comparingInt(Integer::parseInt)).forEach(key -> {
            Map<UUID, UUID> map = new LinkedHashMap<>();
            ConfigurationSection section = rounds.getConfigurationSection(key);
            if (section != null) for (String hunterRaw : section.getKeys(false)) {
                UUID hunter = uuid(hunterRaw), target = uuid(section.getString(hunterRaw));
                if (hunter != null && target != null) map.put(hunter, target);
            }
            state.assignments.add(map);
        });
        ConfigurationSection progress = y.getConfigurationSection("event.progress");
        if (progress != null) for (String raw : progress.getKeys(false)) {
            UUID id = uuid(raw); if (id == null) continue;
            PlayerProgress p = new PlayerProgress();
            p.round = progress.getInt(raw + ".round");
            p.deadline = progress.getLong(raw + ".deadline");
            p.targetOfflineSince = progress.getLong(raw + ".target-offline-since");
            p.finished = progress.getBoolean(raw + ".finished");
            state.progress.put(id, p);
        }
        ConfigurationSection attacks = y.getConfigurationSection("event.attacks");
        if (attacks != null) for (String raw : attacks.getKeys(false)) {
            UUID victim = uuid(raw), attacker = uuid(attacks.getString(raw + ".attacker"));
            if (victim != null && attacker != null) state.attacks.put(victim, new AttackCredit(attacker, attacks.getLong(raw + ".at")));
        }
        ConfigurationSection claimRoot = y.getConfigurationSection("claims");
        if (claimRoot != null) for (String playerRaw : claimRoot.getKeys(false)) {
            UUID player = uuid(playerRaw); if (player == null) continue;
            List<RewardClaim> list = new ArrayList<>();
            ConfigurationSection entries = claimRoot.getConfigurationSection(playerRaw);
            if (entries != null) entries.getKeys(false).stream().sorted(Comparator.comparingInt(Integer::parseInt)).forEach(key -> {
                UUID id = uuid(entries.getString(key + ".id"));
                if (id != null) list.add(new RewardClaim(id, entries.getString(key + ".label", "Reward"), readStacks(y, "claims." + playerRaw + "." + key + ".items")));
            });
            if (!list.isEmpty()) claims.put(player, list);
        }
        return new Loaded(draft, state, claims);
    }

    public void save(DraftConfig draft, EventState state, Map<UUID, List<RewardClaim>> claims) {
        folder.mkdirs();
        YamlConfiguration y = new YamlConfiguration();
        y.set("draft.rounds", draft.rounds);
        y.set("draft.round-duration-ms", draft.roundDurationMillis);
        y.set("draft.bossbar", draft.bossBar);
        y.set("draft.kill-credit-ms", draft.killCreditMillis);
        y.set("draft.disconnect-grace-ms", draft.disconnectGraceMillis);
        writePrizeMap(y, "draft.prizes", draft.prizes);
        y.set("event.active", state.active);
        y.set("event.started-at", state.startedAt);
        y.set("event.rounds", state.rounds);
        y.set("event.round-duration-ms", state.roundDurationMillis);
        y.set("event.bossbar", state.bossBar);
        y.set("event.kill-credit-ms", state.killCreditMillis);
        y.set("event.disconnect-grace-ms", state.disconnectGraceMillis);
        y.set("event.participants", state.participants.stream().map(UUID::toString).toList());
        y.set("event.finishers", state.finishers.stream().map(UUID::toString).toList());
        writePrizeMap(y, "event.prizes", state.prizes);
        for (int i = 0; i < state.assignments.size(); i++) for (var e : state.assignments.get(i).entrySet())
            y.set("event.assignments." + i + "." + e.getKey(), e.getValue().toString());
        for (var e : state.progress.entrySet()) {
            String path = "event.progress." + e.getKey(); PlayerProgress p = e.getValue();
            y.set(path + ".round", p.round); y.set(path + ".deadline", p.deadline);
            y.set(path + ".target-offline-since", p.targetOfflineSince); y.set(path + ".finished", p.finished);
        }
        for (var e : state.attacks.entrySet()) {
            String path = "event.attacks." + e.getKey();
            y.set(path + ".attacker", e.getValue().attacker().toString()); y.set(path + ".at", e.getValue().at());
        }
        for (var e : claims.entrySet()) for (int i = 0; i < e.getValue().size(); i++) {
            RewardClaim claim = e.getValue().get(i); String path = "claims." + e.getKey() + "." + i;
            y.set(path + ".id", claim.id().toString()); y.set(path + ".label", claim.label()); y.set(path + ".items", claim.items());
        }
        Path target = new File(folder, "data.yml").toPath(), temp = new File(folder, "data.yml.tmp").toPath();
        try {
            y.save(temp.toFile());
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { throw new IllegalStateException("Could not save Assassins data", e); }
    }

    private static void readPrizeMap(YamlConfiguration y, String path, EnumMap<PrizeTier, List<ItemStack>> map) {
        for (PrizeTier tier : PrizeTier.values()) map.get(tier).addAll(readStacks(y, path + "." + tier.name().toLowerCase(Locale.ROOT)));
    }
    private static void writePrizeMap(YamlConfiguration y, String path, EnumMap<PrizeTier, List<ItemStack>> map) {
        for (PrizeTier tier : PrizeTier.values()) y.set(path + "." + tier.name().toLowerCase(Locale.ROOT), map.get(tier).stream().map(ItemStack::clone).toList());
    }
    private static List<ItemStack> readStacks(YamlConfiguration y, String path) {
        List<ItemStack> result = new ArrayList<>();
        for (Object value : y.getList(path, List.of())) if (value instanceof ItemStack stack) result.add(stack.clone());
        return result;
    }
    private static long positive(long value, long fallback) { return value > 0 ? value : fallback; }
    private static UUID uuid(String raw) { try { return raw == null ? null : UUID.fromString(raw); } catch (IllegalArgumentException e) { return null; } }
    private static void addUuid(List<UUID> list, String raw) { UUID id = uuid(raw); if (id != null) list.add(id); }
}
