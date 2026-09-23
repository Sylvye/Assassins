package dev.tigermce.assassins;

import dev.tigermce.assassins.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {
    @TempDir Path directory;

    @Test void activeEventAndClaimsSurviveRoundTrip() {
        Persistence store = new Persistence(directory.toFile()); DraftConfig draft = new DraftConfig(); EventState state = new EventState();
        UUID hunter = UUID.randomUUID(), target = UUID.randomUUID();
        draft.rounds = 7; draft.roundDurationMillis = 90_000; draft.bossBar = false;
        state.active = true; state.startedAt = 123; state.rounds = 7; state.roundDurationMillis = 90_000; state.bossBar = false; state.killCreditMillis = 12_000; state.disconnectGraceMillis = 34_000;
        state.participants.addAll(List.of(hunter, target)); state.assignments.add(Map.of(hunter, target, target, hunter));
        PlayerProgress progress = new PlayerProgress(2, 999); progress.targetOfflineSince = 500; state.progress.put(hunter, progress);
        state.finishers.add(target); state.attacks.put(target, new AttackCredit(hunter, 800));
        UUID claimId = UUID.randomUUID(); Map<UUID, List<RewardClaim>> claims = new HashMap<>(); claims.put(hunter, new ArrayList<>(List.of(new RewardClaim(claimId, "Round 2 Task Reward", List.of()))));

        store.save(draft, state, claims); Persistence.Loaded loaded = store.load();

        assertEquals(7, loaded.draft().rounds); assertFalse(loaded.draft().bossBar);
        assertTrue(loaded.state().active); assertEquals(target, loaded.state().assignments.getFirst().get(hunter)); assertEquals(2, loaded.state().progress(hunter).round);
        assertEquals(hunter, loaded.state().attacks.get(target).attacker()); assertEquals(target, loaded.state().finishers.getFirst());
        assertEquals(claimId, loaded.claims().get(hunter).getFirst().id()); assertEquals("Round 2 Task Reward", loaded.claims().get(hunter).getFirst().label());
    }
}
