package dev.tigermce.assassins;

import java.util.*;

public final class AssignmentGenerator {
    private AssignmentGenerator() {}

    public static List<Map<UUID, UUID>> generate(List<UUID> players, int rounds, Random random) {
        if (players.size() < 2) throw new IllegalArgumentException("At least two players are required");
        List<UUID> hunters = new ArrayList<>(players);
        Collections.shuffle(hunters, random);
        List<Map<UUID, UUID>> result = new ArrayList<>();
        int cycle = hunters.size() - 1;
        int offsetStart = random.nextInt(cycle);
        for (int round = 0; round < rounds; round++) {
            int shift = 1 + ((offsetStart + round) % cycle);
            Map<UUID, UUID> assignment = new LinkedHashMap<>();
            for (int i = 0; i < hunters.size(); i++) assignment.put(hunters.get(i), hunters.get((i + shift) % hunters.size()));
            result.add(assignment);
        }
        return result;
    }
}
