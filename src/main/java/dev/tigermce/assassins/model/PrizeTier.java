package dev.tigermce.assassins.model;

public enum PrizeTier {
    TASK("Task Reward"), FIRST("1st Place"), SECOND("2nd Place"), THIRD("3rd Place"), COMPLETION("Completion");
    private final String label;
    PrizeTier(String label) { this.label = label; }
    public String label() { return label; }
    public static PrizeTier forPlace(int place) {
        return switch (place) { case 1 -> FIRST; case 2 -> SECOND; case 3 -> THIRD; default -> COMPLETION; };
    }
}
