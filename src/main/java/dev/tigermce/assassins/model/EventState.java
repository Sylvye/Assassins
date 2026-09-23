package dev.tigermce.assassins.model;

import org.bukkit.inventory.ItemStack;
import java.util.*;

public final class EventState {
    public boolean active;
    public long startedAt;
    public int rounds;
    public long roundDurationMillis;
    public boolean bossBar;
    public long killCreditMillis;
    public long disconnectGraceMillis;
    public final List<UUID> participants = new ArrayList<>();
    public final List<Map<UUID, UUID>> assignments = new ArrayList<>();
    public final Map<UUID, PlayerProgress> progress = new HashMap<>();
    public final List<UUID> finishers = new ArrayList<>();
    public final EnumMap<PrizeTier, List<ItemStack>> prizes = new EnumMap<>(PrizeTier.class);
    public final Map<UUID, AttackCredit> attacks = new HashMap<>();
    public EventState() { for (PrizeTier tier : PrizeTier.values()) prizes.put(tier, new ArrayList<>()); }
    public boolean participant(UUID id) { return participants.contains(id); }
    public PlayerProgress progress(UUID id) { return progress.get(id); }
    public UUID target(UUID hunter) {
        PlayerProgress p = progress.get(hunter);
        return p == null || p.finished || p.round >= assignments.size() ? null : assignments.get(p.round).get(hunter);
    }
}
