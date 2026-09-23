package dev.tigermce.assassins.model;

import org.bukkit.inventory.ItemStack;
import java.util.*;

public final class DraftConfig {
    public int rounds = 5;
    public long roundDurationMillis = 15 * 60_000L;
    public boolean bossBar = true;
    public long killCreditMillis = 60_000L;
    public long disconnectGraceMillis = 5 * 60_000L;
    public final EnumMap<PrizeTier, List<ItemStack>> prizes = new EnumMap<>(PrizeTier.class);
    public DraftConfig() { for (PrizeTier tier : PrizeTier.values()) prizes.put(tier, new ArrayList<>()); }
}
