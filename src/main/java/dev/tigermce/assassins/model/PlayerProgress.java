package dev.tigermce.assassins.model;

public final class PlayerProgress {
    public int round;
    public long deadline;
    public long targetOfflineSince;
    public boolean finished;

    public PlayerProgress() {}
    public PlayerProgress(int round, long deadline) { this.round = round; this.deadline = deadline; }
}
