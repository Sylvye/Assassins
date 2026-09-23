package dev.tigermce.assassins;

import dev.tigermce.assassins.model.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EventRulesTest {
    @Test void directKillerTakesPriority() {
        UUID direct = UUID.randomUUID(), tagged = UUID.randomUUID();
        assertEquals(direct, EventManager.creditedAttacker(direct, new AttackCredit(tagged, 1_000), 2_000, 60_000));
    }
    @Test void recentTaggedAttackReceivesCredit() {
        UUID tagged = UUID.randomUUID();
        assertEquals(tagged, EventManager.creditedAttacker(null, new AttackCredit(tagged, 1_000), 61_000, 60_000));
    }
    @Test void expiredOrMissingTagsReceiveNoCredit() {
        assertNull(EventManager.creditedAttacker(null, new AttackCredit(UUID.randomUUID(), 1_000), 61_001, 60_000));
        assertNull(EventManager.creditedAttacker(null, null, 2_000, 60_000));
    }
    @Test void offlineCompletionUsesEarlierOfGraceAndDeadline() {
        PlayerProgress p = new PlayerProgress(); p.targetOfflineSince = 10_000; p.deadline = 100_000;
        assertEquals(40_000, EventManager.offlineCreditAt(p, 30_000));
        p.deadline = 20_000; assertEquals(20_000, EventManager.offlineCreditAt(p, 30_000));
    }
}
