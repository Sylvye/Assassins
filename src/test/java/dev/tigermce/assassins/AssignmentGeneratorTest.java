package dev.tigermce.assassins;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AssignmentGeneratorTest {
    @Test void everyRoundIsAOneToOneDerangement() {
        List<UUID> players = players(8);
        var rounds = AssignmentGenerator.generate(players, 20, new Random(12));
        for (var round : rounds) {
            assertEquals(new HashSet<>(players), round.keySet());
            assertEquals(new HashSet<>(players), new HashSet<>(round.values()));
            round.forEach((hunter, target) -> assertNotEquals(hunter, target));
        }
    }

    @Test void noPairRepeatsUntilPoolForcesIt() {
        List<UUID> players = players(6);
        var rounds = AssignmentGenerator.generate(players, 10, new Random(4));
        for (UUID hunter : players) {
            Set<UUID> firstCycle = new HashSet<>();
            for (int round = 0; round < players.size() - 1; round++) assertTrue(firstCycle.add(rounds.get(round).get(hunter)));
            assertEquals(players.size() - 1, firstCycle.size());
            assertEquals(rounds.get(0).get(hunter), rounds.get(players.size() - 1).get(hunter));
        }
    }

    @Test void twoPlayersCanRepeatOnlyPossiblePair() {
        List<UUID> players = players(2); var rounds = AssignmentGenerator.generate(players, 4, new Random(1));
        for (var round : rounds) { assertEquals(players.get(1), round.get(players.get(0))); assertEquals(players.get(0), round.get(players.get(1))); }
    }

    @Test void rejectsTooFewPlayers() {
        assertThrows(IllegalArgumentException.class, () -> AssignmentGenerator.generate(players(1), 1, new Random()));
    }
    private static List<UUID> players(int count) { List<UUID> result = new ArrayList<>(); for (int i = 0; i < count; i++) result.add(new UUID(0, i + 1)); return result; }
}
