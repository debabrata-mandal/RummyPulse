package com.example.rummypulse.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MidGameJoinScoreCalculatorTest {
    @Test
    public void completedRoundTwo_usesHighestCumulativeTotalPlusOffset() {
        GameData game = gameWithRoundTwoTotalsOf400And600();

        List<Integer> scores = MidGameJoinScoreCalculator.buildScores(game, 3, 2);

        assertEquals(Integer.valueOf(1), scores.get(0));
        assertEquals(Integer.valueOf(601), scores.get(1));
        assertEquals(602, positiveTotal(scores));
    }

    @Test
    public void secondPlayerInSameRound_getsSameScoreAsFirstPlayer() {
        GameData game = gameWithRoundTwoTotalsOf400And600();
        List<Integer> firstScores = MidGameJoinScoreCalculator.buildScores(game, 3, 2);
        Player firstJoinedPlayer = new Player("Player 3", firstScores, 47);
        firstJoinedPlayer.setMidGameJoinActiveRound(3);
        game.getPlayers().add(firstJoinedPlayer);
        game.setMidGameJoinActiveRound(null);
        game.setMidGameJoinBackfillScore(null);

        List<Integer> secondScores = MidGameJoinScoreCalculator.buildScores(game, 3, 2);

        assertEquals(firstScores, secondScores);
        assertEquals(602, positiveTotal(secondScores));
    }

    @Test
    public void secondPlayerInSameRound_legacyUnmarkedPlayerStillPrevents604() {
        GameData game = gameWithRoundTwoTotalsOf400And600();
        game.getPlayers().add(new Player("Player 3", scores(1, 601), 47));
        game.setMidGameJoinActiveRound(null);
        game.setMidGameJoinBackfillScore(null);

        List<Integer> secondScores = MidGameJoinScoreCalculator.buildScores(game, 3, 2);

        assertEquals(Integer.valueOf(601), secondScores.get(1));
        assertEquals(602, positiveTotal(secondScores));
    }

    @Test
    public void existingIncorrect604Player_doesNotBecomeTheNewBaseline() {
        GameData game = gameWithRoundTwoTotalsOf400And600();
        game.getPlayers().add(new Player("Player 3", scores(1, 601), 47));
        game.getPlayers().add(new Player("Player 4", scores(1, 603), 40));
        game.setMidGameJoinActiveRound(null);
        game.setMidGameJoinBackfillScore(null);

        List<Integer> nextScores = MidGameJoinScoreCalculator.buildScores(game, 3, 2);

        assertEquals(Integer.valueOf(601), nextScores.get(1));
        assertEquals(602, positiveTotal(nextScores));
    }

    @Test
    public void staleLowCachedScore_isRecalculated() {
        GameData game = gameWithRoundTwoTotalsOf400And600();
        game.setMidGameJoinActiveRound(3);
        game.setMidGameJoinBackfillScore(402);

        List<Integer> scores = MidGameJoinScoreCalculator.buildScores(game, 3, 2);

        assertEquals(Integer.valueOf(601), scores.get(1));
        assertEquals(602, positiveTotal(scores));
    }

    private static GameData gameWithRoundTwoTotalsOf400And600() {
        GameData game = new GameData();
        game.setPlayers(new ArrayList<>(Arrays.asList(
                new Player("Debabrata", scores(100, 300), 1),
                new Player("Player 2", scores(200, 400), 2))));
        return game;
    }

    private static List<Integer> scores(int roundOne, int roundTwo) {
        List<Integer> scores = new ArrayList<>();
        scores.add(roundOne);
        scores.add(roundTwo);
        while (scores.size() < 10) {
            scores.add(-1);
        }
        return scores;
    }

    private static int positiveTotal(List<Integer> scores) {
        int total = 0;
        for (Integer score : scores) {
            if (score != null && score > 0) {
                total += score;
            }
        }
        return total;
    }
}
