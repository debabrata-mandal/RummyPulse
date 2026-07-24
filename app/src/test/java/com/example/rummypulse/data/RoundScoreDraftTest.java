package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class RoundScoreDraftTest {

    @Test
    public void normalRoundRequiresAllMissingScoresBeforeApply() {
        GameData source = gameWithScores(-1, -1, -1);
        RoundScoreDraft draft = RoundScoreDraft.start(source, 1, false);

        draft.recordScore(0, 10);
        draft.recordScore(1, 20);
        assertFalse(draft.isComplete());
        assertEquals(-1, source.getPlayers().get(0).getScores().get(0).intValue());

        draft.recordScore(2, 30);
        GameData saved = draft.applyToCopy(source);

        assertTrue(draft.isComplete());
        assertEquals(Arrays.asList(10, 20, 30), firstRound(saved));
        assertEquals(Arrays.asList(-1, -1, -1), firstRound(source));
    }

    @Test
    public void cancelingDraftLeavesSourceUnchanged() {
        GameData source = gameWithScores(-1, -1);
        RoundScoreDraft draft = RoundScoreDraft.start(source, 1, false);

        draft.recordScore(0, 44);

        assertEquals(Arrays.asList(-1, -1), firstRound(source));
        assertFalse(draft.isComplete());
    }

    @Test
    public void reopeningDraftResumesAtFirstUnenteredPlayer() {
        GameData source = gameWithScores(-1, -1, -1);
        RoundScoreDraft draft = RoundScoreDraft.start(source, 1, false);
        draft.recordScore(0, 11);
        draft.recordScore(1, 22);

        RoundScoreDraft restored =
                RoundScoreDraft.deserialize(draft.serialize());

        assertEquals(2, restored.findNextUnreviewed(0));
        assertEquals(11, restored.getScore(0));
        assertEquals(22, restored.getScore(1));
        assertFalse(restored.isCorrectionMode());
        assertEquals(Arrays.asList(-1, -1, -1), firstRound(source));
    }

    @Test
    public void completedDraftCanBeAppliedAgainForRetry() {
        GameData source = gameWithScores(-1, -1);
        RoundScoreDraft draft = RoundScoreDraft.start(source, 1, false);
        draft.recordScore(0, 7);
        draft.recordScore(1, 9);

        GameData firstAttempt = draft.applyToCopy(source);
        GameData retryAttempt = draft.applyToCopy(source);

        assertEquals(firstRound(firstAttempt), firstRound(retryAttempt));
        assertEquals(Arrays.asList(-1, -1), firstRound(source));
    }

    @Test
    public void correctionRequiresEveryExistingScoreToBeReviewed() {
        GameData source = gameWithScores(4, 5);
        RoundScoreDraft draft = RoundScoreDraft.start(source, 1, true);

        assertEquals(0, draft.findNextUnreviewed(0));
        draft.recordScore(0, 14);
        assertFalse(draft.isComplete());
        assertEquals(1, draft.findNextUnreviewed(0));

        draft.recordScore(1, 15);
        assertEquals(Arrays.asList(14, 15), firstRound(draft.applyToCopy(source)));
    }

    @Test
    public void normalPartiallyPersistedRoundSkipsExistingPlayers() {
        GameData source = gameWithScores(3, -1, -1);
        RoundScoreDraft draft = RoundScoreDraft.start(source, 1, false);

        assertEquals(1, draft.findNextUnreviewed(0));
    }

    private static GameData gameWithScores(int... scores) {
        ArrayList<Player> players = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            Player player = new Player();
            player.setName("Player " + (i + 1));
            ArrayList<Integer> rounds = new ArrayList<>();
            rounds.add(scores[i]);
            player.setScores(rounds);
            players.add(player);
        }
        GameData gameData = new GameData();
        gameData.setPlayers(players);
        gameData.setNumPlayers(players.size());
        gameData.setVersion("1.0");
        return gameData;
    }

    private static java.util.List<Integer> firstRound(GameData gameData) {
        ArrayList<Integer> scores = new ArrayList<>();
        for (Player player : gameData.getPlayers()) {
            scores.add(player.getScores().get(0));
        }
        return scores;
    }
}
