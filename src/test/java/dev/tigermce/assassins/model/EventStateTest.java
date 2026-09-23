package dev.tigermce.assassins.model;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EventStateTest {
    @Test void targetTracksPlayersCurrentRound() {
        UUID hunter = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID(); EventState state = new EventState();
        state.assignments.add(Map.of(hunter, first)); state.assignments.add(Map.of(hunter, second)); state.progress.put(hunter, new PlayerProgress(0, 10));
        assertEquals(first, state.target(hunter)); state.progress.get(hunter).round = 1; assertEquals(second, state.target(hunter));
        state.progress.get(hunter).finished = true; assertNull(state.target(hunter));
    }
    @Test void prizeTierMapsOnlyTopThree() {
        assertEquals(PrizeTier.FIRST, PrizeTier.forPlace(1)); assertEquals(PrizeTier.SECOND, PrizeTier.forPlace(2));
        assertEquals(PrizeTier.THIRD, PrizeTier.forPlace(3)); assertEquals(PrizeTier.COMPLETION, PrizeTier.forPlace(4));
    }
}
