package com.example.rummypulse.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable result of validating the ten-round score matrix. */
public final class GameIntegrityResult {
    private final boolean complete;
    private final int firstMissingRound;
    private final List<String> affectedPlayerIds;
    private final List<String> affectedPlayerNames;
    private final List<Integer> missingRounds;
    private final boolean laterRoundConflict;

    GameIntegrityResult(boolean complete, int firstMissingRound,
            List<String> affectedPlayerIds, List<String> affectedPlayerNames,
            List<Integer> missingRounds, boolean laterRoundConflict) {
        this.complete = complete;
        this.firstMissingRound = firstMissingRound;
        this.affectedPlayerIds = immutableCopy(affectedPlayerIds);
        this.affectedPlayerNames = immutableCopy(affectedPlayerNames);
        this.missingRounds = Collections.unmodifiableList(new ArrayList<>(missingRounds));
        this.laterRoundConflict = laterRoundConflict;
    }

    public boolean isComplete() { return complete; }
    public int getFirstMissingRound() { return firstMissingRound; }
    public List<String> getAffectedPlayerIds() { return affectedPlayerIds; }
    public List<String> getAffectedPlayerNames() { return affectedPlayerNames; }
    public List<Integer> getMissingRounds() { return missingRounds; }
    public boolean hasLaterRoundConflict() { return laterRoundConflict; }

    public String describe() {
        if (complete) return "All ten rounds are complete.";
        return "Round " + firstMissingRound + " is missing scores for "
                + String.join(", ", affectedPlayerNames) + ".";
    }

    private static List<String> immutableCopy(List<String> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
