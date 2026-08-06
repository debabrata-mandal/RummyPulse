package com.example.rummypulse.ui.join;

/** Immutable counts of notable round outcomes for one player in one game. */
public final class PlayerRoundStatistics {
    private final int madeGameCount;
    private final int packedCount;
    private final int fullHandCount;

    public PlayerRoundStatistics(int madeGameCount, int packedCount, int fullHandCount) {
        this.madeGameCount = madeGameCount;
        this.packedCount = packedCount;
        this.fullHandCount = fullHandCount;
    }

    public int getMadeGameCount() {
        return madeGameCount;
    }

    public int getPackedCount() {
        return packedCount;
    }

    public int getFullHandCount() {
        return fullHandCount;
    }
}
